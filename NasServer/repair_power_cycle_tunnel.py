"""Migrate the NAS tunnel off the API network namespace; preserve API and secrets.

Run with sudo alongside deploy_owner_update.py and rc6_power_cycle_observer.py.
"""
import copy
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request

ROOT = Path('/share/Docker/AVORA')
SOURCES = [ROOT / 'stack.yml', Path('/share/Docker/PortainerCE/data/compose/1/docker-compose.yml')]
SERVE = ROOT / 'tailscale/config/serve.json'
OBSERVER = ROOT / 'rc6-power-cycle-20260929'
MARKER = '# AVORA_RC6_POWER_CYCLE_20260929'
PHASE = 'preflight'
SENSITIVE_VALUES = []

def phase(value):
    global PHASE
    PHASE = value
    print('STEP: ' + value, flush=True)

def run(*args):
    p = subprocess.run(args, capture_output=True, text=True, timeout=120)
    if p.returncode:
        detail = p.stderr
        for value in sorted(SENSITIVE_VALUES, key=len, reverse=True):
            if value: detail = detail.replace(value, '[redacted]')
        print('COMMAND_ERROR: ' + json.dumps({'phase':PHASE, 'command':list(args[:2]), 'exit':p.returncode, 'detail':detail[-1800:]}), flush=True)
        raise RuntimeError('Command failed')
    return p.stdout

def write_json(path, value):
    tmp = path.with_suffix(path.suffix + '.avora-tmp')
    with tmp.open('w') as f:
        json.dump(value, f, indent=2); f.flush(); os.fsync(f.fileno())
    tmp.chmod(0o600); tmp.replace(path)

def migrate(config, serve, api, tunnel):
    from deploy_owner_update import runtime_service, published_ports
    result = copy.deepcopy(config)
    for service, name in (('avora-api', 'avora-nas-api'), ('avora-tunnel', 'avora-tailscale')):
        if result.get('services', {}).get(service, {}).get('container_name') != name:
            raise ValueError('Unexpected service')
    networks = api['NetworkSettings']['Networks']
    if len(networks) != 1: raise ValueError('Expected one API network')
    network_name = next(iter(networks))
    if network_name in ('host', 'bridge', 'none'): raise ValueError('Expected user-defined network')
    network_keys = [k for k,v in result.get('networks', {}).items() if v.get('name') == network_name]
    if len(network_keys) != 1: raise ValueError('Compose network does not match API')
    aliases = networks[network_name].get('Aliases') or []
    if 'avora-api' not in aliases: raise ValueError('Missing stable API alias')
    for service, item in (('avora-api', api), ('avora-tunnel', tunnel)):
        result['services'][service] = runtime_service(result['services'][service], item)
    result['services']['avora-api']['ports'] = published_ports(api)[0]
    t = result['services']['avora-tunnel']
    if t.get('network_mode') != 'service:avora-api': raise ValueError('Unexpected tunnel network')
    if t.get('ports'): raise ValueError('Unexpected tunnel published ports')
    del t['network_mode']
    t['networks'] = {network_keys[0]: {}}
    t['restart'] = 'always'
    updated = copy.deepcopy(serve)
    handlers = [h for web in updated.get('Web', {}).values() for h in web.get('Handlers', {}).values()]
    proxies = [h for h in handlers if 'Proxy' in h]
    if len(proxies) != 1 or proxies[0]['Proxy'] not in ('http://127.0.0.1:8787', 'http://localhost:8787'):
        raise ValueError('Unexpected existing proxy')
    proxies[0]['Proxy'] = 'http://avora-api:8787'
    return result, updated, network_name

def inspect():
    return json.loads(run('docker', 'inspect', 'avora-nas-api', 'avora-tailscale'))

def unchanged(before, after):
    return (before['Id'] == after['Id'] and before['Image'] == after['Image']
            and before['State']['StartedAt'] == after['State']['StartedAt']
            and before['Config']['Env'] == after['Config']['Env']
            and sorted(json.dumps(m, sort_keys=True) for m in before['Mounts']) == sorted(json.dumps(m, sort_keys=True) for m in after['Mounts'])
            and before['HostConfig']['PortBindings'] == after['HostConfig']['PortBindings'])

def verify(api_before, network):
    for _ in range(45):
        api, tunnel = inspect()
        if not unchanged(api_before, api):
            print('VERIFY_ERROR: ' + json.dumps({'api_id_changed':api_before['Id']!=api['Id'], 'api_start_changed':api_before['State']['StartedAt']!=api['State']['StartedAt'], 'api_image_changed':api_before['Image']!=api['Image'], 'api_environment_changed':api_before['Config']['Env']!=api['Config']['Env']}), flush=True)
            raise RuntimeError('API changed unexpectedly')
        if (tunnel['State']['Running'] and not tunnel['HostConfig']['NetworkMode'].startswith('container:')
                and network in tunnel['NetworkSettings']['Networks']):
            try:
                ts = json.loads(run('docker', 'exec', 'avora-tailscale', 'tailscale', 'status', '--json'))
                raw = run('docker', 'exec', 'avora-tailscale', 'wget', '-qO-', '-T', '5', 'http://avora-api:8787/health')
                health = json.loads(raw)
                if ts.get('BackendState') == 'Running' and health.get('storage_ready') is True:
                    return {'api_id':api['Id'], 'api_started_at':api['State']['StartedAt'],
                            'tunnel_id':tunnel['Id'], 'network':network, 'nas_version':health.get('version')}
            except Exception: pass
        time.sleep(2)
    raise RuntimeError('Tunnel verification failed')

def install_observer(report, backup):
    phase('install_resumable_observer')
    cron_path = Path('/etc/config/crontab')
    systemctl = shutil.which('systemctl')
    if cron_path.exists(): scheduler = 'qnap_cron'
    elif systemctl and Path('/run/systemd/system').exists(): scheduler = 'systemd_timer'
    elif shutil.which('crontab'): scheduler = 'root_cron'
    else: raise RuntimeError('No supported persistent scheduler')
    if OBSERVER.exists(): raise RuntimeError('Observer already exists')
    OBSERVER.mkdir(mode=0o700)
    shutil.copy2(Path(__file__).with_name('rc6_power_cycle_observer.py'), OBSERVER / 'monitor.py')
    start_epoch = (int(time.time()) // 60 + 5) * 60
    config = {'start_epoch':start_epoch, 'end_epoch':start_epoch + 72 * 3600,
              'nas_version':report['nas_version'], 'docker':shutil.which('docker'),
              'schedule':'23:00-10:00 Europe/Istanbul; startup deadline 10:10'}
    write_json(OBSERVER / 'config.json', config)
    run('/usr/local/bin/python3', str(OBSERVER / 'monitor.py'), 'sample', 'nas', str(OBSERVER))
    status = json.loads((OBSERVER / 'status.json').read_text())
    if status['last_issues']: raise RuntimeError('Observer initial health failed')
    if scheduler == 'systemd_timer':
        service = Path('/etc/systemd/system/avora-rc6-observer.service')
        timer = Path('/etc/systemd/system/avora-rc6-observer.timer')
        if service.exists() or timer.exists(): raise RuntimeError('Observer unit already exists')
        service.write_text('[Unit]\nDescription=AVORA bounded RC6 observation\nAfter=docker.service\n[Service]\nType=oneshot\nUMask=0077\nTimeoutStartSec=55\nExecStart=/usr/local/bin/python3 ' + str(OBSERVER/'monitor.py') + ' sample nas ' + str(OBSERVER) + '\n')
        timer.write_text('[Unit]\nDescription=AVORA RC6 minute observations\n[Timer]\nOnBootSec=60s\nOnUnitActiveSec=60s\nAccuracySec=1s\nUnit=avora-rc6-observer.service\n[Install]\nWantedBy=timers.target\n')
        service.chmod(0o644); timer.chmod(0o644)
        run(systemctl, 'daemon-reload')
        run(systemctl, 'enable', '--now', 'avora-rc6-observer.timer')
        run(systemctl, 'start', 'avora-rc6-observer.service')
        run(systemctl, 'is-active', 'avora-rc6-observer.timer')
    else:
        if scheduler == 'qnap_cron': old_cron = cron_path.read_text()
        else:
            result = subprocess.run(['crontab','-l'],capture_output=True,text=True)
            if result.returncode not in (0,1): raise RuntimeError('Cannot read crontab')
            old_cron = result.stdout
        if MARKER in old_cron: raise RuntimeError('Observer cron already exists')
        (backup/'crontab.before').write_text(old_cron)
        entry = '* * * * * /usr/local/bin/python3 ' + str(OBSERVER/'monitor.py') + ' sample nas ' + str(OBSERVER) + ' >/dev/null 2>&1 ' + MARKER + '\n'
        candidate = old_cron.rstrip('\n') + '\n' + entry
        target = cron_path if scheduler == 'qnap_cron' else backup/'crontab.updated'
        target.write_text(candidate)
        run('crontab', str(target))
        if entry.strip() not in run('crontab','-l'): raise RuntimeError('Cron verification failed')
    return {'folder':str(OBSERVER), 'config':config, 'baseline':json.loads((OBSERVER/'baseline.json').read_text()),
            'scheduler':scheduler, 'cron_marker':MARKER if scheduler!='systemd_timer' else None,
            'timer_unit':'avora-rc6-observer.timer' if scheduler=='systemd_timer' else None,
            'cleanup_required_after_test':True}

def main():
    import fcntl
    os.umask(0o077)
    if os.geteuid() != 0: raise RuntimeError('Root required')
    with (ROOT/'config/owner-update.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        api, tunnel = inspect()
        if not api['State']['Running'] or api['State'].get('Health', {}).get('Status') != 'healthy':
            raise RuntimeError('API must be healthy')
        if not tunnel['HostConfig']['NetworkMode'].startswith('container:'):
            raise RuntimeError('Expected existing shared network tunnel')
        global SENSITIVE_VALUES
        SENSITIVE_VALUES = [entry.split('=',1)[1] for item in (api,tunnel) for entry in item['Config']['Env'] if '=' in entry and re.search(r'PASSWORD|SECRET|TOKEN|AUTH_KEY|PRIVATE_KEY',entry.split('=',1)[0],re.I)]
        project = api['Config']['Labels']['com.docker.compose.project']
        configs = []
        for source in SOURCES:
            if source.is_symlink() or not source.is_file(): raise RuntimeError('Unexpected configuration path')
            cfg = json.loads(run('docker', 'compose', '-f', str(source), 'config', '--format', 'json'))
            for name, item in (('avora-api', api), ('avora-tunnel', tunnel)):
                image = json.loads(run('docker', 'image', 'inspect', cfg['services'][name]['image']))[0]
                if image['Id'] != item['Image']: raise RuntimeError('Persisted image differs')
            configs.append(cfg)
        if SERVE.is_symlink(): raise RuntimeError('Unexpected proxy path')
        original_serve = json.loads(SERVE.read_text())
        updated, serve, network = migrate(configs[0], original_serve, api, tunnel)
        updated['name'] = project
        backup = Path(tempfile.mkdtemp(prefix='power-cycle-tunnel-', dir=ROOT/'backups'))
        for i, source in enumerate(SOURCES): shutil.copy2(source, backup/('source-'+str(i)+'.json'))
        shutil.copy2(SERVE, backup/'serve.before.json')
        write_json(backup/'runtime.before.json', {'api':api,'tunnel':tunnel})
        write_json(backup/'candidate.json', updated)
        phase('validate_candidate')
        run('docker','compose','-p',project,'-f',str(backup/'candidate.json'),'config','--quiet')
        try:
            phase('apply_tunnel_only')
            write_json(SERVE, serve)
            run('docker','compose','-p',project,'-f',str(backup/'candidate.json'),'up','-d','--pull','never','--no-deps','--force-recreate','avora-tunnel')
            report = verify(api, network)
            phase('verify_tunnel_restart')
            run('docker','restart','avora-tailscale')
            report = verify(api, network)
            phase('persist_configuration')
            for source in SOURCES: write_json(source, updated)
        except BaseException:
            phase('rollback_tunnel_configuration')
            shutil.copy2(backup/'serve.before.json',SERVE)
            for i,source in enumerate(SOURCES): shutil.copy2(backup/('source-'+str(i)+'.json'),source)
            run('docker','compose','-p',project,'-f',str(SOURCES[0]),'up','-d','--pull','never','--no-deps','--force-recreate','avora-tunnel')
            raise
        report.update(success=True,api_unchanged=True,backup=str(backup),tunnel_restart_verified=True)
        write_json(backup/'result.json',report)
        print('RESULT: '+json.dumps(report),flush=True)
        observer=install_observer(report,backup)
        write_json(backup/'observer.json',observer)
        print('OBSERVER: '+json.dumps(observer),flush=True)
        phase('verify_cron_sample')
        for _ in range(16):
            time.sleep(5)
            state=json.loads((OBSERVER/'status.json').read_text())
            if state.get('sample_count',0)>1:
                print('CRON_RESULT: '+json.dumps({'verified':True,'sample_count':state['sample_count'],'last_issues':state['last_issues']}),flush=True)
                break
        else: raise RuntimeError('Scheduled observer sample not verified')

if __name__=='__main__':
    try: main()
    except (Exception,KeyboardInterrupt) as error:
        print('ERROR: '+json.dumps({'phase':PHASE,'error_type':type(error).__name__,'secret_values_logged':False}),flush=True)
        raise SystemExit(1)
