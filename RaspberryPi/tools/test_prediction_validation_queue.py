"""
Manual tests for PredictionValidationQueue.

Run from the RaspberryPi project root:

    python tools/test_prediction_validation_queue.py
"""

from __future__ import annotations

import sys
from datetime import datetime, timedelta
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(
        0,
        str(PROJECT_ROOT),
    )

from controllers.prediction_validation_queue import (
    PredictionValidationQueue,
)

from models.moisture_prediction import (
    MoisturePrediction,
)

def create_prediction() -> MoisturePrediction:
    """
    Create a test moisture prediction.
    """

    now = datetime.now()

    return MoisturePrediction(
        prediction_status="READY",
        prediction_method="LINEAR_TREND",

        current_moisture=55.0,
        moisture_limit=40.0,

        drying_rate_per_minute=0.05,

        predicted_moisture_1_hour=52.0,
        predicted_moisture_3_hours=46.0,
        predicted_moisture_6_hours=37.0,

        estimated_minutes_until_limit=300.0,

        estimated_limit_reached_at=(
            now
            + timedelta(minutes=300)
        ).isoformat(),

        confidence=0.80,
        confidence_level="HIGH",

        generated_at=now.isoformat(),
    )


def test_enqueue() -> None:
    """
    Test adding a new pending prediction.
    """

    queue = PredictionValidationQueue()

    added = queue.enqueue(
        prediction=create_prediction(),
        current_time=datetime.now(),
    )

    assert added is True
    assert queue.count == 1

    print(
        "[PASS] Prediction added to queue.",
    )


def test_duplicate_is_rejected() -> None:
    """
    Test that only one validation waits at a time.
    """

    queue = PredictionValidationQueue()

    first_added = queue.enqueue(
        prediction=create_prediction(),
    )

    second_added = queue.enqueue(
        prediction=create_prediction(),
    )

    assert first_added is True
    assert second_added is False
    assert queue.count == 1

    print(
        "[PASS] Duplicate pending validation rejected.",
    )


def test_not_due_item_remains() -> None:
    """
    Test that an item remains while its validation
    time has not arrived.
    """

    queue = PredictionValidationQueue()

    start_time = datetime.now()

    queue.enqueue(
        prediction=create_prediction(),
        current_time=start_time,
    )

    validated = queue.validate_due(
        actual_moisture=49.0,
        current_time=(
            start_time
            + timedelta(minutes=30)
        ),
    )

    assert validated == []
    assert queue.count == 1

    print(
        "[PASS] Not-due validation remained queued.",
    )


def test_due_item_is_validated() -> None:
    """
    Test validating an item after one hour.
    """

    queue = PredictionValidationQueue()

    prediction = create_prediction()

    start_time = datetime.now()

    queue.enqueue(
        prediction=prediction,
        current_time=start_time,
    )

    validated = queue.validate_due(
        actual_moisture=49.0,
        current_time=(
            start_time
            + timedelta(minutes=60)
        ),
    )

    expected = [
        (
            prediction,
            49.0,
        )
    ]

    assert validated == expected
    assert queue.count == 0

    print(
        "[PASS] Due prediction validated and removed.",
    )


def test_stale_due_item_is_discarded() -> None:
    """A late sensor reconnect must not corrupt one-hour accuracy."""

    queue = PredictionValidationQueue()
    start_time = datetime.now()
    queue.enqueue(
        prediction=create_prediction(),
        current_time=start_time,
    )

    validated = queue.validate_due(
        actual_moisture=10.0,
        current_time=start_time + timedelta(minutes=76),
    )

    assert validated == []
    assert queue.count == 0
    assert queue.last_expired_count == 1
    print("[PASS] Late prediction validation discarded.")


def test_cancel_all() -> None:
    """
    Test cancellation when irrigation occurs.
    """

    queue = PredictionValidationQueue()

    queue.enqueue(
        prediction=create_prediction(),
    )

    cancelled_count = queue.cancel_all()

    assert cancelled_count == 1
    assert queue.count == 0

    print(
        "[PASS] Pending validation cancelled.",
    )


def test_new_item_after_validation() -> None:
    """
    Test that a new prediction can be added after
    the previous validation is completed.
    """

    queue = PredictionValidationQueue()

    start_time = datetime.now()

    queue.enqueue(
        prediction=create_prediction(),
        current_time=start_time,
    )

    queue.validate_due(
        actual_moisture=50.0,
        current_time=(
            start_time
            + timedelta(minutes=61)
        ),
    )

    added_again = queue.enqueue(
        prediction=create_prediction(),
        current_time=(
            start_time
            + timedelta(minutes=62)
        ),
    )

    assert added_again is True
    assert queue.count == 1

    print(
        "[PASS] New validation added after completion.",
    )

def test_idle_status() -> None:
    """
    Test status generation for an empty queue.
    """

    queue = PredictionValidationQueue()

    current_time = datetime.now()

    status = queue.get_status(
        current_time=current_time,
    )

    assert status.validation_status == "IDLE"
    assert status.pending_count == 0
    assert status.target_minutes == 0
    assert status.next_validation_at == ""
    assert status.remaining_seconds == 0
    assert status.updated_at == current_time.isoformat()

    print(
        "[PASS] Idle queue status generated.",
    )

def test_waiting_status() -> None:
    """
    Test status generation while a prediction
    is waiting for validation.
    """

    queue = PredictionValidationQueue()

    start_time = datetime.now()

    queue.enqueue(
        prediction=create_prediction(),
        current_time=start_time,
    )

    status_time = (
        start_time
        + timedelta(minutes=15)
    )

    status = queue.get_status(
        current_time=status_time,
    )

    assert status.validation_status == "WAITING"
    assert status.pending_count == 1
    assert status.target_minutes == 60
    assert status.next_validation_at == (
        start_time
        + timedelta(minutes=60)
    ).isoformat()

    assert status.remaining_seconds == 2700
    assert status.updated_at == status_time.isoformat()

    print(
        "[PASS] Waiting queue status generated.",
    )

def test_overdue_status_never_negative() -> None:
    """
    Test that remaining seconds never becomes
    negative when validation is overdue.
    """

    queue = PredictionValidationQueue()

    start_time = datetime.now()

    queue.enqueue(
        prediction=create_prediction(),
        current_time=start_time,
    )

    status = queue.get_status(
        current_time=(
            start_time
            + timedelta(minutes=61)
        ),
    )

    assert status.validation_status == "WAITING"
    assert status.pending_count == 1
    assert status.remaining_seconds == 0

    print(
        "[PASS] Overdue status remaining time is zero.",
    )

def main() -> None:
    """
    Run all validation queue tests.
    """

    print(
        "\nPredictionValidationQueue tests started.\n"
    )

    test_enqueue()
    test_duplicate_is_rejected()
    test_not_due_item_remains()
    test_due_item_is_validated()
    test_stale_due_item_is_discarded()
    test_cancel_all()
    test_new_item_after_validation()

    test_idle_status()
    test_waiting_status()
    test_overdue_status_never_negative()

    print(
        "\nAll PredictionValidationQueue "
        "tests passed successfully.\n"
    )

if __name__ == "__main__":
    main()
