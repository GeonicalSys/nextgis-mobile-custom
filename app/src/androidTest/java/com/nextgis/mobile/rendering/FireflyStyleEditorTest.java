package com.nextgis.mobile.rendering;

import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.fragment.StyleFragment;
import com.nextgis.maplibui.mapui.VectorLayerUI;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Actual editor, scrolling and touches; configuration belongs only to this test window. */
@RunWith(AndroidJUnit4.class)
public class FireflyStyleEditorTest {
    public static class ConfiguredEditor extends StyleFragment {
        Configuration configuration;
        boolean dark;
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            // Retain the Activity's WindowManager token while overriding only this window's resources.
            mContext = new android.view.ContextThemeWrapper(requireActivity(),
                    dark ? R.style.SdfTheme_Dark : R.style.SdfTheme_Light);
            mContext.applyOverrideConfiguration(configuration);
        }
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            return super.onCreateView(inflater.cloneInContext(mContext), container, state);
        }
    }

    private static void textFits(TextView text) {
        assertNotNull(text); assertTrue(text.isShown());
        android.text.Layout layout = text.getLayout(); assertNotNull(layout);
        assertTrue(text.getText() + " fits vertically", layout.getHeight() <= text.getHeight()
                - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
        assertEquals(text.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(text.getText() + " has no ellipsis", 0, layout.getEllipsisCount(line));
            assertTrue(text.getText() + " fits horizontally", layout.getLineMax(line) <= layout.getWidth() + 1);
        }
    }

    private static void reveal(VectorStyleRenderingTest.Fixture f, ConfiguredEditor editor, int id) {
        f.screen.onActivity(activity -> {
            View view = editor.requireView().findViewById(id);
            view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void touch(VectorStyleRenderingTest.Fixture f, ConfiguredEditor editor, int id) {
        AtomicReference<Rect> target = new AtomicReference<>();
        f.screen.onActivity(activity -> {
            View view = editor.requireView().findViewById(id);
            Rect bounds = new Rect(); assertTrue("Action is really visible", view.getGlobalVisibleRect(bounds));
            Rect window = new Rect(); editor.requireDialog().getWindow().getDecorView().getGlobalVisibleRect(window);
            assertTrue("Action stays in the test window", window.contains(bounds));
            assertEquals("Action is not clipped vertically", view.getHeight(), bounds.height());
            int[] origin = new int[2]; view.getLocationOnScreen(origin);
            target.set(new Rect(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight()));
        });
        Rect bounds = target.get(); long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY(), 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY(), 0);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        down.recycle(); up.recycle();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void russianFireflyEditorSupportsSelectionPresetSaveAndCancelAcrossLargeInterfaceMatrix() throws Exception {
        int[][] screens = {{360,640,420},{640,360,420},{320,568,540},{568,320,540},{280,500,620},{500,280,620}};
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            VectorLayerUI layer = f.add(GeoConstants.GTPoint);
            for (float font : new float[]{1, 1.3f, 2}) for (int[] screen : screens)
                for (boolean dark : new boolean[]{false, true}) for (boolean cancel : new boolean[]{false, true}) {
                int orientation = screen[0] > screen[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                f.screen.onActivity(activity -> activity.setRequestedOrientation(orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                AtomicBoolean rotated = new AtomicBoolean(); long deadline = SystemClock.uptimeMillis() + 15000;
                while (!rotated.get() && SystemClock.uptimeMillis() < deadline) {
                    f.screen.onActivity(activity -> rotated.set(activity.getResources().getConfiguration().orientation == orientation));
                    if (!rotated.get()) Thread.sleep(50);
                }
                assertTrue("Requested Activity orientation", rotated.get());
                SimpleMarkerStyle original = new SimpleMarkerStyle(Color.GREEN, Color.BLACK, 8, SimpleMarkerStyle.MarkerStyleCircle);
                original.setCircleBlur(2);
                SimpleMarkerStyle edited = original.clone();
                ConfiguredEditor editor = new ConfiguredEditor();
                AtomicBoolean saved = new AtomicBoolean();
                f.screen.onActivity(activity -> {
                    Configuration config = new Configuration(activity.getResources().getConfiguration());
                    config.setLocale(new Locale("ru")); config.fontScale = font; config.densityDpi = screen[2];
                    config.screenWidthDp = screen[0]; config.screenHeightDp = screen[1];
                    config.smallestScreenWidthDp = Math.min(screen[0], screen[1]); config.orientation = orientation;
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                            | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                    editor.configuration = config; editor.dark = dark;
                    editor.setStyle(edited); editor.setLayer(layer); editor.setTitle("Стиль точки");
                    editor.setPositiveText("Сохранить"); editor.setNegativeText("Отменить");
                    editor.setOnPositiveClickedListener(() -> {
                        layer.setRenderer(new SimpleFeatureRenderer(layer, edited)); assertTrue(layer.save());
                        saved.set(true); editor.dismiss();
                    });
                    editor.setOnNegativeClickedListener(editor::dismiss);
                    editor.showNow(activity.getSupportFragmentManager(), "firefly-ui");
                    editor.requireDialog().getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    ((android.view.inputmethod.InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                            .hideSoftInputFromWindow(editor.requireView().getWindowToken(), 0);
                    Rect usable = new Rect(); activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);
                    float density = screen[2] / 160f;
                    editor.requireDialog().getWindow().setLayout(Math.min(usable.width(), Math.round(screen[0] * density)),
                            Math.min(usable.height(), Math.round(screen[1] * density)));
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                f.screen.onActivity(activity -> ((Spinner) editor.requireView().findViewById(R.id.type)).setSelection(8));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                f.screen.onActivity(activity -> {
                    assertEquals(SimpleMarkerStyle.MarkerStyleFirefly, edited.getType());
                    assertEquals("Changing type preserves the existing colour", Color.GREEN, edited.getColor());
                    assertEquals(2, edited.getCircleBlur(), 0);
                    textFits((TextView) ((Spinner) editor.requireView().findViewById(R.id.type)).getSelectedView());
                    for (int id : new int[]{R.id.marker_size_label, R.id.marker_blur_label, R.id.marker_fill_label,
                            R.id.marker_stroke_label, R.id.marker_width_label, R.id.fill_alpha_label, R.id.stroke_alpha_label,
                            R.id.firefly_hint, R.id.firefly_preset}) textFits(editor.requireView().findViewById(id));
                });
                reveal(f, editor, R.id.firefly_preset); touch(f, editor, R.id.firefly_preset);
                f.screen.onActivity(activity -> {
                    assertEquals(Color.WHITE, edited.getColor()); assertEquals(Color.rgb(255,102,133), edited.getOutColor());
                    assertEquals(6, edited.getSize(), 0); assertEquals(14, edited.getWidth(), 0);
                    android.graphics.Bitmap preview = com.nextgis.maplibui.util.FeatureTypePreview.render(editor.requireContext(), edited);
                    assertEquals("Category preview has the same crisp white core", Color.WHITE,
                            preview.getPixel(preview.getWidth() / 2, preview.getHeight() / 2));
                    assertEquals("The whole preview fits inside its transparent margin", 0, Color.alpha(preview.getPixel(0, 0)));
                    preview.recycle();
                    textFits(editor.requireView().findViewById(R.id.button_positive));
                    textFits(editor.requireView().findViewById(R.id.button_negative));
                });
                touch(f, editor, cancel ? R.id.button_negative : R.id.button_positive);
                assertEquals(!cancel, saved.get());
                assertEquals("Editing a clone never changes the original circle", SimpleMarkerStyle.MarkerStyleCircle, original.getType());
                assertEquals(2, original.getCircleBlur(), 0);
                if (!cancel) {
                    SimpleMarkerStyle restored = new SimpleMarkerStyle(); restored.fromJSON(edited.toJSON());
                    assertEquals(SimpleMarkerStyle.MarkerStyleFirefly, restored.getType());
                    assertEquals(Color.WHITE, restored.getColor());
                }
            }
        }
    }
}
