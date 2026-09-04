"""Provider errors are actionable without exposing API keys or response bodies."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

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

    def test_photo_invalid_key_is_explicit_and_key_is_not_in_url(self):
        with patch("services.plant_vision_service.requests.post",
                   return_value=self.response()) as post:
            with self.assertRaisesRegex(RuntimeError, "^VISION_API_KEY_INVALID$"):
                self.service().analyze("eA==", "image/jpeg", {})
        self.assertNotIn("params", post.call_args.kwargs)
        self.assertEqual({"x-goog-api-key": "private-test-key"},
                         post.call_args.kwargs["headers"])

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


if __name__ == "__main__":
    unittest.main()
