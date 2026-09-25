"""Verify only installed valves may start the shared pump."""

from __future__ import annotations

import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.hardware_test_stubs import install_hardware_import_stubs

install_hardware_import_stubs()

import hardware.valve_controller as valve_module
from core.config import RelayConfig, ValveConfig
from hardware.valve_controller import ValveController


def main() -> None:
    assert ValveConfig.ACTIVE_LOW is True
    # The pump uses its separate, already verified single-channel relay.
    assert RelayConfig.ACTIVE_LOW is False

    output_events = []
    original_output = valve_module.GPIO.output
    original_sleep = valve_module.time.sleep
    valve_module.GPIO.output = (
        lambda pin, level: output_events.append((pin, level))
    )
    valve_module.time.sleep = lambda _seconds: None

    valves = ValveController()

    try:
        valves.initialize()
        initialized_levels = dict(output_events)
        assert initialized_levels == {
            pin: valve_module.GPIO.HIGH
            for pin in ValveConfig.GPIO_PINS.values()
        }
        valves.open("valve-001")
        assert output_events[-1] == (
            ValveConfig.GPIO_PINS["valve-001"],
            valve_module.GPIO.LOW,
        )
        valves.close_all()
        closed_levels = dict(output_events)
        assert closed_levels == {
            pin: valve_module.GPIO.HIGH
            for pin in ValveConfig.GPIO_PINS.values()
        }

        failed_pin = ValveConfig.GPIO_PINS["valve-003"]
        close_attempts: list[tuple[int, int]] = []

        def fail_one_close(pin: int, level: int) -> None:
            close_attempts.append((pin, level))
            if pin == failed_pin:
                raise RuntimeError("simulated GPIO failure")

        valves._active_valve_id = "valve-001"
        valves._active_valve_opened_at = time.monotonic()
        valve_module.GPIO.output = fail_one_close

        try:
            valves.close_all()
            raise AssertionError("Valve GPIO failure was ignored.")
        except RuntimeError as exc:
            assert str(failed_pin) in str(exc)

        assert len(close_attempts) == len(ValveConfig.GPIO_PINS)
        assert {
            pin for pin, _level in close_attempts
        } == set(ValveConfig.GPIO_PINS.values())
        assert all(
            level == valve_module.GPIO.HIGH
            for _pin, level in close_attempts
        )
        assert valves.active_valve_id is None
    finally:
        valve_module.GPIO.output = original_output
        valve_module.time.sleep = original_sleep

    assert valves.is_physical_valve("valve-001") is True
    assert valves.is_simulated_valve("valve-001") is False
    valves._active_valve_id = "valve-001"
    valves._active_valve_opened_at = time.monotonic()
    assert valves.is_ready_for_pump("valve-001") is False
    valves._active_valve_opened_at -= (
        ValveConfig.OPENING_DELAY_SECONDS + 0.1
    )
    assert valves.is_ready_for_pump("valve-001") is True
    assert valves.is_ready_for_pump("valve-002") is False

    for valve_id in ValveConfig.GPIO_PINS:
        assert valve_id in ValveConfig.GPIO_PINS
        assert valves.is_physical_valve(valve_id) is True
        assert valves.is_simulated_valve(valve_id) is False

    print("[PASS] All eight LOW-trigger valve channels start safely OFF.")


if __name__ == "__main__":
    main()
