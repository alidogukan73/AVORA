import copy
import datetime
import importlib.util
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'NasServer'))
import repair_power_cycle_tunnel as repair
spec = importlib.util.spec_from_file_location('power_observer', ROOT / 'tools/rc6_power_cycle_observer.py')
observer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(observer)

class MigrationTests(unittest.TestCase):
    def fixtures(self):
        api = {'Id':'api-id','Image':'api-image-id','State':{'StartedAt':'same'},
               'Config':{'Env':['SECRET=preserved'],'Image':'api-image','Cmd':['python','server'], 'WorkingDir':'/app','Labels':{}},
               'Mounts':[{'Type':'bind','Source':'/data','Destination':'/data','RW':True}],
               'HostConfig':{'PortBindings':{'8787/tcp':[{'HostIp':'127.0.0.1','HostPort':'18787'}]}},
               'NetworkSettings':{'Networks':{'avora_default':{'Aliases':['avora-api']}}}}
        tunnel = copy.deepcopy(api);tunnel['Config']['Env']=['TS_STATE_DIR=/var/lib/tailscale']
        cfg = {'services':{'avora-api':{'container_name':'avora-nas-api','read_only':True},
                           'avora-tunnel':{'container_name':'avora-tailscale','network_mode':'service:avora-api','restart':'always'}},
               'networks':{'default':{'name':'avora_default'}}}
        serve = {'Web':{'domain:443':{'Handlers':{'/':{'Proxy':'http://127.0.0.1:8787'}}}},'AllowFunnel':{'domain:443':True}}
        return cfg,serve,api,tunnel
    def test_migration_preserves_api_env_ports_mounts_and_funnel(self):
        cfg,serve,api,tunnel=self.fixtures();original=copy.deepcopy((cfg,serve,api,tunnel))
        updated,proxy,network=repair.migrate(cfg,serve,api,tunnel)
        self.assertEqual(original,(cfg,serve,api,tunnel))
        self.assertEqual({'SECRET':'preserved'},updated['services']['avora-api']['environment'])
        self.assertEqual('127.0.0.1',updated['services']['avora-api']['ports'][0]['host_ip'])
        self.assertEqual('/data',updated['services']['avora-api']['volumes'][0]['source'])
        self.assertTrue(updated['services']['avora-api']['read_only'])
        self.assertNotIn('network_mode',updated['services']['avora-tunnel'])
        self.assertEqual({'default':{}},updated['services']['avora-tunnel']['networks'])
        self.assertEqual('http://avora-api:8787',proxy['Web']['domain:443']['Handlers']['/']['Proxy'])
        self.assertEqual(serve['AllowFunnel'],proxy['AllowFunnel'])
    def test_unexpected_alias_or_proxy_is_rejected(self):
        cfg,serve,api,tunnel=self.fixtures()
        api['NetworkSettings']['Networks']['avora_default']['Aliases']=[]
        with self.assertRaises(ValueError):repair.migrate(cfg,serve,api,tunnel)
        cfg,serve,api,tunnel=self.fixtures();serve['Web']['domain:443']['Handlers']['/']['Proxy']='http://other:9999'
        with self.assertRaises(ValueError):repair.migrate(cfg,serve,api,tunnel)
    def test_mount_order_does_not_report_false_api_change(self):
        _,_,api,_=self.fixtures()
        api['Mounts'].append({'Type':'bind','Source':'/logs','Destination':'/logs','RW':True})
        after=copy.deepcopy(api);after['Mounts'].reverse()
        self.assertTrue(repair.unchanged(api,after))
    def test_api_restart_or_secret_change_is_not_unchanged(self):
        _,_,api,_=self.fixtures();after=copy.deepcopy(api)
        self.assertTrue(repair.unchanged(api,after))
        after['State']['StartedAt']='new';self.assertFalse(repair.unchanged(api,after))
        after=copy.deepcopy(api);after['Config']['Env']=['SECRET=changed'];self.assertFalse(repair.unchanged(api,after))

class ScheduleTests(unittest.TestCase):
    def epoch(self,h,m):
        return datetime.datetime(2026,9,30,h,m,tzinfo=observer.TR).timestamp()
    def pi(self):
        return {'boot_id':'boot','code_sha256':'hash','services':{'unit':'stable'},'vision':{'configured':True},
                'nas_local':{'error_type':'URLError'},'nas_public':{'error_type':'URLError'}}
    def test_boundaries(self):
        for h,m,phase in [(22,59,'required_online'),(23,0,'scheduled_off'),(9,59,'scheduled_off'),(10,0,'startup_grace'),(10,9,'startup_grace'),(10,10,'required_online')]:
            self.assertEqual(phase,observer.phase(self.epoch(h,m)))
    def test_expected_offline_never_hides_pi_failure(self):
        baseline=self.pi();current=copy.deepcopy(baseline)
        self.assertEqual([],observer.evaluate('pi',current,baseline,None,self.epoch(23,5),'0.1.6')[0])
        current['services']={'unit':'changed'}
        self.assertIn('services_changed',observer.evaluate('pi',current,baseline,None,self.epoch(23,5),'0.1.6')[0])
    def test_online_deadline_requires_both_endpoints(self):
        b=self.pi()
        self.assertEqual([],observer.evaluate('pi',b,b,None,self.epoch(10,9),'0.1.6')[0])
        self.assertEqual(['nas_local:unavailable','nas_public:unavailable'],observer.evaluate('pi',b,b,None,self.epoch(10,10),'0.1.6')[0])
    def test_scheduled_boot_allowed_but_daytime_restart_rejected(self):
        b={'boot_id':'old','code_sha256':'hash','containers':{'avora-nas-api':{'id':'id','image':'image','started_at':'yesterday','restarts':0,'running':True,'health':'healthy'}},'nas_local':{'version':'0.1.6','status':'ok','storage_ready':True},'tailscale_backend':'Running'}
        c=copy.deepcopy(b);c['boot_id']='new';c['uptime_seconds']=150;c['containers']['avora-nas-api']['started_at']='today'
        issues,events=observer.evaluate('nas',c,b,b,self.epoch(10,3),'0.1.6')
        self.assertEqual([],issues);self.assertIn('scheduled_boot',events)
        issues,_=observer.evaluate('nas',c,b,b,self.epoch(12,3),'0.1.6')
        self.assertIn('unexpected_boot',issues);self.assertIn('avora-nas-api:unexpected_restart',issues)
    def test_version_change_is_detected_even_during_grace(self):
        b=self.pi();c=copy.deepcopy(b);c['nas_local']={'version':'new'}
        self.assertIn('nas_local:version_changed',observer.evaluate('pi',c,b,None,self.epoch(10,3),'0.1.6')[0])

if __name__=='__main__':unittest.main()
