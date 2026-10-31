package com.nextgis.maplibui.view;

import android.app.Application;
import android.content.res.Configuration;
import android.database.MatrixCursor;
import android.graphics.Rect;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplibui.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import static org.junit.Assert.*;

/** Actual panel widgets, native CPU font metrics and bounded available panel widths.
 * Technical selected-track metadata only; no GIS Activity, DB, map or export is started.
 * 176/216 are addressed available panel widths, not a claim that the full viewport is available.
 * Outer map-rail/stack, device touch, installed APK and OEM acceptance remain separate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TrackSelectionPanelTest {
    private interface Probe { void run(Host host); }
    private static final class Host implements AutoCloseable {
        final ActivityController<AppCompatActivity> controller;
        final AppCompatActivity activity;
        final TrackSelectionPanel panel;
        final int availableDp;
        final String witness;
        Host(int viewportDp, float fontScale, String locale, boolean dark) {
            availableDp = viewportDp == 280 ? 176 : 216;
            witness = "viewport=" + viewportDp + "/available=" + availableDp + "/font="
                    + fontScale + "/" + locale + (dark ? "/dark" : "/light");
            RuntimeEnvironment.setQualifiers(locale + "-w" + viewportDp + "dp-h640dp-mdpi");
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
            panel = new TrackSelectionPanel(activity);
            assertEquals(witness, fontScale, activity.getResources().getConfiguration().fontScale, .001f);
            DisplayMetrics effective = activity.getResources().getDisplayMetrics();
            assertTrue(witness, effective.density > 0f);
            assertEquals(witness, fontScale, effective.scaledDensity / effective.density, .001f);
            TextView title = panel.findViewById(R.id.track_panel_title);
            assertEquals("Native-font setup must not silently use legacy fake metrics",
                    "org.robolectric.shadows.ShadowNativePaint", Shadow.extract(title.getPaint())
                            .getClass().getName());
            assertTrue("Native glyph setup needs nontrivial font advance",
                    title.getPaint().measureText("WWWWWWWW") > title.getTextSize() * 3f);
        }
        int px(int dp) { return Math.round(dp * activity.getResources().getDisplayMetrics().density); }
        void measure(int available) { measure(available, 600); }
        void measure(int available, int naturalHeightLimit) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(px(available), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(naturalHeightLimit), View.MeasureSpec.AT_MOST));
            panel.layout(0, 0, panel.getMeasuredWidth(), panel.getMeasuredHeight());
            assertEquals(witness, px(available), panel.getWidth());
            View content = ((ViewGroup) panel).getChildAt(0);
            assertEquals(witness + " should wrap before emergency scale", 1f, content.getScaleX(), .001f);
            assertEquals(witness + " should wrap before emergency scale", 1f, content.getScaleY(), .001f);
        }
        TextView share() {
            int id = activity.getResources().getIdentifier("track_panel_share", "id",
                    activity.getResources().getResourcePackageName(R.id.track_panel_title));
            assertTrue("Selected-track Share has a stable owned ID", id != 0);
            TextView share = panel.findViewById(id);
            assertNotNull("Actual selected-track panel exposes Share", share);
            return share;
        }
        @Override public void close() { controller.pause().stop().destroy(); }
    }
    private static TrackLayer.TrackInfo info(int id, boolean visible) {
        try (MatrixCursor cursor = new MatrixCursor(new String[]{TrackLayer.FIELD_ID,
                TrackLayer.FIELD_NAME, TrackLayer.FIELD_COLOR, TrackLayer.FIELD_VISIBLE,
                TrackLayer.FIELD_END})) {
            cursor.addRow(new Object[]{id, "technical track", 0xff008844, visible ? 1 : 0, null});
            assertTrue(cursor.moveToFirst());
            return TrackLayer.TrackInfo.fromCursor(cursor);
        }
    }
    private void matrix(Probe probe) {
        for (int viewport : new int[]{280, 320})
            for (float fontScale : new float[]{1f, 2f})
                for (String locale : new String[]{"ru", "en"})
                    for (boolean dark : new boolean[]{false, true})
                        try (Host host = new Host(viewport, fontScale, locale, dark)) {
                            probe.run(host);
                        }
    }
    private static Rect bounds(View view, ViewGroup root) {
        Rect rect = new Rect(0, 0, view.getWidth(), view.getHeight());
        root.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }
    private static List<TextView> buttons(Host host) {
        return Arrays.asList(host.panel.findViewById(R.id.track_panel_color),
                host.panel.findViewById(R.id.track_panel_rename),
                host.panel.findViewById(R.id.track_panel_visibility), host.share(),
                host.panel.findViewById(R.id.track_panel_delete));
    }
    private static void completeText(Host host, TextView text) {
        String witness = host.witness + ": " + text.getText();
        assertEquals(witness, View.VISIBLE, text.getVisibility());
        assertTrue(witness, text.getText().length() > 0);
        Layout layout = text.getLayout();
        assertNotNull(witness, layout);
        assertTrue(witness, layout.getLineCount() > 0);
        assertEquals(witness, text.getText().length(), layout.getLineEnd(layout.getLineCount() - 1));
        int innerWidth = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int innerHeight = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        assertTrue(witness, innerWidth > 0 && innerHeight > 0);
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertEquals(witness, 0, layout.getEllipsisCount(line));
            assertTrue(witness, layout.getLineLeft(line) >= -1f);
            assertTrue(witness, layout.getLineRight(line) <= innerWidth + 1f);
        }
        assertTrue(witness, layout.getLineBottom(layout.getLineCount() - 1) <= innerHeight + 1);
    }
    private static String actionLayoutDiagnostic(Host host) {
        StringBuilder result = new StringBuilder(" panel=").append(host.panel.getWidth())
                .append('x').append(host.panel.getHeight());
        List<TextView> actions = buttons(host);
        for (int i = 0; i < actions.size(); i++) {
            TextView action = actions.get(i);
            ViewGroup row = (ViewGroup) action.getParent();
            Layout text = action.getLayout();
            result.append(" [").append(i).append(':')
                    .append(action.getResources().getResourceEntryName(action.getId()))
                    .append(" rect=").append(bounds(action, host.panel))
                    .append(" localTop=").append(action.getTop())
                    .append(" measured=").append(action.getMeasuredWidth()).append('x')
                    .append(action.getMeasuredHeight()).append(" baseline=").append(action.getBaseline())
                    .append(" row#=").append(System.identityHashCode(row))
                    .append(" rowRect=").append(bounds(row, host.panel))
                    .append(" rowMeasured=").append(row.getMeasuredWidth()).append('x')
                    .append(row.getMeasuredHeight()).append(" rowIndex=").append(row.indexOfChild(action))
                    .append(" textLines=").append(text == null ? -1 : text.getLineCount())
                    .append(" lastTextBottom=").append(text == null || text.getLineCount() == 0 ? -1
                            : text.getLineBottom(text.getLineCount() - 1))
                    .append(" paddingTB=").append(action.getCompoundPaddingTop()).append('/')
                    .append(action.getCompoundPaddingBottom()).append(']');
        }
        return result.toString();
    }

    private static void actionTargetsFit(Host host) {
        List<TextView> buttons = buttons(host);
        for (int i = 0; i < buttons.size(); i++) {
            TextView button = buttons.get(i);
            Rect rect = bounds(button, host.panel);
            assertTrue(host.witness + " minimum action target", rect.width() >= host.px(48)
                    && rect.height() >= host.px(48));
            assertTrue(host.witness + " action outside panel", rect.left >= 0 && rect.top >= 0
                    && rect.right <= host.panel.getWidth() && rect.bottom <= host.panel.getHeight());
            completeText(host, button);
            for (int j = i + 1; j < buttons.size(); j++)
                assertFalse(host.witness + " overlapping action targets pair=" + i + "/" + j
                                + actionLayoutDiagnostic(host),
                        Rect.intersects(rect, bounds(buttons.get(j), host.panel)));
        }
    }
    private static void targetsFit(Host host) {
        actionTargetsFit(host);
        View close = host.panel.findViewById(R.id.track_panel_close);
        Rect rect = bounds(close, host.panel);
        assertTrue(host.witness, rect.width() >= host.px(48) && rect.height() >= host.px(48));
        assertTrue(host.witness, rect.left >= 0 && rect.top >= 0
                && rect.right <= host.panel.getWidth() && rect.bottom <= host.panel.getHeight());
        completeText(host, host.panel.findViewById(R.id.track_panel_title));
    }

    private static void currentRowsHaveTheIndependentCapacityAndOrder(Host host) {
        List<TextView> controls = buttons(host);
        int inner = host.panel.getWidth() - host.panel.getPaddingLeft() - host.panel.getPaddingRight();
        int columns = Math.max(1, Math.min(5, inner / host.px(48)));
        List<ViewGroup> rows = new ArrayList<>();
        for (int i = 0; i < controls.size(); i++) {
            ViewGroup row = (ViewGroup) controls.get(i).getParent();
            int rowIndex = i / columns;
            if (!rows.contains(row)) rows.add(row);
            assertEquals(host.witness + " rows preserve logical action order", rowIndex, rows.indexOf(row));
            assertEquals(host.witness + " action index inside row", i % columns,
                    row.indexOfChild(controls.get(i)));
            assertEquals(host.witness + " row has only its current logical cohort",
                    Math.min(columns, 5 - rowIndex * columns), row.getChildCount());
            assertEquals(host.witness, View.VISIBLE, row.getVisibility());
        }
        assertEquals(host.witness, (5 + columns - 1) / columns, rows.size());
    }

    @Test public void shareIsLocalizedAndUsesOnlyTheAdditiveActionValue() {
        matrix(host -> {
            List<Integer> actions = new ArrayList<>();
            host.panel.bind(info(41, true), actions::add);
            assertEquals(1, TrackSelectionPanel.COLOR);
            assertEquals(2, TrackSelectionPanel.RENAME);
            assertEquals(3, TrackSelectionPanel.VISIBILITY);
            assertEquals(4, TrackSelectionPanel.DELETE);
            assertEquals(5, TrackSelectionPanel.CLOSE);
            assertEquals(host.activity.getString(R.string.menu_share), host.share().getText().toString());
            assertEquals(host.share().getText().toString(), host.share().getContentDescription().toString());
            assertTrue(host.share().performClick());
            assertEquals(Arrays.asList(6), actions);
        });
    }

    @Test public void narrowPanelsKeepFiveReadableSeparateActionTargetsAtBothFontSizes() {
        matrix(host -> {
            host.panel.bind(info(41, true), ignored -> {});
            host.measure(host.availableDp);
            targetsFit(host);
            currentRowsHaveTheIndependentCapacityAndOrder(host);
        });
    }

    @Test public void wideNarrowWideRemeasureRetainsViewsOrderAndEveryActionListener() {
        matrix(host -> {
            List<Integer> actions = new ArrayList<>();
            host.panel.bind(info(41, true), actions::add);
            List<TextView> original = buttons(host);
            for (int width : new int[]{320, host.availableDp, 320}) {
                host.measure(width);
                targetsFit(host);
                List<TextView> current = buttons(host);
                for (int i = 0; i < original.size(); i++) assertSame(original.get(i), current.get(i));
                actions.clear();
                for (TextView button : current) assertTrue(button.performClick());
                assertEquals(Arrays.asList(1, 2, 3, 6, 4), actions);
                if (width == 320) {
                    ViewGroup row = (ViewGroup) current.get(0).getParent();
                    assertEquals(5, row.getChildCount());
                    for (int i = 0; i < current.size(); i++) assertSame(current.get(i), row.getChildAt(i));
                }
            }
            actions.clear();
            assertTrue(host.panel.findViewById(R.id.track_panel_close).performClick());
            assertEquals(Arrays.asList(5), actions);
        });
    }

    @Test public void hiddenRebindRetainsShareAndUsesOnlyTheLatestListener() {
        matrix(host -> {
            List<Integer> oldActions = new ArrayList<>(), currentActions = new ArrayList<>();
            host.panel.bind(info(41, true), oldActions::add);
            TextView original = host.share();
            host.panel.bind(info(73, false), currentActions::add);
            host.measure(host.availableDp);
            targetsFit(host);
            assertSame(original, host.share());
            assertEquals(View.VISIBLE, host.panel.getVisibility());
            assertEquals(host.activity.getString(R.string.menu_visibility_on),
                    ((TextView) host.panel.findViewById(R.id.track_panel_visibility)).getText().toString());
            assertEquals(host.activity.getString(R.string.track_hidden_name, "technical track"),
                    ((TextView) host.panel.findViewById(R.id.track_panel_title)).getText().toString());
            assertTrue(host.share().performClick());
            assertTrue(oldActions.isEmpty());
            assertEquals(Arrays.asList(6), currentActions);
        });
    }

    @Test public void veryNarrowActionRowsReflowThroughTwoAndOneColumnsWithoutLosingTargetsOrListeners() {
        matrix(host -> {
            List<Integer> actions = new ArrayList<>();
            host.panel.bind(info(41, true), actions::add);
            List<TextView> original = buttons(host);
            // Isolate horizontal action capacity with a tall natural-height budget.
            // At 64dp the 48dp Close consumes the header's remaining width: no title/outer-window claim.
            for (int width : new int[]{176, 112, 216, 64, 320, 112, 176}) {
                host.measure(width, 2000);
                actionTargetsFit(host);
                currentRowsHaveTheIndependentCapacityAndOrder(host);
                List<TextView> current = buttons(host);
                actions.clear();
                for (int i = 0; i < original.size(); i++) {
                    assertSame(original.get(i), current.get(i));
                    assertTrue(current.get(i).performClick());
                }
                assertEquals(Arrays.asList(1, 2, 3, 6, 4), actions);
            }
        });
    }

}
