"""
Watering controller.

Contains irrigation decision logic.
"""

from __future__ import annotations

import time
from collections.abc import Callable

from core.logger import AppLogger
from hardware.relay import RelayController
from hardware.valve_controller import ValveController
from models.command_state import CommandState
from models.sensor_reading import SensorReading
from models.watering_state import WateringState
from models.watering_result import WateringResult

class WateringController:
    """
    Controls irrigation logic.
    """

    def __init__(
        self,
        relay: RelayController,
        valves: ValveController | None = None,
    ) -> None:

        self._relay = relay
        self._valves = valves
        self._logger = AppLogger().logger

        # Histerezis
        self._waiting_for_reset = False

        # Son sulama zamanı (monotonic)
        self._last_watering_time = 0.0
        self._last_command_cooldown = 120

        self._state = WateringState.READY

    def should_water(
        self,
        reading: SensorReading,
        commands: CommandState,
    ) -> bool:
        """
        Decide whether irrigation is required.
        """

        now = time.monotonic()

        self._last_command_cooldown = commands.cooldown_seconds       

        # -----------------------------
        # 1) Histerezis kontrolü
        # -----------------------------
        if self._waiting_for_reset:

            self._logger.debug(
                "Waiting for soil recovery..."
            )

            if (
                reading.moisture
                >= commands.moisture_limit
                + commands.restart_delta
            ):

                self._waiting_for_reset = False

                self._logger.info(
                    "Recovery completed."
                )                

                self._state = WateringState.READY

                self._logger.info(
                    "Soil moisture recovered."
                )

            else:

                self._state = WateringState.WAITING_FOR_RESET

                return False

        # -----------------------------
        # 2) Cooldown kontrolü
        # -----------------------------
        if (
            self._last_watering_time > 0
            and (
                now - self._last_watering_time
                < commands.cooldown_seconds
            )
        ):

            self._state = WateringState.COOLDOWN

            remaining = int(
                commands.cooldown_seconds
                - (now - self._last_watering_time)
            )

            self._logger.debug(
                "Cooldown active (%d s remaining).",
                remaining,
            )

            return False

        # -----------------------------
        # 3) Sulama gerekli mi?
        # -----------------------------
        if reading.moisture < commands.moisture_limit:

            self._state = WateringState.WATERING

            self._logger.info(
                "Watering required."
            )            

            return True

        self._state = WateringState.READY

        return False

    def water(
        self,
        duration: int,
        get_commands: Callable[[], CommandState],
        on_relay_changed: Callable[[bool], None] | None = None,
        on_progress: Callable[[], None] | None = None,
    ) -> WateringResult:
        """
        Water for the specified duration.

        Returns
        -------
        bool
            True if watering completed,
            False if interrupted.
        """
        started = time.monotonic()

        try:
            self._logger.info(
                "Starting irrigation (%d s).",
                duration,
            )

            self._relay.on()

            self._state = WateringState.WATERING

            self._notify_relay_changed(
                callback=on_relay_changed,
                relay_on=True,
            )

            while (
                time.monotonic()
                - started
                < duration
            ):

                if on_progress is not None:
                    on_progress()

                commands = get_commands()

                # Sistem kapatıldı
                if not commands.enabled:

                    self._relay.off()

                    self._notify_relay_changed(
                        callback=on_relay_changed,
                        relay_on=False,
                    )

                    self._state = WateringState.DISABLED

                    elapsed = int(
                        time.monotonic() - started
                    )

                    return WateringResult(
                        completed=False,
                        stop_reason="SYSTEM_DISABLED",
                        duration=elapsed,
                    )

                # Manuel moda geçildi
                if not commands.auto_mode:

                    self._relay.off()

                    self._notify_relay_changed(
                        callback=on_relay_changed,
                        relay_on=False,
                    )

                    self._state = WateringState.MANUAL

                    elapsed = int(
                        time.monotonic() - started
                    )

                    return WateringResult(
                        completed=False,
                        stop_reason="MANUAL_MODE",
                        duration=elapsed,
                    )

                time.sleep(0.2)

            self._relay.off()

            self._notify_relay_changed(
                callback=on_relay_changed,
                relay_on=False,
            )

            # Başarıyla sulandıysa

            self._last_watering_time = time.monotonic()
            self._last_command_cooldown = (
                get_commands().cooldown_seconds
            )
            self._state = WateringState.COOLDOWN

            self._logger.info(
                "Irrigation completed.",
            )

            elapsed = int(
                time.monotonic() - started
            )

            return WateringResult(
                completed=True,
                stop_reason="COMPLETED",
                duration=elapsed,
            )

        except Exception as exc:

            self._relay.off()

            self._notify_relay_changed(
                callback=on_relay_changed,
                relay_on=False,
            )

            self._state = WateringState.ERROR

            self._logger.exception(exc)

            elapsed = int(
                time.monotonic() - started
            )

            return WateringResult(
                completed=False,
                stop_reason="ERROR",
                duration=elapsed,
            )

    def water_zone(
        self,
        *,
        valve_id: str,
        duration: int,
        get_commands: Callable[[], CommandState],
        on_relay_changed: Callable[[bool], None] | None = None,
        on_valve_changed: Callable[[str | None, bool], None] | None = None,
        on_progress: Callable[[], None] | None = None,
    ) -> WateringResult:
        """
        Water one zone using the safe valve/pump sequence.

        In valve simulation mode the physical pump is deliberately
        blocked, because no real water path has been opened.
        """

        if self._valves is None:
            raise RuntimeError(
                "Zone valve controller is not configured.",
            )

        started = time.monotonic()

        try:
            self._valves.open(valve_id)
            if on_valve_changed is not None:
                on_valve_changed(valve_id, True)

            if self._valves.is_simulated_valve(valve_id):
                self._logger.info(
                    "Zone watering simulated; pump was not started. "
                    "valve_id=%s requested_duration=%d",
                    valve_id,
                    duration,
                )
                return WateringResult(
                    completed=False,
                    stop_reason="VALVE_SIMULATION",
                    duration=0,
                )

            # The UI is notified immediately after relay output changes,
            # while the pump still waits for the valve's mechanical travel.
            while not self._valves.is_ready_for_pump(valve_id):
                if on_progress is not None:
                    on_progress()
                commands = get_commands()
                if not commands.enabled:
                    return WateringResult(
                        completed=False,
                        stop_reason="SYSTEM_DISABLED",
                        duration=0,
                    )
                if not commands.auto_mode:
                    return WateringResult(
                        completed=False,
                        stop_reason="MANUAL_MODE",
                        duration=0,
                    )
                time.sleep(0.2)

            return self.water(
                duration=duration,
                get_commands=get_commands,
                on_relay_changed=on_relay_changed,
                on_progress=on_progress,
            )

        finally:
            self._relay.off()
            self._notify_relay_changed(
                callback=on_relay_changed,
                relay_on=False,
            )
            if on_valve_changed is not None:
                on_valve_changed(None, False)
            self._valves.close_all()

    def _notify_relay_changed(
        self,
        *,
        callback: Callable[[bool], None] | None,
        relay_on: bool,
    ) -> None:
        """
        Report relay state without allowing a network callback
        to interrupt physical relay control.
        """

        if callback is None:
            return

        try:
            callback(
                relay_on,
            )
        except Exception as exc:
            self._logger.warning(
                "Relay state callback failed. "
                "relay=%s error=%s",
                "ON" if relay_on else "OFF",
                exc,
            )
    
    @property
    def state(self) -> WateringState:
        """
        Current irrigation state.
        """

        return self._state
    
    @property
    def cooldown_remaining(self) -> int:
        """
        Remaining cooldown time in seconds.
        """

        if self._last_watering_time <= 0:
            return 0

        remaining = int(
            self._last_watering_time
            + self._last_command_cooldown
            - time.monotonic()
        )

        return max(0, remaining)
