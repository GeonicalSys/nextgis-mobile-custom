---
title: Crash recovery and durable drafts
type: architecture
last_verified: 2026-08-22
related_code:
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
| Point durability | Each accepted GPS fix is inserted into `trackpoints` immediately |
| GPS validation | Shared `LocationTrackFilter` retains valid movement through 160 km/h, rejects invalid/old/inaccurate fixes and isolated material spikes, and drains its delayed two-fix buffer on stop or before a long-gap segment reset |
| Provider ownership | Track uses `tracks_location_source`; ordinary location and walk use `location_source`. Enabling a provider for the map cannot silently enable it for track recording |
| Provider arbitration | If both sources are enabled, network is available before GPS and during GPS outages, but is suppressed for 12 seconds after each usable GPS fix to avoid mixed-provider jumps |
| Recording flag | Durable preference `track_recording_enabled` is set on start and cleared **only** by the menu action «Stop track» / «Завершить запись трека» |
| After reboot / cold start | `BootLoader` and `MainActivity` call `TrackerService.ensureRecordingRunningIfEnabled()` — silent auto-start, no dialog |
| Continuity of track id | Not required. Closing unfinished tracks and starting a new id after a crash is allowed; previous points remain in SQLite / on the map |
| Forbidden stop | Reboot, process death, and legacy `track_restore=false` must not stop recording while the durable flag is set |

Key types: `TrackerService`, `BootLoader`, `MainActivity`.

The filter validates distance from the newest accepted fix, even while that fix
is waiting in the chord buffer. This prevents a single rejection from anchoring
all later vehicle fixes to an increasingly old point. A sampling gap above
30 seconds starts a new validation segment only after the previous buffer has
been emitted; user intervals of 45 seconds or more therefore do not erase data.
HyperLog includes provider plus aggregate input/passed/dropped/chord/gap counts,
the count of network fixes suppressed by recent GPS, but never coordinates.

## Walk digitizing (line / polygon by walk)

| Concern | Behavior |
|---------|----------|
| Draft store | SharedPreferences `walkedit_temp` (layer/feature ids, WKT, geometry/ring indices, next insertion index, timestamp) |
| Explicit stop | Save edits / Cancel → `WalkEditService.stopAndClearDraft()` → draft cleared |
| Unexpected stop | FGS kill, crash, permission stop → draft kept; HyperLog `unexpected walk end` |
| Soft-interrupt | While UI is in walk mode (or draft exists) and service is not running → Continue/Discard dialog |
| Cold start | Recovery hub in `MainActivity` offers the same dialog even if Android already restarted the `START_STICKY` service; the service is paused while the user decides |
| UI ownership | A cold draft is never restored silently by `MapFragment`; silent restore is reserved for configuration recreation of an already attached walk UI |
| Cold MapLibre overlay | Continue reconstructs one property-bearing edit feature on the current style, restores polygon fill and outline from the same source, and extracts the vertex cache before hiding it for the active walk; Stop republishes those vertices for ordinary editing |
| GPS pipeline | Walk uses the same 160 km/h-capable filter and GPS-first/network-fallback arbitration as tracks, but reads ordinary `location_source` and `location_min_time` / `location_min_distance` settings |

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
| Cold MapLibre | Continue is retained through a bounded retry until editable MapLibre sources are ready; timeout keeps the draft for the next launch |

The latest geometry is durable, including the visible line in the reported
“draw line → swipe app away” case. The transient undo/redo stack itself is not
serialized. Polygon conversion explicitly closes every non-empty outer/inner
GeoJSON ring before MapLibre vertex extraction; a restored manual Polygon or
MultiPolygon therefore shows the same fill and node order before and after a
node is moved. WKT recovery identifies rings by parenthesis depth, preserving
one outer ring and only actual holes instead of duplicating the outer ring as a
hole that cancels the fill.

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
| Save result after cold restore | The result carries layer id and whether the row was newly inserted. `MapFragment` resolves the layer from the active map and reloads the persisted feature without assuming that the pre-crash `mSelectedLayer` or temporary MapLibre edit object still exists. When the new id is absent from the process-local GeoJSON list, `MapDrawable` performs a full layer-data reload instead of a style-only refresh, so the object becomes visible without restarting the app |

No layer insert until the user explicitly Saves.

## Recovery hub (`MainActivity.maybeOfferCrashRecovery`)

Order after map resume:

1. Track: auto-start if `track_recording_enabled` (already handled; no dialog)
2. Walk draft interrupted → Continue / Discard
3. Manual geometry draft → Continue / Discard
4. Form draft → Continue / Discard

Recovery decisions and journal lifecycle are written to HyperLog with the
`CrashRecovery`, `GeometryDraft`, `FormDraft`, and `MapFragment mode` prefixes.
Geometry coordinates, field values, photo paths, and credentials are not logged.

## Out of scope

- Dialog for unfinished tracks
- Mandatory same track id after crash
- Cloud sync of drafts / restore from LayerBackup ZIP
- Persisting the complete manual-geometry undo/redo history (the latest geometry is persisted)
