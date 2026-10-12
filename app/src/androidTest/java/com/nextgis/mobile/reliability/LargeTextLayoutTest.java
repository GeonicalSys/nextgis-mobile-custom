package com.nextgis.mobile.reliability;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.fragment.MapFragment;
import com.nextgis.mobile.util.AppSettingsConstants;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Native text geometry and visible action bounds; synthetic Debug emulator only. */
@RunWith(AndroidJUnit4.class)
public class LargeTextLayoutTest {
    private static final int[][] SCREENS = {{360, 640, 420}, {320, 568, 540}, {568, 320, 540}, {280, 500, 620}};

    private static Context themed(float fontScale, int[] screen, boolean dark) {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(app.getPackageName().endsWith(".debug"));
        assertTrue(Build.HARDWARE.equals("ranchu") || Build.HARDWARE.equals("goldfish"));
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.setLocale(new Locale("ru"));
        config.fontScale = fontScale;
        config.densityDpi = screen[2];
        config.screenWidthDp = screen[0];
        config.screenHeightDp = screen[1];
        config.smallestScreenWidthDp = Math.min(screen[0], screen[1]);
        config.orientation = screen[0] > screen[1] ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        return new ContextThemeWrapper(app.createConfigurationContext(config), dark ? R.style.AppTheme_Dark : R.style.AppTheme);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void layout(View view, int width, int maxHeight) {
        for (int pass = 0; pass < 3; pass++) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, width, view.getMeasuredHeight());
            if (!view.isLayoutRequested()) break;
        }
    }

    private static String name(View view) {
        return view.getId() == View.NO_ID ? view.getClass().getSimpleName() : view.getResources().getResourceEntryName(view.getId());
    }

    private static void textFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getText().length() > 0) {
                android.text.Layout lines = text.getLayout();
                assertNotNull(name(text) + " has a text layout", lines);
                assertTrue(name(text) + " has room for every line: " + text.getText(),
                        lines.getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
                assertEquals(name(text) + " retains its full text", text.getText().length(), lines.getLineEnd(lines.getLineCount() - 1));
                for (int line = 0; line < lines.getLineCount(); line++) {
                    assertEquals(name(text) + " has no ellipsis", 0, lines.getEllipsisCount(line));
                    assertTrue(name(text) + " line fits horizontally", lines.getLineMax(line) <= lines.getWidth() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            textFits(((ViewGroup) view).getChildAt(i));
    }

    private static RectF bounds(ViewGroup root, View child) {
        RectF result = new RectF(0, 0, child.getWidth(), child.getHeight());
        View current = child;
        while (current != root) {
            current.getMatrix().mapRect(result);
            View parent = (View) current.getParent();
            result.offset(current.getLeft() - parent.getScrollX(), current.getTop() - parent.getScrollY());
            current = parent;
        }
        return result;
    }

    private static void actionsFit(ViewGroup root, int... ids) {
        for (int id : ids) {
            View action = root.findViewById(id);
            if (action == null || action.getVisibility() != View.VISIBLE) continue;
            RectF rect = bounds(root, action);
            assertTrue(name(action) + " has a visible target", rect.width() > 0 && rect.height() > 0);
            assertTrue(name(action) + " stays inside the panel: " + rect,
                    rect.left >= -1 && rect.top >= -1 && rect.right <= root.getWidth() + 1 && rect.bottom <= root.getHeight() + 1);
        }
    }

    private static void image(View view, String fileName) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        android.content.res.TypedArray colors = view.getContext().obtainStyledAttributes(new int[]{android.R.attr.colorBackground});
        canvas.drawColor(colors.getColor(0, android.graphics.Color.WHITE)); colors.recycle();
        view.draw(canvas);
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "large-text-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, fileName + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    private static void geometry(ViewGroup root, View view, StringBuilder output) {
        output.append(name(view)).append(' ').append(view.getClass().getSimpleName())
                .append(" measured=").append(view.getMeasuredWidth()).append('x').append(view.getMeasuredHeight())
                .append(" bounds=").append(bounds(root, view)).append(" scale=").append(view.getScaleY()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            geometry(root, ((ViewGroup) view).getChildAt(i), output);
    }

    private static void screen(String fileName) {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "large-text-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, fileName + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    @Test public void azimuthTextAndCloseStayWithinSmallPortraitAndLandscapePanels() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f}) for (int[] screen : SCREENS) {
                Context context = themed(scale, screen, dark);
                ViewGroup panel = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.layout_stakeout_panel, null);
                panel.setVisibility(View.VISIBLE);
                panel.findViewById(R.id.stakeout_sound).setVisibility(View.GONE);
                ((TextView) panel.findViewById(R.id.stakeout_distance)).setText(R.string.azimuth_select_target);
                ((TextView) panel.findViewById(R.id.stakeout_correction_value)).setText("−180,0°");
                int width = dp(context, screen[0] - 24), height = dp(context, screen[1] - 156);
                layout(panel, width, height);
                assertTrue("Panel respects the available map height", panel.getHeight() <= height);
                textFits(panel);
                TextView instruction = panel.findViewById(R.id.stakeout_distance);
                for (int line = 0; line < instruction.getLayout().getLineCount() - 1; line++) {
                    int end = instruction.getLayout().getLineEnd(line);
                    assertTrue("Instructions wrap between complete words", Character.isWhitespace(instruction.getText().charAt(end - 1))
                            || (end < instruction.getText().length() && Character.isWhitespace(instruction.getText().charAt(end))));
                }
                actionsFit(panel, R.id.stakeout_stop, R.id.stakeout_correction_minus,
                        R.id.stakeout_correction_plus, R.id.stakeout_correction_value, R.id.stakeout_correction_reset);
                image(panel, "azimuth-" + dark + "-" + scale + "-" + screen[0]);
            }
        });
    }

    @Test public void sharedRowsAndButtonsShowFullRussianLabelsAtLargeScales() {
        int[] layouts = {com.nextgis.maplibui.R.layout.row_layer, com.nextgis.maplibui.R.layout.row_resource_radio,
                com.nextgis.maplibui.R.layout.row_select_layer, com.nextgis.maplibui.R.layout.row_resource_check,
                com.nextgis.maplibui.R.layout.row_loading};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f}) {
                Context context = themed(scale, SCREENS[1], dark);
                for (int resource : layouts) {
                    ViewGroup row = (ViewGroup) LayoutInflater.from(context).inflate(resource, null);
                    TextView label = row.findViewById(com.nextgis.maplibui.R.id.tvName);
                    if (label == null) label = row.findViewById(com.nextgis.maplibui.R.id.tvLayerName);
                    label.setText("Полевые площадные объекты и измерения лесосеки");
                    TextView description = row.findViewById(com.nextgis.maplibui.R.id.tvDesc);
                    if (description != null) description.setText("Работа без подключения к интернету");
                    layout(row, dp(context, 288), dp(context, 500));
                    textFits(row);
                    actionsFit(row, com.nextgis.maplibui.R.id.check);
                }
                ViewGroup login = (ViewGroup) LayoutInflater.from(context).inflate(com.nextgis.maplibui.R.layout.fragment_ngid_login, null);
                layout(login, dp(context, 320), dp(context, 568));
                textFits(login);
                actionsFit(login, com.nextgis.maplibui.R.id.signin, com.nextgis.maplibui.R.id.signup,
                        com.nextgis.maplibui.R.id.onpremiseButton);
                image(login, "login-" + dark + "-" + scale);
            }
        });
    }

    @Test public void fittedPanelKeepsTransformedActionTargetsTouchable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = themed(2f, SCREENS[1], false);
            ViewGroup panel = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.layout_stakeout_panel, null);
            panel.setVisibility(View.VISIBLE);
            ((TextView) panel.findViewById(R.id.stakeout_correction_value)).setText("−180,0°");
            layout(panel, dp(context, 296), dp(context, 100));
            actionsFit(panel, R.id.stakeout_stop, R.id.stakeout_sound, R.id.stakeout_correction_minus,
                    R.id.stakeout_correction_plus, R.id.stakeout_correction_value, R.id.stakeout_correction_reset);
            for (int id : new int[]{R.id.stakeout_stop, R.id.stakeout_sound, R.id.stakeout_correction_minus,
                    R.id.stakeout_correction_plus, R.id.stakeout_correction_value, R.id.stakeout_correction_reset}) {
                View action = panel.findViewById(id);
                AtomicBoolean hit = new AtomicBoolean();
                action.setOnTouchListener((view, event) -> { hit.set(true); return true; });
                RectF rect = bounds(panel, action);
                long now = android.os.SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, rect.centerX(), rect.centerY(), 0);
                MotionEvent up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, rect.centerX(), rect.centerY(), 0);
                panel.dispatchTouchEvent(down); panel.dispatchTouchEvent(up);
                down.recycle(); up.recycle();
                assertTrue(name(action) + " receives touches at its displayed position", hit.get());
            }
        });
    }

    @Test public void statusAndMapToolsRemainWithinEnlargedScreenBounds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f}) for (int[] screen : SCREENS) {
                Context context = themed(scale, screen, dark);
                ViewGroup status = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.status_panel, null);
                int[] ids = {R.id.tv_latitude, R.id.tv_longitude, R.id.tv_zoom, R.id.tv_source, R.id.tv_accuracy, R.id.tv_altitude, R.id.tv_speed};
                String[] values = {"59,762542° Ш.", "133,466473° Д.", "15,9z", "GPS: 23", "±123,4 м", "−1234,5 м", "123,4 км/ч"};
                for (int i = 0; i < ids.length; i++) ((TextView) status.findViewById(ids[i])).setText(values[i]);
                layout(status, dp(context, screen[0]), dp(context, screen[1]));
                textFits(status);
                actionsFit(status, ids);
                image(status, "status-" + dark + "-" + scale + "-" + screen[0]);
                com.nextgis.mobile.view.MapControlRail rail = new com.nextgis.mobile.view.MapControlRail(context, null);
                int[] tools = {R.id.action_measurements, R.id.action_track_status, R.id.action_zoom_in, R.id.action_zoom_out};
                for (int id : tools) {
                    android.widget.ImageButton button = new android.widget.ImageButton(context);
                    button.setId(id); button.setContentDescription("Действие карты");
                    button.setMinimumWidth(dp(context, 64)); button.setMinimumHeight(dp(context, 64));
                    rail.addView(button);
                }
                layout(rail, dp(context, 160), dp(context, screen[1] - 156));
                actionsFit(rail, tools);
                layout(rail, dp(context, 80), dp(context, 40));
                View overflow = rail.findViewWithTag("map_control_overflow");
                assertTrue("A tiny rail retains a visible overflow action", overflow.getVisibility() == View.VISIBLE);
                RectF rect = bounds(rail, overflow);
                assertTrue("Even a scaled overflow button fits", rect.left >= -1 && rect.top >= -1
                        && rect.right <= rail.getWidth() + 1 && rect.bottom <= rail.getHeight() + 1);
            }
        });
    }

    @Test public void preferenceTitlesAndSummariesWrapAtLargeScales() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f}) {
                Context context = themed(scale, SCREENS[1], dark);
                androidx.preference.PreferenceManager manager = new androidx.preference.PreferenceManager(context);
                androidx.preference.PreferenceScreen screen = manager.createPreferenceScreen(context);
                androidx.preference.Preference preference = new androidx.preference.Preference(context);
                preference.setTitle("Автоматическая синхронизация всех загруженных проектов");
                preference.setSummary("Отправлять изменения при доступном подключении к интернету");
                screen.addPreference(preference);
                com.nextgis.maplibui.fragment.NGPreferenceFragment fragment = new com.nextgis.maplibui.fragment.NGPreferenceFragment() {
                    @Override protected void createPreferences(androidx.preference.PreferenceScreen ignored) { }
                };
                androidx.recyclerview.widget.RecyclerView.Adapter adapter = fragment.onCreateAdapter(screen);
                androidx.recyclerview.widget.RecyclerView.ViewHolder holder = adapter.createViewHolder(new FrameLayout(context), adapter.getItemViewType(0));
                adapter.bindViewHolder(holder, 0);
                layout(holder.itemView, dp(context, 320), dp(context, 568));
                textFits(holder.itemView);
                image(holder.itemView, "preference-" + dark + "-" + scale);
            }
        });
    }

    @Test public void fixedTableCellsFitFullTextAndRestoreLargerTypeWhenSpaceReturns() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[] resources = {com.nextgis.maplibui.R.layout.table_view_cell_layout,
                    com.nextgis.maplibui.R.layout.table_view_row_header_layout,
                    com.nextgis.maplibui.R.layout.table_view_column_header_layout};
            int[] ids = {com.nextgis.maplibui.R.id.cell_data, com.nextgis.maplibui.R.id.row_header_textview,
                    com.nextgis.maplibui.R.id.column_header_textView};
            for (boolean dark : new boolean[]{false, true}) for (float scale : new float[]{1f, 1.3f, 2f}) {
                Context context = themed(scale, SCREENS[3], dark);
                for (int i = 0; i < resources.length; i++) {
                    ViewGroup cell = (ViewGroup) LayoutInflater.from(context).inflate(resources[i], null);
                    TextView text = cell.findViewById(ids[i]);
                    text.setText(i == 0 ? "−1234567,89" : "Площадь лесного участка");
                    layout(cell, dp(context, 100), dp(context, 64)); textFits(cell);
                    float small = text.getTextSize();
                    layout(cell, dp(context, 500), dp(context, 64)); textFits(cell);
                    assertTrue("Table typography recovers when the cell grows", text.getTextSize() >= small);
                }
            }
        });
    }

    private static void preferenceRowsFit(androidx.preference.PreferenceFragmentCompat fragment) {
        androidx.recyclerview.widget.RecyclerView list = fragment.getListView();
        assertTrue("The actual preference list has a viewport", list.getWidth() > 0 && list.getHeight() > 0);
        androidx.recyclerview.widget.RecyclerView.Adapter adapter = list.getAdapter();
        assertNotNull(adapter);
        for (int position = 0; position < adapter.getItemCount(); position++) {
            androidx.recyclerview.widget.RecyclerView.ViewHolder holder = adapter.createViewHolder(
                    new FrameLayout(list.getContext()), adapter.getItemViewType(position));
            adapter.bindViewHolder(holder, position);
            layout(holder.itemView, list.getWidth(), dp(list.getContext(), 2000));
            textFits(holder.itemView);
        }
    }

    @Test public void realSettingsAndSecondaryScreensRetainCompleteLabels() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        themed(app.getResources().getConfiguration().fontScale, SCREENS[1], false);
        boolean landscape = "landscape".equals(InstrumentationRegistry.getArguments().getString("uiOrientation"));
        int requested = landscape ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        int expected = landscape ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        try (ActivityScenario<com.nextgis.mobile.activity.SettingsActivity> scenario =
                     ActivityScenario.launch(com.nextgis.mobile.activity.SettingsActivity.class)) {
            scenario.onActivity(activity -> activity.setRequestedOrientation(requested));
            instrumentation.waitForIdleSync(); Thread.sleep(500);
            scenario.onActivity(activity -> {
                assertEquals("ru", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                assertEquals(expected, activity.getResources().getConfiguration().orientation);
                activity.getSupportFragmentManager().executePendingTransactions();
                for (androidx.fragment.app.Fragment fragment : activity.getSupportFragmentManager().getFragments())
                    if (fragment instanceof androidx.preference.PreferenceFragmentCompat && fragment.isVisible())
                        preferenceRowsFit((androidx.preference.PreferenceFragmentCompat) fragment);
                textFits(activity.findViewById(com.nextgis.maplibui.R.id.main_toolbar));
                image(activity.getWindow().getDecorView(), "settings-menu-" + expected);
            });
            for (String key : new String[]{"com.nextgis.mobile.PREFS_GENERAL", "com.nextgis.mobile.PREFS_MAP",
                    "com.nextgis.mobile.PREFS_LOCATION", "com.nextgis.mobile.PREFS_TRACKING"}) {
                scenario.onActivity(activity -> {
                    android.os.Bundle args = new android.os.Bundle();
                    args.putString(androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, key);
                    activity.replaceSettingsFragment(args);
                    activity.getSupportFragmentManager().executePendingTransactions();
                });
                instrumentation.waitForIdleSync(); Thread.sleep(300);
                scenario.onActivity(activity -> {
                    androidx.preference.PreferenceFragmentCompat fragment = (androidx.preference.PreferenceFragmentCompat)
                            activity.getSupportFragmentManager().findFragmentByTag(key);
                    assertNotNull(fragment); assertTrue(fragment.isVisible());
                    preferenceRowsFit(fragment);
                    textFits(activity.findViewById(com.nextgis.maplibui.R.id.main_toolbar));
                    image(activity.getWindow().getDecorView(), "settings-" + key.substring(key.lastIndexOf('.') + 1) + "-" + expected);
                });
            }
        }
        for (Class<? extends android.app.Activity> type : new Class[]{
                com.nextgis.maplibui.activity.NGWSettingsActivity.class,
                com.nextgis.maplibui.activity.NGIDSettingsActivity.class,
                com.nextgis.maplibui.activity.NGWLoginActivity.class,
                com.nextgis.mobile.activity.ProjectSettingsActivity.class,
                com.nextgis.mobile.activity.UnderlayCatalogActivity.class}) {
            try (ActivityScenario<? extends android.app.Activity> scenario = ActivityScenario.launch(type)) {
                scenario.onActivity(activity -> {
                    activity.setRequestedOrientation(requested);
                    activity.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                    new androidx.core.view.WindowInsetsControllerCompat(activity.getWindow(), activity.getWindow().getDecorView())
                            .hide(androidx.core.view.WindowInsetsCompat.Type.ime());
                });
                instrumentation.waitForIdleSync(); Thread.sleep(500);
                scenario.onActivity(activity -> {
                    assertEquals("ru", activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                    assertEquals(expected, activity.getResources().getConfiguration().orientation);
                    View toolbar = activity.findViewById(com.nextgis.maplibui.R.id.main_toolbar);
                    if (toolbar != null) textFits(toolbar);
                    if (activity instanceof com.nextgis.maplibui.activity.NGPreferenceActivity) {
                        androidx.fragment.app.FragmentActivity host = (androidx.fragment.app.FragmentActivity) activity;
                        for (androidx.fragment.app.Fragment fragment : host.getSupportFragmentManager().getFragments())
                            if (fragment instanceof androidx.preference.PreferenceFragmentCompat && fragment.isVisible())
                                preferenceRowsFit((androidx.preference.PreferenceFragmentCompat) fragment);
                    } else textFits(activity.findViewById(android.R.id.content));
                    if (activity instanceof com.nextgis.maplibui.activity.NGWLoginActivity) {
                        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                        actionsFit(decor, com.nextgis.maplibui.R.id.signin, com.nextgis.maplibui.R.id.guest);
                        for (int id : new int[]{com.nextgis.maplibui.R.id.signin, com.nextgis.maplibui.R.id.guest}) {
                            RectF target = bounds(decor, decor.findViewById(id));
                            assertTrue("Login actions retain usable dimensions", target.width() >= dp(activity, 20)
                                    && target.height() >= dp(activity, 20));
                        }
                    }
                    image(activity.getWindow().getDecorView(), type.getSimpleName() + "-" + expected);
                });
            }
        }
    }

    @Test public void realMapCloseAndDialogActionsStayReachable() throws Exception {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context app = instrumentation.getTargetContext();
        themed(app.getResources().getConfiguration().fontScale, SCREENS[1], false);
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        boolean hadIntro = prefs.contains(AppSettingsConstants.KEY_PREF_INTRO);
        boolean oldIntro = prefs.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false);
        assertTrue(prefs.edit().putBoolean(AppSettingsConstants.KEY_PREF_INTRO, true).commit());
        androidx.core.os.LocaleListCompat oldLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales();
        android.os.LocaleList oldPlatformLocales = Build.VERSION.SDK_INT >= 33
                ? app.getSystemService(android.app.LocaleManager.class).getApplicationLocales() : null;
        if (Build.VERSION.SDK_INT >= 33) app.getSystemService(android.app.LocaleManager.class)
                .setApplicationLocales(android.os.LocaleList.forLanguageTags("ru"));
        else instrumentation.runOnMainSync(() -> androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags("ru")));
        boolean landscape = "landscape".equals(InstrumentationRegistry.getArguments().getString("uiOrientation"));
        int expectedOrientation = landscape ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.setRequestedOrientation(landscape
                    ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            long deadline = android.os.SystemClock.uptimeMillis() + 30_000;
            AtomicBoolean ready = new AtomicBoolean();
            while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> ready.set(activity.getMapFragment() != null
                        && activity.getMapFragment().getEditLayerOverlay() != null
                        && activity.getResources().getConfiguration().orientation == expectedOrientation
                        && "ru".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage())
                        && ((activity.getWindow().getDecorView().getWidth() > activity.getWindow().getDecorView().getHeight()) == landscape)));
                if (!ready.get()) Thread.sleep(100);
            }
            assertTrue("Real map initialized", ready.get());
            scenario.onActivity(activity -> activity.getMapFragment().setNewMode(MapFragment.MODE_AZIMUTH_POINTS));
            scenario.onActivity(activity -> {
                int[] ids = {R.id.tv_latitude, R.id.tv_longitude, R.id.tv_source, R.id.tv_accuracy, R.id.tv_altitude, R.id.tv_speed};
                String[] values = {"59,762542° Ш.", "133,466473° Д.", "GPS: 23", "±123,4 м", "−1234,5 м", "123,4 км/ч"};
                for (int i = 0; i < ids.length; i++) {
                    TextView value = activity.findViewById(ids[i]);
                    if (value != null) value.setText(values[i]);
                }
            });
            instrumentation.waitForIdleSync(); Thread.sleep(300);
            java.util.concurrent.atomic.AtomicReference<RectF> closeBounds = new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(activity -> {
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                textFits(activity.findViewById(R.id.main_toolbar));
                ViewGroup panel = activity.findViewById(R.id.stakeout_panel);
                assertTrue(panel.isShown()); textFits(panel);
                assertEquals("The actual instruction is Russian", "Выберите точку А", ((TextView) panel.findViewById(R.id.stakeout_distance)).getText().toString());
                RectF panelRect = bounds(decor, panel), toolbarRect = bounds(decor, activity.findViewById(R.id.main_toolbar));
                assertTrue("The guidance content stays below the toolbar", panelRect.top >= toolbarRect.bottom - 1);
                actionsFit((ViewGroup) activity.findViewById(R.id.maprl), R.id.stakeout_stop,
                        R.id.stakeout_correction_minus, R.id.stakeout_correction_plus, R.id.stakeout_correction_reset);
                RectF rect = bounds(decor, activity.findViewById(R.id.stakeout_stop));
                android.graphics.Rect visible = new android.graphics.Rect(); decor.getWindowVisibleDisplayFrame(visible);
                assertTrue("Close stays in the usable window", rect.left >= visible.left - 1 && rect.right <= visible.right + 1
                        && rect.top >= visible.top - 1 && rect.bottom <= visible.bottom + 1);
                assertTrue("Close remains usable after fitting", rect.width() >= dp(activity, 20) && rect.height() >= dp(activity, 20));
                closeBounds.set(rect);
                ViewGroup rail = activity.findViewById(R.id.map_control_rail);
                boolean reachable = false;
                for (int i = 0; i < rail.getChildCount(); i++) {
                    View action = rail.getChildAt(i);
                    if (action.getVisibility() != View.VISIBLE || action.getWidth() == 0) continue;
                    RectF target = bounds(decor, action);
                    assertTrue("Map tool remains within the usable window", target.left >= visible.left - 1
                            && target.right <= visible.right + 1 && target.top >= visible.top - 1 && target.bottom <= visible.bottom + 1);
                    assertFalse("The guidance panel must not cover a map tool", RectF.intersects(target, bounds(decor, panel)));
                    assertTrue("A fitted map tool is larger than a token pixel", target.width() >= dp(activity, 24) && target.height() >= dp(activity, 24));
                    reachable |= target.width() > 0 && target.height() > 0;
                }
                assertTrue("Map tools remain reachable beside enlarged telemetry", reachable);
                image(decor, "real-azimuth-" + expectedOrientation);
            });
            RectF close = closeBounds.get(); long now = android.os.SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, close.centerX(), close.centerY(), 0);
            MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, close.centerX(), close.centerY(), 0);
            instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up); down.recycle(); up.recycle();
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> assertEquals("A displayed X actually closes the panel",
                    MapFragment.MODE_NORMAL, (int) ((com.nextgis.maplib.map.MaplibreMapInteraction) activity.getMapFragment()).getMode()));
            scenario.onActivity(activity -> {
                androidx.drawerlayout.widget.DrawerLayout drawer = activity.findViewById(R.id.drawer_layout);
                drawer.openDrawer(androidx.core.view.GravityCompat.START, false);
                RectF rect = bounds((ViewGroup) activity.getWindow().getDecorView(), activity.findViewById(R.id.layers));
                assertTrue("Layers drawer fits a narrow enlarged-display screen", rect.left >= -1
                        && rect.right <= activity.getWindow().getDecorView().getWidth() + 1);
                drawer.closeDrawer(androidx.core.view.GravityCompat.START, false);
            });
            boolean compat = "compat".equals(InstrumentationRegistry.getArguments().getString("dialogStyle"));
            AtomicBoolean cancelled = new AtomicBoolean();
            java.util.concurrent.atomic.AtomicReference<RectF> cancelBounds = new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicReference<android.app.Dialog> dialog = new java.util.concurrent.atomic.AtomicReference<>();
            scenario.onActivity(activity -> {
                EditText input = new EditText(activity); input.setText("Проверка крупного интерфейса");
                android.app.Dialog window = compat ? com.nextgis.maplibui.util.FittedDialogs.compatBuilder(activity)
                        .setTitle("Сохранить изменения").setView(input)
                        .setPositiveButton("Сохранить изменения", null).setNegativeButton("Отменить", (d, b) -> cancelled.set(true)).create()
                        : com.nextgis.maplibui.util.FittedDialogs.nativeBuilder(activity)
                        .setTitle("Сохранить изменения").setView(input)
                        .setPositiveButton("Сохранить изменения", null).setNegativeButton("Отменить", (d, b) -> cancelled.set(true)).create();
                window.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                window.show();
                dialog.set(window);
            });
            instrumentation.waitForIdleSync(); Thread.sleep(300);
            scenario.onActivity(activity -> new androidx.core.view.WindowInsetsControllerCompat(
                    dialog.get().getWindow(), dialog.get().getWindow().getDecorView())
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime()));
            instrumentation.waitForIdleSync();
            AtomicBoolean keyboardHidden = new AtomicBoolean();
            long keyboardDeadline = android.os.SystemClock.uptimeMillis() + 15_000;
            while (!keyboardHidden.get() && android.os.SystemClock.uptimeMillis() < keyboardDeadline) {
                scenario.onActivity(activity -> {
                    androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(
                            dialog.get().getWindow().getDecorView());
                    keyboardHidden.set(insets != null && !insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
                });
                if (!keyboardHidden.get()) Thread.sleep(100);
            }
            screen("screen-dialog-" + expectedOrientation + "-" + (compat ? "compat" : "native"));
            scenario.onActivity(activity -> {
                android.app.Dialog window = dialog.get();
                ViewGroup decor = (ViewGroup) window.getWindow().getDecorView();
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(decor);
                assertNotNull("Dialog reports its window insets", insets);
                assertFalse("Check the complete interface with the keyboard closed", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
                image(decor, "real-dialog-" + expectedOrientation + "-" + (compat ? "compat" : "native"));
                StringBuilder details = new StringBuilder(); geometry(decor, decor, details);
                android.util.Log.i("LargeTextQa", details.toString());
                textFits(decor);
                actionsFit(decor, android.R.id.button1, android.R.id.button2);
                android.graphics.Rect visible = new android.graphics.Rect(); decor.getWindowVisibleDisplayFrame(visible);
                int[] origin = new int[2]; decor.getLocationOnScreen(origin);
                for (int buttonId : new int[]{android.R.id.button1, android.R.id.button2}) {
                    RectF target = bounds(decor, decor.findViewById(buttonId)); target.offset(origin[0], origin[1]);
                    assertTrue("Dialog action remains inside the usable screen: " + target + " in " + visible,
                            target.left >= visible.left - 1
                            && target.right <= visible.right + 1 && target.top >= visible.top - 1 && target.bottom <= visible.bottom + 1);
                    assertTrue("Dialog action retains a usable touch target", target.width() >= dp(activity, 20) && target.height() >= dp(activity, 20));
                    if (buttonId == android.R.id.button2) cancelBounds.set(target);
                }
            });
            RectF cancel = cancelBounds.get(); now = android.os.SystemClock.uptimeMillis();
            down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cancel.centerX(), cancel.centerY(), 0);
            up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, cancel.centerX(), cancel.centerY(), 0);
            instrumentation.sendPointerSync(down); instrumentation.sendPointerSync(up); down.recycle(); up.recycle();
            instrumentation.waitForIdleSync();
            assertTrue("The fitted Cancel button receives an actual touch", cancelled.get());
            assertFalse("Cancel closes the dialog", dialog.get().isShowing());
            scenario.onActivity(activity -> ((android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0));
            instrumentation.waitForIdleSync(); Thread.sleep(300);
        } finally {
            android.content.SharedPreferences.Editor restore = prefs.edit();
            if (hadIntro) restore.putBoolean(AppSettingsConstants.KEY_PREF_INTRO, oldIntro);
            else restore.remove(AppSettingsConstants.KEY_PREF_INTRO);
            assertTrue(restore.commit());
            if (Build.VERSION.SDK_INT >= 33) app.getSystemService(android.app.LocaleManager.class).setApplicationLocales(oldPlatformLocales);
            else instrumentation.runOnMainSync(() -> androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(oldLocales));
        }
    }
}
