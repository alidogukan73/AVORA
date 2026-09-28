import os
import tempfile
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from avora_nas.config import Settings, ConfigurationError
from avora_nas.database import AccountDatabase, InvalidCredentialsError, InvalidResetTokenError, LoginRateLimitError
from avora_nas.recovery import PasswordRecovery, RecoveryUnavailableError, send_recovery_email
from avora_nas.security import PasswordPolicyError, token_digest
from avora_nas.service import AvoraService


class RecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.settings = Settings(data_dir=Path(self.temp.name), setup_token="S" * 48,
                                 smtp_host="smtp.example.com", smtp_from="avora@example.com")
        self.service = AvoraService(self.settings)
        self.db = self.service.accounts
        self.owner = self.service.setup_admin("S" * 48, "owner@example.com", "Owner", "Original-password-2026")
        self.now = int(time.time())

    def test_mail_delivery_is_generic_and_only_hash_is_saved(self):
        with patch("avora_nas.recovery.send_recovery_email") as send:
            for email in (" OWNER@example.com ", "missing@example.com"):
                self.assertIsNone(self.service.recovery.request(email, "test"))
            self.service.recovery._queue.join()
            send.assert_called_once()
            _, email, code = send.call_args.args
            self.assertEqual("owner@example.com", email)
            self.assertEqual(32, len(code))
            with self.db._connect() as conn:
                rows = conn.execute("SELECT * FROM password_resets").fetchall()
            self.assertEqual(1, len(rows))
            self.assertEqual(token_digest(code), rows[0]["token_hash"])

    def test_reset_revokes_all_sessions_and_tokens_but_preserves_data(self):
        one = self.service.login("owner@example.com", "Original-password-2026")
        two = self.service.login("owner@example.com", "Original-password-2026")
        store_path = self.service.tenant_store(self.owner).path
        before = store_path.read_bytes()
        code = self.db.create_password_reset(self.owner.email, self.now)
        other = self.db.create_password_reset(self.owner.email, self.now)
        self.service.recovery.confirm(self.owner.email, code, "Replacement-password-2026", "test")
        for session in (one, two):
            with self.assertRaises(InvalidCredentialsError):
                self.service.authenticate(session.token)
        self.assertEqual(before, store_path.read_bytes())
        self.assertIsNotNone(self.service.login(self.owner.email, "Replacement-password-2026"))
        for invalid in (code, other):
            with self.assertRaises(InvalidResetTokenError):
                self.db.reset_password(self.owner.email, invalid, "Another-password-2026", self.now)

    def test_wrong_email_wrong_code_expired_and_weak_password(self):
        code = self.db.create_password_reset(self.owner.email, self.now)
        for email, token, now in (("missing@example.com", code, self.now),
                                  (self.owner.email, "wrong", self.now),
                                  (self.owner.email, code, self.now + 900)):
            with self.assertRaises(InvalidResetTokenError):
                self.db.reset_password(email, token, "Replacement-password-2026", now)
        with self.assertRaises(PasswordPolicyError):
            self.db.reset_password(self.owner.email, code, "short", self.now)
        self.db.reset_password(self.owner.email, code, "Replacement-password-2026", self.now)

    def test_concurrent_redemption_only_succeeds_once(self):
        code = self.db.create_password_reset(self.owner.email, self.now)
        def redeem(_):
            try:
                self.db.reset_password(self.owner.email, code, "Replacement-password-2026", self.now)
                return True
            except InvalidResetTokenError:
                return False
        with ThreadPoolExecutor(max_workers=2) as pool:
            self.assertEqual(1, sum(pool.map(redeem, range(2))))

    def test_change_password_invalidates_recovery(self):
        session = self.service.login(self.owner.email, "Original-password-2026")
        code = self.db.create_password_reset(self.owner.email, self.now)
        self.db.change_password(self.owner, session.token, "Original-password-2026", "Replacement-password-2026")
        with self.assertRaises(InvalidResetTokenError):
            self.db.reset_password(self.owner.email, code, "Another-password-2026", self.now)

    def test_disabled_account_cannot_request_or_redeem(self):
        code = self.db.create_password_reset(self.owner.email, self.now)
        with self.db._connect() as conn:
            conn.execute("UPDATE users SET active = 0 WHERE id = ?", (self.owner.id,))
        self.assertIsNone(self.db.create_password_reset(self.owner.email, self.now))
        with self.assertRaises(InvalidResetTokenError):
            self.db.reset_password(self.owner.email, code, "Replacement-password-2026", self.now)

    def test_persistent_account_and_source_limits_do_not_block_login(self):
        with patch("avora_nas.recovery.send_recovery_email"):
            for _ in range(3):
                self.service.recovery.request(self.owner.email, "source", self.now)
            self.service.recovery._queue.join()
        restarted = PasswordRecovery(self.settings, AccountDatabase(self.settings.account_database))
        with self.assertRaises(LoginRateLimitError):
            restarted.request(self.owner.email.upper(), "another", self.now)
        for index in range(30):
            with self.assertRaises(InvalidResetTokenError):
                restarted.confirm(f"missing{index}@example.com", "wrong", "Password-for-test-2026", "source", self.now)
        with self.assertRaises(LoginRateLimitError):
            restarted.confirm("different@example.com", "wrong", "Password-for-test-2026", "source", self.now)
        self.assertIsNotNone(self.service.login(self.owner.email, "Original-password-2026"))

    def test_unconfigured_and_failed_mail_delivery(self):
        disabled = PasswordRecovery(replace(self.settings, smtp_host=""), self.db)
        with self.assertRaises(RecoveryUnavailableError):
            disabled.request(self.owner.email, "test")
        with patch("avora_nas.recovery.send_recovery_email", side_effect=OSError("private details")):
            with self.assertLogs("avora_nas", level="ERROR") as logs:
                self.service.recovery.request(self.owner.email, "test")
                self.service.recovery._queue.join()
            self.assertNotIn("private details", " ".join(logs.output))
        with self.db._connect() as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) FROM password_resets").fetchone()[0])

    def test_smtp_uses_verified_tls_before_authentication(self):
        settings = replace(self.settings, smtp_username="sender", smtp_password="test-secret")
        with patch("avora_nas.recovery.smtplib.SMTP") as smtp:
            send_recovery_email(settings, self.owner.email, "test-code")
            client = smtp.return_value
            self.assertTrue(client.starttls.called)
            self.assertTrue(client.login.called)
            self.assertTrue(client.send_message.called)
            self.assertLess([c[0] for c in client.mock_calls].index("starttls"),
                            [c[0] for c in client.mock_calls].index("login"))
        with patch("avora_nas.recovery.smtplib.SMTP_SSL") as smtp:
            send_recovery_email(replace(settings, smtp_security="ssl", smtp_port=465), self.owner.email, "test-code")
            self.assertIn("context", smtp.call_args.kwargs)
        with self.assertRaises(ConfigurationError):
            replace(settings, smtp_security="none").prepare()

    def test_environment_preserves_smtp_config_without_exposing_password(self):
        with patch.dict(os.environ, {
            "AVORA_DATA_DIR": self.temp.name, "AVORA_SETUP_TOKEN": "S" * 48,
            "AVORA_SMTP_HOST": "smtp.example.com", "AVORA_SMTP_PORT": "465",
            "AVORA_SMTP_SECURITY": "ssl", "AVORA_SMTP_FROM": "sender@example.com",
            "AVORA_SMTP_USERNAME": "sender", "AVORA_SMTP_PASSWORD": "smtp-test-secret",
        }, clear=True):
            settings = Settings.from_environment()
        self.assertEqual("smtp.example.com", settings.smtp_host)
        self.assertEqual(465, settings.smtp_port)
        self.assertEqual("ssl", settings.smtp_security)
        self.assertEqual("smtp-test-secret", settings.smtp_password)
        self.assertNotIn("smtp-test-secret", repr(settings))

    def test_smtp_rejects_invalid_sender_and_partial_credentials(self):
        for settings in (replace(self.settings, smtp_from="invalid"),
                         replace(self.settings, smtp_username="sender", smtp_password="")):
            with self.assertRaises(ConfigurationError):
                settings.prepare()


if __name__ == "__main__":
    unittest.main()
