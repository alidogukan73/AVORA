"""Exchange an authenticated NAS administrator session for a stable Firebase identity."""
from __future__ import annotations

import re
import threading
import uuid

from .config import Settings
from .database import User


class FirebaseIdentityUnavailableError(RuntimeError):
    pass


def owner_uid(user_id: str) -> str:
    return "avora_nas_" + uuid.UUID(user_id).hex


class FirebaseIdentity:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.Lock()
        self._app = None

    def create_owner_session(self, user: User) -> dict:
        # The trusted NAS database role is the authority; never accept role, UID or
        # target garden from the client. Family accounts keep their approval workflow.
        if user.role != "admin":
            raise PermissionError("Administrator access is required.")
        device_id = self.settings.firebase_device_id
        if not self.settings.firebase_credentials_file or not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", device_id):
            raise FirebaseIdentityUnavailableError("Firebase identity is not configured.")
        with self._lock:
            try:
                from firebase_admin import auth, credentials, initialize_app
                if self._app is None:
                    self._app = initialize_app(
                        credentials.Certificate(self.settings.firebase_credentials_file),
                        {"httpTimeout": 10}, name="avora-nas-" + uuid.uuid4().hex,
                    )
                uid = owner_uid(user.id)
                try:
                    account = auth.get_user(uid, app=self._app)
                except auth.UserNotFoundError:
                    account = auth.create_user(uid=uid, app=self._app)
                if account.disabled:
                    raise PermissionError("Firebase account is disabled.")
                claims = dict(account.custom_claims or {})
                if claims.get("avora_device_id") not in (None, device_id):
                    raise PermissionError("The identity belongs to another device.")
                if claims.get("avora_device_id") != device_id:
                    claims["avora_device_id"] = device_id
                    auth.set_custom_user_claims(uid, claims, app=self._app)
                token = auth.create_custom_token(uid, app=self._app)
                return {"firebase_uid": uid, "device_id": device_id,
                        "custom_token": token.decode("utf-8") if isinstance(token, bytes) else token}
            except PermissionError:
                raise
            except Exception:
                # SDK errors can include sensitive service-account or token details.
                raise FirebaseIdentityUnavailableError("Firebase identity is unavailable.") from None
