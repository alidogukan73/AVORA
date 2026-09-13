"""Regression checks for owner-only garden access request notifications."""

from __future__ import annotations

import logging
import sys
import time
import types
import uuid
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def install_dependency_stubs() -> None:
    firebase_admin = types.ModuleType("firebase_admin")
    credentials = types.ModuleType("firebase_admin.credentials")
    credentials.Certificate = lambda *_args, **_kwargs: object()
    auth = types.ModuleType("firebase_admin.auth")
    auth.get_user = lambda _uid: None
    database = types.ModuleType("firebase_admin.db")
    database.Reference = object
    database.reference = lambda *_args, **_kwargs: None
    messaging = types.ModuleType("firebase_admin.messaging")
    messaging.UnregisteredError = type("UnregisteredError", (Exception,), {})
    messaging.AndroidConfig = lambda **kwargs: SimpleNamespace(**kwargs)
    messaging.Message = lambda **kwargs: SimpleNamespace(**kwargs)
    messaging.send = lambda _message: "message-id"
    firebase_admin.credentials = credentials
    firebase_admin.auth = auth
    firebase_admin.db = database
    firebase_admin.messaging = messaging
    firebase_admin.initialize_app = lambda *_args, **_kwargs: object()
    sys.modules.setdefault("firebase_admin", firebase_admin)
    sys.modules.setdefault("firebase_admin.credentials", credentials)
    sys.modules.setdefault("firebase_admin.auth", auth)
    sys.modules.setdefault("firebase_admin.db", database)
    sys.modules.setdefault("firebase_admin.messaging", messaging)

    paho = types.ModuleType("paho")
    paho_mqtt = types.ModuleType("paho.mqtt")
    mqtt_client = types.ModuleType("paho.mqtt.client")
    paho.mqtt = paho_mqtt
    paho_mqtt.client = mqtt_client
    sys.modules.setdefault("paho", paho)
    sys.modules.setdefault("paho.mqtt", paho_mqtt)
    sys.modules.setdefault("paho.mqtt.client", mqtt_client)


install_dependency_stubs()

from core import firebase_service as firebase_module
from core.firebase_service import FirebaseService, _valid_uuid


class FakeReference:
    def __init__(self, values: dict, path: tuple[str, ...] = ()) -> None:
        self.values = values
        self.path = path

    def child(self, name: str) -> "FakeReference":
        parts = tuple(part for part in str(name).split("/") if part)
        return FakeReference(self.values, self.path + parts)

    def _node(self, create: bool = True):
        node = self.values
        for name in self.path:
            if not isinstance(node, dict):
                return None
            if create:
                node = node.setdefault(name, {})
            else:
                node = node.get(name)
                if node is None:
                    return None
        return node

    def get(self):
        node = self._node(create=False)
        return dict(node) if isinstance(node, dict) else node

    def update(self, values: dict) -> None:
        self._node().update(values)

    def delete(self) -> None:
        if not self.path:
            self.values.clear()
            return
        parent = FakeReference(self.values, self.path[:-1])._node(create=False)
        if isinstance(parent, dict):
            parent.pop(self.path[-1], None)


def service_for(values: dict) -> FirebaseService:
    service = FirebaseService.__new__(FirebaseService)
    service._logger = logging.getLogger("access-request-notification-test")
    service._last_push_sent_at = {}
    service._owner_uid_cache = {}
    reference = FakeReference(values)
    service._device_ref = lambda: reference
    service._access_request_notification_ref = lambda: reference.child(
        "access_request_notifications")
    return service


def test_owner_only_delivery() -> None:
    values = {
        "push_tokens": {
            "owner-phone": {"token": "owner-token", "firebase_uid": "owner-uid"},
            "family-phone": {"token": "family-token", "firebase_uid": "family-uid"},
            "legacy-phone": {"token": "legacy-token"},
        }
    }
    service = service_for(values)
    sent_tokens: list[str] = []

    def get_user(uid: str):
        claims = {"avora_device_id": "avora-001"} if uid == "owner-uid" else {}
        return SimpleNamespace(custom_claims=claims)

    with patch.object(firebase_module.auth, "get_user", side_effect=get_user), patch.object(
        firebase_module.messaging,
        "send",
        side_effect=lambda message: sent_tokens.append(message.token) or "message-id",
    ):
        delivered = service._send_push_notification(
            event_code="GARDEN_ACCESS_REQUEST",
            event_id="access-request:test",
            zone_id="",
            owner_only=True,
        )

    assert delivered is True
    assert sent_tokens == ["owner-token"]


def test_queue_processing_and_retry() -> None:
    now_millis = int(time.time() * 1000)
    valid_id = str(uuid.uuid4())
    retry_id = str(uuid.uuid4())
    values = {
        "access_request_notifications": {
            "family-uid": {
                "request_id": valid_id,
                "firebase_uid": "family-uid",
                "requested_at_epoch": now_millis,
                "source": "android",
            },
            "spoofed-uid": {
                "request_id": str(uuid.uuid4()),
                "firebase_uid": "different-uid",
                "requested_at_epoch": now_millis,
                "source": "android",
            },
        }
    }
    service = service_for(values)
    calls: list[dict] = []
    service._send_push_notification = lambda **kwargs: calls.append(kwargs) or True
    service._process_access_request_notifications()

    assert calls == [{
        "event_code": "GARDEN_ACCESS_REQUEST",
        "event_id": f"access-request:{valid_id}",
        "zone_id": "",
        "owner_only": True,
    }]
    valid = values["access_request_notifications"]["family-uid"]
    assert valid["delivery_status"] == "sent"
    assert valid["processed_at_epoch"] > 0
    assert values["access_request_notifications"]["spoofed-uid"][
        "delivery_status"
    ] == "invalid"

    values["access_request_notifications"]["retry-uid"] = {
        "request_id": retry_id,
        "firebase_uid": "retry-uid",
        "requested_at_epoch": now_millis,
        "source": "android",
    }
    service._send_push_notification = lambda **_kwargs: False
    service._process_access_request_notifications()
    retry = values["access_request_notifications"]["retry-uid"]
    assert retry["delivery_status"] == "not_delivered"
    assert "processed_at_epoch" not in retry


def main() -> None:
    assert _valid_uuid(str(uuid.uuid4())) is True
    assert _valid_uuid("not-a-request-id") is False
    test_owner_only_delivery()
    test_queue_processing_and_retry()
    print("[PASS] Garden access notifications target verified owner devices only.")


if __name__ == "__main__":
    main()
