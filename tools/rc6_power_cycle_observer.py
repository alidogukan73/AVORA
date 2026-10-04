"""Bounded RC-6 observer for the confirmed 23:00/10:00 NAS power schedule.

No application writes or recovery actions. NAS runs sample from cron; Pi runs loop.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import urllib.request

UTC = datetime.timezone.utc
TR = datetime.timezone(datetime.timedelta(hours=3))

def utc(epoch):
    return datetime.datetime.fromtimestamp(epoch, UTC).isoformat()

def phase(epoch):
    local = datetime.datetime.fromtimestamp(epoch, TR)
    minute = local.hour * 60 + local.minute
    if minute >= 23 * 60 or minute < 10 * 60:
        return 'scheduled_off'
    if minute < 10 * 60 + 10:
        return 'startup_grace'
    return 'required_online'

def command(args):
    return subprocess.check_output(args, stderr=subprocess.DEVNULL, text=True, timeout=15).strip()

def digest(root, subpaths):
    result = hashlib.sha256()
    for subpath in subpaths:
        p = root / subpath
        for file in sorted(p.rglob('*.py')) if p.is_dir() else [p]:
            result.update(str(file.relative_to(root)).encode() + b'\0' + file.read_bytes())
    return result.hexdigest()

def endpoint(url):
    try:
        with urllib.request.urlopen(url, timeout=8) as r:
            data = json.load(r)
        return {k: data.get(k) for k in ('status', 'storage_ready', 'version', 'configured') if k in data}
    except Exception as error:
        return {'error_type': type(error).__name__}

def snapshot(mode, config):
    result = {'boot_id': Path('/proc/sys/kernel/random/boot_id').read_text().strip(),
              'uptime_seconds': float(Path('/proc/uptime').read_text().split()[0])}
    if mode == 'pi':
        services = {}
        for unit in ('avora.service', 'avora-vision.service', 'mosquitto.service'):
            raw = command(['systemctl', 'show', unit, '-p', 'MainPID', '-p', 'NRestarts',
                           '-p', 'ActiveState', '-p', 'SubState', '-p', 'ExecMainStartTimestampMonotonic'])
            services[unit] = dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)
        result['services'] = services
        root = Path('/home/ali/AVORA/RaspberryPi')
        result['code_sha256'] = digest(root, ['main.py', 'core', 'services', 'hardware'])
        result['nas_local'] = endpoint('http://192.168.1.111:18787/health')
        result['nas_public'] = endpoint('https://avora-nas.tailf335a4.ts.net/health')
        result['vision'] = endpoint('https://avora-pi.tailf335a4.ts.net/health')
    else:
        data = json.loads(command([config['docker'], 'inspect', 'avora-nas-api', 'avora-tailscale']))
        result['containers'] = {}
        for c in data:
            s = c['State']
            result['containers'][c['Name'].lstrip('/')] = {
                'id': c['Id'], 'image': c['Image'], 'running': s.get('Running'),
                'started_at': s['StartedAt'], 'restarts': c['RestartCount'],
                'health': s.get('Health', {}).get('Status', 'none'), 'oom': s.get('OOMKilled', False)}
        result['code_sha256'] = digest(Path('/share/Docker/AVORA/app'), ['avora_nas'])
        result['nas_local'] = endpoint('http://127.0.0.1:18787/health')
        try:
            ts = json.loads(command([config['docker'], 'exec', 'avora-tailscale', 'tailscale', 'status', '--json']))
            result['tailscale_backend'] = ts.get('BackendState')
        except Exception as error:
            result['tailscale_backend'] = type(error).__name__
    return result

def evaluate(mode, current, baseline, previous, epoch, expected_version):
    issues, events = [], []
    period = phase(epoch)
    required = period == 'required_online'
    if current.get('code_sha256') != baseline.get('code_sha256'):
        issues.append('code_changed')
    if mode == 'pi':
        for key in ('boot_id', 'services'):
            if current.get(key) != baseline.get(key): issues.append(key + '_changed')
        if current.get('vision', {}).get('configured') is not True: issues.append('vision_unhealthy')
    else:
        boot_changed = previous and current.get('boot_id') != previous.get('boot_id')
        if boot_changed:
            (events if period == 'startup_grace' else issues).append(
                'scheduled_boot' if period == 'startup_grace' else 'unexpected_boot')
        for name, c in current.get('containers', {}).items():
            original = baseline.get('containers', {}).get(name, {})
            if any(c.get(k) != original.get(k) for k in ('id', 'image')): issues.append(name + ':identity_changed')
            if required and (not c.get('running') or c.get('oom') or
                             (name == 'avora-nas-api' and c.get('health') != 'healthy')):
                issues.append(name + ':unhealthy')
            old = (previous or {}).get('containers', {}).get(name)
            if old and any(c.get(k) != old.get(k) for k in ('started_at', 'restarts')):
                if not (period == 'startup_grace' and current.get('uptime_seconds', 99999) < 600): issues.append(name + ':unexpected_restart')
        if required and current.get('tailscale_backend') != 'Running': issues.append('tailscale_not_running')
    for key in ('nas_local', 'nas_public') if mode == 'pi' else ('nas_local',):
        h = current.get(key, {})
        if h.get('version') is not None and h['version'] != expected_version: issues.append(key + ':version_changed')
        if required and (h.get('status') != 'ok' or h.get('storage_ready') is not True): issues.append(key + ':unavailable')
    return sorted(set(issues)), events

def atomic(path, data):
    tmp = path.with_suffix('.tmp')
    with tmp.open('w') as f:
        json.dump(data, f)
        f.flush(); os.fsync(f.fileno())
    tmp.replace(path)

def sample(folder, mode):
    import fcntl
    os.umask(0o077)
    config = json.loads((folder / 'config.json').read_text())
    epoch = time.time()
    if epoch > config['end_epoch'] + 120: return
    with (folder / 'sample.lock').open('a') as lock:
        try: fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError: return
        status_path = folder / 'status.json'
        old = json.loads(status_path.read_text()) if status_path.exists() else {}
        baseline_path = folder / 'baseline.json'
        baseline = json.loads(baseline_path.read_text()) if baseline_path.exists() else None
        events = []
        try:
            current = snapshot(mode, config)
            if baseline is None:
                baseline = current
                atomic(baseline_path, baseline)
            issues, events = evaluate(mode, current, baseline, old.get('last_complete_snapshot'), epoch, config['nas_version'])
            complete = current
        except Exception as error:
            current = {'error_type': type(error).__name__}
            if mode == 'nas' and phase(epoch) == 'startup_grace':
                issues = []
                events.append('startup_observation_unavailable:' + type(error).__name__)
            else:
                issues = ['snapshot_failed:' + type(error).__name__]
            complete = old.get('last_complete_snapshot')
        previous_epoch = old.get('last_sample_epoch')
        if previous_epoch is not None and epoch - previous_epoch > 150:
            # NAS gaps are expected only across the confirmed nightly shutdown.
            prior_local = datetime.datetime.fromtimestamp(previous_epoch, TR)
            now_local = datetime.datetime.fromtimestamp(epoch, TR)
            planned = (mode == 'nas' and now_local.date() == prior_local.date() + datetime.timedelta(days=1)
                       and (prior_local.hour == 22 and prior_local.minute >= 58 or prior_local.hour == 23 and prior_local.minute <= 2)
                       and phase(epoch) == 'startup_grace')
            (events if planned else issues).append('scheduled_offline_gap' if planned else 'observation_gap')
        record = {'epoch': epoch, 'at': utc(epoch), 'mode': mode, 'phase': phase(epoch),
                  'sample': current, 'issues': sorted(set(issues)), 'events': events}
        with (folder / 'samples.jsonl').open('a') as f:
            f.write(json.dumps(record) + '\n'); f.flush(); os.fsync(f.fileno())
        status = {'mode': mode, 'last_sample_at': utc(epoch), 'last_sample_epoch': epoch,
                  'sample_count': old.get('sample_count', 0) + 1,
                  'problem_samples': old.get('problem_samples', 0) + bool(issues),
                  'last_issues': record['issues'], 'last_events': events, 'phase': phase(epoch),
                  'last_snapshot': current, 'last_complete_snapshot': complete,
                  'first_issue': old.get('first_issue') or ({'at': utc(epoch), 'issues': issues} if issues else None)}
        atomic(status_path, status)

if __name__ == '__main__':
    try:
        action, mode, location = sys.argv[1:]
        if mode not in ('pi', 'nas'): raise ValueError('Invalid mode')
        folder = Path(location)
        if action == 'sample': sample(folder, mode)
        elif action == 'run':
            config = json.loads((folder / 'config.json').read_text())
            while time.time() <= config['end_epoch'] + 120:
                tick = time.monotonic(); sample(folder, mode)
                time.sleep(max(1, 60 - (time.monotonic() - tick)))
        else: raise ValueError('Invalid action')
    except Exception as error:
        print(json.dumps({'success': False, 'error_type': type(error).__name__}), flush=True)
        raise SystemExit(1)
