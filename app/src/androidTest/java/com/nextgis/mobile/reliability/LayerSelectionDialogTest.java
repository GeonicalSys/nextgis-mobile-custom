package com.nextgis.mobile.reliability;

import android.app.Dialog;
import android.content.Context;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.util.GeoConstants;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.dialog.ChooseLayerDialog;
import com.nextgis.maplibui.mapui.VectorLayerUI;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.AppSettingsConstants;
import com.nextgis.mobile.util.VerificationEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The real layer chooser, native list measurement and physical taps; no network or real movement. */
@RunWith(AndroidJUnit4.class)
public class LayerSelectionDialogTest {
    public static class RecordingChooser extends ChooseLayerDialog {
        AtomicReference<ILayer> chosen;
        @Override public void onLayerSelect(ILayer layer) { chosen.set(layer); dismiss(); }
    }
    private GISApplication app;
    private ActivityScenario<MainActivity> scenario;
    private final List<ILayer> layers = new ArrayList<>();

    @Before public void setUp() throws Exception {
        app = (GISApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        VerificationEnvironment.requireIsolated(app);
        PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean("battery_dont_show_pref", true)
                .putBoolean(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_SHOW_GEO_DIALOG, false)
                .putString(com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_THEME,
                        "dark".equals(InstrumentationRegistry.getArguments().getString("uiTheme"))?"dark":"light").commit();
        scenario = ActivityScenario.launch(MainActivity.class);
        String orientation=InstrumentationRegistry.getArguments().getString("uiOrientation", "portrait");
        scenario.onActivity(activity -> activity.setRequestedOrientation("landscape".equals(orientation)
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        awaitReady();
        scenario.onActivity(activity -> {
            MapDrawable map = (MapDrawable) app.getMap();
            int[] types = {GeoConstants.GTLineString, GeoConstants.GTPoint, GeoConstants.GTPolygon};
            String[] names = {"Полевые линейные", "Полевые точечные", "Полевые площадные"};
            for (int i=0; i<types.length; i++) {
                VectorLayerUI layer = new VectorLayerUI(app, new File(map.getPath(),
                        "chooser_test_" + UUID.randomUUID().toString().replace("-", "")));
                layer.setName(names[i]);
                layer.create(types[i], new ArrayList<>());
                map.addLayer(layer); layers.add(layer);
            }
            map.save();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void awaitReady() throws Exception {
        long deadline=android.os.SystemClock.uptimeMillis()+30_000;
        java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
        while (!ready.get() && android.os.SystemClock.uptimeMillis()<deadline) {
            scenario.onActivity(activity -> ready.set(!activity.getSupportFragmentManager().isStateSaved()
                    && activity.getWindow().getDecorView().hasWindowFocus()
                    && activity.getMapFragment()!=null && activity.getMapFragment().getEditLayerOverlay()!=null));
            if (!ready.get()) Thread.sleep(100);
        }
        assertTrue("The verification map must be initialized and in the foreground",ready.get());
    }

    @After public void tearDown() {
        if (scenario != null) {
            scenario.onActivity(activity -> {
                androidx.fragment.app.Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(ChooseLayerDialog.TAG);
                if (fragment instanceof ChooseLayerDialog) ((ChooseLayerDialog)fragment).dismissNow();
                for (ILayer layer : layers) assertTrue(((VectorLayerUI)layer).delete(false));
                app.getMap().save();
            });
            scenario.close();
        }
    }

    private ChooseLayerDialog show(AtomicReference<ILayer> chosen) throws Exception {
        awaitReady();
        AtomicReference<ChooseLayerDialog> reference = new AtomicReference<>();
        scenario.onActivity(activity -> {
            RecordingChooser dialog = new RecordingChooser();
            dialog.chosen = chosen;
            dialog.setLayerList(layers).setCode(0).setTitle("Выбрать слой").setTheme(activity.getThemeId());
            dialog.showNow(activity.getSupportFragmentManager(), ChooseLayerDialog.TAG);
            reference.set(dialog);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        long until=android.os.SystemClock.uptimeMillis()+15_000;
        while (!reference.get().requireDialog().getWindow().getDecorView().hasWindowFocus()
                && android.os.SystemClock.uptimeMillis()<until) Thread.sleep(100);
        assertTrue("The layer chooser owns input focus",reference.get().requireDialog().getWindow().getDecorView().hasWindowFocus());
        return reference.get();
    }

    @Test public void allThreeEligibleGeometryRowsHaveARealViewport() throws Exception {
        ChooseLayerDialog chooser = show(new AtomicReference<>());
        scenario.onActivity(activity -> {
            ListView list = chooser.requireDialog().findViewById(com.nextgis.maplibui.R.id.listView);
            assertEquals(3, list.getAdapter().getCount());
            assertEquals("A three-layer picker must not be measured as one row", 3, list.getChildCount());
            for (int i=0; i<3; i++) {
                TextView name = list.getChildAt(i).findViewById(com.nextgis.maplibui.R.id.tvName);
                assertEquals(layers.get(i).getName(), name.getText().toString());
                assertNotNull(name.getLayout());
                for (int line=0; line<name.getLineCount(); line++) assertEquals(0,name.getLayout().getEllipsisCount(line));
            }
        });
    }

    @Test public void pointLineAndPolygonAreEachChosenByAnActualTap() throws Exception {
        for (int index=0; index<3; index++) {
            AtomicReference<ILayer> chosen = new AtomicReference<>();
            ChooseLayerDialog chooser = show(chosen);
            int position=index;
            android.graphics.Rect bounds = new android.graphics.Rect();
            scenario.onActivity(activity -> {
                ListView list = chooser.requireDialog().findViewById(com.nextgis.maplibui.R.id.listView);
                assertEquals(3, list.getChildCount());
                View row = list.getChildAt(position);
                assertTrue(row.getGlobalVisibleRect(bounds));
                int[] screen = new int[2]; row.getLocationOnScreen(screen);
                bounds.set(screen[0], screen[1], screen[0]+row.getWidth(), screen[1]+row.getHeight());
            });
            long now=android.os.SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
            MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
            InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
            down.recycle(); up.recycle();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            awaitChosen(chosen);
            assertSame(layers.get(index),chosen.get());
        }
    }

    @Test public void allChoicesAndCancelRemainTouchableAtLargeScales() throws Exception {
        AtomicReference<ILayer> chosen=new AtomicReference<>();
        ChooseLayerDialog chooser=show(chosen);
        scenario.onActivity(activity -> {
            ListView list=chooser.requireDialog().findViewById(com.nextgis.maplibui.R.id.listView);
            assertEquals(3,list.getAdapter().getCount());
            for (android.view.ViewParent parent=list.getParent(); parent instanceof View; parent=parent.getParent())
                assertFalse("A scrolling adapter keeps its bounded native viewport",parent instanceof com.nextgis.maplibui.view.FittedPanelLayout);
            assertTrue(list.getHeight()>0);
            list.setSelectionFromTop(2,0);
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Rect target=new android.graphics.Rect();
        scenario.onActivity(activity -> {
            ListView list=chooser.requireDialog().findViewById(com.nextgis.maplibui.R.id.listView);
            int child=2-list.getFirstVisiblePosition();
            assertTrue(child>=0 && child<list.getChildCount());
            View row=list.getChildAt(child);
            TextView label=row.findViewById(com.nextgis.maplibui.R.id.tvName);
            assertEquals(layers.get(2).getName(),label.getText().toString());
            assertNotNull(label.getLayout());
            assertEquals(label.length(),label.getLayout().getLineEnd(label.getLayout().getLineCount()-1));
            for(int line=0;line<label.getLineCount();line++) assertEquals(0,label.getLayout().getEllipsisCount(line));
            assertTrue(label.getLayout().getHeight()<=label.getHeight()-label.getCompoundPaddingTop()-label.getCompoundPaddingBottom()+1);
            android.graphics.Rect usable=new android.graphics.Rect();
            chooser.requireDialog().getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);
            View cancel=chooser.requireDialog().findViewById(android.R.id.button2);
            int[] location=new int[2]; cancel.getLocationOnScreen(location);
            assertTrue(location[0]>=usable.left && location[1]>=usable.top
                    && location[0]+cancel.getWidth()<=usable.right+1
                    && location[1]+cancel.getHeight()<=usable.bottom+1);
            row.getLocationOnScreen(location);
            target.set(location[0],location[1],location[0]+row.getWidth(),location[1]+row.getHeight());
            int[] listOrigin=new int[2]; list.getLocationOnScreen(listOrigin);
            assertTrue(target.intersect(listOrigin[0],listOrigin[1],listOrigin[0]+list.getWidth(),listOrigin[1]+list.getHeight()));
            try {
                File file=new File(app.getFilesDir(),"verification-chooser-"+activity.getResources().getConfiguration().orientation+".png");
                android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)) {assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}
                bitmap.recycle();
            } catch(java.io.IOException error) {throw new AssertionError(error);}
        });
        long now=android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,target.centerX(),target.centerY(),0);
        MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,target.centerX(),target.centerY(),0);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        down.recycle();up.recycle();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        awaitChosen(chosen);
        assertSame(layers.get(2),chosen.get());
        ChooseLayerDialog cancelChooser=show(new AtomicReference<>());
        Dialog cancelDialog=cancelChooser.requireDialog();
        scenario.onActivity(activity -> {
            View cancel=cancelDialog.findViewById(android.R.id.button2);
            int[] location=new int[2]; cancel.getLocationOnScreen(location);
            target.set(location[0],location[1],location[0]+cancel.getWidth(),location[1]+cancel.getHeight());
        });
        now=android.os.SystemClock.uptimeMillis();
        down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,target.centerX(),target.centerY(),0);
        up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,target.centerX(),target.centerY(),0);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(down);
        InstrumentationRegistry.getInstrumentation().sendPointerSync(up);
        down.recycle();up.recycle();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertFalse("Cancel receives a physical touch",cancelDialog.isShowing());
    }

    private void awaitChosen(AtomicReference<ILayer> chosen) throws Exception {
        // AbsListView dispatches its click after the native pressed-state delay.
        long until=android.os.SystemClock.uptimeMillis()+5_000;
        while(chosen.get()==null && android.os.SystemClock.uptimeMillis()<until) Thread.sleep(20);
    }
}
