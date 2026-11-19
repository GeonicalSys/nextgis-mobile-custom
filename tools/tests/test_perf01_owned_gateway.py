import importlib.util, io, json, os, socket, tempfile, threading, unittest
from pathlib import Path
from unittest.mock import patch
from http.server import ThreadingHTTPServer
import requests
source=Path(__file__).resolve().parents[1]/'perf01_owned_gateway.py'
spec=importlib.util.spec_from_file_location('owned_gateway',source); module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
FIXTURE={'owner':'mobile-night-20261117-01a1e5ee','base':'https://azimuth.nextgis.com','layer_id':1549,'group_id':1548}
class Raw(io.BytesIO):
    def read(self,size=-1,decode_content=False):return super().read(size)
class Reply:
    def __init__(self,payload=b'{"total_count":1000}',code=200):self.raw=Raw(payload);self.status_code=code;self.headers={'Content-Type':'application/json'}
    def __enter__(self):return self
    def __exit__(self,*args):return False
class GatewayTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='night-gateway-owned-',dir=os.environ.get('TEMP'))
        self.env=patch.dict(os.environ,{'LISA_NIGHT_NGW_LOGIN':'synthetic-test-login','LISA_NIGHT_NGW_PASSWORD':'synthetic-not-a-real-password'});self.env.start();self.forwarded=[]
        def upstream(method,url,**kwargs):self.forwarded.append((method,url,kwargs));return Reply()
        self.gateway=module.OwnedGateway(FIXTURE,Path(self.temp.name)/'evidence',allow_writes=True,upstream=upstream)
        self.server=ThreadingHTTPServer(('127.0.0.1',0),self.gateway.handler());self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.base=f'http://127.0.0.1:{self.server.server_port}'
    def tearDown(self):self.server.shutdown();self.server.server_close();self.thread.join(5);self.env.stop();self.temp.cleanup()
    def test_scope_is_exact_even_for_lookalike_and_encoded_paths(self):
        for path in ['/api/resource/1548/feature/1','/api/resource/15490/feature/1','https://other.invalid/api/resource/1549/feature/1','//other.invalid/api/resource/1549/feature/1','/api/resource/1549/feature/%31','/api/resource/1549/feature/../1548','/api/resource/1549/feature/0']:
            self.assertFalse(self.gateway.allowed('PUT',path),path)
        self.assertTrue(self.gateway.allowed('PUT','/api/resource/1549/feature/1'))
    def test_mutating_routes_require_explicit_opt_in(self):
        self.gateway.allow_writes=False
        for method in ['POST','PUT','PATCH','DELETE']:
            self.assertFalse(self.gateway.allowed(method,'/api/resource/1549/feature/1'))
        self.assertTrue(self.gateway.allowed('GET','/api/resource/1549/feature/1'))
    def test_blocked_request_never_reaches_upstream(self):
        r=requests.delete(self.base+'/api/resource/0',timeout=5);self.assertEqual(403,r.status_code);self.assertEqual([],self.forwarded)
        self.assertEqual('blocked_scope',self.gateway.summary()['calls'][0]['result'])
    def test_body_bytes_and_status_are_exact_without_request_secrets(self):
        r=requests.put(self.base+'/api/resource/1549/feature/1',data=b'{"fields":{"revision":2}}',auth=('dummy-login','dummy-secret'),timeout=5)
        self.assertEqual(200,r.status_code);self.assertEqual(25,self.gateway.summary()['request_body_bytes'])
        self.assertEqual(len(r.content),self.gateway.summary()['upstream_response_body_bytes'])
        encoded=json.dumps(self.gateway.summary())
        for secret in ['dummy-login','dummy-secret','synthetic-not-a-real-password']:
            self.assertNotIn(secret,encoded)
        self.assertEqual('synthetic-not-a-real-password',self.forwarded[0][2]['auth'][1])
    def test_after_commit_fault_has_one_upstream_request_and_no_false_success(self):
        self.gateway.fault={'mode':'after_commit','method':'POST','path':'/api/resource/1549/feature/'}
        with self.assertRaises(requests.ConnectionError):requests.post(self.base+'/api/resource/1549/feature/',json={'fields':{'fixture_key':'probe'}},timeout=5)
        self.assertEqual(1,len(self.forwarded));row=self.gateway.summary()['calls'][0]
        self.assertEqual(200,row['upstream_status']);self.assertEqual('injected_disconnect_after_upstream_response',row['result'])
    def test_before_forward_fault_never_sends_the_mutation(self):
        self.gateway.fault={'mode':'before_forward','method':'POST'}
        with self.assertRaises(requests.ConnectionError):requests.post(self.base+'/api/resource/1549/feature/',data=b'{}',timeout=5)
        self.assertEqual([],self.forwarded)
    def test_control_requires_owned_nonce_and_marks_next_phase(self):
        self.assertEqual(403,requests.get(self.base+'/__night__/control',timeout=5).status_code)
        r=requests.post(self.base+'/__night__/control',headers={'X-Night-Control':self.gateway.nonce},json={'phase':'one-percent'},timeout=5)
        self.assertEqual(200,r.status_code);self.assertEqual('one-percent',r.json()['phase'])
    def test_phase_is_bound_at_request_start_not_completion(self):
        self.gateway.phase='started'
        def upstream(method,url,**kwargs):
            self.gateway.phase='next-phase';return Reply()
        self.gateway.upstream=upstream
        requests.get(self.base+'/api/resource/1549/feature_count',timeout=5)
        self.assertEqual('started',self.gateway.summary()['calls'][0]['phase'])
    def test_tus_creation_claims_only_its_returned_session_and_rewrites_location(self):
        path='/api/component/file_upload/own-session-123'
        def upstream(method,url,**kwargs):
            self.forwarded.append((method,url,kwargs))
            reply=Reply(b'',201 if method=='POST' else 204)
            reply.headers.update({'Tus-Resumable':'1.0.0','Upload-Offset':'7'})
            if method=='POST':reply.headers['Location']='https://azimuth.nextgis.com'+path
            return reply
        self.gateway.upstream=upstream
        self.assertFalse(self.gateway.allowed('PATCH',path))
        response=requests.post(self.base+'/api/component/file_upload/',headers={'Tus-Resumable':'1.0.0','Upload-Length':'7','Upload-Metadata':'filename b3duLnBuZw=='},timeout=5)
        self.assertEqual(201,response.status_code);self.assertEqual(self.base+path,response.headers['Location'])
        patch_reply=requests.patch(response.headers['Location'],data=b'PNGtest',headers={'Tus-Resumable':'1.0.0','Upload-Offset':'0'},timeout=5)
        self.assertEqual(204,patch_reply.status_code);self.assertEqual('7',patch_reply.headers['Upload-Offset'])
        self.assertEqual(b'PNGtest',self.forwarded[1][2]['data'])
        self.assertEqual('7',self.forwarded[0][2]['headers']['Upload-Length'])
        self.assertEqual('1.0.0',self.forwarded[1][2]['headers']['Tus-Resumable'])
        self.assertFalse(self.gateway.allowed('GET','/api/component/file_upload/other-session'))
        self.assertEqual([path],self.gateway.summary()['owned_upload_paths'])
    def test_foreign_or_ambiguous_upload_location_is_not_admitted(self):
        for location in ['https://other.invalid/api/component/file_upload/x','//other.invalid/api/component/file_upload/x','/api/resource/1549','/api/component/file_upload/upload','/api/component/file_upload/x?secret=value','/api/component/file_upload/%31']:
            with self.assertRaises(ValueError):self.gateway.claim_upload(location)
        self.assertEqual([],self.gateway.summary()['owned_upload_paths'])
    def test_chunked_binary_payload_counts_decoded_bytes_and_forwards_exact_content(self):
        body=[b'PNG',b'\x00\xffdata']
        response=requests.post(self.base+'/api/component/file_upload/upload',data=iter(body),headers={'Content-Type':'application/octet-stream'},timeout=5)
        self.assertEqual(200,response.status_code);self.assertEqual(b''.join(body),self.forwarded[0][2]['data'])
        self.assertEqual(sum(map(len,body)),self.gateway.summary()['request_body_bytes'])
        self.assertNotIn('Transfer-Encoding',self.forwarded[0][2]['headers'])
    def test_ambiguous_or_oversized_chunk_framing_never_reaches_upstream(self):
        for headers,body in [(b'Transfer-Encoding: chunked\r\nContent-Length: 3\r\n',b'0\r\n\r\n'),(b'Transfer-Encoding: chunked\r\n',b'A00001\r\n')]:
            with socket.create_connection(self.server.server_address,timeout=5) as client:
                client.sendall(b'POST /api/component/file_upload/upload HTTP/1.1\r\nHost: localhost\r\n'+headers+b'\r\n'+body)
                self.assertIn(b' 400 ',client.recv(1024).split(b'\r\n',1)[0])
        self.assertEqual([],self.forwarded)
if __name__=='__main__':unittest.main(verbosity=2)
