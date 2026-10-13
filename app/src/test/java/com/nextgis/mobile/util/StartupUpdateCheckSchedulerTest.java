package com.nextgis.mobile.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class StartupUpdateCheckSchedulerTest {
    @Test public void resumeBeforeLifecycleDispatchDoesNotLoseTheCheck() {
        Fixture f = new Fixture();
        f.host.ready = false;
        f.scheduler.onResume();
        assertNotNull(f.host.task);
        f.host.ready = true;
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void failedRequestKeepsPendingAndRetriesWithoutRestart() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.finish(false);
        assertFalse(f.scheduler.isComplete());
        assertEquals(10000, f.host.delay);
        f.runNext();
        assertEquals(2, f.host.checks);
        f.finish(true);
        assertTrue(f.scheduler.isComplete());
        assertNull(f.host.task);
    }

    @Test public void restoredWindowInANewProcessChecksEvenIfOldProcessCompleted() {
        Fixture f = new Fixture();
        f.scheduler.restore(false, true);
        f.scheduler.onResume();
        f.runNext();
        assertEquals(1, f.host.checks);
        assertFalse(f.scheduler.isComplete());
    }

    @Test public void sameProcessRecreationPreservesCompletionAndDoesNotRepeatOffer() {
        Fixture f = new Fixture();
        f.scheduler.restore(true, true);
        f.scheduler.onResume();
        f.scheduler.onWindowFocusChanged(true);
        f.scheduler.onNetworkChanged();
        assertTrue(f.scheduler.isComplete());
        assertNull(f.host.task);
        assertEquals(0, f.host.checks);
    }

    @Test public void unfinishedCheckIsRetriedAfterRecreationInTheSameProcess() {
        Fixture f = new Fixture();
        f.scheduler.restore(true, false);
        f.scheduler.onResume();
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void restoredNetworkBringsRetryForward() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.finish(false);
        long offlineRetryDue = f.host.due;
        f.scheduler.onNetworkChanged();
        assertTrue(f.host.due < offlineRetryDue);
        f.runNext();
        assertEquals(2, f.host.checks);
    }

    @Test public void networkAndFocusEventsCannotPostponeAnAlreadyScheduledAttempt() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        long due = f.host.due;
        f.host.now += 1000;
        f.scheduler.onNetworkChanged();
        f.scheduler.onWindowFocusChanged(true);
        assertEquals(due, f.host.due);
    }

    @Test public void modalWindowDefersCheckUntilFocusReturns() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.host.ready = false;
        f.runNext();
        assertEquals(0, f.host.checks);
        assertFalse(f.scheduler.isComplete());
        assertNull(f.host.task);
        f.host.ready = true;
        f.scheduler.onWindowFocusChanged(true);
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void busyUpdaterIsRetriedWithoutConsumingStartupCheck() {
        Fixture f = new Fixture();
        f.host.busy = true;
        f.scheduler.onResume();
        f.runNext();
        assertEquals(0, f.host.checks);
        assertFalse(f.scheduler.isComplete());
        assertNotNull(f.host.task);
        f.host.busy = false;
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void companionOfferDoesNotDiscardApplicationUpdateCheck() {
        Fixture f = new Fixture();
        f.host.companion = true;
        f.scheduler.onResume();
        f.runNext();
        assertEquals(0, f.host.checks);
        assertFalse(f.scheduler.isComplete());
        f.host.companion = false;
        f.scheduler.onWindowFocusChanged(true);
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void pauseCancelsTimerAndBackgroundNetworkEventsDoNotStartRequests() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.scheduler.onPause();
        f.scheduler.onNetworkChanged();
        assertNull(f.host.task);
        assertEquals(0, f.host.checks);
        f.scheduler.onResume();
        f.runNext();
        assertEquals(1, f.host.checks);
    }

    @Test public void responseDeferredInBackgroundIsRetriedOnResume() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.scheduler.onPause();
        f.finish(false);
        assertNull(f.host.task);
        assertFalse(f.scheduler.isComplete());
        f.scheduler.onResume();
        f.runNext();
        assertEquals(2, f.host.checks);
    }

    @Test public void inFlightRequestDoesNotDuplicateAfterResumeOrNetworkChanges() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.scheduler.onPause();
        f.scheduler.onResume();
        f.scheduler.onNetworkChanged();
        f.scheduler.onWindowFocusChanged(true);
        assertEquals(1, f.host.checks);
        assertNull(f.host.task);
    }

    @Test public void retriesBackOffToAtMostOneRequestEveryFiveMinutes() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        long[] delays = {10000, 30000, 120000, 300000, 300000};
        for (long expected : delays) {
            f.runNext();
            f.finish(false);
            assertEquals(expected, f.host.delay);
        }
    }

    @Test public void closedActivityIgnoresLateResponseAndNetworkCallbacks() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.scheduler.close();
        f.finish(false);
        f.scheduler.onResume();
        f.scheduler.onNetworkChanged();
        assertNull(f.host.task);
        assertEquals(1, f.host.checks);
    }

    @Test public void installContinuationStopsRetriesAndIgnoresAnOlderFailedRequest() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        f.scheduler.complete("installation_continuation");
        f.finish(false);
        assertTrue(f.scheduler.isComplete());
        assertNull(f.host.task);
    }

    @Test public void duplicateCompletionCannotStartAnotherRetry() {
        Fixture f = new Fixture();
        f.scheduler.onResume();
        f.runNext();
        StartupUpdateCheckScheduler.Completion first = f.host.completion;
        first.finish(false);
        f.runNext();
        first.finish(true);
        assertFalse(f.scheduler.isComplete());
        f.finish(true);
        assertTrue(f.scheduler.isComplete());
    }

    @Test public void diagnosticEventsExplainRestoreBusyFailureAndRetryCompletion() {
        Fixture f = new Fixture();
        f.scheduler.restore(false, true);
        f.scheduler.onResume();
        f.host.busy = true;
        f.runNext();
        f.host.busy = false;
        f.runNext();
        f.finish(false);
        f.runNext();
        f.finish(true);
        String log = String.join("\n", f.host.events);
        assertTrue(log.contains("sameProcess=false completed=true pending=true"));
        assertTrue(log.contains("reason=updater_busy_or_install_pending"));
        assertTrue(log.contains("retry_needed attempt=1"));
        assertTrue(log.contains("completed reason=valid_result attempt=2"));
    }

    private static final class Fixture {
        final FakeHost host = new FakeHost();
        final StartupUpdateCheckScheduler scheduler = new StartupUpdateCheckScheduler(host);

        void runNext() {
            assertNotNull("Expected a pending check", host.task);
            Runnable next = host.task;
            host.task = null;
            host.now = host.due;
            next.run();
        }

        void finish(boolean completed) {
            assertNotNull(host.completion);
            host.completion.finish(completed);
        }
    }

    private static final class FakeHost implements StartupUpdateCheckScheduler.Host {
        long now, due, delay;
        boolean ready = true, busy, companion;
        int checks;
        Runnable task;
        StartupUpdateCheckScheduler.Completion completion;
        List<String> events = new ArrayList<>();
        public long now() { return now; }
        public boolean isReady() { return ready; }
        public boolean isBusy() { return busy; }
        public boolean continueCompanion() { return companion; }
        public void check(StartupUpdateCheckScheduler.Completion callback) { checks++; completion = callback; }
        public void postDelayed(Runnable action, long delayMs) { task = action; delay = delayMs; due = now + delayMs; }
        public void removeCallbacks(Runnable action) { if (task == action) task = null; }
        public void log(String event) { events.add(event); }
    }
}
