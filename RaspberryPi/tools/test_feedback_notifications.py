"""Feedback push retry, deduplication and owner targeting regression checks."""
from unittest.mock import patch
from test_access_request_notifications import FakeReference, service_for, firebase_module, test_owner_only_delivery

class Reference(FakeReference):
    def child(self, name):
        return Reference(self.values, self.path + tuple(name.split("/")))

    def transaction(self, callback):
        result = callback(self.get())
        parent = Reference(self.values, self.path[:-1])._node()
        parent[self.path[-1]] = result
        return result

    def order_by_child(self, name):
        self.order = name
        return self

    def start_at(self, value):
        self.start = value
        return self

    def get(self):
        values = super().get()
        if hasattr(self, "start") and isinstance(values, dict):
            return {key: value for key, value in values.items()
                    if isinstance(value, dict) and value.get(self.order, 0) >= self.start}
        return values


def main():
    now = 1800000000
    values = {}
    feedback = {
        "fresh": {"created_at": now * 1000, "status": "new",
                  "email_delivery": {"status": "sent"}},
        "read": {"created_at": now * 1000, "status": "read"},
        "old": {"created_at": (now - 90000) * 1000, "status": "new"},
    }
    service = service_for(values)
    service._device_ref = lambda: Reference(values)
    calls = []
    service._send_push_notification = lambda **kwargs: calls.append(kwargs) or False
    with patch.object(firebase_module.time, "time", return_value=now), patch.object(
            firebase_module.db, "reference", side_effect=lambda path:
            Reference(feedback) if path.endswith("user_feedback")
            else Reference(values).child("push_state/activation_epoch")):
        service._process_feedback_notifications()
        assert len(calls) == 1
        assert calls[0] == {"event_code": "FEEDBACK_RECEIVED",
                            "event_id": "feedback:fresh", "zone_id": "", "owner_only": True}
        assert feedback["fresh"]["push_delivery"]["status"] == "not_delivered"
        service._process_feedback_notifications()
        assert len(calls) == 1, "polling must be throttled"
        service._last_feedback_push_scan = 0
        service._send_push_notification = lambda **kwargs: calls.append(kwargs) or True
        service._process_feedback_notifications()
        assert len(calls) == 2, "failed delivery must retry even when email is sent"
        assert feedback["fresh"]["push_delivery"]["status"] == "sent"
        service._last_feedback_push_scan = 0
        service._process_feedback_notifications()
        assert len(calls) == 2, "sent feedback must not notify again"
        assert "push_delivery" not in feedback["old"]
        assert "push_delivery" not in feedback["read"]
        # A restart uses persisted delivery and activation state.
        del service._last_feedback_push_scan
        service._process_feedback_notifications()
        assert len(calls) == 2
    test_owner_only_delivery()
    print("[PASS] Feedback push owner targeting, retry, deduplication and restart recovery.")

if __name__ == "__main__":
    main()
