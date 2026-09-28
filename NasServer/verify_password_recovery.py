"""Verify the installed NAS recovery flow with real mail and isolated account data.

Run on the NAS host with sudo. SMTP credentials remain inside avora-nas-api.
Only the temporary account is reset; the production API and data are untouched.
"""
from __future__ import annotations

import email.policy
import email.parser
import imaplib
import json
import os
import re
import secrets
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path


PHASE = "initialization"


def step(name):
    global PHASE
    PHASE = name
    print("STEP: " + name, flush=True)


def extract_code(raw, recipient):
    message = email.parser.BytesParser(policy=email.policy.default).parsebytes(raw)
    from email.utils import getaddresses
    if recipient not in [address.lower() for _, address in getaddresses(message.get_all("To", []))]:
        raise ValueError("Unexpected recipient")
    body = message.get_body(preferencelist=("plain",))
    if body is None:
        raise ValueError("Missing plain text")
    codes = re.findall(r"(?m)^([A-Za-z0-9_-]{32})\s*$", body.get_content())
    if len(codes) != 1:
        raise ValueError("Expected one recovery code")
    return codes[0]


def inbox_code(settings, recipient):
    with imaplib.IMAP4_SSL("imap.gmail.com", 993,
                          ssl_context=ssl.create_default_context(), timeout=15) as mailbox:
        mailbox.login(settings.smtp_username, settings.smtp_password)
        for _ in range(18):
            status, _ = mailbox.select("INBOX", readonly=True)
            if status != "OK":
                raise RuntimeError("Inbox unavailable")
            status, matches = mailbox.uid("search", None, "HEADER", "To", recipient)
            if status != "OK":
                raise RuntimeError("Inbox search failed")
            for uid in (matches[0] or b"").split()[-3:]:
                status, parts = mailbox.uid("fetch", uid, "(BODY.PEEK[])")
                if status != "OK":
                    continue
                for part in parts:
                    if isinstance(part, tuple) and isinstance(part[1], bytes):
                        return extract_code(part[1], recipient)
            time.sleep(3)
    raise TimeoutError("Recovery message not delivered")


def run_contract(settings, recipient, read_code):
    from avora_nas import server as server_module
    from avora_nas.service import AvoraService
    service = AvoraService(settings)
    server_module.SERVICE = service
    server = server_module.AvoraHttpServer(("127.0.0.1", 0), server_module.Handler)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    base = "http://127.0.0.1:" + str(server.server_address[1])
    old_password, new_password = secrets.token_urlsafe(32), secrets.token_urlsafe(32)

    def request(path, payload=None, token=None, expected=200, method=None):
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        if path == "/v1/setup":
            headers["X-AVORA-Setup-Token"] = settings.setup_token
        req = urllib.request.Request(base + path, headers=headers,
                                     data=json.dumps(payload).encode() if payload is not None else None,
                                     method=method or ("POST" if payload is not None else "GET"))
        try:
            response = urllib.request.urlopen(req, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status, body = response.code, json.load(response)
        if status != expected:
            raise RuntimeError("Unexpected HTTP status")
        return body

    try:
        step("temporary_account")
        request("/v1/setup", {"email": recipient, "display_name": "AVORA recovery acceptance",
                              "password": old_password}, expected=201)
        old_tokens = [request("/v1/auth/login", {"email": recipient, "password": old_password})
                      ["access_token"] for _ in range(2)]
        step("request_recovery_email")
        request("/v1/auth/forgot-password", {"email": recipient}, expected=202)
        step("verify_inbox_delivery")
        code = read_code(settings, recipient)
        step("reset_password")
        payload = {"email": recipient, "code": code, "new_password": new_password}
        if request("/v1/auth/reset-password", payload) != {"reset": True}:
            raise RuntimeError("Reset response mismatch")
        step("reject_reused_code")
        retry = request("/v1/auth/reset-password", payload, expected=400)
        if retry.get("error", {}).get("code") != "invalid_reset_token":
            raise RuntimeError("Reused code not rejected correctly")
        step("reject_old_password_and_sessions")
        request("/v1/auth/login", {"email": recipient, "password": old_password}, expected=401)
        for token in old_tokens:
            request("/v1/me", token=token, expected=401)
        step("new_password_login")
        new_token = request("/v1/auth/login", {"email": recipient, "password": new_password})["access_token"]
        if request("/v1/me", token=new_token)["user"]["email"] != recipient:
            raise RuntimeError("Account mismatch")
        request("/v1/auth/logout", token=new_token, method="POST")
        return {"success": True, "real_email_delivery": True, "password_reset": True,
                "reused_code_rejected": True, "old_password_rejected": True,
                "old_sessions_revoked": True, "new_password_login": True,
                "production_account_password_changed": False,
                "scope": "installed_NAS_code_with_isolated_temporary_database"}
    finally:
        server.shutdown()
        server.server_close()
        worker.join(timeout=5)
        # The delivery task finishes before removing its isolated account database.
        service.recovery._queue.join()
        server_module.SERVICE = None


def verify_inside_container():
    from avora_nas.config import Settings
    with tempfile.TemporaryDirectory(prefix="avora-recovery-check-") as temporary:
        # Redirect configuration before Settings.from_environment can open any data.
        os.environ["AVORA_DATA_DIR"] = temporary
        os.environ["AVORA_SETUP_TOKEN"] = secrets.token_urlsafe(36)
        os.environ["AVORA_FIREBASE_CREDENTIALS_FILE"] = ""
        os.environ["AVORA_FIREBASE_DEVICE_ID"] = ""
        settings = Settings.from_environment()
        username = settings.smtp_username.strip().lower()
        if settings.smtp_host != "smtp.gmail.com" or not username.endswith("@gmail.com"):
            raise RuntimeError("Expected configured Gmail sender")
        local, domain = username.split("@")
        recipient = local.split("+")[0] + "+avora-check-" + secrets.token_hex(8) + "@" + domain
        result = run_contract(settings, recipient, inbox_code)
    result["temporary_data_removed"] = not Path(temporary).exists()
    print(json.dumps(result), flush=True)


def main():
    try:
        if "--inside-container" in sys.argv:
            verify_inside_container()
            return 0
        step("verify_running_api")
        command = ["docker", "inspect", "--format", "{{.Id}} {{.State.Running}} {{.State.Health.Status}}", "avora-nas-api"]
        before = subprocess.check_output(command, stderr=subprocess.DEVNULL, text=True).strip()
        if not before.endswith(" true healthy"):
            raise RuntimeError("API must be healthy")
        run = subprocess.run(["docker", "exec", "-i", "avora-nas-api", "python", "-", "--inside-container"],
                             input=Path(__file__).read_text(encoding="utf-8"), text=True,
                             stderr=subprocess.DEVNULL)
        if run.returncode:
            return run.returncode
        after = subprocess.check_output(command, stderr=subprocess.DEVNULL, text=True).strip()
        if after != before:
            raise RuntimeError("API identity or health changed")
        print("DIAG: Existing API identity and health unchanged.", flush=True)
        return 0
    except (Exception, KeyboardInterrupt) as error:
        # Never include exception text, addresses, passwords, tokens or message bodies.
        print(json.dumps({"success": False, "phase": PHASE, "error_type": type(error).__name__}), flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
