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
                                 read_projects, snapshot_project, load_policy_fixture)

from mobile_style_evidence import collect_style_images
from mobile_sqlite_evidence import SQLITE_CLASS, collect_sqlite_evidence
from mobile_native_log import start_native_log_capture
from mobile_render_acceptance import STRESS_CLASS, STRESS_CASE, STRESS_SEED, record_stress
from mobile_emulator_safety import (SOFTWARE_GPU, require_software_graphics, require_whpx,
                                    windows_resources, require_windows_resources, require_memory_limit, launch_environment, require_cpu_vulkan, WindowsEmulatorProcess)
from mobile_angle_driver import SystemAngleProbe, cached_probe, file_sha256, require_runtime_files
from mobile_build_identity import (GRADLE_ARGUMENTS, build_and_record, load_basis,
                                   recipe as build_recipe, verify_pair)

ROOT = Path(__file__).resolve().parents[1]
EMULATOR_HARDWARE = {'ranchu', 'goldfish'}
OFFLINE_TILE_CLASS = 'com.nextgis.mobile.rendering.DirectVectorTilesOfflineTest'


class NativeCheckFailure(RuntimeError):
    """A completed instrumentation attempt reported a native assertion/protocol failure."""


class NativeStartupTimeout(RuntimeError):
    """Instrumentation did not report the start of a test within its startup budget."""



class Verification:
    def __init__(self, args):
        self.args=args
        self.sdk=Path(args.sdk or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
                      or Path(os.environ.get('LOCALAPPDATA',''))/'Android/Sdk')
        self.adb=str(self.sdk/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb'))
        self.native_log_capture=start_native_log_capture
        self.plan=json.loads((ROOT/'tools/mobile-test-plan.json').read_text(encoding='utf-8'))
        self.catalog=native_catalog(ROOT)
        self.required=validate_plan(self.plan,self.catalog)
        self.required_phone=frozenset(expand_selectors(self.plan['phone'],self.catalog))
        self.fingerprint=source_fingerprint(ROOT)
        stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        self.output=Path(args.output or ROOT/'build/verification'/stamp)
        self.output.mkdir(parents=True,exist_ok=True)
        self.results=[]
        self.checks={}
        self.devices={}
        self.owned_emulator=None
        self.emulator_process=None
        self.angle_session=None
        self.saved_emulator={}
        self.project_fixture=None
        self.build_basis=None
        self.emulator_profile={}
        mode=getattr(args,'emulator_graphics','auto')
        local=ROOT/'build/verification/emulator-profile.json'
        if mode!='native' and not (mode=='auto' and getattr(args,'phone_only',False)) and local.is_file():
            profile=json.loads(local.read_text(encoding='utf-8'))
            if profile.get('schema_version')!=1 or not all(isinstance(profile.get(key),str) and profile[key] for key in ('runtime','avd','avd_home','emulator_home','runtime_exe_sha256')):
                raise ValueError('The local emulator profile is incomplete; no emulator was launched')
            if getattr(args,'avd',None) and args.avd!=profile['avd'] and mode=='auto':
                raise ValueError('An explicit AVD differs from the verified local profile; choose a graphics mode explicitly')
            self.emulator_profile=profile
        self.graphics_mode=('guest-angle' if self.emulator_profile else 'native') if mode=='auto' else ('guest-angle' if mode=='angle' else mode)

    def guest_angle(self):
        return getattr(self,'graphics_mode',getattr(self.args,'emulator_graphics','native')) in ('angle','guest-angle')

    def command(self, argv, *, name=None, timeout=900, allow_failure=False, input_text=None,
                native_startup_timeout=None):
        if (getattr(self,'emulator_transport_bound',False) and len(argv)>2
                and str(argv[0])==str(self.adb) and argv[1]=='-s' and argv[2]==self.owned_emulator):
            process=self.emulator_process
            if isinstance(process,WindowsEmulatorProcess) and not process.owns_console(int(self.owned_emulator.removeprefix('emulator-'))):
                raise RuntimeError('The owned emulator transport disappeared or changed owner; no ADB command was sent')
        if name:
            print('RUN '+name,flush=True)
            log=self.output/(name+'.log')
            with log.open('w',encoding='utf-8') as stream:
                if native_startup_timeout is None:
                    result=subprocess.run([str(x) for x in argv],cwd=ROOT,stdout=stream,
                                          stderr=subprocess.STDOUT,timeout=timeout,encoding='utf-8',errors='replace',input=input_text)
                else:
                    if input_text is not None: raise ValueError('Native startup supervision does not accept stdin')
                    result=self.wait_native_command(argv,stream,log,timeout,native_startup_timeout)
            text=log.read_text(encoding='utf-8')
        else:
            result=subprocess.run([str(x) for x in argv],cwd=ROOT,stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT,timeout=timeout,encoding='utf-8',errors='replace',input=input_text)
            text=result.stdout
        if result.returncode and not allow_failure:
            raise RuntimeError('Command failed: '+str(argv[0])+'\n'+text[-5000:])
        return text

    def wait_native_command(self,argv,stream,log,timeout,startup_timeout):
        # Own this one ADB client; never terminate the shared ADB server or another run.
        process=subprocess.Popen([str(x) for x in argv],cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,
                                 creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        began=time.monotonic();reported_start=False;startup_expired=False
        try:
            while process.poll() is None:
                elapsed=time.monotonic()-began
                if not reported_start:
                    reported_start=bool(re.search(r'^INSTRUMENTATION_STATUS_CODE:\s*1\s*$',
                                                 log.read_text(encoding='utf-8',errors='replace'),re.M))
                    attempt=getattr(self,'active_native_attempt',None)
                    if reported_start and attempt is not None:
                        attempt['phase']='after-test-start'
                        self.write_progress()
                    if not reported_start and elapsed>=startup_timeout:
                        startup_expired=True
                        raise NativeStartupTimeout('Instrumentation did not report a test start within '
                                                   +str(startup_timeout)+' seconds; see '+str(log))
                if elapsed>=timeout: raise subprocess.TimeoutExpired([str(x) for x in argv],timeout)
                time.sleep(.1)
            return subprocess.CompletedProcess(argv,process.returncode)
        finally:
            if process.poll() is None: process.kill()
            process.wait()
            if startup_expired:
                log.with_suffix('.startup-timeout.json').write_text(json.dumps({
                    'phase':'before-test-start','startup_timeout_seconds':startup_timeout,
                    'client_pid':process.pid,'client_exited':process.returncode is not None,
                    'client_returncode':process.returncode,'log':str(log),
                    'created_at':datetime.now(timezone.utc).isoformat()},indent=2),encoding='utf-8')

    def shell(self,serial,*argv):
        if getattr(self,'restoring',False):
            return self.command([self.adb,'-s',serial,'shell',*argv],timeout=8).strip()
        return self.command([self.adb,'-s',serial,'shell',*argv]).strip()

    def identity(self,serial,kind):
        hardware=self.shell(serial,'getprop','ro.hardware')
        if (hardware in EMULATOR_HARDWARE) != (kind=='emulator'):
            raise ValueError('Incorrect '+kind+' device: '+serial)
        sdk=int(self.shell(serial,'getprop','ro.build.version.sdk'))
        if sdk < (36 if kind=='emulator' else 26):
            raise ValueError(kind+' does not meet the verification API requirement')
        self.devices[kind]={'serial':serial,'hardware':hardware,'sdk':sdk,
                            'characteristics':self.shell(serial,'getprop','ro.build.characteristics'),
                            'fingerprint':self.shell(serial,'getprop','ro.build.fingerprint')}
        return serial

    def select_devices(self):
        phone_only=getattr(self.args,'phone_only',False)
        angle=self.guest_angle()
        if angle and phone_only:
            raise ValueError('ANGLE routing is restricted to an owned emulator; PhoneOnly stays unchanged')
        if phone_only and (self.args.scope!='related' or not self.args.phone_serial):
            raise ValueError('Phone-only checks require Related and an explicit phone serial')
        if not phone_only and not getattr(self.args, 'allow_emulator', False):
            raise ValueError('Emulator runs are on hold after host hard hangs. Review the host incident first; '
                             'an explicitly authorized software-only run requires --allow-emulator. '
                             'Related --phone-only does not launch an emulator.')
        listing=self.command([self.adb,'devices'])
        connected=[line.split()[0] for line in listing.splitlines() if re.match(r'^\S+\s+device$',line)]
        if self.args.scope=='full':
            phones=[serial for serial in connected if not serial.startswith('emulator-')]
            if not self.args.phone_serial and len(phones)!=1:
                raise ValueError('Full acceptance needs one connected phone or an explicit --phone-serial')
            self.phone=self.identity(self.args.phone_serial or phones[0],'phone')
            if OFFLINE_TILE_CLASS in self.plan['phone']:
                self.require_offline_usb_phone()
            self.select_project()
        elif self.args.phone_serial:
            self.phone=self.identity(self.args.phone_serial,'phone')
            self.related_phone_cases=sorted({case for area in self.args.area or ['layers']
                for case in self.plan.get('related_phone_areas',{}).get(area,[])})
            if not self.related_phone_cases:
                raise ValueError('No stationary phone checks are defined for the selected Related areas')
            if any(case.partition('#')[0] == OFFLINE_TILE_CLASS for case in self.related_phone_cases):
                self.require_offline_usb_phone()
            if any('ProjectLayerSelectionTest' in case for case in self.related_phone_cases): self.select_project()
            if phone_only: return
        if self.args.scope!='full' and getattr(self.args,'test_project_fixture',None) and not self.project_fixture:
            self.select_project()
        emulators=[serial for serial in connected if serial.startswith('emulator-')]
        if angle and (self.args.emulator_serial or emulators):
            raise ValueError('ANGLE requires a new owned emulator with verified software Vulkan; do not borrow an AVD')
        if not self.args.emulator_serial and len(emulators)>1:
            raise ValueError('Select --emulator-serial when more than one emulator is running')
        selected=self.args.emulator_serial or (emulators[0] if emulators else self.start_emulator())
        self.emulator=self.identity(selected,'emulator')
        surface=self.shell(self.emulator,'dumpsys','SurfaceFlinger')
        (self.output/'emulator-surfaceflinger.txt').write_text(surface,encoding='utf-8')
        if self.guest_angle():
            raw=self.command([self.adb,'-s',self.emulator,'shell','cmd','gpu','vkjson'],timeout=25)
            (self.output/'emulator-vulkan.json').write_text(raw,encoding='utf-8')
            proof=require_cpu_vulkan(surface,json.loads(raw),(self.output/'emulator-start.log').read_text(encoding='utf-8',errors='replace'))
            self.checks['emulator_vulkan']=proof
            self.devices['emulator']['graphics']='Vulkan CPU: '+proof['device_name']
        else:self.devices['emulator']['graphics']=require_software_graphics(surface)
        for key,command in [('font',['settings','get','system','font_scale']),
                            ('density',['wm','density']),('wifi',['settings','get','global','wifi_on']),
                            ('data',['settings','get','global','mobile_data']),
                            ('location',['cmd','location','is-location-enabled']),
                            ('screen_timeout',['settings','get','system','screen_off_timeout'])]:
            self.saved_emulator[key]=self.shell(self.emulator,*command)
        for key in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
            self.saved_emulator[key]=self.shell(self.emulator,'settings','get','global',key)
            self.shell(self.emulator,'settings','put','global',key,'0')
        self.shell(self.emulator,'cmd','location','set-location-enabled','true')
        self.shell(self.emulator,'settings','put','system','screen_off_timeout','2147483647')

    def select_project(self):
        source=getattr(self.args,'test_project_fixture',None)
        if source:
            fixture=load_policy_fixture(Path(source),self.args.test_project or DEFAULT_PROJECT)
            self.project_fixture=self.output/'test-project-layers.json'
            self.project_fixture.write_text(json.dumps(fixture,ensure_ascii=False),encoding='utf-8')
            self.checks['test_project']={'id':fixture['project_id'],'layer_count':len(fixture['layers']),
                'snapshot_sha256':hashlib.sha256(self.project_fixture.read_bytes()).hexdigest(),
                'source':'explicit-policy-fixture','source_sha256':hashlib.sha256(Path(source).read_bytes()).hexdigest()}
            print('Test project '+str(fixture['project_id'])+': explicitly supplied read-only policy snapshot',flush=True)
            return
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
        guest=self.guest_angle()
        profile=getattr(self,'emulator_profile',{})
        runtime=Path(getattr(self.args,'emulator_runtime',None) or profile.get('runtime') or self.sdk/'emulator')
        binary=runtime/('emulator.exe' if os.name=='nt' else 'emulator')
        memory_mib=2560 if guest else 2048
        if guest:
            contract=json.loads((ROOT/'tools/emulator-angle-driver.json').read_text(encoding='utf-8'))
            if contract.get('schema_version')!=2:raise ValueError('Invalid software emulator contract')
            if file_sha256(binary)!=contract['emulator']['exe_sha256']:raise ValueError('The software runtime differs from its verified identity')
            runtime_files=require_runtime_files(runtime,contract)
            if os.name!='nt':raise ValueError('The GuestAngle profile is currently validated on Windows only')
            expected=profile.get('runtime_exe_sha256')
            if expected and file_sha256(binary)!=expected:raise ValueError('The selected emulator runtime changed since profile verification')
            version=self.command([binary,'-version'],timeout=15)
            if '37.2.13.0' not in version:raise ValueError('GuestAngle requires the validated Emulator37.2.13 runtime')
            self.checks['emulator_runtime']={'version':'37.2.13','binary':str(binary),'sha256':file_sha256(binary),'files_sha256':runtime_files,'backend':'lavapipe'}
        if os.name=='nt':
            acceleration=self.command([binary,'-accel-check'],timeout=15)
            require_whpx(acceleration)
            self.checks['emulator_host']={'acceleration':acceleration,
                'resources':require_windows_resources(windows_resources(),6 if guest else 4.5),'cores':2,'memory_mib':memory_mib}
        if guest:self.angle_inputs=cached_probe(ROOT,self.sdk,ROOT/'build/verification/emulator-gles-cache')
        avd=getattr(self.args,'avd',None) or profile.get('avd')
        environment=launch_environment(runtime,guest,avd_home=profile.get('avd_home'),emulator_home=profile.get('emulator_home'))
        avds=[avd] if avd else avd_names(self.command([binary,'-list-avds']))
        if not avds:raise ValueError('Install an API36 AVD first, then use --avd NAME')
        for port in range(5556,5680,2):
            with socket.socket() as check:
                if check.connect_ex(('127.0.0.1',port))!=0:break
        else:raise RuntimeError('No unused emulator console port is available')
        serial='emulator-'+str(port)
        argv=[str(binary),'-avd',avds[0],'-port',str(port),'-read-only','-no-snapshot','-no-window','-no-audio',
              '-accel','on','-cores','2','-memory',str(memory_mib),'-gpu','lavapipe' if guest else SOFTWARE_GPU,
              '-feature','Vulkan,GuestAngle' if guest else '-Vulkan']
        with (self.output/'emulator-start.log').open('w',encoding='utf-8') as stream:
            if os.name=='nt':process=WindowsEmulatorProcess(argv,stream,environment,ROOT)
            else:process=subprocess.Popen(argv,stdout=stream,stderr=subprocess.STDOUT,env=environment)
        self.owned_emulator=serial;self.emulator_process=process
        print('Starting isolated read-only emulator '+serial,flush=True)
        until=time.monotonic()+180
        while time.monotonic()<until:
            require_memory_limit((self.output/'emulator-start.log').read_text(encoding='utf-8',errors='replace'),memory_mib)
            if process.poll() is not None:raise RuntimeError('The isolated emulator exited before boot; see emulator-start.log')
            text=self.command([self.adb,'-s',serial,'shell','getprop','sys.boot_completed'],allow_failure=True,timeout=8)
            if text.strip()=='1':
                if os.name=='nt':
                    if 'Windows Hypervisor Platform accelerator is operational' not in (self.output/'emulator-start.log').read_text(encoding='utf-8',errors='replace'):
                        raise RuntimeError('The launched emulator did not confirm actual WHPX execution')
                    if not process.owns_console(port):raise RuntimeError('The selected console belongs to a different process')
                self.emulator_transport_bound=True
                return serial
            time.sleep(2)
        raise RuntimeError('The isolated emulator did not boot; see emulator-start.log')

    def configure_emulator_graphics(self):
        if not self.guest_angle():return
        if not self.owned_emulator or self.owned_emulator!=self.emulator:
            raise ValueError("GuestAngle is restricted to the current run's owned read-only emulator")
        memory=re.search(r'^MemTotal:\s+(\d+)\s+kB',self.shell(self.emulator,'cat','/proc/meminfo'),re.M)
        if not memory or int(memory.group(1))>2560*1024:raise ValueError('The guest exceeded the admitted RAM limit')
        self.devices['emulator']['memory_total_kib']=int(memory.group(1))
        self.angle_session=SystemAngleProbe(self.command,self.adb,self.emulator,self.output,self.angle_inputs)
        proof=self.angle_session.verify()
        contract=json.loads((ROOT/'tools/emulator-angle-driver.json').read_text(encoding='utf-8'))
        fingerprint=self.shell(self.emulator,'getprop','ro.build.fingerprint')
        if fingerprint!=contract['system_image']['fingerprint']:
            raise ValueError('The GuestAngle system image differs from the validated API36 revision7 build')
        if set(proof['system_library_sha256'].values())!={contract['system_image']['gles_library_sha256']}:
            raise ValueError('The actual system ANGLE library differs from the validated image')
        self.devices['emulator']['fingerprint']=fingerprint
        self.checks['emulator_angle']=proof
        self.devices['emulator']['graphics_mode']='guest-system-angle-over-lavapipe'

    def build(self):
        if self.args.skip_build and self.args.scope=='full':
            raise ValueError('A publication full run must build the tested source itself')
        gradle=ROOT/('gradlew.bat' if os.name=='nt' else 'gradlew')
        if self.args.scope=='full':
            self.command([gradle,'-PciReliabilityChecks=true','-Pkotlin.incremental=false','--console=plain',
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
        receipt=ROOT/'build/verification/apk-build.json'
        recipe=build_recipe(ROOT)
        if self.args.skip_build:
            self.discover_verification_apks()
            self.build_basis=load_basis(receipt,self.fingerprint,recipe,self.apk_pair())
        else:
            self.build_basis=build_and_record(receipt,self.fingerprint,recipe,
                    lambda:source_fingerprint(ROOT),
                    lambda:self.command([gradle,*GRADLE_ARGUMENTS],name='verification-apks',timeout=1800),
                    self.discover_verification_apks)

    def apk_pair(self):
        return dict(zip(('app','test'),self.apks))

    def assert_build_pair(self):
        verify_pair(self.build_basis,source_fingerprint(ROOT),build_recipe(ROOT),self.apk_pair())

    def discover_verification_apks(self):
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
        return self.apk_pair()

    def install(self,serial):
        self.assert_build_pair()
        for apk in self.apks:
            text=self.command([self.adb,'-s',serial,'install','-r','-g',apk],timeout=120)
            if 'Success' not in text: raise RuntimeError('Verification APK installation failed: '+text)
        self.assert_build_pair()

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
        self.shell(serial,'am','force-stop',PACKAGE+'.test')
        if self.shell(serial,'pm','clear',PACKAGE)!='Success': raise RuntimeError('Cannot reset the disposable verification workspace')
        self.shell(serial,'cmd','appops','set','--uid',PACKAGE,'MANAGE_EXTERNAL_STORAGE','allow')
        for permission in ('ACCESS_COARSE_LOCATION','ACCESS_FINE_LOCATION','ACCESS_BACKGROUND_LOCATION','GET_ACCOUNTS','POST_NOTIFICATIONS'):
            self.command([self.adb,'-s',serial,'shell','pm','grant',PACKAGE,'android.permission.'+permission],allow_failure=True)
        for permission in ('BLUETOOTH_CONNECT','BLUETOOTH_SCAN'):
            self.command([self.adb,'-s',serial,'shell','pm','revoke',PACKAGE,'android.permission.'+permission],allow_failure=True)
        self.command([self.adb,'-s',serial,'shell','cmd','locale','set-app-locales',PACKAGE,'--locales','ru'],allow_failure=True)

    def require_offline_usb_phone(self):
        usb_serial = self.command([self.adb, '-d', 'get-serialno'], timeout=10, allow_failure=True).strip()
        if usb_serial != self.phone:
            raise RuntimeError('Connect the selected offline verification phone as the only USB Android device. '
                               'The tablet can stay connected by Wi-Fi.')

    def phone_offline_settings(self):
        return tuple(self.command([self.adb, '-s', self.phone, 'shell', 'settings', 'get', 'global', setting],
                                  timeout=10).strip() for setting in ('airplane_mode_on', 'wifi_on'))

    def phone_native(self, selector, name):
        if selector.partition('#')[0] != OFFLINE_TILE_CLASS:
            return self.native(self.phone, [selector], name)
        self.require_offline_usb_phone()
        expected = ('1', '0')
        state = self.phone_offline_settings()
        operator_change = state != expected
        if operator_change:
            print('WAIT: enable airplane mode on the USB phone, turn off Wi-Fi and leave it unlocked.', flush=True)
        try:
            deadline = time.monotonic() + 600
            while state != expected:
                if time.monotonic() >= deadline:
                    raise RuntimeError('The offline verification phone did not reach airplane mode with Wi-Fi off')
                time.sleep(2)
                state = self.phone_offline_settings()
            self.checks['manual_offline'] = {'serial': self.phone, 'transport': 'usb',
                                             'airplane_mode': True, 'wifi': False}
            return self.native(self.phone, [selector], name, extra={'manualOffline': 'true'})
        finally:
            if operator_change:
                print('DONE: restore your previous airplane mode and Wi-Fi settings on the phone.', flush=True)

    def stage_project_fixture(self,serial):
        if not self.project_fixture:
            raise ProjectSelectionRequired('На каком проекте можно протестировать? Укажите --test-project.')
        directory=self.shell(serial,'run-as',PACKAGE,'pwd')
        if not re.fullmatch(r'/data/(?:user/\d+|data)/'+re.escape(PACKAGE),directory):
            raise RuntimeError('Unexpected verification private directory: '+directory)
        self.shell(serial,'run-as',PACKAGE,'mkdir','-p','files')
        payload=self.project_fixture.read_text(encoding='utf-8')
        relative='files/test-project-layers.json'
        # The debug app owns this file. No shared-storage grant or remote shell redirection.
        self.command([self.adb,'-s',serial,'exec-in','run-as',PACKAGE,'tee',relative],input_text=payload)
        expected=hashlib.sha256(payload.encode('utf-8')).hexdigest()
        actual=self.shell(serial,'run-as',PACKAGE,'sha256sum',relative).split()
        if not actual or actual[0]!=expected:
            raise RuntimeError('The verification policy snapshot transfer failed its SHA-256 check')
        return directory+'/'+relative

    def native(self,serial,selectors,name,*,reset=True,extra=None,fatal=None):
        if selectors == ['com.nextgis.mobile.util.DiagnosticsDeliveryTest']:
            return self.diagnostics()
        if selectors == [STRESS_CLASS]:
            seed = self.native(serial, [STRESS_SEED], name+'-seed')
            if seed.get('failed'): return seed
            workload = self.native(serial, [STRESS_CASE], name+'-cold', reset=False)
            if not workload.get('failed'):
                raw = self.shell(serial, 'run-as', PACKAGE, 'cat', 'files/render-stress-evidence.json')
                evidence = json.loads(raw)
                (self.output/(name+'-evidence.json')).write_text(json.dumps(evidence,indent=2),encoding='utf-8')
                device = next(item for item in self.devices.values() if item['serial'] == serial)
                self.checks['render_stress'] = record_stress(ROOT/'build/verification/render-stress.json',
                                                          evidence, device, self.fingerprint)
            return {'passed':seed['passed']+workload['passed'], 'skipped':seed['skipped']+workload['skipped'],
                    'expected_crash':False, 'failed':workload.get('failed',False)}
        if len(selectors)==1 and selectors[0] in self.plan.get('isolated_methods', []):
            passed=[];skipped=[];failures=[];failed=False
            for case in sorted(self.catalog[selectors[0]]):
                try:
                    result=self.native(serial,[case],name+'-'+case.split('#')[1],extra=extra)
                except NativeCheckFailure as error:
                    failures.append(str(error));continue
                failed=failed or result.get('failed',False)
                passed.extend(result['passed']);skipped.extend(result['skipped'])
            if failures: raise NativeCheckFailure('\n'.join(failures))
            return {'passed':passed,'skipped':skipped,'expected_crash':False,'failed':failed}
        if selectors==['com.nextgis.mobile.util.DiagnosticsNoiseTest']:
            return self.diagnostics_noise(serial,name)
        if reset: self.prepare(serial)
        extra=dict(extra or {})
        if any('ProjectLayerSelectionTest' in selector for selector in selectors):
            extra['testProjectFixture']=self.stage_project_fixture(serial)
        expected=expand_selectors(selectors,self.catalog)
        argv=[self.adb,'-s',serial,'shell','am','instrument','-w','-r','-e','verificationPhone',
              'true' if serial==getattr(self,'phone',None) else 'false','-e','class',','.join(selectors)]
        for key,value in (extra or {}).items(): argv+=['-e',key,str(value)]
        native_log=None
        if any(case.startswith(SQLITE_CLASS+'#') for case in expected):
            try:
                factory=getattr(self,'native_log_capture',None)
                if factory is not None:
                    native_log=factory(self.adb,serial,self.output/(name+'-uid-logcat.txt'))
            except Exception as error:
                self.checks.setdefault('native_uid_logs',[]).append(
                    {'case':name,'status':'diagnostic_failure','error_type':type(error).__name__,
                     'error_code':getattr(error,'code','unknown')})
        attempt={'name':name,'device':serial,'selectors':list(selectors),'expected_cases':sorted(expected),
                 'passed':[],'skipped':[],'expected_crash':False,'failed':False,
                 'status':'running','phase':'startup-pending','log':str(self.output/(name+'.log'))}
        self.results.append(attempt)
        self.active_native_attempt=attempt
        self.write_progress()
        command_error=None
        try:
            text=self.command(argv+[RUNNER],name=name,allow_failure=bool(fatal),native_startup_timeout=60)
        except Exception as error:
            command_error=error
        finally:
            self.active_native_attempt=None
            if native_log is not None:
                try: log_state=native_log.stop()
                except Exception as error:
                    log_state={'status':'diagnostic_failure','error_type':type(error).__name__}
                self.checks.setdefault('native_uid_logs',[]).append({'case':name,**log_state})
            # Retain fixed verification files before the next per-method pm clear, even on
            # command/protocol failure. Diagnostic errors never replace the native failure.
            for case in sorted(expected):
                if not case.startswith(SQLITE_CLASS+'#'): continue
                evidence_name=re.sub(r'[^A-Za-z0-9_.-]', '_', name)[:72]+'-'+hashlib.sha256(case.encode()).hexdigest()[:12]
                try:
                    evidence=collect_sqlite_evidence(self.adb,serial,self.output/'sqlite-evidence'/evidence_name,
                                                     case=case,source_fingerprint=self.fingerprint)
                except Exception as error:
                    evidence={'status':'diagnostic_failure','case':case,'source_fingerprint':self.fingerprint,
                              'error':type(error).__name__+': '+str(error)[:640]}
                self.checks.setdefault('native_sqlite_evidence',[]).append(evidence)
                print('SQLite diagnostic artifacts '+evidence['status']+': '+case,flush=True)
        if command_error is not None:
            log=self.output/(name+'.log')
            try: partial=log.read_text(encoding='utf-8',errors='replace')
            except OSError: partial=''
            started=bool(re.search(r'^INSTRUMENTATION_STATUS_CODE:\s*1\s*$',partial,re.M))
            attempt.update({'status':'failed','name':name,'device':serial,'selectors':list(selectors),
                'expected_cases':sorted(expected),'passed':[],'skipped':[],'expected_crash':False,
                'failed':True,'interrupted':True,'phase':'after-test-start' if started else 'before-test-start',
                'error':str(command_error),'error_type':type(command_error).__name__,'log':str(log)})
            self.write_progress()
            # Transport/startup failures abort the environment; the started attempt remains visible.
            raise command_error
        if any(selector.startswith('com.nextgis.mobile.rendering.')
               or selector.startswith('com.nextgis.mobile.util.MaintenanceRemindersNativeTest')
               or selector.startswith('com.nextgis.mobile.reliability.LargeTextLayoutTest') for selector in selectors):
            evidence_name=re.sub(r'[^A-Za-z0-9_.-]', '_', name)
            collect_style_images(self.adb,serial,self.output/'images'/evidence_name)
        native_failure=None
        try: result=parse_instrumentation(text,expected,fatal=fatal)
        except ValueError as error:
            # Preserve evidence before reset/finalization, including cases that die before
            # the workload can produce its own final JSON. This owned emulator contains no
            # customer application; physical capture stays limited to the verification PID.
            if serial.startswith('emulator-'):
                for suffix, args in [('crash', ['logcat','-d','-b','crash','-v','threadtime']),
                                     ('logcat', ['logcat','-d','-v','threadtime','-t','1200']),
                                     ('exit', ['dumpsys','activity','exit-info',PACKAGE]),
                                     ('activity', ['dumpsys','activity','activities']),
                                     ('workload', ['run-as',PACKAGE,'cat','files/render-stress-evidence.json'])]:
                    evidence = self.command([self.adb,'-s',serial,'shell',*args],allow_failure=True,timeout=30)
                    (self.output/(name+'-'+suffix+'.txt')).write_text(evidence,encoding='utf-8')
            message=str(error)+'; see '+str(self.output/(name+'.log'))+'\n'+text[-4000:]
            native_failure=message
            # Each independent check resets only the disposable verification workspace.
            # Preserve the failure and finish the roster; transport/setup failures still abort.
            result={'passed':[],'skipped':[],'expected_crash':False,'failed':True,'error':message}
        attempt.update(status='failed' if result.get('failed') else 'passed',phase='completed',**result)
        self.write_progress()
        if result.get('failed'):
            print('FAIL '+name+'; retained in receipt',flush=True)
        else:
            print('PASS '+name+': '+str(len(result['passed']))+' cases'+(' (expected fatal)' if fatal else ''),flush=True)
        if native_failure is not None and self.args.scope!='full': raise NativeCheckFailure(native_failure)
        return result

    def write_progress(self):
        temporary=self.output/'progress.pending'
        with temporary.open('w',encoding='utf-8') as stream:
            json.dump(self.receipt('running'),stream,ensure_ascii=False,indent=2)
            stream.flush();os.fsync(stream.fileno())
        temporary.replace(self.output/'progress.json')

    def require_native_success(self):
        failures=[result['name'] for result in self.results if result.get('failed')]
        if failures: raise RuntimeError('Native checks failed: '+', '.join(failures))

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
        first_result=len(self.results)
        for density,font,theme,orientation in itertools.product((420,540,617),('1.0','1.3','2.0'),('light','dark'),('portrait','landscape')):
            self.shell(self.emulator,'wm','density',str(density))
            self.shell(self.emulator,'settings','put','system','font_scale',font)
            name=f'chooser-{density}-{font}-{theme}-{orientation}'
            self.native(self.emulator,[case],name,reset=False,extra={'uiTheme':theme,'uiOrientation':orientation})
        self.checks['ui_matrix']=not any(result.get('failed') for result in self.results[first_result:])

    def real_window_matrix(self):
        """Retain the former cloud-only enlarged-window probes on the owned emulator."""
        self.prepare(self.emulator)
        first_result=len(self.results)
        self.shell(self.emulator,'wm','density','620')
        self.shell(self.emulator,'settings','put','system','font_scale','2.0')
        for orientation in ('portrait','landscape'):
            for style in ('native','compat'):
                self.native(self.emulator,[
                    'com.nextgis.mobile.reliability.LargeTextLayoutTest#realMapCloseAndDialogActionsStayReachable'],
                    f'real-window-{orientation}-{style}',reset=False,
                    extra={'uiOrientation':orientation,'dialogStyle':style})
            self.native(self.emulator,[
                'com.nextgis.mobile.reliability.LargeTextLayoutTest#realSettingsAndSecondaryScreensRetainCompleteLabels'],
                f'real-window-{orientation}-settings',reset=False,extra={'uiOrientation':orientation})
        self.checks['real_window_matrix']=not any(result.get('failed') for result in self.results[first_result:])

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
            for killed, replay in [('hardKillWithoutSdkEnvelopeWhileOffline', 'previousHardKillReachesDurableFallbackWithOriginalIdentity'),
                                   ('nativeAbortWhileOffline', 'previousNativeAbortIncludesTheActualOsBacktrace')]:
                case = name+'#'+killed
                self.native(serial,[case],'diagnostic-'+killed,reset=False,fatal=case)
                self.native(serial,[name+'#'+replay],'diagnostic-'+replay,reset=False)
            self.native(serial,[name+'#reportsSurviveOfflineRestart'],'diagnostic-offline-restart',reset=False)
            def reconnect():
                time.sleep(5); self.shell(serial,'svc','wifi','enable')
            reconnect_thread=threading.Thread(target=reconnect); reconnect_thread.start()
            try: self.native(serial,[name+'#deliverWhenNetworkReturns'],'diagnostic-reconnect',reset=False)
            finally: reconnect_thread.join()
            self.check_diagnostic_receiver(captured)
        finally:
            self.command([self.adb,'-s',serial,'reverse','--remove','tcp:18539'],allow_failure=True)
            server.shutdown(); server.server_close()
            self.shell(serial,'svc','wifi','enable')
        self.native(serial,[name+'#workerRetriesTemporaryServerFailureWithoutNetworkChange'],'diagnostic-http-retry')

    def check_diagnostic_receiver(self,captured):
        import gzip
        error=None
        try:
            if len(captured)<2: raise ValueError('Local receiver did not accept both persisted diagnostic reports')
            delivered=b'\n'.join(gzip.decompress(body) for body in captured)
            for required in (b'process_exit_fallback', b'verification-before-kill', b'verification-before-native-abort', b'native_frames'):
                if required not in delivered: raise ValueError('OS fallback did not reach the local receiver: '+str(required))
        except (ValueError,OSError,EOFError) as failure:
            error=str(failure)
        self.checks['process_exit_delivery']=error is None
        report={'name':'diagnostic-receiver','device':self.emulator,'passed':[],'skipped':[],
                'host_check':True,'failed':error is not None,'received_reports':len(captured)}
        if error is not None: report['error']=error
        (self.output/'diagnostic-receiver.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        self.results.append(report)
        if error is not None:
            if self.args.scope!='full': raise NativeCheckFailure(error)
            print('FAIL diagnostic-receiver; retained in receipt, continuing independent Full checks',flush=True)

    def receipt(self,status):
        # Native stress owns its current evidence; an unrelated disk receipt must not replace it.
        return {'schema_version':2,'scope':self.args.scope,'status':status,
                'created_at':datetime.now(timezone.utc).isoformat(),'source_fingerprint':self.fingerprint,
                'build_basis':getattr(self,'build_basis',None),
                'devices':self.devices,'checks':self.checks,'runs':self.results,
                'passed_cases':sorted({case for result in self.results for case in result['passed']})}

    def restore(self):
        self.restoring=True
        process=getattr(self,'emulator_process',None)
        guarded=process is not None and isinstance(process,WindowsEmulatorProcess)
        cleanup={'owned_emulator':self.owned_emulator,'settings_restored':None,
                 'owned_console_closed':None,'owned_process_exited':None}
        watchdog=None
        if guarded:
            watchdog=threading.Timer(45,process.terminate)
            watchdog.daemon=True;watchdog.start()
        try:
            for device in self.devices.values():
                for package in (PACKAGE,PACKAGE+'.test'):
                    self.command([self.adb,'-s',device['serial'],'shell','am','force-stop',package],
                                 allow_failure=True,timeout=8)
            angle_error=None
            if getattr(self,'angle_session',None) is not None:
                try:
                    self.angle_session.close()
                    cleanup['angle_restored']=True
                except Exception as error:
                    angle_error=error;cleanup['angle_restore_error']=str(error)
            if not self.saved_emulator:
                if angle_error:raise angle_error
                return
            serial=self.emulator
            for key in ('window_animation_scale','transition_animation_scale','animator_duration_scale'):
                if key not in self.saved_emulator:continue
                value=self.saved_emulator[key]
                self.shell(serial,'settings','delete','global',key) if value=='null' else self.shell(serial,'settings','put','global',key,value)
            if 'font' in self.saved_emulator:self.shell(serial,'settings','put','system','font_scale',self.saved_emulator['font'])
            if 'density' in self.saved_emulator:
                density=re.search(r'Override density:\s*(\d+)',self.saved_emulator['density'])
                self.shell(serial,'wm','density',density.group(1) if density else 'reset')
            for key,service in [('wifi','wifi'),('data','data')]:
                if key in self.saved_emulator:self.shell(serial,'svc',service,'enable' if self.saved_emulator[key]=='1' else 'disable')
            if 'location' in self.saved_emulator:self.shell(serial,'cmd','location','set-location-enabled',self.saved_emulator['location'])
            if 'screen_timeout' in self.saved_emulator:
                value=self.saved_emulator['screen_timeout']
                self.shell(serial,'settings','delete','system','screen_off_timeout') if value=='null' else self.shell(serial,'settings','put','system','screen_off_timeout',value)
            cleanup['settings_restored']=True
            if angle_error:raise angle_error
        except Exception as error:
            cleanup['restore_error']=str(error)
            raise
        finally:
            try:
                if self.owned_emulator:
                    port=int(self.owned_emulator.removeprefix('emulator-'))
                    owns_console=not guarded or process.owns_console(port)
                    cleanup['owned_console_verified']=owns_console
                    if owns_console:
                        self.command([self.adb,'-s',self.owned_emulator,'emu','kill'],allow_failure=True,timeout=10)
                    if process is not None:
                        cleanup['owned_process_exited']=False
                        forced_shutdown=False
                        try:process.wait(timeout=26)
                        except subprocess.TimeoutExpired:
                            if not guarded:raise
                            process.terminate();process.wait(timeout=10)
                            forced_shutdown=True
                        cleanup['owned_process_exited']=True
                    with socket.socket() as probe:
                        probe.settimeout(.2)
                        cleanup['owned_console_closed']=probe.connect_ex(('127.0.0.1',port))!=0
                    if guarded:
                        cleanup['owned_job_remaining']=process.active()
                        cleanup['forced_stop']=process.forced
                        cleanup['resource_guard_error']=process.resource_error
                        cleanup['last_host_resources']=process.resource_stats
                        if process.resource_error:raise RuntimeError(process.resource_error)
                        if forced_shutdown:raise RuntimeError('The emulator failed bounded shutdown; its entire owned job was stopped')
                    if owns_console and not cleanup['owned_console_closed']:
                        raise RuntimeError('Owned emulator console remained open: '+self.owned_emulator)
            finally:
                try:
                    if guarded:process.close()
                finally:
                    if watchdog:watchdog.cancel()
                    self.restoring=False
                    (self.output/'cleanup.json').write_text(json.dumps(cleanup,indent=2),encoding='utf-8')

    def run(self):
        if self.args.scope=='check-publication':
            receipt=json.loads((ROOT/'build/verification/full-passed.json').read_text(encoding='utf-8'))
            check_publication_receipt(receipt,self.fingerprint,self.required,required_phone=self.required_phone)
            self.discover_verification_apks()
            verify_pair(receipt['build_basis'],self.fingerprint,build_recipe(ROOT),self.apk_pair())
            print('PASS: full phone/tablet/emulator acceptance matches the current source',flush=True)
            return
        if self.args.scope=='list':
            print(json.dumps(self.plan,ensure_ascii=False,indent=2)); return
        primary_error=None
        try:
            if self.args.scope=='full':
                # A failed/interrupted new acceptance must not leave a prior green gate behind.
                (ROOT/'build/verification/full-passed.json').unlink(missing_ok=True)
            # Reject stale/missing skip-build provenance before the first ADB/device action.
            self.build(); self.select_devices()
            if hasattr(self,'emulator'):
                self.install(self.emulator)
                self.configure_emulator_graphics()
            if self.args.scope=='related' and hasattr(self,'phone'):
                self.install(self.phone)
            if self.args.scope=='full':
                self.install(self.phone)
                for test in self.plan['emulator']:
                    self.native(self.emulator,[test],'emulator-'+test.rsplit('.',1)[1])
                self.grant_and_deny(self.emulator)
                self.diagnostics(); self.ui_matrix(); self.real_window_matrix()
                # Finish independent work before requiring a manually unlocked phone.
                for test in self.plan['phone']:
                    self.phone_native(test,'phone-'+test.rsplit('.',1)[1])
                self.require_native_success()
                check_publication_receipt(self.receipt('passed'),self.fingerprint,self.required,
                                          required_phone=self.required_phone)
            else:
                related_failures=[]
                selected=set()
                for area in self.args.area or ['layers']:
                    if area not in self.plan['areas']: raise ValueError('Unknown area: '+area)
                    selected.update(self.plan['areas'][area])
                if hasattr(self,'emulator'):
                    for index,test in enumerate(sorted(selected)):
                        try: self.native(self.emulator,[test],f'related-{index}-'+test.replace('#','-').rsplit('.',1)[1])
                        except NativeCheckFailure as error: related_failures.append(test+': '+str(error))
                    if any('StakeoutForegroundServiceTest' in test for test in selected): self.grant_and_deny(self.emulator)
                    if 'layers' in (self.args.area or ['layers']): self.ui_matrix()
                    if 'real-windows' in (self.args.area or []): self.real_window_matrix()
                phone_cases = sorted(getattr(self,'related_phone_cases',[]),
                                     key=lambda selector: selector.partition('#')[0] == OFFLINE_TILE_CLASS)
                for index,test in enumerate(phone_cases):
                    try: self.phone_native(test,f'phone-related-{index}-'+test.split('#')[-1])
                    except NativeCheckFailure as error: related_failures.append(test+': '+str(error))
                if related_failures: raise NativeCheckFailure('\n'.join(related_failures))
            self.assert_build_pair()
        except Exception as error:
            primary_error=error
            receipt=self.receipt('failed'); receipt['error']=str(error)
            (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
            raise
        finally:
            try: self.restore()
            except Exception as error:
                receipt=self.receipt('failed')
                receipt['error']=str(primary_error) if primary_error is not None else 'Cleanup failed: '+str(error)
                receipt['cleanup_error']=str(error)
                (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
                if primary_error is None: raise
        # A run becomes successful only after its owned resources have been finalized.
        try: self.assert_build_pair()
        except Exception as error:
            receipt=self.receipt('failed'); receipt['error']=str(error)
            (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
            raise
        receipt=self.receipt('passed')
        (self.output/'result.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
        if self.args.scope=='full':
            (ROOT/'build/verification/full-passed.json').write_text(json.dumps(receipt,ensure_ascii=False,indent=2),encoding='utf-8')
        print('PASS '+self.args.scope+': '+str(len(receipt['passed_cases']))+' distinct native cases; '+str(self.output),flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scope',choices=('related','full','check-publication','list'),default='related')
    parser.add_argument('--area',action='append')
    parser.add_argument('--phone-serial'); parser.add_argument('--emulator-serial')
    parser.add_argument('--phone-only',action='store_true',help='Related stationary verification checks only; explicit phone serial required')
    parser.add_argument('--allow-emulator',action='store_true',help='Explicitly approved software-only emulator execution after reviewing host safety; never use host GPU')
    parser.add_argument('--emulator-graphics',choices=('auto','native','angle','guest-angle'),default='auto',
                        help='GuestAngle uses system OpenGL ES over verified CPU Vulkan; auto uses the verified local profile when configured')
    parser.add_argument('--emulator-runtime',help='Directory containing the validated emulator executable; SDK and ADB remain separate')
    parser.add_argument('--test-project',help='Explicitly authorized Collector ID, name or workspace directory; default 1403')
    parser.add_argument('--test-project-fixture',help='Explicitly authorized metadata-only policy snapshot with a matching --test-project ID')
    parser.add_argument('--avd'); parser.add_argument('--sdk'); parser.add_argument('--output')
    parser.add_argument('--skip-build',action='store_true')
    args=parser.parse_args()
    try: Verification(args).run()
    except Exception as error:
        print('FAIL: '+str(error),file=sys.stderr,flush=True); return 1
    return 0

if __name__=='__main__': sys.exit(main())
