package com.nextgis.mobile.util;

import org.junit.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class SyncJournalCommitTest {
    private static void finish(Thread caller, AtomicReference<Throwable> failure) throws Exception {
        caller.join(5000);
        assertFalse("Durable write must finish", caller.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    @Test public void interruptedCallerDoesNotInterruptTheDiskWriter() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                assertTrue(SyncJournalCommit.commit(() -> {
                    assertFalse(Thread.currentThread().isInterrupted());
                    assertEquals("SyncRecoveryJournalDisk", Thread.currentThread().getName());
                    return true;
                }));
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Throwable error) { failure.set(error); }
        });
        caller.start(); finish(caller, failure);
    }

    @Test public void cancellationDuringTheWriteWaitsForDurabilityAndRemainsVisible() throws Exception {
        CountDownLatch writing = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                assertTrue(SyncJournalCommit.commit(() -> {
                    writing.countDown();
                    assertTrue(release.await(4, TimeUnit.SECONDS));
                    assertFalse(Thread.currentThread().isInterrupted());
                    return true;
                }));
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Throwable error) { failure.set(error); }
        });
        caller.start();
        try { assertTrue(writing.await(3, TimeUnit.SECONDS)); caller.interrupt(); }
        finally { release.countDown(); }
        finish(caller, failure);
    }

    @Test public void genuineDiskFailureCannotBecomeSuccessBecauseTheCallerWasCancelled() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                assertFalse(SyncJournalCommit.commit(() -> false));
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Throwable error) { failure.set(error); }
        });
        caller.start(); finish(caller, failure);
    }
}
