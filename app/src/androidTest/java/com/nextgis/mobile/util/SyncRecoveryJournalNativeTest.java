package com.nextgis.mobile.util;

import android.accounts.Account;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.mobile.BuildConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real Android preferences; the verification package owns every fixture. */
@RunWith(AndroidJUnit4.class)
public class SyncRecoveryJournalNativeTest {
    private Context context;

    @Before public void prepare() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(BuildConfig.FIELD_VERIFICATION);
        assertEquals("com.nextgis.mobile.verification.debug", context.getPackageName());
        assertTrue(context.getSharedPreferences("sync_recovery_journal", Context.MODE_PRIVATE).edit().clear().commit());
    }

    private ProjectSyncRunner.Target target(String id) throws Exception {
        return new ProjectSyncRunner.Target("synthetic-" + id, "Synthetic", context.getCacheDir().getAbsolutePath(), "map");
    }

    private String diskQueue() throws Exception {
        return new String(Files.readAllBytes(new File(context.getApplicationInfo().dataDir,
                "shared_prefs/sync_recovery_journal.xml").toPath()), StandardCharsets.UTF_8);
    }

    private void interrupted(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            Thread.currentThread().interrupt();
            try {
                action.run();
                assertTrue("Cancellation remains visible after durable persistence", Thread.currentThread().isInterrupted());
            } catch (Throwable error) { failure.set(error); }
        }, "synthetic-cancelled-sync");
        caller.start(); caller.join(15000);
        assertFalse("Journal commit must finish", caller.isAlive());
        if (failure.get() != null) throw new AssertionError("Interrupted account sync could not persist its journal", failure.get());
    }

    @Test public void interruptedEnqueueCommitsThePendingPairToDiskAndRestoresCancellation() throws Exception {
        ProjectSyncRunner.Target target = target("enqueue");
        Account account = new Account("synthetic-account", "synthetic-journal");
        interrupted(() -> {
            try { SyncRecoveryJournal.enqueueOrThrow(context, List.of(target), List.of(account), true); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        assertEquals(1, SyncRecoveryJournal.load(context).length());
        assertTrue(diskQueue().contains("synthetic-enqueue"));
        assertTrue(diskQueue().contains("synthetic-account"));
    }

    @Test public void interruptedAcknowledgmentRetainsTheOtherPendingPairOnDisk() throws Exception {
        ProjectSyncRunner.Target finished = target("finished"), pending = target("pending");
        Account account = new Account("synthetic-account", "synthetic-journal");
        SyncRecoveryJournal.enqueueOrThrow(context, List.of(finished, pending), List.of(account), false);
        interrupted(() -> assertTrue(SyncRecoveryJournal.complete(context, finished, account.name)));
        assertEquals(1, SyncRecoveryJournal.load(context).length());
        assertTrue(diskQueue().contains("synthetic-pending"));
        assertFalse(diskQueue().contains("synthetic-finished"));
    }
}
