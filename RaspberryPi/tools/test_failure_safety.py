"""
Failure safety tests for pump and zone valves.
"""

from __future__ import annotations

import logging
import sys
import time
import types
from datetime import datetime
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

from services.irrigation_service import IrrigationService
from hardware.mqtt_sensor import MqttSensorReading
from hardware.sensor_provider import SoilMoistureSensorProvider


class FakeRelay:
    def __init__(self, *, fail_off: bool = False) -> None:
        self.is_on = True
        self.fail_off = fail_off
        self.off_attempted = False

    def off(self) -> None:
        self.off_attempted = True
        if self.fail_off:
            raise RuntimeError("relay failure")
        self.is_on = False


class FakeValves:
    def __init__(self, *, fail_close: bool = False) -> None:
        self.active_valve_id = "valve-001"
        self.fail_close = fail_close
        self.close_attempted = False

    def close_all(self) -> None:
        self.close_attempted = True
        if self.fail_close:
            raise RuntimeError("valve failure")
        self.active_valve_id = None


class StaleMqttSensor:
    stale_after_seconds = 30.0

    def __init__(self) -> None:
        self.reading = MqttSensorReading(
            sensor_id="soil-001",
            raw=2000,
            voltage=0.25,
            moisture=30,
            rssi=-60,
            received_at=datetime.now(),
            received_monotonic=time.monotonic() - 90,
        )

    def get_fresh_reading(self):
        return None

    def get_latest_reading(self):
        return self.reading


class InitializationProbe:
    def __init__(
        self,
        events: list[str],
        name: str,
        *,
        fail: bool = False,
    ) -> None:
        self._events = events
        self._name = name
        self._fail = fail

    def initialize(self) -> None:
        self._events.append(self._name)
        if self._fail:
            raise RuntimeError("sensor initialization failed")


def verify_actuators_initialize_before_sensor() -> None:
    events: list[str] = []
    service = IrrigationService.__new__(IrrigationService)
    service._relay = InitializationProbe(events, "relay")
    service._valves = InitializationProbe(events, "valves")
    service._sensor = InitializationProbe(
        events,
        "sensor",
        fail=True,
    )

    try:
        service.initialize()
        raise AssertionError("Sensor initialization failure was ignored.")
    except RuntimeError as exc:
        assert str(exc) == "sensor initialization failed"

    assert events == ["relay", "valves", "sensor"]


def service_with(
    relay: FakeRelay,
    valves: FakeValves,
) -> IrrigationService:
    service = IrrigationService.__new__(IrrigationService)
    service._relay = relay
    service._valves = valves
    service._logger = logging.getLogger("failure-safety-test")
    service._manual_relay_started_at = 10.0
    service._manual_relay_timeout_latched = True
    return service


def main() -> None:
    verify_actuators_initialize_before_sensor()

    provider = SoilMoistureSensorProvider.__new__(
        SoilMoistureSensorProvider,
    )
    provider._initialized = True
    provider._mode = "mqtt"
    provider._mqtt_sensor = StaleMqttSensor()

    try:
        provider.read()
        raise AssertionError("Stale sensor data was accepted.")
    except RuntimeError as exc:
        assert "stale" in str(exc).lower()

    relay = FakeRelay()
    valves = FakeValves()
    service = service_with(relay, valves)

    service._enter_fail_safe(reason="SENSOR_STALE")

    assert relay.off_attempted is True
    assert relay.is_on is False
    assert valves.close_attempted is True
    assert valves.active_valve_id is None
    assert service._manual_relay_started_at == 0.0
    assert service._manual_relay_timeout_latched is False

    failing_relay = FakeRelay(fail_off=True)
    independent_valves = FakeValves()
    service = service_with(failing_relay, independent_valves)

    service._enter_fail_safe(reason="FIREBASE_ERROR")

    assert failing_relay.off_attempted is True
    assert independent_valves.close_attempted is True
    assert independent_valves.active_valve_id is None

    independent_relay = FakeRelay()
    failing_valves = FakeValves(fail_close=True)
    service = service_with(independent_relay, failing_valves)

    service._enter_fail_safe(reason="VALVE_ERROR")

    assert independent_relay.off_attempted is True
    assert independent_relay.is_on is False
    assert failing_valves.close_attempted is True

    print(
        "[PASS] Sensor and Firebase failure safety scenarios.",
    )


if __name__ == "__main__":
    main()
