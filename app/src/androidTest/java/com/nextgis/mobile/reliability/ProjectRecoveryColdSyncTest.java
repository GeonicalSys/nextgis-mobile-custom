package com.nextgis.mobile.reliability;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.work.WorkManager;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.LayerOriginMetadata;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.FeatureChanges;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplib.util.NetworkUtil;
import com.nextgis.maplib.util.ProjectRecoveryGuard;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplib.util.SyncWorkspaceSession;
import com.nextgis.maplib.util.UnderlayFiles;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.mapui.LocalChangesWorker;
import com.nextgis.maplibui.mapui.NGWVectorLayerUI;
import com.nextgis.maplibui.service.LayerFillService;
import com.nextgis.maplibui.util.CollectorProjectRegistry;
import com.nextgis.maplibui.util.LayerBackupManager;
import com.nextgis.maplibui.util.LocalChangesRepository;
import com.nextgis.maplibui.util.ProjectArchiveManager;
import com.nextgis.mobile.util.VerificationEnvironment;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Stationary, opt-in native SQLite/loopback checks. Never opens an Activity or changes radios. */
@RunWith(AndroidJUnit4.class)
public class ProjectRecoveryColdSyncTest {
    private static boolean earlyInvoked, earlyNoMap, legacyGetterRejected;
    private static boolean earlyHeld, earlyBlocked, earlyStillNoMap;
    private static Throwable earlyFailure;
    private GISApplication app;

    /** Called by the test runner before Application.onCreate, not after clearing a live map. */
    public static void captureBeforeApplicationCreate(Context context) {
        earlyInvoked = true;
        earlyNoMap = MapBase.getActiveInstance() == null;
        try {
            try { MapBase.getInstance(); }
            catch (IllegalArgumentException expected) { legacyGetterRejected = true; }
            LayerGroup detached = new LayerGroup(context,
                    new File(context.getCacheDir(), "early-recovery-probe"), null);
            earlyHeld = ProjectRecoveryGuard.isHeld(detached);
            earlyBlocked = ProjectRecoveryGuard.isSyncBlocked(detached);
            earlyStillNoMap = MapBase.getActiveInstance() == null;
        } catch (Throwable error) { earlyFailure = error; }
    }

    @Before public void isolatedStationaryWorkspace() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
        assertEquals(VerificationEnvironment.PACKAGE, context.getPackageName());
        app = (GISApplication) context.getApplicationContext();
        noActivity();
    }

    private static void noActivity() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Stage stage : new Stage[]{Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED})
                assertTrue("This scenario must not open the map screen",
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).isEmpty());
        });
    }

    @Test public void recoveryGuardWorksBeforeApplicationCreatesItsFirstMap() {
        assertTrue("The runner must capture the real early startup phase", earlyInvoked);
        assertTrue("No map exists before Application.onCreate", earlyNoMap);
        assertTrue("The old singleton accessor really is unavailable in this phase", legacyGetterRejected);
        if (earlyFailure != null) throw new AssertionError("Early guard failed", earlyFailure);
        assertFalse(earlyHeld); assertFalse(earlyBlocked);
        assertTrue("Reading an orphan layer must not create a map", earlyStillNoMap);
    }

    private final class Fixture implements AutoCloseable {
        final MapBase active = MapBase.getActiveInstance();
        final String activeUid = CollectorProjectRegistry.getActiveProject(app).getProjectUid();
        final File registry = new File(app.getExternalFilesDir(SettingsConstants.KEY_PREF_MAP),
                "collector_projects/collector_projects_registry.json");
        final byte[] registryBefore = registry.isFile() ? Files.readAllBytes(registry.toPath()) : null;
        final Account account = new Account("cold-recovery-" + UUID.randomUUID(), app.getAccountsType());
        final CollectorProjectRegistry.ProjectInfo original;
        final Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        final MapDrawable source;
        final NGWVectorLayerUI layer;
        final long id;
        final byte[] photo = new byte[]{0, 7, 9, 4, (byte) 255};
        final byte[] form = "{\"synthetic\":\"cold-recovery\"}".getBytes(StandardCharsets.UTF_8);
        CollectorProjectRegistry.ProjectInfo copy;
        File archive;

        Fixture(LocalChangesUploadTest.Endpoint endpoint) throws Exception {
            assertNotNull(active);
            assertTrue("Loopback delivery needs the existing network; no radio is changed",
                    new NetworkUtil(app).isNetworkAvailable());
            cancelJobs();
            Bundle data = new Bundle(); data.putString("url", endpoint.url()); data.putString("login", "synthetic");
            assertTrue(AccountManager.get(app).addAccountExplicitly(account, "", data));
            ContentResolver.setIsSyncable(account, app.getAuthority(), 0);
            ContentResolver.setSyncAutomatically(account, app.getAuthority(), false);
            original = CollectorProjectRegistry.createLocalProject(app, "Cold recovery source");
            assertNotNull(original);
            source = new MapDrawable(bitmap, app, new File(original.getMapPath(),
                    original.getMapName() + Constants.MAP_EXT), app.getLayerFactory(), false);
            source.setName(original.getName());
            try (SyncWorkspaceSession session = new SyncWorkspaceSession(source);
                 SyncWorkspaceSession.Scope ignored = session.enter()) {
                layer = new NGWVectorLayerUI(app, new File(source.getPath(), "cold_recovery_line"));
                source.addLayer(layer); layer.setName("Cold recovery line"); layer.beginBulkImport();
                layer.create(GeoConstants.GTLineString,
                        Collections.singletonList(new Field(GeoConstants.FTString, "name", "Name")));
                layer.endBulkImport(); layer.setAccountName(account.name); layer.setRemoteId(1329);
                JSONObject config = layer.toJSON();
                config.put("ngw_layer_type", Connection.NGWResourceTypePostgisLayer); layer.fromJSON(config);
                layer.setSyncType(Constants.SYNC_ALL); layer.setSyncDirection(2);
                ContentValues row = new ContentValues(); row.put("name", "cold-field-line");
                row.put(Constants.FIELD_GEOM, GeoGeometryFactory.fromWKT(
                        "LINESTRING (1 2, 3 4)", GeoConstants.CRS_WEB_MERCATOR).toBlob());
                id = layer.insertAddChanges(row); assertTrue(id >= 10000000);
                File photos = new File(layer.getPath(), Long.toString(id)); assertTrue(photos.mkdirs());
                Files.write(new File(photos, "photo.bin").toPath(), photo);
                Files.write(new File(layer.getPath(), "form.json").toPath(), form);
                assertTrue(layer.save()); assertTrue(source.save());
            }
            // Drain insert notifications while outbound is disabled, then cancel this app's jobs.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); Thread.sleep(1200); cancelJobs();
            layer.setSyncDirection(3); assertTrue(layer.save()); assertTrue(source.save());
            assertTrue(endpoint.mutations.isEmpty()); unchangedActive();
        }

        void cancelJobs() throws Exception {
            WorkManager.getInstance(app).cancelAllWorkByTag("local-changes-upload")
                    .getResult().get(10, TimeUnit.SECONDS);
        }
        void restore() throws Exception {
            archive = ProjectArchiveManager.create(app, source, original);
            copy = ProjectArchiveManager.restore(app, archive, original, "Cold restored copy",
                    MapContentProviderHelper.getCurrentDatabaseVersion());
            assertNotEquals(original.getProjectUid(), copy.getProjectUid()); unchangedActive();
        }
        long pending() {
            return FeatureChanges.getChangeCount(source.getDatabase(true), layer.getChangeTableName());
        }
        void unchangedActive() {
            assertSame("A background workspace must not replace the active model", active, MapBase.getActiveInstance());
            assertEquals(activeUid, CollectorProjectRegistry.getActiveProject(app).getProjectUid()); noActivity();
        }
        LocalChangesRepository.Row queuedCopy() throws Exception {
            return LocalChangesRepository.load(app, copy.getProjectUid(), 50, 0).rows.stream()
                    .filter(row -> row.featureId == id && row.queued).findFirst().get();
        }
        String approveCopy() throws Exception {
            LocalChangesRepository.Row row = queuedCopy();
            String operation = LocalChangesRepository.withLayer(app, copy.getProjectUid(), row.layerPath,
                    vector -> vector.prepareChangeReview(row.changeId, row.identity,
                            row.featureId, row.attachmentId, row.operation));
            assertTrue(LocalChangesRepository.withLayer(app, copy.getProjectUid(), row.layerPath,
                    vector -> vector.authorizeUploadRetry(operation)));
            return operation;
        }
        @Override public void close() throws Exception {
            try { Thread.sleep(1200); cancelJobs(); }
            finally {
                try { source.closeSyncWorkspace(); }
                finally {
                    if (!bitmap.isRecycled()) bitmap.recycle();
                    try {
                        if (copy != null) remove(new File(copy.getMapPath()));
                        remove(new File(original.getMapPath()));
                        if (archive != null) Files.deleteIfExists(archive.toPath());
                        if (registryBefore == null) Files.deleteIfExists(registry.toPath());
                        else Files.write(registry.toPath(), registryBefore);
                    } finally { assertTrue(AccountManager.get(app).removeAccountExplicitly(account)); }
                }
            }
            unchangedActive();
        }
    }

    private static void remove(File path) throws Exception {
        if (path.exists()) UnderlayFiles.deleteChildTree(path.getParentFile(), path);
        assertFalse(path.exists());
    }
    private static long number(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) { assertTrue(cursor.moveToFirst()); return cursor.getLong(0); }
    }

    @Test public void formReplacementKeepsTheOldQueueUntilTheEditIsAcknowledged() throws Exception {
        Method formHandoff = LayerFillService.class.getDeclaredMethod(
                "copyFormLayerExtras", Bundle.class, Bundle.class);
        formHandoff.setAccessible(true);
        Bundle request = new Bundle();
        request.putInt(LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX, 0);
        request.putBoolean(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY, true);
        Bundle fill = new Bundle();
        formHandoff.invoke(null, request, fill);
        assertEquals(0, fill.getInt(LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX, -1));
        assertTrue(fill.getBoolean(LayerFillService.KEY_DEFER_MAP_RELOAD_UNTIL_QUEUE_EMPTY));
        Bundle freshImport = new Bundle();
        formHandoff.invoke(null, new Bundle(), freshImport);
        assertFalse(freshImport.containsKey(LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX));

        Method publish = LayerFillService.class.getDeclaredMethod("publishLayerReplacement",
                LayerGroup.class, NGWVectorLayer.class, List.class, int.class, int.class, long[].class);
        publish.setAccessible(true);
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint();
             Fixture f = new Fixture(endpoint);
             SyncWorkspaceSession session = new SyncWorkspaceSession(f.source);
             SyncWorkspaceSession.Scope ignored = session.enter()) {
            int originalIndex = f.source.getChildLayerIndex(f.layer);
            int originalLayerCount = f.source.getLayerCount();
            NGWVectorLayerUI stage = new NGWVectorLayerUI(
                    app, new File(f.source.getPath(), "cold_recovery_replacement"));
            stage.setParent(f.source);
            stage.setName("Updated field line");
            stage.beginBulkImport();
            stage.create(GeoConstants.GTLineString, Arrays.asList(
                    new Field(GeoConstants.FTString, "name", "Name"),
                    new Field(GeoConstants.FTDateTime, "deleted_at", "Deleted at")));
            stage.endBulkImport();
            stage.setAccountName(f.account.name);
            stage.setRemoteId(f.layer.getRemoteId());
            assertTrue(stage.save());

            assertFalse(publishReplacementOnMain(publish, f, stage, originalIndex));
            assertEquals(1, f.pending());
            assertSame(f.layer, f.source.getLayer(originalIndex));
            assertNotNull(f.layer.getFeature(f.id));
            assertTrue(endpoint.mutations.isEmpty());

            assertTrue(f.layer.sendLocalChanges(new SyncResult()));
            assertEquals(0, f.pending());
            assertEquals(1, endpoint.mutations.size());
            assertTrue(app.backupEditableLayerData(f.layer, LayerBackupManager.REASON_SCHEMA_REBUILD));
            assertTrue(publishReplacementOnMain(publish, f, stage, originalIndex));
            assertSame(stage, f.source.getLayer(originalIndex));
            assertEquals(originalLayerCount, f.source.getLayerCount());
            assertEquals(-1, f.source.getChildLayerIndex(f.layer));
            assertTrue(stage.validateSqliteSchemaAgainstFields().isEmpty());
            assertEquals(1, endpoint.mutations.size());
            f.unchangedActive();
        }
    }

    @Test public void previousVersionDuplicateIsRepairedWithoutDiscardingTheLocallyEditedCopy() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint();
             Fixture f = new Fixture(endpoint);
             SyncWorkspaceSession session = new SyncWorkspaceSession(f.source);
             SyncWorkspaceSession.Scope ignored = session.enter()) {
            int originalIndex = f.source.getChildLayerIndex(f.layer);
            int originalCount = f.source.getLayerCount();
            LayerOriginMetadata origin = LayerOriginMetadata.collectorLayer(
                    f.original.getProjectUid(), originalIndex, 178L);
            f.layer.setLayerOriginMetadata(origin);
            assertTrue(f.layer.save());

            NGWVectorLayerUI duplicate = new NGWVectorLayerUI(
                    app, new File(f.source.getPath(), "cold_recovery_duplicate"));
            f.source.addLayer(duplicate);
            duplicate.setName("Copy from the old form loader");
            duplicate.beginBulkImport();
            duplicate.create(GeoConstants.GTLineString,
                    Collections.singletonList(new Field(GeoConstants.FTString, "name", "Name")));
            duplicate.endBulkImport();
            duplicate.setAccountName(f.account.name);
            duplicate.setRemoteId(f.layer.getRemoteId());
            duplicate.setLayerOriginMetadata(origin);
            assertTrue(duplicate.save());
            assertTrue(f.source.save());
            assertEquals(originalCount + 1, f.source.getLayerCount());

            assertTrue(app.repairProjectIntegrityBeforeSync(f.account.name));
            assertEquals(originalCount, f.source.getLayerCount());
            assertSame(f.layer, f.source.getLayer(originalIndex));
            assertEquals(1, f.pending());
            assertNotNull(f.layer.getFeature(f.id));
            assertTrue(endpoint.mutations.isEmpty());
            f.unchangedActive();
        }
    }

    private static boolean publishReplacementOnMain(
            Method publish, Fixture fixture, NGWVectorLayer stage, int originalIndex) {
        AtomicBoolean published = new AtomicBoolean();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(SyncWorkspaceSession.capture(() -> {
            try {
                published.set((Boolean) publish.invoke(null, fixture.source, stage,
                        Collections.singletonList(fixture.layer), originalIndex, -1, null));
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Replacement callback failed", error);
            }
        }));
        return published.get();
    }

    @Test public void heldCopyRejectsEverySyncEntryAndPreservesOriginalRowsPhotosAndFormsWithoutAnActivity() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.restore(); endpoint.holdRead = true; endpoint.releaseRead.countDown();
            assertFalse(LocalChangesWorker.uploadProject(app, f.copy.getMapPath()));
            LocalChangesRepository.Row row = f.queuedCopy();
            LocalChangesRepository.withLayer(app, f.copy.getProjectUid(), row.layerPath, vector -> {
                assertTrue(ProjectRecoveryGuard.isHeld(vector));
                SyncResult send = new SyncResult(); assertFalse(vector.sendLocalChanges(send));
                assertTrue(send.stats.numConflictDetectedExceptions > 0);
                SyncResult full = new SyncResult(); vector.sync(app.getAuthority(), new android.util.Pair<>(4, 0), full);
                assertTrue(full.stats.numConflictDetectedExceptions > 0);
                SyncResult config = new SyncResult(); vector.syncNgwResourceConfigOnly(app.getAuthority(), config, true);
                assertTrue(config.stats.numConflictDetectedExceptions > 0);
                return null;
            });
            assertEquals("No remote mutation is admitted", 0, endpoint.mutations.size());
            assertEquals("A held copy must not even pull configuration", 1, endpoint.readEntered.getCount());
            assertEquals(1, f.pending()); assertNotNull(f.layer.getFeature(f.id));
            assertArrayEquals(f.photo, Files.readAllBytes(new File(f.layer.getPath(), f.id + "/photo.bin").toPath()));
            assertArrayEquals(f.form, Files.readAllBytes(new File(f.copy.getMapPath(), "cold_recovery_line/form.json").toPath()));
            assertArrayEquals(f.photo, Files.readAllBytes(new File(f.copy.getMapPath(), "cold_recovery_line/" + f.id + "/photo.bin").toPath()));
            f.unchangedActive();
        }
    }

    @Test public void layerParentAndThreadScopeKeepHeldProjectsSeparateFromTheActiveModel() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.restore(); LocalChangesRepository.Row row = f.queuedCopy();
            LocalChangesRepository.withLayer(app, f.copy.getProjectUid(), row.layerPath, vector -> {
                assertTrue(ProjectRecoveryGuard.isHeld(vector));
                LayerGroup detached = new LayerGroup(app, new File(app.getCacheDir(), "orphan"), null);
                assertTrue("The background scope is used for an unparented layer", ProjectRecoveryGuard.isHeld(detached));
                try (SyncWorkspaceSession other = new SyncWorkspaceSession(f.source); SyncWorkspaceSession.Scope ignored = other.enter()) {
                    assertTrue("A real parent wins over a different thread scope", ProjectRecoveryGuard.isHeld(vector));
                    assertFalse("The unrelated source scope is not held", ProjectRecoveryGuard.isHeld(detached));
                }
                assertTrue(ProjectRecoveryGuard.isHeld(detached)); return null;
            });
            assertFalse(ProjectRecoveryGuard.isHeld(new LayerGroup(app, new File(app.getCacheDir(), "outside"), null)));
            assertEquals(1, f.pending()); assertTrue(endpoint.mutations.isEmpty()); f.unchangedActive();
        }
    }

    @Test public void reviewedCopySendsOnceAndBlocksOriginalReplayWhilePreservingTheOriginalQueue() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint(); Fixture f = new Fixture(endpoint)) {
            f.restore(); assertFalse(LocalChangesRepository.releaseReviewedCopy(app, f.copy.getProjectUid()));
            f.approveCopy(); assertTrue(LocalChangesRepository.releaseReviewedCopy(app, f.copy.getProjectUid()));
            assertTrue(ProjectRecoveryGuard.isDeliveryBlocked(f.source.getPath()));
            assertTrue(LocalChangesWorker.uploadProject(app, f.copy.getMapPath()));
            assertEquals(1, endpoint.mutations.size()); assertEquals(1, f.pending());
            assertTrue(LocalChangesWorker.uploadProject(app, f.original.getMapPath()));
            assertTrue(LocalChangesWorker.uploadProject(app, f.copy.getMapPath()));
            SyncResult blocked = new SyncResult(); assertFalse(f.layer.sendLocalChanges(blocked));
            assertTrue(blocked.stats.numConflictDetectedExceptions > 0);
            assertEquals("Neither the original nor a repeated copy pass may POST again", 1, endpoint.mutations.size());
            assertEquals(1, f.pending()); assertNotNull(f.layer.getFeature(f.id));
            try (SQLiteDatabase db = SQLiteDatabase.openDatabase(new File(f.copy.getMapPath(), "layers.db").getPath(), null, SQLiteDatabase.OPEN_READONLY)) {
                assertEquals(1, number(db, "SELECT count(*) FROM cold_recovery_line WHERE _id=42 AND name='cold-field-line'"));
                assertEquals(0, FeatureChanges.getChangeCount(db, f.layer.getChangeTableName()));
            }
            f.unchangedActive();
        }
    }

    @Test public void aLaterSourceAcknowledgementRequiresReadOnlyReconciliationBeforeCopyAdmission() throws Exception {
        try (LocalChangesUploadTest.Endpoint endpoint = new LocalChangesUploadTest.Endpoint(); Fixture f = new Fixture(endpoint)) {
            endpoint.serveFeatures = true; f.restore();
            String operation = f.approveCopy(); LocalChangesRepository.Row row = f.queuedCopy();
            assertTrue(LocalChangesWorker.uploadProject(app, f.original.getMapPath()));
            assertEquals(1, endpoint.mutations.size()); assertEquals(0, f.pending());
            try { LocalChangesRepository.releaseReviewedCopy(app, f.copy.getProjectUid()); fail("The sibling ACK must reject stale NEW authorization"); }
            catch (LocalChangesRepository.SiblingAcknowledgementException expected) { }
            assertTrue(ProjectRecoveryGuard.isHeld(new File(f.copy.getMapPath())));
            assertFalse(ProjectRecoveryGuard.isDeliveryBlocked(f.source.getPath()));
            assertTrue(LocalChangesRepository.withLayer(app, f.copy.getProjectUid(), row.layerPath,
                    vector -> vector.reconcileUpload(operation, 42)));
            assertEquals("Reconciliation is read-only", 1, endpoint.mutations.size());
            assertTrue(LocalChangesRepository.releaseReviewedCopy(app, f.copy.getProjectUid()));
            assertTrue(LocalChangesWorker.uploadProject(app, f.copy.getMapPath()));
            assertTrue(LocalChangesWorker.uploadProject(app, f.original.getMapPath()));
            assertEquals(1, endpoint.mutations.size()); assertNotNull(f.layer.getFeature(42)); f.unchangedActive();
        }
    }
}
