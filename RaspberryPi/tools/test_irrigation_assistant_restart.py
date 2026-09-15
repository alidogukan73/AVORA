"""
Safety tests for the selected-zone irrigation-assistant reset command.
"""

from __future__ import annotations

import logging
import sys
import time
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

from controllers.multi_zone_decision_engine import MultiZoneDecisionEngine
from services.irrigation_service import IrrigationService


class FakeFirebase:
    def __init__(self) -> None:
        self.acks: list[dict] = []
        self.safety_updates: list[dict] = []
        self.configs = {
            "soil-001": {
                "zone_id": "zone-001",
                "sensor_id": "soil-001",
            },
            "soil-002": {
                "zone_id": "zone-002",
                "sensor_id": "soil-002",
            },
            "soil-003": {
                "zone_id": "zone-003",
                "sensor_id": "soil-003",
            },
        }

    def get_all_zone_configs_by_sensor(self) -> dict[str, dict]:
        return self.configs

    def acknowledge_irrigation_assistant_reset(self, **payload) -> None:
        self.acks.append(payload)

    def update_zone_irrigation_safety_state(self, **payload) -> None:
        self.safety_updates.append(payload)

    def update_prediction_validation_status(self, _status) -> None:
        return


class FakeRelay:
    def __init__(self, is_on: bool = False) -> None:
        self.is_on = is_on


class FakeExecutor:
    active_zone_id = None


class FakeResettableEngine:
    def __init__(self) -> None:
        self.reset_count = 0

    def reset(self) -> None:
        self.reset_count += 1


class FakeQueue:
    def __init__(self) -> None:
        self.cancelled = 0

    def cancel_all(self) -> int:
        self.cancelled += 1
        return 1

    def get_status(self) -> SimpleNamespace:
        return SimpleNamespace(
            validation_status="IDLE",
            pending_count=0,
            remaining_seconds=0,
        )


def command(
    request_id: str,
    zone_id: str = "zone-002",
    zone_ids: tuple[str, ...] = (),
) -> SimpleNamespace:
    return SimpleNamespace(
        irrigation_assistant_reset_requested=True,
        irrigation_assistant_reset_request_id=request_id,
        irrigation_assistant_reset_zone_id=zone_id,
        irrigation_assistant_reset_zone_ids=zone_ids,
        irrigation_assistant_reset_requested_at_ms=int(time.time() * 1000),
    )


def service_with(
    relay_on: bool = False,
) -> tuple[IrrigationService, dict[str, FakeQueue]]:
    service = IrrigationService.__new__(IrrigationService)
    service._firebase = FakeFirebase()
    service._relay = FakeRelay(relay_on)
    service._zone_executor = FakeExecutor()
    service._active_zone_test_request_id = ""
    service._last_irrigation_assistant_reset_request_id = ""
    service._multi_zone_engine = MultiZoneDecisionEngine()
    for sensor_id in ("soil-001", "soil-002", "soil-003"):
        service._multi_zone_engine.restore_safety_state(
            sensor_id,
            completed_watering_cycles=3,
            waiting_for_moisture_recovery=True,
        )
    queues = {
        "zone-001": FakeQueue(),
        "zone-002": FakeQueue(),
        "zone-003": FakeQueue(),
    }
    service._zone_prediction_validation_queues = queues
    service._smart_engine = FakeResettableEngine()
    service._prediction_validation_queue = FakeQueue()
    service._last_zone_ai_update = 45.0
    service._last_ai_decision_update = 45.0
    service._last_prediction_validation_status_update = 45.0
    service._logger = logging.getLogger("assistant-restart-test")
    return service, queues


def main() -> None:
    service, queues = service_with()
    service._process_irrigation_assistant_reset_command(
        command("reset-success"),
    )

    state = service._multi_zone_engine.get_safety_state("soil-002")
    assert state["completed_watering_cycles"] == 0
    assert state["waiting_for_moisture_recovery"] is False
    assert queues["zone-001"].cancelled == 0
    assert queues["zone-002"].cancelled == 1
    assert queues["zone-003"].cancelled == 0
    assert service._smart_engine.reset_count == 0
    assert service._prediction_validation_queue.cancelled == 0
    assert service._last_zone_ai_update == 0.0
    assert service._last_ai_decision_update == 0.0
    assert service._last_prediction_validation_status_update == 0.0
    assert service._firebase.acks[-1]["result"] == "COMPLETED"
    assert service._firebase.safety_updates[-1][
        "completed_watering_cycles"
    ] == 0

    multi_service, multi_queues = service_with()
    multi_service._process_irrigation_assistant_reset_command(
        command(
            "reset-multiple",
            "zone-001",
            ("zone-001", "zone-003"),
        ),
    )
    assert multi_service._multi_zone_engine.get_safety_state(
        "soil-001"
    )["completed_watering_cycles"] == 0
    assert multi_service._multi_zone_engine.get_safety_state(
        "soil-002"
    )["completed_watering_cycles"] == 3
    assert multi_service._multi_zone_engine.get_safety_state(
        "soil-003"
    )["completed_watering_cycles"] == 0
    assert multi_queues["zone-001"].cancelled == 1
    assert multi_queues["zone-002"].cancelled == 0
    assert multi_queues["zone-003"].cancelled == 1
    assert multi_service._firebase.acks[-1]["result"] == "COMPLETED_MULTIPLE"
    assert multi_service._firebase.acks[-1]["zone_ids"] == (
        "zone-001",
        "zone-003",
    )

    atomic_service, atomic_queues = service_with()
    atomic_service._process_irrigation_assistant_reset_command(
        command(
            "reset-invalid-multiple",
            "zone-001",
            ("zone-001", "zone-999"),
        ),
    )
    for sensor_id in ("soil-001", "soil-002", "soil-003"):
        assert atomic_service._multi_zone_engine.get_safety_state(
            sensor_id
        )["completed_watering_cycles"] == 3
    assert all(queue.cancelled == 0 for queue in atomic_queues.values())
    assert atomic_service._firebase.acks[-1]["result"] == "ZONE_NOT_FOUND"

    all_service, all_queues = service_with()
    all_service._process_irrigation_assistant_reset_command(
        command("reset-all", "ALL"),
    )

    for sensor_id in ("soil-001", "soil-002", "soil-003"):
        all_state = all_service._multi_zone_engine.get_safety_state(
            sensor_id
        )
        assert all_state["completed_watering_cycles"] == 0
        assert all_state["waiting_for_moisture_recovery"] is False
    assert all_queues["zone-001"].cancelled == 1
    assert all_queues["zone-002"].cancelled == 1
    assert all_queues["zone-003"].cancelled == 1
    assert all_service._smart_engine.reset_count == 1
    assert all_service._prediction_validation_queue.cancelled == 1
    assert all_service._firebase.acks[-1]["result"] == "COMPLETED_ALL"
    assert len(all_service._firebase.safety_updates) == 3

    active_service, active_queues = service_with(relay_on=True)
    active_service._process_irrigation_assistant_reset_command(
        command("reset-rejected"),
    )

    active_state = active_service._multi_zone_engine.get_safety_state(
        "soil-002"
    )
    assert active_state["waiting_for_moisture_recovery"] is True
    assert active_queues["zone-001"].cancelled == 0
    assert active_queues["zone-002"].cancelled == 0
    assert active_queues["zone-003"].cancelled == 0
    assert active_service._smart_engine.reset_count == 0
    assert active_service._firebase.acks[-1]["result"] == "WATERING_ACTIVE"

    print(
        "[PASS] Irrigation assistant restart safety scenarios.",
    )


if __name__ == "__main__":
    main()
