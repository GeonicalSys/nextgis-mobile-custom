# GeonicalSystem Fork — Customizations Catalog

This document describes all modifications made to the official NextGIS Mobile
application (https://github.com/nextgis/android_gisapp, branch `maplibre`) in
the GeonicalSystem fork. It serves as a reference for reproducing changes on
future upstream versions.

Base commit: `7dde21c` (upstream `maplibre` branch, "3.0.0 release").

---

## Table of Contents

1. [Build System Upgrade](#1-build-system-upgrade)
2. [Walk-by-Geometry Feature Restoration](#2-walk-by-geometry-feature-restoration)
3. [MapLibre Rendering and Layer Loading](#3-maplibre-rendering-and-layer-loading)
4. [NGW Sync and Layer Fill](#4-ngw-sync-and-layer-fill)
5. [NGW Resource Selection UI](#5-ngw-resource-selection-ui)
6. [Miscellaneous App Fixes](#6-miscellaneous-app-fixes)
7. [Localization](#7-localization)
8. [Git Workflow Reference](#8-git-workflow-reference)

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

## 8. Git Workflow Reference

### Repository structure

```
upstream (read-only)                    origin (read-write)
─────────────────────                   ────────────────────
nextgis/android_gisapp          →       GeonicalSystem/nextgis-mobile-custom
nextgis/android_maplib          →       GeonicalSystem/android_maplib
nextgis/android_maplibui        →       GeonicalSystem/android_maplibui
nextgis/easypicker              →       GeonicalSystem/easypicker
```

### Branches

- `my-maplibre` — custom development branch (in all repos)
- `maplibre` — tracks upstream `maplibre` branch (main repo only)
- `master` — tracks upstream `master` branch (submodules)

### Pulling upstream updates

```bash
# Main repo
cd android_gisapp
git fetch upstream
git merge upstream/maplibre

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
