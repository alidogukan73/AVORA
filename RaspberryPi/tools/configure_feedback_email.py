"""Store the Gmail app password outside the repository for systemd."""

from __future__ import annotations

import argparse
import getpass
import os
import re
import tempfile
from email.utils import parseaddr
from pathlib import Path


DEFAULT_SENDER_EMAIL = "alidogukan@gmail.com"
DEFAULT_RECIPIENT_EMAIL = "alidogukan@gmail.com"
DEFAULT_TARGET = Path("/etc/avora/feedback-email.env")
EMAIL_PATTERN = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


def normalize_email(value: str) -> str:
    text = value.strip()
    _, address = parseaddr(text)
    if address != text or not EMAIL_PATTERN.fullmatch(address):
        raise ValueError("Geçerli bir Gmail adresi girin.")
    return address


def normalize_app_password(value: str) -> str:
    password = "".join(value.split())
    if len(password) != 16:
        raise ValueError(
            "Google uygulama şifresi boşluksuz 16 karakter olmalıdır.",
        )
    if "\n" in password or "\r" in password or "=" in password:
        raise ValueError("Google uygulama şifresi geçersiz karakter içeriyor.")
    return password


def render_environment(
    sender_email: str,
    recipient_email: str,
    app_password: str,
) -> str:
    return "\n".join(
        (
            "AVORA_FEEDBACK_EMAIL_ENABLED=true",
            f"AVORA_FEEDBACK_EMAIL_FROM={sender_email}",
            f"AVORA_FEEDBACK_EMAIL_TO={recipient_email}",
            f"AVORA_GMAIL_APP_PASSWORD={app_password}",
            "AVORA_FEEDBACK_EMAIL_DELIVERY_MODE=auto",
            "AVORA_FEEDBACK_EMAIL_SEND_EXISTING=false",
            "",
        ),
    )


def write_private_environment(target: Path, content: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o755)
    file_descriptor, temporary_name = tempfile.mkstemp(
        prefix=".feedback-email-",
        dir=target.parent,
        text=True,
    )
    temporary_path = Path(temporary_name)
    try:
        os.fchmod(file_descriptor, 0o600)
        with os.fdopen(file_descriptor, "w", encoding="utf-8") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary_path, target)
        os.chmod(target, 0o600)
    except Exception:
        temporary_path.unlink(missing_ok=True)
        raise


def main() -> None:
    parser = argparse.ArgumentParser(
        description=(
            "AVORA geri bildirim e-postası için Gmail uygulama şifresini "
            "güvenli systemd ortam dosyasına kaydeder."
        ),
    )
    parser.add_argument(
        "--sender-email",
        default=DEFAULT_SENDER_EMAIL,
        help="Gmail SMTP gönderen adresi.",
    )
    parser.add_argument(
        "--recipient-email",
        default=DEFAULT_RECIPIENT_EMAIL,
        help="Geri bildirimlerin ulaşacağı Gmail adresi veya artı etiketi.",
    )
    parser.add_argument(
        "--target",
        type=Path,
        default=DEFAULT_TARGET,
        help=argparse.SUPPRESS,
    )
    arguments = parser.parse_args()

    if hasattr(os, "geteuid") and os.geteuid() != 0:
        parser.error("Bu araç sudo ile çalıştırılmalıdır.")

    try:
        sender_email = normalize_email(arguments.sender_email)
        recipient_email = normalize_email(arguments.recipient_email)
        app_password = normalize_app_password(
            getpass.getpass("AVORA Gmail uygulama şifresi: "),
        )
    except ValueError as exc:
        parser.error(str(exc))

    write_private_environment(
        arguments.target,
        render_environment(
            sender_email,
            recipient_email,
            app_password,
        ),
    )
    print(f"Gizli yapılandırma kaydedildi: {arguments.target}")
    print("Şifre Git deposuna veya uygulama günlüklerine yazılmadı.")


if __name__ == "__main__":
    main()
