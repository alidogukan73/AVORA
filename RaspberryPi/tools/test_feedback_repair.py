"""Guard checks for the credential-only feedback repair tool."""
import sys
import time
import unittest
from pathlib import Path
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.repair_feedback_email import update_environment, parse_environment, ensure_idle, transfer_path


class FeedbackRepairTest(unittest.TestCase):
    def test_preserves_other_settings_and_replaces_all_duplicate_secrets(self):
        original = '# keep\nAVORA_GMAIL_APP_PASSWORD=old\nAVORA_FEEDBACK_EMAIL_ENABLED=false\nAVORA_GMAIL_APP_PASSWORD=duplicate\nAVORA_FEEDBACK_EMAIL_SEND_EXISTING=false\nAVORA_FEEDBACK_EMAIL_TO=owner@gmail.com\n'
        updated = update_environment(original, 'A' * 16)
        self.assertEqual(1, updated.count('AVORA_GMAIL_APP_PASSWORD='))
        self.assertNotIn('old', updated)
        values = parse_environment(updated)
        self.assertEqual('true', values['AVORA_FEEDBACK_EMAIL_ENABLED'])
        self.assertEqual('false', values['AVORA_FEEDBACK_EMAIL_SEND_EXISTING'])
        self.assertEqual('owner@gmail.com', values['AVORA_FEEDBACK_EMAIL_TO'])
        self.assertTrue(updated.startswith('# keep\n'))

    def test_rejects_secret_with_environment_injection(self):
        with self.assertRaises(ValueError):
            update_environment('', 'abc\nOTHER=true')
        with self.assertRaises(ValueError):
            transfer_path('../elsewhere')

    def device(self, status=None, commands=None, zones=None):
        values = {'status': status or {'online': True, 'last_seen_epoch': int(time.time())},
                  'commands': commands or {}, 'zones': zones or {}}
        device = Mock()
        device.child.side_effect = lambda key: Mock(get=lambda: values[key])
        return device

    def test_only_fresh_idle_status_allows_restart(self):
        ensure_idle(self.device())
        with self.assertRaises(RuntimeError):
            ensure_idle(self.device(status={'online': True, 'last_seen_epoch': 1}))
        with self.assertRaises(RuntimeError):
            ensure_idle(self.device(commands={'manual_watering': {'requested': True}}))
        with self.assertRaises(RuntimeError):
            ensure_idle(self.device(zones={'zone': {'valve_open': True}}))
        with self.assertRaises(RuntimeError):
            ensure_idle(self.device(commands={'relay': True}))


if __name__ == '__main__':
    unittest.main()
