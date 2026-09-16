"""Deterministic checks for the owner-only backed-up mutation service."""

from __future__ import annotations

import copy
import json
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.superadmin_data_service import (  # noqa: E402
    SuperadminDataService,
    SuperadminMutationPlanner,
    _preview_token,
)


OLD = "zone-003-2026-100"
NEW = "zone-003-2026-300"


def fixture() -> dict:
    return {
        "zones": {
            "zone-003": {
                "zone_id": "zone-003",
                "name": "Salatalık",
                "irrigation_enabled": True,
                "season": {
                    "active_season_id": OLD,
                    "active_season_ids": {OLD: True, NEW: True},
                    "status": "ACTIVE",
                },
            }
        },
        "garden_journal": {
            "seasons": {
                OLD: {
                    "season_id": OLD,
                    "zone_id": "zone-003",
                    "label": "Eski",
                    "status": "ACTIVE",
                    "started_at_epoch": 100,
                    "includes_legacy_records": False,
                },
                NEW: {
                    "season_id": NEW,
                    "zone_id": "zone-003",
                    "label": "Yeni",
                    "status": "ACTIVE",
                    "started_at_epoch": 300,
                    "includes_legacy_records": False,
                },
            },
            "season_outcomes": {OLD: {"id": OLD}},
            "events": {
                "old-event": {"zone_id": "zone-003", "season_id": OLD},
                "new-event": {"zone_id": "zone-003", "season_id": NEW},
            },
            "photo_metadata": {
                "old-photo": {"zone_id": "zone-003", "season_id": OLD},
            },
        },
        "watering_history": {
            "old-water": {"zone_id": "zone-003", "season_ids": {OLD: True}},
            "new-water": {"zone_id": "zone-003", "season_id": NEW},
        },
        "fertilizer_history": {},
        "notifications": {},
        "notification_deletions": {},
        "seedling": {"batches": {}, "daily_logs": {}, "transfer_claims": {}},
        "irrigation_runtime": {"active_zone_id": "", "pending_waterings": {}},
        "user_feedback": {},
        "superadmin": {"commands": {}, "audit": {}},
    }


def at(root: dict, path: str):
    value = root
    for segment in path.split("/"):
        if not isinstance(value, dict) or segment not in value:
            return None
        value = value[segment]
    return value


def assign(root: dict, path: str, value) -> None:
    parts = path.split("/")
    parent = root
    for segment in parts[:-1]:
        parent = parent.setdefault(segment, {})
    if value is None:
        parent.pop(parts[-1], None)
    else:
        parent[parts[-1]] = copy.deepcopy(value)


class FakeStore:
    device_id = "avora-001"

    def __init__(self, root: dict, commands: dict, owner: bool = True) -> None:
        self.root = root
        self.commands = commands
        self.owner = owner
        self.audit = {}

    def pending_commands(self) -> dict:
        return {
            key: value for key, value in self.commands.items()
            if value.get("status") == "pending"
        }

    def processing_commands(self) -> dict:
        return {
            key: value for key, value in self.commands.items()
            if value.get("status") == "processing"
        }

    def claim(self, command_id: str, now_epoch: int) -> dict | None:
        command = self.commands[command_id]
        if command["status"] != "pending":
            return None
        command.update(status="processing", claimed_at_epoch=now_epoch)
        return dict(command)

    def finish(self, command_id: str, values: dict) -> None:
        self.commands[command_id].update(values)

    def device_snapshot(self) -> dict:
        return copy.deepcopy(self.root)

    def apply(self, updates: dict) -> None:
        for path, value in updates.items():
            assign(self.root, path, value)

    def write_audit(self, operation_id: str, values: dict) -> None:
        self.audit[operation_id] = values

    def is_owner(self, uid: str) -> bool:
        return self.owner and uid == "owner-uid"

    def prune_completed_commands(self, cutoff_epoch: int) -> int:
        removable = [
            command_id
            for command_id, value in self.commands.items()
            if value.get("status") in {"completed", "failed"}
            and 0 < int(value.get("completed_at_epoch", 0)) <= cutoff_epoch
        ]
        for command_id in removable:
            self.commands.pop(command_id, None)
        return len(removable)


def command(operation: str, **extra) -> dict:
    return {
        "status": "pending",
        "operation": operation,
        "category": extra.pop("category", "seasons"),
        "record_id": extra.pop("record_id", OLD),
        "requested_by_uid": "owner-uid",
        "requested_at": int(time.time()),
        "expires_at": int(time.time()) + 300,
        **extra,
    }


def test_planner_cascades_and_preserves_other_season() -> None:
    root = fixture()
    updates = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    assert updates[f"garden_journal/seasons/{OLD}"] is None
    assert updates[f"garden_journal/season_outcomes/{OLD}"] is None
    assert updates["garden_journal/events/old-event"] is None
    assert updates["watering_history/old-water"] is None
    assert "garden_journal/events/new-event" not in updates
    assert "watering_history/new-water" not in updates
    assert updates["zones/zone-003/season/active_season_id"] == NEW


def test_legacy_season_only_claims_untagged_records_inside_its_dates() -> None:
    root = fixture()
    season = root["garden_journal"]["seasons"][OLD]
    season["includes_legacy_records"] = True
    season["ended_at_epoch"] = 200
    root["watering_history"].update({
        "legacy-inside": {"zone_id": "zone-003", "occurred_at_epoch": 150},
        "legacy-outside": {"zone_id": "zone-003", "occurred_at_epoch": 250},
    })

    updates = SuperadminMutationPlanner.delete(root, "seasons", OLD)

    assert updates["watering_history/legacy-inside"] is None
    assert "watering_history/legacy-outside" not in updates


def test_preview_does_not_mutate() -> None:
    root = fixture()
    command_id = "11111111-1111-1111-1111-111111111111"
    store = FakeStore(root, {command_id: command("preview_delete")})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
    assert at(root, f"garden_journal/seasons/{OLD}") is not None
    assert store.commands[command_id]["status"] == "completed"
    assert store.commands[command_id]["preview"]["affected_count"] >= 4


def test_delete_is_backed_up_and_restorable() -> None:
    root = fixture()
    delete_id = "22222222-2222-2222-2222-222222222222"
    delete_plan = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    store = FakeStore(root, {delete_id: command(
        "delete", preview_token=_preview_token(delete_plan, root)
    )})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
        backup_id = store.commands[delete_id]["backup_id"]
        backup_path = Path(folder) / f"{backup_id}.json"
        assert backup_path.is_file()
        backup = json.loads(backup_path.read_text(encoding="utf-8"))
        assert backup["before"][f"garden_journal/seasons/{OLD}"]["label"] == "Eski"
        assert at(root, f"garden_journal/seasons/{OLD}") is None

        restore_id = "33333333-3333-3333-3333-333333333333"
        store.commands[restore_id] = command(
            "restore", record_id="restore", backup_id=backup_id
        )
        assert service.process_once() == 1
        assert at(root, f"garden_journal/seasons/{OLD}")["label"] == "Eski"
        assert at(root, "garden_journal/events/old-event") is not None


def test_feedback_delete_is_single_step_backed_up_and_audited() -> None:
    root = fixture()
    feedback_id = "123e4567-e89b-12d3-a456-426614174099"
    root["user_feedback"][feedback_id] = {
        "id": feedback_id,
        "subject": "Deneme geri bildirimi",
        "status": "new",
    }
    command_id = "12121212-1212-1212-1212-121212121212"
    store = FakeStore(root, {command_id: command(
        "delete_feedback", category="feedback", record_id=feedback_id
    )})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
        backup_id = store.commands[command_id]["backup_id"]
        backup = json.loads(
            (Path(folder) / f"{backup_id}.json").read_text(encoding="utf-8")
        )
    assert at(root, f"user_feedback/{feedback_id}") is None
    assert backup["before"][f"user_feedback/{feedback_id}"]["status"] == "new"
    assert store.commands[command_id]["status"] == "completed"
    assert store.audit[command_id]["operation"] == "delete_feedback"


def test_feedback_delete_cannot_target_another_category() -> None:
    root = fixture()
    command_id = "13131313-1313-1313-1313-131313131313"
    store = FakeStore(root, {command_id: command(
        "delete_feedback", category="seasons", record_id=OLD
    )})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
    assert store.commands[command_id]["status"] == "failed"
    assert at(root, f"garden_journal/seasons/{OLD}") is not None


def test_non_owner_is_rejected() -> None:
    root = fixture()
    command_id = "44444444-4444-4444-4444-444444444444"
    store = FakeStore(root, {command_id: command("delete")}, owner=False)
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
    assert store.commands[command_id]["status"] == "failed"
    assert at(root, f"garden_journal/seasons/{OLD}") is not None


def test_completed_commands_are_pruned_after_seven_days() -> None:
    now = int(time.time())
    old_id = "55555555-5555-5555-5555-555555555555"
    recent_id = "66666666-6666-6666-6666-666666666666"
    commands = {
        old_id: {
            "status": "completed",
            "completed_at_epoch": now - (8 * 24 * 60 * 60),
        },
        recent_id: {
            "status": "completed",
            "completed_at_epoch": now - (6 * 24 * 60 * 60),
        },
    }
    store = FakeStore(fixture(), commands)
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 0
    assert old_id not in store.commands
    assert recent_id in store.commands


def test_notification_delete_creates_remote_tombstone() -> None:
    root = fixture()
    root["notifications"]["note-1"] = {
        "id": "note-1",
        "zone_id": "zone-003",
        "season_id": OLD,
        "source_key": f"SEASON:{OLD}",
    }
    updates = SuperadminMutationPlanner.delete(root, "notifications", "note-1")
    assert updates["notifications/note-1"] is None
    assert updates["notification_deletions/note-1/source_key"] == f"SEASON:{OLD}"
    assert updates["notification_deletions/note-1/deleted_at_epoch"] > 0


def test_season_delete_unlinks_seedling_transfer() -> None:
    root = fixture()
    root["garden_journal"]["seasons"][OLD]["source_seedling_batch_id"] = "batch-1"
    root["seedling"]["batches"]["batch-1"] = {
        "batch_id": "batch-1",
        "status": "ARCHIVED",
        "archive_reason": "TRANSFERRED",
        "transferred_season_id": OLD,
        "transferred_zone_id": "zone-003",
    }
    root["seedling"]["transfer_claims"]["batch-1"] = {
        "batch_id": "batch-1", "season_id": OLD, "zone_id": "zone-003"
    }
    updates = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    assert updates["seedling/batches/batch-1/status"] == "ACTIVE"
    assert updates["seedling/batches/batch-1/transferred_season_id"] is None
    assert updates["seedling/transfer_claims/batch-1"] is None


def test_season_delete_removes_photo_derived_records_and_tombstones() -> None:
    root = fixture()
    root["garden_journal"]["events"]["photo-derived"] = {
        "zone_id": "",
        "season_id": "",
        "source_key": "PLANT_PHOTO:old-photo",
    }
    root["notifications"]["photo-note"] = {
        "id": "photo-note",
        "zone_id": "",
        "season_id": "",
        "source_key": "PLANT_PHOTO:old-photo",
    }
    updates = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    assert updates["garden_journal/events/photo-derived"] is None
    assert updates["notifications/photo-note"] is None
    assert updates[
        "notification_deletions/photo-note/source_key"
    ] == "PLANT_PHOTO:old-photo"


def test_active_irrigation_blocks_zone_delete() -> None:
    root = fixture()
    root["irrigation_runtime"]["pending_waterings"]["run-1"] = {
        "record": {"zone_id": "zone-003", "season_id": OLD}
    }
    try:
        SuperadminMutationPlanner.delete(root, "zones", "zone-003")
        raise AssertionError("Active irrigation must block zone deletion")
    except ValueError as error:
        assert "Sulama" in str(error)


def test_delete_requires_matching_preview_token() -> None:
    root = fixture()
    command_id = "77777777-7777-7777-7777-777777777777"
    store = FakeStore(root, {command_id: command("delete", preview_token="wrong")})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
    assert store.commands[command_id]["status"] == "failed"
    assert at(root, f"garden_journal/seasons/{OLD}") is not None


def test_stale_applied_command_is_completed_from_backup() -> None:
    root = fixture()
    command_id = "88888888-8888-8888-8888-888888888888"
    plan = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    store = FakeStore(root, {})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        service._write_backup(
            command_id, command_id, "delete", "seasons", OLD,
            "owner-uid", copy.deepcopy(root), plan,
        )
        store.apply(plan)
        store.commands[command_id] = command(
            "delete",
            status="processing",
            claimed_at_epoch=int(time.time()) - 300,
            backup_id=command_id,
            preview_token=_preview_token(plan, root),
        )
        assert service.process_once() == 1
    assert store.commands[command_id]["status"] == "completed"
    assert command_id in store.audit


def test_zone_delete_unlinks_transferred_batch_without_overlapping_paths() -> None:
    root = fixture()
    root["garden_journal"]["seasons"][OLD]["source_seedling_batch_id"] = "batch-1"
    root["seedling"]["batches"]["batch-1"] = {
        "batch_id": "batch-1",
        "zone_id": "zone-003",
        "status": "ARCHIVED",
        "transferred_season_id": OLD,
        "transferred_zone_id": "zone-003",
    }
    updates = SuperadminMutationPlanner.delete(root, "zones", "zone-003")
    assert updates["seedling/batches/batch-1"] is None
    assert not any(
        path.startswith("seedling/batches/batch-1/") for path in updates
    )


def test_update_rejects_identity_change_and_invalid_firebase_key() -> None:
    root = fixture()
    store = FakeStore(root, {})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        try:
            service._update_plan(
                root,
                {
                    "replacement_json": json.dumps({"season_id": NEW}),
                    "expected_record_json": json.dumps(
                        root["garden_journal"]["seasons"][OLD]
                    ),
                },
                "seasons",
                OLD,
            )
            raise AssertionError("Identity changes must be rejected")
        except ValueError as error:
            assert "season_id" in str(error)

        try:
            service._update_plan(
                root,
                {
                    "replacement_json": json.dumps({
                        "season_id": OLD,
                        "zone_id": "zone-003",
                        "bad/key": True,
                    }),
                    "expected_record_json": json.dumps(
                        root["garden_journal"]["seasons"][OLD]
                    ),
                },
                "seasons",
                OLD,
            )
            raise AssertionError("Invalid Firebase keys must be rejected")
        except ValueError as error:
            assert "Firebase" in str(error)


def test_delete_token_detects_changes_on_same_affected_paths() -> None:
    root = fixture()
    plan = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    token = _preview_token(plan, root)
    root["garden_journal"]["events"]["old-event"]["note"] = "new value"
    command_id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    store = FakeStore(root, {command_id: command("delete", preview_token=token)})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        assert service.process_once() == 1
    assert store.commands[command_id]["status"] == "failed"
    assert at(root, f"garden_journal/seasons/{OLD}") is not None


def test_update_rejects_stale_source_and_null_values() -> None:
    root = fixture()
    current = copy.deepcopy(root["garden_journal"]["seasons"][OLD])
    store = FakeStore(root, {})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        stale = copy.deepcopy(current)
        stale["label"] = "stale"
        try:
            service._update_plan(
                root,
                {
                    "replacement_json": json.dumps(current),
                    "expected_record_json": json.dumps(stale),
                },
                "seasons",
                OLD,
            )
            raise AssertionError("Stale record updates must be rejected")
        except ValueError as error:
            assert "değişmiş" in str(error)

        invalid = copy.deepcopy(current)
        invalid["label"] = None
        try:
            service._update_plan(
                root,
                {
                    "replacement_json": json.dumps(invalid),
                    "expected_record_json": json.dumps(current),
                },
                "seasons",
                OLD,
            )
            raise AssertionError("Null replacement values must be rejected")
        except ValueError as error:
            assert "null" in str(error)


def test_update_preserves_core_types_and_relationships() -> None:
    root = fixture()
    current = copy.deepcopy(root["garden_journal"]["seasons"][OLD])
    store = FakeStore(root, {})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        for changed, expected_error in (
            ({"season_id": OLD}, "zone_id"),
            ({**current, "zone_id": "zone-999"}, "Bağlantı"),
            ({**current, "status": 7}, "veri türü"),
        ):
            try:
                service._update_plan(
                    root,
                    {
                        "replacement_json": json.dumps(changed),
                        "expected_record_json": json.dumps(current),
                    },
                    "seasons",
                    OLD,
                )
                raise AssertionError("Unsafe schema edit must be rejected")
            except ValueError as error:
                assert expected_error in str(error)


def test_restore_refuses_to_overwrite_newer_changes() -> None:
    root = fixture()
    delete_id = "99999999-9999-9999-9999-999999999999"
    plan = SuperadminMutationPlanner.delete(root, "seasons", OLD)
    store = FakeStore(root, {})
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        service._write_backup(
            delete_id, delete_id, "delete", "seasons", OLD,
            "owner-uid", copy.deepcopy(root), plan,
        )
        store.apply(plan)
        root["garden_journal"]["events"]["old-event"] = {
            "zone_id": "zone-003",
            "season_id": OLD,
            "note": "newer value",
        }
        try:
            service._restore_plan(root, {"backup_id": delete_id})
            raise AssertionError("Restore must not overwrite newer changes")
        except ValueError as error:
            assert "yeni verilerin" in str(error)


def test_legacy_migration_runs_only_once_after_success() -> None:
    class MigratingStore(FakeStore):
        def __init__(self):
            super().__init__(fixture(), {})
            self.migration_calls = 0

        def migrate_legacy_data(self) -> int:
            self.migration_calls += 1
            return 2

    store = MigratingStore()
    with tempfile.TemporaryDirectory() as folder:
        service = SuperadminDataService(store, Path(folder), poll_seconds=2)
        service.process_once()
        service.process_once()
    assert store.migration_calls == 1


if __name__ == "__main__":
    test_planner_cascades_and_preserves_other_season()
    test_legacy_season_only_claims_untagged_records_inside_its_dates()
    test_preview_does_not_mutate()
    test_delete_is_backed_up_and_restorable()
    test_non_owner_is_rejected()
    test_completed_commands_are_pruned_after_seven_days()
    test_notification_delete_creates_remote_tombstone()
    test_season_delete_unlinks_seedling_transfer()
    test_season_delete_removes_photo_derived_records_and_tombstones()
    test_active_irrigation_blocks_zone_delete()
    test_delete_requires_matching_preview_token()
    test_stale_applied_command_is_completed_from_backup()
    test_zone_delete_unlinks_transferred_batch_without_overlapping_paths()
    test_update_rejects_identity_change_and_invalid_firebase_key()
    test_delete_token_detects_changes_on_same_affected_paths()
    test_update_rejects_stale_source_and_null_values()
    test_update_preserves_core_types_and_relationships()
    test_restore_refuses_to_overwrite_newer_changes()
    test_legacy_migration_runs_only_once_after_success()
    print("[PASS] Superadmin data service tests.")
