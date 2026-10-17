package com.nextgis.mobile.util;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Journal durability must not depend on the account-sync caller's cancellation flag. */
final class SyncJournalCommit {
    // Journal callers already serialize their read/modify/write transaction. Only one write can queue.
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SyncRecoveryJournalDisk");
        thread.setDaemon(true);
        return thread;
    });

    private SyncJournalCommit() { }

    static boolean commit(Callable<Boolean> operation) throws IOException {
        boolean interrupted = Thread.interrupted();
        try {
            Future<Boolean> result = WRITER.submit(operation);
            while (true) {
                try { return result.get(); }
                catch (InterruptedException cancelled) { interrupted = true; }
                catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                    if (cause instanceof Error) throw (Error) cause;
                    throw new IOException("Sync journal disk writer failed", cause);
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
