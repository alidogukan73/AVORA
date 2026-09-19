"""Restart authorization timing and actuator safety without real hardware."""
from pathlib import Path
from types import SimpleNamespace
import sys
import threading
import time
import unittest
from unittest.mock import Mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from tools.hardware_test_stubs import install_hardware_import_stubs
install_hardware_import_stubs()
from core.firebase_service import FirebaseService
from models.command_state import CommandState
from services.irrigation_service import IrrigationService


class DeviceRestartSafetyTest(unittest.TestCase):
    def service(self):
        value = object.__new__(IrrigationService)
        value._firebase = Mock()
        value._firebase.consume_restart_command.return_value = True
        value._relay = SimpleNamespace(is_on=False)
        value._zone_executor = SimpleNamespace(active_zone_id=None)
        value._valves = SimpleNamespace(active_valve_id=None)
        value._active_zone_test_request_id = ""
        value._logger = Mock()
        return value

    def test_idle_restart_is_consumed_and_holds_new_watering(self):
        value = self.service()
        self.assertTrue(value._process_device_restart_command(CommandState(restart_device=True)))
        value._firebase.consume_restart_command.assert_called_once()
        value._firebase.device_control.restart_device.assert_called_once()
        self.assertTrue(value._process_device_restart_command(CommandState()))
        value._firebase.device_control.restart_device.assert_called_once()

    def test_pump_valve_zone_and_test_each_block_restart(self):
        for active in ("pump", "valve", "zone", "test"):
            with self.subTest(active=active):
                value = self.service()
                if active == "pump": value._relay.is_on = True
                if active == "valve": value._valves.active_valve_id = "valve-1"
                if active == "zone": value._zone_executor.active_zone_id = "zone-1"
                if active == "test": value._active_zone_test_request_id = "test-1"
                self.assertFalse(value._process_device_restart_command(CommandState(restart_device=True)))
                value._firebase.consume_restart_command.assert_called_once()
                value._firebase.device_control.restart_device.assert_not_called()

    def test_pending_watering_commands_block_restart(self):
        for pending in ("relay", "manual_watering_requested", "zone_test_requested"):
            with self.subTest(pending=pending):
                value = self.service()
                self.assertFalse(value._process_device_restart_command(
                    CommandState(restart_device=True, **{pending: True})))
                value._firebase.device_control.restart_device.assert_not_called()

    def test_stale_or_already_consumed_request_does_not_restart(self):
        value = self.service()
        value._firebase.consume_restart_command.return_value = False
        self.assertFalse(value._process_device_restart_command(CommandState(restart_device=True)))
        value._firebase.device_control.restart_device.assert_not_called()

    def test_acknowledgement_failure_never_reboots(self):
        value = self.service()
        value._firebase.consume_restart_command.side_effect = RuntimeError("offline")
        with self.assertRaises(RuntimeError):
            value._process_device_restart_command(CommandState(restart_device=True))
        value._firebase.device_control.restart_device.assert_not_called()

    def test_failed_reboot_does_not_latch_irrigation(self):
        value = self.service()
        value._firebase.device_control.restart_device.side_effect = RuntimeError("reboot failed")
        with self.assertRaises(RuntimeError):
            value._process_device_restart_command(CommandState(restart_device=True))
        self.assertFalse(getattr(value, "_device_restart_pending", False))

    def consume(self, payload, retry=None):
        value = object.__new__(FirebaseService)
        value._command_lock = threading.Lock()
        value._command_state = CommandState(restart_device=True)
        stored = dict(payload)
        def transaction(callback):
            result = callback(stored)
            if retry is not None:
                result = callback(retry)
            stored.clear()
            stored.update(result)
        reference = Mock()
        reference.child.return_value.transaction.side_effect = transaction
        value._device_ref = lambda: reference
        return value.consume_restart_command(), stored, value.command_state

    def test_consumption_clears_flag_but_preserves_other_commands(self):
        accepted, stored, cached = self.consume({
            "restart_device": True, "restart_requested_at": int(time.time() * 1000),
            "auto_mode": True,
        })
        self.assertTrue(accepted)
        self.assertFalse(stored["restart_device"])
        self.assertFalse(cached.restart_device)
        self.assertTrue(stored["auto_mode"])

    def test_legacy_expired_future_and_boolean_timestamps_are_consumed_without_reboot(self):
        now = int(time.time() * 1000)
        for timestamp in (None, now - 61_000, now + 11_000, True):
            with self.subTest(timestamp=timestamp):
                accepted, stored, _ = self.consume({
                    "restart_device": True, "restart_requested_at": timestamp,
                })
                self.assertFalse(accepted)
                self.assertFalse(stored["restart_device"])

    def test_transaction_retry_cannot_execute_an_already_consumed_request(self):
        accepted, _, _ = self.consume({
            "restart_device": True, "restart_requested_at": int(time.time() * 1000),
        }, retry={"restart_device": False})
        self.assertFalse(accepted)

    def test_acknowledgement_preserves_a_newer_listener_request(self):
        value = object.__new__(FirebaseService)
        value._command_lock = threading.Lock()
        value._command_state = CommandState(restart_device=True)
        newer = CommandState(restart_device=True, auto_mode=False)
        def transaction(callback):
            callback({"restart_device": True, "restart_requested_at": int(time.time() * 1000)})
            value._command_state = newer
        reference = Mock()
        reference.child.return_value.transaction.side_effect = transaction
        value._device_ref = lambda: reference
        self.assertTrue(value.consume_restart_command())
        self.assertIs(value.command_state, newer)

    def test_realtime_listener_only_updates_state_and_never_reboots(self):
        value = object.__new__(FirebaseService)
        value._running = True
        value._stop_event = threading.Event()
        value._command_lock = threading.Lock()
        value._parse_commands = lambda payload: CommandState(restart_device=True)
        value._logger = Mock()
        value.device_control = Mock()
        value._handle_command_event(SimpleNamespace(event_type="put", path="/", data={}))
        self.assertTrue(value.command_state.restart_device)
        value.device_control.restart_device.assert_not_called()


if __name__ == "__main__":
    unittest.main()
