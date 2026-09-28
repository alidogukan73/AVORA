"""Bounded asynchronous mail delivery; public responses never disclose account existence."""
from __future__ import annotations

import logging
import queue
import smtplib
import ssl
import threading
import time
from email.message import EmailMessage

from .config import Settings
from .database import AccountDatabase, LoginRateLimitError
from .security import normalize_email


class RecoveryUnavailableError(RuntimeError):
    pass


def send_recovery_email(settings: Settings, email: str, token: str) -> None:
    message = EmailMessage()
    message["From"] = settings.smtp_from
    message["To"] = email
    message["Subject"] = "AVORA - Şifre sıfırlama kodu"
    message.set_content(
        "AVORA NAS hesabınızın şifresini sıfırlamak için uygulamadaki "
        "Şifremi unuttum ekranına bu kodu yapıştırın:\n\n"
        + token + "\n\nKod 15 dakika geçerlidir ve yalnızca bir kez kullanılabilir. "
        "Bu isteği siz yapmadıysanız e-postayı yok sayabilirsiniz. "
        "Kodu kimseyle paylaşmayın.\n"
    )
    context = ssl.create_default_context()
    if settings.smtp_security == "ssl":
        client = smtplib.SMTP_SSL(settings.smtp_host, settings.smtp_port,
                                  timeout=10, context=context)
    else:
        client = smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=10)
    with client:
        if settings.smtp_security == "starttls":
            client.starttls(context=context)
        if settings.smtp_username:
            client.login(settings.smtp_username, settings.smtp_password)
        client.send_message(message)


class PasswordRecovery:
    def __init__(self, settings: Settings, accounts: AccountDatabase) -> None:
        self.settings = settings
        self.accounts = accounts
        self._queue: queue.Queue = queue.Queue(maxsize=32)
        self._lock = threading.Lock()
        self._worker: threading.Thread | None = None

    def _limit(self, purpose: str, email: str, source: str, now: int) -> None:
        # Serialize check/increment within the single NAS process. Buckets survive restarts.
        buckets = ((f"recovery-{purpose}-account:{email}", 3 if purpose == "mail" else 10),
                   (f"recovery-{purpose}-source:{source}", 30))
        retry = self.accounts.login_retry_after(tuple(key for key, _ in buckets), now)
        if retry:
            raise LoginRateLimitError(retry)
        for key, limit in buckets:
            self.accounts.record_login_failure(key, limit, 900, 900, now)

    def request(self, email: str, source: str, now: int | None = None) -> None:
        if not self.settings.smtp_host:
            raise RecoveryUnavailableError("Password recovery is not configured.")
        email = normalize_email(email)
        timestamp = int(time.time()) if now is None else now
        with self._lock:
            self._limit("mail", email, source, timestamp)
            if self._worker is None or not self._worker.is_alive():
                self._worker = threading.Thread(target=self._deliver, daemon=True,
                                                name="avora-recovery-mail")
                self._worker.start()
            try:
                self._queue.put_nowait(email)
            except queue.Full:
                raise RecoveryUnavailableError("Password recovery is busy.") from None

    def confirm(self, email: str, token: str, password: str, source: str,
                now: int | None = None) -> None:
        email = normalize_email(email)
        timestamp = int(time.time()) if now is None else now
        with self._lock:
            self._limit("confirm", email, source, timestamp)
        self.accounts.reset_password(email, token, password, timestamp)

    def _deliver(self) -> None:
        while True:
            # Retire idle workers, with the same lock as request to avoid stranding jobs.
            try:
                email = self._queue.get(timeout=30)
            except queue.Empty:
                with self._lock:
                    if self._queue.empty():
                        self._worker = None
                        return
                continue
            token = None
            try:
                token = self.accounts.create_password_reset(email, int(time.time()))
                if token:
                    send_recovery_email(self.settings, email, token)
            except Exception:
                # SMTP responses can contain credentials/addresses. Never log the exception.
                logging.getLogger("avora_nas").error("Password recovery delivery failed; check SMTP configuration.")
                if token:
                    try:
                        self.accounts.discard_password_reset(token)
                    except Exception:
                        logging.getLogger("avora_nas").error("Could not discard failed recovery delivery.")
            finally:
                self._queue.task_done()
