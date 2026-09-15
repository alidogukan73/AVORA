"""
Safety scenarios for shared pump zone execution.
"""

from __future__ import annotations

import sys
import time
import types
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Never allow a regression test to touch the Raspberry Pi GPIO pins. The
# executor still sees a physical-valve configuration, but every GPIO call is
# captured by this in-memory module.
rpi = types.ModuleType("RPi")
gpio = types.ModuleType("RPi.GPIO")
gpio.BCM = 11
gpio.OUT = 1
gpio.HIGH = 1
gpio.LOW = 0
gpio.pin_values = {}
gpio.setmode = lambda _mode: None
gpio.setwarnings = lambda _enabled: None
gpio.setup = lambda pin, _mode: gpio.pin_values.setdefault(pin, gpio.HIGH)
gpio.output = lambda pin, value: gpio.pin_values.__setitem__(pin, value)
gpio.cleanup = lambda *_args: gpio.pin_values.clear()
rpi.GPIO = gpio
sys.modules["RPi"] = rpi
sys.modules["RPi.GPIO"] = gpio

from controllers.shared_pump_zone_executor import (
    SharedPumpZoneExecutor,
)
from hardware.valve_controller import ValveController
from models.command_state import CommandState
from models.watering_result import WateringResult


class FakePump:
    def __init__(self) -> None:
        self.is_on = False
        self.on_count = 0

    def on(self) -> None:
        self.is_on = True
        self.on_count += 1

    def off(self) -> None:
        self.is_on = False


class CompletedWateringController:
    def water_zone(self, **_kwargs) -> WateringResult:
        return WateringResult(
            completed=True,
            stop_reason="COMPLETED",
            duration=10,
        )

    @property
    def state(self):
        return types.SimpleNamespace(value="COOLDOWN")


def main() -> None:
    pump = FakePump()
    valves = ValveController()
    valves.initialize()
    # Keep one explicit simulation channel for the fail-closed scenario even
    # though the production deployment now approves all eight valves.
    valves.configure_physical_valves({"valve-001"})
    executor = SharedPumpZoneExecutor(pump, valves)

    result = executor.execute(
        zone_id="zone-002",
        valve_id="valve-002",
        duration=30,
        get_commands=lambda: CommandState(),
    )

    assert result.stop_reason == "VALVE_SIMULATION"
    assert pump.on_count == 0
    assert pump.is_on is False
    assert valves.active_valve_id is None
    assert executor.active_zone_id is None

    valves.configure_physical_valves({"valve-001", "valve-002"})

    zero = executor.execute(
        zone_id="zone-001",
        valve_id="valve-001",
        duration=0,
        get_commands=lambda: CommandState(),
    )
    assert zero.stop_reason == "ZERO_DURATION"
    assert pump.on_count == 0

    executor._controller = CompletedWateringController()
    zone_1_commands = CommandState(cooldown_seconds=600)
    zone_2_commands = CommandState(cooldown_seconds=120)

    completed = executor.execute(
        zone_id="zone-001",
        valve_id="valve-001",
        duration=10,
        get_commands=lambda: zone_1_commands,
    )
    assert completed.completed is True
    assert executor.is_cooldown_active("zone-001") is True
    assert executor.cooldown_remaining_for("zone-001") > 0
    assert executor.is_cooldown_active("zone-002") is False

    executor.execute(
        zone_id="zone-002",
        valve_id="valve-002",
        duration=10,
        get_commands=lambda: zone_2_commands,
    )
    zone_1_remaining = executor.cooldown_remaining_for(
        "zone-001",
    )
    zone_2_remaining = executor.cooldown_remaining_for(
        "zone-002",
    )
    assert zone_1_remaining > zone_2_remaining > 0

    expired = executor.restore_cooldown(
        zone_id="zone-003",
        cooldown_until_epoch=int(time.time()) - 30,
        max_remaining_seconds=600,
    )
    assert expired == 0
    assert executor.is_cooldown_active("zone-003") is False

    restored = executor.restore_cooldown(
        zone_id="zone-003",
        cooldown_until_epoch=int(time.time()) + 300,
        max_remaining_seconds=600,
    )
    assert 298 <= restored <= 300
    assert executor.is_cooldown_active("zone-003") is True

    capped = executor.restore_cooldown(
        zone_id="zone-004",
        cooldown_until_epoch=int(time.time()) + 999999,
        max_remaining_seconds=120,
    )
    assert capped == 120
    assert (
        executor.cooldown_until_epoch_for("zone-004")
        <= int(time.time()) + 120
    )

    valves.cleanup()
    print(
        "[PASS] Shared pump executor safety scenarios.",
    )


if __name__ == "__main__":
    main()
