package com.nextgis.mobile.util;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import androidx.preference.PreferenceManager;
import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.mobile.MainApplication;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentResolver;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Actual journal APIs; generic cache/disk preferences, no GIS database or network. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = SyncRecoveryJournalConfirmationTest.ModelApplication.class,
        shadows = SyncRecoveryJournalConfirmationTest.QuietLog.class)
public class SyncRecoveryJournalConfirmationTest {
    private static final String PREFS = "sync_recovery_journal", QUEUE = "pending_v2";

    @Implements(HyperLog.class)
    public static class QuietLog {
        @Implementation protected static void e(String tag, String message) { }
        @Implementation protected static void e(String tag, String message, Throwable error) { }
        @Implementation protected static void w(String tag, String message) { }
        @Implementation protected static void w(String tag, String message, Throwable error) { }
        @Implementation protected static void v(String tag, String message) { }
    }

    public static class ModelApplication extends MainApplication {
        FakePreferences journal;
        MapBase model;
        @Override public void onCreate() { /* No production bootstrap, workers or migrations. */ }
        @Override public synchronized MapBase getMap() { return model; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return PREFS.equals(name) && journal != null ? journal : super.getSharedPreferences(name, mode);
        }
        @Override public void onTerminate() { }
    }

    static class ModelMap extends MapDrawable {
        ModelMap(Context context, File file) {
            super(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), context, file, null);
        }
        @Override public SQLiteDatabase getDatabase(boolean readOnly) {
            throw new AssertionError("Journal models must not open the database engine");
        }
        static void restoreActive(MapBase previous) { mInstance = previous; }
    }

    enum Outcome { TRUE, FALSE_CACHE_ONLY, FALSE_AFTER_DISK, RUNTIME_AFTER_CACHE, ERROR_AFTER_CACHE }

    /** Generic SharedPreferences protocol, deliberately independent of journal JSON semantics. */
    static final class FakePreferences implements SharedPreferences {
        final Map<String, Object> cache = new HashMap<>(), disk = new HashMap<>();
        volatile Outcome outcome = Outcome.TRUE;
        volatile CountDownLatch published, finishWrite;
        volatile ReadGate readGate;
        volatile RuntimeException readFailure;
        int commits;
        boolean hostileEquality;
        final RuntimeException runtime = new IllegalStateException("technical post-memory failure");
        final Error fatal = new AssertionError("technical post-memory fatal");

        static Map<String, Object> copy(Map<String, Object> source) {
            Map<String, Object> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                Object value = entry.getValue();
                result.put(entry.getKey(), value instanceof Set ? new HashSet<>((Set<?>) value) : value);
            }
            return result;
        }
        synchronized void seed(Map<String, Object> values) {
            cache.clear(); cache.putAll(copy(values)); disk.clear(); disk.putAll(copy(values));
        }
        synchronized Map<String, Object> raw() { return copy(cache); }
        // Android getAll copies the map, but its Set values are still shared.
        private synchronized Map<String, Object> frameworkMap() { return new HashMap<>(cache); }
        private Map<String, Object> read() {
            ReadGate gate = readGate;
            if (gate != null && Thread.currentThread().getName().equals(gate.reader)) {
                gate.entered.countDown(); await(gate.capture);
                Map<String, Object> captured = frameworkMap(); gate.captured.countDown(); await(gate.returnRead);
                if (gate.error != null) throw gate.error;
                return captured;
            }
            if (readFailure != null) throw readFailure;
            return frameworkMap();
        }
        @Override public Map<String, ?> getAll() { return read(); }
        @Override public String getString(String key, String value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (String) values.get(key) : value;
        }
        @SuppressWarnings("unchecked") @Override public Set<String> getStringSet(String key, Set<String> value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (Set<String>) values.get(key) : value;
        }
        @Override public int getInt(String key, int value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (Integer) values.get(key) : value;
        }
        @Override public long getLong(String key, long value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (Long) values.get(key) : value;
        }
        @Override public float getFloat(String key, float value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (Float) values.get(key) : value;
        }
        @Override public boolean getBoolean(String key, boolean value) {
            Map<String, Object> values = read(); return values.containsKey(key) ? (Boolean) values.get(key) : value;
        }
        @Override public boolean contains(String key) { return read().containsKey(key); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { }
        @Override public boolean equals(Object other) { return hostileEquality ? other instanceof FakePreferences : this == other; }
        @Override public int hashCode() { return hostileEquality ? 1 : System.identityHashCode(this); }
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, Object> changes = new HashMap<>();
                final Set<String> removed = new HashSet<>();
                boolean clear;
                private Editor put(String key, Object value) {
                    if (value == null) return remove(key);
                    changes.put(key, value); removed.remove(key); return this;
                }
                @Override public Editor putString(String key, String value) { return put(key, value); }
                @Override public Editor putStringSet(String key, Set<String> value) { return put(key, value == null ? null : new HashSet<>(value)); }
                @Override public Editor putInt(String key, int value) { return put(key, value); }
                @Override public Editor putLong(String key, long value) { return put(key, value); }
                @Override public Editor putFloat(String key, float value) { return put(key, value); }
                @Override public Editor putBoolean(String key, boolean value) { return put(key, value); }
                @Override public Editor remove(String key) { changes.remove(key); removed.add(key); return this; }
                @Override public Editor clear() { clear = true; return this; }
                @Override public void apply() { commit(); }
                @Override public boolean commit() {
                    Map<String, Object> wholeFile;
                    Outcome result = outcome;
                    synchronized (FakePreferences.this) {
                        commits++;
                        if (clear) cache.clear();
                        for (String key : removed) cache.remove(key);
                        cache.putAll(copy(changes));
                        wholeFile = copy(cache);
                    }
                    if (published != null) published.countDown();
                    if (finishWrite != null) await(finishWrite);
                    if (result == Outcome.RUNTIME_AFTER_CACHE) throw runtime;
                    if (result == Outcome.ERROR_AFTER_CACHE) throw fatal;
                    if (result == Outcome.TRUE || result == Outcome.FALSE_AFTER_DISK) {
                        synchronized (FakePreferences.this) { disk.clear(); disk.putAll(wholeFile); }
                    }
                    return result == Outcome.TRUE;
                }
            };
        }
    }

    static final class ReadGate {
        final String reader;
        final CountDownLatch entered = new CountDownLatch(1), capture = new CountDownLatch(1),
                captured = new CountDownLatch(1), returnRead = new CountDownLatch(1);
        RuntimeException error;
        ReadGate(String reader) { this.reader = reader; }
    }

    private ModelApplication app;
    private ModelMap map;
    private MapBase previousMap;
    private FakePreferences prefs;
    private ProjectSyncRunner.Target target;
    private Account alpha, beta;
    private final List<Thread> threads = new ArrayList<>();
    private final List<CountDownLatch> releases = new ArrayList<>();

    @Before public void setUp() throws Exception {
        app = (ModelApplication) RuntimeEnvironment.getApplication();
        prefs = new FakePreferences(); app.journal = prefs;
        previousMap = MapBase.getActiveInstance();
        map = new ModelMap(app, new File(app.getFilesDir(), "journal-model/map.ngm"));
        map.setName("Journal model"); app.model = map;
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "journal-model").commit();
        target = ProjectSyncRunner.inventory(app).get(0);
        alpha = new Account("alpha", app.getAccountsType()); beta = new Account("beta", app.getAccountsType());
    }

    @After public void tearDown() throws Exception {
        for (CountDownLatch release : releases) release.countDown();
        for (Thread thread : threads) { thread.join(5_000); assertFalse("Owned technical thread must finish", thread.isAlive()); }
        ModelMap.restoreActive(previousMap);
        if (map != null) map.closeSyncWorkspace();
    }

    private static void await(CountDownLatch gate) {
        try { assertTrue("Technical test gate timed out", gate.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private Thread start(String name, Runnable action) {
        Thread thread = new Thread(action, name); threads.add(thread); thread.start(); return thread;
    }
    private void enqueue(boolean manual, Account... accounts) throws IOException {
        SyncRecoveryJournal.enqueueOrThrow(app, List.of(target), Arrays.asList(accounts), manual);
    }
    private JSONArray pending() throws JSONException { return SyncRecoveryJournal.load(app); }
    private static void assertAccounts(JSONArray actual, String... accounts) throws JSONException {
        Set<String> found = new HashSet<>();
        for (int i = 0; i < actual.length(); i++) found.add(actual.getJSONObject(i).getString("account"));
        assertEquals(new HashSet<>(Arrays.asList(accounts)), found); assertEquals(accounts.length, actual.length());
    }
    private static JSONObject entry(JSONArray values, String account) throws JSONException {
        for (int i = 0; i < values.length(); i++) if (account.equals(values.getJSONObject(i).getString("account"))) return values.getJSONObject(i);
        throw new AssertionError("Missing technical account " + account);
    }
    private void rejectedEnqueue(Account... accounts) throws Exception {
        try { enqueue(true, accounts); fail("Write must remain rejected"); }
        catch (SyncRecoveryJournal.PersistenceFailure error) { assertEquals("commit", error.diagnostics.get("phase")); }
    }
    private int requests(Account account) {
        ShadowContentResolver.Status status = ShadowContentResolver.getStatus(account, app.getAuthority(), false);
        return status == null ? 0 : status.syncRequests;
    }

    @Test public void falseAckKeepsBothPairsDespiteChangedFrameworkCache() throws Exception {
        enqueue(false, alpha, beta);
        long originalTime = entry(pending(), alpha.name).getLong("started_at");
        prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
        assertAccounts(new JSONArray((String) prefs.raw().get(QUEUE)), beta.name);
        assertAccounts(pending(), alpha.name, beta.name);
        assertEquals(originalTime, entry(pending(), alpha.name).getLong("started_at"));
        assertEquals(2, prefs.commits);
    }

    @Test public void otherSuccessfulAckDoesNotConfirmAnEarlierRejectedRemoval() throws Exception {
        enqueue(true, alpha, beta);
        prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
        prefs.outcome = Outcome.TRUE;
        assertTrue(SyncRecoveryJournal.complete(app, target, beta.name));
        assertAccounts(pending(), alpha.name);
        assertTrue(entry(pending(), alpha.name).getBoolean("manual"));
        assertAccounts(new JSONArray((String) prefs.disk.get(QUEUE)), alpha.name);
        assertEquals(3, prefs.commits); // No compensating write.
    }

    @Test public void rejectedEnqueueDoesNotPublishNewPairsOrManualPromotion() throws Exception {
        enqueue(false, alpha);
        prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        rejectedEnqueue(alpha, beta);
        prefs.outcome = Outcome.TRUE;
        Account gamma = new Account("gamma", alpha.type); enqueue(false, gamma);
        assertAccounts(pending(), alpha.name, gamma.name);
        assertFalse(entry(pending(), alpha.name).getBoolean("manual"));
        assertEquals(3, prefs.commits);
    }

    @Test public void falseMayChangeDiskButCannotInventCurrentProcessConfirmation() throws Exception {
        enqueue(false, alpha, beta);
        prefs.outcome = Outcome.FALSE_AFTER_DISK;
        assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
        assertAccounts(new JSONArray((String) prefs.disk.get(QUEUE)), beta.name);
        assertAccounts(pending(), alpha.name, beta.name);
    }

    @Test public void failedLegacyMigrationCannotLoseMarkersOnLaterForeignOwnerWrite() throws Exception {
        Map<String, Object> original = new HashMap<>();
        original.put(QUEUE, "[]"); original.put("active", true); original.put("account", alpha.name);
        original.put("workspace", target.uid + "|" + target.path); original.put("manual", true);
        original.put("started_at", 23L); original.put("attempt", 4); original.put("unknown_key", "retain");
        prefs.seed(original); prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        try { SyncRecoveryJournal.enqueueOrThrow(app, Collections.emptyList(), Collections.emptyList(), false); fail(); }
        catch (SyncRecoveryJournal.PersistenceFailure expected) { assertEquals("commit", expected.diagnostics.get("phase")); }
        assertFalse(prefs.raw().containsKey("active"));
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putString(SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID, "now-foreign").commit();
        prefs.outcome = Outcome.TRUE;
        SyncRecoveryJournal.enqueueOrThrow(app, Collections.emptyList(), Collections.emptyList(), false);
        assertEquals(original, prefs.raw()); assertEquals(original, prefs.disk); assertEquals(2, prefs.commits);
        assertEquals(0, pending().length());
    }

    @Test public void failedPruneDoesNotDispatchOrPublishItsRetirement() throws Exception {
        AccountManager.get(app).addAccountExplicitly(alpha, "", null);
        ContentResolver.setSyncAutomatically(alpha, app.getAuthority(), true);
        ProjectSyncRunner.Target removed = new ProjectSyncRunner.Target("removed", "Removed", target.path, "removed");
        SyncRecoveryJournal.enqueueOrThrow(app, List.of(target, removed), List.of(alpha), false);
        int before = requests(alpha); prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        SyncRecoveryJournal.schedulePendingIfNeeded(app);
        assertEquals(before, requests(alpha));
        JSONArray values = pending(); assertEquals(2, values.length());
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < values.length(); i++) keys.add(values.getJSONObject(i).getString("target"));
        assertEquals(Set.of(target.key(), removed.key()), keys);
    }

    @Test public void successfulRecoveryRespectsManualAndTrustedIdentityWithoutNetwork() throws Exception {
        AccountManager.get(app).addAccountExplicitly(alpha, "", null);
        AccountManager.get(app).addAccountExplicitly(beta, "", null);
        ContentResolver.setSyncAutomatically(alpha, app.getAuthority(), false);
        ContentResolver.setSyncAutomatically(beta, app.getAuthority(), false);
        enqueue(false, alpha); enqueue(true, beta);
        int alphaBefore = requests(alpha), betaBefore = requests(beta);
        SyncRecoveryJournal.schedulePendingIfNeeded(app);
        assertEquals(alphaBefore, requests(alpha)); assertEquals(betaBefore + 1, requests(beta));
        ShadowContentResolver.Status request = ShadowContentResolver.getStatus(beta, app.getAuthority(), false);
        assertEquals(target.key(), request.syncExtras.getString(ProjectSyncRunner.EXTRA_RECOVERY_TARGET));
        assertTrue(request.syncExtras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL));
        assertFalse(request.syncExtras.getBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY));
        assertAccounts(pending(), alpha.name, beta.name);
    }

    @Test public void readerDuringHeldRejectedWriteSeesPriorViewWithoutJoiningWriter() throws Exception {
        enqueue(false, alpha, beta);
        prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        prefs.published = new CountDownLatch(1); prefs.finishWrite = new CountDownLatch(1); releases.add(prefs.finishWrite);
        AtomicReference<Throwable> error = new AtomicReference<>(); AtomicReference<Boolean> result = new AtomicReference<>();
        Thread writer = start("journal-owned-caller", () -> {
            try { result.set(SyncRecoveryJournal.complete(app, target, alpha.name)); } catch (Throwable failure) { error.set(failure); }
        });
        try { await(prefs.published); assertAccounts(pending(), alpha.name, beta.name); }
        finally { prefs.finishWrite.countDown(); writer.join(5_000); }
        assertNull(error.get()); assertEquals(Boolean.FALSE, result.get());
    }

    @Test public void delayedRawMissCannotEscapeRejectedCacheAfterLaterSuccess() throws Exception {
        enqueue(false, alpha, beta);
        ReadGate gate = new ReadGate("journal-raw-reader"); prefs.readGate = gate;
        releases.add(gate.capture); releases.add(gate.returnRead);
        AtomicReference<JSONArray> result = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        Thread reader = start(gate.reader, () -> { try { result.set(pending()); } catch (Throwable failure) { error.set(failure); } });
        try {
            await(gate.entered); prefs.outcome = Outcome.FALSE_CACHE_ONLY;
            assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
            gate.capture.countDown(); await(gate.captured);
            prefs.outcome = Outcome.TRUE; assertTrue(SyncRecoveryJournal.complete(app, target, beta.name));
        } finally { gate.returnRead.countDown(); reader.join(5_000); }
        assertNull(error.get()); assertAccounts(result.get(), alpha.name);
    }

    @Test public void rawMissErrorRechecksSnapshotInstalledMeanwhile() throws Exception {
        enqueue(false, alpha, beta);
        ReadGate gate = new ReadGate("journal-error-reader"); gate.error = new ClassCastException("technical raw miss"); prefs.readGate = gate;
        releases.add(gate.capture); releases.add(gate.returnRead);
        AtomicReference<JSONArray> result = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        Thread reader = start(gate.reader, () -> { try { result.set(pending()); } catch (Throwable failure) { error.set(failure); } });
        try {
            await(gate.entered); prefs.outcome = Outcome.FALSE_CACHE_ONLY;
            assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
            gate.capture.countDown(); await(gate.captured);
        } finally { gate.returnRead.countDown(); reader.join(5_000); }
        assertNull(error.get()); assertAccounts(result.get(), alpha.name, beta.name);
    }

    @Test public void exactPreferenceOwnersDoNotCollideThroughEqualsOrContextIdentity() throws Exception {
        prefs.hostileEquality = true; enqueue(false, alpha);
        prefs.outcome = Outcome.FALSE_CACHE_ONLY; assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
        FakePreferences first = prefs, second = new FakePreferences(); second.hostileEquality = true; app.journal = second;
        enqueue(false, beta); assertAccounts(pending(), beta.name);
        app.journal = first;
        Context sameOwner = new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) { return PREFS.equals(name) ? first : super.getSharedPreferences(name, mode); }
        };
        assertAccounts(SyncRecoveryJournal.load(sameOwner), alpha.name);
    }

    @Test public void postMemoryRuntimeAndFatalKeepExistingErrorSemanticsAndPriorPairs() throws Exception {
        enqueue(false, alpha, beta);
        prefs.outcome = Outcome.RUNTIME_AFTER_CACHE; assertFalse(SyncRecoveryJournal.complete(app, target, alpha.name));
        assertAccounts(pending(), alpha.name, beta.name);
        prefs.outcome = Outcome.ERROR_AFTER_CACHE;
        try { SyncRecoveryJournal.complete(app, target, beta.name); fail(); }
        catch (Error error) { assertSame(prefs.fatal, error); }
        assertAccounts(pending(), alpha.name, beta.name); assertEquals(3, prefs.commits);
    }

    @Test public void malformedQueueAndDemandedLegacyTypesKeepTheirOriginalFailures() throws Exception {
        prefs.seed(Map.of(QUEUE, "{broken"));
        try { enqueue(false, alpha); fail(); }
        catch (SyncRecoveryJournal.PersistenceFailure error) {
            assertEquals("read_queue", error.diagnostics.get("phase")); assertTrue(error.getCause() instanceof JSONException);
        }
        assertEquals("{broken", prefs.raw().get(QUEUE)); assertEquals(0, prefs.commits);
        prefs.seed(Map.of(QUEUE, "[]", "active", "not-a-boolean"));
        try { enqueue(false, alpha); fail(); }
        catch (SyncRecoveryJournal.PersistenceFailure error) {
            assertEquals("legacy_inventory", error.diagnostics.get("phase")); assertTrue(error.getCause() instanceof ClassCastException);
        }
        assertEquals("not-a-boolean", prefs.raw().get("active")); assertEquals(0, prefs.commits);
    }

    @Test @SuppressWarnings("unchecked") public void inactiveMalformedSupportedValuesAndUnknownKeysAreNotSanitized() throws Exception {
        Map<String, Object> values = new HashMap<>(); values.put(QUEUE, "[]"); values.put("active", false);
        values.put("account", 7); values.put("workspace", 2.5f); values.put("manual", Set.of("unchanged"));
        values.put("started_at", "not-a-long"); values.put("attempt", 8L); values.put("unknown_key", Set.of("outside"));
        prefs.seed(values); enqueue(false, alpha);
        Set<String> borrowed;
        synchronized (prefs) { borrowed = (Set<String>) prefs.cache.get("manual"); }
        prefs.outcome = Outcome.FALSE_CACHE_ONLY;
        prefs.published = new CountDownLatch(1); prefs.finishWrite = new CountDownLatch(1); releases.add(prefs.finishWrite);
        AtomicReference<Throwable> error = new AtomicReference<>(); AtomicReference<Boolean> result = new AtomicReference<>();
        Thread writer = start("journal-set-snapshot", () -> {
            try { result.set(SyncRecoveryJournal.complete(app, target, alpha.name)); } catch (Throwable failure) { error.set(failure); }
        });
        try {
            await(prefs.published);
            borrowed.add("after-snapshot");
        } finally { prefs.finishWrite.countDown(); writer.join(5_000); }
        assertNull(error.get()); assertEquals(Boolean.FALSE, result.get());
        prefs.outcome = Outcome.TRUE; enqueue(false, beta);
        for (String key : values.keySet()) if (!QUEUE.equals(key)) assertEquals(values.get(key), prefs.raw().get(key));
        assertAccounts(pending(), alpha.name, beta.name);
    }

    @Test public void bootstrapRawStateIsNotReplacedByAnInventedDiskConfirmation() throws Exception {
        prefs.seed(Map.of(QUEUE, "[]")); prefs.disk.put(QUEUE, "unread hypothetical old disk");
        assertEquals(0, pending().length()); assertEquals(0, prefs.commits);
        RuntimeException failure = new ClassCastException("technical first-read failure"); prefs.readFailure = failure;
        try { pending(); fail(); } catch (RuntimeException error) { assertSame(failure, error); }
    }

    @Test public void healthySuccessKeepsNormalMergingAndExistingRawSeedFixtures() throws Exception {
        enqueue(false, alpha); enqueue(true, alpha);
        assertAccounts(pending(), alpha.name); assertTrue(entry(pending(), alpha.name).getBoolean("manual"));
        prefs.seed(Map.of(QUEUE, "[]", "active", false));
        assertEquals(0, pending().length()); enqueue(false, beta); assertAccounts(pending(), beta.name);
    }
}
