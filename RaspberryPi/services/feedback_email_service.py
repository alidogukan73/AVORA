"""Reliable Raspberry Pi delivery of Android feedback to Gmail."""

from __future__ import annotations

import imaplib
import json
import os
import re
import smtplib
import ssl
import threading
import time
import uuid
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
from datetime import timezone
from email.message import EmailMessage
from email.policy import SMTP
from email.utils import formatdate
from email.utils import parseaddr
from typing import Protocol

from core.logger import AppLogger


_EMAIL_PATTERN = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")
_SENSITIVE_DIAGNOSTIC_PARTS = (
    "api_key",
    "password",
    "secret",
    "token",
)


def _environment_flag(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def _environment_int(name: str, default: int, minimum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return max(minimum, value)


def _single_line(value, limit: int) -> str:
    text = " ".join(str(value or "").replace("\x00", "").split())
    return text[:limit]


def _multi_line(value, limit: int) -> str:
    text = str(value or "").replace("\x00", "")
    text = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    return text[:limit]


def _valid_email(value) -> str:
    text = _single_line(value, 254)
    if not text or "\n" in text or "\r" in text:
        return ""
    _, address = parseaddr(text)
    if address != text or not _EMAIL_PATTERN.fullmatch(address):
        return ""
    return address


def _mailbox_identity(value) -> str:
    address = _valid_email(value).lower()
    if not address:
        return ""

    local_part, domain = address.rsplit("@", 1)
    if domain in {"gmail.com", "googlemail.com"}:
        local_part = local_part.split("+", 1)[0].replace(".", "")
        domain = "gmail.com"
    return f"{local_part}@{domain}"


def _created_epoch(feedback: dict) -> int:
    try:
        created_at = float(feedback.get("created_at", 0))
    except (TypeError, ValueError):
        return 0
    if created_at > 10_000_000_000:
        created_at /= 1000
    return max(0, int(created_at))


def _created_label(feedback: dict) -> str:
    created_at = _created_epoch(feedback)
    if created_at <= 0:
        return "Bilinmiyor"
    return datetime.fromtimestamp(
        created_at,
        tz=timezone.utc,
    ).astimezone().strftime("%d.%m.%Y %H:%M:%S %Z")


def _type_label(value) -> str:
    normalized = _single_line(value, 40).lower()
    return {
        "problem": "Sorun",
        "suggestion": "Öneri",
        "question": "Soru",
    }.get(normalized, normalized.capitalize() or "Belirtilmedi")


def _diagnostic_lines(value) -> list[str]:
    if not isinstance(value, dict):
        return []

    lines: list[str] = []
    for key in sorted(value, key=str):
        safe_key = _single_line(key, 80)
        normalized_key = safe_key.lower()
        if any(part in normalized_key for part in _SENSITIVE_DIAGNOSTIC_PARTS):
            continue

        item = value[key]
        if isinstance(item, (dict, list, tuple)):
            rendered = json.dumps(
                item,
                ensure_ascii=False,
                sort_keys=True,
            )
        else:
            rendered = str(item)
        lines.append(f"- {safe_key}: {_single_line(rendered, 500)}")
        if len(lines) >= 20:
            break
    return lines


@dataclass(frozen=True)
class FeedbackEmailSettings:
    """Environment-backed Gmail settings; the password never enters Git."""

    enabled: bool
    sender: str
    recipient: str
    app_password: str
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 465
    imap_host: str = "imap.gmail.com"
    imap_port: int = 993
    delivery_mode: str = "auto"
    poll_interval_seconds: int = 60
    retry_initial_seconds: int = 60
    retry_max_seconds: int = 3600
    lease_seconds: int = 300
    send_existing: bool = False

    @classmethod
    def from_environment(cls) -> "FeedbackEmailSettings":
        return cls(
            enabled=_environment_flag(
                "AVORA_FEEDBACK_EMAIL_ENABLED",
            ),
            sender=os.getenv(
                "AVORA_FEEDBACK_EMAIL_FROM",
                "alidogukan@gmail.com",
            ).strip(),
            recipient=os.getenv(
                "AVORA_FEEDBACK_EMAIL_TO",
                "alidogukan+avora@gmail.com",
            ).strip(),
            app_password="".join(
                os.getenv(
                    "AVORA_GMAIL_APP_PASSWORD",
                    "",
                ).split(),
            ),
            smtp_host=os.getenv(
                "AVORA_FEEDBACK_SMTP_HOST",
                "smtp.gmail.com",
            ).strip(),
            smtp_port=_environment_int(
                "AVORA_FEEDBACK_SMTP_PORT",
                465,
                1,
            ),
            imap_host=os.getenv(
                "AVORA_FEEDBACK_IMAP_HOST",
                "imap.gmail.com",
            ).strip(),
            imap_port=_environment_int(
                "AVORA_FEEDBACK_IMAP_PORT",
                993,
                1,
            ),
            delivery_mode=os.getenv(
                "AVORA_FEEDBACK_EMAIL_DELIVERY_MODE",
                "auto",
            ).strip().lower(),
            poll_interval_seconds=_environment_int(
                "AVORA_FEEDBACK_EMAIL_POLL_SECONDS",
                60,
                60,
            ),
            retry_initial_seconds=_environment_int(
                "AVORA_FEEDBACK_EMAIL_RETRY_SECONDS",
                60,
                10,
            ),
            retry_max_seconds=_environment_int(
                "AVORA_FEEDBACK_EMAIL_RETRY_MAX_SECONDS",
                3600,
                60,
            ),
            lease_seconds=_environment_int(
                "AVORA_FEEDBACK_EMAIL_LEASE_SECONDS",
                300,
                60,
            ),
            send_existing=_environment_flag(
                "AVORA_FEEDBACK_EMAIL_SEND_EXISTING",
            ),
        )

    def validate(self) -> None:
        if not self.enabled:
            return
        if not _valid_email(self.sender):
            raise ValueError("Gmail sender address is invalid.")
        if not _valid_email(self.recipient):
            raise ValueError("Feedback recipient address is invalid.")
        delivery_mode = self.resolved_delivery_mode()
        if delivery_mode == "smtp" and not self.smtp_host:
            raise ValueError("SMTP host is required.")
        if delivery_mode == "gmail_inbox" and not self.imap_host:
            raise ValueError("IMAP host is required.")
        if self.delivery_mode not in {"auto", "gmail_inbox", "smtp"}:
            raise ValueError("Feedback email delivery mode is invalid.")
        if len(self.app_password) != 16:
            raise ValueError("Gmail app password must contain 16 characters.")

    def resolved_delivery_mode(self) -> str:
        if self.delivery_mode != "auto":
            return self.delivery_mode
        if _mailbox_identity(self.sender) == _mailbox_identity(self.recipient):
            return "gmail_inbox"
        return "smtp"


class FeedbackEmailRepository(Protocol):
    def get_user_feedback(self) -> dict[str, dict]: ...

    def get_or_create_feedback_email_activation_epoch(
        self,
        now_epoch: int,
    ) -> int: ...

    def claim_user_feedback_email(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        now_epoch: int,
        lease_seconds: int,
    ) -> dict | None: ...

    def mark_user_feedback_email_sent(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        message_id: str,
        now_epoch: int,
    ) -> None: ...

    def mark_user_feedback_email_failed(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        error_message: str,
        now_epoch: int,
        next_attempt_at_epoch: int,
    ) -> None: ...


class FeedbackEmailTransport(Protocol):
    def send(self, message: EmailMessage) -> None: ...


def build_feedback_email(
    feedback_id: str,
    feedback: dict,
    settings: FeedbackEmailSettings,
) -> EmailMessage:
    """Build a bounded Turkish plain-text email without unsafe headers."""

    feedback_type = _type_label(feedback.get("type"))
    subject = _single_line(feedback.get("subject"), 160) or "Başlıksız"
    description = _multi_line(feedback.get("description"), 10_000)
    area = _single_line(
        feedback.get("area_label") or feedback.get("area"),
        160,
    ) or "Belirtilmedi"
    contact = _valid_email(feedback.get("contact_email"))
    safe_feedback_id = _single_line(feedback_id, 120) or "unknown"
    safe_device_id = _single_line(feedback.get("device_id"), 120)
    safe_user_id = _single_line(feedback.get("user_id"), 160)

    body_lines = [
        "Yeni AVORA geri bildirimi",
        "",
        f"Tür: {feedback_type}",
        f"İlgili bölüm: {area}",
        f"Konu: {subject}",
        f"Gönderilme zamanı: {_created_label(feedback)}",
        f"İletişim e-postası: {contact or 'Belirtilmedi'}",
        "",
        "Açıklama:",
        description or "Açıklama girilmedi.",
        "",
        f"Geri bildirim kimliği: {safe_feedback_id}",
        f"AVORA cihaz kimliği: {safe_device_id or 'Belirtilmedi'}",
        f"Kullanıcı kimliği: {safe_user_id or 'Belirtilmedi'}",
    ]

    diagnostics = _diagnostic_lines(feedback.get("diagnostics"))
    if diagnostics:
        body_lines.extend(["", "Tanı bilgileri:", *diagnostics])

    message_key = re.sub(
        r"[^A-Za-z0-9._-]",
        "-",
        safe_feedback_id,
    )[:80]
    message = EmailMessage()
    message["From"] = settings.sender
    message["To"] = settings.recipient
    message["Subject"] = (
        f"[AVORA Geri Bildirim] {feedback_type}: {subject}"
    )[:240]
    message["Date"] = formatdate(localtime=False)
    message["Message-ID"] = f"<{message_key}@feedback.avora-alidogukan>"
    message["X-AVORA-Feedback-ID"] = safe_feedback_id
    if contact:
        message["Reply-To"] = contact
    message.set_content("\n".join(body_lines), charset="utf-8")
    return message


class GmailSmtpTransport:
    """Send one already-built message over TLS-protected Gmail SMTP."""

    def __init__(self, settings: FeedbackEmailSettings) -> None:
        self._settings = settings

    def send(self, message: EmailMessage) -> None:
        context = ssl.create_default_context()
        with smtplib.SMTP_SSL(
            self._settings.smtp_host,
            self._settings.smtp_port,
            timeout=10,
            context=context,
        ) as smtp:
            smtp.login(
                self._settings.sender,
                self._settings.app_password,
            )
            refused = smtp.send_message(message)
            if refused:
                raise RuntimeError("SMTP refused one or more recipients.")


class GmailInboxTransport:
    """Append self-addressed AVORA messages directly to Gmail INBOX."""

    def __init__(
        self,
        settings: FeedbackEmailSettings,
        client_factory: Callable[..., imaplib.IMAP4_SSL] = imaplib.IMAP4_SSL,
    ) -> None:
        self._settings = settings
        self._client_factory = client_factory

    def send(self, message: EmailMessage) -> None:
        context = ssl.create_default_context()
        client = self._client_factory(
            self._settings.imap_host,
            self._settings.imap_port,
            ssl_context=context,
            timeout=10,
        )
        try:
            client.login(
                self._settings.sender,
                self._settings.app_password,
            )
            status, response = client.append(
                "INBOX",
                None,
                None,
                message.as_bytes(policy=SMTP),
            )
            if status != "OK":
                detail = _single_line(response, 300)
                raise RuntimeError(
                    f"Gmail IMAP inbox append failed: {detail}",
                )
        finally:
            try:
                client.logout()
            except Exception:
                pass


def create_feedback_email_transport(
    settings: FeedbackEmailSettings,
) -> FeedbackEmailTransport:
    mode = settings.resolved_delivery_mode()
    if mode == "gmail_inbox":
        return GmailInboxTransport(settings)
    return GmailSmtpTransport(settings)


class FeedbackEmailService:
    """Poll Firebase safely and deliver each new feedback record once."""

    def __init__(
        self,
        repository: FeedbackEmailRepository,
        settings: FeedbackEmailSettings | None = None,
        transport: FeedbackEmailTransport | None = None,
    ) -> None:
        self._repository = repository
        self._settings = settings or FeedbackEmailSettings.from_environment()
        self._delivery_mode = (
            "custom"
            if transport is not None
            else self._settings.resolved_delivery_mode()
        )
        self._transport = transport or create_feedback_email_transport(
            self._settings,
        )
        self._logger = AppLogger().logger
        self._stop_event = threading.Event()
        self._thread: threading.Thread | None = None
        self._activation_epoch: int | None = None

    def start(self) -> None:
        if self._thread is not None and self._thread.is_alive():
            return
        if not self._settings.enabled:
            self._logger.info("Feedback email delivery is disabled.")
            return

        try:
            self._settings.validate()
        except ValueError as exc:
            self._logger.error(
                "Feedback email delivery configuration is invalid: %s",
                exc,
            )
            return

        self._stop_event.clear()
        self._thread = threading.Thread(
            target=self._run,
            daemon=True,
            name="FeedbackEmail",
        )
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        if self._thread is None:
            return
        self._thread.join(timeout=12)
        if self._thread.is_alive():
            self._logger.warning(
                "Feedback email thread did not stop gracefully.",
            )
        self._thread = None

    def process_once(self) -> int:
        """Process pending feedback once; exposed for deterministic tests."""

        if not self._settings.enabled:
            return 0
        self._settings.validate()

        now_epoch = int(time.time())
        if self._activation_epoch is None:
            if self._settings.send_existing:
                self._activation_epoch = 0
            else:
                self._activation_epoch = (
                    self._repository
                    .get_or_create_feedback_email_activation_epoch(now_epoch)
                )
            self._logger.info(
                "Feedback email activation loaded. epoch=%d send_existing=%s",
                self._activation_epoch,
                self._settings.send_existing,
            )

        feedback_items = self._repository.get_user_feedback()
        ordered_items = sorted(
            feedback_items.items(),
            key=lambda item: (_created_epoch(item[1]), item[0]),
        )

        delivered = 0
        for feedback_id, feedback in ordered_items:
            if self._stop_event.is_set():
                break
            if not isinstance(feedback, dict):
                continue

            created_at = _created_epoch(feedback)
            if created_at <= 0 or created_at < self._activation_epoch:
                continue

            delivery = feedback.get("email_delivery")
            if isinstance(delivery, dict):
                if str(delivery.get("status", "")).lower() == "sent":
                    continue
                try:
                    next_attempt = int(
                        delivery.get("next_attempt_at_epoch", 0),
                    )
                except (TypeError, ValueError):
                    next_attempt = 0
                if next_attempt > now_epoch:
                    continue

            lease_token = uuid.uuid4().hex
            claimed = self._repository.claim_user_feedback_email(
                feedback_id,
                lease_token=lease_token,
                now_epoch=now_epoch,
                lease_seconds=self._settings.lease_seconds,
            )
            if claimed is None:
                continue

            try:
                message = build_feedback_email(
                    feedback_id,
                    feedback,
                    self._settings,
                )
                self._transport.send(message)
                self._repository.mark_user_feedback_email_sent(
                    feedback_id,
                    lease_token=lease_token,
                    message_id=str(message["Message-ID"]),
                    now_epoch=int(time.time()),
                )
                delivered += 1
                self._logger.info(
                    "Feedback email delivered. feedback_id=%s",
                    feedback_id,
                )
            except Exception as exc:
                attempt_count = max(
                    1,
                    int(claimed.get("attempt_count", 1)),
                )
                retry_seconds = min(
                    self._settings.retry_initial_seconds
                    * (2 ** min(attempt_count - 1, 10)),
                    self._settings.retry_max_seconds,
                )
                safe_error = _single_line(exc, 500).replace(
                    self._settings.app_password,
                    "<redacted>",
                )
                failed_at = int(time.time())
                self._repository.mark_user_feedback_email_failed(
                    feedback_id,
                    lease_token=lease_token,
                    error_message=safe_error,
                    now_epoch=failed_at,
                    next_attempt_at_epoch=failed_at + retry_seconds,
                )
                self._logger.warning(
                    "Feedback email delivery failed; retry scheduled. "
                    "feedback_id=%s retry_seconds=%d error=%s",
                    feedback_id,
                    retry_seconds,
                    safe_error,
                )

        return delivered

    def _run(self) -> None:
        self._logger.info(
            "Feedback email delivery started. mode=%s",
            self._delivery_mode,
        )
        while not self._stop_event.is_set():
            try:
                self.process_once()
            except Exception as exc:
                self._logger.warning(
                    "Feedback email cycle failed; it will be retried. error=%s",
                    _single_line(exc, 500),
                )

            if self._stop_event.wait(
                self._settings.poll_interval_seconds,
            ):
                break
        self._logger.info("Feedback email delivery stopped.")
