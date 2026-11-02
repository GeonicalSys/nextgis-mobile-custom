package com.nextgis.mobile.util;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplibui.util.ProjectOperationCoordinator;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.UnderlayCatalogActivity;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Isolated UI/cancellation coverage; not acceptance of a real Debug export or its throughput. */
@RunWith(AndroidJUnit4.class)
public class LegacyUnderlayProgressNativeTest {
    private final Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void queuedCancelReleasesLeaseAndLateCallbacksCannotReplaceCurrentStatus() throws Exception {
        VerificationEnvironment.requireIsolated(app);
        Intent intent = new Intent(app, UnderlayCatalogActivity.class).putExtra("choose", true);
        try (ActivityScenario<UnderlayCatalogActivity> screen = ActivityScenario.launch(intent)) {
            long end = SystemClock.uptimeMillis() + 30000;
            while (ProjectOperationCoordinator.isBusy() && SystemClock.uptimeMillis() < end) Thread.sleep(50);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            ProjectOperationCoordinator.Lease lease = ProjectOperationCoordinator.tryBegin(app, ProjectOperationCoordinator.Kind.UNDERLAY_MIGRATION);
            assertNotNull("Own one exclusive verification lease", lease);
            try {
                screen.onActivity(activity -> {try {
                    LegacyUnderlayImporter.TransferControl owner = new LegacyUnderlayImporter.TransferControl();
                    set(activity, "transferControl", owner); set(activity, "transferActive", true);
                    AtomicInteger state = new AtomicInteger(0);
                    set(activity, "transferStartState", state); set(activity, "transferLease", lease);
                    call(activity, "setTransferBusy", new Class<?>[]{boolean.class}, true);
                    TextView status = activity.findViewById(R.id.underlay_transfer_status);
                    assertFalse("Show opening immediately, before any bytes arrive", status.getText().toString().isEmpty());
                    LegacyUnderlayImporter.Progress value = new LegacyUnderlayImporter.Progress(1, 2, 4096, 20, false,
                            LegacyUnderlayImporter.Phase.FINALIZING, 185000);
                    progress(activity, value, owner);
                    assertEquals(LegacyUnderlayProgressText.format(activity, value), status.getText().toString());
                    progress(activity, new LegacyUnderlayImporter.Progress(1, 2, 0, 0, false,
                            LegacyUnderlayImporter.Phase.OPENING, 0), new LegacyUnderlayImporter.TransferControl());
                    assertEquals("A previous owner cannot overwrite this transfer", LegacyUnderlayProgressText.format(activity, value), status.getText().toString());
                    assertTrue(activity.findViewById(R.id.underlay_transfer_cancel).performClick());
                    assertTrue(owner.isCanceled()); assertEquals(2, state.get());
                    assertFalse((Boolean) get(activity, "transferActive"));
                    assertNull(get(activity, "transferControl")); assertNull(get(activity, "transferLease"));
                    assertEquals(View.GONE, activity.findViewById(R.id.underlay_transfer_cancel).getVisibility());
                    progress(activity, value, owner);
                    assertEquals("Canceled callbacks cannot revive progress", "", status.getText().toString());
                } catch (Exception failure) { throw new AssertionError(failure); }});
                assertFalse("Cancel-before-worker releases the exclusive workspace", ProjectOperationCoordinator.isBusy());
            } finally { lease.close(); }
        }
    }

    @Test public void russianStagesAndCancelFitThemesFontsAndNarrowPortraitLandscape() {
        VerificationEnvironment.requireIsolated(app);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float font : new float[]{1f, 1.3f, 2f})
                for (int[] size : new int[][]{{320,480},{480,320},{280,420},{420,280}}) {
                    Configuration config = new Configuration(app.getResources().getConfiguration());
                    config.setLocale(new Locale("ru")); config.fontScale = font;
                    config.screenWidthDp = size[0]; config.screenHeightDp = size[1];
                    config.smallestScreenWidthDp = Math.min(size[0], size[1]);
                    config.orientation = size[0] > size[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                    Context context = new ContextThemeWrapper(app.createConfigurationContext(config), dark ? R.style.AppTheme_Dark : R.style.AppTheme);
                    ViewGroup root = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.activity_underlay_catalog, null, false);
                    ScrollView scroll = root.findViewById(R.id.underlay_transfer_scroll);
                    TextView status = root.findViewById(R.id.underlay_transfer_status);
                    Button cancel = root.findViewById(R.id.underlay_transfer_cancel);
                    root.findViewById(R.id.underlay_catalog_list).setVisibility(View.GONE);
                    root.findViewById(R.id.underlay_catalog_empty).setVisibility(View.GONE);
                    root.findViewById(R.id.underlay_import_from_old_app).setVisibility(View.GONE);
                    root.findViewById(R.id.underlay_catalog_progress).setVisibility(View.VISIBLE);
                    scroll.setVisibility(View.VISIBLE); status.setVisibility(View.VISIBLE); cancel.setVisibility(View.VISIBLE);
                    for (LegacyUnderlayImporter.Phase phase : LegacyUnderlayImporter.Phase.values()) {
                        status.setText(LegacyUnderlayProgressText.format(context, new LegacyUnderlayImporter.Progress(
                                12, 123, 123456789012L, 123456789, false, phase, 654321000)));
                        int width = Math.round(size[0] * context.getResources().getDisplayMetrics().density);
                        int height = Math.round(size[1] * context.getResources().getDisplayMetrics().density);
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                        root.layout(0, 0, width, height);
                        textFits(status); textFits(cancel);
                        assertTrue("Scroll has a usable viewport", scroll.getHeight() > 0);
                        assertTrue("Cancel remains below content", scroll.getBottom() <= cancel.getTop());
                        assertTrue("Cancel is fully inside the screen", cancel.getBottom() <= root.getHeight());
                        assertTrue("Cancel retains a touch target", cancel.getHeight() >= 48 * context.getResources().getDisplayMetrics().density - 1);
                        scroll.fullScroll(View.FOCUS_DOWN);
                    }
                    if (font == 2f && size[0] == 280) capture(root, dark ? "transfer-dark" : "transfer-light");
                }
        });
    }

    private static void textFits(TextView text) {
        android.text.Layout layout = text.getLayout(); assertNotNull(layout);
        assertEquals(text.getText().length(), layout.getLineEnd(layout.getLineCount()-1));
        assertTrue(layout.getHeight() <= text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+1);
        for (int line=0; line<layout.getLineCount(); line++) {
            assertEquals(0, layout.getEllipsisCount(line)); assertTrue(layout.getLineMax(line) <= layout.getWidth()+1);
        }
    }
    private void capture(View view, String name) {
        File folder = new File(app.getFilesDir(), "underlay-qa"); assertTrue(folder.isDirectory() || folder.mkdirs());
        Bitmap image = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new File(folder, name+".png"))) {
            view.draw(new Canvas(image)); assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
        finally { image.recycle(); }
    }
    private static void progress(Object activity, LegacyUnderlayImporter.Progress progress,
                                 LegacyUnderlayImporter.TransferControl owner) throws Exception {
        call(activity, "updateTransferProgress", new Class<?>[]{LegacyUnderlayImporter.Progress.class,
                LegacyUnderlayImporter.TransferControl.class}, progress, owner);
    }
    private static void call(Object owner, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = owner.getClass().getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(owner, args);
    }
    private static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void set(Object owner, String name, Object value) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
}
