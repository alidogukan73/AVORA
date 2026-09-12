"""Password and opaque-token helpers built only on Python's standard library."""

from __future__ import annotations

import base64
import hashlib
import hmac
import re
import secrets


PASSWORD_MIN_LENGTH = 12
PASSWORD_MAX_LENGTH = 128
SCRYPT_N = 1 << 15
SCRYPT_R = 8
SCRYPT_P = 1
SCRYPT_MAX_MEMORY = 64 * 1024 * 1024
_EMAIL_PATTERN = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


class PasswordPolicyError(ValueError):
    """Raised when a password does not meet the server policy."""


def validate_password(password: str) -> None:
    if not isinstance(password, str):
        raise PasswordPolicyError("Password must be text.")
    if len(password) < PASSWORD_MIN_LENGTH:
        raise PasswordPolicyError(
            f"Password must contain at least {PASSWORD_MIN_LENGTH} characters."
        )
    if len(password) > PASSWORD_MAX_LENGTH:
        raise PasswordPolicyError(
            f"Password cannot exceed {PASSWORD_MAX_LENGTH} characters."
        )
    if any(ord(character) < 32 for character in password):
        raise PasswordPolicyError("Password cannot contain control characters.")


def hash_password(password: str) -> str:
    validate_password(password)
    salt = secrets.token_bytes(16)
    digest = hashlib.scrypt(
        password.encode("utf-8"),
        salt=salt,
        n=SCRYPT_N,
        r=SCRYPT_R,
        p=SCRYPT_P,
        maxmem=SCRYPT_MAX_MEMORY,
        dklen=32,
    )
    return "$".join(
        (
            "scrypt",
            str(SCRYPT_N),
            str(SCRYPT_R),
            str(SCRYPT_P),
            _encode(salt),
            _encode(digest),
        )
    )


def verify_password(password: str, encoded: str) -> bool:
    try:
        algorithm, raw_n, raw_r, raw_p, raw_salt, raw_digest = encoded.split("$")
        if algorithm != "scrypt":
            return False
        n, r, p = int(raw_n), int(raw_r), int(raw_p)
        if n < (1 << 14) or n > (1 << 18) or r < 1 or r > 16 or p < 1 or p > 4:
            return False
        salt = _decode(raw_salt)
        expected = _decode(raw_digest)
        if len(salt) < 16 or len(expected) != 32:
            return False
        actual = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=n,
            r=r,
            p=p,
            maxmem=SCRYPT_MAX_MEMORY,
            dklen=len(expected),
        )
        return hmac.compare_digest(actual, expected)
    except (AttributeError, TypeError, ValueError):
        return False


def normalize_email(email: str) -> str:
    normalized = email.strip().lower() if isinstance(email, str) else ""
    if len(normalized) > 254 or not _EMAIL_PATTERN.fullmatch(normalized):
        raise ValueError("A valid email address is required.")
    return normalized


def normalize_display_name(display_name: str) -> str:
    normalized = " ".join(display_name.split()) if isinstance(display_name, str) else ""
    if len(normalized) < 2 or len(normalized) > 80:
        raise ValueError("Display name must contain between 2 and 80 characters.")
    if any(ord(character) < 32 for character in normalized):
        raise ValueError("Display name contains invalid characters.")
    return normalized


def new_secret(byte_count: int = 32) -> str:
    return secrets.token_urlsafe(byte_count)


def token_digest(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def constant_time_equal(left: str, right: str) -> bool:
    return hmac.compare_digest(left.encode("utf-8"), right.encode("utf-8"))


def _encode(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def _decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)
