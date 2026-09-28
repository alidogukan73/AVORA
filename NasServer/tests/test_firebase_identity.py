import sys
import unittest
from dataclasses import replace
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from avora_nas.config import Settings
from avora_nas.database import User
from avora_nas.firebase_identity import FirebaseIdentity, FirebaseIdentityUnavailableError, owner_uid


class FirebaseIdentityTest(unittest.TestCase):
    def setUp(self):
        self.settings = Settings(data_dir=Path("unused"), firebase_credentials_file="test-key.json",
                                 firebase_device_id="avora-001")
        self.user = User("b05cbe3d-b3d5-47ee-90d3-d9b413555633", "owner@example.com", "Owner", "admin")
        self.sdk = MagicMock()
        self.sdk.auth.UserNotFoundError = type("UserNotFoundError", (Exception,), {})
        self.sdk.auth.get_user.return_value = SimpleNamespace(disabled=False, custom_claims={"existing": True})
        self.sdk.auth.create_custom_token.return_value = b"test-custom-token"

    def test_stable_identity_survives_reinstall_and_retains_unrelated_claims(self):
        with patch.dict(sys.modules, {"firebase_admin": self.sdk}):
            first = FirebaseIdentity(self.settings).create_owner_session(self.user)
            second = FirebaseIdentity(self.settings).create_owner_session(self.user)
        self.assertEqual(first["firebase_uid"], second["firebase_uid"])
        self.assertEqual("avora_nas_b05cbe3db3d547ee90d3d9b413555633", first["firebase_uid"])
        self.assertEqual("avora-001", first["device_id"])
        args = self.sdk.auth.set_custom_user_claims.call_args.args
        self.assertEqual({"existing": True, "avora_device_id": "avora-001"}, args[1])
        self.assertEqual(first["firebase_uid"], self.sdk.auth.create_custom_token.call_args.args[0])

    def test_family_account_cannot_receive_owner_token(self):
        with self.assertRaises(PermissionError):
            FirebaseIdentity(self.settings).create_owner_session(replace(self.user, role="user"))

    def test_disabled_and_conflicting_device_accounts_are_not_overwritten(self):
        for account in (SimpleNamespace(disabled=True, custom_claims={}),
                        SimpleNamespace(disabled=False, custom_claims={"avora_device_id": "other"})):
            self.sdk.auth.get_user.return_value = account
            with patch.dict(sys.modules, {"firebase_admin": self.sdk}):
                with self.assertRaises(PermissionError):
                    FirebaseIdentity(self.settings).create_owner_session(self.user)
        self.sdk.auth.set_custom_user_claims.assert_not_called()
        self.sdk.auth.create_custom_token.assert_not_called()

    def test_first_login_creates_account_and_existing_owner_is_idempotent(self):
        self.sdk.auth.get_user.side_effect = self.sdk.auth.UserNotFoundError()
        self.sdk.auth.create_user.return_value = SimpleNamespace(disabled=False, custom_claims={})
        with patch.dict(sys.modules, {"firebase_admin": self.sdk}):
            FirebaseIdentity(self.settings).create_owner_session(self.user)
        self.sdk.auth.create_user.assert_called_once()
        self.sdk.reset_mock()
        self.sdk.auth.get_user.side_effect = None
        self.sdk.auth.get_user.return_value = SimpleNamespace(disabled=False, custom_claims={"avora_device_id": "avora-001"})
        with patch.dict(sys.modules, {"firebase_admin": self.sdk}):
            FirebaseIdentity(self.settings).create_owner_session(self.user)
        self.sdk.auth.set_custom_user_claims.assert_not_called()

    def test_missing_configuration_and_sdk_errors_fail_closed(self):
        with self.assertRaises(FirebaseIdentityUnavailableError):
            FirebaseIdentity(replace(self.settings, firebase_credentials_file="")).create_owner_session(self.user)
        self.sdk.auth.get_user.side_effect = RuntimeError("private key details")
        with patch.dict(sys.modules, {"firebase_admin": self.sdk}):
            with self.assertRaises(FirebaseIdentityUnavailableError) as error:
                FirebaseIdentity(self.settings).create_owner_session(self.user)
        self.assertNotIn("private key", str(error.exception))
        self.sdk.auth.create_custom_token.assert_not_called()


if __name__ == "__main__":
    unittest.main()
