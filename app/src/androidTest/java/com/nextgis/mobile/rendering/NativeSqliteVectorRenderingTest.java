package com.nextgis.mobile.rendering;

import android.database.Cursor;
import com.nextgis.maplib.datasource.GeoGeometry;
import com.nextgis.maplib.datasource.GeoGeometryCollection;
import com.nextgis.maplib.datasource.GeoGeometryFactory;
import com.nextgis.maplib.datasource.GeoLineString;
import com.nextgis.maplib.display.SimpleFeatureRenderer;
import com.nextgis.maplib.display.SimpleLineStyle;
import com.nextgis.maplib.display.SimpleMarkerStyle;
import com.nextgis.maplib.display.MarkerIconRegistry;
import com.nextgis.maplib.display.PolygonPatternOptions;
import com.nextgis.maplib.display.PolygonPatternRegistry;
import com.nextgis.maplib.map.VisibleLineLabelLayout;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.nextgis.maplib.datasource.Feature;
import com.nextgis.maplib.datasource.GeoLinearRing;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.datasource.GeoPolygon;
import com.nextgis.maplib.display.FieldStyleRule;
import com.nextgis.maplib.display.LabelAttributes;
import com.nextgis.maplib.display.RuleFeatureRenderer;
import com.nextgis.maplib.display.SimplePolygonStyle;
import com.nextgis.maplib.map.MPLFeaturesUtils;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.MplFeatureStyleProps;
import com.nextgis.maplib.map.MLP.MLGeometryEditClass;
import com.nextgis.maplib.map.NativeVectorTileEncoder;
import com.nextgis.maplib.map.SqliteVectorTileProvider;
import com.nextgis.maplib.map.SqliteVectorTileRequest;
import com.nextgis.maplib.util.DatabaseContext;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.mobile.fragment.MapFragment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.layers.LayoutPropertyValue;
import org.maplibre.android.style.layers.PropertyValue;
import org.maplibre.android.style.sources.CustomVectorSource;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.android.style.sources.Source;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Isolated SQLite -> production reload -> native CustomVectorSource, with independent contents. */
@RunWith(AndroidJUnit4.class)
public class NativeSqliteVectorRenderingTest {
    private static final int ROWS = 129, VERTICES = 64;
    private static final Map<Long, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(31L, "Альфа31"); LABELS.put(62L, "Безкат62"); LABELS.put(99L, "Прочие99");
    }

    @Test public void sqliteSourceRendersAllOriginalPolygonsCategoriesAndLabelsWithoutGlobalFeatures() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            Corpus corpus = new Corpus(f);
            byte[] before = contents(corpus.layer);
            reload(f, corpus.layer); fitCorpus(f);
            awaitRendered(f, corpus.layer, false);
            assertStreaming(f, corpus.layer);
            assertOriginals(provider(f, corpus.layer), corpus);
            assertFillPixels(f, false);
            assertArrayEquals("Rendering must not write any SQLite row", before, contents(corpus.layer));
            assertTrue(corpus.layer.isVisible());
        }
    }

    @Test public void contentStyleAndVisibilityChangesRetireLateProvidersWithoutRebuildingGlobalFeatures() throws Exception {
        SqliteVectorTileProvider beforeTeardown;
        SqliteVectorTileRequest teardownRequest;
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            Corpus corpus = new Corpus(f);
            byte[] before = contents(corpus.layer);
            reload(f, corpus.layer); fitCorpus(f); awaitRendered(f, corpus.layer, false);
            SqliteVectorTileProvider old = provider(f, corpus.layer);
            SqliteVectorTileRequest oldRequest = old.snapshot(16, 32768, 32768);

            // A real content notification invalidates the current epoch even when rows did not change.
            f.screen.onActivity(activity -> {
                corpus.layer.notifyLayerChanged();
                ((MapDrawable) f.app.getMap()).reloadVectorLayerDataToMaplibre(corpus.layer);
            });
            awaitReplacement(f, corpus.layer, old); assertRetired(old, oldRequest);
            awaitRendered(f, corpus.layer, false); assertStreaming(f, corpus.layer);

            SqliteVectorTileProvider beforeStyle = provider(f, corpus.layer);
            SqliteVectorTileRequest styleRequest = beforeStyle.snapshot(16, 32768, 32768);
            // Do not manufacture a data notification to conceal a missing style-generation fence.
            installStyle(corpus.layer, true);
            f.screen.onActivity(activity -> ((MapDrawable) f.app.getMap())
                    .reloadVectorLayerStyleToMaplibre(corpus.layer.getId()));
            awaitReplacement(f, corpus.layer, beforeStyle); assertRetired(beforeStyle, styleRequest);
            assertCurrentBackendStyleFocus(f, corpus.layer);
            awaitRendered(f, corpus.layer, true); assertFillPixels(f, true);

            SqliteVectorTileProvider beforeHide = provider(f, corpus.layer);
            SqliteVectorTileRequest hideRequest = beforeHide.snapshot(16, 32768, 32768);
            AtomicReference<String> detachedSourceId = new AtomicReference<>();
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap();
                detachedSourceId.set(map.getVectorRenderSourceId(corpus.layer.getId()));
                assertNotNull("Capture the actual native source that must detach", detachedSourceId.get());
                corpus.layer.setVisible(false);
                map.reloadVectorLayerDataToMaplibre(corpus.layer);
            });
            await("Hidden source must detach", () -> {
                AtomicReference<Boolean> gone = new AtomicReference<>(false);
                f.screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    gone.set(map.getSqliteVectorProvider(corpus.layer.getId()) == null
                            && map.getMaplibreMap().getStyle().getSource(detachedSourceId.get()) == null);
                });
                return gone.get();
            });
            assertRetired(beforeHide, hideRequest);
            assertTrue("Hidden layer cannot leave native polygon hits", rendered(f, corpus.layer, false).isEmpty());
            f.screen.onActivity(activity -> {
                corpus.layer.setVisible(true);
                ((MapDrawable) f.app.getMap()).reloadVectorLayerDataToMaplibre(corpus.layer);
            });
            awaitRendered(f, corpus.layer, true); assertStreaming(f, corpus.layer);
            assertOriginals(provider(f, corpus.layer), corpus);
            assertArrayEquals("Style/data invalidation and hide/show must preserve every row", before, contents(corpus.layer));
            beforeTeardown = provider(f, corpus.layer);
            teardownRequest = beforeTeardown.snapshot(16, 32768, 32768);
        }
        // Fixture.close deletes its own layer and closes the real Activity/MapView.
        assertRetired(beforeTeardown, teardownRequest);
    }

    @Test public void existingSqlitePolygonEditKeepAndCancelAndTemporaryHideUndoPreserveAllOriginalRows() throws Exception {
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            Corpus corpus = new Corpus(f);
            final long selectedId = 31;
            byte[] before = contents(corpus.layer);
            try {
                reload(f, corpus.layer); fitCorpus(f); awaitRendered(f, corpus.layer, false);
                assertCurrentSqliteWork(f, corpus, "before edit");
                Feature original = provider(f, corpus.layer).loadOriginalFeature(selectedId);
                AtomicReference<MLGeometryEditClass> editor = new AtomicReference<>();
                await("Current native edit sources must be attached", () -> {
                    AtomicReference<Boolean> ready = new AtomicReference<>(false);
                    f.screen.onActivity(activity -> ready.set(((MapDrawable) f.app.getMap()).areEditSourcesReadyForCurrentStyle()));
                    return ready.get();
                });
                f.screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    MapFragment fragment = activity.getMapFragment();
                    fragment.showViewModeForFeature(corpus.layer, original, corpus.layer, original.getGeometry(), selectedId, selectedId, false);
                    assertTrue("The real selected-feature Edit action must be enabled", activity.getBottomToolbar().getMenu()
                            .findItem(com.nextgis.mobile.R.id.menu_feature_edit).isEnabled());
                    assertTrue(activity.getBottomToolbar().getMenu().performIdentifierAction(com.nextgis.mobile.R.id.menu_feature_edit, 0));
                    assertEquals(MapFragment.MODE_EDIT,
                            ((com.nextgis.maplib.map.MaplibreMapInteraction) fragment).getMode().intValue());
                    editor.set(map.editingObject);
                });
                assertNotNull("Production editor must attach to one DB original", editor.get());
                assertEditablePolygon(editor.get().editingFeature, selectedId, corpus.coordinates.get(selectedId));
                awaitSelectedPolygon(f, selectedId, corpus.coordinates.get(selectedId)); awaitBackgroundExcluding(f, corpus.layer, selectedId);
                assertCurrentSqliteWork(f, corpus, "normal edit entry with128 background IDs");
                assertStreaming(f, corpus.layer);
                assertArrayEquals(before, contents(corpus.layer));

                // Move one real editor vertex; update the same overlay/draft callback as map gestures.
                double[][] moved = new double[VERTICES + 1][2];
                for (int i = 0; i <= VERTICES; i++) moved[i] = corpus.coordinates.get(selectedId)[i].clone();
                moved[0][0] += 2; moved[0][1] += 1; moved[VERTICES] = moved[0].clone();
                f.screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    map.editingObject.updateSelectionVerticeIndex(0);
                    map.editingObject.movePointTo(latLng(moved[0][0], moved[0][1]));
                    activity.getMapFragment().updateGeometryFromMaplibre(map.editingObject.editingFeature,
                            map.originalSelectedFeature, map.editingObject);
                    // Same retained-editor branch used by MODE_EDIT_BY_WALK, without GPS/service startup.
                    map.unselectFeatureFromEdit(false, true); map.hideVertex(); map.hideMarker();
                    assertSame("Keep-edit must retain the current editor", editor.get(), map.editingObject);
                });
                assertEditablePolygon(editor.get().editingFeature, selectedId, moved);
                awaitSelectedPolygon(f, selectedId, moved); awaitBackgroundExcluding(f, corpus.layer, selectedId);
                assertCurrentSqliteWork(f, corpus, "retained editor with128 background IDs");
                assertStreaming(f, corpus.layer); assertArrayEquals(before, contents(corpus.layer));

                f.screen.onActivity(activity -> activity.getMapFragment().cancelEdits());
                awaitRendered(f, corpus.layer, false); assertStreaming(f, corpus.layer);
                assertCurrentSqliteWork(f, corpus, "cancel with129 background IDs");
                f.screen.onActivity(activity -> assertNull("Cancel must release the editor", ((MapDrawable) f.app.getMap()).editingObject));
                assertOriginals(provider(f, corpus.layer), corpus);
                assertArrayEquals("Cancel must discard the actual moved vertex", before, contents(corpus.layer));

                // The same paired model/native operations as the delete prompt and its Undo action.
                f.screen.onActivity(activity -> {
                    corpus.layer.hideFeature(selectedId);
                    ((MapDrawable) f.app.getMap()).hideFeature(selectedId, corpus.layer.getId());
                });
                awaitBackgroundExcluding(f, corpus.layer, selectedId); assertStreaming(f, corpus.layer);
                assertCurrentSqliteWork(f, corpus, "temporary hide with128 background IDs");
                assertTrue(corpus.layer.isFeatureHidden(selectedId)); assertArrayEquals(before, contents(corpus.layer));
                f.screen.onActivity(activity -> {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    assertNotNull("Undo must retain the one original feature", map.hiddedFeature);
                    corpus.layer.showFeature(selectedId);
                    map.showFeatureFromHide(selectedId, corpus.layer.getId(), map.hiddedFeature);
                });
                awaitRendered(f, corpus.layer, false); assertStreaming(f, corpus.layer);
                assertCurrentSqliteWork(f, corpus, "Undo with129 background IDs");
                assertFalse(corpus.layer.isFeatureHidden(selectedId));
                assertOriginals(provider(f, corpus.layer), corpus);
                assertArrayEquals("Temporary hide/Undo must not delete or alter any row", before, contents(corpus.layer));
                f.screen.onActivity(activity -> activity.getMapFragment().setNewMode(MapFragment.MODE_NORMAL));
            } catch (AssertionError | Exception original) {
                if (original instanceof AssertionError && String.valueOf(original.getMessage()).contains("SQLite native witness:"))
                    throw (AssertionError) original;
                throw new AssertionError(original.getMessage() + "\nSQLite native witness: "
                        + failureEvidence(f, corpus.layer, false), original);
            }
        }
    }


    @Test public void sqlitePointSimpleCircleRetainsOriginalsPaintAndRussianLabels() throws Exception {
        runGeometrySmoke(GeoConstants.GTPoint, false, false, false);
    }

    @Test public void sqliteMultiPointRulesRetainBothPartsAndTheCustomMarker() throws Exception {
        runGeometrySmoke(GeoConstants.GTMultiPoint, true, true, false);
    }

    @Test public void sqliteLineSimpleLabelsFollowTheVisiblePartAfterPan() throws Exception {
        runGeometrySmoke(GeoConstants.GTLineString, false, false, false);
    }

    @Test public void sqliteMultiLineRulesRetainPartsAndViewportLabelsAfterPan() throws Exception {
        runGeometrySmoke(GeoConstants.GTMultiLineString, true, false, false);
    }

    @Test public void sqlitePolygonHoleAndProceduralPatternReachActualPixels() throws Exception {
        runGeometrySmoke(GeoConstants.GTPolygon, false, false, true);
    }

    @Test public void sqliteMultiPolygonRulesRetainBothPartsAndHoles() throws Exception {
        runGeometrySmoke(GeoConstants.GTMultiPolygon, true, false, false);
    }

    @Test public void sqliteFieldLinesAndWholePointGroupsStayAboveSavedAndCurrentTracks() throws Exception {
        Bitmap icon = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        icon.eraseColor(Color.CYAN);
        String iconName = "sqlite-qa-cyan-priority-" + java.util.UUID.randomUUID();
        try (TrackControlsTest.Tracks tracks = new TrackControlsTest.Tracks()) {
            VectorStyleRenderingTest.Fixture f = tracks.f;
            PriorityCorpus[] witnessCorpus = {null};
            AtomicReference<AssertionError> failure = new AtomicReference<>();
            try {
                priorityUi(f, activity -> MarkerIconRegistry.registerCustomIconBitmap(iconName, icon));
                PriorityCorpus corpus = new PriorityCorpus(f, iconName); witnessCorpus[0] = corpus;
                long saved = tracks.add("Сохранённый", Color.RED, 0);
                long current = tracks.add("Текущий", Color.RED, -140);
                android.content.ContentValues active = new android.content.ContentValues();
                active.putNull(com.nextgis.maplib.map.TrackLayer.FIELD_END);
                assertEquals(1, f.app.getContentResolver().update(com.nextgis.maplibui.util.TrackActions.uri(f.app), active,
                        com.nextgis.maplib.map.TrackLayer.FIELD_ID + "=?", new String[]{Long.toString(current)}));
                for (VectorLayerUI layer : corpus.all) reload(f, layer);
                fitCorpus(f); priorityReloadTracks(tracks, saved, current);
                assertPriorityScene(tracks, corpus, "initial production SQLite bind", false, true);

                priorityUi(f, activity -> activity.getMapFragment().loadLayersLite());
                priorityReloadTracks(tracks, saved, current);
                assertPriorityScene(tracks, corpus, "lite load", false, true);

                SqliteVectorTileProvider beforeFull = provider(f, corpus.point);
                priorityUi(f, activity -> assertTrue(activity.getMapFragment().reloadMapStyleAndLayersAfterLayerFillBatch()));
                awaitReplacement(f, corpus.point, beforeFull);
                fitCorpus(f); priorityReloadTracks(tracks, saved, current);
                assertPriorityScene(tracks, corpus, "full style and layer reload", false, true);

                priorityUi(f, activity -> {
                    tracks.map().moveLayer(0, corpus.multiPoint);
                    activity.getMapFragment().loadLayersLite();
                });
                priorityReloadTracks(tracks, saved, current);
                assertPriorityScene(tracks, corpus, "logical layer reorder", false, true);

                SqliteVectorTileProvider beforeStyle = provider(f, corpus.point);
                String oldStyleSource = prioritySource(f, corpus.point);
                corpus.point.setRenderer(new SimpleFeatureRenderer(corpus.point, priorityPointStyle(18)));
                assertTrue(corpus.point.save());
                priorityUi(f, activity -> tracks.map().reloadVectorLayerStyleToMaplibre(corpus.point.getId()));
                awaitReplacement(f, corpus.point, beforeStyle);
                assertFalse(beforeStyle.isCurrent());
                assertNotEquals("A real style generation uses a fresh current source", oldStyleSource, prioritySource(f, corpus.point));
                assertPriorityScene(tracks, corpus, "new style generation", false, true);

                SqliteVectorTileProvider beforeData = provider(f, corpus.point);
                String oldDataSource = prioritySource(f, corpus.point);
                android.content.ContentValues changed = new android.content.ContentValues(); changed.put("name", "ТочкаБ");
                android.net.Uri row = android.net.Uri.parse("content://" + f.app.getAuthority() + "/" + corpus.point.getPath().getName() + "/1");
                assertEquals("Change one owned technical row through the real provider", 1,
                        f.app.getContentResolver().update(row, changed, null, null));
                corpus.labels.get(corpus.point).put(1L, "ТочкаБ");
                corpus.rows.put(corpus.point, contents(corpus.point, 1));
                priorityUi(f, activity -> tracks.map().reloadVectorLayerDataToMaplibre(corpus.point));
                awaitReplacement(f, corpus.point, beforeData);
                assertFalse(beforeData.isCurrent());
                assertNotEquals("A real data generation uses a fresh current source", oldDataSource, prioritySource(f, corpus.point));
                assertPriorityScene(tracks, corpus, "new data generation", false, true);

                PriorityBindings beforeTracks = priorityBindings(f, corpus);
                android.content.ContentValues renamed = new android.content.ContentValues();
                renamed.put(com.nextgis.maplib.map.TrackLayer.FIELD_NAME, "Обновлённый");
                assertEquals(1, f.app.getContentResolver().update(com.nextgis.maplibui.util.TrackActions.uri(f.app), renamed,
                        com.nextgis.maplib.map.TrackLayer.FIELD_ID + "=?", new String[]{Long.toString(saved)}));
                priorityReloadTracks(tracks, saved, current);
                await("Actual saved-track source must observe the updated literal name", () -> priorityTrackName(tracks, saved, "Обновлённый"));
                assertPriorityBindings(beforeTracks, priorityBindings(f, corpus), false);

                // The production delete-prompt/Undo pair must survive an actual order repair.
                Map<String, String> originalBodyFilters = priorityPointFilters(f, corpus.point);
                priorityUi(f, activity -> { corpus.point.hideFeature(1L); tracks.map().hideFeature(1L, corpus.point.getId()); });
                await("Hidden point must disappear from the actual native body", () -> priorityRenderedBody(f, corpus.point).isEmpty());
                PriorityBindings bindings = priorityBindings(f, corpus);
                priorityUi(f, activity -> {
                    org.maplibre.android.maps.Style style = tracks.map().getMaplibreMap().getStyle();
                    // Disturb native order using cached wrappers, then the normal public source-reapply hook.
                    for (String childId : bindings.pointChildren) {
                        org.maplibre.android.style.layers.Layer cached = style.getLayer(childId);
                        assertTrue(style.removeLayer(cached)); style.addLayerBelow(cached, "track-line-glow");
                    }
                    tracks.map().reloadVectorLayerStylePropsToMaplibre(corpus.ordinaryLine);
                });
                awaitPriorityOrder(f, corpus, "hidden point after unchanged ordinary source reapply");
                PriorityBindings afterOrder = priorityBindings(f, corpus);
                assertPriorityBindings(bindings, afterOrder, true);
                assertPriorityScene(tracks, corpus, "hidden point after order repair", true, false);
                priorityUi(f, activity -> {
                    assertNotNull(tracks.map().hiddedFeature);
                    corpus.point.showFeature(1L);
                    tracks.map().showFeatureFromHide(1L, corpus.point.getId(), tracks.map().hiddedFeature);
                    // Inspect background pixels, not the transient selection copy returned by Undo.
                    tracks.map().hideSelectedDotSource();
                });
                assertPriorityScene(tracks, corpus, "Undo after order repair", false, true);
                assertEquals("Undo must restore the original role filter without the temporary hidden-FID condition",
                        originalBodyFilters, priorityPointFilters(f, corpus.point));
                assertFalse(corpus.point.isFeatureHidden(1L));
            } catch (AssertionError | Exception original) {
                String witness = priorityFailure(f, witnessCorpus[0]);
                AssertionError report = new AssertionError("SQLite priority: " + original.getMessage() + "\n" + witness, original);
                failure.set(report); throw report;
            } finally {
                try { priorityUi(f, activity -> MarkerIconRegistry.registerCustomIconBitmap(iconName, null)); }
                catch (AssertionError | RuntimeException cleanup) {
                    if (failure.get() == null) throw cleanup;
                    failure.get().addSuppressed(cleanup);
                }
            }
        } finally { icon.recycle(); }
    }

    /** Return native observation failures to instrumentation before fixture cleanup/evidence capture. */
    private static void priorityUi(VectorStyleRenderingTest.Fixture f,
                                   java.util.function.Consumer<com.nextgis.mobile.activity.MainActivity> action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        f.screen.onActivity(activity -> {
            try { action.accept(activity); }
            catch (AssertionError | Exception error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError("Priority native UI observation failed", failure.get());
    }

    /** Tiny technical DBs, persisted styles and production reload only; never Fixture.bind/classic. */
    private static final class PriorityCorpus {
        final VectorLayerUI fieldLine, fieldMultiLine, point, multiPoint, ordinaryLine, polygon;
        final List<VectorLayerUI> all = new ArrayList<>();
        final Map<VectorLayerUI, Map<Long, String>> labels = new LinkedHashMap<>();
        final Map<VectorLayerUI, Map<Long, JSONArray>> coordinates = new LinkedHashMap<>();
        final Map<VectorLayerUI, byte[]> rows = new LinkedHashMap<>();
        PriorityCorpus(VectorStyleRenderingTest.Fixture f, String icon) throws Exception {
            fieldLine = add(f, GeoConstants.GTLineString, "Полевые линейные", "LineString",
                    new String[]{"[[-280,0],[280,0]]"}, new String[]{"ЛинияА"}, priorityLineStyle());
            fieldMultiLine = add(f, GeoConstants.GTMultiLineString, "Полевые линейные", "MultiLineString",
                    new String[]{"[[[-280,-140],[280,-140]],[[-280,-170],[280,-170]]]"}, new String[]{"ЛинияМ"}, priorityLineStyle());
            point = add(f, GeoConstants.GTPoint, "Полевые точки", "Point", new String[]{"[0,0]"}, new String[]{"ТочкаА"}, priorityPointStyle(14));
            SimpleMarkerStyle other = (SimpleMarkerStyle) smokeStyle(GeoConstants.GTMultiPoint, Color.GREEN, Color.BLACK, false);
            multiPoint = add(f, GeoConstants.GTMultiPoint, "Мультиточки", "MultiPoint",
                    new String[]{"[[-120,140],[-80,140]]", "[[100,160],[140,160]]"}, new String[]{"МультиА", "МультиБ"}, other);
            SimpleMarkerStyle category = (SimpleMarkerStyle) smokeStyle(GeoConstants.GTMultiPoint, Color.GREEN, Color.BLACK, false);
            category.setIconImage(icon); category.setIconSize(2); category.setIconAllowOverlap(true); category.setIconIgnorePlacement(true);
            FieldStyleRule rule = new FieldStyleRule(multiPoint); rule.setKey("kind"); rule.setOtherStyle(other); rule.setStyle("A", category);
            multiPoint.setRenderer(new RuleFeatureRenderer(multiPoint, rule, other)); assertTrue(multiPoint.save());
            ordinaryLine = add(f, GeoConstants.GTLineString, "Обычная линия", "LineString",
                    new String[]{"[[-280,0],[280,0]]"}, new String[]{"Обычная"}, smokeStyle(GeoConstants.GTLineString, Color.MAGENTA, Color.BLACK, false));
            polygon = add(f, GeoConstants.GTPolygon, "Обычный полигон", "Polygon",
                    new String[]{"[[[-250,-240],[250,-240],[250,240],[-250,240],[-250,-240]]]"}, new String[]{"ПлощадьА"}, smokeStyle(GeoConstants.GTPolygon, Color.LTGRAY, Color.BLACK, false));
        }
        private VectorLayerUI add(VectorStyleRenderingTest.Fixture f, int type, String name, String geometryType,
                                  String[] shapes, String[] names, com.nextgis.maplib.display.Style style) throws Exception {
            VectorLayerUI layer = f.add(type); layer.setName(name); all.add(layer);
            Map<Long, String> expectedNames = new LinkedHashMap<>(); labels.put(layer, expectedNames);
            Map<Long, JSONArray> expectedCoordinates = new LinkedHashMap<>(); coordinates.put(layer, expectedCoordinates);
            android.database.sqlite.SQLiteDatabase database = DatabaseContext.getDatabaseForLayer(layer, false);
            database.beginTransaction();
            try {
                for (int i = 0; i < shapes.length; i++) {
                    JSONArray shape = new JSONArray(shapes[i]);
                    GeoGeometry geometry = GeoGeometryFactory.fromJson(new JSONObject().put("type", geometryType).put("coordinates", shape));
                    setSmokeCrs(geometry);
                    Feature feature = new Feature(i + 1L, layer.getFields()); feature.setGeometry(geometry);
                    feature.setFieldValue("name", names[i]); feature.setFieldValue("kind", i == 0 ? "A" : "other");
                    layer.createFeatureBatch(feature, database, false);
                    expectedNames.put(i + 1L, names[i]); expectedCoordinates.put(i + 1L, new JSONArray(shapes[i]));
                }
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            layer.rebuildCache(null); layer.setRenderer(new SimpleFeatureRenderer(layer, style)); assertTrue(layer.save());
            rows.put(layer, contents(layer, shapes.length)); return layer;
        }
    }

    private static SimpleLineStyle priorityLineStyle() {
        SimpleLineStyle style = (SimpleLineStyle) smokeStyle(GeoConstants.GTLineString, Color.BLUE, Color.BLACK, false);
        style.setWidth(4); return style;
    }
    private static SimpleMarkerStyle priorityPointStyle(int size) {
        SimpleMarkerStyle style = (SimpleMarkerStyle) smokeStyle(GeoConstants.GTPoint, Color.GREEN, Color.BLACK, false);
        style.setType(SimpleMarkerStyle.MarkerStyleFirefly); style.setSize(size); style.setWidth(size); return style;
    }
    private static String prioritySource(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) {
        AtomicReference<String> id = new AtomicReference<>();
        priorityUi(f, activity -> id.set(((MapDrawable) f.app.getMap()).getVectorRenderSourceId(layer.getId())));
        return id.get();
    }
    private static void priorityReloadTracks(TrackControlsTest.Tracks tracks, long saved, long current) throws Exception {
        priorityUi(tracks.f, activity -> { tracks.map().reloadTrackListToMap(); tracks.map().reloadCurrentTrackToMap(); tracks.map().checkLayerVisibility(tracks.layer.getId()); });
        await("Saved and current track snapshots must both contain their owned IDs", () -> {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            priorityUi(tracks.f, activity -> {
                org.maplibre.android.maps.Style style = tracks.map().getMaplibreMap().getStyle(); boolean foundSaved = false, foundCurrent = false;
                for (String prefix : new String[]{"track-line", "track-inprogress"}) {
                    GeoJsonSource source = style.getSourceAs(prefix + "-source"); if (source == null) return;
                    for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null))
                        if (feature.hasProperty(com.nextgis.maplib.map.TrackMapStyle.TRACK_ID)) {
                            long id = feature.getNumberProperty(com.nextgis.maplib.map.TrackMapStyle.TRACK_ID).longValue();
                            if (prefix.equals("track-line") && id == saved) foundSaved = true;
                            if (prefix.equals("track-inprogress") && id == current) foundCurrent = true;
                        }
                }
                ready.set(foundSaved && foundCurrent);
            }); return ready.get();
        });
    }
    private static boolean priorityTrackName(TrackControlsTest.Tracks tracks, long id, String name) {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        priorityUi(tracks.f, activity -> {
            GeoJsonSource source = tracks.map().getMaplibreMap().getStyle().getSourceAs("track-line-source");
            if (source != null) for (org.maplibre.geojson.Feature feature : source.querySourceFeatures(null))
                if (feature.hasProperty(com.nextgis.maplib.map.TrackMapStyle.TRACK_ID)
                        && feature.getNumberProperty(com.nextgis.maplib.map.TrackMapStyle.TRACK_ID).longValue() == id
                        && name.equals(feature.getStringProperty(com.nextgis.maplib.map.TrackMapStyle.NAME))) result.set(true);
        }); return result.get();
    }
    private static Map<String, String> priorityPointFilters(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) {
        Map<String, String> result = new LinkedHashMap<>();
        priorityUi(f, activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
            for (org.maplibre.android.style.layers.Layer child : style.getLayers()) {
                if (!map.getVectorRenderSourceId(layer.getId()).equals(priorityLayerSource(child))) continue;
                org.maplibre.android.style.expressions.Expression filter = child instanceof CircleLayer ? ((CircleLayer) child).getFilter()
                        : child instanceof SymbolLayer ? ((SymbolLayer) child).getFilter() : null;
                result.put(child.getId(), filter == null ? "none" : filter.toString());
            }
        }); assertFalse("Current point body/sprite/label role filters exist", result.isEmpty()); return result;
    }

    /** Firefly core/glow is a Symbol sprite; its Circle baseline deliberately excludes Firefly. */
    private static Map<Long, org.maplibre.geojson.Feature> priorityRenderedBody(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) {
        int type = layer.getGeometryType();
        if (type != GeoConstants.GTPoint && type != GeoConstants.GTMultiPoint) return smokeRendered(f, layer, type, 0);
        List<org.maplibre.geojson.Feature> hits = new ArrayList<>();
        priorityUi(f, activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); List<String> bodies = new ArrayList<>();
            String actual = map.getVectorRenderSourceId(layer.getId());
            for (org.maplibre.android.style.layers.Layer child : map.getMaplibreMap().getStyle().getLayers())
                if (actual.equals(priorityLayerSource(child)) && (child instanceof CircleLayer
                        || child instanceof SymbolLayer && child.getId().contains("-layout-firefly-"))) bodies.add(child.getId());
            if (!bodies.isEmpty()) hits.addAll(map.getMaplibreMap().queryRenderedFeatures(new RectF(0, 0,
                    map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), bodies.toArray(new String[0])));
        });
        Map<Long, org.maplibre.geojson.Feature> result = new LinkedHashMap<>();
        for (org.maplibre.geojson.Feature hit : hits) {
            assertTrue("Every raw point body/sprite hit retains its SQL identity", hit.hasProperty(MPLFeaturesUtils.prop_featureid));
            assertNotNull("Point body/sprite geometry is present", hit.geometry());
            assertTrue("Point body/sprite never includes another geometry role", hit.geometry().type().equals("Point") || hit.geometry().type().equals("MultiPoint"));
            result.put(hit.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong(), hit);
        }
        return result;
    }

    private static void assertPriorityScene(TrackControlsTest.Tracks tracks, PriorityCorpus corpus, String stage,
                                            boolean pointHidden, boolean pixels) throws Exception {
        VectorStyleRenderingTest.Fixture f = tracks.f;
        for (VectorLayerUI layer : corpus.all) {
            assertStreaming(f, layer);
            Set<Long> expected = pointHidden && layer == corpus.point ? java.util.Collections.emptySet() : corpus.labels.get(layer).keySet();
            await(stage + ": complete native body IDs for " + layer.getName(), () -> priorityRenderedBody(f, layer).keySet().equals(expected));
            await(stage + ": actual placed label IDs for " + layer.getName(), () -> smokeRendered(f, layer, layer.getGeometryType(), 1).keySet().equals(expected));
            for (Map.Entry<Long, org.maplibre.geojson.Feature> label : smokeRendered(f, layer, layer.getGeometryType(), 1).entrySet())
                assertEquals(stage + ": literal SQL label", corpus.labels.get(layer).get(label.getKey()), label.getValue().getStringProperty(MplFeatureStyleProps.SIGNATURE));
            for (Map.Entry<Long, JSONArray> original : corpus.coordinates.get(layer).entrySet()) {
                Feature feature = provider(f, layer).loadOriginalFeature(original.getKey());
                assertNotNull(feature); assertCoordinates(stage + ": full SQL original", original.getValue(), originalCoordinates(feature.getGeometry()));
                assertEquals(corpus.labels.get(layer).get(original.getKey()), feature.getFieldValue("name"));
            }
            assertArrayEquals(stage + ": view changes never mutate owned SQL rows", corpus.rows.get(layer), contents(layer, corpus.labels.get(layer).size()));
        }
        awaitPriorityOrder(f, corpus, stage); f.renderedFrame();
        assertEquals("The rule icon really reaches the current native source", java.util.Collections.singleton(1L),
                smokeRendered(f, corpus.multiPoint, GeoConstants.GTMultiPoint, 2).keySet());
        if (pixels) {
            Bitmap frame = f.snapshot();
            try {
                PointF green = priorityScreenPoint(f, 0, 0), blueSaved = priorityScreenPoint(f, 180, 0), blueCurrent = priorityScreenPoint(f, 180, -140);
                assertPriorityPixel(frame, green, true, stage + ": point covers saved track");
                assertPriorityPixel(frame, blueSaved, false, stage + ": field line covers saved track");
                assertPriorityPixel(frame, blueCurrent, false, stage + ": field multiline covers current track");
            } finally { frame.recycle(); }
        }
    }
    private static PointF priorityScreenPoint(VectorStyleRenderingTest.Fixture f, double x, double y) {
        AtomicReference<PointF> result = new AtomicReference<>();
        priorityUi(f, activity -> result.set(((MapDrawable) f.app.getMap()).getMaplibreMap().getProjection().toScreenLocation(latLng(x, y))));
        return result.get();
    }
    private static void assertPriorityPixel(Bitmap frame, PointF sample, boolean green, String message) {
        int x = Math.round(sample.x), y = Math.round(sample.y), matches = 0;
        assertTrue(message + " sample in actual frame", x > 0 && y > 0 && x < frame.getWidth()-1 && y < frame.getHeight()-1);
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int pixel = frame.getPixel(x+dx, y+dy);
            if (green ? Color.green(pixel) > 180 && Color.red(pixel) < 60 && Color.blue(pixel) < 60
                    : Color.blue(pixel) > 180 && Color.red(pixel) < 60 && Color.green(pixel) < 60) matches++;
        }
        assertTrue(message + " literal opaque color patch, matches=" + matches, matches >= 5);
    }

    private static String priorityLayerSource(org.maplibre.android.style.layers.Layer layer) {
        return layer instanceof CircleLayer ? ((CircleLayer) layer).getSourceId() : layer instanceof LineLayer ? ((LineLayer) layer).getSourceId()
                : layer instanceof FillLayer ? ((FillLayer) layer).getSourceId() : layer instanceof SymbolLayer ? ((SymbolLayer) layer).getSourceId() : null;
    }
    private static List<Integer> priorityGroup(org.maplibre.android.maps.Style style, VectorLayerUI model, String actualSource) {
        List<Integer> group = new ArrayList<>(); List<org.maplibre.android.style.layers.Layer> children = style.getLayers();
        String lineLabels = model.getPath().toString() + MPLFeaturesUtils.source_polygon_text;
        boolean line = model.getGeometryType() == GeoConstants.GTLineString || model.getGeometryType() == GeoConstants.GTMultiLineString;
        for (int i = 0; i < children.size(); i++) {
            String source = priorityLayerSource(children.get(i));
            if (actualSource.equals(source) || line && lineLabels.equals(source)) group.add(i);
        }
        assertFalse("Actual whole source group must be present: " + model.getName() + " / " + actualSource, group.isEmpty());
        return group;
    }
    private static void awaitPriorityOrder(VectorStyleRenderingTest.Fixture f, PriorityCorpus corpus, String stage) throws Exception {
        AtomicReference<String> last = new AtomicReference<>();
        long deadline = SystemClock.uptimeMillis() + 30000;
        do {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            priorityUi(f, activity -> {
                try { assertPriorityOrder((MapDrawable) f.app.getMap(), corpus); ready.set(true); }
                catch (AssertionError error) { last.set(error.getMessage()); }
            });
            if (ready.get()) return;
            Thread.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        fail(stage + ": actual native priority did not settle: " + last.get());
    }
    /** Literal semantic ranges; never calls the production ordering planner/helper. */
    private static void assertPriorityOrder(MapDrawable map, PriorityCorpus corpus) {
        org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
        List<org.maplibre.android.style.layers.Layer> children = style.getLayers(); Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < children.size(); i++) positions.put(children.get(i).getId(), i);
        int minTrack = Integer.MAX_VALUE, maxTrack = -1;
        for (String prefix : new String[]{"track-line", "track-inprogress"}) for (String suffix : new String[]{"-glow", "-selection", "-layer"}) {
            Integer position = positions.get(prefix + suffix); assertNotNull("All saved/current track passes exist", position);
            minTrack = Math.min(minTrack, position); maxTrack = Math.max(maxTrack, position);
        }
        Integer tool = positions.get("selected-dot-layer"), gps = positions.get("user-location-layer");
        assertNotNull(tool); assertNotNull(gps); assertTrue("GPS stays above tools", gps > tool);
        for (Map.Entry<String, Integer> layer : positions.entrySet()) if (layer.getKey().startsWith("user-location-"))
            assertTrue("Every GPS accuracy/heading/cursor pass stays above tools", layer.getValue() > tool);
        Map<VectorLayerUI, List<Integer>> groups = new LinkedHashMap<>();
        for (VectorLayerUI layer : corpus.all) {
            String actual = map.getVectorRenderSourceId(layer.getId()); assertNotNull(actual);
            assertNotEquals("This case must exercise current generated source aliases", layer.getPath().toString(), actual);
            assertTrue(map.isSqliteVectorLayer(layer.getId())); assertTrue(style.getSource(actual) instanceof CustomVectorSource);
            assertTrue(map.getSqliteVectorProvider(layer.getId()).isCurrent());
            List<Integer> group = priorityGroup(style, layer, actual); groups.put(layer, group);
            int symbols = 0, circles = 0, icons = 0, fireflySprites = 0;
            for (int index : group) {
                org.maplibre.android.style.layers.Layer child = children.get(index);
                if (child instanceof SymbolLayer) symbols++;
                if (child instanceof CircleLayer) circles++;
                if (child.getId().contains("_icon")) icons++;
                if (child instanceof SymbolLayer && child.getId().contains("-layout-firefly-")) fireflySprites++;
                assertTrue("Every ordinary group member remains below tools", index < tool);
            }
            assertTrue("Each body group includes its actual label child", symbols > 0);
            if (layer == corpus.point) assertTrue("Firefly core/glow sprite and its separate Circle role baseline are exercised", fireflySprites > 0 && circles > 0);
            if (layer == corpus.multiPoint) assertTrue("Rule icon children participate in whole-group order", icons > 0);
        }
        int maxField = -1;
        for (VectorLayerUI layer : new VectorLayerUI[]{corpus.fieldLine, corpus.fieldMultiLine}) {
            assertTrue("Every field body/label child is above every track pass", java.util.Collections.min(groups.get(layer)) > maxTrack);
            maxField = Math.max(maxField, java.util.Collections.max(groups.get(layer)));
        }
        for (VectorLayerUI layer : new VectorLayerUI[]{corpus.point, corpus.multiPoint})
            assertTrue("Whole point/halo/icon/label group stays above both field-line groups", java.util.Collections.min(groups.get(layer)) > maxField);
        for (VectorLayerUI layer : new VectorLayerUI[]{corpus.ordinaryLine, corpus.polygon})
            assertTrue("Ordinary body and polygon Point-label anchor must not be promoted above tracks", java.util.Collections.max(groups.get(layer)) < minTrack);
        boolean pointFirst = map.getLayers().indexOf(corpus.point) < map.getLayers().indexOf(corpus.multiPoint);
        List<Integer> low = groups.get(pointFirst ? corpus.point : corpus.multiPoint), high = groups.get(pointFirst ? corpus.multiPoint : corpus.point);
        assertTrue("Whole point groups preserve logical relative order without interleaving", java.util.Collections.max(low) < java.util.Collections.min(high));
        boolean fieldFirst = map.getLayers().indexOf(corpus.fieldLine) < map.getLayers().indexOf(corpus.fieldMultiLine);
        low = groups.get(fieldFirst ? corpus.fieldLine : corpus.fieldMultiLine); high = groups.get(fieldFirst ? corpus.fieldMultiLine : corpus.fieldLine);
        assertTrue("Whole field-line groups preserve logical relative order without interleaving", java.util.Collections.max(low) < java.util.Collections.min(high));
    }

    private static final class PriorityBindings {
        final Map<VectorLayerUI, String> sourceIds = new LinkedHashMap<>();
        final Map<VectorLayerUI, SqliteVectorTileProvider> providers = new LinkedHashMap<>();
        final Map<VectorLayerUI, Source> sources = new LinkedHashMap<>();
        final Map<String, org.maplibre.android.style.layers.Layer> children = new LinkedHashMap<>();
        final List<String> pointChildren = new ArrayList<>();
        final Set<String> promotedChildren = new HashSet<>();
    }
    private static PriorityBindings priorityBindings(VectorStyleRenderingTest.Fixture f, PriorityCorpus corpus) {
        PriorityBindings result = new PriorityBindings();
        priorityUi(f, activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
            List<org.maplibre.android.style.layers.Layer> layers = style.getLayers();
            for (VectorLayerUI model : corpus.all) {
                String id = map.getVectorRenderSourceId(model.getId()); result.sourceIds.put(model, id);
                result.providers.put(model, map.getSqliteVectorProvider(model.getId())); result.sources.put(model, style.getSource(id));
                for (int position : priorityGroup(style, model, id)) {
                    String child = layers.get(position).getId(); result.children.put(child, style.getLayer(child));
                    if (model == corpus.point || model == corpus.multiPoint) result.pointChildren.add(child);
                    if (model == corpus.point || model == corpus.multiPoint || model == corpus.fieldLine || model == corpus.fieldMultiLine)
                        result.promotedChildren.add(child);
                }
            }
        }); return result;
    }
    private static void assertPriorityBindings(PriorityBindings before, PriorityBindings after, boolean promotedOnly) {
        assertEquals("Order-only work must retain current native aliases", before.sourceIds, after.sourceIds);
        for (VectorLayerUI layer : before.sourceIds.keySet()) {
            assertSame("Order-only work must preserve the current provider", before.providers.get(layer), after.providers.get(layer));
            assertSame("Order-only work must preserve its bound source wrapper", before.sources.get(layer), after.sources.get(layer));
        }
        for (String id : before.children.keySet()) if (!promotedOnly || before.promotedChildren.contains(id))
            assertSame("Order repair must retain the cached wrapper/filter owner: " + id, before.children.get(id), after.children.get(id));
    }
    private static String priorityFailure(VectorStyleRenderingTest.Fixture f, PriorityCorpus corpus) {
        try {
            JSONObject state = new JSONObject().put("schema_version", 1).put("case", "sqliteFieldLinesAndWholePointGroupsStayAboveSavedAndCurrentTracks");
            JSONArray order = new JSONArray(), sources = new JSONArray();
            priorityUi(f, activity -> {
                try {
                    MapDrawable map = (MapDrawable) f.app.getMap(); org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
                    for (org.maplibre.android.style.layers.Layer child : style.getLayers())
                        order.put(new JSONObject().put("id", child.getId()).put("source", priorityLayerSource(child)).put("type", child.getClass().getSimpleName()));
                    if (corpus != null) for (VectorLayerUI layer : corpus.all) sources.put(new JSONObject().put("layer_id", layer.getId())
                                .put("current_source", map.getVectorRenderSourceId(layer.getId()))
                                .put("sqlite", map.isSqliteVectorLayer(layer.getId())).put("bridge", new JSONObject(map.getSqliteVectorDiagnostics(layer.getId()))));
                } catch (Exception error) { throw new IllegalStateException(error); }
            });
            state.put("native_order", order).put("current_sources", sources);
            File directory = new File(f.app.getFilesDir(), "native-sqlite-qa"); assertTrue(directory.isDirectory() || directory.mkdirs());
            state.put("snapshot_pixels", saveFailureSnapshot(f, directory));
            writeFailureState(state, new JSONArray(), new JSONArray(), new JSONArray(), directory, false);
            return "files/native-sqlite-qa/failure.json + failure.png retained before owned fixture cleanup";
        } catch (Exception | AssertionError error) { return "Priority diagnostic failure: " + error.getClass().getSimpleName() + ": " + error.getMessage(); }
    }

    private static final String[] SMOKE_NAMES = {"Альфа1", "Безкат2", "Прочие3"};
    private static final String[] SMOKE_KINDS = {" a ", null, "unmatched"};

    /** Three real isolated SQLite rows per case; no classic bind or global feature-list producer. */
    private static void runGeometrySmoke(int type, boolean rules, boolean custom, boolean pattern) throws Exception {
        Bitmap icon = custom ? Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888) : null;
        String iconName = "sqlite-qa-cyan-" + java.util.UUID.randomUUID().toString();
        try (VectorStyleRenderingTest.Fixture f = new VectorStyleRenderingTest.Fixture()) {
            VectorLayerUI layer = f.add(type);
            Map<Long, JSONArray> expected = new LinkedHashMap<>();
            try {
                f.screen.onActivity(activity -> {
                    org.maplibre.android.maps.Style style = ((MapDrawable) f.app.getMap()).getMaplibreMap().getStyle();
                    style.addLayerAt(new BackgroundLayer("sqlite-qa-white").withProperties(PropertyFactory.backgroundColor(Color.WHITE)), 0);
                    if (icon != null) { icon.eraseColor(Color.CYAN); MarkerIconRegistry.registerCustomIconBitmap(iconName, icon); }
                });
                android.database.sqlite.SQLiteDatabase database = DatabaseContext.getDatabaseForLayer(layer, false);
                database.beginTransaction();
                try {
                    for (long id = 1; id <= 3; id++) {
                        JSONArray input = smokeCoordinates(type, (id - 2) * 160);
                        expected.put(id, new JSONArray(input.toString()));
                        String geometryName = type == GeoConstants.GTPoint ? "Point" : type == GeoConstants.GTMultiPoint ? "MultiPoint"
                                : type == GeoConstants.GTLineString ? "LineString" : type == GeoConstants.GTMultiLineString ? "MultiLineString"
                                : type == GeoConstants.GTPolygon ? "Polygon" : "MultiPolygon";
                        GeoGeometry geometry = GeoGeometryFactory.fromJson(new JSONObject().put("type", geometryName).put("coordinates", input));
                        setSmokeCrs(geometry);
                        Feature feature = new Feature(id, layer.getFields()); feature.setGeometry(geometry);
                        feature.setFieldValue("name", SMOKE_NAMES[(int) id - 1]); feature.setFieldValue("kind", SMOKE_KINDS[(int) id - 1]);
                        layer.createFeatureBatch(feature, database, false);
                    }
                    database.setTransactionSuccessful();
                } finally { database.endTransaction(); }
                layer.rebuildCache(null);
                com.nextgis.maplib.display.Style other = smokeStyle(type, rules ? Color.GREEN : Color.RED, Color.MAGENTA, pattern);
                com.nextgis.maplib.display.Style category = smokeStyle(type, Color.RED, Color.BLUE, false);
                if (custom) {
                    SimpleMarkerStyle marker = (SimpleMarkerStyle) category;
                    marker.setIconImage(iconName); marker.setIconSize(4); marker.setIconAllowOverlap(true); marker.setIconIgnorePlacement(true);
                }
                if (rules) {
                    FieldStyleRule rule = new FieldStyleRule(layer); rule.setKey("kind"); rule.setKeyIgnoreCase(true);
                    rule.setOtherStyle(other); rule.setStyle("A", category);
                    FieldStyleRule restored = new FieldStyleRule(layer); restored.fromJSON(rule.toJSON());
                    layer.setRenderer(new RuleFeatureRenderer(layer, restored, other));
                } else {
                    AtomicReference<com.nextgis.maplib.display.Style> restored = new AtomicReference<>();
                    SimpleFeatureRenderer.fromJSON(new JSONObject().put("style", other.toJSON()), restored);
                    layer.setRenderer(new SimpleFeatureRenderer(layer, restored.get()));
                }
                assertTrue(layer.save());
                byte[] before = contents(layer, 3);
                reload(f, layer); fitCorpus(f);
                assertSmokeNative(f, layer, type, rules); assertStreaming(f, layer);
                assertSmokeOriginals(provider(f, layer), type, expected);
                assertSmokePixels(f, type, rules, custom, pattern);
                if (custom) {
                    await("Only category A must place the custom icon", () -> smokeRendered(f, layer, type, 2).keySet()
                            .equals(java.util.Collections.singleton(1L)));
                    assertEquals(iconName, smokeRendered(f, layer, type, 2).get(1L).getStringProperty(MplFeatureStyleProps.ICON_IMAGE));
                    f.screen.onActivity(activity -> assertNotNull(((MapDrawable) f.app.getMap()).getMaplibreMap().getStyle().getImage(iconName)));
                }
                if (type == GeoConstants.GTLineString || type == GeoConstants.GTMultiLineString) {
                    assertLineViewportLabels(f, layer, type, 0);
                    f.screen.onActivity(activity -> {
                        MapDrawable map = (MapDrawable) f.app.getMap();
                        map.setZoomAndCenter((float) map.getMaplibreMap().getCameraPosition().zoom, new GeoPoint(420, 0), false, 0);
                    });
                    assertSmokeNative(f, layer, type, rules); assertLineViewportLabels(f, layer, type, 420);
                    assertStreaming(f, layer); assertSmokeOriginals(provider(f, layer), type, expected);
                }
                assertArrayEquals("SQLite native rendering/labels must preserve every typed original row", before, contents(layer, 3));
            } catch (AssertionError | Exception original) {
                throw new AssertionError("SQLite geometry=" + type + ": " + original.getMessage()
                        + "\nSQLite native witness: " + failureEvidence(f, layer, false, 3), original);
            } finally {
                if (custom) f.screen.onActivity(activity -> MarkerIconRegistry.registerCustomIconBitmap(iconName, null));
            }
        } finally { if (icon != null) icon.recycle(); }
    }

    private static com.nextgis.maplib.display.Style smokeStyle(int type, int color, int textColor, boolean pattern) {
        com.nextgis.maplib.display.Style style;
        if (type == GeoConstants.GTPoint || type == GeoConstants.GTMultiPoint) {
            SimpleMarkerStyle marker = new SimpleMarkerStyle(); marker.setType(SimpleMarkerStyle.MarkerStyleCircle);
            marker.setSize(8); marker.setField("name"); marker.setTextColor(textColor); marker.setTextSize(4);
            marker.setTextAlignment(SimpleMarkerStyle.ALIGN_TOP); style = marker;
        } else if (type == GeoConstants.GTLineString || type == GeoConstants.GTMultiLineString) {
            SimpleLineStyle line = new SimpleLineStyle(); line.setField("name"); line.setTextColor(textColor); line.setTextSize(4);
            line.setType(SimpleLineStyle.LineStyleSolid); style = line;
        } else {
            SimplePolygonStyle polygon = polygon(color, textColor);
            if (pattern) {
                PolygonPatternOptions options = new PolygonPatternOptions(); options.setColor(Color.BLUE); options.setAlpha(255);
                options.setAngle(45); options.setSpacing(16); options.setWidth(3);
                polygon.setFillPattern(PolygonPatternRegistry.FILL_PATTERN_HATCH); polygon.setPatternOptions(options);
            }
            style = polygon;
        }
        style.setColor(color); style.setAlpha(255); style.setOutAlpha(0); style.setWidth(2);
        LabelAttributes labels = LabelAttributes.fromStyle(style);
        labels.setTextAllowOverlap(true); labels.setTextHaloWidth(0); labels.setTextOpacity(255);
        labels.setTextOptional(false); labels.setTextPadding(0); labels.setLineLabelRepeat(false); labels.setLineLabelHorizontal(true);
        return style;
    }

    private static JSONArray smokeCoordinates(int type, double y) throws Exception {
        if (type == GeoConstants.GTPoint) return xy(0, y);
        if (type == GeoConstants.GTMultiPoint) return new JSONArray().put(xy(-45, y)).put(xy(45, y));
        JSONArray line = new JSONArray().put(xy(-1000, y)).put(xy(-250, y)).put(xy(300, y)).put(xy(1000, y));
        if (type == GeoConstants.GTLineString) return line;
        if (type == GeoConstants.GTMultiLineString) return new JSONArray().put(line)
                .put(new JSONArray().put(xy(-800, y + 35)).put(xy(100, y + 35)).put(xy(800, y + 35)));
        if (type == GeoConstants.GTPolygon) return new JSONArray().put(smokeRing(0, y, 100, 45, false))
                .put(smokeRing(0, y, 20, 12, true));
        return new JSONArray()
                .put(new JSONArray().put(smokeRing(-90, y, 65, 45, false)).put(smokeRing(-90, y, 15, 12, true)))
                .put(new JSONArray().put(smokeRing(90, y, 65, 45, false)).put(smokeRing(90, y, 15, 12, true)));
    }

    private static JSONArray xy(double x, double y) throws org.json.JSONException { return new JSONArray().put(x).put(y); }
    private static JSONArray smokeRing(double x, double y, double rx, double ry, boolean hole) throws org.json.JSONException {
        JSONArray ring = new JSONArray().put(xy(x - rx, y - ry));
        if (hole) ring.put(xy(x - rx, y + ry)).put(xy(x + rx, y + ry)).put(xy(x + rx, y - ry));
        else ring.put(xy(x + rx, y - ry)).put(xy(x + rx, y + ry)).put(xy(x - rx, y + ry));
        return ring.put(xy(x - rx, y - ry));
    }

    private static void setSmokeCrs(GeoGeometry geometry) {
        geometry.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        if (geometry instanceof GeoGeometryCollection) {
            GeoGeometryCollection collection = (GeoGeometryCollection) geometry;
            for (int i = 0; i < collection.size(); i++) setSmokeCrs(collection.get(i));
        } else if (geometry instanceof GeoPolygon) {
            GeoPolygon polygon = (GeoPolygon) geometry; setSmokeCrs(polygon.getOuterRing());
            for (int i = 0; i < polygon.getInnerRingCount(); i++) setSmokeCrs(polygon.getInnerRing(i));
        } else if (geometry instanceof GeoLineString) {
            GeoLineString line = (GeoLineString) geometry;
            for (int i = 0; i < line.getPointCount(); i++) setSmokeCrs(line.getPoint(i));
        }
    }

    /** Inspect each actual native role; do not mix icon/text anchors with geometry or deduplicate first. */
    private static Map<Long, org.maplibre.geojson.Feature> smokeRendered(VectorStyleRenderingTest.Fixture f,
                                                                       VectorLayerUI layer, int type, int role) {
        List<org.maplibre.geojson.Feature> hits = new ArrayList<>();
        boolean point = type == GeoConstants.GTPoint || type == GeoConstants.GTMultiPoint;
        boolean line = type == GeoConstants.GTLineString || type == GeoConstants.GTMultiLineString;
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); List<String> children = new ArrayList<>();
            String nativeId = map.getVectorRenderSourceId(layer.getId());
            String wanted = role == 1 && line ? layer.getPath().toString() + MPLFeaturesUtils.source_polygon_text : nativeId;
            for (org.maplibre.android.style.layers.Layer child : map.getMaplibreMap().getStyle().getLayers()) {
                String source = child instanceof CircleLayer ? ((CircleLayer) child).getSourceId()
                        : child instanceof FillLayer ? ((FillLayer) child).getSourceId()
                        : child instanceof LineLayer ? ((LineLayer) child).getSourceId()
                        : child instanceof SymbolLayer ? ((SymbolLayer) child).getSourceId() : null;
                if (wanted == null || !wanted.equals(source)) continue;
                boolean matches = role == 0 ? point ? child instanceof CircleLayer : line ? child instanceof LineLayer : child instanceof FillLayer
                        : child instanceof SymbolLayer && (role == 2 ? child.getId().contains("_icon") : child.getId().startsWith("symbol-"));
                if (matches) children.add(child.getId());
            }
            if (!children.isEmpty()) hits.addAll(map.getMaplibreMap().queryRenderedFeatures(new RectF(0, 0,
                    map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), children.toArray(new String[0])));
        });
        Map<Long, org.maplibre.geojson.Feature> result = new LinkedHashMap<>();
        for (org.maplibre.geojson.Feature hit : hits) {
            assertTrue("Every smoke hit retains its SQL identity", hit.hasProperty(MPLFeaturesUtils.prop_featureid));
            long id = hit.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong();
            assertTrue("Unexpected native SQL identity=" + id, id >= 1 && id <= 3);
            String geometry = hit.geometry() == null ? "null" : hit.geometry().type();
            assertTrue("Native role=" + role + " type=" + type + " actual=" + geometry,
                    role != 0 || point ? geometry.equals("Point") || geometry.equals("MultiPoint")
                            : line ? geometry.equals("LineString") || geometry.equals("MultiLineString")
                            : geometry.equals("Polygon") || geometry.equals("MultiPolygon"));
            if (role == 0 && !point) assertFalse("Geometry must not be a duplicate polygon label anchor", hit.hasProperty(NativeVectorTileEncoder.LABEL_ANCHOR));
            if (result.containsKey(id) && role == 0) {
                org.maplibre.geojson.Feature previous = result.get(id);
                for (String key : new String[]{MplFeatureStyleProps.COLOR_FILL_RULE, MplFeatureStyleProps.COLOR_FILL})
                    assertEquals("Every native component/fragment must retain the same paint SQL id=" + id + " property=" + key,
                            property(previous, key), property(hit, key));
            }
            result.put(id, hit);
        }
        return result;
    }

    private static void assertSmokeNative(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, int type, boolean rules) throws Exception {
        Set<Long> wanted = new HashSet<>(java.util.Arrays.asList(1L, 2L, 3L));
        await("All3 complete SQL geometry IDs must render, type=" + type, () -> smokeRendered(f, layer, type, 0).keySet().equals(wanted));
        Map<Long, org.maplibre.geojson.Feature> geometry = smokeRendered(f, layer, type, 0);
        for (Map.Entry<Long, org.maplibre.geojson.Feature> entry : geometry.entrySet()) {
            String key = entry.getValue().hasProperty(MplFeatureStyleProps.COLOR_FILL_RULE) ? MplFeatureStyleProps.COLOR_FILL_RULE : MplFeatureStyleProps.COLOR_FILL;
            com.google.gson.JsonElement rawPaint = entry.getValue().getProperty(key);
            String actual = rawPaint == null || rawPaint.isJsonNull() ? null : rawPaint.getAsString();
            if (rules || actual != null) {
                assertNotNull("Rule geometry must retain its own paint id=" + entry.getKey(), actual);
                assertEquals("Independent native paint id=" + entry.getKey(), rules && entry.getKey() != 1 ? "#00FF00" : "#FF0000",
                        actual.toUpperCase(java.util.Locale.ROOT));
            }
        }
        if (!rules) assertSimpleFactoryFallbacks(f, layer, type);
        await("All3 Russian SQL labels must be placed, type=" + type, () -> smokeRendered(f, layer, type, 1).keySet().equals(wanted));
        for (Map.Entry<Long, org.maplibre.geojson.Feature> entry : smokeRendered(f, layer, type, 1).entrySet()) {
            assertEquals(SMOKE_NAMES[entry.getKey().intValue() - 1], entry.getValue().getStringProperty(MplFeatureStyleProps.SIGNATURE));
            com.google.gson.JsonElement rawPaint = entry.getValue().getProperty(MplFeatureStyleProps.TEXT_COLOR);
            String actual = rawPaint == null || rawPaint.isJsonNull() ? null : rawPaint.getAsString();
            if (rules || actual != null) {
                assertNotNull("Rule label must retain its own paint id=" + entry.getKey(), actual);
                assertEquals(rules && entry.getKey() == 1 ? "#0000FF" : "#FF00FF", actual.toUpperCase(java.util.Locale.ROOT));
            }
        }
        f.renderedFrame();
    }

    private static void assertSimpleFactoryFallbacks(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, int type) {
        f.screen.onActivity(activity -> {
            try {
                org.maplibre.android.maps.Style style = ((MapDrawable) f.app.getMap()).getMaplibreMap().getStyle();
                org.maplibre.android.style.layers.Layer geometry = style.getLayer("nglayer-layer-" + layer.getId());
                boolean point = type == GeoConstants.GTPoint || type == GeoConstants.GTMultiPoint;
                boolean line = type == GeoConstants.GTLineString || type == GeoConstants.GTMultiLineString;
                assertTrue("Simple native factory must retain its real geometry layer", point ? geometry instanceof CircleLayer
                        : line ? geometry instanceof LineLayer : geometry instanceof FillLayer);
                org.maplibre.android.style.layers.PropertyValue<String> paint = point ? ((CircleLayer) geometry).getCircleColor()
                        : line ? ((LineLayer) geometry).getLineColor() : ((FillLayer) geometry).getFillColor();
                assertEquals("Simple geometry needs an explicit red factory fallback as well as all3 pixel patches", Color.RED, fallbackColor(paint));
                org.maplibre.android.style.layers.Layer labels = style.getLayer("symbol-nglayer-layer-" + layer.getId());
                assertTrue("Simple Russian labels require the real native SymbolLayer", labels instanceof SymbolLayer);
                assertEquals("Simple labels need an explicit magenta factory fallback", Color.MAGENTA,
                        fallbackColor(((SymbolLayer) labels).getTextColor()));
            } catch (org.json.JSONException error) { throw new AssertionError("Cannot inspect the actual simple factory fallback", error); }
        });
    }

    /** Evaluate only the native colour fallback with feature overrides absent, independently of the mapper. */
    private static int fallbackColor(org.maplibre.android.style.layers.PropertyValue<String> paint) throws org.json.JSONException {
        assertNotNull("A simple factory cannot silently omit native paint", paint);
        Object value = paint.isExpression() ? noFeatureOverride(new JSONArray(paint.getExpression().toString())) : paint.getValue();
        assertTrue("Native factory must provide an explicit colour fallback, actual=" + value, value instanceof String);
        return Color.parseColor((String) value);
    }

    private static Object noFeatureOverride(Object value) throws org.json.JSONException {
        if (value == JSONObject.NULL) return null;
        if (!(value instanceof JSONArray)) return value;
        JSONArray expression = (JSONArray) value; String operation = expression.getString(0);
        if (operation.equals("get")) return null;
        if (operation.equals("literal")) return expression.get(1);
        if (operation.equals("to-color")) return noFeatureOverride(expression.get(1));
        if (operation.equals("coalesce")) {
            for (int i = 1; i < expression.length(); i++) {
                Object candidate = noFeatureOverride(expression.get(i)); if (candidate != null) return candidate;
            }
            return null;
        }
        throw new AssertionError("Unsupported native colour fallback operation=" + operation);
    }

    private static void assertLineViewportLabels(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, int type, double centerX) throws Exception {
        AtomicReference<Double> projectedCenter = new AtomicReference<>();
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            LatLng center = map.getMaplibreMap().getProjection().fromScreenLocation(new PointF(
                    map.getMaplibreMapView().getWidth() / 2f, map.getMaplibreMapView().getHeight() / 2f));
            projectedCenter.set(Math.toRadians(center.getLongitude()) * 6378137.0);
        });
        assertEquals("The real camera must reach the commanded pan before checking viewport anchors", centerX, projectedCenter.get(), 2);
        await("Production SQL viewport label anchors must follow the visible line, center=" + centerX, () -> {
            Map<Long, org.maplibre.geojson.Feature> labels = smokeRendered(f, layer, type, 1);
            if (labels.size() != 3) return false;
            for (org.maplibre.geojson.Feature label : labels.values()) {
                if (!(label.geometry() instanceof org.maplibre.geojson.Point) || !label.hasProperty(VisibleLineLabelLayout.ANGLE)) return false;
                double x = Math.toRadians(((org.maplibre.geojson.Point) label.geometry()).longitude()) * 6378137.0;
                // The closest point on these complete horizontal lines has the screen center's x.
                // Independent public projection oracle; no layout/provider helper is called by the test.
                if (Math.abs(x - projectedCenter.get()) > 2 || Math.abs(label.getNumberProperty(VisibleLineLabelLayout.ANGLE).doubleValue()) > .01) return false;
            }
            return true;
        });
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); String source = layer.getPath().toString() + MPLFeaturesUtils.source_polygon_text;
            assertTrue("Viewport label source is separate from native MVT geometry", map.getMaplibreMap().getStyle().getSource(source) instanceof GeoJsonSource);
            for (org.maplibre.android.style.layers.Layer child : map.getMaplibreMap().getStyle().getLayers())
                if (child instanceof SymbolLayer && source.equals(((SymbolLayer) child).getSourceId())) {
                    assertEquals("point", ((SymbolLayer) child).getSymbolPlacement().getValue());
                    assertEquals("viewport", ((SymbolLayer) child).getTextRotationAlignment().getValue());
                }
        });
    }

    private static void assertSmokeOriginals(SqliteVectorTileProvider provider, int type, Map<Long, JSONArray> expected) throws Exception {
        for (long id = 1; id <= 3; id++) {
            Feature original = provider.loadOriginalFeature(id); assertNotNull(original); assertEquals(id, original.getId());
            assertEquals(type, original.getGeometry().getType());
            assertEquals(SMOKE_NAMES[(int) id - 1], original.getFieldValue("name"));
            assertEquals(SMOKE_KINDS[(int) id - 1], original.getFieldValue("kind"));
            assertCoordinates("Original SQL id=" + id, expected.get(id), originalCoordinates(original.getGeometry()));
        }
    }

    private static JSONArray originalCoordinates(GeoGeometry geometry) throws Exception {
        if (geometry instanceof GeoPoint) return xy(((GeoPoint) geometry).getX(), ((GeoPoint) geometry).getY());
        JSONArray result = new JSONArray();
        if (geometry instanceof GeoGeometryCollection) {
            GeoGeometryCollection collection = (GeoGeometryCollection) geometry;
            for (int i = 0; i < collection.size(); i++) result.put(originalCoordinates(collection.get(i)));
        } else if (geometry instanceof GeoPolygon) {
            GeoPolygon polygon = (GeoPolygon) geometry; result.put(originalCoordinates(polygon.getOuterRing()));
            for (int i = 0; i < polygon.getInnerRingCount(); i++) result.put(originalCoordinates(polygon.getInnerRing(i)));
        } else if (geometry instanceof GeoLineString) {
            GeoLineString line = (GeoLineString) geometry;
            for (int i = 0; i < line.getPointCount(); i++) result.put(originalCoordinates(line.getPoint(i)));
        } else fail("Unsupported original geometry=" + geometry.getType());
        return result;
    }

    private static void assertCoordinates(String message, JSONArray expected, JSONArray actual) throws Exception {
        assertEquals(message + " part/vertex count", expected.length(), actual.length());
        for (int i = 0; i < expected.length(); i++) {
            if (expected.get(i) instanceof JSONArray) assertCoordinates(message + "/" + i, expected.getJSONArray(i), actual.getJSONArray(i));
            else assertEquals(message + "/" + i, expected.getDouble(i), actual.getDouble(i), 1e-9);
        }
    }

    private static void assertSmokePixels(VectorStyleRenderingTest.Fixture f, int type, boolean rules, boolean custom, boolean pattern) throws Exception {
        long started = SystemClock.uptimeMillis(), deadline = started + 30000; int attempts = 0;
        // Placement queries can settle before asynchronously uploaded/fading symbols reach a snapshot.
        // Retry the same complete pixel oracle; do not alter the camera, sources, paint or thresholds.
        while (true) {
            attempts++;
            try { assertSmokePixelFrame(f, type, rules, custom, pattern); return; }
            catch (AssertionError pending) {
                if (SystemClock.uptimeMillis() >= deadline) throw new AssertionError("Actual pixel oracle did not settle: attempts="
                        + attempts + ", elapsed_ms=" + (SystemClock.uptimeMillis() - started) + "; " + pending.getMessage(), pending);
                Thread.sleep(50);
            }
        }
    }

    private static void assertSmokePixelFrame(VectorStyleRenderingTest.Fixture f, int type, boolean rules, boolean custom, boolean pattern) throws Exception {
        boolean point = type == GeoConstants.GTPoint || type == GeoConstants.GTMultiPoint;
        boolean line = type == GeoConstants.GTLineString || type == GeoConstants.GTMultiLineString;
        boolean multi = type == GeoConstants.GTMultiPoint || type == GeoConstants.GTMultiLineString || type == GeoConstants.GTMultiPolygon;
        List<PointF> patches = new ArrayList<>(); List<List<PointF>> holes = new ArrayList<>();
        List<Integer> objectIds = new ArrayList<>();
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            for (int id = 1; id <= 3; id++) for (int part = 0; part < (multi ? 2 : 1); part++) {
                double y = (id - 2) * 160 + (line && part == 1 ? 35 : 0);
                double x = point ? multi ? part == 0 ? -45 : 45 : 0 : line ? -200 : multi ? part == 0 ? -135 : 135 : 70;
                patches.add(map.getMaplibreMap().getProjection().toScreenLocation(latLng(x, y))); objectIds.add(id);
                if (!point && !line) {
                    List<PointF> samples = new ArrayList<>(); double holeX = multi ? part == 0 ? -90 : 90 : 0;
                    for (int dy : new int[]{-6, 0, 6}) for (int dx : new int[]{-8, 0, 8})
                        samples.add(map.getMaplibreMap().getProjection().toScreenLocation(latLng(holeX + dx, y + dy)));
                    holes.add(samples);
                }
            }
        });
        Bitmap image = f.snapshot();
        try {
            for (int i = 0; i < patches.size(); i++) {
                PointF patch = patches.get(i); int id = objectIds.get(i), matches = 0, blue = 0;
                for (int dy = -8; dy <= 8; dy++) for (int dx = -8; dx <= 8; dx++) {
                    int x = Math.round(patch.x) + dx, y = Math.round(patch.y) + dy;
                    assertTrue("Independent smoke sample must be in the snapshot", x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight());
                    int color = image.getPixel(x, y);
                    if (custom && id == 1 ? Color.red(color) < 40 && Color.green(color) > 200 && Color.blue(color) > 200
                            : rules && id > 1 ? Color.green(color) > 200 && Color.red(color) < 40 && Color.blue(color) < 40
                            : Color.red(color) > 200 && Color.green(color) < 40 && Color.blue(color) < 40) matches++;
                    if (Color.blue(color) > 200 && Color.red(color) < 40 && Color.green(color) < 40) blue++;
                }
                assertTrue("Actual native geometry pixels SQL id=" + id + " component=" + (multi ? i % 2 : 0)
                        + " matches=" + matches + " blue=" + blue + " sample=" + patch.x + "," + patch.y
                        + " snapshot=" + image.getWidth() + "x" + image.getHeight(), matches >= 5);
                if (pattern) assertTrue("Procedural blue hatch must reach actual native pixels SQL id=" + id, blue >= 5);
            }
            for (List<PointF> hole : holes) {
                int white = 0;
                // Spread samples strictly inside the independently authored hole; a placed label
                // may cover individual pixels, but a filled/dropped hole exposes no white here.
                for (PointF sample : hole) {
                    int x = Math.round(sample.x), y = Math.round(sample.y);
                    assertTrue("Hole sample must lie inside the snapshot", x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight());
                    int color = image.getPixel(x, y);
                    if (Color.red(color) > 245 && Color.green(color) > 245 && Color.blue(color) > 245) white++;
                }
                assertTrue("Each polygon component hole must expose the independent white background", white >= 3);
            }
        } finally { image.recycle(); }
    }

    private static void awaitBackgroundExcluding(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, long excluded) throws Exception {
        Set<Long> expected = new HashSet<>(); for (long id = 1; id <= ROWS; id++) if (id != excluded) expected.add(id);
        Set<Long> labels = new HashSet<>(LABELS.keySet()); labels.remove(excluded);
        await("Exactly128 background polygons must remain without the selected/hidden FID", () -> rendered(f, layer, false).keySet().equals(expected));
        assertEquals(expected, rendered(f, layer, false).keySet());
        await("The excluded FID must also disappear from every background label child", () -> rendered(f, layer, true).keySet().equals(labels));
        assertEquals(labels, rendered(f, layer, true).keySet());
    }

    /** Cached native hits cannot stand in for a live DB/style generation after an editor transition. */
    private static void assertCurrentSqliteWork(VectorStyleRenderingTest.Fixture f, Corpus corpus, String stage) throws Exception {
        SqliteVectorTileProvider current = provider(f, corpus.layer);
        assertNotNull(stage + ": SQL provider must remain registered", current);
        assertTrue(stage + ": registered provider must be current after the normal model transition", current.isCurrent());
        // ID1 is an unchanged visible original throughout editing/hiding ID31. Read it through
        // the actual rich DB boundary, then request its canonical3857 tile outside the UI thread.
        assertEditablePolygon(current.readOriginalFeature(1), 1, corpus.coordinates.get(1L));
        SqliteVectorTileRequest request = current.snapshot(16, 32767, 32768);
        assertTrue(stage + ": a newly captured request must own the current generation", current.isCurrent(request));
        byte[] bytes = current.load(request, () -> true);
        assertNotNull(stage + ": current tile work must return bytes", bytes);
        assertTrue(stage + ": technical tile must remain within its bounded evidence limit", bytes.length > 0 && bytes.length <= 4 * 1024 * 1024);
        JSONObject decoded = SqliteMvtEvidence.summarize(bytes);
        assertEquals(stage + ": independent wire decode", "decoded", decoded.getString("status"));
        assertTrue(stage + ": the wire witness must be complete", decoded.getBoolean("complete"));
        int polygons = 0; JSONArray layers = decoded.getJSONArray("layers");
        for (int i = 0; i < layers.length(); i++) polygons += layers.getJSONObject(i).getJSONObject("type_counts").getInt("polygon");
        assertTrue(stage + ": a visible original's tile must contain actual polygons, not only labels", polygons > 0);
        assertTrue(stage + ": tile IO must finish in the same current generation", current.isCurrent(request));
        assertSame(stage + ": the completed request must still belong to the registered provider", current, provider(f, corpus.layer));
    }

    private static void awaitSelectedPolygon(VectorStyleRenderingTest.Fixture f, long id, double[][] expectedOriginal) throws Exception {
        await("One selected polygon must reach the real tiny overlay source", () -> {
            Set<Long> ids = new HashSet<>();
            List<org.maplibre.geojson.Feature> observed = new ArrayList<>();
            AtomicReference<org.maplibre.geojson.Feature> authoritative = new AtomicReference<>();
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap();
                GeoJsonSource source = map.getMaplibreMap().getStyle().getSourceAs("selected-poly-source");
                if (source != null) observed.addAll(source.querySourceFeatures(null));
                if (map.editingObject != null) authoritative.set(map.editingObject.editingFeature);
            });
            for (org.maplibre.geojson.Feature feature : observed) {
                com.google.gson.JsonElement direction = feature.getProperty("edit_direction");
                if (direction != null) {
                    assertTrue("Only an explicit direction accessory may omit SQL identity", direction.isJsonPrimitive()
                            && direction.getAsJsonPrimitive().isBoolean() && direction.getAsBoolean());
                    assertFalse("A direction accessory is not another selected object", feature.hasProperty(MPLFeaturesUtils.prop_featureid));
                    assertTrue("Direction accessory must remain a line", feature.geometry() instanceof org.maplibre.geojson.LineString);
                    List<org.maplibre.geojson.Point> endpoints = ((org.maplibre.geojson.LineString) feature.geometry()).coordinates();
                    assertEquals("Direction accessory contains exactly one segment", 2, endpoints.size());
                    assertNotEquals("Direction accessory cannot be a degenerate segment", endpoints.get(0), endpoints.get(1));
                    for (org.maplibre.geojson.Point point : endpoints) assertTrue("Direction coordinates must be finite",
                            Double.isFinite(point.longitude()) && Double.isFinite(point.latitude()));
                } else {
                    assertNotNull("Every actual selected polygon retains its SQL identity", feature.getProperty(MPLFeaturesUtils.prop_featureid));
                    assertTrue("Selected overlay geometry must remain polygonal", feature.geometry() instanceof org.maplibre.geojson.Polygon);
                    ids.add(feature.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong());
                }
            }
            if (ids.equals(java.util.Collections.singleton(id)))
                assertEditablePolygon(authoritative.get(), id, expectedOriginal);
            return ids.equals(java.util.Collections.singleton(id));
        });
    }

    private static void assertEditablePolygon(org.maplibre.geojson.Feature feature, long id, double[][] expected) {
        assertNotNull(feature); assertEquals(id, feature.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong());
        assertTrue("Editable geometry must be the full original polygon", feature.geometry() instanceof org.maplibre.geojson.Polygon);
        List<List<org.maplibre.geojson.Point>> rings = ((org.maplibre.geojson.Polygon) feature.geometry()).coordinates();
        assertEquals(1, rings.size()); assertEquals(VERTICES + 1, rings.get(0).size());
        for (int i = 0; i <= VERTICES; i++) {
            LatLng wanted = latLng(expected[i][0], expected[i][1]);
            assertEquals("Editable longitude vertex=" + i, wanted.getLongitude(), rings.get(0).get(i).longitude(), 1e-10);
            assertEquals("Editable latitude vertex=" + i, wanted.getLatitude(), rings.get(0).get(i).latitude(), 1e-10);
        }
    }

    private static final class Corpus {
        final VectorLayerUI layer;
        // Exact input lists are independent of all production conversion/tiling helpers.
        final Map<Long, double[][]> coordinates = new LinkedHashMap<>();
        Corpus(VectorStyleRenderingTest.Fixture f) throws Exception {
            layer = f.add(GeoConstants.GTPolygon);
            android.database.sqlite.SQLiteDatabase database = DatabaseContext.getDatabaseForLayer(layer, false);
            database.beginTransaction();
            try {
                for (long id = 1; id <= ROWS; id++) {
                    int index = (int) id - 1;
                    double[][] input = new double[VERTICES + 1][2];
                    GeoLinearRing ring = new GeoLinearRing(); ring.setCRS(GeoConstants.CRS_WEB_MERCATOR);
                    for (int vertex = 0; vertex <= VERTICES; vertex++) {
                        double angle = 2 * Math.PI * (vertex % VERTICES) / VERTICES;
                        input[vertex][0] = -288 + index % 13 * 48 + 15 * Math.cos(angle);
                        input[vertex][1] = -216 + index / 13 * 48 + 15 * Math.sin(angle);
                        GeoPoint point = new GeoPoint(input[vertex][0], input[vertex][1]);
                        point.setCRS(GeoConstants.CRS_WEB_MERCATOR); ring.add(point);
                    }
                    coordinates.put(id, input);
                    GeoPolygon polygon = new GeoPolygon(); polygon.setCRS(GeoConstants.CRS_WEB_MERCATOR); polygon.setOuterRing(ring);
                    Feature feature = new Feature(id, layer.getFields()); feature.setGeometry(polygon);
                    feature.setFieldValue("name", LABELS.getOrDefault(id, ""));
                    feature.setFieldValue("kind", id % 3 == 1 ? " a " : id % 3 == 2 ? null : "unmatched");
                    layer.createFeatureBatch(feature, database, false);
                }
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            layer.rebuildCache(null); installStyle(layer, false);
        }
    }

    private static void installStyle(VectorLayerUI layer, boolean reversed) throws Exception {
        SimplePolygonStyle other = polygon(reversed ? Color.RED : Color.GREEN, Color.MAGENTA);
        SimplePolygonStyle category = polygon(reversed ? Color.GREEN : Color.RED, Color.BLUE);
        FieldStyleRule rule = new FieldStyleRule(layer); rule.setKey("kind"); rule.setKeyIgnoreCase(true);
        rule.setOtherStyle(other); rule.setStyle("A", category);
        // Persist/restore the public style contract; never construct a full Java feature list.
        FieldStyleRule restored = new FieldStyleRule(layer); restored.fromJSON(rule.toJSON());
        layer.setRenderer(new RuleFeatureRenderer(layer, restored, other)); assertTrue(layer.save());
    }

    private static SimplePolygonStyle polygon(int color, int textColor) {
        SimplePolygonStyle style = new SimplePolygonStyle();
        style.setFill(true); style.setColor(color); style.setOutColor(Color.BLACK);
        style.setAlpha(255); style.setOutAlpha(0); style.setField("name");
        style.setTextColor(textColor); style.setTextSize(4f);
        LabelAttributes labels = LabelAttributes.fromStyle(style);
        labels.setTextAllowOverlap(true); labels.setTextHaloWidth(0); labels.setTextOpacity(255);
        return style;
    }

    private static void reload(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) throws Exception {
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            map.reloadVectorLayerDataToMaplibre(layer);
        });
        await("SQLite provider must be attached", () -> provider(f, layer) != null);
        f.screen.onActivity(activity -> assertTrue("An attached SQL provider must be the effective source",
                ((MapDrawable) f.app.getMap()).isSqliteVectorLayer(layer.getId())));
    }

    private static SqliteVectorTileProvider provider(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) {
        AtomicReference<SqliteVectorTileProvider> result = new AtomicReference<>();
        f.screen.onActivity(activity -> result.set(((MapDrawable) f.app.getMap()).getSqliteVectorProvider(layer.getId())));
        return result.get();
    }

    private static void assertStreaming(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) {
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            String nativeId = map.getVectorRenderSourceId(layer.getId());
            assertNotNull("Prepared vector must expose its effective native source ID", nativeId);
            Source source = map.getMaplibreMap().getStyle().getSource(nativeId);
            assertTrue("CustomVectorSource must replace global GeoJSON", source instanceof CustomVectorSource);
            assertNotNull("Empty compatibility entry means prepared SQL data", map.getLayerFeatures(layer));
            assertTrue("No full Java feature collection may remain", map.getLayerFeatures(layer).isEmpty());
            assertNotNull(map.getSqliteVectorProvider(layer.getId()));
        });
    }

    private static void fitCorpus(VectorStyleRenderingTest.Fixture f) throws Exception {
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            LatLngBounds bounds = new LatLngBounds.Builder().include(latLng(-320, -260)).include(latLng(320, 260)).build();
            map.getMaplibreMap().moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 35));
            CameraPosition camera = map.getMaplibreMap().getCameraPosition();
            map.setZoomAndCenter((float) camera.zoom, new GeoPoint(0, 0), false, 0);
        });
        f.renderedFrame();
    }

    private static LatLng latLng(double x, double y) {
        return new LatLng(Math.toDegrees(2 * Math.atan(Math.exp(y / 6378137.0)) - Math.PI / 2),
                Math.toDegrees(x / 6378137.0));
    }

    private static Map<Long, org.maplibre.geojson.Feature> rendered(VectorStyleRenderingTest.Fixture f,
                                                                  VectorLayerUI layer, boolean labels) {
        Map<Long, org.maplibre.geojson.Feature> result = new HashMap<>();
        List<org.maplibre.geojson.Feature> hits = new ArrayList<>();
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap(); List<String> ids = new ArrayList<>();
            String nativeId = map.getVectorRenderSourceId(layer.getId());
            for (org.maplibre.android.style.layers.Layer child : map.getMaplibreMap().getStyle().getLayers()) {
                String source = child instanceof FillLayer ? ((FillLayer) child).getSourceId()
                        : child instanceof SymbolLayer ? ((SymbolLayer) child).getSourceId() : null;
                if (nativeId != null && nativeId.equals(source)
                        && (labels ? child instanceof SymbolLayer : child instanceof FillLayer)) ids.add(child.getId());
            }
            if (ids.isEmpty()) return;
            hits.addAll(map.getMaplibreMap().queryRenderedFeatures(
                    new RectF(0, 0, map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), ids.toArray(new String[0])));
        });
        // Inspect every raw role before deduplicating legitimate clipped polygon fragments.
        // Assertions stay on the instrumentation thread so failure evidence can still be captured.
        for (org.maplibre.geojson.Feature feature : hits) {
            assertTrue("Every native hit retains its stable SQL identity", feature.hasProperty(MPLFeaturesUtils.prop_featureid));
            long id = feature.getProperty(MPLFeaturesUtils.prop_featureid).getAsLong();
            String role = (labels ? "SymbolLayer" : "FillLayer") + " SQL id=" + id;
            if (labels) {
                assertTrue(role + " must contain a label Point", feature.geometry() instanceof org.maplibre.geojson.Point);
                com.google.gson.JsonElement anchor = feature.getProperty(NativeVectorTileEncoder.LABEL_ANCHOR);
                assertTrue(role + " must retain its explicit label-anchor role", anchor != null && anchor.isJsonPrimitive()
                        && anchor.getAsJsonPrimitive().isBoolean() && anchor.getAsBoolean());
            } else {
                assertTrue(role + " must contain only Polygon/MultiPolygon, actual="
                                + (feature.geometry() == null ? "null" : feature.geometry().type()),
                        feature.geometry() instanceof org.maplibre.geojson.Polygon
                                || feature.geometry() instanceof org.maplibre.geojson.MultiPolygon);
                assertFalse(role + " must not duplicate the label-anchor role", feature.hasProperty(NativeVectorTileEncoder.LABEL_ANCHOR));
            }
            result.put(id, feature);
        }
        return result;
    }

    private static void awaitRendered(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, boolean reversed) throws Exception {
        try { assertRendered(f, layer, reversed); }
        catch (AssertionError | Exception original) {
            String evidence = failureEvidence(f, layer, reversed);
            throw new AssertionError(original.getMessage() + "\nSQLite native witness: " + evidence, original);
        }
    }

    private static void assertRendered(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, boolean reversed) throws Exception {
        Set<Long> expected = new HashSet<>(); for (long id = 1; id <= ROWS; id++) expected.add(id);
        await("Every one of129 SQL objects must be rendered in the fitted camera", () -> rendered(f, layer, false).keySet().equals(expected));
        await("Current category paints must reach every native polygon", () -> {
            Map<Long, org.maplibre.geojson.Feature> current = rendered(f, layer, false);
            if (!current.keySet().equals(expected)) return false;
            for (Map.Entry<Long, org.maplibre.geojson.Feature> polygon : current.entrySet()) {
                org.maplibre.geojson.Feature feature = polygon.getValue();
                String property = feature.hasProperty(MplFeatureStyleProps.COLOR_FILL_RULE)
                        ? MplFeatureStyleProps.COLOR_FILL_RULE : MplFeatureStyleProps.COLOR_FILL;
                if (!feature.hasProperty(property) || !((polygon.getKey() % 3 == 1) != reversed ? "#FF0000" : "#00FF00")
                        .equalsIgnoreCase(feature.getStringProperty(property))) return false;
            }
            return true;
        });
        Map<Long, org.maplibre.geojson.Feature> polygons = rendered(f, layer, false);
        assertEquals(expected, polygons.keySet());
        for (Map.Entry<Long, org.maplibre.geojson.Feature> polygon : polygons.entrySet()) {
            boolean red = (polygon.getKey() % 3 == 1) != reversed;
            org.maplibre.geojson.Feature feature = polygon.getValue();
            String color = feature.hasProperty(MplFeatureStyleProps.COLOR_FILL_RULE) ? MplFeatureStyleProps.COLOR_FILL_RULE : MplFeatureStyleProps.COLOR_FILL;
            assertEquals("Native category paint id=" + polygon.getKey(), red ? "#FF0000" : "#00FF00",
                    feature.getStringProperty(color).toUpperCase(java.util.Locale.ROOT));
        }
        await("Category, NULL and Other Russian labels must all reach native placement", () -> rendered(f, layer, true).keySet().equals(LABELS.keySet()));
        Map<Long, org.maplibre.geojson.Feature> nativeLabels = rendered(f, layer, true);
        for (Map.Entry<Long, String> expectedLabel : LABELS.entrySet()) {
            org.maplibre.geojson.Feature actual = nativeLabels.get(expectedLabel.getKey());
            assertEquals(expectedLabel.getValue(), actual.getStringProperty(MplFeatureStyleProps.SIGNATURE));
            assertEquals(expectedLabel.getKey() == 31 ? "#0000FF" : "#FF00FF",
                    actual.getStringProperty(MplFeatureStyleProps.TEXT_COLOR).toUpperCase(java.util.Locale.ROOT));
        }
        f.renderedFrame();
    }

    /** Failure-only observations. No filtering, deduplication or expectation is changed here. */
    private static String failureEvidence(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, boolean reversed) {
        return failureEvidence(f, layer, reversed, ROWS);
    }

    private static String failureEvidence(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, boolean reversed, int expectedRows) {
        JSONObject state = new JSONObject(); JSONArray rows = new JSONArray(), focus = new JSONArray();
        StringBuilder summary = new StringBuilder();
        AtomicReference<SqliteVectorTileProvider> capturedProvider = new AtomicReference<>();
        AtomicReference<double[]> capturedCamera = new AtomicReference<>();
        try {
            state.put("schema_version", 1).put("expected_ids", expectedRows).put("expected_reversed", reversed)
                    .put("captured_uptime_ms", SystemClock.uptimeMillis());
            f.screen.onActivity(activity -> {
                try {
                    MapDrawable map = (MapDrawable) f.app.getMap();
                    org.maplibre.android.maps.Style style = map.getMaplibreMap().getStyle();
                    String nativeId = map.getVectorRenderSourceId(layer.getId());
                    Source source = nativeId == null ? null : style.getSource(nativeId);
                    SqliteVectorTileProvider provider = map.getSqliteVectorProvider(layer.getId());
                    CameraPosition camera = map.getMaplibreMap().getCameraPosition();
                    capturedProvider.set(provider);
                    if (camera.target != null) capturedCamera.set(new double[]{camera.zoom, camera.target.getLongitude(), camera.target.getLatitude()});
                    org.maplibre.android.maps.MapView view = map.getMaplibreMapView();
                    android.view.View renderView = view == null ? null : view.getRenderView();
                    state.put("logical_source_id", shortText(layer.getPath().toString(), 180))
                            .put("source_id", nativeId == null ? "absent" : shortText(nativeId, 280))
                            .put("source_class", source == null ? "absent" : source.getClass().getName())
                            .put("provider_current", provider != null && provider.isCurrent())
                            .put("sqlite_bridge", new JSONObject(map.getSqliteVectorDiagnostics(layer.getId())))
                            .put("source_query_features_api", "unavailable_CustomVectorSource_13_7_1")
                            .put("source_prefetch_delta", source == null ? "unknown" : String.valueOf(source.getPrefetchZoomDelta()))
                            .put("source_volatile", source == null ? "unknown" : String.valueOf(source.isVolatile()))
                            .put("layer_owner_map_current", DatabaseContext.getMapForLayer(layer) == map)
                            .put("map_class", map.getClass().getSimpleName())
                            .put("java_feature_count", map.getLayerFeatures(layer) == null ? -1 : map.getLayerFeatures(layer).size())
                            .put("width", map.getMaplibreMapView().getWidth()).put("height", map.getMaplibreMapView().getHeight())
                            .put("zoom", camera.zoom).put("bearing", camera.bearing).put("tilt", camera.tilt);
                    state.put("view_current", view == activity.findViewById(com.nextgis.mobile.R.id.mapViewMaplibre))
                            .put("view_attached", view != null && view.isAttachedToWindow())
                            .put("view_shown", view != null && view.isShown())
                            .put("view_focused", view != null && view.hasWindowFocus())
                            .put("view_destroyed", view == null || view.isDestroyed())
                            .put("view_visibility", view == null ? -1 : view.getVisibility())
                            .put("view_window_visibility", view == null ? -1 : view.getWindowVisibility())
                            .put("view_alpha", view == null ? -1 : view.getAlpha())
                            .put("render_view_class", renderView == null ? "unavailable" : renderView.getClass().getSimpleName())
                            .put("render_view_shown", renderView != null && renderView.isShown())
                            .put("render_view_visibility", renderView == null ? -1 : renderView.getVisibility())
                            .put("render_view_alpha", renderView == null ? -1 : renderView.getAlpha())
                            .put("frame_count", "unavailable_public_api")
                            .put("last_frame_age_ms", "unavailable_fresh_public_api");
                    try {
                        SqliteVectorTileRequest identity = provider == null ? null : provider.snapshot(0, 0, 0);
                        state.put("provider_source_epoch", identity == null ? "unknown" : identity.getSourceEpoch())
                                .put("provider_content_version", identity == null ? "unknown" : identity.getContentVersion());
                    } catch (Exception error) { state.put("provider_epoch_error_type", error.getClass().getSimpleName()); }
                    JSONArray layers = new JSONArray(), images = new JSONArray();
                    Set<String> imageNames = new HashSet<>();
                    state.put("layers", layers).put("native_custom_images", images);
                    for (org.maplibre.android.style.layers.Layer child : style.getLayers()) {
                        String sourceId = child instanceof CircleLayer ? ((CircleLayer) child).getSourceId()
                                : child instanceof FillLayer ? ((FillLayer) child).getSourceId()
                                : child instanceof LineLayer ? ((LineLayer) child).getSourceId()
                                : child instanceof SymbolLayer ? ((SymbolLayer) child).getSourceId() : null;
                        String logical = layer.getPath().toString();
                        if (sourceId == null || layers.length() >= 16 || !(sourceId.equals(nativeId)
                                || sourceId.equals(logical) || sourceId.equals(logical + MPLFeaturesUtils.source_polygon_text)
                                || sourceId.startsWith(logical + "#sqlite-native-"))) continue;
                        JSONObject item = new JSONObject().put("id", shortText(child.getId(), 120))
                                .put("class", child.getClass().getSimpleName()).put("source_id", shortText(sourceId, 280))
                                .put("current_source_match", sourceId.equals(nativeId))
                                .put("visibility", String.valueOf(child.getVisibility().getValue()))
                                .put("min_zoom", child.getMinZoom()).put("max_zoom", child.getMaxZoom());
                        if (child instanceof FillLayer) {
                            FillLayer fill = (FillLayer) child;
                            item.put("source_layer", fill.getSourceLayer()).put("filter", shortText(String.valueOf(fill.getFilter()), 600))
                                    .put("fill_color", shortText(String.valueOf(fill.getFillColor()), 600))
                                    .put("fill_opacity", shortText(String.valueOf(fill.getFillOpacity()), 600))
                                    .put("fill_pattern", shortText(String.valueOf(fill.getFillPattern()), 240))
                                    .put("fill_translate", shortText(String.valueOf(fill.getFillTranslate()), 240))
                                    .put("fill_antialias", String.valueOf(fill.getFillAntialias()));
                        } else if (child instanceof LineLayer) {
                            LineLayer line = (LineLayer) child;
                            item.put("source_layer", line.getSourceLayer()).put("filter", shortText(String.valueOf(line.getFilter()), 600))
                                    .put("line_opacity", shortText(String.valueOf(line.getLineOpacity()), 600))
                                    .put("line_width", shortText(String.valueOf(line.getLineWidth()), 600));
                        } else if (child instanceof CircleLayer) {
                            CircleLayer circle = (CircleLayer) child;
                            item.put("source_layer", circle.getSourceLayer())
                                    .put("filter", shortText(String.valueOf(circle.getFilter()), 600))
                                    .put("circle_color", shortText(String.valueOf(circle.getCircleColor()), 600))
                                    .put("circle_opacity", shortText(String.valueOf(circle.getCircleOpacity()), 600));
                        } else {
                            SymbolLayer symbol = (SymbolLayer) child;
                            item.put("source_layer", symbol.getSourceLayer())
                                    .put("filter", shortText(String.valueOf(symbol.getFilter()), 600))
                                    .put("text_offset", shortText(offsetIdentity(symbol.getTextOffset()), 600))
                                    .put("icon_offset", shortText(offsetIdentity(symbol.getIconOffset()), 600))
                                    .put("text_size", shortText(String.valueOf(symbol.getTextSize()), 600))
                                    .put("text_color", shortText(String.valueOf(symbol.getTextColor()), 600))
                                    .put("text_field", shortText(String.valueOf(symbol.getTextField()), 600))
                                    .put("text_font", shortText(String.valueOf(symbol.getTextFont()), 300))
                                    .put("symbol_placement", shortText(String.valueOf(symbol.getSymbolPlacement()), 120))
                                    .put("text_opacity", shortText(String.valueOf(symbol.getTextOpacity()), 600))
                                    .put("icon_opacity", shortText(String.valueOf(symbol.getIconOpacity()), 600))
                                    .put("icon_image", shortText(String.valueOf(symbol.getIconImage()), 600))
                                    .put("icon_size", shortText(String.valueOf(symbol.getIconSize()), 600))
                                    .put("icon_allow_overlap", String.valueOf(symbol.getIconAllowOverlap()))
                                    .put("icon_ignore_placement", String.valueOf(symbol.getIconIgnorePlacement()));
                        }
                        List<org.maplibre.geojson.Feature> hits = map.getMaplibreMap().queryRenderedFeatures(
                                new RectF(0, 0, map.getMaplibreMapView().getWidth(), map.getMaplibreMapView().getHeight()), child.getId());
                        JSONObject geometryCounts = new JSONObject(), paintCounts = new JSONObject(); Set<String> ids = new HashSet<>();
                        for (org.maplibre.geojson.Feature feature : hits) {
                            String id = property(feature, MPLFeaturesUtils.prop_featureid);
                            String geometry = feature.geometry() == null ? "null" : feature.geometry().type();
                            String paint = property(feature, MplFeatureStyleProps.COLOR_FILL_RULE) + "/" + property(feature, MplFeatureStyleProps.COLOR_FILL);
                            geometryCounts.put(geometry, geometryCounts.optInt(geometry) + 1);
                            paintCounts.put(paint, paintCounts.optInt(paint) + 1); ids.add(id);
                            String imageName = property(feature, MplFeatureStyleProps.ICON_IMAGE);
                            if (imageName.startsWith("sqlite-qa-cyan-") && imageNames.size() < 4 && imageNames.add(imageName)) {
                                JSONObject imageState = new JSONObject().put("name", imageName); images.put(imageState);
                                try {
                                    Bitmap image = style.getImage(imageName);
                                    imageState.put("present", image != null);
                                    if (image != null) imageState.put("width", image.getWidth()).put("height", image.getHeight())
                                            .put("density", image.getDensity()).put("has_alpha", image.hasAlpha())
                                            .put("center_argb", String.format(java.util.Locale.ROOT, "%08X",
                                                    image.getPixel(image.getWidth() / 2, image.getHeight() / 2)));
                                } catch (Exception error) { imageState.put("error_type", error.getClass().getSimpleName()); }
                            }
                            boolean selected = id.equals("1") || id.equals("2") || expectedRows == 3 && id.equals("3")
                                    || id.equals("31") || id.equals("62") || id.equals("99");
                            if (rows.length() < 256 || selected && focus.length() < 48) {
                                JSONObject row = new JSONObject().put("layer", shortText(child.getId(), 120)).put("id", id).put("geometry", geometry);
                                JSONObject props = new JSONObject();
                                // The focused five objects retain all bounded scalar properties, including any unexpected marker.
                                if (feature.properties() != null) for (Map.Entry<String, com.google.gson.JsonElement> entry : feature.properties().entrySet()) {
                                    if (props.length() >= (selected ? 48 : 8)) break;
                                    if (selected || entry.getKey().equals(MplFeatureStyleProps.COLOR_FILL_RULE)
                                            || entry.getKey().equals(MplFeatureStyleProps.COLOR_FILL)
                                            || entry.getKey().equals(MplFeatureStyleProps.SIGNATURE)
                                            || entry.getKey().equals(MplFeatureStyleProps.TEXT_COLOR)
                                            || entry.getKey().equals("_label_anchor"))
                                        props.put(shortText(entry.getKey(), 80), property(feature, entry.getKey()));
                                }
                                row.put("properties", props);
                                if (rows.length() < 256) rows.put(row);
                                if (selected && focus.length() < 48) focus.put(row);
                            }
                        }
                        item.put("hits", hits.size()).put("distinct_ids", ids.size()).put("duplicate_hits", hits.size() - ids.size())
                                .put("geometry_counts", geometryCounts).put("paint_counts", paintCounts); layers.put(item);
                    }
                    state.put("raw_rows", rows).put("focus_rows", focus);
                    JSONArray briefFocus = new JSONArray();
                    for (int i = 0; i < focus.length() && i < 24; i++) {
                        JSONObject row = focus.getJSONObject(i), props = row.getJSONObject("properties");
                        briefFocus.put(new JSONObject().put("layer", row.get("layer")).put("id", row.get("id"))
                                .put("geometry", row.get("geometry")).put("fillcolor", props.opt("fillcolor"))
                                .put("colorfill", props.opt("colorfill")).put("signature", props.opt("signature"))
                                .put("anchor", props.opt("_label_anchor")));
                    }
                    summary.append("source=").append(source == null ? "absent" : source.getClass().getSimpleName())
                            .append("; focus=").append(briefFocus).append("; layers=").append(layers);
                } catch (Exception error) { throw new IllegalStateException("Native witness snapshot failed", error); }
            });
            long[] focusIds = expectedRows == 3 ? new long[]{1, 2, 3} : new long[]{31, 62, 99};
            JSONArray backend = backendOriginalFocus(f, layer, focusIds);
            JSONArray focusIdsJson = new JSONArray(); for (long id : focusIds) focusIdsJson.put(id);
            state.put("backend_original_focus", backend).put("backend_original_focus_ids", focusIdsJson)
                    .put("backend_pbf_optional_focus_ids", "legacy_31_62_99_type_counts_cover_all_records");
            summary.insert(0, "backend_original_focus=" + backend + "; ");
            File directory = new File(f.app.getFilesDir(), "native-sqlite-qa");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Cannot create native SQLite evidence directory");
            // Preserve the failed native image before diagnostic backend IO can populate derived caches.
            try { state.put("snapshot_pixels", saveFailureSnapshot(f, directory)); state.put("png_captured_uptime_ms", SystemClock.uptimeMillis()); }
            catch (Exception | AssertionError error) { state.put("png_error_type", error.getClass().getSimpleName()); }
            JSONArray tiles = backendTileEvidence(capturedProvider.get(), capturedCamera.get());
            state.put("backend_pbf_tiles", tiles);
            JSONArray briefTiles = new JSONArray();
            for (int i = 0; i < tiles.length(); i++) {
                JSONObject tile = tiles.getJSONObject(i), item = new JSONObject();
                for (String key : new String[]{"z", "x", "y", "bytes", "source_epoch", "error_type"})
                    if (tile.has(key)) item.put(key, tile.get(key));
                JSONObject decoded = tile.optJSONObject("summary");
                if (decoded != null) {
                    item.put("status", decoded.opt("status"));
                    JSONArray counts = new JSONArray(), decodedLayers = decoded.optJSONArray("layers");
                    if (decodedLayers != null) for (int j = 0; j < decodedLayers.length(); j++)
                        counts.put(decodedLayers.getJSONObject(j).opt("type_counts"));
                    item.put("type_counts", counts);
                }
                briefTiles.put(item);
            }
            summary.insert(0, "backend_pbf_tiles=" + briefTiles + "; ");
            // Persist the original state/image/PBF before changing any diagnostic layout property.
            writeFailureState(state, rows, focus, tiles, directory, false);
            JSONObject probe = literalOffsetProbe(f, layer, capturedProvider.get());
            state.put("literal_offset_probe", probe);
            summary.insert(0, "literal_offset_probe=" + probe + "; ");
            writeFailureState(state, rows, focus, tiles, directory, true);
        } catch (Exception | AssertionError witnessFailure) {
            summary.append("; diagnostic_failure=").append(witnessFailure.getClass().getSimpleName())
                    .append(":").append(shortText(String.valueOf(witnessFailure.getMessage()), 300));
        }
        return shortText(summary.toString(), 10000) + "; files/native-sqlite-qa/failure.json + failure.png (failure-only, before fixture cleanup)";
    }

    private static void writeFailureState(JSONObject state, JSONArray rows, JSONArray focus, JSONArray tiles,
                                          File directory, boolean preserveOriginal) throws Exception {
        // Initial evidence may trim optional detail; a probe append may not erase original witnesses.
        byte[] encoded = state.toString().getBytes(StandardCharsets.UTF_8);
        if (!preserveOriginal) {
            while (encoded.length > 64 * 1024 && rows.length() > 0) {
                rows.remove(rows.length() - 1); state.put("raw_rows_truncated", true);
                encoded = state.toString().getBytes(StandardCharsets.UTF_8);
            }
            while (encoded.length > 64 * 1024 && focus.length() > 5) {
                focus.remove(focus.length() - 1); state.put("focus_rows_truncated", true);
                encoded = state.toString().getBytes(StandardCharsets.UTF_8);
            }
            while (encoded.length > 64 * 1024 && trimBackendPbfFocus(tiles)) {
                state.put("backend_pbf_focus_truncated", true);
                encoded = state.toString().getBytes(StandardCharsets.UTF_8);
            }
        }
        if (encoded.length > 64 * 1024) throw new java.io.IOException("Native SQLite diagnostic JSON exceeded64KiB");
        File target = new File(directory, "failure.json");
        if (!preserveOriginal) {
            try (FileOutputStream output = new FileOutputStream(target)) { output.write(encoded); }
            return;
        }
        File pending = new File(directory, "failure.json.pending");
        try (FileOutputStream output = new FileOutputStream(pending)) { output.write(encoded); }
        // A failed diagnostic append must leave the already saved original JSON readable.
        java.nio.file.Files.move(pending.toPath(), target.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static String offsetIdentity(PropertyValue<Float[]> property) {
        if (property.isExpression()) return "expression:" + property.getExpression();
        Float[] value = property.getValue();
        return value == null ? "undefined" : "literal:" + java.util.Arrays.toString(value);
    }

    private static PropertyValue<?> offsetRestore(String name, PropertyValue<Float[]> property) {
        Object value = property.isExpression() ? property.getExpression()
                : property.getValue() == null ? null : property.getValue().clone();
        return new LayoutPropertyValue<>(name, value);
    }

    private static final class SymbolOffsetRestore {
        final SymbolLayer symbol;
        final PropertyValue<?> text, icon;
        final String textIdentity, iconIdentity;
        SymbolOffsetRestore(SymbolLayer symbol) {
            this.symbol = symbol;
            PropertyValue<Float[]> textValue = symbol.getTextOffset(), iconValue = symbol.getIconOffset();
            text = offsetRestore("text-offset", textValue); icon = offsetRestore("icon-offset", iconValue);
            textIdentity = offsetIdentity(textValue); iconIdentity = offsetIdentity(iconValue);
        }
    }

    private static final class OffsetProbeScene {
        final MapDrawable map;
        final org.maplibre.android.maps.MapLibreMap sdk;
        final org.maplibre.android.maps.Style style;
        final Source source;
        final String sourceId;
        final CameraPosition camera;
        final List<SymbolOffsetRestore> offsets = new ArrayList<>();
        OffsetProbeScene(MapDrawable map, String sourceId) {
            this.map = map; this.sourceId = sourceId; sdk = map.getMaplibreMap();
            style = sdk.getStyle(); source = style.getSource(sourceId); camera = sdk.getCameraPosition();
        }
        boolean ownerCurrent(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, SqliteVectorTileProvider provider) {
            return f.app.getMap() == map && map.getMaplibreMap() == sdk && sdk.getStyle() == style
                    && source != null && style.getSource(sourceId) != null
                    && sourceId.equals(map.getVectorRenderSourceId(layer.getId()))
                    && provider != null && provider == map.getSqliteVectorProvider(layer.getId()) && provider.isCurrent();
        }
        boolean current(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, SqliteVectorTileProvider provider) {
            return ownerCurrent(f, layer, provider) && camera.equals(sdk.getCameraPosition());
        }
    }

    /** Failure-only discriminator. Its observations never replace the original assertion or image. */
    private static JSONObject literalOffsetProbe(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer,
                                                 SqliteVectorTileProvider provider) throws Exception {
        JSONObject result = new JSONObject().put("stage", "after_original_evidence_literal_offsets")
                .put("original_result", "failed").put("status", "not_started")
                .put("text_literal", new JSONArray().put(0).put(-2)).put("icon_literal", new JSONArray().put(0).put(0));
        JSONArray samples = new JSONArray(), errors = new JSONArray(); result.put("samples", samples).put("errors", errors);
        AtomicReference<OffsetProbeScene> captured = new AtomicReference<>(); long started = SystemClock.uptimeMillis();
        try {
            f.screen.onActivity(activity -> {
                MapDrawable map = (MapDrawable) f.app.getMap(); String id = map.getVectorRenderSourceId(layer.getId());
                if (id == null || map.getMaplibreMap() == null || map.getMaplibreMap().getStyle() == null)
                    throw new IllegalStateException("Probe source unavailable");
                OffsetProbeScene scene = new OffsetProbeScene(map, id); captured.set(scene);
                if (!scene.current(f, layer, provider)) throw new IllegalStateException("Probe owner changed");
                for (org.maplibre.android.style.layers.Layer child : scene.style.getLayers())
                    if (child instanceof SymbolLayer && id.equals(((SymbolLayer) child).getSourceId())) {
                        if (scene.offsets.size() >= 16) throw new IllegalStateException("Probe symbol limit exceeded");
                        scene.offsets.add(new SymbolOffsetRestore((SymbolLayer) child));
                    }
                // Capture every exact original before applying the first temporary property.
                for (SymbolOffsetRestore saved : scene.offsets) saved.symbol.setProperties(
                        PropertyFactory.iconOffset(new Float[]{0f, 0f}), PropertyFactory.textOffset(new Float[]{0f, -2f}));
                for (SymbolOffsetRestore saved : scene.offsets)
                    if (!"literal:[0.0, -2.0]".equals(offsetIdentity(saved.symbol.getTextOffset()))
                            || !"literal:[0.0, 0.0]".equals(offsetIdentity(saved.symbol.getIconOffset())))
                        throw new IllegalStateException("Probe literal properties rejected");
                scene.sdk.triggerRepaint();
            });
            OffsetProbeScene scene = captured.get(); result.put("symbol_layers", scene.offsets.size());
            if (scene.offsets.isEmpty()) { result.put("status", "no_symbol_layers"); return result; }
            for (int sample = 0; sample < 2; sample++) {
                Thread.sleep(sample == 0 ? 250 : 750);
                f.screen.onActivity(activity -> {
                    if (!scene.current(f, layer, provider)) throw new IllegalStateException("Probe scene changed");
                });
                Bitmap image = f.snapshot();
                try {
                    f.screen.onActivity(activity -> {
                        if (!scene.current(f, layer, provider)) throw new IllegalStateException("Probe snapshot scene changed");
                    });
                    samples.put(snapshotColorCounts(image).put("sample", sample + 1)
                            .put("elapsed_ms", SystemClock.uptimeMillis() - started));
                }
                finally { image.recycle(); }
            }
            result.put("status", "observed").put("same_scene", true);
        } catch (Exception | AssertionError error) {
            result.put("status", "diagnostic_failed"); errors.put(error.getClass().getSimpleName());
        } finally {
            OffsetProbeScene scene = captured.get(); JSONObject restore = new JSONObject(); result.put("restore", restore);
            long restoreStarted = SystemClock.uptimeMillis();
            if (scene != null) try {
                f.screen.onActivity(activity -> {
                    int restored = 0, properties = 0, mismatches = 0;
                    try {
                        if (!scene.ownerCurrent(f, layer, provider)) { restore.put("status", "owner_changed"); return; }
                        // A camera move invalidates the probe comparison, but not our obligation to restore layouts.
                        restore.put("camera_unchanged", scene.camera.equals(scene.sdk.getCameraPosition()));
                        for (SymbolOffsetRestore saved : scene.offsets) {
                            try {
                                org.maplibre.android.style.layers.Layer current = scene.style.getLayer(saved.symbol.getId());
                                if (!(current instanceof SymbolLayer)
                                        || !scene.sourceId.equals(((SymbolLayer) current).getSourceId())) {
                                    mismatches += 2; continue;
                                }
                                SymbolLayer symbol = (SymbolLayer) current;
                                symbol.setProperties(saved.text, saved.icon);
                                boolean text = saved.textIdentity.equals(offsetIdentity(symbol.getTextOffset()));
                                boolean icon = saved.iconIdentity.equals(offsetIdentity(symbol.getIconOffset()));
                                properties += (text ? 1 : 0) + (icon ? 1 : 0);
                                mismatches += (text ? 0 : 1) + (icon ? 0 : 1); if (text && icon) restored++;
                            } catch (Exception | AssertionError error) {
                                if (errors.length() < 18) errors.put(error.getClass().getSimpleName()); mismatches += 2;
                            }
                        }
                        restore.put("status", mismatches == 0 ? "exact_properties_restored" : "restore_incomplete")
                                .put("layers", restored).put("properties", properties).put("mismatches", mismatches)
                                .put("expected_properties", scene.offsets.size() * 2);
                        scene.sdk.triggerRepaint();
                    } catch (Exception error) { if (errors.length() < 18) errors.put(error.getClass().getSimpleName()); }
                });
            } catch (Exception | AssertionError error) {
                restore.put("status", "diagnostic_failed"); if (errors.length() < 18) errors.put(error.getClass().getSimpleName());
            }
            else restore.put("status", "nothing_captured");
            if (scene != null && "exact_properties_restored".equals(restore.optString("status"))) try {
                Thread.sleep(250);
                f.screen.onActivity(activity -> {
                    if (!scene.current(f, layer, provider)) throw new IllegalStateException("Restore scene changed");
                });
                Bitmap image = f.snapshot();
                try {
                    f.screen.onActivity(activity -> {
                        if (!scene.current(f, layer, provider)) throw new IllegalStateException("Restore snapshot scene changed");
                    });
                    restore.put("snapshot_pixels", snapshotColorCounts(image));
                } finally { image.recycle(); }
            } catch (Exception | AssertionError error) {
                restore.put("snapshot_error_type", error.getClass().getSimpleName());
            }
            restore.put("elapsed_ms", SystemClock.uptimeMillis() - restoreStarted);
            result.put("elapsed_ms", SystemClock.uptimeMillis() - started);
        }
        return result;
    }

    private static JSONObject snapshotColorCounts(Bitmap image) throws Exception {
        long cyan = 0, blue = 0, magenta = 0, red = 0, green = 0;
        int[] row = new int[image.getWidth()];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getPixels(row, 0, row.length, 0, y, row.length, 1);
            for (int color : row) {
                int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                if (r < 40 && g > 200 && b > 200) cyan++;
                if (r < 40 && g < 40 && b > 200) blue++;
                if (r > 200 && g < 40 && b > 200) magenta++;
                if (r > 200 && g < 40 && b < 40) red++;
                if (r < 40 && g > 200 && b < 40) green++;
            }
        }
        return new JSONObject().put("width", image.getWidth()).put("height", image.getHeight())
                .put("cyan", cyan).put("blue", blue).put("magenta", magenta).put("red", red).put("green", green);
    }

    private static JSONObject saveFailureSnapshot(VectorStyleRenderingTest.Fixture f, File directory) throws Exception {
        Bitmap original = f.snapshot();
        try {
            // Preserve the actual oracle resolution: downscaling can erase a tiny PNG symbol.
            // The disk output keeps the collector's2MiB limit without an encoded-image RAM copy.
            File target = new File(directory, "failure.png");
            try (FileOutputStream file = new FileOutputStream(target)) {
                java.io.OutputStream capped = new java.io.OutputStream() {
                    private int written;
                    private void reserve(int length) throws java.io.IOException {
                        if (length < 0 || length > 2 * 1024 * 1024 - written)
                            throw new java.io.IOException("Native SQLite PNG exceeded2MiB");
                        written += length;
                    }
                    @Override public void write(int value) throws java.io.IOException { reserve(1); file.write(value); }
                    @Override public void write(byte[] bytes, int offset, int length) throws java.io.IOException {
                        reserve(length); file.write(bytes, offset, length);
                    }
                };
                if (!original.compress(Bitmap.CompressFormat.PNG, 100, capped)) throw new java.io.IOException("Cannot encode native SQLite snapshot");
            }
            return snapshotColorCounts(original).put("full_resolution", true).put("encoded_bytes", target.length());
        } finally { original.recycle(); }
    }

    private static JSONArray backendTileEvidence(SqliteVectorTileProvider provider, double[] camera) throws Exception {
        JSONArray result = new JSONArray();
        if (provider == null || camera == null) return result.put(new JSONObject().put("error_type", "provider_or_camera_unavailable"));
        int z = Math.max(0, Math.min(18, (int) Math.floor(camera[0]))), count = 1 << z;
        double longitude = ((camera[1] + 180) % 360 + 360) % 360 - 180;
        double latitude = Math.toRadians(Math.max(-85.0511287798066, Math.min(85.0511287798066, camera[2])));
        double fx = (longitude + 180) / 360 * count;
        double fy = (1 - Math.log(Math.tan(latitude) + 1 / Math.cos(latitude)) / Math.PI) / 2 * count;
        int x = Math.min(count - 1, Math.max(0, (int) Math.floor(fx))), y = Math.min(count - 1, Math.max(0, (int) Math.floor(fy)));
        int nx = Math.floorMod(x + (fx - Math.floor(fx) < .5 ? -1 : 1), count);
        int ny = Math.max(0, Math.min(count - 1, y + (fy - Math.floor(fy) < .5 ? -1 : 1)));
        Set<String> seen = new HashSet<>();
        for (int[] xy : new int[][]{{x, y}, {nx, y}, {x, ny}, {nx, ny}}) {
            if (!seen.add(xy[0] + "/" + xy[1])) continue;
            JSONObject row = new JSONObject().put("z", z).put("x", xy[0]).put("y", xy[1])
                    .put("capture_started_uptime_ms", SystemClock.uptimeMillis());
            result.put(row);
            try {
                SqliteVectorTileRequest request = provider.snapshot(z, xy[0], xy[1]);
                row.put("source_epoch", request.getSourceEpoch()).put("content_version", request.getContentVersion());
                byte[] bytes = provider.load(request, () -> true);
                row.put("bytes", bytes.length);
                if (bytes.length > 4 * 1024 * 1024) row.put("error_type", "tile_exceeds_4MiB");
                else row.put("summary", SqliteMvtEvidence.summarize(bytes));
            } catch (Exception error) { row.put("error_type", error.getClass().getSimpleName()).put("error_stage", "backend_tile_load"); }
            row.put("capture_finished_uptime_ms", SystemClock.uptimeMillis());
        }
        return result;
    }

    private static boolean trimBackendPbfFocus(JSONArray tiles) throws Exception {
        for (int i = tiles.length() - 1; i >= 0; i--) {
            JSONObject summary = tiles.getJSONObject(i).optJSONObject("summary");
            JSONArray layers = summary == null ? null : summary.optJSONArray("layers");
            if (layers == null) continue;
            for (int j = layers.length() - 1; j >= 0; j--) {
                JSONArray focus = layers.getJSONObject(j).optJSONArray("focus_records");
                if (focus != null && focus.length() > 0) {
                    focus.remove(focus.length() - 1); summary.put("focus_details_omitted", summary.optInt("focus_details_omitted") + 1);
                    return true;
                }
            }
        }
        return false;
    }

    private static JSONArray backendOriginalFocus(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) throws Exception {
        return backendOriginalFocus(f, layer, new long[]{31, 62, 99});
    }

    private static JSONArray backendOriginalFocus(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, long[] focusIds) throws Exception {
        SqliteVectorTileProvider current = provider(f, layer);
        JSONArray result = new JSONArray();
        // Read rich originals on the instrumentation/IO thread, never inside the native SDK callback.
        for (long id : focusIds) {
            JSONObject row = new JSONObject().put("id", id);
            result.put(row);
            try {
                if (current == null) { row.put("error_type", "provider_unavailable"); continue; }
                org.maplibre.geojson.Feature original = current.readOriginalFeature(id);
                if (original == null) { row.put("error_type", "original_unavailable"); continue; }
                row.put("provider_current", current.isCurrent())
                        .put("feature_id", property(original, MPLFeaturesUtils.prop_featureid))
                        .put("fillcolor", property(original, MplFeatureStyleProps.COLOR_FILL_RULE))
                        .put("colorfill", property(original, MplFeatureStyleProps.COLOR_FILL))
                        .put("opacity", property(original, MplFeatureStyleProps.OPACITY))
                        .put("strokeopacity", property(original, MplFeatureStyleProps.STROKE_OPACITY))
                        .put("signature", property(original, MplFeatureStyleProps.SIGNATURE))
                        .put("textcolor", property(original, MplFeatureStyleProps.TEXT_COLOR));
            } catch (Exception error) {
                // Fixed stage/type only: do not copy SQL, geometry or coordinates from an exception.
                row.put("error_type", error.getClass().getSimpleName()).put("error_stage", "read_original_feature");
            }
        }
        return result;
    }

    private static void assertCurrentBackendStyleFocus(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer) throws Exception {
        try {
            JSONArray actual = backendOriginalFocus(f, layer);
            assertEquals(3, actual.length());
            for (int i = 0; i < actual.length(); i++) {
                JSONObject row = actual.getJSONObject(i); long id = row.getLong("id");
                assertFalse("Current backend mapper failed before SDK id=" + id + ": " + row, row.has("error_type"));
                assertTrue("Current backend generation id=" + id, row.getBoolean("provider_current"));
                assertEquals(Long.toString(id), row.getString("feature_id"));
                assertEquals("Current backend paint before SDK id=" + id, id == 31 ? "#00FF00" : "#FF0000",
                        row.getString("fillcolor").toUpperCase(java.util.Locale.ROOT));
                assertEquals(LABELS.get(id), row.getString("signature"));
            }
        } catch (AssertionError | Exception original) {
            throw new AssertionError(original.getMessage() + "\nSQLite native witness: " + failureEvidence(f, layer, true), original);
        }
    }

    private static String property(org.maplibre.geojson.Feature feature, String key) {
        com.google.gson.JsonElement value = feature.properties() == null ? null : feature.properties().get(key);
        if (value == null || value.isJsonNull()) return "<absent>";
        return shortText(value.isJsonPrimitive() ? value.getAsString() : value.toString(), 160);
    }

    private static String shortText(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit) + "[truncated]";
    }

    private static void assertFillPixels(VectorStyleRenderingTest.Fixture f, boolean reversed) throws Exception {
        Map<Long, PointF> centers = new LinkedHashMap<>();
        f.screen.onActivity(activity -> {
            MapDrawable map = (MapDrawable) f.app.getMap();
            // A, NULL and Other examples away from glyphs and polygon boundaries.
            for (long id : new long[]{55, 56, 57}) {
                int index = (int) id - 1;
                centers.put(id, map.getMaplibreMap().getProjection().toScreenLocation(latLng(-288 + index % 13 * 48, -216 + index / 13 * 48)));
            }
        });
        Bitmap image = f.snapshot();
        try {
            for (Map.Entry<Long, PointF> center : centers.entrySet()) {
                int x = Math.round(center.getValue().x), y = Math.round(center.getValue().y);
                assertTrue("Polygon sample must lie inside the snapshot", x > 0 && y > 0 && x < image.getWidth() - 1 && y < image.getHeight() - 1);
                boolean red = (center.getKey() % 3 == 1) != reversed; int matches = 0;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int color = image.getPixel(x + dx, y + dy);
                    if (red ? Color.red(color) > 200 && Color.green(color) < 40 && Color.blue(color) < 40
                            : Color.green(color) > 200 && Color.red(color) < 40 && Color.blue(color) < 40) matches++;
                }
                assertTrue("Independent category fill pixels for SQL id=" + center.getKey(), matches >= 5);
            }
        } finally { image.recycle(); }
    }

    private static void assertOriginals(SqliteVectorTileProvider provider, Corpus corpus) throws Exception {
        assertNotNull(provider);
        for (long id = 1; id <= ROWS; id++) {
            Feature original = provider.loadOriginalFeature(id); assertNotNull("Missing original SQL id=" + id, original);
            assertEquals(id, original.getId()); assertEquals(LABELS.getOrDefault(id, ""), original.getFieldValueAsString("name"));
            assertEquals(id % 3 == 1 ? " a " : id % 3 == 2 ? null : "unmatched", original.getFieldValue("kind"));
            assertTrue(original.getGeometry() instanceof GeoPolygon);
            GeoLinearRing ring = ((GeoPolygon) original.getGeometry()).getOuterRing();
            assertEquals("Original must never be tile clipping/quantization", VERTICES + 1, ring.getPointCount());
            double[][] expected = corpus.coordinates.get(id);
            for (int vertex = 0; vertex <= VERTICES; vertex++) {
                assertEquals("Original x id=" + id + " vertex=" + vertex, expected[vertex][0], ring.getPoint(vertex).getX(), 1e-9);
                assertEquals("Original y id=" + id + " vertex=" + vertex, expected[vertex][1], ring.getPoint(vertex).getY(), 1e-9);
            }
        }
    }

    private static void awaitReplacement(VectorStyleRenderingTest.Fixture f, VectorLayerUI layer, SqliteVectorTileProvider old) throws Exception {
        await("Reload must replace its obsolete SQL source generation", () -> {
            SqliteVectorTileProvider next = provider(f, layer); return next != null && next != old && next.isCurrent();
        });
    }

    private static void assertRetired(SqliteVectorTileProvider old, SqliteVectorTileRequest request) throws Exception {
        assertFalse("Retired provider cannot remain current", old.isCurrent());
        assertFalse("A late tile carries an obsolete generation", old.isCurrent(request));
        try { old.load(request, () -> true); fail("An obsolete native request must never succeed or populate its cache"); }
        catch (CancellationException expected) { /* Production cancellation, not a mock backend. */ }
    }

    private static byte[] contents(VectorLayerUI layer) throws Exception {
        return contents(layer, ROWS);
    }

    private static byte[] contents(VectorLayerUI layer, int expectedRows) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (Cursor rows = layer.query(null, null, null, "_id", null)) {
            assertNotNull(rows); assertEquals(expectedRows, rows.getCount());
            for (String column : rows.getColumnNames()) hashPart(hash, column.getBytes(StandardCharsets.UTF_8));
            while (rows.moveToNext()) for (int column = 0; column < rows.getColumnCount(); column++) {
                int type = rows.getType(column); hash.update((byte) type);
                switch (type) {
                    case Cursor.FIELD_TYPE_NULL: hashPart(hash, null); break;
                    case Cursor.FIELD_TYPE_INTEGER: hashPart(hash, ByteBuffer.allocate(8).putLong(rows.getLong(column)).array()); break;
                    case Cursor.FIELD_TYPE_FLOAT: hashPart(hash, ByteBuffer.allocate(8).putLong(Double.doubleToRawLongBits(rows.getDouble(column))).array()); break;
                    case Cursor.FIELD_TYPE_BLOB: hashPart(hash, rows.getBlob(column)); break;
                    default: hashPart(hash, rows.getString(column).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        return hash.digest();
    }

    private static void hashPart(MessageDigest hash, byte[] value) {
        hash.update(ByteBuffer.allocate(4).putInt(value == null ? -1 : value.length).array());
        if (value != null) hash.update(value);
    }

    private static void await(String message, BooleanSupplier condition) throws Exception {
        long end = SystemClock.uptimeMillis() + 30000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(80);
        assertTrue(message, condition.getAsBoolean());
    }
}
