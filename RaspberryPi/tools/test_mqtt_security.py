from __future__ import annotations

import os
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from hardware.mqtt_security import configure_mqtt_credentials


class FakeClient:
    def __init__(self) -> None:
        self.credentials: tuple[str, str] | None = None

    def username_pw_set(self, username: str, password: str) -> None:
        self.credentials = (username, password)


def test_complete_credentials_are_applied() -> None:
    client = FakeClient()
    assert configure_mqtt_credentials(client, " avora-pi ", "secret")
    assert client.credentials == ("avora-pi", "secret")


def test_empty_credentials_keep_legacy_transition_available() -> None:
    client = FakeClient()
    assert not configure_mqtt_credentials(client, "", "")
    assert client.credentials is None


def test_credentials_can_be_loaded_from_service_environment() -> None:
    old_username = os.environ.get("AVORA_MQTT_USERNAME")
    old_password = os.environ.get("AVORA_MQTT_PASSWORD")
    try:
        os.environ["AVORA_MQTT_USERNAME"] = "avora-pi"
        os.environ["AVORA_MQTT_PASSWORD"] = "environment-secret"
        client = FakeClient()
        assert configure_mqtt_credentials(client)
        assert client.credentials == ("avora-pi", "environment-secret")
    finally:
        if old_username is None:
            os.environ.pop("AVORA_MQTT_USERNAME", None)
        else:
            os.environ["AVORA_MQTT_USERNAME"] = old_username
        if old_password is None:
            os.environ.pop("AVORA_MQTT_PASSWORD", None)
        else:
            os.environ["AVORA_MQTT_PASSWORD"] = old_password


def test_partial_credentials_fail_closed() -> None:
    for username, password in (("avora-pi", ""), ("", "secret")):
        client = FakeClient()
        try:
            configure_mqtt_credentials(client, username, password)
        except ValueError:
            pass
        else:
            raise AssertionError("Partial MQTT credentials must be rejected.")


if __name__ == "__main__":
    test_complete_credentials_are_applied()
    test_empty_credentials_keep_legacy_transition_available()
    test_credentials_can_be_loaded_from_service_environment()
    test_partial_credentials_fail_closed()
    print("[PASS] MQTT credential security scenarios.")
