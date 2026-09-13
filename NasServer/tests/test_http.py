from __future__ import annotations

import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path

from avora_nas.config import Settings
from avora_nas.service import AvoraService
from avora_nas import server as server_module


class HttpContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        settings = Settings(
            data_dir=Path(self.temporary.name),
            setup_token="H" * 48,
            login_account_attempts=3,
            login_source_attempts=10,
        ).prepare()
        server_module.SERVICE = AvoraService(settings)
        self.server = server_module.AvoraHttpServer(
            ("127.0.0.1", 0), server_module.Handler
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self._stop_server)
        self.base_url = f"http://127.0.0.1:{self.server.server_address[1]}"
        self.setup_token = settings.setup_token

    def _stop_server(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        server_module.SERVICE = None

    def request(
        self,
        method: str,
        path: str,
        body: dict | bytes | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[int, dict | bytes, dict]:
        request_headers = dict(headers or {})
        data = None
        if isinstance(body, dict):
            data = json.dumps(body).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        elif isinstance(body, bytes):
            data = body
        request = urllib.request.Request(
            self.base_url + path, data=data, headers=request_headers, method=method
        )
        try:
            response = urllib.request.urlopen(request, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        try:
            content = response.read()
            value = (
                json.loads(content)
                if response.headers.get_content_type() == "application/json"
                else content
            )
            return response.status, value, dict(response.headers)
        finally:
            response.close()

    def setup_and_login(self) -> str:
        status, _, _ = self.request(
            "POST",
            "/v1/setup",
            {
                "email": "owner@example.com",
                "display_name": "AVORA Sahibi",
                "password": "Guvenli-Yonetici-2026!",
            },
            {"X-AVORA-Setup-Token": self.setup_token},
        )
        self.assertEqual(201, status)
        status, body, _ = self.request(
            "POST",
            "/v1/auth/login",
            {"email": "owner@example.com", "password": "Guvenli-Yonetici-2026!"},
        )
        self.assertEqual(200, status)
        return body["access_token"]

    def test_health_is_public_and_has_security_headers(self) -> None:
        status, body, headers = self.request("GET", "/health")
        self.assertEqual(200, status)
        self.assertEqual("ok", body["status"])
        self.assertEqual("nosniff", headers["X-Content-Type-Options"])
        self.assertEqual("no-store", headers["Cache-Control"])

    def test_forwarded_ip_is_only_trusted_from_loopback_proxy(self) -> None:
        self.assertEqual(
            "198.51.100.24",
            server_module.trusted_client_source("127.0.0.1", "198.51.100.24"),
        )
        self.assertEqual(
            "192.0.2.10",
            server_module.trusted_client_source("192.0.2.10", "198.51.100.24"),
        )
        self.assertEqual(
            "127.0.0.1", server_module.trusted_client_source("127.0.0.1", "bad")
        )

    def test_documents_require_authentication(self) -> None:
        status, body, _ = self.request("GET", "/v1/data/documents")
        self.assertEqual(401, status)
        self.assertEqual("invalid_credentials", body["error"]["code"])

    def test_repeated_bad_passwords_return_retry_after(self) -> None:
        status, _, _ = self.request(
            "POST",
            "/v1/setup",
            {
                "email": "owner@example.com",
                "display_name": "AVORA Sahibi",
                "password": "Guvenli-Yonetici-2026!",
            },
            {"X-AVORA-Setup-Token": self.setup_token},
        )
        self.assertEqual(201, status)
        for _ in range(2):
            status, body, _ = self.request(
                "POST",
                "/v1/auth/login",
                {"email": "owner@example.com", "password": "wrong-password"},
            )
            self.assertEqual(401, status)
            self.assertEqual("invalid_credentials", body["error"]["code"])
        status, body, headers = self.request(
            "POST",
            "/v1/auth/login",
            {"email": "owner@example.com", "password": "wrong-password"},
        )
        self.assertEqual(429, status)
        self.assertEqual("rate_limited", body["error"]["code"])
        self.assertGreaterEqual(int(headers["Retry-After"]), 1)

    def test_account_security_endpoints(self) -> None:
        first = self.setup_and_login()
        status, second_body, _ = self.request(
            "POST",
            "/v1/auth/login",
            {"email": "owner@example.com", "password": "Guvenli-Yonetici-2026!"},
        )
        self.assertEqual(200, status)
        second = second_body["access_token"]
        status, body, _ = self.request(
            "POST",
            "/v1/account/password",
            {
                "current_password": "Guvenli-Yonetici-2026!",
                "new_password": "Yeni-Guvenli-Parola-2026!",
            },
            {"Authorization": f"Bearer {first}"},
        )
        self.assertEqual(200, status)
        self.assertTrue(body["password_changed"])
        self.assertEqual(1, body["revoked_sessions"])
        status, _, _ = self.request(
            "GET", "/v1/me", headers={"Authorization": f"Bearer {first}"}
        )
        self.assertEqual(200, status)
        status, body, _ = self.request(
            "GET", "/v1/me", headers={"Authorization": f"Bearer {second}"}
        )
        self.assertEqual(401, status)
        self.assertEqual("invalid_credentials", body["error"]["code"])
        status, _, _ = self.request(
            "POST",
            "/v1/auth/login",
            {"email": "owner@example.com", "password": "Yeni-Guvenli-Parola-2026!"},
        )
        self.assertEqual(200, status)

    def test_session_devices_are_visible_only_to_the_same_account(self) -> None:
        first = self.setup_and_login()
        first_auth = {"Authorization": f"Bearer {first}"}
        status, identified, _ = self.request(
            "POST",
            "/v1/account/session/device",
            {"device_id": "android-phone", "device_name": "Samsung Galaxy S24"},
            first_auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(identified["updated"])
        status, second, _ = self.request(
            "POST",
            "/v1/auth/login",
            {
                "email": "owner@example.com",
                "password": "Guvenli-Yonetici-2026!",
                "device_id": "android-tablet",
                "device_name": "Samsung Galaxy Tab",
            },
        )
        self.assertEqual(200, status)

        status, body, _ = self.request(
            "GET", "/v1/account/sessions", headers=first_auth
        )
        self.assertEqual(200, status)
        self.assertEqual(2, len(body["sessions"]))
        self.assertTrue(body["sessions"][0]["current"])
        self.assertEqual("Samsung Galaxy S24", body["sessions"][0]["device_name"])
        self.assertEqual("Samsung Galaxy Tab", body["sessions"][1]["device_name"])
        self.assertTrue(all("token_hash" not in item for item in body["sessions"]))

        status, second_view, _ = self.request(
            "GET",
            "/v1/account/sessions",
            headers={"Authorization": f"Bearer {second['access_token']}"},
        )
        self.assertEqual(200, status)
        self.assertTrue(second_view["sessions"][0]["current"])
        self.assertEqual("Samsung Galaxy Tab", second_view["sessions"][0]["device_name"])

    def test_wrong_current_password_returns_specific_error(self) -> None:
        token = self.setup_and_login()
        status, body, _ = self.request(
            "POST",
            "/v1/account/password",
            {"current_password": "Yanlis-Parola-2026!", "new_password": "Yeni-Guvenli-Parola-2026!"},
            {"Authorization": f"Bearer {token}"},
        )
        self.assertEqual(400, status)
        self.assertEqual("current_password_invalid", body["error"]["code"])

    def test_invited_registration_returns_session(self) -> None:
        admin_token = self.setup_and_login()
        status, invite, _ = self.request(
            "POST",
            "/v1/admin/invites",
            {"valid_hours": 24, "max_uses": 1},
            {"Authorization": f"Bearer {admin_token}"},
        )
        self.assertEqual(201, status)
        status, body, _ = self.request(
            "POST",
            "/v1/auth/register",
            {
                "invite_code": invite["invite_code"],
                "email": "family@example.com",
                "display_name": "Aile Üyesi",
                "password": "Aile-Uyesi-2026!",
            },
        )
        self.assertEqual(201, status)
        self.assertEqual("Bearer", body["token_type"])
        self.assertEqual("user", body["user"]["role"])
        status, me, _ = self.request(
            "GET",
            "/v1/me",
            headers={"Authorization": f"Bearer {body['access_token']}"},
        )
        self.assertEqual(200, status)
        self.assertEqual(body["user"]["id"], me["user"]["id"])

    def test_admin_account_list_hides_credentials_and_rejects_family_user(self) -> None:
        admin_token = self.setup_and_login()
        admin_auth = {"Authorization": f"Bearer {admin_token}"}
        status, invite, _ = self.request(
            "POST", "/v1/admin/invites", {"valid_hours": 24, "max_uses": 1}, admin_auth
        )
        self.assertEqual(201, status)
        status, family, _ = self.request(
            "POST",
            "/v1/auth/register",
            {
                "invite_code": invite["invite_code"],
                "email": "family@example.com",
                "display_name": "Aile Üyesi",
                "password": "Aile-Uyesi-2026!",
            },
        )
        self.assertEqual(201, status)

        status, body, _ = self.request(
            "GET", "/v1/admin/accounts", headers=admin_auth
        )
        self.assertEqual(200, status)
        self.assertEqual(2, len(body["accounts"]))
        self.assertEqual(
            ["owner@example.com", "family@example.com"],
            [item["email"] for item in body["accounts"]],
        )
        self.assertTrue(all(item["active_sessions"] >= 1 for item in body["accounts"]))
        self.assertTrue(all("password_hash" not in item for item in body["accounts"]))
        self.assertTrue(all("token_hash" not in item for item in body["accounts"]))

        family_auth = {"Authorization": f"Bearer {family['access_token']}"}
        status, forbidden, _ = self.request(
            "GET", "/v1/admin/accounts", headers=family_auth
        )
        self.assertEqual(403, status)
        self.assertEqual("forbidden", forbidden["error"]["code"])

    def test_family_access_request_requires_admin_approval(self) -> None:
        admin_token = self.setup_and_login()
        admin_auth = {"Authorization": f"Bearer {admin_token}"}
        status, invite, _ = self.request(
            "POST", "/v1/admin/invites", {"valid_hours": 24, "max_uses": 1}, admin_auth
        )
        self.assertEqual(201, status)
        status, registered, _ = self.request(
            "POST",
            "/v1/auth/register",
            {
                "invite_code": invite["invite_code"],
                "email": "family@example.com",
                "display_name": "Aile Üyesi",
                "password": "Aile-Uyesi-2026!",
            },
        )
        self.assertEqual(201, status)
        family_auth = {
            "Authorization": f"Bearer {registered['access_token']}"
        }
        status, requested, _ = self.request(
            "POST",
            "/v1/access-requests",
            {
                "device_id": "avora-001",
                "firebase_uid": "familyFirebaseUser_001",
            },
            family_auth,
        )
        self.assertEqual(200, status)
        request_id = requested["access_request"]["id"]
        self.assertEqual("pending", requested["access_request"]["status"])

        status, _, _ = self.request(
            "GET", "/v1/admin/access-requests?device_id=avora-001", headers=family_auth
        )
        self.assertEqual(403, status)
        status, pending, _ = self.request(
            "GET", "/v1/admin/access-requests?device_id=avora-001", headers=admin_auth
        )
        self.assertEqual(200, status)
        self.assertEqual([request_id], [item["id"] for item in pending["access_requests"]])

        status, approved, _ = self.request(
            "POST",
            "/v1/admin/access-requests/approve",
            {"request_id": request_id},
            admin_auth,
        )
        self.assertEqual(200, status)
        self.assertEqual("approved", approved["access_request"]["status"])

        status, accounts, _ = self.request(
            "GET", "/v1/admin/accounts?device_id=avora-001", headers=admin_auth
        )
        self.assertEqual(200, status)
        family_account = next(
            item for item in accounts["accounts"] if item["role"] == "user"
        )
        self.assertEqual("approved", family_account["access_status"])
        self.assertEqual("familyFirebaseUser_001", family_account["firebase_uid"])
        self.assertIn("inactive_access", family_account)

        status, kept, _ = self.request(
            "POST",
            "/v1/admin/inactive-access/keep",
            {"user_id": family_account["id"], "device_id": "avora-001"},
            admin_auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(kept["kept"])

        status, revoked, _ = self.request(
            "POST",
            "/v1/admin/device-access/revoke",
            {"user_id": family_account["id"], "device_id": "avora-001"},
            admin_auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(revoked["revoked"])
        self.assertEqual("familyFirebaseUser_001", revoked["firebase_uid"])
        status, body, _ = self.request("GET", "/v1/me", headers=family_auth)
        self.assertEqual(401, status)
        self.assertEqual("invalid_credentials", body["error"]["code"])

    def test_admin_can_revoke_unused_invitation(self) -> None:
        token = self.setup_and_login()
        auth = {"Authorization": f"Bearer {token}"}
        status, invite, _ = self.request(
            "POST", "/v1/admin/invites", {"valid_hours": 24, "max_uses": 1}, auth
        )
        self.assertEqual(201, status)
        status, body, _ = self.request(
            "POST",
            "/v1/admin/invites/revoke",
            {"invite_code": invite["invite_code"]},
            auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(body["revoked"])
        status, body, _ = self.request(
            "POST",
            "/v1/auth/register",
            {"invite_code": invite["invite_code"], "email": "family@example.com", "display_name": "Aile Üyesi", "password": "Aile-Uyesi-2026!"},
        )
        self.assertEqual(400, status)
        self.assertEqual("invalid_invite", body["error"]["code"])

    def test_admin_can_disable_and_restore_family_account(self) -> None:
        admin_token = self.setup_and_login()
        admin_auth = {"Authorization": f"Bearer {admin_token}"}
        status, invite, _ = self.request(
            "POST", "/v1/admin/invites", {"valid_hours": 24}, admin_auth
        )
        self.assertEqual(201, status)
        status, family, _ = self.request(
            "POST",
            "/v1/auth/register",
            {
                "invite_code": invite["invite_code"],
                "email": "managed@example.com",
                "display_name": "Yönetilen Aile Üyesi",
                "password": "Aile-Uyesi-2026!",
            },
        )
        self.assertEqual(201, status)
        user_id = family["user"]["id"]
        family_auth = {"Authorization": f"Bearer {family['access_token']}"}

        status, disabled, _ = self.request(
            "POST", "/v1/admin/accounts/disable",
            {"user_id": user_id}, admin_auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(disabled["disabled"])
        self.assertGreater(disabled["delete_eligible_at"], 0)
        status, _, _ = self.request("GET", "/v1/me", headers=family_auth)
        self.assertEqual(401, status)

        status, accounts, _ = self.request(
            "GET", "/v1/admin/accounts?device_id=avora-001", headers=admin_auth
        )
        self.assertEqual(200, status)
        target = next(item for item in accounts["accounts"] if item["id"] == user_id)
        self.assertFalse(target["active"])
        self.assertTrue(target["can_restore"])

        status, restored, _ = self.request(
            "POST", "/v1/admin/accounts/restore",
            {"user_id": user_id}, admin_auth,
        )
        self.assertEqual(200, status)
        self.assertTrue(restored["restored"])

        status, _, _ = self.request(
            "POST", "/v1/admin/accounts/disable",
            {"user_id": user_id}, admin_auth,
        )
        self.assertEqual(200, status)
        status, wrong_password, _ = self.request(
            "POST", "/v1/admin/accounts/delete",
            {"user_id": user_id, "current_password": "wrong-password"},
            admin_auth,
        )
        self.assertEqual(400, status)
        self.assertEqual(
            "current_password_invalid", wrong_password["error"]["code"]
        )
        status, too_early, _ = self.request(
            "POST", "/v1/admin/accounts/delete",
            {
                "user_id": user_id,
                "current_password": "Guvenli-Yonetici-2026!",
            },
            admin_auth,
        )
        self.assertEqual(409, status)
        self.assertEqual("account_state_conflict", too_early["error"]["code"])

    def test_authenticated_document_round_trip(self) -> None:
        token = self.setup_and_login()
        headers = {"Authorization": f"Bearer {token}"}
        status, body, _ = self.request(
            "PUT",
            "/v1/data/documents/profile",
            {"data": {"garden": "AVORA"}, "expected_version": 0},
            headers,
        )
        self.assertEqual(200, status)
        self.assertEqual(1, body["document"]["version"])
        status, body, _ = self.request("GET", "/v1/data/documents/profile", headers=headers)
        self.assertEqual(200, status)
        self.assertEqual("AVORA", body["document"]["data"]["garden"])

    def test_jpeg_upload_and_download(self) -> None:
        token = self.setup_and_login()
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "image/jpeg",
        }
        jpeg = b"\xff\xd8safe-jpeg\xff\xd9"
        status, body, _ = self.request("PUT", "/v1/photos/photo_1", jpeg, headers)
        self.assertEqual(200, status)
        self.assertEqual(len(jpeg), body["photo"]["size_bytes"])
        status, body, headers = self.request(
            "GET", "/v1/photos/photo_1", headers={"Authorization": f"Bearer {token}"}
        )
        self.assertEqual(200, status)
        self.assertEqual(jpeg, body)
        self.assertEqual("image/jpeg", headers["Content-Type"])

    def test_android_compatible_photo_metadata_update(self) -> None:
        token = self.setup_and_login()
        auth = {"Authorization": f"Bearer {token}"}
        jpeg_headers = {**auth, "Content-Type": "image/jpeg"}
        status, _, _ = self.request(
            "PUT", "/v1/photos/photo_1", b"\xff\xd8safe-jpeg\xff\xd9", jpeg_headers
        )
        self.assertEqual(200, status)
        status, body, _ = self.request(
            "POST",
            "/v1/photos/photo_1/metadata",
            {"metadata": {"zone_id": "zone-1", "note": "Fide"}},
            auth,
        )
        self.assertEqual(200, status)
        self.assertEqual("zone-1", body["photo"]["metadata"]["zone_id"])
        status, body, _ = self.request("GET", "/v1/photos", headers=auth)
        self.assertEqual(200, status)
        self.assertEqual("Fide", body["photos"][0]["metadata"]["note"])


if __name__ == "__main__":
    unittest.main()
