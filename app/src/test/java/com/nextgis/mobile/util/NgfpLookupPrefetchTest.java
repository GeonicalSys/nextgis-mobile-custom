package com.nextgis.mobile.util;

import android.app.Application;
import com.nextgis.maplibui.util.LayerUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Actual prefetch and legacy reader entry points; technical JSON files only, no map/DB/network/UI. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class)
public class NgfpLookupPrefetchTest {
    private static JSONObject combo(Object id, boolean autocomplete) throws Exception {
        return new JSONObject().put("type", "combobox").put("attributes", new JSONObject()
                .put("field", "technical_lookup").put("last", false)
                .put("input_search", autocomplete).put("ngw_id", id).put("values", new JSONArray()));
    }
    private static ArrayList<String> actual(Object form) throws Exception {
        File file=Files.createTempFile("ngfp-prefetch-technical-", ".json").toFile();
        try {
            Files.write(file.toPath(),form.toString().getBytes(StandardCharsets.UTF_8));
            java.lang.reflect.Method entry;
            try { entry=LayerUtil.class.getMethod("fillLookupTableIdsForPrefetch",File.class); }
            catch (NoSuchMethodException oldE66) { return LayerUtil.fillLookupTableIds(file); }
            try { return (ArrayList<String>)entry.invoke(null,file); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                Throwable cause=failure.getCause();
                if (cause instanceof Error) throw (Error)cause;
                if (cause instanceof Exception) throw (Exception)cause;
                throw new AssertionError(cause);
            }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    @Test public void topLevelLookupStillUsesRemoteId() throws Exception {
        assertEquals(Arrays.asList("16"), actual(new JSONArray().put(combo(16, false))));
    }
    @Test public void autocompleteUsesSameResourceBindingAndNumericString() throws Exception {
        assertEquals(Arrays.asList("16"), actual(new JSONArray().put(combo("16", true))));
    }
    @Test public void supportedTabsPageRequiresItsLookupBeforeControlsOpen() throws Exception {
        JSONObject page=new JSONObject().put("caption", "Technical page").put("default", true)
                .put("elements", new JSONArray().put(combo(16, false)));
        JSONObject tabs=new JSONObject().put("type", "tabs").put("pages", new JSONArray().put(page));
        // Oracle comes from actual FormBuilder/Tabs control grammar, not helper's current scan.
        assertEquals(Arrays.asList("16"), actual(new JSONArray().put(tabs)));
    }
    @Test public void bothDeclaredOrientationLayoutsRequireTheirOwnLookups() throws Exception {
        JSONObject tab=new JSONObject().put("album_elements", new JSONArray().put(combo(16, false)))
                .put("portrait_elements", new JSONArray().put(combo(17, true)));
        Object form=new JSONObject().put("tabs", new JSONArray().put(tab));
        ArrayList<String> ids;
        try { ids=actual(form); }
        catch (JSONException error) { fail("UI-supported two-orientation layout must not be rejected as non-array");return; }
        assertEquals(2,ids.size());
        assertEquals(new HashSet<>(Arrays.asList("16","17")),new HashSet<>(ids));
    }
    @Test public void unrelatedNumbersAndSameNamedKeysAreNotLookupBindings() throws Exception {
        JSONObject text=new JSONObject().put("type","text_edit").put("attributes",
                new JSONObject().put("field","technical16").put("max_string_count",16)
                        .put("ngw_id",16).put("text","16"));
        assertTrue(actual(new JSONArray().put(text)).isEmpty());
    }
    @Test public void sentinelAndInlineChoicesKeepExistingMeaning() throws Exception {
        JSONObject inline=combo(-1,false);inline.getJSONObject("attributes").remove("ngw_id");
        assertTrue(actual(new JSONArray().put(combo(-1,false)).put(inline)).isEmpty());
    }
    @Test public void malformedPresentReferenceDoesNotBecomeEmptySuccess() throws Exception {
        try { actual(new JSONArray().put(combo(JSONObject.NULL,false)));fail("Present null is malformed, not a missing binding"); }
        catch (JSONException expected) { }
    }

    @Test public void repeatedPlatformNormalizedIdsArePrefetchedOnceInFirstAppearanceOrder() throws Exception {
        assertEquals(Arrays.asList("16","17"),actual(new JSONArray().put(combo("16",false))
                .put(combo(16,true)).put(combo(17,false)).put(combo("17",false))));
    }
    // The public legacy API and both destructive production callers remain exactly E66.
    private static ArrayList<String> legacyActual(Object form) throws Exception {
        File file=Files.createTempFile("ngfp-legacy-delete-technical-", ".json").toFile();
        try {
            Files.write(file.toPath(),form.toString().getBytes(StandardCharsets.UTF_8));
            return LayerUtil.fillLookupTableIds(file);
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    @Test public void legacyDeleteReaderDoesNotExpandNestedReferences() throws Exception {
        JSONObject page=new JSONObject().put("elements",new JSONArray().put(combo(16,false)));
        JSONObject tabs=new JSONObject().put("type","tabs").put("pages",new JSONArray().put(page));
        assertTrue(legacyActual(new JSONArray().put(tabs)).isEmpty());
    }
    @Test public void legacyDeleteReaderStillRejectsRootOrientationObject() throws Exception {
        JSONObject tab=new JSONObject().put("album_elements",new JSONArray().put(combo(16,false)));
        try { legacyActual(new JSONObject().put("tabs",new JSONArray().put(tab)));fail("Legacy destructive reader must keep E66 non-array refusal"); }
        catch (JSONException expected) { }
    }
    @Test public void legacyDeleteReaderKeepsDuplicatesSentinelAndNegativePolicy() throws Exception {
        assertEquals(Arrays.asList("16","16","-2"),legacyActual(new JSONArray()
                .put(combo(16,false)).put(combo("16",true)).put(combo(-1,false)).put(combo(-2,false))));
    }
    @Test public void legacyDeleteReaderKeepsMalformedPresentReferenceError() throws Exception {
        try { legacyActual(new JSONArray().put(combo(JSONObject.NULL,false)));fail("Malformed legacy reference must not become no refs"); }
        catch (JSONException expected) { }
    }
}
