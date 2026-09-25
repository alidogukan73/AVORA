"""Regression checks for responsive and race-safe zone test cancellation."""

from __future__ import annotations

import sys
import types
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

try:
    import RPi.GPIO  # noqa: F401
except ModuleNotFoundError:
    rpi = types.ModuleType("RPi")
    gpio = types.ModuleType("RPi.GPIO")
    rpi.GPIO = gpio
    sys.modules["RPi"] = rpi
    sys.modules["RPi.GPIO"] = gpio

from tools.hardware_test_stubs import install_hardware_import_stubs

install_hardware_import_stubs()

from core.firebase_service import FirebaseService
from services.irrigation_service import IrrigationService


class FakeRelay:
    def __init__(self) -> None:
        self.is_on = False
        self.off_count = 0

    def off(self) -> None:
        self.is_on = False
        self.off_count += 1


class FakeValves:
    def __init__(self) -> None:
        self.active_valve_id: str | None = None
        self.close_count = 0

    @staticmethod
    def is_physical_valve(valve_id: str | None) -> bool:
        return valve_id == "valve-001"

    def open(self, valve_id: str) -> None:
        self.active_valve_id = valve_id

    def close_all(self) -> None:
        self.active_valve_id = None
        self.close_count += 1

    @staticmethod
    def wait_for_opening(_valve_id: str) -> None:
        raise AssertionError("A valve-only test must not block cancellation")


class FakeFirebase:
    def __init__(self) -> None:
        self.acknowledgements: list[dict[str, object]] = []
        self.relay_commands: list[bool] = []
        self.relay_states: list[bool] = []

    def update_active_zone_valve(self, *_args) -> None:
        return None

    def acknowledge_zone_test(self, **kwargs) -> None:
        self.acknowledgements.append(kwargs)

    def set_relay_command(self, relay: bool) -> None:
        self.relay_commands.append(relay)

    def update_relay_status(self, relay: bool) -> None:
        self.relay_states.append(relay)


class FakeZoneStateReference:
    def __init__(
        self,
        zones: dict[str, object],
        *,
        root: "FakeZoneStateReference | None" = None,
        path: tuple[str, ...] = (),
    ) -> None:
        self.zones = zones
        self.root = root or self
        self.path = path
        self.updated: dict[str, object] | None = None

    def child(self, name: str) -> "FakeZoneStateReference":
        return FakeZoneStateReference(
            self.zones,
            root=self.root,
            path=(*self.path, name),
        )

    def get(self):
        if self.path == ("zones",):
            return self.zones
        return None

    def update(self, values: dict[str, object]) -> None:
        self.root.updated = values


class FakeReference:
    def __init__(self) -> None:
        self.path: list[str] = []
        self.updated: dict[str, object] | None = None

    def child(self, name: str) -> "FakeReference":
        self.path.append(name)
        return self

    def update(self, values: dict[str, object]) -> None:
        self.updated = values


def command(*, requested: bool, cancel: bool) -> SimpleNamespace:
    return SimpleNamespace(
        zone_test_requested=requested,
        zone_test_cancel_requested=cancel,
        zone_test_request_id="test-request",
        zone_test_valve_id="valve-001",
        zone_test_zone_id="zone-001",
        zone_test_duration=5,
        zone_test_requested_at_ms=1,
    )


def service_with_fakes() -> tuple[
    IrrigationService,
    FakeRelay,
    FakeValves,
    FakeFirebase,
]:
    service = IrrigationService.__new__(IrrigationService)
    relay = FakeRelay()
    valves = FakeValves()
    firebase = FakeFirebase()
    service._relay = relay
    service._valves = valves
    service._firebase = firebase
    service._active_zone_test_request_id = ""
    service._active_zone_test_valve_id = ""
    service._active_zone_test_mode = ""
    service._active_zone_test_deadline = 0.0
    service._last_zone_test_request_id = ""
    service._manual_relay_started_at = 10.0
    service._manual_relay_timeout_latched = True
    service._is_recent_command = lambda _requested_at: True
    return service, relay, valves, firebase


def verify_zone_test_does_not_block_cancellation() -> None:
    service, relay, valves, firebase = service_with_fakes()

    service._process_zone_test_command(command(requested=True, cancel=False))

    assert valves.active_valve_id == "valve-001"
    assert service._active_zone_test_request_id == "test-request"
    assert firebase.acknowledgements[-1]["active"] is True

    service._process_zone_test_command(command(requested=False, cancel=True))

    assert relay.off_count >= 2
    assert valves.active_valve_id is None
    assert valves.close_count == 1
    assert service._active_zone_test_request_id == ""
    assert firebase.acknowledgements[-1]["result"] == (
        "PHYSICAL_TEST_CANCELLED"
    )


def verify_active_zone_test_blocks_every_pump_path() -> None:
    service, relay, valves, firebase = service_with_fakes()

    service._process_zone_test_command(command(requested=True, cancel=False))
    relay.is_on = True

    blocked = service._enforce_zone_test_pump_lock(
        SimpleNamespace(relay=True),
    )

    assert blocked is True
    assert relay.is_on is False
    assert relay.off_count >= 2
    assert valves.active_valve_id == "valve-001"
    assert service._manual_relay_started_at == 0.0
    assert service._manual_relay_timeout_latched is False
    assert firebase.relay_commands == [False]
    assert firebase.relay_states == [False]

    service._active_zone_test_request_id = ""
    assert service._enforce_zone_test_pump_lock(
        SimpleNamespace(relay=True),
    ) is False


def verify_active_acknowledgement_preserves_cancel_request() -> None:
    reference = FakeReference()
    service = FirebaseService.__new__(FirebaseService)
    service._device_ref = lambda: reference

    service.acknowledge_zone_test(
        request_id="test-request",
        result="PHYSICAL_TEST_ACTIVE",
        active=True,
        remaining_seconds=5,
    )

    assert reference.updated is not None
    assert "cancel_requested" not in reference.updated

    service.acknowledge_zone_test(
        request_id="test-request",
        result="PHYSICAL_TEST_CANCELLED",
    )

    assert reference.updated is not None
    assert reference.updated["cancel_requested"] is False


def verify_restart_clears_stale_test_state() -> None:
    reference = FakeReference()
    service = FirebaseService.__new__(FirebaseService)
    service._device_ref = lambda: reference

    service.reset_zone_test_after_restart()

    assert reference.updated is not None
    assert reference.updated["requested"] is False
    assert reference.updated["cancel_requested"] is False
    assert reference.updated["active"] is False
    assert reference.updated["remaining_seconds"] == 0
    assert reference.updated["result"] == "SERVICE_RESTARTED"


def verify_restart_clears_stale_zone_watering_states() -> None:
    reference = FakeZoneStateReference(
        {
            "zone-001": {"zone_id": "zone-001"},
            "zone-002": {"zone_id": "zone-002"},
            "invalid": "ignored",
        },
    )
    service = FirebaseService.__new__(FirebaseService)
    service._device_ref = lambda: reference

    service.reset_all_zone_watering_states()

    assert reference.updated is not None
    assert (
        reference.updated[
            "zones/zone-001/irrigation_status/watering_active"
        ]
        is False
    )
    assert (
        reference.updated[
            "zones/zone-002/irrigation_status/watering_active"
        ]
        is False
    )
    assert (
        reference.updated[
            "zones/zone-001/irrigation_status/selected_for_watering"
        ]
        is False
    )
    assert not any("invalid" in path for path in reference.updated)


def main() -> None:
    verify_zone_test_does_not_block_cancellation()
    verify_active_zone_test_blocks_every_pump_path()
    verify_active_acknowledgement_preserves_cancel_request()
    verify_restart_clears_stale_test_state()
    verify_restart_clears_stale_zone_watering_states()
    print("[PASS] Zone test cancellation remains immediate and race-safe.")


if __name__ == "__main__":
    main()
