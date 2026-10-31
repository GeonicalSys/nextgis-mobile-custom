package com.nextgis.maplibui.dialog;

import android.app.Application;
import android.content.Context;
import android.widget.CheckBox;
import com.nextgis.maplib.datasource.ngw.Connection;
import com.nextgis.maplibui.util.CheckState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/**
 * Actual adapter + framework CheckBox; label cases use a plain framework Activity, no GIS/map/DB/network.
 * Local IDs and mode bits are technical inputs, not NGW/business records.
 * The import truth projection is backed by the Activity/Dialog loops, not a business import run.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE, application = Application.class, qualifiers = "ru")
@LooperMode(LooperMode.Mode.PAUSED)
public class ResourceSelectionCheckboxTest {
    private static final int A = 11, B = 12, C = 13;
    private Context context;
    private NGWResourcesListAdapter adapter;

    @Before public void prepare() {
        context = RuntimeEnvironment.getApplication();
        // Neither constructor nor non-PostGIS checkbox path dereferences the Activity.
        adapter = new NGWResourcesListAdapter(null);
        adapter.setCheckState(new ArrayList<>());
    }

    private void states(CheckState... values) {
        adapter.setCheckState(new ArrayList<>(Arrays.asList(values)));
    }

    private static CheckState selected(int id, int mode) {
        return new CheckState(id, mode == 1, mode == 2);
    }

    private CheckBox bind(int id, int mode, boolean recycledChecked) {
        CheckBox box = new CheckBox(context);
        box.setChecked(recycledChecked);
        adapter.setCheckBox(Connection.NGWResourceTypeVectorLayer, box, id, mode);
        return box;
    }

    /** Projects the same positive-mode conditions consumed by both actual import loops. */
    private int importEntries(int id, int mode) {
        int result = 0;
        for (CheckState state : adapter.getCheckState()) {
            if (state.getId() == id
                    && (mode == 1 ? state.isCheckState1() : state.isCheckState2())) result++;
        }
        return result;
    }

    private List<String> snapshot() {
        ArrayList<String> result = new ArrayList<>();
        for (CheckState state : adapter.getCheckState())
            result.add(state.getId() + ":" + state.isCheckState1() + ":" + state.isCheckState2());
        return result;
    }

    @Test public void twoSelectedIdsRemainCheckedIndependentlyOfListOrder() {
        for (int mode : new int[]{1, 2}) {
            CheckState a = selected(A, mode), b = selected(B, mode);
            states(a, b);
            assertTrue(bind(A, mode, false).isChecked());
            assertTrue(bind(B, mode, false).isChecked());
            states(b, a);
            assertTrue(bind(A, mode, false).isChecked());
            assertTrue(bind(B, mode, false).isChecked());
        }
    }

    @Test public void anotherSelectedIdCannotSelectThisRow() {
        for (int mode : new int[]{1, 2}) {
            states(selected(B, mode));
            assertFalse(bind(A, mode, true).isChecked());
            assertEquals(0, importEntries(A, mode));
            assertEquals(1, importEntries(B, mode));
        }
    }

    @Test public void emptyStateResetsARecycledCheckedWidgetForBothModes() {
        states();
        for (int mode : new int[]{1, 2}) assertFalse(bind(A, mode, true).isChecked());
        assertTrue(adapter.getCheckState().isEmpty());
    }

    @Test public void oppositeModeCannotLeaveARecycledWidgetChecked() {
        for (int mode : new int[]{1, 2}) {
            states(selected(A, 3 - mode));
            assertFalse(bind(A, mode, true).isChecked());
            assertEquals(0, importEntries(A, mode));
            assertEquals(1, importEntries(A, 3 - mode));
        }
    }

    @Test public void duplicateLocalIdSplitFlagsRenderAnyPositiveForEachMode() {
        CheckState raster = new CheckState(A, true, false);
        CheckState vector = new CheckState(A, false, true);
        for (boolean reverse : new boolean[]{false, true}) {
            if (reverse) states(vector, raster); else states(raster, vector);
            assertTrue(bind(A, 1, false).isChecked());
            assertTrue(bind(A, 2, false).isChecked());
            assertEquals(1, importEntries(A, 1));
            assertEquals(1, importEntries(A, 2));
        }
    }

    @Test public void bindingDoesNotMutateListOrderObjectsOrModeFlags() {
        CheckState a = new CheckState(A, true, false), b = new CheckState(B, false, true);
        List<CheckState> original = new ArrayList<>(Arrays.asList(a, b));
        adapter.setCheckState(original);
        List<String> before = snapshot();
        bind(A, 1, false); bind(B, 2, false); bind(C, 1, true);
        assertSame(original, adapter.getCheckState());
        assertSame(a, original.get(0)); assertSame(b, original.get(1));
        assertEquals(before, snapshot());
    }

    @Test public void bindingDetachesAnExistingWidgetListenerBeforeChangingValue() {
        states(new CheckState(A, false, true));
        CheckBox box = new CheckBox(context); box.setChecked(true);
        AtomicInteger callbacks = new AtomicInteger();
        box.setOnCheckedChangeListener((button, checked) -> callbacks.incrementAndGet());
        List<String> before = snapshot();
        adapter.setCheckBox(Connection.NGWResourceTypeVectorLayer, box, A, 1);
        assertEquals(0, callbacks.get()); assertEquals(before, snapshot());
        assertFalse(box.isChecked());
    }

    @Test public void ordinaryUniqueIdCheckAndUncheckUseTheActualWidgetCallback() {
        for (int mode : new int[]{1, 2}) {
            states(); CheckBox box = bind(A, mode, false);
            box.setChecked(true);
            assertEquals(1, importEntries(A, mode)); assertEquals(0, importEntries(A, 3 - mode));
            assertEquals(1, adapter.getCheckState().size());
            box.setChecked(false);
            assertEquals(0, importEntries(A, mode)); assertTrue(adapter.getCheckState().isEmpty());
        }
    }

    @Test public void uncheckClearsEveryMatchingPositiveAndPreservesOtherMode() {
        for (int mode : new int[]{1, 2}) {
            CheckState both = new CheckState(A, true, true);
            CheckState requestedOnly = selected(A, mode);
            CheckState otherId = new CheckState(B, true, true);
            states(both, requestedOnly, otherId);
            CheckBox box = bind(A, mode, false);
            // On old renderer this may need the preceding genuine check event first.
            if (!box.isChecked()) box.setChecked(true);
            assertEquals(2, importEntries(A, mode));
            box.setChecked(false);
            assertEquals(0, importEntries(A, mode));
            assertEquals(1, importEntries(A, 3 - mode));
            assertEquals(1, importEntries(B, 1)); assertEquals(1, importEntries(B, 2));
            assertEquals(2, adapter.getCheckState().size());
            assertSame(both, adapter.getCheckState().get(0));
            assertSame(otherId, adapter.getCheckState().get(1));
        }
    }

    @Test public void uncheckCannotStopAtAnEarlierNegativeMatchingRecord() {
        for (int mode : new int[]{1, 2}) {
            CheckState earlierNegative = selected(A, 3 - mode);
            states(earlierNegative, selected(A, mode));
            CheckBox box = bind(A, mode, false); assertTrue(box.isChecked());
            box.setChecked(false);
            assertEquals(0, importEntries(A, mode));
            assertEquals(1, importEntries(A, 3 - mode));
            assertEquals(1, adapter.getCheckState().size());
            assertSame(earlierNegative, adapter.getCheckState().get(0));
        }
    }

    @Test public void falseCallbackAfterModelReplacementCannotCreateASelection() {
        for (int mode : new int[]{1, 2}) {
            states(selected(A, mode)); CheckBox box = bind(A, mode, false);
            assertTrue(box.isChecked());
            adapter.setCheckState(new ArrayList<>());
            box.setChecked(false);
            assertTrue(adapter.getCheckState().isEmpty());
            assertEquals(0, importEntries(A, mode));
        }
    }

    @Test public void trueCallbackCannotAmplifyAnAlreadyPositiveDuplicateCohort() {
        for (int mode : new int[]{1, 2}) {
            states(); CheckBox box = bind(A, mode, false);
            CheckState negative = selected(A, 3 - mode), positive = selected(A, mode);
            states(negative, positive);
            List<String> before = snapshot();
            // The public model changed since binding; callback must read its current cohort.
            box.setChecked(true);
            assertEquals(1, importEntries(A, mode));
            assertEquals(1, importEntries(A, 3 - mode));
            assertEquals(before, snapshot());
        }
    }

    @Test public void newTrueModeMarksOnlyOneExistingRecordWithoutChangingOtherModeMultiplicity() {
        for (int mode : new int[]{1, 2}) {
            CheckState first = selected(A, 3 - mode), second = selected(A, 3 - mode);
            states(first, second); CheckBox box = bind(A, mode, false);
            assertFalse(box.isChecked()); box.setChecked(true);
            assertEquals(1, importEntries(A, mode));
            assertEquals(2, importEntries(A, 3 - mode));
            assertEquals(2, adapter.getCheckState().size());
            assertSame(first, adapter.getCheckState().get(0));
            assertSame(second, adapter.getCheckState().get(1));
        }
    }

    @Test public void uncheckRemovesOnlyNowEmptyMatchingRecordsAndLeavesOtherIdsUntouched() {
        for (int mode : new int[]{1, 2}) {
            CheckState requested = selected(A, mode), otherMode = selected(A, 3 - mode);
            CheckState unrelatedEmpty = new CheckState(B, false, false);
            CheckState unrelatedSelected = new CheckState(C, true, true);
            states(requested, otherMode, unrelatedEmpty, unrelatedSelected);
            CheckBox box = bind(A, mode, false);
            if (!box.isChecked()) box.setChecked(true);
            box.setChecked(false);
            assertEquals(0, importEntries(A, mode));
            assertEquals(1, importEntries(A, 3 - mode));
            assertEquals(3, adapter.getCheckState().size());
            assertSame(otherMode, adapter.getCheckState().get(0));
            assertSame(unrelatedEmpty, adapter.getCheckState().get(1));
            assertSame(unrelatedSelected, adapter.getCheckState().get(2));
            assertFalse(unrelatedEmpty.isCheckState1()); assertFalse(unrelatedEmpty.isCheckState2());
            assertTrue(unrelatedSelected.isCheckState1()); assertTrue(unrelatedSelected.isCheckState2());
        }
    }

    @Test public void unsupportedLegacyModeDoesNotChangeTheBoundVisualState() {
        states(new CheckState(A, true, true)); List<String> before = snapshot();
        assertTrue(bind(A, 3, true).isChecked());
        assertFalse(bind(A, 3, false).isChecked());
        assertEquals(before, snapshot());
    }

    @Test public void bindingCanReadAnImmutableListWithoutMutation() {
        CheckState first = new CheckState(A, true, false), second = new CheckState(A, false, true);
        List<CheckState> immutable = Collections.unmodifiableList(Arrays.asList(first, second));
        adapter.setCheckState(immutable);
        List<String> before = snapshot();
        bind(A, 1, false); bind(A, 2, false); bind(B, 1, true);
        assertSame(immutable, adapter.getCheckState()); assertEquals(before, snapshot());
    }

    @Test public void rebindAfterDuplicateUncheckAgreesWithTheImportProjection() {
        for (int mode : new int[]{1, 2}) {
            states(selected(A, 3 - mode), selected(A, mode));
            CheckBox box = bind(A, mode, false); assertTrue(box.isChecked());
            box.setChecked(false);
            assertEquals(0, importEntries(A, mode));
            adapter.setCheckBox(Connection.NGWResourceTypeVectorLayer, box, A, mode);
            assertFalse(box.isChecked());
            assertTrue(bind(A, 3 - mode, false).isChecked());
        }
    }
    private org.robolectric.android.controller.ActivityController<android.app.Activity> rowHost;

    @org.junit.After public void releaseRowHost() {
        if (rowHost != null) rowHost.pause().stop().destroy();
    }

    /** Technical resource metadata only; actual production adapter dispatch/binding is exercised. */
    private static final class RowResource extends com.nextgis.maplib.datasource.ngw.LayerWithStyles {
        private final int styleCount;
        RowResource(int type, int styles) {
            super(100L, null);
            mType = type;
            mName = "technical row " + getId();
            styleCount = styles;
        }
        @Override public int getStyleCount() { return styleCount; }
    }

    private void prepareRows() {
        rowHost = org.robolectric.Robolectric.buildActivity(android.app.Activity.class);
        rowHost.get().setTheme(android.R.style.Theme_Material_Light);
        rowHost.create().start().resume();
        adapter = new NGWResourcesListAdapter(rowHost.get());
        adapter.setTypeMask(Connection.NGWResourceTypeRasterLayer
                | Connection.NGWResourceTypeWMSClient | Connection.NGWResourceTypeWebMap
                | Connection.NGWResourceTypeVectorLayer | Connection.NGWResourceTypeCollector);
        adapter.setCheckState(new ArrayList<>());
    }

    private String rowLabel(android.view.View row, int labelId) {
        return ((android.widget.TextView) row.findViewById(labelId)).getText().toString();
    }

    private CheckBox rowBox(android.view.View row, int boxId) {
        return (CheckBox) row.findViewById(boxId);
    }

    @Test public void rasterLikeRowsResetLabelAfterVectorRecycling() {
        prepareRows();
        for (int type : new int[]{Connection.NGWResourceTypeRasterLayer,
                Connection.NGWResourceTypeWMSClient, Connection.NGWResourceTypeWebMap}) {
            RowResource vector = new RowResource(Connection.NGWResourceTypeVectorLayer, 0);
            states(selected(vector.getId(), 2));
            android.view.View row = adapter.getResourceView(vector, null);
            assertEquals(context.getString(com.nextgis.maplibui.R.string.vector),
                    rowLabel(row, com.nextgis.maplibui.R.id.type1));
            assertTrue(rowBox(row, com.nextgis.maplibui.R.id.checkBox1).isChecked());
            RowResource target = new RowResource(type, 0);
            states(selected(target.getId(), 1));
            android.view.View rebound = adapter.getResourceView(target, row);
            assertSame(row, rebound);
            assertEquals(context.getString(com.nextgis.maplibui.R.string.raster),
                    rowLabel(rebound, com.nextgis.maplibui.R.id.type1));
            assertTrue(rowBox(rebound, com.nextgis.maplibui.R.id.checkBox1).isChecked());
            assertEquals(1, importEntries(target.getId(), 1));
            assertEquals(0, importEntries(target.getId(), 2));
        }
    }

    @Test public void rasterLikeRowsResetLabelAfterCollectorRecycling() {
        prepareRows();
        for (int type : new int[]{Connection.NGWResourceTypeRasterLayer,
                Connection.NGWResourceTypeWMSClient, Connection.NGWResourceTypeWebMap}) {
            RowResource collector = new RowResource(Connection.NGWResourceTypeCollector, 0);
            states(selected(collector.getId(), 2));
            android.view.View row = adapter.getResourceView(collector, null);
            assertEquals(context.getString(com.nextgis.maplibui.R.string.ngw_collector_import),
                    rowLabel(row, com.nextgis.maplibui.R.id.type1));
            assertTrue(rowBox(row, com.nextgis.maplibui.R.id.checkBox1).isChecked());
            RowResource target = new RowResource(type, 0);
            states(selected(target.getId(), 1));
            android.view.View rebound = adapter.getResourceView(target, row);
            assertSame(row, rebound);
            assertEquals(context.getString(com.nextgis.maplibui.R.string.raster),
                    rowLabel(rebound, com.nextgis.maplibui.R.id.type1));
            assertTrue(rowBox(rebound, com.nextgis.maplibui.R.id.checkBox1).isChecked());
        }
    }

    @Test public void vectorAndCollectorRestoreTheirLabelsAndModeTwoAfterRasterRecycling() {
        prepareRows();
        for (int type : new int[]{Connection.NGWResourceTypeVectorLayer,
                Connection.NGWResourceTypeCollector}) {
            RowResource raster = new RowResource(Connection.NGWResourceTypeRasterLayer, 0);
            states(selected(raster.getId(), 1));
            android.view.View row = adapter.getResourceView(raster, null);
            assertEquals(context.getString(com.nextgis.maplibui.R.string.raster),
                    rowLabel(row, com.nextgis.maplibui.R.id.type1));
            RowResource target = new RowResource(type, 0);
            CheckState rasterModeOnly = selected(target.getId(), 1);
            states(rasterModeOnly);
            android.view.View rebound = adapter.getResourceView(target, row);
            assertSame(row, rebound);
            int caption = type == Connection.NGWResourceTypeCollector
                    ? com.nextgis.maplibui.R.string.ngw_collector_import
                    : com.nextgis.maplibui.R.string.vector;
            assertEquals(context.getString(caption), rowLabel(rebound, com.nextgis.maplibui.R.id.type1));
            CheckBox modeTwo = rowBox(rebound, com.nextgis.maplibui.R.id.checkBox1);
            assertFalse(modeTwo.isChecked());
            modeTwo.setChecked(true);
            assertSame(rasterModeOnly, adapter.getCheckState().get(0));
            assertTrue(rasterModeOnly.isCheckState1());
            assertTrue(rasterModeOnly.isCheckState2());
        }
    }

    @Test public void styledVectorUsesSeparateRasterAndVectorModesAfterSingleRow() {
        prepareRows();
        RowResource collector = new RowResource(Connection.NGWResourceTypeCollector, 0);
        states(selected(collector.getId(), 2));
        android.view.View single = adapter.getResourceView(collector, null);
        RowResource vector = new RowResource(Connection.NGWResourceTypeVectorLayer, 1);
        CheckState rasterOnly = selected(vector.getId(), 1);
        states(rasterOnly);
        android.view.View both = adapter.getResourceView(vector, single);
        assertNotSame(single, both);
        assertEquals(com.nextgis.maplibui.R.id.ngw_layer_doublecheck_row, both.getId());
        assertEquals(context.getString(com.nextgis.maplibui.R.string.raster),
                rowLabel(both, com.nextgis.maplibui.R.id.type1));
        assertEquals(context.getString(com.nextgis.maplibui.R.string.vector),
                rowLabel(both, com.nextgis.maplibui.R.id.type2));
        assertTrue(rowBox(both, com.nextgis.maplibui.R.id.checkBox1).isChecked());
        CheckBox vectorMode = rowBox(both, com.nextgis.maplibui.R.id.checkBox2);
        assertFalse(vectorMode.isChecked());
        vectorMode.setChecked(true);
        assertSame(rasterOnly, adapter.getCheckState().get(0));
        assertTrue(rasterOnly.isCheckState1());
        assertTrue(rasterOnly.isCheckState2());
    }

    @Test public void repeatedSingleRowRecyclingAlwaysUsesTheCurrentTypeLabelAndMode() {
        prepareRows();
        android.view.View row = null;
        for (int type : new int[]{Connection.NGWResourceTypeVectorLayer,
                Connection.NGWResourceTypeRasterLayer, Connection.NGWResourceTypeCollector,
                Connection.NGWResourceTypeWMSClient, Connection.NGWResourceTypeVectorLayer,
                Connection.NGWResourceTypeWebMap, Connection.NGWResourceTypeCollector,
                Connection.NGWResourceTypeRasterLayer}) {
            RowResource resource = new RowResource(type, 0);
            boolean vectorMode = type == Connection.NGWResourceTypeVectorLayer
                    || type == Connection.NGWResourceTypeCollector;
            CheckState state = selected(resource.getId(), vectorMode ? 2 : 1);
            states(state);
            android.view.View previous = row;
            row = adapter.getResourceView(resource, row);
            if (previous != null) assertSame(previous, row);
            int caption = type == Connection.NGWResourceTypeCollector
                    ? com.nextgis.maplibui.R.string.ngw_collector_import
                    : vectorMode ? com.nextgis.maplibui.R.string.vector : com.nextgis.maplibui.R.string.raster;
            assertEquals(context.getString(caption), rowLabel(row, com.nextgis.maplibui.R.id.type1));
            assertTrue(rowBox(row, com.nextgis.maplibui.R.id.checkBox1).isChecked());
            assertEquals(resource.getName(), rowLabel(row, com.nextgis.maplibui.R.id.tvName));
            assertSame(state, adapter.getCheckState().get(0));
        }
    }

}
