package com.nextgis.mobile.util;

import java.util.UUID;

/** Foreground-only update retries. All methods and completions run on the main thread. */
public final class StartupUpdateCheckScheduler {
    public static final String PROCESS_SESSION = UUID.randomUUID().toString();
    private static final long START_DELAY_MS = 2500;
    private static final long BUSY_DELAY_MS = 5000;
    private static final long[] RETRY_DELAYS_MS = {10000, 30000, 120000, 300000};

    public interface Completion {
        /** True only after a valid no-update result or an update offer actually shown. */
        void finish(boolean completed);
    }

    public interface Host {
        long now();
        boolean isReady();
        boolean isBusy();
        boolean continueCompanion();
        void check(Completion completion);
        void postDelayed(Runnable action, long delayMs);
        void removeCallbacks(Runnable action);
        void log(String event);
    }

    private final Host host;
    private final Runnable checkTask = this::attempt;
    private boolean pending = true;
    private boolean resumed;
    private boolean closed;
    private boolean inFlight;
    private int retryIndex;
    private int attemptNumber;
    private long scheduledAt = Long.MAX_VALUE;

    public StartupUpdateCheckScheduler(Host host) {
        this.host = host;
    }

    public void restore(boolean sameProcess, boolean completed) {
        pending = !sameProcess || !completed;
        host.log("restore sameProcess=" + sameProcess + " completed=" + completed
                + " pending=" + pending);
    }

    public void onResume() {
        resumed = true;
        schedule(START_DELAY_MS, "resume");
    }

    public void onPause() {
        resumed = false;
        cancelScheduled();
        host.log("paused pending=" + pending + " inFlight=" + inFlight);
    }

    public void onWindowFocusChanged(boolean focused) {
        if (focused) schedule(START_DELAY_MS, "window_focus");
        else cancelScheduled();
    }

    public void onNetworkChanged() {
        retryIndex = 0;
        schedule(START_DELAY_MS, "network_changed");
    }

    public void complete(String reason) {
        pending = false;
        cancelScheduled();
        host.log("completed reason=" + reason + " attempt=" + attemptNumber);
    }

    public boolean isComplete() {
        return !pending;
    }

    public void close() {
        closed = true;
        resumed = false;
        cancelScheduled();
        host.log("closed pending=" + pending + " inFlight=" + inFlight);
    }

    private void attempt() {
        scheduledAt = Long.MAX_VALUE;
        if (closed || !resumed || !pending || inFlight) return;
        if (!host.isReady()) {
            host.log("deferred reason=window_not_ready");
            return; // Focus/resume will schedule again; do not consume the attempt.
        }
        if (host.isBusy()) {
            schedule(BUSY_DELAY_MS, "updater_busy_or_install_pending");
            return;
        }
        if (host.continueCompanion()) {
            schedule(BUSY_DELAY_MS, "companion_ui");
            return;
        }
        inFlight = true;
        int currentAttempt = ++attemptNumber;
        host.log("attempt=" + currentAttempt);
        host.check(completed -> {
            if (!inFlight || currentAttempt != attemptNumber) return;
            inFlight = false;
            if (closed || !pending) return;
            if (completed) {
                complete("valid_result");
            } else {
                long delay = RETRY_DELAYS_MS[retryIndex];
                retryIndex = Math.min(retryIndex + 1, RETRY_DELAYS_MS.length - 1);
                host.log("retry_needed attempt=" + currentAttempt + " delayMs=" + delay);
                schedule(delay, "retry");
            }
        });
    }

    private void schedule(long delayMs, String reason) {
        if (closed || !resumed || !pending || inFlight) return;
        // onResume precedes Lifecycle's RESUMED dispatch and may precede window focus.
        // Check readiness when the delayed task runs instead of losing this wakeup.
        long due = host.now() + delayMs;
        if (due >= scheduledAt) return; // Focus/network events must not postpone a retry.
        cancelScheduled();
        scheduledAt = due;
        host.log("scheduled reason=" + reason + " delayMs=" + delayMs);
        host.postDelayed(checkTask, delayMs);
    }

    private void cancelScheduled() {
        host.removeCallbacks(checkTask);
        scheduledAt = Long.MAX_VALUE;
    }
}
