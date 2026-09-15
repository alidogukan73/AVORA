"""
Service-level selection test for multi-zone automatic irrigation.
"""

from __future__ import annotations

import io
import logging
import sys
import types
from pathlib import Path


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

from controllers.multi_zone_decision_engine import (
    MultiZoneDecisionEngine,
)
from controllers.zone_irrigation_scheduler import (
    ZoneIrrigationScheduler,
)
from controllers.weather_irrigation_policy import (
    WeatherIrrigationPolicy,
)
from controllers.optimal_irrigation_time_engine import (
    OptimalIrrigationTimeEngine,
)
from models.command_state import CommandState
from models.sensor_reading import SensorReading
from services.irrigation_service import IrrigationService


class FakeFirebase:
    def __init__(self) -> None:
        self.states = {}
        self.ai_states = {}
        self.garden_summary = {}
        self.history_requests = []
        self.decision_update_count = 0
        self.configs = {
            "soil-001": self._zone(
                "zone-001",
                "valve-001",
                1,
            ),
            "soil-002": self._zone(
                "zone-002",
                "valve-002",
                2,
            ),
        }

    @staticmethod
    def _zone(
        zone_id: str,
        valve_id: str,
        order: int,
    ) -> dict:
        return {
            "zone_id": zone_id,
            "valve_id": valve_id,
            "order": order,
            "enabled": True,
            "irrigation_enabled": True,
            "moisture_limit": 40,
            "pump_duration": 10,
            "cooldown_seconds": 600,
            "restart_delta": 10,
        }

    def get_all_zone_configs_by_sensor(self) -> dict:
        return self.configs

    def get_recent_watering_records(
        self,
        limit: int = 30,
        sensor_id: str | None = None,
        zone_id: str | None = None,
    ) -> list:
        self.history_requests.append((sensor_id, zone_id))
        return []

    def update_zone_irrigation_decisions(
        self,
        states: dict,
    ) -> None:
        self.states = states
        self.decision_update_count += 1

    def update_zone_irrigation_safety_state(self, **_kwargs) -> None:
        return None

    def update_zone_ai_states(
        self,
        states: dict,
        garden_summary: dict,
        cleared_zone_ids: set[str] | None = None,
    ) -> None:
        self.ai_states = states
        self.garden_summary = garden_summary
        self.cleared_zone_ids = set(cleared_zone_ids or set())


class FakeExecutor:
    def __init__(self) -> None:
        self.cooldown_remaining = 0

    def is_cooldown_active(self, _zone_id: str) -> bool:
        return False

    def cooldown_remaining_for(self, _zone_id: str) -> int:
        return self.cooldown_remaining

    def cooldown_until_epoch_for(self, _zone_id: str) -> int:
        return 0


class FakeValves:
    @staticmethod
    def is_physical_valve(_valve_id: str) -> bool:
        return True


def reading(sensor_id: str, moisture: int) -> SensorReading:
    return SensorReading(
        raw=2000,
        voltage=0.25,
        moisture=moisture,
        sensor_id=sensor_id,
        firmware="test",
        rssi=-60,
        uptime_seconds=100,
    )


def main() -> None:
    service = IrrigationService.__new__(IrrigationService)
    service._firebase = FakeFirebase()
    service._multi_zone_engine = MultiZoneDecisionEngine()
    service._zone_scheduler = ZoneIrrigationScheduler()
    service._zone_executor = FakeExecutor()
    service._valves = FakeValves()
    service._weather_policy = WeatherIrrigationPolicy()
    service._irrigation_time_engine = OptimalIrrigationTimeEngine()
    service._latest_weather_forecast = None
    service._weather_adjustments_by_zone = {}
    service._zone_ai_pipelines = {}
    service._zone_prediction_validation_queues = {}
    service._zone_prediction_histories = {}
    service._zone_ai_season_ids = {}
    service._last_zone_ai_update = 0.0
    service._ai_decision_interval_seconds = 30
    service._prediction_history_limit = 100
    service._last_multi_zone_status_signature = None
    service._last_multi_zone_log_signature = None
    service._last_zone_config_signatures = {}
    service._logger = logging.getLogger("zone-selection-test")
    service._logger.setLevel(logging.INFO)
    log_output = io.StringIO()
    log_handler = logging.StreamHandler(log_output)
    service._logger.addHandler(log_handler)

    commands = CommandState(
        enabled=True,
        auto_mode=True,
        moisture_limit=40,
        pump_duration=10,
        cooldown_seconds=600,
        restart_delta=10,
    )
    readings = {
        "soil-001": reading("soil-001", 32),
        "soil-002": reading("soil-002", 24),
    }

    selected = None
    for _ in range(5):
        selected = service._update_multi_zone_decisions(
            readings=readings,
            global_commands=commands,
        )

    assert selected is not None
    assert selected.candidate.zone_id == "zone-002"
    assert service._firebase.states["zone-002"][
        "selected_for_watering"
    ] is True
    assert service._firebase.states["zone-002"][
        "queue_position"
    ] == 1

    zone_one_ai = service._firebase.ai_states["zone-001"]
    zone_two_ai = service._firebase.ai_states["zone-002"]
    assert zone_one_ai["sensor_id"] == "soil-001"
    assert zone_two_ai["sensor_id"] == "soil-002"
    assert zone_one_ai["moisture_prediction"][
        "current_moisture"
    ] == 32
    assert zone_two_ai["moisture_prediction"][
        "current_moisture"
    ] == 24
    for zone_ai in (zone_one_ai, zone_two_ai):
        assert {
            "decision",
            "explanation",
            "moisture_prediction",
            "prediction_accuracy",
            "confidence",
            "learning_profile",
        }.issubset(zone_ai)
    assert service._firebase.garden_summary["total_zones"] == 2
    assert service._firebase.garden_summary["analyzed_zones"] == 2

    assert ("soil-001", "zone-001") in service._firebase.history_requests
    assert ("soil-002", "zone-002") in service._firebase.history_requests
    assert all(zone_id for _, zone_id in service._firebase.history_requests)

    # Changing the crops in an existing physical zone resets transient AI
    # state before the new duration plan is computed. The plan must still be
    # available later in this same decision cycle.
    service._firebase.configs["soil-001"]["season"] = {
        "status": "ACTIVE",
        "active_season_id": "tomato-season",
        "active_season_ids": {
            "tomato-season": True,
            "pepper-season": True,
        },
    }
    service._last_zone_ai_update = 0.0
    after_crop_change = service._update_multi_zone_decisions(
        readings=readings,
        global_commands=commands,
    )
    assert after_crop_change is not None
    assert "zone-001" in service._watering_duration_plans_by_zone
    assert service._zone_ai_season_ids["zone-001"] == (
        "pepper-season|tomato-season"
    )

    # A newly configured hardware zone remains available for telemetry, but
    # must not enter irrigation/AI decisions until its season is started.
    pre_season = service._firebase._zone(
        "zone-003",
        "valve-003",
        3,
    )
    pre_season["season"] = {
        "status": "CLOSED",
        "active_season_id": "",
    }
    service._firebase.configs["soil-003"] = pre_season
    readings["soil-003"] = reading("soil-003", 18)

    after_zone_creation = service._update_multi_zone_decisions(
        readings=readings,
        global_commands=commands,
    )
    assert after_zone_creation is not None
    assert after_zone_creation.candidate.zone_id == "zone-002"
    assert "zone-003" not in service._firebase.states
    assert "zone-003" not in service._firebase.ai_states
    assert ("soil-003", "zone-003") not in service._firebase.history_requests

    repeated = service._update_multi_zone_decisions(
        readings=readings,
        global_commands=commands,
    )
    assert repeated is not None
    assert repeated.candidate.zone_id == "zone-002"

    updates_before_countdown = (
        service._firebase.decision_update_count
    )
    decision_logs_before_countdown = log_output.getvalue().count(
        "Multi-zone decisions updated."
    )
    service._zone_executor.cooldown_remaining = 119
    countdown_update = service._update_multi_zone_decisions(
        readings=readings,
        global_commands=commands,
    )
    assert countdown_update is not None
    assert service._firebase.decision_update_count == (
        updates_before_countdown + 1
    )

    settings_logs = log_output.getvalue().count(
        "Zone irrigation settings applied."
    )
    assert settings_logs == 2
    decision_logs = log_output.getvalue().count(
        "Multi-zone decisions updated."
    )
    assert decision_logs_before_countdown > 0
    assert decision_logs == decision_logs_before_countdown
    service._logger.removeHandler(log_handler)
    log_handler.close()

    print(
        "[PASS] Service selected the driest eligible zone.",
    )
    print("[PASS] Zone AI states stayed independent.")


if __name__ == "__main__":
    main()
