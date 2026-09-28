"""Repair feedback credentials received with one-use RSA encryption from the NAS.

Run with sudo on the Pi. No secret values are printed or accepted as arguments.
"""
import base64
import json
import os
import re
import secrets
import shlex
import shutil
import ssl
import smtplib
import imaplib
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path('/home/ali/AVORA/RaspberryPi')
TARGET = Path('/etc/avora/feedback-email.env')
PHASE = 'initialization'


def step(name):
    global PHASE
    PHASE = name
    print('STEP: ' + name, flush=True)


def transfer_path(identifier):
    if not re.fullmatch(r'[a-f0-9]{32}', identifier):
        raise ValueError('Invalid transfer identifier')
    return Path('/run/avora-feedback-' + identifier)


def prepare():
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.hazmat.primitives import serialization
    identifier = secrets.token_hex(16)
    folder = transfer_path(identifier)
    folder.mkdir(mode=0o700)
    key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    private = key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    descriptor = os.open(folder / 'key.pem', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, 'wb') as stream:
        stream.write(private)
    public = key.public_key().public_bytes(serialization.Encoding.PEM,
                                          serialization.PublicFormat.SubjectPublicKeyInfo)
    print(json.dumps({'transfer_id': identifier, 'public_key': base64.b64encode(public).decode()}))


def decrypt(identifier, sealed):
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.asymmetric import padding
    folder = transfer_path(identifier)
    try:
        key = serialization.load_pem_private_key((folder / 'key.pem').read_bytes(), password=None)
        plaintext = key.decrypt(base64.b64decode(sealed, validate=True),
                                padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),
                                             algorithm=hashes.SHA256(), label=None))
        return json.loads(plaintext)
    finally:
        (folder / 'key.pem').unlink(missing_ok=True)
        folder.rmdir()


def update_environment(original, password):
    if not re.fullmatch(r'[A-Za-z0-9]{16}', password):
        raise ValueError('Invalid Gmail app password')
    replacements = {'AVORA_FEEDBACK_EMAIL_ENABLED': 'true', 'AVORA_GMAIL_APP_PASSWORD': password}
    lines = []
    for line in original.splitlines():
        key = line.split('=', 1)[0].strip()
        if key in replacements:
            continue
        lines.append(line)
    lines.extend(key + '=' + value for key, value in replacements.items())
    return '\n'.join(lines) + '\n'


def parse_environment(content):
    values = {}
    for line in content.splitlines():
        if line.strip() and not line.lstrip().startswith('#') and '=' in line:
            key, raw = line.split('=', 1)
            parts = shlex.split(raw)
            values[key.strip()] = parts[0] if parts else ''
    return values


def systemctl(*arguments):
    return subprocess.check_output(['systemctl', *arguments], text=True, stderr=subprocess.DEVNULL).strip()


def ensure_idle(device):
    status = device.child('status').get() or {}
    commands = device.child('commands').get() or {}
    zones = device.child('zones').get() or {}
    if not status.get('online') or abs(time.time() - int(status.get('last_seen_epoch', 0))) > 60:
        raise RuntimeError('Current device status required')
    if status.get('relay') or status.get('valve_open') or commands.get('relay'):
        raise RuntimeError('Active watering; retry later')
    manual = commands.get('manual_watering') or {}
    if isinstance(manual, dict) and (manual.get('requested') or manual.get('active')):
        raise RuntimeError('Pending watering; retry later')
    if any(isinstance(zone, dict) and (zone.get('watering_active') or zone.get('valve_open')) for zone in zones.values()):
        raise RuntimeError('Active valve; retry later')


def apply(identifier):
    step('decrypt_credentials')
    credentials = decrypt(identifier, sys.stdin.read(8192).strip())
    original = TARGET.read_text()
    values = parse_environment(original)
    if credentials.get('username') != values.get('AVORA_FEEDBACK_EMAIL_FROM'):
        raise RuntimeError('Sender mismatch')
    candidate = update_environment(original, credentials['password'])
    os.environ.update(parse_environment(candidate))
    os.chdir(ROOT)
    sys.path.insert(0, str(ROOT))
    from services.feedback_email_service import FeedbackEmailSettings
    from tools.configure_feedback_email import write_private_environment
    from core.config import FirebaseConfig, AppConfig
    import firebase_admin
    from firebase_admin import credentials as firebase_credentials, db
    settings = FeedbackEmailSettings.from_environment()
    settings.validate()
    # Never send the transferred credential to a configured third-party host.
    if settings.smtp_host != 'smtp.gmail.com' or settings.imap_host != 'imap.gmail.com':
        raise RuntimeError('Expected Gmail endpoints')
    step('verify_smtp_and_imap')
    with smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port, timeout=15,
                          context=ssl.create_default_context()) as client:
        client.login(settings.sender, settings.app_password)
    with imaplib.IMAP4_SSL(settings.imap_host, settings.imap_port, timeout=15,
                           ssl_context=ssl.create_default_context()) as client:
        client.login(settings.sender, settings.app_password)
    path = Path(FirebaseConfig.CREDENTIALS_FILE)
    if not path.is_absolute():
        path = ROOT / path
    firebase_admin.initialize_app(firebase_credentials.Certificate(str(path)),
                                 {'databaseURL': FirebaseConfig.DATABASE_URL, 'httpTimeout': 15})
    device = db.reference('devices/' + AppConfig.DEVICE_ID)
    step('verify_idle_device')
    ensure_idle(device)
    if systemctl('is-active', 'avora.service') != 'active':
        raise RuntimeError('Service must be active')
    previous_pid = systemctl('show', 'avora.service', '-p', 'MainPID', '--value')
    backup = TARGET.with_name(TARGET.name + '.before-repair-' + time.strftime('%Y%m%d-%H%M%S'))
    shutil.copy2(TARGET, backup)
    os.chmod(backup, 0o600)
    step('apply_configuration')
    write_private_environment(TARGET, candidate)
    try:
        # Recheck after network authentication and immediately before restarting.
        ensure_idle(device)
        step('restart_idle_service')
        subprocess.run(['systemctl', 'restart', 'avora.service'], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=50)
        started = time.time()
        for _ in range(30):
            status = device.child('status').get() or {}
            if systemctl('is-active', 'avora.service') == 'active' and status.get('online') and int(status.get('last_seen_epoch', 0)) >= started:
                break
            time.sleep(2)
        else:
            raise RuntimeError('Service did not recover')
        pid = systemctl('show', 'avora.service', '-p', 'MainPID', '--value')
        env = dict(part.split(b'=', 1) for part in Path('/proc/' + pid + '/environ').read_bytes().split(b'\0') if b'=' in part)
        if pid == previous_pid or env.get(b'AVORA_GMAIL_APP_PASSWORD') != settings.app_password.encode() or env.get(b'AVORA_FEEDBACK_EMAIL_ENABLED') != b'true':
            raise RuntimeError('Running settings mismatch')
    except Exception:
        write_private_environment(TARGET, original)
        step('previous_configuration_restored')
        # Restart only if device state still proves there is no watering.
        ensure_idle(device)
        subprocess.run(['systemctl', 'restart', 'avora.service'], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=50)
        raise
    step('verify_feedback_delivery')
    feedback_id = 'smtp-acceptance-' + secrets.token_hex(12)
    feedback = db.reference('feedback_devices/' + AppConfig.DEVICE_ID + '/user_feedback/' + feedback_id)
    feedback.set({'type': 'suggestion', 'subject': 'AVORA e-posta kabul testi',
                  'description': 'Pi geri bildirim kanalinin kontrollu teslimat testi.',
                  'area': 'system', 'device_id': AppConfig.DEVICE_ID,
                  'created_at': int(time.time() * 1000)})
    try:
        for _ in range(45):
            delivery = feedback.child('email_delivery').get() or {}
            if delivery.get('status') == 'sent':
                break
            time.sleep(3)
        else:
            raise TimeoutError('Background delivery not confirmed')
        with imaplib.IMAP4_SSL(settings.imap_host, settings.imap_port, timeout=15,
                               ssl_context=ssl.create_default_context()) as mailbox:
            mailbox.login(settings.sender, settings.app_password)
            for _ in range(10):
                mailbox.select('INBOX', readonly=True)
                status, matches = mailbox.search(None, 'HEADER', 'Message-ID',
                                                 '<' + feedback_id + '@feedback.avora-alidogukan>')
                if status == 'OK' and any(matches):
                    break
                time.sleep(3)
            else:
                raise TimeoutError('Exact feedback email missing from INBOX')
    finally:
        feedback.delete()
    print(json.dumps({'success': True, 'smtp_authentication': True, 'imap_authentication': True,
                      'background_delivery': True, 'inbox_delivery': True,
                      'test_feedback_removed': True, 'enabled': True,
                      'delivery_mode': settings.resolved_delivery_mode(), 'backup': str(backup),
                      'service_active': systemctl('is-active', 'avora.service') == 'active'}), flush=True)


if __name__ == '__main__':
    try:
        if os.geteuid() != 0:
            raise PermissionError('Root required')
        if sys.argv[1:] == ['prepare']:
            prepare()
        elif len(sys.argv) == 3 and sys.argv[1] == 'apply':
            apply(sys.argv[2])
        else:
            raise ValueError('Expected prepare or apply')
    except Exception as error:
        print(json.dumps({'success': False, 'phase': PHASE, 'error_type': type(error).__name__}), flush=True)
        sys.exit(1)
