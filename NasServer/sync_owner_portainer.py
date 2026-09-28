"""Synchronize the verified AVORA deployment into its existing Portainer source.

Run with sudo on this NAS. Does not recreate or restart any container.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path("/share/Docker/AVORA")
STAGE = Path(__file__).resolve().parent
TARGET = Path("/share/Docker/PortainerCE/data/compose/1/docker-compose.yml")


def compose_config(path):
    result = subprocess.run(["docker", "compose", "-f", str(path), "config", "--format", "json"],
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError("Compose validation failed; sensitive output withheld")
    return json.loads(result.stdout)


def main():
    if os.geteuid() != 0:
        raise RuntimeError("Run with sudo on the NAS")
    os.umask(0o077)
    report_path = STAGE / "result.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if report.get("success") is not True or report.get("verification", {}).get("garden_read_verified") is not True:
        raise RuntimeError("A verified successful deployment is required")
    backup = Path(report["backup"]).resolve()
    if backup.parent != (ROOT / "backups").resolve() or not backup.is_dir():
        raise RuntimeError("Unexpected backup location")
    previous = compose_config(TARGET)
    current = compose_config(ROOT / "stack.yml")
    expected = {"avora-api": "avora-nas-api", "avora-tunnel": "avora-tailscale"}
    for config in (previous, current):
        if set(config["services"]) != set(expected):
            raise RuntimeError("Target is not the expected AVORA stack")
        for service, name in expected.items():
            if config["services"][service].get("container_name") != name:
                raise RuntimeError("Unexpected container identity")
    if current["services"]["avora-api"]["image"] != "avora-nas-api:firebase-owner-20260927":
        raise RuntimeError("Expected verified owner image")
    saved = backup / "portainer-stack-before-sync.yml"
    if not saved.exists():
        shutil.copy2(TARGET, saved)
        saved.chmod(0o600)
    temporary = TARGET.with_name("docker-compose.owner-update.tmp")
    with temporary.open("x", encoding="utf-8") as stream:
        json.dump(current, stream, indent=2)
        stream.write("\n")
    temporary.chmod(0o600)
    try:
        compose_config(temporary)
        os.replace(temporary, TARGET)
    finally:
        temporary.unlink(missing_ok=True)
    report["portainer_source_updated"] = True
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    report_path.chmod(0o644)
    print("Portainer AVORA source synchronized; previous source backed up; no containers restarted.")


if __name__ == "__main__":
    main()
