"""
Command state model.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class CommandState:
    """
    Represents commands received from Firebase.
    """

    auto_mode: bool = True

    relay: bool = False
    relay_requested_at_ms: int = 0

    enabled: bool = True

    moisture_limit: int = 40

    pump_duration: int = 30

    # Hysteresis (%)
    restart_delta: int = 10

    # Minimum interval between irrigations (seconds)
    cooldown_seconds: int = 120

    # Device restart command
    restart_device: bool = False

    # One-shot zone valve test command from Android.
    zone_test_requested: bool = False
    zone_test_request_id: str = ""
    zone_test_zone_id: str = ""
    zone_test_valve_id: str = ""
    zone_test_duration: int = 10
    zone_test_cancel_requested: bool = False
    zone_test_requested_at_ms: int = 0

    # One-shot, bounded manual watering command from Android.  Unlike a
    # valve test this command owns the complete valve -> pump -> close cycle.
    manual_watering_requested: bool = False
    manual_watering_request_id: str = ""
    manual_watering_zone_id: str = ""
    manual_watering_valve_id: str = ""
    manual_watering_duration: int = 30
    manual_watering_cancel_requested: bool = False
    manual_watering_requested_at_ms: int = 0

    # One-shot reset of one or more zones' transient assistant state.
    irrigation_assistant_reset_requested: bool = False
    irrigation_assistant_reset_request_id: str = ""
    irrigation_assistant_reset_zone_id: str = ""
    irrigation_assistant_reset_zone_ids: tuple[str, ...] = ()
    irrigation_assistant_reset_requested_at_ms: int = 0

    # One-shot, validated Raspberry Pi IPv4 configuration request.
    network_configuration_requested: bool = False
    network_configuration_request_id: str = ""
    network_configuration_interface: str = ""
    network_configuration_mode: str = ""
    network_configuration_ip_address: str = ""
    network_configuration_prefix_length: int = 24
    network_configuration_gateway: str = ""
    network_configuration_primary_dns: str = ""
    network_configuration_secondary_dns: str = ""
    network_configuration_requested_at_ms: int = 0
    network_configuration_expires_at_ms: int = 0
    network_configuration_source: str = ""
