package com.nextgis.mobile.util;

import android.accounts.Account;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SyncRecoveryJournalTest {
    private Context context;
    private List<ProjectSyncRunner.Target> targets;
    private final List<Account> accounts = List.of(new Account("synthetic", "synthetic"));
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("sync_recovery_journal", 0).edit().clear().commit();
        targets = List.of(new ProjectSyncRunner.Target("uid", "synthetic", context.getFilesDir().getPath(), "default"));
    }

    @Test public void normalQueueDoesNotReadAnUnneededLegacyInventoryAndRetainsPairs() throws Exception {
        // Application is deliberately not GISApplication: the legacy inventory must not be touched.
        SyncRecoveryJournal.enqueueOrThrow(context, targets, accounts, false);
        SyncRecoveryJournal.enqueueOrThrow(context, targets, accounts, true);
        assertEquals(1, SyncRecoveryJournal.load(context).length());
        assertTrue(SyncRecoveryJournal.load(context).getJSONObject(0).getBoolean("manual"));
    }

    @Test public void corruptExistingQueueReportsTheParsingCauseWithoutDiscardingTheJournal() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("sync_recovery_journal", 0);
        prefs.edit().putString("pending_v2", "{broken").commit();
        try {
            SyncRecoveryJournal.enqueueOrThrow(context, targets, accounts, false); fail();
        } catch (IOException error) {
            assertTrue(error.getMessage().contains("phase=read_queue"));
            assertTrue(error.getCause() instanceof org.json.JSONException);
            assertTrue(error.getMessage().contains("projects=1 accounts=1"));
        }
        assertEquals("{broken", prefs.getString("pending_v2", ""));
    }

    @Test public void failedDiskCommitKeepsItsOwnReasonAndStorageDetails() throws Exception {
        SharedPreferences real = context.getSharedPreferences("sync_recovery_journal", 0);
        Context failing = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return (SharedPreferences) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.class},
                        (proxy, method, args) -> {
                            if (!"edit".equals(method.getName())) return method.invoke(real, args);
                            SharedPreferences.Editor editor = real.edit();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SharedPreferences.Editor.class},
                                    (wrapped, editMethod, editArgs) -> {
                                        if ("commit".equals(editMethod.getName())) return false;
                                        Object value = editMethod.invoke(editor, editArgs);
                                        return value instanceof SharedPreferences.Editor ? wrapped : value;
                                    });
                        });
            }
        };
        try {
            SyncRecoveryJournal.enqueueOrThrow(failing, targets, accounts, false); fail();
        } catch (IOException error) {
            assertTrue(error.getMessage().contains("phase=commit"));
            assertTrue(error.getMessage().contains("storageAvailableBytes="));
            assertTrue(error.getCause().getMessage().contains("commit returned false"));
            assertTrue(error instanceof SyncRecoveryJournal.PersistenceFailure);
            java.util.Map<String, Object> diagnostics = ((SyncRecoveryJournal.PersistenceFailure) error).diagnostics;
            assertEquals("commit", diagnostics.get("phase"));
            assertEquals("owned_thread", diagnostics.get("write_mode"));
            assertEquals(false, diagnostics.get("caller_interrupted_at_entry"));
            assertTrue(((Number) diagnostics.get("storage_available_bytes")).longValue() >= 0);
        }
        assertEquals("[]", real.getString("pending_v2", "[]"));
    }
}
