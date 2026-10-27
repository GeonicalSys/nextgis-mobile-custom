package com.nextgis.mobile.rendering;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.gnss.GnssInputPrefs;
import com.nextgis.maplib.util.SettingsConstants;
import com.nextgis.maplibui.service.TrackerService;
import com.nextgis.maplibui.util.TrackActions;
import com.nextgis.mobile.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static com.nextgis.maplib.map.TrackLayer.*;
import static org.junit.Assert.*;

/** All mutations run only in the runner's isolated verification installation. */
@RunWith(AndroidJUnit4.class)
public class TrackStopConfirmationTest {
    private static void await(BooleanSupplier check) throws Exception {
        long end = SystemClock.uptimeMillis() + 30000;
        while (!check.getAsBoolean() && SystemClock.uptimeMillis() < end) Thread.sleep(60);
        assertTrue("Recording/confirmation did not settle", check.getAsBoolean());
    }

    private static class Session implements AutoCloseable {
        final TrackControlsTest.Tracks tracks = new TrackControlsTest.Tracks();
        final Context app = tracks.f.app;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        final SharedPreferences temp = app.getSharedPreferences(TrackerService.TEMP_PREFERENCES, Context.MODE_PRIVATE);
        final Map<String, ?> original = prefs.getAll();
        final String[] keys = {SettingsConstants.KEY_PREF_TRACK_SEND, SettingsConstants.KEY_PREF_GNSS_INPUT,
                SettingsConstants.KEY_PREF_GNSS_TRANSPORT, SettingsConstants.KEY_PREF_GNSS_DEVICE_ID,
                SettingsConstants.KEY_PREF_TRACK_RECORDING_ENABLED, "track_recording_session", "track_recording_failure",
                "battery_dont_show_pref"};
        long id;
        Session() throws Exception {
            assertFalse("Verification store must have no user recording", TrackerService.hasRecordingSession(app));
            assertTrue(prefs.edit().putBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false)
                    .putBoolean("battery_dont_show_pref",true)
                    .putString(SettingsConstants.KEY_PREF_GNSS_INPUT, GnssInputPrefs.VALUE_EXTERNAL)
                    .putString(SettingsConstants.KEY_PREF_GNSS_TRANSPORT, GnssInputPrefs.TRANSPORT_USB)
                    .putString(SettingsConstants.KEY_PREF_GNSS_DEVICE_ID, "").commit());
            start();
        }
        void start() throws Exception {
            tracks.f.screen.onActivity(TrackerService::start_stop_tracking_GetIconWithTitle);
            await(TrackerService::isTrackRecordingActive);
            rememberCurrent();
        }
        void rememberCurrent() {
            id = Long.parseLong(Uri.parse(temp.getString("track_uri", "")).getLastPathSegment());
            tracks.added.add(id);
        }
        String token() { return TrackerService.getRecordingSessionId(app); }
        void stop() throws Exception {
            String token = token();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    assertTrue(TrackerService.stopRecording(app, token, "native-test-cleanup")));
            await(() -> !TrackerService.hasRecordingSession(app));
        }
        void unchanged() {
            assertTrue(TrackerService.isTrackRecordingActive());
            assertTrue(TrackerService.isTrackRecordingEnabled(app));
            assertFalse("An unconfirmed dialog must not checkpoint Stop", temp.contains("pending_stop"));
            assertEquals(Long.toString(id), Uri.parse(temp.getString("track_uri", "")).getLastPathSegment());
            assertEquals(1, DatabaseUtils.longForQuery(tracks.layer.getDatabase(true),
                    "SELECT count(*) FROM tracks WHERE _id=? AND end IS NULL",new String[]{Long.toString(id)}));
        }
        PendingIntent notificationStop() throws Exception {
            NotificationManager manager = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
            AtomicReference<PendingIntent> result = new AtomicReference<>();
            // Recording state precedes replacement of the initial foreground notification.
            await(() -> {
                for (android.service.notification.StatusBarNotification entry : manager.getActiveNotifications()) {
                    Notification notification = entry.getNotification();
                    if (notification.actions == null) continue;
                    for (Notification.Action action : notification.actions)
                        if (app.getString(com.nextgis.maplibui.R.string.tracks_stop).contentEquals(action.title))
                            result.set(action.actionIntent);
                }
                return result.get() != null;
            });
            return result.get();
        }
        void mapStop() { NativeStyleTouch.tap(tracks.f,R.id.action_track_status); }
        @Override public void close() throws Exception {
            if (TrackerService.hasRecordingSession(app)) stop();
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : keys) {
                Object value = original.get(key);
                if (value instanceof String) restore.putString(key, (String) value);
                else if (value instanceof Boolean) restore.putBoolean(key, (Boolean) value);
                else restore.remove(key);
            }
            assertTrue(restore.commit());
            tracks.close();
        }
    }

    @Test public void mapStopRequiresPositiveConfirmationAndPreservesRecordedPoints() throws Exception {
        try (Session s = new Session()) {
            for (int time : new int[]{1000,2000}) {
                ContentValues point = new ContentValues(); point.put(FIELD_SESSION,s.id);
                point.put(FIELD_LON,time); point.put(FIELD_LAT,0); point.put(FIELD_TIMESTAMP,time);
                point.put(FIELD_SENT,0); point.put(FIELD_SEGMENT,1);
                s.app.getContentResolver().insert(Uri.parse("content://" + s.tracks.f.app.getAuthority()
                        + "/" + TABLE_TRACKPOINTS),point);
            }
            s.mapStop(); TrackControlsTest.Ui.tap(android.R.id.button2); s.unchanged();
            s.mapStop(); TrackControlsTest.Ui.automation().performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); s.unchanged();
            s.mapStop(); TrackControlsTest.Ui.tap(android.R.id.button1);
            await(() -> !TrackerService.hasRecordingSession(s.app));
            assertEquals(1,DatabaseUtils.longForQuery(s.tracks.layer.getDatabase(true),
                    "SELECT count(*) FROM tracks WHERE _id=? AND end>0",new String[]{Long.toString(s.id)}));
            assertEquals(2,DatabaseUtils.longForQuery(s.tracks.layer.getDatabase(true),
                    "SELECT count(*) FROM trackpoints WHERE session=? AND sent=0 AND segment=1",new String[]{Long.toString(s.id)}));
        }
    }

    @Test public void menuAndRealNotificationActionAskBeforeStopping() throws Exception {
        try (Session s = new Session()) {
            TrackControlsTest.Ui.menuItem(s.tracks.f.screen,R.id.menu_track,
                    s.app.getString(com.nextgis.maplibui.R.string.track_stop),false);
            TrackControlsTest.Ui.tap(android.R.id.button2); s.unchanged();
            PendingIntent stop = s.notificationStop();
            stop.send(); TrackControlsTest.Ui.tap(android.R.id.button2); s.unchanged();
            stop.send(); TrackControlsTest.Ui.tap(android.R.id.button1);
            await(() -> !TrackerService.hasRecordingSession(s.app));
        }
    }

    @Test public void staleDialogAndNotificationCannotStopNewSessionAndSplitKeepsSession() throws Exception {
        try (Session s = new Session()) {
            String old = s.token(); PendingIntent oldNotification = s.notificationStop();
            s.mapStop(); s.stop(); s.start(); assertNotEquals(old,s.token());
            TrackControlsTest.Ui.tap(android.R.id.button1); s.unchanged();
            oldNotification.send();
            await(() -> {
                android.view.accessibility.AccessibilityNodeInfo node = TrackControlsTest.Ui.find(TrackControlsTest.Ui.id(R.id.action_track_status));
                if (node == null) return false; node.recycle(); return true;
            });
            s.unchanged();
            String current = s.token(), uri = s.temp.getString("track_uri", "");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> s.app.startService(
                    new Intent(s.app,TrackerService.class).setAction("com.nextgis.maplibui.TRACK_SPLIT")));
            await(() -> TrackerService.isTrackRecordingActive() && !uri.equals(s.temp.getString("track_uri", "")));
            s.rememberCurrent(); assertEquals(current,s.token());
            assertFalse(TrackerService.stopRecording(s.app,old,"stale-native-test")); s.unchanged();
            s.mapStop(); TrackControlsTest.Ui.tap(android.R.id.button1);
            await(() -> !TrackerService.hasRecordingSession(s.app));
        }
    }

    @Test public void stopConfirmationFitsRussianThemesFontsAndNarrowViewportsWithoutStopping() throws Exception {
        try (Session s = new Session()) {
            AtomicReference<Integer> originalOrientation = new AtomicReference<>();
            s.tracks.f.screen.onActivity(a -> originalOrientation.set(a.getRequestedOrientation()));
            try {
                for (int[] screen : new int[][]{{320,568,540},{280,500,620},{568,320,540},{500,280,620}}) {
                    boolean landscape = screen[0] > screen[1];
                    s.tracks.f.screen.onActivity(a -> a.setRequestedOrientation(landscape
                            ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                    AtomicBoolean oriented = new AtomicBoolean();
                    await(() -> { s.tracks.f.screen.onActivity(a -> oriented.set(
                            (a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape)); return oriented.get(); });
                    for (boolean dark : new boolean[]{false,true}) for (float font : new float[]{1f,1.3f,2f}) {
                        AtomicReference<androidx.appcompat.app.AlertDialog> dialog = new AtomicReference<>();
                        AtomicBoolean dismissed = new AtomicBoolean();
                        AtomicInteger cancelDownEvents = new AtomicInteger();
                        AtomicInteger cancelUpEvents = new AtomicInteger();
                        AtomicReference<String> windowInput = new AtomicReference<>("No window input");
                        s.tracks.f.screen.onActivity(a -> {
                            android.content.res.Configuration config = new android.content.res.Configuration(a.getResources().getConfiguration());
                            config.setLocale(new java.util.Locale("ru")); config.fontScale=font;
                            config.densityDpi=screen[2]; config.screenWidthDp=screen[0]; config.screenHeightDp=screen[1];
                            androidx.appcompat.view.ContextThemeWrapper context = new androidx.appcompat.view.ContextThemeWrapper(a,
                                    dark ? R.style.AppTheme_Dark : R.style.AppTheme);
                            context.applyOverrideConfiguration(config);
                            dialog.set(TrackActions.confirmStop(context,s.token(),"layout-native-test",() -> dismissed.set(true)));
                            android.view.Window window = dialog.get().getWindow();
                            window.setCallback(new androidx.appcompat.view.WindowCallbackWrapper(window.getCallback()) {
                                @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                                    windowInput.set(windowInput.get() + "; action=" + event.getActionMasked()
                                            + " local=" + event.getX() + "," + event.getY()
                                            + " raw=" + event.getRawX() + "," + event.getRawY());
                                    return super.dispatchTouchEvent(event);
                                }
                            });
                            dialog.get().getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)
                                    .setOnTouchListener((button, event) -> {
                                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                                            cancelDownEvents.incrementAndGet();
                                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP)
                                            cancelUpEvents.incrementAndGet();
                                        return false;
                                    });
                            android.graphics.Rect usable = new android.graphics.Rect();
                            a.getWindow().getDecorView().getWindowVisibleDisplayFrame(usable);
                            float density = context.getResources().getDisplayMetrics().density;
                            dialog.get().getWindow().setLayout(Math.min(usable.width(),Math.round(screen[0]*density)),
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        });
                        AtomicBoolean fitted = new AtomicBoolean();
                        await(() -> { s.tracks.f.screen.onActivity(a -> {
                            android.view.View root = dialog.get().getWindow().getDecorView();
                            fitted.set(root.hasWindowFocus() && root.getWidth()>0 && !root.isLayoutRequested());
                        }); return fitted.get(); });
                        s.tracks.f.screen.onActivity(a -> com.nextgis.mobile.reliability.LargeTextLayoutTest.assertTrackDialogFits(dialog.get()));
                        try {
                            // Resolve physical bounds from the active, focused accessibility window.
                            // A context-only density/rotation override can precede its input surface.
                            TrackControlsTest.Ui.tap(android.R.id.button2);
                            // Input injection can return before AlertController posts dismissal.
                            // Observe its actual OnDismiss callback; send the Cancel tap only once.
                            await(dismissed::get);
                            assertEquals("One Cancel DOWN reached the actual button", 1, cancelDownEvents.get());
                            assertEquals("One Cancel UP reached the actual button", 1, cancelUpEvents.get());
                            s.tracks.f.screen.onActivity(a -> assertFalse(dialog.get().isShowing()));
                            android.util.Log.i("TrackStopLayoutQA", screen[0] + "x" + screen[1]
                                    + " density=" + screen[2] + " dark=" + dark + " font=" + font
                                    + " DOWN=1 UP=1 dismissed=true " + TrackControlsTest.Ui.lastTouch);
                            s.unchanged();
                        } catch (AssertionError failure) {
                            throw new AssertionError("Stop confirmation Cancel: " + screen[0] + "x" + screen[1]
                                    + " density=" + screen[2] + " dark=" + dark + " font=" + font
                                    + " down=" + cancelDownEvents.get() + " up=" + cancelUpEvents.get()
                                    + " dismissed=" + dismissed.get() + " " + TrackControlsTest.Ui.lastTouch
                                    + " windowInput=" + windowInput.get(), failure);
                        } finally {
                            s.tracks.f.screen.onActivity(a -> { if (dialog.get().isShowing()) dialog.get().dismiss(); });
                        }
                    }
                }
            } finally { s.tracks.f.screen.onActivity(a -> a.setRequestedOrientation(originalOrientation.get())); }
        }
    }
}
