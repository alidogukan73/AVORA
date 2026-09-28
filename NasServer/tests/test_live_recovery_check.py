import contextlib
import io
import tempfile
import unittest
from email.message import EmailMessage
from pathlib import Path
from unittest.mock import patch

from avora_nas import server
from avora_nas.config import Settings
from verify_password_recovery import extract_code, run_contract


class LiveRecoveryCheckTest(unittest.TestCase):
    def message(self, recipient, content):
        message = EmailMessage()
        message["To"] = recipient
        message.set_content(content)
        return message.as_bytes()

    def test_reads_only_exact_recipient_and_single_code(self):
        code = "A" * 32
        raw = self.message("owner+avora-check-test@gmail.com", "Your code:\n\n" + code + "\n\nExpires soon")
        self.assertEqual(code, extract_code(raw, "owner+avora-check-test@gmail.com"))
        with self.assertRaises(ValueError):
            extract_code(raw, "owner@gmail.com")
        with self.assertRaises(ValueError):
            extract_code(self.message("owner@gmail.com", code + "\n" + "B" * 32), "owner@gmail.com")

    def test_contract_really_resets_password_and_revokes_sessions(self):
        with tempfile.TemporaryDirectory() as temporary:
            settings = Settings(data_dir=Path(temporary), setup_token="S" * 48,
                                smtp_host="smtp.example.com", smtp_from="sender@example.com")
            with patch("avora_nas.recovery.send_recovery_email") as send, contextlib.redirect_stdout(io.StringIO()) as output:
                def read_code(_settings, recipient):
                    server.SERVICE.recovery._queue.join()
                    self.assertEqual(recipient, send.call_args.args[1])
                    return send.call_args.args[2]
                result = run_contract(settings, "test@example.com", read_code)
            self.assertTrue(result["success"])
            self.assertTrue(result["reused_code_rejected"])
            self.assertTrue(result["old_password_rejected"])
            self.assertTrue(result["old_sessions_revoked"])
            self.assertTrue(result["new_password_login"])
            self.assertFalse(result["production_account_password_changed"])
            self.assertNotIn(send.call_args.args[2], output.getvalue())
            self.assertIsNone(server.SERVICE)

    def test_mail_failure_stops_before_password_reset_and_cleans_server(self):
        with tempfile.TemporaryDirectory() as temporary:
            settings = Settings(data_dir=Path(temporary), setup_token="S" * 48,
                                smtp_host="smtp.example.com", smtp_from="sender@example.com")
            with patch("avora_nas.recovery.send_recovery_email"), contextlib.redirect_stdout(io.StringIO()) as output:
                def no_mail(_settings, _recipient):
                    raise TimeoutError("not delivered")
                with self.assertRaises(TimeoutError):
                    run_contract(settings, "test@example.com", no_mail)
            self.assertNotIn("STEP: reset_password", output.getvalue())
            self.assertIsNone(server.SERVICE)


if __name__ == "__main__":
    unittest.main()
