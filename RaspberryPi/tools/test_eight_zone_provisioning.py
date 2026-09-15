"""Regression checks for the non-destructive eight-zone inventory migration."""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from tools.provision_eight_zone_layout import _provisioned_zone


def main() -> None:
    now = 1_800_000_000
    existing_season = {
        "status": "ACTIVE",
        "active_season_id": "season-cucumber",
    }
    third = _provisioned_zone(
        3,
        {
            "enabled": True,
            "area_id": "area-existing",
            "area_name": "3. Bölge",
            "name": "Salatalık",
            "sensor_id": "soil-006",
            "valve_id": "valve-006",
            "season": existing_season,
            "irrigation_enabled": True,
        },
        now,
    )
    assert third["sensor_id"] == "soil-006"
    assert third["valve_id"] == "valve-003"
    assert third["season"] == existing_season
    assert third["irrigation_enabled"] is False
    assert third["lifecycle_status"] == "HARDWARE_PENDING"
    assert third["irrigation_status"]["hardware_ready"] is False

    new_zone = _provisioned_zone(8, {}, now)
    assert new_zone["zone_id"] == "zone-008"
    assert new_zone["area_name"] == "8. Bölge"
    assert new_zone["sensor_id"] == ""
    assert new_zone["sensor_enabled"] is False
    assert new_zone["valve_id"] == "valve-008"
    assert new_zone["irrigation_enabled"] is False
    assert new_zone["season"]["status"] == "CLOSED"

    print("[PASS] Eight-zone provisioning preserves history and starts safely.")


if __name__ == "__main__":
    main()
