"""Live read-only garden verification, executed inside the NAS container by deployment.

PUBLIC is injected by the deployment script and contains Android's public Firebase
configuration. Tokens and account details never appear in output. Temporary NAS
sessions are always removed. Two independent Firebase sign-ins must share one UID.
"""
import base64
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

from avora_nas.config import Settings
from avora_nas.database import User
from avora_nas.service import AvoraService


def post(url, body, bearer=None):
    headers = {"Content-Type": "application/json"}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    request = urllib.request.Request(url, data=json.dumps(body).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response)


PHASE = "initialization"


def verify():
    global PHASE
    PHASE = "initialization"
    return _verify()


def _verify():
    global PHASE
    service = AvoraService(Settings.from_environment())
    PHASE = "administrator_lookup"
    with service.accounts._connect() as connection:
        rows = connection.execute("SELECT id,email,display_name,role FROM users WHERE role='admin' AND active=1").fetchall()
    if len(rows) != 1:
        raise ValueError("Expected exactly one active administrator")
    owner = User(**dict(rows[0]))
    identities = []
    counts = []
    for attempt in range(2):
        PHASE = "temporary_nas_session"
        session = service.accounts.create_session_for_user(owner, 300)
        try:
            PHASE = "nas_owner_endpoint"
            result = post("http://127.0.0.1:8787/v1/auth/firebase-owner-session", {}, session.token)
            PHASE = "firebase_sign_in"
            auth = post("https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key="
                        + urllib.parse.quote(PUBLIC["api_key"]),
                        {"token": result["custom_token"], "returnSecureToken": True})
            PHASE = "owner_claim"
            token = auth["idToken"]
            payload = token.split(".")[1]
            claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
            # signInWithCustomToken returns idToken/refreshToken, not localId.
            # The ID token subject is the authenticated UID. The database read
            # below independently validates this token and its owner permission.
            uid = claims.get("sub")
            if claims.get("avora_device_id") != "avora-001" or uid != result["firebase_uid"]:
                raise ValueError("Owner claim mismatch")
            PHASE = "garden_read"
            with urllib.request.urlopen(PUBLIC["database_url"].rstrip("/")
                                        + "/devices/avora-001/zones.json?auth=" + urllib.parse.quote(token), timeout=20) as response:
                zones = json.load(response)
            if not isinstance(zones, dict) or not zones:
                raise ValueError("Expected existing garden zones")
            identities.append(uid)
            counts.append(len(zones))
        finally:
            service.logout(session.token)
    PHASE = "stable_identity"
    if len(set(identities)) != 1:
        raise ValueError("Identity changed between sign-ins")
    return {"success": True, "independent_sign_ins": 2, "stable_owner_identity": True,
            "owner_claim_verified": True, "garden_read_verified": True, "zone_count": counts[-1]}


def failure_report(error):
    # Never serialize exception text: HTTP URLs can contain ID tokens and API keys.
    report = {"success": False, "phase": PHASE, "error_type": type(error).__name__}
    if isinstance(error, urllib.error.HTTPError):
        report["http_status"] = error.code
        try:
            body = json.loads(error.read(4096))
            code = body.get("error")
            if isinstance(code, dict):
                code = code.get("message")
            if code in {"firebase_identity_unavailable", "unauthorized", "forbidden",
                        "INVALID_CUSTOM_TOKEN", "CREDENTIAL_MISMATCH", "API_KEY_INVALID",
                        "TOKEN_EXPIRED", "INVALID_ID_TOKEN", "USER_DISABLED", "Permission denied"}:
                report["error_code"] = code
        except Exception:
            pass
        finally:
            error.close()
    return report


if __name__ == "__main__":
    try:
        print(json.dumps(verify()))
    except Exception as error:
        print(json.dumps(failure_report(error)))
        sys.exit(1)
