"""
Application configuration.
"""

from __future__ import annotations


class AppConfig:
    """
    General application configuration.
    """

    DEVICE_ID = "avora-001"

    VERSION = "2.11.0"

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

    STATUS_UPDATE_INTERVAL_SECONDS = 10

    COMMAND_SYNC_INTERVAL_SECONDS = 0.5


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

    ACTIVE_LOW = False

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

    # Only valve-001 has a real relay/valve test planned right now.  Add a
    # valve id here only after its GPIO relay and 12 V valve wiring pass the
    # valve-only test.  The first five *garden zones* remain active in the
    # app; this list is strictly a physical-hardware safety interlock.
    PHYSICAL_VALVE_IDS = frozenset({
        "valve-001",
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

    MAX_MANUAL_PUMP_DURATION_SECONDS = 120
