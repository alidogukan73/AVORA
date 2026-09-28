"""Interactive NAS Gmail setup; secrets stay in the terminal and private Compose files.

Run with sudo beside deploy_owner_update.py. Preserves the running deployment,
tests SMTP before restarting, backs up both Compose sources, and rolls back on failure.
"""
from __future__ import annotations

import copy
import getpass
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = Path('/share/Docker/AVORA')
PORTAINER = Path('/share/Docker/PortainerCE/data/compose/1/docker-compose.yml')
SENDER = 'alidogukan@gmail.com'
API = 'avora-nas-api'
TUNNEL = 'avora-tailscale'
PHASE = 'startup'


class SetupError(RuntimeError):
    """Only fixed, non-sensitive messages may be used with this exception."""


def phase(value):
    global PHASE
    PHASE = value
    print('STEP: ' + value, flush=True)


def safe_error(error):
    detail = str(error) if isinstance(error, SetupError) else type(error).__name__
    return 'SMTP setup stopped at ' + PHASE + ': ' + detail


def service_diagnostic(name, item, logs=''):
    state = item.get('State', {})
    status = state.get('Status', '')
    if status not in ('created', 'running', 'paused', 'restarting', 'removing', 'exited', 'dead'):
        status = 'unknown'
    evidence = str(state.get('Error', '')) + '\n' + logs
    patterns = {
        'network_namespace_missing': r'network namespace|no such container|cannot join network',
        'port_in_use': r'address already in use|port is already allocated',
        'permission_denied': r'permission denied|operation not permitted',
        'tailscale_login_required': r'NeedsLogin|not logged in|authentication required',
        'missing_file': r'no such file or directory|FileNotFoundError',
        'storage_error': r'database is locked|database disk image is malformed|no space left',
        'invalid_configuration': r'ConfigurationError|invalid configuration',
        'python_exception': r'Traceback \(most recent call last\)',
    }
    return {'service': name, 'status': status, 'running': bool(state.get('Running')),
            'exit_code': int(state.get('ExitCode', 0)), 'oom_killed': bool(state.get('OOMKilled')),
            'restarts': int(item.get('RestartCount', 0)),
            'health': state.get('Health', {}).get('Status') if state.get('Health', {}).get('Status') in ('healthy', 'unhealthy', 'starting') else 'none',
            'error_signals': [key for key, pattern in patterns.items() if re.search(pattern, evidence, re.I)]}


def diagnose():
    phase('read_only_service_diagnostics')
    items = json.loads(run('docker', 'inspect', API, TUNNEL))
    for name, item in zip((API, TUNNEL), items):
        result = subprocess.run(['docker', 'logs', '--tail', '80', name], text=True, capture_output=True)
        print('DIAG: ' + json.dumps(service_diagnostic(name, item, result.stdout + result.stderr)), flush=True)
    print('DIAG: read-only check complete; no services or SMTP settings changed.', flush=True)


def run(*args, input_text=None):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if result.returncode:
        raise SetupError('Command failed (exit ' + str(result.returncode) + '); sensitive output withheld')
    return result.stdout


def password_value(value):
    value = ''.join(value.split())
    if not re.fullmatch(r'[A-Za-z0-9]{16}', value):
        raise ValueError('Expected a 16-character Google app password')
    return value


def smtp_config(original, password):
    result = copy.deepcopy(original)
    result['services']['avora-api']['environment'].update({
        'AVORA_SMTP_HOST': 'smtp.gmail.com', 'AVORA_SMTP_PORT': '465',
        'AVORA_SMTP_SECURITY': 'ssl', 'AVORA_SMTP_FROM': SENDER,
        'AVORA_SMTP_USERNAME': SENDER, 'AVORA_SMTP_PASSWORD': password_value(password),
    })
    return result


def validate_services(config):
    expected = {'avora-api': API, 'avora-tunnel': TUNNEL}
    if set(config.get('services', {})) != set(expected):
        raise SetupError('Unexpected Compose services')
    for service, name in expected.items():
        if config['services'][service].get('container_name') != name:
            raise SetupError('Unexpected container identity')


def private_json(path, value):
    fd, name = tempfile.mkstemp(prefix='.smtp-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            json.dump(value, stream, indent=2)
            stream.write('\n')
        os.chmod(name, 0o600)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def verify_tunnel(expected_api_id=None):
    for attempt in range(20):
        api, tunnel = json.loads(run('docker', 'inspect', API, TUNNEL))
        if expected_api_id and api['Id'] != expected_api_id:
            raise SetupError('API identity changed during tunnel-only repair')
        if (api['State']['Running'] and tunnel['State']['Running']
                and tunnel['HostConfig']['NetworkMode'] == 'container:' + api['Id']):
            result = subprocess.run(['docker', 'exec', TUNNEL, 'tailscale', 'status', '--json'],
                                    text=True, capture_output=True)
            try:
                connected = json.loads(result.stdout).get('BackendState') == 'Running'
            except (ValueError, AttributeError):
                connected = False
            if result.returncode == 0 and connected:
                return
        time.sleep(2)
    raise SetupError('Tunnel did not reconnect to the current API and Tailscale')


def repair_stale_tunnel(config, api, tunnel, project, health, urls):
    state = tunnel['State']
    if state.get('Running'):
        return False
    if (not api['State'].get('Running') or state.get('Status') != 'exited'
            or state.get('ExitCode') != 128
            or not tunnel.get('HostConfig', {}).get('NetworkMode', '').startswith('container:')
            or config['services']['avora-tunnel'].get('network_mode') != 'service:avora-api'):
        raise SetupError('Stopped tunnel does not match the diagnosed network namespace failure')
    phase('repair_stale_tunnel')
    backup = Path(tempfile.mkdtemp(prefix='tunnel-repair-', dir=ROOT / 'backups'))
    candidate = backup / 'existing-configuration.json'
    private_json(candidate, config)
    private_json(backup / 'previous-tunnel.json', tunnel)
    run('docker', 'compose', '-p', project, '-f', str(candidate), 'config', '--quiet')
    # Recreate only the stopped tunnel. Keep its persistent state and the live API.
    run('docker', 'compose', '-p', project, '-f', str(candidate), 'up', '-d', '--pull', 'never',
        '--no-deps', '--force-recreate', 'avora-tunnel')
    verify_tunnel(api['Id'])
    health(urls)
    private_json(backup / 'result.json', {'success': True, 'api_unchanged': True})
    print('DIAG: tunnel repaired; existing API identity and health verified.', flush=True)
    return True


def apply_services(config_path, project, health, urls):
    # Remove the ephemeral tunnel container before replacing its API network namespace.
    # No volume deletion: Tailscale identity and all application data remain mounted.
    run('docker', 'compose', '-p', project, '-f', str(config_path), 'rm', '--stop', '--force', 'avora-tunnel')
    run('docker', 'compose', '-p', project, '-f', str(config_path), 'up', '-d', '--pull', 'never',
        '--no-deps', '--force-recreate', 'avora-api')
    health(urls)
    run('docker', 'compose', '-p', project, '-f', str(config_path), 'up', '-d', '--pull', 'never',
        '--no-deps', '--force-recreate', 'avora-tunnel')
    verify_tunnel()


# This probe never prints server responses, passwords, codes, or mailbox contents.
AUTH_PROBE = '''
import smtplib, ssl
client = smtplib.SMTP_SSL('smtp.gmail.com', 465, timeout=15, context=ssl.create_default_context())
try:
    client.login(SETTINGS['AVORA_SMTP_USERNAME'], SETTINGS['AVORA_SMTP_PASSWORD'])
finally:
    client.close()
print('SMTP authentication passed.')
'''

DELIVERY_PROBE = '''
import email.utils, imaplib, os, smtplib, ssl, time
from email.message import EmailMessage
from avora_nas.config import Settings
settings = Settings.from_environment()
assert settings.smtp_host == 'smtp.gmail.com' and settings.smtp_security == 'ssl'
message = EmailMessage()
message['From'] = settings.smtp_from
message['To'] = settings.smtp_from
message['Subject'] = 'AVORA NAS - SMTP kurulum testi'
message['Message-ID'] = email.utils.make_msgid(domain='avora.local')
message.set_content('AVORA NAS sifre sifirlama e-posta kanali kuruldu. Bu bir teslimat testidir; hesap sifreniz degistirilmedi.')
client = smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port, timeout=15, context=ssl.create_default_context())
try:
    client.login(settings.smtp_username, settings.smtp_password)
    client.send_message(message)
finally:
    client.close()
print('SMTP test message accepted.', flush=True)
with imaplib.IMAP4_SSL('imap.gmail.com', 993, ssl_context=ssl.create_default_context(), timeout=15) as mailbox:
    mailbox.login(settings.smtp_username, settings.smtp_password)
    for attempt in range(12):
        status, _ = mailbox.select('INBOX', readonly=True)
        assert status == 'OK'
        status, matches = mailbox.search(None, 'HEADER', 'Message-ID', message['Message-ID'])
        if status == 'OK' and any(matches):
            print('Exact test message verified in INBOX.', flush=True)
            break
        time.sleep(5)
    else:
        raise RuntimeError('Test message not found in INBOX')
'''


def main():
    import fcntl
    from deploy_owner_update import runtime_service, published_ports, health

    phase('terminal_check')
    if os.geteuid() != 0:
        raise SetupError('Administrator privilege required; run with sudo')
    if not sys.stdin.isatty():
        raise SetupError('Interactive terminal missing; SSH must use -t')
    if '--diagnose-only' in sys.argv:
        return diagnose()
    os.umask(0o077)
    phase('deployment_lock')
    with (ROOT / 'config/owner-update.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return configure(runtime_service, published_ports, health)


def configure(runtime_service, published_ports, health):
    sources = [ROOT / 'stack.yml', PORTAINER]
    configs = []
    for source_index, path in enumerate(sources):
        phase('read_compose_' + ('avora' if source_index == 0 else 'portainer'))
        if not path.is_file() or path.is_symlink():
            raise SetupError('Expected existing regular Compose source')
        value = json.loads(run('docker', 'compose', '-f', str(path), 'config', '--format', 'json'))
        validate_services(value)
        configs.append(value)
    phase('inspect_running_services')
    api, tunnel = json.loads(run('docker', 'inspect', API, TUNNEL))
    project = api['Config']['Labels'].get('com.docker.compose.project')
    if not project or tunnel['Config']['Labels'].get('com.docker.compose.project') != project:
        raise SetupError('Unexpected Compose project')
    if not api['State']['Running']:
        raise SetupError('Existing AVORA API must be running')
    original = configs[0]
    original['name'] = project
    for service, item in (('avora-api', api), ('avora-tunnel', tunnel)):
        # Stop if the two persisted sources no longer describe the running deployment.
        for source_index, config in enumerate(configs):
            phase('verify_image_' + service + '_' + str(source_index))
            image = json.loads(run('docker', 'image', 'inspect', config['services'][service]['image']))[0]
            if image['Id'] != item['Image']:
                raise SetupError('Compose image does not match running service')
        original['services'][service] = runtime_service(original['services'][service], item)
    phase('preserve_ports')
    ports, urls = published_ports(api)
    original['services']['avora-api']['ports'] = ports
    repair_stale_tunnel(original, api, tunnel, project, health, urls)
    phase('gmail_password_input')
    print('Gmail sender: ' + SENDER, flush=True)
    password = password_value(getpass.getpass('Yeni Gmail uygulama sifresi (gizli): '))
    updated = smtp_config(original, password)
    settings = updated['services']['avora-api']['environment']
    # Input travels over the encrypted SSH session and Docker stdin, never argv/logs.
    phase('smtp_authentication')
    run('docker', 'exec', '-i', API, 'python', '-',
        input_text='SETTINGS = ' + repr({k: v for k, v in settings.items() if k.startswith('AVORA_SMTP_')}) + '\n' + AUTH_PROBE)
    print('SMTP authentication passed; preparing backed-up configuration.', flush=True)
    phase('backup_configuration')
    backup = Path(tempfile.mkdtemp(prefix='smtp-update-' + time.strftime('%Y%m%d-%H%M%S') + '-', dir=ROOT / 'backups'))
    for index, path in enumerate(sources):
        shutil.copy2(path, backup / ('source-' + str(index) + '.json'))
        os.chmod(backup / ('source-' + str(index) + '.json'), 0o600)
    rollback = backup / 'rollback.json'
    candidate = backup / 'updated.json'
    private_json(rollback, original)
    private_json(candidate, updated)
    phase('validate_candidate')
    run('docker', 'compose', '-p', project, '-f', str(candidate), 'config', '--quiet')
    changed = False
    try:
        phase('apply_configuration')
        changed = True
        print('Applying SMTP settings; API and its tunnel will restart briefly.', flush=True)
        apply_services(candidate, project, health, urls)
        phase('verify_api_health')
        health(urls)
        check = "import json,urllib.request; p=__import__('os').environ.get('AVORA_PORT','8787'); assert json.load(urllib.request.urlopen('http://127.0.0.1:'+p+'/health',timeout=5))['storage_ready']"
        run('docker', 'exec', API, 'python', '-c', check)
        phase('verify_inbox_delivery')
        print('API health verified; testing delivery to the sender inbox.', flush=True)
        run('docker', 'exec', '-i', API, 'python', '-', input_text=DELIVERY_PROBE)
        phase('persist_configuration')
        for path in sources:
            private_json(path, updated)
        report = {'success': True, 'smtp_authentication': True, 'inbox_delivery': True,
                  'portainer_source_updated': True, 'backup': str(backup),
                  'account_password_changed': False}
        private_json(backup / 'result.json', report)
        print(json.dumps(report), flush=True)
    except BaseException:
        if changed:
            print('Verification failed; restoring previous service configuration.', flush=True)
            for index, path in enumerate(sources):
                shutil.copy2(backup / ('source-' + str(index) + '.json'), path)
                os.chmod(path, 0o600)
            apply_services(rollback, project, health, urls)
            health(urls)
            print('Previous configuration restored and health verified.', flush=True)
        raise


if __name__ == '__main__':
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        # Do not print exceptions: SMTP or Docker may include secret values.
        print(safe_error(error) + '. No secret values logged.', file=sys.stderr)
        sys.exit(1)
