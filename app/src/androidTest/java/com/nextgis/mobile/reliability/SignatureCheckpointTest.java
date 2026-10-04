package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplibui.formcontrol.Sign;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.mobile.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SignatureCheckpointTest {
    private static void size(Sign sign, int width, int height) {
        sign.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        sign.layout(0, 0, width, height);
    }

    private static void touch(Sign sign, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0);
        try { sign.onTouchEvent(event); } finally { event.recycle(); }
    }

    @Test public void signatureAndTypedControlsSurviveDurableCheckpointAndResize() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            String prefix = "signature-test-" + UUID.randomUUID();
            Context storage = new ContextWrapper(app) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    return super.getSharedPreferences(prefix + name, mode);
                }
            };
            Context theme = new ContextThemeWrapper(app, R.style.AppTheme);
            File image = new File(app.getCacheDir(), prefix + ".png");
            try {
                Sign original = new Sign(theme);
                size(original, 300, 160);
                touch(original, MotionEvent.ACTION_DOWN, 20, 80);
                touch(original, MotionEvent.ACTION_MOVE, 160, 120);
                touch(original, MotionEvent.ACTION_UP, 160, 120);
                Bundle state = new Bundle();
                original.saveState(state);
                state.putLong("identifier", 549755813889L);
                state.putDouble("value", 1.125);
                state.putBoolean("confirmed", true);
                state.putIntegerArrayList("choices", new ArrayList<>(Arrays.asList(1, 2)));
                FeatureFormDraftStore.Snapshot snapshot = new FeatureFormDraftStore.Snapshot();
                snapshot.layerId = 1;
                snapshot.geometryWkt = "POINT (10 20)";
                snapshot.mapPath = "/isolated-test-project";
                FeatureFormDraftStore.putControlStateFromBundle(snapshot, state);
                assertTrue(FeatureFormDraftStore.save(storage, snapshot));
                FeatureFormDraftStore.Snapshot loaded = FeatureFormDraftStore.load(storage);
                assertNotNull(loaded);
                assertEquals(snapshot.operationId, loaded.operationId);
                Bundle restoredState = FeatureFormDraftStore.controlStateToBundle(loaded);
                assertEquals(549755813889L, restoredState.getLong("identifier"));
                assertEquals(1.125, restoredState.getDouble("value"), 0);
                assertTrue(restoredState.getBoolean("confirmed"));
                assertEquals(Arrays.asList(1, 2), restoredState.getIntegerArrayList("choices"));
                Sign restored = new Sign(theme);
                restored.init(null, Collections.emptyList(), restoredState, null, null, null, null);
                size(restored, 600, 320);
                assertTrue(restored.hasEdits());
                assertTrue(restored.needsSave());
                restored.save(300, 160, false, image);
                Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath());
                assertNotNull(bitmap);
                try {
                    int background = bitmap.getPixel(0, 0), ink = 0;
                    for (int y = 50; y < 150; y++)
                        for (int x = 10; x < 180; x++)
                            if (bitmap.getPixel(x, y) != background) ink++;
                    assertTrue("Recovered signature must contain the original stroke", ink > 20);
                } finally { bitmap.recycle(); }
            } catch (Exception error) { throw new AssertionError(error); }
            finally {
                FeatureFormDraftStore.clear(storage);
                image.delete();
            }
        });
    }

    @Test public void failedSignatureWriteKeepsInkAvailableForRetry() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Sign sign = new Sign(new ContextThemeWrapper(app, R.style.AppTheme));
            size(sign, 300, 160);
            touch(sign, MotionEvent.ACTION_DOWN, 20, 80);
            touch(sign, MotionEvent.ACTION_MOVE, 160, 120);
            touch(sign, MotionEvent.ACTION_UP, 160, 120);
            File missing = new File(app.getCacheDir(), UUID.randomUUID() + "/missing/sign.png");
            try {
                sign.save(300, 160, false, missing);
                fail("Unavailable destination must fail");
            } catch (IOException expected) { }
            assertTrue(sign.hasEdits());
            assertTrue(sign.needsSave());
            Bundle state = new Bundle();
            sign.saveState(state);
            assertFalse(state.isEmpty());
        });
    }
}
