"""Seal existing NAS Gmail credentials to a one-use public key from the Pi."""
import base64
import json
import subprocess
import sys


def main():
    public = sys.argv[1]
    if len(base64.b64decode(public, validate=True)) > 4096:
        raise ValueError('Invalid public key')
    code = '''
import os, json, base64
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding
if os.environ.get('AVORA_SMTP_HOST') != 'smtp.gmail.com':
    raise RuntimeError('Expected Gmail')
key = serialization.load_pem_public_key(base64.b64decode(PUBLIC))
payload = json.dumps({'username': os.environ['AVORA_SMTP_USERNAME'],
                      'password': os.environ['AVORA_SMTP_PASSWORD']}).encode()
sealed = key.encrypt(payload, padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),
                                          algorithm=hashes.SHA256(), label=None))
print('SEALED: ' + base64.b64encode(sealed).decode(), flush=True)
'''
    code = 'try:\n' + ''.join('    ' + line + '\n' for line in code.splitlines()) + '''
except Exception as error:
    print(json.dumps({'success': False, 'phase': 'container_seal', 'error_type': type(error).__name__}), flush=True)
    raise SystemExit(1)
'''
    result = subprocess.run(['docker', 'exec', '-i', 'avora-nas-api', 'python', '-'],
                            input='PUBLIC = ' + repr(public) + '\n' + code, text=True,
                            capture_output=True)
    if result.returncode or not result.stdout.strip().startswith('SEALED: '):
        try:
            report = json.loads(result.stdout)
            if report.get('phase') == 'container_seal':
                print(json.dumps({'success': False, 'phase': 'container_seal',
                                  'error_type': str(report.get('error_type', 'Unknown'))[:64]}), flush=True)
        except (ValueError, AttributeError):
            pass
        raise RuntimeError('Credential sealing failed')
    print(result.stdout.strip(), flush=True)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'success': False, 'phase': 'seal_credentials',
                          'error_type': type(error).__name__}), flush=True)
        sys.exit(1)
