from __future__ import annotations

import unittest

from avora_nas.security import PasswordPolicyError, hash_password, verify_password


class PasswordSecurityTest(unittest.TestCase):
    def test_password_is_salted_and_never_stored_as_plain_text(self) -> None:
        password = "Dostum-Guvenli-2026!"
        first = hash_password(password)
        second = hash_password(password)

        self.assertNotEqual(first, second)
        self.assertNotIn(password, first)
        self.assertTrue(verify_password(password, first))
        self.assertFalse(verify_password("yanlis-parola", first))

    def test_short_password_is_rejected(self) -> None:
        with self.assertRaises(PasswordPolicyError):
            hash_password("kisa")

    def test_invalid_hash_is_rejected_without_error(self) -> None:
        self.assertFalse(verify_password("anything", "not-a-password-hash"))


if __name__ == "__main__":
    unittest.main()

