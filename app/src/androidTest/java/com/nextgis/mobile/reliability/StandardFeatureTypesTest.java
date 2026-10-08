package com.nextgis.mobile.reliability;

import android.content.Intent;
import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.datasource.Field;
import com.nextgis.maplib.datasource.GeoPoint;
import com.nextgis.maplib.display.FieldStyleRule;
import com.nextgis.maplib.display.RuleFeatureRenderer;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.activity.FormBuilderModifyAttributesActivity;
import com.nextgis.maplibui.formcontrol.DoubleComboboxValue;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.maplibui.util.ConstantsUI;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.FeatureFormDraftStore;
import com.nextgis.maplibui.util.FeatureTypeDefaults;
import com.nextgis.maplibui.util.FeatureTypePreview;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Standard templates only: no phone data, accounts, geometry rows or NGW connection metadata. */
@RunWith(AndroidJUnit4.class)
public class StandardFeatureTypesTest {
    @Test public void standardPointLineAndPolygonCategoriesHaveSymbolsAndCanonicalParents() throws Exception {
        JSONArray fixtures;
        try (java.io.InputStream stream = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("feature-types/standard-field-types.json")) {
            fixtures = new JSONArray(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        }
        GISApplication app = (GISApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        MapContentProviderHelper map = (MapContentProviderHelper)app.getMap();
        assertNull("Run only on the isolated test emulator",FeatureFormDraftStore.load(app));
        int total = 0;
        for (int i = 0; i < fixtures.length(); i++) {
            JSONObject fixture = fixtures.getJSONObject(i);
            VectorLayerUI layer = new VectorLayerUI(app,new File(map.getPath(),"standard_types_"+UUID.randomUUID().toString().replace("-","")));
            map.addLayer(layer);
            try {
                ArrayList<Field> fields = new ArrayList<>();
                JSONArray schema = fixture.getJSONArray("fields");
                for (int j = 0; j < schema.length(); j++) {
                    Field field = new Field(); field.fromJSON(schema.getJSONObject(j)); fields.add(field);
                }
                layer.create(fixture.getInt("geometry_type"),fields); layer.setIsEditable(true);
                layer.setRenderer(fixture.getJSONObject("renderer_properties")); map.save();
                File form = new File(layer.getPath(),"form.json"), meta = new File(layer.getPath(),"ngfp_meta.json");
                JSONArray elements = fixture.getJSONArray("form");
                Files.write(form.toPath(),elements.toString().getBytes(StandardCharsets.UTF_8));
                Files.write(meta.toPath(),"{}".getBytes(StandardCharsets.UTF_8));
                Map<String,String> expectedParents = new LinkedHashMap<>();
                for (int j = 0; j < elements.length(); j++) {
                    JSONObject element = elements.getJSONObject(j);
                    if (!"double_combobox".equals(element.optString("type"))) continue;
                    JSONArray parents = element.getJSONObject("attributes").getJSONArray("values");
                    for (int p = 0; p < parents.length(); p++) {
                        JSONObject parent = parents.getJSONObject(p);
                        JSONArray children = parent.getJSONArray("values");
                        for (int c = 0; c < children.length(); c++) expectedParents.put(children.getJSONObject(c).getString("name"),parent.getString("name"));
                    }
                }
                FieldStyleRule rule = (FieldStyleRule)((RuleFeatureRenderer)layer.getRenderer()).getStyleRule();
                List<FeatureTypeDefaults.Choice> choices = FeatureTypeDefaults.choices(layer);
                assertEquals(fixture.getString("name"),rule.getStyleRules().size(),choices.size());
                for (FeatureTypeDefaults.Choice choice : choices) {
                    assertNotNull(choice.label,choice.state);
                    String type = choice.state.getString(ControlHelper.getSavedStateKey("typeobj"));
                    assertTrue(type,expectedParents.containsKey(type));
                    assertEquals(expectedParents.get(type),choice.state.getString(ControlHelper.getSavedStateKey("classobj")));
                    assertNotNull(FeatureTypePreview.render(app,choice.style));
                }
                total += choices.size();
                if (i == 0) checkPointForm(app,layer,form,meta,choices.get(0).state);
                assertEquals("Inspecting categories must not create objects",0,layer.getCount());
            } finally {
                FeatureFormDraftStore.clear(app); assertTrue(layer.delete(false)); map.save();
            }
        }
        assertEquals(40,total);
    }

    private static void checkPointForm(GISApplication app,VectorLayerUI layer,File form,File meta,Bundle initial) {
        GeoPoint point = new GeoPoint(100,200); point.setCRS(GeoConstants.CRS_WEB_MERCATOR);
        Intent intent = new Intent(app,FormBuilderModifyAttributesActivity.class)
                .putExtra(ConstantsUI.KEY_LAYER_ID,layer.getId()).putExtra(ConstantsUI.KEY_FEATURE_ID,Constants.NOT_FOUND)
                .putExtra(ConstantsUI.KEY_GEOMETRY,point).putExtra(ConstantsUI.KEY_FORM_PATH,form)
                .putExtra(ConstantsUI.KEY_META_PATH,meta).putExtra(FeatureTypeDefaults.INITIAL_VALUES,initial);
        try (ActivityScenario<FormBuilderModifyAttributesActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(a -> {
                try {
                    java.lang.reflect.Field fields = com.nextgis.maplibui.activity.ModifyAttributesActivity.class.getDeclaredField("mFields");
                    fields.setAccessible(true);
                    @SuppressWarnings("unchecked") Map<String,com.nextgis.maplibui.api.IControl> controls = (Map<String,com.nextgis.maplibui.api.IControl>)fields.get(a);
                    DoubleComboboxValue value = (DoubleComboboxValue)controls.get("classobj").getValue();
                    assertEquals(initial.getString(ControlHelper.getSavedStateKey("classobj")),value.mValue);
                    assertEquals(initial.getString(ControlHelper.getSavedStateKey("typeobj")),value.mSubValue);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        }
    }
}
