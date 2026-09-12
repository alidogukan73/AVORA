"""Application service layer for authentication and tenant-isolated storage."""

from __future__ import annotations

import hashlib
import json
import os
import re
import time
import uuid
from pathlib import Path
from typing import Any

from . import __version__
from .config import Settings
from .database import (
    AccountDatabase,
    AccessRequest,
    Document,
    InvalidCurrentPasswordError,
    InvalidCredentialsError,
    LoginRateLimitError,
    PasswordUnchangedError,
    PhotoRecord,
    Session,
    TenantDatabase,
    User,
)
from .database import tenant_database_path
from .security import constant_time_equal


_DOCUMENT_KEY = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$")
_PHOTO_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
_DEVICE_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$")
_FIREBASE_UID = re.compile(r"^[A-Za-z0-9_-]{16,128}$")
_REQUEST_ID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


class AvoraService:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings.prepare()
        self.accounts = AccountDatabase(self.settings.account_database)

    def health(self) -> dict[str, Any]:
        return {
            "service": "avora-nas-api",
            "version": __version__,
            "status": "ok",
            "initialized": self.accounts.has_users(),
            "storage_ready": self._storage_ready(),
            "time_epoch": int(time.time()),
        }

    def setup_admin(
        self, setup_token: str, email: str, display_name: str, password: str
    ) -> User:
        if not constant_time_equal(setup_token or "", self.settings.setup_token):
            raise PermissionError("The setup token is invalid.")
        user = self.accounts.create_initial_admin(email, display_name, password)
        self.tenant_store(user)
        self._retire_setup_token_file()
        return user

    def login(
        self,
        email: str,
        password: str,
        source: str = "unknown",
        now: int | None = None,
    ) -> Session:
        timestamp = int(time.time()) if now is None else int(now)
        account_bucket = _login_bucket("account", email)
        source_bucket = _login_bucket("source", source)
        retry_after = self.accounts.login_retry_after(
            (account_bucket, source_bucket), timestamp
        )
        if retry_after:
            raise LoginRateLimitError(retry_after)
        try:
            session = self.accounts.create_session(
                email,
                password,
                self.settings.session_hours * 3600,
                timestamp,
            )
        except InvalidCredentialsError:
            account_retry = self.accounts.record_login_failure(
                account_bucket,
                self.settings.login_account_attempts,
                self.settings.login_window_seconds,
                self.settings.login_block_seconds,
                timestamp,
            )
            source_retry = self.accounts.record_login_failure(
                source_bucket,
                self.settings.login_source_attempts,
                self.settings.login_window_seconds,
                self.settings.login_block_seconds,
                timestamp,
            )
            retry_after = max(account_retry, source_retry)
            if retry_after:
                raise LoginRateLimitError(retry_after)
            raise
        self.accounts.clear_login_failures((account_bucket,))
        return session

    def authenticate(self, token: str) -> User:
        return self.accounts.authenticate(token)

    def logout(self, token: str) -> None:
        self.accounts.logout(token)

    def change_password(
        self,
        token: str,
        user: User,
        current_password: str,
        new_password: str,
        source: str = "unknown",
        now: int | None = None,
    ) -> int:
        timestamp = int(time.time()) if now is None else int(now)
        account_bucket = _login_bucket("password-account", user.email)
        source_bucket = _login_bucket("password-source", source)
        retry_after = self.accounts.login_retry_after(
            (account_bucket, source_bucket), timestamp
        )
        if retry_after:
            raise LoginRateLimitError(retry_after)
        try:
            revoked = self.accounts.change_password(
                user, token, current_password, new_password
            )
        except InvalidCurrentPasswordError:
            account_retry = self.accounts.record_login_failure(
                account_bucket,
                self.settings.login_account_attempts,
                self.settings.login_window_seconds,
                self.settings.login_block_seconds,
                timestamp,
            )
            source_retry = self.accounts.record_login_failure(
                source_bucket,
                self.settings.login_source_attempts,
                self.settings.login_window_seconds,
                self.settings.login_block_seconds,
                timestamp,
            )
            retry_after = max(account_retry, source_retry)
            if retry_after:
                raise LoginRateLimitError(retry_after)
            raise
        except PasswordUnchangedError:
            raise
        self.accounts.clear_login_failures((account_bucket, source_bucket))
        return revoked

    def revoke_other_sessions(self, token: str, user: User) -> int:
        return self.accounts.revoke_other_sessions(user, token)

    def create_invite(
        self, admin: User, valid_hours: int = 72, max_uses: int = 1
    ) -> tuple[str, int]:
        return self.accounts.create_invite(admin, valid_hours, max_uses)

    def revoke_invite(self, admin: User, invite_code: str) -> bool:
        return self.accounts.revoke_invite(admin, invite_code)

    def register_session(
        self,
        invite_code: str,
        email: str,
        display_name: str,
        password: str,
        now: int | None = None,
    ) -> Session:
        user = self.register(invite_code, email, display_name, password)
        return self.accounts.create_session_for_user(
            user,
            self.settings.session_hours * 3600,
            now,
        )

    def register(
        self, invite_code: str, email: str, display_name: str, password: str
    ) -> User:
        user = self.accounts.register_user(invite_code, email, display_name, password)
        self.tenant_store(user)
        return user

    def request_device_access(
        self, user: User, device_id: str, firebase_uid: str
    ) -> AccessRequest:
        device_id = _validated_identifier(device_id, _DEVICE_ID, "device ID")
        firebase_uid = _validated_identifier(
            firebase_uid, _FIREBASE_UID, "Firebase user ID"
        )
        return self.accounts.upsert_access_request(user, device_id, firebase_uid)

    def list_pending_access_requests(
        self, admin: User, device_id: str
    ) -> list[AccessRequest]:
        device_id = _validated_identifier(device_id, _DEVICE_ID, "device ID")
        return self.accounts.list_pending_access_requests(admin, device_id)

    def approve_access_request(
        self, admin: User, request_id: str
    ) -> AccessRequest | None:
        request_id = _validated_identifier(request_id, _REQUEST_ID, "request ID")
        return self.accounts.approve_access_request(admin, request_id)

    def tenant_store(self, user: User) -> TenantDatabase:
        path = tenant_database_path(self.settings.tenant_database_dir, user.id)
        return TenantDatabase(path)

    def get_document(self, user: User, key: str) -> Document | None:
        return self.tenant_store(user).get_document(validate_document_key(key))

    def list_documents(self, user: User) -> list[Document]:
        return self.tenant_store(user).list_documents()

    def put_document(
        self,
        user: User,
        key: str,
        data: Any,
        expected_version: int | None = None,
    ) -> Document:
        encoded = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > self.settings.max_json_bytes:
            raise ValueError("The document is too large.")
        return self.tenant_store(user).put_document(
            validate_document_key(key), data, expected_version
        )

    def save_photo(self, user: User, photo_id: str, content: bytes) -> PhotoRecord:
        photo_id = validate_photo_id(photo_id)
        if not content or len(content) > self.settings.max_photo_bytes:
            raise ValueError("The photo size is outside the allowed range.")
        if (
            len(content) < 4
            or not content.startswith(b"\xff\xd8")
            or not content.endswith(b"\xff\xd9")
        ):
            raise ValueError("Only complete JPEG photos are accepted.")
        digest = hashlib.sha256(content).hexdigest()
        folder = self._tenant_photo_dir(user)
        target = (folder / f"{photo_id}.jpg").resolve()
        if target.parent != folder or target.is_symlink():
            raise ValueError("Unsafe photo path.")
        temporary = folder / f".{photo_id}.{uuid.uuid4().hex}.tmp"
        previous = folder / f".{photo_id}.{uuid.uuid4().hex}.bak"
        moved_previous = False
        try:
            with temporary.open("xb") as stream:
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
            if target.exists():
                os.replace(target, previous)
                moved_previous = True
            try:
                os.replace(temporary, target)
                record = self.tenant_store(user).upsert_photo(
                    photo_id, digest, len(content)
                )
            except Exception:
                if target.exists():
                    target.unlink()
                if moved_previous and previous.exists():
                    os.replace(previous, target)
                raise
            if previous.exists():
                previous.unlink()
            return record
        finally:
            if temporary.exists():
                temporary.unlink()

    def get_photo(self, user: User, photo_id: str) -> tuple[PhotoRecord, Path] | None:
        photo_id = validate_photo_id(photo_id)
        record = self.tenant_store(user).get_photo(photo_id)
        if record is None:
            return None
        folder = self._tenant_photo_dir(user)
        path = (folder / f"{photo_id}.jpg").resolve()
        if path.parent != folder or path.is_symlink() or not path.is_file():
            return None
        if path.stat().st_size != record.size_bytes:
            return None
        return record, path

    def list_photos(self, user: User) -> list[PhotoRecord]:
        return self.tenant_store(user).list_photos()

    def update_photo_metadata(
        self, user: User, photo_id: str, metadata: dict[str, Any]
    ) -> PhotoRecord:
        photo_id = validate_photo_id(photo_id)
        encoded = json.dumps(metadata, ensure_ascii=False, separators=(",", ":"))
        if len(encoded.encode("utf-8")) > 64 * 1024:
            raise ValueError("Photo metadata is too large.")
        return self.tenant_store(user).update_photo_metadata(photo_id, metadata)

    def _tenant_photo_dir(self, user: User) -> Path:
        user_id = str(uuid.UUID(user.id))
        if user_id != user.id:
            raise ValueError("Invalid user identity.")
        root = self.settings.photo_dir.resolve()
        folder = (root / user_id).resolve()
        if folder.parent != root:
            raise ValueError("Unsafe photo directory.")
        folder.mkdir(parents=True, exist_ok=True)
        if folder.is_symlink():
            raise ValueError("Symlink photo directories are not allowed.")
        return folder

    def _storage_ready(self) -> bool:
        probe = self.settings.database_dir / ".healthcheck"
        try:
            probe.write_text("ok", encoding="utf-8")
            return probe.read_text(encoding="utf-8") == "ok"
        except OSError:
            return False
        finally:
            try:
                probe.unlink(missing_ok=True)
            except OSError:
                pass

    def _retire_setup_token_file(self) -> None:
        try:
            self.settings.setup_token_file.unlink(missing_ok=True)
        except OSError:
            pass


def validate_document_key(key: str) -> str:
    if not isinstance(key, str) or not _DOCUMENT_KEY.fullmatch(key):
        raise ValueError("Invalid document key.")
    return key


def validate_photo_id(photo_id: str) -> str:
    if not isinstance(photo_id, str) or not _PHOTO_ID.fullmatch(photo_id):
        raise ValueError("Invalid photo identity.")
    return photo_id


def _validated_identifier(value: str, pattern: re.Pattern[str], label: str) -> str:
    if not isinstance(value, str):
        raise ValueError(f"The {label} is invalid.")
    normalized = value.strip()
    if not pattern.fullmatch(normalized):
        raise ValueError(f"The {label} is invalid.")
    return normalized


def _login_bucket(kind: str, value: str) -> str:
    normalized = str(value or "unknown").strip().casefold()[:512]
    return f"{kind}:{normalized or 'unknown'}"
