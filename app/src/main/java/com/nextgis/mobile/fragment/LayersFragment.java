/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2014-2019 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.mobile.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.api.ILayer;
import com.nextgis.maplib.api.INGWLayer;
import com.nextgis.maplib.datasource.ngw.SyncAdapter;
import com.nextgis.maplib.map.LayerGroup;
import com.nextgis.maplib.map.MapBase;
import com.nextgis.maplib.map.MapContentProviderHelper;
import com.nextgis.maplib.map.MapDrawable;
import com.nextgis.maplib.map.TrackLayer;
import com.nextgis.maplib.map.VectorLayer;
import com.nextgis.maplib.service.NGWSyncService;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;
import com.nextgis.maplib.util.SettingsConstants;
import android.text.TextUtils;
import com.nextgis.maplibui.GISApplication;
import com.nextgis.maplibui.fragment.LayersListAdapter;
import com.nextgis.maplibui.fragment.ReorderedLayerView;
import com.nextgis.maplibui.mapui.SyncAccountWorker;
import com.nextgis.maplibui.util.ControlHelper;
import com.nextgis.maplibui.util.HyperLogCrashHandler;
import com.nextgis.maplibui.util.NGIDUtils;
import com.nextgis.maplibui.util.UiUtil;
import com.nextgis.mobile.R;
import com.nextgis.mobile.activity.CreateVectorLayerActivity;
import com.nextgis.mobile.activity.MainActivity;
import com.nextgis.mobile.util.OfflineSyncIntentService;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_MULTI_PROCESS;
import static android.widget.Toast.LENGTH_LONG;
import static com.nextgis.maplib.util.Constants.SYNC_NONE;
import static com.nextgis.maplib.util.Constants.TAG;
import static com.nextgis.maplibui.util.ConstantsUI.GA_CREATE;
import static com.nextgis.maplibui.util.ConstantsUI.GA_EDIT;
import static com.nextgis.maplibui.util.ConstantsUI.GA_GEOSERVICE;
import static com.nextgis.maplibui.util.ConstantsUI.GA_IMPORT;
import static com.nextgis.maplibui.util.ConstantsUI.GA_LAYER;
import static com.nextgis.maplibui.util.ConstantsUI.GA_LOCAL;
import static com.nextgis.maplibui.util.ConstantsUI.GA_MENU;
import static com.nextgis.maplibui.util.ConstantsUI.GA_NGW;
import static com.nextgis.maplibui.util.SettingsConstantsUI.KEY_PREF_OFFLINE_SYNC_ON;
import static com.nextgis.mobile.util.AppSettingsConstants.AUTHORITY;

/**
 * A layers fragment class
 */
public class LayersFragment
        extends Fragment implements View.OnClickListener, PopupMenu.OnMenuItemClickListener {
    protected ActionBarDrawerToggle mDrawerToggle;
    protected DrawerLayout          mDrawerLayout;
    protected ReorderedLayerView    mLayersListView;
    protected View                  mFragmentContainerView;
    protected LayersListAdapter     mListAdapter;
    protected TextView              mInfoText;
    protected SyncReceiver          mSyncReceiver;
    protected ImageButton           mSyncButton;
    protected View                  mSyncPendingBadge;
    protected ImageButton           mNewLayer;
    protected List<Account>         mAccounts;
    private boolean mSyncFailureDialogShowing;

    private static final String PREF_PENDING_SYNC_ERROR = "pending_sync_error";
    private static final int SYNC_ERROR_MESSAGE_MAX = 200;
    private static final long SYNC_STATE_RECONCILE_DELAY_MS = 1500L;

    ObjectAnimator rotation;

    /**
     * Broadcast delivery is lifecycle-sensitive. While the animator is running, reconcile it with
     * the adapter-owned process state so a missed final broadcast cannot leave an infinite spinner.
     */
    private final Runnable mSyncStateReconcileRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSyncButton == null || !isAdded()) {
                return;
            }
            if (!NGWSyncService.isSyncStarted()) {
                HyperLog.v(Constants.TAG,
                        "LayersFragment: stopping stale sync animation after state reconciliation");
                refreshSyncButtonAnimateState(false);
                updateInfo();
                refreshPendingChangesBadge();
                return;
            }
            mSyncButton.postDelayed(this, SYNC_STATE_RECONCILE_DELAY_MS);
        }
    };

    /** Posted from {@link #syncState()}; cleared in {@link #onDestroyView()} to avoid NPE after layout is nulled. */
    private final Runnable mSyncDrawerStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDrawerLayout == null || !LayersFragment.this.isAdded()) {
                return;
            }
            if (mDrawerToggle == null) {
                return;
            }
            mDrawerToggle.syncState();
            if (!mDrawerToggleRegistered) {
                mDrawerLayout.addDrawerListener(mDrawerToggle);
                mDrawerToggleRegistered = true;
            }
        }
    };

    /** {@link DrawerLayout#addDrawerListener} is registered once per view; removed in {@link #onDestroyView()}. */
    private boolean mDrawerToggleRegistered;


    private static class LayerEditListener
            implements LayersListAdapter.onEdit {

        private final WeakReference<LayersFragment> fragmentRef;
        private final WeakReference<MainActivity> activityRef;
        private final WeakReference<MapFragment> mapFragmentRef;

        LayerEditListener(
                LayersFragment fragment,
                WeakReference<MainActivity> activityRef,
                WeakReference<MapFragment> mapFragmentRef) {
            this.fragmentRef = new WeakReference<>(fragment);
            this.activityRef = activityRef;
            this.mapFragmentRef = mapFragmentRef;
        }

        @Override
        public void onLayerEdit(ILayer layer) {
            LayersFragment fragment = fragmentRef.get();
            MainActivity activity = activityRef.get();
            MapFragment mapFragment = mapFragmentRef.get();

            if (fragment == null || activity == null || mapFragment == null)
                return;

            if (layer instanceof VectorLayer) {
                if (!((VectorLayer) layer).isEditingAllowed()) {
                    Toast.makeText(
                            activity,
                            com.nextgis.maplibui.R.string.layer_not_editable_in_collector,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                try {
                    if (mapFragment.getLayerFeaturesML((VectorLayer) layer) == null) {
                        Toast.makeText(
                                this.activityRef.get(),
                                com.nextgis.maplibui.R.string.edit_invisible,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                } catch (Exception ex){
                    Log.e("edit", ex.getMessage());

                }
            }

            IGISApplication application = (IGISApplication) activity.getApplication();
            application.sendEvent(GA_LAYER, GA_EDIT, GA_MENU);
            mapFragment.onFinishChooseLayerDialog(MapFragment.EDIT_LAYER, layer, false, false);
            fragment.toggle();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mSyncReceiver = new SyncReceiver();
        mAccounts = new ArrayList<>();
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState)
    {
        View view = inflater.inflate(R.layout.fragment_layers, container, false);

        final LinearLayout linearLayout = view.findViewById(R.id.action_space);
        if (null != linearLayout) {
            linearLayout.setBackgroundColor(ControlHelper.getColor(view.getContext(), android.R.attr.colorPrimary));
        }

        mSyncButton = view.findViewById(R.id.sync);
        mSyncPendingBadge = view.findViewById(R.id.sync_pending_badge);
        mNewLayer = view.findViewById(R.id.new_layer);
        mNewLayer.setOnClickListener(this);
        mInfoText = view.findViewById(R.id.info);

        setupSyncOptions();

        updateInfo();
        refreshPendingChangesBadge();
        return view;
    }

    protected void setupSyncOptions()
    {
        mAccounts.clear();
        final AccountManager accountManager = AccountManager.get(getActivity().getApplicationContext());
        Log.d(TAG, "LayersFragment: AccountManager.get(" + getActivity().getApplicationContext() + ")");
        final IGISApplication application = (IGISApplication) getActivity().getApplication();
        List<INGWLayer> layers = new ArrayList<>();

        for (Account account : accountManager.getAccountsByType(application.getAccountsType())) {
            layers.clear();
            MapContentProviderHelper.getLayersByAccount(application.getMap(), account.name, layers);

            if (layers.size() > 0)
                mAccounts.add(account);
        }

        if (mAccounts.isEmpty()) {
            if (null != mSyncButton) {
                mSyncButton.setEnabled(false);
                mSyncButton.setVisibility(View.GONE);
            }
            if (null != mSyncPendingBadge) {
                mSyncPendingBadge.setVisibility(View.GONE);
            }
            if (null != mInfoText) {
                mInfoText.setVisibility(View.INVISIBLE);
            }
        } else {
            if (null != mSyncButton) {
                mSyncButton.setVisibility(View.VISIBLE);
                mSyncButton.setEnabled(true);
                mSyncButton.setOnClickListener(this);
            }
            if (null != mInfoText) {
                mInfoText.setVisibility(View.VISIBLE);
            }
        }
        refreshPendingChangesBadge();
    }


    protected void updateInfo() {
        if (null == mInfoText || getContext() == null) {
            return;
        }

        SharedPreferences sharedPreferences = getContext().getSharedPreferences(Constants.PREFERENCES, MODE_MULTI_PROCESS);
        long timeStamp = sharedPreferences.getLong(SettingsConstants.KEY_PREF_LAST_SYNC_TIMESTAMP, 0);
        if (timeStamp > 0) {
            mInfoText.setText(ControlHelper.getSyncTime(getContext(), timeStamp));
        }
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        // Indicate that this fragment would like to influence the set of actions in the action bar.
        setHasOptionsMenu(true);
    }


    public boolean isDrawerOpen()
    {
        return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mFragmentContainerView);
    }


    /**
     * Users of this fragment must call this method to set up the navigation drawer interactions.
     *
     * @param fragmentId
     *         The android:id of this fragment in its activity's layout.
     * @param drawerLayout
     *         The DrawerLayout containing this fragment's UI.
     */
    public void setUp(
            int fragmentId,
            DrawerLayout drawerLayout,
            final MapDrawable map)
    {
        final WeakReference<MainActivity> activityRef = new WeakReference<MainActivity>((MainActivity)getActivity());
        if (activityRef.get() == null)
            return;
        mFragmentContainerView = activityRef.get().findViewById(fragmentId);

        Display display = activityRef.get().getWindowManager().getDefaultDisplay();

        int displayWidth;
        Point size = new Point();
        display.getSize(size);
        displayWidth = size.x;

        ViewGroup.LayoutParams params = mFragmentContainerView.getLayoutParams();
        if (params.width >= displayWidth) {
            params.width = (int) (displayWidth * 0.8);
        }
        mFragmentContainerView.setLayoutParams(params);


        final WeakReference<MapFragment> mapFragmentRef = new WeakReference<>(activityRef.get().getMapFragment());
        MapFragment mapFragmentForInit = mapFragmentRef.get();
        if (mapFragmentForInit == null || mapFragmentForInit.getMMapRef() == null || mapFragmentForInit.getMMapRef().get() == null)
            return;
        mListAdapter = new LayersListAdapter(activityRef.get(), mapFragmentForInit.getMMapRef().get());
        mListAdapter.setDrawer(drawerLayout);
        mListAdapter.setOnPencilClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mapFragmentRef.get()!= null
                //        &&   !mapFragmentRef.get().hasEdits()
                ) {
                    if (mapFragmentRef.get().getMFinishListener() != null)
                        mapFragmentRef.get().getMFinishListener().onClick(null);
                    return;
                }

                if (activityRef.get() == null)
                    return;
                AlertDialog builder = new AlertDialog.Builder(activityRef.get())
                        .setTitle(com.nextgis.maplibui.R.string.save)
                        .setMessage(com.nextgis.maplibui.R.string.has_edits)
                        .setPositiveButton(com.nextgis.maplibui.R.string.save, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (mapFragmentRef.get()!= null ) {
                                    mapFragmentRef.get().saveEdits();
                                    mapFragmentRef.get().setNewMode(MapFragment.MODE_NORMAL);
                                }
                            }
                        })
                        .setNegativeButton(com.nextgis.maplibui.R.string.discard, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (mapFragmentRef.get()!= null ) {
                                    mapFragmentRef.get().cancelEdits();
                                    mapFragmentRef.get().setNewMode(MapFragment.MODE_NORMAL);
                                }
                            }
                        }).create();
                builder.show();
            }
        });



        mListAdapter.setOnLayerEditListener(new LayerEditListener(this, activityRef, mapFragmentRef));
//                new LayersListAdapter.onEdit() {
//            @Override
//            public void onLayerEdit(ILayer layer) {
//                if (activityRef.get() == null)
//                    return;
//                IGISApplication application = (IGISApplication) activityRef.get().getApplication();
//                application.sendEvent(GA_LAYER, GA_EDIT, GA_MENU);
//                mapFragmentRef.get().onFinishChooseLayerDialog(MapFragment.EDIT_LAYER, layer);
//                toggle();
//            }
//        });

        if (mapFragmentRef.get() != null)
            mapFragmentRef.get().setOnModeChangeListener(new MapFragment.onModeChange() {
                @Override
                public void onModeChangeListener() {
                    mListAdapter.notifyDataSetChanged();
                }
            });

        mLayersListView = mFragmentContainerView.findViewById(R.id.layer_list);
        mLayersListView.setAdapter(mListAdapter);
        mLayersListView.setDrawer(drawerLayout);

        mDrawerLayout = drawerLayout;

        // set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        // set up the drawer's list view with items and click listener

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.

        mDrawerToggle = new ActionBarDrawerToggle(
                getActivity(),                    // host Activity
                mDrawerLayout,// DrawerLayout object
//                R.drawable.ic_drawer,             // nav drawer image to replace 'Up' caret
                com.nextgis.maplibui.R.string.layers_drawer_open,
                // "open drawer" description for accessibility
                com.nextgis.maplibui.R.string.layers_drawer_close
                // "close drawer" description for accessibility
        )
        {
            @Override
            public void onDrawerClosed(View drawerView)
            {
                super.onDrawerClosed(drawerView);
                if (!isAdded()) {
                    return;
                }

                getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }


            @Override
            public void onDrawerOpened(View drawerView)
            {
                super.onDrawerOpened(drawerView);
                if (!isAdded()) {
                    return;
                }

                setupSyncOptions();
                getActivity().supportInvalidateOptionsMenu(); // calls onPrepareOptionsMenu()
            }
        };

        mDrawerToggleRegistered = false;
        // Defer code dependent on restoration of previous instance state.
        syncState();
    }

    public void syncState() {
        if (mDrawerLayout == null) {
            return;
        }
        mDrawerLayout.removeCallbacks(mSyncDrawerStateRunnable);
        mDrawerLayout.post(mSyncDrawerStateRunnable);
    }

    public void toggle() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START))
            mDrawerLayout.closeDrawer(GravityCompat.START);
        else
            mDrawerLayout.openDrawer(GravityCompat.START);
    }

    public boolean isDrawerToggleEnabled() {
        return mDrawerToggle.isDrawerIndicatorEnabled();
    }

    public void setDrawerToggleEnabled(boolean state)
    {
        if (mDrawerToggle != null) {
            mDrawerToggle.setDrawerIndicatorEnabled(state);

            if (state) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            } else {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        return mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        // Forward the new configuration the drawer toggle component.
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    public void refreshSyncButtonAnimateState(boolean start) {
//        Log.e("RRFRSH", "refreshSyncButtonAnimateState with = " + (start? "true" : "false"));

        if (mSyncButton == null) {
            return;
        }
        if (start) {
            if (rotation == null) {
                rotation = ObjectAnimator.ofFloat(
                        mSyncButton, "rotation",
                        mSyncButton.getRotation(),
                        mSyncButton.getRotation() + 360 * 10);
                //rotation.setDuration(700 * 10); // 10 reteat
                rotation.setDuration(6000);                    // one rotate (ms)
                rotation.setRepeatCount(ValueAnimator.INFINITE);
                rotation.setInterpolator(new LinearInterpolator()); // smooth rotate
            }
            if (!rotation.isStarted()) {
                rotation.start();
            }
            mSyncButton.removeCallbacks(mSyncStateReconcileRunnable);
            mSyncButton.postDelayed(
                    mSyncStateReconcileRunnable, SYNC_STATE_RECONCILE_DELAY_MS);

// old rotation
//            RotateAnimation rotateAnimation = new RotateAnimation(
//                    0, 360, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
//            rotateAnimation.setFillAfter(true);
//            rotateAnimation.setDuration(700);
//            rotateAnimation.setRepeatCount(500);
//
            //mSyncButton.startAnimation(rotateAnimation);
        } else {
            mSyncButton.removeCallbacks(mSyncStateReconcileRunnable);
            if (rotation!= null)
                rotation.cancel();
            mSyncButton.clearAnimation();
        }
    }


    @Override
    public void onResume()
    {
        super.onResume();
          mListAdapter.onResume();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SyncAdapter.SYNC_START);
        intentFilter.addAction(SyncAdapter.SYNC_FINISH);
        intentFilter.addAction(SyncAdapter.SYNC_CANCELED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getActivity().registerReceiver(mSyncReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(mSyncReceiver, intentFilter);
        }

        refreshSyncButtonAnimateState(NGWSyncService.isSyncStarted());
        updateInfo();
        refreshPendingChangesBadge();
        maybeShowPendingSyncFailure();
    }


    @Override
    public void onPause()
    {
        refreshSyncButtonAnimateState(false);
        getActivity().unregisterReceiver(mSyncReceiver);
        super.onPause();
    }

    /**
     * Dot on the sync button when the map has local NGW/vector edits not yet pushed.
     */
    protected void refreshPendingChangesBadge() {
        if (mSyncPendingBadge == null) {
            return;
        }
        if (mSyncButton == null || mSyncButton.getVisibility() != View.VISIBLE) {
            mSyncPendingBadge.setVisibility(View.GONE);
            return;
        }
        boolean pending = false;
        try {
            if (getActivity() != null) {
                IGISApplication app = (IGISApplication) getActivity().getApplication();
                MapBase map = app != null ? app.getMap() : null;
                if (map instanceof LayerGroup) {
                    pending = ((LayerGroup) map).isChanges();
                }
            }
        } catch (RuntimeException ex) {
            HyperLog.w(Constants.TAG, "refreshPendingChangesBadge: " + ex.getMessage(), ex);
        }
        mSyncPendingBadge.setVisibility(pending ? View.VISIBLE : View.GONE);
    }

    private void storePendingSyncError(String error) {
        Context context = getContext();
        if (context == null || TextUtils.isEmpty(error)) {
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_PENDING_SYNC_ERROR, error)
                .apply();
    }

    private String takePendingSyncError() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String error = prefs.getString(PREF_PENDING_SYNC_ERROR, null);
        if (!TextUtils.isEmpty(error)) {
            prefs.edit().remove(PREF_PENDING_SYNC_ERROR).apply();
        }
        return error;
    }

    private void maybeShowPendingSyncFailure() {
        String error = takePendingSyncError();
        if (!TextUtils.isEmpty(error)) {
            showSyncFailureDialog(error);
        }
    }

    private String shortenSyncError(String error) {
        if (error == null) {
            return "";
        }
        String trimmed = error.replace("\r\n", " ").replace('\n', ' ').trim();
        if (trimmed.length() <= SYNC_ERROR_MESSAGE_MAX) {
            return trimmed;
        }
        return trimmed.substring(0, SYNC_ERROR_MESSAGE_MAX - 1) + "…";
    }

    private void showSyncFailureDialog(final String error) {
        if (!isAdded() || getActivity() == null || getActivity().isFinishing()) {
            storePendingSyncError(error);
            return;
        }
        if (mSyncFailureDialogShowing) {
            storePendingSyncError(error);
            return;
        }
        mSyncFailureDialogShowing = true;
        final String shortError = shortenSyncError(error);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.sync_failed_retry_title)
                .setMessage(getString(R.string.sync_failed_retry_message, shortError))
                .setPositiveButton(com.nextgis.maplibui.R.string.retry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSyncFailureDialogShowing = false;
                        startManualSync();
                    }
                })
                .setNegativeButton(com.nextgis.maplibui.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSyncFailureDialogShowing = false;
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mSyncFailureDialogShowing = false;
                    }
                })
                .show();
    }

    /** Same path as tapping the drawer sync button (without the OS auto-sync prompt). */
    protected void startManualSync() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            if (!isSomeToSync()) {
                Toast.makeText(context, com.nextgis.maplibui.R.string.sync_no_layers, LENGTH_LONG).show();
            }
        } catch (Exception ex) {
            HyperLog.e("SYNC", ex.getMessage());
        }

        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String base = preferences.getString("ngid_url", NGIDUtils.NGID_MY);
        boolean offlineSync = preferences.getBoolean(KEY_PREF_OFFLINE_SYNC_ON, false);

        if (offlineSync || !NGIDUtils.NGID_MY.equals(base)) {
            HyperLog.v(Constants.TAG, "startManualSync: on-premise sync");
            if (!OfflineSyncIntentService.startActionFoo(context)) {
                Toast.makeText(context, R.string.project_operation_wait, LENGTH_LONG).show();
            }
        } else {
            final Runnable switchRunnable = new Runnable() {
                @Override
                public void run() {
                    Context ctx = getContext();
                    if (ctx == null) {
                        return;
                    }
                    final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                    if (!prefs.getBoolean(KEY_PREF_OFFLINE_SYNC_ON, false)) {
                        prefs.edit().putBoolean(KEY_PREF_OFFLINE_SYNC_ON, true).apply();
                    }
                    if (!OfflineSyncIntentService.startActionFoo(ctx)) {
                        Toast.makeText(ctx, R.string.project_operation_wait, LENGTH_LONG).show();
                    }
                }
            };

            GISApplication.getInstance().startRunnable(switchRunnable);

            for (Account account : mAccounts) {
                HyperLog.v(Constants.TAG, "startManualSync: queue for " + account.name);
                Bundle settingsBundle = new Bundle();
                settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.setIsSyncable(account, AUTHORITY, 1);
                Log.d("SSYNC", "LayersFragment requestSync account=" + account.name
                        + " authority=" + AUTHORITY + " isSyncable="
                        + ContentResolver.getIsSyncable(account, AUTHORITY)
                        + " auto=" + ContentResolver.getSyncAutomatically(account, AUTHORITY));
                ContentResolver.requestSync(account, AUTHORITY, settingsBundle);
            }
        }
        updateInfo();
        refreshPendingChangesBadge();
    }

    public boolean isSomeToSync(){

        final AccountManager accountManager = AccountManager.get(getActivity().getApplicationContext());
        final IGISApplication application = (IGISApplication) getActivity().getApplication();
        List<INGWLayer> layers = new ArrayList<>();

        List<Account> allAccounts = new ArrayList<>();

        for (Account account : accountManager.getAccountsByType(application.getAccountsType())) {
            layers.clear();
            MapContentProviderHelper.getLayersByAccount(application.getMap(), account.name, layers);
            if (layers.size() > 0)
                allAccounts.add(account);
        }

        if (allAccounts.size() == 0)
            return false;

        String name = getContext().getPackageName() + "_preferences";
        SharedPreferences mSharedPreferences = getContext().getSharedPreferences(name, MODE_MULTI_PROCESS);
        boolean trackSync = mSharedPreferences.getBoolean(SettingsConstants.KEY_PREF_TRACK_SEND, false);

        for (int i = 0; i < allAccounts.size(); i++ ){
            if (isSomeToSync(allAccounts.get(i), trackSync))
                return true;
        }
        return false;
    }

    public boolean isSomeToSync(Account account, boolean trackSync){


        MapContentProviderHelper layerGroup =(MapContentProviderHelper) MapBase.getInstance();
        List<ILayer> layersToSync = new ArrayList<>();
        for (int i = 0; i < layerGroup.getLayerCount(); i++){
            ILayer layer = layerGroup.getLayer(i);

            if (layer instanceof INGWLayer && !account.name.equals(((INGWLayer)layer).getAccountName()))
                continue;

            if (layer instanceof  INGWLayer && ((INGWLayer) layer).getSyncType() == SYNC_NONE)
                continue;

            // only ngw and track
            if (! ((layer instanceof INGWLayer) || (layer instanceof TrackLayer && trackSync ) ) )
                continue;


            boolean exists = false;
            for (ILayer added : layersToSync){
                if (added.getPath().equals(layer.getPath())){
                    exists = true;
                    break;
                }
            }
            if (!exists)
                layersToSync.add(layer);
        }
        return layersToSync.size()>0;
    }

    @Override
    public void onClick(View v) {


        switch (v.getId()) {
            case R.id.sync:
                HyperLog.v(Constants.TAG, "onClick sync clicked!");
                startManualSync();
                break;
            case R.id.new_layer:
                if (getActivity() != null) {
//                    View view = getActivity().getWindow().getDecorView().findViewById(android.R.id.content);
                    View view = getActivity().findViewById(R.id.new_layer);
                    PopupMenu popup = new PopupMenu(getActivity(), view);
                    UiUtil.setForceShowIcon(popup);
                    popup.getMenuInflater().inflate(R.menu.add_layer, popup.getMenu());
                    popup.setOnMenuItemClickListener(this);
                    if (!AccountUtil.isProUser(getActivity())) {
                        popup.getMenu().findItem(R.id.menu_add_ngw).setIcon(com.nextgis.maplibui.R.drawable.ic_lock_black_24dp);
                    }
                    popup.show();
                }

                break;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        IGISApplication application = (IGISApplication) getActivity().getApplication();
        switch (menuItem.getItemId()) {
            case R.id.menu_new:
                application.sendEvent(GA_LAYER, GA_CREATE, GA_LOCAL);
                Intent intentNewLayer = new Intent(getActivity(), CreateVectorLayerActivity.class);
                startActivity(intentNewLayer);
                return true;
            case R.id.menu_add_local:
                application.sendEvent(GA_LAYER, GA_CREATE, GA_IMPORT);
                ((MainActivity) getActivity()).addLocalLayer();
                return true;
            case R.id.menu_add_underlay_file:
                ((MainActivity) getActivity()).addUnderlayFile();
                return true;
            case R.id.menu_add_underlay_catalog:
                startActivity(new Intent(getActivity(), com.nextgis.mobile.activity.UnderlayCatalogActivity.class)
                        .putExtra("choose", true));
                return true;
            case R.id.menu_add_by_url:
                application.sendEvent(GA_LAYER, GA_CREATE, GA_NGW);
                ((MainActivity) getActivity()).addNGWLayerByUrl();
                return true;
            case R.id.menu_add_remote:
                application.sendEvent(GA_LAYER, GA_CREATE, GA_GEOSERVICE);
                ((MainActivity) getActivity()).addRemoteLayer();
                return true;
            case R.id.menu_add_ngw:
                if (!AccountUtil.isUserExists(getActivity())) {
                    ControlHelper.showNoLoginDialog(getActivity());
                } else {
                    application.sendEvent(GA_LAYER, GA_CREATE, GA_NGW);
                    ((MainActivity) getActivity()).addNGWLayer();
                }
                return true;
            default:
                return super.onContextItemSelected(menuItem);
        }
    }

    protected class SyncReceiver
            extends BroadcastReceiver
    {

        @Override
        public void onReceive(
                Context context,
                Intent intent)
        {
            if (intent.getAction().equals(SyncAdapter.SYNC_START)) {
                refreshSyncButtonAnimateState(true);
            } else if (intent.getAction().equals(SyncAdapter.SYNC_FINISH) || intent.getAction().equals(SyncAdapter.SYNC_CANCELED)) {
                if (intent.hasExtra(SyncAdapter.EXCEPTION)) {
                    String error = intent.getStringExtra(SyncAdapter.EXCEPTION);
                    if (isResumed()) {
                        showSyncFailureDialog(error);
                    } else {
                        storePendingSyncError(error);
                    }
                }

                refreshSyncButtonAnimateState(false);
                updateInfo();
                refreshPendingChangesBadge();
            } else {
                refreshSyncButtonAnimateState(false);
                updateInfo();
                refreshPendingChangesBadge();
            }
        }
    }

    @Override
    public void onDestroyView() {
        refreshSyncButtonAnimateState(false);
        super.onDestroyView();

        if (mLayersListView != null) {
            mLayersListView.setAdapter(null);
        }

        if (mListAdapter != null) {
            mListAdapter.setOnLayerEditListener(null);
            mListAdapter.setOnPencilClickListener(null);
//            mListAdapter.onPause(); // if exists
        }

        mListAdapter = null;
        mLayersListView = null;
        mSyncButton = null;
        mSyncPendingBadge = null;
        if (mDrawerLayout != null) {
            mDrawerLayout.removeCallbacks(mSyncDrawerStateRunnable);
            if (mDrawerToggle != null && mDrawerToggleRegistered) {
                mDrawerLayout.removeDrawerListener(mDrawerToggle);
                mDrawerToggleRegistered = false;
            }
        }
        mDrawerLayout = null;

        final WeakReference<MainActivity> activityRef = new WeakReference<MainActivity>((MainActivity)getActivity());
        if (activityRef.get() == null)
            return;
        final WeakReference<MapFragment> mapFragmentRef = new WeakReference<>(activityRef.get().getMapFragment());
        MapFragment mf = mapFragmentRef != null ? mapFragmentRef.get() : null;
        if (mf != null) {
            mf.setOnModeChangeListener(null);
        }



    }

}
