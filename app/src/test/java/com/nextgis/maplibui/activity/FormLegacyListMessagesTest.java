package com.nextgis.maplibui.activity;

import android.app.Application;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import static com.nextgis.maplibui.util.ConstantsUI.KEY_META_PATH;
import static org.junit.Assert.*;

/** Actual legacy metadata/list error methods; no Activity lifecycle, map, SQLite or network. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28, manifest=Config.NONE, application=Application.class, qualifiers="ru")
public class FormLegacyListMessagesTest {
    public static class ProbeActivity extends FormBuilderModifyAttributesActivity {
        int controls;
        @Override protected void fillControls(LinearLayout layout, Bundle state) { controls++; }
    }
    private ProbeActivity activity;
    private File meta;
    @Before public void setUp() { activity=Robolectric.buildActivity(ProbeActivity.class).get(); ShadowToast.reset(); }
    @After public void cleanUp() throws Exception { if(meta!=null) Files.deleteIfExists(meta.toPath()); }
    private void fill(String json) throws Exception {
        meta=Files.createTempFile(new File(System.getProperty("java.io.tmpdir")).toPath(),
                "legacy-form-message-", ".json").toFile();
        Files.write(meta.toPath(),json.getBytes(StandardCharsets.UTF_8));
        Bundle extras=new Bundle();extras.putSerializable(KEY_META_PATH,meta);
        Method method=FormBuilderModifyAttributesActivity.class.getDeclaredMethod(
                "fillTable",LinearLayout.class,Bundle.class,Bundle.class);
        method.setAccessible(true);method.invoke(activity,null,null,extras);
    }
    private Object value(String key) throws Exception {
        Field field=FormBuilderModifyAttributesActivity.class.getDeclaredField(key);
        field.setAccessible(true);return field.get(activity);
    }
    @Test public void missingKeyListNamesTheFormListWithoutCodeLiteral() throws Exception {
        fill("{\"lists\":{},\"key_list\":\"selection\"}");
        assertEquals("Не удалось открыть список формы.",ShadowToast.getTextOfLatestToast());
        assertNull(value("mColumn"));assertEquals(1,activity.controls);
    }
    @Test public void obsoleteSavedRowExplainsTheMissingChoiceWithoutChangingReset() throws Exception {
        fill("{\"lists\":{\"selection\":[\"technical choice\"]},\"key_list\":\"selection\",\"key_list_saved\":5}");
        assertEquals("Ранее выбранная строка формы больше недоступна.",ShadowToast.getTextOfLatestToast());
        assertEquals(-1,value("mRow"));assertEquals(1,activity.controls);
    }
    @Test public void emptyAskListExplainsNoChoicesAndKeepsCallback() throws Exception {
        Field table=FormBuilderModifyAttributesActivity.class.getDeclaredField("mTable");
        table.setAccessible(true);table.set(activity,Collections.emptyMap());
        final int[] callbacks={0};
        Method ask=FormBuilderModifyAttributesActivity.class.getDeclaredMethod(
                "askForRow",FormBuilderModifyAttributesActivity.OnAskRowListener.class);
        ask.setAccessible(true);ask.invoke(activity,(FormBuilderModifyAttributesActivity.OnAskRowListener)()->callbacks[0]++);
        assertEquals("В списке формы нет доступных строк.",ShadowToast.getTextOfLatestToast());
        assertEquals(-1,value("mRow"));assertEquals(1,callbacks[0]);
    }
    @Test public void validSavedChoiceKeepsItsRowAndShowsNoError() throws Exception {
        fill("{\"lists\":{\"selection\":[\"technical choice\"]},\"key_list\":\"selection\",\"key_list_saved\":0}");
        assertNull(ShadowToast.getLatestToast());assertEquals(0,value("mRow"));
        assertEquals("selection",value("mColumn"));assertEquals(1,activity.controls);
    }
}
