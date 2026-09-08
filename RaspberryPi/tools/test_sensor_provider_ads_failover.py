"""Failover checks for independent ESP32 ADS1115 modules."""

from __future__ import annotations

from datetime import datetime
from types import SimpleNamespace

from tools.hardware_test_stubs import install_hardware_import_stubs

install_hardware_import_stubs()

from hardware.sensor_provider import SoilMoistureSensorProvider


def main() -> None:
    secondary_reading = SimpleNamespace(
        raw=5300,
        voltage=0.663,
        moisture=60,
        sensor_id="soil-005",
        firmware="2.3.0",
        rssi=-60,
        uptime_seconds=120,
    )
    ads_status = SimpleNamespace(
        node_online=True,
        primary_available=False,
        secondary_available=True,
        firmware="2.3.0",
        rssi=-60,
        uptime_seconds=120,
        received_at=datetime.now().astimezone(),
    )

    mqtt_sensor = SimpleNamespace(
        get_fresh_reading=lambda: None,
        get_fresh_readings=lambda: {"soil-005": secondary_reading},
        get_latest_reading=lambda: None,
        get_latest_readings=lambda: {"soil-005": secondary_reading},
        get_latest_ads1115_status=lambda: ads_status,
    )

    provider = SoilMoistureSensorProvider.__new__(
        SoilMoistureSensorProvider
    )
    provider._initialized = True
    provider._mode = "mqtt"
    provider._mqtt_sensor = mqtt_sensor
    provider._mqtt_startup_timeout_seconds = 20.0
    provider._initialized_at_monotonic = 0.0

    reading = provider._read_mqtt_sensor()
    assert reading.sensor_id == "soil-005"
    assert reading.moisture == 60
    assert provider.is_waiting_for_first_reading() is False
    assert provider.get_ads1115_status() is ads_status

    print("[PASS] Healthy ADS1115 keeps the sensor pipeline active.")


if __name__ == "__main__":
    main()
