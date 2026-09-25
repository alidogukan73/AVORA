"""Import-only stubs for service tests running away from Raspberry Pi hardware."""

from __future__ import annotations

import sys
import types


def install_hardware_import_stubs() -> None:
    try:
        import firebase_admin  # noqa: F401
    except ModuleNotFoundError:
        firebase_admin = types.ModuleType("firebase_admin")
        auth = types.ModuleType("firebase_admin.auth")
        credentials = types.ModuleType("firebase_admin.credentials")
        database = types.ModuleType("firebase_admin.db")
        messaging = types.ModuleType("firebase_admin.messaging")
        firebase_admin._apps = {}
        firebase_admin.initialize_app = lambda *_args, **_kwargs: object()
        credentials.Certificate = lambda *_args, **_kwargs: object()
        database.reference = lambda *_args, **_kwargs: None
        messaging.Notification = object
        messaging.Message = object
        messaging.AndroidConfig = object
        messaging.AndroidNotification = object
        messaging.send = lambda *_args, **_kwargs: "test-message"
        firebase_admin.credentials = credentials
        firebase_admin.auth = auth
        firebase_admin.db = database
        firebase_admin.messaging = messaging
        sys.modules["firebase_admin"] = firebase_admin
        sys.modules["firebase_admin.auth"] = auth
        sys.modules["firebase_admin.credentials"] = credentials
        sys.modules["firebase_admin.db"] = database
        sys.modules["firebase_admin.messaging"] = messaging

    try:
        import paho.mqtt.client  # noqa: F401
    except ModuleNotFoundError:
        paho = types.ModuleType("paho")
        mqtt_package = types.ModuleType("paho.mqtt")
        mqtt_client = types.ModuleType("paho.mqtt.client")
        mqtt_client.CallbackAPIVersion = types.SimpleNamespace(VERSION2=2)
        mqtt_client.MQTTv311 = 4
        mqtt_client.MQTT_ERR_SUCCESS = 0
        mqtt_client.Client = object
        paho.mqtt = mqtt_package
        mqtt_package.client = mqtt_client
        sys.modules["paho"] = paho
        sys.modules["paho.mqtt"] = mqtt_package
        sys.modules["paho.mqtt.client"] = mqtt_client

    try:
        import psutil  # noqa: F401
    except ModuleNotFoundError:
        psutil = types.ModuleType("psutil")
        psutil.cpu_percent = lambda **_kwargs: 0.0
        psutil.virtual_memory = lambda: types.SimpleNamespace(percent=0.0)
        psutil.boot_time = lambda: 0.0
        sys.modules["psutil"] = psutil

    try:
        import RPi.GPIO  # noqa: F401
    except ModuleNotFoundError:
        rpi = types.ModuleType("RPi")
        gpio = types.ModuleType("RPi.GPIO")
        gpio.BCM = 11
        gpio.OUT = 1
        gpio.HIGH = 1
        gpio.LOW = 0
        gpio.setmode = lambda _mode: None
        gpio.setwarnings = lambda _enabled: None
        gpio.setup = lambda *_args, **_kwargs: None
        gpio.output = lambda *_args, **_kwargs: None
        gpio.cleanup = lambda *_args, **_kwargs: None
        rpi.GPIO = gpio
        sys.modules["RPi"] = rpi
        sys.modules["RPi.GPIO"] = gpio

    try:
        import board  # noqa: F401
        import busio  # noqa: F401
        import adafruit_ads1x15  # noqa: F401
        return
    except ModuleNotFoundError:
        pass

    board = types.ModuleType("board")
    board.SCL = object()
    board.SDA = object()
    busio = types.ModuleType("busio")
    busio.I2C = object
    adafruit = types.ModuleType("adafruit_ads1x15")
    ads1x15 = types.ModuleType("adafruit_ads1x15.ads1x15")
    ads1115 = types.ModuleType("adafruit_ads1x15.ads1115")
    analog_in = types.ModuleType("adafruit_ads1x15.analog_in")
    ads1115.ADS1115 = object
    analog_in.AnalogIn = object
    adafruit.ads1x15 = ads1x15
    sys.modules["board"] = board
    sys.modules["busio"] = busio
    sys.modules["adafruit_ads1x15"] = adafruit
    sys.modules["adafruit_ads1x15.ads1x15"] = ads1x15
    sys.modules["adafruit_ads1x15.ads1115"] = ads1115
    sys.modules["adafruit_ads1x15.analog_in"] = analog_in
