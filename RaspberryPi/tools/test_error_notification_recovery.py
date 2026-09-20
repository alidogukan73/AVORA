"""Regression checks for stable device-error recovery notifications."""

from __future__ import annotations

import logging
import sys
import time
import types
from pathlib import Path
from unittest.mock import patch


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

try:
    import paho.mqtt.client  # noqa: F401
except ModuleNotFoundError:
    paho = types.ModuleType("paho")
    paho_mqtt = types.ModuleType("paho.mqtt")
    mqtt_client = types.ModuleType("paho.mqtt.client")
    paho.mqtt = paho_mqtt
    paho_mqtt.client = mqtt_client
    sys.modules["paho"] = paho
    sys.modules["paho.mqtt"] = paho_mqtt
    sys.modules["paho.mqtt.client"] = mqtt_client

try:
    import psutil  # noqa: F401
except ModuleNotFoundError:
    sys.modules["psutil"] = types.ModuleType("psutil")

try:
    import board  # noqa: F401
    import busio  # noqa: F401
    import adafruit_ads1x15  # noqa: F401
except ModuleNotFoundError:
    board = types.ModuleType("board")
    busio = types.ModuleType("busio")
    adafruit = types.ModuleType("adafruit_ads1x15")
    ads1x15 = types.ModuleType("adafruit_ads1x15.ads1x15")
    ads1115 = types.ModuleType("adafruit_ads1x15.ads1115")
    analog_in = types.ModuleType("adafruit_ads1x15.analog_in")
    ads1115.ADS1115 = type("ADS1115", (), {})
    analog_in.AnalogIn = type("AnalogIn", (), {})
    adafruit.ads1x15 = ads1x15
    sys.modules["board"] = board
    sys.modules["busio"] = busio
    sys.modules["adafruit_ads1x15"] = adafruit
    sys.modules["adafruit_ads1x15.ads1x15"] = ads1x15
    sys.modules["adafruit_ads1x15.ads1115"] = ads1115
    sys.modules["adafruit_ads1x15.analog_in"] = analog_in

from services.irrigation_service import IrrigationService


class FakeFirebase:
    def __init__(self) -> None:
        self.clear_count = 0

    def clear_error(self) -> None:
        self.clear_count += 1


def service_with_active_error() -> IrrigationService:
    service = IrrigationService.__new__(IrrigationService)
    service._firebase = FakeFirebase()
    service._logger = logging.getLogger("error-recovery-test")
    service._update_error_active = True
    service._last_update_error_log = 10.0
    service._update_recovery_started_at = 0.0
    service._update_recovery_success_count = 0
    service._update_recovery_confirmation_seconds = 120.0
    service._update_recovery_required_successes = 3
    return service


def main() -> None:
    service = service_with_active_error()

    with patch(
        "services.irrigation_service.time.monotonic",
        return_value=1_000.0,
    ):
        service._mark_update_cycle_recovered()
    assert service._update_error_active is True
    assert service._firebase.clear_count == 0

    service._update_recovery_started_at = 879.0
    service._update_recovery_success_count = 2
    with patch(
        "services.irrigation_service.time.monotonic",
        return_value=1_000.0,
    ):
        service._mark_update_cycle_recovered()

    assert service._update_error_active is False
    assert service._firebase.clear_count == 1
    assert service._update_recovery_started_at == 0.0
    assert service._update_recovery_success_count == 0

    print("Error notification recovery debounce tests passed.")


if __name__ == "__main__":
    main()
