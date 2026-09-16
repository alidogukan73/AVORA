"""
Application configuration.
"""

from __future__ import annotations


class AppConfig:
    """
    General application configuration.
    """

    DEVICE_ID = "avora-001"

    VERSION = "2.12.4"

    LOOP_DELAY_SECONDS = 2.0


class FirebaseConfig:
    """
    Firebase configuration.
    """

    DATABASE_URL = (
        "https://avora-alidogukan-default-rtdb.europe-west1.firebasedatabase.app/"
    )

    CREDENTIALS_FILE = "firebase_key.json"

    HTTP_TIMEOUT_SECONDS = 10

    # Android considers the device offline only after three minutes. A
    # 30-second heartbeat keeps that detection responsive without rewriting
    # the same Firebase status snapshot six times per minute.
    STATUS_UPDATE_INTERVAL_SECONDS = 30

    # Raspberry Pi resource and network information changes much more slowly
    # than the connection heartbeat.
    HEALTH_UPDATE_INTERVAL_SECONDS = 60

    # Sensor-to-zone routing changes rarely. Local irrigation still evaluates
    # every two seconds; this only limits the Firebase configuration refresh.
    ZONE_MAP_REFRESH_INTERVAL_SECONDS = 60

    # Keep local sensor safety fast while reducing cloud telemetry churn.
    SENSOR_CLOUD_PUBLISH_INTERVAL_SECONDS = 15

    # Normal command delivery uses one long-lived Firebase stream. This
    # interval is used only if that stream cannot be started.
    COMMAND_SYNC_FALLBACK_INTERVAL_SECONDS = 10.0


class SensorConfig:
    """
    Soil moisture sensor configuration.
    """

    # -------------------------------------------------
    # Sensor source
    # -------------------------------------------------

    # "wired" → ADS1115 doğrudan Raspberry Pi üzerinde
    # "mqtt"  → ESP32 üzerinden kablosuz MQTT sensörü
    SENSOR_MODE = "mqtt"

    # -------------------------------------------------
    # Wired ADS1115 configuration
    # -------------------------------------------------

    I2C_ADDRESS = 0x48

    GAIN = 1

    SAMPLE_COUNT = 10

    SAMPLE_DELAY_MS = 50

    SOIL_DRY_VALUE = 13850

    SOIL_WET_VALUE = 4442

    RESTART_DELTA = 10

    MIN_WATERING_INTERVAL_SECONDS = 120

    # -------------------------------------------------
    # Wireless MQTT sensor configuration
    # -------------------------------------------------

    MQTT_BROKER = "127.0.0.1"

    MQTT_PORT = 1883

    MQTT_TOPIC = (
        "avora/sensors/+"
    )

    MQTT_ADS_STATUS_TOPIC = "avora/status/esp32/ads1115"

    MQTT_SENSOR_ID = "soil-001"

    MQTT_STALE_AFTER_SECONDS = 30.0

    MQTT_STARTUP_TIMEOUT_SECONDS = 20.0


class SeedlingConfig:
    """Advisory-only seedling sensor network configuration."""

    MQTT_BROKER = "127.0.0.1"

    MQTT_PORT = 1883

    MQTT_TOPIC = "avora/seedling/+/telemetry"

    MQTT_CLIENT_ID = "avora-pi-seedling-assistant"

    # Only explicitly provisioned nodes may publish through the Admin SDK bridge.
    MQTT_ALLOWED_NODE_IDS = ("seedling-001",)

    TELEMETRY_STALE_AFTER_SECONDS = 45

    # This module publishes advice only. It must never operate an actuator.
    ADVISORY_ONLY = True


class RelayConfig:
    """
    Relay configuration.
    """

    GPIO_PIN = 17

    ACTIVE_LOW = False


class ValveConfig:
    """
    Two-wire, power-open / power-off-close zone valves.

    Keep simulation enabled until every physical valve and
    its separate 12 V supply have been installed and tested.
    """

    # Simulation is no longer global: a valve becomes physical only when its
    # relay and 12 V wiring have actually been installed and tested.  This
    # prevents an unfinished zone from ever starting the shared pump.
    SIMULATION_MODE = False

    # The installed eight-channel valve relay board is LOW-triggered:
    # HIGH keeps a channel safely OFF; LOW energizes only the selected valve.
    ACTIVE_LOW = True

    GPIO_PINS = {
        "valve-001": 5,
        "valve-002": 6,
        "valve-003": 13,
        "valve-004": 19,
        "valve-005": 26,
        "valve-006": 16,
        "valve-007": 20,
        "valve-008": 21,
    }

    # Raspberry Pi 40-pin header positions.  These are published to Firebase
    # for the Android app as read-only wiring documentation.
    GPIO_PHYSICAL_PINS = {
        "valve-001": 29,
        "valve-002": 31,
        "valve-003": 33,
        "valve-004": 35,
        "valve-005": 37,
        "valve-006": 36,
        "valve-007": 38,
        "valve-008": 40,
    }

    # All eight LOW-trigger valve relay inputs are connected for on-site
    # commissioning.  Automatic irrigation remains independently disabled on
    # unconfigured zones; this list only permits explicit valve operation.
    PHYSICAL_VALVE_IDS = frozenset({
        "valve-001",
        "valve-002",
        "valve-003",
        "valve-004",
        "valve-005",
        "valve-006",
        "valve-007",
        "valve-008",
    })

    OPENING_DELAY_SECONDS = 8.0
    CLOSING_DELAY_SECONDS = 8.0

class LogConfig:
    """
    Logging configuration.
    """

    LEVEL = "INFO"

    FORMAT = (
        "%(asctime)s | %(levelname)-8s | %(message)s"
    )

    DATE_FORMAT = "%Y-%m-%d %H:%M:%S"

    LOG_FILE = "avora.log"

    MAX_BYTES = 5 * 1024 * 1024

    BACKUP_COUNT = 3

class IrrigationConfig:
    """
    Irrigation configuration.
    """

    # Toprak nem eşiği (%)
    DEFAULT_MOISTURE_LIMIT = 40

    # Varsayılan sulama süresi (saniye)
    DEFAULT_PUMP_DURATION_SECONDS = 20

    # Sulama sonrası bekleme süresi (v2.3.6/v2.4'te kullanılacak)
    COOLDOWN_SECONDS = 600

    DEFAULT_RESTART_DELTA = 10

    # Damla sulamada nem sensÃ¶re hemen ulaÅŸmayabilir. AynÄ± bÃ¶lge,
    # bekleme sÃ¼resi korunarak bu sayÄ± kadar kÄ±sa Ã§evrim yapabilir.
    # Limit dolunca nem toparlanmasÄ± gÃ¶rÃ¼lmeden yeni Ã§evrim baÅŸlatÄ±lmaz.
    DEFAULT_MAX_AUTOMATIC_WATERING_CYCLES = 3

    DEFAULT_COOLDOWN_SECONDS = 600

    MIN_MOISTURE_LIMIT = 5
    MAX_MOISTURE_LIMIT = 95

    MIN_PUMP_DURATION_SECONDS = 0
    MAX_PUMP_DURATION_SECONDS = 10800

    MIN_RESTART_DELTA = 1
    MAX_RESTART_DELTA = 30

    MIN_COOLDOWN_SECONDS = 60
    MAX_COOLDOWN_SECONDS = 86400

    DEFAULT_MANUAL_PUMP_DURATION_LIMIT_SECONDS = 4 * 60 * 60
    MAX_MANUAL_PUMP_DURATION_SECONDS = 12 * 60 * 60
