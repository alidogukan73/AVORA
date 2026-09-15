"""
Sensor history entry model.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SensorHistoryEntry:
    """
    One sensor observation stored for analysis and charts.
    """

    moisture: int

    sensor_id: str

    voltage: float

    raw: int

    trend_classification: str

    moisture_change_per_minute: float

    trend_sample_count: int

    recorded_at: str

    trend_duration_seconds: float

    average_moisture: float

    zone_id: str = ""
