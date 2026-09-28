import base64
import contextlib
import io
import json
import os
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
import export_feedback_credentials as export


class FeedbackCredentialExportTest(unittest.TestCase):
    def test_only_sealed_payload_leaves_container(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        public = base64.b64encode(key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
        password = 'TestSecretValue1'

        def container_run(_command, **kwargs):
            with contextlib.redirect_stdout(io.StringIO()) as output:
                exec(compile(kwargs['input'], '<container-probe>', 'exec'), {})
            return SimpleNamespace(returncode=0, stdout=output.getvalue())

        with patch.dict(os.environ, {'AVORA_SMTP_HOST': 'smtp.gmail.com',
                                    'AVORA_SMTP_USERNAME': 'test@gmail.com',
                                    'AVORA_SMTP_PASSWORD': password}), \
                patch.object(export.sys, 'argv', ['export.py', public]), \
                patch.object(export.subprocess, 'run', side_effect=container_run), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            export.main()
        sealed = output.getvalue().strip().removeprefix('SEALED: ')
        self.assertNotIn(password, output.getvalue())
        self.assertNotIn('test@gmail.com', output.getvalue())
        plaintext = key.decrypt(base64.b64decode(sealed), padding.OAEP(
            mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
        self.assertEqual({'username': 'test@gmail.com', 'password': password}, json.loads(plaintext))

    def test_container_error_does_not_forward_stderr(self):
        with patch.object(export.sys, 'argv', ['export.py', 'YQ==']), \
                patch.object(export.subprocess, 'run', return_value=SimpleNamespace(
                    returncode=1, stdout='', stderr='private diagnostic value')), \
                contextlib.redirect_stdout(io.StringIO()) as output:
            with self.assertRaises(RuntimeError):
                export.main()
        self.assertNotIn('private diagnostic value', output.getvalue())


if __name__ == '__main__':
    unittest.main()
