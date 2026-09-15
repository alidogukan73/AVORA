"""
Exclusive execution of one garden zone on the shared pump.
"""

from __future__ import annotations

import threading
import time
from collections.abc import Callable

from controllers.watering_controller import WateringController
from hardware.relay import RelayController
from hardware.valve_controller import ValveController
from models.command_state import CommandState
from models.watering_result import WateringResult


class SharedPumpZoneExecutor:
    """
    Prevent concurrent zones from using the shared pump.
    """

    def __init__(
        self,
        pump: RelayController,
        valves: ValveController,
    ) -> None:
        self._controller = WateringController(
            pump,
            valves,
        )
        self._lock = threading.Lock()
        self._active_zone_id: str | None = None
        self._zone_cooldown_deadlines: dict[str, float] = {}
        self._zone_cooldown_until_epochs: dict[str, int] = {}

    def execute(
        self,
        *,
        zone_id: str,
        valve_id: str,
        duration: int,
        get_commands: Callable[[], CommandState],
        on_relay_changed: Callable[[bool], None] | None = None,
        on_valve_changed: (
            Callable[[str | None, bool], None] | None
        ) = None,
        on_progress: Callable[[], None] | None = None,
    ) -> WateringResult:
        if not self._lock.acquire(blocking=False):
            return WateringResult(
                completed=False,
                stop_reason="SHARED_PUMP_BUSY",
                duration=0,
            )

        self._active_zone_id = zone_id

        try:
            if duration <= 0:
                return WateringResult(
                    completed=False,
                    stop_reason="ZERO_DURATION",
                    duration=0,
                )

            result = self._controller.water_zone(
                valve_id=valve_id,
                duration=duration,
                get_commands=get_commands,
                on_relay_changed=on_relay_changed,
                on_valve_changed=on_valve_changed,
                on_progress=on_progress,
            )

            if result.completed:
                cooldown_seconds = max(
                    0,
                    int(get_commands().cooldown_seconds),
                )
                self._zone_cooldown_deadlines[zone_id] = (
                    time.monotonic() + cooldown_seconds
                )
                self._zone_cooldown_until_epochs[zone_id] = (
                    int(time.time()) + cooldown_seconds
                )

            return result
        finally:
            self._active_zone_id = None
            self._lock.release()

    @property
    def active_zone_id(self) -> str | None:
        return self._active_zone_id

    def cooldown_remaining_for(
        self,
        zone_id: str,
    ) -> int:
        """
        Return only the requested zone's remaining cooldown.
        """

        deadline = self._zone_cooldown_deadlines.get(zone_id)
        if deadline is None:
            return 0

        remaining = int(deadline - time.monotonic())
        if remaining <= 0:
            self._zone_cooldown_deadlines.pop(zone_id, None)
            self._zone_cooldown_until_epochs.pop(zone_id, None)
            return 0

        return remaining

    def cooldown_until_epoch_for(
        self,
        zone_id: str,
    ) -> int:
        if self.cooldown_remaining_for(zone_id) <= 0:
            return 0

        return self._zone_cooldown_until_epochs.get(
            zone_id,
            0,
        )

    def restore_cooldown(
        self,
        *,
        zone_id: str,
        cooldown_until_epoch: int,
        max_remaining_seconds: int,
    ) -> int:
        """
        Restore a persisted wall-clock deadline safely.

        Future values are capped by the configured cooldown so a
        corrupt timestamp cannot block a zone indefinitely.
        """

        try:
            until_epoch = int(cooldown_until_epoch)
            maximum = max(0, int(max_remaining_seconds))
        except (TypeError, ValueError):
            return 0

        remaining = until_epoch - int(time.time())
        if remaining <= 0 or maximum <= 0:
            self._zone_cooldown_deadlines.pop(zone_id, None)
            self._zone_cooldown_until_epochs.pop(zone_id, None)
            return 0

        safe_remaining = min(remaining, maximum)
        safe_until_epoch = int(time.time()) + safe_remaining
        self._zone_cooldown_deadlines[zone_id] = (
            time.monotonic() + safe_remaining
        )
        self._zone_cooldown_until_epochs[zone_id] = (
            safe_until_epoch
        )
        return safe_remaining

    def is_cooldown_active(
        self,
        zone_id: str,
    ) -> bool:
        return self.cooldown_remaining_for(zone_id) > 0

    @property
    def cooldown_remaining(self) -> int:
        """
        Largest remaining zone cooldown for legacy status clients.
        """

        return max(
            (
                self.cooldown_remaining_for(zone_id)
                for zone_id in tuple(
                    self._zone_cooldown_deadlines,
                )
            ),
            default=0,
        )

    @property
    def state(self):
        return self._controller.state
