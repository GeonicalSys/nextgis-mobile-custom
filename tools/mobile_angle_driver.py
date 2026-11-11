"""Build one independent GLES probe and verify the actual system ANGLE software driver."""
from __future__ import annotations
import hashlib,json,os,re,shlex,shutil,subprocess,time,zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
PROBE_PACKAGE='org.geonical.emulator.glesprobe'

def sha256(data):return hashlib.sha256(data).hexdigest()
def file_sha256(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def require_runtime_files(runtime: Path,contract: dict) -> dict:
    expected=contract.get('emulator',{}).get('files_sha256',{})
    required={'lib64/vulkan/libvulkan_lvp.dll','lib64/vulkan/lvp_icd.json','lib64/vulkan/vulkan-1.dll'}
    if set(expected)!=required:raise ValueError('The CPU rendering stack has no complete pinned identity')
    actual={name:file_sha256(runtime/name) for name in sorted(required)}
    if actual!=expected:raise ValueError('The emulator CPU rendering stack changed since verification')
    return actual

def run_tool(argv: list, cwd: Path) -> None:
    result = subprocess.run(list(map(str, argv)), cwd=cwd, capture_output=True, timeout=120,
                            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    if result.returncode:
        raise RuntimeError("GLES probe build failed: " +
                           result.stderr.decode("utf-8", errors="replace")[-1600:])


def toolchain(sdk: Path) -> tuple[Path, Path, Path]:
    versions = [path for path in (sdk/"build-tools").iterdir()
                if path.is_dir() and re.fullmatch(r"\d+\.\d+\.\d+", path.name)]
    if not versions:
        raise ValueError("Install Android SDK build tools before preparing the GLES probe")
    tools = max(versions, key=lambda path: tuple(map(int, path.name.split("."))))
    suffix = ".exe" if os.name == "nt" else ""
    home = os.environ.get("JAVA_HOME")
    javac = shutil.which("javac")
    candidates = [Path(home)/"bin"] if home else [
        *([Path(javac).resolve().parent] if javac else []),
        Path("C:/Program Files/Android/Android Studio/jbr/bin")]
    jdk = next((path for path in candidates if (path/("java"+suffix)).is_file()
                and (path/("javac"+suffix)).is_file()), candidates[0])
    jar = sdk/"platforms/android-36/android.jar"
    for path in (jdk/("java"+suffix), jdk/("javac"+suffix), tools/("aapt2"+suffix),
                 tools/("zipalign"+suffix), tools/"lib/d8.jar", tools/"lib/apksigner.jar", jar):
        if not path.is_file():
            raise ValueError("Required the GLES probe fixture build tool is missing: " + str(path))
    return tools, jdk, jar


def cached_probe(root: Path,sdk: Path,cache: Path) -> dict:
    tools,jdk,android_jar=toolchain(sdk)
    suffix='.exe' if os.name=='nt' else ''
    key=Path.home()/'.android/debug.keystore'
    if not key.is_file():raise ValueError('Build the verification APK once to create the normal debug signing key')
    fixture=root/'tools/fixtures/emulator-gles'
    inputs={'builder':file_sha256(Path(__file__)),
            'fixture':{path.name:file_sha256(path) for path in sorted(fixture.iterdir()) if path.is_file()},
            'toolchain':{str(path):file_sha256(path) for path in (jdk/('java'+suffix),jdk/('javac'+suffix),jdk.parent/'release',
                tools/('aapt2'+suffix),tools/('zipalign'+suffix),tools/'lib/d8.jar',tools/'lib/apksigner.jar',android_jar)},
            'signing_key_sha256':file_sha256(key)}
    identity=sha256(json.dumps(inputs,sort_keys=True).encode())
    cache=cache.resolve();cache.mkdir(parents=True,exist_ok=True)
    destination=cache/identity;receipt_path=destination/'receipt.json'
    def read_cached():
        receipt=json.loads(receipt_path.read_text(encoding='utf-8'))
        apk=destination/'probe.apk'
        if receipt.get('recipe_sha256')!=identity or apk.is_symlink() or file_sha256(apk)!=receipt.get('probe_sha256'):
            raise ValueError('Cached GLES probe identity differs; the existing entry was preserved')
        return dict(receipt,probe=apk)
    if destination.exists():return read_cached()
    with TemporaryDirectory(prefix='gles-probe-',dir=cache) as temporary:
        work=Path(temporary);classes=work/'classes';classes.mkdir()
        java=[jdk/('java'+suffix),'-Xmx128m','-XX:ActiveProcessorCount=2','-Djava.io.tmpdir='+str(work)]
        run_tool([jdk/('javac'+suffix),'-J-Xmx128m','-J-XX:ActiveProcessorCount=2','--release','11',
                  '-cp',android_jar,'-d',classes,*sorted(fixture.glob('*.java'))],work)
        run_tool([*java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','29','--lib',android_jar,
                  '--output',work/'classes.zip',*sorted(classes.rglob('*.class'))],work)
        run_tool([tools/('aapt2'+suffix),'link','-I',android_jar,'--manifest',fixture/'AndroidManifest.xml',
                  '-o',work/'unsigned.apk'],work)
        with zipfile.ZipFile(work/'classes.zip') as original,zipfile.ZipFile(work/'unsigned.apk','a') as target:
            target.writestr('classes.dex',original.read('classes.dex'),compress_type=zipfile.ZIP_DEFLATED)
        run_tool([tools/('zipalign'+suffix),'-p','4',work/'unsigned.apk',work/'aligned.apk'],work)
        published=work/'published';published.mkdir()
        run_tool([*java,'-jar',tools/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','androiddebugkey',
                  '--ks-pass','pass:android','--key-pass','pass:android','--out',published/'probe.apk',work/'aligned.apk'],work)
        run_tool([*java,'-jar',tools/'lib/apksigner.jar','verify',published/'probe.apk'],work)
        receipt={'recipe_sha256':identity,'inputs':inputs,'probe_sha256':file_sha256(published/'probe.apk')}
        (published/'receipt.json').write_text(json.dumps(receipt,indent=2),encoding='utf-8')
        try:published.rename(destination)
        except OSError:
            if not receipt_path.is_file():raise
    return read_cached()


def require_guest_angle_proof(proof: dict) -> dict:
    if not isinstance(proof,dict):raise ValueError('The GLES proof must be an object')
    renderer=proof.get('renderer','')
    if not isinstance(renderer,str) or 'ANGLE' not in renderer or 'llvmpipe' not in renderer.lower():
        raise ValueError('The actual GLES context must use system ANGLE over CPU llvmpipe')
    libraries=proof.get('loaded_libraries',[])
    if not isinstance(libraries,list) or any(not isinstance(path,str) for path in libraries):raise ValueError('Malformed loaded-library proof')
    paths=[path for path in libraries if path.endswith('libGLESv2_angle.so')]
    if not paths or any(not path.startswith(('/system/','/vendor/','/apex/')) or '/..' in path for path in paths):
        raise ValueError('The actual loaded GLES library is not system ANGLE')
    for name,expected in (('constant_control',[26,128,230,255]),('fragment_first',[51,77,179,255]),('shared_first',[51,77,179,255])):
        actual=proof.get(name,{}).get('rgba',[])
        if len(actual)!=4 or any(type(value) is not int or not 0<=value<=255 for value in actual) or any(abs(a-b)>1 for a,b in zip(actual,expected)):
            raise ValueError('The independent GLSL uniform-block oracle failed: '+name)
    return proof


class SystemAngleProbe:
    """Use only the owned read-only emulator; do not change per-app/global driver selection."""
    def __init__(self,command,adb,serial,output,inputs):
        if not re.fullmatch(r'emulator-\d+',serial):raise ValueError('The GLES diagnostic is restricted to the owned emulator')
        self.command,self.adb,self.serial,self.output,self.inputs=command,adb,serial,Path(output),inputs
        self.installed=False

    def shell(self,*args,allow_failure=False):
        return self.command([self.adb,'-s',self.serial,'shell','-T',shlex.join(map(str,args))],
                            timeout=15,allow_failure=allow_failure).strip()

    def install(self):
        apk=Path(self.inputs['probe']);expected=self.inputs['probe_sha256']
        if file_sha256(apk)!=expected:raise ValueError('The prepared GLES probe changed before installation')
        current=self.shell('pm','path',PROBE_PACKAGE,allow_failure=True)
        if current:
            lines=current.splitlines()
            if len(lines)!=1 or not lines[0].startswith('package:/data/app/'):
                raise ValueError('An existing diagnostic package has an unknown location')
            if self.shell('sha256sum',lines[0].removeprefix('package:')).split()[0]!=expected:
                raise ValueError('An existing diagnostic package has a different identity; it was preserved')
            return
        self.installed=True
        if 'Success' not in self.command([self.adb,'-s',self.serial,'install','-t',apk],timeout=120):
            raise RuntimeError('Could not install the GLES diagnostic')

    def verify(self):
        try:
            properties={key:self.shell('getprop',key) for key in ('ro.hardware.egl','debug.hwui.renderer','debug.renderengine.backend')}
            if properties['ro.hardware.egl']!='angle' or any(not properties[key].startswith('skiavk') for key in ('debug.hwui.renderer','debug.renderengine.backend')):
                raise ValueError('The owned guest did not activate the supported GuestAngle/SkiaVK boot configuration')
            if self.shell('settings','get','global','angle_debug_package') not in ('','null'):
                raise ValueError('A separate debug ANGLE package overrides this system-driver profile')
            routing={key:self.shell('settings','get','global',key) for key in
                     ('angle_gl_driver_selection_pkgs','angle_gl_driver_selection_values','angle_gl_driver_all_angle')}
            if any(value not in ('','null') for key,value in routing.items() if key!='angle_gl_driver_all_angle') or routing['angle_gl_driver_all_angle'] not in ('','null','0'):
                raise ValueError('Per-app/global ANGLE routing overrides this system-driver baseline')
            self.install()
            self.shell('am','force-stop',PROBE_PACKAGE)
            self.shell('run-as',PROBE_PACKAGE,'rm','-f','files/proof.json')
            self.shell('am','start','-W','-n',PROBE_PACKAGE+'/.ProbeActivity')
            until=time.monotonic()+35
            while time.monotonic()<until:
                raw=self.shell('run-as',PROBE_PACKAGE,'cat','files/proof.json',allow_failure=True)
                for line in raw.splitlines():
                    if not line.startswith('{'):continue
                    proof=json.loads(line)
                    (self.output/'emulator-gles-raw.json').write_text(json.dumps(proof,indent=2),encoding='utf-8')
                    require_guest_angle_proof(proof)
                    paths=[path for path in proof['loaded_libraries'] if path.endswith('libGLESv2_angle.so')]
                    hashes={path:self.shell('sha256sum',path).split()[0] for path in paths}
                    if any(not re.fullmatch('[0-9a-f]{64}',value) for value in hashes.values()):raise ValueError('No valid loaded system-library SHA-256')
                    proof.update(system_library_sha256=hashes,boot_properties=properties,driver_routing=routing,probe_apk_sha256=self.inputs['probe_sha256'])
                    (self.output/'emulator-gles-proof.json').write_text(json.dumps(proof,indent=2),encoding='utf-8')
                    return proof
                time.sleep(.5)
            raise RuntimeError('The GLES probe did not return its independent oracle result')
        except Exception:
            for name,args in (('crash',['logcat','-d','-b','crash','-v','threadtime']),('exit',['dumpsys','activity','exit-info',PROBE_PACKAGE])):
                try:
                    data=self.command([self.adb,'-s',self.serial,'shell',*args],timeout=8,allow_failure=True)
                    (self.output/('probe-failure-'+name+'.txt')).write_text(data,encoding='utf-8')
                except Exception:pass
            raise

    def close(self):
        if not self.installed:return
        self.shell('am','force-stop',PROBE_PACKAGE)
        answer=self.command([self.adb,'-s',self.serial,'uninstall',PROBE_PACKAGE],timeout=15,allow_failure=True)
        if 'Success' not in answer and self.shell('pm','path',PROBE_PACKAGE,allow_failure=True):
            raise RuntimeError('The diagnostic package was not removed')
        self.installed=False
