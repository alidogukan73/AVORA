"""Owner-only, backed-up correction commands for AVORA application data."""

from __future__ import annotations

import json
import hashlib
import os
import re
import threading
import time
from datetime import datetime
from datetime import timezone
from pathlib import Path

from firebase_admin import auth
from firebase_admin import db

from core.config import AppConfig
from core.logger import AppLogger


CATEGORY_PATHS = {
    "zones": "zones",
    "seasons": "garden_journal/seasons",
    "seedling_batches": "seedling/batches",
    "journal_events": "garden_journal/events",
    "photos": "garden_journal/photo_metadata",
    "watering": "watering_history",
    "fertilizer": "fertilizer_history",
    "notifications": "notifications",
    "feedback": "user_feedback",
}
_COMMAND_ID = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
)
_RECORD_ID = re.compile(r"^[^./#$\[\]/]{1,180}$")
_MAX_REPLACEMENT_BYTES = 128 * 1024
_MAX_REPLACEMENT_NODES = 5000
_MAX_REPLACEMENT_DEPTH = 16
_INVALID_FIREBASE_KEY = re.compile(r"[.#$\[\]/]")
_BACKUP_SCHEMA_V1 = "avora-superadmin-backup-v1"
_BACKUP_SCHEMA_V2 = "avora-superadmin-backup-v2"
_CORE_FIELDS = {
    "zones": ("zone_id", "name", "plant_type"),
    "seasons": ("season_id", "zone_id", "status", "started_at_epoch"),
    "seedling_batches": ("batch_id", "status", "stage", "updated_at_epoch"),
    "journal_events": ("id", "zone_id", "season_id", "type", "occurred_at_epoch"),
    "photos": ("id", "zone_id", "season_id", "captured_at_epoch"),
    "watering": ("zone_id", "started_at", "duration", "completed"),
    "fertilizer": ("application_id", "zone_id", "product_id", "applied_at_epoch"),
    "notifications": ("id", "type", "title", "created_at_epoch"),
    "feedback": ("id", "type", "status", "created_at", "user_id"),
}
_RELATION_FIELDS = {
    "zones": ("zone_id", "area_id", "sensor_id", "valve_id"),
    "seasons": ("season_id", "zone_id", "area_id", "source_seedling_batch_id"),
    "seedling_batches": (
        "batch_id", "zone_id", "transferred_season_id", "transferred_zone_id",
    ),
    "journal_events": ("id", "event_id", "zone_id", "season_id", "source_key"),
    "photos": ("id", "zone_id", "season_id", "related_application_id"),
    "watering": ("id", "record_id", "zone_id", "season_id", "season_ids"),
    "fertilizer": (
        "application_id", "id", "zone_id", "season_id", "season_ids", "product_id",
    ),
    "notifications": ("id", "zone_id", "season_id", "source_key"),
    "feedback": ("id", "device_id", "user_id"),
}


def _dict(value) -> dict:
    return dict(value) if isinstance(value, dict) else {}


def _safe(value) -> str:
    return str(value or "").strip()


def _integer(value) -> int:
    try:
        number = int(value or 0)
    except (TypeError, ValueError):
        return 0
    return number // 1000 if number > 10_000_000_000 else number


def _path_value(root: dict, path: str):
    current = root
    for segment in path.split("/"):
        if not isinstance(current, dict) or segment not in current:
            return None
        current = current[segment]
    return current


def _references(record: dict, field: str, identifier: str) -> bool:
    if _safe(record.get(field)) == identifier:
        return True
    plural = record.get(field + "s")
    if isinstance(plural, dict):
        return plural.get(identifier) is True or identifier in plural.values()
    return isinstance(plural, list) and identifier in plural


def _record_epoch(key: str, record: dict) -> int:
    for field in (
        "occurred_at_epoch", "captured_at_epoch", "applied_at_epoch",
        "completed_at_epoch", "started_at_epoch", "created_at_epoch",
        "created_at", "updated_at_epoch",
    ):
        value = _integer(record.get(field))
        if value > 0:
            return value
    try:
        return int(datetime.strptime(key[:19], "%Y-%m-%dT%H-%M-%S")
                   .replace(tzinfo=timezone.utc).timestamp())
    except (TypeError, ValueError):
        return 0


def _preview_token(updates: dict, root: dict | None = None) -> str:
    if root is None:
        payload = sorted(str(path) for path in updates)
    else:
        payload = [
            {"path": path, "before": _path_value(root, path)}
            for path in sorted(updates)
        ]
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def _zone_irrigation_busy(root: dict, zone_id: str) -> bool:
    zone_id = _safe(zone_id)
    if not zone_id:
        return False
    zone = _dict(_path_value(root, f"zones/{zone_id}"))
    irrigation = _dict(zone.get("irrigation_status"))
    if irrigation.get("watering_active") is True:
        return True
    if irrigation.get("selected_for_watering") is True:
        return True

    active_zone = _safe(_path_value(root, "irrigation_runtime/active_zone_id"))
    if not active_zone:
        active_zone = _safe(_path_value(root, "status/active_zone_id"))
    hardware_active = any(
        value is True
        for value in (
            _path_value(root, "status/relay"),
            _path_value(root, "status/valve_open"),
            _path_value(root, "commands/relay"),
            _path_value(root, "irrigation_hardware/valve_open"),
        )
    )
    if hardware_active and (not active_zone or active_zone == zone_id):
        return True

    pending = _dict(_path_value(root, "irrigation_runtime/pending_waterings"))
    for value in pending.values():
        record = _dict(_dict(value).get("record"))
        if _safe(record.get("zone_id")) == zone_id:
            return True
    return False


def _notification_removal(
    updates: dict, notification_id: str, notification: dict, deleted_at: int
) -> None:
    updates[f"notifications/{notification_id}"] = None
    updates[f"notification_deletions/{notification_id}/source_key"] = _safe(
        notification.get("source_key")
    )
    updates[f"notification_deletions/{notification_id}/deleted_at_epoch"] = deleted_at


def _validate_replacement_tree(value, depth: int = 0, counter: list[int] | None = None) -> None:
    if counter is None:
        counter = [0]
    if depth > _MAX_REPLACEMENT_DEPTH:
        raise ValueError("Düzenleme içeriği çok derin.")
    counter[0] += 1
    if counter[0] > _MAX_REPLACEMENT_NODES:
        raise ValueError("Düzenleme içeriğinde çok fazla alan var.")
    if isinstance(value, dict):
        for key, child in value.items():
            if not isinstance(key, str) or not key or len(key) > 180:
                raise ValueError("Düzenleme içeriğinde geçersiz alan adı var.")
            if _INVALID_FIREBASE_KEY.search(key):
                raise ValueError("Düzenleme içeriğinde Firebase ile uyumsuz alan adı var.")
            _validate_replacement_tree(child, depth + 1, counter)
    elif isinstance(value, list):
        for child in value:
            _validate_replacement_tree(child, depth + 1, counter)
    elif value is None:
        raise ValueError(
            "Düzenleme içeriğinde null kullanılamaz; alanı silmek için JSON'dan kaldırın."
        )


def _replacement_identity_fields(category: str) -> tuple[str, ...]:
    return {
        "zones": ("zone_id",),
        "seasons": ("season_id",),
        "seedling_batches": ("batch_id",),
        "journal_events": ("id", "event_id"),
        "photos": ("id",),
        "watering": ("id", "record_id"),
        "fertilizer": ("application_id", "id"),
        "notifications": ("id",),
        "feedback": ("id",),
    }.get(category, ())


def _validate_replacement(
    category: str, record_id: str, current: dict, replacement: dict
) -> None:
    if not replacement:
        raise ValueError("Kayıt içeriği boş bırakılamaz.")
    _validate_replacement_tree(replacement)
    for field in _replacement_identity_fields(category):
        before = current.get(field)
        after = replacement.get(field)
        if before is not None and _safe(after) != record_id:
            raise ValueError(f"{field} alanı silinemez veya değiştirilemez.")
        if before is None and after is not None and _safe(after) != record_id:
            raise ValueError(f"{field} alanı kayıt kimliğiyle eşleşmiyor.")
    for field in _CORE_FIELDS.get(category, ()):
        if field in current and field not in replacement:
            raise ValueError(f"Zorunlu {field} alanı silinemez.")
    for field, before in current.items():
        if field not in replacement:
            continue
        after = replacement[field]
        before_type = _json_value_type(before)
        after_type = _json_value_type(after)
        if before_type != after_type:
            raise ValueError(f"{field} alanının veri türü değiştirilemez.")
    for field in _RELATION_FIELDS.get(category, ()):
        if replacement.get(field) != current.get(field):
            raise ValueError(f"Bağlantı alanı {field} bu ekrandan değiştirilemez.")


def _json_value_type(value) -> str:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, dict):
        return "object"
    if isinstance(value, list):
        return "array"
    return type(value).__name__


class FirebaseSuperadminStore:
    """Small Admin SDK boundary kept injectable for deterministic tests."""

    def __init__(self, device_id: str) -> None:
        self.device_id = device_id
        self.base_path = f"devices/{device_id}"
        self.superadmin_path = f"superadmin_devices/{device_id}"

    def migrate_legacy_data(self) -> int:
        legacy_reference = db.reference(f"{self.base_path}/superadmin")
        legacy = _dict(legacy_reference.get())
        if not legacy:
            return 0
        destination = db.reference(self.superadmin_path)
        current = _dict(destination.get())
        updates = {}
        for collection in ("commands", "audit"):
            existing = _dict(current.get(collection))
            for key, value in _dict(legacy.get(collection)).items():
                if key not in existing:
                    updates[f"{collection}/{key}"] = value
        if updates:
            destination.update(updates)
        legacy_reference.delete()
        return len(updates)

    def pending_commands(self) -> dict:
        value = (
            db.reference(f"{self.superadmin_path}/commands")
            .order_by_child("status")
            .equal_to("pending")
            .limit_to_first(5)
            .get()
        )
        return _dict(value)

    def processing_commands(self) -> dict:
        value = (
            db.reference(f"{self.superadmin_path}/commands")
            .order_by_child("status")
            .equal_to("processing")
            .limit_to_first(10)
            .get()
        )
        return _dict(value)

    def prune_completed_commands(self, cutoff_epoch: int) -> int:
        reference = db.reference(f"{self.superadmin_path}/commands")
        values = (
            reference.order_by_child("completed_at_epoch")
            .end_at(cutoff_epoch)
            .limit_to_first(100)
            .get()
        )
        removals = {
            command_id: None
            for command_id, command in _dict(values).items()
            if _safe(_dict(command).get("status")) in {"completed", "failed"}
            and 0 < _integer(_dict(command).get("completed_at_epoch")) <= cutoff_epoch
        }
        if removals:
            reference.update(removals)
        return len(removals)

    def claim(self, command_id: str, now_epoch: int) -> dict | None:
        reference = db.reference(
            f"{self.superadmin_path}/commands/{command_id}"
        )

        def transaction(current):
            value = _dict(current)
            if _safe(value.get("status")) != "pending":
                return value
            value["status"] = "processing"
            value["claimed_at_epoch"] = now_epoch
            value["processor"] = "raspberry_pi"
            return value

        result = reference.transaction(transaction)
        if not isinstance(result, dict) or result.get("status") != "processing":
            return None
        if _integer(result.get("claimed_at_epoch")) != now_epoch:
            return None
        return result

    def finish(self, command_id: str, values: dict) -> None:
        db.reference(
            f"{self.superadmin_path}/commands/{command_id}"
        ).update(values)

    def device_snapshot(self) -> dict:
        return _dict(db.reference(self.base_path).get())

    def apply(self, updates: dict) -> None:
        db.reference(self.base_path).update(updates)

    def write_audit(self, operation_id: str, values: dict) -> None:
        db.reference(
            f"{self.superadmin_path}/audit/{operation_id}"
        ).set(values)

    def is_owner(self, uid: str) -> bool:
        user = auth.get_user(uid)
        claims = user.custom_claims or {}
        return claims.get("avora_device_id") == self.device_id


class SuperadminMutationPlanner:
    """Builds bounded multi-location updates from a device snapshot."""

    RELATED_COLLECTIONS = {
        "watering": "watering_history",
        "fertilizer": "fertilizer_history",
        "journal_events": "garden_journal/events",
        "photos": "garden_journal/photo_metadata",
        "notifications": "notifications",
    }

    @classmethod
    def delete(cls, root: dict, category: str, record_id: str) -> dict:
        if category not in CATEGORY_PATHS:
            raise ValueError("Desteklenmeyen veri türü.")
        if not _RECORD_ID.fullmatch(record_id):
            raise ValueError("Geçersiz kayıt kimliği.")
        target_path = f"{CATEGORY_PATHS[category]}/{record_id}"
        target = _path_value(root, target_path)
        if target is None:
            raise ValueError("Silinecek kayıt bulunamadı.")
        if category == "seasons":
            return cls._delete_season(root, record_id, _dict(target))
        if category == "zones":
            return cls._delete_zone(root, record_id)

        updates = {target_path: None}
        if category == "seedling_batches":
            updates[f"seedling/daily_logs/{record_id}"] = None
            updates[f"seedling/transfer_claims/{record_id}"] = None
            season_id = _safe(_dict(target).get("transferred_season_id"))
            if season_id:
                season = _dict(_path_value(
                    root, f"garden_journal/seasons/{season_id}"
                ))
                if _safe(season.get("source_seedling_batch_id")) == record_id:
                    updates[
                        f"garden_journal/seasons/{season_id}/source_seedling_batch_id"
                    ] = None
        elif category == "photos":
            cls._delete_photo_links(root, record_id, updates)
        elif category == "notifications":
            updates.pop(target_path, None)
            _notification_removal(
                updates, record_id, _dict(target), int(time.time())
            )
        return updates

    @classmethod
    def _delete_photo_links(cls, root: dict, photo_id: str, updates: dict) -> None:
        deleted_at = int(time.time())
        for name, path in (
            ("journal_events", "garden_journal/events"),
            ("notifications", "notifications"),
        ):
            for key, record in _dict(_path_value(root, path)).items():
                value = _dict(record)
                if photo_id in _safe(value.get("source_key")):
                    if name == "notifications":
                        _notification_removal(updates, key, value, deleted_at)
                    else:
                        updates[f"{path}/{key}"] = None

    @classmethod
    def _delete_season(cls, root: dict, season_id: str, season: dict) -> dict:
        zone_id = _safe(season.get("zone_id"))
        if _zone_irrigation_busy(root, zone_id):
            raise ValueError("Sulama veya vana işlemi sürerken sezon silinemez.")
        updates = {
            f"garden_journal/seasons/{season_id}": None,
            f"garden_journal/season_outcomes/{season_id}": None,
        }
        started = _integer(season.get("started_at_epoch"))
        ended = _integer(season.get("ended_at_epoch"))
        legacy = season.get("includes_legacy_records") is True
        deleted_at = int(time.time())
        deleted_photo_ids = set()
        for name, path in cls.RELATED_COLLECTIONS.items():
            for key, record in _dict(_path_value(root, path)).items():
                value = _dict(record)
                if _safe(value.get("zone_id")) != zone_id:
                    continue
                direct = _references(value, "season_id", season_id)
                record_time = _record_epoch(key, value)
                in_period = (legacy and not _safe(value.get("season_id"))
                             and started > 0 and record_time >= started
                             and (ended <= 0 or record_time <= ended))
                if direct or in_period:
                    if name == "photos":
                        deleted_photo_ids.add(key)
                    if name == "notifications":
                        _notification_removal(updates, key, value, deleted_at)
                    else:
                        updates[f"{path}/{key}"] = None
        for photo_id in deleted_photo_ids:
            cls._delete_photo_links(root, photo_id, updates)
        cls._unlink_seedling_transfers(root, season_id, season, updates)
        if zone_id:
            cls._remove_active_season(root, zone_id, season_id, updates)
        return updates

    @classmethod
    def _unlink_seedling_transfers(
        cls, root: dict, season_id: str, season: dict, updates: dict
    ) -> None:
        batch_ids = set()
        source_batch_id = _safe(season.get("source_seedling_batch_id"))
        if source_batch_id:
            batch_ids.add(source_batch_id)
        for key, batch in _dict(_path_value(root, "seedling/batches")).items():
            if _safe(_dict(batch).get("transferred_season_id")) == season_id:
                batch_ids.add(key)

        now_epoch = int(time.time())
        for batch_id in batch_ids:
            batch = _dict(_path_value(root, f"seedling/batches/{batch_id}"))
            if batch:
                prefix = f"seedling/batches/{batch_id}/"
                updates[prefix + "status"] = "ACTIVE"
                updates[prefix + "archive_reason"] = None
                updates[prefix + "archived_at_epoch"] = 0
                updates[prefix + "transferred_season_id"] = None
                updates[prefix + "transferred_zone_id"] = None
                updates[prefix + "transferred_at_epoch"] = None
                updates[prefix + "updated_at_epoch"] = now_epoch
            claim = _dict(_path_value(root, f"seedling/transfer_claims/{batch_id}"))
            if not claim or _safe(claim.get("season_id")) == season_id:
                updates[f"seedling/transfer_claims/{batch_id}"] = None

    @classmethod
    def _remove_active_season(
        cls, root: dict, zone_id: str, season_id: str, updates: dict
    ) -> None:
        zone = _dict(_path_value(root, f"zones/{zone_id}"))
        state = _dict(zone.get("season"))
        active_ids = _dict(state.get("active_season_ids"))
        active_ids.pop(season_id, None)
        if _safe(state.get("active_season_id")) != season_id and season_id not in _dict(
            state.get("active_season_ids")
        ):
            return
        remaining = []
        for key in active_ids:
            value = _dict(_path_value(root, f"garden_journal/seasons/{key}"))
            if value and _safe(value.get("status")).upper() == "ACTIVE":
                remaining.append((key, value))
        remaining.sort(key=lambda item: _integer(item[1].get("started_at_epoch")), reverse=True)
        prefix = f"zones/{zone_id}/season/"
        now_epoch = int(time.time())
        if remaining:
            primary_id, primary = remaining[0]
            updates[prefix + "active_season_ids"] = {key: True for key, _ in remaining}
            updates[prefix + "active_season_id"] = primary_id
            updates[prefix + "status"] = "ACTIVE"
            updates[prefix + "label"] = _safe(primary.get("label"))
            updates[prefix + "started_at_epoch"] = _integer(primary.get("started_at_epoch"))
            updates[prefix + "ended_at_epoch"] = 0
            updates[prefix + "include_legacy_records"] = bool(
                primary.get("includes_legacy_records", False)
            )
        else:
            updates[prefix + "active_season_ids"] = None
            updates[prefix + "active_season_id"] = ""
            updates[prefix + "status"] = "CLOSED"
            updates[prefix + "label"] = ""
            updates[prefix + "started_at_epoch"] = 0
            updates[prefix + "ended_at_epoch"] = now_epoch
            updates[prefix + "include_legacy_records"] = False
            updates[f"zones/{zone_id}/irrigation_enabled"] = False
            updates[f"zones/{zone_id}/fertilization/enabled"] = False
        updates[prefix + "updated_at_epoch"] = now_epoch

    @classmethod
    def _delete_zone(cls, root: dict, zone_id: str) -> dict:
        if _zone_irrigation_busy(root, zone_id):
            raise ValueError("Sulama veya vana işlemi sürerken bölge silinemez.")
        updates = {f"zones/{zone_id}": None}
        deleted_at = int(time.time())
        linked_seasons = []
        deleted_batches = set()
        deleted_photo_ids = set()
        for key, season in _dict(_path_value(root, "garden_journal/seasons")).items():
            if _safe(_dict(season).get("zone_id")) == zone_id:
                linked_seasons.append((key, _dict(season)))
                updates[f"garden_journal/seasons/{key}"] = None
                updates[f"garden_journal/season_outcomes/{key}"] = None
        for name, path in cls.RELATED_COLLECTIONS.items():
            for key, record in _dict(_path_value(root, path)).items():
                value = _dict(record)
                if _safe(value.get("zone_id")) == zone_id:
                    if name == "photos":
                        deleted_photo_ids.add(key)
                    if name == "notifications":
                        _notification_removal(updates, key, value, deleted_at)
                    else:
                        updates[f"{path}/{key}"] = None
        for photo_id in deleted_photo_ids:
            cls._delete_photo_links(root, photo_id, updates)
        for key, batch in _dict(_path_value(root, "seedling/batches")).items():
            value = _dict(batch)
            if _safe(value.get("zone_id")) == zone_id:
                deleted_batches.add(key)
                updates[f"seedling/batches/{key}"] = None
                updates[f"seedling/daily_logs/{key}"] = None
                updates[f"seedling/transfer_claims/{key}"] = None
            elif _safe(value.get("transferred_zone_id")) == zone_id:
                prefix = f"seedling/batches/{key}/"
                updates[prefix + "status"] = "ACTIVE"
                updates[prefix + "archive_reason"] = None
                updates[prefix + "archived_at_epoch"] = 0
                updates[prefix + "transferred_season_id"] = None
                updates[prefix + "transferred_zone_id"] = None
                updates[prefix + "transferred_at_epoch"] = None
                updates[prefix + "updated_at_epoch"] = deleted_at
                updates[f"seedling/transfer_claims/{key}"] = None
        for key, claim in _dict(_path_value(root, "seedling/transfer_claims")).items():
            if _safe(_dict(claim).get("zone_id")) == zone_id:
                updates[f"seedling/transfer_claims/{key}"] = None
        for season_id, season in linked_seasons:
            cls._unlink_seedling_transfers(root, season_id, season, updates)
        for batch_id in deleted_batches:
            prefix = f"seedling/batches/{batch_id}/"
            for path in list(updates):
                if path.startswith(prefix):
                    updates.pop(path, None)
        return updates


def summarize_updates(updates: dict) -> dict:
    groups: dict[str, int] = {}
    for path in updates:
        group = path.split("/", 1)[0]
        if path.startswith("garden_journal/"):
            parts = path.split("/")
            group = parts[1] if len(parts) > 1 else "garden_journal"
        groups[group] = groups.get(group, 0) + 1
    return {"affected_count": len(updates), "groups": groups}


class SuperadminDataService:
    """Polls owner commands and applies each mutation after a private backup."""

    def __init__(
        self,
        store: FirebaseSuperadminStore | None = None,
        backup_root: Path | None = None,
        poll_seconds: int = 15,
    ) -> None:
        self._store = store or FirebaseSuperadminStore(AppConfig.DEVICE_ID)
        self._backup_root = backup_root or Path(
            "/home/ali/AVORA-data-backups/superadmin"
        )
        self._poll_seconds = max(2, poll_seconds)
        self._stale_processing_seconds = 90
        self._command_retention_seconds = 7 * 24 * 60 * 60
        self._prune_interval_seconds = 6 * 60 * 60
        self._last_prune_at = 0
        self._legacy_migration_checked = False
        self._logger = AppLogger().logger
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run,
            daemon=True,
            name="SuperadminData",
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread is not None:
            self._thread.join(timeout=10)
        self._thread = None

    def process_once(self) -> int:
        now_epoch = int(time.time())
        self._migrate_legacy_data()
        self._prune_if_due(now_epoch)
        recovered = self._recover_stale_processing(now_epoch)
        commands = self._store.pending_commands()
        ordered = sorted(
            commands.items(),
            key=lambda item: (_integer(_dict(item[1]).get("requested_at")), item[0]),
        )
        processed = 0
        for command_id, command in ordered:
            if self._stop_event.is_set() or processed >= 5:
                break
            if not _COMMAND_ID.fullmatch(_safe(command_id)):
                continue
            if _safe(_dict(command).get("status")) != "pending":
                continue
            claimed = self._store.claim(command_id, int(time.time()))
            if claimed is None:
                continue
            self._process(command_id, claimed)
            processed += 1
        return recovered + processed

    def _migrate_legacy_data(self) -> None:
        if self._legacy_migration_checked:
            return
        migrate = getattr(self._store, "migrate_legacy_data", None)
        if not callable(migrate):
            self._legacy_migration_checked = True
            return
        try:
            moved = migrate()
            self._legacy_migration_checked = True
            if moved:
                self._logger.info(
                    "Legacy superadmin data moved to owner-only storage. count=%d",
                    moved,
                )
        except Exception as exc:
            self._logger.warning(
                "Legacy superadmin data migration will be retried. error=%s",
                " ".join(str(exc).split())[:300],
            )

    def _recover_stale_processing(self, now_epoch: int) -> int:
        lookup = getattr(self._store, "processing_commands", None)
        if not callable(lookup):
            return 0
        recovered = 0
        try:
            commands = lookup()
        except Exception as exc:
            self._logger.warning(
                "Superadmin recovery scan skipped. error=%s",
                " ".join(str(exc).split())[:300],
            )
            return 0
        for command_id, command in sorted(commands.items()):
            value = _dict(command)
            claimed_at = _integer(value.get("claimed_at_epoch"))
            if claimed_at > 0 and now_epoch - claimed_at < self._stale_processing_seconds:
                continue
            if not _COMMAND_ID.fullmatch(_safe(command_id)):
                continue
            self._recover_command(command_id, value)
            recovered += 1
        return recovered

    def _recover_command(self, command_id: str, command: dict) -> None:
        backup_id = _safe(command.get("backup_id")) or command_id
        try:
            uid = _safe(command.get("requested_by_uid"))
            if not uid or not self._store.is_owner(uid):
                raise PermissionError("Kurtarılacak komut cihaz sahibine ait değil.")
            backup = self._read_backup(backup_id)
            before = backup.get("before")
            after = backup.get("after")
            if not isinstance(before, dict) or not isinstance(after, dict) or not after:
                raise ValueError("Komut güvenli biçimde kurtarılamıyor; v2 yedeği bulunamadı.")
            root = self._store.device_snapshot()
            if self._snapshot_matches(root, after):
                pass
            elif self._snapshot_matches(root, before):
                self._store.apply(after)
            else:
                raise ValueError(
                    "Komut sırasında veriler değişmiş; otomatik kurtarma uygulanmadı."
                )
            self._store.finish(command_id, {
                "status": "processing",
                "processing_stage": "applied",
                "backup_id": backup_id,
            })
            self._complete_mutation(command_id, command, backup_id, after)
            self._logger.info(
                "Recovered stale superadmin command. id=%s", command_id
            )
        except Exception as exc:
            safe_error = " ".join(str(exc).split())[:500]
            self._store.finish(command_id, {
                "status": "failed",
                "completed_at_epoch": int(time.time()),
                "backup_id": backup_id if _COMMAND_ID.fullmatch(backup_id) else "",
                "result_message": safe_error or "Yönetim işlemi kurtarılamadı.",
            })
            self._logger.warning(
                "Superadmin command recovery failed. id=%s error=%s",
                command_id,
                safe_error,
            )

    def _prune_if_due(self, now_epoch: int) -> None:
        if now_epoch - self._last_prune_at < self._prune_interval_seconds:
            return
        self._last_prune_at = now_epoch
        prune = getattr(self._store, "prune_completed_commands", None)
        if not callable(prune):
            return
        try:
            removed = prune(now_epoch - self._command_retention_seconds)
            if removed:
                self._logger.info(
                    "Expired superadmin commands pruned. count=%d",
                    removed,
                )
        except Exception as exc:
            self._logger.warning(
                "Superadmin command cleanup skipped. error=%s",
                " ".join(str(exc).split())[:300],
            )

    def _process(self, command_id: str, command: dict) -> None:
        backup_id = ""
        try:
            uid = _safe(command.get("requested_by_uid"))
            if not uid or not self._store.is_owner(uid):
                raise PermissionError("İşlemi isteyen hesap cihaz sahibi değil.")
            if _integer(command.get("expires_at")) < int(time.time()):
                raise ValueError("Yönetim isteğinin süresi doldu.")

            operation = _safe(command.get("operation"))
            category = _safe(command.get("category"))
            record_id = _safe(command.get("record_id"))
            root = self._store.device_snapshot()

            if operation == "preview_delete":
                updates = SuperadminMutationPlanner.delete(
                    root, category, record_id
                )
                preview = summarize_updates(updates)
                preview["token"] = _preview_token(updates, root)
                self._store.finish(command_id, {
                    "status": "completed",
                    "completed_at_epoch": int(time.time()),
                    "result_message": "Silme önizlemesi hazır.",
                    "preview": preview,
                })
                return

            if operation == "delete":
                updates = SuperadminMutationPlanner.delete(
                    root, category, record_id
                )
                if _safe(command.get("preview_token")) != _preview_token(updates, root):
                    raise ValueError(
                        "Veriler önizlemeden sonra değişti. Silme önizlemesini yenileyin."
                    )
            elif operation == "update":
                updates = self._update_plan(root, command, category, record_id)
            elif operation == "restore":
                updates = self._restore_plan(root, command)
            else:
                raise ValueError("Desteklenmeyen yönetim işlemi.")

            backup_id = command_id
            self._write_backup(
                backup_id,
                command_id,
                operation,
                category,
                record_id,
                uid,
                root,
                updates,
            )
            self._store.finish(command_id, {
                "status": "processing",
                "processing_stage": "backup_ready",
                "backup_id": backup_id,
            })
            self._store.apply(updates)
            self._store.finish(command_id, {
                "status": "processing",
                "processing_stage": "applied",
                "backup_id": backup_id,
            })
            self._complete_mutation(command_id, command, backup_id, updates)
            summary = summarize_updates(updates)
            self._logger.info(
                "Superadmin data operation completed. id=%s operation=%s "
                "category=%s affected=%d",
                command_id,
                operation,
                category,
                summary["affected_count"],
            )
        except Exception as exc:
            safe_error = " ".join(str(exc).split())[:500]
            if backup_id:
                self._store.finish(command_id, {
                    "status": "processing",
                    "processing_stage": "recovery_required",
                    "backup_id": backup_id,
                    "result_message": "İşlem güvenli kurtarma için bekliyor.",
                })
            else:
                self._store.finish(command_id, {
                    "status": "failed",
                    "completed_at_epoch": int(time.time()),
                    "result_message": safe_error or "İşlem tamamlanamadı.",
                })
            self._logger.warning(
                "Superadmin data operation failed. id=%s error=%s",
                command_id,
                safe_error,
            )

    def _complete_mutation(
        self, command_id: str, command: dict, backup_id: str, updates: dict
    ) -> None:
        summary = summarize_updates(updates)
        completed = int(time.time())
        self._store.write_audit(command_id, {
            "id": command_id,
            "operation": _safe(command.get("operation")),
            "category": _safe(command.get("category")),
            "record_id": _safe(command.get("record_id")),
            "requested_by_uid": _safe(command.get("requested_by_uid")),
            "completed_at_epoch": completed,
            "affected_count": summary["affected_count"],
            "groups": summary["groups"],
            "backup_id": backup_id,
            "status": "completed",
        })
        self._store.finish(command_id, {
            "status": "completed",
            "processing_stage": "completed",
            "completed_at_epoch": completed,
            "result_message": "İşlem tamamlandı ve yedeklendi.",
            "affected_count": summary["affected_count"],
            "backup_id": backup_id,
        })

    def _update_plan(
        self, root: dict, command: dict, category: str, record_id: str
    ) -> dict:
        if category not in CATEGORY_PATHS:
            raise ValueError("Desteklenmeyen veri türü.")
        if not _RECORD_ID.fullmatch(record_id):
            raise ValueError("Geçersiz kayıt kimliği.")
        target_path = f"{CATEGORY_PATHS[category]}/{record_id}"
        current = _dict(_path_value(root, target_path))
        if not current:
            raise ValueError("Düzenlenecek kayıt bulunamadı.")
        if category == "zones" and _zone_irrigation_busy(root, record_id):
            raise ValueError("Sulama veya vana işlemi sürerken bölge düzenlenemez.")
        if category == "seasons" and _zone_irrigation_busy(
            root, _safe(current.get("zone_id"))
        ):
            raise ValueError("Sulama veya vana işlemi sürerken sezon düzenlenemez.")
        replacement_json = str(command.get("replacement_json") or "")
        if not replacement_json or len(replacement_json.encode("utf-8")) > _MAX_REPLACEMENT_BYTES:
            raise ValueError("Düzenleme içeriği boş veya çok büyük.")
        replacement = json.loads(
            replacement_json,
            parse_constant=lambda value: (_ for _ in ()).throw(
                ValueError(f"Geçersiz sayısal değer: {value}")
            ),
        )
        if not isinstance(replacement, dict):
            raise ValueError("Kayıt içeriği bir nesne olmalıdır.")
        expected_json = str(command.get("expected_record_json") or "")
        if not expected_json or len(expected_json.encode("utf-8")) > _MAX_REPLACEMENT_BYTES:
            raise ValueError("Düzenlenen kaydın önceki sürümü bulunamadı.")
        expected = json.loads(
            expected_json,
            parse_constant=lambda value: (_ for _ in ()).throw(
                ValueError(f"Geçersiz sayısal değer: {value}")
            ),
        )
        if not isinstance(expected, dict) or expected != current:
            raise ValueError(
                "Kayıt düzenleme ekranı açıldıktan sonra değişmiş. Yenileyip tekrar deneyin."
            )
        _validate_replacement(category, record_id, current, replacement)
        return {target_path: replacement}

    def _restore_plan(self, root: dict, command: dict) -> dict:
        backup_id = _safe(command.get("backup_id"))
        if not _COMMAND_ID.fullmatch(backup_id):
            raise ValueError("Geçersiz geri yükleme kimliği.")
        backup = self._read_backup(backup_id)
        before = backup.get("before")
        if not isinstance(before, dict) or not before:
            raise ValueError("Yedek içeriği geçersiz.")
        self._validate_update_paths(before)
        after = backup.get("after")
        if backup.get("schema") == _BACKUP_SCHEMA_V2:
            if not isinstance(after, dict) or not self._snapshot_matches(root, after):
                raise ValueError(
                    "Kayıtlar bu işlemden sonra değişmiş; geri yükleme yeni verilerin üzerine yazamaz."
                )
        return before

    def _read_backup(self, backup_id: str) -> dict:
        if not _COMMAND_ID.fullmatch(_safe(backup_id)):
            raise ValueError("Geçersiz geri yükleme kimliği.")
        backup_path = self._backup_root / f"{backup_id}.json"
        resolved_root = self._backup_root.resolve()
        resolved_path = backup_path.resolve()
        if resolved_path.parent != resolved_root or not resolved_path.is_file():
            raise ValueError("Geri yükleme yedeği bulunamadı.")
        backup = json.loads(resolved_path.read_text(encoding="utf-8"))
        if not isinstance(backup, dict) or backup.get("schema") not in {
            _BACKUP_SCHEMA_V1, _BACKUP_SCHEMA_V2
        }:
            raise ValueError("Yedek biçimi desteklenmiyor.")
        if backup.get("device_id") != self._store.device_id:
            raise ValueError("Yedek bu AVORA cihazına ait değil.")
        return backup

    @staticmethod
    def _validate_update_paths(updates: dict) -> None:
        for path in updates:
            if not isinstance(path, str) or not path or len(path) > 600:
                raise ValueError("Yedekte geçersiz veri yolu var.")
            segments = path.split("/")
            if any(
                not segment or len(segment) > 180
                or _INVALID_FIREBASE_KEY.search(segment)
                for segment in segments
            ):
                raise ValueError("Yedekte geçersiz veri yolu var.")

    @classmethod
    def _snapshot_matches(cls, root: dict, expected: dict) -> bool:
        cls._validate_update_paths(expected)
        return all(_path_value(root, path) == value for path, value in expected.items())

    def _write_backup(
        self,
        backup_id: str,
        command_id: str,
        operation: str,
        category: str,
        record_id: str,
        uid: str,
        root: dict,
        updates: dict,
    ) -> Path:
        self._backup_root.mkdir(parents=True, exist_ok=True)
        self._backup_root.chmod(0o700)
        resolved_root = self._backup_root.resolve()
        backup_path = (resolved_root / f"{backup_id}.json").resolve()
        if backup_path.parent != resolved_root:
            raise RuntimeError("Yedek hedefi doğrulanamadı.")
        payload = {
            "schema": _BACKUP_SCHEMA_V2,
            "device_id": self._store.device_id,
            "backup_id": backup_id,
            "command_id": command_id,
            "operation": operation,
            "category": category,
            "record_id": record_id,
            "requested_by_uid": uid,
            "created_at": datetime.now(timezone.utc).isoformat(),
            "before": {path: _path_value(root, path) for path in updates},
            "after": updates,
        }
        temporary_path = backup_path.with_suffix(".json.tmp")
        try:
            with temporary_path.open("w", encoding="utf-8") as handle:
                handle.write(json.dumps(payload, ensure_ascii=False, indent=2))
                handle.flush()
                os.fsync(handle.fileno())
            temporary_path.chmod(0o600)
            os.replace(temporary_path, backup_path)
            backup_path.chmod(0o600)
        except Exception:
            temporary_path.unlink(missing_ok=True)
            raise
        return backup_path

    def _run(self) -> None:
        self._logger.info("Superadmin data service started.")
        while not self._stop_event.is_set():
            try:
                self.process_once()
            except Exception as exc:
                self._logger.warning(
                    "Superadmin data cycle failed; retrying. error=%s",
                    " ".join(str(exc).split())[:500],
                )
            self._stop_event.wait(self._poll_seconds)
        self._logger.info("Superadmin data service stopped.")
