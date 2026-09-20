"""HTTP contract checks for malformed Plant Vision gateway requests."""

from __future__ import annotations

import http.client
import json
import socket
import sys
import threading
import unittest
from http import HTTPStatus
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services import plant_vision_server


class _FakeService:
    def configured(self) -> bool:
        return True

    def analyze(self, _image: str, _mime_type: str, _context: dict) -> dict:
        return {"is_plant_photo": True}

    def advise_organic(self, _context: dict) -> dict:
        return {"recommendations": []}


class PlantVisionServerRequestsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.original_authorization = plant_vision_server._authorization_method
        cls.original_service = plant_vision_server.SERVICE
        plant_vision_server._authorization_method = lambda _headers: "APP_CHECK"
        plant_vision_server.SERVICE = _FakeService()
        cls.server = plant_vision_server.PlantVisionHttpServer(
            ("127.0.0.1", 0), plant_vision_server.Handler
        )
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)
        plant_vision_server._authorization_method = cls.original_authorization
        plant_vision_server.SERVICE = cls.original_service

    def request(
        self, body: bytes, content_type: str = "application/json"
    ) -> tuple[int, dict]:
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_address[1], timeout=3
        )
        connection.request(
            "POST",
            "/v1/plant-assistant/analyze",
            body=body,
            headers={
                "Content-Type": content_type,
                "X-Firebase-AppCheck": "test-token",
            },
        )
        response = connection.getresponse()
        payload = json.loads(response.read().decode("utf-8"))
        connection.close()
        return response.status, payload

    def raw_request(self, request: bytes) -> bytes:
        with socket.create_connection(
            ("127.0.0.1", self.server.server_address[1]), timeout=3
        ) as connection:
            connection.sendall(request)
            connection.settimeout(3)
            chunks = []
            while True:
                chunk = connection.recv(4096)
                if not chunk:
                    break
                chunks.append(chunk)
            return b"".join(chunks)

    def test_invalid_content_length_returns_json_instead_of_dropping_connection(self):
        response = self.raw_request(
            b"POST /v1/plant-assistant/analyze HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            b"Content-Type: application/json\r\n"
            b"X-Firebase-AppCheck: test-token\r\n"
            b"Content-Length: nope\r\n\r\n"
        )
        self.assertIn(b" 400 ", response.split(b"\r\n", 1)[0])
        self.assertIn(b'"error": "INVALID_CONTENT_LENGTH"', response)

    def test_health_is_loopback_default_and_has_security_headers(self):
        self.assertEqual("127.0.0.1", plant_vision_server.HOST)
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.server.server_address[1], timeout=3
        )
        connection.request("GET", "/health")
        response = connection.getresponse()
        response.read()
        self.assertEqual(HTTPStatus.OK, response.status)
        self.assertEqual("no-store", response.getheader("Cache-Control"))
        self.assertEqual("nosniff", response.getheader("X-Content-Type-Options"))
        self.assertEqual("DENY", response.getheader("X-Frame-Options"))
        self.assertEqual("no-referrer", response.getheader("Referrer-Policy"))
        connection.close()

    def test_missing_content_length_has_stable_error(self):
        response = self.raw_request(
            b"POST /v1/plant-assistant/analyze HTTP/1.1\r\n"
            b"Host: localhost\r\n"
            b"Content-Type: application/json\r\n"
            b"X-Firebase-AppCheck: test-token\r\n\r\n"
        )
        self.assertIn(b" 411 ", response.split(b"\r\n", 1)[0])
        self.assertIn(b'"error": "CONTENT_LENGTH_REQUIRED"', response)

    def test_incomplete_slow_body_times_out_with_stable_error(self):
        previous_timeout = plant_vision_server.REQUEST_READ_TIMEOUT_SECONDS
        plant_vision_server.REQUEST_READ_TIMEOUT_SECONDS = 0.05
        try:
            response = self.raw_request(
                b"POST /v1/plant-assistant/analyze HTTP/1.1\r\n"
                b"Host: localhost\r\n"
                b"Content-Type: application/json\r\n"
                b"X-Firebase-AppCheck: test-token\r\n"
                b"Content-Length: 100\r\n\r\n"
            )
        finally:
            plant_vision_server.REQUEST_READ_TIMEOUT_SECONDS = previous_timeout
        self.assertIn(b" 408 ", response.split(b"\r\n", 1)[0])
        self.assertIn(b'"error": "REQUEST_TIMEOUT"', response)

    def test_malformed_json_uses_stable_non_parser_error(self):
        status, payload = self.request(b"{")
        self.assertEqual(HTTPStatus.BAD_REQUEST, status)
        self.assertEqual({"error": "INVALID_JSON"}, payload)

    def test_non_object_json_is_rejected(self):
        status, payload = self.request(b"[]")
        self.assertEqual(HTTPStatus.BAD_REQUEST, status)
        self.assertEqual({"error": "INVALID_REQUEST"}, payload)

    def test_missing_image_is_a_client_error(self):
        status, payload = self.request(b"{}")
        self.assertEqual(HTTPStatus.BAD_REQUEST, status)
        self.assertEqual({"error": "INVALID_IMAGE"}, payload)

    def test_context_and_mime_types_are_validated_before_service_call(self):
        cases = (
            (
                {"image_base64": "/9g=", "context": []},
                "INVALID_CONTEXT",
            ),
            (
                {"image_base64": "/9g=", "mime_type": [], "context": {}},
                "UNSUPPORTED_IMAGE_TYPE",
            ),
        )
        for body, expected_code in cases:
            with self.subTest(body=body):
                status, payload = self.request(json.dumps(body).encode("utf-8"))
                self.assertEqual(HTTPStatus.BAD_REQUEST, status)
                self.assertEqual({"error": expected_code}, payload)

    def test_oversized_context_uses_payload_too_large_status(self):
        body = json.dumps({
            "image_base64": "/9g=",
            "context": {
                "note": "x"
                * (plant_vision_server.PlantVisionService.MAX_CONTEXT_BYTES + 1)
            },
        }).encode("utf-8")
        status, payload = self.request(body)
        self.assertEqual(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, status)
        self.assertEqual({"error": "CONTEXT_TOO_LARGE"}, payload)

    def test_wrong_http_content_type_is_rejected(self):
        status, payload = self.request(b"{}", "text/plain")
        self.assertEqual(HTTPStatus.UNSUPPORTED_MEDIA_TYPE, status)
        self.assertEqual({"error": "UNSUPPORTED_CONTENT_TYPE"}, payload)

    def test_runtime_errors_map_to_contract_statuses(self):
        self.assertEqual(
            (HTTPStatus.GATEWAY_TIMEOUT, "VISION_TIMEOUT"),
            plant_vision_server._runtime_error(RuntimeError("VISION_TIMEOUT")),
        )
        self.assertEqual(
            (HTTPStatus.SERVICE_UNAVAILABLE, "VISION_NOT_CONFIGURED"),
            plant_vision_server._runtime_error(
                RuntimeError("VISION_NOT_CONFIGURED")
            ),
        )
        self.assertEqual(
            (HTTPStatus.INTERNAL_SERVER_ERROR, "ANALYSIS_FAILED"),
            plant_vision_server._runtime_error(RuntimeError("private details")),
        )


if __name__ == "__main__":
    unittest.main()
