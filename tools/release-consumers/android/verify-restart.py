#!/usr/bin/env python3
"""Install only the dedicated proof app; exercise durable consent across process kills."""
import os, pathlib, subprocess, json, time, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent
adb = os.environ.get('QM_ADB', 'adb')
serial = os.environ['QM_ANDROID_DEVICE']
package = 'com.quietmetrix.releaseproof'
def command(*args):
    return subprocess.check_output([adb,'-s',serial,*args],text=True).strip()
def launch(action, default=False):
    command('shell','am','force-stop',package)
    command('shell','am','start','-W','-n',package+'/.MainActivity','--es','proof_action',action,'--ez','default_allowed',str(default).lower())
    return command('shell','run-as',package,'cat','files/proof.txt')
command('install','-r',str(root/'build/outputs/apk/debug/quietmetrix-android-release-proof-debug.apk'))
# Reset only this purpose-built fixture app, never the demo/sample/customer app.
command('shell','pm','clear',package)
def count(metric):
    xml=command('shell','run-as',package,'cat','shared_prefs/release_proof_quietmetrix.xml')
    node=ET.fromstring(xml).find("string[@name='release_proof_counter_pipeline_v1']")
    if node is None: return 0
    state=json.loads(node.text)
    cells=state.get('pending',[])+[cell for batch in state.get('outbox',[]) for cell in batch['counters']]
    return sum(cell['n'] for cell in cells if cell['metric']==metric and cell.get('hour') is not None)
def wait_for(metric,expected):
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        try:
            if count(metric)==expected:return
        except (ET.ParseError,json.JSONDecodeError,subprocess.CalledProcessError):
            pass  # A lifecycle write can replace the file while adb is reading it.
        time.sleep(.2)
    raise AssertionError((metric,expected,count(metric)))
assert launch('grant') == 'consent=true;enabled=true;chosen=true'
wait_for('visit_start_v2',1)
command('shell','input','keyevent','KEYCODE_HOME')
wait_for('visit_complete_v2',1)
assert launch('probe') == 'consent=true;enabled=true;chosen=true'
wait_for('visit_start_v2',2)
assert launch('probe') == 'consent=true;enabled=true;chosen=true'
wait_for('visit_start_v2',3)
assert count('visit_complete_v2')==1, 'Abrupt kill must preserve the visit without inventing a completion'
assert launch('revoke') == 'consent=false;enabled=false;chosen=true'
assert launch('probe',True) == 'consent=false;enabled=false;chosen=true'
wait_for('visit_start_v2',0)
command('shell','am','force-stop',package)
print('Android device proof passed: published SDK consumer, consent/opt-out durable through process kills; foreground visits persist and background closes duration.')
