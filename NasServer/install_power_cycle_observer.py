"""Install only the bounded observer after a completed tunnel migration."""
import json,os,tempfile,time
from pathlib import Path
import repair_power_cycle_tunnel as repair

def main():
    import fcntl
    os.umask(0o077)
    if os.geteuid()!=0: raise RuntimeError('Root required')
    with (repair.ROOT/'config/owner-update.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        api,tunnel=repair.inspect()
        if not tunnel['State']['Running'] or tunnel['HostConfig']['NetworkMode'].startswith('container:'):
            raise RuntimeError('Independent tunnel must already be running')
        networks=set(api['NetworkSettings']['Networks']) & set(tunnel['NetworkSettings']['Networks'])
        if len(networks)!=1: raise RuntimeError('Expected one shared network')
        report=repair.verify(api,next(iter(networks)))
        backup=Path(tempfile.mkdtemp(prefix='rc6-observer-install-',dir=repair.ROOT/'backups'))
        observer=repair.install_observer(report,backup)
        repair.write_json(backup/'observer.json',observer)
        print('OBSERVER: '+json.dumps(observer),flush=True)
        repair.phase('verify_scheduled_sample')
        initial=json.loads((repair.OBSERVER/'status.json').read_text())['sample_count']
        for _ in range(18):
            time.sleep(5)
            status=json.loads((repair.OBSERVER/'status.json').read_text())
            if status['sample_count']>initial:
                print('CRON_RESULT: '+json.dumps({'verified':True,'scheduler':observer['scheduler'],'sample_count':status['sample_count'],'last_issues':status['last_issues']}),flush=True)
                return
        raise RuntimeError('Scheduled sample not verified')

if __name__=='__main__':
    try:main()
    except (Exception,KeyboardInterrupt) as error:
        print('ERROR: '+json.dumps({'phase':repair.PHASE,'error_type':type(error).__name__,'secret_values_logged':False}),flush=True)
        raise SystemExit(1)
