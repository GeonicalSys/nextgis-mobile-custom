from pathlib import Path
import copy,importlib.util,unittest
p=Path(__file__).resolve().parents[1]/'perf01_tile_profile_summary.py'
spec=importlib.util.spec_from_file_location('summary',p);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class ProfileSummaryTest(unittest.TestCase):
 def fixture(self,n=20):
  rows=[]
  for i in range(1,n+1):
   elapsed=i*1000000
   rows.append({'schema':1,'session':'independent','event':'sqlite_request','request_id':i,'sequence':i+1,'source_type':'sqlite_native','outcome':'returned_to_sdk','elapsed_ns':elapsed,'timing_available':True,'phase_wall_ns':[elapsed,0],'phase_thread_cpu_known_ns':[elapsed//2,0],'phase_cpu_unknown_segments':[0,0],'counts':[1 if i%2 else 0,0 if i%2 else 1,100]})
  summary={'schema':1,'session':'independent','stopped':True,'phase_columns':['io_queue','cache_read'],'count_columns':['cache_hits','cache_misses','pbf_bytes'],'layer_columns':['layer_id','requests','active_at_snapshot','raw_unobserved','request_elapsed_ns_sum','cache_hits','cache_misses','pbf_bytes'],'global':[-1,n,0,0,n*(n+1)//2*1000000,(n+1)//2,n//2,n*100],'global_phase_wall_ns':[n*(n+1)//2*1000000,0],'detailed_complete':True,'raw_rejected':0,'sink_failures':0,'clock_failures':0,'untracked_layer_requests':0,'cpu_fully_attributed':True}
  return rows,summary
 def test_twenty_authored_latencies_have_median10point5_and_rank19(self):
  rows,s=self.fixture();out=m.summarize(rows,s);v=out['latency_by_source_and_outcome']['sqlite_native:returned_to_sdk'];self.assertEqual(10.5,v['p50_ms']);self.assertEqual(19,v['p95_ms']);self.assertEqual(.5,out['observed_cache_hit_rate'])
 def test_calibration_has_no_p95(self):
  rows,s=self.fixture(1);self.assertIsNone(m.summarize(rows,s)['latency_by_source_and_outcome']['sqlite_native:returned_to_sdk']['p95_ms'])
 def test_missing_raw_cannot_claim_complete(self):
  rows,s=self.fixture();rows.pop();self.assertRaises(ValueError,m.summarize,rows,s)
 def test_partial_summary_preserves_unknown_percentiles(self):
  rows,s=self.fixture();rows.pop();s['detailed_complete']=False;s['raw_rejected']=1;out=m.summarize(rows,s);self.assertFalse(out['population_complete']);self.assertIsNone(out['latency_by_source_and_outcome']['sqlite_native:returned_to_sdk']['p50_ms'])
 def test_duplicate_request_is_not_an_extra_trial(self):
  rows,s=self.fixture();rows[-1]=copy.deepcopy(rows[0]);self.assertRaises(ValueError,m.summarize,rows,s)
 def test_mixed_session_rejected(self):
  rows,s=self.fixture();rows[0]['session']='other';self.assertRaises(ValueError,m.summarize,rows,s)
 def test_conflicting_complete_flag_rejected(self):
  rows,s=self.fixture();s['global'][2]=1;self.assertRaises(ValueError,m.summarize,rows,s)
 def test_no_lookup_does_not_mean_zero_hit_rate(self):
  rows,s=self.fixture();s['global'][5:7]=[0,0]
  for row in rows:row['counts'][:2]=[0,0]
  self.assertIsNone(m.summarize(rows,s)['observed_cache_hit_rate'])
 def test_cancelled_work_has_separate_distribution(self):
  rows,s=self.fixture();rows[0]['outcome']='cancelled';out=m.summarize(rows,s);self.assertEqual(1,out['latency_by_source_and_outcome']['sqlite_native:cancelled']['n']);self.assertEqual(19,out['latency_by_source_and_outcome']['sqlite_native:returned_to_sdk']['n'])
 def test_unknown_cpu_is_retained(self):
  rows,s=self.fixture();s['cpu_fully_attributed']=False;self.assertFalse(m.summarize(rows,s)['cpu_fully_attributed'])
 def test_boolean_is_not_numeric_latency(self):
  rows,s=self.fixture();rows[0]['elapsed_ns']=True;self.assertRaises(ValueError,m.summarize,rows,s)
 def test_phase_sum_has_independent_elapsed_check(self):
  rows,s=self.fixture();rows[0]['phase_wall_ns'][0]+=1;self.assertRaises(ValueError,m.summarize,rows,s)
 def test_raw_count_loss_rejected(self):
  rows,s=self.fixture();rows[0]['counts'][0]=0;self.assertRaises(ValueError,m.summarize,rows,s)
if __name__=='__main__':unittest.main(verbosity=2)
