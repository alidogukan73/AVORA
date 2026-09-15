"""Regression checks for the bounded Android manual-watering command."""

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

from controllers.watering_controller import WateringController
from core.firebase_service import FirebaseService
from core.config import IrrigationConfig
from models.command_state import CommandState
from models.watering_result import WateringResult
from services.irrigation_service import IrrigationService


class FakeRelay:
    def __init__(self) -> None:
        self.is_on = False
        self.on_count = 0
        self.off_count = 0

    def on(self) -> None:
        self.is_on = True
        self.on_count += 1

    def off(self) -> None:
        self.is_on = False
        self.off_count += 1


class FakeValves:
    def __init__(self, *, physical: bool = True) -> None:
        self.physical = physical
        self.active_valve_id: str | None = None
        self.close_count = 0

    def is_physical_valve(self, valve_id: str | None) -> bool:
        return self.physical and valve_id in {"valve-001", "valve-006"}

    def is_simulated_valve(self, valve_id: str | None) -> bool:
        return not self.is_physical_valve(valve_id)

    def open(self, valve_id: str) -> None:
        self.active_valve_id = valve_id

    def close_all(self) -> None:
        self.active_valve_id = None
        self.close_count += 1

    @staticmethod
    def is_ready_for_pump(_valve_id: str | None = None) -> bool:
        return False


class FakeFirebase:
    def __init__(self, command: CommandState, *, manual_limit: int = 4 * 60 * 60) -> None:
        self.command_state = command
        self.manual_limit = manual_limit
        self.acknowledgements: list[dict[str, object]] = []
        self.saved_records = []
        self.valve_states = []
        self.zone_watering_states = []
        self.relay_states = []
        self.relay_commands = []

    @staticmethod
    def get_all_zone_configs_by_sensor() -> dict[str, dict]:
        return {
            "soil-001": {
                "zone_id": "zone-001",
                "valve_id": "valve-001",
                "enabled": True,
            },
            "soil-006": {
                "zone_id": "zone-003",
                "valve_id": "valve-006",
                "enabled": True,
            },
        }

    @staticmethod
    def get_zone_valve_config(zone_id: str) -> dict | None:
        configs = {
            "zone-001": {
                "zone_id": "zone-001",
                "sensor_id": "soil-001",
                "valve_id": "valve-001",
                "enabled": True,
            },
            "zone-003": {
                "zone_id": "zone-003",
                "sensor_id": "soil-006",
                "valve_id": "valve-006",
                "enabled": True,
            },
        }
        value = configs.get(zone_id)
        return dict(value) if value is not None else None

    def get_weather_irrigation_settings(self) -> dict:
        return {"manual_watering_max_duration_seconds": self.manual_limit}

    def acknowledge_manual_watering(self, **values) -> None:
        self.acknowledgements.append(values)

    def set_relay_command(self, value: bool) -> None:
        self.relay_commands.append(value)

    def update_relay_status(self, value: bool) -> None:
        self.relay_states.append(value)

    def update_zone_watering_active(self, zone_id: str, active: bool) -> None:
        self.zone_watering_states.append((zone_id, active))

    def update_active_zone_valve(self, *values) -> None:
        self.valve_states.append(values)

    @staticmethod
    def get_zone_active_season_ids(_zone_id: str) -> tuple[str, ...]:
        return ("season-001",)

    def save_watering(self, *, result, record) -> None:
        self.saved_records.append((result, record))

    @staticmethod
    def update_zone_cooldown(**_values) -> None:
        return None


class FakeSensor:
    @staticmethod
    def get_fresh_readings():
        return {
            "soil-001": SimpleNamespace(moisture=52),
            "soil-006": SimpleNamespace(moisture=48),
        }


class FakeExecutor:
    def __init__(self) -> None:
        self.execute_count = 0

    def execute(self, **values) -> WateringResult:
        self.execute_count += 1
        values["on_valve_changed"](values["valve_id"], True)
        values["on_relay_changed"](True)
        values["on_progress"]()
        values["on_relay_changed"](False)
        values["on_valve_changed"](None, False)
        return WateringResult(
            completed=True,
            stop_reason="COMPLETED",
            duration=values["duration"],
        )

    @staticmethod
    def cooldown_until_epoch_for(_zone_id: str) -> int:
        return 100

    @staticmethod
    def cooldown_remaining_for(_zone_id: str) -> int:
        return 60


def manual_command(**changes) -> CommandState:
    values = {
        "manual_watering_requested": True,
        "manual_watering_request_id": "request-001",
        "manual_watering_zone_id": "zone-001",
        "manual_watering_valve_id": "valve-001",
        "manual_watering_duration": 30,
        "manual_watering_requested_at_ms": 1,
        "enabled": True,
    }
    values.update(changes)
    return CommandState(**values)


def service_with_fakes(
    command: CommandState,
    *,
    physical: bool = True,
    manual_limit: int = 4 * 60 * 60,
):
    service = IrrigationService.__new__(IrrigationService)
    service._relay = FakeRelay()
    service._valves = FakeValves(physical=physical)
    service._firebase = FakeFirebase(command, manual_limit=manual_limit)
    service._sensor = FakeSensor()
    service._zone_executor = FakeExecutor()
    service._last_manual_watering_request_id = ""
    service._active_zone_test_request_id = ""
    service._is_recent_command = lambda *_args, **_kwargs: True
    service._update_status_if_needed = lambda: None
    service._logger = SimpleNamespace(
        info=lambda *_args: None,
        warning=lambda *_args: None,
        exception=lambda *_args: None,
    )
    return service


def verify_complete_manual_cycle() -> None:
    service = service_with_fakes(manual_command(auto_mode=False))
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._zone_executor.execute_count == 1
    assert service._firebase.relay_commands == [False]
    assert [item["result"] for item in service._firebase.acknowledgements] == [
        "PREPARING_VALVE",
        "WATERING",
        "COMPLETED",
    ]
    assert service._firebase.zone_watering_states == [
        ("zone-001", True),
        ("zone-001", False),
    ]
    assert service._firebase.saved_records[0][1].mode == "MANUAL"
    assert service._firebase.saved_records[0][1].season_id == "season-001"


def verify_cancelled_request_never_arms_hardware() -> None:
    service = service_with_fakes(manual_command(
        manual_watering_cancel_requested=True,
    ))
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._zone_executor.execute_count == 0
    assert service._relay.on_count == 0
    assert service._firebase.acknowledgements[-1]["result"] == "CANCELLED"


def verify_second_installed_zone_can_water() -> None:
    service = service_with_fakes(manual_command(
        manual_watering_zone_id="zone-003",
        manual_watering_valve_id="valve-006",
    ))
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._zone_executor.execute_count == 1
    assert service._firebase.valve_states[0] == (
        "valve-006",
        True,
        "zone-003",
        "valve-006",
        True,
        False,
    )
    assert service._firebase.saved_records[0][1].zone_id == "zone-003"


def verify_configurable_duration_reaches_executor() -> None:
    service = service_with_fakes(manual_command(
        manual_watering_duration=12 * 60 * 60,
    ), manual_limit=12 * 60 * 60)
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._firebase.saved_records[0][1].duration == 12 * 60 * 60
    assert IrrigationConfig.MAX_MANUAL_PUMP_DURATION_SECONDS == 12 * 60 * 60


def verify_admin_limit_is_authoritative() -> None:
    service = service_with_fakes(manual_command(
        manual_watering_duration=8 * 60 * 60,
    ), manual_limit=4 * 60 * 60)
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._firebase.saved_records[0][1].duration == 4 * 60 * 60


def verify_unapproved_valve_never_arms_hardware() -> None:
    service = service_with_fakes(manual_command(), physical=False)
    assert service._process_manual_watering_command(
        service._firebase.command_state,
    )
    assert service._zone_executor.execute_count == 0
    assert service._relay.on_count == 0
    assert service._firebase.acknowledgements[-1]["result"] == (
        "PHYSICAL_VALVE_REQUIRED"
    )


def verify_opening_wait_is_immediately_cancellable() -> None:
    relay = FakeRelay()
    valves = FakeValves()
    controller = WateringController(relay, valves)
    progress = []
    result = controller.water_zone(
        valve_id="valve-001",
        duration=30,
        get_commands=lambda: CommandState(enabled=False),
        on_progress=lambda: progress.append(True),
    )
    assert not result.completed
    assert result.stop_reason == "SYSTEM_DISABLED"
    assert relay.on_count == 0
    assert valves.active_valve_id is None
    assert progress


def verify_firebase_can_approve_only_deployed_gpio() -> None:
    service = FirebaseService.__new__(FirebaseService)
    service._zone_config_by_sensor_id = {
        "soil-001": {"valve_id": "valve-001", "valve_mode": "PHYSICAL"},
        "soil-006": {"valve_id": "valve-006", "valve_mode": "PHYSICAL"},
        "soil-008": {"valve_id": "valve-008", "valve_mode": "PHYSICAL"},
        "soil-999": {"valve_id": "valve-999", "valve_mode": "PHYSICAL"},
    }
    assert service.get_physical_valve_ids() == {
        "valve-001",
        "valve-006",
        "valve-008",
    }


def main() -> None:
    verify_complete_manual_cycle()
    verify_cancelled_request_never_arms_hardware()
    verify_second_installed_zone_can_water()
    verify_configurable_duration_reaches_executor()
    verify_admin_limit_is_authoritative()
    verify_unapproved_valve_never_arms_hardware()
    verify_opening_wait_is_immediately_cancellable()
    verify_firebase_can_approve_only_deployed_gpio()
    print("[PASS] Manual watering remains bounded, cancellable and hardware-safe.")


if __name__ == "__main__":
    main()
