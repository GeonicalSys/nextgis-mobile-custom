---
title: Crash recovery and durable drafts
type: architecture
last_verified: 2026-09-12
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/WalkEditService.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/ModifyAttributesActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FeatureFormDraftStore.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/GeometryEditDraftStore.java
  - maplib/src/main/java/com/nextgis/maplib/util/LocationProviderArbiter.java
  - maplib/src/main/java/com/nextgis/maplib/util/LocationTrackFilter.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
---

# Crash recovery and durable drafts

This document describes how the app protects unfinished user work across process
death, Force Stop, and reboot.

## Invariant

`INV-CRASH-DRAFT-RECOVERY`: track recording auto-resumes without a dialog; walk,
manual geometry, and attribute-form drafts survive unexpected stops and are
offered via Continue/Discard; track points already written to SQLite must not be
lost.

## Track recording

| Concern | Behavior |
|---------|----------|
| Point durability | Each sampled, validated GNSS point is inserted immediately; the bounded filter and sampling tail is flushed before explicit Save |
| GPS validation | Shared `LocationTrackFilter` retains valid movement through 160 km/h, rejects invalid/old/inaccurate fixes and isolated material spikes, and drains its delayed two-fix buffer on stop or before a long-gap segment reset |
| Provider ownership | Application-owned GpsEventSource; GPS/Network for display, GNSS only for track/walk recording |
| GPS gaps | Database v6 persists trackpoints.segment; gaps survive map reload and GPX export. Walk stores gps_paused and requires explicit reconnection |
| Recording flag | Durable preference `track_recording_enabled` is set on start and cleared **only** by the menu action «Stop track» / «Завершить запись трека» |
| Process ordering | `TrackerService` runs in the default application process. The toolbar Start lifecycle therefore executes before a later toolbar Stop, and both sides observe one in-process preference state; a stale delayed Start is rejected if the durable flag is already off |
| After reboot / cold start | `BootLoader` and `MainActivity` call `TrackerService.ensureRecordingRunningIfEnabled()` — silent auto-start, no dialog |
| Continuity of track id | Not required. Closing unfinished tracks and starting a new id after a crash is allowed; previous points remain in SQLite / on the map |
| Forbidden stop | Reboot, process death, and legacy `track_restore=false` must not stop recording while the durable flag is set |
| Background sound | With `background_recording_sound=true`, the shared validated GNSS stream before decimation distinguishes stationary coordinates from missing delivery. While the UI is hidden/screen off and usable fixes remain fresh, the GPS session owns a partial wake lock independently of the sound setting and keeps a short alarm-stream heartbeat on a fixed 10-second cadence independent of point inserts. Notification volume does not suppress it; if the alarm stream is muted or has zero volume, a short vibration replaces the heartbeat. An observed persistence failure uses a distinct tone or double vibration at most once per minute. Missing fresh fixes, a killed process or revoked permission silence the feedback and remain the user-visible warning |
| Permission revoked | If Android removes coarse/fine location while recording, a sticky restart must not call `startForeground()` for the forbidden location FGS. The service stops with `START_NOT_STICKY`, retains `track_recording_enabled`, and can resume after permission returns without crashing the app |

Key types: `TrackerService`, `BootLoader`, `MainActivity`.

The source uses monotonic measurement age, retains historical live batches only within
recorder lifetime, and flushes the pre-gap buffer before notifying services. Acquisition
remains frequent even when saved points are sparse. See [location pipeline](location-pipeline.md)
for filter thresholds, display freshness, database migration and field verification.

## Walk digitizing (line / polygon by walk)

| Concern | Behavior |
|---------|----------|
| Draft store | SharedPreferences `walkedit_temp` (layer/feature ids, WKT, geometry/ring indices, next insertion index, gps_paused, timestamp) |
| Explicit stop | Save edits / Cancel → `WalkEditService.stopAndClearDraft()` → draft cleared |
| Unexpected stop | FGS kill, crash, permission stop → draft kept; HyperLog `unexpected walk end` |
| Soft-interrupt | While UI is in walk mode (or draft exists) and service is not running → Continue/Discard dialog |
| Cold start | Recovery hub in `MainActivity` offers the same dialog even if Android already restarted the `START_STICKY` service; the service is paused while the user decides |
| UI ownership | A cold draft is never restored silently by `MapFragment`; silent restore is reserved for configuration recreation of an already attached walk UI |
| Cold MapLibre overlay | Continue can be pressed before style loading finishes. It keeps renderer attachment pending until the current style owns `selected-poly-source`, `selected-dot-source` and `vertex-source`, then reconstructs one property-bearing edit feature, restores polygon fill and outline only for polygon layer types, explicitly removes fill for line types, and extracts the vertex cache before hiding it for the active walk; Stop republishes those vertices for ordinary editing |
| Draft exclusivity | Entering or restoring walk mode clears `geometry_edit_draft`; one sketch cannot be offered both as walk and normal geometry recovery |
| Finish action | The right action in the active-walk bottom bar uses the walking-person recording icon and invokes the existing Save/Stop transition instead of opening location settings |
| GPS pipeline | Shared GNSS-only filter; location_min_time / location_min_distance control point decimation, not acquisition. GPS loss or draft recovery pauses insertion until explicit Continue and connect |
| Background sound | The same enabled-by-default fixed 10-second alarm-stream heartbeat is driven by fresh usable health callbacks rather than WKT changes, so it continues while stationary and stops when coordinates become stale; zero alarm volume falls back to vibration, and commit failure uses the distinct throttled tone/double-vibration signal |
| Permission revoked | Missing/revoked location permission or a `startForeground()` race stops the service without a location FGS and keeps `walkedit_temp` for Continue/Discard |

Key types: `WalkEditService`, `EditLayerOverlay.stopGeometryByWalk`, `MapFragment` watchdog / resume helpers.

## Manual geometry editing (vertices / taps)

| Concern | Behavior |
|---------|----------|
| Draft store | `GeometryEditDraftStore` (`geometry_edit_draft` prefs, JSON: active map path, layer/feature ids, edit mode, latest WKT, timestamp) |
| Write | Synchronous after MapLibre geometry callbacks, tap insertion, undo/redo, vertex `panStop`, and again in `MapFragment.onPause`; coordinates are not copied to HyperLog |
| Existing object | Continue reloads its attributes from SQLite and replaces only geometry with the draft |
| New object | Continue recreates feature id `-1`, restores the latest geometry and returns to `MODE_EDIT`; legacy mode `5` drafts migrate to this mode |
| Clear | Explicit geometry Cancel, successful existing-feature update, or successful handoff of a new geometry to the attribute form |
| Validation | Active map path, vector layer, edit policy, feature existence and geometry type must match; this prevents cross-project `layer_id` collisions |
| Cold MapLibre | Continue is retained through a bounded retry until all editable source objects belong to the current MapLibre style; a non-null style alone is insufficient, and timeout keeps the draft for the next launch |

The latest geometry is durable, including the visible line in the reported
“draw line → swipe app away” case. The transient undo/redo stack itself is not
serialized. Polygon conversion explicitly closes every non-empty outer/inner
GeoJSON ring before MapLibre vertex extraction; a restored manual Polygon or
MultiPolygon therefore shows the same fill and node order before and after a
node is moved. WKT recovery identifies Polygon rings and MultiPolygon members by
parenthesis depth, preserving every polygon part, one outer ring per part and
only actual holes instead of duplicating or truncating rings in a way that
cancels the fill.

The insertion-direction overlay is derived again from the restored selected
vertex and geometry structure: it is not separate draft state. The following
vertex therefore stays inside the restored line part or polygon ring, including
last-to-first wrapping for a closed ring.

Key types: `GeometryEditDraftStore`, `MapFragment.persistManualGeometryDraft`,
`MapFragment.resumeManualGeometryFromDraft`.

## Attribute form

| Concern | Behavior |
|---------|----------|
| Draft store | `FeatureFormDraftStore` (`feature_form_draft` prefs, JSON) |
| Contents | Layer/feature ids, geometry WKT, control `saveState` snapshot, pending photo paths, form/meta paths |
| Write | `ModifyAttributesActivity.onPause` when `hasEdits()` |
| Clear | Successful Save, Discard in form dialog, Discard in recovery hub. Save/Discard marks the Activity terminal before `finish()`, so the following `onPause()` cannot recreate the draft |
| Restore | Recovery hub → `LayerUtil.showEditFormFromDraft` with `apply_form_draft` |
| Validation | A draft for an existing feature is offered only while that feature row still exists; typed control values retain their Bundle type |
| Save result after cold restore | The result carries layer id and whether the row was newly inserted. `MapFragment` resolves the layer from the active map and reloads the persisted feature without assuming that the pre-crash `mSelectedLayer` or temporary MapLibre edit object still exists. When the new id is absent from the process-local GeoJSON list, `MapDrawable` performs a full layer-data reload instead of a style-only refresh, so the object becomes visible without restarting the app. Every successful result then terminates creation/existing-feature editing, clears edit and view selection, and returns the standard `MODE_NORMAL` map UI |

No layer insert until the user explicitly Saves.

## Recovery hub (`MainActivity.maybeOfferCrashRecovery`)

Order after map resume:

1. Track: auto-start if `track_recording_enabled` (already handled; no dialog)
2. Walk draft interrupted → Continue / Discard
3. Manual geometry draft → Continue / Discard
4. Form draft → Continue / Discard

Walk and manual geometry are mutually exclusive owners of one geometry editing
session. Starting/restoring walk removes an older non-walk geometry journal, so
steps 2 and 3 cannot serially reopen the same sketch in different modes.

Recovery decisions and journal lifecycle are written to HyperLog with the
`CrashRecovery`, `GeometryDraft`, `FormDraft`, and `MapFragment mode` prefixes.
Geometry coordinates, field values, photo paths, and credentials are not logged.

## Out of scope

- Dialog for unfinished tracks
- Mandatory same track id after crash
- Cloud sync of drafts / restore from LayerBackup ZIP
- Persisting the complete manual-geometry undo/redo history (the latest geometry is persisted)
