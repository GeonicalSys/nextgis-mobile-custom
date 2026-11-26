package com.nextgis.mobile.util;

import android.app.Application;
import android.os.Looper;
import com.hypertrack.hyperlog.HyperLog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
public class DefaultBasemapInitializerTest {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Main main = new Main();

    @Before public void initializeLogging() { HyperLog.initialize(RuntimeEnvironment.getApplication()); }

    @After public void stopWorker() throws Exception {
        worker.shutdownNow();
        assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
    }

    static final class Main implements DefaultBasemapInitializer.MainDispatcher {
        final Queue<Runnable> ready = new ConcurrentLinkedQueue<>(), delayed = new ConcurrentLinkedQueue<>();
        @Override public void post(Runnable task) { ready.add(task); }
        @Override public void later(Runnable task) { delayed.add(task); }
        void drain() { for (Runnable task; (task = ready.poll()) != null; ) task.run(); }
        void retry() { ready.addAll(delayed); delayed.clear(); }
    }

    static final class Work implements DefaultBasemapInitializer.Work {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CountDownLatch advance = new CountDownLatch(1), advanced = new CountDownLatch(1);
        final AtomicInteger progressRevision = new AtomicInteger();
        boolean owned = true, leaseAvailable = true, block, observeProgress;
        Exception extractionFailure, publicationFailure;
        int closes, publications, cleanups, acquisitions;
        Thread extractionThread, publicationThread, cleanupThread;
        @Override public boolean ownsDestination() { return owned; }
        @Override public DefaultBasemapInitializer.Lease acquire() {
            acquisitions++;
            if (!leaseAvailable) return null;
            return new DefaultBasemapInitializer.Lease() {
                @Override public boolean awaitAccess() { return true; }
                @Override public void heartbeat() { }
                @Override public void close() { closes++; }
            };
        }
        @Override public void extract(AtomicBoolean cancelled) throws Exception {
            extractionThread = Thread.currentThread();
            if (observeProgress) progressRevision.incrementAndGet();
            entered.countDown();
            if (observeProgress) {
                if (!advance.await(5, TimeUnit.SECONDS)) throw new IOException("Fixture progress timed out");
                progressRevision.incrementAndGet(); advanced.countDown();
            }
            if (block && !release.await(5, TimeUnit.SECONDS)) throw new IOException("Fixture extraction timed out");
            if (extractionFailure != null) throw extractionFailure;
        }
        @Override public void publish() throws Exception {
            publicationThread = Thread.currentThread(); publications++;
            if (publicationFailure != null) throw publicationFailure;
        }
        @Override public void cleanup() { cleanupThread = Thread.currentThread(); cleanups++; }
        @Override public int archiveProgressRevision() { return progressRevision.get(); }
    }

    private void complete(DefaultBasemapInitializer initializer) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!initializer.snapshot().completed && System.nanoTime() < deadline) {
            main.drain(); Thread.sleep(1);
        }
        main.drain(); assertTrue("Startup must reach its terminal state", initializer.snapshot().completed);
    }

    @Test public void realWorkerLeavesMainResponsiveUntilMetadataAndCleanupFinish() throws Exception {
        Work work = new Work(); work.block = true;
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); main.drain(); assertTrue(work.entered.await(2, TimeUnit.SECONDS));
        AtomicBoolean heartbeat = new AtomicBoolean(); main.post(() -> heartbeat.set(true)); main.drain();
        assertTrue(heartbeat.get()); assertTrue(initializer.snapshot().mainHeartbeatDuringExtraction);
        assertFalse(initializer.snapshot().completed); assertEquals(0, work.publications); assertEquals(0, work.closes);
        work.release.countDown(); complete(initializer);
        assertTrue(initializer.snapshot().succeeded); assertTrue(initializer.snapshot().stagingCleaned);
        assertNotSame(Thread.currentThread(), work.extractionThread); assertNotSame(Thread.currentThread(), work.cleanupThread);
        assertSame(Thread.currentThread(), work.publicationThread); assertEquals(1, work.closes);
    }

    @Test public void replacedOwnerCannotReceiveTheCompletedArchive() throws Exception {
        Work work = new Work(); work.block = true;
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); main.drain(); assertTrue(work.entered.await(2, TimeUnit.SECONDS));
        work.owned = false; work.release.countDown(); complete(initializer);
        assertTrue(initializer.snapshot().cancelled); assertFalse(initializer.snapshot().succeeded);
        assertEquals(0, work.publications); assertEquals(1, work.cleanups); assertEquals(1, work.closes);
    }

    @Test public void progressCanAdvanceWhileMainDispatchesAndTheOwnedLeaseStaysHeld() throws Exception {
        Work work = new Work(); work.block = true; work.observeProgress = true;
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); main.drain(); assertTrue(work.entered.await(2, TimeUnit.SECONDS));
        assertEquals(1, initializer.snapshot().archiveProgressRevision);
        AtomicInteger heartbeats = new AtomicInteger(); main.post(heartbeats::incrementAndGet); main.drain();
        work.advance.countDown(); assertTrue(work.advanced.await(2, TimeUnit.SECONDS));
        main.post(heartbeats::incrementAndGet); main.drain();
        assertEquals(2, heartbeats.get()); assertEquals(2, initializer.snapshot().archiveProgressRevision);
        assertFalse(initializer.snapshot().completed); assertEquals(0, work.publications); assertEquals(0, work.closes);
        work.release.countDown(); complete(initializer);
        assertTrue(initializer.snapshot().succeeded); assertTrue(initializer.snapshot().stagingCleaned);
        assertEquals(2, initializer.snapshot().archiveProgressRevision); assertEquals(1, work.closes);
    }

    @Test public void noLongerOwnedBeforeDispatchDoesNotAcquireOrExtract() {
        Work work = new Work(); work.owned = false;
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); main.drain();
        assertTrue(initializer.snapshot().cancelled); assertTrue(initializer.snapshot().completed);
        assertEquals(0, work.acquisitions); assertNull(work.extractionThread);
    }

    @Test public void extractionFailureSkipsMetadataAndStillClosesOwnedWork() throws Exception {
        Work work = new Work(); work.extractionFailure = new IOException("Synthetic archive error");
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); complete(initializer);
        assertEquals("IOException", initializer.snapshot().failureType); assertFalse(initializer.snapshot().succeeded);
        assertEquals(0, work.publications); assertEquals(1, work.cleanups); assertEquals(1, work.closes);
    }

    @Test public void metadataFailureDoesNotLeakTheWorkspaceLeaseOrStage() throws Exception {
        Work work = new Work(); work.publicationFailure = new IOException("Synthetic metadata error");
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); complete(initializer);
        assertEquals("IOException", initializer.snapshot().failureType);
        assertEquals(1, work.publications); assertEquals(1, work.cleanups); assertEquals(1, work.closes);
    }

    @Test public void busyWorkspaceIsDeferredWithoutBlockingMain() throws Exception {
        Work work = new Work(); work.leaseAvailable = false;
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        initializer.schedule(work); main.drain();
        assertEquals(1, main.delayed.size()); assertNull(work.extractionThread); assertFalse(initializer.snapshot().completed);
        work.leaseAvailable = true; main.retry(); complete(initializer);
        assertTrue(initializer.snapshot().succeeded); assertEquals(1, work.closes);
    }

    @Test public void rejectedWorkerCannotKeepAnAcquiredWorkspaceLease() {
        Work work = new Work(); Executor rejected = task -> { throw new RejectedExecutionException("Synthetic worker rejection"); };
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(rejected, main);
        initializer.schedule(work); main.drain();
        assertTrue(initializer.snapshot().completed); assertEquals("RejectedExecutionException", initializer.snapshot().failureType);
        assertEquals(1, work.closes); assertNull(work.extractionThread);
    }

    @Test public void readinessCannotBlockTheApplicationMainThread() {
        assertSame(Looper.getMainLooper(), Looper.myLooper());
        DefaultBasemapInitializer initializer = new DefaultBasemapInitializer(worker, main);
        assertThrows(IllegalStateException.class, () -> initializer.awaitIdle(1));
    }
}
