"""Fail closed before a graphics workload can touch a host GPU."""
import ctypes
from ctypes import wintypes
import os
import json
from pathlib import Path
import re
import socket
import struct
import subprocess
import threading
import time
from uuid import uuid4

SOFTWARE_GPU = 'swiftshader_indirect'  # Installed Emulator35.6.11; no hardware fallback.
GIB = 1024 ** 3


def require_software_graphics(surface_flinger: str) -> str:
    lines = re.findall(r'^\s*(GLES:.*)$', surface_flinger, re.MULTILINE)
    if len(lines) != 1 or not re.search(r'SwiftShader|llvmpipe|lavapipe', lines[0], re.IGNORECASE):
        raise ValueError('Emulator graphics must report an actual software renderer; host/unknown GPU is blocked')
    return lines[0].strip()


def require_whpx(acceleration: str) -> None:
    if not re.search(r'WHPX[^\r\n]*is installed and usable', acceleration):
        raise ValueError('Windows emulator execution requires usable WHPX; AEHD and software CPU fallback are blocked')


def windows_resources() -> dict:
    class PerformanceInfo(ctypes.Structure):
        _fields_ = [('cb', wintypes.DWORD)] + [(name, ctypes.c_size_t) for name in (
            'CommitTotal', 'CommitLimit', 'CommitPeak', 'PhysicalTotal', 'PhysicalAvailable',
            'SystemCache', 'KernelTotal', 'KernelPaged', 'KernelNonpaged', 'PageSize',
        )] + [(name, wintypes.DWORD) for name in ('HandleCount', 'ProcessCount', 'ThreadCount')]
    data = PerformanceInfo()
    function = ctypes.WinDLL('psapi', use_last_error=True).GetPerformanceInfo
    function.argtypes = (ctypes.POINTER(PerformanceInfo), wintypes.DWORD)
    function.restype = wintypes.BOOL
    if not function(ctypes.byref(data), ctypes.sizeof(data)):
        raise ctypes.WinError(ctypes.get_last_error())
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    create = kernel.CreateMemoryResourceNotification
    create.argtypes = (ctypes.c_int,); create.restype = wintypes.HANDLE
    query = kernel.QueryMemoryResourceNotification
    query.argtypes = (wintypes.HANDLE, ctypes.POINTER(wintypes.BOOL)); query.restype = wintypes.BOOL
    close = kernel.CloseHandle
    close.argtypes = (wintypes.HANDLE,); close.restype = wintypes.BOOL
    notification = create(0)  # LowMemoryResourceNotification, not an allocation or a system change.
    if not notification:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        low = wintypes.BOOL()
        if not query(notification, ctypes.byref(low)):
            raise ctypes.WinError(ctypes.get_last_error())
    finally:
        close(notification)
    return {'available_ram_bytes': data.PhysicalAvailable * data.PageSize,
            'commit_bytes': data.CommitTotal * data.PageSize,
            'commit_limit_bytes': data.CommitLimit * data.PageSize, 'low_memory': bool(low.value)}


def require_windows_resources(stats: dict, minimum_gib: float = 4.5) -> dict:
    if (stats['available_ram_bytes'] < minimum_gib * GIB or stats.get('low_memory', True)
            or stats['commit_limit_bytes'] <= 0
            or stats['commit_bytes'] / stats['commit_limit_bytes'] > .75):
        raise ValueError('The bounded emulator requires '+str(minimum_gib)+' GiB available Windows RAM, no OS low-memory signal and commit at most 75%')
    return stats


def require_memory_limit(startup_log: str, maximum_mib: int = 2048) -> None:
    # This SDK silently raises an unsupported -memory value. Check before guest boot/load.
    values = re.findall(r'Increasing RAM size to\s+(\d+)MB', startup_log)
    if any(int(value) > maximum_mib for value in values):
        raise ValueError('The emulator silently increased guest RAM beyond the admitted limit')


def require_cpu_vulkan(surface: str, document: dict, startup: str) -> dict:
    if 'RE Vulkan (Ganesh)' not in surface or not re.search(r'^Vulkan device initialized: 1\s*$',surface,re.M):
        raise ValueError('The Android compositor did not initialize its Vulkan renderer')
    if not isinstance(document,dict):raise ValueError('Guest Vulkan diagnostic must be an object')
    devices=document.get('devices',[])
    if not isinstance(devices,list) or len(devices)!=1 or not isinstance(devices[0],dict):
        raise ValueError('Guest Vulkan must expose exactly one CPU device')
    properties=devices[0].get('properties',{})
    if not isinstance(properties,dict):raise ValueError('Malformed Vulkan properties')
    name=properties.get('deviceName','')
    if not isinstance(name,str) or properties.get('deviceType')!=4 or 'llvmpipe' not in name.lower():
        raise ValueError('Guest Vulkan must report CPU llvmpipe, never hardware or an unknown device')
    selected=re.findall(r'Selecting Vulkan device:\s*(.+)',startup)
    if not selected or any('llvmpipe' not in value.lower() for value in selected):
        raise ValueError('The actual host Vulkan device must be CPU llvmpipe')
    return {'backend':'Skia Vulkan Ganesh','device_name':name,'device_type':4,'host_selected':selected}


def launch_environment(runtime: Path, guest_angle: bool, *, avd_home=None, emulator_home=None) -> dict:
    env=os.environ.copy()
    if guest_angle:
        icd=runtime/'lib64/vulkan/lvp_icd.json'
        if not icd.is_file():raise ValueError('The selected emulator has no CPU Lavapipe ICD')
        env.update(ANDROID_EMU_VK_ICD='lavapipe',ANDROID_EMU_VK_RUNTIME='LOCAL',
                   VK_DRIVER_FILES=str(icd),VK_ICD_FILENAMES=str(icd),LP_NUM_THREADS='2')
    if avd_home:env['ANDROID_AVD_HOME']=str(avd_home)
    if emulator_home:env['ANDROID_EMULATOR_HOME']=str(emulator_home)
    return env


def resource_stop_reason(stats):
    if stats.get('low_memory',True):return 'Windows signalled low memory'
    if stats['available_ram_bytes']<1.5*GIB:return 'Available Windows RAM fell below 1.5 GiB'
    if stats['commit_limit_bytes']<=0 or stats['commit_bytes']/stats['commit_limit_bytes']>.82:
        return 'Windows commit exceeded 82%'
    return None


class WindowsEmulatorProcess:
    """Own the complete SDK process tree before its suspended launcher can create children."""
    def __init__(self,argv,stdout,env,cwd):
        try:
            import msvcrt,win32api,win32con,win32event,win32job,win32process
        except ImportError as error:
            raise ValueError('Guarded Windows emulator execution requires pywin32; no process was launched') from error
        self.api,self.event,self.jobs,self.processes=win32api,win32event,win32job,win32process
        self.args=list(map(str,argv));self.lock=threading.RLock();self.closed=False;self.forced=False
        self.resource_error=None;self.resource_stats={};self.monitor_stop=threading.Event()
        self.job=win32job.CreateJobObject(None,'Local\\LisaEmulator-'+uuid4().hex)
        limits=win32job.QueryInformationJobObject(self.job,win32job.JobObjectExtendedLimitInformation)
        limits['BasicLimitInformation']['LimitFlags']|=win32job.JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        win32job.SetInformationJobObject(self.job,win32job.JobObjectExtendedLimitInformation,limits)
        self.resource_log=Path(stdout.name).with_name('emulator-host-resources.jsonl')
        self.process=None;thread=None
        try:
            with open(os.devnull,'rb',buffering=0) as stdin:
                handles=[msvcrt.get_osfhandle(file.fileno()) for file in (stdin,stdout)]
                inherited=[os.get_handle_inheritable(handle) for handle in handles]
                try:
                    for handle in handles:os.set_handle_inheritable(handle,True)
                    startup=win32process.STARTUPINFO();startup.dwFlags|=win32con.STARTF_USESTDHANDLES
                    startup.hStdInput=handles[0];startup.hStdOutput=startup.hStdError=handles[1]
                    flags=win32con.CREATE_SUSPENDED|subprocess.CREATE_NO_WINDOW|subprocess.BELOW_NORMAL_PRIORITY_CLASS
                    self.process,thread,self.pid,_=win32process.CreateProcess(self.args[0],subprocess.list2cmdline(self.args),
                        None,None,True,flags,env,str(cwd),startup)
                finally:
                    for handle,value in zip(handles,inherited):os.set_handle_inheritable(handle,value)
            win32job.AssignProcessToJobObject(self.job,self.process)
            win32process.ResumeThread(thread)
            self.monitor=threading.Thread(target=self.watch_resources,name='owned-emulator-resource-guard',daemon=True)
            self.monitor.start()
        except Exception:
            if self.process is not None:win32api.TerminateProcess(self.process,1);self.process.Close()
            self.job.Close();self.closed=True
            raise
        finally:
            if thread is not None:thread.Close()

    def watch_resources(self):
        streak=0
        while not self.monitor_stop.wait(2):
            try:
                if not self.active():return
                stats=windows_resources()
                self.resource_stats=stats
                with self.resource_log.open('a',encoding='utf-8') as evidence:
                    evidence.write(json.dumps(dict(timestamp=time.time(),**stats))+'\n');evidence.flush();os.fsync(evidence.fileno())
                reason=resource_stop_reason(stats)
                streak=streak+1 if reason else 0
                if reason and (stats.get('low_memory',True) or streak>=3):
                    self.resource_error=reason
                    self.terminate()
                    return
            except Exception as error:
                self.resource_error='Resource guard failed: '+str(error)
                try:self.terminate()
                except Exception:pass
                return

    def active(self):
        if self.closed:return 0
        return self.jobs.QueryInformationJobObject(self.job,self.jobs.JobObjectBasicAccountingInformation)['ActiveProcesses']

    def poll(self):
        if self.event.WaitForSingleObject(self.process,0)==self.event.WAIT_OBJECT_0:
            return self.processes.GetExitCodeProcess(self.process)
        return None

    def wait(self,timeout):
        until=time.monotonic()+timeout
        while time.monotonic()<until:
            if self.poll() is not None and self.active()==0:return self.poll()
            time.sleep(.1)
        raise subprocess.TimeoutExpired(self.args,timeout)

    def terminate(self):
        with self.lock:
            if self.closed:return True
            if self.active():self.forced=True;self.jobs.TerminateJobObject(self.job,1)
            until=time.monotonic()+10
            while self.active() and time.monotonic()<until:time.sleep(.1)
            if self.active():raise RuntimeError('The owned SDK job still contains processes')
            return True

    def owns_console(self,port):
        """Do not send ADB emu kill to a listener outside this exact Windows job."""
        with self.lock:
            if self.closed:return False
            function=ctypes.WinDLL('iphlpapi',use_last_error=True).GetExtendedTcpTable
            function.argtypes=(ctypes.c_void_p,ctypes.POINTER(wintypes.DWORD),wintypes.BOOL,wintypes.ULONG,ctypes.c_int,wintypes.ULONG)
            function.restype=wintypes.DWORD
            size=wintypes.DWORD()
            if function(None,ctypes.byref(size),False,2,3,0) not in (0,122) or not 4<=size.value<=16*1024**2:return False
            data=ctypes.create_string_buffer(size.value)
            if function(data,ctypes.byref(size),False,2,3,0):return False
            count=struct.unpack_from('<I',data)[0]
            if 4+count*24>size.value:return False
            listeners=[]
            for index in range(count):
                _,address,number,_,_,pid=struct.unpack_from('<6I',data,4+index*24)
                if address in (0,0x0100007f) and socket.ntohs(number&0xffff)==port:listeners.append(pid)
            if len(listeners)!=1:return False
            handle=None
            try:
                handle=self.api.OpenProcess(0x1000,False,listeners[0])
                return self.jobs.IsProcessInJob(handle,self.job)
            except Exception:return False
            finally:
                if handle is not None:handle.Close()

    def close(self):
        self.monitor_stop.set()
        monitor=getattr(self,'monitor',None)
        if monitor is not None and monitor is not threading.current_thread():monitor.join(timeout=12)
        with self.lock:
            if self.closed:return
            try:self.terminate()
            finally:self.job.Close();self.process.Close();self.closed=True
