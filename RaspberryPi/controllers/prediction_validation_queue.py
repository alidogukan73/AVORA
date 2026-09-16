"""
Prediction validation queue.

Stores moisture predictions until their future
validation time arrives.
"""

from __future__ import annotations

from datetime import datetime, timedelta

from controllers.prediction_validation_engine import (
    PredictionValidationEngine,
)

from models.moisture_prediction import (
    MoisturePrediction,
)

from models.pending_prediction_validation import (
    PendingPredictionValidation,
)
from models.prediction_validation_status import (
    PredictionValidationStatus,
)


class PredictionValidationQueue:
    """
    Manages moisture predictions waiting for
    future validation.

    Observation mode only.

    The current accuracy engine supports one-hour
    prediction validation, so this queue currently
    accepts only 60-minute targets.
    """

    TARGET_MINUTES = 60
    MAX_VALIDATION_DELAY_SECONDS = 15 * 60

    def __init__(self) -> None:
        self._engine = PredictionValidationEngine()

        self._pending: list[
            PendingPredictionValidation
        ] = []
        self._last_expired_count = 0

    @property
    def last_expired_count(self) -> int:
        """Return how many overdue predictions the latest validation dropped."""

        return self._last_expired_count

    @property
    def count(self) -> int:
        """
        Return the number of pending validations.
        """

        return len(self._pending)

    @property
    def items(
        self,
    ) -> tuple[PendingPredictionValidation, ...]:
        """
        Return a read-only snapshot of pending items.
        """

        return tuple(self._pending)

    def get_status(
        self,
        *,
        current_time: datetime | None = None,
    ) -> PredictionValidationStatus:
        """
        Return the current prediction-validation
        queue status.

        This status can be uploaded to Firebase and
        displayed by dashboard clients.
        """

        now = current_time or datetime.now()

        if not self._pending:
            return PredictionValidationStatus(
                validation_status="IDLE",
                pending_count=0,
                target_minutes=0,
                next_validation_at="",
                remaining_seconds=0,
                updated_at=now.isoformat(),
            )

        next_pending = min(
            self._pending,
            key=lambda item: datetime.fromisoformat(
                item.validate_at,
            ),
        )

        validate_at = datetime.fromisoformat(
            next_pending.validate_at,
        )

        remaining_seconds = max(
            0,
            int(
                (
                    validate_at
                    - now
                ).total_seconds()
            ),
        )

        return PredictionValidationStatus(
            validation_status="WAITING",
            pending_count=len(self._pending),
            target_minutes=next_pending.target_minutes,
            next_validation_at=next_pending.validate_at,
            remaining_seconds=remaining_seconds,
            updated_at=now.isoformat(),
        )

    def enqueue(
        self,
        *,
        prediction: MoisturePrediction,
        current_time: datetime | None = None,
    ) -> bool:
        """
        Add a prediction for one-hour validation.

        Only one pending one-hour prediction is kept
        at a time.

        Returns True when a new item is added.
        Returns False when an item is already waiting.
        """

        if self._has_pending_validation():
            return False

        now = current_time or datetime.now()

        pending = PendingPredictionValidation(
            prediction=prediction,
            target_minutes=self.TARGET_MINUTES,
            validate_at=(
                now
                + timedelta(
                    minutes=self.TARGET_MINUTES,
                )
            ).isoformat(),
        )

        self._pending.append(
            pending,
        )

        return True

    def validate_due(
        self,
        *,
        actual_moisture: float,
        current_time: datetime | None = None,
    ) -> list[
        tuple[
            MoisturePrediction,
            float,
        ]
    ]:
        """
        Validate all predictions whose target time
        has arrived.

        Returns history-compatible tuples:

            (
                prediction,
                actual_moisture,
            )

        Items that are not due remain in the queue.
        """

        now = current_time or datetime.now()
        self._last_expired_count = 0

        validated: list[
            tuple[
                MoisturePrediction,
                float,
            ]
        ] = []

        remaining: list[
            PendingPredictionValidation
        ] = []

        for pending in self._pending:

            try:
                validate_at = datetime.fromisoformat(pending.validate_at)
            except ValueError:
                # A malformed persisted timestamp can never be validated
                # safely, so remove it instead of blocking the queue.
                self._last_expired_count += 1
                continue

            if (
                now - validate_at
            ).total_seconds() > self.MAX_VALIDATION_DELAY_SECONDS:
                # A reading received long after the intended target time is
                # not evidence for one-hour forecast accuracy.
                self._last_expired_count += 1
                continue

            result = self._engine.validate(
                pending=pending,
                actual_moisture=actual_moisture,
                current_time=now,
            )

            if result is None:
                remaining.append(
                    pending,
                )

                continue

            validated.append(
                (
                    pending.prediction,
                    actual_moisture,
                )
            )

        self._pending = remaining

        return validated

    def cancel_all(
        self,
    ) -> int:
        """
        Cancel all pending validations.

        This must be called when irrigation occurs,
        because irrigation changes soil moisture and
        invalidates predictions made before watering.

        Returns the number of cancelled items.
        """

        cancelled_count = len(
            self._pending,
        )

        self._pending.clear()

        return cancelled_count

    def _has_pending_validation(
        self,
    ) -> bool:
        """
        Return whether a one-hour validation
        is already waiting.
        """

        return any(
            pending.target_minutes
            == self.TARGET_MINUTES
            for pending in self._pending
        )
