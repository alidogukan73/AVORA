"""Shared MQTT credential validation without logging sensitive values."""

from __future__ import annotations

import os
from typing import Protocol


class MqttCredentialClient(Protocol):
    def username_pw_set(self, username: str, password: str) -> None: ...


def configure_mqtt_credentials(
    client: MqttCredentialClient,
    username: str | None = None,
    password: str | None = None,
) -> bool:
    """Configure both credential fields or reject an unsafe partial setup."""

    if username is None and password is None:
        username = os.environ.get("AVORA_MQTT_USERNAME", "")
        password = os.environ.get("AVORA_MQTT_PASSWORD", "")
    normalized_username = str(username or "").strip()
    normalized_password = str(password or "")
    if bool(normalized_username) != bool(normalized_password):
        raise ValueError(
            "MQTT username and password must either both be set or both be empty."
        )
    if not normalized_username:
        return False
    client.username_pw_set(normalized_username, normalized_password)
    return True
