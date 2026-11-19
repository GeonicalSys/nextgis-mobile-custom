package com.nextgis.mobile.rendering;

import android.os.SystemClock;
import com.nextgis.maplib.util.RenderResourceProfiler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/** Test-owned observation; existing producer accounting is reused, not duplicated. */
final class Perf01TileObservation implements AutoCloseable {
    private final ArrayBlockingQueue<Map<String,Object>> records=new ArrayBlockingQueue<>(4096);
    private final File output;
    private final long started=SystemClock.elapsedRealtime();
    private final String session="perf01-"+UUID.randomUUID().toString();
    private boolean stopped;
    private JSONObject result;

    Perf01TileObservation(File directory,String version,int build,String revision) throws Exception {
        output=new File(directory,"perf01-tile-profile-"+session+".json");
        if(output.exists())throw new IllegalStateException("Never overwrite a prior observation");
        synchronized(RenderResourceProfiler.class) {
            if(RenderResourceProfiler.isActive())throw new IllegalStateException("Another profiler owns this process");
            RenderResourceProfiler.start(session,version,build,revision,records::offer);
            if(!session.equals(RenderResourceProfiler.snapshot().get("session")))throw new IllegalStateException("Profiler did not start");
        }
    }
    static long field(Map<String,Object> summary,String column) {
        java.util.List<?> columns=(java.util.List<?>)summary.get("layer_columns");
        long[] global=(long[])summary.get("global");
        int index=columns==null?-1:columns.indexOf(column);
        if(index<0||global==null||index>=global.length)throw new IllegalStateException("Missing profiler field");
        return global[index];
    }
    JSONObject finish(String reason) throws Exception {
        if(stopped)return result;
        long tailStarted=SystemClock.elapsedRealtime(),deadline=tailStarted+30000,quietSince=-1,lastRequests=-1;
        while(SystemClock.elapsedRealtime()<deadline){
            Map<String,Object> summary=RenderResourceProfiler.snapshot();
            if(!session.equals(summary.get("session")))throw new IllegalStateException("Profiler ownership changed; preserve foreign observer");
            long active=field(summary,"active_at_snapshot"),requests=field(summary,"requests"),now=SystemClock.elapsedRealtime();
            if(active==0&&requests==lastRequests){if(quietSince<0)quietSince=now;if(now-quietSince>=500)break;}else quietSince=-1;
            lastRequests=requests;Thread.sleep(50);
        }
        Map<String,Object> summary;
        synchronized(RenderResourceProfiler.class){
            if(!session.equals(RenderResourceProfiler.snapshot().get("session")))throw new IllegalStateException("Profiler ownership changed; do not stop it");
            summary=RenderResourceProfiler.stop(reason);stopped=true;
        }
        JSONArray raw=new JSONArray();Map<String,Object> next;while((next=records.poll())!=null)raw.put(new JSONObject(next));
        result=new JSONObject().put("schema",1).put("session",session).put("summary",new JSONObject(summary)).put("records",raw)
            .put("observation_ms",SystemClock.elapsedRealtime()-started).put("post_frame_observation_tail_ms",SystemClock.elapsedRealtime()-tailStarted)
            .put("scope","All instrumented requests begun in this observation window; adjacent/prefetch and validation overhead are included. Provider/SDK return is not full-scene acceptance.");
        byte[] bytes=result.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>8*1024*1024)throw new IllegalStateException("Bounded profile evidence exceeded 8MiB");
        Files.write(output.toPath(),bytes);return result;
    }
    String filename(){return output.getName();}
    @Override public void close() throws Exception {
        if(!stopped)finish("interrupted_observation");
    }
}
