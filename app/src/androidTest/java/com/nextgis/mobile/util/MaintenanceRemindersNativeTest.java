package com.nextgis.mobile.util;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.util.ProjectSyncState;
import com.nextgis.mobile.BuildConfig;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Stationary checks: only verification private state and component configuration change. */
@RunWith(AndroidJUnit4.class)
public class MaintenanceRemindersNativeTest {
    private Context context;

    @Before public void requireVerification() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true)
                .putBoolean("battery_dont_show_pref", true)
                .putBoolean("background_gps_enabled", false).commit();
        context.getSharedPreferences(MaintenanceReminders.PREFERENCES, 0).edit().clear().commit();
    }

    @Test public void durableFullSyncAndUpdateClocksRemainIndependentOfLocalProjectsAndAcknowledgement() {
        long now = System.currentTimeMillis(), old = now - 8L * 24 * 60 * 60 * 1000;
        String project = new File(context.getFilesDir(), "technical-maintenance-project").getAbsolutePath();
        long version = BuildConfig.VERSION_CODE + 1L;
        assertTrue(ProjectSyncState.markComplete(context, project, old));
        MaintenanceReminders.recordSuccessfulCheck(context, version, old);
        MaintenanceReminders.Reminder stale = MaintenanceReminders.read(context, project, 0, now);
        assertTrue(stale.syncOverdue);
        assertFalse(stale.syncUnknown);
        assertTrue(stale.checkOverdue);
        assertEquals(MaintenanceReminders.Update.DOWNLOAD, stale.update);
        MaintenanceReminders.markShown(context, "technical-launch");
        Context reopened = new android.content.ContextWrapper(context);
        assertTrue(MaintenanceReminders.alreadyShown(reopened, "technical-launch"));
        assertFalse(MaintenanceReminders.alreadyShown(reopened, "technical-next-launch"));
        assertEquals(old, ProjectSyncState.lastComplete(context, project));
        MaintenanceReminders.Reminder local = MaintenanceReminders.read(reopened, null, version, now);
        assertFalse(local.syncOverdue);
        assertTrue(local.checkOverdue);
        assertEquals(MaintenanceReminders.Update.INSTALL, local.update);
        assertTrue(ProjectSyncState.markComplete(context, project, now));
        MaintenanceReminders.recordSuccessfulCheck(context, BuildConfig.VERSION_CODE, now);
        assertFalse(MaintenanceReminders.read(reopened, project, 0, now).needed());
    }

    @Test public void russianWarningsKeepCompleteTextAndActionsAcrossThemesFontsAndNarrowViewports() {
        int[][] screens = {{360, 640}, {640, 360}, {320, 568}, {568, 320}, {280, 500}, {500, 280}};
        MaintenanceReminders.Reminder[] reminders = {
                new MaintenanceReminders.Reminder(true, false, true, MaintenanceReminders.Update.NONE),
                new MaintenanceReminders.Reminder(true, true, false, MaintenanceReminders.Update.NONE),
                new MaintenanceReminders.Reminder(true, false, true, MaintenanceReminders.Update.DOWNLOAD),
                new MaintenanceReminders.Reminder(true, false, true, MaintenanceReminders.Update.INSTALL)
        };
        AtomicInteger installs = new AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f})
                for (int[] screen : screens) for (MaintenanceReminders.Reminder reminder : reminders) {
                    AlertDialog[] shown = new AlertDialog[1];
                    try {
                        scenario.onActivity(activity -> {
                        Configuration config = new Configuration(activity.getResources().getConfiguration());
                        config.setLocale(new Locale("ru"));
                        config.fontScale = scale;
                        config.screenWidthDp = screen[0]; config.screenHeightDp = screen[1];
                        config.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
                        config.orientation = screen[0] > screen[1]
                                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
                                dark ? R.style.AppTheme_Dark : R.style.AppTheme);
                        themed.applyOverrideConfiguration(config);
                        AlertDialog dialog = reminder.update == MaintenanceReminders.Update.INSTALL
                                ? AppUpdateManager.readyDialog(themed, "3.1.2.123", "Повышена надёжность работы.",
                                    reminder.message(themed), installs::incrementAndGet)
                                : MaintenanceReminders.dialog(themed, reminder);
                            shown[0] = dialog;
                            dialog.show();
                        });
                        // WindowManager attaches the new decor on a later UI traversal (API30).
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                        scenario.onActivity(activity -> {
                            AlertDialog dialog = shown[0];
                            Context themed = dialog.getContext();
                            View decor = dialog.getWindow().getDecorView();
                            assertTrue("Capture requires an attached dialog", decor.isAttachedToWindow());
                            float density = themed.getResources().getDisplayMetrics().density;
                            int width = Math.round(screen[0] * density), height = Math.round(screen[1] * density);
                            for (int pass = 0; pass < 3; pass++) {
                                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
                                decor.layout(0, 0, width, decor.getMeasuredHeight());
                            }
                            TextView message = decor.findViewById(android.R.id.message);
                            assertNotNull(message);
                            assertTrue(message.getText().toString().contains(reminder.message(themed)));
                            assertTrue("Lead with the warning", message.getText().toString().startsWith(reminder.message(themed)));
                            for (View parent = message; parent != null; parent = parent.getParent() instanceof View
                                    ? (View) parent.getParent() : null) {
                                assertEquals("Do not shrink enlarged message", 1f, parent.getScaleY(), .001f);
                            }
                            androidx.core.widget.NestedScrollView scroll = decor.findViewById(androidx.appcompat.R.id.scrollView);
                            assertNotNull("Message owns a scroll viewport", scroll);
                            assertTrue("Message viewport is visible", scroll.getHeight() > 0);
                            if (scroll.getChildAt(0).getHeight() > scroll.getHeight()) {
                                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
                                assertTrue("Long message can reach its end", scroll.getScrollY() > 0);
                                scroll.scrollTo(0, 0);
                            }
                            if (scale == 2f && (screen[0] == 280 || screen[1] == 280)
                                    && reminder.update == MaintenanceReminders.Update.INSTALL) {
                                capture(decor, "ready-" + (dark ? "dark-" : "light-") + screen[0] + "x" + screen[1]);
                            }
                            textFits(decor);
                            assertTrue("Dialog fits available height", decor.getHeight() <= height);
                            View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                            assertTrue("Positive action remains visible", button.isShown() && button.getWidth() > 0);
                            android.graphics.Rect bounds = new android.graphics.Rect(0, 0, button.getWidth(), button.getHeight());
                            ((ViewGroup) decor).offsetDescendantRectToMyCoords(button, bounds);
                            assertTrue("Action is inside dialog font=" + scale + " screen=" + screen[0] + "x" + screen[1]
                                    + " update=" + reminder.update + " bounds=" + bounds + " height=" + decor.getHeight(),
                                    bounds.top >= 0 && bounds.bottom <= decor.getHeight());
                            if (reminder.update == MaintenanceReminders.Update.INSTALL) {
                                assertEquals("Обновить", ((TextView) button).getText().toString());
                                View later = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                                assertTrue("Later remains visible", later.isShown() && later.getWidth() > 0);
                                android.graphics.Rect laterBounds = new android.graphics.Rect(0, 0, later.getWidth(), later.getHeight());
                                ((ViewGroup) decor).offsetDescendantRectToMyCoords(later, laterBounds);
                                assertTrue("Both actions inside dialog: " + laterBounds,
                                        laterBounds.top >= 0 && laterBounds.bottom <= decor.getHeight());
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                            } else dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                        });
                    } finally {
                        scenario.onActivity(activity -> { if (shown[0] != null) shown[0].dismiss(); });
                    }
                }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals("Acknowledging never starts installation", 0, installs.get());
        }
    }

    private static void textFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && ((TextView) view).getText().length() > 0) {
            TextView text = (TextView) view;
            android.text.Layout lines = text.getLayout();
            assertNotNull("Layout for " + text.getText(), lines);
            assertTrue("Complete height: " + text.getText(), lines.getHeight()
                    <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
            for (int line = 0; line < lines.getLineCount(); line++) {
                assertEquals("No ellipsis: " + text.getText(), 0, lines.getEllipsisCount(line));
                assertTrue("Complete width: " + text.getText(), lines.getLineMax(line) <= lines.getWidth() + 1);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            textFits(((ViewGroup) view).getChildAt(i));
    }

    private void capture(View decor, String name) {
        File folder = new File(context.getFilesDir(), "maintenance-qa");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(decor.getWidth(), decor.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new File(folder, name + ".png"))) {
            decor.draw(new android.graphics.Canvas(image));
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { image.recycle(); }
    }
}
