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
17. [Walk reconciliation](#17-walk-reconciliation)
18. [NGW district filter (collector project)](#18-ngw-district-filter-collector-project)
19. [Photo attachment coordinate overlay](#19-photo-attachment-coordinate-overlay)
20. [Collector project architecture foundation](#20-collector-project-architecture-foundation)
21. [Layer data backups before automatic reload/removal](#21-layer-data-backups-before-automatic-reloadremoval)
22. [Collector composition apply sync](#22-collector-composition-apply-sync)
23. [Collector multi-project UX](#23-collector-multi-project-ux)
24. [Local vector tiles render-mode](#24-local-vector-tiles-render-mode)
25. [Self-hosted APK updates](#25-self-hosted-apk-updates)

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

**Purpose:** MapLibre vector rendering, GeoJSON conversion, disk cache, hot-reload,
NGW style sync, and editing session management.

### Architecture (fork, GeonicalSystem)

| Component | Role |
|-----------|------|
| `MplFeatureStyleProps` | Canonical GeoJSON property names; `apply()` / `clear()` per geometry type |
| `maplib/.../map/mpl/*LayerFactory` | Point / Line / Polygon MapLibre layer builders (`MplLayerBuildContext`) |
| `MPLFeaturesUtils` | GeoJSON feature build, rule-style props, `createSourceForLayer`, hot-reload helpers |
| `VectorLayerRenderCache` | Disk cache schema 3: geom file + in-memory style; optional native URI file |
| `NgwLayerConfigAdapter` | Normalizes NGW/collector `renderer_properties` before `setRenderer()` |
| `MapDrawable` | `loadLayersToMaplibreMap`, style-only reload, native GeoJSON URI wiring |

### VectorLayerRenderCache (F1 / F4) — cold start

**Flags** ([`Constants.java`](maplib/src/main/java/com/nextgis/maplib/util/Constants.java)):

| Flag | Default | Role |
|------|---------|------|
| `VECTOR_RENDER_DISK_CACHE_ENABLED` | `false` | Disk cache read/write/invalidation (`VectorLayerRenderCache`); **disabled 2026-06** after `Expression.toArray()` NPE during collector import (`LineLayerFactory.lineDasharray(null)`); re-enable only after on-device regression (see matrix below) |
| `MAP_STARTUP_PARALLEL_VECTOR_PREP` | `false` | Parallel vector prep in thread pool — enable only after cache regression |
| `MAP_STARTUP_UX_EXTRAS_ENABLED` | derived | Progress caption, timing logs, HyperLog placeholder URL |
| `USE_MAPLIBRE_NATIVE_GEOJSON_URI` | `false` | Native file URI for **read-only** layers with cache HIT only |

- **Schema 3** (`features-geom.geojson`): geometry + stable ids only; style applied in memory via `MPLFeaturesUtils.refreshMaplibreStyleOnFeatures()`.
- **Schema 3 styled file** (`features-styled.geojson`): full features with style props for native MapLibre parse (F4).
- **Data change** → `invalidateOnDataChange` (bumps `geom_cache_generation`, deletes cache).
- **Style change** → `invalidateOnStyleChange` (keeps geom cache; deletes styled file).
- **Legacy schema 2** is purged on load; next MISS rewrites schema 3.
- **`hasValidCache(layer)`** — meta-only check (no GeoJSON parse) for diagnostics.
- **`USE_MAPLIBRE_NATIVE_GEOJSON_URI`**: when `true`, layers with `!isEditingAllowed()` (collector display-only) with valid styled cache use file URI; falls back to `setGeoJson` on failure. Requires `VECTOR_RENDER_DISK_CACHE_ENABLED`.

#### On-device regression matrix (before enabling parallel prep or native URI)

Test layer: 20k+ features, `is_editable: false`, rare sync. Logcat tag: `VectorLayerRenderCache`.

| # | Scenario | Expected |
|---|----------|----------|
| 1 | 1st cold start after clearing app cache | MISS, `DB build`, `cache WRITE geom`, features visible |
| 2 | 2nd cold start | HIT `geom+style`, no `DB build`, correct style |
| 3 | Rule-style + labels after HIT | Category colors and labels correct |
| 4 | Simple style color change | Hot-reload, no full SQLite scan |
| 5 | Rule-style change | Props updated, geom cache retained |
| 6 | Visibility off/on | No SQLite scan |
| 7 | Data sync | `invalidateOnDataChange`, one rebuild |
| 8 | Editable layer edit/walk | No regression |

### Hot-reload (F2)

`MapDrawable.reloadFillLayerStyleToMaplibre` / `reloadVectorLayerStylePropsToMaplibre`:

| Situation | Behaviour |
|-----------|-----------|
| Simple style, paint-only change | Update MapLibre layer paint only (no SQLite scan) |
| Rule-style or labels | Refresh props in memory → `setGeoJson` on source |
| No features in memory | Load geom cache + apply style; else full data reload |

Data edits still use `reloadVectorLayerDataToMaplibre` (full SQLite scan + cache write).

### Iteration changelog (2026-06 — MapLibre max + stability)

Phased work in one development cycle; details for crash/sync/FGS fixes are in
[§10.11](#1011-reliability-hardening-pass-crash-diagnosability-maplibre-nulls-fgs-sync).

| Phase | Theme | Key changes |
|-------|-------|-------------|
| **0** | Cache + reload bugs | Real signature text on edit (not placeholder); `VectorLayerRenderCache` treats `styleFp` mismatch as MISS; `invalidateOnStyleChange()` on renderer change; `MapDrawable.canReloadVectorLayerStyleOnMap()` (style reload no longer blocked outside `MODE_NORMAL`); `VectorLayer.applySoftConfigUpdate` calls `notifyLayerChanged()` only when renderer/zoom/visibility actually changed |
| **1** | Labels | `LabelAttributes` (halo, zoom scale, collisions, `${field}` template); `text-size` interpolate **wraps** size (not buried inside `coalesce`); simple renderer drops stale per-feature `textsize` via `removeTextStyleProps()` so SymbolLayer owns size; line/polygon text props parity in `applyGeometrySpecificStyle` |
| **2** | Symbology | `MplStyleMapper` (opacity, line cap/join, dash presets, blur scale); fill/stroke opacity via `coalesce(get(prop), default)` — **not** `toNumber(get)` (missing prop became 0); markers via SymbolLayer + SDF; polygon fill patterns; line type 4 «dash with edging» |
| **H1** | Factory split | `MplFeatureStyleProps`, `MplLayerBuildContext`, `PointLayerFactory` / `LineLayerFactory` / `PolygonLayerFactory` extracted from `MPLFeaturesUtils` |
| **F1** | Geom/style cache split | Schema 3 geom file + in-memory style refresh (`toGeometryShells`, `refreshMaplibreStyleOnFeatures`, `needsSourceStyleRefresh`) — see [VectorLayerRenderCache](#vectorlayerrendercache-f1--f4--cold-start) |
| **F2** | Hot-reload paths | Paint-only vs props refresh vs full SQLite reload — see [Hot-reload (F2)](#hot-reload-f2) |
| **F4** | Native URI (flagged off) | Styled GeoJSON file + `USE_MAPLIBRE_NATIVE_GEOJSON_URI`; requires disk cache enabled |
| **G1** | NGW renderer JSON | `NgwLayerConfigAdapter` — see [NGW renderer mapping (G1)](#ngw-renderer-mapping-g1) |

**MapLibre `Expression.toArray()` NPE family** (collector import + settings exit): do not pass `null` into
`setFilter`, `lineDasharray`, or `fillPattern`; use `Expression.coalesce` for optional pattern props.
Documented file-by-file in [§10.11](#1011-reliability-hardening-pass-crash-diagnosability-maplibre-nulls-fgs-sync).

### MapLibre style features (iterations 1–3)

- Layer opacity (`layer_opacity`), text opacity, line miter limit
- Rule-style: `key_ignore_case`, explicit `other_style` category
- `circle-blur`, `line-blur` (line blur ×4 in `MplStyleMapper` for thin lines)
- Polygon fill patterns 4–6 (brick, forest, marsh) via `PolygonPatternRegistry`
- Markers: SymbolLayer + SDF sprites; rule-style dual FillLayer (solid / pattern)

### NGW renderer mapping (G1)

`NgwLayerConfigAdapter` runs on `VectorLayer.fromJSON`, `setRenderer`, and `applySoftConfigUpdate`:

- Hoists nested `label_attributes` (halo, template, zoom, collisions)
- Maps legacy aliases: `label` → `display_name`, `label_field` → `value`, `halo_*` → `text_halo_*`, `template` → `label_template`
- Converts web rule map `rules: { "key": style }` → mobile JSONArray format
- Normalizes fractional opacity (0–1) to 0–255
- Renderer name aliases: `RuleRenderer` → `RuleFeatureRenderer`

See also [§9 — Config Sync from NGW Description](#9-config-sync-from-ngw-description).

### Files changed — `maplib/`

| File | Changes |
|------|---------|
| `MapDrawable.java` | MapLibre interaction, walk/edit, `loadLayersToMaplibreMap`, hot-reload, `sourceNativeUriMap` |
| `MPLFeaturesUtils.java` | GeoJSON props, rule-style, layer factories orchestration, native URI fallback |
| `MplFeatureStyleProps.java` | Props matrix (H1) |
| `map/mpl/PointLayerFactory.java` | Circle / Symbol layers |
| `map/mpl/LineLayerFactory.java` | Line + dash sublayers |
| `map/mpl/PolygonLayerFactory.java` | Fill + outline + pattern layers |
| `VectorLayerRenderCache.java` | Geom/style split cache, styled file for native URI |
| `NgwLayerConfigAdapter.java` | NGW renderer JSON normalization (G1) |
| `NgwLayerSchemaCompat.java` | NGW schema compatibility check |
| `LabelAttributes.java` | Halo, template, zoom, collisions |
| `PolygonPatternRegistry.java` | Fill patterns + custom PNG API |
| `MplStyleMapper.java` | MapLibre paint expressions (blur scale, dash, etc.) |
| `FieldStyleRule.java` | Rule keys, `other_style`, `key_ignore_case` |
| `VectorLayer.java` | `applySoftConfigUpdate`, cache invalidation hooks |
| `MaplibreMapInteraction.java` | `reloadMapStyleAndLayersAfterLayerFillBatch`, `loadLayersLite()` |
| `GeoJSONUtil.java` | Streaming GeoJSON, hole validation (B5) |

### Regression checklist

- Walk-by-geometry: live geometry + restore after process kill
- Edit session: selection overlay, history, style reload
- Rule-style: solid + patterned fill on same layer
- Large layer: cold start cache hit; style-only change without `DB build` in logcat
- NGW sync: change style in Web GIS description → soft update on next sync

### How to reproduce

On upstream merge, carefully merge `MapDrawable.java`, `MPLFeaturesUtils.java`, and
`VectorLayerRenderCache.java` — highest conflict risk. Keep `MaplibreMapInteraction`
interface in sync. Enable native URI only after device testing:
`VectorLayerRenderCache.USE_MAPLIBRE_NATIVE_GEOJSON_URI = true`.

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

### Post-push feature refresh

After a successful NGW feature create/update, `NGWVectorLayer.sendLocalChanges()` immediately
requests that single feature back from NGW and applies server data to the local row. This keeps
server-owned values filled by PostgreSQL triggers/defaults (for example `district`, `created_at`,
`updated_at`) visible without waiting for the next sync cycle.

- Refresh is data-only: it uses the single-feature endpoint without attachment extensions and does
  not reconcile attachment tables.
- Refresh runs after processed change records are removed, then re-checks pending local data changes
  before applying the server row, so edits made during the sync are not overwritten.
- Refresh failures are logged but do not make the already successful push fail; a later pull can
  still converge the row.

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
| `maplib/.../util/NgwLayerConfigAdapter.java` | Normalizes server `renderer_properties` (label halo/template, rule map format, opacity aliases) before `setRenderer()` |

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

### 10.11 Reliability hardening pass (crash diagnosability, MapLibre nulls, FGS, sync)

**Purpose:** make crashes diagnosable from the exported log (what + why), eliminate the
remaining MapLibre main-thread `Expression.toArray()` NPE family, harden first load and the
collector foreground-service stop, and close silent-failure gaps in data/config sync. Disk render
cache, parallel vector prep, native GeoJSON URI and cold-start flags are out of scope (untouched).
MapLibre rendering phases 0–2 / F1–F4 / G1 from the same cycle: [§3 iteration changelog](#iteration-changelog-2026-06--maplibre-max--stability).
`SYNC_FINISH` in maplib `SyncAdapter` here complements [§11](#11-sync-ui-fixes) (app `SyncAdapter` early returns).

| Area | File(s) | Change |
|------|---------|--------|
| Crash log content | `ProdLogUtil.java`, `HyperLogCrashHandler.java` | Persist the full stack trace + cause chain embedded in the HyperLog message (HyperLog stores message text only, not the throwable), capped by `MAX_CRASH_MESSAGE_CHARS`; bounded wait so the async DB write flushes before process death; phase breadcrumb (`setPhase`/`getPhase`) appended to crash headline |
| Log format clobber | `MainApplication.java` | `installHyperLogCrashHandler` re-applies `CustomLogMessageFormat` instead of HyperLog's default `LogFormat` |
| Hot-path logging | `MapDrawable.java` | `logErr(...)` → logcat + HyperLog with full stack; replaced `Log.e(..., ex.getMessage())` catches in map load/edit paths; `ProdLogUtil.setPhase` breadcrumb in `loadLayersToMaplibreMap` |
| MapLibre null guards (sources) | `MPLFeaturesUtils.java` | Raster URL null; `(RasterSource)` / `(GeoJsonSource)` casts guarded with `instanceof` (native URI fallback still logs via `Log.w` + message only — not HyperLog) |
| MapLibre NPE — filter | `PolygonLayerFactory.java` | `fillLayer.setFilter(null)` → always-true `Expression.all()`; rule→simple settings-exit crash |
| MapLibre NPE — line dash | `LineLayerFactory.java` | Solid lines: omit `lineDasharray` property entirely (was `lineDasharray(null)` → NPE during collector layer add) |
| MapLibre NPE — fill pattern | `PolygonLayerFactory.java` | Clearing pattern uses `clearFillPattern()` (`fillPattern("")`) instead of passing null/invalid pattern to MapLibre |
| MapLibre NPE — pattern match | `PolygonPatternRegistry.java` | `patternImageMatchExpression` uses `Expression.coalesce` so missing `fillpattern` prop does not feed null into `toNumber` |
| Simple polygon style reload | `MapDrawable.java` | On style-only reload, re-apply `applyTextAndStyle` for simple polygon features (labels after rule→simple switch) |
| Disk cache off (stability) | `Constants.java` | `VECTOR_RENDER_DISK_CACHE_ENABLED = false` until device regression; tied to collector-import NPE investigation — see [§3 flags](#vectorlayerrendercache-f1--f4--cold-start) |
| Wrong-layer-type reuse | `PolygonLayerFactory.java`, `LineLayerFactory.java` | Drop a wrong-kind reused layer before cast (mirror `PointLayerFactory`); guards `(FillLayer)`/`(LineLayer)` ClassCastException |
| MapLibre null guards (map) | `MapDrawable.java` | `getStyle()` in `updateMapBackground`; `getLayerById(...).getPath()` in `loadLayersToMaplibreMapLite` |
| Main-thread guard | `MapDrawable.java` | `postMainGuarded`/`runGuarded` wrap style-mutation posts (`addLayerByID`, `recreateNGWWebMapSourceById`, `reloadVectorLayer*`, `loadLayersToMaplibreMapLite`, `reloadVectorLayerStyleToMaplibre`) so one bad layer cannot crash the app or abort the rest |
| First load | `MapFragment.kt`, `GISApplication.java` | `onMapReady` safe-calls map ref and null-checks `styleJson` (no `setStyle(fromJson(null))`); `getMap()` logs a corrupt/missing `.ngm` (existing file that fails to parse) instead of silently empty map |
| Collector FGS lifecycle | `LayerFillService.java`, `SelectNGWResourceActivity.java`, `SelectNGWResourceDialog.java` | New `ACTION_ADD_BATCH` + `startFillBatch(...)`: the whole import batch is one `startForegroundService` (single `stopSelf` at drain end) instead of 1 + N−1 `startService` — fixes `ForegroundServiceDidNotStopInTimeException`; explicit `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on `startForeground` (Q+); `mLayerGroup` null/cast guard in `enqueueOneTaskFromExtras` |
| Collector batch integrity | `IGISApplication.java`, `GISApplication.java`, callers | `registerCollectorImportBatch` returns `boolean`; callers abort the collector import (log + toast) instead of importing with no verify/repair; `finalizeCollectorImportVerifyAndRepairIfNeeded` clears the orphaned batch on `map == null` / group-not-found |
| Sync pull semantics | `NgwPullDecision.java` (+ test), `NGWVectorLayer.java` | A failed pull (`ExistFeatureResult.result == false`) no longer treated as success — `getChangesFromServer` aborts (caller skips push, keeps local edits, no tracked-timestamp advance). Pure decision extracted for unit test (`NgwPullDecisionTest`) |
| Config-hash gating | `VectorLayer.java`, `NGWVectorLayer.java` | `applySoftConfigUpdate` tracks ALTER failure (`wasLastSoftConfigUpdateIncomplete`); `KEY_PREF_LAST_CONFIG_HASH` not advanced when a soft schema change failed, so it retries; ALTER failure logged via HyperLog |
| Sync spinner / uncaught | `SyncAdapter.java` (maplib) | Top-level `try/catch(Throwable)/finally` in `onPerformSync` always broadcasts `SYNC_FINISH` (early return / exception); uncaught logged + marked as I/O error; `isSomeToSync` map-null guard |
| Track restart FGS race | `TrackerService.java` | Restarting when unfinished tracks exist closes stale open tracks directly and starts one new foreground service, instead of `ACTION_STOP` + immediate `startForegroundService`; avoids Android's "did not then call startForeground" crash |
| WorkManager in service processes | `GISApplication.java` | Periodic sync setup and delayed `resetSyncTime()` run only in the default app process; service processes such as `:tracks` no longer call `WorkManager.getInstance()` during tracker startup |
| Old-settings compat | `VectorLayer.java` | `fromJSON`: a malformed/foreign renderer/style falls back to `setDefaultRenderer()` (layer stays on the map) instead of failing `load()` and silently dropping the layer; logs which layer |

**Deliberate non-change (flagged for review):** the "buggy data" branches in
`NGWVectorLayer.addFeatureOnServer` / `changeFeatureOnServer` still drop a pending change when the
local feature row is missing, but now log it at WARN with full context (`logBuggyChangeDrop`).
Changing the return value here risks perpetual sync-error retry storms, so the behavior is kept and
documented for further review rather than altered in this pass.

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

### Problem 4: Android periodic sync can become non-runnable after account state drift

The fork keeps Android `PeriodicSync` metadata for compatibility, but account-level
auto sync can silently stop running when `isSyncable <= 0`, and UI toggles
previously only changed `setSyncAutomatically`.

**Fix:** add `SyncAccountWorker` as the app-controlled periodic scheduler. Account
creation, sync toggles, and reset paths explicitly repair `isSyncable`, save the
per-account interval, and schedule/cancel unique WorkManager jobs. Manual sync
also repairs `isSyncable` before `requestSync`. The platform sync adapter is now
hidden from Android's public sync UI and is not marked "always syncable"; the app
owns the scheduling policy.

| File | Changes |
|------|---------|
| `SyncAccountWorker.java` (maplibui) | Unique per-account WorkManager job; requests sync and reschedules itself after success |
| `GISApplication.java` (maplibui) | `getAccountSyncTime`, `isSyncable` repair, WorkManager schedule/cancel from `setSyncPeriod`, default-process guard |
| `NGWSettingsFragment.java`, `VectorLayerSettingsActivity.java`, `LayersFragment.java` | Sync toggles use the shared helper, repair `isSyncable`, and schedule/cancel worker jobs |
| `syncadapter.xml` (app) | Platform sync adapter hidden from system sync UI; upload/always-syncable flags disabled |
| `OfflineSyncIntentService.java` (app) | Extra diagnostics for account/layer selection and sync result |

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

### Collector layer «Редактируемый» (display-only policy)

Separate from mobile **`is_editable`** in layer description (edit-mode toggle in the app).

| Concept | JSON / key | Meaning |
|---------|------------|---------|
| Mobile edit toggle | `is_editable` in layer description | User on/off for edit session (unchanged) |
| Collector policy | `collector_editable` in local layer config | From NGW collector item «Редактируемый» at import |

**Import:** [`CollectorResource`](maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorResource.java) parses collector item keys `editable`, `layer_editable`, `is_editable` (item level only). Logcat/HyperLog: `CollectorResource item editable parse…`. Passed via `KEY_COLLECTOR_LAYER_EDITABLE` → `VectorLayer.setCollectorEditable()`.

**When `collector_editable: false`:** no create-object layer list entry; no layer-panel edit menu; no «edit layer» on feature toolbar; view/identify/attributes still work.

**Repair:** `registerCollectorImportBatch(…, boolean[] collectorEditables, …)` preserves flags for re-queue.

**Not synced** from NGW description on soft config update (v1). Re-import collector to refresh.

### Layer fill UI and rasters

| File | Changes |
|------|---------|
| `LayerFillService.java` | Collector extras on intents; `insertLayer` for collector NGW; **`LocalTMSFillTask` + `mIsNgrc`** → insert **above** the `osm` layer (`getChildLayerIndex(osm)+1`), or index 0 if OSM missing; `getDescription()` falls back to `mLayerName` when `mLayer` is null (`UnzipForm`) |
| `LayerFillProgressDialogFragment.java` | Refresh title on `STATUS_START` for multi-layer batches |
| `SelectNGWResourceActivity.java` / `SelectNGWResourceDialog.java` | Full-project `long[]`, `registerCollectorImportBatch(…, collectorEditables, fullOrder)`, `KEY_COLLECTOR_LAYER_EDITABLE` on fill intents |
| `GISApplication.java` | Batch state, repair passes, verify/repair intents with full project order |
| `IGISApplication.java` | Extended `registerCollectorImportBatch`, `notifyCollectorLayerFillResult`, `finalize…`, `clear…` |

### Deferred map reload (batch fill)

| File | Changes |
|------|---------|
| `GISApplication.java` | `requestMapReloadAfterLayerFillBatch()` posts to main; `flushPendingMapReloadAfterLayerFillIfNeeded()` |
| `MapFragment.kt` | Calls flush on `onResume()`; optional startup progress caption when `MAP_STARTUP_UX_EXTRAS_ENABLED` |
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

### Product display names and application IDs

Production flavors **NextGIS ЛИСА** and **NextGIS БЕЛКА** share `applicationId`
**`com.nextgis.mobile.geonical`**. Installing one over the other replaces the brand; they are
not intended to coexist. The official **`com.nextgis.mobile`** application remains independent.

There is one internal variant, **`lisaDebug`**, displayed as **ЛИСА тест** with the preserved
`applicationId` **`com.nextgis.mobile.debug`**. `belkaDebug` is disabled by the Android Components
variant filter.

| File | Notes |
|------|--------|
| `app/build.gradle` | Production `.geonical` suffix, internal `.debug` suffix, brand names, isolated authorities/account types, and `belkaDebug` filtering. |
| `app/src/lisa/res`, `app/src/belka/res` | Lisa density-specific branded launcher icon; Belka keeps the upstream icon until its asset is supplied. |
| `app/src/main/res/values/strings.xml` | Жёсткий **`app_name`** убран; строки задаются flavor’ами. |
| `maplibui/src/main/res/values/strings.xml` | Fallback **`app_name`** для сборки библиотеки; в итоговом APK подменяется значением из **`app`**. |
| `NGActivity.java` (maplibui) | **`getAppName()`** по-прежнему через **`ApplicationInfo.loadLabel(PackageManager)`** — совпадает с лейблом установленного варианта. |

**Сборка:** `./gradlew :app:assembleRelease` собирает оба production APK. Для внутренней
сборки используется `./gradlew :app:assembleLisaDebug`; alias `assembleDebug` указывает на неё.
Разрешены только `lisaDebug`, `lisaRelease`, `belkaRelease`.

The Lisa flavor contains density-specific `ic_launcher_lisa.png` resources generated from the
approved branding ICO. Belka intentionally keeps the upstream launcher icon for now.

Production APKs are signed by the permanent Geonical keystore stored outside the repository.
The user-level `~/.gradle/gradle.properties` supplies `GEONICAL_STORE_FILE`,
`GEONICAL_STORE_PASSWORD`, `GEONICAL_KEY_ALIAS`, and `GEONICAL_KEY_PASSWORD`. A partial
configuration fails early; with no Geonical properties, local release builds remain unsigned.

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
  - **Патч `3.0.3.3` / `versionCode` 181** — см. [§16 — 3.0.3.3](#3033-versioncode-181).
  - **Патч `3.0.3.2` / `versionCode` 180** — см. [§16 — 3.0.3.2](#3032-versioncode-180).
  - **Патч `3.0.3.4` / `versionCode` 182** — MapLibre max + reliability hardening: [§16 — 3.0.3.4](#3034-versioncode-182).
  - **Патч `3.0.3.5` / `versionCode` 183** — стабильность записи трека и WorkManager-синхронизации:
    [§16 — 3.0.3.5](#3035-versioncode-183).
  - **Патч `3.0.3.6` / `versionCode` 184** — отдельные Geonical/debug application IDs,
    self-hosted APK updater и flavor-защита: [§16 — 3.0.3.6](#3036-versioncode-184).
  - **Патч `3.0.3.7` / `versionCode` 185** — тихая автоматическая проверка обновлений
    при запуске и увеличенные сетевые тайм-ауты: [§16 — 3.0.3.7](#3037-versioncode-185).
  - **Патч `3.0.3.8` / `versionCode` 186** — приглушённая оранжевая палитра production-сборок:
    [§16 — 3.0.3.8](#3038-versioncode-186).
  - **Патч `3.0.3.9` / `versionCode` 187** — удаление унаследованного Google Analytics и его
    обработчика падений: [§16 — 3.0.3.9](#3039-versioncode-187).

---

## 16. Fork patch releases (GeonicalSystem)

Трекинг версий форка относительно апстрима (`versionName` / `versionCode` в [`app/build.gradle`](app/build.gradle); у модуля **`maplib`** выравнивается `versionName` в [`maplib/build.gradle`](maplib/build.gradle) для `BuildConfig`).

### 3.0.3.9 (`versionCode` 187)

- **Приватность:** из debug и production удалены SDK Google Analytics, унаследованный tracking ID,
  автоматический GA-обработчик падений и настройка отправки статистики.
- **Логи:** HyperLog продолжает сохранять диагностические логи локально; Sentry остаётся отдельным
  каналом и будет подключён только к контролируемому Geonical endpoint.

### 3.0.3.8 (`versionCode` 186)

- **Production-тема:** `lisaRelease` и `belkaRelease` используют приглушённую оранжевую палитру
  (`primary #B65F2E`, `primary_dark #7A3518`, `accent #C97B45`).
- **Debug-тема:** `lisaDebug` сохраняет зелёную палитру и не затрагивается production-настройкой.

### 3.0.3.7 (`versionCode` 185)

- **Автопроверка:** один тихий запрос манифеста при каждом свежем запуске главного экрана,
  только при подтверждённом Android интернет-соединении. Ошибки и отсутствие обновления не
  мешают запуску; найденное обновление показывается в отменяемом диалоге.
- **Жизненный цикл:** проверка ждёт фокуса окна и не повторяется при пересоздании Activity,
  возврате из настроек или установщика.
- **Сеть:** подключение к репозиторию ждёт до 30 секунд, а скачивание допускает до пяти минут
  без поступления очередных данных и не ограничено по общей продолжительности.

### 3.0.3.6 (`versionCode` 184)

- **Идентичность:** production ЛИСА/БЕЛКА используют `com.nextgis.mobile.geonical`,
  официальный `com.nextgis.mobile` не затрагивается; внутренний `com.nextgis.mobile.debug`
  сохранён для обновления уже установленных тестовых сборок.
- **Варианты:** разрешены `lisaDebug`, `lisaRelease`, `belkaRelease`; debug называется
  «ЛИСА тест», а `belkaDebug` отключён.
- **Обновления:** ручная HTTPS-проверка, загрузка, SHA-256/package/version/certificate
  валидация и запуск системного установщика.
- **Flavor-защита:** APK содержит подписанную метку `debug`/`lisa`/`belka`; публикатор и
  клиент отклоняют cross-flavor обновления.
- **Брендинг:** отдельные flavor-ресурсы `app_launcher_icon` для будущих иконок ЛИСЫ и БЕЛКИ.

### 3.0.3.5 (`versionCode` 183)

- **Запись трека:** перезапуск записи после crash/stale незавершённого трека теперь закрывает открытые строки треков напрямую и запускает ровно один foreground `TrackerService`, без гонки `ACTION_STOP` + `startForegroundService`.
- **Процесс трекера:** `GISApplication` не планирует WorkManager-синхронизацию и отложенный `resetSyncTime()` в служебных процессах (`:tracks` и похожих), поэтому старт записи трека не падает через две секунды из-за `WorkManager is not initialized properly`.
- **Синхронизация NGW:** добавлен `SyncAccountWorker` — уникальный WorkManager-job на учётку, который вызывает `requestSync` и сам перепланируется; UI-переключатели и создание аккаунта явно чинят `isSyncable`.
- **Диагностика:** sync-пути логируют выбор аккаунтов/слоёв, состояние `requestSync` и результат синка через `SSYNC` / HyperLog.

### 3.0.3.4 (`versionCode` 182)

Крупный цикл MapLibre-рендеринга + усиление стабильности. Технические таблицы:
[§3 — iteration changelog](#iteration-changelog-2026-06--maplibre-max--stability),
[§10.11](#1011-reliability-hardening-pass-crash-diagnosability-maplibre-nulls-fgs-sync),
[§18 — district filter](#18-ngw-district-filter-collector-project).

#### Что заметит пользователь

- **Стиль слоя на карте:** подписи с ореолом, масштабирование по зуму, коллизии; прозрачность
  заливки/обводки; типы линий (сплошная, пунктир, с обводкой, пунктир с обводкой); cap/join;
  маркеры-иконки (SymbolLayer); штриховки полигонов; rule-style ближе к Web GIS
  (`NgwLayerConfigAdapter`).
- **Настройки слоя (UI):** расширенный `StyleFragment` — подписи, opacity, line cap/join, dash,
  шаблон `${поле}`, rule-style parity; общие настройки слоя — opacity слоя
  (`LayerGeneralSettingsFragment`).
- **Смена стиля без полной перезагрузки:** hot-reload (paint-only / props refresh) — см. [§3 F2](#hot-reload-f2).
- **Коллектор, display-only слои:** кнопки «Редактировать слой/объект» скрыты, если при импорте
  слой помечен нередактируемым (`collector_editable: false`); просмотр атрибутов и идентификация
  работают — см. [§13](#13-collector-import-verification-layer-ordering-and-sync-timestamp).
  `MapFragment`: отдельное меню `select_action_view.xml`; `LayersFragment` — toast при попытке
  редактирования нередактируемого слоя.
- **Импорт коллектора:** стабильнее — один foreground-service на весь batch (без
  `ForegroundServiceDidNotStopInTimeException`); при ошибке регистрации batch импорт не
  продолжается «вслепую».
- **Синхронизация:** сбой pull с сервера не считается успехом (локальные правки не затираются);
  спиннер синка не зависает при исключении в maplib `SyncAdapter`; битый renderer в старых
  настройках слоя → fallback на дефолтный стиль вместо пропажи слоя.
- **Крэши / диагностика:** меньше NPE при загрузке слоёв MapLibre; экспорт HyperLog содержит
  полный stack trace; phase breadcrumb перед крэшем.

#### Технические детали (кратко)

| Блок | Суть |
|------|------|
| MapLibre рендеринг | Фазы 0–2, рефакторинг `*LayerFactory`, schema 3 geom/style cache, `MplStyleMapper`, `LabelAttributes` |
| MapLibre NPE family | `setFilter`, `lineDasharray`, `fillPattern`, pattern `coalesce`; `postMainGuarded`/`runGuarded` |
| Disk cache | `VECTOR_RENDER_DISK_CACHE_ENABLED = false` до on-device регрессии (см. [§3 flags](#vectorlayerrendercache-f1--f4--cold-start)) |
| FGS / batch | `ACTION_ADD_BATCH`, `startFillBatch`, `FOREGROUND_SERVICE_TYPE_DATA_SYNC` |
| Sync | `NgwPullDecision` + test; config hash gating; `fromJSON` renderer fallback |
| District filter | NGW vector/PostGIS subset по `resmeta.items.district` — [§18](#18-ngw-district-filter-collector-project) |
| First load | `MapFragment.onMapReady` null-safe; битый `.ngm` логируется в `GISApplication.getMap()` |
| Tests | `maplib/src/test/java/` — `NgwPullDecisionTest`, district/NGW URL helpers |

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

### 3.0.3.3 (`versionCode` 181)

- **Запись трека (`TrackerService`):** HyperLog lifecycle start/stop/restore/insert и сводка
  счётчиков при stop; идемпотентный `stopTrack(reason)` (без двойного закрытия при
  `ACTION_STOP` + `onDestroy`); `ContextCompat.startForegroundService` и ранний
  `startForeground`; provider gate как у walk (трековые **или** общие настройки location);
  `flushRemaining()` + closing snap последней raw-точки; broadcast `trackpoint` через
  `MESSAGE_INTENT_TRACK` / `VALUE_TRACK_POINT`.
- **Отображение текущего трека:** `MapDrawable.reloadCurrentTrackToMap(leadLocation)` —
  GPS lead-preview до текущей позиции только на карте (`track-inprogress-source`), без
  записи в БД; `MapFragment` / `MainActivity.TrackStartStopReceiver` обновляют трек по
  GPS-fix, `trackpoint`, start и stop.
- **Фильтр GPS:** `LocationTrackFilter` пишет причины drop в HyperLog (не только при
  `DEBUG_MODE`).
- **Настройки трека:** дефолт `tracks_location_source` = `3` (GPS + other networks), fallback
  в `LocationUtil` — `3`.

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

---

## 18. NGW district filter (collector project)

**Purpose:** optional per-district data subset for NGW vector/PostGIS layers in collector projects.
District value comes from NGW `resmeta.items.district` (Latin, e.g. `vologda`), stored on the
**LayerGroup** as `collector_district` at collector import — not in per-layer JSON/description.

### Opt-in rule

Filter applies only when **all** are true:

1. parent `LayerGroup` has non-empty `collector_district`;
2. layer type is NGW vector or PostGIS (`NGWResourceTypeVectorLayer` / `NGWResourceTypePostgisLayer`);
3. layer schema contains field `district`.

Otherwise behaviour is unchanged (full feature pull, legacy count-check, no `fld_*` in URL).

### Files

| File | Changes |
|------|---------|
| `NgwResmetaUtil.java` | Read `resmeta.items.{key}` from NGW resource envelope |
| `DistrictFilterUtil.java` | Build `fld_district=...`, resolve opt-in decision |
| `NGWUtil.java` | `getFeaturesUrl(server, id, where)` appends `where` when non-empty |
| `CollectorResource.java` | `getProjectDistrict()` from `resmeta.items.district` |
| `LayerGroup.java` | `collector_district` persist + `findCollectorDistrict(ILayer)` |
| `NGWVectorLayer.java` | `applyDistrictFilterFromProjectGroup()` before fill/pull; skip count-check when active; omit runtime `server_where` from `toJSON` |
| `SelectNGWResourceActivity.java`, `SelectNGWResourceDialog.java` | Set group district on collector import when resmeta present |

### Tests

- JVM unit tests: `maplib/src/test/java/com/nextgis/maplib/util/` (`NgwResmetaUtilTest`,
  `DistrictFilterUtilTest`, `NGWUtilFeaturesUrlTest`).

### Manual regression

- Legacy projects without `collector_district` in group JSON.
- Collector import without `resmeta.items.district`.
- Single-layer NGW import outside collector.
- Reference PostGIS layer without `district` field inside district-enabled project (full load).

---

## 19. Photo attachment coordinate overlay

**Purpose:** stamp WGS84 coordinates (and optional capture time) onto new photo attachments
when saving a feature; write GPS and `DateTimeOriginal` into EXIF on the saved JPEG.

### Settings (Map preferences)

| Key | Default | Description |
|-----|---------|-------------|
| `photo_overlay_enabled` | `false` | Master switch |
| `photo_overlay_use_object_coords` | `false` | Use feature geometry (centroid for lines/polygons) instead of GPS |
| `photo_overlay_show_time` | `false` | Show timestamp on photo and in EXIF |

Coordinate **display format** reuses existing map settings `coordinates_format` and
`coordinates_fraction_digits`.

### Behaviour

- Processing runs in `ModifyAttributesActivity.putAttaches()` for **new** attachments only.
- GPS mode: `GpsEventSource.getLastKnownLocation()` at save time; no coordinates if no fix.
- Object mode: `GeoGeometryUtil.getWgs84RepresentativePoint()` on feature geometry.
- On failure, falls back to raw file copy (attachment not lost).
- Background thread + progress dialog when overlay processing is needed.

### Files

| File | Changes |
|------|---------|
| `preferences_map.xml`, `SettingsConstantsUI.java`, `strings.xml` (en/ru) | Settings UI |
| `GeoGeometryUtil.java` | WGS84 representative point / centroid |
| `GeoGeometryUtilTest.java` | Unit tests |
| `LocationUtil.java` | `locationFromLatLon`, `writeDateTimeToExif`, `setExifOrientationNormal`; uses existing `writeLocationToExif` |
| `PhotoOverlayData.java`, `PhotoOverlayUtil.java` | Bitmap overlay + EXIF pipeline |
| `ModifyAttributesActivity.java` | Integration, async save with progress |

---

## 20. Collector project architecture foundation

**Purpose:** persist enough Collector project and layer-origin metadata during new imports so future
composition sync, form sync, backup-safe rebuilds, multi-project switching, and local vector tile
rendering can be added without re-importing already downloaded heavy vector data.

### Metadata

| JSON block | Stored on | Role |
|------------|-----------|------|
| `collector_project` | `LayerGroup` | Stable identity of imported Collector project: `project_uid`, account, remote id, name, district, composition sync flag, last composition diagnostics |
| `layer_origin` | `NGWVectorLayer` | Marks layer as `collector_project` managed or `manual_ngw`; stores project uid, collector order, form id, render mode |

These fields are intentionally written before the final sync managers exist. Comments in code mark them
as Collector architecture foundation so they are not removed as apparently unused plumbing.

### Behaviour

- New Collector imports store `collector_project` on the target group.
- Each Collector layer fill receives `KEY_COLLECTOR_PROJECT_UID` and persists `layer_origin.managed_by_project=true`.
- Manual NGW layer imports persist `layer_origin.type=manual_ngw` and `managed_by_project=false`.
- `VECTOR_LAYER_WITH_FORM` carries the same origin metadata through the `UnzipForm -> NGW_LAYER` subtask.
- Collector verify/repair and schema-mismatch rebuild preserve layer origin metadata.
- Current projects without these fields are not migrated; future architecture applies to projects imported after this change.

### Planning docs

| File | Purpose |
|------|---------|
| `COLLECTOR_PROJECT_SETUP_GUIDE.md` | Short NGW project setup checklist: Collector project, resource description config, resmeta district, form ids, manual layers |
| `COLLECTOR_ARCHITECTURE_ROADMAP.md` | Roadmap for composition dry-run, backup gateway, form sync, config cleanup, multi-project UI, local vector tiles |

### Files

| File | Changes |
|------|---------|
| `CollectorProjectMetadata.java` | New persistent project identity model |
| `LayerOriginMetadata.java` | New persistent layer ownership/render-mode model |
| `LayerGroup.java` | Serialize/deserialize `collector_project` |
| `NGWVectorLayer.java` | Serialize/deserialize `layer_origin` |
| `IGISApplication.java`, `GISApplication.java` | Keep `collectorProjectUid` through Collector verify/repair and schema rebuild |
| `LayerFillService.java` | Origin extras and persistence after NGW fill, including form subtask path |
| `SelectNGWResourceActivity.java`, `SelectNGWResourceDialog.java` | Write project/layer origin metadata during new Collector/manual NGW imports |

---

## 21. Layer data backups before automatic reload/removal

**Purpose:** protect locally collected editable layer data when sync detects that a layer must be
automatically reloaded, or when future Collector composition sync removes a layer from a project.

### Behaviour

- Schema mismatch sync first tries `sendLocalChanges()`.
- If local changes were sent successfully, the layer can be reloaded quietly.
- If unsent local changes remain, the app creates a data-only backup ZIP and then reloads the layer.
- If backup creation fails, destructive reload/removal is skipped.
- Future Collector composition sync must remove project-managed layers through
  `scheduleCollectorLayerRemovalWithBackup()` so a backup is mandatory even when there are no local
  changes.
- Main overflow menu now has `Share backups` / `Clear backups` next to log sharing actions.

### Backup contents

- `manifest.json` with layer/account/remote id/reason/origin metadata.
- Raw JSON dumps of feature, change, and attachment tables.
- Attachment files from per-feature numeric directories.
- Layer config and ngfp forms are intentionally excluded.

### Files

| File | Changes |
|------|---------|
| `LayerBackupManager.java` | Data-only ZIP creation, sharing bundle, backup cleanup |
| `GISApplication.java` | Backup-aware schema rebuild and Collector-removal foundation hook |
| `IGISApplication.java` | Explicit Collector layer removal hook for future composition sync |
| `MainActivity.kt`, `main.xml`, `strings.xml` | Share/clear backup menu actions |

---

## 22. Collector composition apply sync

**Purpose:** keep locally imported Collector project groups aligned with the live Collector project
composition in NGW during sync while protecting local unsent data.

### Behaviour

- Runs after normal NGW data/config sync for the current account.
- Finds local `LayerGroup` entries with valid `collector_project` metadata and
  `composition_sync=true`.
- Downloads the current NGW Collector project resource and walks nested project items.
- Persists last composition check diagnostics on the project group (`last_composition_check_at`,
  diff summary, incomplete/error flags) for future multi-project UI and easier log correlation.
- Builds a remote snapshot with layer remote id, order, name, Collector editable flag, first form id,
  form payload hash, raw mobile config JSON, and mobile config hash from NGW resource description.
- Compares only local `NGWVectorLayer` entries whose `layer_origin.managed_by_project=true` and
  `layer_origin.project_uid` matches the project.
- Manual NGW layers and layers without `layer_origin` are ignored by composition diff.
- Logs diff counts and entries to HyperLog: `add`, `remove`, `reorder`, `update_form`,
  `update_config`, `update_editable`.
- Applies `add` by enqueueing the normal Collector NGW fill path with origin/order/form/config
  metadata.
- Applies `remove` only through `scheduleCollectorLayerRemovalWithBackup()`, so data-only backup is
  mandatory before deleting a project-managed layer.
- Applies `update_form` by downloading the NGFP payload and replacing only local form sidecars
  (`*_form.json`, `*_ngfp_meta.json`); vector data tables are not rebuilt for form-only changes.
- Applies `reorder` and `update_editable` without deleting data.
- Applies remaining `update_config` diffs from the already downloaded Collector snapshot if the
  normal NGW config pass did not converge the layer before composition check.
- Soft config changes are applied in place (renderer, aliases, visibility, zoom, sync/edit flags,
  additive fields); hard schema/config changes route through the backup-aware Collector refill
  gateway.
- Config hash is advanced only after a match or completed soft update; parse failures and incomplete
  field additions keep the old hash so the next sync retries.
- Stores `last_form_hash` when NGFP forms are unpacked so future sync can detect form-content
  changes even when the form resource id did not change.
- Normalizes NGFP `meta.json` before hash comparison by ignoring transient `ngw_connection`
  details, preventing repeated false `update_form` diffs after a successful form sync.
- Form-only sync also fills missing lookup-table layers referenced by the new form, reusing the
  existing form import lookup handling.
- Preserves unpacked NGFP sidecar files across transient NGW feature-download retries. Without
  this, a first-attempt `HTTP 503` during `NGWVectorLayerFillTask` could recreate the layer storage
  and silently drop `*_form.json` / `*_ngfp_meta.json`.

### Files

| File | Changes |
|------|---------|
| `CollectorProjectCompositionSync.java` | Remote snapshot fetch, local managed-layer snapshot, diff logging, diagnostics persistence, apply orchestration, fallback config apply |
| `CollectorProjectMetadata.java` | Optional last-composition diagnostics stored on `collector_project` |
| `IGISApplication.java`, `GISApplication.java` | App hooks for Collector additions, form-only updates, refills, removals, reorder/editable updates |
| `LayerFormHashUtil.java`, `LayerFillService.java` | Stable normalized NGFP hash calculation, `last_form_hash` persistence, form preservation across transient fill retries |
| `SelectNGWResourceDialog.java` | Passes default form ids during Collector/manual NGW imports |
| `SyncAdapter.java` | Invokes composition apply after normal sync/config pass |

---

## 23. Collector isolated multi-project workspaces

**Purpose:** support multiple imported Collector projects on one device without mixing all project
layers into a single map tree.

### Behaviour

- Each imported Collector project is registered in `collector_projects_registry.json`.
- Each Collector project gets an isolated map workspace under
  `map/collector_projects/collector_<remote_id>_<hash>/map.ngm`.
- During Collector import, the app activates that project's workspace before scheduling
  `LayerFillService` tasks. The project metadata is stored on the workspace root `MapBase`.
- Manual NGW layers imported while that project is active stay inside the same workspace and remain
  `manual_ngw`, so Collector composition sync still ignores them.
- The main overflow menu has `Switch project`; selecting a project saves the current map, writes
  `map_path`, `map_name`, and `active_collector_project_uid`, closes the current `MapDrawable`, and
  recreates `MainActivity`.
- Sync runs only against the currently loaded project workspace, because `SyncAdapter` works from
  the active `MapBase`.
- If a registry exists but no active project preference is set, startup shows the project selector.

### Files

| File | Changes |
|------|---------|
| `CollectorProjectRegistry.java` | Registry, workspace path management, activation, Collector import workspace preparation |
| `SelectNGWResourceActivity.java`, `SelectNGWResourceDialog.java` | Switch Collector imports into the isolated project workspace before fill tasks are queued |
| `MainActivity.kt` | Project switcher dialog, startup selector fallback, map save/close/recreate switching |
| `main.xml`, `strings.xml`, `values-ru/strings.xml` | `Switch project` menu item and labels |
| `SettingsConstants.java` | `active_collector_project_uid` preference key |

---

## 24. Local vector tiles render-mode

**Purpose:** make heavy read-only NGW/Collector polygon layers opt in to local MVT rendering
without changing the classic GeoJSON render path for existing layers.

### Behaviour

- Mobile layer config may declare `mobile_render_mode = local_vector_tiles`.
- `LayerFillService` preserves that value when it writes `layer_origin` for Collector and manual
  NGW imports.
- Config sync treats `render_mode` as a soft config property and stores it in `layer_origin`.
- Unknown/empty render modes normalize to `classic`.
- `Constants.LOCAL_VECTOR_TILES_ENABLED` is currently `true`.
- `LocalVectorTileServer` exposes local `.pbf` tiles on `127.0.0.1` and MapLibre reads them as a
  `VectorSource`.
- `LocalVectorTileProvider` builds MVT tiles lazily from the existing local SQLite layer store using
  the layer spatial query path.
- The first implementation intentionally supports polygon/multipolygon layers only. Other geometry
  types, provider failures, or disabled feature flag are logged and fall back to the classic
  `GeoJsonSource` path.
- Current styling scope is basic fill, outline, opacity, order, and optional text label. Full style
  parity, clipping/simplification, and tile cache are follow-up tasks.

### Files

| File | Changes |
|------|---------|
| `LayerOriginMetadata.java` | Render-mode normalization and factory overloads |
| `LayerConfigUtil.java`, `LayerConfigDiff.java` | Read `mobile_render_mode` / `render_mode` and compare as soft config |
| `VectorLayer.java`, `LayerFillService.java` | Persist render-mode changes without dropping origin metadata |
| `LocalVectorTileRenderMode.java`, `MapDrawable.java`, `Constants.java` | Feature flag, routing, and safe classic fallback hook |
| `LocalVectorTileServer.java`, `LocalVectorTileProvider.java`, `LocalVectorTileEncoder.java` | Loopback HTTP tile endpoint and minimal MVT encoder |
| `MPLFeaturesUtils.java` | MapLibre `VectorSource` and local-vector-tile fill/outline/label style path |

---

## 25. Self-hosted APK updates

**Purpose:** let users automatically or manually check for and install production updates from the
Geonical HTTPS APK repository without an app store.

### Behaviour

- The General settings screen exposes `Check updates` and shows the installed version.
- Each fresh launch of `MainActivity` performs one silent background check when Android reports
  validated internet access. No-update and network-error results stay silent; a newer version opens
  the same cancellable update prompt as a manual check.
- The startup check waits until the activity has window focus, so it does not compete with startup
  permission or project-selection dialogs. Activity recreation does not trigger another check.
- The app reads `https://wiki-geonical.ru/mobile/<flavor>/stable/manifest.json` and compares
  its `versionCode` with the installed build.
- Manifest connections allow 30 seconds. APK downloads allow a five-minute interval without
  receiving data; their total duration is not capped, so slow downloads can take longer.
- Production uses `lisa` / `belka` repositories; the sole internal build uses `debug`.
- A newer APK is downloaded to the app cache with progress feedback and is reused if the
  user must first enable installation from this source in Android settings.
- Before opening the Android package installer, the app verifies APK size and SHA-256,
  package name, version code, and signing-certificate SHA-256 against both the manifest
  and the currently installed application.
- The manifest must point back into the same trusted HTTPS repository.
- Every APK embeds `com.nextgis.mobile.UPDATE_FLAVOR`; both the publisher and the client reject
  a Lisa/Belka flavor mismatch before publication or installation.

### Files

| File | Changes |
|------|---------|
| `AppUpdateManager.java` | Silent/manual checks, connectivity and timeout policy, update prompt, download, integrity/signature checks, installer launch |
| `MainActivity.kt` | One focus-safe automatic update check per fresh application launch |
| `SettingsFragment.java`, `preferences_general.xml` | Manual update-check entry and current-version summary |
| `AndroidManifest.xml`, `provider_paths.xml` | Package-install permission and cache APK sharing through `FileProvider` |
| `AppSettingsConstants.java`, `strings.xml`, `values-ru/strings.xml` | Repository URL, preference key, and localized UI text |
