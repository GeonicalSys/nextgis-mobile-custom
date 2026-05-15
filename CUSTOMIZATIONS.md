# GeonicalSystem Fork — Customizations Catalog

This document describes all modifications made to the official NextGIS Mobile
application ([nextgis/nextgis_mobile_android](https://github.com/nextgis/nextgis_mobile_android); the GitHub project was formerly **`android_gisapp`**, and the fork’s original base used upstream branch **`maplibre`**) in the GeonicalSystem fork. It serves as a reference for reproducing changes on future upstream versions.

Base commit: `7dde21c` (at the time: upstream **`maplibre`**, “3.0.0 release”). The official repo now ships from **`master`** only (`maplibre` is not present on the current remote).

---

## Table of Contents

1. [Build System Upgrade](#1-build-system-upgrade)
2. [Walk-by-Geometry Feature Restoration](#2-walk-by-geometry-feature-restoration)
3. [MapLibre Rendering and Layer Loading](#3-maplibre-rendering-and-layer-loading)
4. [NGW Sync and Layer Fill](#4-ngw-sync-and-layer-fill)
5. [NGW Resource Selection UI](#5-ngw-resource-selection-ui)
6. [Miscellaneous App Fixes](#6-miscellaneous-app-fixes)
7. [Localization](#7-localization)
8. [SQLite Schema Validation and Auto-Rebuild](#8-sqlite-schema-validation-and-auto-rebuild)
9. [Config Sync from NGW Description](#9-config-sync-from-ngw-description)
10. [Stability Hardening](#10-stability-hardening)
11. [Sync UI Fixes](#11-sync-ui-fixes)
12. [Upstream Merge History](#12-upstream-merge-history)
13. [Collector Import Verification, Layer Ordering, and Sync Timestamp](#13-collector-import-verification-layer-ordering-and-sync-timestamp)
14. [Default Preferences, Base Layers, Tracks Display, and NGRc Zoom](#14-default-preferences-base-layers-tracks-display-and-ngrc-zoom)
15. [Git Workflow Reference](#15-git-workflow-reference)
16. [Fork patch releases (GeonicalSystem)](#16-fork-patch-releases-geonicalsystem)

---

## 1. Build System Upgrade

**Purpose:** upgrade to AGP 9.1.0 / Gradle 9.3.1 / Kotlin 2.2.10 to support
the latest Android tooling and compileSdk 36.

### Files changed

| File | Changes |
|------|---------|
| `build.gradle` | AGP 8.13.2 -> 9.1.0, Kotlin 2.2.0 -> 2.2.10 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13 -> 9.3.1 |
| `gradle.properties` | 10 new AGP 9.x compatibility flags (`android.defaults.buildfeatures.resvalues`, `android.enableAppCompileTimeRClass`, `android.newDsl`, etc.) |
| `app/build.gradle` | `archivesBaseName` -> `base.archivesName`; signing config made conditional (builds without `keystore_google`); `proguard-android.txt` -> `proguard-android-optimize.txt` |
| `wizardpager/build.gradle` | Removed deprecated `targetSdkVersion` from `defaultConfig`; added `lint { targetSdk }` and `testOptions { targetSdk }` |
| `maplib/build.gradle` | AGP 9.x build compatibility fixes |
| `maplibui/build.gradle` | AGP 9.x build compatibility fixes |
| `easypicker/easypicker/build.gradle` | Same proguard/targetSdk fixes |
| `easypicker/app/build.gradle` | proguard file rename |

### How to reproduce on a new upstream version

1. Update `build.gradle` versions of AGP and Kotlin.
2. Update `gradle-wrapper.properties` to matching Gradle version.
3. Add the AGP compatibility flags to `gradle.properties` (check AGP release
   notes for which flags are needed).
4. In each module's `build.gradle`, replace deprecated `targetSdkVersion` with
   `lint { targetSdk }` + `testOptions { targetSdk }`.
5. Replace `proguard-android.txt` with `proguard-android-optimize.txt`.
6. Make signing config conditional so the project builds without a keystore.

---

## 2. Walk-by-Geometry Feature Restoration

**Purpose:** the official `maplibre` branch had the "add geometry by walk"
feature commented out. This modification restores it and integrates it with the
MapLibre map engine.

### What it does

- Allows the user to create line/polygon geometries by walking with GPS.
- Pressing the walk FAB button starts recording GPS positions as vertices.
- The geometry is rendered live on the MapLibre map during walk.
- Works for LineString, MultiLineString, Polygon, MultiPolygon layer types.

### Files changed — `app/`

| File | Changes |
|------|---------|
| `MapFragment.kt` | ~300 lines. Uncommented `addGeometryByWalk()` and walk menu handler. Added: `prepareMaplibreSessionForNewWalkGeometry()` — starts MapLibre edit session before walk recording; `walkStartAnchorWebMercator()` — gets GPS/camera anchor in Web Mercator; `buildInitialWalkGeometry()` — creates initial degenerate geometry at anchor; `applyInitialWalkGeometryAtStartLocation()` — applies initial geometry to overlay; `attachMaplibreToCurrentWalkOverlayGeometry()` — re-attaches MapLibre to overlay geometry on process restore; `reloadMapStyleAndLayersAfterLayerFillBatch()` — reloads map style after batch layer fill. Fixed null safety in `loadLayersLite()`. Fixed walk end with null check for `editingObject`. Fixed walk point check for `WalkEditService.isServiceRunning`. |
| `fragment_map.xml` | Uncommented `add_geometry_by_walk` FAB button |
| `fragment_map_tab.xml` | Same |
| `fragment_map.xml` (landscape) | Same |
| `AndroidManifest.xml` | Added `FOREGROUND_SERVICE_LOCATION` permission |

### Files changed — `maplibui/`

| File | Changes |
|------|---------|
| `EditLayerOverlay.java` | ~96 lines: walk editing overlay logic, `setGeometryFromWalkEdit()`, `newGeometryByWalk()` improvements |
| `WalkEditService.java` | ~70 lines: improved service lifecycle, added `isServiceRunning()` static check |
| `edit_line.xml` | Re-enabled `menu_edit_by_walk` item |
| `edit_polygon.xml` | Same |
| `edit_multiline.xml` | Same |
| `edit_multipolygon.xml` | Same |
| `MapViewOverlays.java` | Added `reloadMapStyleAndLayersAfterLayerFillBatch()` callback interface |

### How to reproduce on a new upstream version

1. Check if upstream has re-enabled walk-by-geometry. If yes, merge and compare.
2. If still commented out: uncomment the FAB in all 3 layout XMLs, uncomment
   `addGeometryByWalk()` in `MapFragment`, uncomment menu handlers.
3. Add the MapLibre integration methods (prepare session, build initial geometry,
   anchor, attach).
4. Add `FOREGROUND_SERVICE_LOCATION` permission to manifest.
5. Apply `EditLayerOverlay` and `WalkEditService` changes in maplibui.
6. Re-enable walk menu items in edit menus.

---

## 3. MapLibre Rendering and Layer Loading

**Purpose:** fix and extend MapLibre map rendering, GeoJSON conversion, and
editing session management.

### Files changed — `maplib/`

| File | Changes |
|------|---------|
| `MapDrawable.java` | ~356 lines: extended MapLibre map interaction — `startFeatureSelectionForEdit()`, `replaceGeometryFromHistoryChanges()`, `updateHistoryByWalkEnd()`, `addPointByWalk()`, `deleteCurrentPoint()`, style loading with `loadLayersToMaplibreMap()` / `loadLayersToMaplibreMapLite()`, editing object management |
| `MPLFeaturesUtils.java` | ~231 lines: reworked GeoJSON feature conversion for MapLibre rendering — batch feature processing, memory-efficient large dataset handling |
| `MaplibreMapInteraction.java` | New interface methods: `reloadMapStyleAndLayersAfterLayerFillBatch()`, `loadLayersLite()` |
| `GeoJSONUtil.java` | ~172 lines: reworked GeoJSON serialization — streaming for large datasets, coordinate precision control |
| `VectorLayer.java` | Added `queryAllFeatureIdsFromDb()` for direct SQLite ID retrieval; `defaultStyleNoExcept` getter |
| `VectorLayerRenderCache.java` | New file: render cache for vector layer styles |
| `NGWLayerSchemaCompat.java` | New file: NGW layer schema compatibility utilities |

### How to reproduce

These changes are deeply integrated with MapDrawable internals. On upstream
update, carefully merge `MapDrawable.java` and `MPLFeaturesUtils.java` — these
are the most likely conflict points. The interface changes in
`MaplibreMapInteraction.java` must be kept in sync.

---

## 4. NGW Sync and Layer Fill

**Purpose:** improve NGW synchronization reliability, rework layer filling
service for large datasets, and prevent sync during active layer fill.

### Files changed — `maplib/`

| File | Changes |
|------|---------|
| `CollectorResource.java` | ~330 lines: major expansion for collector resource handling — batch download, progress callbacks, error recovery |
| `Connection.java` | NGW connection improvements |
| `ResourceGroup.java` | Resource group handling improvements |
| `Resource.java` | New methods for resource metadata |
| `NGWVectorLayer.java` | ~256 lines: sync and data handling improvements — batch operations, conflict resolution, improved error handling |
| `SyncAdapter.java` (maplib) | Sync guard improvements |
| `IGISApplication.java` | New interface method `isLayerFillServiceBusy()` |
| `NetworkUtil.java` | Network utility additions for large data transfers |
| `SettingsConstants.java` | New settings keys for sync/fill configuration |

### Files changed — `maplibui/`

| File | Changes |
|------|---------|
| `LayerFillService.java` | ~613 lines: major rework — batch layer filling, progress tracking, memory management for large layers, sync guard integration |
| `LayerFillProgressDialogFragment.java` | ~175 lines: progress UI rework with detailed status display |
| `GISApplication.java` | ~121 lines: application-level additions — `isLayerFillServiceBusy()` implementation, service state management |

### Files changed — `app/`

| File | Changes |
|------|---------|
| `SyncAdapter.java` | Skip `onPerformSync` when `isLayerFillServiceBusy()` returns true |

### How to reproduce

1. Add `isLayerFillServiceBusy()` to `IGISApplication` interface.
2. Implement it in `GISApplication`.
3. Add the sync guard check in `SyncAdapter.onPerformSync()`.
4. Apply the `LayerFillService` rework (the largest single change).
5. Update `LayerFillProgressDialogFragment` for new progress tracking.
6. Apply `CollectorResource` and `NGWVectorLayer` improvements.

### Layer fill: large vector datasets (SQLite, ANR, screen off)

**Purpose:** keep bulk NGW / GeoJSON import usable on tens of thousands of features
without “Application not responding”, silent stop after screen-off, or losing the
progress UI when returning to the map.

**maplib — SQLite write transactions**

- `NGWVectorLayer.createFromNGW()`: `beginBulkImport()` + explicit
  `SQLiteDatabase` transaction; **commit/restart every N features**
  (`NGW_FILL_SQL_TX_BATCH`, currently **250**) so the DB write lock does not block
  the UI thread for minutes (aligns with upstream, which did not hold one giant
  transaction over the whole import).
- Throttled `IProgressor` updates: every `NGW_FILL_PROGRESS_FEATURE_STEP` features
  or after `NGW_FILL_PROGRESS_MIN_INTERVAL_MS`.
- `GeoJSONUtil.fillLayerFromGeoJSONStream()` / `createLayerFromGeoJSONStream()`:
  same pattern with `GEOJSON_FILL_SQL_TX_BATCH` and matching progress throttling.

**maplibui — service and dialog**

- `LayerFillService`: `PARTIAL_WAKE_LOCK` while the worker drains the queue;
  throttled `sendBroadcast(ACTION_UPDATE)`; minimal foreground service notification
  where required; `HyperLog` on unexpected task failure.
- `AndroidManifest.xml`: `WAKE_LOCK` permission for the above.
- `LayerFillProgressDialogFragment`: broadcast receiver registered on **application**
  context; `onMainMapActivityResume()` re-attaches progress after activity recreate;
  `STATUS_STOP` handling fixed so `KEY_TOTAL == 0` does not close the dialog before
  toast / follow-up sync; dialog not cancelable by back as during critical fill.
- `GISApplication`: `requestMapReloadAfterLayerFillBatch()` posts work to the main
  thread; `flushPendingMapReloadAfterLayerFillIfNeeded()` when the map fragment is
  ready; batch defer flags for heavy map reload until the fill queue is empty.

**maplib — `IGISApplication`**

- Extended with batch defer + pending map reload hooks used by `LayerFillService`
  / `GISApplication` (`isLayerFillBatchDeferringHeavyMapReload`,
  `setLayerFillBatchDeferringHeavyMapReload`, `requestMapReloadAfterLayerFillBatch`,
  `flushPendingMapReloadAfterLayerFillIfNeeded`).

**app**

- `MainActivity.kt`: on resume, if `isLayerFillServiceBusy`, posts
  `LayerFillProgressDialogFragment.onMainMapActivityResume()` so the progress UI
  restores reliably.

**Other**

- `LayerGeneralSettingsFragment.java`: null-safe `onDestroyView()` when
  `onCreateView` returned early (`mLayer == null`) — avoids NPE on
  `mEditText` / `mRangeBar`.

**Upstream comparison artifacts (repo root)**

| File | Purpose |
|------|---------|
| `UPSTREAM_LAYER_FILL_DIFF.txt` | Short summary + `git` commands to reproduce diffs |
| `UPSTREAM_DIFF_maplib_NGW_GeoJSON.patch.txt` | Full diff vs nextgis `android_maplib` @ `b8e4997` for `NGWVectorLayer` + `GeoJSONUtil` |
| `UPSTREAM_DIFF_maplibui_LayerFill.patch.txt` | Full diff vs nextgis `android_maplibui` `master` for `LayerFill*.java` |

**Why NGRc / local TMS unpack feels fine:** those paths mostly stream files or do
short disk work and do not hold a single SQLite write transaction over tens of
thousands of inserts while the map keeps querying the same DB.

---

## 5. NGW Resource Selection UI

**Purpose:** improve the NGW resource selection experience.

### Files changed — `maplibui/`

| File | Changes |
|------|---------|
| `SelectNGWResourceActivity.java` | ~70 lines: improved resource selection flow |
| `SelectNGWResourceDialog.java` | ~75 lines: enhanced dialog with better resource handling |
| `NGWResourcesListAdapter.java` | ~42 lines: improved list adapter |
| `LayerFactoryUI.java` | ~10 lines: layer creation changes |
| `StyleFragment.java` | ~23 lines: styling UI changes |
| `RuleFeatureRendererUI.java` | Minor fix |
| `SimpleFeatureRendererUI.java` | Minor fix |
| `AttributesActivity.java` | Minor fix |

---

## 6. Miscellaneous App Fixes

| File | Changes |
|------|---------|
| `AttributesFragment.java` | Changed `mLayer.query(null)` to `mLayer.queryAllFeatureIdsFromDb()` for reliable feature ID retrieval from SQLite |
| `Layer.java` (maplib) | Minor fix |
| `LayerGroup.java` (maplib) | ~28 lines: additions |
| `LocalTMSLayer.java` (maplib) | ~14 lines removed |

---

## 7. Localization

New Russian and English strings added:

| File | New strings |
|------|-------------|
| `maplib/src/main/res/values-ru/strings.xml` | Sync and layer operation messages |
| `maplib/src/main/res/values/strings.xml` | Same (English) |
| `maplibui/src/main/res/values-ru/strings.xml` | UI messages for fill service, walk, resource selection |
| `maplibui/src/main/res/values/strings.xml` | Same (English) |

---

## 8. SQLite Schema Validation and Auto-Rebuild

**Purpose:** prevent silent sync failures when the local SQLite table schema
doesn't match the server (e.g. interrupted initial download, field added on
server after fill).

### What it does

- After `createFromNGW()` completes, validates that every field in `mFields`
  has a corresponding column in the SQLite table via `PRAGMA table_info`.
  Throws `NGException` if columns are missing, so `LayerFillService` marks the
  task as failed immediately instead of leaving a broken layer.
- During sync in `getChangesFromServer()`, when the per-feature INSERT fails
  with `SQLiteException` containing "has no column", stops the loop immediately
  (instead of repeating the error for every feature) and calls
  `scheduleNgwLayerRebuildAfterSchemaMismatch()` to delete and re-download
  the layer automatically.

### Files changed

| File | Changes |
|------|---------|
| `VectorLayer.java` | New method `validateSqliteSchemaAgainstFields()` — compares `mFields` against actual SQLite columns |
| `NGWVectorLayer.java` | Post-fill validation in `createFromNGW()`; schema-mismatch detection in inner per-feature catch and outer `SQLiteException` catch of `getChangesFromServer()` |

---

## 9. Config Sync from NGW Description

**Purpose:** during sync, compare the layer config JSON from the NGW resource
`description` field with the local layer state. Apply changes (style, visibility,
zoom, sync settings, new fields) without re-downloading data.

### Architecture

- Config is stored as JSON text in the `description` field of the NGW resource.
- On initial layer fill, `LayerFillService` applies it via `fromJSON()`.
- On each sync, `getChangesFromServer()` fetches the same resource meta (already
  needed for schema check), extracts description, compares MD5 hash with the
  last applied hash to avoid false positives.
- If hash differs, parses the config and classifies changes as SOFT (can update
  in-place) or HARD (requires full rebuild).

### Soft vs Hard changes

| Change | Type | Action |
|--------|------|--------|
| Renderer/style | Soft | Apply new renderer |
| Visibility, zoom, name | Soft | Update and save |
| sync_type, sync_direction, tracked | Soft | Update and save |
| New field | Soft | `ALTER TABLE ADD COLUMN` + update `mFields` |
| Field alias changed | Soft | Update `Field.alias` |
| Field type changed | Hard | `scheduleNgwLayerRebuildAfterSchemaMismatch()` |
| Geometry type changed | Hard | Same |

### New files

| File | Purpose |
|------|---------|
| `maplib/.../util/LayerConfigUtil.java` | Config parsing extracted from `LayerFillService`: `extractNgwResourceDescriptionJson()`, `parseLayerConfigObject()`, `unwrapLayerConfigJsonText()`, HTML stripping, balanced JSON extraction, `md5()` |
| `maplib/.../util/LayerConfigDiff.java` | Compares server config vs local layer, classifies changes as `MATCH` / `SOFT` / `HARD`, tracks added fields, alias changes, renderer/visibility/zoom/name/sync changes |

### Modified files

| File | Changes |
|------|---------|
| `VectorLayer.java` | New method `applySoftConfigUpdate(LayerConfigDiff)` — applies soft changes: `ALTER TABLE ADD COLUMN`, alias updates, renderer, visibility, zoom, name |
| `NGWVectorLayer.java` | Override `applySoftConfigUpdate()` for sync_type/direction/tracked/serverWhere; config check block in `getChangesFromServer()` with MD5 hash comparison |
| `SettingsConstants.java` | New key `KEY_PREF_LAST_CONFIG_HASH` |
| `LayerFillService.java` | Delegates config parsing to `LayerConfigUtil`; stores config hash after initial fill |

### How to reproduce

1. Create `LayerConfigUtil` and `LayerConfigDiff` in maplib.
2. Add `applySoftConfigUpdate()` to `VectorLayer` and override in `NGWVectorLayer`.
3. In `getChangesFromServer()`, after schema check, add config check block.
4. In `LayerFillService`, save MD5 hash after applying config on initial fill.
5. Replace private parsing methods in `LayerFillService` with delegation to `LayerConfigUtil`.

### NGW config when data sync is off (`SYNC_NONE`) — 3.0.2.3

**Problem:** server-side changes to the NGW resource `description` (style/config) were only
reconciled for layers that took part in **data** sync. Layers with `getSyncType() == SYNC_NONE`
were excluded from `SyncAdapter`’s layer list, and `NGWVectorLayer.sync()` returned before
`getChangesFromServer()`, so the config block never ran.

**Changes (fork, GeonicalSystem):**

- Extract `tryRefreshServerResourceMetaAndConfig()` + `ConfigRefreshOutcome`; the start of
  `getChangesFromServer()` delegates to it (same behavior for network, missing table, schema
  mismatch, hash, soft/hard config as before).
- `NGWVectorLayer.sync()`: if `SYNC_NONE` and the layer is inited (`mFields != null`), run the
  same config refresh, then return (no feature download). Covers **single-layer** sync
  (`ACTION_LPATH` bundle).
- `SyncAdapter.onPerformSync`: after the main `sync()` pass, if **not** `ACTION_LPATH` and
  not canceled, recursively walk the map `LayerGroup` and call
  `NGWVectorLayer.syncNgwResourceConfigOnly()` for each account’s `SYNC_NONE` vector layer.
- `isSomeToSync()`: new `hasNgwVectorLayerForAccount(LayerGroup, …)` — if **any** `NGWVectorLayer`
  exists for the account, return true so `onPerformSync` is not skipped when *only* data sync
  is off (app layer previously bailed on `!super.isSomeToSync`).

| File | Notes |
|------|--------|
| `maplib/.../NGWVectorLayer.java` | Config extraction; public `syncNgwResourceConfigOnly(authority, syncResult)` |
| `maplib/.../SyncAdapter.java` | Second pass, `isSomeToSync` helper, `NGWVectorLayer` import |

---

## 10. Stability Hardening

**Purpose:** fix 80+ potential crash points identified by code audit across
all three modules.

### 10.1 Global crash handling

| File | Changes |
|------|---------|
| `MainApplication.java` | `HyperLogCrashHandler` installed as last UncaughtExceptionHandler — every crash written to HyperLog file before process death |
| `OfflineSyncIntentService.java` | Top-level `try-catch` with HyperLog around `handleActionFoo()` |
| `WalkEditService.java` | `UnsupportedOperationException` for unknown geometry type replaced with log + return |

### 10.2 Cursor leak fixes (6 fixes in maplib)

| File | Method | Fix |
|------|--------|-----|
| `DatabaseContext.java` | `getDbForLayer` | Close 5 PRAGMA query cursors |
| `TrackLayer.java` | `loadTrack` | `try/finally` with cursor.close() |
| `TrackLayer.java` | `getColor` | Close cursor when moveToFirst fails |
| `MapDrawable.java` | `createFeatureListFromCurrentTrack` | Close mCursor on all paths |
| `VectorLayer.java` | `getLagreGeometryFromQuery` | Close cursor before return null |
| `VectorLayer.java` | `rebuildCache` | Close cursor in finally block |

### 10.3 ConcurrentModificationException fixes (3 fixes)

| File | Fix |
|------|-----|
| `LayerGroup.java` `removeLayer` | `Iterator.remove()` instead of `map.remove()` in loop |
| `MapEventSource.java` `mListeners` | Changed to `CopyOnWriteArrayList` |
| `LayerGroup.java` `runDraw` | Snapshot `mLayers.values()` under synchronized block |

### 10.4 LayerFillProgressDialogFragment (4 fixes)

- Null check for `mLayerFillReceiver` in `onAttach`
- `isFinishing()` guard before dialog/toast operations
- Added `break` in `STATUS_STOP` to prevent fall-through
- Null check for `mProgressDialog`

### 10.5 MapFragment.kt null safety

- Added helper properties `mapViewOrNull`, `mapDrawableOrNull`
- Replaced `getContext()!!`, `context!!`, `activity!!` with safe-call patterns
  in lifecycle-dependent methods (drawScaleRuler, onDestroyView, onResume, etc.)

### 10.6 Overlay null safety

| File | Fix |
|------|-----|
| `EditLayerOverlay.java` | Bounds check for `mDrawItems.get(0)`, null guards for `mSelectedItem`, `mContext`, `mBottomToolbar` |
| `UndoRedoOverlay.java` | Null guard for `mTopToolbar` |

### 10.7 Fragment lifecycle guards

| File | Fix |
|------|-----|
| `AttributesFragment.java` | 6 null guards for `getActivity()`/`getContext()` |
| `LayersFragment.java` | Null-safe chain for `mapFragmentRef` access |
| `FullCompassFragment.java` | Null check before `getActivity()` cast |
| `AboutActivity.java` | Null checks in inner fragment classes |

### 10.8 MapDrawable + WalkEditService

| File | Fix |
|------|-----|
| `MapDrawable.java` | Null guards for weak refs in `clearMapLibreMap`, `changeFeatureId`, `zoomToLatLng` |
| `WalkEditService.java` | Removed redundant `stopSelf()` from `onDestroy()`, removed duplicate `removeNotification()` |

### 10.9 SettingsFragment ArrayIndexOutOfBounds

7 fixes for `findIndexOfValue` / `parseInt` results used as array index without
bounds checking.

### 10.10 Logging

- 10+ empty catch blocks replaced with `HyperLog.w` logging
- Lifecycle breadcrumbs added to MapFragment, MainActivity, LayerFillService, WalkEditService

---

## 11. Sync UI Fixes

**Purpose:** fix two UI bugs in synchronization status display.

### Problem 1: Sync spinner never stops

`SyncAdapter.onPerformSync()` had 3 early `return` paths (no user, nothing to
sync, LayerFillService busy) that did not send `SYNC_FINISH` broadcast. If
`LayersFragment` started the animation via `isSyncStarted()`, it never received
the stop signal.

**Fix:** added `sendSyncFinishBroadcast()` call in all early return paths.

### Problem 2: Last sync time not updating

`LayersFragment.onResume()` registered the `SyncReceiver` and checked animation
state, but did not call `updateInfo()`. If sync finished while the fragment was
paused, the timestamp was written to SharedPreferences but the UI never read it.

**Fix:** added `updateInfo()` call in `onResume()`.

### Files changed

| File | Changes |
|------|---------|
| `SyncAdapter.java` (app) | New `sendSyncFinishBroadcast()` method; called in all early return paths |
| `LayersFragment.java` | Added `updateInfo()` in `onResume()` |

### Problem 3: Last sync time updates after a failed sync

`SyncAdapter` (maplib) wrote `KEY_PREF_LAST_SYNC_TIMESTAMP` immediately after
`sync()` returned, before inspecting `syncResult.stats` / `mError`, so a failed
sync still refreshed the “last synced” label.

**Fix:** persist the timestamp only when `mapContentProviderHelper != null`,
`mError` is empty, and `!syncResult.hasError()` (same notion as the app
`SyncAdapter` notification path).

| File | Changes |
|------|---------|
| `SyncAdapter.java` (maplib) | Moved timestamp `putLong` to after error aggregation; gated on success |

---

## 12. Upstream Merge History

### Merge 1: upstream/master commit `9158b52` (2026-03-27)

Merged upstream changes:
- NGW auth changed to JSON format
- Demo NGW project support (`demo_project` resource type)
- Feature value updates in `AttributesActivity`
- `ChooseLayerDialog`: added `useCreatePointFromOverlay` parameter
- `addCurrentLocation`: improved point creation from overlay
- Cancel edits on click in `MODE_EDIT`
- `reloadFeatureToMaplibre()` + `updateSelectedMarker()` after feature create
- `LayersFragment`: sync button state fixes
- `OfflineSyncIntentService`: periodic sync logging
- Version bump to 3.0.2 (build 171)

Conflicts resolved (our customizations preserved):
- Walk-by-geometry feature kept active
- Sync guard (`isLayerFillServiceBusy`) kept
- `queryAllFeatureIdsFromDb` in `AttributesFragment` kept
- Batch import with transactions in `NGWVectorLayer`/`GeoJSONUtil` kept
- AGP 9.x build system kept
- Conditional signing config kept

---

## 13. Collector Import Verification, Layer Ordering, and Sync Timestamp

**Purpose:** make Collector project import resilient to network changes; keep
vector layer order aligned with the collector project in the drawer (including
after repair and when adding a single missing “middle” layer); show the layer
name in the fill progress dialog during form unzip; defer heavy map reload until the
fill queue drains with a safe flush on `MapFragment` resume.

### Collector batch (maplib + maplibui)

- **Register** expected vector layers plus **full** collector project remote-id
  order (`collector.getLayers()`) so insert position is correct even when only a
  subset is downloaded.
- **`LayerFillService`** notifies per-layer success/failure; **`UnzipForm`**
  failures notify using `KEY_REMOTE_ID`; **`NGWVectorLayerFillTask`** uses
  `KEY_COLLECTOR_TRACKING_REMOTE_ID` when form metadata overrides resource id.
- **`finalizeCollectorImportVerifyAndRepairIfNeeded()`** (main thread): compare
  map vs expected set; remove broken/missing; re-queue fill (up to **3** repair
  waves); `HyperLog` + toasts `collector_import_repair_queued` /
  `collector_import_repair_gave_up`.
- **`LayerGroup.computeCollectorOrderedInsertIndex`**: ranks use `L - 1 -
  projectIndex` so order matches the drawer (adapter uses reversed index:
  internal slot 0 = list bottom).
- **`LayerGroup.findNgwVectorLayerByRemoteIdRecursive`**: locate layer for
  verify/repair.

### Layer fill UI and rasters

| File | Changes |
|------|---------|
| `LayerFillService.java` | Collector extras on intents; `insertLayer` for collector NGW; **`LocalTMSFillTask` + `mIsNgrc`** → insert **above** the `osm` layer (`getChildLayerIndex(osm)+1`), or index 0 if OSM missing; `getDescription()` falls back to `mLayerName` when `mLayer` is null (`UnzipForm`) |
| `LayerFillProgressDialogFragment.java` | Refresh title on `STATUS_START` for multi-layer batches |
| `SelectNGWResourceActivity.java` / `SelectNGWResourceDialog.java` | Full-project `long[]`, `registerCollectorImportBatch(…, fullOrder)`, forward enqueue with `KEY_COLLECTOR_ORDER_INDEX` + `KEY_COLLECTOR_PROJECT_REMOTE_IDS` |
| `GISApplication.java` | Batch state, repair passes, verify/repair intents with full project order |
| `IGISApplication.java` | Extended `registerCollectorImportBatch`, `notifyCollectorLayerFillResult`, `finalize…`, `clear…` |

### Deferred map reload (batch fill)

| File | Changes |
|------|---------|
| `GISApplication.java` | `requestMapReloadAfterLayerFillBatch()` posts to main; `flushPendingMapReloadAfterLayerFillIfNeeded()` |
| `MapFragment.kt` | Calls flush on `onResume()`; optional startup progress caption when `MAP_STARTUP_OPTIMIZATIONS_ENABLED` |
| `MainApplication.java` | Optional HyperLog “no remote” URL when startup optimizations flag is on |

### Strings (maplibui)

- `collector_import_repair_queued`, `collector_import_repair_gave_up` (en + ru)

---

## 14. Default Preferences, Base Layers, Tracks Display, and NGRc Zoom

**Purpose:** align factory defaults with GeonicalSystem product expectations; drop
legacy “demo” vector layers; show tracks as polylines only; widen `.ngrc` raster
visibility slightly beyond packaged tile zoom range.

### Default preferences (app `res/xml` + matching `get*` fallbacks)

| Area | Keys / behavior |
|------|-----------------|
| **General** (`preferences_general.xml`) | Sync notification off; analytics (`ga_enabled`) off; extended logs on; compass magnetic needle on (`compass_show_magnetic`). |
| **Map** (`preferences_map.xml`) | Scale ruler off; zoom level on; measuring ruler on; map background `light`. |
| **Location** (`preferences_location.xml`) | Source **3** (GPS + other networks); min time **2** s; min distance **5** m. |
| **Tracks** (`preferences_tracks.xml`) | Min time **5** s; min distance **5** m. |

**Code fallbacks** (when a preference key is absent): `MainApplication`,
`MainActivity`, `SettingsActivity` (`save_log`, `KEY_PREF_GA`); `MapFragment.kt`
(map overlays); `GISApplication.getMapBackground()` and `MapDrawable.updateMapBackground()`
(`map_bg` → light); `GpsEventSource`, `WalkEditService` (location distance **5**);
`TrackerService` (track interval defaults **5** / **5**); `LocationUtil` (location
source default **3** for non-track queries).

**Compass:** `CompassFragment.onResume()` reads true north / magnetic / vibrate
from `SharedPreferences` so the map mini-compass matches settings (maplibui).

### No default editable vector layers

`MainApplication.initBaseLayers()` adds **only** the OSM base raster when missing,
with **`setVisible(false)`**; if OSM already exists (e.g. after settings reset), it is
forced **off** via **`((ILayerView) existingOsm).setVisible(false)`** (`ILayer` has no
`setVisible`). Removed creation of empty `vector_a` / `vector_b` / `vector_c`
(“points/lines/polygons for edit”). `SettingsFragment.deleteLayers()` on reset no
longer preserves those paths—only OSM and the tracks layer stay.

**`.ngrc` insert order:** `LayerGroup.getChildLayerIndex` + `insertLayer(osmIndex + 1, …)`
so local rasters sit **above** OSM in the stack (drawn on top of OSM).

**maplib:** `LayerGroup.getChildLayerIndex(ILayer)` wraps the existing static index helper.

### Tracks: no start/end flag icons

- **MapLibre:** removed `track-flag-source`, `track-flags-layer`, and flag bitmap
  registration from `MapDrawable`; `createFeatureListFlagsFromTrackLayer()` in
  `MPLFeaturesUtils` returns an empty feature list (API kept).
- **Canvas / legacy renderer:** `TrackRenderer` no longer draws green/red flags;
  `setEndingMarker` removed; `TrackLayerUI` does not install a flag bitmap.

### `.ngrc` local raster zoom range

`TMSLayer.fillFromNgrc()`: after `load()`, expands layer visibility by **±2**
zoom levels vs values from the archive config, clamped to
`GeoConstants.DEFAULT_MIN_ZOOM` / `DEFAULT_MAX_ZOOM`, then `save()`.

### Product display names: flavors **lisa** / **belka** (один `applicationId`)

Два варианта сборки модуля **`app`** с разным названием в лаунчере, **без** смены пакета: и **NextGIS ЛИСА**, и **NextGIS Белка** используют `applicationId` **`com.nextgis.mobile`** (в debug — **`.debug`**). Установка APK «Белки» **обновляет** уже установленную «ЛИСУ», а не ставится вторым приложением.

| File | Notes |
|------|--------|
| `app/build.gradle` | `flavorDimensions "brand"`; flavors **`lisa`** / **`belka`** с `resValue` для **`APP_NAME`** и **`app_name`**. Имена из `buildTypes` убраны — только Sentry, провайдеры, `buildConfigField`. |
| `app/src/main/res/values/strings.xml` | Жёсткий **`app_name`** убран; строки задаются flavor’ами. |
| `maplibui/src/main/res/values/strings.xml` | Fallback **`app_name`** для сборки библиотеки; в итоговом APK подменяется значением из **`app`**. |
| `NGActivity.java` (maplibui) | **`getAppName()`** по-прежнему через **`ApplicationInfo.loadLabel(PackageManager)`** — совпадает с лейблом установленного варианта. |

**Сборка:** `./gradlew :app:assembleRelease` собирает **оба** release (`lisaRelease`, `belkaRelease`). В Android Studio откройте **Build Variants** и выберите строку модуля **`app`**: там варианты вида **`lisaDebug`**, **`belkaRelease`** и т.д. У модулей-библиотек (`maplibui`, `maplib`, …) flavors нет — у них по-прежнему только **debug** / **release**; это нормально. APK лежат в `app/build/outputs/apk/lisa/<buildType>/` и `app/build/outputs/apk/belka/<buildType>/` (базовое имя архива — `ngmobile-<versionName>` из `base.archivesName`).

**Неоднозначные Gradle-задачи:** без flavor в имени (`assembleDebugUnitTest`, `testDebugUnitTest`, `assembleDebug` и т.п.) Gradle находит несколько кандидатов. В конце `app/build.gradle` добавлены **alias-задачи**, по умолчанию указывающие на вариант **`lisa`**. Для **Белки** вызывайте явно, например `:app:assembleBelkaDebugUnitTest`.

---

## 15. Git Workflow Reference

### Repository structure

```
upstream (read-only)                         origin (read-write)
────────────────────────────                 ────────────────────
nextgis/nextgis_mobile_android      →        GeonicalSystem/nextgis-mobile-custom
nextgis/android_maplib              →        GeonicalSystem/android_maplib
nextgis/android_maplibui            →        GeonicalSystem/android_maplibui
nextgis/easypicker                  →        GeonicalSystem/easypicker
```

### Branches

- `my-maplibre` — custom development branch (in all repos)
- **Parent app:** merge **`upstream/master`** from `nextgis/nextgis_mobile_android` into your working branch (that upstream remote exposes only **`master`** today; the historical **`maplibre`** branch is gone there)
- **Submodules** (`maplib`, `maplibui`, `easypicker`): each has its own **`upstream`** (`nextgis/android_maplib`, …); merge **`upstream/master`** inside each, then commit updated submodule pointers in the parent

### Pulling upstream updates

```bash
# Main repo (app + submodule pointers)
cd android_gisapp
git fetch upstream
git merge upstream/master

# Each submodule
cd maplib
git fetch upstream
git merge upstream/master

cd ../maplibui
git fetch upstream
git merge upstream/master

cd ../easypicker
git fetch upstream
git merge upstream/master
```

### After merge: update submodule references

```bash
cd android_gisapp
git add maplib maplibui easypicker
git commit -m "Update submodules after upstream merge"
```

### Key conflict-prone files (check carefully on merge)

- `maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java`
- `maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java`
- `maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java`
- `maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java`
- `maplibui/src/main/java/com/nextgis/maplibui/overlay/EditLayerOverlay.java`
- `app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt`

### Upstream sync (официальный `nextgis_mobile_android`)

- Remote **`upstream`** родительского репозитория: `https://github.com/nextgis/nextgis_mobile_android.git` (на GitHub раньше тот же проект фигурировал как **`android_gisapp`**).
- **Краткий handoff для новых чатов с ИИ:** **[`CONTEXT_INSTRUCTION.md`](CONTEXT_INSTRUCTION.md)** — структура репо, порядок чтения доков, инварианты форка, карта «горячих» файлов.
- Полный отчёт о последней синхронизации: **[`UPSTREAM_SYNC_REPORT.md`](UPSTREAM_SYNC_REPORT.md)** (ref `upstream/master`, инвентаризация, merge maplib / maplibui / корня, классификация A/B/C).
- Пользовательский changelog (что заметит пользователь) — **[`WHATS_NEW.md`](WHATS_NEW.md)**.
- **Скрипт-помощник:** **[`tools/upstream-sync.ps1`](tools/upstream-sync.ps1)** —
  PowerShell-скрипт с режимами `Inventory` / `BackupTags` / `MergeSubmodules` / `MergeRoot`.
  Скрипт делает только безопасные операции (fetch / tag / merge --no-commit), без
  push / reset --hard / --force / --amend.
  ```powershell
  pwsh tools/upstream-sync.ps1 -Mode Inventory             # fetch + draft report
  pwsh tools/upstream-sync.ps1 -Mode BackupTags            # pre-upstream-sync-<date>-<repo>
  pwsh tools/upstream-sync.ps1 -Mode MergeSubmodules       # maplib + maplibui + easypicker
  pwsh tools/upstream-sync.ps1 -Mode MergeRoot             # parent + submodule pointer bump
  ```
- **Backup-теги:** перед каждым merge стоит ставить `pre-upstream-sync-<date>-<repo>` теги
  на `my-maplibre` — это даёт быстрый откат через `git reset --hard <tag>`.
- После крупного merge: `git fetch upstream --prune`, при необходимости merge в сабмодулях первыми, затем обновить указатели в корне и проверить сборку обоих flavors.
- **Проверено в работе:**
  - **2026-03 / upstream 3.0.2 / `versionCode` 173** (см. отчёт в `UPSTREAM_SYNC_REPORT.md`,
    раздел в самом начале).
  - **2026-05 / upstream 3.0.3 / `versionCode` 178** → форк `3.0.3.1` / 179 (раздел
    «Цикл 2026-05-15» в `UPSTREAM_SYNC_REPORT.md`). Walk-by-geometry разобран per-аспект в
    [§17 Walk reconciliation](#17-walk-reconciliation).
  - **Патч `3.0.3.2` / `versionCode` 180** — см. [§16 — 3.0.3.2](#3032-versioncode-180).

---

## 16. Fork patch releases (GeonicalSystem)

Трекинг версий форка относительно апстрима (`versionName` / `versionCode` в [`app/build.gradle`](app/build.gradle); у модуля **`maplib`** выравнивается `versionName` в [`maplib/build.gradle`](maplib/build.gradle) для `BuildConfig`).

### 3.0.2.2 (`versionCode` 174)

- **Стабильность (редактирование / UI):** в мультиполигоне исправлен краш при удалении вершины при несогласованных индексах выделения (`MultiPolygonEditClass`, `MapDrawable.canDeleteCurrentPointSafe`, `EditLayerOverlay`, `MapFragment`). В **`LayersFragment`** устранён NPE при `onDestroyView`: отложенный runnable больше не вызывает `setDrawerListener`/`addDrawerListener` на уже обнулённом `DrawerLayout` (`removeCallbacks`, `removeDrawerListener`, регистрация слушателя один раз).
- **Синхронизация NGW:** в `NGWVectorLayer.cursorToJson` для геометрии используется `getColumnIndexOrThrow(FIELD_GEOM)` вместо `getColumnIndex`, чтобы индекс колонки для `getBlob` был валиден (lint `Range` / отсутствие колонки — явное исключение).

### 3.0.2.3 (`versionCode` 175)

- **NGW `description` / config при `SYNC_NONE`:** сверка `resourceMeta` и JSON описания с сервера для **всех** векторных NGW-слоёв учётки при полной синхронизации; рекурсивный второй проход для слоёв с отключённой синхронизацией данных; `isSomeToSync` учитывает наличие `NGWVectorLayer` для аккаунта; `sync()` по одному слою (`ACTION_LPATH`) обновляет конфиг при `SYNC_NONE`. Подробно: [§9 — Config Sync from NGW Description](#9-config-sync-from-ngw-description) (подзаголовок *NGW config when data sync is off*).
- **Карта после пакетного fill:** `MaplibreMapInteraction.reloadMapStyleAndLayersAfterLayerFillBatch()` возвращает `boolean`; в `IGISApplication` — `clearMapReloadAfterLayerFillPending()`; реализации в `GISApplication` и `MapFragment` сбрасывают внутренний «pending map reload» после успешного reload / bounded retry.
- **Сброс настроек (импорт/карта):** в `SettingsFragment` при подтверждённом сбросе всегда `RESULT_OK`, `resetMap()` + единый путь `deleteLayers` / `initBaseLayers`, без ветвления `SDCardUtils` — чтобы после сброса `MainActivity` пересоздавалась и `MapFragment` не оставался на устаревшем `MapDrawable` (слои с импорта отображаются без перезапуска процесса).

### 3.0.3.1 (`versionCode` 179)

- **Поднят base upstream до 3.0.3.** Закрыт цикл синка от 2026-05-15 — см.
  [`UPSTREAM_SYNC_REPORT.md`](UPSTREAM_SYNC_REPORT.md), раздел «Цикл 2026-05-15».
- **Растровые слои (`.ngrc`) после импорта:** добавлен `loadLayersLite()` вызов в
  `MapDrawable.addLayerByID` сразу после `createFillLayerForLayer` для GT_RASTER_WA —
  совпадает с эффектом тапа по списку слоёв, новый растр сразу встаёт под пользовательский
  стек без перезапуска. `MPLFeaturesUtils.resolveRasterSiblingAnchorOrNull` ищет OSM
  как якорь (`addLayerAbove(raster, osm)`) или предпочитает sibling выше при наличии.
- **Walk-by-geometry:** проведена per-аспектная сверка нашей реализации с upstream'овской
  (см. [§17 Walk reconciliation](#17-walk-reconciliation)). Сохранён наш pipeline
  (`applyInitialWalkGeometryAtStartLocation` + `prepareMaplibreSessionForNewWalkGeometry`),
  взяты upstream'овские `ChooseLayerDialog(useCreatePoint, startFillByWalk)`, `saveToHistory`
  + `updateHistoryByWalkEnd`, поля `layerForWalkRestore/featureToRestore` для process-kill
  restore, signature `startFeatureSelectionForEdit` с `isFillByWalking`.

### 3.0.3.2 (`versionCode` 180)

- **NGW / схема полей:** `NGWLayerSchemaCompat.localSchemaMatchesServerMeta` — сравнение
  **в обе стороны** (локальное поле, удалённое на Web GIS, больше не игнорируется).
- **Пересборка слоя при mismatch схемы:** `GISApplication.scheduleNgwLayerRebuildAfterSchemaMismatch` —
  перед удалением слоя попытка `sendLocalChanges`; сохранение индекса слоя в группе и
  `LayerFillService.KEY_LAYER_RESTORE_INSERT_INDEX` для вставки на прежнее место; после старта
  fill — `LayerFillProgressDialogFragment.startBatchFillProgress` (как при batch fill). В intent
  не передаётся устаревший `KEY_LAYER_CONFIG_JSON` (свежее описание подтягивается в сервисе).
- **`LayerFillService`:** при успешном fill — `insertLayer` по сохранённому индексу, если extra задан.
- **Карта / краш:** `MapDrawable.checkLayerVisibility` — ранний выход, если слой уже удалён
  (NPE `Layer.isVisible()` при гонке с пересборкой по mismatch). `MapDrawable.syncUserLocationSourceFromStyle`
  после lite-перезагрузки стиля и из `updateLocation`, чтобы GeoJson user-location не «отваливался».
- **`MapFragment`:** исправлен refresh слоёв в `onResume` — `getVectorLayersById(..., layerId)` вместо
  `id` фрагмента; убран `tmpFirstLocation`; общий `applyLocationFixToMap` + `onBestLocationChanged`
  (GpsEventSource шлёт только «лучший» фикс туда); `updateLastLocation` с fallback на `mCurrentCenter`;
  вызовы после `setMapLayersLoaded` / `loadLayersLite` / «локации» для актуального puck без обязательного
  сворачивания приложения.
- **Логи (прод):** `ProdLogUtil` (обрезка, scrub URL, сводка `SyncResult`); `NGWVectorLayer.reportSyncHttpFailure`
  и доработки `log`/`getFeatures`; итог sync в `SyncAdapter`; `HyperLogCrashHandler` — короткий headline
  + `throwable`, fallback в `Log.e`; `Logger` / `GISApplication` / `MainApplication` — не ставить второй
  `HyperLogCrashHandler`, если он уже default; `LayerFillService` — строка в лог перед Toast ошибки fill.
- **Документация:** в §15 ссылка на [`CONTEXT_INSTRUCTION.md`](CONTEXT_INSTRUCTION.md); в корне репозитория
  добавлен этот файл (короткий handoff для новых чатов с ИИ).

---

## 17. Walk reconciliation

Контекст: upstream `3.0.3` восстановил «Add geometry ByWalk» (root коммиты `f95c07e` +
`b9dd9d6`, maplib `147262e`, maplibui `584acce5`). Наш форк уже имел свою реализацию (см.
[§2 Walk-by-Geometry Feature Restoration](#2-walk-by-geometry-feature-restoration)). Цикл
2026-05-15 — первая сверка по аспектам.

| Аспект | Наш форк | Upstream | Решение | Обоснование |
|--------|----------|----------|---------|-------------|
| Стартовая геометрия (single-layer вход) | `applyInitialWalkGeometryAtStartLocation` строит degenerate-line / micro-polygon в Web-Mercator от GPS (или камеры) | `createPointFromOverlay(true)` + повторный `newGeometryByWalk` | **ours** | Наш anchor явный и устойчивый (Web-Mercator с offset метрами), дублирующий вызов upstream выглядит ad-hoc. |
| MapLibre edit-session старт | `prepareMaplibreSessionForNewWalkGeometry` → `MapDrawable.startFeatureSelectionForEdit(layer, type, feature, true, style, true)` + `replaceGeometryFromHistoryChanges(startGeom)` | пустой блок в `addGeometryByWalk` (полагается на `EditLayerOverlay.newGeometryByWalk`) | **ours** | Без явного MapLibre session restore последняя точка не «цепляется» к редактируемому фиче после rotate/process kill. |
| Process-kill / rotate restore | `attachMaplibreToCurrentWalkOverlayGeometry` пере-подключает MapLibre к текущей overlay-геометрии | поля `layerForWalkRestore` + `featureToRestore` в `MapDrawable`, восстановление в `loadLayersToMaplibreMap` after-style-loaded | **hybrid** | Используем `attach…` из форка для UI-возврата + upstream'овские поля и блок restore в `MapDrawable` (вложены в наш listener в `loadLayersToMaplibreMap`). |
| History (Undo/Redo) при walk-end | вызывался только наш `editLayerOverlay.onOptionsItemSelected` | `undoRedoOverlay.saveToHistory(...)` + `(mApp.map as MapDrawable).updateHistoryByWalkEnd()` | **upstream** | Полноценный undo в режиме walk, у нас он отсутствовал. |
| ChooseLayerDialog signature | `ChooseLayerDialog(boolean useCreatePoint)` | `ChooseLayerDialog(boolean useCreatePoint, boolean startFillByWalk)` | **upstream** | Новый параметр нужен и используется в `onFinishChooseLayerDialog` для разветвления walk vs обычного «edit». |
| `MaplibreMapInteraction.setMapLayersLoaded` / `checkCreateIfNeed` | отсутствовали | новые методы интерфейса | **upstream** | Реализация в `MapFragment.kt` — пустые `override` (fork использует свой deferred reload, см. §13). |
| `startFeatureSelectionForEdit(...)` signature | 5 параметров | 6 (добавлен `isFillByWalking`) | **upstream** | Наш walk-вход всегда передаёт `true`, обычный edit — параметр не нужен на верхнем уровне. |
| Track start/end flags после walk | отсутствуют | upstream восстановил `track-flag-source`/`track-flags-layer` + bitmaps | **ours** (skip) | §14 — флаги намеренно отключены; pollute стиля MapLibre лишними source/layer. `checkLayerVisibility(track.id)` сохранён. |
| Дубликат `R.id.add_geometry_by_walk` в when-handler MapFragment | дубликат был | upstream удалил | **upstream** | Это был баг форка (двойная регистрация click handler). |
| Имя поля `MapDrawable.mapFragment` | `mapFragment` | upstream переименовал в `mapContext` (плюс `setMapContext`) | **upstream** | Все ссылки форка обновлены (9 мест в `MapDrawable`, 3 в `MapFragment`, 1 в `GISApplication`). |

### Что НЕ применено (и почему)

- Upstream'овский `addGeometryByWalk` single-layer pipeline (двойной `newGeometryByWalk`) —
  заменён нашим (см. таблицу выше).
- Track-flag иконки — оставлены отключёнными (§14).

### Следующий цикл — на что смотреть

- Если upstream объединит свой walk-restore (`layerForWalkRestore/featureToRestore`) с
  нашим `attachMaplibreToCurrentWalkOverlayGeometry`, можно будет уменьшить hybrid и
  перейти на чисто upstream-вариант.
- `WalkEditService.isServiceRunning(Context)` сейчас — наш статический helper; upstream
  его не использует. Если upstream добавит свой эквивалент — сравнить семантику.
- `Multi*EditClass.addNewFlowPoint(LatLng, boolean)` — новый upstream-метод; наш форк сейчас
  им не пользуется напрямую, но если потребуется «начать walk без anchor» — это естественная
  точка интеграции.
