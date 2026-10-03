package com.nextgis.mobile.reliability;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.NGWVectorLayer;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

/** Real SQLite fault injection; never opens an existing project or connects to NGW. */
@RunWith(AndroidJUnit4.class)
public class FeaturePersistenceTest {
    private MapBase previous;
    private TestMap map;
    private NGWVectorLayer layer;
    private SQLiteDatabase db;

    private static class TestMap extends MapContentProviderHelper {
        TestMap(Context context, File path) {
            super(context, path, ((IGISApplication) context).getLayerFactory());
        }
        static void restore(MapBase map) { mInstance = map; }
    }

    @Before public void setUp() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getApplicationContext();
        previous = MapBase.getInstance();
        File root = new File(app.getCacheDir(), "reliability-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        map = new TestMap(app, new File(root, "test.ngm"));
        layer = new NGWVectorLayer(app, new File(root, "test_features"));
        layer.setSyncType(Constants.SYNC_DATA | Constants.SYNC_ATTACH);
        layer.beginBulkImport(); // The tests inspect committed SQLite, without map UI broadcasts.
        map.addLayer(layer);
        layer.create(GeoConstants.GTPoint, Collections.emptyList());
        db = map.getDatabase(false);
    }

    @After public void tearDown() {
        TestMap.restore(previous);
        if (db != null) db.close();
    }

    private ContentValues feature() throws Exception {
        ContentValues values = new ContentValues();
        values.put(Constants.FIELD_GEOM, new GeoPoint(10, 20).toBlob());
        return values;
    }

    @Test public void failedChangeRecordRollsBackFeature() throws Exception {
        db.execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON "
                + layer.getChangeTableName()
                + " BEGIN SELECT RAISE(ABORT, 'injected outbox failure'); END");
        try { layer.insertAddChanges(feature()); } catch (RuntimeException expected) { }
        assertEquals(0, DatabaseUtils.queryNumEntries(db, "test_features"));
        assertEquals(0, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
    }

    @Test public void insertedFeatureAndOutboxUseOwningProject() throws Exception {
        File other = new File(map.getPath(), "other");
        assertTrue(other.mkdirs());
        TestMap active = new TestMap(layer.getContext(), new File(other, "other.ngm"));
        long id = layer.insertAddChanges(feature());
        assertTrue(id >= 0);
        assertEquals(1, DatabaseUtils.queryNumEntries(db, "test_features"));
        assertEquals(1, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
        active.getDatabase(false).close();
    }

    @Test public void returnedIdBelongsToInsertedRowDespiteTriggerInsert() {
        db.execSQL("CREATE TABLE unrelated (_id INTEGER PRIMARY KEY)");
        db.execSQL("CREATE TRIGGER insert_other AFTER INSERT ON test_features "
                + "BEGIN INSERT INTO unrelated VALUES (700); END");
        ContentValues values = new ContentValues();
        values.put(Constants.FIELD_ID, 42L);
        assertEquals(42, layer.insertViaSql(db, values, "test_features"));
    }

    @Test public void failedLayerDropRollsBackAllTablesAndKeepsFiles() throws Exception {
        layer.insertAddChanges(feature());
        File photo = new File(layer.getPath(), "important-photo");
        assertTrue(photo.createNewFile());
        db.execSQL("DROP TABLE " + layer.getAttachmentsTableName());
        db.execSQL("CREATE VIEW " + layer.getAttachmentsTableName() + " AS SELECT 1");
        try {
            layer.delete(false);
            fail("A failed table drop must prevent file deletion");
        } catch (android.database.sqlite.SQLiteException expected) { }
        assertEquals(1, DatabaseUtils.queryNumEntries(db, "test_features"));
        assertEquals(1, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
        assertTrue(photo.exists());
        assertSame(layer, map.getLayerById(layer.getId()));
    }

    @Test public void rawOuterRollbackDoesNotPublishCommittedChange() throws Exception {
        java.util.concurrent.atomic.AtomicInteger published = new java.util.concurrent.atomic.AtomicInteger();
        db.beginTransaction();
        try {
            layer.insertAddChanges(feature());
            com.nextgis.maplib.util.LayerDatabaseTransaction.afterCommit(db, published::incrementAndGet);
        } finally { db.endTransaction(); }
        assertEquals(0, DatabaseUtils.queryNumEntries(db, "test_features"));
        assertEquals(0, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
        assertEquals(0, published.get());
    }

    @Test public void backupCopiesRowsAndPhotoWithoutOverwritingPreviousArchive() throws Exception {
        layer.setName("Isolated backup test");
        long id = layer.insertAddChanges(feature());
        File directory = new File(layer.getPath(), Long.toString(id));
        assertTrue(directory.mkdirs());
        byte[] photo = new byte[]{1, 2, 3, 4, 5};
        java.nio.file.Files.write(new File(directory, "123").toPath(), photo);
        Context storage = new android.content.ContextWrapper(layer.getContext()) {
            @Override public File getExternalFilesDir(String type) {
                return new File(map.getPath(), "isolated-backup");
            }
        };
        com.nextgis.maplibui.util.LayerBackupManager.BackupResult first =
                com.nextgis.maplibui.util.LayerBackupManager.backupLayerData(storage, layer, "test");
        com.nextgis.maplibui.util.LayerBackupManager.BackupResult second =
                com.nextgis.maplibui.util.LayerBackupManager.backupLayerData(storage, layer, "test");
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertNotEquals(first.getFile(), second.getFile());
        try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(first.getFile())) {
            org.json.JSONObject rows = new org.json.JSONObject(new String(archive.getInputStream(
                    archive.getEntry("tables/features.json")).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(1, rows.getJSONArray("rows").length());
            assertEquals(id, rows.getJSONArray("rows").getJSONObject(0).getLong(Constants.FIELD_ID));
            java.util.zip.ZipEntry attachment = archive.getEntry("attachments/" + id + "/123");
            assertNotNull(attachment);
            assertArrayEquals(photo, archive.getInputStream(attachment).readAllBytes());
        }
        assertEquals(1, DatabaseUtils.queryNumEntries(db, "test_features"));
    }

    @Test public void missingFeatureTableCannotProduceSuccessfulBackup() {
        Context storage = new android.content.ContextWrapper(layer.getContext()) {
            @Override public File getExternalFilesDir(String type) {
                return new File(map.getPath(), "isolated-backup");
            }
        };
        db.execSQL("DROP TABLE test_features");
        assertFalse(com.nextgis.maplibui.util.LayerBackupManager
                .backupLayerData(storage, layer, "test").isSuccess());
    }

    @Test public void failedSinglePhotoDeleteKeepsFileMetadataAndPreviousChange() throws Exception {
        attachmentDeleteRollback(false);
    }

    @Test public void failedAllPhotosDeleteKeepsFilesMetadataAndPreviousChange() throws Exception {
        attachmentDeleteRollback(true);
    }

    private void attachmentDeleteRollback(boolean all) throws Exception {
        long id = layer.insertAddChanges(feature());
        db.delete(layer.getChangeTableName(), null, null);
        layer.addChange(id, 123, Constants.CHANGE_OPERATION_CHANGED);
        File folder = new File(layer.getPath(), Long.toString(id));
        assertTrue(folder.mkdirs());
        File photo = new File(folder, "123");
        File metadata = new File(folder, "meta.json");
        assertTrue(photo.createNewFile());
        org.json.JSONArray json = new org.json.JSONArray().put(
                new com.nextgis.maplib.util.AttachItem("123", "photo.jpg", "image/jpeg", "").toJSON());
        java.nio.file.Files.write(metadata.toPath(), json.toString().getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        String authority = ((IGISApplication) layer.getContext()).getAuthority();
        android.net.Uri uri = android.net.Uri.parse("content://" + authority + "/test_features/"
                + id + "/" + Constants.URI_ATTACH + (all ? "" : "/123"));
        db.execSQL("CREATE TRIGGER fail_photo_delete BEFORE INSERT ON " + layer.getChangeTableName()
                + " BEGIN SELECT RAISE(ABORT, 'injected attachment delete failure'); END");
        long generation = layer.getDataGeneration();
        try {
            layer.delete(uri, null, null);
            fail("Outbox failure must prevent attachment deletion");
        } catch (android.database.sqlite.SQLiteException expected) { }
        assertTrue(photo.exists());
        assertTrue(metadata.exists());
        assertTrue(layer.getAttachMap(Long.toString(id)).containsKey("123"));
        assertEquals(1, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
        assertEquals(generation, layer.getDataGeneration());
        db.execSQL("DROP TRIGGER fail_photo_delete");
        assertTrue(layer.delete(uri, null, null) > 0);
        assertFalse(photo.exists());
        assertFalse(metadata.exists());
        assertEquals(1, DatabaseUtils.queryNumEntries(db, layer.getChangeTableName()));
        assertEquals(generation + 1, layer.getDataGeneration());
    }
}
