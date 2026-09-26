from __future__ import annotations

import os
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path
from unittest.mock import patch

from avora_nas.config import Settings
from avora_nas.database import (
    AccountDatabase,
    AccountLifecycleError,
    DataConflictError,
    InvalidCurrentPasswordError,
    InvalidCredentialsError,
    InviteError,
    LoginRateLimitError,
)
from avora_nas.service import AvoraService


class AvoraServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.settings = Settings(
            data_dir=Path(self.temporary.name),
            setup_token="S" * 48,
            max_json_bytes=128 * 1024,
            max_photo_bytes=1024 * 1024,
        ).prepare()
        self.service = AvoraService(self.settings)
        self.admin = self.service.setup_admin(
            self.settings.setup_token,
            "owner@example.com",
            "AVORA Sahibi",
            "Guvenli-Yonetici-2026!",
        )

    def test_setup_is_one_time_only(self) -> None:
        with self.assertRaises(Exception):
            self.service.setup_admin(
                self.settings.setup_token,
                "other@example.com",
                "Other Admin",
                "Another-Strong-2026!",
            )

    def test_login_returns_opaque_session_and_logout_revokes_it(self) -> None:
        session = self.service.login("OWNER@example.com", "Guvenli-Yonetici-2026!")
        self.assertGreaterEqual(len(session.token), 32)
        self.assertEqual(self.admin.id, self.service.authenticate(session.token).id)
        self.service.logout(session.token)
        with self.assertRaises(InvalidCredentialsError):
            self.service.authenticate(session.token)

    def test_authenticated_use_slides_the_session_expiry(self) -> None:
        session = self.service.accounts.create_session(
            "owner@example.com",
            "Guvenli-Yonetici-2026!",
            ttl_seconds=100,
            now=1_800_000_000,
        )

        self.service.accounts.authenticate(
            session.token,
            now=1_800_000_090,
            ttl_seconds=100,
        )

        user = self.service.accounts.authenticate(
            session.token,
            now=1_800_000_150,
        )
        self.assertEqual(self.admin.id, user.id)

    def test_password_change_keeps_current_session_and_revokes_others(self) -> None:
        first = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        second = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        revoked = self.service.change_password(
            first.token,
            first.user,
            "Guvenli-Yonetici-2026!",
            "Yeni-Guvenli-Parola-2026!",
        )
        self.assertEqual(1, revoked)
        self.assertEqual(self.admin.id, self.service.authenticate(first.token).id)
        with self.assertRaises(InvalidCredentialsError):
            self.service.authenticate(second.token)
        with self.assertRaises(InvalidCredentialsError):
            self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        replacement = self.service.login(
            "owner@example.com", "Yeni-Guvenli-Parola-2026!"
        )
        self.assertEqual(self.admin.id, replacement.user.id)

    def test_wrong_current_password_does_not_change_or_revoke(self) -> None:
        first = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        second = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        with self.assertRaises(InvalidCurrentPasswordError):
            self.service.change_password(
                first.token,
                first.user,
                "Yanlis-Parola-2026!",
                "Yeni-Guvenli-Parola-2026!",
            )
        self.assertEqual(self.admin.id, self.service.authenticate(first.token).id)
        self.assertEqual(self.admin.id, self.service.authenticate(second.token).id)
        login = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        self.assertEqual(self.admin.id, login.user.id)

    def test_revoke_other_sessions_keeps_current_session(self) -> None:
        first = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        second = self.service.login("owner@example.com", "Guvenli-Yonetici-2026!")
        self.assertEqual(1, self.service.revoke_other_sessions(first.token, first.user))
        self.assertEqual(self.admin.id, self.service.authenticate(first.token).id)
        with self.assertRaises(InvalidCredentialsError):
            self.service.authenticate(second.token)

    def test_active_sessions_show_devices_without_exposing_tokens(self) -> None:
        now = 1_800_000_000
        phone = self.service.login(
            "owner@example.com",
            "Guvenli-Yonetici-2026!",
            now=now,
            device_id="android-phone",
            device_name="Samsung Galaxy S24",
        )
        tablet = self.service.login(
            "owner@example.com",
            "Guvenli-Yonetici-2026!",
            now=now + 10,
            device_id="android-tablet",
            device_name="Samsung Galaxy Tab",
        )

        sessions = self.service.list_sessions(phone.token, phone.user, now + 20)

        self.assertEqual(2, len(sessions))
        self.assertTrue(sessions[0].current)
        self.assertEqual("Samsung Galaxy S24", sessions[0].device_name)
        self.assertFalse(sessions[1].current)
        self.assertEqual("Samsung Galaxy Tab", sessions[1].device_name)
        self.service.identify_session(
            tablet.token, tablet.user, "android-tablet", "Ali'nin Tableti"
        )
        updated = self.service.list_sessions(tablet.token, tablet.user, now + 20)
        self.assertEqual("Ali'nin Tableti", updated[0].device_name)
        self.assertTrue(updated[0].current)

    def test_repeated_failed_logins_are_blocked_across_restart(self) -> None:
        for offset in range(self.settings.login_account_attempts - 1):
            with self.assertRaises(InvalidCredentialsError):
                self.service.login(
                    "owner@example.com", "wrong-password", "192.0.2.10", 1000 + offset
                )
        with self.assertRaises(LoginRateLimitError) as blocked:
            self.service.login(
                "owner@example.com",
                "wrong-password",
                "192.0.2.10",
                1000 + self.settings.login_account_attempts - 1,
            )
        self.assertEqual(self.settings.login_block_seconds, blocked.exception.retry_after)

        restarted = AvoraService(self.settings)
        with self.assertRaises(LoginRateLimitError):
            restarted.login(
                "owner@example.com", "Guvenli-Yonetici-2026!", "192.0.2.10", 1005
            )
        session = restarted.login(
            "owner@example.com",
            "Guvenli-Yonetici-2026!",
            "192.0.2.10",
            1000
            + self.settings.login_account_attempts
            - 1
            + self.settings.login_block_seconds
            + 1,
        )
        self.assertEqual(self.admin.id, session.user.id)

    def test_existing_session_table_is_migrated_for_device_metadata(self) -> None:
        legacy_path = Path(self.temporary.name) / "legacy-accounts.sqlite3"
        with closing(sqlite3.connect(legacy_path)) as connection:
            connection.executescript(
                """
                CREATE TABLE users (
                    id TEXT PRIMARY KEY,
                    email TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    display_name TEXT NOT NULL,
                    password_hash TEXT NOT NULL,
                    role TEXT NOT NULL,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE sessions (
                    token_hash TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                );
                """
            )

        AccountDatabase(legacy_path)

        with closing(sqlite3.connect(legacy_path)) as connection:
            columns = {
                row[1] for row in connection.execute("PRAGMA table_info(sessions)")
            }
        self.assertTrue(
            {"device_id", "device_name", "last_seen_at"}.issubset(columns)
        )

    def test_invitation_is_single_use(self) -> None:
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        user = self.service.register(
            code, "guest@example.com", "Test Kullanıcısı", "Test-Kullanicisi-2026!"
        )
        self.assertEqual("user", user.role)
        with self.assertRaises(InviteError):
            self.service.register(
                code, "second@example.com", "İkinci Kullanıcı", "Ikinci-Kullanici-2026!"
            )

    def test_registered_user_receives_session_and_is_physically_isolated(self) -> None:
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        session = self.service.register_session(
            code, "guest@example.com", "Aile Üyesi", "Aile-Uyesi-2026!"
        )
        self.assertEqual("user", session.user.role)
        self.assertEqual(session.user.id, self.service.authenticate(session.token).id)
        self.assertTrue(
            (self.settings.tenant_database_dir / f"{session.user.id}.sqlite3").is_file()
        )

    def test_only_admin_can_list_accounts_and_active_session_counts(self) -> None:
        now = 1_800_000_000
        admin_session = self.service.login(
            "owner@example.com", "Guvenli-Yonetici-2026!", now=now
        )
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        family_session = self.service.register_session(
            code,
            "family@example.com",
            "Aile Üyesi",
            "Aile-Uyesi-2026!",
            now=now,
        )

        accounts = self.service.list_accounts(self.admin, now=now + 1)

        self.assertEqual(["admin", "user"], [item.role for item in accounts])
        self.assertEqual(
            ["owner@example.com", "family@example.com"],
            [item.email for item in accounts],
        )
        self.assertEqual([1, 1], [item.active_sessions for item in accounts])
        self.assertTrue(all(item.active for item in accounts))
        with self.assertRaises(PermissionError):
            self.service.list_accounts(family_session.user, now=now + 1)
        self.service.logout(admin_session.token)

    def test_admin_can_revoke_unused_invitation(self) -> None:
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        self.assertTrue(self.service.revoke_invite(self.admin, code))
        self.assertFalse(self.service.revoke_invite(self.admin, code))
        with self.assertRaises(InviteError):
            self.service.register(
                code, "guest@example.com", "Aile Üyesi", "Aile-Uyesi-2026!"
            )

    def test_admin_can_request_access_for_a_new_firebase_identity(self) -> None:
        request = self.service.request_device_access(
            self.admin, "avora-001", "adminFirebaseUser_001"
        )
        self.assertEqual("pending", request.status)
        self.assertEqual(self.admin.id, request.user_id)
        self.assertEqual("owner@example.com", request.email)

        pending = self.service.list_pending_access_requests(
            self.admin, "avora-001"
        )
        self.assertEqual([request.id], [item.id for item in pending])

    def test_invited_user_requests_access_and_admin_approves_it(self) -> None:
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        user = self.service.register(
            code, "family@example.com", "Aile Üyesi", "Aile-Uyesi-2026!"
        )
        request = self.service.request_device_access(
            user, "avora-001", "familyFirebaseUser_001"
        )
        self.assertEqual("pending", request.status)
        self.assertEqual(user.id, request.user_id)
        self.assertEqual("Aile Üyesi", request.display_name)

        pending = self.service.list_pending_access_requests(self.admin, "avora-001")
        self.assertEqual([request.id], [item.id for item in pending])
        with self.assertRaises(PermissionError):
            self.service.list_pending_access_requests(user, "avora-001")

        approved = self.service.approve_access_request(self.admin, request.id)
        self.assertIsNotNone(approved)
        self.assertEqual("approved", approved.status)
        self.assertEqual(
            [], self.service.list_pending_access_requests(self.admin, "avora-001")
        )

    def test_inactive_family_access_is_reviewed_without_deleting_account_data(self) -> None:
        day = 24 * 60 * 60
        now = 1_900_000_000
        code, _ = self.service.create_invite(self.admin, valid_hours=24, max_uses=1)
        family = self.service.register_session(
            code,
            "inactive@example.com",
            "Pasif Aile Üyesi",
            "Aile-Uyesi-2026!",
            now=now,
        )
        self.service.put_document(family.user, "garden", {"kept": True}, 0)
        request = self.service.request_device_access(
            family.user, "avora-001", "inactiveFirebaseUser_001"
        )
        self.service.approve_access_request(self.admin, request.id)

        before = self.service.list_accounts(
            self.admin, "avora-001", now + (30 * day) - 1
        )
        self.assertFalse(next(item for item in before if item.role == "user").inactive_access)
        due = self.service.list_accounts(self.admin, "avora-001", now + (30 * day))
        self.assertFalse(next(item for item in due if item.role == "admin").inactive_access)
        self.assertTrue(next(item for item in due if item.role == "user").inactive_access)
        with self.assertRaises(PermissionError):
            self.service.keep_inactive_device_access(
                family.user, family.user.id, "avora-001", now + (30 * day)
            )
        with self.assertRaises(PermissionError):
            self.service.revoke_device_access(
                family.user, family.user.id, "avora-001"
            )

        self.assertTrue(
            self.service.keep_inactive_device_access(
                self.admin, family.user.id, "avora-001", now + (30 * day)
            )
        )
        reviewed = self.service.list_accounts(
            self.admin, "avora-001", now + (60 * day) - 1
        )
        self.assertFalse(next(item for item in reviewed if item.role == "user").inactive_access)

        revoked = self.service.revoke_device_access(
            self.admin, family.user.id, "avora-001"
        )
        self.assertIsNotNone(revoked)
        self.assertEqual("inactiveFirebaseUser_001", revoked[0])
        with self.assertRaises(InvalidCredentialsError):
            self.service.authenticate(family.token)
        remaining = self.service.list_accounts(self.admin, "avora-001")
        family_account = next(item for item in remaining if item.id == family.user.id)
        self.assertEqual("", family_account.access_status)
        self.assertEqual(
            {"kept": True},
            self.service.get_document(family.user, "garden").data,
        )

    def test_family_account_disable_restore_and_permanent_deletion(self) -> None:
        day = 24 * 60 * 60
        now = 1_900_000_000
        code, _ = self.service.create_invite(self.admin)
        family = self.service.register_session(
            code,
            "managed@example.com",
            "Yönetilen Aile Üyesi",
            "Aile-Uyesi-2026!",
            now=now,
        )
        self.service.put_document(family.user, "garden", {"retained": True}, 0)
        self.service.save_photo(
            family.user, "daily_1", b"\xff\xd8family-photo\xff\xd9"
        )
        request = self.service.request_device_access(
            family.user, "avora-001", "managedFirebaseUser_001"
        )
        self.service.approve_access_request(self.admin, request.id)
        database_path = (
            self.settings.tenant_database_dir / f"{family.user.id}.sqlite3"
        )
        photo_path = self.settings.photo_dir / family.user.id / "daily_1.jpg"
        self.assertTrue(database_path.is_file())
        self.assertTrue(photo_path.is_file())
        self.assertIsNone(
            self.service.disable_account(self.admin, self.admin.id, now)
        )

        disabled = self.service.disable_account(self.admin, family.user.id, now)
        self.assertIsNotNone(disabled)
        self.assertEqual(now + (30 * day), disabled[0])
        with self.assertRaises(InvalidCredentialsError):
            self.service.authenticate(family.token)
        account = next(
            item for item in self.service.list_accounts(
                self.admin, "avora-001", now + 1
            ) if item.id == family.user.id
        )
        self.assertFalse(account.active)
        self.assertTrue(account.can_restore)
        self.assertFalse(account.can_permanently_delete)
        self.assertEqual("", account.access_status)
        with self.assertRaises(AccountLifecycleError):
            self.service.permanently_delete_account(
                self.admin,
                family.user.id,
                "Guvenli-Yonetici-2026!",
                now + (29 * day),
            )

        self.assertTrue(
            self.service.restore_account(
                self.admin, family.user.id, now + (29 * day)
            )
        )
        restored = self.service.login(
            "managed@example.com", "Aile-Uyesi-2026!", now=now + (29 * day)
        )
        self.assertEqual(family.user.id, restored.user.id)
        self.assertEqual(
            {"retained": True},
            self.service.get_document(restored.user, "garden").data,
        )

        second_disabled_at = now + (30 * day)
        self.service.disable_account(
            self.admin, family.user.id, second_disabled_at
        )
        deletion_time = second_disabled_at + (30 * day)
        due = next(
            item for item in self.service.list_accounts(
                self.admin, "avora-001", deletion_time
            ) if item.id == family.user.id
        )
        self.assertFalse(due.can_restore)
        self.assertTrue(due.can_permanently_delete)
        with self.assertRaises(InvalidCurrentPasswordError):
            self.service.permanently_delete_account(
                self.admin, family.user.id, "wrong-password", deletion_time
            )

        deleted = self.service.permanently_delete_account(
            self.admin,
            family.user.id,
            "Guvenli-Yonetici-2026!",
            deletion_time,
        )
        self.assertEqual(family.user.id, deleted.id)
        self.assertFalse(database_path.exists())
        self.assertFalse(photo_path.exists())
        self.assertNotIn(
            family.user.id,
            [item.id for item in self.service.list_accounts(self.admin)],
        )

    def test_new_firebase_identity_reopens_an_approved_request(self) -> None:
        code, _ = self.service.create_invite(self.admin)
        user = self.service.register(
            code, "family@example.com", "Aile Üyesi", "Aile-Uyesi-2026!"
        )
        first = self.service.request_device_access(
            user, "avora-001", "familyFirebaseUser_001"
        )
        self.service.approve_access_request(self.admin, first.id)
        second = self.service.request_device_access(
            user, "avora-001", "familyFirebaseUser_002"
        )
        self.assertEqual(first.id, second.id)
        self.assertEqual("pending", second.status)

    def test_invalid_invitation_is_rejected_before_password_hashing(self) -> None:
        with patch("avora_nas.database.hash_password") as password_hash:
            with self.assertRaises(InviteError):
                self.service.register(
                    "avora_invalid-code-that-does-not-exist",
                    "guest@example.com",
                    "Aile Üyesi",
                    "Aile-Uyesi-2026!",
                )
        password_hash.assert_not_called()

    def test_each_user_has_a_physically_separate_database(self) -> None:
        code, _ = self.service.create_invite(self.admin)
        user = self.service.register(
            code, "guest@example.com", "Test Kullanıcısı", "Test-Kullanicisi-2026!"
        )
        self.service.put_document(self.admin, "garden", {"owner": "admin"}, 0)
        self.service.put_document(user, "garden", {"owner": "guest"}, 0)

        self.assertEqual("admin", self.service.get_document(self.admin, "garden").data["owner"])
        self.assertEqual("guest", self.service.get_document(user, "garden").data["owner"])
        databases = sorted(self.settings.tenant_database_dir.glob("*.sqlite3"))
        self.assertEqual(2, len(databases))
        self.assertNotEqual(databases[0].name, databases[1].name)

    def test_document_version_prevents_silent_overwrite(self) -> None:
        first = self.service.put_document(self.admin, "zones", {"count": 1}, 0)
        second = self.service.put_document(
            self.admin, "zones", {"count": 2}, first.version
        )
        self.assertEqual(2, second.version)
        with self.assertRaises(DataConflictError):
            self.service.put_document(self.admin, "zones", {"count": 3}, first.version)

    def test_photos_and_metadata_are_isolated_per_user(self) -> None:
        code, _ = self.service.create_invite(self.admin)
        user = self.service.register(
            code, "guest@example.com", "Test Kullanıcısı", "Test-Kullanicisi-2026!"
        )
        admin_photo = b"\xff\xd8admin-photo\xff\xd9"
        user_photo = b"\xff\xd8user-photo\xff\xd9"
        self.service.save_photo(self.admin, "daily_1", admin_photo)
        self.service.save_photo(user, "daily_1", user_photo)
        self.service.update_photo_metadata(self.admin, "daily_1", {"zone": "one"})

        admin_record, admin_path = self.service.get_photo(self.admin, "daily_1")
        user_record, user_path = self.service.get_photo(user, "daily_1")
        self.assertEqual({"zone": "one"}, admin_record.metadata)
        self.assertEqual({}, user_record.metadata)
        self.assertNotEqual(admin_path.parent, user_path.parent)
        self.assertEqual(admin_photo, admin_path.read_bytes())
        self.assertEqual(user_photo, user_path.read_bytes())

    def test_failed_photo_metadata_write_restores_previous_file(self) -> None:
        original = b"\xff\xd8original-photo\xff\xd9"
        replacement = b"\xff\xd8replacement-photo\xff\xd9"
        self.service.save_photo(self.admin, "daily_1", original)
        _, original_path = self.service.get_photo(self.admin, "daily_1")

        class FailingStore:
            def upsert_photo(self, *_args, **_kwargs):
                raise RuntimeError("simulated database failure")

        with patch.object(self.service, "tenant_store", return_value=FailingStore()):
            with self.assertRaises(RuntimeError):
                self.service.save_photo(self.admin, "daily_1", replacement)

        self.assertEqual(original, original_path.read_bytes())
        self.assertEqual([], list(original_path.parent.glob(".*.bak")))
        self.assertEqual([], list(original_path.parent.glob(".*.tmp")))

    def test_setup_token_is_not_recreated_after_initialization(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            environment = {"AVORA_DATA_DIR": folder}
            with patch.dict(os.environ, environment, clear=True):
                first_settings = Settings.from_environment()
                token_file = first_settings.setup_token_file
                self.assertTrue(token_file.is_file())
                first_service = AvoraService(first_settings)
                first_service.setup_admin(
                    first_settings.setup_token,
                    "fresh@example.com",
                    "Yeni Yönetici",
                    "Yeni-Guvenli-Parola-2026!",
                )
                self.assertFalse(token_file.exists())
                Settings.from_environment()
                self.assertFalse(token_file.exists())


if __name__ == "__main__":
    unittest.main()

