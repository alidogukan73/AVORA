"""Regression checks for low-bandwidth Firebase command synchronization."""

from __future__ import annotations

import sys
import threading
import time
import types
from pathlib import Path
from types import SimpleNamespace


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

try:
    import paho.mqtt.client  # noqa: F401
except ModuleNotFoundError:
    paho = types.ModuleType("paho")
    paho_mqtt = types.ModuleType("paho.mqtt")
    mqtt_client = types.ModuleType("paho.mqtt.client")
    paho.mqtt = paho_mqtt
    paho_mqtt.client = mqtt_client
    sys.modules["paho"] = paho
    sys.modules["paho.mqtt"] = paho_mqtt
    sys.modules["paho.mqtt.client"] = mqtt_client


from core.firebase_service import FirebaseService
from models.command_state import CommandState


class FakeLogger:
    def info(self, *args, **kwargs) -> None:
        pass

    def warning(self, *args, **kwargs) -> None:
        pass

    def debug(self, *args, **kwargs) -> None:
        pass


class FakeDeviceControl:
    def restart_device(self) -> None:
        raise AssertionError("Unexpected restart command")


class FakePublisher:
    def stop(self) -> None:
        pass


class FakeListenerRegistration:
    def __init__(self) -> None:
        self.closed = False

    def close(self) -> None:
        self.closed = True


class FakeCommandReference:
    def __init__(self, payload: dict, fail_listener: bool = False) -> None:
        self.payload = payload
        self.fail_listener = fail_listener
        self.callback = None
        self.get_calls = 0
        self.registration = FakeListenerRegistration()

    def listen(self, callback):
        if self.fail_listener:
            raise RuntimeError("stream unavailable")
        self.callback = callback
        return self.registration

    def get(self):
        self.get_calls += 1
        return dict(self.payload)


class FakeDeviceReference:
    def __init__(self, commands: FakeCommandReference) -> None:
        self.commands = commands

    def child(self, path: str) -> FakeCommandReference:
        assert path == "commands"
        return self.commands


def command_payload(*, relay: bool = False) -> dict:
    return {
        "auto_mode": True,
        "relay": relay,
        "enabled": True,
        "moisture_limit": 40,
        "pump_duration": 10,
        "restart_delta": 10,
        "cooldown_seconds": 600,
        "restart_device": False,
    }


def make_service(reference: FakeDeviceReference) -> FirebaseService:
    service = FirebaseService.__new__(FirebaseService)
    service._logger = FakeLogger()
    service._command_state = CommandState()
    service._command_lock = threading.Lock()
    service._sync_thread = None
    service._command_listener = None
    service._running = False
    service._stop_event = threading.Event()
    service._retry_delay = 0.5
    service._max_retry_delay = 30.0
    service._device_ref = lambda: reference
    service.device_control = FakeDeviceControl()
    service._sensor_config_publisher = FakePublisher()
    return service


def main() -> None:
    commands = FakeCommandReference(command_payload())
    service = make_service(FakeDeviceReference(commands))
    service.start_command_sync()

    assert commands.callback is not None
    assert service._sync_thread is None
    assert commands.get_calls == 0

    commands.callback(SimpleNamespace(
        event_type="put",
        path="/",
        data=command_payload(relay=True),
    ))
    assert service.command_state.relay is True
    assert commands.get_calls == 0

    commands.payload = command_payload(relay=False)
    commands.callback(SimpleNamespace(
        event_type="patch",
        path="/",
        data={"relay": False},
    ))
    assert service.command_state.relay is False
    assert commands.get_calls == 1

    service.stop_command_sync()
    assert commands.registration.closed is True

    fallback_commands = FakeCommandReference(
        command_payload(),
        fail_listener=True,
    )
    fallback = make_service(FakeDeviceReference(fallback_commands))
    fallback.start_command_sync()
    deadline = time.monotonic() + 1.0
    while fallback_commands.get_calls == 0 and time.monotonic() < deadline:
        time.sleep(0.01)
    fallback.stop_command_sync()
    assert fallback_commands.get_calls >= 1

    print("[PASS] Firebase commands use realtime sync with a safe fallback.")


if __name__ == "__main__":
    main()
