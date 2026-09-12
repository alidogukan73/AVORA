"""Dependency-free HTTP entry point for the AVORA NAS API."""

from __future__ import annotations

import ipaddress
import json
import logging
import os
import signal
import threading
import time
from dataclasses import asdict
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlsplit

from .config import ConfigurationError, Settings
from .database import (
    DataConflictError,
    InvalidCurrentPasswordError,
    InvalidCredentialsError,
    InviteError,
    LoginRateLimitError,
    PasswordUnchangedError,
    SetupCompleteError,
)
from .security import PasswordPolicyError
from .service import AvoraService


LOGGER = logging.getLogger("avora_nas")
SERVICE: AvoraService | None = None


class RequestError(RuntimeError):
    def __init__(self, status: HTTPStatus, code: str, message: str) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.message = message


class AvoraHttpServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 32


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "AVORA"
    sys_version = ""

    def setup(self) -> None:
        super().setup()
        self.connection.settimeout(15.0)

    def do_GET(self) -> None:  # noqa: N802
        self._dispatch("GET")

    def do_POST(self) -> None:  # noqa: N802
        self._dispatch("POST")

    def do_PUT(self) -> None:  # noqa: N802
        self._dispatch("PUT")

    def do_PATCH(self) -> None:  # noqa: N802
        self._dispatch("PATCH")

    def do_DELETE(self) -> None:  # noqa: N802
        self._json_error(HTTPStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed.")

    def do_OPTIONS(self) -> None:  # noqa: N802
        self._json_error(HTTPStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed.")

    def _dispatch(self, method: str) -> None:
        try:
            service = _service()
            path = urlsplit(self.path).path
            if path == "/health" and method == "GET":
                self._json(HTTPStatus.OK, service.health())
                return
            if path == "/v1/setup" and method == "POST":
                body = self._read_json(service.settings.max_json_bytes)
                user = service.setup_admin(
                    self._header("X-AVORA-Setup-Token"),
                    required_text(body, "email"),
                    required_text(body, "display_name"),
                    required_text(body, "password"),
                )
                self._json(HTTPStatus.CREATED, {"user": asdict(user)})
                return
            if path == "/v1/auth/login" and method == "POST":
                body = self._read_json(32 * 1024)
                session = service.login(
                    required_text(body, "email"),
                    required_text(body, "password"),
                    self._login_source(),
                )
                self._json(
                    HTTPStatus.OK,
                    {
                        "access_token": session.token,
                        "token_type": "Bearer",
                        "expires_at": session.expires_at,
                        "user": asdict(session.user),
                    },
                )
                return
            if path == "/v1/auth/register" and method == "POST":
                body = self._read_json(32 * 1024)
                session = service.register_session(
                    required_text(body, "invite_code"),
                    required_text(body, "email"),
                    required_text(body, "display_name"),
                    required_text(body, "password"),
                )
                self._json(
                    HTTPStatus.CREATED,
                    {
                        "access_token": session.token,
                        "token_type": "Bearer",
                        "expires_at": session.expires_at,
                        "user": asdict(session.user),
                    },
                )
                return

            token, user = self._authenticated(service)
            if path == "/v1/auth/logout" and method == "POST":
                self._require_empty_body()
                service.logout(token)
                self._json(HTTPStatus.OK, {"logged_out": True})
                return
            if path == "/v1/me" and method == "GET":
                self._json(HTTPStatus.OK, {"user": asdict(user)})
                return
            if path == "/v1/account/password" and method == "POST":
                body = self._read_json(32 * 1024)
                revoked = service.change_password(
                    token,
                    user,
                    required_text(body, "current_password"),
                    required_text(body, "new_password"),
                    self._login_source(),
                )
                self._json(
                    HTTPStatus.OK,
                    {"password_changed": True, "revoked_sessions": revoked},
                )
                return
            if path == "/v1/account/sessions/revoke-others" and method == "POST":
                self._require_empty_body()
                revoked = service.revoke_other_sessions(token, user)
                self._json(
                    HTTPStatus.OK,
                    {"revoked_sessions": revoked},
                )
                return
            if path == "/v1/access-requests" and method == "POST":
                body = self._read_json(32 * 1024)
                request = service.request_device_access(
                    user,
                    required_text(body, "device_id"),
                    required_text(body, "firebase_uid"),
                )
                self._json(HTTPStatus.OK, {"access_request": asdict(request)})
                return
            if path == "/v1/admin/access-requests" and method == "GET":
                query = urlsplit(self.path).query
                parameters = dict(
                    item.split("=", 1) if "=" in item else (item, "")
                    for item in query.split("&") if item
                )
                device_id = unquote(parameters.get("device_id", ""))
                requests = service.list_pending_access_requests(user, device_id)
                self._json(
                    HTTPStatus.OK,
                    {"access_requests": [asdict(item) for item in requests]},
                )
                return
            if path == "/v1/admin/access-requests/approve" and method == "POST":
                body = self._read_json(32 * 1024)
                request = service.approve_access_request(
                    user, required_text(body, "request_id")
                )
                if request is None:
                    raise RequestError(
                        HTTPStatus.NOT_FOUND,
                        "access_request_not_found",
                        "Pending access request not found.",
                    )
                self._json(HTTPStatus.OK, {"access_request": asdict(request)})
                return
            if path == "/v1/admin/invites" and method == "POST":
                body = self._read_json(32 * 1024)
                valid_hours = optional_int(body, "valid_hours", 72)
                max_uses = optional_int(body, "max_uses", 1)
                code, expires_at = service.create_invite(user, valid_hours, max_uses)
                self._json(
                    HTTPStatus.CREATED,
                    {"invite_code": code, "expires_at": expires_at, "max_uses": max_uses},
                )
                return
            if path == "/v1/admin/invites/revoke" and method == "POST":
                body = self._read_json(32 * 1024)
                revoked = service.revoke_invite(
                    user, required_text(body, "invite_code")
                )
                if not revoked:
                    raise RequestError(
                        HTTPStatus.NOT_FOUND, "invite_not_found", "Invitation not found."
                    )
                self._json(HTTPStatus.OK, {"revoked": True})
                return
            if path == "/v1/data/documents" and method == "GET":
                documents = [asdict(item) for item in service.list_documents(user)]
                self._json(HTTPStatus.OK, {"documents": documents})
                return
            if path.startswith("/v1/data/documents/"):
                key = _single_path_value(path, "/v1/data/documents/")
                if method == "GET":
                    document = service.get_document(user, key)
                    if document is None:
                        raise RequestError(HTTPStatus.NOT_FOUND, "not_found", "Document not found.")
                    self._json(HTTPStatus.OK, {"document": asdict(document)})
                    return
                if method == "PUT":
                    body = self._read_json(service.settings.max_json_bytes)
                    if "data" not in body:
                        raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Field 'data' is required.")
                    expected = body.get("expected_version")
                    if expected is not None and (isinstance(expected, bool) or not isinstance(expected, int) or expected < 0):
                        raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Invalid expected_version.")
                    document = service.put_document(user, key, body["data"], expected)
                    self._json(HTTPStatus.OK, {"document": asdict(document)})
                    return
            if path == "/v1/photos" and method == "GET":
                photos = [asdict(item) for item in service.list_photos(user)]
                self._json(HTTPStatus.OK, {"photos": photos})
                return
            if path.startswith("/v1/photos/") and path.endswith("/metadata"):
                photo_path = path[: -len("/metadata")]
                photo_id = _single_path_value(photo_path, "/v1/photos/")
                if method == "POST":
                    body = self._read_json(64 * 1024)
                    metadata = body.get("metadata")
                    if not isinstance(metadata, dict):
                        raise RequestError(
                            HTTPStatus.BAD_REQUEST,
                            "invalid_request",
                            "Field 'metadata' must be an object.",
                        )
                    record = service.update_photo_metadata(user, photo_id, metadata)
                    self._json(HTTPStatus.OK, {"photo": asdict(record)})
                    return
            if path.startswith("/v1/photos/"):
                photo_id = _single_path_value(path, "/v1/photos/")
                if method == "PUT":
                    if self.headers.get_content_type().lower() != "image/jpeg":
                        raise RequestError(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "invalid_photo", "Content-Type must be image/jpeg.")
                    content = self._read_body(service.settings.max_photo_bytes)
                    record = service.save_photo(user, photo_id, content)
                    self._json(HTTPStatus.OK, {"photo": asdict(record)})
                    return
                if method == "PATCH":
                    body = self._read_json(64 * 1024)
                    metadata = body.get("metadata")
                    if not isinstance(metadata, dict):
                        raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Field 'metadata' must be an object.")
                    record = service.update_photo_metadata(user, photo_id, metadata)
                    self._json(HTTPStatus.OK, {"photo": asdict(record)})
                    return
                if method == "GET":
                    result = service.get_photo(user, photo_id)
                    if result is None:
                        raise RequestError(HTTPStatus.NOT_FOUND, "not_found", "Photo not found.")
                    record, file_path = result
                    self._file(HTTPStatus.OK, file_path, "image/jpeg", record.sha256)
                    return
            raise RequestError(HTTPStatus.NOT_FOUND, "not_found", "Endpoint not found.")
        except RequestError as exc:
            self._json_error(exc.status, exc.code, exc.message)
        except LoginRateLimitError as exc:
            self._json_error(
                HTTPStatus.TOO_MANY_REQUESTS,
                "rate_limited",
                "Too many login attempts. Try again later.",
                {"Retry-After": str(exc.retry_after)},
            )
        except InvalidCredentialsError:
            time.sleep(0.2)
            self._json_error(HTTPStatus.UNAUTHORIZED, "invalid_credentials", "Invalid credentials.")
        except InvalidCurrentPasswordError:
            time.sleep(0.2)
            self._json_error(HTTPStatus.BAD_REQUEST, "current_password_invalid", "The current password is invalid.")
        except PasswordUnchangedError:
            self._json_error(HTTPStatus.BAD_REQUEST, "password_unchanged", "The new password must be different.")
        except PermissionError as exc:
            self._json_error(HTTPStatus.FORBIDDEN, "forbidden", str(exc))
        except SetupCompleteError as exc:
            self._json_error(HTTPStatus.CONFLICT, "setup_complete", str(exc))
        except InviteError as exc:
            self._json_error(HTTPStatus.BAD_REQUEST, "invalid_invite", str(exc))
        except DataConflictError as exc:
            self._json_error(HTTPStatus.CONFLICT, "version_conflict", str(exc))
        except PasswordPolicyError as exc:
            self._json_error(HTTPStatus.BAD_REQUEST, "weak_password", str(exc))
        except (KeyError, ValueError) as exc:
            self._json_error(HTTPStatus.BAD_REQUEST, "invalid_request", str(exc))
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True
        except Exception:
            LOGGER.exception("Unhandled request failure for %s %s", method, urlsplit(self.path).path)
            self._json_error(HTTPStatus.INTERNAL_SERVER_ERROR, "server_error", "The request could not be completed.")

    def _authenticated(self, service: AvoraService) -> tuple[str, Any]:
        authorization = self.headers.get("Authorization", "")
        scheme, separator, token = authorization.partition(" ")
        if separator != " " or scheme.lower() != "bearer" or not token:
            raise InvalidCredentialsError("Authentication is required.")
        return token, service.authenticate(token)

    def _read_json(self, maximum: int) -> dict[str, Any]:
        if self.headers.get_content_type().lower() != "application/json":
            raise RequestError(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "invalid_json", "Content-Type must be application/json.")
        content = self._read_body(maximum)
        try:
            value = json.loads(content.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_json", "The JSON body is invalid.") from exc
        if not isinstance(value, dict):
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_json", "The JSON body must be an object.")
        return value

    def _read_body(self, maximum: int) -> bytes:
        if self.headers.get("Transfer-Encoding"):
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Transfer-Encoding is not supported.")
        raw_length = self.headers.get("Content-Length")
        if raw_length is None:
            raise RequestError(HTTPStatus.LENGTH_REQUIRED, "length_required", "Content-Length is required.")
        try:
            length = int(raw_length)
        except ValueError as exc:
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Invalid Content-Length.") from exc
        if length < 0 or length > maximum:
            raise RequestError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "body_too_large", "Request body is too large.")
        content = self.rfile.read(length)
        if len(content) != length:
            raise RequestError(HTTPStatus.BAD_REQUEST, "incomplete_body", "Request body is incomplete.")
        return content

    def _require_empty_body(self) -> None:
        raw_length = self.headers.get("Content-Length", "0")
        try:
            length = int(raw_length)
        except ValueError as exc:
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "Invalid Content-Length.") from exc
        if length != 0:
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", "This endpoint requires an empty body.")

    def _header(self, name: str) -> str:
        value = self.headers.get(name, "")
        if len(value) > 512:
            raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", f"Header {name} is too long.")
        return value

    def _login_source(self) -> str:
        return trusted_client_source(
            self.client_address[0], self._header("X-Forwarded-For")
        )

    def _json(
        self,
        status: HTTPStatus,
        value: dict[str, Any],
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status.value)
        self._security_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        for name, header_value in (extra_headers or {}).items():
            self.send_header(name, header_value)
        self.end_headers()
        self.wfile.write(body)

    def _json_error(
        self,
        status: HTTPStatus,
        code: str,
        message: str,
        extra_headers: dict[str, str] | None = None,
    ) -> None:
        try:
            self._json(
                status,
                {"error": {"code": code, "message": message}},
                extra_headers,
            )
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            self.close_connection = True

    def _file(self, status: HTTPStatus, path: Path, content_type: str, etag: str) -> None:
        size = path.stat().st_size
        self.send_response(status.value)
        self._security_headers()
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(size))
        self.send_header("ETag", f'"{etag}"')
        self.end_headers()
        with path.open("rb") as stream:
            while chunk := stream.read(64 * 1024):
                self.wfile.write(chunk)

    def _security_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        self.send_header("Referrer-Policy", "no-referrer")

    def log_message(self, format: str, *args: Any) -> None:
        LOGGER.info("client=%s %s", self.client_address[0], format % args)


def required_text(body: dict[str, Any], name: str) -> str:
    value = body.get(name)
    if not isinstance(value, str) or not value:
        raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", f"Field '{name}' is required.")
    return value


def optional_int(body: dict[str, Any], name: str, default: int) -> int:
    value = body.get(name, default)
    if isinstance(value, bool) or not isinstance(value, int):
        raise RequestError(HTTPStatus.BAD_REQUEST, "invalid_request", f"Field '{name}' must be an integer.")
    return value


def _single_path_value(path: str, prefix: str) -> str:
    raw = path[len(prefix) :]
    value = unquote(raw)
    if not raw or "/" in value or "\\" in value:
        raise RequestError(HTTPStatus.NOT_FOUND, "not_found", "Endpoint not found.")
    return value


def _service() -> AvoraService:
    if SERVICE is None:
        raise RuntimeError("AVORA service is not initialized.")
    return SERVICE


def trusted_client_source(peer: str, forwarded_for: str) -> str:
    try:
        peer_address = ipaddress.ip_address(peer)
    except ValueError:
        return "unknown"
    if not peer_address.is_loopback or not forwarded_for:
        return str(peer_address)
    candidate = forwarded_for.split(",", 1)[0].strip()
    try:
        forwarded_address = ipaddress.ip_address(candidate)
    except ValueError:
        return str(peer_address)
    return str(forwarded_address)


def configure_logging(log_dir: Path) -> None:
    level = os.environ.get("AVORA_LOG_LEVEL", "INFO").upper()
    numeric_level = getattr(logging, level, logging.INFO)
    logging.basicConfig(
        level=numeric_level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def main() -> None:
    global SERVICE
    try:
        settings = Settings.from_environment()
    except ConfigurationError as exc:
        raise SystemExit(f"Configuration error: {exc}") from exc
    configure_logging(settings.log_dir)
    SERVICE = AvoraService(settings)
    server = AvoraHttpServer((settings.host, settings.port), Handler)
    stop = threading.Event()

    def request_shutdown(_signum: int, _frame: Any) -> None:
        if stop.is_set():
            return
        stop.set()
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, request_shutdown)
    signal.signal(signal.SIGINT, request_shutdown)
    LOGGER.info("AVORA NAS API listening on %s:%s", settings.host, settings.port)
    try:
        server.serve_forever(poll_interval=0.5)
    finally:
        server.server_close()
        LOGGER.info("AVORA NAS API stopped")


if __name__ == "__main__":
    main()
