package com.nextgis.mobile.util;

import android.app.Application;
import android.app.Dialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.nextgis.maplib.util.HttpResponse;
import com.nextgis.maplibui.dialog.CreateFromQMSLayerDialog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class QmsCatalogDialogTest {
    private ActivityController<FragmentActivity> activity;
    private CatalogDialog dialog;

    @Before public void openDialog() {
        activity = Robolectric.buildActivity(FragmentActivity.class).setup();
        dialog = new CatalogDialog();
        dialog.showNow(activity.get().getSupportFragmentManager(), "qms");
    }

    @After public void closeActivity() {
        if (activity != null) activity.pause().stop().destroy();
    }

    @Test public void lateCatalogResponseAfterDismissalDoesNotBuildAnAdapter() throws Exception {
        Object pending = startSyntheticRequest();
        dialog.dismissNow();

        deliver(pending);

        assertNull(dialog.rows().getAdapter());
    }

    @Test public void activeRequestPublishesRowsAndAnOlderRequestCannotReplaceThem() throws Exception {
        Object older = startSyntheticRequest();
        Object current = startSyntheticRequest();

        deliver(older);
        assertNull(dialog.rows().getAdapter());

        deliver(current);
        assertEquals(1, dialog.rows().getAdapter().getCount());
        Map<?, ?> row = (Map<?, ?>) dialog.rows().getAdapter().getItem(0);
        assertEquals("Synthetic QMS layer", row.get("name"));
    }

    private Object startSyntheticRequest() throws Exception {
        Class<?> type = Class.forName(CreateFromQMSLayerDialog.class.getName() + "$LoadLayersList");
        Constructor<?> constructor = type.getDeclaredConstructor(CreateFromQMSLayerDialog.class);
        constructor.setAccessible(true);
        Object request = constructor.newInstance(dialog);
        Field current = CreateFromQMSLayerDialog.class.getDeclaredField("task");
        current.setAccessible(true);
        current.set(dialog, new WeakReference<>(request));
        return request;
    }

    private static void deliver(Object request) throws Exception {
        Method callback = request.getClass().getDeclaredMethod("onPostExecute", HttpResponse.class);
        callback.setAccessible(true);
        HttpResponse response = new HttpResponse(200, "OK",
                "[{\"id\":42,\"name\":\"Synthetic QMS layer\",\"icon\":\"icon\"}]");
        response.setOk(true);
        try {
            callback.invoke(request, response);
        } catch (InvocationTargetException error) {
            throw new AssertionError("Catalog response failed", error.getCause());
        }
    }

    public static class CatalogDialog extends CreateFromQMSLayerDialog {
        @Override public Dialog onCreateDialog(Bundle state) {
            FragmentActivity host = requireActivity();
            mLayers = new ListView(host);
            mProgress = new LinearLayout(host);
            mQMSLayers = new LinearLayout(host);
            mData = new ArrayList<>();
            return new AlertDialog.Builder(host, com.nextgis.maplibui.R.style.Theme_NextGIS_AppCompat_Light_Dialog)
                    .create();
        }

        ListView rows() {
            return mLayers;
        }
    }
}
