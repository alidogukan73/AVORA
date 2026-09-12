"""SQLite persistence with one physical data database per AVORA user."""

from __future__ import annotations

import json
import sqlite3
import time
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator

from .security import (
    hash_password,
    new_secret,
    normalize_display_name,
    normalize_email,
    token_digest,
    validate_password,
    verify_password,
)


class DataConflictError(RuntimeError):
    """Raised when a write uses an outdated version."""


class InvalidCredentialsError(RuntimeError):
    """Raised for failed authentication without revealing the exact reason."""


class InvalidCurrentPasswordError(RuntimeError):
    """Raised when an authenticated user cannot confirm the current password."""


class PasswordUnchangedError(RuntimeError):
    """Raised when a password change would keep the existing password."""


class LoginRateLimitError(RuntimeError):
    """Raised when authentication attempts must be delayed."""

    def __init__(self, retry_after: int) -> None:
        super().__init__("Too many login attempts.")
        self.retry_after = max(1, int(retry_after))


class InviteError(RuntimeError):
    """Raised when an invitation cannot be used."""


class SetupCompleteError(RuntimeError):
    """Raised when bootstrap is attempted after an account already exists."""


@dataclass(frozen=True)
class User:
    id: str
    email: str
    display_name: str
    role: str


@dataclass(frozen=True)
class Session:
    token: str
    expires_at: int
    user: User


@dataclass(frozen=True)
class Document:
    key: str
    data: Any
    version: int
    updated_at: int


@dataclass(frozen=True)
class PhotoRecord:
    id: str
    sha256: str
    size_bytes: int
    metadata: dict[str, Any]
    created_at: int
    updated_at: int


@dataclass(frozen=True)
class AccessRequest:
    id: str
    device_id: str
    user_id: str
    firebase_uid: str
    email: str
    display_name: str
    status: str
    created_at: int
    updated_at: int


class AccountDatabase:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=10.0)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA busy_timeout = 10000")
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def _initialize(self) -> None:
        with self._connect() as connection:
            connection.execute("PRAGMA journal_mode = WAL")
            connection.execute("PRAGMA synchronous = NORMAL")
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS users (
                    id TEXT PRIMARY KEY,
                    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    display_name TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL CHECK (role IN ('admin', 'user')),
                    active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS sessions (
                    token_hash TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);
                CREATE TABLE IF NOT EXISTS invites (
                    code_hash TEXT PRIMARY KEY,
                    created_by TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    max_uses INTEGER NOT NULL CHECK (max_uses BETWEEN 1 AND 100),
                    uses INTEGER NOT NULL DEFAULT 0 CHECK (uses >= 0)
                );
                CREATE INDEX IF NOT EXISTS invites_expiry_idx ON invites(expires_at);
                CREATE TABLE IF NOT EXISTS access_requests (
                    id TEXT PRIMARY KEY,
                    device_id TEXT NOT NULL,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    firebase_uid TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'pending'
                        CHECK (status IN ('pending', 'approved')),
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    UNIQUE(device_id, user_id)
                );
                CREATE INDEX IF NOT EXISTS access_requests_status_idx
                    ON access_requests(device_id, status, created_at);
                CREATE UNIQUE INDEX IF NOT EXISTS access_requests_firebase_uid_idx
                    ON access_requests(device_id, firebase_uid);
                CREATE TABLE IF NOT EXISTS login_throttle (
                    bucket_hash TEXT PRIMARY KEY,
                    failures INTEGER NOT NULL CHECK (failures > 0),
                    window_started_at INTEGER NOT NULL,
                    blocked_until INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                );
                CREATE INDEX IF NOT EXISTS login_throttle_updated_idx
                    ON login_throttle(updated_at);
                """
            )

    def has_users(self) -> bool:
        with self._connect() as connection:
            row = connection.execute("SELECT 1 FROM users LIMIT 1").fetchone()
        return row is not None

    def create_initial_admin(
        self, email: str, display_name: str, password: str, now: int | None = None
    ) -> User:
        timestamp = int(time.time()) if now is None else int(now)
        normalized_email = normalize_email(email)
        normalized_name = normalize_display_name(display_name)
        encoded_password = hash_password(password)
        user = User(str(uuid.uuid4()), normalized_email, normalized_name, "admin")
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            if connection.execute("SELECT 1 FROM users LIMIT 1").fetchone() is not None:
                raise SetupCompleteError("AVORA has already been initialized.")
            connection.execute(
                """INSERT INTO users
                   (id, email, display_name, password_hash, role, active, created_at)
                   VALUES (?, ?, ?, ?, 'admin', 1, ?)""",
                (user.id, user.email, user.display_name, encoded_password, timestamp),
            )
        return user

    def create_session(
        self, email: str, password: str, ttl_seconds: int, now: int | None = None
    ) -> Session:
        timestamp = int(time.time()) if now is None else int(now)
        try:
            normalized_email = normalize_email(email)
        except ValueError as exc:
            raise InvalidCredentialsError("Invalid email or password.") from exc
        with self._connect() as connection:
            row = connection.execute(
                """SELECT id, email, display_name, password_hash, role, active
                   FROM users WHERE email = ?""",
                (normalized_email,),
            ).fetchone()
        if row is None or not row["active"] or not verify_password(password, row["password_hash"]):
            raise InvalidCredentialsError("Invalid email or password.")
        token = new_secret(32)
        expires_at = timestamp + ttl_seconds
        with self._connect() as connection:
            connection.execute("DELETE FROM sessions WHERE expires_at <= ?", (timestamp,))
            connection.execute(
                "INSERT INTO sessions(token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                (token_digest(token), row["id"], timestamp, expires_at),
            )
        return Session(token, expires_at, _row_to_user(row))

    def create_session_for_user(
        self, user: User, ttl_seconds: int, now: int | None = None
    ) -> Session:
        timestamp = int(time.time()) if now is None else int(now)
        token = new_secret(32)
        expires_at = timestamp + ttl_seconds
        with self._connect() as connection:
            connection.execute("DELETE FROM sessions WHERE expires_at <= ?", (timestamp,))
            connection.execute(
                "INSERT INTO sessions(token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                (token_digest(token), user.id, timestamp, expires_at),
            )
        return Session(token, expires_at, user)

    def authenticate(self, token: str, now: int | None = None) -> User:
        timestamp = int(time.time()) if now is None else int(now)
        if not isinstance(token, str) or len(token) < 32 or len(token) > 256:
            raise InvalidCredentialsError("Authentication is required.")
        with self._connect() as connection:
            row = connection.execute(
                """SELECT u.id, u.email, u.display_name, u.role, u.active, s.expires_at
                   FROM sessions s JOIN users u ON u.id = s.user_id
                   WHERE s.token_hash = ?""",
                (token_digest(token),),
            ).fetchone()
            if row is None or not row["active"] or row["expires_at"] <= timestamp:
                if row is not None:
                    connection.execute(
                        "DELETE FROM sessions WHERE token_hash = ?", (token_digest(token),)
                    )
                raise InvalidCredentialsError("Authentication is required.")
        return _row_to_user(row)

    def logout(self, token: str) -> None:
        if not isinstance(token, str) or not token:
            return
        with self._connect() as connection:
            connection.execute("DELETE FROM sessions WHERE token_hash = ?", (token_digest(token),))

    def change_password(
        self,
        user: User,
        current_token: str,
        current_password: str,
        new_password: str,
    ) -> int:
        validate_password(new_password)
        current_token_hash = token_digest(current_token)
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                "SELECT password_hash, active FROM users WHERE id = ?",
                (user.id,),
            ).fetchone()
            if (
                row is None
                or not row["active"]
                or not verify_password(current_password, row["password_hash"])
            ):
                raise InvalidCurrentPasswordError("The current password is invalid.")
            if verify_password(new_password, row["password_hash"]):
                raise PasswordUnchangedError("The new password must be different.")
            encoded_password = hash_password(new_password)
            connection.execute(
                "UPDATE users SET password_hash = ? WHERE id = ?",
                (encoded_password, user.id),
            )
            cursor = connection.execute(
                "DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?",
                (user.id, current_token_hash),
            )
        return max(0, cursor.rowcount)

    def revoke_other_sessions(self, user: User, current_token: str) -> int:
        current_token_hash = token_digest(current_token)
        with self._connect() as connection:
            cursor = connection.execute(
                "DELETE FROM sessions WHERE user_id = ? AND token_hash <> ?",
                (user.id, current_token_hash),
            )
        return max(0, cursor.rowcount)

    def login_retry_after(
        self, bucket_keys: tuple[str, ...], now: int | None = None
    ) -> int:
        timestamp = int(time.time()) if now is None else int(now)
        hashes = tuple(token_digest(key) for key in bucket_keys if key)
        if not hashes:
            return 0
        placeholders = ",".join("?" for _ in hashes)
        with self._connect() as connection:
            row = connection.execute(
                f"SELECT MAX(blocked_until) AS blocked_until FROM login_throttle "
                f"WHERE bucket_hash IN ({placeholders})",
                hashes,
            ).fetchone()
        blocked_until = int(row["blocked_until"] or 0)
        return max(0, blocked_until - timestamp)

    def record_login_failure(
        self,
        bucket_key: str,
        max_attempts: int,
        window_seconds: int,
        block_seconds: int,
        now: int | None = None,
    ) -> int:
        if not bucket_key or max_attempts < 1 or window_seconds < 1 or block_seconds < 1:
            raise ValueError("Invalid login throttle parameters.")
        timestamp = int(time.time()) if now is None else int(now)
        bucket_hash = token_digest(bucket_key)
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                """SELECT failures, window_started_at, blocked_until
                   FROM login_throttle WHERE bucket_hash = ?""",
                (bucket_hash,),
            ).fetchone()
            if row is None or timestamp - row["window_started_at"] >= window_seconds:
                failures = 1
                window_started_at = timestamp
                blocked_until = 0
            else:
                failures = row["failures"] + 1
                window_started_at = row["window_started_at"]
                blocked_until = row["blocked_until"]
            if failures >= max_attempts:
                blocked_until = max(blocked_until, timestamp + block_seconds)
            connection.execute(
                """INSERT INTO login_throttle
                   (bucket_hash, failures, window_started_at, blocked_until, updated_at)
                   VALUES (?, ?, ?, ?, ?)
                   ON CONFLICT(bucket_hash) DO UPDATE SET
                     failures = excluded.failures,
                     window_started_at = excluded.window_started_at,
                     blocked_until = excluded.blocked_until,
                     updated_at = excluded.updated_at""",
                (bucket_hash, failures, window_started_at, blocked_until, timestamp),
            )
            retention = max(window_seconds, block_seconds) * 7
            connection.execute(
                """DELETE FROM login_throttle
                   WHERE updated_at < ? AND blocked_until <= ?""",
                (timestamp - retention, timestamp),
            )
        return max(0, blocked_until - timestamp)

    def clear_login_failures(self, bucket_keys: tuple[str, ...]) -> None:
        hashes = tuple(token_digest(key) for key in bucket_keys if key)
        if not hashes:
            return
        placeholders = ",".join("?" for _ in hashes)
        with self._connect() as connection:
            connection.execute(
                f"DELETE FROM login_throttle WHERE bucket_hash IN ({placeholders})",
                hashes,
            )

    def create_invite(
        self,
        admin: User,
        valid_hours: int = 72,
        max_uses: int = 1,
        now: int | None = None,
    ) -> tuple[str, int]:
        if admin.role != "admin":
            raise PermissionError("Administrator access is required.")
        if not 1 <= valid_hours <= 24 * 30 or not 1 <= max_uses <= 20:
            raise ValueError("Invitation limits are outside the allowed range.")
        timestamp = int(time.time()) if now is None else int(now)
        expires_at = timestamp + valid_hours * 3600
        code = "avora_" + new_secret(24)
        with self._connect() as connection:
            connection.execute("DELETE FROM invites WHERE expires_at <= ?", (timestamp,))
            connection.execute(
                """INSERT INTO invites
                   (code_hash, created_by, created_at, expires_at, max_uses, uses)
                   VALUES (?, ?, ?, ?, ?, 0)""",
                (token_digest(code), admin.id, timestamp, expires_at, max_uses),
            )
        return code, expires_at

    def revoke_invite(self, admin: User, invite_code: str) -> bool:
        if admin.role != "admin":
            raise PermissionError("Administrator access is required.")
        if not isinstance(invite_code, str) or not invite_code:
            raise ValueError("Invitation code is required.")
        with self._connect() as connection:
            cursor = connection.execute(
                "DELETE FROM invites WHERE code_hash = ?",
                (token_digest(invite_code),),
            )
        return cursor.rowcount > 0

    def register_user(
        self,
        invite_code: str,
        email: str,
        display_name: str,
        password: str,
        now: int | None = None,
    ) -> User:
        timestamp = int(time.time()) if now is None else int(now)
        normalized_email = normalize_email(email)
        normalized_name = normalize_display_name(display_name)
        validate_password(password)
        user = User(str(uuid.uuid4()), normalized_email, normalized_name, "user")
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            invite = connection.execute(
                "SELECT expires_at, max_uses, uses FROM invites WHERE code_hash = ?",
                (token_digest(invite_code),),
            ).fetchone()
            if (
                invite is None
                or invite["expires_at"] <= timestamp
                or invite["uses"] >= invite["max_uses"]
            ):
                raise InviteError("Invitation is invalid or expired.")
            encoded_password = hash_password(password)
            try:
                connection.execute(
                    """INSERT INTO users
                       (id, email, display_name, password_hash, role, active, created_at)
                       VALUES (?, ?, ?, ?, 'user', 1, ?)""",
                    (user.id, user.email, user.display_name, encoded_password, timestamp),
                )
            except sqlite3.IntegrityError as exc:
                raise InviteError("This account cannot be registered.") from exc
            new_uses = invite["uses"] + 1
            if new_uses >= invite["max_uses"]:
                connection.execute(
                    "DELETE FROM invites WHERE code_hash = ?", (token_digest(invite_code),)
                )
            else:
                connection.execute(
                    "UPDATE invites SET uses = ? WHERE code_hash = ?",
                    (new_uses, token_digest(invite_code)),
                )
        return user

    def upsert_access_request(
        self,
        user: User,
        device_id: str,
        firebase_uid: str,
        now: int | None = None,
    ) -> AccessRequest:
        if user.role != "user":
            raise PermissionError("Only invited users can request device access.")
        timestamp = int(time.time()) if now is None else int(now)
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            existing = connection.execute(
                """SELECT id, firebase_uid, status, created_at
                   FROM access_requests WHERE device_id = ? AND user_id = ?""",
                (device_id, user.id),
            ).fetchone()
            if existing is None:
                request_id = str(uuid.uuid4())
                connection.execute(
                    """INSERT INTO access_requests
                       (id, device_id, user_id, firebase_uid, status, created_at, updated_at)
                       VALUES (?, ?, ?, ?, 'pending', ?, ?)""",
                    (request_id, device_id, user.id, firebase_uid, timestamp, timestamp),
                )
            elif existing["firebase_uid"] == firebase_uid:
                request_id = existing["id"]
                connection.execute(
                    "UPDATE access_requests SET updated_at = ? WHERE id = ?",
                    (timestamp, request_id),
                )
            else:
                request_id = existing["id"]
                connection.execute(
                    """UPDATE access_requests
                       SET firebase_uid = ?, status = 'pending',
                           created_at = ?, updated_at = ?
                       WHERE id = ?""",
                    (firebase_uid, timestamp, timestamp, request_id),
                )
            row = connection.execute(
                """SELECT r.id, r.device_id, r.user_id, r.firebase_uid,
                          u.email, u.display_name, r.status,
                          r.created_at, r.updated_at
                   FROM access_requests r JOIN users u ON u.id = r.user_id
                   WHERE r.id = ?""",
                (request_id,),
            ).fetchone()
        return _row_to_access_request(row)

    def list_pending_access_requests(
        self, admin: User, device_id: str
    ) -> list[AccessRequest]:
        if admin.role != "admin":
            raise PermissionError("Administrator access is required.")
        with self._connect() as connection:
            rows = connection.execute(
                """SELECT r.id, r.device_id, r.user_id, r.firebase_uid,
                          u.email, u.display_name, r.status,
                          r.created_at, r.updated_at
                   FROM access_requests r JOIN users u ON u.id = r.user_id
                   WHERE r.device_id = ? AND r.status = 'pending'
                   ORDER BY r.created_at ASC""",
                (device_id,),
            ).fetchall()
        return [_row_to_access_request(row) for row in rows]

    def approve_access_request(
        self,
        admin: User,
        request_id: str,
        now: int | None = None,
    ) -> AccessRequest | None:
        if admin.role != "admin":
            raise PermissionError("Administrator access is required.")
        timestamp = int(time.time()) if now is None else int(now)
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            cursor = connection.execute(
                """UPDATE access_requests SET status = 'approved', updated_at = ?
                   WHERE id = ? AND status = 'pending'""",
                (timestamp, request_id),
            )
            if cursor.rowcount == 0:
                return None
            row = connection.execute(
                """SELECT r.id, r.device_id, r.user_id, r.firebase_uid,
                          u.email, u.display_name, r.status,
                          r.created_at, r.updated_at
                   FROM access_requests r JOIN users u ON u.id = r.user_id
                   WHERE r.id = ?""",
                (request_id,),
            ).fetchone()
        return _row_to_access_request(row)


class TenantDatabase:
    def __init__(self, path: Path) -> None:
        self.path = path
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=10.0)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA busy_timeout = 10000")
        try:
            yield connection
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def _initialize(self) -> None:
        with self._connect() as connection:
            connection.execute("PRAGMA journal_mode = WAL")
            connection.execute("PRAGMA synchronous = NORMAL")
            connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    key TEXT PRIMARY KEY,
                    json_value TEXT NOT NULL,
                    version INTEGER NOT NULL CHECK (version > 0),
                    updated_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS photos (
                    id TEXT PRIMARY KEY,
                    sha256 TEXT NOT NULL,
                    size_bytes INTEGER NOT NULL CHECK (size_bytes > 0),
                    metadata_json TEXT NOT NULL DEFAULT '{}',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
                """
            )

    def get_document(self, key: str) -> Document | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT key, json_value, version, updated_at FROM documents WHERE key = ?", (key,)
            ).fetchone()
        return _row_to_document(row) if row else None

    def list_documents(self) -> list[Document]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT key, json_value, version, updated_at FROM documents ORDER BY key"
            ).fetchall()
        return [_row_to_document(row) for row in rows]

    def put_document(
        self,
        key: str,
        data: Any,
        expected_version: int | None = None,
        now: int | None = None,
    ) -> Document:
        timestamp = int(time.time()) if now is None else int(now)
        encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            current = connection.execute(
                "SELECT version FROM documents WHERE key = ?", (key,)
            ).fetchone()
            current_version = current["version"] if current else 0
            if expected_version is not None and expected_version != current_version:
                raise DataConflictError("The document changed on another client.")
            version = current_version + 1
            connection.execute(
                """INSERT INTO documents(key, json_value, version, updated_at)
                   VALUES (?, ?, ?, ?)
                   ON CONFLICT(key) DO UPDATE SET
                     json_value = excluded.json_value,
                     version = excluded.version,
                     updated_at = excluded.updated_at""",
                (key, encoded, version, timestamp),
            )
        return Document(key, json.loads(encoded), version, timestamp)

    def upsert_photo(
        self,
        photo_id: str,
        sha256: str,
        size_bytes: int,
        metadata: dict[str, Any] | None = None,
        now: int | None = None,
    ) -> PhotoRecord:
        timestamp = int(time.time()) if now is None else int(now)
        clean_metadata = metadata if isinstance(metadata, dict) else {}
        encoded = json.dumps(
            clean_metadata, ensure_ascii=False, separators=(",", ":"), sort_keys=True
        )
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            existing = connection.execute(
                "SELECT created_at FROM photos WHERE id = ?", (photo_id,)
            ).fetchone()
            created_at = existing["created_at"] if existing else timestamp
            connection.execute(
                """INSERT INTO photos
                   (id, sha256, size_bytes, metadata_json, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?)
                   ON CONFLICT(id) DO UPDATE SET
                     sha256 = excluded.sha256,
                     size_bytes = excluded.size_bytes,
                     metadata_json = excluded.metadata_json,
                     updated_at = excluded.updated_at""",
                (photo_id, sha256, size_bytes, encoded, created_at, timestamp),
            )
        return PhotoRecord(photo_id, sha256, size_bytes, clean_metadata, created_at, timestamp)

    def get_photo(self, photo_id: str) -> PhotoRecord | None:
        with self._connect() as connection:
            row = connection.execute(
                """SELECT id, sha256, size_bytes, metadata_json, created_at, updated_at
                   FROM photos WHERE id = ?""",
                (photo_id,),
            ).fetchone()
        return _row_to_photo(row) if row else None

    def list_photos(self) -> list[PhotoRecord]:
        with self._connect() as connection:
            rows = connection.execute(
                """SELECT id, sha256, size_bytes, metadata_json, created_at, updated_at
                   FROM photos ORDER BY created_at DESC, id"""
            ).fetchall()
        return [_row_to_photo(row) for row in rows]

    def update_photo_metadata(
        self, photo_id: str, metadata: dict[str, Any], now: int | None = None
    ) -> PhotoRecord:
        timestamp = int(time.time()) if now is None else int(now)
        encoded = json.dumps(metadata, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        with self._connect() as connection:
            cursor = connection.execute(
                "UPDATE photos SET metadata_json = ?, updated_at = ? WHERE id = ?",
                (encoded, timestamp, photo_id),
            )
            if cursor.rowcount != 1:
                raise KeyError(photo_id)
        record = self.get_photo(photo_id)
        if record is None:
            raise KeyError(photo_id)
        return record


def tenant_database_path(root: Path, user_id: str) -> Path:
    parsed = uuid.UUID(user_id)
    canonical_id = str(parsed)
    if canonical_id != user_id:
        raise ValueError("Invalid user identity.")
    root = root.resolve()
    path = (root / f"{canonical_id}.sqlite3").resolve()
    if path.parent != root:
        raise ValueError("Unsafe tenant database path.")
    return path


def _row_to_user(row: sqlite3.Row) -> User:
    return User(row["id"], row["email"], row["display_name"], row["role"])


def _row_to_document(row: sqlite3.Row) -> Document:
    return Document(row["key"], json.loads(row["json_value"]), row["version"], row["updated_at"])


def _row_to_photo(row: sqlite3.Row) -> PhotoRecord:
    return PhotoRecord(
        row["id"],
        row["sha256"],
        row["size_bytes"],
        json.loads(row["metadata_json"]),
        row["created_at"],
        row["updated_at"],
    )


def _row_to_access_request(row: sqlite3.Row) -> AccessRequest:
    return AccessRequest(
        id=row["id"],
        device_id=row["device_id"],
        user_id=row["user_id"],
        firebase_uid=row["firebase_uid"],
        email=row["email"],
        display_name=row["display_name"],
        status=row["status"],
        created_at=row["created_at"],
        updated_at=row["updated_at"],
    )
