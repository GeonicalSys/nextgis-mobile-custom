"""Validate raw opt-in RenderResourceProfiler observations without inventing missing data."""
from collections import defaultdict
import math, statistics

OUTCOMES={'provider_returned','returned_to_sdk','cancelled','failed','abandoned'}
def integer(value,label):
    if type(value) is not int or value<0:raise ValueError('Invalid '+label)
    return value

def columns(values,label):
    if not isinstance(values,list) or not values or any(not isinstance(x,str) or not x for x in values) or len(values)!=len(set(values)):
        raise ValueError('Invalid '+label)
    return values

def distribution(values,complete):
    values=sorted(values)
    return {'n':len(values),'population_complete':complete,'p50_ms':statistics.median(values)/1e6 if values and complete else None,
            'p95_ms':values[math.ceil(.95*len(values))-1]/1e6 if len(values)>=20 and complete else None}

def summarize(records,summary):
    if summary.get('schema')!=1 or summary.get('stopped') is not True:raise ValueError('A terminal profiler summary is required')
    session=summary.get('session');phases=columns(summary.get('phase_columns'),'phase columns');counts=columns(summary.get('count_columns'),'count columns')
    names=columns(summary.get('layer_columns'),'aggregate columns');values=summary.get('global')
    if not isinstance(values,list) or len(values)!=len(names):raise ValueError('Invalid aggregate row')
    aggregate=dict(zip(names,values))
    for name,value in aggregate.items():
        if name!='layer_id':integer(value,name)
    for name in ['requests','active_at_snapshot','raw_unobserved','request_elapsed_ns_sum',*counts]:
        if name not in aggregate:raise ValueError('Missing aggregate '+name)
    for name in ['raw_rejected','sink_failures','clock_failures','untracked_layer_requests']:
        integer(summary.get(name),name)
    complete=summary.get('detailed_complete') is True
    if complete and (aggregate['active_at_snapshot'] or aggregate['raw_unobserved'] or any(summary[x] for x in ['raw_rejected','sink_failures','clock_failures','untracked_layer_requests'])):
        raise ValueError('Complete flag conflicts with missing/active observations')
    rows=[r for r in records if r.get('event')=='sqlite_request'];seen=set();sequences=set();series=defaultdict(list);raw_counts=[0]*len(counts);raw_phases=[0]*len(phases);elapsed_total=0
    for row in rows:
        if row.get('schema')!=1 or row.get('session')!=session:raise ValueError('Mixed profiler sessions')
        identity=integer(row.get('request_id'),'request id');sequence=integer(row.get('sequence'),'sequence')
        if identity in seen or sequence in sequences:raise ValueError('Duplicated profiler request')
        seen.add(identity);sequences.add(sequence)
        if row.get('source_type') not in ('sqlite_native','sqlite_line_labels') or row.get('outcome') not in OUTCOMES:raise ValueError('Unknown source/outcome')
        elapsed=integer(row.get('elapsed_ns'),'elapsed ns')
        if row.get('timing_available') is not True:raise ValueError('Unavailable raw clock cannot be a latency sample')
        for key,expected in [('phase_wall_ns',len(phases)),('phase_thread_cpu_known_ns',len(phases)),('phase_cpu_unknown_segments',len(phases)),('counts',len(counts))]:
            a=row.get(key)
            if not isinstance(a,list) or len(a)!=expected:raise ValueError('Invalid '+key)
            for value in a:integer(value,key)
        if sum(row['phase_wall_ns'])!=elapsed:raise ValueError('Phase totals differ from completed elapsed')
        elapsed_total+=elapsed
        for i,value in enumerate(row['counts']):raw_counts[i]+=value
        for i,value in enumerate(row['phase_wall_ns']):raw_phases[i]+=value
        series[(row['source_type'],row['outcome'])].append(elapsed)
    if complete:
        if len(rows)!=aggregate['requests'] or elapsed_total!=aggregate['request_elapsed_ns_sum']:raise ValueError('Complete raw population is missing')
        if raw_counts!=[aggregate[name] for name in counts]:raise ValueError('Raw counters differ from aggregate')
        if raw_phases!=summary.get('global_phase_wall_ns'):raise ValueError('Raw phase work differs from aggregate')
    hits=aggregate.get('cache_hits');misses=aggregate.get('cache_misses')
    if hits is None or misses is None:raise ValueError('Cache lookup counters are required')
    return {'schema':1,'session':session,'population_complete':complete,'requests_started':aggregate['requests'],'retained_completed_requests':len(rows),
            'active_at_stop':aggregate['active_at_snapshot'],'cache_hits':hits,'cache_misses':misses,'cache_lookup_denominator':hits+misses,
            'observed_cache_hit_rate':hits/(hits+misses) if hits+misses else None,
            'latency_by_source_and_outcome':{source+':'+outcome:distribution(samples,complete) for (source,outcome),samples in sorted(series.items())},
            'phase_work_ms':{name:summary['global_phase_wall_ns'][i]/1e6 for i,name in enumerate(phases)},
            'cpu_fully_attributed':summary.get('cpu_fully_attributed') is True,
            'limits':['Observed instrumented request wall times include observer work; sums overlap across workers.',
                      'SQLite-native requests and line-label requests and terminal outcomes stay separate.',
                      'Cache denominator is actual lookups, not every started request.',
                      'Missing raw/active/overflow populations have no accepted percentile; CPU unknown remains explicit.',
                      'Provider/SDK return is not independently validated full-scene rendering or ordinary APK launch.']}
