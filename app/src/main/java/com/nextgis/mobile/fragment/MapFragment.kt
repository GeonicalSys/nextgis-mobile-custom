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
package com.nextgis.mobile.fragment

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Vibrator
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.getbase.floatingactionbutton.FloatingActionButton
import com.getbase.floatingactionbutton.FloatingActionsMenu
import com.google.android.material.snackbar.Snackbar
import com.nextgis.maplib.api.GpsEventListener
import com.nextgis.maplib.api.IGISApplication
import com.nextgis.maplib.api.ILayer
import com.nextgis.maplib.datasource.Feature
import com.nextgis.maplib.datasource.Geo
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.datasource.GeoGeometry
import com.nextgis.maplib.datasource.GeoGeometryFactory
import com.nextgis.maplib.datasource.GeoLineString
import com.nextgis.maplib.datasource.GeoLinearRing
import com.nextgis.maplib.datasource.GeoMultiLineString
import com.nextgis.maplib.datasource.GeoMultiPoint
import com.nextgis.maplib.datasource.GeoMultiPolygon
import com.nextgis.maplib.datasource.GeoPoint
import com.nextgis.maplib.datasource.GeoPolygon
import com.nextgis.maplib.location.GpsEventSource
import com.nextgis.maplib.map.Layer
import com.nextgis.maplib.map.LayerIdentifyPolicy
import com.nextgis.maplib.map.LayerGroup
import com.nextgis.maplib.map.MLP.MLGeometryEditClass
import com.nextgis.maplib.map.MPLFeaturesUtils
import com.nextgis.maplib.map.MapDrawable
import com.nextgis.maplib.map.MaplibreMapInteraction
import com.nextgis.maplib.map.VectorLayer
import com.hypertrack.hyperlog.HyperLog
import com.nextgis.maplib.util.Constants
import com.nextgis.maplib.util.Constants.MESSAGE_INTENT_RELOAD
import com.nextgis.maplib.util.Constants.MESSAGE_INTENT_STYLING
import com.nextgis.maplib.util.FileUtil
import com.nextgis.maplib.util.GeoConstants
import com.nextgis.maplib.util.LocationUtil
import com.nextgis.maplib.util.MapUtil
import com.nextgis.maplib.util.MultiPolygonGeometryRepair
import com.nextgis.maplib.util.StakeoutGeometryTarget
import com.nextgis.maplibui.GISApplication
import com.nextgis.maplibui.api.EditEventListener
import com.nextgis.maplibui.api.ILayerUI
import com.nextgis.maplibui.api.IVectorLayerUI
import com.nextgis.maplibui.api.MapViewEventListener
import com.nextgis.maplibui.dialog.ChooseLayerDialog
import com.nextgis.maplibui.fragment.CompassFragment
import com.nextgis.maplibui.mapui.MapViewOverlays
import com.nextgis.maplibui.overlay.CurrentLocationOverlay
import com.nextgis.maplibui.overlay.CurrentTrackOverlay
import com.nextgis.maplibui.overlay.EditLayerOverlay
import com.nextgis.maplibui.overlay.RulerOverlay
import com.nextgis.maplibui.overlay.RulerOverlay.OnRulerChanged
import com.nextgis.maplibui.overlay.UndoRedoOverlay
import com.nextgis.maplibui.service.TrackerService
import com.nextgis.maplibui.service.WalkEditService
import com.nextgis.maplibui.util.WalkSessionStore
import com.nextgis.maplibui.util.FeatureFormDraftStore
import com.nextgis.maplibui.util.LayerUtil
import com.nextgis.maplibui.util.WalkSessionPolicy
import com.nextgis.maplibui.view.WalkRecordingPanel
import com.nextgis.maplibui.util.ConstantsUI
import com.nextgis.maplibui.util.ControlHelper
import com.nextgis.maplibui.util.GeometryEditDraftStore
import com.nextgis.maplibui.util.NotificationHelper
import com.nextgis.maplibui.util.SettingsConstantsUI
import com.nextgis.mobile.MainApplication
import com.nextgis.mobile.R
import com.nextgis.mobile.activity.MainActivity
import com.nextgis.mobile.util.AppConstants
import com.nextgis.mobile.util.AppSettingsConstants
import com.nextgis.mobile.stakeout.StakeoutController
import com.nextgis.mobile.stakeout.MagneticAzimuthCalculator
import com.nextgis.mobile.stakeout.WorldMagneticModel2025
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView as MapLibreMapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.renderer.MapRenderer
import org.maplibre.android.module.http.HttpRequestImpl
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPoint
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Main map fragment
 */
public class MapFragment

    : Fragment(), MapViewEventListener, GpsEventListener, EditEventListener,
    View.OnClickListener, OnRulerChanged,
    OnMapReadyCallback ,
    MaplibreMapInteraction,
    MapLibreMap.OnCameraIdleListener
{
    protected var mTolerancePX: Float = 0f

    protected var mPreferences: SharedPreferences? = null
    protected var mApp: MainApplication? = null
    protected var mActivity: MainActivity? = null

    lateinit var mMapRef: WeakReference<MapViewOverlays>
    private var mapLibreMapView: MapLibreMapView? = null
    @Volatile
    private var awaitingMapLibreFrameAfterResume = false
    private var mapLibreRenderRecoveryActive = false
    private var mapLibreRenderRecoveryAttempt = 0
    private var mapLibreRenderRecoveryFrames = 0
    private var mapLibreRenderRecoveryReason = ""
    private var mapLibreLayersAppliedForCurrentView = false
    private var mapLibreLastFrameFullyRendered = false
    private var mapLibreLoadingForegroundCleared = false
    private var mapLibrePreviousRefreshMode: MapRenderer.RenderingRefreshMode? = null
    private var mapLibreHostResumed = false
    private val mapLibreRenderRecoveryRunnable = Runnable {
        runMapLibreRenderRecoveryAttempt()
    }
    private val mapLibreFrameListener =
        MapLibreMapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
            if (awaitingMapLibreFrameAfterResume) {
                awaitingMapLibreFrameAfterResume = false
                HyperLog.v(
                    Constants.TAG,
                    "MapLibreMapView first frame after resume fully=$fully"
                )
            }
            if (mapLibreRenderRecoveryActive) {
                mapLibreLastFrameFullyRendered = fully
                val styleFullyLoaded = mapDrawableOrNull
                    ?.maplibreMap
                    ?.style
                    ?.isFullyLoaded == true
                if (styleFullyLoaded) {
                    mapLibreRenderRecoveryFrames++
                }
                val mapLibreView = mapLibreMapView
                val loadingForegroundVisible = mapLibreView?.foreground != null
                if (fully &&
                    !loadingForegroundVisible &&
                    mapLibreRenderRecoveryAttempt >= MAPLIBRE_MIN_PRESENTATION_ATTEMPTS
                ) {
                    finishMapLibreRenderRecovery(
                        "frame-ready fully=$fully styleFullyLoaded=$styleFullyLoaded " +
                            "loadingForeground=$loadingForegroundVisible"
                    )
                }
            }
        }
    private val mapLibreLoadFailureListener =
        MapLibreMapView.OnDidFailLoadingMapListener { errorMessage ->
            HyperLog.e(
                Constants.TAG,
                "MapLibreMapView load failed: ${errorMessage.take(500)}"
            )
        }


    protected var mivZoomIn: FloatingActionButton? = null
    protected var mivZoomOut: FloatingActionButton? = null
    protected var mRuler: FloatingActionButton? = null
    protected var mAzimuth: FloatingActionButton? = null
    protected var mAddNewGeometry: FloatingActionButton? = null
    protected var mAddPointButton: FloatingActionButton? = null

    protected var mStatusSource: TextView? = null
    protected var mStatusAccuracy: TextView? = null
    protected var mStatusSpeed: TextView? = null
    protected var mStatusAltitude: TextView? = null
    protected var mStatusLatitude: TextView? = null
    protected var mStatusLongitude: TextView? = null
    protected var mZoom: TextView? = null
    protected var mStatusPanel: FrameLayout? = null
    protected var mScaleRulerLayout: LinearLayout? = null
    protected var mScaleRulerText: TextView? = null
    private var mStakeoutPanel: View? = null
    private var mStakeoutDirection: ImageView? = null
    private var mStakeoutDistance: TextView? = null
    private var mStakeoutAzimuth: TextView? = null
    private var mStakeoutDetails: TextView? = null
    private var mStakeoutSound: ImageButton? = null
    private var mStakeoutStop: ImageButton? = null
    private var mStakeoutController: StakeoutController? = null
    private var lastStakeoutUiState: StakeoutController.UiState? = null
    private var azimuthStartPoint: GeoPoint? = null
    private var azimuthTargetPoint: GeoPoint? = null
    private var azimuthStaticTrueBearing: Float? = null

    //, mZoomLevel;
    protected var mScaleRuler: ImageView? = null
    protected var mCenterCross: ImageView? = null

    var stylingProgrerss: View? = null;
    var textStylingProgrerss: TextView? = null;

    private var mMessageStyling: MessageStyling? = null
    private var mMessageReload: MessageReloadLayer? = null


    protected var mMapRelativeLayout: RelativeLayout? = null
    protected var mGpsEventSource: GpsEventSource? = null
    protected var mMainButton: FloatingActionsMenu? = null
    var mode: Int = 0
        protected set
    protected var mCurrentLocationOverlay: CurrentLocationOverlay? = null
    protected var mCurrentTrackOverlay: CurrentTrackOverlay? = null
    var editLayerOverlay: EditLayerOverlay? = null
        protected set
    var undoRedoOverlay: UndoRedoOverlay? = null
        protected set
    protected var mRulerOverlay: RulerOverlay? = null
    private var preserveRulerHistoryDuringModeRestore = false
    protected var mCurrentCenter: GeoPoint? = null
    protected var mSelectedLayer: VectorLayer? = null

    protected var mCoordinatesFormat: Int = 0
    protected var mCoordinatesFraction: Int = 0

    protected var mChooseLayerDialogRef: WeakReference<ChooseLayerDialog> = WeakReference(null)

    protected var mGPSDialog: AlertDialog? = null
    protected var mVibrator: Vibrator? = null

    protected var mIsCompassDragging: Boolean = false
    protected var mStatusPanelMode: Int = 0
    protected var mModeListener: onModeChange? = null
    public var mFinishListener: View.OnClickListener? = null

    protected val ADD_CURRENT_LOC: Int = 1
    protected val ADD_GEOMETRY_BY_WALK: Int = 3
    protected val ADD_POINT_BY_TAP: Int = 4
    private var mNeedSave = false
    private var mEditAttributesFormFromEditMode = false
    private var mNewFeatureFormLaunchInProgress = false

    var longClickProcessed = false

    /** Soft-interrupt: walk mode or orphan draft while WalkEditService is dead. */
    private var walkInterruptPromptShown = false
    /** Set by MainActivity recovery hub to avoid duplicate walk dialogs. */
    var crashRecoveryWalkDialogShown = false
    /** Continue can be pressed before MapLibre has finished rebuilding its editable sources. */
    private var pendingManualGeometryResume = false
    private var pendingManualGeometrySnapshot: GeometryEditDraftStore.Snapshot? = null
    private var manualGeometryResumeRetryCount = 0
    private val manualGeometryResumeMaxRetries = 35
    private val manualGeometryResumeRunnable = Runnable {
        tryResumeManualGeometryFromDraft()
    }

    /** Bounded delayed retries when MapLibre is not ready at end of layer-fill batch. */
    private var mapReloadAfterFillRetryCount = 0
    private val mapReloadAfterFillMaxRetries = 35
    private var mapReloadAfterFillRetryRunnable: Runnable? = null
    private var mapReloadAfterFillAwaitingCompletion = false

    private val mapViewOrNull get() = mMapRef.get()
    private val mapDrawableOrNull get() = mapViewOrNull?.map

    interface onModeChange {
        fun onModeChangeListener()
    }

    fun setOnModeChangeListener(listener: onModeChange?) {
        mModeListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        HyperLog.v(Constants.TAG, "MapFragment.onCreate")
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        mActivity = activity as MainActivity?
        mTolerancePX = mActivity!!.resources.displayMetrics.density * ConstantsUI.TOLERANCE_DP

        mPreferences = PreferenceManager.getDefaultSharedPreferences(mActivity)
        mApp = mActivity!!.application as MainApplication
        mVibrator = mActivity!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        mGpsEventSource = mApp!!.gpsEventSource
        mStakeoutController = StakeoutController(
            mActivity!!,
            mPreferences!!,
            mGpsEventSource!!,
            object : StakeoutController.Listener {
                override fun onStakeoutStateChanged(state: StakeoutController.UiState) {
                    updateStakeoutWidget(state)
                }
            }
        )

        mMapRef = WeakReference(MapViewOverlays(mActivity, mApp!!.map as MapDrawable))

        mMapRef.get()!!.id = R.id.map_view

        editLayerOverlay = EditLayerOverlay(mActivity, mMapRef.get())

        mMessageStyling = MessageStyling()
        mMessageReload = MessageReloadLayer()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {
        HyperLog.v(Constants.TAG, "MapFragment.onCreateView")
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        walkPreviewKey = null
        finishedWalkEditId = savedInstanceState?.getString("finished_walk_edit_id")
        attachWalkPanel(view)

        mCurrentLocationOverlay = CurrentLocationOverlay(mActivity, mMapRef.get())
        mCurrentLocationOverlay!!.setStandingMarker(R.mipmap.ic_location_standing)
        mCurrentLocationOverlay!!.setMovingMarker(R.mipmap.ic_location_moving)
        mCurrentLocationOverlay!!.setAutopanningEnabled(true)

        mCurrentTrackOverlay = CurrentTrackOverlay(mActivity, mMapRef.get())
        mRulerOverlay = RulerOverlay(mActivity, mMapRef.get())
        undoRedoOverlay = UndoRedoOverlay(mActivity, mMapRef.get())

        mMapRef.get()!!.addOverlay(mCurrentTrackOverlay)
        mMapRef.get()!!.addOverlay(mCurrentLocationOverlay)
        mMapRef.get()!!.addOverlay(editLayerOverlay)
        mMapRef.get()!!.addOverlay(undoRedoOverlay)
        mMapRef.get()!!.addOverlay(mRulerOverlay)

        stylingProgrerss = view.findViewById(R.id.stylingProgress)
        textStylingProgrerss= view.findViewById(R.id.textStyling)

        stylingProgrerss?.setOnClickListener { null }

        //search relative view of map, if not found - add it
        mMapRelativeLayout = view.findViewById(R.id.maprl)
        if (mMapRelativeLayout != null) {
            mMapRelativeLayout!!.addView(
                mMapRef.get(), 0, RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                )
            )
        }


        var mapZoom = try {
            mPreferences!!.getFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, mMapRef.get()!!.minZoom)
        } catch (e: ClassCastException) {
            mMapRef.get()!!.minZoom
        }

        var mapScrollX: Double
        var mapScrollY: Double
        try {
            mapScrollX = java.lang.Double.longBitsToDouble(
                mPreferences!!.getLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_X,
                    0
                )
            )
            mapScrollY = java.lang.Double.longBitsToDouble(
                mPreferences!!.getLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_Y,
                    0
                )
            )
        } catch (e: ClassCastException) {
            mapScrollX = 0.0
            mapScrollY = 0.0
        }
        mMapRef.get()!!.setZoomAndCenter(mapZoom, GeoPoint(mapScrollX, mapScrollY))

        mMainButton = view.findViewById(R.id.multiple_actions)
        mAddPointButton = view.findViewById(R.id.add_point_by_tap)
        mAddPointButton?.setOnClickListener(this)

        val addCurrentLocation = view.findViewById<View>(R.id.add_current_location)
        addCurrentLocation.setOnClickListener(this)

        mAddNewGeometry = view.findViewById(R.id.add_new_geometry)
        mAddNewGeometry?.setOnClickListener(this)
        mRuler = view.findViewById(R.id.action_ruler)
        mRuler?.setOnClickListener(this)
        mAzimuth = view.findViewById(R.id.action_azimuth)
        mAzimuth?.setOnClickListener(this)

        val addGeometryByWalk = view.findViewById<View>(R.id.add_geometry_by_walk)
        addGeometryByWalk.setOnClickListener(this)

        mivZoomIn = view.findViewById(R.id.action_zoom_in)
        mivZoomIn?.setOnClickListener(this)

        mivZoomOut = view.findViewById(R.id.action_zoom_out)
        mivZoomOut?.setOnClickListener(this)

        mStatusPanel = view.findViewById(R.id.fl_status_panel)
        mCenterCross = view.findViewById(R.id.iv_center_cross)
        mScaleRuler = view.findViewById(R.id.iv_ruler)
        mScaleRulerText = view.findViewById(R.id.tv_ruler)
        mScaleRulerText?.setText(rulerText)

        //        mZoomLevel = view.findViewById(R.id.tv_zoom_level);
//        mZoomLevel.setText(getZoomText());
        if (mZoom != null) mZoom!!.text = zoomText

        mScaleRulerLayout = view.findViewById(R.id.ll_ruler)
        mStakeoutPanel = view.findViewById(R.id.stakeout_panel)
        mStakeoutDirection = view.findViewById(R.id.stakeout_direction)
        mStakeoutDistance = view.findViewById(R.id.stakeout_distance)
        mStakeoutAzimuth = view.findViewById(R.id.stakeout_azimuth)
        mStakeoutDetails = view.findViewById(R.id.stakeout_details)
        mStakeoutSound = view.findViewById(R.id.stakeout_sound)
        mStakeoutStop = view.findViewById(R.id.stakeout_stop)
        mStakeoutSound?.setOnClickListener { mStakeoutController?.toggleMuted() }
        mStakeoutStop?.setOnClickListener { setNewMode(MODE_NORMAL) }
        drawScaleRuler()

        return view
    }

    override fun changeProgress(show: Boolean) {
        if (show) {
            mapLibreLayersAppliedForCurrentView = false
            stylingProgrerss?.visibility = View.VISIBLE
            // MAP_STARTUP_UX_EXTRAS: default caption — see Constants.MAP_STARTUP_UX_EXTRAS_ENABLED
            textStylingProgrerss?.text =
                if (com.nextgis.maplib.util.Constants.MAP_STARTUP_UX_EXTRAS_ENABLED) {
                    context?.getString(com.nextgis.maplib.R.string.map_loading_preparing) ?: ""
                } else ""
        } else {
            stylingProgrerss?.visibility = View.GONE
            textStylingProgrerss?.text = ""
            if (mapDrawableOrNull?.maplibreMap?.style != null) {
                mapLibreLayersAppliedForCurrentView = true
                startMapLibreRenderRecovery("styling-complete")
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapViewMaplibre = view.findViewById<MapLibreMapView>(R.id.mapViewMaplibre)
        mapLibreMapView = mapViewMaplibre
        mapViewMaplibre.addOnDidFinishRenderingFrameListener(mapLibreFrameListener)
        mapViewMaplibre.addOnDidFailLoadingMapListener(mapLibreLoadFailureListener)

        mMapRef.get()!!.map!!.maplibreMapView = mapViewMaplibre

        // So GISApplication.mMap.mapContext.get() is non-null as soon as the fragment view exists
        // (e.g. after resetMap() + new MapDrawable) — before getMapAsync/onMapReady.
        mMapRef.get()!!.map!!.setMapContext(this)

        mapViewMaplibre.onCreate(savedInstanceState)
        HyperLog.v(
            Constants.TAG,
            "MapLibreMapView renderer=${mapViewMaplibre.renderView.javaClass.simpleName} " +
                "sdk=${Build.VERSION.SDK_INT} tilePrefetch=" +
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        )

        mapViewMaplibre.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapLibreMap) {
        HyperLog.v(Constants.TAG, "MapFragment.onMapReady")
        val mapRef = mMapRef.get()
        val mapDrawable = mapRef?.map
        if (mapRef == null || mapDrawable == null) {
            HyperLog.e(Constants.TAG, "onMapReady: app map/drawable is null, cannot load layers")
            return
        }
        mapDrawable.setMapContext(this)

        val  interceptor = (mApp as IGISApplication).getAuthInterceptor();

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dispatcher(getDispatcher())
            .build()

        // set global http client for raster auth
        HttpRequestImpl.setOkHttpClient(client)

        mapDrawable.maplibreMap = mapboxMap

        configureMapRotationGestures(mapboxMap, isMapRotationEnabled)
        mapboxMap.uiSettings.isCompassEnabled = false

        val restoredBearing = if (isMapRotationEnabled) {
            try {
                mPreferences?.getFloat(AppSettingsConstants.KEY_PREF_MAP_BEARING, 0f) ?: 0f
            } catch (_: ClassCastException) {
                0f
            }
        } else {
            0f
        }
        setMapBearing(mapboxMap, restoredBearing.toDouble(), false)

        mapboxMap.addOnCameraIdleListener(this)

        // ngwstyle.json missing/unreadable would make setStyle(fromJson(null)) crash; abort with a
        // logged, user-visible error instead so the rest of the app stays usable.
        val styleJson = loadJsonFromAssets(requireContext(), "ngwstyle.json")
        if (styleJson == null) {
            HyperLog.e(Constants.TAG, "onMapReady: failed to load ngwstyle.json; map style not applied")
            context?.let {
                Toast.makeText(it, it.getString(com.nextgis.maplib.R.string.error), Toast.LENGTH_LONG).show()
            }
            return
        }

        val allLayers = mapRef.getAllLayers()

        mapDrawable.loadLayersToMaplibreMap(styleJson, allLayers, true, true)
    }

    override fun checkCreateIfNeed() {
        // Upstream collector hook: invoked after map style finished loading.
        // No collector auto-create flow in this fork — left as a stable no-op until needed.
    }

    override fun setMapLayersLoaded() {
        mapLibreLayersAppliedForCurrentView = true
        startMapLibreRenderRecovery("layers-applied")
        updateLastLocation()
        updateAzimuthOverlayFromLatestLocation()
        if (mapReloadAfterFillAwaitingCompletion) {
            mapReloadAfterFillAwaitingCompletion = false
            (mApp as? IGISApplication)?.clearMapReloadAfterLayerFillPending()
            HyperLog.v(
                Constants.TAG,
                "reloadMapStyleAfterLayerFill: completed after MapLibre style/source apply"
            )
        }
        if (pendingManualGeometryResume) {
            HyperLog.v(Constants.TAG, "GeometryDraft retry after MapLibre layers loaded")
            tryResumeManualGeometryFromDraft()
        }
    }

    override fun loadLayersLite(){
        val mapDrawable = mMapRef.get()?.map ?: return
        val mapLibreMap = mapDrawable.maplibreMap ?: return
        if (mapLibreMap.style == null) {
            return
        }
        val allLayers = mMapRef.get()!!.getAllLayers()
        mapDrawable.loadLayersToMaplibreMapLite(allLayers, false)
        updateLastLocation()
    }

    override fun reloadMapStyleAndLayersAfterLayerFillBatch(): Boolean {
        val mapRef = mMapRef.get() ?: return false
        val mapDrawable = mapRef.map ?: return false
        mapReloadAfterFillAwaitingCompletion = true
        if (mapDrawable.getMaplibreMap() == null) {
            scheduleMapReloadAfterLayerFillRetry()
            return false
        }
        mapReloadAfterFillRetryCount = 0
        val started = doReloadMapStyleAndLayersAfterLayerFillBatch()
        if (!started) {
            scheduleMapReloadAfterLayerFillRetry()
        }
        return started
    }

    override fun reloadLayerStyle(layerId: Int) {
        mMapRef.get()?.map?.reloadVectorLayerStyleToMaplibre(layerId)
    }

    private fun doReloadMapStyleAndLayersAfterLayerFillBatch(): Boolean {
        val mapRef = mMapRef.get() ?: return false
        val mapDrawable = mapRef.map ?: return false
        if (mapDrawable.getMaplibreMap() == null) {
            return false
        }
        val ctx = context ?: return false
        val styleJson = loadJsonFromAssets(ctx, "ngwstyle.json") ?: return false
        val vectorLayers = mapRef.getVectorLayersByType(GeoConstants.GTAnyCheck)
        val layersTrack = mapRef.getLayersByType(Constants.LAYERTYPE_TRACKS)
        vectorLayers.addAll(layersTrack)
        val allLayers = mapRef.getAllLayers()
        mapDrawable.loadLayersToMaplibreMap(styleJson, allLayers, true, true)
        return true
    }

    /**
     * [MapViewOverlays] keeps a final [MapDrawable] from [onCreate]. [GISApplication.resetMap]
     * replaces the app map with a new instance while the view can still reference the old drawable,
     * so imports attach to the new map and the UI stays empty. If we detect that, recreate
     * [MainActivity] to rebuild the map view. When instances match, refresh the weak
     * [MapDrawable.mapFragment] link (also set early in [onViewCreated] before [onMapReady]).
     */
    private fun ensureMapViewBoundToApplicationMap() {
        val appMap = try {
            (mApp as MainApplication).map as MapDrawable
        } catch (e: ClassCastException) {
            return
        }
        val viewMap = mMapRef.get()?.map as? MapDrawable ?: return
        if (viewMap !== appMap) {
            HyperLog.w(
                Constants.TAG,
                "MapFragment: MapView MapDrawable != application map; recreating activity to rebind"
            )
            mActivity?.recreate()
            return
        }
        viewMap.setMapContext(this)
    }

    private fun startMapLibreRenderRecovery(reason: String) {
        val mapLibreView = mapLibreMapView ?: return
        if (!mapLibreRenderRecoveryActive) {
            try {
                mapLibrePreviousRefreshMode = mapLibreView.renderingRefreshMode
            } catch (exception: RuntimeException) {
                mapLibrePreviousRefreshMode = null
                HyperLog.w(
                    Constants.TAG,
                    "MapLibre render recovery could not read rendering mode",
                    exception
                )
            }
        }
        try {
            mapLibreView.setRenderingRefreshMode(
                MapRenderer.RenderingRefreshMode.CONTINUOUS
            )
        } catch (exception: RuntimeException) {
            HyperLog.w(
                Constants.TAG,
                "MapLibre render recovery could not enable continuous rendering",
                exception
            )
        }
        mapLibreRenderRecoveryActive = true
        mapLibreRenderRecoveryAttempt = 0
        mapLibreRenderRecoveryFrames = 0
        mapLibreRenderRecoveryReason = reason
        mapLibreLastFrameFullyRendered = false
        mapLibreLoadingForegroundCleared = false
        HyperLog.v(Constants.TAG, "MapLibre render recovery started reason=$reason")
        scheduleMapLibreRenderRecoveryAttempt(mapLibreView, 0L)
    }

    private fun scheduleMapLibreRenderRecoveryAttempt(
        mapLibreView: MapLibreMapView,
        delayMs: Long
    ) {
        mapLibreView.removeCallbacks(mapLibreRenderRecoveryRunnable)
        mapLibreView.postDelayed(mapLibreRenderRecoveryRunnable, delayMs)
    }

    private fun runMapLibreRenderRecoveryAttempt() {
        val mapLibreView = mapLibreMapView ?: return
        if (!mapLibreRenderRecoveryActive || !isResumed) {
            return
        }
        val mapLibreMap = mapDrawableOrNull?.maplibreMap ?: return
        mapLibreRenderRecoveryAttempt++
        mapLibreMap.triggerRepaint()
        invalidateMapLibrePresentation(mapLibreView)

        if (mapLibreRenderRecoveryAttempt == MAPLIBRE_MIN_PRESENTATION_ATTEMPTS) {
            // A real camera transaction follows the same native path as the gesture that wakes
            // rendering on unaffected devices, but preserves the exact camera position.
            mapLibreMap.moveCamera(
                CameraUpdateFactory.newCameraPosition(mapLibreMap.cameraPosition)
            )
        }

        if (mapLibreRenderRecoveryAttempt >= MAPLIBRE_FOREGROUND_FALLBACK_ATTEMPT &&
            mapLibreLayersAppliedForCurrentView &&
            mapLibreView.foreground != null
        ) {
            mapLibreView.foreground = null
            mapLibreLoadingForegroundCleared = true
            mapLibreMap.triggerRepaint()
            invalidateMapLibrePresentation(mapLibreView)
            HyperLog.w(
                Constants.TAG,
                "MapLibre render recovery cleared stale loading foreground " +
                    "reason=$mapLibreRenderRecoveryReason " +
                    "attempt=$mapLibreRenderRecoveryAttempt " +
                    "readyFrames=$mapLibreRenderRecoveryFrames"
            )
        }

        if (mapLibreRenderRecoveryAttempt < MAPLIBRE_REPAINT_ATTEMPTS) {
            scheduleMapLibreRenderRecoveryAttempt(mapLibreView, MAPLIBRE_REPAINT_DELAY_MS)
            return
        }

        val styleFullyLoaded = mapLibreMap.style?.isFullyLoaded == true
        val hadLoadingForeground = mapLibreView.foreground != null
        if (mapLibreLayersAppliedForCurrentView && hadLoadingForeground) {
            mapLibreView.foreground = null
            mapLibreLoadingForegroundCleared = true
            mapLibreMap.triggerRepaint()
            invalidateMapLibrePresentation(mapLibreView)
            HyperLog.w(
                Constants.TAG,
                "MapLibre render recovery cleared stale loading foreground " +
                    "reason=$mapLibreRenderRecoveryReason attempts=$mapLibreRenderRecoveryAttempt " +
                    "readyFrames=$mapLibreRenderRecoveryFrames " +
                    "styleFullyLoaded=$styleFullyLoaded lastFully=$mapLibreLastFrameFullyRendered"
            )
        } else {
            HyperLog.v(
                Constants.TAG,
                "MapLibre render recovery finished without foreground fallback " +
                    "reason=$mapLibreRenderRecoveryReason attempts=$mapLibreRenderRecoveryAttempt " +
                    "readyFrames=$mapLibreRenderRecoveryFrames " +
                    "styleFullyLoaded=$styleFullyLoaded lastFully=$mapLibreLastFrameFullyRendered " +
                    "foregroundCleared=$mapLibreLoadingForegroundCleared"
            )
        }
        stopMapLibreRenderRecovery()
    }

    private fun invalidateMapLibrePresentation(mapLibreView: MapLibreMapView) {
        mapLibreView.renderView.postInvalidateOnAnimation()
        mapLibreView.postInvalidateOnAnimation()
        mapLibreView.parent?.let { parent ->
            if (parent is View) {
                parent.postInvalidateOnAnimation()
            }
        }
    }

    private fun finishMapLibreRenderRecovery(result: String) {
        if (!mapLibreRenderRecoveryActive) {
            return
        }
        HyperLog.v(
            Constants.TAG,
            "MapLibre render recovery completed reason=$mapLibreRenderRecoveryReason " +
                "attempts=$mapLibreRenderRecoveryAttempt readyFrames=$mapLibreRenderRecoveryFrames " +
                "result=$result"
        )
        stopMapLibreRenderRecovery()
    }

    private fun stopMapLibreRenderRecovery() {
        mapLibreMapView?.let { mapLibreView ->
            mapLibreView.removeCallbacks(mapLibreRenderRecoveryRunnable)
            mapLibrePreviousRefreshMode?.let { previousMode ->
                try {
                    mapLibreView.setRenderingRefreshMode(previousMode)
                } catch (exception: RuntimeException) {
                    HyperLog.w(
                        Constants.TAG,
                        "MapLibre render recovery could not restore rendering mode",
                        exception
                    )
                }
            }
        }
        mapLibreRenderRecoveryActive = false
        mapLibreRenderRecoveryAttempt = 0
        mapLibreRenderRecoveryFrames = 0
        mapLibreLoadingForegroundCleared = false
        mapLibrePreviousRefreshMode = null
    }

    private fun scheduleMapReloadAfterLayerFillRetry() {
        val v = view
        if (v == null) {
            return
        }
        if (mapReloadAfterFillRetryCount >= mapReloadAfterFillMaxRetries) {
            HyperLog.w(
                Constants.TAG,
                "reloadMapStyleAfterLayerFill: MapLibre map not ready after $mapReloadAfterFillMaxRetries delayed attempts"
            )
            return
        }
        mapReloadAfterFillRetryCount++
        mapReloadAfterFillRetryRunnable?.let { v.removeCallbacks(it) }
        val r = Runnable {
            mapReloadAfterFillRetryRunnable = null
            if (doReloadMapStyleAndLayersAfterLayerFillBatch()) {
                mapReloadAfterFillRetryCount = 0
            } else if (mMapRef.get()?.map?.getMaplibreMap() == null
                && mapReloadAfterFillRetryCount < mapReloadAfterFillMaxRetries) {
                scheduleMapReloadAfterLayerFillRetry()
            }
        }
        mapReloadAfterFillRetryRunnable = r
        v.postDelayed(r, 100)
    }

    override fun getLongLongClickProcesses(): Boolean {
        return longClickProcessed
    }

    override fun setLongLongClickProcesses(longLongCLickPrecesses: Boolean) {
        this.longClickProcessed = longLongCLickPrecesses;
    }

    private fun getDispatcher(): Dispatcher {
        val dispatcher = Dispatcher()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Matches core limit set on
            // https://github.com/mapbox/mapbox-gl-native/blob/master/platform/android/src/http_file_source.cpp#L192
            dispatcher.maxRequestsPerHost = 20
        } else {
            // Limiting concurrent request on Android 4.4, to limit impact of SSL handshake platform library crash
            // https://github.com/mapbox/mapbox-gl-native/issues/14910
            dispatcher.maxRequestsPerHost = 10
        }
        return dispatcher
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        if (undoRedoOverlay != null) undoRedoOverlay!!.defineUndoRedo()

        if (editLayerOverlay != null) editLayerOverlay!!.setHasEdits(editLayerOverlay!!.hasEdits())
    }

    fun restartGpsListener() {
        mGpsEventSource!!.removeListener(this)
        mGpsEventSource!!.addListener(this)
    }

    val isEditMode: Boolean
        get() = mode == MODE_EDIT || mode == MODE_EDIT_BY_WALK

    private var walkPanel: WalkRecordingPanel? = null
    private var pointSessionId: String? = null
    private var finishedWalkEditId: String? = null
    private var pendingWalkFinishId: String? = null
    private var walkPreviewKey: String? = null
    private var walkPreviewMap: MapDrawable? = null

    private fun attachWalkPanel(root: View) {
        pointSessionId = WalkSessionStore.load(requireContext())?.pointId?.takeIf { it.isNotEmpty() }
        walkPanel = root.findViewById(R.id.walk_recording_panel)
        walkPanel?.setListener(object : WalkRecordingPanel.Listener {
            override fun onSessionChanged(session: WalkSessionStore.Snapshot?) {
                val key = session?.let { "${it.id}:${it.revision}:$finishedWalkEditId" }
                val map = mMapRef.get()?.map
                if (map != null && (key != walkPreviewKey || map !== walkPreviewMap)) {
                    walkPreviewKey = key
                    walkPreviewMap = map
                    map.showWalkPreview(
                        if (session != null && session.id != finishedWalkEditId) session.geometry() else null
                    )
                }
                if (session?.phase == WalkSessionPolicy.Phase.FINISHED && pendingWalkFinishId == session.id) {
                    pendingWalkFinishId = null
                    root.post { openFinishedWalk(session) }
                }
                mScaleRulerLayout?.visibility = if (session == null && mode == MODE_NORMAL
                    && mPreferences?.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_SCALE_RULER, false) == true
                ) View.VISIBLE else View.GONE
            }

            override fun onFinishWalk(session: WalkSessionStore.Snapshot) {
                if (session.phase == WalkSessionPolicy.Phase.FINISHED) openFinishedWalk(session)
                else {
                    pendingWalkFinishId = session.id
                    if (!WalkEditService.requestCommand(requireContext(), session.id, WalkSessionPolicy.Command.FINISH))
                        pendingWalkFinishId = null
                }
            }

            override fun onDiscardWalk(session: WalkSessionStore.Snapshot) {
                AlertDialog.Builder(requireContext())
                    .setTitle(com.nextgis.maplibui.R.string.walk_discard)
                    .setMessage(com.nextgis.maplibui.R.string.walk_discard_confirm)
                    .setPositiveButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                        WalkEditService.requestCommand(requireContext(), session.id, WalkSessionPolicy.Command.DISCARD)
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }

            override fun onShowWalk(session: WalkSessionStore.Snapshot) {
                val envelope = session.geometry()?.envelope ?: return
                mMapRef.get()?.setZoomAndCenter(mMapRef.get()!!.zoomLevel, envelope.center)
            }
        })
    }

    private fun openFinishedWalk(session: WalkSessionStore.Snapshot) {
        val ctx = context ?: return
        val current = WalkSessionStore.load(ctx) ?: return
        if (current.id != session.id || current.isPointActive || current.phase != WalkSessionPolicy.Phase.FINISHED
            || mode == MODE_EDIT || mNewFeatureFormLaunchInProgress) return
        val layer = mMapRef.get()?.map?.getLayerById(current.layerId) as? VectorLayer ?: return
        val geometry = current.geometry() ?: return
        val draft = GeometryEditDraftStore.Snapshot().apply {
            layerId = current.layerId
            featureId = current.featureId
            editMode = MODE_EDIT
            geometryWkt = geometry.toWKT(true)
            mapPath = current.mapPath
        }
        if (!GeometryEditDraftStore.save(ctx, draft, "finished-walk-edit")) return
        finishedWalkEditId = current.id
        resumeManualGeometryFromDraft()
        walkPanel?.refresh()
    }

    private fun beginPointCreation(tool: Int): Boolean {
        val ctx = context ?: return false
        val session = WalkSessionStore.load(ctx) ?: return true
        if (session.isPointActive) {
            return pointSessionId == session.pointId && session.pointStage == WalkSessionStore.STAGE_CHOOSE
        }
        pointSessionId = WalkSessionStore.beginPoint(ctx, tool)
        if (pointSessionId == null) {
            Toast.makeText(ctx, com.nextgis.maplibui.R.string.walk_already_active, Toast.LENGTH_LONG).show()
            return false
        }
        mCurrentLocationOverlay?.setAutopanningEnabled(false)
        walkPanel?.refresh()
        return true
    }

    private fun bindPointCreation(layer: VectorLayer) {
        pointSessionId?.let { WalkSessionStore.bindPoint(requireContext(), it, layer.id, WalkSessionStore.STAGE_GEOMETRY) }
    }

    private fun finishPointCreation() {
        pointSessionId?.let { context?.let { ctx -> WalkSessionStore.endPoint(ctx, it) } }
        pointSessionId = null
        walkPanel?.refresh()
    }

    fun hasPointCreationWithoutDraft(): Boolean {
        val session = WalkSessionStore.load(context) ?: return false
        return session.isPointActive && mode != MODE_EDIT && !isDialogShown
                && !GeometryEditDraftStore.hasAnyDraft(context) && FeatureFormDraftStore.load(context) == null
    }

    fun resumePointCreationSelection() {
        val session = WalkSessionStore.load(context) ?: return
        pointSessionId = session.pointId
        // There is no geometry/form journal: return to the same point-creation command.
        WalkSessionStore.bindPoint(requireContext(), session.pointId, Constants.NOT_FOUND, WalkSessionStore.STAGE_CHOOSE)
        when (session.pointTool) {
            ADD_CURRENT_LOC -> addCurrentLocation(true)
            ADD_POINT_BY_TAP -> addPointByTap()
            else -> addNewGeometry()
        }
    }

    fun discardPointCreationWithoutDraft() {
        pointSessionId = WalkSessionStore.load(context)?.pointId
        finishPointCreation()
    }

    val isRulerMeasuring: Boolean
        get() = mRulerOverlay?.isMeasuring == true

    fun onOptionsItemSelected(id: Int): Boolean {
        val result: Boolean
        when (id) {
            android.R.id.home -> {
                cancelEdits()
                return true
            }

            0 -> {
                mMapRef.get()!!.isLockMap = false
                setNewMode(MODE_EDIT)
                return true
            }



            com.nextgis.maplibui.R.id.menu_edit_undo, com.nextgis.maplibui.R.id.menu_edit_redo -> {
                if (mRulerOverlay?.isMeasuring == true) {
                    result = undoRedoOverlay!!.onOptionsItemSelected(id)
                    if (result) {
                        val geometry = undoRedoOverlay!!.feature.geometry as? GeoLineString
                        if (geometry != null)
                            mMapRef.get()?.map?.restoreMeasurementGeometry(geometry)
                    }
                    return result
                }

                result = undoRedoOverlay!!.onOptionsItemSelected(id)
                if (result) {
                    val undoRedoFeature = undoRedoOverlay!!.feature
                    val feature = editLayerOverlay!!.selectedFeature
                    feature.geometry = undoRedoFeature.geometry
                    editLayerOverlay!!.fillDrawItems(undoRedoFeature.geometry)

                    val original = mSelectedLayer!!.getGeometryForId(feature.id)
                    val hasEdits = original != null && undoRedoFeature.geometry == original

                    editLayerOverlay!!.setHasEdits(!hasEdits)
                    mMapRef.get()!!.map!!.replaceGeometryFromHistoryChanges(feature.geometry)
                    mMapRef.get()!!.map!!.updateMarkerByEditObject();
                    mMapRef.get()!!.buffer()
                    mMapRef.get()!!.postInvalidate()
                    persistManualGeometryDraft(
                        if (id == com.nextgis.maplibui.R.id.menu_edit_undo) "undo" else "redo"
                    )
                }
                return result
            }

            com.nextgis.maplibui.R.id.menu_edit_by_walk -> {
                if (WalkSessionStore.load(context) != null) {
                    Toast.makeText(context, com.nextgis.maplibui.R.string.walk_already_active, Toast.LENGTH_LONG).show()
                    return true
                }
                undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)
                setNewMode(MODE_EDIT_BY_WALK)
                return true
            }

            com.nextgis.maplibui.R.id.menu_edit_delete_point  ->{
                val map = mMapRef.get()?.map ?: return false
                if (!map.canDeleteCurrentPointSafe()) {
                    return true
                }
                return map.deleteCurrentPoint()
            }

            com.nextgis.maplibui.R.id.menu_edit_delete_line  ->{
                val result = mMapRef.get()!!.map!!.deleteCurrentLine();
                return result
            }

            com.nextgis.maplibui.R.id.menu_edit_add_new_line  ->{
                val center = mMapRef.get()!!.map!!.maplibreMap.cameraPosition.target
                val result = mMapRef.get()!!.map!!.addNewLine(center, mMapRef.get()!!.map!!.maplibreMap.getProjection());
                return result
            }

            com.nextgis.maplibui.R.id.menu_edit_add_new_point  ->{
                val center = mMapRef.get()!!.map!!.maplibreMap.cameraPosition.target
                val result = mMapRef.get()!!.map!!.addNewPoint(center);
                return result
            }

            com.nextgis.maplibui.R.id.menu_edit_move_point_to_center  ->{
                val center = mMapRef.get()!!.map!!.maplibreMap.cameraPosition.target
                return mMapRef.get()!!.map!!.moveToPoint(center);
            }

            com.nextgis.maplibui.R.id.menu_edit_move_point_to_current_location  ->{

                if (mCurrentCenter != null) {
                    val latlng = convert3857To4326(mCurrentCenter!!.x, mCurrentCenter!!.y)
                    return mMapRef.get()!!.map!!.moveToPoint(LatLng(latlng[1], latlng[0]));
                }
                return false;
            }

            com.nextgis.maplibui.R.id.menu_edit_attributes -> return showSelectedFeatureAttributesFormFromEditMode()

            else -> {
                result = editLayerOverlay!!.onOptionsItemSelected(id)
                if (result) undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)
                return result
            }
        }
        return false
    }


    fun saveEdits(): Boolean {
        val feature = editLayerOverlay!!.selectedFeature
        var featureId = Constants.NOT_FOUND.toLong()
        var geometry: GeoGeometry? = null

        if (mode == MODE_EDIT_BY_WALK) {
            editLayerOverlay!!.stopGeometryByWalk()
            setNewMode(MODE_EDIT)

            val mapDrawable = mApp!!.map as MapDrawable
            if (mapDrawable.editingObject != null) {
                mapDrawable.updateHistoryByWalkEnd()
            } else {
                Toast.makeText(
                    context,
                    com.nextgis.maplibui.R.string.not_enough_points,
                    Toast.LENGTH_SHORT
                ).show()
            }
            undoRedoOverlay!!.defineUndoRedo()

            return true
        }

        if (feature != null) {
            geometry = feature.geometry
            featureId = feature.id
        }

        /*
         * Only GTMultiPolygon layers opt into automatic topology repair. Keep one feature and one
         * attribute form: JTS may split an invalid ring into several polygon parts, but the parts
         * remain components of the same GeoMultiPolygon.
         */
        if (MultiPolygonGeometryRepair.supportsLayerType(
                mSelectedLayer?.geometryType ?: GeoConstants.GTNone
            )
            && geometry is GeoMultiPolygon
        ) {
            val repair = MultiPolygonGeometryRepair.repairIfNeeded(geometry)
            when (repair.status) {
                MultiPolygonGeometryRepair.Status.REPAIRED -> {
                    geometry = repair.geometry
                    feature?.geometry = geometry
                    editLayerOverlay!!.fillDrawItems(geometry)
                    mMapRef.get()?.map?.let { map ->
                        if (map.editingObject != null) {
                            map.replaceGeometryFromHistoryChanges(geometry)
                            map.updateMarkerByEditObject()
                        }
                    }
                    HyperLog.v(
                        Constants.TAG,
                        "MultiPolygon geometry repaired layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId parts=${repair.polygonCount} " +
                            "reason=${repair.diagnostic}"
                    )
                    Toast.makeText(
                        context,
                        getString(
                            com.nextgis.maplibui.R.string.multipolygon_repaired,
                            repair.polygonCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }

                MultiPolygonGeometryRepair.Status.INSUFFICIENT_POINTS -> {
                    HyperLog.w(
                        Constants.TAG,
                        "MultiPolygon geometry has insufficient points layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId reason=${repair.diagnostic}"
                    )
                    Toast.makeText(
                        context,
                        com.nextgis.maplibui.R.string.not_enough_points,
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }

                MultiPolygonGeometryRepair.Status.FAILED -> {
                    HyperLog.w(
                        Constants.TAG,
                        "MultiPolygon geometry repair failed layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId reason=${repair.diagnostic}"
                    )
                    Toast.makeText(
                        context,
                        com.nextgis.maplibui.R.string.multipolygon_repair_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                MultiPolygonGeometryRepair.Status.UNCHANGED -> Unit
            }
        }

        if (geometry == null || !geometry.isValid) {
            val message = invalidGeometryMessage(geometry)
            HyperLog.w(
                Constants.TAG,
                "Geometry save rejected layer=${mSelectedLayer?.id ?: Constants.NOT_FOUND} " +
                    "feature=$featureId type=${geometry?.type ?: Constants.NOT_FOUND} " +
                    "reason=${resources.getResourceEntryName(message)}"
            )
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        //MapUtil.isGeometryIntersects(context, geometry);
            //return false

        mMapRef.get()!!.isLockMap = false
        editLayerOverlay!!.setHasEdits(false)

        if (mSelectedLayer != null) {
            if (featureId == Constants.NOT_FOUND.toLong()) {
                //show attributes edit activity
                val vectorLayerUI = mSelectedLayer as IVectorLayerUI
                if (pointSessionId != null || finishedWalkEditId != null) {
                    if (!LayerUtil.showSessionEditForm(mSelectedLayer!!, requireActivity(), featureId,
                            geometry, finishedWalkEditId)) {
                        editLayerOverlay!!.setHasEdits(true)
                        return false
                    }
                } else vectorLayerUI.showEditForm(mActivity, featureId, geometry, -1)
                clearManualGeometryDraft("handoff-to-attribute-form")
            } else {
                var uri =  Uri.parse("content://" + mApp!!.authority + "/" + mSelectedLayer!!.path.name)
                uri = ContentUris.withAppendedId(uri!!, featureId)
                val values = ContentValues()

                try {
                    values.put(Constants.FIELD_GEOM, geometry.toBlob())
                } catch (e: IOException) {
                    editLayerOverlay!!.setHasEdits(true)
                    HyperLog.e(
                        Constants.TAG,
                        "GeometryDraft geometry serialization failed layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId: ${e.message}",
                        e
                    )
                    Toast.makeText(
                        context,
                        com.nextgis.maplibui.R.string.error_db_update,
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                val updated = try {
                    mActivity!!.contentResolver.update(uri, values, null, null)
                } catch (e: RuntimeException) {
                    editLayerOverlay!!.setHasEdits(true)
                    HyperLog.e(
                        Constants.TAG,
                        "GeometryDraft save-to-layer crashed layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId: ${e.message}",
                        e
                    )
                    Toast.makeText(
                        context,
                        com.nextgis.maplibui.R.string.error_db_update,
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }
                if (updated != 1) {
                    editLayerOverlay!!.setHasEdits(true)
                    HyperLog.e(
                        Constants.TAG,
                        "GeometryDraft save-to-layer failed layer=${mSelectedLayer!!.id} " +
                            "feature=$featureId updated=$updated"
                    )
                    Toast.makeText(
                        context,
                        com.nextgis.maplibui.R.string.error_db_update,
                        Toast.LENGTH_LONG
                    ).show()
                    return false
                }

                finishSuccessfulFeatureSave(
                    "geometry-save-success",
                    mSelectedLayer?.id ?: Constants.NOT_FOUND,
                    featureId
                )

            }
        }

        return true
    }

    private fun invalidGeometryMessage(geometry: GeoGeometry?): Int {
        if (geometry is GeoPolygon) {
            return when {
                geometry.outerRing.pointCount < 3 ->
                    com.nextgis.maplibui.R.string.not_enough_points
                geometry.intersects() -> com.nextgis.maplib.R.string.self_intersection
                !geometry.isHolesInside -> com.nextgis.maplib.R.string.ring_outside
                geometry.isHolesIntersect -> com.nextgis.maplib.R.string.rings_intersection
                else -> com.nextgis.maplib.R.string.error_geojson_invalid_geometry
            }
        }
        return com.nextgis.maplibui.R.string.not_enough_points
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == IVectorLayerUI.MODIFY_REQUEST) {
            mNewFeatureFormLaunchInProgress = false
        }

        if (requestCode == IVectorLayerUI.MODIFY_REQUEST && mEditAttributesFormFromEditMode) {
            mEditAttributesFormFromEditMode = false
            if (resultCode == Activity.RESULT_OK && data != null) {
                val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())
                val layer = mSelectedLayer
                if (id != Constants.NOT_FOUND.toLong() && layer != null) {
                    mMapRef.get()?.map?.reloadFeatureToMaplibre(id, layer)
                }
                finishSuccessfulFeatureSave(
                    "attribute-form-save-success",
                    layer?.id ?: Constants.NOT_FOUND,
                    id
                )
            }
            return
        }

        if (mode == MODE_INFO || resultCode != Activity.RESULT_OK) {
            editLayerOverlay!!.setHasEdits(true)
            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
                    val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())
                    val layer = mSelectedLayer
                    if (id != Constants.NOT_FOUND.toLong() && layer != null) {
                        mMapRef.get()?.map?.reloadFeatureToMaplibre(id, layer)
                        finishSuccessfulFeatureSave(
                            "info-form-save-success",
                            layer.id,
                            id
                        )
                    }
                }
            } else if (mode == MODE_EDIT) {
                cancelEdits()
                setNewMode(MODE_NORMAL)
            }
            return
        }

        if (requestCode == IVectorLayerUI.MODIFY_REQUEST && data != null) {
            val id = data.getLongExtra(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())

            if (id != Constants.NOT_FOUND.toLong()) {
                val previousLayer = mSelectedLayer
                val resultLayerId = data.getIntExtra(
                    ConstantsUI.KEY_LAYER_ID,
                    previousLayer?.id ?: Constants.NOT_FOUND
                )
                val resultLayer = when {
                    previousLayer?.id == resultLayerId -> previousLayer
                    resultLayerId != Constants.NOT_FOUND ->
                        mMapRef.get()?.map?.getLayerById(resultLayerId) as? VectorLayer
                    else -> previousLayer
                }
                if (resultLayer == null) {
                    HyperLog.e(
                        Constants.TAG,
                        "FormSave result cannot resolve layer resultLayer=$resultLayerId " +
                            "feature=$id mode=${modeName(mode)}"
                    )
                    setNewMode(MODE_NORMAL)
                    return
                }

                val wasNewFeature = data.getBooleanExtra(
                    ConstantsUI.KEY_WAS_NEW_FEATURE,
                    previousLayer != null
                        && editLayerOverlay?.selectedFeatureId == Constants.NOT_FOUND.toLong()
                )
                HyperLog.v(
                    Constants.TAG,
                    "FormSave result received layer=$resultLayerId feature=$id " +
                        "wasNew=$wasNewFeature mode=${modeName(mode)} " +
                        "retainedLayer=${previousLayer?.id ?: Constants.NOT_FOUND}"
                )
                val mapDrawable = mMapRef.get()?.map
                val hasActiveCreationSession = wasNewFeature
                    && previousLayer?.id == resultLayer.id
                    && mapDrawable?.editingObject != null
                    && mapDrawable.originalSelectedFeature != null

                mSelectedLayer = resultLayer
                editLayerOverlay!!.setSelectedLayer(resultLayer)
                resultLayer.showFeature(id)

                if (mapDrawable == null) {
                    HyperLog.w(
                        Constants.TAG,
                        "FormSave result applied to overlay but map is unavailable " +
                            "layer=${resultLayer.id} feature=$id"
                    )
                    finishSuccessfulFeatureSave(
                        "form-save-success-no-map",
                        resultLayer.id,
                        id
                    )
                    return
                }
                if (hasActiveCreationSession) {
                    mapDrawable.finishCreateNewFeature(id, resultLayer)
                } else if (wasNewFeature) {
                    HyperLog.v(
                        Constants.TAG,
                        "FormSave cold-recovery result has no temporary MapLibre edit session; " +
                            "reloading persisted feature layer=${resultLayer.id} feature=$id"
                    )
                }
                mapDrawable.reloadFeatureToMaplibre(id, resultLayer)
                finishSuccessfulFeatureSave(
                    if (wasNewFeature) "new-feature-form-save-success"
                    else "existing-feature-form-save-success",
                    resultLayer.id,
                    id
                )
            }
        } else if (editLayerOverlay!!.selectedFeatureGeometry != null) editLayerOverlay!!.setHasEdits(
            true
        )
    }

    fun hasEdits(): Boolean {
        return editLayerOverlay != null && editLayerOverlay!!.hasEdits()
    }

    /**
     * A successful feature write is terminal for both creation and existing-feature editing.
     * Return to the ordinary map instead of leaving a selected feature/action toolbar behind.
     */
    private fun finishSuccessfulFeatureSave(reason: String, layerId: Int, featureId: Long) {
        val session = WalkSessionStore.load(context)
        if (session != null && session.id == finishedWalkEditId && session.layerId == layerId) {
            WalkSessionStore.clear(requireContext(), session.id)
        }
        finishedWalkEditId = null
        finishPointCreation()
        editLayerOverlay?.setHasEdits(false)
        editLayerOverlay?.setSelectedFeature(null)
        mMapRef.get()?.map?.cancelFeatureEdit(false)
        clearManualGeometryDraft(reason)
        closeAttributesPanelAfterSuccessfulSave()
        setNewMode(MODE_NORMAL)
        editLayerOverlay?.setSelectedLayer(null)
        HyperLog.v(
            Constants.TAG,
            "FeatureSave finished edit session reason=$reason layer=$layerId feature=$featureId"
        )
    }

    private fun closeAttributesPanelAfterSuccessfulSave() {
        val activity = mActivity ?: return
        val fragmentManager = activity.supportFragmentManager
        val attributesFragment =
            fragmentManager.findFragmentByTag("ATTRIBUTES") as? AttributesFragment ?: return
        if (!attributesFragment.isVisible) return

        if (attributesFragment.isTablet) {
            fragmentManager.beginTransaction().remove(attributesFragment).commit()
        } else {
            activity.finishFragment()
        }
    }


    fun cancelEdits() {
//        if (mEditLayerOverlay.hasEdits()) TODO prompt dialog
//            return;

        // restore

        editLayerOverlay!!.panStop()
        panStop()

        editLayerOverlay!!.setHasEdits(false)
        if (mode == MODE_EDIT_BY_WALK) {
            editLayerOverlay!!.stopGeometryByWalk() // TODO toast?
            setNewMode(MODE_EDIT)
            undoRedoOverlay!!.clearHistory()
            undoRedoOverlay!!.defineUndoRedo()
        }
        val featureId = editLayerOverlay!!.selectedFeatureId
        val wasNewFeature = featureId == Constants.NOT_FOUND.toLong()
        editLayerOverlay!!.setSelectedFeature(featureId)
        mMapRef.get()!!.map!!.cancelFeatureEdit(featureId != -1L)
        setNewMode(if (wasNewFeature) MODE_NORMAL else MODE_SELECT_ACTION)
        clearManualGeometryDraft("geometry-cancel")
        finishedWalkEditId = null
        finishPointCreation()
    }

    private fun requestCancelEdits() {
        val isNewFeature = editLayerOverlay?.selectedFeatureId == Constants.NOT_FOUND.toLong()
        if (!isNewFeature) {
            cancelEdits()
            return
        }

        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle(R.string.geometry_edit_cancel_title)
            .setMessage(R.string.geometry_edit_cancel_message)
            .setPositiveButton(R.string.geometry_edit_cancel_confirm) { _, _ -> cancelEdits() }
            .setNegativeButton(R.string.geometry_edit_cancel_continue, null)
            .show()
    }

    private fun modeName(value: Int): String {
        return when (value) {
            MODE_NORMAL -> "MODE_NORMAL"
            MODE_SELECT_ACTION -> "MODE_SELECT_ACTION"
            MODE_EDIT -> "MODE_EDIT"
            MODE_INFO -> "MODE_INFO"
            MODE_EDIT_BY_WALK -> "MODE_EDIT_BY_WALK"
            MODE_SELECT_FOR_VIEW -> "MODE_SELECT_FOR_VIEW"
            MODE_STAKEOUT -> "MODE_STAKEOUT"
            MODE_AZIMUTH_CURRENT -> "MODE_AZIMUTH_CURRENT"
            MODE_AZIMUTH_POINTS -> "MODE_AZIMUTH_POINTS"
            else -> "MODE_UNKNOWN($value)"
        }
    }

    private fun isLiveStakeoutMode(value: Int): Boolean =
        value == MODE_STAKEOUT || value == MODE_AZIMUTH_CURRENT

    private fun isMapAzimuthMode(value: Int): Boolean =
        value == MODE_AZIMUTH_CURRENT || value == MODE_AZIMUTH_POINTS

    fun setNewMode(mode: Int, vararg readOnly: Boolean) {
        if (mode == MODE_EDIT_BY_WALK) {
            if (editLayerOverlay?.startIndependentWalk() == true) {
                clearManualGeometryDraft("walk-ownership-transferred")
                mMapRef.get()?.map?.cancelFeatureEdit(false)
                setNewMode(MODE_NORMAL)
                walkPanel?.refresh()
                mActivity?.askBackgroundPerm(null)
            } else {
                Toast.makeText(context, com.nextgis.maplibui.R.string.walk_already_active, Toast.LENGTH_LONG).show()
            }
            return
        }
        val previousMode = this.mode
        if (isLiveStakeoutMode(previousMode) && mode != previousMode) {
            mStakeoutController?.stop()
            mStakeoutPanel?.visibility = View.GONE
            lastStakeoutUiState = null
        }
        if (isMapAzimuthMode(previousMode) && mode != previousMode) {
            clearAzimuthMeasurement()
        }
        var askPerm = false
        if (mMapRef.get()!!.map!!.checkMeasurment(mode)){
            mRulerOverlay!!.stopMeasuring()
            undoRedoOverlay!!.clearHistory()
            showMainButton()
            showRulerButton()
            showAzimuthButton()
            hideAddByTapButton()
            mAddPointButton!!.setIcon(com.nextgis.maplibui.R.drawable.ic_action_add_point)
            mActivity!!.title = mActivity!!.appName
            mActivity!!.setSubtitle(null)
            mMapRef.get()!!.map.stoptMeasuring()

        }

        if (previousMode != mode) {
            HyperLog.v(
                Constants.TAG,
                "MapFragment mode ${modeName(previousMode)} -> ${modeName(mode)} " +
                    "layer=${mSelectedLayer?.id ?: Constants.NOT_FOUND} " +
                    "feature=${editLayerOverlay?.selectedFeatureId ?: Constants.NOT_FOUND} " +
                    "hasEdits=${editLayerOverlay?.hasEdits() == true}"
            )
        }

        this.mode = mode
        walkUiAttachedInProcess = mode == MODE_EDIT_BY_WALK
        stakeoutUiAttachedInProcess = mode == MODE_STAKEOUT

        hideMainButton()
        hideAddByTapButton()
        hideRulerButton()
        hideAzimuthButton()

        val toolbar = mActivity!!.bottomToolbar
        toolbar.background.alpha = 128
        toolbar.visibility = View.VISIBLE
        mActivity!!.showDefaultToolbar()

        if (mStatusPanelMode != 3) mStatusPanel!!.visibility = View.INVISIBLE

        when (mode) {
            MODE_NORMAL -> {
                if (mSelectedLayer != null) mSelectedLayer!!.isLocked = false

                mSelectedLayer = null
                toolbar.visibility = View.GONE
                showMainButton()
                showRulerButton()
                showAzimuthButton()
                if (mStatusPanelMode != 0) mStatusPanel!!.visibility = View.VISIBLE
                editLayerOverlay!!.showAllFeatures()
                editLayerOverlay!!.mode = EditLayerOverlay.MODE_NONE
                if (!preserveRulerHistoryDuringModeRestore)
                    undoRedoOverlay!!.clearHistory()
                mMapRef.get()!!.map!!.unselectFeatureFromView()
                mStakeoutPanel?.visibility = View.GONE
                mScaleRulerLayout?.visibility = if (
                    mPreferences?.getBoolean(
                        AppSettingsConstants.KEY_PREF_SHOW_SCALE_RULER,
                        false
                    ) == true
                ) View.VISIBLE else View.GONE
            }

            MODE_EDIT -> {
                if (mSelectedLayer == null) {
                    setNewMode(MODE_NORMAL)
                    return
                }

                mSelectedLayer!!.isLocked = true
                mActivity!!.showEditToolbar()
                editLayerOverlay!!.mode = EditLayerOverlay.MODE_EDIT
                toolbar.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)
                mFinishListener = View.OnClickListener { requestCancelEdits() }
                toolbar.setNavigationOnClickListener(mFinishListener)
                toolbar.setOnMenuItemClickListener { menuItem ->
                    onOptionsItemSelected(
                        menuItem.itemId
                    )
                }
                mMapRef.get()!!.map!!.showVertex()
                mMapRef.get()!!.map!!.showMarker();
            }

            MODE_EDIT_BY_WALK -> {
                mSelectedLayer!!.isLocked = true
                if (previousMode != MODE_EDIT_BY_WALK) {
                    clearManualGeometryDraft("walk-mode-start")
                }
                mActivity!!.showEditToolbar()
                editLayerOverlay!!.mode = EditLayerOverlay.MODE_EDIT_BY_WALK
                undoRedoOverlay!!.clearHistory()
                toolbar.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)
                mFinishListener = View.OnClickListener { requestCancelEdits() }
                toolbar.setNavigationOnClickListener(mFinishListener)

                mMapRef.get()!!.map!!.unselectFeatureFromEdit(false, true)
                mMapRef.get()!!.map!!.hideVertex()

                mMapRef.get()!!.map!!.hideMarker()

                askPerm = true


            }

            MODE_SELECT_ACTION -> {
                if (mSelectedLayer == null) {
                    setNewMode(MODE_NORMAL)
                    return
                }

                mSelectedLayer!!.isLocked = true
                toolbar.title = null
                toolbar.menu.clear()
                toolbar.inflateMenu(R.menu.select_action)
                toolbar.menu.findItem(R.id.menu_feature_edit).setEnabled(false)
                toolbar.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)

                mFinishListener = View.OnClickListener {
                    setNewMode(MODE_NORMAL)
                }
                toolbar.setNavigationOnClickListener(mFinishListener)

                toolbar.setOnMenuItemClickListener(
                    Toolbar.OnMenuItemClickListener { item ->
                        if (mSelectedLayer == null) return@OnMenuItemClickListener false
                        when (item.itemId) {
                            R.id.menu_feature_edit -> startFeatureGeometryEdit()

                            R.id.menu_feature_edit_attributes -> showSelectedFeatureAttributesFormFromEditMode()
                            R.id.menu_feature_delete -> deleteFeature()
                            R.id.menu_feature_attributes -> setNewMode(MODE_INFO)
                            R.id.menu_feature_stakeout -> startStakeout()
                        }
                        true
                    })

                editLayerOverlay!!.mode = EditLayerOverlay.MODE_HIGHLIGHT
                undoRedoOverlay!!.clearHistory()
            }

            MODE_SELECT_FOR_VIEW -> {
                if (mSelectedLayer == null) {
                    setNewMode(MODE_NORMAL)
                    return
                }

                toolbar.title = null
                toolbar.menu.clear()
                toolbar.inflateMenu(R.menu.select_action_view)
                toolbar.menu.findItem(R.id.menu_feature_edit)?.isVisible =
                    mSelectedLayer!!.isEditingAllowed
                toolbar.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)

                mFinishListener = View.OnClickListener { setNewMode(MODE_NORMAL) }
                toolbar.setNavigationOnClickListener(mFinishListener)

                toolbar.setOnMenuItemClickListener(
                    Toolbar.OnMenuItemClickListener { item ->
                        if (mSelectedLayer == null) return@OnMenuItemClickListener false
                        when (item.itemId) {
                            R.id.menu_feature_attributes -> setNewMode(
                                MODE_INFO,
                                true
                            )
                            R.id.menu_feature_edit -> startLayerEditMode()
                            R.id.menu_feature_stakeout -> startStakeout()
                        }
                        true
                    })

                editLayerOverlay!!.mode = EditLayerOverlay.MODE_HIGHLIGHT
                undoRedoOverlay!!.clearHistory()
            }

            MODE_STAKEOUT -> {
                if (mSelectedLayer == null || mStakeoutController?.isActive != true) {
                    setNewMode(MODE_NORMAL)
                    return
                }
                mSelectedLayer!!.isLocked = true
                toolbar.visibility = View.GONE
                mActivity!!.title = getString(R.string.stakeout_title)
                mActivity!!.setSubtitle(null)
                mFinishListener = View.OnClickListener { setNewMode(MODE_NORMAL) }
                editLayerOverlay!!.mode = EditLayerOverlay.MODE_HIGHLIGHT
                undoRedoOverlay!!.clearHistory()
                mStakeoutPanel?.visibility = View.VISIBLE
                mScaleRulerLayout?.visibility = View.GONE
                if (mStatusPanelMode != 0) mStatusPanel?.visibility = View.VISIBLE
            }

            MODE_AZIMUTH_CURRENT, MODE_AZIMUTH_POINTS -> {
                toolbar.visibility = View.GONE
                mActivity!!.title = getString(R.string.azimuth_tool)
                mActivity!!.setSubtitle(null)
                mFinishListener = View.OnClickListener { setNewMode(MODE_NORMAL) }
                editLayerOverlay!!.mode = EditLayerOverlay.MODE_NONE
                undoRedoOverlay!!.clearHistory()
                mStakeoutPanel?.visibility = View.VISIBLE
                mStakeoutSound?.visibility = View.GONE
                mStakeoutDirection?.rotation = 0f
                mStakeoutDirection?.alpha = 0.35f
                mStakeoutAzimuth?.visibility = View.GONE
                mStakeoutDetails?.visibility = View.GONE
                mStakeoutDistance?.setText(
                    if (mode == MODE_AZIMUTH_CURRENT) {
                        R.string.azimuth_select_target
                    } else {
                        R.string.azimuth_select_start
                    }
                )
                mScaleRulerLayout?.visibility = View.GONE
                if (mStatusPanelMode != 0) mStatusPanel?.visibility = View.VISIBLE
                askPerm = mode == MODE_AZIMUTH_CURRENT
            }

            MODE_INFO -> {
                if (mSelectedLayer == null) {
                    setNewMode(MODE_NORMAL)
                    return
                }
                var readOnlyModeValue = false
                if (readOnly.size > 0) readOnlyModeValue = readOnly[0]

                mSelectedLayer!!.isLocked = if (readOnlyModeValue) false else true
                val tabletSize = resources.getBoolean(R.bool.isTablet)
                val fragmentManager = mActivity!!.supportFragmentManager
                val fragmentTransaction = fragmentManager.beginTransaction()
                //get or create fragment
                val attributesFragment = getAttributesFragment(fragmentManager)

                val attrBundle = Bundle()
                attrBundle.putBoolean(AttributesFragment.KEY_READ_ONLY, readOnlyModeValue)
                attributesFragment.arguments = attrBundle
                attributesFragment.isTablet = tabletSize
                var container = R.id.mainview

                if (attributesFragment.isTablet) {
                    container = R.id.fl_attributes
                } else {
                    val hide = fragmentManager.findFragmentById(R.id.map)
                    fragmentTransaction.hide(hide!!)
                }

                if (!attributesFragment.isAdded) {
                    fragmentTransaction.add(container, attributesFragment, "ATTRIBUTES")
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)

                    if (!attributesFragment.isTablet) fragmentTransaction.addToBackStack(null)
                }

                if (!attributesFragment.isVisible) {
                    fragmentTransaction.show(attributesFragment)
                }

                fragmentTransaction.commit()

                attributesFragment.setSelectedFeature(
                    mSelectedLayer,
                    editLayerOverlay!!.selectedFeatureId
                )
                attributesFragment.setToolbar(toolbar, editLayerOverlay, readOnlyModeValue)

                mFinishListener = View.OnClickListener { view ->
                    (activity as MainActivity).finishFragment()
                    if (attributesFragment.isTablet) activity!!.supportFragmentManager.beginTransaction()
                        .remove(attributesFragment).commit()
                    if (view == null) setNewMode(MODE_NORMAL)
                }

                toolbar.setNavigationIcon(com.nextgis.maplibui.R.drawable.ic_action_cancel_dark)
                toolbar.setNavigationOnClickListener(mFinishListener)
            }
        }

        if (mModeListener != null) mModeListener!!.onModeChangeListener()

        updateCenterCrossVisibility()
        setMarginsToPanel()
        defineMenuItems()

        walkPanel?.setEditorAvailable(mode == MODE_NORMAL || mode == MODE_SELECT_ACTION || mode == MODE_SELECT_FOR_VIEW)

        if (askPerm)
            Handler().postDelayed(Runnable(){
                mActivity?.askBackgroundPerm(null)
            }, 1000)

    }

    private fun getAttributesFragment(fragmentManager: FragmentManager): AttributesFragment {
        var attributesFragment =
            fragmentManager.findFragmentByTag("ATTRIBUTES") as AttributesFragment?
        if (null == attributesFragment) attributesFragment = AttributesFragment()

        return attributesFragment
    }

    private fun showSelectedFeatureAttributesFormFromEditMode(): Boolean {
        val activity = mActivity ?: return false
        val layer = mSelectedLayer ?: return false
        val layerUI = layer as? IVectorLayerUI ?: return false
        val featureId = editLayerOverlay?.selectedFeatureId ?: Constants.NOT_FOUND.toLong()
        if (featureId == Constants.NOT_FOUND.toLong()) {
            if (mNewFeatureFormLaunchInProgress) return true
            if (editLayerOverlay?.selectedFeatureGeometry == null) return false

            mNewFeatureFormLaunchInProgress = true
            val launched = saveEdits()
            if (!launched) {
                mNewFeatureFormLaunchInProgress = false
            }
            return launched
        }

        if (mEditAttributesFormFromEditMode) return true
        mEditAttributesFormFromEditMode = true

        if (!layer.isFieldsInitialized) {
            layerUI.showEditForm(activity, featureId, null, -1)
            return true
        }

        layerUI.showEditForm(activity, featureId, null, -1)
        return true
    }

    /**
     * From identify (MODE_INFO): enter layer edit session and open the attribute form
     * for [featureId]. Gated by [VectorLayer.isEditingAllowed].
     */
    fun startAttributeFormFromIdentify(featureId: Long) {
        val layer = mSelectedLayer ?: return
        if (!layer.isEditingAllowed) {
            showLayerNotEditableInCollectorToast()
            return
        }

        if (featureId != Constants.NOT_FOUND.toLong()) {
            editLayerOverlay?.setSelectedFeature(featureId)
        }

        val fragmentManager = mActivity?.supportFragmentManager ?: return
        val attributesFragment =
            fragmentManager.findFragmentByTag("ATTRIBUTES") as? AttributesFragment
        attributesFragment?.setSkipRestoreOnDestroy(true)

        (activity as? MainActivity)?.finishFragment()
        if (attributesFragment != null && attributesFragment.isTablet) {
            fragmentManager.beginTransaction().remove(attributesFragment).commit()
        }

        startLayerEditMode()
        mActivity?.bottomToolbar?.post {
            if (mode != MODE_SELECT_ACTION) {
                startLayerEditMode()
            }
            showSelectedFeatureAttributesFormFromEditMode()
        }
    }

    private fun updateCenterCrossVisibility() {
        mCenterCross?.visibility = if (mode == MODE_EDIT && mSelectedLayer != null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    protected fun defineMenuItems() {
        if (mode == MODE_NORMAL || mode == MODE_INFO || isLiveStakeoutMode(mode)
            || mode == MODE_AZIMUTH_POINTS
        ) return

        if (mSelectedLayer == null) {
            setNewMode(MODE_NORMAL)
            return
        }


        val noFeature = editLayerOverlay!!.selectedFeatureGeometry == null
        val featureId = editLayerOverlay!!.selectedFeatureId

        var featureName: String? =
            String.format(getString(com.nextgis.maplibui.R.string.feature_n), featureId)
        if (mSelectedLayer!!.featureLabelField != Constants.FIELD_ID &&
            !noFeature &&
            featureId != Constants.NOT_FOUND.toLong()
        ) {
            val feature = mSelectedLayer!!.getFeature(featureId)
            if (feature != null) featureName = mSelectedLayer!!.getFeatureLabel(feature)
        }

        featureName =
            if (noFeature) getString(com.nextgis.maplibui.R.string.nothing_selected) else if (featureId == Constants.NOT_FOUND.toLong()) getString(
                com.nextgis.maplibui.R.string.new_feature
            ) else featureName
        mActivity!!.title = featureName
        mActivity!!.setSubtitle(mSelectedLayer!!.name)

        val hasSelectedFeature = editLayerOverlay!!.selectedFeatureId != Constants.NOT_FOUND.toLong()
                && editLayerOverlay!!.selectedFeature != null
        val toolbar = mActivity!!.bottomToolbar
        val isViewOnlySelection = mode == MODE_SELECT_FOR_VIEW
        val editingAllowed = mSelectedLayer == null || mSelectedLayer!!.isEditingAllowed

        for (i in 0..<toolbar.menu.size()) {
            var item = toolbar.menu.findItem(R.id.menu_feature_delete)
            if (item != null) ControlHelper.setEnabled(
                item,
                hasSelectedFeature && !isViewOnlySelection && editingAllowed
            )

            item = toolbar.menu.findItem(R.id.menu_feature_edit)
            if (item != null) {
                item.isVisible = editingAllowed
                ControlHelper.setEnabled(item, hasSelectedFeature && editingAllowed)
            }

            item = toolbar.menu.findItem(R.id.menu_feature_attributes)
            if (item != null) ControlHelper.setEnabled(item, hasSelectedFeature)

            item = toolbar.menu.findItem(R.id.menu_feature_edit_attributes)
            if (item != null) ControlHelper.setEnabled(item, hasSelectedFeature && editingAllowed)

            item = toolbar.menu.findItem(R.id.menu_feature_stakeout)
            if (item != null) {
                val geometry = editLayerOverlay!!.selectedFeatureGeometry
                ControlHelper.setEnabled(
                    item,
                    hasSelectedFeature
                            && geometry != null
                            && StakeoutGeometryTarget.isSupportedType(geometry.type)
                )
            }
        }

        updateEditAttributesActionAvailability()
    }

    private fun updateEditAttributesActionAvailability() {
        val overlay = editLayerOverlay ?: return
        val editAttributesItem = mActivity?.bottomToolbar?.menu?.findItem(
            com.nextgis.maplibui.R.id.menu_edit_attributes
        ) ?: return
        val hasPersistedFeature = overlay.selectedFeatureId != Constants.NOT_FOUND.toLong()
                && overlay.selectedFeature != null
        val hasNewFeatureGeometry = overlay.selectedFeatureId == Constants.NOT_FOUND.toLong()
                && overlay.selectedFeature != null
                && overlay.selectedFeatureGeometry != null
        ControlHelper.setEnabled(editAttributesItem, hasPersistedFeature || hasNewFeatureGeometry)
    }


    override fun onDestroyView() {
        HyperLog.v(Constants.TAG, "MapFragment.onDestroyView")
        walkPanel?.setListener(null)
        walkPanel = null
        mapLibreHostResumed = false
        stopMapLibreRenderRecovery()
        mapLibreLayersAppliedForCurrentView = false
        mMapRef.get()?.removeCallbacks(manualGeometryResumeRunnable)
        mapReloadAfterFillRetryRunnable?.let { r ->
            view?.removeCallbacks(r)
        }
        mapReloadAfterFillRetryRunnable = null
        mapReloadAfterFillRetryCount = 0
        val mapView = mapViewOrNull
        if (mapView != null) {
            mapView.removeListener(this)
            if (mapView.map.maplibreMap != null) {
                mapView.map.clearMapListeners()
            }
            mMapRelativeLayout?.removeView(mapView)
        }

        awaitingMapLibreFrameAfterResume = false
        mapLibreMapView?.let { mapLibreView ->
            HyperLog.v(Constants.TAG, "MapLibreMapView.onDestroy")
            mapLibreView.removeOnDidFinishRenderingFrameListener(mapLibreFrameListener)
            mapLibreView.removeOnDidFailLoadingMapListener(mapLibreLoadFailureListener)
            mapLibreView.setOnTouchListener(null)

            val mapDrawable = mapDrawableOrNull
            if (mapDrawable?.maplibreMapView === mapLibreView) {
                mapDrawable.maplibreMap = null
                mapDrawable.maplibreMapView = null
            }
            mapLibreView.onDestroy()
        }
        mapLibreMapView = null

        editLayerOverlay?.mBottomToolbar?.setOnClickListener(null)
        editLayerOverlay?.mBottomToolbar = null

        super.onDestroyView()
    }

    override fun onDestroy() {
        if (mStakeoutController?.isActive == true) {
            mSelectedLayer?.isLocked = false
        }
        if (isMapAzimuthMode(mode)) {
            clearAzimuthMeasurement()
        }
        mStakeoutController?.release()
        mStakeoutController = null
        super.onDestroy()
    }


    protected fun drawScaleRuler() {
        val act = activity ?: return
        val scaleRuler = mScaleRuler ?: return
        val px =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 10f, resources.displayMetrics)
                .toInt()
        val notch =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, 1f, resources.displayMetrics)
                .toInt()
        val ruler = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(ruler)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ContextCompat.getColor(act, com.nextgis.maplibui.R.color.primary_dark)
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(0f, px.toFloat(), px.toFloat(), px.toFloat(), paint)
        canvas.drawLine(0f, px.toFloat(), 0f, 0f, paint)
        canvas.drawLine(0f, 0f, notch.toFloat(), 0f, paint)
        canvas.drawLine(px.toFloat(), px.toFloat(), px.toFloat(), (px - notch).toFloat(), paint)
        scaleRuler.setImageBitmap(ruler)
    }


    protected fun showMapButtons(
        show: Boolean,
        rl: RelativeLayout?
    ) {
        if (null == rl) {
            return
        }
        var v = rl.findViewById<View>(R.id.action_zoom_out)
        if (null != v) {
            if (show) {
                v.visibility = View.VISIBLE
            } else {
                v.visibility = View.GONE
            }
        }

        v = rl.findViewById(R.id.action_zoom_in)
        if (null != v) {
            if (show) {
                v.visibility = View.VISIBLE
            } else {
                v.visibility = View.GONE
            }
        }
    }


    override fun onLayerAdded(id: Int) {
    }


    override fun onLayerDeleted(id: Int) {
        setNewMode(MODE_NORMAL)
    }


    override fun onLayerChanged(id: Int) {

        // remove - moved update to MapViewOverlays
//        Log.e("","");
//
//        //mMapRef.get()!!.map!!.reloadLayerByID(id); // todo change to update ony item - feature id
//        mMapRef.get()!!.map!!.reloadFillLayerStyleToMaplibre(id); // todo change to update ony item - feature id
//
//        mMapRef.get()!!.map!!.checkLayerVisibility(id);

    }

    override fun onLayerVisibleChanged(id: Int) {
        // no need
    }

    override fun onLayerChangedFeatureId(
        oldFeatureId: Long,
        newFeatureId: Long,
        layerId: Int) {
        //// remove - moved update to MapViewOverlays
        //mMapRef.get()!!.map!!.changeFeatureId(oldFeatureId,newFeatureId, layerId);
    }


    override fun onExtentChanged(
        zoom: Float,
        center: GeoPoint
    ) {
        setZoomInEnabled(mMapRef.get()!!.canZoomIn())
        setZoomOutEnabled(mMapRef.get()!!.canZoomOut())
        mScaleRulerText!!.text = rulerText
        if (mZoom != null)
            mZoom!!.text = zoomText
    }


//    protected val zoomText: String
//        get() = String.format("%.0fz", mMapRef.get()!!.zoomLevel)

    protected val zoomText: String
        get() = getZText()  //

    fun getZText(): String {

        val mapLibreMap = mMapRef.get()!!.map.maplibreMap
        if (mapLibreMap != null) {
            //return "${mapLibreMap.zoom.toInt()}z"
            return "%.1fz".format(Locale.US, mapLibreMap.zoom)
        }
        else
            return ".z"
    }

    fun getCurrentZoom(): Int {
        val mapLibreMap = mMapRef.get()!!.map.maplibreMap
        if (mapLibreMap != null)
            return mapLibreMap.zoom.toInt()
        else
            return -1

    }

    protected val rulerText: String
        get() {
            var p1 =
                GeoPoint(mScaleRuler!!.left.toDouble(), mScaleRuler!!.bottom.toDouble())
            var p2 =
                GeoPoint(mScaleRuler!!.right.toDouble(), mScaleRuler!!.bottom.toDouble())
            p1 = mMapRef.get()!!.map.screenToMap(p1)
            p2 = mMapRef.get()!!.map.screenToMap(p2)
            p1.crs = GeoConstants.CRS_WEB_MERCATOR
            p2.crs = GeoConstants.CRS_WEB_MERCATOR
            val s = GeoLineString()
            s.add(p1)
            s.add(p2)

            val result = LocationUtil.formatLength(context, s.length, 1)
            if (result == null)
                return "";
            return result;
        }


    override fun onLayersReordered() {




    }


    override fun onLayerDrawFinished(
        id: Int,
        percent: Float
    ) {
        //Log.d(Constants.TAG, "onLayerDrawFinished: " + id + " percent " + percent);
        /*if (percent >= 1.0)
            mLayerDrawn++;
        MainActivity activity = (MainActivity) mActivity;
        if (null != activity){
            if (percent >= 1.0) {
                if (id == mMap.getTopVisibleLayerId()) {
                    activity.onRefresh(false, 0);
                } else {
                    activity.onRefresh(true, (mLayerDrawn * 100) / mMap.getVisibleLayerCount());
                }
            }
        }*/
        if (percent >= 1.0 && id == Constants.DRAW_FINISH_ID) {
            /** mMap.getMap().getId()) {finish id come at end */
            if (null != mActivity) {
                mActivity!!.onRefresh(false)
            }
        }
    }


    override fun onLayerDrawStarted() {
        if (null != mActivity) {
            mActivity!!.onRefresh(true)
        }
    }


    protected fun setZoomInEnabled(bEnabled: Boolean) {
        if (mivZoomIn == null) {
            return
        }

        mivZoomIn!!.isEnabled = bEnabled
    }


    protected fun setZoomOutEnabled(bEnabled: Boolean) {
        if (mivZoomOut == null) {
            return
        }
        mivZoomOut!!.isEnabled = bEnabled
    }

    protected fun setMapLibreZoomOutEnabled() {
        val mapLibreMap = mMapRef.get()!!.map.maplibreMap
        if (mapLibreMap != null){
            if (mapLibreMap.zoom <= mapLibreMap.minZoomLevel) {
                mivZoomOut!!.isEnabled = false
                return
            }
        }
        mivZoomOut!!.isEnabled = true
    }


    protected fun setMapLibreZoomInEnabled() {
        val mapLibreMap = mMapRef.get()!!.map.maplibreMap
        if (mapLibreMap != null){
            if (mapLibreMap.zoom  >= mapLibreMap.maxZoomLevel) {
                mivZoomIn!!.isEnabled = false
                return
            }
        }
        mivZoomIn!!.isEnabled = true
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("finished_walk_edit_id", finishedWalkEditId)
        mapLibreMapView?.onSaveInstanceState(outState)
        outState.putBoolean(BUNDLE_KEY_IS_MEASURING, mRulerOverlay!!.isMeasuring)
        val rulerGeometry = mMapRef.get()?.map?.measurementGeometry
        if (rulerGeometry != null) {
            try {
                outState.putByteArray(BUNDLE_KEY_RULER_GEOMETRY, rulerGeometry.toBlob())
            } catch (exception: IOException) {
                HyperLog.w(Constants.TAG, "Ruler state save failed", exception)
            }
        }
        // A free-point measurement is intentionally transient. Do not restart location/audio
        // guidance or preserve half-selected points after recreation/process death.
        outState.putInt(KEY_MODE, if (isMapAzimuthMode(mode)) MODE_NORMAL else mode)
        outState.putInt(
            BUNDLE_KEY_LAYER,
            if (null == mSelectedLayer) Constants.NOT_FOUND else mSelectedLayer!!.id
        )

        val feature = editLayerOverlay!!.selectedFeature
        outState.putLong(
            BUNDLE_KEY_FEATURE_ID,
            feature?.id ?: Constants.NOT_FOUND.toLong()
        )

        if (null != feature && feature.geometry != null) {
            try {
                outState.putByteArray(BUNDLE_KEY_SAVED_FEATURE, feature.geometry.toBlob())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    override fun onViewStateRestored(
        savedInstanceState: Bundle?
    ) {
        super.onViewStateRestored(savedInstanceState)
        if (null == savedInstanceState) {
            mode = MODE_NORMAL
        } else {
            val restoredMode = savedInstanceState.getInt(KEY_MODE)
            mode = if (restoredMode == LEGACY_MODE_EDIT_BY_TOUCH) MODE_EDIT else restoredMode

            val layerId = savedInstanceState.getInt(BUNDLE_KEY_LAYER)
            val layer = mMapRef.get()!!.getLayerById(layerId)
            var feature: Feature? = null

            if (null != layer && layer is VectorLayer) {
                mSelectedLayer = layer

                if (savedInstanceState.containsKey(BUNDLE_KEY_SAVED_FEATURE)) {
                    var geometry: GeoGeometry? = null

                    try {
                        geometry = GeoGeometryFactory.fromBlob(
                            savedInstanceState.getByteArray(
                                BUNDLE_KEY_SAVED_FEATURE
                            )
                        )
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    feature = Feature()
                    feature.id =
                        savedInstanceState.getLong(BUNDLE_KEY_FEATURE_ID)
                    feature.geometry = geometry
                }
            }

            editLayerOverlay!!.setSelectedLayer(mSelectedLayer)
            editLayerOverlay!!.selectedFeature = feature
        }

        val savedStakeoutSession =
            savedInstanceState?.getInt(KEY_MODE, MODE_NORMAL) == MODE_STAKEOUT
        if (savedStakeoutSession) {
            if (stakeoutUiAttachedInProcess) {
                val geometry = editLayerOverlay?.selectedFeatureGeometry
                if (geometry != null) {
                    try {
                        mStakeoutController?.start(geometry)
                    } catch (exception: RuntimeException) {
                        HyperLog.w(
                            Constants.TAG,
                            "Stakeout restore after configuration change failed",
                            exception
                        )
                        mode = MODE_SELECT_FOR_VIEW
                    }
                } else {
                    mode = MODE_SELECT_FOR_VIEW
                }
            } else {
                // Do not silently restart audible guidance after process death.
                mode = if (mSelectedLayer != null) MODE_SELECT_FOR_VIEW else MODE_NORMAL
            }
        }

        val ctx = context
        val savedWalkSession =
            savedInstanceState?.getInt(KEY_MODE, MODE_NORMAL) == MODE_EDIT_BY_WALK
        /*
         * savedInstanceState can also be restored after process death.  This process-only marker
         * distinguishes a configuration recreation from a cold restoration of the task.
         */
        val restoringAttachedWalkUi = savedWalkSession && walkUiAttachedInProcess
        val walkServiceRunning = WalkEditService.isServiceRunning(ctx)
        /*
         * A configuration recreation of an already visible walk session may be restored
         * silently.  A cold Activity must leave the durable draft untouched until the recovery
         * hub receives an explicit Continue/Discard answer.
         */
        if (restoringAttachedWalkUi
            && (walkServiceRunning || WalkEditService.hasValidDraft(ctx))) {
            restoreWalkSessionFromDraft(preferRunningService = walkServiceRunning)
        } else if (savedWalkSession) {
            // The recovery hub owns a saved task restored in a new process.
            mode = MODE_NORMAL
        }

        val restoringRulerMeasurement = savedInstanceState?.getBoolean(
            BUNDLE_KEY_IS_MEASURING,
            false
        ) == true
        preserveRulerHistoryDuringModeRestore = restoringRulerMeasurement
        try {
            setNewMode(mode)
        } finally {
            preserveRulerHistoryDuringModeRestore = false
        }

        var restoredRulerGeometry: GeoLineString? = null
        if (restoringRulerMeasurement && savedInstanceState?.containsKey(
                BUNDLE_KEY_RULER_GEOMETRY
            ) == true) {
            try {
                restoredRulerGeometry = GeoGeometryFactory.fromBlob(
                    savedInstanceState.getByteArray(BUNDLE_KEY_RULER_GEOMETRY)
                ) as? GeoLineString
            } catch (exception: IOException) {
                HyperLog.w(Constants.TAG, "Ruler state restore failed", exception)
            }
        }
        if (restoringRulerMeasurement)
            startMeasuring(resetHistory = false, restoredGeometry = restoredRulerGeometry)
    }

    /**
     * Rebuild overlay / MapLibre walk session from walkedit_temp.
     * @return true if draft was applied to UI
     */
    private fun restoreWalkSessionFromDraft(preferRunningService: Boolean): Boolean {
        val ctx = context ?: return false
        if (WalkSessionStore.load(ctx) != null) {
            walkPanel?.refresh()
            return true
        }
        if (!WalkEditService.hasValidDraft(ctx) && !preferRunningService)
            return false
        val preferences = WalkEditService.getDraftPreferences(ctx)
        val layerId = preferences.getInt(ConstantsUI.KEY_LAYER_ID, Constants.NOT_FOUND)
        val featureId =
            preferences.getLong(ConstantsUI.KEY_FEATURE_ID, Constants.NOT_FOUND.toLong())
        val layer = mMapRef.get()?.map?.getLayerById(layerId) ?: return false
        if (layer !is VectorLayer)
            return false
        mSelectedLayer = layer
        editLayerOverlay!!.setSelectedLayer(mSelectedLayer)

        if (featureId > Constants.NOT_FOUND) editLayerOverlay!!.setSelectedFeature(featureId)
        else editLayerOverlay!!.newGeometryByWalk()

        val geometry = GeoGeometryFactory.fromWKT(
            preferences.getString(ConstantsUI.KEY_GEOMETRY, ""),
            GeoConstants.CRS_WEB_MERCATOR
        )
        if (geometry != null) {
            val insertIndex = if (preferences.contains(WalkEditService.KEY_INSERT_INDEX)) {
                preferences.getInt(WalkEditService.KEY_INSERT_INDEX, 0)
            } else {
                (geometry as? GeoLineString)?.pointCount ?: 0
            }
            editLayerOverlay!!.restoreWalkTarget(
                preferences.getInt(WalkEditService.KEY_GEOMETRY_INDEX, 0),
                preferences.getInt(WalkEditService.KEY_RING_INDEX, 0),
                insertIndex
            )
            editLayerOverlay!!.setGeometryFromWalkEdit(geometry)
        }

        val adopted = WalkSessionStore.adoptLegacy(ctx, editLayerOverlay!!.selectedFeature.geometry)
        setNewMode(MODE_NORMAL)
        return adopted
    }

    fun hasInterruptedWalkDraft(): Boolean {
        val ctx = context ?: return false
        // Independent sessions expose their actual service/GPS state in the panel.
        // A point form, camera or cold map renderer is not a recording interruption.
        if (WalkSessionStore.load(ctx) != null) return false
        if (!WalkEditService.hasValidDraft(ctx))
            return false
        /*
         * START_STICKY can restart the service before MainActivity resumes.  That is still an
         * interrupted UI session when no walk editor is attached, and must go through the hub.
         */
        return mode != MODE_EDIT_BY_WALK || !WalkEditService.isServiceRunning(ctx)
    }

    /** Pause a sticky service that has no attached walk editor while the hub awaits a decision. */
    fun pauseInterruptedWalkForRecovery() {
        val ctx = context ?: return
        if (mode != MODE_EDIT_BY_WALK) {
            WalkEditService.pauseAndKeepDraft(ctx)
        }
    }

    /** Continue after crash / soft-interrupt: restore UI and restart WalkEditService. */
    fun resumeWalkFromDraft(): Boolean {
        val ctx = context ?: return false
        if (!WalkEditService.hasValidDraft(ctx))
            return false
        walkInterruptPromptShown = false
        crashRecoveryWalkDialogShown = false
        if (!restoreWalkSessionFromDraft(preferRunningService = false))
            return false
        setNewMode(MODE_NORMAL)
        val activityName = activity?.javaClass?.name
        return WalkEditService.resumeFromDraft(ctx, activityName)
    }

    fun discardWalkDraft() {
        val ctx = context ?: return
        if (WalkSessionStore.isPointActive(ctx)) return
        walkInterruptPromptShown = false
        crashRecoveryWalkDialogShown = false
        if (WalkEditService.isServiceRunning(ctx)) {
            WalkEditService.stopAndClearDraft(ctx)
        }
        // Make Discard observable immediately; ACTION_STOP clears it again when the service exits.
        WalkEditService.clearDraft(ctx)
        walkPanel?.refresh()
        if (mode == MODE_EDIT_BY_WALK) {
            setNewMode(MODE_SELECT_ACTION)
        }
    }

    /**
     * Soft-interrupt while UI is still in walk mode but the service died.
     * Offers Continue / Discard once per interruption.
     */
    fun checkWalkServiceWatchdog() {
        val ctx = context ?: return
        if (WalkEditService.isServiceRunning(ctx)) {
            walkInterruptPromptShown = false
            return
        }
        /*
         * Orphan drafts discovered during cold start belong to MainActivity's ordered recovery
         * hub.  This local watchdog only owns a service death while the walk UI is already open.
         */
        val interrupted = mode == MODE_EDIT_BY_WALK && WalkEditService.hasValidDraft(ctx)
        if (!interrupted)
            return
        if (walkInterruptPromptShown || crashRecoveryWalkDialogShown)
            return
        walkInterruptPromptShown = true
        showWalkInterruptedDialog()
    }

    private fun showWalkInterruptedDialog() {
        val act = activity ?: return
        AlertDialog.Builder(act)
            .setTitle(com.nextgis.maplibui.R.string.walkedit_interrupted_title)
            .setMessage(com.nextgis.maplibui.R.string.walkedit_interrupted_message)
            .setPositiveButton(com.nextgis.maplibui.R.string.walkedit_continue) { _, _ ->
                resumeWalkFromDraft()
            }
            .setNegativeButton(com.nextgis.maplibui.R.string.discard) { _, _ ->
                discardWalkDraft()
            }
            .setCancelable(false)
            .show()
    }

    private fun isManualGeometryEditMode(value: Int = mode): Boolean {
        return value == MODE_EDIT
    }

    private fun currentMapDraftPath(): String? {
        return mApp?.map?.path?.absolutePath
    }

    /**
     * Persist the latest non-walk geometry. commit() in the store is deliberate: apply() can
     * still be pending when the user swipes the process away.
     */
    private fun persistManualGeometryDraft(reason: String): Boolean {
        if (!isManualGeometryEditMode()) {
            return false
        }

        val overlay = editLayerOverlay ?: return false
        if (!overlay.hasEdits()) {
            GeometryEditDraftStore.clear(context, "no-edits-$reason")
            return false
        }

        val layer = mSelectedLayer
        val feature = overlay.selectedFeature
        val geometry = feature?.geometry
        val mapPath = currentMapDraftPath()
        if (layer == null || feature == null || geometry == null || mapPath.isNullOrBlank()) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft save skipped reason=$reason mode=${modeName(mode)} " +
                    "layer=${layer?.id ?: Constants.NOT_FOUND} feature=${feature?.id
                        ?: Constants.NOT_FOUND} geometry=${geometry?.type ?: Constants.NOT_FOUND}"
            )
            return false
        }

        return try {
            val snapshot = GeometryEditDraftStore.Snapshot().apply {
                layerId = layer.id
                featureId = feature.id
                editMode = mode
                geometryWkt = geometry.toWKT(true)
                this.mapPath = mapPath
            }
            GeometryEditDraftStore.save(requireContext(), snapshot, reason)
        } catch (e: RuntimeException) {
            HyperLog.e(
                Constants.TAG,
                "GeometryDraft save crashed reason=$reason layer=${layer.id} " +
                    "feature=${feature.id}: ${e.message}",
                e
            )
            false
        }
    }

    private fun recoverableManualGeometryDraft(): GeometryEditDraftStore.Snapshot? {
        val ctx = context ?: return null
        val snapshot = GeometryEditDraftStore.load(ctx) ?: return null
        val currentMapPath = currentMapDraftPath()
        if (currentMapPath.isNullOrBlank() || snapshot.mapPath != currentMapPath) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft rejected: active map differs layer=${snapshot.layerId}"
            )
            GeometryEditDraftStore.clear(ctx, "map-mismatch")
            return null
        }

        val layer = mMapRef.get()?.map?.getLayerById(snapshot.layerId)
        if (layer !is VectorLayer) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft rejected: vector layer missing layer=${snapshot.layerId}"
            )
            GeometryEditDraftStore.clear(ctx, "layer-missing")
            return null
        }
        if (!layer.isEditingAllowed) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft temporarily unavailable: layer not editable layer=${snapshot.layerId}"
            )
            return null
        }

        val geometry = GeometryEditDraftStore.geometryFromSnapshot(snapshot)
        if (geometry == null || !Geo.isGeometryTypeSame(layer.geometryType, geometry.type)) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft rejected: geometry type mismatch layer=${snapshot.layerId} " +
                    "layerType=${layer.geometryType} draftType=${geometry?.type
                        ?: Constants.NOT_FOUND}"
            )
            GeometryEditDraftStore.clear(ctx, "geometry-invalid")
            return null
        }
        if (snapshot.featureId != Constants.NOT_FOUND.toLong()
            && layer.getFeature(snapshot.featureId) == null
        ) {
            HyperLog.w(
                Constants.TAG,
                "GeometryDraft rejected: feature missing layer=${snapshot.layerId} " +
                    "feature=${snapshot.featureId}"
            )
            GeometryEditDraftStore.clear(ctx, "feature-missing")
            return null
        }
        return snapshot
    }

    fun hasInterruptedManualGeometryDraft(): Boolean {
        val snapshot = recoverableManualGeometryDraft() ?: return false
        val sameLiveSession = isManualGeometryEditMode()
            && mSelectedLayer?.id == snapshot.layerId
            && editLayerOverlay?.selectedFeatureId == snapshot.featureId
            && editLayerOverlay?.hasEdits() == true
        HyperLog.v(
            Constants.TAG,
            "GeometryDraft recovery check interrupted=${!sameLiveSession} " +
                "uiMode=${modeName(mode)} layer=${snapshot.layerId} feature=${snapshot.featureId}"
        )
        return !sameLiveSession
    }

    /**
     * Continue from the recovery hub. MapLibre sources are asynchronous on cold start, so a
     * bounded retry keeps the user's explicit choice pending until the editable source exists.
     */
    fun resumeManualGeometryFromDraft(): Boolean {
        val snapshot = recoverableManualGeometryDraft() ?: return false
        pendingManualGeometryResume = true
        pendingManualGeometrySnapshot = snapshot
        manualGeometryResumeRetryCount = 0
        HyperLog.v(
            Constants.TAG,
            "GeometryDraft Continue requested layer=${snapshot.layerId} " +
                "feature=${snapshot.featureId} mode=${modeName(snapshot.editMode)}"
        )
        return tryResumeManualGeometryFromDraft()
    }

    private fun tryResumeManualGeometryFromDraft(): Boolean {
        if (!pendingManualGeometryResume) {
            return false
        }
        val snapshot = pendingManualGeometrySnapshot ?: recoverableManualGeometryDraft() ?: run {
            pendingManualGeometryResume = false
            return false
        }
        val layer = mMapRef.get()?.map?.getLayerById(snapshot.layerId) as? VectorLayer
            ?: return scheduleManualGeometryResumeRetry("layer-not-ready")
        WalkSessionStore.load(context)?.let { session ->
            if (session.isPointActive && session.pointLayer == snapshot.layerId) pointSessionId = session.pointId
            if (session.phase == WalkSessionPolicy.Phase.FINISHED && session.layerId == snapshot.layerId
                && session.featureId == snapshot.featureId) finishedWalkEditId = session.id
        }
        val geometry = GeometryEditDraftStore.geometryFromSnapshot(snapshot)
            ?: run {
                pendingManualGeometryResume = false
                GeometryEditDraftStore.clear(context, "resume-geometry-invalid")
                return false
            }
        val feature = if (snapshot.featureId == Constants.NOT_FOUND.toLong()) {
            Feature().apply { id = Constants.NOT_FOUND.toLong() }
        } else {
            layer.getFeature(snapshot.featureId)
                ?: return scheduleManualGeometryResumeRetry("feature-not-ready")
        }
        feature.geometry = geometry

        val mapDrawable = mMapRef.get()?.map
            ?: return scheduleManualGeometryResumeRetry("map-not-ready")
        if (snapshot.editMode == MODE_EDIT && !mapDrawable.areEditSourcesReadyForCurrentStyle()) {
            return scheduleManualGeometryResumeRetry("editable-source-not-ready")
        }

        mSelectedLayer = layer
        editLayerOverlay!!.setSelectedLayer(layer)
        editLayerOverlay!!.selectedFeature = feature
        editLayerOverlay!!.fillDrawItems(geometry)

        if (snapshot.editMode == MODE_EDIT) {
            mapDrawable.startFeatureSelectionForEdit(
                layer,
                layer.geometryType,
                feature,
                snapshot.featureId == Constants.NOT_FOUND.toLong(),
                layer.defaultStyleNoExcept,
                false
            )
            if (mapDrawable.editingObject == null) {
                return scheduleManualGeometryResumeRetry("editable-source-not-ready")
            }
            mapDrawable.replaceGeometryFromHistoryChanges(geometry)
            mapDrawable.updateMarkerByEditObject()
        }

        undoRedoOverlay!!.clearHistory()
        undoRedoOverlay!!.saveToHistory(feature)
        editLayerOverlay!!.setHasEdits(true)
        setNewMode(snapshot.editMode)
        defineMenuItems()
        mMapRef.get()?.buffer()
        mMapRef.get()?.postInvalidate()

        pendingManualGeometryResume = false
        pendingManualGeometrySnapshot = null
        manualGeometryResumeRetryCount = 0
        mMapRef.get()?.removeCallbacks(manualGeometryResumeRunnable)
        HyperLog.v(
            Constants.TAG,
            "GeometryDraft resumed layer=${snapshot.layerId} feature=${snapshot.featureId} " +
                "mode=${modeName(snapshot.editMode)} geometryType=${geometry.type}"
        )
        return true
    }

    private fun scheduleManualGeometryResumeRetry(reason: String): Boolean {
        if (!pendingManualGeometryResume) {
            return false
        }
        if (manualGeometryResumeRetryCount >= manualGeometryResumeMaxRetries) {
            pendingManualGeometryResume = false
            pendingManualGeometrySnapshot = null
            HyperLog.e(
                Constants.TAG,
                "GeometryDraft resume timed out reason=$reason retries=$manualGeometryResumeRetryCount"
            )
            context?.let {
                Toast.makeText(
                    it,
                    com.nextgis.maplibui.R.string.geometry_edit_restore_error,
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }
        manualGeometryResumeRetryCount++
        if (manualGeometryResumeRetryCount == 1
            || manualGeometryResumeRetryCount % 10 == 0
        ) {
            HyperLog.v(
                Constants.TAG,
                "GeometryDraft resume deferred reason=$reason " +
                    "retry=$manualGeometryResumeRetryCount"
            )
        }
        mMapRef.get()?.removeCallbacks(manualGeometryResumeRunnable)
        mMapRef.get()?.postDelayed(manualGeometryResumeRunnable, 100)
        return true
    }

    fun discardManualGeometryDraft() {
        val draft = GeometryEditDraftStore.load(context)
        val walk = WalkSessionStore.load(context)
        if (walk != null && walk.isPointActive && walk.pointLayer == draft?.layerId) {
            pointSessionId = walk.pointId
            finishPointCreation()
        }
        pendingManualGeometryResume = false
        pendingManualGeometrySnapshot = null
        manualGeometryResumeRetryCount = 0
        mMapRef.get()?.removeCallbacks(manualGeometryResumeRunnable)
        GeometryEditDraftStore.clear(context, "user-discard")
        HyperLog.v(Constants.TAG, "GeometryDraft Discard selected")
    }

    private fun clearManualGeometryDraft(reason: String) {
        pendingManualGeometryResume = false
        pendingManualGeometrySnapshot = null
        manualGeometryResumeRetryCount = 0
        mMapRef.get()?.removeCallbacks(manualGeometryResumeRunnable)
        GeometryEditDraftStore.clear(context, reason)
    }


    override fun onStart() {
        super.onStart()
        mapLibreMapView?.let { mapLibreView ->
            HyperLog.v(Constants.TAG, "MapLibreMapView.onStart")
            mapLibreView.onStart()
        }
    }

    override fun onPause() {
        HyperLog.v(
            Constants.TAG,
            "MapFragment.onPause mode=${modeName(mode)} " +
                "layer=${mSelectedLayer?.id ?: Constants.NOT_FOUND} " +
                "feature=${editLayerOverlay?.selectedFeatureId ?: Constants.NOT_FOUND} " +
                "hasEdits=${editLayerOverlay?.hasEdits() == true}"
        )
        persistManualGeometryDraft("onPause")
        mStakeoutController?.setForeground(false)
        if (null != mCurrentLocationOverlay) {
            mCurrentLocationOverlay!!.stopShowingCurrentLocation()
        }
        if (null != mGpsEventSource) {
            mGpsEventSource!!.removeListener(this)
        }
        if (null != editLayerOverlay) {
            editLayerOverlay!!.removeListener(this)
            editLayerOverlay!!.onPause()
        }

        val edit = mPreferences!!.edit()
        if (null != mMapRef.get()) {
            if (mMapRef.get()!!.map!!.maplibreMap != null) {
                val mapLibreMap = mMapRef.get()!!.map!!.maplibreMap
                edit.putFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL,
                    mapLibreMap.cameraPosition.zoom.toFloat())
                edit.putFloat(
                    AppSettingsConstants.KEY_PREF_MAP_BEARING,
                    if (isMapRotationEnabled) mapLibreMap.cameraPosition.bearing.toFloat() else 0f
                )

                val point2 = mMapRef.get()!!.map.getMaplibreCenter()
                edit.putLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_X,
                    java.lang.Double.doubleToRawLongBits(point2.x))
                edit.putLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_Y,
                    java.lang.Double.doubleToRawLongBits(point2.y))
            } else {
                edit.putFloat(SettingsConstantsUI.KEY_PREF_ZOOM_LEVEL, mMapRef.get()!!.zoomLevel)
                val point = mMapRef.get()!!.mapCenter

                edit.putLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_X,
                    java.lang.Double.doubleToRawLongBits(point.x))
                edit.putLong(
                    SettingsConstantsUI.KEY_PREF_SCROLL_Y,
                    java.lang.Double.doubleToRawLongBits(point.y))
            }
            mMapRef.get()!!.removeListener(this)
        }
        edit.apply()

        mActivity?.unregisterReceiver(mMessageStyling)
        mActivity?.unregisterReceiver(mMessageReload)

        awaitingMapLibreFrameAfterResume = false
        mapLibreHostResumed = false
        stopMapLibreRenderRecovery()
        mapLibreMapView?.let { mapLibreView ->
            HyperLog.v(Constants.TAG, "MapLibreMapView.onPause")
            mapLibreView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        mapLibreMapView?.let { mapLibreView ->
            HyperLog.v(Constants.TAG, "MapLibreMapView.onStop")
            mapLibreView.onStop()
        }
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapLibreMapView?.let { mapLibreView ->
            HyperLog.v(Constants.TAG, "MapLibreMapView.onLowMemory")
            mapLibreView.onLowMemory()
        }
    }


    override fun onResume() {
        HyperLog.v(Constants.TAG, "MapFragment.onResume")
        super.onResume()

        mapLibreMapView?.let { mapLibreView ->
            mapLibreHostResumed = true
            awaitingMapLibreFrameAfterResume = true
            HyperLog.v(Constants.TAG, "MapLibreMapView.onResume")
            mapLibreView.onResume()
            startMapLibreRenderRecovery("resume")
        }

        ensureMapViewBoundToApplicationMap()
        mApp?.let { (it as IGISApplication).flushPendingMapReloadAfterLayerFillIfNeeded(this) }

        var showControls =
            mPreferences!!.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_ZOOM_CONTROLS, true)
        showMapButtons(showControls, mMapRelativeLayout)

        if (Constants.DEBUG_MODE) Log.d(
            Constants.TAG,
            "KEY_PREF_SHOW_ZOOM_CONTROLS: " + (if (showControls) "ON" else "OFF")
        )

        showControls =
            mPreferences!!.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_SCALE_RULER, false)
        if (showControls && !isLiveStakeoutMode(mode) && mode != MODE_AZIMUTH_POINTS) {
            mScaleRulerLayout!!.visibility = View.VISIBLE
        }
        else mScaleRulerLayout!!.visibility = View.GONE

        showControls = mPreferences!!.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_ZOOM, true)
        if (showControls) {
//            mZoomLevel.setVisibility(View.VISIBLE);
            if (mZoom != null) mZoom!!.visibility = View.VISIBLE
        } else {
//            mZoomLevel.setVisibility(View.GONE);
            if (mZoom != null) mZoom!!.visibility = View.GONE
        }

        showControls =
            mPreferences!!.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_MEASURING, true)
        mRuler!!.visibility = if (showControls && mode == MODE_NORMAL) View.VISIBLE else View.GONE
        mAzimuth?.visibility = if (mode == MODE_NORMAL) View.VISIBLE else View.GONE

        if (null != mMapRef.get()) {
            mMapRef.get()!!.map.setBackground(mApp!!.mapBackground)
            mMapRef.get()!!.addListener(this)
        }

        val coordinatesFormat =
            mPreferences!!.getString(
                SettingsConstantsUI.KEY_PREF_COORD_FORMAT,
                Location.FORMAT_DEGREES.toString() + ""
            )!!
        mCoordinatesFormat =
            if (FileUtil.isIntegerParseInt(coordinatesFormat)) coordinatesFormat.toInt()
            else Location.FORMAT_DEGREES
        mCoordinatesFraction = mPreferences!!.getInt(
            SettingsConstantsUI.KEY_PREF_COORD_FRACTION,
            AppConstants.DEFAULT_COORDINATES_FRACTION_DIGITS
        )

        if (null != mCurrentLocationOverlay) {
            mCurrentLocationOverlay!!.updateMode(
                mPreferences!!.getString(
                    SettingsConstantsUI.KEY_PREF_SHOW_CURRENT_LOC,
                    "3"
                )
            )
            mCurrentLocationOverlay!!.startShowingCurrentLocation()
        }
        if (null != mGpsEventSource) {
            mGpsEventSource!!.addListener(this)
            mStakeoutController?.setForeground(true)
            if (mGPSDialog == null || !mGPSDialog!!.isShowing) mGPSDialog =
                NotificationHelper.showLocationInfo(
                    activity
                )
        }

        if (null != editLayerOverlay) {
            editLayerOverlay!!.addListener(this)
            editLayerOverlay!!.onResume()
        }

        try {
            val statusPanelModeStr =
                mPreferences!!.getString(SettingsConstantsUI.KEY_PREF_SHOW_STATUS_PANEL, "1")!!
            mStatusPanelMode =
                if (FileUtil.isIntegerParseInt(statusPanelModeStr)) statusPanelModeStr.toInt()
                else 0
        } catch (e: ClassCastException) {
            mStatusPanelMode = 0
            if (Constants.DEBUG_MODE) Log.d(
                Constants.TAG,
                "Previous version of KEY_PREF_SHOW_STATUS_PANEL of bool type. Let set it to 0"
            )
        }

        if (null != mStatusPanel) {
            if (mStatusPanelMode != 0) {
                mStatusPanel!!.visibility = View.VISIBLE
                fillStatusPanel(mGpsEventSource!!.lastKnownLocation)

                if (mode != MODE_NORMAL && !isLiveStakeoutMode(mode)
                    && mode != MODE_AZIMUTH_POINTS && mStatusPanelMode != 3
                ) mStatusPanel!!.visibility =
                    View.INVISIBLE
            } else {
                mStatusPanel!!.removeAllViews()
            }

            setMarginsToPanel()
        }

        val showCompass =
            mPreferences!!.getBoolean(AppSettingsConstants.KEY_PREF_SHOW_COMPASS, true)
        checkCompass(showCompass)

        updateLastLocation()

        if (GISApplication.needUpdateBackground){
            try {
                GISApplication.needUpdateBackground = false
                mapViewOrNull?.map?.updateMapBackground()
            } catch (exception : Exception) {
                HyperLog.w(Constants.TAG, "MapFragment.onResume: " + exception.message, exception)
            }

        }
        val intentFilter = IntentFilter()
        intentFilter.addAction( MESSAGE_INTENT_STYLING)

        val intentFilterReload = IntentFilter()
        intentFilterReload.addAction( MESSAGE_INTENT_RELOAD)
//        intentFilter.addAction( MESSAGE_INTENT_STYLING_RASTER)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mActivity?.registerReceiver( mMessageStyling, intentFilter, RECEIVER_NOT_EXPORTED)
            mActivity?.registerReceiver( mMessageReload, intentFilterReload, RECEIVER_NOT_EXPORTED)
        } else {
            mActivity?.registerReceiver(mMessageStyling, intentFilter)
            mActivity?.registerReceiver(mMessageReload, intentFilterReload)
        }

        val ctx = context ?: return
        val progressStyling = (ctx.applicationContext as IGISApplication).getingStyleInProgress
        changeProgress(progressStyling)

        walkPanel?.refresh()
        checkWalkServiceWatchdog()


        val listOfLayers = (context?.applicationContext  as IGISApplication).getlayersToRefresh()
        if (listOfLayers!= null)
            for (layerId in listOfLayers)
            (context?.applicationContext  as IGISApplication).removeLayerToRefresh(layerId)
        if (listOfLayers!= null)
            for (layerId in listOfLayers){
                if (mMapRef.get()!= null && mMapRef.get()!!.map!= null && layerId != -1){
                    val targetlayer = LayerGroup.getVectorLayersById(mMapRef.get()?.map, layerId)
                    if (targetlayer != null) {
                        val isVisible = (targetlayer as Layer).isVisible()
                        if (isVisible) {
                            if (mMapRef.get()!!.map!!.getLayerVisible(layerId)) {
                                Handler().postDelayed({
                                    mMapRef.get()!!.map!!.refreshLayerVisibility(layerId, false)
                                }, 300)

                                Handler().postDelayed({
                                    mMapRef.get()!!.map!!.refreshLayerVisibility(layerId, true)
                                }, 600)
                            }
                        }
                    }
                }
            }
    }

    protected fun setMarginsToPanel() {
        val act = mActivity ?: return
        val statusPanel = mStatusPanel ?: return
        val toolbar = act.bottomToolbar

        toolbar.post {
            val isToolbarVisible = toolbar.visibility == View.VISIBLE
            val isPanelVisible = statusPanel.visibility == View.VISIBLE
            val toolbarHeight = toolbar.measuredHeight

            val lp = statusPanel.layoutParams as RelativeLayout.LayoutParams
            var bottom = if (isToolbarVisible && isPanelVisible) toolbarHeight
            else 0

            lp.setMargins(lp.leftMargin, lp.topMargin, lp.rightMargin, bottom)
            statusPanel.layoutParams = lp

            bottom = if (isToolbarVisible && !isPanelVisible) toolbarHeight
            else 0

            statusPanel.minimumHeight = bottom
            statusPanel.requestLayout()
        }
    }


    protected fun checkCompass(showCompass: Boolean) {
        val mapLayout = mMapRelativeLayout ?: return
        val compassContainer = R.id.fl_compass
        val compass = mapLayout.findViewById<FrameLayout>(compassContainer)

        if (!showCompass) {
            compass.visibility = View.GONE
            return
        }

        val fragmentManager = (mActivity ?: return).supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        //get or create fragment
        var compassFragment =
            fragmentManager.findFragmentByTag("NEEDLE_COMPASS") as CompassFragment?
        if (null == compassFragment) compassFragment = CompassFragment()

        compass.isClickable = false
        compassFragment.setStyle(true)
        if (!compassFragment.isAdded) fragmentTransaction.add(
            compassContainer,
            compassFragment,
            "NEEDLE_COMPASS"
        )
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)

        if (!compassFragment.isVisible) {
            fragmentTransaction.show(compassFragment)
        }

        fragmentTransaction.commit()

        compass.visibility = View.VISIBLE
        compass.setOnClickListener(this)
        compass.setOnLongClickListener {
            mIsCompassDragging = true
            mVibrator?.vibrate(5)
            true
        }
        // Thanks to http://javatechig.com/android/how-to-drag-a-view-in-android
        compass.setOnTouchListener(object : OnTouchListener {
            private var _xDelta = 0
            private var _yDelta = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val X = event.rawX.toInt()
                val Y = event.rawY.toInt()
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val lParams = v.layoutParams as RelativeLayout.LayoutParams
                        _xDelta = X - lParams.leftMargin
                        _yDelta = Y - lParams.topMargin
                        return false
                    }

                    MotionEvent.ACTION_UP -> {
                        mIsCompassDragging = false
                        return false
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!mIsCompassDragging) return false

                        val layoutParams = v.layoutParams as RelativeLayout.LayoutParams
                        val width = v.width
                        val height = v.height
                        var toolbarHeight = 0
                        if (mActivity!!.supportActionBar != null) toolbarHeight =
                            mActivity!!.supportActionBar!!
                                .height
                        if (X > width / 3 && X < v.rootView.width - width / 3) layoutParams.leftMargin =
                            X - _xDelta
                        if (Y > height / 2 + toolbarHeight && Y < v.rootView.height - height / 2) layoutParams.topMargin =
                            Y - _yDelta

                        v.layoutParams = layoutParams
                    }
                }
                mMapRelativeLayout!!.invalidate()
                return true
            }
        })
    }


    protected fun addNewGeometry() {
        if (isDialogShown || !beginPointCreation(EDIT_LAYER)) return
        mApp!!.sendEvent(ConstantsUI.GA_LAYER, ConstantsUI.GA_EDIT, ConstantsUI.GA_FAB)

        //show select layer dialog if several layers, else start default or custom form
        val layers = filterLayersForCreation( mMapRef.get()!!.getVectorLayersByType(if (WalkSessionStore.load(context) != null)
            GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck else
            GeoConstants.GTPointCheck or GeoConstants.GTMultiPointCheck or
                    GeoConstants.GTLineStringCheck or GeoConstants.GTMultiLineStringCheck or
                    GeoConstants.GTPolygonCheck or GeoConstants.GTMultiPolygonCheck))

        if (layers.isEmpty()) {
            finishPointCreation()
            Toast.makeText(mActivity, getString(R.string.warning_no_edit_layers), Toast.LENGTH_LONG)
                .show()
        } else if (layers.size == 1) {
            val layer = layers[0] as VectorLayer
            startNewGeometryCreation(layer)

            Toast.makeText(
                mActivity,
                String.format(getString(R.string.edit_layer), layer.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            if (isDialogShown) return
            //open choose edit layer dialog
            mChooseLayerDialogRef = WeakReference(ChooseLayerDialog(false, false))
            mChooseLayerDialogRef.get()!!.setPointSessionId(pointSessionId)
            mChooseLayerDialogRef.get()!!.setLayerList(layers)
                .setCode(EDIT_LAYER)
                .setTitle(getString(com.nextgis.maplibui.R.string.choose_layers))
                .setTheme(mActivity!!.themeId) //.show(mActivity.getSupportFragmentManager(), "choose_layer");
                .show(childFragmentManager, ChooseLayerDialog.TAG)
        }
    }

    /** Start a new sketch immediately: one centre point/node, then taps add subsequent nodes. */
    private fun startNewGeometryCreation(layer: VectorLayer) {
        bindPointCreation(layer)
        ensureLayerVisibleForCreation(layer)
        if (mSelectedLayer !== layer) {
            mSelectedLayer?.isLocked = false
        }
        mSelectedLayer = layer
        editLayerOverlay!!.setSelectedLayer(layer)
        editLayerOverlay!!.selectedFeature = Feature()
        editLayerOverlay!!.createNewGeometry()
        undoRedoOverlay!!.clearHistory()
        setNewMode(MODE_EDIT)
        editLayerOverlay!!.setHasEdits(true)

        val map = mMapRef.get()?.map ?: return
        map.startFeatureSelectionForEdit(
            layer,
            layer.geometryType,
            editLayerOverlay!!.selectedFeature,
            true,
            layer.defaultStyleNoExcept,
            false
        )
        map.editingObject?.let { editObject ->
            updateGeometryFromMaplibre(
                editObject.editingFeature,
                map.originalSelectedFeature,
                editObject
            )
            map.updateMarkerByEditObject()
        }
        persistManualGeometryDraft("new-sketch-start")
        HyperLog.v(Constants.TAG, "Geometry edit session started new layer=${layer.id}")
    }


    protected fun addPointByTap() {
        if (isDialogShown || !beginPointCreation(ADD_POINT_BY_TAP)) return
        if (mSelectedLayer != null) mSelectedLayer!!.isLocked = false

        //show select layer dialog if several layers, else start default or custom form
        val layers = filterLayersForCreation(mMapRef.get()!!.getVectorLayersByType(GeoConstants.GTPointCheck
                or GeoConstants.GTMultiPointCheck))

        if (layers.isEmpty()) {
            finishPointCreation()
            Toast.makeText(
                mActivity, getString(R.string.warning_no_edit_layers), Toast.LENGTH_LONG
            )
                .show()
        } else if (layers.size == 1) {
            //open form
            val layer = layers[0] as VectorLayer

            ensureLayerVisibleForCreation(layer)
            mSelectedLayer = layer
            editLayerOverlay!!.setSelectedLayer(layer)
            createPointFromOverlay(false)

            Toast.makeText(
                mActivity,
                String.format(getString(R.string.edit_layer), layer.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            if (isDialogShown) return
            //open choose edit layer dialog
            mChooseLayerDialogRef = WeakReference(ChooseLayerDialog(false, false))
            mChooseLayerDialogRef.get()!!.setPointSessionId(pointSessionId)
            mChooseLayerDialogRef.get()!!.setLayerList(layers)
                .setCode(ADD_POINT_BY_TAP)
                .setTitle(getString(com.nextgis.maplibui.R.string.choose_layers))
                .setTheme(mActivity!!.themeId)
                .show(mActivity!!.supportFragmentManager, ChooseLayerDialog.TAG)
        }
    }

    protected fun createPointFromOverlay(isFillByWalking: Boolean) {
        mSelectedLayer?.let { bindPointCreation(it) }
        editLayerOverlay!!.selectedFeature = Feature()

        if (mCurrentCenter != null)
            editLayerOverlay!!.selectedFeature.geometry = GeoPoint(mCurrentCenter!!.x, mCurrentCenter!!.y)
        else
            editLayerOverlay!!.selectedFeature.geometry = GeoPoint()
        setNewMode(MODE_EDIT)
        undoRedoOverlay!!.clearHistory()
        val mapLibreMap = mMapRef.get()!!.map!!.maplibreMap
        editLayerOverlay!!.createPointFromOverlay()
        editLayerOverlay!!.setHasEdits(true)
        undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)

        mMapRef.get()!!.map!!.startFeatureSelectionForEdit(mSelectedLayer,
            mSelectedLayer!!.geometryType,
            editLayerOverlay!!.selectedFeature, true,mSelectedLayer!!.defaultStyleNoExcept,
            isFillByWalking)
    }

    // useCreatePouintFromOverlay - need to call if create by click R.id.add_current_location button
    protected fun addCurrentLocation(useCreatePointFromOverlay: Boolean) {
        if (isDialogShown || !beginPointCreation(ADD_CURRENT_LOC)) return
        //show select layer dialog if several layers, else start default or custom form
        val layers = filterLayersForCreation (mMapRef.get()!!.getVectorLayersByType(
            GeoConstants.GTMultiPointCheck or GeoConstants.GTPointCheck))


        if (layers.isEmpty()) {
            finishPointCreation()
            Toast.makeText(
                mActivity, getString(R.string.warning_no_edit_layers), Toast.LENGTH_LONG
            )
                .show()
        } else if (layers.size == 1) {
            //open form
            val vectorLayer = layers[0]
            if (vectorLayer is ILayerUI) {
                ensureLayerVisibleForCreation(vectorLayer as VectorLayer)
                mSelectedLayer = vectorLayer as VectorLayer
                editLayerOverlay!!.setSelectedLayer(mSelectedLayer)

                if (useCreatePointFromOverlay)
                    createPointFromOverlay(false)

                launchCurrentPointForm(vectorLayer)

                Toast.makeText(
                    mActivity,
                    String.format(getString(R.string.edit_layer), vectorLayer.getName()),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                finishPointCreation()
                Toast.makeText(
                    mActivity, getString(R.string.warning_no_edit_layers),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            if (isDialogShown) return
            //open choose dialog
            mChooseLayerDialogRef = WeakReference(ChooseLayerDialog(true, false))
            mChooseLayerDialogRef.get()!!.setPointSessionId(pointSessionId)
            mChooseLayerDialogRef.get()!!.setLayerList(layers)
                .setCode(ADD_CURRENT_LOC)
                .setTitle(getString(com.nextgis.maplibui.R.string.choose_layers))
                .setTheme(mActivity!!.themeId)
                .show(mActivity!!.supportFragmentManager, ChooseLayerDialog.TAG)
        }
    }

    /** Vector layers allowed for object creation (collector «Редактируемый» policy). */
    private fun launchCurrentPointForm(layer: VectorLayer) {
        val layerUI = layer as? IVectorLayerUI ?: return
        if (pointSessionId == null) {
            layerUI.showEditForm(mActivity, Constants.NOT_FOUND.toLong(), null, -1)
            return
        }
        val location = mGpsEventSource?.lastKnownLocation
        if (location == null) {
            Toast.makeText(context, com.nextgis.maplibui.R.string.walk_gps_wait, Toast.LENGTH_LONG).show()
            cancelEdits()
            return
        }
        bindPointCreation(layer)
        val point = GeoPoint(location.longitude, location.latitude).apply {
            crs = GeoConstants.CRS_WGS84
            project(GeoConstants.CRS_WEB_MERCATOR)
        }
        val geometry: GeoGeometry = if (layer.geometryType == GeoConstants.GTMultiPoint)
            GeoMultiPoint().apply { crs = GeoConstants.CRS_WEB_MERCATOR; add(point) } else point
        editLayerOverlay!!.selectedFeature.geometry = geometry
        mMapRef.get()?.map?.replaceGeometryFromHistoryChanges(geometry)
        if (LayerUtil.showSessionEditForm(layer, requireActivity(), Constants.NOT_FOUND.toLong(), geometry, null)) {
            clearManualGeometryDraft("point-form-handoff")
        }
    }

    protected fun filterLayersForCreation(layerList: MutableList<ILayer>): MutableList<ILayer> {
        var i = 0
        while (i < layerList.size) {
            val layer = layerList[i]
            if (layer is VectorLayer && !layer.isEditingAllowed) {
                layerList.removeAt(i)
                continue
            }
            i++
        }
        return layerList
    }

    /** Object creation must not start in a hidden target layer. */
    private fun ensureLayerVisibleForCreation(layer: VectorLayer) {
        if (layer.isVisible) return
        layer.setVisible(true)
        layer.save()
    }

    private fun showLayerNotEditableInCollectorToast() {
        Toast.makeText(
            mActivity,
            com.nextgis.maplibui.R.string.layer_not_editable_in_collector,
            Toast.LENGTH_LONG
        ).show()
    }

    protected fun addGeometryByWalk() {
        if (WalkSessionStore.load(context) != null || WalkEditService.hasValidDraft(context)) {
            Toast.makeText(context, com.nextgis.maplibui.R.string.walk_already_active, Toast.LENGTH_LONG).show()
            walkPanel?.refresh()
            return
        }
        val layers = filterLayersForCreation(
            mMapRef.get()!!.getVectorLayersByType(
                GeoConstants.GTLineStringCheck or GeoConstants.GTPolygonCheck
                    or GeoConstants.GTMultiLineStringCheck or GeoConstants.GTMultiPolygonCheck
            )
        )

        if (layers.isEmpty()) {
            Toast.makeText(mActivity, getString(R.string.warning_no_edit_layers), Toast.LENGTH_LONG)
                .show()
        } else if (layers.size == 1) {
            // Fork walk implementation (CUSTOMIZATIONS §2): explicit MapLibre edit session +
            // validated GNSS anchor for initial geometry. Upstream variant called newGeometryByWalk twice
            // around createPointFromOverlay(true) — reconciled: keep fork pipeline as the more
            // deterministic path (§17 Walk reconciliation).
            val layer = layers[0] as VectorLayer
            ensureLayerVisibleForCreation(layer)
            mSelectedLayer = layer
            editLayerOverlay!!.setSelectedLayer(layer)
            editLayerOverlay!!.newGeometryByWalk()
            if (!applyInitialWalkGeometryAtStartLocation()) return
            prepareMaplibreSessionForNewWalkGeometry()
            setNewMode(MODE_EDIT_BY_WALK)

            Toast.makeText(
                mActivity,
                String.format(getString(R.string.edit_layer), layer.name),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            if (isDialogShown) return
            // Upstream API: ChooseLayerDialog(useCreatePoint, startFillByWalk).
            mChooseLayerDialogRef = WeakReference(ChooseLayerDialog(true, true))
            mChooseLayerDialogRef.get()!!.setLayerList(layers)
                .setCode(ADD_GEOMETRY_BY_WALK)
                .setTitle(getString(com.nextgis.maplibui.R.string.choose_layers))
                .setTheme(mActivity!!.themeId)
                .show(mActivity!!.supportFragmentManager, ChooseLayerDialog.TAG)
        }
    }

    /**
     * MapLibre edit session must exist before walk recording.
     * Start geometry must match [editLayerOverlay] (validated GNSS anchor), not the default camera-centre stub.
     */
    private fun prepareMaplibreSessionForNewWalkGeometry() {
        val map = mMapRef.get()?.map ?: return
        val layer = mSelectedLayer ?: return
        val startGeom = editLayerOverlay!!.selectedFeature?.geometry ?: return
        map.startFeatureSelectionForEdit(
            layer,
            layer.geometryType,
            editLayerOverlay!!.selectedFeature,
            true,
            layer.defaultStyleNoExcept,
            true // isFillByWalking (upstream walk flow)
        )
        val editObj = map.editingObject ?: return
        try {
            map.replaceGeometryFromHistoryChanges(startGeom)
        } catch (ex: Exception) {
            Log.w("MapFragment", "replaceGeometryFromHistoryChanges walk start", ex)
        }
        updateGeometryFromMaplibre(
            editObj.editingFeature,
            map.originalSelectedFeature,
            editObj
        )
    }

    /** Only a fresh validated GNSS fix may seed a walk recording. */
    private fun walkStartAnchorWebMercator(): GeoPoint? {
        val loc = mGpsEventSource?.lastRecordingLocation ?: return null
        val point = GeoPoint(loc.longitude, loc.latitude)
        point.crs = GeoConstants.CRS_WGS84
        return if (point.project(GeoConstants.CRS_WEB_MERCATOR)) point else null
    }

    private fun geoPointWebMercatorCopy(x: Double, y: Double): GeoPoint {
        val p = GeoPoint(x, y)
        p.crs = GeoConstants.CRS_WEB_MERCATOR
        return p
    }

    /** Initial walk geometry is one anchor node; accepted fixes are inserted after it. */
    private fun buildInitialWalkGeometry(geometryType: Int, anchorWm: GeoPoint): GeoGeometry {
        val a = geoPointWebMercatorCopy(anchorWm.x, anchorWm.y)
        when (geometryType) {
            GeoConstants.GTLineString -> {
                val line = GeoLineString()
                line.crs = GeoConstants.CRS_WEB_MERCATOR
                line.add(geoPointWebMercatorCopy(a.x, a.y))
                return line
            }
            GeoConstants.GTMultiLineString -> {
                val line = GeoLineString()
                line.crs = GeoConstants.CRS_WEB_MERCATOR
                line.add(geoPointWebMercatorCopy(a.x, a.y))
                val ml = GeoMultiLineString()
                ml.crs = GeoConstants.CRS_WEB_MERCATOR
                ml.add(line)
                return ml
            }
            GeoConstants.GTPolygon -> {
                val ring = GeoLinearRing()
                ring.crs = GeoConstants.CRS_WEB_MERCATOR
                ring.add(geoPointWebMercatorCopy(a.x, a.y))
                val poly = GeoPolygon()
                poly.crs = GeoConstants.CRS_WEB_MERCATOR
                poly.setOuterRing(ring)
                return poly
            }
            GeoConstants.GTMultiPolygon -> {
                val ring = GeoLinearRing()
                ring.crs = GeoConstants.CRS_WEB_MERCATOR
                ring.add(geoPointWebMercatorCopy(a.x, a.y))
                val poly = GeoPolygon()
                poly.crs = GeoConstants.CRS_WEB_MERCATOR
                poly.setOuterRing(ring)
                val mp = GeoMultiPolygon()
                mp.crs = GeoConstants.CRS_WEB_MERCATOR
                mp.add(poly)
                return mp
            }
            else -> {
                val line = GeoLineString()
                line.crs = GeoConstants.CRS_WEB_MERCATOR
                line.add(geoPointWebMercatorCopy(a.x, a.y))
                return line
            }
        }
    }

    private fun applyInitialWalkGeometryAtStartLocation(): Boolean {
        val layer = mSelectedLayer ?: return false
        val anchor = walkStartAnchorWebMercator()
        if (anchor == null) {
            Toast.makeText(
                context,
                com.nextgis.maplibui.R.string.walk_gps_wait,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        val geom = buildInitialWalkGeometry(layer.geometryType, anchor)
        val feat = editLayerOverlay!!.selectedFeature
        feat.geometry = geom
        editLayerOverlay!!.fillDrawItems(geom)
        return true
    }

    fun onFinishChooseLayerDialog(
        code: Int,
        layer: ILayer?,
        useCreatePointFromOverlay: Boolean,
        startFillByWalk: Boolean
    ) {
        val vectorLayer = layer as? VectorLayer ?: run { finishPointCreation(); return }
        if (code != ADD_GEOMETRY_BY_WALK) bindPointCreation(vectorLayer)

        ensureLayerVisibleForCreation(vectorLayer)

        if (mSelectedLayer != null) mSelectedLayer!!.isLocked = false

        mSelectedLayer = vectorLayer
        editLayerOverlay!!.setSelectedLayer(vectorLayer)


        if (useCreatePointFromOverlay && code != ADD_GEOMETRY_BY_WALK)
            createPointFromOverlay(startFillByWalk)

        if (code == ADD_CURRENT_LOC) {
            if (layer is ILayerUI) {
                launchCurrentPointForm(vectorLayer)
            }
        } else if (code == EDIT_LAYER) {
            startNewGeometryCreation(vectorLayer)
        } else if (code == ADD_GEOMETRY_BY_WALK) {
            editLayerOverlay!!.newGeometryByWalk()
            if (!applyInitialWalkGeometryAtStartLocation()) return
            prepareMaplibreSessionForNewWalkGeometry()
            setNewMode(MODE_EDIT_BY_WALK)
        } else if (code == ADD_POINT_BY_TAP) {
            createPointFromOverlay(false)
        }
    }


    override fun processMapLongClick(clickeEnelope: GeoEnvelope, clickPoint: PointF): Boolean {
        return  onLongPressFromMaplibre(clickeEnelope, clickPoint)
        return true
    }

    override fun processMapClick(screenx: Float, screeny: Float): Boolean {    // x y - screen coordinates
//        Log.e("CCCLLIICK", "screenX: " + screenx + " - " + " screeny: " + screeny)
        onSingleTapUpFromMaplibre(screenx, screeny)
        return true
    }

    fun onLongPressFromMaplibre(clickeEnelope: GeoEnvelope, clickPoint : PointF): Boolean {

        if (!(mode == MODE_NORMAL || mode == MODE_SELECT_ACTION) || mRulerOverlay!!.isMeasuring) {
            return false
        }

        if (null == clickeEnelope)
            return false
        val point = GeoPoint(clickeEnelope.center.x, clickeEnelope.center.y)
        point.crs = GeoConstants.CRS_WEB_MERCATOR

        //show actions dialog
        val layers = mMapRef.get()!!.getVectorLayersByType(GeoConstants.GTAnyCheck)
        var items: List<Long>

        var vectorLayer: VectorLayer? = null
        var selectedSingleVectorLayer: VectorLayer? = null
        var selectedSingleFeatureId: Long = -1

        val mSelectedLayers = ArrayList<String>()
        var geometry: GeoGeometry? = null
        var featureId: Long = -1

        val selectedVectorLayer: MutableList<VectorLayer> = ArrayList()
        val selectedGeometry: MutableList<GeoGeometry?> = ArrayList()
        val selectedFeatures: MutableList<Feature> = ArrayList()

        var originalFeatureForSelect : Feature? = null

        layersLoop@ for (layer in layers) {
            //if (!layer.isValid) continue

            vectorLayer = layer as VectorLayer
            if (!LayerIdentifyPolicy.shouldInclude(vectorLayer)) continue

//            Log.e("CCLICK", "on long:")
//            Log.e("CCLICK", clickeEnelope.toString())
            items = vectorLayer.query(clickeEnelope)
            for (i in items.indices) {    // Refine RTree envelope candidates by actual geometry
                featureId = items[i]
                geometry = vectorLayer.getGeometryForId(featureId)

//                Log.e("CCLICK", "on long check contains point:" + point.toString())
//                Log.e("CCLICK", "on long check contains poly:" + geometry.toString())
                if (EditLayerOverlay.notContains(geometry, point, clickeEnelope)) {
                    continue
                }
                val feature = vectorLayer.getFeature(featureId)
                if (feature == null) {
                    Toast.makeText(mActivity, "not feature for " + featureId, Toast.LENGTH_LONG).show();
                    continue
                }
                originalFeatureForSelect = vectorLayer.getFeature(featureId)
                if (originalFeatureForSelect != null) {
                    val valueForHint = getHintText(vectorLayer, feature)

                    if (feature != null){
                        if (valueForHint == null)
                            mSelectedLayers.add(layer.getName() + ": " + featureId)
                        else
                            mSelectedLayers.add(layer.getName() + ": " + valueForHint)

                        selectedSingleVectorLayer = layer
                        selectedSingleFeatureId = featureId

                        selectedVectorLayer.add(vectorLayer)
                        selectedGeometry.add(geometry)
                        selectedFeatures.add(feature)
                    }
                } else {
                    mSelectedLayers.add(layer.getName() + ": " + featureId + " is null")
                }
            }
        }

        if (mSelectedLayers.size > 1 || selectedFeatures.size > 1) {
            showOverlayPointMultiChoise(
                clickPoint.x.toDouble(), clickPoint.y.toDouble(), mSelectedLayers,
                selectedVectorLayer,
                selectedGeometry,
                selectedFeatures,
                true)
            return  true

        } else if (mSelectedLayers.size == 1 || selectedFeatures.size == 1) {


            if (mSelectedLayer != null)
                mSelectedLayer!!.isLocked = false

            mSelectedLayer = selectedSingleVectorLayer
            editLayerOverlay!!.setSelectedLayer(selectedSingleVectorLayer)

            if (geometry != null && mSelectedLayer != null) {

                editLayerOverlay!!.setSelectedFeature(selectedSingleFeatureId)
                mMapRef.get()!!.map!!.startFeatureSelectionForView(mSelectedLayer, originalFeatureForSelect)
                defineMenuItems()
                if (mode != MODE_SELECT_ACTION)
                    setNewMode(MODE_SELECT_ACTION)
            }


            return true
        } else {
            showOverlayPoint(clickPoint.x.toDouble(), clickPoint.y.toDouble())
        }
        //set select action mode
        //mMapRef.get()!!.postInvalidate()

        return true
    }

    override fun onLongPress(event: MotionEvent) {
//        if (!(mode == MODE_NORMAL || mode == MODE_SELECT_ACTION) || mRulerOverlay!!.isMeasuring) {
//            return
//        }
//
//        val dMinX = (event.x - mTolerancePX).toDouble()
//        val dMaxX = (event.x + mTolerancePX).toDouble()
//        val dMinY = (event.y - mTolerancePX).toDouble()
//        val dMaxY = (event.y + mTolerancePX).toDouble()
//
//        val mapEnv = mMapRef.get()!!.screenToMap(GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)) ?: return
//
//        var exactEnv: GeoEnvelope? = GeoEnvelope(
//            event.x.toDouble(),
//            event.x.toDouble(),
//            event.y.toDouble(),
//            event.y.toDouble()
//        )
//        exactEnv = mMapRef.get()!!.screenToMap(exactEnv)
//        if (null == exactEnv) return
//        val point = GeoPoint(exactEnv.maxX, exactEnv.minY)
//        point.crs = GeoConstants.CRS_WEB_MERCATOR
//
//        //show actions dialog
//        val layers = mMapRef.get()!!.getVectorLayersByType(GeoConstants.GTAnyCheck)
//        var items: List<Long>
//
//
//        var vectorLayer: VectorLayer? = null
//        var selectedSingleVectorLayer: VectorLayer? = null
//        var selectedSingleFeatureId: Long = -1
//
//        val mSelectedLayers = ArrayList<String>()
//        var geometry: GeoGeometry? = null
//        var featureId: Long = -1
//
//        val selectedVectorLayer: MutableList<VectorLayer> = ArrayList()
//        val selectedGeometry: MutableList<GeoGeometry?> = ArrayList()
//        val selectedFeaturesList: MutableList<Feature> = ArrayList()
//
//        layersLoop@ for (layer in layers) {
//            //if (!layer.isValid) continue
//
//            if (!(layer as ILayerView).isVisible) continue
//
//            vectorLayer = layer as VectorLayer
//            items = vectorLayer.query(mapEnv)
//
//            for (i in items.indices) {    // FIXME hack for bad RTree cache
//                featureId = items[i]
//                geometry = vectorLayer.getGeometryForId(featureId)
//                if (EditLayerOverlay.notContains(geometry, point)) {
//                    continue
//                }
//
//                val feature = vectorLayer.getFeature(featureId)
//                val valueForHint = getHintText(vectorLayer, feature)
//
//                if (feature != null){
//                    if (valueForHint == null)
//                        mSelectedLayers.add(layer.getName() + ": " + featureId)
//                    else
//                        mSelectedLayers.add(layer.getName() + ": " + valueForHint)
//                }
//
//                selectedSingleVectorLayer = layer
//                selectedSingleFeatureId = featureId
//
//                selectedVectorLayer.add(vectorLayer)
//                selectedGeometry.add(geometry)
//                selectedFeaturesList.add(feature)
//            }
//        }
//
//        if (mSelectedLayers.size > 1)
//            showOverlayPointMultiChoise(
//                event.x.toDouble(), event.y.toDouble(), mSelectedLayers,
//                selectedVectorLayer,
//                selectedGeometry,
//                selectedFeaturesList,
//                true)
//        else {
//            if (mSelectedLayer != null)
//                mSelectedLayer!!.isLocked = false
//
//            mSelectedLayer = selectedSingleVectorLayer
//            editLayerOverlay!!.setSelectedLayer(selectedSingleVectorLayer)
//
//            if (geometry != null) editLayerOverlay!!.setSelectedFeature(selectedSingleFeatureId)
//
//            setMode(MODE_SELECT_ACTION)
//            showOverlayPoint(event.x.toDouble(), event.y.toDouble())
//        }
//        //set select action mode
//        mMapRef.get()!!.postInvalidate()
        // old odd code
    }

    fun showAddByTapButton() {
        mAddPointButton!!.visibility = View.VISIBLE
    }

    fun hideAddByTapButton() {
        mAddPointButton!!.visibility = View.GONE
    }


    fun showRulerButton() {
        if (mPreferences!!.getBoolean(
                AppSettingsConstants.KEY_PREF_SHOW_MEASURING,
                true
            )
        ) mRuler!!.visibility =
            View.VISIBLE
    }


    fun hideRulerButton() {
        mRuler!!.visibility = View.GONE
    }

    fun showAzimuthButton() {
        mAzimuth?.visibility = View.VISIBLE
    }

    fun hideAzimuthButton() {
        mAzimuth?.visibility = View.GONE
    }


    fun showMainButton() {
        if (mode == MODE_EDIT_BY_WALK) return

        mAddNewGeometry!!.iconDrawable.alpha = 255
        mMainButton!!.visibility = View.VISIBLE
    }


    fun hideMainButton() {
        mMainButton!!.visibility = View.GONE
    }


    fun hideOverlayPoint() {
        editLayerOverlay!!.hideOverlayPoint()
        mMapRef.get()!!.postInvalidate()

        hideAddByTapButton()
        showMainButton()
        mMapRef.get()!!.map.clearPressedPoint()

    }


    fun showOverlayPoint(screenX : Double, screenY: Double) {
        hideMainButton()
        showAddByTapButton()
        editLayerOverlay!!.setOverlayPoint(screenX, screenY)

        val pointf = PointF(screenX.toFloat(), screenY.toFloat())
        val latLng: LatLng = mMapRef.get()!!.map!!.maplibreMap.getProjection().fromScreenLocation(pointf)

        mMapRef.get()!!.map.addPressedPoint(latLng)
    }

    fun showOverlayPointMultiChoise(
        x : Double,  y: Double,
        featureNames: List<String>,
        vectorLayer: List<VectorLayer>,
        geometry: List<GeoGeometry?>,
        features: List<Feature> ,
        editMode : Boolean) {

        val items = featureNames.toTypedArray<String>()
        val ctx = context ?: return
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(R.string.choose_object)
        builder.setItems(items) { dialog, which -> //String selectedItem = items[which];
            // remove after some time
            if (mSelectedLayer != null) mSelectedLayer!!.isLocked = false

            mSelectedLayer = vectorLayer[which]
            editLayerOverlay!!.setSelectedLayer(vectorLayer[which])

            if (geometry[which] != null) {
                showViewModeForFeature(mSelectedLayer!!,
                    features[which],
                    mSelectedLayer!!,
                    geometry[which],
                            features[which].id,
                    features[which].id ,
                    editMode)


//                editLayerOverlay!!.setSelectedFeature(features[which].id)
//                if (editMode)
//                    mMapRef.get()!!.map!!.startFeatureSelectionForEdit(
//                        mSelectedLayer, mSelectedLayer!!.geometryType,
//                        features[which],
//                        false, mSelectedLayer!!.defaultStyleNoExcept)
//                else {
//                    mMapRef.get()!!.map!!.startFeatureSelectionForView(
//                        mSelectedLayer,
//                        features[which])
//                    if (mode != MODE_SELECT_ACTION)
//                        setMode(MODE_SELECT_ACTION)
//                }
            } else {

//                setMode(MODE_SELECT_ACTION)
//                //showOverlayPoint(x,y)
//
//                hideMainButton()
//                showAddByTapButton()
//                editLayerOverlay!!.setOverlayPoint(x,y)
            }
        }
        builder.create().show()
    }

    override fun onSingleTapUp(event: MotionEvent) {
        if (mRulerOverlay!!.isMeasuring) return
        when (mode) {
            MODE_EDIT -> {
                if (editLayerOverlay!!.selectGeometryInScreenCoordinates(
                        event.x,
                        event.y
                    )
                ) undoRedoOverlay!!.saveToHistory(
                    editLayerOverlay!!.selectedFeature
                )
                defineMenuItems()
            }

            MODE_SELECT_ACTION -> {
                editLayerOverlay!!.selectGeometryInScreenCoordinates(event.x, event.y)
                defineMenuItems()
            }

            MODE_INFO -> {
                editLayerOverlay!!.selectGeometryInScreenCoordinates(event.x, event.y)

                if (null != editLayerOverlay) {
                    val attributesFragment =
                        mActivity!!.supportFragmentManager.findFragmentByTag("ATTRIBUTES") as AttributesFragment?
                    if (attributesFragment != null) {
                        attributesFragment.setSelectedFeature(
                            mSelectedLayer,
                            editLayerOverlay!!.selectedFeatureId
                        )
                        mMapRef.get()!!.postInvalidate()
                    }
                }
            }

            else -> if (mode == MODE_NORMAL || mode == MODE_SELECT_FOR_VIEW) {
                // check objects on map to select
                val dMinX = (event.x - mTolerancePX).toDouble()
                val dMaxX = (event.x + mTolerancePX).toDouble()
                val dMinY = (event.y - mTolerancePX).toDouble()
                val dMaxY = (event.y + mTolerancePX).toDouble()

                val mapEnv = mMapRef.get()!!.screenToMap(GeoEnvelope(dMinX, dMaxX, dMinY, dMaxY)) ?: return

                var exactEnv: GeoEnvelope? = GeoEnvelope(
                    event.x.toDouble(),
                    event.x.toDouble(),
                    event.y.toDouble(),
                    event.y.toDouble()
                )
                exactEnv = mMapRef.get()!!.screenToMap(exactEnv)
                if (null == exactEnv) return
                val point = GeoPoint(exactEnv.maxX, exactEnv.minY)
                point.crs = GeoConstants.CRS_WEB_MERCATOR

                //show actions dialog
                val layers = mMapRef.get()!!.getVectorLayersByType(GeoConstants.GTAnyCheck)
                var items: List<Long>


                var vectorLayer: VectorLayer? = null
                var selectedSingleVectorLayer: VectorLayer? = null
                var selectedSingleFeatureId: Long = -1

                val mSelectedLayers = ArrayList<String>()
                var geometry: GeoGeometry? = null
                var featureId: Long = -1

                val selectedVectorLayer: MutableList<VectorLayer> = ArrayList()
                val selectedGeometry: MutableList<GeoGeometry?> = ArrayList()
                val selectedFeatures: MutableList<Feature> = ArrayList()


                layersLoop@ for (layer in layers) {
                    //if (!layer.isValid) continue

                    vectorLayer = layer as VectorLayer
                    if (!LayerIdentifyPolicy.shouldInclude(vectorLayer)) continue
                    items = vectorLayer.query(mapEnv)

                    var i = 0
                    while (i < items.size) {
                        // Refine RTree envelope candidates by actual geometry
                        featureId = items[i]
                        geometry = vectorLayer.getGeometryForId(featureId)
                        if (EditLayerOverlay.notContains(geometry, point, mapEnv)) {
                            i++
                            continue
                        }



                        val feature = vectorLayer.getFeature(featureId)
                        val valueForHint = getHintText(vectorLayer, feature)

                        if (feature != null){
                            if (valueForHint == null)
                                mSelectedLayers.add(layer.getName() + ": " + featureId)
                            else
                                mSelectedLayers.add(layer.getName() + ": " + valueForHint)
                        }

                        selectedSingleVectorLayer = layer
                        selectedSingleFeatureId = featureId

                        selectedVectorLayer.add(vectorLayer)
                        selectedGeometry.add(geometry)
                        selectedFeatures.add(feature)

                        mMapRef.get()!!.map!!.startFeatureSelectionForView(mSelectedLayer, feature)

                        i++
                    }
                }

                if (mSelectedLayers.size == 0 && mode == MODE_SELECT_FOR_VIEW) {
                    // need select none
                    setNewMode(MODE_NORMAL)
                } else {
                    if (mSelectedLayers.size > 1 || selectedFeatures.size >1)
                        showOverlayPointMultiChoise(
                        event.x.toDouble(), event.y.toDouble(), mSelectedLayers,
                        selectedVectorLayer,
                        selectedGeometry,
                        selectedFeatures,
                        false )
                    else {
                        if (mSelectedLayer != null) mSelectedLayer!!.isLocked = false

                        mSelectedLayer = selectedSingleVectorLayer
                        editLayerOverlay!!.setSelectedLayer(selectedSingleVectorLayer)

                        if (geometry != null) editLayerOverlay!!.setSelectedFeature(
                            selectedSingleFeatureId
                        )

                        setNewMode(MODE_SELECT_FOR_VIEW)
                        //showOverlayPoint(event);
                    }
                }
                //set select action mode
                mMapRef.get()!!.postInvalidate()
            } else if (!mRulerOverlay!!.isMeasuring) hideOverlayPoint()
        }
    }

    fun getClickEnelope(clickPoint: PointF, maplibreMap:MapLibreMap): GeoEnvelope {
        val ctx = context ?: return GeoEnvelope()
        val TOLERANCE_DP = 20
        val mTolerancePX = ctx.resources.displayMetrics.density * TOLERANCE_DP

        val minP = PointF(clickPoint.x - mTolerancePX, clickPoint.y - mTolerancePX)
        val maxP = PointF(clickPoint.x + mTolerancePX, clickPoint.y + mTolerancePX)

        val minL: LatLng = maplibreMap.getProjection().fromScreenLocation(minP)
        val maxL: LatLng = maplibreMap.getProjection().fromScreenLocation(maxP)

        val minPoints = MPLFeaturesUtils.convert4326To3857(minL.longitude, minL.latitude)
        val maxPoints = MPLFeaturesUtils.convert4326To3857(maxL.longitude, maxL.latitude)

        var minx = minPoints[0]
        var maxx = maxPoints[0]
        var miny = minPoints[1]
        var maxy = maxPoints[1]

        if (minx > maxx) {
            minx = maxPoints[0]
            maxx = minPoints[0]
        }
        if (miny > maxy) {
            miny = maxPoints[1]
            maxy = minPoints[1]
        }

        //val exactEnv: GeoEnvelope = GeoEnvelope(pointsMin[0],  pointsMax[0], pointsMin[1], pointsMax[1])
        val exactEnv = GeoEnvelope(minx, maxx, miny, maxy)
        return exactEnv
    }


    fun onSingleTapUpFromMaplibre(screenx: Float, screeny :Float) {
        if (mRulerOverlay!!.isMeasuring) return
        when (mode) {
            MODE_EDIT -> {
                val map = mMapRef.get()?.map ?: return
                if (map.addSketchPoint(screenx, screeny)) {
                    undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)
                    persistManualGeometryDraft("sketch-tap")
                }
            }
            MODE_AZIMUTH_CURRENT -> selectCurrentLocationAzimuthTarget(screenx, screeny)
            MODE_AZIMUTH_POINTS -> selectFreeAzimuthPoint(screenx, screeny)
            MODE_INFO -> {
                if (null != editLayerOverlay) {
                    val attributesFragment =
                        mActivity!!.supportFragmentManager.findFragmentByTag("ATTRIBUTES") as AttributesFragment?
                    if (attributesFragment != null) {
                        attributesFragment.setSelectedFeature(
                            mSelectedLayer,
                            editLayerOverlay!!.selectedFeatureId
                        )
                        mMapRef.get()!!.postInvalidate()
                    }
                }
            }

            else -> if (mode == MODE_NORMAL || mode == MODE_SELECT_FOR_VIEW || mode == MODE_SELECT_ACTION) {
                // check objects on map to select
                val dMinX = (screenx - mTolerancePX)
                val dMaxX = (screenx + mTolerancePX)
                val dMinY = (screeny - mTolerancePX)
                val dMaxY = (screeny + mTolerancePX)

                val screenPointMin = PointF(dMinX, dMinY)
                val screenPointMax = PointF(dMaxX, dMaxY)

                val minPoint = mMapRef.get()!!.map!!.maplibreMap.getProjection().fromScreenLocation(screenPointMin)
                val maxPoint = mMapRef.get()!!.map!!.maplibreMap.getProjection().fromScreenLocation(screenPointMax)

//                Log.e("CCCLLIICK", " click at: " + screenx + " - " + " screeny: " + screeny)
//                Log.e("CCCLLIICK", "points lnglong " + minPoint.longitude + " : " +  minPoint.latitude + " : "
//                        + maxPoint.longitude + " : " + maxPoint.latitude )

                val pointsMin = convert4326To3857(minPoint.longitude, minPoint.latitude)
                val pointsMax = convert4326To3857(maxPoint.longitude, maxPoint.latitude);

                var minx =   pointsMin[0];
                var maxx =   pointsMax[0];
                var miny =   pointsMin[1];
                var maxy =   pointsMax[1];

                if (minx > maxx){
                    minx =   pointsMax[0]
                    maxx =   pointsMin[0]
                }
                if (miny > maxy){
                    miny =   pointsMax[1]
                    maxy =   pointsMin[1]
                }
                //val exactEnv: GeoEnvelope = GeoEnvelope(minx,maxx , miny, maxy)
                val pointClick = PointF(screenx, screeny)
                val exactEnv: GeoEnvelope = getClickEnelope(pointClick, mMapRef.get()!!.map!!.maplibreMap)

                val point = GeoPoint(exactEnv.center.x, exactEnv.center.y)
                point.crs = GeoConstants.CRS_WEB_MERCATOR


                //show actions dialog
                var layers = mMapRef.get()!!.getVectorLayersByType(GeoConstants.GTAnyCheck)
                var items: List<Long>


                var vectorLayer: VectorLayer? = null
                var selectedSingleVectorLayer: VectorLayer? = null
                var selectedSingleFeatureId: Long = -1

                val mSelectedLayers = ArrayList<String>()
                var geometry: GeoGeometry? = null
                var featureId: Long = -1

                val selectedVectorLayer: MutableList<VectorLayer> = ArrayList()
                val selectedGeometry: MutableList<GeoGeometry?> = ArrayList()
                val selectedFeatures: MutableList<Feature> = ArrayList()


             if (mode == MODE_SELECT_ACTION && selectedLayer != null){
                 layers = mutableListOf<ILayer>()
                 layers.add(selectedLayer)
             }

                layersLoop@ for (layer in layers) {
                    //if (!layer.isValid) continue

                    vectorLayer = layer as VectorLayer
                    if (!LayerIdentifyPolicy.shouldInclude(vectorLayer)) continue
//                    Log.e("CCLICK", "on tapUp:")
//                    Log.e("CCLICK", exactEnv.toString())
                    items = vectorLayer.query(exactEnv)

                    var i = 0
                    while (i < items.size) {
                        // Refine RTree envelope candidates by actual geometry
                        featureId = items[i]
                        geometry = vectorLayer.getLargeGeometryForId(featureId)

//                        Log.e("CCLICK", "on Up check contains point:" + point.toString())
//                        Log.e("CCLICK", "on Up check contains poly:" + geometry.toString())

                        if (EditLayerOverlay.notContains(geometry, point, exactEnv)) {
                            i++
                            continue
                        }

                        val feature = vectorLayer.getFeature(featureId)
                        val valueForHint = getHintText(vectorLayer, feature)

                        if (feature != null){
                            if (valueForHint == null)
                                mSelectedLayers.add(layer.getName() + ": " + featureId)
                            else
                                mSelectedLayers.add(layer.getName() + ": " + valueForHint)

//                            selectedFeatures.add(feature)

                            selectedSingleVectorLayer = layer
                            selectedSingleFeatureId = featureId

                            selectedVectorLayer.add(vectorLayer)
                            selectedGeometry.add(geometry)
                            selectedFeatures.add(feature)
                        } else {
                            i++
                            continue
                        }
                        mSelectedLayer = layer
                        i++
                    }
                }

                if (mSelectedLayers.size == 0 && mode == MODE_SELECT_FOR_VIEW) {
                    // need select none
                    setNewMode(MODE_NORMAL)
                } else {
                    if (mSelectedLayers.size > 1 || selectedFeatures.size > 1)
                        showOverlayPointMultiChoise(
                            screenx.toDouble(),
                            screeny.toDouble(),
                            mSelectedLayers,
                        selectedVectorLayer,
                        selectedGeometry,
                        selectedFeatures,
                        false )
                    else if (mSelectedLayers.size == 1 || selectedFeatures.size == 1){

                        showViewModeForFeature(selectedVectorLayer.get(0),
                            selectedFeatures.get(0),
                            selectedSingleVectorLayer,
                            geometry,
                            selectedSingleFeatureId,
                            featureId ,
                            mode == MODE_SELECT_ACTION)


//                        if (mSelectedLayer != null)
//                            mSelectedLayer!!.isLocked = false
//
//                        mSelectedLayer = selectedSingleVectorLayer
//                        editLayerOverlay!!.setSelectedLayer(selectedSingleVectorLayer)
//
//                        if (geometry != null) editLayerOverlay!!.setSelectedFeature(selectedSingleFeatureId)
//
//                        if (mode != MODE_SELECT_ACTION)
//                            setMode(MODE_SELECT_FOR_VIEW)
//                        //showOverlayPoint(event);
//
//                        if (featureId != -1L && mSelectedLayer != null) {
//                            mMapRef.get()!!.map!!.startFeatureSelectionForView(
//                                selectedVectorLayer.get(0),
//                                selectedFeatures.get(0))
//                        }
                    } else {
                        if (mAddPointButton!!.visibility == View.VISIBLE) {
                            editLayerOverlay!!.hideOverlayPoint()
                            //mMapRef.get()!!.postInvalidate()

                            hideAddByTapButton()
                            showMainButton()

                            mMapRef.get()!!.map.clearPressedPoint()
                        }
                    }
                }
                //set select action mode
                //mMapRef.get()!!.postInvalidate()
            } else if (!mRulerOverlay!!.isMeasuring) hideOverlayPoint()
        }
    }

    fun showViewModeForFeature(layerd: ILayer,
                               originalSelectedFeature: Feature,
                               selectedSingleVectorLayer: VectorLayer?,
                               geometry: GeoGeometry?,
                               selectedSingleFeatureId: Long ,
                               featureId: Long,
                               editMode : Boolean){
        if (mSelectedLayer != null)
            mSelectedLayer!!.isLocked = false

        mSelectedLayer = selectedSingleVectorLayer
        editLayerOverlay!!.setSelectedLayer(selectedSingleVectorLayer)

        if (geometry != null)
            editLayerOverlay!!.setSelectedFeature(selectedSingleFeatureId)

        if (editMode) {
            if (mode != MODE_SELECT_ACTION)
                setNewMode(MODE_SELECT_ACTION)
        } else {
            if (mode != MODE_SELECT_ACTION)
                setNewMode(MODE_SELECT_FOR_VIEW)

        }
        //showOverlayPoint(event);
        /*
        * final ILayer  ilayerd, Integer layerGeoType,
                                             Feature originalSelectedFeature, boolean createNew,
                                             com.nextgis.maplib.display.Style ngstyle*/

        if (selectedSingleFeatureId != -1L && mSelectedLayer != null) {
            if (mode == MODE_SELECT_ACTION)
                editLayerOverlay!!.setSelectedFeature(selectedSingleFeatureId)

            mMapRef.get()!!.map!!.startFeatureSelectionForView(
                    layerd,
                    originalSelectedFeature)
            defineMenuItems()
        }
    }


    override fun panStart(e: MotionEvent) {
        if (editLayerOverlay!!.mode == EditLayerOverlay.MODE_CHANGE) mNeedSave = true
    }

    override fun panMoveTo(e: MotionEvent) {
    }

    override fun panStop() {
        if (mNeedSave) {
            mNeedSave = false
            undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)
            persistManualGeometryDraft("vertex-pan-stop")
        }
    }


    /**
     * Applies a GPS/network fix to [mCurrentCenter], MapLibre user-location source, track overlay,
     * and status panel. Recorded geometry is supplied exclusively by its foreground service.
     */
    private fun applyLocationFixToMap(location: Location) {
        val mapDrawable = mapDrawableOrNull ?: return

        if (mCurrentCenter == null) {
            mCurrentCenter = GeoPoint()
        }

        mCurrentCenter!!.setCoordinates(location.longitude, location.latitude)
        mCurrentCenter!!.crs = GeoConstants.CRS_WGS84

        if (!mCurrentCenter!!.project(GeoConstants.CRS_WEB_MERCATOR)) {
            mCurrentCenter = null
        }

        val isStanding =
            !location.hasBearing() || !location.hasSpeed() || location.speed == 0f

        mapDrawable.updateLocation(
            Point.fromLngLat(location.longitude, location.latitude),
            isStanding,
            if (location.hasBearing()) location.bearing else 0f,
            location.accuracy
        )
        if (mode == MODE_AZIMUTH_CURRENT && azimuthTargetPoint != null) {
            mapDrawable.showAzimuthMeasurement(
                Point.fromLngLat(location.longitude, location.latitude),
                azimuthTargetPoint!!.toMapLibrePoint(),
                false,
                true
            )
        }

        if (TrackerService.hasUnfinishedTracks(context)) {
            mapDrawable.reloadCurrentTrackToMap()
        }

        // Soft-interrupt: do not keep appending MapLibre-only points without the service.
        if (mode == MODE_EDIT_BY_WALK && !WalkEditService.isServiceRunning(context)) {
            checkWalkServiceWatchdog()
        }
    }

    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            onLocationUnavailable()
            return
        }
        applyLocationFixToMap(location)
        fillStatusPanel(location)
    }

    override fun onBestLocationChanged(location: Location) {
        applyLocationFixToMap(location)
        fillStatusPanel(location)
    }

    public fun reloadTracks(){

        if (mMapRef.get()!!.map!!.maplibreMap==null)
            return
        mMapRef.get()!!.map!!.reloadCurrentTrackToMap()
        mMapRef.get()!!.map!!.reloadTrackListToMap()


    }

    override fun onLocationUnavailable() {
        mCurrentCenter = null
        mapDrawableOrNull?.clearLocation()
        if (mode == MODE_AZIMUTH_CURRENT) {
            mapDrawableOrNull?.showAzimuthMeasurement(null, azimuthTargetPoint?.toMapLibrePoint(), false, true)
        }
        fillStatusPanel(null)
    }

    fun updateLastLocation() {
        val location = mGpsEventSource?.lastKnownLocation
        if (location == null) onLocationUnavailable() else onLocationChanged(location)
    }

    private fun fillStatusPanel(location: Location?) {
        if (mStatusPanelMode == 0 || mStatusPanel == null || mActivity == null) return

        var panel = mStatusPanel!!.getChildAt(0)
        if (panel == null) {
            panel = mActivity!!.layoutInflater.inflate(R.layout.status_panel, mStatusPanel, false)
            defineTextViews(panel)
            fillTextViews(location)
            mStatusPanel!!.removeAllViews()
            panel.background.alpha = 128
            mStatusPanel!!.addView(panel)
        } else
            fillTextViews(location)
    }

    private fun fillTextViews(location: Location?) {
        if (null == location) {
            setDefaultTextViews()
        } else {
            if (location.provider == LocationManager.GPS_PROVIDER) {
                var text = ""
                val satellites = if (location.extras != null) location.extras!!
                    .getInt("satellites") else 0
                if (satellites > 0) text += satellites

                mStatusSource!!.text = text
                mStatusSource!!.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        activity!!,
                        com.nextgis.maplibui.R.drawable.ic_location
                    ), null, null, null
                )
            } else {
                mStatusSource!!.text = ""
                mStatusSource!!.setCompoundDrawablesWithIntrinsicBounds(
                    ContextCompat.getDrawable(
                        activity!!,
                        com.nextgis.maplibui.R.drawable.ic_signal_wifi
                    ), null, null, null
                )
            }

            mStatusAccuracy!!.text = String.format(
                Locale.getDefault(),
                "%.1f %s", location.accuracy, getString(com.nextgis.maplib.R.string.unit_meter)
            )
            mStatusAltitude!!.text = String.format(
                Locale.getDefault(),
                "%.1f %s", location.altitude, getString(com.nextgis.maplib.R.string.unit_meter)
            )
            mStatusSpeed!!.text = String.format(
                Locale.getDefault(),
                "%.1f %s/%s",
                location.speed * 3600 / 1000,
                getString(com.nextgis.maplib.R.string.unit_kilometer),
                getString(com.nextgis.maplib.R.string.unit_hour)
            )
            mStatusLatitude!!.text =
                formatCoordinate(
                    location.latitude,
                    com.nextgis.maplibui.R.string.latitude_caption_short
                )
            mStatusLongitude!!.text =
                formatCoordinate(
                    location.longitude,
                    com.nextgis.maplibui.R.string.longitude_caption_short
                )
        }
    }


    private fun formatCoordinate(value: Double, appendix: Int): String {
        return LocationUtil.formatCoordinate(
            value,
            mCoordinatesFormat,
            mCoordinatesFraction
        ) + " " + getString(appendix)
    }


    private fun setDefaultTextViews() {
        mStatusSource!!.setCompoundDrawables(null, null, null, null)
        mStatusSource!!.text = ""
        mStatusAccuracy!!.text = getString(com.nextgis.maplibui.R.string.n_a)
        mStatusAltitude!!.text = getString(com.nextgis.maplibui.R.string.n_a)
        mStatusSpeed!!.text = getString(com.nextgis.maplibui.R.string.n_a)
        mStatusLatitude!!.text = getString(com.nextgis.maplibui.R.string.n_a)
        mStatusLongitude!!.text = getString(com.nextgis.maplibui.R.string.n_a)
    }


    private val isFitOneLine: Boolean
        get() {
            mStatusLongitude!!.measure(0, 0)
            mStatusLatitude!!.measure(0, 0)
            mStatusAltitude!!.measure(0, 0)
            mStatusSpeed!!.measure(0, 0)
            mStatusAccuracy!!.measure(0, 0)
            mStatusSource!!.measure(0, 0)

            val totalWidth =
                mStatusSource!!.measuredWidth + mStatusLongitude!!.measuredWidth +
                        mStatusLatitude!!.measuredWidth + mStatusAccuracy!!.measuredWidth +
                        mStatusSpeed!!.measuredWidth + mStatusAltitude!!.measuredWidth

            val metrics = DisplayMetrics()
            mActivity!!.windowManager.defaultDisplay.getMetrics(metrics)

            return totalWidth < metrics.widthPixels
            //        return totalWidth < mStatusPanel.getWidth();
        }


    private fun defineTextViews(panel: View) {
        mStatusSource = panel.findViewById(R.id.tv_source)
        mStatusAccuracy = panel.findViewById(R.id.tv_accuracy)
        mStatusSpeed = panel.findViewById(R.id.tv_speed)
        mStatusAltitude = panel.findViewById(R.id.tv_altitude)
        mStatusLatitude = panel.findViewById(R.id.tv_latitude)
        mStatusLongitude = panel.findViewById(R.id.tv_longitude)
        mZoom = panel.findViewById(R.id.tv_zoom)
        if (mZoom != null) mZoom!!.visibility = if (mPreferences!!.getBoolean(
                AppSettingsConstants.KEY_PREF_SHOW_ZOOM,
                true
            )
        ) View.VISIBLE else View.GONE
    }


    override fun onGpsStatusChanged(event: Int) {
    }


    override fun onStartEditSession() {
    }


    override fun onFinishEditSession() {
        setNewMode(MODE_NORMAL)
    }

    override fun onFinishEditByWalkSession() {
        saveEdits()
    }


    fun hideBottomBar() {
        mActivity!!.bottomToolbar.visibility = View.GONE
    }


    fun restoreBottomBar(mode: Int) {
        setNewMode(if (mode != -1) mode else this.mode)
    }


    fun addLocalTMSLayer(uri: Uri?) {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.addLocalTMSLayer(uri)
        }
    }


    fun addLocalVectorLayer(uri: Uri?) {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.addLocalVectorLayer(uri)
        }
    }


    fun addLocalVectorLayerWithForm(uri: Uri?) {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.addLocalVectorLayerWithForm(uri)
        }
    }

    fun locateCurrentPosition() {
        val mapDrawable = mapDrawableOrNull
        if (mCurrentCenter != null && mapDrawable?.maplibreMap != null) {
            val mapLibreMap = mapDrawable.maplibreMap
            val targetZoom = maxOf(mapLibreMap.cameraPosition.zoom, LOCATE_MIN_ZOOM)
                .coerceIn(mapLibreMap.minZoomLevel, mapLibreMap.maxZoomLevel)
            mapViewOrNull!!.panTo(mCurrentCenter)

            val lonLat = convert3857To4326(mCurrentCenter!!.x, mCurrentCenter!!.y)

            val targetPosition = CameraPosition.Builder()
                .target(LatLng(lonLat[1], lonLat[0]))
                .zoom(targetZoom)
                .bearing(0.0)
                .tilt(0.0)
                .build()

            persistMapBearing(0f)
            mapLibreMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(targetPosition),
                CAMERA_ANIMATION_MS)
            /* Menu «локация» only moves the camera; MapLibre puck is updated from GPS callbacks.
               Push the current fix onto user-location-source so the marker appears without resume. */
            updateLastLocation()
        } else if (!locateFirstLayerExtent()) {
            mapDrawable?.maplibreMap?.let { setMapBearing(it, 0.0, true) }
            Toast.makeText(
                mActivity,
                com.nextgis.maplibui.R.string.error_no_location,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val isMapRotationEnabled: Boolean
        get() = mPreferences?.getBoolean(
            AppSettingsConstants.KEY_PREF_MAP_ROTATION_ENABLED,
            false
        ) ?: false

    private fun configureMapRotationGestures(mapLibreMap: MapLibreMap, enabled: Boolean) {
        mapLibreMap.uiSettings.isRotateGesturesEnabled = enabled
        mapLibreMap.uiSettings.isTiltGesturesEnabled = false
        // MapLibre disables rotation when pinch wins the first motion event by default. Allow both
        // detectors to run together and lower only the rotation start angle for an immediate twist.
        mapLibreMap.uiSettings.isDisableRotateWhenScaling = false
        mapLibreMap.uiSettings.isIncreaseScaleThresholdWhenRotating = false
        mapLibreMap.gesturesManager.rotateGestureDetector.angleThreshold =
            ROTATION_ANGLE_THRESHOLD_DEGREES
    }

    fun toggleMapRotation(): Boolean {
        val enabled = !isMapRotationEnabled
        mPreferences?.edit()
            ?.putBoolean(AppSettingsConstants.KEY_PREF_MAP_ROTATION_ENABLED, enabled)
            ?.apply()

        mapDrawableOrNull?.maplibreMap?.let { mapLibreMap ->
            configureMapRotationGestures(mapLibreMap, enabled)
            if (!enabled) {
                persistMapBearing(0f)
                setMapBearing(mapLibreMap, 0.0, true)
            }
        }
        return enabled
    }

    private fun locateFirstLayerExtent(): Boolean {
        val mapView = mapViewOrNull ?: return false
        val mapDrawable = mapDrawableOrNull ?: return false
        val mapLibreMap = mapDrawable.maplibreMap ?: return false
        var fallbackLayer: ILayer? = null
        var fallbackExtent: GeoEnvelope? = null
        for (candidate in mapView.getAllLayers()) {
            try {
                val extent = candidate.extents
                if (isUsableExtent(extent)) {
                    fallbackLayer = candidate
                    fallbackExtent = extent
                    break
                }
            } catch (exception: RuntimeException) {
                HyperLog.w(
                    Constants.TAG,
                    "Locate fallback: cannot read extent for layer id=${candidate.id}",
                    exception
                )
            }
        }
        val layer = fallbackLayer ?: return false
        val extent = fallbackExtent ?: return false

        persistMapBearing(0f)
        setMapBearing(mapLibreMap, 0.0, false)
        val center = extent.center
        val lonLat = convert3857To4326(center.x, center.y)
        val fallbackZoom = LOCATE_MIN_ZOOM.coerceIn(
            mapLibreMap.minZoomLevel,
            mapLibreMap.maxZoomLevel
        )
        mapDrawable.setZoomAndCenter(
            fallbackZoom.toFloat(),
            center,
            true,
            CAMERA_ANIMATION_MS
        )
        val targetPosition = CameraPosition.Builder()
            .target(LatLng(lonLat[1], lonLat[0]))
            .zoom(fallbackZoom)
            .bearing(0.0)
            .tilt(0.0)
            .build()
        mapLibreMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(targetPosition),
            CAMERA_ANIMATION_MS
        )
        HyperLog.v(
            Constants.TAG,
            "Locate fallback: layer=\"${layer.name}\" id=${layer.id}"
        )
        return true
    }

    private fun isUsableExtent(extent: GeoEnvelope?): Boolean {
        if (extent == null || !extent.isInit()) return false
        val coordinates = doubleArrayOf(
            extent.minX,
            extent.minY,
            extent.maxX,
            extent.maxY
        )
        return coordinates.all { it.isFinite() } &&
            extent.minX <= extent.maxX && extent.minY <= extent.maxY
    }

    private fun persistMapBearing(bearing: Float) {
        mPreferences?.edit()
            ?.putFloat(AppSettingsConstants.KEY_PREF_MAP_BEARING, bearing)
            ?.apply()
    }

    private fun setMapBearing(mapLibreMap: MapLibreMap, bearing: Double, animate: Boolean) {
        val current = mapLibreMap.cameraPosition
        val safeBearing = if (bearing.isFinite()) normalizeBearing(bearing) else 0.0
        val targetPosition = CameraPosition.Builder()
            .target(current.target)
            .zoom(current.zoom)
            .bearing(safeBearing)
            .tilt(0.0)
            .build()
        val update = CameraUpdateFactory.newCameraPosition(targetPosition)
        if (animate) {
            mapLibreMap.animateCamera(update, NORTH_UP_ANIMATION_MS)
        } else {
            mapLibreMap.moveCamera(update)
        }
    }

    private fun normalizeBearing(bearing: Double): Double =
        ((bearing % 360.0) + 360.0) % 360.0


    fun addNGWLayer() {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.addNGWLayer()
        }
    }


    fun addRemoteLayer() {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.addRemoteLayer()
        }
    }


    fun refresh() {
        if (null != mMapRef.get()) {
            mMapRef.get()!!.drawMapDrawable()
        }
    }

    val isDialogShown: Boolean
        get() = mChooseLayerDialogRef.get() != null && mChooseLayerDialogRef.get()!!.isResumed

    protected fun showFullCompass() {
        val fragmentManager = mActivity!!.supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val compassFragment = FullCompassFragment()
        compassFragment.setClickable(true)

        val container = R.id.mainview
        fragmentTransaction.add(container, compassFragment, "COMPASS_FULL")
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .addToBackStack(null)
            .commit()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.fl_compass -> showFullCompass()
            R.id.add_current_location -> {
                if (v.isEnabled) addCurrentLocation(true)
                    mMainButton!!.collapse()
            }
            R.id.add_new_geometry -> {
                if (v.isEnabled) addNewGeometry()
                    mMainButton!!.collapse()
            }
            R.id.add_geometry_by_walk -> {
                if (v.isEnabled)
                    addGeometryByWalk()
                mMainButton!!.collapse()
            }

            R.id.action_zoom_in -> {
                //if (v.isEnabled) mMapRef.get()!!.zoomIn() // old
                // test
                //mMapRef.get()!!.map!!.changePointColor()

                val currentZoom =  mMapRef.get()!!.map.maplibreMap.cameraPosition.zoom
                var newZoom = currentZoom + 1.0
                if (newZoom > mMapRef.get()!!.map.maplibreMap.maxZoomLevel)
                    newZoom = mMapRef.get()!!.map.maplibreMap.maxZoomLevel
                val cameraUpdate = CameraUpdateFactory.zoomTo(newZoom)
                mMapRef.get()!!.map.maplibreMap.animateCamera(cameraUpdate)
            }
            R.id.action_zoom_out -> {
                //if (v.isEnabled) mMapRef.get()!!.zoomOut()
                val currentZoom = mMapRef.get()!!.map.maplibreMap.cameraPosition.zoom
                var newZoom = currentZoom - 1.0
                if (newZoom < mMapRef.get()!!.map.maplibreMap.minZoomLevel)
                    newZoom = mMapRef.get()!!.map.maplibreMap.minZoomLevel
                val cameraUpdate = CameraUpdateFactory.zoomTo(newZoom)
                mMapRef.get()!!.map.maplibreMap.animateCamera(cameraUpdate)
            }

            R.id.add_point_by_tap -> if (mRulerOverlay!!.isMeasuring) {
                mRulerOverlay!!.stopMeasuring()
                undoRedoOverlay!!.clearHistory()
                showMainButton()
                showRulerButton()
                showAzimuthButton()
                hideAddByTapButton()
                mAddPointButton!!.setIcon(com.nextgis.maplibui.R.drawable.ic_action_add_point)
                mActivity!!.title = mActivity!!.appName
                mActivity!!.setSubtitle(null)
                mActivity!!.showDefaultToolbar()
                mMapRef.get()!!.map.stoptMeasuring()

            } else addPointByTap()

            R.id.action_ruler -> {
                startMeasuring()
                Toast.makeText(context, R.string.tap_to_measure, Toast.LENGTH_SHORT).show()
            }
            R.id.action_azimuth -> showAzimuthModeDialog()
        }
    }

    protected fun startMeasuring(
        resetHistory: Boolean = true,
        restoredGeometry: GeoLineString? = null
    ) {
        mRulerOverlay!!.startMeasuring(this, mCurrentCenter)
        mMapRef.get()!!.map.startMeasuring()
        if (restoredGeometry != null)
            mMapRef.get()!!.map.restoreMeasurementGeometry(restoredGeometry)
        if (resetHistory || !undoRedoOverlay!!.hasHistory()) {
            undoRedoOverlay!!.clearHistory()
            saveRulerToHistory()
        }
        hideOverlayPoint()
        hideMainButton()
        hideRulerButton()
        hideAzimuthButton()
        showAddByTapButton()
        mAddPointButton!!.setIcon(com.nextgis.maplibui.R.drawable.ic_action_apply_dark)
        mActivity!!.showRulerToolbar()
    }

    private fun saveRulerToHistory() {
        val geometry = mMapRef.get()?.map?.measurementGeometry ?: return
        val feature = Feature()
        feature.geometry = geometry
        undoRedoOverlay!!.saveToHistory(feature)
    }

    override fun onLengthChanged(length: Double) {
        mActivity!!.title = LocationUtil.formatLength(context, length, 3)
        saveRulerToHistory()
    }

    override fun onAreaChanged(area: Double) {
        mActivity!!.setSubtitle(
            if (area > 0) LocationUtil.formatAreaHectares(context, area) else null
        )
    }

    public companion object {
        /**
         * Survives Activity recreation but not process death, unlike savedInstanceState.
         * It prevents a restored task from silently bypassing crash recovery.
         */
        @Volatile
        private var walkUiAttachedInProcess = false

        @Volatile
        private var stakeoutUiAttachedInProcess = false

        const val MODE_NORMAL: Int = 0
        const val MODE_SELECT_ACTION: Int = 1
        const val MODE_EDIT: Int = 2
        const val MODE_INFO: Int = 3
        const val MODE_EDIT_BY_WALK: Int = 4
        const val MODE_SELECT_FOR_VIEW: Int = 6
        const val MODE_STAKEOUT: Int = 7
        const val MODE_AZIMUTH_CURRENT: Int = 8
        const val MODE_AZIMUTH_POINTS: Int = 9


        protected const val KEY_MODE: String = "mode"
        protected const val BUNDLE_KEY_LAYER: String = "layer"
        protected const val BUNDLE_KEY_FEATURE_ID: String = "feature"
        protected const val BUNDLE_KEY_SAVED_FEATURE: String = "feature_blob"
        protected const val BUNDLE_KEY_IS_MEASURING: String = "is_measuring"
        protected const val BUNDLE_KEY_RULER_GEOMETRY: String = "ruler_geometry"
        const val EDIT_LAYER: Int = 2
        private const val LOCATE_MIN_ZOOM = 12.0
        private const val CAMERA_ANIMATION_MS = 800
        private const val MAPLIBRE_MIN_PRESENTATION_ATTEMPTS = 4
        private const val MAPLIBRE_FOREGROUND_FALLBACK_ATTEMPT = 8
        private const val MAPLIBRE_REPAINT_ATTEMPTS = 12
        private const val MAPLIBRE_REPAINT_DELAY_MS = 120L
        private const val NORTH_UP_ANIMATION_MS = 350
        private const val ROTATION_ANGLE_THRESHOLD_DEGREES = 0.5f
        private const val LEGACY_MODE_EDIT_BY_TOUCH = 5
    }

    private fun showAzimuthModeDialog() {
        val ctx = context ?: return
        val items = arrayOf(
            getString(R.string.azimuth_mode_current),
            getString(R.string.azimuth_mode_points)
        )
        AlertDialog.Builder(ctx)
            .setTitle(R.string.azimuth_mode_title)
            .setItems(items) { _, selected ->
                clearAzimuthMeasurement()
                setNewMode(
                    if (selected == 0) MODE_AZIMUTH_CURRENT else MODE_AZIMUTH_POINTS
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun mapPointFromScreen(screenX: Float, screenY: Float): GeoPoint? {
        val map = mapDrawableOrNull?.maplibreMap ?: return null
        val coordinate = map.projection.fromScreenLocation(PointF(screenX, screenY))
        return GeoPoint(coordinate.longitude, coordinate.latitude).apply {
            crs = GeoConstants.CRS_WGS84
        }
    }

    private fun selectCurrentLocationAzimuthTarget(screenX: Float, screenY: Float) {
        val target = mapPointFromScreen(screenX, screenY) ?: return
        azimuthStartPoint = null
        azimuthTargetPoint = target
        azimuthStaticTrueBearing = null
        mStakeoutDistance?.setText(R.string.stakeout_waiting_for_gps)
        mStakeoutAzimuth?.visibility = View.GONE
        mStakeoutDetails?.visibility = View.GONE
        mStakeoutDirection?.alpha = 0.35f
        mStakeoutSound?.visibility = View.VISIBLE
        updateAzimuthOverlayFromLatestLocation()
        try {
            mStakeoutController?.start(target)
        } catch (exception: RuntimeException) {
            HyperLog.w(Constants.TAG, "Azimuth target initialization failed", exception)
            Toast.makeText(context, R.string.stakeout_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun selectFreeAzimuthPoint(screenX: Float, screenY: Float) {
        val point = mapPointFromScreen(screenX, screenY) ?: return
        if (azimuthStartPoint == null || azimuthTargetPoint != null) {
            azimuthStartPoint = point
            azimuthTargetPoint = null
            azimuthStaticTrueBearing = null
            mapDrawableOrNull?.showAzimuthMeasurement(
                point.toMapLibrePoint(),
                null,
                true,
                false
            )
            mStakeoutDistance?.setText(R.string.azimuth_select_end)
            mStakeoutAzimuth?.visibility = View.GONE
            mStakeoutDetails?.visibility = View.GONE
            mStakeoutDirection?.rotation = 0f
            mStakeoutDirection?.alpha = 0.35f
            return
        }

        azimuthTargetPoint = point
        mapDrawableOrNull?.showAzimuthMeasurement(
            azimuthStartPoint!!.toMapLibrePoint(),
            point.toMapLibrePoint(),
            true,
            true
        )
        updateFreePointAzimuthResult()
    }

    private fun updateFreePointAzimuthResult() {
        val start = azimuthStartPoint ?: return
        val target = azimuthTargetPoint ?: return
        try {
            val result = StakeoutGeometryTarget(target).calculate(start.x, start.y)
            val declination = WorldMagneticModel2025(
                start.y.toFloat(),
                start.x.toFloat(),
                0f,
                System.currentTimeMillis()
            ).declination
            val magneticBearing = MagneticAzimuthCalculator.fromTrueBearing(
                result.bearingDegrees,
                declination,
                result.distanceMeters
            )
            azimuthStaticTrueBearing = magneticBearing?.let {
                normalizeBearing(result.bearingDegrees).toFloat()
            }
            renderAzimuthDistance(result.distanceMeters)
            renderMagneticAzimuth(magneticBearing)
            val declinationText = getString(
                R.string.azimuth_declination_format,
                formatAngle(declination.toDouble())
            )
            mStakeoutDetails?.text =
                "$declinationText\n${getString(R.string.azimuth_adjust_points)}"
            mStakeoutDetails?.visibility = View.VISIBLE
            mStakeoutSound?.visibility = View.GONE
            updateStaticAzimuthArrowForMapBearing()
        } catch (exception: RuntimeException) {
            HyperLog.w(Constants.TAG, "Free-point azimuth calculation failed", exception)
            Toast.makeText(context, R.string.stakeout_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateAzimuthOverlayFromLatestLocation() {
        when (mode) {
            MODE_AZIMUTH_CURRENT -> {
                val target = azimuthTargetPoint ?: return
                val location = mGpsEventSource?.lastKnownLocation
                mapDrawableOrNull?.showAzimuthMeasurement(
                    location?.let { Point.fromLngLat(it.longitude, it.latitude) },
                    target.toMapLibrePoint(),
                    false,
                    true
                )
            }
            MODE_AZIMUTH_POINTS -> mapDrawableOrNull?.showAzimuthMeasurement(
                azimuthStartPoint?.toMapLibrePoint(),
                azimuthTargetPoint?.toMapLibrePoint(),
                azimuthStartPoint != null,
                azimuthTargetPoint != null
            )
        }
    }

    private fun clearAzimuthMeasurement() {
        azimuthStartPoint = null
        azimuthTargetPoint = null
        azimuthStaticTrueBearing = null
        mapDrawableOrNull?.clearAzimuthMeasurement()
    }

    private fun GeoPoint.toMapLibrePoint(): Point = Point.fromLngLat(x, y)

    private fun renderAzimuthDistance(distanceMeters: Double) {
        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = if (distanceMeters < 10.0) 2 else 1
            minimumFractionDigits = if (distanceMeters < 10.0) 2 else 0
        }
        mStakeoutDistance?.text = getString(
            R.string.stakeout_distance_format,
            format.format(distanceMeters)
        )
    }

    private fun renderMagneticAzimuth(magneticBearing: Float?) {
        mStakeoutAzimuth?.text = if (magneticBearing == null) {
            getString(R.string.azimuth_undefined)
        } else {
            getString(
                R.string.azimuth_magnetic_format,
                formatAngle(magneticBearing.toDouble())
            )
        }
        mStakeoutAzimuth?.visibility = View.VISIBLE
        mStakeoutDirection?.alpha = if (magneticBearing == null) 0.35f else 1f
    }

    private fun formatAngle(value: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 1
        }.format(value)

    private fun updateStaticAzimuthArrowForMapBearing() {
        val trueBearing = azimuthStaticTrueBearing ?: run {
            mStakeoutDirection?.rotation = 0f
            return
        }
        val mapBearing = mapDrawableOrNull?.maplibreMap?.cameraPosition?.bearing ?: 0.0
        mStakeoutDirection?.rotation = normalizeBearing(trueBearing - mapBearing).toFloat()
    }

    private fun startStakeout() {
        val layer = mSelectedLayer ?: return
        val featureId = editLayerOverlay?.selectedFeatureId ?: Constants.NOT_FOUND.toLong()
        if (featureId == Constants.NOT_FOUND.toLong()) return
        val geometry = editLayerOverlay?.selectedFeatureGeometry
            ?: layer.getLargeGeometryForId(featureId)
            ?: return
        try {
            mStakeoutController?.start(geometry)
            setNewMode(MODE_STAKEOUT)
        } catch (exception: RuntimeException) {
            HyperLog.w(Constants.TAG, "Stakeout target initialization failed", exception)
            Toast.makeText(context, R.string.stakeout_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateStakeoutWidget(state: StakeoutController.UiState) {
        lastStakeoutUiState = state
        if (!isLiveStakeoutMode(mode)) return
        mStakeoutPanel?.visibility = View.VISIBLE
        mStakeoutSound?.visibility = View.VISIBLE
        mStakeoutSound?.setImageResource(
            if (state.muted) R.drawable.ic_stakeout_sound_off
            else R.drawable.ic_stakeout_sound_on
        )
        mStakeoutSound?.contentDescription = getString(
            if (state.muted) R.string.stakeout_unmute else R.string.stakeout_mute
        )

        if (state.waitingForFix || state.distanceMeters == null) {
            mStakeoutDistance?.setText(R.string.stakeout_waiting_for_gps)
            mStakeoutAzimuth?.visibility = View.GONE
            mStakeoutDetails?.visibility = View.GONE
            mStakeoutDirection?.alpha = 0.35f
            return
        }

        val format = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = if (state.distanceMeters < 10.0) 2 else 1
            minimumFractionDigits = if (state.distanceMeters < 10.0) 2 else 0
        }
        val distance = getString(
            R.string.stakeout_distance_format,
            format.format(state.distanceMeters)
        )
        mStakeoutDistance?.text = if (state.reached) {
            "$distance\n${getString(R.string.stakeout_reached)}"
        } else {
            distance
        }
        renderMagneticAzimuth(state.magneticBearingDegrees)
        val details = mutableListOf<String>()
        state.accuracyMeters?.let { accuracy ->
            val accuracyFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                maximumFractionDigits = 3
                minimumFractionDigits = 0
            }
            details += getString(
                R.string.stakeout_accuracy_format,
                accuracyFormat.format(accuracy)
            )
        }
        details += getString(
            R.string.azimuth_declination_format,
            formatAngle(state.declinationDegrees.toDouble())
        )
        if (mode == MODE_AZIMUTH_CURRENT) {
            details += getString(R.string.azimuth_adjust_target)
        }
        mStakeoutDetails?.text = details.joinToString(" · ")
        mStakeoutDetails?.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
        mStakeoutDirection?.rotation = if (state.magneticBearingDegrees == null) {
            0f
        } else if (state.usesDeviceCompass) {
            state.relativeBearingDegrees
        } else {
            val mapBearing = mapDrawableOrNull?.maplibreMap?.cameraPosition?.bearing ?: 0.0
            normalizeBearing(state.absoluteBearingDegrees - mapBearing).toFloat()
        }
    }

    private fun startLayerEditMode() {
        val layer = mSelectedLayer ?: return
        if (blockWalkLayerEdit(layer)) return
        if (!layer.isEditingAllowed) {
            showLayerNotEditableInCollectorToast()
            return
        }
        val featureId = editLayerOverlay!!.selectedFeatureId
        editLayerOverlay!!.setSelectedLayer(layer)
        if (featureId != Constants.NOT_FOUND.toLong()) {
            editLayerOverlay!!.setSelectedFeature(featureId)
        }
        setNewMode(MODE_SELECT_ACTION)
    }

    private fun startFeatureGeometryEdit() {
        val layer = mSelectedLayer ?: return
        if (blockWalkLayerEdit(layer)) return
        if (!layer.isEditingAllowed) {
            showLayerNotEditableInCollectorToast()
            return
        }
        if (mMapRef.get()?.map?.getLayerFeatures(layer) == null) {
            Toast.makeText(
                context,
                com.nextgis.maplibui.R.string.edit_invisible,
                Toast.LENGTH_LONG
            ).show()
            return
        }
        setNewMode(MODE_EDIT)
        HyperLog.v(
            Constants.TAG,
            "Geometry edit session started existing layer=${layer.id} " +
                "feature=${editLayerOverlay!!.selectedFeatureId}"
        )
        undoRedoOverlay!!.saveToHistory(editLayerOverlay!!.selectedFeature)
        editLayerOverlay!!.setHasEdits(false)
        mMapRef.get()?.map?.startFeatureSelectionForEdit(
            layer,
            layer.geometryType,
            editLayerOverlay!!.selectedFeature,
            false,
            layer.defaultStyleNoExcept,
            false
        )
    }

    fun deleteFeature() {
        val selectedFeatureId = editLayerOverlay!!.selectedFeatureId
        val layer = mSelectedLayer
        if (layer != null && blockWalkLayerEdit(layer)) return

        val builder = AlertDialog.Builder(activity!!)
            .setTitle(com.nextgis.maplibui.R.string.delete_confirm_feature)
            .setMessage(com.nextgis.maplibui.R.string.delete_feature)
            .setPositiveButton(com.nextgis.maplibui.R.string.menu_delete) {
                dialog: DialogInterface?, which: Int ->
                val snackbar = Snackbar.make(activity!!.findViewById<View>(R.id.mainview), activity!!.getString(com.nextgis.maplibui.R.string.delete_item_done), Snackbar.LENGTH_LONG)
                    .setAction(com.nextgis.maplibui.R.string.undo) { v: View? ->
                        layer!!.showFeature(selectedFeatureId)
                        editLayerOverlay!!.setSelectedFeature(selectedFeatureId)
                        defineMenuItems()
                        mMapRef.get()!!.map.showFeatureFromHide(selectedFeatureId, layer!!.id,
                            mMapRef.get()!!.map!!.hiddedFeature)
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(snackbar: Snackbar, event: Int) {
                            super.onDismissed(snackbar, event)
                            if (event == DISMISS_EVENT_MANUAL)
                                return
                            if (event != DISMISS_EVENT_ACTION) {
                                val deleted = layer!!.deleteAddChanges(selectedFeatureId)
                                if (deleted <= 0) {
                                    layer.showFeature(selectedFeatureId)
                                    editLayerOverlay!!.setSelectedFeature(selectedFeatureId)
                                    mMapRef.get()!!.map!!.showFeatureFromHide(
                                        selectedFeatureId, layer.id,
                                        mMapRef.get()!!.map!!.hiddedFeature)
                                    defineMenuItems()
                                    return
                                }
                                mMapRef.get()!!.map!!.deleteFeature(selectedFeatureId, layer.id)
                            }
                        }

                        override fun onShown(snackbar: Snackbar) {
                            super.onShown(snackbar)
                        }
                    })
                mSelectedLayer!!.hideFeature(selectedFeatureId)
                mMapRef.get()!!.map.hideFeature(selectedFeatureId, layer!!.id)
                editLayerOverlay?.setSelectedFeature(null)
                defineMenuItems()

                val view = snackbar.view
                val textView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                textView.setTextColor(ContextCompat.getColor(mActivity!!, com.nextgis.maplibui.R.color.color_white))
                snackbar.show()
            }
            .setNegativeButton(
                com.nextgis.maplibui.R.string.cancel
            ) { dialog: DialogInterface?, which: Int -> }.create()
        builder.show()
    }

    private fun blockWalkLayerEdit(layer: VectorLayer): Boolean {
        val session = WalkSessionStore.load(context) ?: return false
        if (session.layerId != layer.id || finishedWalkEditId == session.id) return false
        Toast.makeText(context, com.nextgis.maplibui.R.string.walk_layer_busy, Toast.LENGTH_LONG).show()
        return true
    }

    fun loadJsonFromAssets(context: Context, fileName: String): String? {
        return try {
            val inputStream = context.assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
    }

    override
    fun setHasEdit() {
        editLayerOverlay!!.setHasEdits(true)
    }

    override
    fun updateActions(editObject: MLGeometryEditClass?){
        editLayerOverlay!!.updateActions(editObject)
    }

    override fun getMode(): Int {
        return mode;
    }

    override fun onAzimuthMeasurementPointMoved(
        startPoint: Boolean,
        point: Point,
        finished: Boolean
    ) {
        val movedPoint = GeoPoint(point.longitude(), point.latitude()).apply {
            crs = GeoConstants.CRS_WGS84
        }
        when (mode) {
            MODE_AZIMUTH_CURRENT -> {
                if (startPoint) return
                azimuthTargetPoint = movedPoint
                if (finished) {
                    mStakeoutController?.updateTarget(movedPoint)
                }
            }
            MODE_AZIMUTH_POINTS -> {
                if (startPoint) {
                    azimuthStartPoint = movedPoint
                } else {
                    azimuthTargetPoint = movedPoint
                }
                if (finished && azimuthStartPoint != null && azimuthTargetPoint != null) {
                    updateFreePointAzimuthResult()
                }
            }
        }
    }

    override
    fun updateGeometryFromMaplibre(feature: org.maplibre.geojson.Feature?,
                                   originalSelectedFeature: Feature?,
                                   editObject: MLGeometryEditClass?) {
        if (feature == null || originalSelectedFeature == null)
            return
        originalSelectedFeature.geometry = getGeometryFromMaplibreGeometry(feature)
        editLayerOverlay!!.updateGeometryFromMaplibre(originalSelectedFeature.geometry)
        editLayerOverlay!!.fillDrawItems(originalSelectedFeature.geometry)
        /*
         val undoRedoFeature = undoRedoOverlay!!.feature
                    val feature = editLayerOverlay!!.selectedFeature
                    feature.geometry = undoRedoFeature.geometry
                    editLayerOverlay!!.fillDrawItems(undoRedoFeature.geometry)

                    val original = mSelectedLayer!!.getGeometryForId(feature.id)
                    val hasEdits = original != null && undoRedoFeature.geometry == original

        * */
        editLayerOverlay!!.updateActions(editObject)
        undoRedoOverlay!!.saveToHistory(originalSelectedFeature)
        persistManualGeometryDraft("maplibre-change")
        updateEditAttributesActionAvailability()
    }

    override fun getSelectedLayer(): VectorLayer? {
        return mSelectedLayer
    }

    override fun getGeometryFromMaplibreGeometry(feature: org.maplibre.geojson.Feature?) : GeoGeometry? {

        if (feature == null)
            return null;


        if (feature.geometry()!= null && feature.geometry() is MultiPolygon){
            val multipoly = feature.geometry() as MultiPolygon
            val geomultiPolygon = GeoMultiPolygon()
            geomultiPolygon.crs = GeoConstants.CRS_WEB_MERCATOR
            for (poly in multipoly.coordinates()){
                val geoPolygon = GeoPolygon()
                geoPolygon.crs = GeoConstants.CRS_WEB_MERCATOR
                geoPolygon.outerRing.crs = GeoConstants.CRS_WEB_MERCATOR

                var iter = 0
                for (outer in poly){

                    if (iter == 0){ // outer ring
                        for (outer2 in outer){
                            val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
                            val geopoint = GeoPoint(points[0], points[1])
                            geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                            geoPolygon.add(geopoint)
                        }
                    } else {
                        // inner
                        val ring = GeoLinearRing()
                        ring.crs = GeoConstants.CRS_WEB_MERCATOR

                        for (outer2 in outer){
                            val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
                            val geopoint = GeoPoint(points[0], points[1])
                            geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                            ring.add(geopoint)
                        }
                        geoPolygon.addInnerRing(ring)
                    }
                    iter++
                }

                geomultiPolygon.add(geoPolygon)
            }
            return geomultiPolygon
        }



        if (feature.geometry()!= null && feature.geometry() is Polygon){
            val poly = feature.geometry() as Polygon

            val geoPolygon = GeoPolygon()
            geoPolygon.crs = GeoConstants.CRS_WEB_MERCATOR

            var iter = 0
            for (outer in poly.coordinates()){

                if (iter == 0){ // outer ring
                    for (outer2 in outer){
                        val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
                        val geopoint = GeoPoint(points[0], points[1])
                        geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                        geoPolygon.add(geopoint)
                    }
                } else {
                    // inner
                    val ring = GeoLinearRing()

                    for (outer2 in outer){
                        val points: DoubleArray = convert4326To3857(outer2.longitude(), outer2.latitude())
                        val geopoint = GeoPoint(points[0], points[1])
                        geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                        ring.add(geopoint)
                    }
                        geoPolygon.addInnerRing(ring)
                }
                iter++
            }
            return geoPolygon
        }
        if (feature.geometry()!= null && feature.geometry() is Point){
            val point = feature.geometry() as Point

            val geoPoint = GeoPoint()
            geoPoint.crs = GeoConstants.CRS_WEB_MERCATOR
            val points: DoubleArray = convert4326To3857(point.longitude(), point.latitude())
            geoPoint.setCoordinates(points[0], points[1])
            return geoPoint
        }
        if (feature.geometry()!= null && feature.geometry() is MultiPoint){
            val geoPoint = GeoMultiPoint()

            val multiPoint = feature.geometry() as MultiPoint
            for ( point in multiPoint.coordinates()){
                val newPoint = GeoPoint()

                val points: DoubleArray = convert4326To3857(point.longitude(), point.latitude())
                newPoint.setCoordinates(points[0], points[1])
                newPoint.crs = GeoConstants.CRS_WEB_MERCATOR
                geoPoint.add(newPoint)
            }
            geoPoint.crs = GeoConstants.CRS_WEB_MERCATOR
            return geoPoint
        }


        if (feature.geometry()!= null && feature.geometry() is LineString){

            val geoLineObj = GeoLineString()
            geoLineObj.crs = GeoConstants.CRS_WEB_MERCATOR

            val poly = feature.geometry() as LineString

            val geoLine = GeoLineString()
            geoLine.crs = GeoConstants.CRS_WEB_MERCATOR

            for (outer in poly.coordinates()){
                val points: DoubleArray = convert4326To3857(outer.longitude(), outer.latitude())
                val geopoint = GeoPoint(points[0], points[1])
                geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                geoLineObj.add(geopoint)
            }
            return geoLineObj
        }

        if (feature.geometry()!= null && feature.geometry() is MultiLineString){
            val geoMultiLineObj = GeoMultiLineString()
            geoMultiLineObj.crs = GeoConstants.CRS_WEB_MERCATOR

            val geoMultiLine = feature.geometry() as MultiLineString
            for (line in geoMultiLine.lineStrings()){

                val geoLineObj = GeoLineString()
                geoLineObj.crs = GeoConstants.CRS_WEB_MERCATOR

                val poly = line as LineString

                val geoLine = GeoLineString()
                geoLine.crs = GeoConstants.CRS_WEB_MERCATOR

                for (outer in poly.coordinates()){
                    val points: DoubleArray = convert4326To3857(outer.longitude(), outer.latitude())
                    val geopoint = GeoPoint(points[0], points[1])
                    geopoint.crs = GeoConstants.CRS_WEB_MERCATOR
                    geoLineObj.add(geopoint)
                }
                geoMultiLineObj.add(geoLineObj)
            }
            return geoMultiLineObj
        }

        return null;
    }


    private fun convert4326To3857(lon: Double, lat: Double): DoubleArray {
        val x = lon * 20037508.34 / 180
        val y = ln(tan(Math.PI / 4 + Math.toRadians(lat) / 2)) * 20037508.34 / Math.PI
        return doubleArrayOf(x, y)
    }

    fun convert3857To4326(x: Double, y: Double): DoubleArray {
        val lon = x * 180 / 20037508.34
        val lat = Math.toDegrees(atan(sinh(y * Math.PI / 20037508.34)))
        return doubleArrayOf(lon, lat)
    }

    fun getHintText(vectorLayer: VectorLayer, feature: Feature?):String? {
        return vectorLayer.getFeatureLabel(feature)
    }

    override fun onCameraIdle() {

        val zoom = getCurrentZoom()
        setMapLibreZoomInEnabled()
        setMapLibreZoomOutEnabled()
        mScaleRulerText!!.text = rulerText
        if (mZoom != null)
            mZoom!!.text = zoomText
        if (isMapRotationEnabled) {
            mapDrawableOrNull?.maplibreMap?.cameraPosition?.bearing?.let {
                persistMapBearing(it.toFloat())
            }
        }
        lastStakeoutUiState?.let { updateStakeoutWidget(it) }
        if (mode == MODE_AZIMUTH_POINTS) updateStaticAzimuthArrowForMapBearing()
    }

    private inner class MessageStyling : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent ){
            if (intent.action == MESSAGE_INTENT_STYLING) {
                val textmessage = intent.getStringExtra(ConstantsUI.KEY_MESSAGE);
                textStylingProgrerss?.setText(textmessage)
            }
        }
        //            if (intent.action == MESSAGE_INTENT_STYLING_RASTER) {
//                // raster
//                val layerId: Int? = intent.getIntExtra(LAYER_ID_KEY, -1)
//                val alpha = intent.getIntExtra(FIELD_ALPHA, 0)
//                val contrast = intent.getFloatExtra(FIELD_CONTRAST, 0f)
//                val brightnessMin = intent.getFloatExtra(FIELD_BRIGHTNESS_MIN, 0f)
//                val brightnessMax = intent.getFloatExtra(FIELD_BRIGHTNESS_MAX, 1f)
//
//
//
//                mMapRef.get()!!.map!!.updateRasterLayerProperties(layerId as Int?, alpha, contrast, brightnessMin,
//                    brightnessMax)
//            }
    }


    private inner class MessageReloadLayer : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent ){
            if (intent.action == MESSAGE_INTENT_RELOAD) {
                val layerid = intent.getIntExtra(ConstantsUI.KEY_LAYER_ID, -1);
                (context.applicationContext  as IGISApplication).removeLayerToRefresh(layerid)
                if (mMapRef.get()!= null && mMapRef.get()!!.map!= null && layerid != -1){

                    if (mMapRef.get()!!.map!!.getLayerVisible(layerid) == true) {
                        Handler().postDelayed({
                            mMapRef.get()!!.map!!.refreshLayerVisibility(layerid, false)
                        }, 300)

                        Handler().postDelayed({
                            mMapRef.get()!!.map!!.refreshLayerVisibility(layerid, true)
                        }, 600)
                    }
                }
            }
        }
    }

    public fun getLayerFeaturesML(layer: VectorLayer): List<org.maplibre.geojson.Feature>? {
            return mMapRef.get()!!.map!!.getLayerFeatures(layer)
        }




}
