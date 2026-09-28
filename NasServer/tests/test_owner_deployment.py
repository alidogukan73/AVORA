import importlib.util
from pathlib import Path
import sys
import json
import tempfile
import unittest
from unittest.mock import MagicMock, patch


class OwnerDeploymentTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location("owner_deployment", Path(__file__).parents[1] / "deploy_owner_update.py")
        self.module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {"fcntl": MagicMock()}):
            spec.loader.exec_module(self.module)

    def test_runtime_settings_and_mounts_are_preserved(self):
        configured = {"read_only": True, "mem_limit": "384m", "environment": {"OLD": "stale"}}
        running = {"Config": {"Env": ["SMTP_PASSWORD=test-only", "SETTING=a=b"],
                              "Image": "current-image", "Cmd": ["python", "-m", "avora_nas.server"],
                              "WorkingDir": "/app", "Labels": {"com.docker.compose.project": "avora", "io.portainer.test": "keep"}},
                   "Mounts": [{"Type": "bind", "Source": "/existing/photos", "Destination": "/data/photos", "RW": True},
                              {"Type": "bind", "Source": "/existing/app", "Destination": "/app", "RW": False}]}
        result = self.module.runtime_service(configured, running)
        self.assertEqual({"SMTP_PASSWORD": "test-only", "SETTING": "a=b"}, result["environment"])
        self.assertTrue(result["read_only"])
        self.assertEqual("384m", result["mem_limit"])
        self.assertEqual("/existing/photos", result["volumes"][0]["source"])
        self.assertTrue(result["volumes"][1]["read_only"])
        self.assertEqual({"io.portainer.test": "keep"}, result["labels"])
        self.assertEqual({"OLD": "stale"}, configured["environment"])

    def test_unexpected_storage_fails_closed(self):
        running = {"Config": {"Env": [], "Image": "image", "Cmd": [], "WorkingDir": ""},
                   "Mounts": [{"Type": "volume"}]}
        with self.assertRaises(RuntimeError):
            self.module.runtime_service({}, running)

    def test_existing_wildcard_and_loopback_bindings_remain_unchanged(self):
        for address in ("", "0.0.0.0", "127.0.0.1", "192.168.1.111"):
            inspected = {"HostConfig": {"PortBindings": {"8787/tcp": [{"HostIp": address, "HostPort": "18787"}]}}}
            ports, urls = self.module.published_ports(inspected)
            self.assertEqual(address, ports[0].get("host_ip", ""))
            self.assertEqual("18787", ports[0]["published"])
            expected = address if address not in ("", "0.0.0.0") else "127.0.0.1"
            self.assertEqual([f"http://{expected}:18787/health"], urls)

    def test_dual_stack_and_additional_ports_are_preserved(self):
        inspected = {"HostConfig": {"PortBindings": {
            "8787/tcp": [{"HostIp": "0.0.0.0", "HostPort": "18787"}, {"HostIp": "::", "HostPort": "18787"}],
            "41641/udp": [{"HostIp": "127.0.0.1", "HostPort": "41641"}],
        }}}
        ports, urls = self.module.published_ports(inspected)
        self.assertEqual(3, len(ports))
        self.assertEqual("::", ports[1]["host_ip"])
        self.assertIn("http://[::1]:18787/health", urls)
        self.assertEqual("udp", ports[2]["protocol"])

    def test_missing_or_invalid_api_bindings_fail_closed(self):
        for bindings in ({}, {"8787/tcp": [{"HostIp": "invalid", "HostPort": "18787"}]},
                         {"8787/tcp": [{"HostIp": "", "HostPort": "0"}]}):
            with self.assertRaises((RuntimeError, ValueError)):
                self.module.published_ports({"HostConfig": {"PortBindings": bindings}})

    def test_verifier_reports_phase_without_stderr_secrets(self):
        with tempfile.TemporaryDirectory() as folder:
            stage = Path(folder)
            (stage / "verify_owner_session.py").write_text("pass", encoding="utf-8")
            (stage / "firebase-public.json").write_text("{}", encoding="utf-8")
            report = {"success": False, "phase": "firebase_sign_in", "http_status": 400}
            result = MagicMock(returncode=1, stdout=json.dumps(report), stderr="SECRET-token")
            with patch.object(self.module, "STAGE", stage), patch.object(self.module.subprocess, "run", return_value=result):
                with self.assertRaises(RuntimeError) as failure:
                    self.module.verify_container("candidate")
            self.assertIn("firebase_sign_in", str(failure.exception))
            self.assertNotIn("SECRET", str(failure.exception))

    def test_candidate_uses_database_copy_and_is_removed_after_failed_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            backup = Path(folder) / "backup"
            backup.mkdir()
            (backup / "accounts.sqlite3").write_bytes(b"test database")
            calls = []
            def run(*args, **kwargs):
                calls.append(args)
                return "exact-created-id\n" if args[1] == "create" else ""
            api = {"NetworkSettings": {"Networks": {"avora_default": {}}}}
            with patch.object(self.module, "run", side_effect=run), \
                    patch.object(self.module, "verify_container", side_effect=RuntimeError("probe failed")):
                with self.assertRaisesRegex(RuntimeError, "probe failed"):
                    self.module.validate_candidate(backup, {}, api)
            self.assertEqual(b"test database", (backup / "validation-data/database/accounts.sqlite3").read_bytes())
            self.assertIn(str(backup / "validation-data") + ":/data", calls[0])
            self.assertNotIn("-p", calls[0])
            self.assertEqual(("docker", "rm", "-f", "exact-created-id"), calls[-1])


class OwnerVerificationReportTest(unittest.TestCase):
    def test_custom_token_response_without_local_id_verifies_token_subject(self):
        import base64
        import io
        spec = importlib.util.spec_from_file_location("owner_verification", Path(__file__).parents[1] / "verify_owner_session.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.PUBLIC = {"api_key": "test-public-key", "database_url": "https://example.test"}
        uid = "avora_nas_test"
        payload = base64.urlsafe_b64encode(json.dumps({"sub": uid, "avora_device_id": "avora-001"}).encode()).decode().rstrip("=")
        firebase_response = {"idToken": "header." + payload + ".signature", "refreshToken": "unused", "expiresIn": "3600"}
        service = MagicMock()
        service.accounts._connect.return_value.__enter__.return_value.execute.return_value.fetchall.return_value = [
            {"id": "test-id", "email": "owner@example.test", "display_name": "Owner", "role": "admin"}]
        service.accounts.create_session_for_user.return_value.token = "test-nas-session"
        with patch.object(module.Settings, "from_environment"), \
                patch.object(module, "AvoraService", return_value=service), \
                patch.object(module, "post", side_effect=[{"firebase_uid": uid, "custom_token": "test"}, firebase_response] * 2), \
                patch.object(module.urllib.request, "urlopen", side_effect=lambda *args, **kwargs: io.BytesIO(b'{"zone-1": {}}')):
            report = module.verify()
        self.assertTrue(report["success"])
        self.assertTrue(report["stable_owner_identity"])
        self.assertEqual(2, service.logout.call_count)

    def test_http_error_does_not_disclose_url_or_response_secrets(self):
        import io
        import urllib.error
        spec = importlib.util.spec_from_file_location("owner_verification", Path(__file__).parents[1] / "verify_owner_session.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.PHASE = "garden_read"
        error = urllib.error.HTTPError("https://example/?auth=SECRET", 401, "SECRET", {},
                                       io.BytesIO(b'{"error":"Permission denied","token":"SECRET"}'))
        report = module.failure_report(error)
        self.assertEqual("garden_read", report["phase"])
        self.assertEqual(401, report["http_status"])
        self.assertEqual("Permission denied", report["error_code"])
        self.assertNotIn("SECRET", json.dumps(report))
