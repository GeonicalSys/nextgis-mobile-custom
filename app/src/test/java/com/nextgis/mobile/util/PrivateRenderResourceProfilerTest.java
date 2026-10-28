package com.nextgis.mobile.util;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.maplibre.android.maps.RenderingStats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, manifest = Config.NONE, application = Application.class)
public class PrivateRenderResourceProfilerTest {
    @Test public void sdkSecondsAreConvertedWithoutCallingThemCpuOrGpuTime() {
        assertEquals(12.5, PrivateRenderResourceProfiler.secondsToMillis(.0125), 0);
        assertNull(PrivateRenderResourceProfiler.secondsToMillis(-1));
        assertNull(PrivateRenderResourceProfiler.secondsToMillis(Double.NaN));
        assertNull(PrivateRenderResourceProfiler.secondsToMillis(Double.POSITIVE_INFINITY));
        assertNull(PrivateRenderResourceProfiler.secondsToMillis(Double.MAX_VALUE));
    }

    @Test public void frameSummaryRetainsTotalsPeaksAndInvalidResourceCountersSeparately() {
        PrivateRenderResourceProfiler.FrameAccumulator frames = new PrivateRenderResourceProfiler.FrameAccumulator(null);
        RenderingStats a = new RenderingStats();
        a.encodingTime=.002; a.renderingTime=.018; a.memTextures=100; a.memBuffers=300;
        a.memVertexBuffers=200; a.memIndexBuffers=50; a.memUniformBuffers=30;
        a.numDrawCalls=4; a.numActiveTextures=2; a.numBuffers=3;
        frames.add(7,11,false,a,1_000);
        RenderingStats b = new RenderingStats();
        b.encodingTime=.004; b.renderingTime=.040; b.memTextures=-1; b.memBuffers=250;
        b.memVertexBuffers=150; b.numDrawCalls=6; b.numActiveTextures=1; b.numBuffers=2;
        frames.add(7,11,true,b,2_000);
        Map<String,Object> out=frames.take();
        assertEquals(2L,out.get("frames")); assertEquals(1L,out.get("fully_frames"));
        assertEquals(6.0,(Double)out.get("sdk_encoding_sum_ms"),0);
        assertEquals(58.0,(Double)out.get("sdk_rendering_sum_ms"),0.000001);
        assertEquals(40.0,(Double)out.get("sdk_rendering_max_ms"),0.000001);
        assertEquals(10L,out.get("sdk_draw_calls_sum"));
        assertNull(out.get("sdk_textures_bytes")); assertEquals(100L,out.get("sdk_peak_textures_bytes"));
        assertEquals(250L,out.get("sdk_buffers_bytes")); assertEquals(300L,out.get("sdk_peak_buffers_bytes"));
        assertEquals(150L,out.get("sdk_vertex_buffers_bytes")); // Subcategory not added to aggregate300.
        assertEquals(1L,out.get("invalid_sdk_values"));
        assertEquals(1L,out.get("duration_hist_4")); assertEquals(1L,out.get("duration_hist_5"));
        assertNull(frames.take());
    }

    @Test public void summaryDoesNotInventZeroForEntirelyInvalidMemoryCounter() {
        PrivateRenderResourceProfiler.FrameAccumulator frames = new PrivateRenderResourceProfiler.FrameAccumulator(null);
        RenderingStats stats = new RenderingStats(); stats.memTextures=-4;
        frames.add(1,1,true,stats,3);
        Map<String,Object> out=frames.take();
        assertNull(out.get("sdk_textures_bytes")); assertNull(out.get("sdk_peak_textures_bytes"));
    }

    @Test public void fileBudgetPreservesFooterAndDoesNotWritePartialJsonLine() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        PrivateRenderResourceProfiler.BoundedOutput out=new PrivateRenderResourceProfiler.BoundedOutput(bytes,256,128);
        Map<String,Object> large=new LinkedHashMap<>(); large.put("event","raw"); large.put("data","x".repeat(200));
        assertFalse(out.write(large,false)); assertEquals(0,bytes.size());
        Map<String,Object> footer=new LinkedHashMap<>(); footer.put("event","app_end"); footer.put("complete",true);
        assertTrue(out.write(footer,true));
        String actual=bytes.toString(StandardCharsets.UTF_8);
        assertTrue(actual.endsWith("\n")); assertTrue(actual.contains("app_end"));
        assertTrue(bytes.size()<=256);
    }

    @Test public void storageFailureIsReportedToOwnerRatherThanFalseSuccessfulWrite() {
        OutputStream broken=new OutputStream() { @Override public void write(int value) throws IOException { throw new IOException("technical"); } };
        PrivateRenderResourceProfiler.BoundedOutput out=new PrivateRenderResourceProfiler.BoundedOutput(broken,512,128);
        try { out.write(Map.of("event","technical"),false); fail(); }
        catch(IOException expected) { assertEquals(0,out.bytes); }
    }

    @Test public void largerCheckpointBypassesRawRecordCapWhileStillReservingTheTerminalFooter() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        PrivateRenderResourceProfiler.BoundedOutput out=new PrivateRenderResourceProfiler.BoundedOutput(bytes,1024*1024,256*1024);
        Map<String,Object> checkpoint=Map.of("event","app_checkpoint","technical","x".repeat(160*1024));
        assertFalse(out.write(checkpoint,false));
        assertTrue(out.writeCheckpoint(checkpoint));
        assertTrue(out.bytes<=1024*1024-256*1024);
        assertTrue(out.write(Map.of("event","app_end","complete",true),true));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("app_checkpoint"));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("app_end"));
    }

    @Test public void queueIsBoundedByMemoryAndCountAndAccountsForRefusal() {
        Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PrivateRenderResourceProfiler.Session session=new PrivateRenderResourceProfiler.Session(app);
        for(int i=0;i<512;i++) assertTrue(session.offer(Map.of("event","technical","n",i)));
        assertFalse(session.offer(Map.of("event","overflow")));
        assertEquals(1,session.droppedRecords); assertEquals(512,session.queue.size());
        assertTrue(session.queuedBytes<=PrivateRenderResourceProfiler.MAX_QUEUE_BYTES);
        session.stopRequested=true;
        assertFalse(session.offer(Map.of("event","after_stop")));
    }

    @Test public void arbitraryObjectsAndOversizedValuesDoNotEnterTheWriterQueue() {
        Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PrivateRenderResourceProfiler.Session session=new PrivateRenderResourceProfiler.Session(app);
        assertFalse(session.offer(Map.of("event","technical","bad",new Object())));
        assertFalse(session.offer(Map.of("event","technical","large","x".repeat(1025))));
        assertEquals(0,session.queue.size()); assertEquals(2,session.droppedRecords);
    }

    @Test public void invalidArrayCannotOverflowTheQueueByteAccountIntoASmallAcceptedValue() {
        Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PrivateRenderResourceProfiler.Session session=new PrivateRenderResourceProfiler.Session(app);
        Object[] invalid=new Object[4096]; java.util.Arrays.fill(invalid,new Object());
        assertFalse(session.offer(Map.of("event","technical","nested",invalid)));
        assertEquals(0,session.queuedBytes); assertEquals(0,session.queue.size());
    }

    @Test public void terminalAggregateHasAPriorityFooterSlotEvenAfterTheRawQueueIsFullOrStopped() {
        Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PrivateRenderResourceProfiler.Session session=new PrivateRenderResourceProfiler.Session(app);
        for(int i=0;i<512;i++) assertTrue(session.offer(Map.of("event","technical","n",i)));
        session.stopRequested=true;
        Map<String,Object> terminal=Map.of("event","resource_end","session",session.id,"total_requests",123L);
        assertTrue(session.offer(terminal));
        assertEquals(123L,session.terminalSummary.get("total_requests"));
        assertEquals(0,session.droppedRecords); assertEquals(512,session.queue.size());
    }

    @Test public void anotherSessionsTerminalCannotClaimThisFileOrStopItsWriter() {
        Application app=org.robolectric.RuntimeEnvironment.getApplication();
        PrivateRenderResourceProfiler.Session session=new PrivateRenderResourceProfiler.Session(app);
        assertFalse(session.offer(Map.of("event","resource_end","session","other")));
        assertNull(session.terminalSummary); assertFalse(session.stopRequested);
        assertEquals(1,session.droppedRecords);
    }

    @Test public void oneSecondSummaryDoesNotMisattributeMixedScenesToOnlyTheLastScene() {
        PrivateRenderResourceProfiler.FrameAccumulator frames=new PrivateRenderResourceProfiler.FrameAccumulator(null);
        RenderingStats stats=new RenderingStats();
        frames.add(5,8,false,stats,100);
        frames.add(6,9,true,stats,200);
        Map<String,Object> out=frames.take();
        assertEquals(8L,out.get("scene_sequence_at_first_frame"));
        assertEquals(9L,out.get("scene_sequence_at_last_frame"));
        assertEquals(1L,out.get("scene_transitions")); assertEquals(1L,out.get("view_transitions"));
    }

    @Test public void missingOrNegativeProcAndMemoryValuesRemainUnknown() {
        assertNull(PrivateRenderResourceProfiler.parseLong(null));
        assertNull(PrivateRenderResourceProfiler.parseLong("-1"));
        assertNull(PrivateRenderResourceProfiler.parseLong("unknown"));
        assertEquals(Long.valueOf(0),PrivateRenderResourceProfiler.parseLong("0"));
    }

    @Test public void kernelProcessIdentityUsesField22AfterACommandWithSpacesAndClosingParentheses() {
        String stat="123 (worker ) label) S"+" 0".repeat(18)+" 987654321 123 456";
        assertEquals(Long.valueOf(987654321),PrivateRenderResourceProfiler.parseKernelStartTicks(stat));
    }

    @Test public void missingMalformedOrNegativeKernelIdentityIsNotInventedFromAnotherClock() {
        assertNull(PrivateRenderResourceProfiler.parseKernelStartTicks(null));
        assertNull(PrivateRenderResourceProfiler.parseKernelStartTicks("123 (worker) S 1 2 3"));
        assertNull(PrivateRenderResourceProfiler.parseKernelStartTicks("123 (worker) S"+" 0".repeat(18)+" -5"));
        assertNull(PrivateRenderResourceProfiler.parseKernelStartTicks("123 (worker) S"+" 0".repeat(18)+" unknown"));
        assertNull(PrivateRenderResourceProfiler.parseKernelStartTicks("123 (worker) S"+" 0".repeat(18)+" 0"));
    }
}
