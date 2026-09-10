"""Small LAN-only HTTP gateway for the Android Plant Doctor screen."""

from __future__ import annotations

import json
import hmac
import logging
import os
import re
import socket
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Callable, Mapping

import firebase_admin
from firebase_admin import app_check, credentials

from core.config import FirebaseConfig
from services.plant_vision_service import PlantVisionService

HOST = os.getenv("AVORA_VISION_HOST", "0.0.0.0")
PORT = int(os.getenv("AVORA_VISION_PORT", "8787"))
MAX_BODY_BYTES = 7 * 1024 * 1024
REQUEST_READ_TIMEOUT_SECONDS = max(
    1.0, float(os.getenv("AVORA_VISION_REQUEST_READ_TIMEOUT", "15"))
)
FIREBASE_APP_ID = os.getenv(
    "AVORA_VISION_FIREBASE_APP_ID",
    "1:1067555097897:android:e2f618ead2f5410a807b38",
).strip()
SERVICE = PlantVisionService()
LOGGER = logging.getLogger("avora.plant_vision")
APP_CHECK_READY = False

CLIENT_ERROR_CODES = frozenset({
    "CONTEXT_TOO_LARGE",
    "IMAGE_TOO_LARGE",
    "INVALID_CONTEXT",
    "INVALID_IMAGE",
    "UNSUPPORTED_IMAGE_TYPE",
})
RUNTIME_ERROR_CODES = frozenset({
    "VISION_API_KEY_INVALID",
    "VISION_INVALID_RESPONSE",
    "VISION_NOT_CONFIGURED",
    "VISION_PROVIDER_UNAVAILABLE",
    "VISION_TIMEOUT",
})


class RequestValidationError(Exception):
    def __init__(self, status: HTTPStatus, code: str) -> None:
        super().__init__(code)
        self.status = status
        self.code = code


def _content_length(headers: Mapping[str, str]) -> int:
    get_all = getattr(headers, "get_all", None)
    values = get_all("Content-Length", []) if callable(get_all) else []
    if len(values) > 1:
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_CONTENT_LENGTH")
    raw_value = headers.get("Content-Length")
    if raw_value is None:
        raise RequestValidationError(HTTPStatus.LENGTH_REQUIRED, "CONTENT_LENGTH_REQUIRED")
    try:
        length = int(raw_value)
    except (TypeError, ValueError) as error:
        raise RequestValidationError(
            HTTPStatus.BAD_REQUEST, "INVALID_CONTENT_LENGTH"
        ) from error
    if length <= 0:
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "EMPTY_REQUEST")
    if length > MAX_BODY_BYTES:
        raise RequestValidationError(
            HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "REQUEST_TOO_LARGE"
        )
    return length


def _require_json_content_type(headers: Mapping[str, str]) -> None:
    media_type = headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
    if media_type != "application/json":
        raise RequestValidationError(
            HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_CONTENT_TYPE"
        )


def _parse_json_object(raw_body: bytes) -> dict:
    try:
        body = json.loads(raw_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_JSON") from error
    if not isinstance(body, dict):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_REQUEST")
    return body


def _analysis_arguments(body: dict) -> tuple[str, str, dict]:
    if not set(body).issubset({"image_base64", "mime_type", "context"}):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_REQUEST")
    image_base64 = body.get("image_base64")
    mime_type = body.get("mime_type", "image/jpeg")
    context = body.get("context", {})
    if not isinstance(image_base64, str) or not image_base64:
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_IMAGE")
    if not isinstance(mime_type, str):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "UNSUPPORTED_IMAGE_TYPE")
    if not isinstance(context, dict):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_CONTEXT")
    PlantVisionService._validate_context(context)
    return image_base64, mime_type, context


def _organic_context(body: dict) -> dict:
    if not set(body).issubset({"context"}):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_REQUEST")
    context = body.get("context", {})
    if not isinstance(context, dict):
        raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INVALID_CONTEXT")
    PlantVisionService._validate_context(context)
    return context


def _client_error(error: ValueError) -> tuple[HTTPStatus, str]:
    code = str(error)
    if code not in CLIENT_ERROR_CODES:
        return HTTPStatus.BAD_REQUEST, "INVALID_REQUEST"
    if code in {"CONTEXT_TOO_LARGE", "IMAGE_TOO_LARGE"}:
        return HTTPStatus.REQUEST_ENTITY_TOO_LARGE, code
    return HTTPStatus.BAD_REQUEST, code


def _runtime_error(error: RuntimeError) -> tuple[HTTPStatus, str]:
    code = str(error)
    if code == "VISION_TIMEOUT":
        return HTTPStatus.GATEWAY_TIMEOUT, code
    if code == "VISION_NOT_CONFIGURED":
        return HTTPStatus.SERVICE_UNAVAILABLE, code
    if code in RUNTIME_ERROR_CODES or re.fullmatch(r"VISION_PROVIDER_ERROR:\d{3}", code):
        return HTTPStatus.BAD_GATEWAY, code
    return HTTPStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_FAILED"


def _initialize_app_check() -> None:
    global APP_CHECK_READY
    try:
        firebase_admin.get_app()
    except ValueError:
        credentials_path = Path(FirebaseConfig.CREDENTIALS_FILE)
        if not credentials_path.is_absolute():
            credentials_path = Path(__file__).resolve().parents[1] / credentials_path
        firebase_admin.initialize_app(credentials.Certificate(credentials_path))
    APP_CHECK_READY = True
    LOGGER.info("Plant vision Firebase App Check initialized.")


def _verify_app_check_token(
    token: str,
    verifier: Callable[[str], Mapping[str, object]] | None = None,
) -> bool:
    if not token or not FIREBASE_APP_ID:
        return False
    verify = verifier or app_check.verify_token
    try:
        claims = verify(token)
    except Exception as error:
        LOGGER.warning(
            "Plant vision App Check verification failed: %s",
            type(error).__name__,
        )
        return False
    received_app_id = str(claims.get("app_id", "")).strip()
    return hmac.compare_digest(received_app_id, FIREBASE_APP_ID)


def _authorization_method(
    headers: Mapping[str, str],
    verifier: Callable[[str], Mapping[str, object]] | None = None,
) -> str:
    app_check_token = headers.get("X-Firebase-AppCheck", "").strip()
    if _verify_app_check_token(app_check_token, verifier):
        return "APP_CHECK"
    return ""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            self._json(HTTPStatus.NOT_FOUND, {"error": "NOT_FOUND"})
            return
        self._json(
            HTTPStatus.OK,
            {"configured": SERVICE.configured() and APP_CHECK_READY},
        )

    def do_POST(self) -> None:  # noqa: N802
        supported_paths = {
            "/v1/plant-assistant/analyze",
            "/v1/fertilizer-assistant/organic-alternatives",
        }
        if self.path not in supported_paths:
            self._json(HTTPStatus.NOT_FOUND, {"error": "NOT_FOUND"})
            return
        authorization_method = _authorization_method(self.headers)
        if not authorization_method:
            self._json(HTTPStatus.UNAUTHORIZED, {"error": "UNAUTHORIZED"})
            return
        try:
            _require_json_content_type(self.headers)
            length = _content_length(self.headers)
            self.connection.settimeout(REQUEST_READ_TIMEOUT_SECONDS)
            try:
                raw_body = self.rfile.read(length)
            except socket.timeout as error:
                raise RequestValidationError(
                    HTTPStatus.REQUEST_TIMEOUT, "REQUEST_TIMEOUT"
                ) from error
            if len(raw_body) != length:
                raise RequestValidationError(HTTPStatus.BAD_REQUEST, "INCOMPLETE_REQUEST")
            body = _parse_json_object(raw_body)
            if self.path == "/v1/plant-assistant/analyze":
                result = SERVICE.analyze(*_analysis_arguments(body))
            else:
                result = SERVICE.advise_organic(_organic_context(body))
            self._json(HTTPStatus.OK, result)
        except RequestValidationError as error:
            self._json(error.status, {"error": error.code})
        except ValueError as error:
            status, code = _client_error(error)
            self._json(status, {"error": code})
        except RuntimeError as error:
            status, code = _runtime_error(error)
            LOGGER.warning("Plant vision request failed: %s", code)
            self._json(status, {"error": code})
        except Exception:
            LOGGER.exception("Unexpected plant vision error")
            self._json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "ANALYSIS_FAILED"})

    def log_message(self, format: str, *args: object) -> None:
        LOGGER.info("%s - %s", self.address_string(), format % args)

    def _json(self, status: HTTPStatus, payload: dict) -> None:
        content = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        _initialize_app_check()
    except Exception:
        LOGGER.exception("Plant vision Firebase App Check initialization failed.")
        raise
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    LOGGER.info("Plant vision server listening on %s:%s", HOST, PORT)
    server.serve_forever()


if __name__ == "__main__":
    main()
