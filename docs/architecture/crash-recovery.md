---
title: Crash recovery and durable drafts
type: architecture
last_verified: 2026-09-13
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

One background walk owns its complete geometry independently of the foreground
point editor. Normal map menus remain available; `WalkRecordingPanel` owns
Resume, Finish, Show and Discard actions. A second walk cannot start.

| Concern | Behavior |
|---------|----------|
| Durable owner | `WalkSessionStore` in `walkedit_temp` stores session UUID, active map path, complete WKT, target member/ring/insertion, revision, phase and GPS pause state alongside legacy part-only keys |
| Geometry | `WalkGeometrySnapshot` changes only the selected part of a private copy, retaining other members and holes; WKT restoration removes the selected ring's synthetic closing duplicate, including a one-node ring |
| Live preview | Independent `walk-preview-source` shows confirmed geometry without borrowing the point editor. Root CRS is restored on the private copy before conversion from metres to WGS84; full/lite style reload restores the cached preview |
| Point start | A durable point UUID is acquired before the layer chooser. Only one Point/MultiPoint creation session may accompany the recording; stage progresses through choose, geometry and form |
| Complete control lock | From point start until successful point Save or explicit Cancel/Discard, all walk controls are disabled, including Show and notification/backend Resume/Finish/Discard. GPS processing and geometry persistence continue |
| Lifecycle | Camera, backgrounding, screen off, failed Save and process recreation retain the point lock. A GPS gap may automatically pause recording but cannot allow manual reconnection before the point session ends |
| Finish | A matching command changes RECORDING to FINISHING; the service flushes its validated tail, persists full geometry and acknowledges FINISHED before the UI opens manual editing/form. No new point can begin during this transition |
| Final save | The walk draft is retained through validation/form errors and cleared after successful feature Save; cancelling final editing keeps the finished draft available |
| Unexpected end | Service death keeps the private geometry. Sticky recovery with existing vertices pauses insertion; the panel offers explicit Resume/Discard. FINISHING recovers as FINISHED, never as a new recording |
| Owner validation | Commands carry the session UUID and check map identity, phase and point lock. Old notification intents cannot control a later walk |
| Layer/project protection | The active walk and point layers (and containing groups) are reserved against removal/rebuild. Project-changing operations are deferred while a session owns geometry |
| Legacy draft | The previous part-only journal is reconstructed using its owning feature, adopted once into the full-geometry store, and shown through the panel; a live legacy service adopts the UUID without resetting GNSS |

GNSS-only acquisition, gap pause, permission recovery and optional recording
sound follow [the location pipeline](location-pipeline.md). The screen does
not stop the recorder when its view is destroyed. No pending raw location is
used to extend the preview or final geometry.

Key types: `WalkEditService`, `WalkSessionStore`, `WalkSessionPolicy`,
`WalkGeometrySnapshot`, `WalkRecordingPanel`, `MapFragment`, `MapDrawable`.

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
| Contents | Layer/feature ids, geometry WKT, control `saveState` snapshot, pending photo paths, form/meta paths, optional point/walk session UUIDs |
| Write | Session-owned point/final-walk forms get an initial durable checkpoint before Activity launch; `ModifyAttributesActivity.onPause` then persists control/photo state |
| Clear | Successful Save, Discard in form dialog, Discard in recovery hub. Save/Discard marks the Activity terminal before `finish()`, so the following `onPause()` cannot recreate the draft |
| Restore | Recovery hub → `LayerUtil.showEditFormFromDraft` with `apply_form_draft` |
| Validation | A draft for an existing feature is offered only while that feature row still exists; typed control values retain their Bundle type |
| Save result after cold restore | The result carries layer id and whether the row was newly inserted. `MapFragment` resolves the layer from the active map and reloads the persisted feature without assuming that the pre-crash `mSelectedLayer` or temporary MapLibre edit object still exists. When the new id is absent from the process-local GeoJSON list, `MapDrawable` performs a full layer-data reload instead of a style-only refresh, so the object becomes visible without restarting the app. Every successful result then terminates creation/existing-feature editing, clears edit and view selection, and returns the standard `MODE_NORMAL` map UI |

No layer insert until the user explicitly Saves.

## Recovery hub (`MainActivity.maybeOfferCrashRecovery`)

Order after map resume:

1. Track auto-start if its durable recording flag is enabled.
2. An owned point/final-walk form checkpoint takes precedence over a duplicate geometry handoff checkpoint.
3. Legacy interrupted walk reconstruction, then manual geometry/form recovery as applicable.
4. A point owner without a recoverable geometry/form offers explicit Continue/Discard; chooser recreation does not silently release the lock.

The independent walk is displayed by its panel and passive map source. A point
draft can coexist with that walk; two editors for the same transferred sketch
are not reopened. Session UUID matching prevents a recovered old form from
unlocking or deleting a newer walk.

Recovery decisions and journal lifecycle are written to HyperLog with the
`CrashRecovery`, `GeometryDraft`, `FormDraft`, and `MapFragment mode` prefixes.
Geometry coordinates, field values, photo paths, and credentials are not logged.

## Out of scope

- Dialog for unfinished tracks
- Mandatory same track id after crash
- Cloud sync of drafts / restore from LayerBackup ZIP
- Persisting the complete manual-geometry undo/redo history (the latest geometry is persisted)
