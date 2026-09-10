"""Provider errors are actionable without exposing API keys or response bodies."""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import requests

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from services.plant_vision_service import PlantVisionService


class PlantVisionProviderErrorsTest(unittest.TestCase):
    def service(self):
        service = PlantVisionService()
        service._read_key = Mock(return_value="private-test-key")
        return service

    def response(self, status=400, reason="API_KEY_INVALID"):
        response = Mock(ok=False, status_code=status)
        response.json.return_value = {
            "error": {
                "message": "private-test-key must never be returned to the app",
                "details": [{"reason": reason}],
            }
        }
        return response

    def success_response(self, result=None):
        response = Mock(ok=True, status_code=200)
        response.json.return_value = {
            "candidates": [{
                "content": {
                    "parts": [{
                        "text": json.dumps(result or {
                            "is_plant_photo": True,
                            "title": "Görsel ön değerlendirme",
                        })
                    }]
                }
            }]
        }
        return response

    def test_photo_invalid_key_is_explicit_and_key_is_not_in_url(self):
        with patch("services.plant_vision_service.requests.post",
                   return_value=self.response()) as post:
            with self.assertRaisesRegex(RuntimeError, "^VISION_API_KEY_INVALID$"):
                self.service().analyze("/9g=", "image/jpeg", {})
        self.assertNotIn("params", post.call_args.kwargs)
        self.assertEqual({"x-goog-api-key": "private-test-key"},
                         post.call_args.kwargs["headers"])
        self.assertEqual(
            PlantVisionService.REQUEST_TIMEOUT,
            post.call_args.kwargs["timeout"],
        )

    def test_organic_advice_uses_the_same_safe_error_handling(self):
        with patch("services.plant_vision_service.requests.post",
                   return_value=self.response()):
            with self.assertRaisesRegex(RuntimeError, "^VISION_API_KEY_INVALID$"):
                self.service().advise_organic({})

    def test_expired_key_has_the_same_actionable_error(self):
        with self.assertRaisesRegex(RuntimeError, "^VISION_API_KEY_INVALID$"):
            PlantVisionService._raise_provider_error(
                self.response(reason="API_KEY_EXPIRED"))

    def test_unrelated_400_is_not_misdiagnosed_as_invalid_key(self):
        with self.assertRaisesRegex(RuntimeError, "^VISION_PROVIDER_ERROR:400$"):
            PlantVisionService._raise_provider_error(
                self.response(reason="INVALID_REQUEST"))

    def test_non_json_response_does_not_leak_provider_content(self):
        response = self.response(status=503)
        response.json.side_effect = ValueError("sensitive response")
        with self.assertRaisesRegex(RuntimeError, "^VISION_PROVIDER_ERROR:503$"):
            PlantVisionService._raise_provider_error(response)

    def test_malformed_error_shape_keeps_http_status(self):
        for body in (None, [], {"error": "bad"}, {"error": {"details": None}}):
            with self.subTest(body=body):
                response = self.response(status=429)
                response.json.return_value = body
                with self.assertRaisesRegex(RuntimeError, "^VISION_PROVIDER_ERROR:429$"):
                    PlantVisionService._raise_provider_error(response)

    def test_transient_provider_status_is_retried_once(self):
        responses = [self.response(status=503), self.success_response()]
        with patch(
            "services.plant_vision_service.requests.post", side_effect=responses
        ) as post, patch("services.plant_vision_service.time.sleep") as sleep:
            result = self.service().analyze("/9g=", "image/jpeg", {})
        self.assertTrue(result["is_plant_photo"])
        self.assertEqual(2, post.call_count)
        sleep.assert_called_once()

    def test_timeout_returns_safe_code_without_duplicate_submission(self):
        with patch(
            "services.plant_vision_service.requests.post",
            side_effect=requests.Timeout("provider details must stay private"),
        ) as post, patch("services.plant_vision_service.time.sleep"):
            with self.assertRaisesRegex(RuntimeError, "^VISION_TIMEOUT$"):
                self.service().analyze("/9g=", "image/jpeg", {})
        self.assertEqual(1, post.call_count)

    def test_connect_timeout_is_retried_once(self):
        with patch(
            "services.plant_vision_service.requests.post",
            side_effect=requests.ConnectTimeout("private network details"),
        ) as post, patch("services.plant_vision_service.time.sleep"):
            with self.assertRaisesRegex(RuntimeError, "^VISION_TIMEOUT$"):
                self.service().analyze("/9g=", "image/jpeg", {})
        self.assertEqual(PlantVisionService.MAX_PROVIDER_ATTEMPTS, post.call_count)

    def test_connection_error_is_retried_then_returns_safe_code(self):
        with patch(
            "services.plant_vision_service.requests.post",
            side_effect=requests.ConnectionError("private network details"),
        ) as post, patch("services.plant_vision_service.time.sleep"):
            with self.assertRaisesRegex(
                RuntimeError, "^VISION_PROVIDER_UNAVAILABLE$"
            ):
                self.service().analyze("/9g=", "image/jpeg", {})
        self.assertEqual(PlantVisionService.MAX_PROVIDER_ATTEMPTS, post.call_count)

    def test_non_object_model_json_is_rejected(self):
        response = self.success_response()
        response.json.return_value["candidates"][0]["content"]["parts"][0][
            "text"
        ] = "[]"
        with patch(
            "services.plant_vision_service.requests.post", return_value=response
        ):
            with self.assertRaisesRegex(RuntimeError, "^VISION_INVALID_RESPONSE$"):
                self.service().analyze("/9g=", "image/jpeg", {})


if __name__ == "__main__":
    unittest.main()
