"""
Safe control for two-wire motorized garden-zone valves.
"""

from __future__ import annotations

import time

import RPi.GPIO as GPIO

from core.config import ValveConfig
from core.logger import AppLogger


class ValveController:
    """
    Controls normally-closed, two-wire motorized valves.

    Applying power opens a valve. Removing power closes it.
    Only one valve may be selected at a time.
    """

    def __init__(self) -> None:
        self._logger = AppLogger().logger
        self._initialized = False
        self._active_valve_id: str | None = None
        self._active_valve_opened_at: float | None = None
        self._physical_valve_ids = frozenset(
            ValveConfig.PHYSICAL_VALVE_IDS,
        )
        self._configured_gpio_pins: set[int] = set()

    def initialize(self) -> None:
        if self._initialized:
            return

        physical_pins = self._physical_pins()
        if not physical_pins:
            self._initialized = True
            self._logger.info(
                "Zone valves initialized in simulation mode. "
                "The real pump is blocked for zone watering.",
            )
            return

        GPIO.setmode(GPIO.BCM)
        GPIO.setwarnings(False)

        for pin in physical_pins:
            GPIO.setup(pin, GPIO.OUT)
            self._configured_gpio_pins.add(pin)
            GPIO.output(
                pin,
                GPIO.HIGH
                if ValveConfig.ACTIVE_LOW
                else GPIO.LOW,
            )

        self._initialized = True
        self._logger.info(
            "Zone valves initialized. physical_count=%d total_count=%d",
            len(physical_pins),
            len(ValveConfig.GPIO_PINS),
        )

    def open(self, valve_id: str) -> None:
        self._require_initialized()

        if valve_id not in ValveConfig.GPIO_PINS:
            raise ValueError(
                f"Unknown garden valve: {valve_id}",
            )

        if self._active_valve_id == valve_id:
            return

        self.close_all()

        if self.is_physical_valve(valve_id):
            GPIO.output(
                ValveConfig.GPIO_PINS[valve_id],
                GPIO.LOW
                if ValveConfig.ACTIVE_LOW
                else GPIO.HIGH,
            )

        self._active_valve_id = valve_id
        self._active_valve_opened_at = (
            time.monotonic()
            if self.is_physical_valve(valve_id)
            else None
        )
        self._logger.info(
            "Zone valve %s. valve_id=%s",
            "OPEN" if self.is_physical_valve(valve_id) else "simulated OPEN",
            valve_id,
        )

    def wait_for_opening(self, valve_id: str) -> None:
        """Wait for a physical valve before starting the shared pump."""
        if not self.is_physical_valve(valve_id):
            return

        remaining = ValveConfig.OPENING_DELAY_SECONDS
        if (
            self._active_valve_id == valve_id
            and self._active_valve_opened_at is not None
        ):
            remaining -= time.monotonic() - self._active_valve_opened_at

        if remaining > 0:
            time.sleep(remaining)

    def is_ready_for_pump(self, valve_id: str | None = None) -> bool:
        """Return true only after the active physical valve fully opens."""
        selected_valve_id = valve_id or self._active_valve_id
        if (
            selected_valve_id is None
            or selected_valve_id != self._active_valve_id
            or not self.is_physical_valve(selected_valve_id)
            or self._active_valve_opened_at is None
        ):
            return False

        return (
            time.monotonic() - self._active_valve_opened_at
            >= ValveConfig.OPENING_DELAY_SECONDS
        )

    def close_all(self) -> None:
        self._require_initialized()

        previous = self._active_valve_id
        physical_pins = self._physical_pins()
        failures: list[tuple[int, Exception]] = []

        # Attempt every installed channel independently.  One failed GPIO
        # write must not prevent the remaining valves from receiving their
        # safe CLOSED level.
        if physical_pins:
            inactive_level = (
                GPIO.HIGH
                if ValveConfig.ACTIVE_LOW
                else GPIO.LOW
            )
            for pin in physical_pins:
                try:
                    GPIO.output(
                        pin,
                        inactive_level,
                    )
                except Exception as exc:
                    failures.append((pin, exc))

        # Clear the software interlock state even when a physical output write
        # failed.  This prevents the pump from treating an uncertain valve as
        # confirmed open on the next control cycle.
        self._active_valve_id = None
        self._active_valve_opened_at = None

        if previous is not None:
            self._logger.info(
                "Zone valve %s. valve_id=%s",
                "CLOSED"
                if self.is_physical_valve(previous)
                else "simulated CLOSED",
                previous,
            )

            if self.is_physical_valve(previous):
                time.sleep(ValveConfig.CLOSING_DELAY_SECONDS)

        if failures:
            failed_pins = ",".join(
                str(pin)
                for pin, _error in failures
            )
            self._logger.error(
                "Zone valve shutdown was incomplete. gpio_pins=%s",
                failed_pins,
            )
            raise RuntimeError(
                "Failed to drive zone valve GPIO pins CLOSED: "
                f"{failed_pins}"
            ) from failures[0][1]

    @property
    def simulation_mode(self) -> bool:
        """Compatibility status: true only when no physical valve exists."""
        return not bool(self._physical_pins())

    def is_physical_valve(self, valve_id: str | None) -> bool:
        return (
            not ValveConfig.SIMULATION_MODE
            and valve_id in self._physical_valve_ids
            and valve_id in ValveConfig.GPIO_PINS
        )

    def is_simulated_valve(self, valve_id: str | None) -> bool:
        return not self.is_physical_valve(valve_id)

    @property
    def active_valve_id(self) -> str | None:
        return self._active_valve_id

    def configure_physical_valves(
        self,
        valve_ids: set[str] | frozenset[str],
    ) -> None:
        """Apply Firebase physical/simulation selections safely."""
        requested = frozenset(
            valve_id
            for valve_id in valve_ids
            if valve_id in ValveConfig.GPIO_PINS
        )
        if requested == self._physical_valve_ids:
            return

        if self._active_valve_id is not None:
            self.close_all()

        if requested and not self._configured_gpio_pins:
            GPIO.setmode(GPIO.BCM)
            GPIO.setwarnings(False)

        for valve_id in requested:
            pin = ValveConfig.GPIO_PINS[valve_id]
            if pin not in self._configured_gpio_pins:
                GPIO.setup(pin, GPIO.OUT)
                self._configured_gpio_pins.add(pin)
            GPIO.output(
                pin,
                GPIO.HIGH if ValveConfig.ACTIVE_LOW else GPIO.LOW,
            )

        self._physical_valve_ids = requested
        self._logger.info(
            "Physical valve configuration updated. valve_ids=%s",
            ",".join(sorted(requested)) or "none",
        )

    def cleanup(self) -> None:
        if not self._initialized:
            return

        try:
            self.close_all()
            # Keep installed valve relays at their inactive output level.
            # GPIO.cleanup() would return the pins to high-impedance inputs,
            # allowing external relay-board pull resistors to determine their
            # state while the backend service is stopped or restarted.
            if self._configured_gpio_pins:
                inactive_level = (
                    GPIO.HIGH
                    if ValveConfig.ACTIVE_LOW
                    else GPIO.LOW
                )
                for pin in self._configured_gpio_pins:
                    GPIO.output(pin, inactive_level)
                self._logger.info(
                    "Zone valve GPIO pins held at safe CLOSED level.",
                )
        finally:
            self._initialized = False
            self._active_valve_id = None
            self._active_valve_opened_at = None

    def _require_initialized(self) -> None:
        if not self._initialized:
            raise RuntimeError(
                "Valve controller is not initialized.",
            )

    def _physical_pins(self) -> tuple[int, ...]:
        return tuple(
            ValveConfig.GPIO_PINS[valve_id]
            for valve_id in self._physical_valve_ids
            if valve_id in ValveConfig.GPIO_PINS
        )
