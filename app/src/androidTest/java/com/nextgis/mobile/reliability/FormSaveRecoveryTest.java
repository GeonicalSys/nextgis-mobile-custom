package com.nextgis.mobile.reliability;

import android.content.Intent;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import android.widget.PopupMenu;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.activity.ModifyAttributesActivity;
import com.nextgis.maplibui.control.PhotoGallery;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.FeatureFormDraftStore;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** An actual Save with an unreadable photo must stay open and resume without a duplicate object. */
@RunWith(AndroidJUnit4.class)
public class FormSaveRecoveryTest {
    private void save(ModifyAttributesActivity activity) {
        PopupMenu menu = new PopupMenu(activity, new View(activity));
        activity.onOptionsItemSelected(menu.getMenu().add(0, R.id.menu_apply, 0, "Save"));
    }

    @Test public void failedPhotoKeepsTheFormAndRetryReusesTheCreatedFeature() throws Exception {
        GISApplication app = (GISApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        assertNull("Use an isolated emulator without a user's form", FeatureFormDraftStore.load(app));
        MapContentProviderHelper map = (MapContentProviderHelper) app.getMap();
        assertNotNull(map);
        VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(), "form_test_"
                + UUID.randomUUID().toString().replace("-", "")));
        layer.setName("Form recovery test");
        map.addLayer(layer);
        layer.create(GeoConstants.GTPoint, Collections.emptyList());
        layer.setIsEditable(true);
        GeoPoint point = new GeoPoint(10, 20);
        point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        File missingPhoto = new File(app.getCacheDir(), "retry_photo_" + UUID.randomUUID() + ".jpg");
        Intent form = new Intent(app, ModifyAttributesActivity.class)
                .putExtra(ConstantsUI.KEY_LAYER_ID, layer.getId())
                .putExtra(ConstantsUI.KEY_FEATURE_ID, (long) Constants.NOT_FOUND)
                .putExtra(ConstantsUI.KEY_GEOMETRY, point)
                .putExtra(ConstantsUI.KEY_GEOMETRY_CHANGED, true);
        try (ActivityScenario<ModifyAttributesActivity> scenario = ActivityScenario.launch(form)) {
            Field fields = ModifyAttributesActivity.class.getDeclaredField("mFields");
            Field saving = ModifyAttributesActivity.class.getDeclaredField("mFormSaving");
            fields.setAccessible(true); saving.setAccessible(true);
            scenario.onActivity(activity -> {
                try {
                    Map<?, ?> controls = (Map<?, ?>) fields.get(activity);
                    PhotoGallery gallery = null;
                    for (Object value : controls.values()) if (value instanceof PhotoGallery) gallery = (PhotoGallery) value;
                    assertNotNull(gallery);
                    gallery.restorePendingPhotoPaths(Collections.singletonList(missingPhoto.getAbsolutePath()));
                    assertEquals(1, gallery.getNewAttaches().size());
                    save(activity);
                } catch (IllegalAccessException error) { throw new AssertionError(error); }
            });
            AtomicBoolean attempted = new AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (!attempted.get() && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    try { attempted.set(!saving.getBoolean(activity)); }
                    catch (IllegalAccessException error) { throw new AssertionError(error); }
                });
                if (!attempted.get()) Thread.sleep(50);
            }
            assertTrue(attempted.get());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
            FeatureFormDraftStore.Snapshot failed = FeatureFormDraftStore.load(app);
            assertNotNull(failed);
            assertTrue(failed.featureId >= 0);
            assertEquals(1, DatabaseUtils.queryNumEntries(map.getDatabase(false), layer.getPath().getName()));
            Bitmap photo = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
            try (FileOutputStream output = new FileOutputStream(missingPhoto)) {
                assertTrue(photo.compress(Bitmap.CompressFormat.JPEG, 90, output));
            } finally { photo.recycle(); }
            scenario.onActivity(this::save);
            deadline = SystemClock.uptimeMillis() + 15000;
            while (FeatureFormDraftStore.load(app) != null && SystemClock.uptimeMillis() < deadline) Thread.sleep(50);
            assertNull(FeatureFormDraftStore.load(app));
            assertEquals(1, DatabaseUtils.queryNumEntries(map.getDatabase(false), layer.getPath().getName()));
            assertNotNull(layer.getFeature(failed.featureId));
        } finally {
            FeatureFormDraftStore.clear(app);
            assertTrue(layer.delete(false));
            map.save();
            missingPhoto.delete();
        }
    }
}
