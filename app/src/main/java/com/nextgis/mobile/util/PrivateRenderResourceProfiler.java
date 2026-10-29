package com.nextgis.mobile.util;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import com.nextgis.maplib.util.RenderResourceProfiler;
import com.nextgis.mobile.BuildConfig;

import org.json.JSONObject;
import org.maplibre.android.maps.RenderingStats;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Private, local-only observer. Normal APKs do not start it or register its receiver. */
public final class PrivateRenderResourceProfiler {
    static final long DURATION_NS = TimeUnit.MINUTES.toNanos(60);
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final int FOOTER_RESERVE = 256 * 1024;
    static final int MAX_QUEUE_BYTES = 2 * 1024 * 1024;
    private static final String TAG = "LISA-Resources";
    private static volatile Session active;

    private PrivateRenderResourceProfiler() { }

    public static boolean isActive() {
        Session session = active;
        return session != null && !session.stopRequested;
    }

    public static void initialize(Context context) {
        if (!BuildConfig.PRIVATE_RESOURCE_PROFILING || active != null) return;
        try {
            Session session = new Session(context.getApplicationContext());
            active = session;
            RenderResourceProfiler.start(session.id, BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE, BuildConfig.SOURCE_REVISION, session::offer);
            session.registerStop();
            Thread thread = new Thread(session, "LISA-ResourceWriter");
            thread.setDaemon(true);
            thread.start();
        } catch (Exception error) {
            active = null;
            try { RenderResourceProfiler.stop("app_observer_start_failed"); } catch (Exception ignored) { }
            Log.w(TAG, "Private observer unavailable: " + error.getClass().getSimpleName());
        }
    }

    public static long viewAttached() {
        Session session = active;
        return session == null ? 0 : session.views.incrementAndGet();
    }

    public static void scene(long view, int boundary, int reason, double zoom, double bearing, double pitch) {
        Session session = active;
        if (session == null || session.stopRequested) return;
        try {
            if (boundary == 0 || boundary == 1) session.scene.incrementAndGet();
            Map<String, Object> record = fields();
            record.put("view_generation", view);
            record.put("scene_sequence", session.scene.get());
            record.put("boundary", boundary); // 0=ready,1=move-start,2=camera-idle,3=SDK-idle,4=view-detach
            record.put("reason", reason);
            record.put("zoom", finite(zoom));
            record.put("bearing", finite(bearing));
            record.put("pitch", finite(pitch));
            record.put("elapsed_realtime_ns", SystemClock.elapsedRealtimeNanos());
            RenderResourceProfiler.event("scene", record);
        } catch (Exception ignored) { session.observerErrors.incrementAndGet(); }
    }

    public static void frame(long view, boolean fully, RenderingStats stats) {
        Session session = active;
        if (session == null || session.stopRequested || stats == null) return;
        long start = SystemClock.elapsedRealtimeNanos();
        long cpu = Debug.threadCpuTimeNanos();
        try {
            session.frames.add(view, session.scene.get(), fully, stats, start);
            session.observedFrame(view, fully, stats, start);
        } catch (Exception ignored) {
            session.observerErrors.incrementAndGet();
        } finally {
            session.frameObserverWall.addAndGet(Math.max(0, SystemClock.elapsedRealtimeNanos() - start));
            session.frameObserverCpu.addAndGet(Math.max(0, Debug.threadCpuTimeNanos() - cpu));
        }
    }

    static Map<String, Object> fields() { return new LinkedHashMap<>(); }
    static Double finite(double value) { return Double.isFinite(value) ? value : null; }
    static Double secondsToMillis(double seconds) {
        return Double.isFinite(seconds) && seconds >= 0 && seconds <= Double.MAX_VALUE / 1000
                ? seconds * 1000 : null;
    }

    /** Bound producer traversal and queued memory without serializing on the rendering thread. */
    static int estimate(Object value, int depth) {
        if (depth > 8) return MAX_QUEUE_BYTES + 1;
        if (value == null || value instanceof Number || value instanceof Boolean) return 16;
        if (value instanceof String) return ((String) value).length() <= 1024
                ? 16 + ((String) value).length() * 3 : MAX_QUEUE_BYTES + 1;
        int bytes = 32;
        if (value instanceof Map) {
            if (((Map<?, ?>) value).size() > 512) return MAX_QUEUE_BYTES + 1;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                bytes += estimate(entry.getKey(), depth + 1) + estimate(entry.getValue(), depth + 1);
                if (bytes > MAX_QUEUE_BYTES) return bytes;
            }
        } else if (value instanceof Iterable) {
            int count = 0;
            for (Object item : (Iterable<?>) value) {
                if (++count > 4096) return MAX_QUEUE_BYTES + 1;
                bytes += estimate(item, depth + 1);
                if (bytes > MAX_QUEUE_BYTES) return bytes;
            }
        } else if (value.getClass().isArray()) {
            int count = java.lang.reflect.Array.getLength(value);
            if (count > 4096) return MAX_QUEUE_BYTES + 1;
            for (int i = 0; i < count; i++) {
                bytes += estimate(java.lang.reflect.Array.get(value, i), depth + 1);
                if (bytes > MAX_QUEUE_BYTES) return bytes;
            }
        } else return MAX_QUEUE_BYTES + 1;
        return bytes;
    }

    static final class Pending {
        final Map<String, Object> record;
        final int estimatedBytes;
        Pending(Map<String, Object> record, int bytes) {
            this.record = new LinkedHashMap<>(record);
            estimatedBytes = bytes;
        }
    }

    static final class BoundedOutput {
        final OutputStream output;
        final long cap;
        final int reserve;
        long bytes;
        BoundedOutput(OutputStream output, long cap, int reserve) {
            this.output = output; this.cap = cap; this.reserve = reserve;
        }
        boolean write(Map<String, Object> record, boolean footer) throws IOException {
            return write(record, footer, footer ? reserve : 128 * 1024);
        }
        boolean writeCheckpoint(Map<String, Object> record) throws IOException {
            return write(record, false, reserve);
        }
        private boolean write(Map<String, Object> record, boolean footer, int recordCap) throws IOException {
            byte[] line = (new JSONObject(record).toString() + "\n").getBytes(StandardCharsets.UTF_8);
            if (line.length > recordCap
                    || bytes + line.length > cap - (footer ? 0 : reserve)) return false;
            output.write(line);
            bytes += line.length;
            return true;
        }
    }

    static final class Session implements Runnable {
        final Context context;
        final String id = UUID.randomUUID().toString().replace("-", "");
        final long started = SystemClock.elapsedRealtimeNanos();
        final long deadline = started + DURATION_NS;
        final LinkedBlockingQueue<Pending> queue = new LinkedBlockingQueue<>(512);
        final AtomicLong scene = new AtomicLong();
        final AtomicLong views = new AtomicLong();
        final AtomicLong observerErrors = new AtomicLong();
        final AtomicLong frameObserverWall = new AtomicLong();
        final AtomicLong frameObserverCpu = new AtomicLong();
        final AtomicLong firstFullyScene = new AtomicLong(-1);
        final AtomicLong slowFramesRateDropped = new AtomicLong();
        final FrameAccumulator frames = new FrameAccumulator(this);
        final Object queueLock = new Object();
        volatile boolean stopRequested;
        volatile String stopReason = "deadline";
        volatile Map<String, Object> terminalSummary;
        int queuedBytes;
        int queuePeak;
        long droppedRecords;
        long droppedEstimateBytes;
        long oversizedRecords;
        long writtenRecords;
        long writerWallNs;
        long writerCpuNs;
        long memoryProbeWallNs;
        long memoryProbeCpuNs;
        long maxMemoryGapNs;
        long lastMemoryNs;
        long lastPssNs;
        long nextCheckpointNs = started + TimeUnit.SECONDS.toNanos(10);
        long lastCompleteCheckpointElapsedNs;
        long slowWindowNs;
        int slowWindowCount;
        boolean exportAvailable;
        boolean storageFailure;
        BroadcastReceiver receiver;

        Session(Context context) { this.context = context; }

        void observedFrame(long view, boolean fully, RenderingStats stats, long now) {
            long currentScene = scene.get();
            boolean first = fully && firstFullyScene.getAndSet(currentScene) != currentScene;
            Double encoding = secondsToMillis(stats.encodingTime), rendering = secondsToMillis(stats.renderingTime);
            boolean slow = encoding != null && rendering != null && Math.max(encoding, rendering) >= 100;
            if (slow) synchronized (frames) {
                if (now - slowWindowNs >= TimeUnit.SECONDS.toNanos(1)) { slowWindowNs = now; slowWindowCount = 0; }
                if (slowWindowCount >= 5) { slowFramesRateDropped.incrementAndGet(); slow = false; }
                else slowWindowCount++;
            }
            if (!first && !slow) return;
            Map<String, Object> record = fields();
            record.put("view_generation", view); record.put("scene_sequence", currentScene);
            record.put("elapsed_realtime_ns", now); record.put("fully", fully);
            record.put("sdk_encoding_ms", encoding); record.put("sdk_rendering_ms", rendering);
            record.put("first_fully_callback_after_scene", first);
            RenderResourceProfiler.event(slow ? "frame_slow" : "frame_first_full", record);
        }

        boolean offer(Map<String, Object> record) {
            Object owner = record.get("session");
            if (owner != null && !id.equals(owner)) {
                synchronized (queueLock) { droppedRecords++; }
                return false;
            }
            // The authoritative library end is persisted once in the reserved app footer,
            // never put behind raw records or rejected by the smaller raw-record budget.
            if ("resource_end".equals(record.get("event")) && id.equals(owner)) {
                terminalSummary = new LinkedHashMap<>(record);
                if (!stopRequested) { stopReason = "library_stop"; stopRequested = true; }
                return true;
            }
            if (stopRequested) return false;
            int bytes = estimate(record, 0);
            synchronized (queueLock) {
                if (stopRequested || bytes > 128 * 1024 || queuedBytes + bytes > MAX_QUEUE_BYTES
                        || !queue.offer(new Pending(record, bytes))) {
                    droppedRecords++;
                    droppedEstimateBytes += Math.min(bytes, MAX_QUEUE_BYTES + 1);
                    return false;
                }
                queuedBytes += bytes;
                queuePeak = Math.max(queuePeak, queue.size());
                return true;
            }
        }

        void registerStop() {
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (intent != null && (context.getPackageName() + ".resourceprofiler.STOP").equals(intent.getAction())
                            && id.equals(intent.getStringExtra("session"))) {
                        stopReason = "external_stop";
                        stopRequested = true;
                    }
                }
            };
            IntentFilter filter = new IntentFilter(context.getPackageName() + ".resourceprofiler.STOP");
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Manifest.permission.DUMP,
                    null, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter, Manifest.permission.DUMP, null);
        }

        OutputStream open() throws IOException {
            String name = "lisa-resources-" + id + ".jsonl";
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/x-ndjson");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LISA-Diagnostics");
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    OutputStream result = uri == null ? null : context.getContentResolver().openOutputStream(uri, "w");
                    if (result != null) { exportAvailable = true; return result; }
                } catch (Exception error) { observerErrors.incrementAndGet(); }
            }
            File directory = new File(context.getFilesDir(), "resource-profiler");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Private diagnostic directory unavailable");
            File file = new File(directory, name);
            if (!file.createNewFile()) throw new IOException("Diagnostic session collision");
            exportAvailable = false;
            return new FileOutputStream(file, false);
        }

        Map<String, Object> header() {
            Map<String, Object> record = fields();
            record.put("schema_version", 1);
            record.put("event", "app_session");
            record.put("session", id);
            record.put("pid", Process.myPid());
            record.put("package_name", context.getPackageName());
            record.put("app_version", BuildConfig.VERSION_NAME);
            record.put("app_build", BuildConfig.VERSION_CODE);
            record.put("brand", BuildConfig.FLAVOR);
            record.put("source_revision", BuildConfig.SOURCE_REVISION);
            record.put("private_resource_profiling", true);
            record.put("wall_time_ms", System.currentTimeMillis());
            record.put("elapsed_realtime_ns", started);
            record.put("process_start_elapsed_ms", Process.getStartElapsedRealtime());
            Long startTicks = kernelStartTicks();
            Long tickHz = clockTickHz();
            record.put("process_start_ticks", startTicks);
            record.put("clock_tick_hz", tickHz);
            record.put("kernel_process_identity_available", startTicks != null && tickHz != null);
            record.put("process_start_elapsed_is_kernel_identity", false);
            record.put("process_64_bit", Process.is64Bit());
            record.put("api_level", Build.VERSION.SDK_INT);
            record.put("deadline_elapsed_ns", deadline);
            record.put("max_file_bytes", MAX_FILE_BYTES);
            record.put("queue_capacity_records", 512);
            record.put("queue_capacity_estimated_bytes", MAX_QUEUE_BYTES);
            record.put("summary_reserve_bytes", FOOTER_RESERVE);
            record.put("export_available", exportAvailable);
            record.put("sdk_times_are_gpu_or_thread_cpu", false);
            record.put("sdk_duration_histogram_basis", "max_encoding_rendering_ms");
            record.put("sdk_duration_histogram_upper_bounds_ms", java.util.Arrays.asList(1,4,8,16,32,64,100));
            record.put("scene_time_correlation_only", true);
            record.put("no_wakelock", true);
            return record;
        }

        @Override public void run() {
            Map<String, Object> finalSummary = fields();
            try (OutputStream raw = open(); BufferedOutputStream stream = new BufferedOutputStream(raw, 64 * 1024)) {
                BoundedOutput out = new BoundedOutput(stream, MAX_FILE_BYTES, FOOTER_RESERVE);
                out.write(header(), false);
                stream.flush();
                Log.i(TAG, "session=" + id + " export_available=" + exportAvailable);
                long lastFlush = started;
                while (!stopRequested && SystemClock.elapsedRealtimeNanos() < deadline) {
                    long now = SystemClock.elapsedRealtimeNanos();
                    if (lastMemoryNs == 0 || now - lastMemoryNs >= TimeUnit.SECONDS.toNanos(1)) {
                        memory(now);
                        frames.flush(now);
                    }
                    if (now >= nextCheckpointNs) {
                        if (!checkpoint(out, stream, now)) {
                            oversizedRecords++;
                            stopReason = "file_limit";
                            stopRequested = true;
                        }
                        nextCheckpointNs = now + TimeUnit.SECONDS.toNanos(30);
                    }
                    Pending item = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (item != null) {
                        synchronized (queueLock) { queuedBytes -= item.estimatedBytes; }
                        if (!write(out, item.record)) { oversizedRecords++; stopReason = "file_limit"; stopRequested = true; }
                    }
                    now = SystemClock.elapsedRealtimeNanos();
                    if (now - lastFlush >= TimeUnit.SECONDS.toNanos(1)) { stream.flush(); lastFlush = now; }
                }
                stopRequested = true;
                Map<String, Object> finalFrames = frames.take();
                finalSummary = RenderResourceProfiler.stop(stopReason);
                if (finalSummary.isEmpty() && terminalSummary != null) finalSummary = terminalSummary;
                Pending item;
                while ((item = queue.poll()) != null) {
                    if (!write(out, item.record)) { oversizedRecords++; }
                }
                Map<String, Object> end = fields();
                end.put("event", "app_end"); end.put("session", id); end.put("pid", Process.myPid());
                end.put("wall_time_ms", System.currentTimeMillis());
                end.put("elapsed_realtime_ns", SystemClock.elapsedRealtimeNanos());
                end.put("stop_reason", stopReason);
                end.put("export_available", exportAvailable);
                end.put("summary", finalSummary);
                end.put("resource_end_persisted_in_footer", terminalSummary != null);
                end.put("final_frame_summary", finalFrames);
                end.put("slow_frames_rate_dropped", slowFramesRateDropped.get());
                end.put("queue_dropped_records", droppedRecords);
                end.put("queue_dropped_estimate_bytes", droppedEstimateBytes);
                end.put("file_rejected_records", oversizedRecords);
                end.put("queue_peak_records", queuePeak);
                end.put("writer_records", writtenRecords);
                end.put("writer_bytes_before_footer", out.bytes);
                end.put("writer_wall_ns", writerWallNs); end.put("writer_cpu_ns", writerCpuNs);
                end.put("memory_observer_wall_ns", memoryProbeWallNs); end.put("memory_observer_cpu_ns", memoryProbeCpuNs);
                end.put("frame_observer_wall_ns", frameObserverWall.get()); end.put("frame_observer_cpu_ns", frameObserverCpu.get());
                end.put("max_memory_observed_gap_ns", maxMemoryGapNs);
                end.put("last_complete_checkpoint_elapsed_ns", lastCompleteCheckpointElapsedNs);
                end.put("observer_errors", observerErrors.get());
                end.put("raw_app_stream_complete", droppedRecords == 0 && oversizedRecords == 0);
                end.put("summary_available", !finalSummary.isEmpty());
                end.put("complete_footer", true);
                if (!out.write(end, true)) {
                    end.remove("summary"); end.put("summary_omitted_size_limit", true);
                    end.put("summary_available", false); end.put("resource_end_persisted_in_footer", false);
                    out.write(end, true);
                }
                stream.flush();
            } catch (Exception error) {
                storageFailure = true;
                observerErrors.incrementAndGet();
                stopRequested = true;
                try { RenderResourceProfiler.stop("app_writer_failure"); } catch (Exception ignored) { }
                Log.w(TAG, "session=" + id + " writer_failed=" + error.getClass().getSimpleName());
            } finally {
                stopRequested = true;
                if (receiver != null) try { context.unregisterReceiver(receiver); } catch (Exception ignored) { }
                if (active == this) active = null;
                Log.i(TAG, "session=" + id + " stopped=1 storage_failure=" + storageFailure);
            }
        }

        boolean write(BoundedOutput output, Map<String, Object> record) throws IOException {
            long wall = SystemClock.elapsedRealtimeNanos(), cpu = Debug.threadCpuTimeNanos();
            try {
                boolean written = output.write(record, false);
                if (written) writtenRecords++;
                return written;
            } finally {
                writerWallNs += Math.max(0, SystemClock.elapsedRealtimeNanos() - wall);
                writerCpuNs += Math.max(0, Debug.threadCpuTimeNanos() - cpu);
            }
        }

        boolean checkpoint(BoundedOutput output, OutputStream stream, long now) throws IOException {
            long wall = SystemClock.elapsedRealtimeNanos(), cpu = Debug.threadCpuTimeNanos();
            try {
                Map<String,Object> record=fields();
                record.put("schema_version",1); record.put("event","app_checkpoint");
                record.put("session",id); record.put("pid",Process.myPid());
                record.put("wall_time_ms",System.currentTimeMillis()); record.put("elapsed_realtime_ns",now);
                record.put("checkpoint_elapsed_ns",Math.max(0,now-started));
                record.put("previous_complete_checkpoint_elapsed_ns",lastCompleteCheckpointElapsedNs);
                record.put("is_terminal",false);
                record.put("summary",RenderResourceProfiler.snapshot());
                record.put("raw_pending_records",queue.size());
                record.put("queue_dropped_records",droppedRecords);
                record.put("file_rejected_records",oversizedRecords);
                record.put("observer_errors",observerErrors.get());
                record.put("checkpoint_build_wall_ns",Math.max(0,SystemClock.elapsedRealtimeNanos()-wall));
                record.put("checkpoint_build_cpu_ns",Math.max(0,Debug.threadCpuTimeNanos()-cpu));
                boolean written=output.writeCheckpoint(record);
                if (written) {
                    stream.flush();
                    writtenRecords++;
                    lastCompleteCheckpointElapsedNs=Math.max(0,now-started);
                }
                return written;
            } finally {
                writerWallNs+=Math.max(0,SystemClock.elapsedRealtimeNanos()-wall);
                writerCpuNs+=Math.max(0,Debug.threadCpuTimeNanos()-cpu);
            }
        }

        void memory(long now) {
            long wall = SystemClock.elapsedRealtimeNanos(), cpu = Debug.threadCpuTimeNanos();
            try {
                Map<String, Object> record = fields();
                record.put("elapsed_realtime_ns", now);
                record.put("process_cpu_ms", Process.getElapsedCpuTime());
                Runtime runtime = Runtime.getRuntime();
                record.put("java_allocated_bytes", runtime.totalMemory() - runtime.freeMemory());
                record.put("java_committed_bytes", runtime.totalMemory());
                record.put("java_max_bytes", runtime.maxMemory());
                record.put("native_allocated_bytes", Debug.getNativeHeapAllocatedSize());
                record.put("native_heap_size_bytes", Debug.getNativeHeapSize());
                record.put("native_heap_free_bytes", Debug.getNativeHeapFreeSize());
                record.put("rss_kb", ownStatus("VmRSS:"));
                record.put("thread_count", ownStatus("Threads:"));
                if (lastPssNs == 0 || now - lastPssNs >= TimeUnit.SECONDS.toNanos(5)) {
                    Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
                    lastPssNs = now;
                    record.put("total_pss_kb", memory.getTotalPss());
                    record.put("native_pss_kb", memory.nativePss); record.put("dalvik_pss_kb", memory.dalvikPss);
                    record.put("graphics_memory_stat_kb", parseLong(memory.getMemoryStat("summary.graphics")));
                    ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (manager != null) {
                        ActivityManager.MemoryInfo system = new ActivityManager.MemoryInfo(); manager.getMemoryInfo(system);
                        record.put("system_available_bytes", system.availMem); record.put("system_total_bytes", system.totalMem);
                        record.put("system_low_memory", system.lowMemory);
                    }
                    record.put("pss_collected", true);
                } else record.put("pss_collected", false);
                if (lastMemoryNs != 0) maxMemoryGapNs = Math.max(maxMemoryGapNs, now - lastMemoryNs);
                lastMemoryNs = now;
                record.put("probe_wall_ns", Math.max(0, SystemClock.elapsedRealtimeNanos() - wall));
                record.put("probe_cpu_ns", Math.max(0, Debug.threadCpuTimeNanos() - cpu));
                RenderResourceProfiler.event("memory", record);
            } catch (Exception ignored) { observerErrors.incrementAndGet(); lastMemoryNs = now; }
            finally {
                memoryProbeWallNs += Math.max(0, SystemClock.elapsedRealtimeNanos() - wall);
                memoryProbeCpuNs += Math.max(0, Debug.threadCpuTimeNanos() - cpu);
            }
        }
    }

    static Long ownStatus(String key) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line; int characters = 0;
            while ((line = reader.readLine()) != null && (characters += line.length()) < 16 * 1024) {
                if (line.startsWith(key)) return parseLong(line.substring(key.length()).trim().split("\\s+")[0]);
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** /proc stat comm may contain spaces and closing parentheses; field 3 starts after the last ')'. */
    static Long parseKernelStartTicks(String stat) {
        if (stat == null || stat.length() > 4096) return null;
        int left = stat.indexOf('('), right = stat.lastIndexOf(')');
        if (left < 1 || right <= left || right + 1 >= stat.length()) return null;
        Long pid = parseLong(stat.substring(0, left).trim());
        if (pid == null || pid <= 0) return null;
        String[] fields = stat.substring(right + 1).trim().split("\\s+");
        if (fields.length < 20 || fields[0].length() != 1 || !Character.isLetter(fields[0].charAt(0))) return null;
        Long ticks = parseLong(fields[19]); // field22 - field3 =19, no clock conversion/rounding.
        return ticks != null && ticks > 0 ? ticks : null;
    }

    static Long kernelStartTicks() {
        try (FileReader reader = new FileReader("/proc/self/stat")) {
            char[] bytes = new char[4097];
            int used = 0, read;
            while (used < bytes.length && (read = reader.read(bytes, used, bytes.length - used)) > 0) used += read;
            return used > 4096 ? null : parseKernelStartTicks(new String(bytes, 0, used));
        } catch (Exception ignored) { return null; }
    }

    static Long clockTickHz() {
        try { long hz = Os.sysconf(OsConstants._SC_CLK_TCK); return hz > 0 ? hz : null; }
        catch (Exception ignored) { return null; }
    }

    static Long parseLong(String value) {
        try { long v = Long.parseLong(value); return v >= 0 ? v : null; } catch (Exception ignored) { return null; }
    }

    static final class FrameAccumulator {
        private final Session session;
        long frames, fullyFrames, invalid, firstNs, lastNs, firstScene, firstView, scene, view, drawCalls;
        long sceneTransitions, viewTransitions;
        double encodingSum, renderingSum, encodingMax, renderingMax;
        final long[] histogram = new long[8];
        final long[] current = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
        final long[] peak = {-1,-1,-1,-1,-1,-1,-1,-1,-1,-1};
        private static final String[] KEYS = {"textures_bytes","buffers_bytes","index_buffers_bytes","vertex_buffers_bytes",
                "uniform_buffers_bytes","active_textures","buffers","draw_calls","texture_update_bytes","buffer_update_bytes"};
        FrameAccumulator(Session session) { this.session = session; }
        synchronized void add(long generation, long sceneSequence, boolean fully, RenderingStats stats, long now) {
            if (frames == 0) { firstNs = now; firstScene = sceneSequence; firstView = generation; }
            else {
                if (scene != sceneSequence) sceneTransitions++;
                if (view != generation) viewTransitions++;
            }
            frames++; if (fully) fullyFrames++;
            lastNs = now; scene = sceneSequence; view = generation;
            Double encoding = secondsToMillis(stats.encodingTime), rendering = secondsToMillis(stats.renderingTime);
            if (encoding == null || rendering == null) invalid++;
            else {
                encodingSum += encoding; renderingSum += rendering;
                encodingMax = Math.max(encodingMax, encoding); renderingMax = Math.max(renderingMax, rendering);
                histogram[bucket(Math.max(encoding, rendering))]++;
            }
            update(0,stats.memTextures); update(1,stats.memBuffers); update(2,stats.memIndexBuffers);
            update(3,stats.memVertexBuffers); update(4,stats.memUniformBuffers); update(5,stats.numActiveTextures);
            update(6,stats.numBuffers); update(7,stats.numDrawCalls); update(8,stats.textureUpdateBytes);
            update(9,stats.bufferUpdateBytes);
            if (stats.numDrawCalls >= 0) drawCalls += stats.numDrawCalls;
        }
        synchronized Map<String, Object> take() {
            if (frames == 0) return null;
            Map<String,Object> out = fields();
            out.put("view_generation", view); out.put("scene_sequence_at_last_frame", scene);
            out.put("view_generation_at_first_frame", firstView); out.put("scene_sequence_at_first_frame", firstScene);
            out.put("scene_transitions", sceneTransitions); out.put("view_transitions", viewTransitions);
            out.put("first_frame_elapsed_ns", firstNs); out.put("last_frame_elapsed_ns", lastNs);
            out.put("frames", frames); out.put("fully_frames", fullyFrames);
            out.put("sdk_encoding_sum_ms", encodingSum); out.put("sdk_rendering_sum_ms", renderingSum);
            out.put("sdk_encoding_max_ms", encodingMax); out.put("sdk_rendering_max_ms", renderingMax);
            out.put("sdk_draw_calls_sum", drawCalls); out.put("invalid_sdk_values", invalid);
            for (int i=0;i<8;i++) out.put("duration_hist_"+i, histogram[i]);
            for (int i=0;i<KEYS.length;i++) {
                out.put("sdk_"+KEYS[i], current[i] < 0 ? null : current[i]);
                out.put("sdk_peak_"+KEYS[i], peak[i] < 0 ? null : peak[i]);
            }
            frames=fullyFrames=invalid=drawCalls=0;
            sceneTransitions=viewTransitions=0;
            encodingSum=renderingSum=encodingMax=renderingMax=0;
            java.util.Arrays.fill(histogram,0); java.util.Arrays.fill(peak,-1);
            return out;
        }
        private void update(int index,long value) {
            current[index]=value>=0?value:-1;
            if(value<0) invalid++;
            else peak[index]=Math.max(peak[index],value);
        }
        void flush(long now) {
            Map<String,Object> out=take();
            if (out != null) RenderResourceProfiler.event("frame_summary",out);
        }
        static int bucket(double ms) {
            double[] bounds={1,4,8,16,32,64,100};
            for(int i=0;i<bounds.length;i++) if(ms<bounds[i]) return i;
            return 7;
        }
    }
}
