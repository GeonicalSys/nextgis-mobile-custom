package com.nextgis.maplibui.dialog;

import android.app.Application;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplib.datasource.ngw.LayerWithStyles;
import com.nextgis.maplibui.R;
import com.nextgis.maplibui.util.CheckState;
import java.util.ArrayList;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/**
 * Actual adapter/XML/theme layout measurements using a generic AppCompat host.
 * Technical resource labels only, no GIS Activity, map, account, network, DB or import writes.
 * These assertions are not native rendering, touch, OEM, rotation or installed-APK acceptance.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ResourceSelectionRowLayoutTest {
    private interface Probe { void run(Host host); }

    private static final class RowResource extends LayerWithStyles {
        private final int styles;
        RowResource(int type, int styles, String name) {
            super(100L, null);
            mType = type;
            mName = name;
            this.styles = styles;
        }
        @Override public int getStyleCount() { return styles; }
    }

    private static final class Host implements AutoCloseable {
        final ActivityController<AppCompatActivity> controller;
        final AppCompatActivity activity;
        final NGWResourcesListAdapter adapter;
        final int width;
        final String witness;
        Host(int widthDp, float fontScale, String locale, boolean dark) {
            witness = widthDp + "dp/font" + fontScale + "/" + locale + (dark ? "/dark" : "/light");
            RuntimeEnvironment.setQualifiers(locale + "-w" + widthDp + "dp-h640dp-mdpi");
            Configuration config = new Configuration(RuntimeEnvironment.getApplication()
                    .getResources().getConfiguration());
            config.fontScale = fontScale;
            config.setLocale(Locale.forLanguageTag(locale));
            DisplayMetrics metrics = new DisplayMetrics();
            metrics.setTo(RuntimeEnvironment.getApplication().getResources().getDisplayMetrics());
            metrics.scaledDensity = metrics.density * fontScale;
            RuntimeEnvironment.getApplication().getResources().updateConfiguration(config, metrics);
            controller = Robolectric.buildActivity(AppCompatActivity.class);
            activity = controller.get();
            activity.setTheme(dark ? R.style.Theme_NextGIS_AppCompat_Dark
                    : R.style.Theme_NextGIS_AppCompat_Light);
            controller.create().start().resume();
            width = Math.round(widthDp * activity.getResources().getDisplayMetrics().density);
            adapter = new NGWResourcesListAdapter(activity);
            adapter.setTypeMask(Connection.NGWResourceTypeResourceGroup
                    | Connection.NGWResourceTypeRasterLayer | Connection.NGWResourceTypeWMSClient
                    | Connection.NGWResourceTypeWebMap | Connection.NGWResourceTypeVectorLayer
                    | Connection.NGWResourceTypeCollector);
            adapter.setCheckState(new ArrayList<>());
            assertEquals(witness, fontScale,
                    activity.getResources().getConfiguration().fontScale, 0.001f);
            DisplayMetrics effectiveMetrics = activity.getResources().getDisplayMetrics();
            assertTrue(witness + " invalid density", effectiveMetrics.density > 0f);
            assertEquals(witness + " effective text scale differs from requested fontScale", fontScale,
                    effectiveMetrics.scaledDensity / effectiveMetrics.density, 0.001f);
            // Setup calibration: a legacy one-pixel-per-character paint is not a layout oracle.
            Paint glyphProbe = new Paint();
            assertEquals(witness + " expected CPU native text backend",
                    "org.robolectric.shadows.ShadowNativePaint",
                    Shadow.extract(glyphProbe).getClass().getName());
            glyphProbe.setTextSize(14f * effectiveMetrics.scaledDensity);
            float narrowAdvance = glyphProbe.measureText("iiii");
            float wideAdvance = glyphProbe.measureText("WWWW");
            assertTrue(witness + " native glyph/font setup has no positive advance", narrowAdvance > 0f);
            assertTrue(witness + " native glyph calibration is still fixed-width/legacy",
                    wideAdvance > 2f * narrowAdvance);
            assertTrue(witness + " native full caption has no advance",
                    glyphProbe.measureText(activity.getString(R.string.ngw_collector_import)) > 0f);
        }
        void measureRow(View row) {
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
        }
        @Override public void close() { controller.pause().stop().destroy(); }
    }

    private void matrix(Probe probe) {
        for (int width : new int[]{280, 320})
            for (float scale : new float[]{1f, 2f})
                for (String locale : new String[]{"ru", "en"})
                    for (boolean dark : new boolean[]{false, true})
                        try (Host host = new Host(width, scale, locale, dark)) {
                            probe.run(host);
                        }
    }

    private static Rect bounds(View view, ViewGroup root) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private static void completeText(Host host, TextView text, ViewGroup root) {
        String message = host.witness + " text=" + text.getText();
        assertEquals(message, View.VISIBLE, text.getVisibility());
        assertTrue(message, text.getText().length() > 0);
        Layout layout = text.getLayout();
        assertNotNull(message, layout);
        assertTrue(message, layout.getLineCount() > 0);
        int innerWidth = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int innerHeight = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue(message + " no text width", innerWidth > 0);
        assertEquals(message + " missing suffix", text.getText().length(),
                layout.getLineEnd(layout.getLineCount() - 1));
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(message + " ellipsis line" + line, 0, layout.getEllipsisCount(line));
            assertTrue(message + " left clipping line" + line, layout.getLineLeft(line) >= -1f);
            assertTrue(message + " right clipping line" + line,
                    layout.getLineRight(line) <= innerWidth + 1f);
        }
        assertTrue(message + " wrapped lines are clipped vertically",
                layout.getLineBottom(layout.getLineCount() - 1) <= innerHeight + 1);
        Rect rect = bounds(text, root);
        assertTrue(message + " outside row", rect.left >= 0 && rect.top >= 0
                && rect.right <= root.getWidth() && rect.bottom <= root.getHeight());
    }

    private static void separated(Host host, ViewGroup row, View first, View second) {
        assertFalse(host.witness + " overlapping visible elements",
                Rect.intersects(bounds(first, row), bounds(second, row)));
    }

    private ViewGroup bind(Host host, RowResource resource, View recycled) {
        host.adapter.getCheckState().clear();
        boolean raster = resource.getType() == Connection.NGWResourceTypeRasterLayer
                || resource.getType() == Connection.NGWResourceTypeWMSClient
                || resource.getType() == Connection.NGWResourceTypeWebMap;
        host.adapter.getCheckState().add(new CheckState(resource.getId(), raster, !raster));
        ViewGroup row = (ViewGroup) host.adapter.getResourceView(resource, recycled);
        host.measureRow(row);
        return row;
    }

    @Test public void collectorFullCaptionAndNameFitAtSmallWidthsLargeFontBothLocalesThemes() {
        matrix(host -> {
            ViewGroup row = bind(host, new RowResource(Connection.NGWResourceTypeCollector, 0,
                    "Technical resource name for layout measurement"), null);
            TextView caption = row.findViewById(R.id.type1);
            assertEquals(host.activity.getString(R.string.ngw_collector_import), caption.getText().toString());
            completeText(host, caption, row);
            TextView name = row.findViewById(R.id.tvName);
            completeText(host, name, row);
            separated(host, row, name, caption);
            separated(host, row, caption, row.findViewById(R.id.checkBox1));
            assertTrue(((CheckBox) row.findViewById(R.id.checkBox1)).isChecked());
        });
    }

    @Test public void adjacentSingleAndStyledDoubleRowsKeepCompleteLabelsNamesAndSeparateControls() {
        matrix(host -> {
            int[] types = {Connection.NGWResourceTypeRasterLayer, Connection.NGWResourceTypeWMSClient,
                    Connection.NGWResourceTypeWebMap, Connection.NGWResourceTypeVectorLayer};
            for (int type : types) {
                for (int styles : type == Connection.NGWResourceTypeVectorLayer ? new int[]{0, 1} : new int[]{0}) {
                    ViewGroup row = bind(host, new RowResource(type, styles,
                            "Technical name with several words for wrapping"), null);
                    TextView name = row.findViewById(R.id.tvName);
                    TextView first = row.findViewById(R.id.type1);
                    completeText(host, name, row);
                    completeText(host, first, row);
                    separated(host, row, name, first);
                    separated(host, row, first, row.findViewById(R.id.checkBox1));
                    if (styles > 0) {
                        TextView second = row.findViewById(R.id.type2);
                        completeText(host, second, row);
                        separated(host, row, name, second);
                        separated(host, row, first, second);
                        separated(host, row, second, row.findViewById(R.id.checkBox2));
                        assertFalse(((CheckBox) row.findViewById(R.id.checkBox1)).isChecked());
                        assertTrue(((CheckBox) row.findViewById(R.id.checkBox2)).isChecked());
                    }
                }
            }
        });
    }

    @Test public void recycledSingleRowRelayoutKeepsCurrentFullCaptionAndModeWithoutStateMutation() {
        matrix(host -> {
            View recycled = null;
            for (int type : new int[]{Connection.NGWResourceTypeCollector, Connection.NGWResourceTypeRasterLayer,
                    Connection.NGWResourceTypeVectorLayer, Connection.NGWResourceTypeWMSClient,
                    Connection.NGWResourceTypeCollector, Connection.NGWResourceTypeWebMap}) {
                ViewGroup row = bind(host, new RowResource(type, 0, "Technical recycled row"), recycled);
                if (recycled != null) assertSame(recycled, row);
                CheckState state = host.adapter.getCheckState().get(0);
                boolean before1 = state.isCheckState1(), before2 = state.isCheckState2();
                completeText(host, (TextView) row.findViewById(R.id.type1), row);
                host.measureRow(row);
                assertSame(state, host.adapter.getCheckState().get(0));
                assertEquals(before1, state.isCheckState1());
                assertEquals(before2, state.isCheckState2());
                recycled = row;
            }
        });
    }

    @Test public void resourceScreenRetainsBoundedListReachableAddButtonAndScrollableFullPath() {
        matrix(host -> {
            ViewGroup screen = (ViewGroup) LayoutInflater.from(host.activity)
                    .inflate(R.layout.activity_resources, null, false);
            RowResource parent = new RowResource(Connection.NGWResourceTypeResourceGroup, 0,
                    "Technical long ancestor name for horizontal path measurement");
            RowResource current = new RowResource(Connection.NGWResourceTypeResourceGroup, 0,
                    "Technical current folder with another long path label");
            current.setParent(parent);
            host.adapter.mCurrentResource = current;
            LinearLayout path = screen.findViewById(R.id.path);
            host.adapter.setPathLayout(path);
            int height = Math.round(640 * host.activity.getResources().getDisplayMetrics().density);
            screen.measure(View.MeasureSpec.makeMeasureSpec(host.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            screen.layout(0, 0, screen.getMeasuredWidth(), screen.getMeasuredHeight());
            View list = screen.findViewById(R.id.listView);
            TextView add = screen.findViewById(R.id.button1);
            assertTrue(host.witness + " list lost its viewport", list.getHeight() > 0);
            completeText(host, add, screen);
            assertTrue(host.witness + " list covers Add",
                    bounds(list, screen).bottom <= bounds(add, screen).top);
            for (int i = 0; i < path.getChildCount(); i++) {
                View child = path.getChildAt(i);
                if (child instanceof TextView) completeText(host, (TextView) child, path);
            }
            HorizontalScrollView scroller = (HorizontalScrollView) path.getParent();
            scroller.scrollTo(path.getWidth(), 0);
            assertTrue(host.witness + " long path cannot scroll", scroller.getScrollX() > 0);
            assertTrue(host.witness + " current path tail unreachable",
                    scroller.getScrollX() + scroller.getWidth() >= path.getRight());
        });
    }

    @Test public void collectorRowRestoresHorizontalLayoutAcrossWideNarrowWideWithoutReparenting() {
        for (boolean dark : new boolean[]{false, true}) {
            try (Host host = new Host(280, 2f, "ru", dark)) {
                ViewGroup row = bind(host, new RowResource(Connection.NGWResourceTypeCollector, 0,
                        "Technical recycled row"), null);
                TextView name = row.findViewById(R.id.tvName);
                TextView caption = row.findViewById(R.id.type1);
                CheckBox checkbox = row.findViewById(R.id.checkBox1);
                Object nameOwner = name.getParent(), captionOwner = caption.getParent();
                CheckState state = host.adapter.getCheckState().get(0);
                for (int widthDp : new int[]{640, 280, 640}) {
                    int width = Math.round(widthDp * host.activity.getResources()
                            .getDisplayMetrics().density);
                    row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
                    completeText(host, name, row);
                    completeText(host, caption, row);
                    Rect nameBounds = bounds(name, row), captionBounds = bounds(caption, row);
                    if (widthDp == 280) {
                        assertTrue(host.witness + " narrow full name must precede controls vertically",
                                nameBounds.bottom <= captionBounds.top);
                    } else {
                        assertTrue(host.witness + " wider row did not restore horizontal placement",
                                nameBounds.right <= captionBounds.left);
                    }
                    assertSame(nameOwner, name.getParent());
                    assertSame(captionOwner, caption.getParent());
                    assertSame(state, host.adapter.getCheckState().get(0));
                    assertFalse(state.isCheckState1());
                    assertTrue(state.isCheckState2());
                    assertTrue(checkbox.isChecked());
                    assertEquals(host.activity.getString(R.string.ngw_collector_import),
                            caption.getText().toString());
                }
            }
        }
    }
}
