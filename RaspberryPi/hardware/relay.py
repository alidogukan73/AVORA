"""
Relay control module.
"""

from __future__ import annotations

import threading

import RPi.GPIO as GPIO

from core.config import RelayConfig
from core.logger import AppLogger


class RelayController:
    """
    Controls the irrigation relay.
    """

    def __init__(self) -> None:
        self._logger = AppLogger().logger

        self._pin = RelayConfig.GPIO_PIN

        self._initialized = False
        self._state = False
        self._lock = threading.RLock()
        self._watchdog_timer: threading.Timer | None = None
        self._watchdog_generation = 0
        self._hard_timeout_latched = False

    def initialize(self) -> None:
        """
        Initialize relay GPIO.
        """

        with self._lock:
            try:
                GPIO.setmode(GPIO.BCM)
                GPIO.setwarnings(False)

                GPIO.setup(
                    self._pin,
                    GPIO.OUT,
                )

                # Keep relay OFF at startup.
                GPIO.output(
                    self._pin,
                    self._inactive_level(),
                )

                self._cancel_watchdog_locked()
                self._state = False
                self._hard_timeout_latched = False
                self._initialized = True

                self._logger.info(
                    "Relay initialized on GPIO %d.",
                    self._pin,
                )

            except Exception as exc:
                self._logger.exception(exc)
                raise

    def on(self) -> None:
        """
        Turn relay on.
        """

        with self._lock:
            if not self._initialized:
                raise RuntimeError(
                    "Relay is not initialized.",
                )

            if self._hard_timeout_latched:
                raise RuntimeError(
                    "Relay hard safety timeout is latched; "
                    "an explicit OFF transition is required.",
                )

            if self._state:
                return

            maximum_seconds = float(
                RelayConfig.MAX_CONTINUOUS_RUN_SECONDS,
            )
            if maximum_seconds <= 0:
                raise RuntimeError(
                    "Relay hard safety timeout must be greater than zero.",
                )

            GPIO.output(
                self._pin,
                self._active_level(),
            )
            self._state = True

            self._cancel_watchdog_locked()
            generation = self._watchdog_generation
            watchdog = threading.Timer(
                maximum_seconds,
                self._hard_timeout_expired,
                args=(generation, maximum_seconds),
            )
            watchdog.daemon = True
            self._watchdog_timer = watchdog

            try:
                watchdog.start()
            except Exception:
                self._watchdog_timer = None
                GPIO.output(
                    self._pin,
                    self._inactive_level(),
                )
                self._state = False
                self._hard_timeout_latched = True
                raise

            self._logger.info(
                "Relay ON. hard_timeout=%.1f seconds",
                maximum_seconds,
            )

    def off(self) -> None:
        """
        Turn relay off.
        """

        with self._lock:
            if not self._initialized:
                raise RuntimeError(
                    "Relay is not initialized.",
                )

            was_on = self._state
            was_latched = self._hard_timeout_latched
            self._cancel_watchdog_locked()

            # Reassert the physical OFF level even when the cached software
            # state already says OFF.  This repairs any state divergence
            # caused by a transient GPIO or process fault.
            GPIO.output(
                self._pin,
                self._inactive_level(),
            )

            self._state = False
            self._hard_timeout_latched = False

            if was_on or was_latched:
                self._logger.info(
                    "Relay OFF.",
                )

    def toggle(self) -> None:
        """
        Toggle relay state.
        """

        if self.is_on:
            self.off()
        else:
            self.on()

    @property
    def is_on(self) -> bool:
        """
        Return relay state.
        """

        with self._lock:
            return self._state

    @property
    def hard_timeout_latched(self) -> bool:
        """Return whether the local maximum runtime forced the relay OFF."""
        with self._lock:
            return self._hard_timeout_latched

    def cleanup(self) -> None:
        """
        Leave the relay output at its inactive electrical level.

        Do not call ``GPIO.cleanup`` here. Cleanup changes the pin back to a
        high-impedance input and some relay boards then pull an active-high
        input HIGH. Holding the configured output at the inactive level keeps
        the pump safely off while the service is stopped or restarted.
        """

        with self._lock:
            if not self._initialized:
                return

            try:
                self._cancel_watchdog_locked()
                GPIO.output(
                    self._pin,
                    self._inactive_level(),
                )

                self._logger.info(
                    "Relay GPIO held at safe OFF level.",
                )

            finally:
                self._initialized = False
                self._state = False
                self._hard_timeout_latched = False

    @staticmethod
    def _active_level():
        return GPIO.LOW if RelayConfig.ACTIVE_LOW else GPIO.HIGH

    @staticmethod
    def _inactive_level():
        return GPIO.HIGH if RelayConfig.ACTIVE_LOW else GPIO.LOW

    def _cancel_watchdog_locked(self) -> None:
        self._watchdog_generation += 1
        watchdog = self._watchdog_timer
        self._watchdog_timer = None
        if watchdog is not None:
            watchdog.cancel()

    def _hard_timeout_expired(
        self,
        generation: int,
        maximum_seconds: float,
    ) -> None:
        with self._lock:
            if (
                generation != self._watchdog_generation
                or not self._initialized
                or not self._state
            ):
                return

            self._watchdog_timer = None
            self._hard_timeout_latched = True

            try:
                GPIO.output(
                    self._pin,
                    self._inactive_level(),
                )
            except Exception as exc:
                # Keep the reported state ON when the physical OFF write
                # failed; callers must continue treating the pump as unsafe.
                self._logger.critical(
                    "Relay hard safety timeout could not force GPIO OFF: %s",
                    exc,
                )
                return

            self._state = False
            self._logger.critical(
                "Relay hard safety timeout reached; forced OFF after "
                "%.1f seconds.",
                maximum_seconds,
            )
