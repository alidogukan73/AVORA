"""Runtime configuration for the lightweight AVORA NAS API."""

from __future__ import annotations

import os
import secrets
import sqlite3
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path


class ConfigurationError(RuntimeError):
    """Raised when a security-sensitive setting is invalid."""


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    host: str = "0.0.0.0"
    port: int = 8787
    session_hours: int = 24
    setup_token: str = ""
    max_json_bytes: int = 2 * 1024 * 1024
    max_photo_bytes: int = 20 * 1024 * 1024
    login_window_seconds: int = 10 * 60
    login_block_seconds: int = 15 * 60
    login_account_attempts: int = 5
    login_source_attempts: int = 60

    @property
    def database_dir(self) -> Path:
        return self.data_dir / "database"

    @property
    def tenant_database_dir(self) -> Path:
        return self.database_dir / "users"

    @property
    def photo_dir(self) -> Path:
        return self.data_dir / "photos"

    @property
    def backup_dir(self) -> Path:
        return self.data_dir / "backups"

    @property
    def config_dir(self) -> Path:
        return self.data_dir / "config"

    @property
    def log_dir(self) -> Path:
        return self.data_dir / "logs"

    @property
    def account_database(self) -> Path:
        return self.database_dir / "accounts.sqlite3"

    @property
    def setup_token_file(self) -> Path:
        return self.config_dir / "setup_token.txt"

    def prepare(self) -> "Settings":
        root = self.data_dir.expanduser().resolve()
        if root == Path(root.anchor):
            raise ConfigurationError("AVORA_DATA_DIR cannot be a filesystem root.")
        for folder in (
            self.database_dir,
            self.tenant_database_dir,
            self.photo_dir,
            self.backup_dir,
            self.config_dir,
            self.log_dir,
        ):
            folder.mkdir(parents=True, exist_ok=True)
            if folder.is_symlink():
                raise ConfigurationError(f"Symlink data directories are not allowed: {folder}")
        return self

    @classmethod
    def from_environment(cls) -> "Settings":
        data_dir = Path(os.environ.get("AVORA_DATA_DIR", "/data"))
        host = os.environ.get("AVORA_HOST", "0.0.0.0").strip() or "0.0.0.0"
        port = _bounded_int("AVORA_PORT", 8787, 1, 65535)
        session_hours = _bounded_int("AVORA_SESSION_HOURS", 24, 1, 24 * 30)
        max_json_bytes = _bounded_int(
            "AVORA_MAX_JSON_BYTES", 2 * 1024 * 1024, 64 * 1024, 8 * 1024 * 1024
        )
        max_photo_bytes = _bounded_int(
            "AVORA_MAX_PHOTO_BYTES", 20 * 1024 * 1024, 1024 * 1024, 50 * 1024 * 1024
        )
        login_window_seconds = _bounded_int(
            "AVORA_LOGIN_WINDOW_SECONDS", 10 * 60, 60, 60 * 60
        )
        login_block_seconds = _bounded_int(
            "AVORA_LOGIN_BLOCK_SECONDS", 15 * 60, 60, 24 * 60 * 60
        )
        login_account_attempts = _bounded_int(
            "AVORA_LOGIN_ACCOUNT_ATTEMPTS", 5, 3, 20
        )
        login_source_attempts = _bounded_int(
            "AVORA_LOGIN_SOURCE_ATTEMPTS", 60, 10, 500
        )
        settings = cls(
            data_dir=data_dir,
            host=host,
            port=port,
            session_hours=session_hours,
            max_json_bytes=max_json_bytes,
            max_photo_bytes=max_photo_bytes,
            login_window_seconds=login_window_seconds,
            login_block_seconds=login_block_seconds,
            login_account_attempts=login_account_attempts,
            login_source_attempts=login_source_attempts,
        ).prepare()
        token = os.environ.get("AVORA_SETUP_TOKEN", "").strip()
        if not token:
            token = _read_or_create_setup_token(
                settings.setup_token_file, settings.account_database
            )
        if len(token) < 32:
            raise ConfigurationError("The AVORA setup token must contain at least 32 characters.")
        return cls(
            data_dir=settings.data_dir,
            host=settings.host,
            port=settings.port,
            session_hours=settings.session_hours,
            setup_token=token,
            max_json_bytes=settings.max_json_bytes,
            max_photo_bytes=settings.max_photo_bytes,
            login_window_seconds=settings.login_window_seconds,
            login_block_seconds=settings.login_block_seconds,
            login_account_attempts=settings.login_account_attempts,
            login_source_attempts=settings.login_source_attempts,
        )


def _bounded_int(name: str, default: int, minimum: int, maximum: int) -> int:
    raw = os.environ.get(name, str(default)).strip()
    try:
        value = int(raw)
    except ValueError as exc:
        raise ConfigurationError(f"{name} must be an integer.") from exc
    if not minimum <= value <= maximum:
        raise ConfigurationError(f"{name} must be between {minimum} and {maximum}.")
    return value


def _read_or_create_setup_token(path: Path, account_database: Path) -> str:
    if path.exists():
        if path.is_symlink() or not path.is_file():
            raise ConfigurationError("The setup token path is not a regular file.")
        token = path.read_text(encoding="utf-8").strip()
        if len(token) < 32:
            raise ConfigurationError("The saved setup token is invalid.")
        return token
    if _account_database_has_users(account_database):
        return secrets.token_urlsafe(48)
    token = secrets.token_urlsafe(48)
    path.write_text(token + "\n", encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass
    return token


def _account_database_has_users(path: Path) -> bool:
    if not path.is_file() or path.is_symlink():
        return False
    try:
        uri = f"file:{path.resolve().as_posix()}?mode=ro"
        with closing(sqlite3.connect(uri, uri=True, timeout=2.0)) as connection:
            row = connection.execute(
                "SELECT 1 FROM users WHERE active = 1 LIMIT 1"
            ).fetchone()
        return row is not None
    except sqlite3.Error:
        return False
