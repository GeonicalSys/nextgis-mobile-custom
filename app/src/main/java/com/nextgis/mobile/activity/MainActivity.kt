/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Author:   Dmitry Baryshnikov (aka Bishop), bishop.dev@gmail.com
 * Author:   NikitaFeodonit, nfeodonit@yandex.com
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *****************************************************************************
 * Copyright (c) 2012-2021 NextGIS, info@nextgis.com
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
package com.nextgis.mobile.activity

import android.Manifest
import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SyncResult
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.snackbar.Snackbar
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.api.GpsEventListener
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.datasource.GeoMultiPoint
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.datasource.ngw.Connection
import com.nextgis.maplib.datasource.ngw.Resource
import com.nextgis.maplib.datasource.ngw.ResourceGroup
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.NGWVectorLayer
import com.nextgis.maplib.map.VectorLayer
import com.nextgis.maplib.util.AccountUtil
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplib.util.MapUtil
import com.nextgis.maplib.util.NGWUtil
import com.nextgis.maplib.util.NGWResourceUrl
import com.nextgis.maplib.util.NetworkUtil
import com.nextgis.maplib.util.SettingsConstants
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.activity.NGActivity
import com.nextgis.maplibui.api.IChooseLayerResult
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.fragment.BottomToolbar
import com.nextgis.maplibui.fragment.LayerFillProgressDialogFragment
import com.nextgis.maplibui.mapui.TrackLayerUI.CODE_TRACK_LIST
import com.nextgis.maplibui.mapui.SyncAccountWorker
import com.nextgis.maplibui.overlay.EditLayerOverlay
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.TrackerService.BackgroundPermissionCallback
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.ConstantsUI.KEY_BATTERY
import com.nextgis.maplibui.util.ConstantsUI.KEY_TRACK_ACTION
import com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_POINT
import com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_START
import com.nextgis.maplibui.util.ConstantsUI.VALUE_TRACK_STOP
import com.nextgis.maplibui.util.ControlHelper
import com.nextgis.maplibui.util.CollectorProjectRegistry
import com.nextgis.maplibui.util.FeatureFormDraftStore
import com.nextgis.maplibui.util.LayerBackupManager
import com.nextgis.maplibui.util.LayerUtil
import com.nextgis.maplibui.util.NGIDUtils
import com.nextgis.maplibui.util.NGWResourceImportHelper
import com.nextgis.maplibui.util.ProjectOperationCoordinator
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.nextgis.maplibui.util.UiUtil
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.R
import com.nextgis.mobile.fragment.LayersFragment
import com.nextgis.mobile.fragment.MapFragment
import com.nextgis.mobile.util.AppUpdateManager
import com.nextgis.mobile.util.DebugCompanionInstaller
import com.nextgis.maplibui.service.LayerFillService
import com.nextgis.mobile.util.AppSettingsConstants
import com.nextgis.mobile.util.SDCardUtils
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Main activity. Map and drawer with layers list created here
 */
class MainActivity : NGActivity(), GpsEventListener, IChooseLayerResult {


//    lateinit var settingsFrame : FrameLayout


    var mapFragment: MapFragment? = null
        protected set
    protected var mLayersFragment: LayersFragment? = null
    private var mMessageReceiver: MessageReceiver? = null
    protected var mToolbar: Toolbar? = null


    protected var mTrackReceiver: TrackStartStopReceiver? = null

    protected var mBackPressed: Long = 0
    protected var mTrackItem: MenuItem? = null
    private val ngwUrlExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NGWResourceUrl").apply { isDaemon = true }
    }
    private var ngwUrlImportInProgress = false
    private val startupUpdateCheckHandler = Handler(Looper.getMainLooper())
    private var startupUpdateCheckPending = false
    private var crashRecoveryOffered = false
    private var companionCheckedThisLaunch = false
    private val startupUpdateCheckRunnable = Runnable {
        if (isFinishing || isDestroyed || !hasWindowFocus() || AppUpdateManager.isBusyOrPending(this)) {
            return@Runnable
        }
        if (DebugCompanionInstaller.resume(this, null)) {
            startupUpdateCheckPending = false
            companionCheckedThisLaunch = true
            return@Runnable
        }
        if (!companionCheckedThisLaunch) {
            companionCheckedThisLaunch = true
            if (DebugCompanionInstaller.offer(this, false)) {
                startupUpdateCheckPending = false
                return@Runnable
            }
        }
        if (!startupUpdateCheckPending) return@Runnable
        startupUpdateCheckPending = false
        AppUpdateManager.checkForUpdateAutomatically(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        HyperLog.v(Constants.TAG, "MainActivity.onCreate")
        super.onCreate(savedInstanceState)
        // initialize the default settings
        PreferenceManager.setDefaultValues(this, R.xml.preferences_general, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_map, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_location, false)
        PreferenceManager.setDefaultValues(this, R.xml.preferences_tracks, false)
        migratePhotoOverlayDefaults()

        if (!mPreferences.getBoolean(AppSettingsConstants.KEY_PREF_INTRO, false)) {
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
            return
        }

        startupUpdateCheckPending = savedInstanceState == null

        setContentView(R.layout.activity_main)
        mMessageReceiver = MessageReceiver()

        mTrackReceiver = TrackStartStopReceiver()

//        settingsFrame = findViewById(R.id.settingsFrame)

        mToolbar = findViewById(R.id.main_toolbar)
        setSupportActionBar(mToolbar)
        if (null != supportActionBar) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawerLayout.setStatusBarBackgroundColor(
            ControlHelper.getColor(
                this,
                android.R.attr.colorPrimaryDark
            )
        )

        val fm = supportFragmentManager
        mapFragment = fm.findFragmentById(R.id.map) as MapFragment?
        mapFragment!!.undoRedoOverlay?.setTopToolbar(mToolbar)
        mapFragment!!.editLayerOverlay?.setTopToolbar(mToolbar)
        mapFragment!!.editLayerOverlay?.setBottomToolbar(bottomToolbar)

        val app = application as MainApplication
        mLayersFragment = fm.findFragmentById(R.id.layers) as LayersFragment?

        if (mLayersFragment != null && null != mLayersFragment!!.view) {
            mLayersFragment?.view?.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        com.nextgis.maplibui.R.color.color_grey_050
                    )
                )
            // Set up the drawer.
            mLayersFragment!!.setUp(R.id.layers, drawerLayout, app.map as MapDrawable)
        }

        var progressFragment =
            fm.findFragmentByTag(TAG_FRAGMENT_PROGRESS) as LayerFillProgressDialogFragment?
        if (progressFragment == null) {
            progressFragment = LayerFillProgressDialogFragment()
            fm.beginTransaction().add(progressFragment, TAG_FRAGMENT_PROGRESS).commit()
        }

        maybeShowCollectorProjectSelectorOnStartup()

        if (!hasLocationPermissions()) {
            Handler().postDelayed({ processAllPermisions(PERMISSIONS_REQUEST_ZERO) }, 1500)
        }

        //        if (!hasLocationPermissions()) {
//            List<String> permslist = new ArrayList<>();
//            permslist.add(Manifest.permission.ACCESS_COARSE_LOCATION);
//            permslist.add(Manifest.permission.ACCESS_FINE_LOCATION);
        /*            permslist.add(Manifest.permission.GET_ACCOUNTS);
        * /            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
        * /                permslist.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        * /
        * /            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2)
        * /                permslist.add(Manifest.permission.POST_NOTIFICATIONS); */
//
//            new Handler().postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    requestPermissions(R.string.permissions, R.string.location_permissions, PERMISSIONS_REQUEST_LOC, permslist.toArray(new String[permslist.size()])); // list.toArray(new Foo[list.size()])
//                }
//            }, 5000);
//
//        }
        NGIDUtils.get(this) { response ->
            if (response.isOk) {
                var support = getExternalFilesDir(null)
                support = if (support == null) File(filesDir, Constants.SUPPORT)
                else File(support, Constants.SUPPORT)

                try {
                    val jsonString = FileUtil.readFromFile(support)
                    val json = JSONObject(jsonString)
                    if (json.optBoolean(Constants.JSON_SUPPORTED_KEY)) {
                        val id = json.getString(Constants.JSON_USER_ID_KEY)
                        NetworkUtil.setUserNGUID(id)
                    }
                } catch (exception: Exception) {
                    HyperLog.w(Constants.TAG, "MainActivity.onCreate: " + exception.message, exception)
                }

                try {
                    FileUtil.writeToFile(support, response.responseBody)
                } catch (ignored: IOException) {
                    HyperLog.w(Constants.TAG, "MainActivity.onCreate: " + ignored.message, ignored)
                }

                NetworkUtil.setIsPro(AccountUtil.isProUser(baseContext))
            }
            if (mapFragment!!.editLayerOverlay?.mode == EditLayerOverlay.MODE_NONE)
                mToolbar?.setTitle(appName)
            //                boolean isLoggedIn = !TextUtils.isEmpty(mPreferences.getString(NGIDUtils.PREF_ACCESS_TOKEN, ""));
            //                if (!isLoggedIn)
            //                    showSnack();
        }
    }

    private fun migratePhotoOverlayDefaults() {
        if (mPreferences.getBoolean(
                AppSettingsConstants.KEY_PREF_PHOTO_OVERLAY_DEFAULTS_MIGRATED,
                false
            )
        ) {
            return
        }
        mPreferences.edit()
            .putBoolean(
                SettingsConstantsUI.KEY_PREF_PHOTO_OVERLAY_ENABLED,
                SettingsConstantsUI.DEFAULT_PHOTO_OVERLAY_ENABLED
            )
            .putBoolean(
                SettingsConstantsUI.KEY_PREF_PHOTO_OVERLAY_USE_OBJECT,
                SettingsConstantsUI.DEFAULT_PHOTO_OVERLAY_USE_OBJECT
            )
            .putBoolean(AppSettingsConstants.KEY_PREF_PHOTO_OVERLAY_DEFAULTS_MIGRATED, true)
            .apply()
    }

    private fun showSnack() {
        val snackbar = Snackbar.make(
            findViewById(R.id.mainview),
            getString(R.string.support_available),
            Snackbar.LENGTH_LONG
        )
            .setAction(R.string.more) {
                val pricing =
                    Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.pricing)))
                startActivity(pricing)
            }

        val view = snackbar.view
        val textView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(
            ContextCompat.getColor(
                view.context,
                com.nextgis.maplibui.R.color.color_white
            )
        )
        snackbar.show()
    }

    fun processAllPermisions(startlevel: Int) {
        if (startlevel == PERMISSIONS_REQUEST_ZERO) {
            if (!hasLocationPermissions()) {
                val permslist: MutableList<String> = ArrayList()
                permslist.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                permslist.add(Manifest.permission.ACCESS_FINE_LOCATION)
                requestPermissions(
                    this,
                    R.string.permissions,
                    R.string.location_permissions,
                    PERMISSIONS_REQUEST_LOC,
                    PERMISSIONS_REQUEST_LOC,
                    *permslist.toTypedArray<String>()
                ) // list.toArray(new Foo[list.size()])
                return
            } else processAllPermisions(PERMISSIONS_REQUEST_LOC)
            return
        }
        if (startlevel == PERMISSIONS_REQUEST_LOC) {
            if (!hasAccountCreatePermissions()) {
                val permslist: MutableList<String> = ArrayList()
                permslist.add(Manifest.permission.GET_ACCOUNTS)
                requestPermissions(
                    this,
                    R.string.permissions,
                    R.string.account_permissions,
                    PERMISSIONS_REQUEST_ACCOUNT,
                    -1,
                    *permslist.toTypedArray<String>()
                ) // list.toArray(new Foo[list.size()])
            } else processAllPermisions(PERMISSIONS_REQUEST_ACCOUNT)
            return
        }
        if (startlevel == PERMISSIONS_REQUEST_ACCOUNT) {
            if (!hasSDCARDWritePermissions()) {
                val permslist: MutableList<String> = ArrayList()
                permslist.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                requestPermissions(
                    this,
                    R.string.permissions,
                    R.string.memory_permissions,
                    PERMISSIONS_REQUEST_MEMORY,
                    PERMISSIONS_REQUEST_MEMORY,
                    *permslist.toTypedArray<String>()
                ) // list.toArray(new Foo[list.size()])
            } else processAllPermisions(PERMISSIONS_REQUEST_MEMORY)
            return
        }
        if (startlevel == PERMISSIONS_REQUEST_MEMORY) {
            if (!hasNotifyPermissions()) {
                val permslist: MutableList<String> = ArrayList()
                permslist.add(Manifest.permission.POST_NOTIFICATIONS)
                requestPermissions(
                    this, R.string.permissions, R.string.push_permissions, PERMISSIONS_REQUEST_PUSH,
                    -1,
                    *permslist.toTypedArray<String>()
                ) // list.toArray(new Foo[list.size()])
            }
        }
    }

    protected fun hasLocationPermissions(): Boolean {
        val permissions =
            isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        return permissions
    }

    protected fun hasAccountCreatePermissions(): Boolean {
        return isPermissionGranted(Manifest.permission.GET_ACCOUNTS)
    }

    protected fun hasSDCARDWritePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else true
    }

    protected fun hasNotifyPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray ) {
        when (requestCode) {
            TrackerService.PERMISSIONS_REQUEST_ZERO_LOCATION_POSPONDED -> if (com.nextgis.maplib.util.PermissionUtil.hasLocationPermissions(this)) {
                var item: MenuItem? = null
                try {
                    item = mToolbar!!.menu.findItem(R.id.menu_track)
                } catch (ex: Exception) {
                    HyperLog.w(Constants.TAG, "MainActivity.onRequestPermissionsResult: " + ex.message, ex)
                }
                askBackgroundPerm(item)
            }

            PERMISSIONS_REQUEST_LOC -> {
                mapFragment!!.restartGpsListener()
                processAllPermisions(PERMISSIONS_REQUEST_LOC)
            }

            PERMISSIONS_REQUEST_ACCOUNT -> processAllPermisions(PERMISSIONS_REQUEST_ACCOUNT)
            PERMISSIONS_REQUEST_MEMORY -> processAllPermisions(PERMISSIONS_REQUEST_MEMORY)
            PERMISSIONS_REQUEST_PUSH -> {
                // Notification permission alone must not enable sync notifications;
                // that remains the user toggle KEY_PREF_SHOW_SYNC (default false).
            }

            LOCATION_BACKGROUND_REQUEST -> {
                if (mTrackItem != null)
                    controlTrack(mTrackItem)
                if (mTrackItem == null)
                    checkBatteryOptimize()
                mTrackItem = null
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun showEditToolbar() {
        //stopRefresh(mToolbar!!.menu.findItem(R.id.menu_refresh))
        mToolbar!!.menu.clear()
        mToolbar!!.inflateMenu(com.nextgis.maplibui.R.menu.edit_geometry)

        var item = mToolbar!!.menu.findItem(com.nextgis.maplibui.R.id.menu_edit_redo)
        val visible = mapFragment!!.mode != MapFragment.MODE_EDIT_BY_WALK
        item.setVisible(visible)
        item = mToolbar!!.menu.findItem(com.nextgis.maplibui.R.id.menu_edit_undo)
        item.setVisible(visible)

        mLayersFragment!!.isDrawerToggleEnabled = false
        mToolbar!!.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)
    }

    fun showRulerToolbar() {
        mToolbar!!.menu.clear()
        mToolbar!!.inflateMenu(com.nextgis.maplibui.R.menu.ruler_measurement)
        mLayersFragment!!.isDrawerToggleEnabled = false
        mToolbar!!.navigationIcon = null
        mapFragment!!.undoRedoOverlay?.defineUndoRedo()
    }


    fun showDefaultToolbar() {
        mToolbar?.title = appName
        mToolbar?.setSubtitle(null)
        mToolbar?.menu?.clear()
        mToolbar?.inflateMenu(R.menu.main)
        updateMapRotationMenuItem(mToolbar?.menu?.findItem(R.id.menu_map_rotation))
        mLayersFragment!!.isDrawerToggleEnabled = true
        mLayersFragment!!.syncState()
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (null != mLayersFragment && !mLayersFragment!!.isDrawerOpen) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            menuInflater.inflate(R.menu.main, menu)
            updateMapRotationMenuItem(menu.findItem(R.id.menu_map_rotation))

            //restoreActionBar();
            return true
        }
        return super.onCreateOptionsMenu(menu)
    }


    val bottomToolbar: BottomToolbar
        get() = findViewById<View>(com.nextgis.maplibui.R.id.bottom_toolbar) as BottomToolbar


    private fun controlTrack(item: MenuItem?) {
        if (item != null) {
            val iconAndTitle = TrackerService.start_stop_tracking_GetIconWithTitle(this)
            setTrackItem(item, iconAndTitle.second, iconAndTitle.first)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val app = application as IGISApplication

        when (item.itemId) {
            android.R.id.home -> if (hasFragments()) return finishFragment()
            else if (mapFragment!!.isEditMode)
                return mapFragment!!.onOptionsItemSelected(item.itemId)
            else {
                mLayersFragment!!.toggle()
                return true
            }

            R.id.menu_settings -> {
                app.showSettings(
                    SettingsConstantsUI.ACTION_PREFS_GENERAL, RELOAD_ACTIVITY_DATA,
                    this
                )
                return true
            }

            R.id.menu_about -> {
                val intentAbout = Intent(this, AboutActivity::class.java)
                startActivity(intentAbout)
                return true
            }

            R.id.menu_locate -> {
                locateCurrentPosition()
                return true
            }

            R.id.menu_map_rotation -> {
                val enabled = mapFragment?.toggleMapRotation() ?: false
                updateMapRotationMenuItem(item, enabled)
                return true
            }

            R.id.menu_track -> {
                askBackgroundPerm(item)
                return true
            }

//            R.id.menu_refresh -> {
//                if (null != mapFragment) {
//                    mapFragment!!.refreshSyncButtonAnimateState()
//                }
//                return true
//            }

            com.nextgis.maplibui.R.id.menu_edit_save -> return mapFragment!!.saveEdits()
            com.nextgis.maplibui.R.id.menu_edit_undo, com.nextgis.maplibui.R.id.menu_edit_redo -> return mapFragment!!.onOptionsItemSelected(
                item.itemId
            )

            R.id.menu_share_log -> {
                shareLog()
                return true
            }

            R.id.menu_clear_log -> {
                clearLog()
                return true
            }

            R.id.menu_share_layer_backups -> {
                shareLayerBackups()
                return true
            }

            R.id.menu_clear_layer_backups -> {
                clearLayerBackups()
                return true
            }

            R.id.menu_collector_projects -> {
                showCollectorProjectsDialog()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    public fun askBackgroundPerm(item: MenuItem?) {
        TrackerService.showBackgroundDialog(this, object : BackgroundPermissionCallback {
            override fun beforeAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    mTrackItem = item
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    requestPermissions(
                        this@MainActivity,
                        R.string.permissions,
                        R.string.location_permissions,
                        LOCATION_BACKGROUND_REQUEST,
                        -1,
                        *permissions
                    )
                } else {
                    controlTrack(item)
                    if (item == null) // byWalk
                        checkBatteryOptimize()
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.Q)
            override fun onAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    mTrackItem = item
                    requestbackgroundLocationPermissions()
                } else {
                    controlTrack(item)
                    if (item == null) // byWalk
                        checkBatteryOptimize()
                }
            }

            @RequiresApi(api = Build.VERSION_CODES.Q)
            override fun afterAndroid10(hasBackgroundPermission: Boolean) {
                if (!hasBackgroundPermission) {
                    mTrackItem = item
                    requestbackgroundLocationPermissions()
                } else {
                    controlTrack(item)
                    if (item == null) // byWalk
                        checkBatteryOptimize()
                }
            }
        })
    }

    public fun checkBatteryOptimize(){
        val batteryOK = TrackerService.checkIsBatteryPermOK(this)
        if (!batteryOK) {

            Handler().postDelayed(Runnable () {
                val msg = Intent(ConstantsUI.MESSAGE_INTENT_TRACK)
                msg.setPackage(this.getPackageName())
                msg.putExtra(ConstantsUI.KEY_MESSAGE_TRACK, true)
                msg.putExtra(KEY_BATTERY, false)
                msg.putExtra(KEY_TRACK_ACTION, VALUE_TRACK_START)
                msg.setPackage(getPackageName())
                sendBroadcast(msg)
            }, 1000)

        }
    }


    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun requestbackgroundLocationPermissions() {
        val permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ActivityCompat.requestPermissions(this, permissions, LOCATION_BACKGROUND_REQUEST)
    }


    private fun shareLog() {
        HyperLog.getDeviceLogsInFile(this)
        val dir = File(getExternalFilesDir(null), "LogFiles")
        val size = FileUtil.getDirectorySize(dir)
        if (size == 0L) {
            Toast.makeText(this, com.nextgis.maplib.R.string.error_empty_dataset, Toast.LENGTH_LONG)
                .show()
            return
        }

        val files = zipLogs(dir)
        val type = "text/plain"
        UiUtil.share(files, type, this, true)
    }

    private fun clearLog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_log)
            .setMessage(R.string.clear_log_message)
            .setPositiveButton(R.string.clear_log) { _, _ ->
                HyperLog.deleteLogs()
                val dir = File(getExternalFilesDir(null), "LogFiles")
                dir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
                Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }

    private fun shareLayerBackups() {
        try {
            val file = LayerBackupManager.zipBackupsForShare(this)
            if (file == null) {
                Toast.makeText(this, com.nextgis.maplib.R.string.error_empty_dataset, Toast.LENGTH_LONG)
                    .show()
                return
            }
            UiUtil.share(file, "application/zip", this, true)
        } catch (ignored: IOException) {
            HyperLog.w(Constants.TAG, "MainActivity.shareLayerBackups: " + ignored.message, ignored)
            Toast.makeText(this, R.string.layer_backups_share_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun clearLayerBackups() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_layer_backups)
            .setMessage(R.string.clear_layer_backups_message)
            .setPositiveButton(R.string.clear_layer_backups) { _, _ ->
                if (LayerBackupManager.clearBackups(this)) {
                    Toast.makeText(this, R.string.layer_backups_cleared, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.layer_backups_clear_error, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .show()
    }

    private fun maybeShowCollectorProjectSelectorOnStartup() {
        val activeUid = mPreferences.getString(
            SettingsConstants.KEY_PREF_ACTIVE_COLLECTOR_PROJECT_UID,
            ""
        )
        if (!activeUid.isNullOrBlank()) {
            return
        }
        if (CollectorProjectRegistry.listProjects(this).isEmpty()) {
            return
        }
        Handler(Looper.getMainLooper()).post {
            if (!isFinishing && !isDestroyed) {
                showCollectorProjectsDialog()
            }
        }
    }

    /**
     * Recovery hub: track auto-resumes silently; walk/manual-geometry/form drafts are offered
     * one at a time in that order.
     */
    private fun maybeOfferCrashRecovery() {
        if (crashRecoveryOffered || isFinishing || isDestroyed) {
            return
        }
        val map = mapFragment ?: return
        HyperLog.v(Constants.TAG, "CrashRecovery hub check started")
        if (map.hasInterruptedWalkDraft()) {
            crashRecoveryOffered = true
            map.crashRecoveryWalkDialogShown = true
            map.pauseInterruptedWalkForRecovery()
            HyperLog.v(Constants.TAG, "CrashRecovery offering walk draft")
            AlertDialog.Builder(this)
                .setTitle(com.nextgis.maplibui.R.string.walkedit_interrupted_title)
                .setMessage(com.nextgis.maplibui.R.string.walkedit_interrupted_message)
                .setPositiveButton(com.nextgis.maplibui.R.string.walkedit_continue) { _, _ ->
                    HyperLog.v(Constants.TAG, "CrashRecovery walk Continue selected")
                    map.resumeWalkFromDraft()
                    maybeOfferManualGeometryDraftRecovery()
                }
                .setNegativeButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                    HyperLog.v(Constants.TAG, "CrashRecovery walk Discard selected")
                    map.discardWalkDraft()
                    maybeOfferManualGeometryDraftRecovery()
                }
                .setCancelable(false)
                .show()
            return
        }
        maybeOfferManualGeometryDraftRecovery()
    }

    private fun maybeOfferManualGeometryDraftRecovery() {
        val map = mapFragment ?: return
        val form = FeatureFormDraftStore.load(this)
        if (form?.pointSessionId != null || form?.walkSessionId != null) {
            maybeOfferFormDraftRecovery()
            return
        }
        if (!map.hasInterruptedManualGeometryDraft()) {
            maybeOfferFormDraftRecovery()
            return
        }
        crashRecoveryOffered = true
        HyperLog.v(Constants.TAG, "CrashRecovery offering manual geometry draft")
        AlertDialog.Builder(this)
            .setTitle(com.nextgis.maplibui.R.string.geometry_edit_interrupted_title)
            .setMessage(com.nextgis.maplibui.R.string.geometry_edit_interrupted_message)
            .setPositiveButton(com.nextgis.maplibui.R.string.geometry_edit_continue) { _, _ ->
                HyperLog.v(Constants.TAG, "CrashRecovery geometry Continue selected")
                map.resumeManualGeometryFromDraft()
                maybeOfferFormDraftRecovery()
            }
            .setNegativeButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                HyperLog.v(Constants.TAG, "CrashRecovery geometry Discard selected")
                map.discardManualGeometryDraft()
                maybeOfferFormDraftRecovery()
            }
            .setCancelable(false)
            .show()
    }

    private fun maybeOfferFormDraftRecovery() {
        val draft = FeatureFormDraftStore.load(this)
        if (draft == null) {
            if (maybeOfferPointCreationRecovery()) return
            crashRecoveryOffered = true
            HyperLog.v(Constants.TAG, "CrashRecovery hub check completed: no remaining drafts")
            return
        }
        if (!LayerUtil.isEditFormDraftRecoverable(this, draft)) {
            HyperLog.w(
                Constants.TAG,
                "CrashRecovery discarding invalid form draft layer=${draft.layerId} " +
                    "feature=${draft.featureId}"
            )
            FeatureFormDraftStore.clear(this)
            maybeOfferPointCreationRecovery()
            crashRecoveryOffered = true
            return
        }
        crashRecoveryOffered = true
        HyperLog.v(
            Constants.TAG,
            "CrashRecovery offering form draft layer=${draft.layerId} feature=${draft.featureId}"
        )
        AlertDialog.Builder(this)
            .setTitle(com.nextgis.maplibui.R.string.form_draft_title)
            .setMessage(com.nextgis.maplibui.R.string.form_draft_message)
            .setPositiveButton(com.nextgis.maplibui.R.string.form_draft_continue) { _, _ ->
                HyperLog.v(Constants.TAG, "CrashRecovery form Continue selected")
                LayerUtil.showEditFormFromDraft(this, draft)
            }
            .setNegativeButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                HyperLog.v(Constants.TAG, "CrashRecovery form Discard selected")
                FeatureFormDraftStore.discard(this)
                if (draft.pointSessionId != null || draft.walkSessionId != null)
                    mapFragment?.discardManualGeometryDraft()
            }
            .setCancelable(false)
            .show()
    }

    private fun maybeOfferPointCreationRecovery(): Boolean {
        val map = mapFragment ?: return false
        if (!map.hasPointCreationWithoutDraft()) return false
        crashRecoveryOffered = true
        AlertDialog.Builder(this)
            .setTitle(com.nextgis.maplibui.R.string.form_draft_title)
            .setMessage(com.nextgis.maplibui.R.string.walk_point_recovery)
            .setPositiveButton(com.nextgis.maplibui.R.string.form_draft_continue) { _, _ ->
                map.resumePointCreationSelection()
            }
            .setNegativeButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                map.discardPointCreationWithoutDraft()
            }
            .setCancelable(false)
            .show()
        return true
    }

    private fun showCollectorProjectsDialog() {
        val projects = CollectorProjectRegistry.listProjects(this)
        if (projects.isEmpty()) {
            Toast.makeText(this, R.string.collector_projects_empty, Toast.LENGTH_LONG).show()
            return
        }

        ProjectChooserDialog.show(this, projects, ::switchCollectorProject)
    }

    private fun switchCollectorProject(project: CollectorProjectRegistry.ProjectInfo) {
        val gisApp = application as IGISApplication
        if (com.nextgis.maplibui.util.WalkSessionStore.load(this) != null) {
            Toast.makeText(this, com.nextgis.maplibui.R.string.walk_project_busy, Toast.LENGTH_LONG).show()
            return
        }
        if (TrackerService.isTrackerServiceRunning(this)) {
            Toast.makeText(this, R.string.collector_project_switch_tracking, Toast.LENGTH_LONG).show()
            return
        }
        if (gisApp.isLayerFillServiceBusy || ProjectOperationCoordinator.isBusy()) {
            Toast.makeText(this, R.string.collector_project_switch_busy, Toast.LENGTH_LONG).show()
            return
        }
        if (mapFragment?.isEditMode == true) {
            Toast.makeText(this, R.string.collector_project_switch_edit_mode, Toast.LENGTH_LONG).show()
            return
        }
        if (project.isActive(this)) {
            Toast.makeText(this, R.string.collector_project_already_active, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            gisApp.getMap()?.save()
        } catch (exception: RuntimeException) {
            HyperLog.w(
                Constants.TAG,
                "Collector project switch: current map save failed: " + exception.message,
                exception
            )
        }

        if (!CollectorProjectRegistry.activateProject(this, project.projectUid)) {
            Toast.makeText(this, R.string.collector_project_not_found, Toast.LENGTH_LONG).show()
            return
        }

        val account = if (project.isLocal) null else gisApp.getAccount(project.accountName)
        val app = application as? GISApplication
        if (account != null && app != null
            && ContentResolver.getSyncAutomatically(account, gisApp.authority)
        ) {
            val period = GISApplication.getAccountSyncTime(account, app)
            SyncAccountWorker.scheduleSoon(this, account.name, period)
            HyperLog.v(
                Constants.TAG,
                "Collector project switch: scheduled composition/data sync account=${account.name}"
            )
        }
        HyperLog.v(
            Constants.TAG,
            "Collector project switch: activated projectUid=${project.projectUid}"
        )
        recreate()
    }

    private fun zipLogs(dir: File): File? {
        var temp = MapUtil.prepareTempDir(this, "shared_layers", false)
        val outdated = ArrayList<File>()
        try {
            val fileName = "ng-logs.zip"
            if (temp == null) {
                AlertDialog.Builder(this)
                    .setMessage(com.nextgis.maplibui.R.string.error_file_create)
                    .setPositiveButton(com.nextgis.maplibui.R.string.ok, null)
                    .create()
                    .show()
                //Toast.makeText(this, R.string.error_file_create, Toast.LENGTH_LONG).show();
            }

            temp = File(temp, fileName)
            temp.createNewFile()
            val fos = FileOutputStream(temp, false)
            val zos = ZipOutputStream(BufferedOutputStream(fos))

            val buffer = ByteArray(1024)
            var length: Int

            for (file in dir.listFiles()) {
                if (System.currentTimeMillis() - file.lastModified() > 60 * 60 * 1000) outdated.add(
                    file
                )
                try {
                    val fis = FileInputStream(file)
                    zos.putNextEntry(ZipEntry(file.name))

                    while ((fis.read(buffer).also { length = it }) > 0) zos.write(buffer, 0, length)

                    zos.closeEntry()
                    fis.close()
                } catch (ignored: Exception) {
                    HyperLog.w(Constants.TAG, "MainActivity.zipLogs: " + ignored.message, ignored)
                }
            }

            zos.close()
            fos.close()
        } catch (ignored: IOException) {
            HyperLog.w(Constants.TAG, "MainActivity.zipLogs: " + ignored.message, ignored)
            temp = null
        }
        for (file in outdated) {
            file.delete()
        }
        return temp
    }

    private fun setTrackItem(item: MenuItem?, title: Int, icon: Int) {
        if (null != item) {
            item.setTitle(title)
            item.setIcon(icon)
        }
    }


    fun hasFragments(): Boolean {
        return supportFragmentManager.backStackEntryCount > 0
    }


    fun finishFragment(): Boolean {
        if (hasFragments()) {
            supportFragmentManager.popBackStack()
            setActionBarState(true)
            return true
        }

        return false
    }


    override fun isHomeEnabled(): Boolean {
        return false
    }


    @Synchronized
    fun onRefresh(isRefresh: Boolean) {
//        val refreshItem = mToolbar!!.menu.findItem(R.id.menu_refresh)
//        if (null != refreshItem) {
//            if (isRefresh) {
//                if (refreshItem.actionView == null) {
//                    refreshItem.setActionView(R.layout.layout_refresh)
//                    val progress =
//                        refreshItem.actionView!!.findViewById<ProgressBar>(R.id.refreshingProgress)
//                    progress?.indeterminateDrawable?.setColorFilter(
//                        ContextCompat.getColor(
//                            this,
//                            com.nextgis.maplibui.R.color.color_grey_200
//                        ), PorterDuff.Mode.SRC_IN
//                    )
//                }
//            } else stopRefresh(refreshItem)
//        }
    }

    protected fun stopRefresh(refreshItem: MenuItem?) {
        val handler = Handler(Looper.getMainLooper())
        val r = Runnable {
            if (refreshItem != null && refreshItem.actionView != null) {
                refreshItem.actionView!!.clearAnimation()
                refreshItem.setActionView(null)
            }
        }
        handler.post(r)
    }


    fun addUnderlayFile() = chooseLocalFile(6410)
    fun addLocalLayer() = chooseLocalFile(FILE_SELECT_CODE)
    private fun chooseLocalFile(request: Int) {
        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
        // browser.
        // https://developer.android.com/guide/topics/providers/document-provider.html#client
        val intent = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Intent(Intent.ACTION_GET_CONTENT)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT)
        }
        intent.setType("*/*")
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.select_file)),
                request
            )
        } catch (ex: ActivityNotFoundException) {
            //TODO: open select local resource dialog
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(
                this, getString(R.string.warning_install_file_manager), Toast.LENGTH_SHORT
            )
                .show()
        }
    }



    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?) {
        //http://stackoverflow.com/questions/10114324/show-dialogfragment-from-onactivityresult
        //http://stackoverflow.com/questions/16265733/failure-delivering-result-onactivityforresult/18345899
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {

            6410 -> if (resultCode == RESULT_OK) {
                val uri = data?.data ?: return
                val name = FileUtil.getFileNameByUri(this, uri, "").lowercase(Locale.ROOT)
                if (name.endsWith(".ngrc") || name.endsWith(".mbtiles") || name.endsWith(".zip")) {
                    val fill = Intent(this, LayerFillService::class.java).apply {
                        action = LayerFillService.ACTION_ADD_TASK
                        putExtra(LayerFillService.KEY_URI, uri)
                        putExtra(LayerFillService.KEY_NAME, FileUtil.getFileNameByUri(this@MainActivity, uri, "").substringBeforeLast('.'))
                        putExtra(LayerFillService.KEY_INPUT_TYPE, LayerFillService.TMS_LAYER)
                        putExtra(LayerFillService.KEY_LAYER_GROUP_ID, (application as IGISApplication).map.id)
                        if (!name.endsWith(".ngrc")) putExtra(LayerFillService.KEY_TMS_TYPE, com.nextgis.maplib.util.GeoConstants.TMSTYPE_MBTILES_RASTER)
                    }
                    LayerFillProgressDialogFragment.startFill(fill)
                } else Toast.makeText(this, R.string.underlay_file_required, Toast.LENGTH_LONG).show()
            }

            CODE_TRACK_LIST -> mapFragment!!.mMapRef.get()!!.map.reloadTrackListToMap()


            FILE_SELECT_CODE -> if (resultCode == RESULT_OK) {
                // Get the Uri of the selected file
                val uri = data?.data
                if (Constants.DEBUG_MODE) Log.d(
                    Constants.TAG, "File Uri: " + (uri?.toString()
                        ?: "")
                )
                //check the file type from extension
                val fileName = FileUtil.getFileNameByUri(this, uri, "")
                if (fileName.lowercase(Locale.getDefault()).endsWith("mbtiles") ||
                    fileName.lowercase(Locale.getDefault()).endsWith("ngrc") ||
                    fileName.lowercase(Locale.getDefault()).endsWith("zip")
                ) { //create local tile layer
                    if (null != mapFragment) {
                        mapFragment!!.addLocalTMSLayer(uri)
                    }
                } else if (fileName.lowercase(Locale.getDefault()).let {
                        it.endsWith("geojson") || it.endsWith("kml") || it.endsWith("gpx")
                    }
                ) { //create local vector layer
                    if (null != mapFragment) {
                        mapFragment!!.addLocalVectorLayer(uri)
                    }
                } else if (fileName.lowercase(Locale.getDefault())
                        .endsWith("ngfp")
                ) { //create local vector layer with form
                    if (null != mapFragment) {
                        mapFragment!!.addLocalVectorLayerWithForm(uri)
                    }
                } else {
                    Toast.makeText(
                        this, getString(R.string.error_file_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            IVectorLayerUI.MODIFY_REQUEST -> mapFragment!!.onActivityResult(
                requestCode,
                resultCode,
                data
            )

            RELOAD_ACTIVITY_DATA -> if (resultCode == RESULT_OK) {
                finish()
                val intent = Intent(this, this.javaClass)
                startActivity(intent)
            }
        }
    }


    protected fun locateCurrentPosition() {
        if (!hasLocationPermissions()) {
            val permslist: MutableList<String> = ArrayList()
            permslist.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            permslist.add(Manifest.permission.ACCESS_FINE_LOCATION)
            requestPermissions(
                this,
                R.string.permissions,
                R.string.location_permissions,
                PERMISSIONS_REQUEST_LOC_SILENT,
                PERMISSIONS_REQUEST_LOC_SILENT,
                *permslist.toTypedArray<String>()
            ) //
        }

        if (null != mapFragment) {
            mapFragment!!.locateCurrentPosition()
        }
    }

    private fun updateMapRotationMenuItem(
        item: MenuItem?,
        enabled: Boolean = mapFragment?.isMapRotationEnabled == true
    ) {
        item ?: return
        item.setIcon(
            if (enabled) R.drawable.ic_map_rotation_enabled
            else R.drawable.ic_map_rotation_disabled
        )
        item.setTitle(
            if (enabled) R.string.disable_map_rotation
            else R.string.allow_map_rotation
        )
    }


    fun testSync() {
        val application = application as IGISApplication
        val map = application.map
        var ngwVectorLayer: NGWVectorLayer
        for (i in 0..<map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                ngwVectorLayer = layer
                val ver = NGWUtil.getNgwVersion(this, ngwVectorLayer.accountName)
                ngwVectorLayer.sync(application.authority, ver, SyncResult())
            }
        }
    }


    fun testUpdate() {
        //test sync
        val application = application as IGISApplication
        val map = application.map
        var ngwVectorLayer: NGWVectorLayer? = null
        for (i in 0..<map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                ngwVectorLayer = layer
            }
        }
        if (null != ngwVectorLayer) {
            val uri = Uri.parse(
                "content://" + AppSettingsConstants.AUTHORITY + "/" +
                        ngwVectorLayer.path.name
            )
            val updateUri = ContentUris.withAppendedId(uri, 29)
            val values = ContentValues()
            values.put("width", 4)
            values.put("azimuth", 8.0)
            values.put("status", "test4")
            values.put("temperatur", -10)
            values.put("name", "xxx")

            val calendar: Calendar = GregorianCalendar(2014, Calendar.JANUARY, 23)
            values.put("datetime", calendar.timeInMillis)
            try {
                val pt = GeoPoint(67.0, 65.0)
                pt.crs = GeoConstants.CRS_WGS84
                pt.project(GeoConstants.CRS_WEB_MERCATOR)
                val mpt = GeoMultiPoint()
                mpt.add(pt)
                values.put(Constants.FIELD_GEOM, mpt.toBlob())
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val result = contentResolver.update(updateUri, values, null, null)
            if (Constants.DEBUG_MODE) {
                if (result == 0) {
                    Log.d(Constants.TAG, "update failed")
                } else {
                    Log.d(Constants.TAG, "" + result)
                }
            }
        }
    }


    fun testAttachUpdate() {
        val application = application as IGISApplication
        /*MapBase map = application.getMap();
        NGWVectorLayer ngwVectorLayer = null;
        for(int i = 0; i < map.getLayerCount(); i++){
            ILayer layer = map.getLayer(i);
            if(layer instanceof NGWVectorLayer)
            {
                ngwVectorLayer = (NGWVectorLayer)layer;
            }
        }
        if(null != ngwVectorLayer) {
            Uri updateUri = Uri.parse("content://" + SettingsConstants.AUTHORITY + "/" +
                                      ngwVectorLayer.getPath().getName() + "/36/attach/1000");
        */
        val updateUri = Uri.parse(
            "content://" + AppSettingsConstants.AUTHORITY +
                    "/layer_20150210140455993/36/attach/2"
        )

        val values = ContentValues()
        values.put(VectorLayer.ATTACH_DISPLAY_NAME, "no_image.jpg")
        values.put(VectorLayer.ATTACH_DESCRIPTION, "simple update description")
        //    values.put(VectorLayer.ATTACH_ID, 999);
        val result = contentResolver.update(updateUri, values, null, null)
        if (Constants.DEBUG_MODE) {
            if (result == 0) {
                Log.d(Constants.TAG, "update failed")
            } else {
                Log.d(Constants.TAG, "" + result)
            }
        }
        //}
    }


    fun testAttachDelete() {
        val application = application as IGISApplication
        /*MapBase map = application.getMap();
        NGWVectorLayer ngwVectorLayer = null;
        for(int i = 0; i < map.getLayerCount(); i++){
            ILayer layer = map.getLayer(i);
            if(layer instanceof NGWVectorLayer)
            {
                ngwVectorLayer = (NGWVectorLayer)layer;
            }
        }
        if(null != ngwVectorLayer) {
            Uri deleteUri = Uri.parse("content://" + SettingsConstants.AUTHORITY + "/" +
                                ngwVectorLayer.getPath().getName() + "/36/attach/1000");
        */
        val deleteUri = Uri.parse(
            "content://" + AppSettingsConstants.AUTHORITY +
                    "/layer_20150210140455993/36/attach/1"
        )
        val result = contentResolver.delete(deleteUri, null, null)
        if (Constants.DEBUG_MODE) {
            if (result == 0) {
                Log.d(Constants.TAG, "delete failed")
            } else {
                Log.d(Constants.TAG, "" + result)
            }
        }
        //}
    }


    fun testAttachInsert() {
        val application = application as IGISApplication
        /*MapBase map = application.getMap();
        NGWVectorLayer ngwVectorLayer = null;
        for(int i = 0; i < map.getLayerCount(); i++){
            ILayer layer = map.getLayer(i);
            if(layer instanceof NGWVectorLayer)
            {
                ngwVectorLayer = (NGWVectorLayer)layer;
            }
        }
        if(null != ngwVectorLayer) {
            Uri uri = Uri.parse("content://" + SettingsConstants.AUTHORITY + "/" + ngwVectorLayer.getPath().getName() + "/36/attach");
        */
        val uri = Uri.parse(
            "content://" + AppSettingsConstants.AUTHORITY + "/layer_20150210140455993/36/attach"
        )
        val values = ContentValues()
        values.put(VectorLayer.ATTACH_DISPLAY_NAME, "test_image.jpg")
        values.put(VectorLayer.ATTACH_MIME_TYPE, "image/jpeg")
        values.put(VectorLayer.ATTACH_DESCRIPTION, "test image description")

        val result = contentResolver.insert(uri, values)
        if (result == null) {
            Log.d(Constants.TAG, "insert failed")
        } else {
            try {
                val outStream = contentResolver.openOutputStream(result)
                val sourceBitmap = BitmapFactory.decodeResource(
                    resources, com.nextgis.maplibui.R.drawable.bk_tile
                )
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outStream!!)
                outStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            if (Constants.DEBUG_MODE) Log.d(Constants.TAG, result.toString())
        }
        //}
    }


    fun testInsert() {
        //test sync
        val application = application as IGISApplication
        val map = application.map
        var ngwVectorLayer: NGWVectorLayer? = null
        for (i in 0..<map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                ngwVectorLayer = layer
            }
        }
        if (null != ngwVectorLayer) {
            val uri = Uri.parse(
                "content://" + AppSettingsConstants.AUTHORITY + "/" +
                        ngwVectorLayer.path.name
            )
            val values = ContentValues()
            //values.put(VectorLayer.FIELD_ID, 26);
            values.put("width", 1)
            values.put("azimuth", 2.0)
            values.put("status", "grot")
            values.put("temperatur", -13)
            values.put("name", "get")

            val calendar: Calendar = GregorianCalendar(2015, Calendar.JANUARY, 23)
            values.put("datetime", calendar.timeInMillis)

            try {
                val pt = GeoPoint(37.0, 55.0)
                pt.crs = GeoConstants.CRS_WGS84
                pt.project(GeoConstants.CRS_WEB_MERCATOR)
                val mpt = GeoMultiPoint()
                mpt.add(pt)
                values.put(Constants.FIELD_GEOM, mpt.toBlob())
            } catch (e: IOException) {
                e.printStackTrace()
            }
            val result = contentResolver.insert(uri, values)
            if (Constants.DEBUG_MODE) {
                if (result == null) {
                    Log.d(Constants.TAG, "insert failed")
                } else {
                    Log.d(Constants.TAG, result.toString())
                }
            }
        }
    }


    fun testDelete() {
        val application = application as IGISApplication
        val map = application.map
        var ngwVectorLayer: NGWVectorLayer? = null
        for (i in 0..<map.layerCount) {
            val layer = map.getLayer(i)
            if (layer is NGWVectorLayer) {
                ngwVectorLayer = layer
            }
        }
        if (null != ngwVectorLayer) {
            val uri = Uri.parse(
                "content://" + AppSettingsConstants.AUTHORITY + "/" +
                        ngwVectorLayer.path.name
            )
            val deleteUri = ContentUris.withAppendedId(uri, 27)
            val result = contentResolver.delete(deleteUri, null, null)
            if (Constants.DEBUG_MODE) {
                if (result == 0) {
                    Log.d(Constants.TAG, "delete failed")
                } else {
                    Log.d(Constants.TAG, "" + result)
                }
            }
        }
    }


    fun addNGWLayer() {
        if (null != mapFragment) {
            mapFragment!!.addNGWLayer()
        }
    }


    fun addRemoteLayer() {
        if (null != mapFragment) {
            mapFragment!!.addRemoteLayer()
        }
    }


    override fun onFinishChooseLayerDialog(
        code: Int,
        layer: ILayer
    , useCreatePoint: Boolean,
        startFillByWalk: Boolean) {
        if (null != mapFragment) {
            mapFragment!!.onFinishChooseLayerDialog(code, layer, useCreatePoint, startFillByWalk)
        }
    }


    private inner class MessageReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent ){

            if (intent.action == ConstantsUI.MESSAGE_INTENT) {
                Toast.makeText(
                    this@MainActivity, intent.extras!!.getString(
                        ConstantsUI.KEY_MESSAGE
                    ), Toast.LENGTH_SHORT
                ).show()
            }

            if (intent.action == Constants.MESSAGE_ALERT_INTENT) {
                val message = intent.extras!!.getString(Constants.MESSAGE_EXTRA)
                val title = intent.extras!!.getString(Constants.MESSAGE_TITLE_EXTRA)

                val s = SpannableString(message) // msg should have url to enable clicking
                Linkify.addLinks(s, Linkify.ALL)

                val builder = android.app.AlertDialog.Builder(this@MainActivity)
                builder.setMessage(s)
                    .setPositiveButton("ok", null)
                    .setTitle(title)
                val alertDialog = builder.create()
                alertDialog.show()

                (alertDialog.findViewById<View>(android.R.id.message) as TextView).movementMethod =
                    LinkMovementMethod.getInstance()
            }
        }
    }

    protected inner class TrackStartStopReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent ){
            if (intent.action == ConstantsUI.MESSAGE_INTENT_TRACK) {
                val tAction =  intent.getStringExtra(KEY_TRACK_ACTION)
                if (tAction.equals(VALUE_TRACK_START)){
                    mapFragment?.reloadTracks()
                }
                if (tAction.equals(VALUE_TRACK_STOP) || tAction.equals(VALUE_TRACK_POINT)){
                    mapFragment?.reloadTracks()
                }

                val batteryOK =  intent.getBooleanExtra(KEY_BATTERY, true)

                if (!batteryOK) {

                    val name = getPackageName() + "_preferences"
                    val mSharedPreferences = getSharedPreferences(name, MODE_MULTI_PROCESS)

                    val prefBatteryName =  "battery_dont_show_pref"
                    val dontShow = mSharedPreferences.getBoolean(prefBatteryName, false)

                    if (!dontShow) {
                        val container = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(50, 0, 50, 0)
                        }

                        val checkBox = CheckBox(this@MainActivity).apply {
                            text =
                                this@MainActivity.getString(com.nextgis.maplibui.R.string.do_not_ask_again)
                        }
                        container.addView(checkBox)

                        val builder = android.app.AlertDialog.Builder(this@MainActivity)
                        builder.setMessage(com.nextgis.maplibui.R.string.battery_optimization)
                            .setPositiveButton(com.nextgis.maplibui.R.string.battery_optimization_turnoff) { dialog, which ->
                                if (checkBox.isChecked) {
                                    mSharedPreferences.edit().putBoolean(prefBatteryName, true).apply()
                                }

                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.setData(Uri.parse("package:" + getPackageName()))
                                startActivity(intent)
                            }
                            .setTitle(com.nextgis.maplibui.R.string.battery_optimization_title)
                            .setNegativeButton(com.nextgis.maplibui.R.string.cancel) { dialog, which ->
                                if (checkBox.isChecked) {
                                    mSharedPreferences.edit().putBoolean(prefBatteryName, true).apply()
                                }
                            }
                            .setView(container)
                        val alertDialog = builder.create()
                        alertDialog.show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppUpdateManager.resumePendingInstallation(this)) {
            companionCheckedThisLaunch = true
            startupUpdateCheckPending = false
            startupUpdateCheckHandler.removeCallbacks(startupUpdateCheckRunnable)
        }
        val gisApp = application as IGISApplication
        if (gisApp.isLayerFillServiceBusy) {
            /* Defer: avoids re-entrancy with MapFragment/map resume and window token races after screen on. */
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing) {
                    LayerFillProgressDialogFragment.onMainMapActivityResume(this@MainActivity)
                }
            }
        }
        mToolbar!!.background.alpha = 128
        val intentFilter = IntentFilter()
        intentFilter.addAction(ConstantsUI.MESSAGE_INTENT)
        intentFilter.addAction(Constants.MESSAGE_ALERT_INTENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mMessageReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mMessageReceiver, intentFilter)
        }


        val intentFilterTrack = IntentFilter()
        intentFilterTrack.addAction(ConstantsUI.MESSAGE_INTENT_TRACK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mTrackReceiver, intentFilterTrack, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mTrackReceiver, intentFilterTrack)
        }

        mapFragment!!.reloadTracks()

        // Durable track-recording flag: silently resume after crash/reboot (no dialog).
        TrackerService.ensureRecordingRunningIfEnabled(this)

        maybeOfferCrashRecovery()
        if (SDCardUtils.isSDCardUsedAndExtracted(this)) {
            val builder = android.app.AlertDialog.Builder(this@MainActivity)
            builder.setMessage(com.nextgis.maplibui.R.string.no_sd_card_attention)
                .setPositiveButton(com.nextgis.maplibui.R.string.ok, null)
                .setTitle(com.nextgis.maplibui.R.string.sd_card)
            val alertDialog = builder.create()
            alertDialog.show()
        }

    }

    fun addNGWLayerByUrl() {
        val input = EditText(this).apply {
            hint = getString(R.string.ngw_resource_url_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_add_by_url)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importNGWResourceByUrl(input.text?.toString().orEmpty())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importNGWResourceByUrl(rawUrl: String) {
        if (ngwUrlImportInProgress) {
            Toast.makeText(this, R.string.ngw_url_import_in_progress, Toast.LENGTH_SHORT).show()
            return
        }

        val parsed = try {
            NGWResourceUrl.parse(rawUrl)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(this, R.string.ngw_resource_url_invalid, Toast.LENGTH_LONG).show()
            return
        }

        ngwUrlImportInProgress = true
        mapFragment?.changeProgress(true)
        ngwUrlExecutor.execute {
            val resolved = resolveNGWResource(parsed)
            runOnUiThread {
                ngwUrlImportInProgress = false
                mapFragment?.changeProgress(false)
                if (isFinishing || isDestroyed) {
                    return@runOnUiThread
                }
                handleResolvedNGWResource(resolved)
            }
        }
    }

    private fun resolveNGWResource(parsed: NGWResourceUrl): NGWUrlResolution {
        val connections = NetworkUtil.fillConnections(this, AccountManager.get(this))
        var connection: Connection? = null
        for (index in 0 until connections.childrenCount) {
            val candidate = connections.getChild(index)
            if (candidate is Connection && parsed.matchesServerUrl(candidate.url)) {
                connection = candidate
                break
            }
        }

        val needsGuestAccount = connection == null
        val targetConnection = connection ?: Connection(
            parsed.accountName,
            Constants.NGW_ACCOUNT_GUEST,
            "",
            parsed.serverUrl
        )
        val guest = Constants.NGW_ACCOUNT_GUEST == targetConnection.login
        if (!targetConnection.connect(guest, parsed.resourceId)) {
            return NGWUrlResolution(parsed, null, 401, needsGuestAccount)
        }

        val loaded = targetConnection.rootResource.loadTargetResource()
        return NGWUrlResolution(
            parsed,
            loaded.resource,
            loaded.responseCode,
            needsGuestAccount
        )
    }

    private fun handleResolvedNGWResource(resolved: NGWUrlResolution) {
        val resource = resolved.resource
        if (resource == null) {
            val message = when (resolved.responseCode) {
                401, 403 -> R.string.ngw_resource_permission_denied
                404 -> R.string.ngw_resource_not_found
                in 200..299 -> R.string.ngw_resource_type_unsupported
                else -> R.string.ngw_resource_load_failed
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        if (!NGWResourceImportHelper.supports(resource)) {
            Toast.makeText(this, R.string.ngw_resource_type_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        if (!resource.hasDataReadPermission()) {
            Toast.makeText(this, R.string.ngw_resource_permission_denied, Toast.LENGTH_LONG).show()
            return
        }

        if (resolved.needsGuestAccount) {
            val app = application as IGISApplication
            val accountAdded = app.addAccount(
                resolved.parsed.accountName,
                resolved.parsed.serverUrl,
                Constants.NGW_ACCOUNT_GUEST,
                "",
                Constants.NGW_ACCOUNT_GUEST
            )
            if (!accountAdded) {
                Toast.makeText(this, R.string.ngw_guest_account_failed, Toast.LENGTH_LONG).show()
                return
            }
        }

        val group = (application as IGISApplication).map as? LayerGroup
        val result = NGWResourceImportHelper.importResource(this, group, resource)
        val message = when (result) {
            NGWResourceImportHelper.Result.VECTOR_QUEUED -> R.string.ngw_resource_import_started
            NGWResourceImportHelper.Result.RASTER_ADDED -> R.string.ngw_resource_added
            NGWResourceImportHelper.Result.READ_PERMISSION_DENIED ->
                R.string.ngw_resource_permission_denied
            NGWResourceImportHelper.Result.UNSUPPORTED -> R.string.ngw_resource_type_unsupported
            NGWResourceImportHelper.Result.FAILED -> R.string.ngw_resource_load_failed
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        if (result == NGWResourceImportHelper.Result.RASTER_ADDED) {
            mLayersFragment?.onResume()
        }
    }

    private data class NGWUrlResolution(
        val parsed: NGWResourceUrl,
        val resource: Resource?,
        val responseCode: Int,
        val needsGuestAccount: Boolean
    )

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            startupUpdateCheckHandler.removeCallbacks(startupUpdateCheckRunnable)
            startupUpdateCheckHandler.postDelayed(
                startupUpdateCheckRunnable,
                STARTUP_UPDATE_CHECK_DELAY_MS
            )
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (null != mLayersFragment && !mLayersFragment!!.isDrawerOpen) {
            val recording = TrackerService.isTrackerServiceRunning(this)
                    || TrackerService.isTrackRecordingEnabled(this)
            val title =
                if (recording) com.nextgis.maplibui.R.string.track_stop else com.nextgis.maplibui.R.string.track_start
            val icon =
                if (recording) com.nextgis.maplibui.R.drawable.ic_action_maps_directions_walk_rec else com.nextgis.maplibui.R.drawable.ic_action_maps_directions_walk
            setTrackItem(menu.findItem(R.id.menu_track), title, icon)
        }

        if (mapFragment!!.isRulerMeasuring) showRulerToolbar()
        else if (mapFragment!!.isEditMode) showEditToolbar()

        val log = menu.findItem(R.id.menu_share_log)
        log?.setVisible(mPreferences.getBoolean("save_log", true))
        updateMapRotationMenuItem(menu.findItem(R.id.menu_map_rotation))

        return super.onPrepareOptionsMenu(menu)
    }


    override fun onPause() {
        try {
            if (mMessageReceiver != null) {
                unregisterReceiver(mMessageReceiver)
                //mMessageReceiver = null
            }

            if (mTrackReceiver != null) {
                unregisterReceiver(mTrackReceiver)
                //mTrackReceiver = null
            }

        } catch (ignored: Exception) {
            HyperLog.w(Constants.TAG, "MainActivity.onPause: " + ignored.message, ignored)
        }

        super.onPause()
    }

    override fun onBackPressed() {
        if (finishFragment()) return

        if (mBackPressed + 2000 > System.currentTimeMillis()) super.onBackPressed()
        else Toast.makeText(this, R.string.press_aback_again, Toast.LENGTH_SHORT).show()

        mBackPressed = System.currentTimeMillis()
    }

    override fun onLocationChanged(location: Location) {
    }

    override fun onBestLocationChanged(location: Location) {
    }


    override fun onGpsStatusChanged(event: Int) {
    }


    fun setActionBarState(state: Boolean) {
        mLayersFragment!!.isDrawerToggleEnabled = state

        if (state) {
            mToolbar!!.background.alpha = 128
            bottomToolbar.background.alpha = 128
        } else {
            mToolbar!!.background.alpha = 255
            bottomToolbar.background.alpha = 255
        }
    }


    fun hideBottomBar() {
        mapFragment!!.hideBottomBar()
    }


    fun restoreBottomBar(mode: Int) {
        if (mapFragment!!.isAdded) mapFragment!!.restoreBottomBar(mode)
    }

    fun setSubtitle(subtitle: String?) {
        mToolbar!!.subtitle = subtitle
    }

    fun requestPermissions(
        activity1: Activity, title: Int, message: Int, requestCode: Int,
        nextLevelOndeny: Int,
        vararg permissions: String?
    ) {
        val activity = activity1
        if (true) {
            val builder = AlertDialog.Builder(activity).setTitle(title)
                .setMessage(message)
                .setPositiveButton(
                    com.nextgis.maplibui.R.string.allow
                ) { dialog: DialogInterface?, which: Int ->
                    ActivityCompat.requestPermissions(
                        activity,
                        permissions,
                        requestCode
                    )
                }
                .setNegativeButton(
                    com.nextgis.maplibui.R.string.deny
                ) { dialog: DialogInterface?, which: Int ->
                    if (nextLevelOndeny > 0) processAllPermisions(nextLevelOndeny)
                }
                .create()
            builder.setCanceledOnTouchOutside(false)
            builder.show()
        }
    }

    companion object {
        protected const val PERMISSIONS_REQUEST_ZERO: Int = 0
        protected const val PERMISSIONS_REQUEST_LOC: Int = 1
        protected const val PERMISSIONS_REQUEST_ACCOUNT: Int = 2
        protected const val PERMISSIONS_REQUEST_MEMORY: Int = 3
        protected const val PERMISSIONS_REQUEST_PUSH: Int = 4

        protected const val PERMISSIONS_REQUEST_LOC_SILENT: Int = 6
        const val LOCATION_BACKGROUND_REQUEST: Int = 5
        protected const val TAG_FRAGMENT_PROGRESS: String = "layer_fill_dialog_fragment"
        private const val STARTUP_UPDATE_CHECK_DELAY_MS: Long = 2500

        protected const val FILE_SELECT_CODE: Int = 555
        protected const val RELOAD_ACTIVITY_DATA: Int = 777
    }

    override fun onDestroy() {
        HyperLog.v(Constants.TAG, "MainActivity.onDestroy")
        startupUpdateCheckHandler.removeCallbacks(startupUpdateCheckRunnable)
        ngwUrlExecutor.shutdownNow()
        mMessageReceiver = null
        mTrackReceiver = null
        super.onDestroy()
    }

    override fun refreshLayersFrarment() {
        super.refreshLayersFrarment()
        if (mLayersFragment != null)
            mLayersFragment?.onResume()
    }

    public fun showToast( text: String ){
        Toast.makeText(this, text, Toast.LENGTH_LONG)
            .show()
    }

//    override fun startLayerPropFragment(fragment: Fragment) {
//        super.startLayerPropFragment(fragment)
//
//
//        supportFragmentManager.beginTransaction()
//            .replace(R.id.settingsFrame, fragment)
//            .addToBackStack(null)
//            .commit()
//
//
//    }
}
