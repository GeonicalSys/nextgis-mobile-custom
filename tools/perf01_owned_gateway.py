"""Loopback-only NGW measurement gateway for an explicitly owned synthetic fixture.

No credentials enter the device, report, URL or request journal. Application-body
bytes are counted; TLS/TCP overhead and direct-device network latency are not.
Only upload sessions returned by this gateway's own creation requests are admitted.
"""
from __future__ import annotations
import argparse, hashlib, json, os, re, secrets, socket, threading, time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, urljoin
import requests

class OwnedGateway:
    def __init__(self, fixture, output, *, allow_writes=False, upstream=requests.request):
        self.fixture=fixture; self.output=Path(output); self.allow_writes=allow_writes; self.upstream=upstream
        self.layer=int(fixture['layer_id']); self.group=int(fixture['group_id']); self.base=fixture['base']
        if not re.fullmatch(r'mobile-night-[0-9]{8}-[0-9a-f]{8}',fixture['owner']) or self.base!='https://azimuth.nextgis.com':
            raise ValueError('Unreviewed fixture identity')
        self.auth=(os.environ['LISA_NIGHT_NGW_LOGIN'],os.environ['LISA_NIGHT_NGW_PASSWORD'])
        if not all(self.auth):raise ValueError('Explicit host-side test WebGIS credentials are required')
        self.nonce=secrets.token_hex(16); self.lock=threading.RLock(); self.idle=threading.Condition(self.lock); self.active=0
        self.phase='preparation'; self.calls=[]; self.fault=None; self.upload_paths=set()
        self.output.mkdir(parents=True,exist_ok=False)
    def verify_remote_ownership(self):
        """Before forwarding writes, verify exact new resource keys and parent identity."""
        def metadata(resource):
            response=requests.get(self.base+f'/api/resource/{resource}',auth=self.auth,timeout=(15,60),allow_redirects=False)
            if response.status_code!=200:raise ValueError('Owned resource metadata unavailable')
            return response.json()
        owner=self.fixture['owner'].replace('-','_')
        group=metadata(self.group)['resource'];layer=metadata(self.layer)
        if group['cls']!='resource_group' or group.get('keyname')!=owner:
            raise ValueError('Group ownership differs; no gateway writes allowed')
        resource=layer['resource']
        if resource['cls'] not in ('vector_layer','postgis_layer') or resource['parent']['id']!=self.group or not (resource.get('keyname') or '').startswith(owner+'_'):
            raise ValueError('Layer ownership differs; no gateway writes allowed')
        if not {'fixture_key','revision','test_label','nullable_text'} <= {f['keyname'] for f in layer['feature_layer']['fields']}:
            raise ValueError('Synthetic schema differs')
        return {'group_id':self.group,'layer_id':self.layer,'class':resource['cls'],'ownership':'verified exact owned key and parent'}
    def allowed(self, method, target):
        parsed=urlsplit(target)
        if parsed.scheme or parsed.netloc or parsed.fragment or not parsed.path.startswith('/') or '%' in parsed.path:
            return False
        path=parsed.path.rstrip('/')
        if method=='GET' and path in (f'/api/resource/{self.group}',f'/api/resource/{self.layer}', '/api/component/pyramid/pkg_version'):
            return True
        if re.fullmatch(rf'/api/resource/{self.layer}/(?:feature(?:/[1-9][0-9]*(?:/attachment(?:/[1-9][0-9]*(?:/download)?)?)?)?|feature_count|extent)',path):
            return method=='GET' or self.allow_writes and method in ('POST','PUT','PATCH','DELETE') and '/feature' in path and '/feature_count' not in path
        if not self.allow_writes:return False
        if method=='POST' and path in ('/api/component/file_upload','/api/component/file_upload/upload'):return True
        with self.lock:return path in self.upload_paths and method in ('GET','HEAD','PATCH','POST','DELETE')
    def claim_upload(self, location):
        parsed=urlsplit(urljoin(self.base+'/api/component/file_upload/',location))
        origin=urlsplit(self.base)
        if (parsed.scheme,parsed.netloc)!=(origin.scheme,origin.netloc) or parsed.query or parsed.fragment or parsed.path.endswith('/upload') or not re.fullmatch(r'/api/component/file_upload/[A-Za-z0-9_-]{1,128}',parsed.path):
            raise ValueError('Upload location is outside the newly created session scope')
        with self.lock:self.upload_paths.add(parsed.path)
        return parsed.path
    def record(self, row):
        with self.lock:
            row['index']=len(self.calls); row.setdefault('phase',self.phase); self.calls.append(row)
            with (self.output/'requests.jsonl').open('a',encoding='utf-8') as stream:
                stream.write(json.dumps(row,sort_keys=True)+'\n')
    def wait_idle(self,timeout=15):
        deadline=time.monotonic()+timeout
        with self.idle:
            while self.active:
                remaining=deadline-time.monotonic()
                if remaining<=0:raise TimeoutError('Gateway accounting still has active requests')
                self.idle.wait(remaining)
    def summary(self,*,wait=True):
        if wait:self.wait_idle()
        with self.lock:
            return {'scope':'application HTTP payloads through host gateway; not TLS/TCP or direct-device network performance', 'owner':self.fixture['owner'], 'layer_id':self.layer,'phase':self.phase,'requests':len(self.calls),'request_body_bytes':sum(x.get('request_body_bytes',0) for x in self.calls),'upstream_response_body_bytes':sum(x.get('response_body_bytes',0) for x in self.calls),'response_payload_offered_bytes':sum(x.get('response_payload_offered_bytes',0) for x in self.calls),'response_payload_unknown_calls':sum('response_payload_offered_bytes' not in x and x.get('result') not in ('blocked_scope','injected_disconnect_before_forward') for x in self.calls),'owned_upload_paths':sorted(self.upload_paths),'calls':list(self.calls)}
    def handler(self):
        gateway=self
        class Handler(BaseHTTPRequestHandler):
            protocol_version='HTTP/1.1'
            def log_message(self,*args): pass
            def respond(self,code,body=b'',content_type='application/json'):
                self.send_response(code); self.send_header('Content-Type',content_type); self.send_header('Content-Length',str(len(body))); self.send_header('Connection','close');self.end_headers()
                if body:self.wfile.write(body)
                self.close_connection=True
            def read_body(self, maximum):
                self.connection.settimeout(15)
                transfer=self.headers.get('Transfer-Encoding')
                lengths=self.headers.get_all('Content-Length',[])
                if len(lengths)>1 or transfer and lengths:raise ValueError('Ambiguous body framing')
                if transfer:
                    if transfer.strip().lower()!='chunked':raise ValueError('Unsupported transfer coding')
                    chunks=[];total=0
                    while True:
                        line=self.rfile.readline(130)
                        if not line.endswith(b'\r\n') or len(line)>128:raise ValueError('Invalid chunk line')
                        token=line[:-2].split(b';',1)[0]
                        if not re.fullmatch(b'[0-9a-fA-F]{1,8}',token):raise ValueError('Invalid chunk size')
                        size=int(token,16)
                        if not size:
                            trailers=0
                            while True:
                                line=self.rfile.readline(4098);trailers+=len(line)
                                if trailers>4096 or not line.endswith(b'\r\n'):raise ValueError('Invalid trailer bounds')
                                if line==b'\r\n':return b''.join(chunks)
                        total+=size
                        if total>maximum:raise ValueError('Body exceeds reviewed bound')
                        chunk=self.rfile.read(size)
                        if len(chunk)!=size or self.rfile.read(2)!=b'\r\n':raise ValueError('Incomplete chunk')
                        chunks.append(chunk)
                length=int(lengths[0]) if lengths else 0
                if not 0<=length<=maximum:raise ValueError('Body exceeds reviewed bound')
                body=self.rfile.read(length)
                if len(body)!=length:raise ValueError('Incomplete body')
                return body
            def request(self):
                with gateway.idle:gateway.active+=1
                try:self.forward()
                finally:
                    with gateway.idle:gateway.active-=1;gateway.idle.notify_all()
            def forward(self):
                parsed=urlsplit(self.path)
                if parsed.path=='/__night__/control':
                    if self.headers.get('X-Night-Control')!=gateway.nonce:
                        return self.respond(403,b'{}')
                    if self.command=='POST':
                        try:body=json.loads(self.read_body(4096))
                        except (ValueError,OSError):return self.respond(400,b'{}')
                        with gateway.lock:
                            if 'phase' in body:
                                if not re.fullmatch('[a-z0-9_-]{1,64}',body['phase']):return self.respond(400,b'{}')
                                gateway.phase=body['phase']
                            gateway.fault=body.get('fault')
                    return self.respond(200,json.dumps(gateway.summary(wait=False)).encode())
                if not gateway.allowed(self.command,self.path):
                    gateway.record({'method':self.command,'path':parsed.path,'result':'blocked_scope'})
                    return self.respond(403,b'{"error":"outside_owned_fixture"}')
                try:body=self.read_body(10*1024*1024)
                except (ValueError,OSError):return self.respond(400,b'{}')
                start=time.monotonic()
                row={'phase':gateway.phase,'utc':datetime.now(timezone.utc).isoformat(),'method':self.command,'path':parsed.path,'query_sha256':hashlib.sha256(parsed.query.encode()).hexdigest(),'request_body_bytes':len(body),'request_body_sha256':hashlib.sha256(body).hexdigest()}
                with gateway.lock:
                    fault=gateway.fault
                    if fault and (fault.get('method') not in (None,self.command) or fault.get('path') not in (None,parsed.path)):
                        fault=None
                    if fault and not fault.get('until_clear'):gateway.fault=None
                if fault and fault.get('mode')=='before_forward':
                    row.update(result='injected_disconnect_before_forward',elapsed_ms=0);gateway.record(row);self.close_connection=True;return
                headers={'Accept-Encoding':'identity'}
                for key in ('Content-Type','Accept','Range','Tus-Resumable','Upload-Length','Upload-Offset','Upload-Metadata','Upload-Defer-Length'):
                    if self.headers.get(key):headers[key]=self.headers[key]
                override=self.headers.get('X-HTTP-Method-Override')
                if override in ('PATCH','HEAD'):headers['X-HTTP-Method-Override']=override
                try:
                    with gateway.upstream(self.command,gateway.base+self.path,data=body or None,headers=headers,auth=gateway.auth,timeout=(15,120),allow_redirects=False,stream=True) as response:
                        payload=response.raw.read(24*1024*1024+1,decode_content=False)
                        if len(payload)>24*1024*1024:raise ValueError('Response exceeds reviewed fixture bound')
                        row.update(upstream_status=response.status_code,response_body_bytes=len(payload),response_body_sha256=hashlib.sha256(payload).hexdigest(),elapsed_ms=round((time.monotonic()-start)*1000,3),result='forwarded')
                        if 300<=response.status_code<400:raise ValueError('Upstream redirect rejected')
                        location=None
                        if self.command=='POST' and parsed.path.rstrip('/')=='/api/component/file_upload' and response.status_code==201:
                            upload=gateway.claim_upload(response.headers.get('Location',''))
                            location=f'http://127.0.0.1:{self.server.server_port}'+upload;row['created_upload_path']=upload
                        if fault and fault.get('mode')=='after_commit':
                            row.update(result='injected_disconnect_after_upstream_response',response_payload_offered_bytes=0);gateway.record(row);self.close_connection=True;return
                        self.send_response(response.status_code)
                        for key in ('Content-Type','Content-Encoding','Content-Range','Tus-Resumable','Upload-Offset','Upload-Length','Upload-Expires'):
                            if key in response.headers:self.send_header(key,response.headers[key])
                        if location:self.send_header('Location',location)
                        self.send_header('Content-Length',str(len(payload)));self.send_header('Connection','close');self.end_headers()
                        self.wfile.write(payload);row['response_payload_offered_bytes']=len(payload);gateway.record(row);self.close_connection=True
                except (requests.RequestException,ValueError,OSError) as failure:
                    if 'index' not in row:
                        row.update(result='downstream_transport_failure' if 'upstream_status' in row else 'upstream_transport_failure',category=type(failure).__name__,elapsed_ms=round((time.monotonic()-start)*1000,3));gateway.record(row)
                    self.close_connection=True
            do_GET=request; do_HEAD=request; do_POST=request; do_PUT=request; do_PATCH=request; do_DELETE=request
        return Handler

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--fixture',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--allow-writes',action='store_true');args=parser.parse_args()
    gateway=OwnedGateway(json.loads(args.fixture.read_text(encoding='utf-8')),args.output,allow_writes=args.allow_writes)
    if not gateway.auth[1]:raise ValueError('Credential environment is required')
    gateway.verify_remote_ownership()
    server=ThreadingHTTPServer(('127.0.0.1',0),gateway.handler());server.daemon_threads=True
    connection={'host':'127.0.0.1','port':server.server_port,'control_nonce':gateway.nonce,'layer_id':gateway.layer,'group_id':gateway.group,'dummy_login':'night-fixture','dummy_password':'synthetic-only','writes_authorized':args.allow_writes}
    (gateway.output/'connection.json').write_text(json.dumps(connection,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in connection.items() if k!='control_nonce'}),flush=True)
    try:server.serve_forever(poll_interval=.5)
    except KeyboardInterrupt:pass
    finally:
        server.server_close();(gateway.output/'summary.json').write_text(json.dumps(gateway.summary(),indent=2),encoding='utf-8')
if __name__=='__main__':main()
