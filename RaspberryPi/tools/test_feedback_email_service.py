"""Regression checks for Raspberry Pi feedback email delivery."""

from __future__ import annotations

import sys
import time
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from services.feedback_email_service import (  # noqa: E402
    FeedbackEmailService,
    FeedbackEmailSettings,
    GmailInboxTransport,
    GmailSmtpTransport,
    build_feedback_email,
    create_feedback_email_transport,
)
from tools.configure_feedback_email import (  # noqa: E402
    normalize_app_password,
    render_environment,
)


class FakeRepository:
    def __init__(self, activation_epoch: int, feedback: dict[str, dict]) -> None:
        self.activation_epoch = activation_epoch
        self.feedback = feedback
        self.scan_modes: list[bool] = []

    def get_user_feedback(self, *, full_scan: bool = False) -> dict[str, dict]:
        self.scan_modes.append(full_scan)
        if full_scan:
            return self.feedback
        recent = sorted(self.feedback.items(), key=lambda item: item[1]["created_at"])[-50:]
        return dict(recent)

    def get_or_create_feedback_email_activation_epoch(
        self,
        now_epoch: int,
    ) -> int:
        del now_epoch
        return self.activation_epoch

    def claim_user_feedback_email(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        now_epoch: int,
        lease_seconds: int,
    ) -> dict | None:
        del lease_seconds
        item = self.feedback[feedback_id]
        delivery = item.setdefault("email_delivery", {})
        if delivery.get("status") in {"sending", "sent"}:
            return None
        attempt_count = int(delivery.get("attempt_count", 0)) + 1
        delivery.update(
            {
                "status": "sending",
                "lease_token": lease_token,
                "claimed_at_epoch": now_epoch,
                "attempt_count": attempt_count,
            },
        )
        return dict(delivery)

    def mark_user_feedback_email_sent(
        self,
        feedback_id: str,
        *,
        lease_token: str,
        message_id: str,
        now_epoch: int,
    ) -> None:
        delivery = self.feedback[feedback_id]["email_delivery"]
        assert delivery["lease_token"] == lease_token
        delivery.update(
            {
                "status": "sent",
                "message_id": message_id,
                "sent_at_epoch": now_epoch,
            },
        )

    def mark_user_feedback_email_failed(self, *args, **kwargs) -> None:
        feedback_id = args[0]
        delivery = self.feedback[feedback_id]["email_delivery"]
        assert delivery["lease_token"] == kwargs["lease_token"]
        delivery.update(
            {
                "status": "failed",
                "last_error": kwargs["error_message"],
                "failed_at_epoch": kwargs["now_epoch"],
                "next_attempt_at_epoch": kwargs["next_attempt_at_epoch"],
            },
        )


class FakeTransport:
    def __init__(self) -> None:
        self.messages = []

    def send(self, message) -> None:
        self.messages.append(message)


class FailingTransport:
    def __init__(self, error_message: str) -> None:
        self.error_message = error_message

    def send(self, message) -> None:
        del message
        raise RuntimeError(self.error_message)


class FakeImapClient:
    def __init__(self) -> None:
        self.login_values = None
        self.append_values = None
        self.logged_out = False

    def login(self, username: str, password: str) -> None:
        self.login_values = (username, password)

    def append(self, mailbox, flags, date_time, message):
        self.append_values = (mailbox, flags, date_time, message)
        return "OK", [b"APPENDUID 1 42"]

    def logout(self) -> None:
        self.logged_out = True


class FakeImapFactory:
    def __init__(self) -> None:
        self.client = FakeImapClient()
        self.connection_values = None

    def __call__(self, host, port, **kwargs):
        self.connection_values = (host, port, kwargs)
        return self.client


def main() -> None:
    now_epoch = int(time.time())
    settings = FeedbackEmailSettings(
        enabled=True,
        sender="alidogukan@gmail.com",
        recipient="alidogukan@gmail.com",
        app_password="abcdefghijklmnop",
    )
    feedback = {
        "old-test": {
            "created_at": (now_epoch - 60) * 1000,
            "type": "problem",
            "subject": "Eski deneme",
            "description": "Gönderilmemeli",
        },
        "new-feedback": {
            "created_at": (now_epoch + 1) * 1000,
            "type": "suggestion",
            "area_label": "Sulama ve donanım",
            "subject": "Pompa ekranı",
            "description": "Daha anlaşılır bir durum açıklaması eklenebilir.",
            "contact_email": "user@example.com",
            "diagnostics": {
                "app_version": "2.10.0",
                "api_key": "must-not-leak",
            },
        },
    }
    repository = FakeRepository(now_epoch, feedback)
    transport = FakeTransport()
    service = FeedbackEmailService(
        repository,
        settings=settings,
        transport=transport,
    )

    assert service.process_once() == 1
    assert service.process_once() == 0
    assert repository.scan_modes == [True, False]
    assert len(transport.messages) == 1
    message = transport.messages[0]
    assert message["To"] == "alidogukan@gmail.com"
    assert message["Reply-To"] == "user@example.com"
    assert "Öneri" in str(message["Subject"])
    body = message.get_content()
    assert "Sulama ve donanım" in body
    assert "app_version" in body
    assert "must-not-leak" not in body
    assert feedback["new-feedback"]["email_delivery"]["status"] == "sent"
    assert "email_delivery" not in feedback["old-test"]

    backlog = {
        f"feedback-{index:03d}": {
            "created_at": (now_epoch + index + 1) * 1000,
            "type": "problem",
            "subject": f"Sorun {index}",
            "description": "Uzun kesinti sonrasında ulaşması gereken mesaj.",
        }
        for index in range(52)
    }
    backlog_repository = FakeRepository(now_epoch, backlog)
    backlog_transport = FakeTransport()
    backlog_service = FeedbackEmailService(
        backlog_repository, settings=settings, transport=backlog_transport,
    )
    assert backlog_service.process_once() == 52
    assert backlog_repository.scan_modes == [True]
    assert len(backlog_transport.messages) == 52

    unsafe = build_feedback_email(
        "line\nbreak",
        {
            "created_at": now_epoch * 1000,
            "type": "problem\nBcc: attacker@example.com",
            "subject": "Başlık\nBcc: attacker@example.com",
            "description": "Açıklama",
            "contact_email": "victim@example.com\nBcc: attacker@example.com",
        },
        settings,
    )
    assert "\n" not in str(unsafe["Subject"])
    assert unsafe["Reply-To"] is None

    assert settings.resolved_delivery_mode() == "gmail_inbox"
    assert isinstance(
        create_feedback_email_transport(settings),
        GmailInboxTransport,
    )
    external_sender_settings = FeedbackEmailSettings(
        enabled=True,
        sender="avora.sender@gmail.com",
        recipient="alidogukan@gmail.com",
        app_password="abcdefghijklmnop",
    )
    assert external_sender_settings.resolved_delivery_mode() == "smtp"
    assert isinstance(
        create_feedback_email_transport(external_sender_settings),
        GmailSmtpTransport,
    )

    imap_factory = FakeImapFactory()
    inbox_transport = GmailInboxTransport(
        settings,
        client_factory=imap_factory,
    )
    inbox_transport.send(message)
    assert imap_factory.connection_values[0:2] == ("imap.gmail.com", 993)
    assert imap_factory.client.login_values == (
        "alidogukan@gmail.com",
        "abcdefghijklmnop",
    )
    assert imap_factory.client.append_values[0] == "INBOX"
    assert b"X-AVORA-Feedback-ID" in (
        imap_factory.client.append_values[3]
    )
    assert imap_factory.client.logged_out

    failed_feedback = {
        "smtp-failure": {
            "created_at": (now_epoch + 2) * 1000,
            "type": "problem",
            "subject": "SMTP denemesi",
            "description": "Geçici hata yeniden denenmeli.",
        },
    }
    failed_repository = FakeRepository(now_epoch, failed_feedback)
    failed_service = FeedbackEmailService(
        failed_repository,
        settings=settings,
        transport=FailingTransport(
            f"authentication failed: {settings.app_password}",
        ),
    )
    assert failed_service.process_once() == 0
    failed_delivery = failed_feedback["smtp-failure"]["email_delivery"]
    assert failed_delivery["status"] == "failed"
    assert settings.app_password not in failed_delivery["last_error"]
    assert "<redacted>" in failed_delivery["last_error"]
    assert failed_delivery["next_attempt_at_epoch"] > now_epoch

    assert normalize_app_password("abcd efgh ijkl mnop") == "abcdefghijklmnop"
    environment = render_environment(
        "alidogukan@gmail.com",
        "alidogukan@gmail.com",
        "abcdefghijklmnop",
    )
    assert "AVORA_FEEDBACK_EMAIL_ENABLED=true" in environment
    assert (
        "AVORA_FEEDBACK_EMAIL_TO=alidogukan@gmail.com"
        in environment
    )
    assert "AVORA_FEEDBACK_EMAIL_DELIVERY_MODE=auto" in environment
    assert "AVORA_GMAIL_APP_PASSWORD=abcdefghijklmnop" in environment
    assert "AVORA_FEEDBACK_EMAIL_SEND_EXISTING=false" in environment

    print("[PASS] Raspberry Pi feedback email delivery tests.")


if __name__ == "__main__":
    main()
