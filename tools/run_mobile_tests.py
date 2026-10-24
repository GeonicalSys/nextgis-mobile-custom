"""Run related checks or complete phone/emulator acceptance; never target a working app."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import http.server
import itertools
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import threading
import time
from mobile_test_support import (PACKAGE, RUNNER, avd_names, check_publication_receipt, expand_selectors,
                                 native_catalog, parse_instrumentation, phone_unlocked, source_fingerprint, validate_plan)
from mobile_test_project import (DEFAULT_PROJECT, ProjectSelectionRequired, choose_project,
                                 read_projects, snapshot_project)

from mobile_style_evidence import collect_style_images

ROOT = Path(__file__).resolve().parents[1]
EMULATOR_HARDWARE = {'ranchu', 'goldfish'}



class Verification:
    def __init__(self, args):
        self.args=args
        self.sdk=Path(args.sdk or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
                      or Path(os.environ.get('LOCALAPPDATA',''))/'Android/Sdk')
        self.adb=str(self.sdk/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
        self.plan=json.loads((ROOT/'tools/mobile-test-plan.json').read_text(encoding='utf-8'))
        self.catalog=native_catalog(ROOT)
        self.required=validate_plan(self.plan,self.catalog)
        self.fingerprint=source_fingerprint(ROOT)
        stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        self.output=Path(args.output or ROOT/'build/verification'/stamp)
        self.output.mkdir(parents=True,exist_ok=True)
        self.results=[]
        self.checks={}
        self.devices={}
        self.owned_emulator=None
        self.saved_emulator={}
        self.project_fixture=None

    def command(self, argv, *, name=None, timeout=900, allow_failure=False):
        if name:
            print('RUN '+name,flush=True)
            log=self.output/(name+'.log')
            with log.open('w',encoding='utf-8') as stream:
                result=subprocess.run([str(x) for x in argv],cwd=ROOT,stdout=stream,
                                      stderr=subprocess.STDOUT,timeout=timeout,encoding='utf-8',errors='replace')
            text=log.read_text(encoding='utf-8')
        else:
            result=subprocess.run([str(x) for x in argv],cwd=ROOT,stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT,timeout=timeout,encoding='utf-8',errors='replace')
            text=result.stdout
        if result.returncode and not allow_failure:
            raise RuntimeError('Command failed: '+str(argv[0])+'\n'+text[-5000:])
        return text

    def shell(self,serial,*argv):
        return self.command([self.adb,'-s',serial,'shell',*argv]).strip()

    def identity(self,serial,kind):
        hardware=self.shell(serial,'getprop','ro.hardware')
        if (hardware in EMULATOR_HARDWARE) != (kind=='emulator'):
            raise ValueError('Incorrect '+kind+' device: '+serial)
        sdk=int(self.shell(serial,'getprop','ro.build.version.sdk'))
        if sdk < (36 if kind=='emulator' else 26):
            raise ValueError(kind+' does not meet the verification API requirement')
        self.devices[kind]={'serial':serial,'hardware':hardware,'sdk':sdk,
                            'fingerprint':self.shell(serial,'getprop','ro.build.fingerprint')}
        return serial

    def select_devices(self):
        phone_only=getattr(self.args,'phone_only',False)
        if phone_only and (self.args.scope!='related' or not self.args.phone_serial):
            raise ValueError('Phone-only checks require Related and an explicit phone serial')
        listing=self.command([self.adb,'devices'])
        connected=[line.split()[0] for line in listing.splitlines() if re.match(r'^\S+\s+device$',line)]
        if self.args.scope=='full':
            phones=[serial for serial in connected if not serial.startswith('emulator-')]
            if not self.args.phone_serial and len(phones)!=1:
                raise ValueError('Full acceptance needs one connected phone or an explicit --phone-serial')
            self.phone=self.identity(self.args.phone_serial or phones[0],'phone')
            self.select_project()
        elif self.args.phone_serial:
            self.phone=self.identity(self.args.phone_serial,'phone')
            self.related_phone_cases=sorted({case for area in self.args.area or ['layers']
                for case in self.plan.get('related_phone_areas',{}).get(area,[])})
            if not self.related_phone_cases:
                raise ValueError('No stationary phone checks are defined for the selected Related areas')
            if phone_only: return
        emulators=[serial for serial in connected if serial.startswith('emulator-')]
        if not self.args.emulator_serial and len(emulators)>1:
            raise ValueError('Select --emulator-serial when more than one emulator is running')
        selected=self.args.emulator_serial or (emulators[0] if emulators else self.start_emulator())
        self.emulator=self.identity(selected,'emulator')
        for key,command in [('font',['settings','get','system','font_scale']),
                            ('density',['wm','density']),('wifi',['settings','get','global','wifi_on']),
                            ('data',['settings','get','global','mobile_data']),
                            ('location',['cmd','location','is-location-enabled'])]:
            self.saved_emulator[key]=self.shell(self.emulator,*command)
        for key in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
            self.saved_emulator[key]=self.shell(self.emulator,'settings','get','global',key)
            self.shell(self.emulator,'settings','put','global',key,'0')
        self.shell(self.emulator,'cmd','location','set-location-enabled','true')

    def select_project(self):
        # Read-only discovery in the existing app. Never choose another project automatically.
        def read(*args):
            return self.command([self.adb,'-s',self.phone,'shell',*args],allow_failure=True).strip()
        selection=ROOT/'build/verification/authorized-test-project.json'
        selector=self.args.test_project or (json.loads(selection.read_text(encoding='utf-8'))['selector']
                    if selection.exists() else DEFAULT_PROJECT)
        try: projects=read_projects(read)
        except (ValueError, KeyError): projects=[]
        while True:
            try: project=choose_project(projects,selector); break
            except ProjectSelectionRequired as error:
                if not sys.stdin.isatty(): raise
                print(str(error),flush=True)
                selector=input('Введите ID, название или имя каталога разрешённого проекта: ').strip()
                if not selector: raise
        fixture=snapshot_project(read,project)
        self.project_fixture=self.output/'test-project-layers.json'
        self.project_fixture.write_text(json.dumps(fixture,ensure_ascii=False),encoding='utf-8')
        self.checks['test_project']={'id':project['id'],'layer_count':len(fixture['layers']),
                                    'snapshot_sha256':hashlib.sha256(self.project_fixture.read_bytes()).hexdigest()}
        selection.parent.mkdir(parents=True,exist_ok=True)
        selection.write_text(json.dumps({'selector':project['directory']}),encoding='utf-8')
        print('Test project '+str(project['id'])+': read-only layer policy snapshot',flush=True)

    def start_emulator(self):
        binary=self.sdk/'emulator'/('emulator.exe' if os.name=='nt' else 'emulator')
        avds=[self.args.avd] if self.args.avd else avd_names(self.command([binary,'-list-avds']))
        if not avds: raise ValueError('Install an API 36 AVD first, then use --avd NAME')
        for port in range(5556,5680,2):
            with socket.socket() as check:
                if check.connect_ex(('127.0.0.1',port))!=0: break
        serial='emulator-'+str(port)
        stream=(self.output/'emulator-start.log').open('w',encoding='utf-8')
        process=subprocess.Popen([str(binary),'-avd',avds[0],'-port',str(port),'-read-only','-no-snapshot',
                          '-no-window','-no-audio','-gpu','host'],stdout=stream,stderr=subprocess.STDOUT,
                         creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        stream.close()
        self.owned_emulator=serial
        print('Starting isolated read-only emulator '+serial,flush=True)
        until=time.monotonic()+180
        while time.monotonic()<until:
            if process.poll() is not None:
                raise RuntimeError('The isolated emulator exited before boot; see emulator-start.log')
            text=self.command([self.adb,'-s',serial,'shell','getprop','sys.boot_completed'],allow_failure=True)
            if text.strip()=='1': return serial
            time.sleep(2)
        raise RuntimeError('The isolated emulator did not boot; see emulator-start.log')

    def build(self):
        gradle=ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')
        if self.args.scope=='full':
            self.command([gradle,'-PciReliabilityChecks=true','--console=plain',
                          ':maplib:testDebugUnitTest',':maplibui:testDebugUnitTest',':app:testLisaDebugUnitTest',
                          ':maplibui:assembleDebug'],name='all-units',timeout=1800)
            self.command([sys.executable,'-m','unittest','discover','-s','tools/tests','-v'],name='runner-units')
            self.checks['units']=True
            self.command(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',
                          ROOT/'tools/verify-apk-version-matrix.ps1'],name='version-matrix',timeout=1800)
            self.checks['versions']=True
            self.command(['powershell.exe','-NoProfile','-ExecutionPolicy','Bypass','-File',
                          ROOT/'tools/docs-check.ps1','-RunTests'],name='docs',timeout=300)
            self.checks['docs']=True
        if self.args.skip_build:
            if self.args.scope=='full': raise ValueError('A publication full run must build the tested source itself')
        else:
            self.command([gradle,'-PfieldVerificationChecks=true','-PciReliabilityChecks=true','--console=plain',
                          ':app:assembleLisaDebug',':app:assembleLisaDebugAndroidTest'],name='verification-apks',timeout=1800)
        main=list((ROOT/'app/build/outputs/apk/lisa/debug').glob('*.apk'))
        tests=list((ROOT/'app/build/outputs/apk/androidTest/lisa/debug').glob('*.apk'))
        if len(main)!=1 or len(tests)!=1: raise ValueError('Exactly one verification app/test APK is required')
        aapt=self.sdk/'build-tools'
        versions=sorted((path for path in aapt.iterdir() if path.is_dir()),reverse=True)
        tool=next(path/('aapt.exe' if os.name=='nt' else 'aapt') for path in versions
                  if (path/('aapt.exe' if os.name=='nt' else 'aapt')).is_file())
        for apk,package in [(main[0],PACKAGE),(tests[0],PACKAGE+'.test')]:
            badging=self.command([tool,'dump','badging',apk])
            if not re.search(r"^package: name='"+re.escape(package)+"'",badging,re.M):
                raise ValueError('Refusing to install a non-verification APK: '+apk.name)
        self.apks=main+tests

    def install(self,serial):
        for apk in self.apks:
            text=self.command([self.adb,'-s',serial,'install','-r','-g',apk],timeout=120)
            if 'Success' not in text: raise RuntimeError('Verification APK installation failed: '+text)

    def prepare(self,serial):
        # The identity is a fixed constant, never derived from a caller-supplied package/URI.
        self.shell(serial,'input','keyevent','KEYCODE_WAKEUP')
        self.shell(serial,'wm','dismiss-keyguard')
        if serial == getattr(self, 'phone', None):
            deadline = time.monotonic() + 600
            announced = False
            while not phone_unlocked(self.shell(serial, 'dumpsys', 'trust')):
                if not announced:
                    print('WAIT: unlock the selected phone manually; UI tests have not started', flush=True)
                    announced = True
                if time.monotonic() >= deadline:
                    raise RuntimeError('The selected phone is locked; unlock it and rerun Full acceptance')
                time.sleep(2)
            # An expanded notification shade keeps input focus even after the test Activity starts.
            self.shell(serial,'cmd','statusbar','collapse')
        self.shell(serial,'am','force-stop',PACKAGE)
        if self.shell(serial,'pm','clear',PACKAGE)!='Success': raise RuntimeError('Cannot reset the disposable verification workspace')
        self.shell(serial,'cmd','appops','set','--uid',PACKAGE,'MANAGE_EXTERNAL_STORAGE','allow')
        for permission in ('ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION','GET_ACCOUNTS','POST_NOTIFICATIONS'):
            self.command([self.adb,'-s',serial,'shell','pm','grant',PACKAGE,'android.permission.'+permission],allow_failure=True)
        for permission in ('BLUETOOTH_CONNECT','BLUETOOTH_SCAN'):
            self.command([self.adb,'-s',serial,'shell','pm','revoke',PACKAGE,'android.permission.'+permission],allow_failure=True)
        self.command([self.adb,'-s',serial,'shell','cmd','locale','set-app-locales',PACKAGE,'--locales','ru'],allow_failure=True)

    def native(self,serial,selectors,name,*,reset=True,extra=None,fatal=None):
        if len(selectors)==1 and selectors[0] in self.plan.get('isolated_methods', []):
            passed=[];skipped=[]
            for case in sorted(self.catalog[selectors[0]]):
                result=self.native(serial,[case],name+'-'+case.split('#')[1],extra=extra)
                passed.extend(result['passed']);skipped.extend(result['skipped'])
            return {'passed':passed,'skipped':skipped,'expected_crash':False}
        if selectors==['com.nextgis.mobile.util.DiagnosticsNoiseTest']:
            return self.diagnostics_noise(serial,name)
        if reset: self.prepare(serial)
        extra=dict(extra or {})
        if any('ProjectLayerSelectionTest' in selector for selector in selectors):
            if not self.project_fixture: raise ProjectSelectionRequired('На каком проекте можно протестировать? Укажите --test-project.')
            destination='/sdcard/Android/data/'+PACKAGE+'/files/test-project-layers.json'
            self.shell(serial,'mkdir','-p',destination.rsplit('/',1)[0])
            self.command([self.adb,'-s',serial,'push',self.project_fixture,destination])
            extra['testProjectFixture']=destination
        expected=expand_selectors(selectors,self.catalog)
        argv=[self.adb,'-s',serial,'shell','am','instrument','-w','-r','-e','verificationPhone',
              'true' if serial==getattr(self,'phone',None) else 'false','-e','class',','.join(selectors)]
        for key,value in (extra or {}).items(): argv+=['-e',key,str(value)]
        text=self.command(argv+[RUNNER],name=name,allow_failure=bool(fatal))
        if any(selector.startswith('com.nextgis.mobile.rendering.') for selector in selectors):
            evidence_name=re.sub(r'[^A-Za-z0-9_.-]', '_', name)
            collect_style_images(self.adb,serial,self.output/'images'/evidence_name)
        try: result=parse_instrumentation(text,expected,fatal=fatal)
        except ValueError as error:
            raise RuntimeError(str(error)+'; see '+str(self.output/(name+'.log'))+'\n'+text[-4000:]) from error
        self.results.append({'name':name,'device':serial,**result})
        (self.output/'progress.json').write_text(json.dumps(self.receipt('running'),ensure_ascii=False,indent=2),encoding='utf-8')
        print('PASS '+name+': '+str(len(result['passed']))+' cases'+(' (expected fatal)' if fatal else ''),flush=True)
        return result

    def diagnostics_noise(self,serial,name):
        test='com.nextgis.mobile.util.DiagnosticsNoiseTest#'
        # These first two cases deliberately cross a real process restart without clearing data.
        self.native(serial,[test+'seedObsoleteEndpointAndLargeLocalBacklog'],name+'-seed')
        self.native(serial,[test+'coldStartRetainsBacklogAndDelegatingHandlerWithoutRemoteUpload'],name+'-restart',reset=False)
        remaining=[test+method for method in ('androidJsonHashKeepsSnapshotDownloadAndStagedFormConsistent',
                    'sdkBeforeSendPersistsAddressAndOneHttpEventPerBurst',
                    'actualSdkHttpCaptureRetainsOriginalRequestAndResponse')]
        return self.native(serial,remaining,name+'-sdk',reset=False)

    def grant_and_deny(self,serial):
        case='com.nextgis.mobile.reliability.StakeoutForegroundServiceTest#deniedLocationStopsGuidanceWithoutCrashOrLeakedGps'
        self.prepare(serial)
        for permission in ('ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION'):
            self.shell(serial,'pm','revoke',PACKAGE,'android.permission.'+permission)
        self.native(serial,[case],'stakeout-location-denied',reset=False)

    def ui_matrix(self):
        case='com.nextgis.mobile.reliability.LayerSelectionDialogTest#allChoicesAndCancelRemainTouchableAtLargeScales'
        self.prepare(self.emulator)
        for density,font,theme,orientation in itertools.product((420,540,617),('1.0','1.3','2.0'),('light','dark'),('portrait','landscape')):
            self.shell(self.emulator,'wm','density',str(density))
            self.shell(self.emulator,'settings','put','system','font_scale',font)
            name=f'chooser-{density}-{font}-{theme}-{orientation}'
            self.native(self.emulator,[case],name,reset=False,extra={'uiTheme':theme,'uiOrientation':orientation})
        self.checks['ui_matrix']=True

    def diagnostics(self):
        serial=self.emulator
        name='com.nextgis.mobile.util.DiagnosticsDeliveryTest'
        captured=[]
        class Receiver(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                captured.append(self.rfile.read(int(self.headers.get('Content-Length','0'))))
                self.send_response(200); self.send_header('Content-Length','2'); self.end_headers(); self.wfile.write(b'{}')
            def log_message(self,*_): pass
        server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Receiver)
        thread=threading.Thread(target=server.serve_forever,daemon=True); thread.start()
        self.prepare(serial)
        try:
            self.command([self.adb,'-s',serial,'reverse','tcp:18539','tcp:'+str(server.server_port)])
            self.shell(serial,'svc','wifi','disable'); self.shell(serial,'svc','data','disable')
            self.native(serial,[name+'#queueHandledWhileOffline'],'diagnostic-offline-handled',reset=False)
            fatal=name+'#crashWhileOffline'
            self.native(serial,[fatal],'diagnostic-offline-fatal',reset=False,fatal=fatal)
            self.native(serial,[name+'#reportsSurviveOfflineRestart'],'diagnostic-offline-restart',reset=False)
            def reconnect():
                time.sleep(5); self.shell(serial,'svc','wifi','enable')
            reconnect_thread=threading.Thread(target=reconnect); reconnect_thread.start()
            try: self.native(serial,[name+'#deliverWhenNetworkReturns'],'diagnostic-reconnect',reset=False)
            finally: reconnect_thread.join()
            if len(captured)<2: raise RuntimeError('Local receiver did not accept both persisted diagnostic reports')
        finally:
            self.command([self.adb,'-s',serial,'reverse','--remove','tcp:18539'],allow_failure=True)
            server.shutdown(); server.server_close()
            self.shell(serial,'svc','wifi','enable')
        self.native(serial,[name+'#workerRetriesTemporaryServerFailureWithoutNetworkChange'],'diagnostic-http-retry')

    def receipt(self,status):
        return {'schema_version':1,'scope':self.args.scope,'status':status,
                'created_at':datetime.now(timezone.utc).isoformat(),'source_fingerprint':self.fingerprint,
                'devices':self.devices,'checks':self.checks,'runs':self.results,
                'passed_cases':sorted({case for result in self.results for case in result['passed']})}

    def restore(self):
        for device in self.devices.values():
            self.command([self.adb,'-s',device['serial'],'shell','am','force-stop',PACKAGE],allow_failure=True)
        if not self.saved_emulator: return
        serial=self.emulator
        for key in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
            value=self.saved_emulator[key]
            self.shell(serial,'settings','delete','global',key) if value=='null' else self.shell(serial,'settings','put','global',key,value)
        self.shell(serial,'settings','put','system','font_scale',self.saved_emulator['font'])
        density=re.search(r'Override density:\s*(\d+)',self.saved_emulator['density'])
        self.shell(serial,'wm','density',density.group(1) if density else 'reset')
        for key,service in [('wifi','wifi'),('data','data')]:
            self.shell(serial,'svc',service,'enable' if self.saved_emulator[key]=='1' else 'disable')
        self.shell(serial,'cmd','location','set-location-enabled',self.saved_emulator['location'])
        if self.owned_emulator:
            self.command([self.adb,'-s',self.owned_emulator,'emu','kill'],allow_failure=True)

    def run(self):
        if self.args.scope=='check-publication':
            receipt=json.loads((ROOT/'build/verification/full-passed.json').read_text(encoding='utf-8'))
            check_publication_receipt(receipt,self.fingerprint,self.required)
            print('PASS: full phone/emulator acceptance matches the current source',flush=True)
            return
        if self.args.scope=='list':
            print(json.dumps(self.plan,ensure_ascii=False,indent=2)); return
        try:
            if self.args.scope=='full':
                # A failed/interrupted new acceptance must not leave a prior green gate behind.
                (ROOT/'build/verification/full-passed.json').unlink(missing_ok=True)
            self.select_devices(); self.build()
            if hasattr(self,'emulator'): self.install(self.emulator)
            if self.args.scope=='related' and hasattr(self,'phone'):
                self.install(self.phone)
            if self.args.scope=='full':
                self.install(self.phone)
                for test in self.plan['emulator']:
                    self.native(self.emulator,[test],'emulator-'+test.rsplit('.',1)[1])
                self.grant_and_deny(self.emulator)
                self.diagnostics(); self.ui_matrix()
                # Finish independent work before requiring a manually unlocked phone.
                for test in self.plan['phone']:
                    self.native(self.phone,[test],'phone-'+test.rsplit('.',1)[1])
                check_publication_receipt(self.receipt('passed'),self.fingerprint,self.required)
            else:
                selected=set()
                for area in self.args.area or ['layers']:
                    if area not in self.plan['areas']: raise ValueError('Unknown area: '+area)
                    selected.update(self.plan['areas'][area])
                if hasattr(self,'emulator'):
                    for index,test in enumerate(sorted(selected)):
                        self.native(self.emulator,[test],f'related-{index}-'+test.replace('#','-').rsplit('.',1)[1])
                    if any('StakeoutForegroundServiceTest' in test for test in selected): self.grant_and_deny(self.emulator)
                    if 'layers' in (self.args.area or ['layers']): self.ui_matrix()
                phone_failures=[]
                for index,test in enumerate(getattr(self,'related_phone_cases',[])):
                    try: self.native(self.phone,[test],f'phone-related-{index}-'+test.split('#')[-1])
                    except Exception as error: phone_failures.append(test+': '+str(error))
                if phone_failures: raise RuntimeError('\n'.join(phone_failures))
            if source_fingerprint(ROOT)!=self.fingerprint: raise RuntimeError('Source changed during verification; rerun the affected checks')
            receipt=self.receipt('passed')
            (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
            if self.args.scope=='full':
                (ROOT/'build/verification/full-passed.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
            print('PASS '+self.args.scope+': '+str(len(receipt['passed_cases']))+' distinct native cases; '+str(self.output),flush=True)
        except Exception as error:
            receipt=self.receipt('failed'); receipt['error']=str(error)
            (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
            raise
        finally: self.restore()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scope',choices=('related','full','check-publication','list'),default='related')
    parser.add_argument('--area',action='append')
    parser.add_argument('--phone-serial'); parser.add_argument('--emulator-serial')
    parser.add_argument('--phone-only',action='store_true',help='Related stationary verification checks only; explicit phone serial required')
    parser.add_argument('--test-project',help='Explicitly authorized Collector ID, name or workspace directory; default 1403')
    parser.add_argument('--avd'); parser.add_argument('--sdk'); parser.add_argument('--output')
    parser.add_argument('--skip-build',action='store_true')
    args=parser.parse_args()
    try: Verification(args).run()
    except Exception as error:
        print('FAIL: '+str(error),file=sys.stderr,flush=True); return 1
    return 0

if __name__=='__main__': sys.exit(main())
