"""Run with sudo on the NAS after uploading a reviewed AVORA release directory.

Preserves running container environment and bind mounts. Backs up source/config/SQLite,
builds before downtime, verifies a real Firebase read, and rolls back service code/image
on failure. Does not restore or delete user data during rollback.
"""
from __future__ import annotations

import fcntl
import ipaddress
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import time
import urllib.request


ROOT = Path("/share/Docker/AVORA")
STAGE = Path(__file__).resolve().parent
API = "avora-nas-api"
TUNNEL = "avora-tailscale"
IMAGE = "avora-nas-api:firebase-owner-20260927"


def run(*args, input_text=None):
    result = subprocess.run(args, input=input_text, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"{args[0]} command failed (exit {result.returncode}); no sensitive output displayed")
    return result.stdout


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    path.chmod(0o600)


def runtime_service(config, inspected):
    result = dict(config)
    result["environment"] = dict(item.split("=", 1) for item in inspected["Config"]["Env"])
    result["image"] = inspected["Config"]["Image"]
    result["command"] = inspected["Config"]["Cmd"]
    result["working_dir"] = inspected["Config"]["WorkingDir"] or "/"
    result["volumes"] = [
        {"type": "bind", "source": mount["Source"], "target": mount["Destination"],
         "read_only": not mount["RW"], "bind": {"create_host_path": False}}
        for mount in inspected["Mounts"] if mount["Type"] == "bind"
    ]
    if any(mount["Type"] not in ("bind", "tmpfs") for mount in inspected["Mounts"]):
        raise RuntimeError("Unexpected mount type; automatic deployment stopped")
    result["labels"] = {key: value for key, value in inspected["Config"].get("Labels", {}).items()
                        if not key.startswith("com.docker.compose.")}
    return result


def published_ports(inspected):
    """Preserve the existing exposure exactly; do not invent or widen a binding."""
    ports = []
    health_urls = []
    for container_port, bindings in (inspected["HostConfig"].get("PortBindings") or {}).items():
        target, protocol = container_port.split("/", 1)
        if protocol not in ("tcp", "udp") or not 1 <= int(target) <= 65535:
            raise RuntimeError("Unexpected published container port")
        for binding in bindings or []:
            host = binding.get("HostIp", "")
            number = int(binding["HostPort"])
            if not 1 <= number <= 65535:
                raise RuntimeError("Unexpected published host port")
            if host:
                ipaddress.ip_address(host)
            port = {"target": int(target), "published": str(number), "protocol": protocol}
            if host:
                port["host_ip"] = host
            ports.append(port)
            if container_port == "8787/tcp":
                probe_host = "127.0.0.1" if host in ("", "0.0.0.0") else "::1" if host == "::" else host
                if ":" in probe_host:
                    probe_host = "[" + probe_host + "]"
                health_urls.append(f"http://{probe_host}:{number}/health")
    if not health_urls:
        raise RuntimeError("The existing API has no published TCP health port")
    return ports, health_urls


def health(urls):
    for _ in range(45):
        for url in urls:
            try:
                with urllib.request.urlopen(url, timeout=3) as response:
                    if json.load(response).get("status") == "ok":
                        return
            except Exception:
                pass
        time.sleep(1)
    raise RuntimeError("NAS health verification failed")


def verify_container(container):
    probe = (STAGE / "verify_owner_session.py").read_text(encoding="utf-8")
    public = json.loads((STAGE / "firebase-public.json").read_text(encoding="utf-8"))
    result = subprocess.run(["docker", "exec", "-i", container, "python", "-"],
                            input="PUBLIC = " + repr(public) + "\n" + probe,
                            text=True, capture_output=True)
    try:
        report = json.loads(result.stdout)
    except (ValueError, TypeError):
        report = {"success": False, "phase": "verification_process", "exit_code": result.returncode}
    if result.returncode or report.get("success") is not True:
        # Only our verifier's structured, redacted report is displayed.
        raise RuntimeError("Owner verification failed: " + json.dumps(report))
    return report


def validate_candidate(backup, environment, api):
    """Test staged code against an isolated account DB before touching the service."""
    data = backup / "validation-data"
    (data / "database").mkdir(parents=True, mode=0o700)
    shutil.copy2(backup / "accounts.sqlite3", data / "database/accounts.sqlite3")
    env_file = backup / "validation.env"
    candidate_env = dict(environment)
    candidate_env.update({"AVORA_DATA_DIR": "/data", "AVORA_HOST": "127.0.0.1", "AVORA_PORT": "8787"})
    if any("\n" in value or "\r" in value for value in candidate_env.values()):
        raise RuntimeError("Multiline container settings require manual review")
    env_file.write_text("\n".join(key + "=" + value for key, value in candidate_env.items()) + "\n", encoding="utf-8")
    env_file.chmod(0o600)
    networks = list(api["NetworkSettings"]["Networks"])
    if not networks or networks[0] in ("host", "none"):
        raise RuntimeError("Expected an isolated Docker network for candidate validation")
    cid = run("docker", "create", "--network", networks[0], "--read-only", "--tmpfs", "/tmp",
              "--env-file", str(env_file), "-v", str(STAGE / "app") + ":/app:ro",
              "-v", str(data) + ":/data", "-v", str(ROOT / "config") + ":/data/config:ro",
              IMAGE, "python", "-m", "avora_nas.server").strip()
    try:
        run("docker", "start", cid)
        check = ("import json,time,urllib.request\n"
                 "for attempt in range(30):\n"
                 " try:\n"
                 "  assert json.load(urllib.request.urlopen('http://127.0.0.1:8787/health',timeout=2))['status']=='ok'\n"
                 "  break\n"
                 " except Exception: time.sleep(1)\n"
                 "else: raise SystemExit(1)\n")
        run("docker", "exec", "-i", cid, "python", "-", input_text=check)
        return verify_container(cid)
    finally:
        # Exact ID returned by our own create; never remove any existing container.
        run("docker", "rm", "-f", cid)


def main():
    if os.geteuid() != 0:
        raise SystemExit("Run this prepared script with sudo on the NAS.")
    os.umask(0o077)
    lock = (ROOT / "config/owner-update.lock").open("a")
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    credential = ROOT / "config/firebase-service-account.json"
    if not credential.is_file() or credential.stat().st_mode & 0o077:
        raise RuntimeError("Owner-only Firebase credential file is required")
    api, tunnel = json.loads(run("docker", "inspect", API, TUNNEL))
    project = api["Config"].get("Labels", {}).get("com.docker.compose.project")
    if not project or tunnel["Config"].get("Labels", {}).get("com.docker.compose.project") != project:
        raise RuntimeError("API and tunnel must belong to the same existing Compose project")
    if api["Config"]["Labels"].get("com.docker.compose.service") != "avora-api":
        raise RuntimeError("Unexpected API service")
    mounts = {item["Destination"]: Path(item["Source"]).resolve() for item in api["Mounts"]}
    for target, folder in (("/app", "app"), ("/data/database", "database"), ("/data/config", "config")):
        if mounts.get(target) != (ROOT / folder).resolve():
            raise RuntimeError("Unexpected data mount; deployment stopped")
    ports, health_urls = published_ports(api)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    backup = ROOT / "backups" / ("owner-update-" + stamp)
    backup.mkdir(mode=0o700)
    source = ROOT / "app/avora_nas"
    shutil.copytree(source, backup / "avora_nas")
    shutil.copy2(ROOT / "stack.yml", backup / "stack.yml")
    with sqlite3.connect(f"file:{ROOT / 'database/accounts.sqlite3'}?mode=ro", uri=True) as live:
        with sqlite3.connect(backup / "accounts.sqlite3") as saved:
            live.backup(saved)
    old = json.loads(run("docker", "compose", "-f", str(ROOT / "stack.yml"), "config", "--format", "json"))
    if set(old["services"]) != {"avora-api", "avora-tunnel"}:
        raise RuntimeError("Unexpected services; deployment stopped without changing containers")
    old["name"] = project
    old["services"]["avora-api"] = runtime_service(old["services"]["avora-api"], api)
    old["services"]["avora-tunnel"] = runtime_service(old["services"]["avora-tunnel"], tunnel)
    old["services"]["avora-api"]["ports"] = ports
    rollback_image = "avora-nas-api:before-owner-" + stamp
    run("docker", "tag", api["Image"], rollback_image)
    old["services"]["avora-api"]["image"] = rollback_image
    rollback = backup / "rollback.json"
    write_json(rollback, old)
    updated = json.loads(json.dumps(old))
    updated["services"]["avora-api"]["image"] = IMAGE
    updated["services"]["avora-api"]["environment"].update({
        "AVORA_FIREBASE_DEVICE_ID": "avora-001",
        "AVORA_FIREBASE_CREDENTIALS_FILE": "/data/config/firebase-service-account.json",
    })
    compose = backup / "updated.json"
    write_json(compose, updated)
    print("Source, SQLite and configuration backed up. Building image while current service runs.", flush=True)
    run("docker", "build", "-t", IMAGE, str(STAGE))
    run("docker", "run", "--rm", "--network", "none", "-v", str(credential) + ":/run/firebase.json:ro",
        IMAGE, "python", "-c", "from firebase_admin import credentials; credentials.Certificate('/run/firebase.json')")
    run("docker", "compose", "-p", project, "-f", str(compose), "config", "--quiet")
    print("Validating staged API in an isolated container; current service remains running.", flush=True)
    candidate = validate_candidate(backup, updated["services"]["avora-api"]["environment"], api)
    write_json(backup / "candidate-verification.json", candidate)
    print("Isolated verification passed: stable owner identity and real garden read confirmed.", flush=True)
    print("Image ready; applying API and tunnel update.", flush=True)
    phase = "copy_source"
    try:
        for file in (STAGE / "app/avora_nas").glob("*.py"):
            shutil.copy2(file, source / file.name)
        phase = "recreate_services"
        run("docker", "compose", "-p", project, "-f", str(compose), "up", "-d", "--pull", "never",
            "--force-recreate", "avora-api", "avora-tunnel")
        phase = "live_health"
        health(health_urls)
        phase = "live_owner_verification"
        verification = verify_container(API)
        write_json(backup / "verification.json", verification)
        # Persist the working configuration for subsequent local Compose updates.
        shutil.copy2(compose, ROOT / "stack.yml")
        # Keep Portainer's stored stack source consistent when it owns this project.
        original_file = api["Config"]["Labels"].get("com.docker.compose.project.config_files", "")
        portainer_synced = False
        if original_file.startswith("/data/compose/") and "," not in original_file and ".." not in original_file:
            for cid in run("docker", "ps", "-q").split():
                item = json.loads(run("docker", "inspect", cid))[0]
                if "portainer" in item["Config"]["Image"].lower():
                    try:
                        run("docker", "cp", cid + ":" + original_file, str(backup / "portainer-stack.yml"))
                        run("docker", "cp", str(compose), cid + ":" + original_file)
                        portainer_synced = True
                        break
                    except RuntimeError:
                        continue
        report = {"success": True, "backup": str(backup), "portainer_source_updated": portainer_synced,
                  "verification": verification}
        (STAGE / "result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        (STAGE / "result.json").chmod(0o644)
        print(json.dumps(report), flush=True)
    except Exception as error:
        print("Failed phase: " + phase + "; " + str(error), flush=True)
        print("Verification failed; restoring previous application and container configuration.", flush=True)
        shutil.copy2(backup / "stack.yml", ROOT / "stack.yml")
        for file in (backup / "avora_nas").glob("*.py"):
            shutil.copy2(file, source / file.name)
        run("docker", "compose", "-p", project, "-f", str(rollback), "up", "-d", "--pull", "never",
            "--force-recreate", "avora-api", "avora-tunnel")
        health(health_urls)
        raise RuntimeError("Update rolled back; user data was not restored or deleted") from None


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        detail = str(error) if isinstance(error, RuntimeError) else type(error).__name__
        report = STAGE / "result.json"
        report.write_text(json.dumps({"success": False, "error": detail}), encoding="utf-8")
        report.chmod(0o644)
        print("Deployment stopped: " + detail, file=sys.stderr)
        sys.exit(1)
