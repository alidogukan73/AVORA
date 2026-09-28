import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location('smtp_setup', Path(__file__).parents[1] / 'configure_recovery_smtp.py')
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class SmtpSetupTest(unittest.TestCase):
    def test_stale_tunnel_repair_never_recreates_api(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'backups').mkdir()
            api = {'Id': 'current-api', 'State': {'Running': True}}
            tunnel = {'State': {'Running': False, 'Status': 'exited', 'ExitCode': 128},
                      'HostConfig': {'NetworkMode': 'container:old-api'}}
            with patch.object(setup, 'ROOT', root), patch.object(setup, 'run') as run, \
                    patch.object(setup, 'verify_tunnel') as verify, contextlib.redirect_stdout(io.StringIO()):
                self.assertTrue(setup.repair_stale_tunnel(self.config(), api, tunnel, 'avora', Mock(), []))
                up = [call.args for call in run.call_args_list if 'up' in call.args]
                self.assertEqual(1, len(up))
                self.assertEqual('avora-tunnel', up[0][-1])
                self.assertIn('--no-deps', up[0])
                self.assertNotIn('avora-api', up[0])
                verify.assert_called_once_with('current-api')

    def test_unrelated_tunnel_crash_is_not_repaired(self):
        tunnel = {'State': {'Running': False, 'Status': 'exited', 'ExitCode': 1},
                  'HostConfig': {'NetworkMode': 'container:old-api'}}
        with patch.object(setup, 'run') as run:
            with self.assertRaises(setup.SetupError):
                setup.repair_stale_tunnel(self.config(), {'State': {'Running': True}}, tunnel, 'avora', Mock(), [])
            run.assert_not_called()

    def test_api_replacement_removes_tunnel_first_without_deleting_volumes(self):
        with patch.object(setup, 'run') as run, patch.object(setup, 'verify_tunnel') as verify:
            setup.apply_services(Path('/config.json'), 'avora', Mock(), [])
            calls = [call.args for call in run.call_args_list]
            self.assertIn('rm', calls[0])
            self.assertEqual('avora-tunnel', calls[0][-1])
            self.assertNotIn('-v', calls[0])
            self.assertEqual('avora-api', calls[1][-1])
            self.assertEqual('avora-tunnel', calls[2][-1])
            verify.assert_called_once_with()

    def test_diagnostic_redacts_container_error_and_logs(self):
        item = {'State': {'Status': 'exited', 'ExitCode': 1, 'Running': False,
                         'Error': 'cannot join network namespace; password=secret-value'}}
        value = setup.service_diagnostic(setup.TUNNEL, item, 'token=secret-value permission denied')
        self.assertEqual('exited', value['status'])
        self.assertEqual(['network_namespace_missing', 'permission_denied'], value['error_signals'])
        self.assertNotIn('secret-value', json.dumps(value))

    def test_diagnostics_do_not_print_unknown_exception_content(self):
        setup.PHASE = 'smtp_authentication'
        detail = setup.safe_error(RuntimeError('password=do-not-log'))
        self.assertIn('smtp_authentication', detail)
        self.assertNotIn('do-not-log', detail)
        self.assertIn('Unexpected Compose services', setup.safe_error(setup.SetupError('Unexpected Compose services')))

    def config(self):
        return {'name': 'avora', 'services': {
            'avora-api': {'container_name': setup.API, 'image': 'api-image',
                          'environment': {'EXISTING': 'keep', 'AVORA_FIREBASE_DEVICE_ID': 'avora-001'},
                          'ports': ['127.0.0.1:18787:8787'], 'read_only': True,
                          'volumes': ['/existing:/data'], 'mem_limit': '384m'},
            'avora-tunnel': {'container_name': setup.TUNNEL, 'image': 'tunnel-image',
                             'network_mode': 'service:avora-api', 'environment': {'TS_STATE_DIR': '/state'}},
        }}

    def test_only_smtp_environment_changes(self):
        source = self.config()
        original = copy.deepcopy(source)
        updated = setup.smtp_config(source, 'abcd efgh ijkl mnop')
        self.assertEqual(original, source)
        self.assertEqual('abcdefghijklmnop', updated['services']['avora-api']['environment']['AVORA_SMTP_PASSWORD'])
        for key, value in original['services']['avora-api'].items():
            if key != 'environment':
                self.assertEqual(value, updated['services']['avora-api'][key])
        for key, value in original['services']['avora-api']['environment'].items():
            self.assertEqual(value, updated['services']['avora-api']['environment'][key])
        self.assertEqual(original['services']['avora-tunnel'], updated['services']['avora-tunnel'])

    def test_invalid_password_and_unexpected_services_rejected(self):
        for value in ('', 'short', 'a' * 15 + '$', 'a' * 17):
            with self.assertRaises(ValueError):
                setup.password_value(value)
        config = self.config()
        config['services']['other'] = {}
        with self.assertRaises(RuntimeError):
            setup.validate_services(config)
        config = self.config()
        config['services']['avora-api']['container_name'] = 'other'
        with self.assertRaises(RuntimeError):
            setup.validate_services(config)

    def exercise(self, failure=None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'backups').mkdir()
            source = self.config()
            original = json.dumps(source)
            (root / 'stack.yml').write_text(original)
            portainer = root / 'portainer.yml'
            portainer.write_text(original)
            api = {'Config': {'Labels': {'com.docker.compose.project': 'avora'}},
                   'State': {'Running': True}, 'Image': 'api-id'}
            tunnel = copy.deepcopy(api)
            tunnel['Image'] = 'tunnel-id'
            calls = []

            def run(*args, input_text=None):
                calls.append((args, input_text))
                if args[1] == 'inspect':
                    return json.dumps([api, tunnel])
                if args[1:3] == ('image', 'inspect'):
                    return json.dumps([{'Id': 'api-id' if args[-1] == 'api-image' else 'tunnel-id'}])
                if 'config' in args and '--format' in args:
                    return original
                if input_text and setup.AUTH_PROBE in input_text and failure == 'auth':
                    raise RuntimeError('redacted')
                if input_text == setup.DELIVERY_PROBE and failure in ('delivery', 'interrupt'):
                    if failure == 'interrupt':
                        raise KeyboardInterrupt()
                    raise RuntimeError('redacted')
                return ''

            health = Mock()
            with patch.object(setup, 'ROOT', root), patch.object(setup, 'PORTAINER', portainer), \
                    patch.object(setup, 'run', side_effect=run), \
                    patch.object(setup, 'verify_tunnel'), \
                    patch.object(setup.getpass, 'getpass', return_value='abcdefghijklmnop'), \
                    contextlib.redirect_stdout(io.StringIO()) as output:
                action = lambda: setup.configure(lambda config, item: config,
                    lambda item: (source['services']['avora-api']['ports'], ['http://127.0.0.1:18787/health']), health)
                if failure:
                    with self.assertRaises(KeyboardInterrupt if failure == 'interrupt' else RuntimeError):
                        action()
                else:
                    action()
            self.assertNotIn('abcdefghijklmnop', output.getvalue())
            for args, _ in calls:
                self.assertNotIn('abcdefghijklmnop', ' '.join(args))
            restarts = [args for args, _ in calls if 'up' in args]
            if failure == 'auth':
                self.assertEqual([], restarts)
                self.assertEqual([], list((root / 'backups').iterdir()))
            elif failure:
                self.assertEqual(4, len(restarts))
                self.assertIn('rollback.json', ' '.join(restarts[-1]))
            else:
                self.assertEqual(2, len(restarts))
                saved = json.loads((root / 'stack.yml').read_text())
                self.assertEqual(saved, json.loads(portainer.read_text()))
                self.assertEqual('smtp.gmail.com', saved['services']['avora-api']['environment']['AVORA_SMTP_HOST'])
                report = json.loads(next((root / 'backups').glob('*/result.json')).read_text())
                self.assertTrue(report['inbox_delivery'])
            if failure:
                self.assertEqual(original, (root / 'stack.yml').read_text())
                self.assertEqual(original, portainer.read_text())

    def test_auth_failure_does_not_change_deployment(self):
        self.exercise('auth')

    def test_delivery_failure_restores_both_sources_and_services(self):
        self.exercise('delivery')

    def test_interrupt_restores_both_sources_and_services(self):
        self.exercise('interrupt')

    def test_success_persists_settings_and_safe_report(self):
        self.exercise()
