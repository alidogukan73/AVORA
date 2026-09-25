"""Actuator-level hard timeout checks for the irrigation pump relay."""

from __future__ import annotations

import sys
import time
import types
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

rpi = types.ModuleType("RPi")
gpio = types.ModuleType("RPi.GPIO")
gpio.BCM = 11
gpio.OUT = 1
gpio.HIGH = 1
gpio.LOW = 0
gpio.setmode = lambda _mode: None
gpio.setwarnings = lambda _enabled: None
gpio.setup = lambda _pin, _mode: None
gpio.output = lambda _pin, _level: None
rpi.GPIO = gpio
sys.modules["RPi"] = rpi
sys.modules["RPi.GPIO"] = gpio

import hardware.relay as relay_module
from controllers.watering_controller import WateringController
from core.config import IrrigationConfig, RelayConfig
from hardware.relay import RelayController
from models.command_state import CommandState


class FakeValves:
    def __init__(self) -> None:
        self.active_valve_id: str | None = None
        self.close_count = 0

    def open(self, valve_id: str) -> None:
        self.active_valve_id = valve_id

    @staticmethod
    def is_simulated_valve(_valve_id: str) -> bool:
        return False

    @staticmethod
    def is_ready_for_pump(_valve_id: str) -> bool:
        return True

    def close_all(self) -> None:
        self.active_valve_id = None
        self.close_count += 1


def main() -> None:
    assert RelayConfig.MAX_CONTINUOUS_RUN_SECONDS == 5 * 60 * 60
    assert IrrigationConfig.MAX_PUMP_DURATION_SECONDS == 5 * 60 * 60

    original_output = relay_module.GPIO.output
    original_maximum = RelayConfig.MAX_CONTINUOUS_RUN_SECONDS
    output_events: list[tuple[int, int]] = []
    relay_module.GPIO.output = (
        lambda pin, level: output_events.append((pin, level))
    )
    RelayConfig.MAX_CONTINUOUS_RUN_SECONDS = 0.05

    relay = RelayController()
    try:
        relay.initialize()
        relay.on()
        assert relay.is_on is True

        deadline = time.monotonic() + 1.0
        while relay.is_on and time.monotonic() < deadline:
            time.sleep(0.01)

        assert relay.is_on is False
        assert relay.hard_timeout_latched is True
        assert output_events[-1] == (
            RelayConfig.GPIO_PIN,
            relay_module.GPIO.LOW,
        )

        try:
            relay.on()
            raise AssertionError("A latched hard timeout allowed restart.")
        except RuntimeError as exc:
            assert "latched" in str(exc)

        relay.off()
        assert relay.hard_timeout_latched is False

        relay.on()
        relay.off()
        event_count_after_off = len(output_events)
        time.sleep(0.1)
        assert len(output_events) == event_count_after_off
        assert relay.is_on is False

        valves = FakeValves()
        controller = WateringController(relay, valves)
        result = controller.water_zone(
            valve_id="valve-001",
            duration=2,
            get_commands=lambda: CommandState(
                enabled=True,
                auto_mode=True,
            ),
        )
        assert result.completed is False
        assert result.stop_reason == "HARD_SAFETY_TIMEOUT"
        assert relay.is_on is False
        assert valves.active_valve_id is None
        assert valves.close_count == 1
    finally:
        relay.cleanup()
        RelayConfig.MAX_CONTINUOUS_RUN_SECONDS = original_maximum
        relay_module.GPIO.output = original_output

    print("[PASS] Pump relay hard timeout is local, latched and cancellable.")


if __name__ == "__main__":
    main()
