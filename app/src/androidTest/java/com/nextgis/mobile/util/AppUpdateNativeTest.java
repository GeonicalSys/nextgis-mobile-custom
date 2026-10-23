package com.nextgis.mobile.util;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.NetworkType;

import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.*;

/** Native transfer/storage and the production dialog; disposable verification identity only. */
@RunWith(AndroidJUnit4.class)
public class AppUpdateNativeTest {
    private Context context;

    @Before public void requireVerificationWorkspace() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        VerificationEnvironment.requireIsolated(context);
    }

    @Test public void transferFinishesWhileTheActivityIsStoppedAndRetainsItsVerifiedFile() throws Exception {
        byte[] contents = "native technical APK transfer".getBytes(StandardCharsets.UTF_8);
        File directory = new File(context.getFilesDir(), "update-native-test");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File source = new File(directory, "source");
        Files.write(source.toPath(), contents);
        String hash = AppUpdateDownload.sha256(source, null);
        File destination = new File(directory, "background.apk");
        CountDownLatch entered = new CountDownLatch(1), proceed = new CountDownLatch(1);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            entered.countDown();
            try {
                if (!proceed.await(15, TimeUnit.SECONDS)) throw new IOException("Activity did not stop");
            } catch (InterruptedException error) { throw new IOException(error); }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("technical test")
                    .body(ResponseBody.create(contents, MediaType.get("application/octet-stream"))).build();
        }).build();
        java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.Future<?> transfer = executor.submit(() -> {
                try {
                    AppUpdateDownload.download(client, new Request.Builder().url("https://update.test/test.apk").build(),
                            destination, contents.length, hash, new AppUpdateDownload.Guard() {
                                @Override public void check() {}
                                @Override public void attach(Call call) {}
                                @Override public void validateUrl(String url) { assertEquals("https://update.test/test.apk", url); }
                            });
                } catch (Exception error) { throw new AssertionError(error); }
            });
            assertTrue(entered.await(15, TimeUnit.SECONDS));
            // MainActivity may finish its first-launch route. Closing is terminal in either
            // case and proves the transfer does not need an Activity to remain alive.
            scenario.moveToState(Lifecycle.State.DESTROYED);
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
            proceed.countDown();
            transfer.get(15, TimeUnit.SECONDS);
            assertArrayEquals(contents, Files.readAllBytes(destination.toPath()));
            assertFalse(new File(destination.getPath() + ".part").exists());
        } finally {
            proceed.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void readyDialogKeepsCompleteRussianTextAndBothActionsAtLargeScales() {
        int[][] screens = {{360, 640}, {640, 360}, {320, 568}, {568, 320}, {280, 500}, {500, 280}};
        AtomicInteger installs = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<AlertDialog> installDialog =
                new java.util.concurrent.atomic.AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f})
                    for (int[] screen : screens) {
                        Configuration config = new Configuration(activity.getResources().getConfiguration());
                        config.setLocale(new Locale("ru"));
                        config.fontScale = scale;
                        config.screenWidthDp = screen[0];
                        config.screenHeightDp = screen[1];
                        config.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
                        config.orientation = screen[0] > screen[1]
                                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
                        ContextThemeWrapper themed = new ContextThemeWrapper(activity,
                                dark ? R.style.AppTheme_Dark : R.style.AppTheme);
                        themed.applyOverrideConfiguration(config);
                        AlertDialog dialog = AppUpdateManager.readyDialog(themed, "3.1.2.123",
                                "Повышена надёжность работы приложения.", installs::incrementAndGet);
                        try {
                            dialog.show();
                            View decor = dialog.getWindow().getDecorView();
                            float density = themed.getResources().getDisplayMetrics().density;
                            int width = Math.round(screen[0] * density), height = Math.round(screen[1] * density);
                            for (int pass = 0; pass < 3; pass++) {
                                decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
                                decor.layout(0, 0, width, decor.getMeasuredHeight());
                            }
                            assertTrue(containsText(decor, "Обновление готово"));
                            assertEquals("Обновить", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
                            assertEquals("Позже", dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
                            textFits(decor);
                            assertTrue(decor.getHeight() <= height);
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                            assertEquals(0, installs.get());
                        } finally { dialog.dismiss(); }
                    }
                AlertDialog install = AppUpdateManager.readyDialog(activity, "3.1.2.123", "", installs::incrementAndGet);
                installDialog.set(install);
                install.show();
                assertEquals(0, installs.get());
                install.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            // AlertController posts the button listener to the main queue; do not dismiss
            // or assert its effect from the same main-thread callback that clicks it.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(1, installs.get());
            scenario.onActivity(activity -> installDialog.get().dismiss());
        }
    }

    @Test public void backgroundScheduleAllowsMeteredNetworksAndRejectsRoaming() {
        assertEquals(NetworkType.NOT_ROAMING, AppUpdateWorker.constraints().getRequiredNetworkType());
        assertTrue(new AppUpdateManager.NetworkState(true, true, false, false, true, false, true).canDownload());
        assertFalse(new AppUpdateManager.NetworkState(true, true, true, false, true, false, false).canDownload());
        assertTrue(new AppUpdateManager.NetworkState(true, true, true, true, false, false, true).canDownload());
    }

    private static boolean containsText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }

    private static void textFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && ((TextView) view).getText().length() > 0) {
            TextView text = (TextView) view;
            android.text.Layout lines = text.getLayout();
            assertNotNull("Layout for " + text.getText(), lines);
            assertTrue("Complete height for " + text.getText(),
                    lines.getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
            for (int line = 0; line < lines.getLineCount(); line++) {
                assertEquals("No ellipsis: " + text.getText(), 0, lines.getEllipsisCount(line));
                assertTrue("Complete width for " + text.getText(), lines.getLineMax(line) <= lines.getWidth() + 1);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            textFits(((ViewGroup) view).getChildAt(i));
    }
}
