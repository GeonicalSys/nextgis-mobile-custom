---
title: Crash recovery and durable drafts
type: architecture
last_verified: 2026-10-09
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

Explicit [feature type defaults](feature-type-creation.md) survive in the optional
typed initial_values payload of the geometry v1 journal. Geometry-to-form launch
checkpoints these fields and stable cascade keys before starting the Activity;
recovered control state takes precedence over the initial category.

Разворачивание каскадного `double_combobox` в отдельные поля меняет только
отображение. Saved-state keys и pinned cascade definition прежние; старые
черновики сохраняют выбор по именам полей. Свайп отменяет касание исходного
контрола до смены вкладки, не меняя флажок или введённый комментарий.

Проверка обязательных полей выполняется после checkpoint формы и до изменения
строки/вложений. Незавершённый обязательный атрибут не запрещает durable draft,
ротацию или восстановление фотографии/геометрии. После исправления Save
использует прежний owner/operation UUID и штатное завершение; Save/Discard
по-прежнему не позволяют onPause() восстановить уже удалённый черновик.
Точные правила заполнения — в
[NGW sync and storage](ngw-sync-and-storage.md#обязательность-полей).

This document describes how the app protects unfinished user work across process
death, Force Stop, and reboot.

## Invariant

`INV-CRASH-DRAFT-RECOVERY`: track recording auto-resumes without a dialog; walk,
manual geometry, and attribute-form drafts survive unexpected stops and are
offered via Continue/Discard; track points already written to SQLite must not be
lost.

## Track recording

Battery Saver restrictions do not mean the service died: screen-off GPS delivery
can stop with a live foreground service and wake lock. The power warning is
recomputed on service start and Activity Resume, never persisted as recording
intent or used to close a track. Existing segment and durable Stop rules apply.

| Concern | Behavior |
|---------|----------|
| Recording mode | `track_recording_mode` is checkpointed before permissions/start and retained across process death, reboot and split. Missing/unknown values retain mixed recording. Pedestrian speed rejection occurs before sampling/queueing, closes the accepted tail and resumes in a new persisted segment; Stop cannot flush rejected vehicle fixes. See location pipeline for threshold and fallback. |
| Point durability | Each sampled, validated GNSS point is queued in owning-map AtomicFile storage before a serial idempotent SQLite insert; filter/sampling tails are flushed before Stop. Pending files are acknowledged only after database commit |
| GPS validation | Shared `LocationTrackFilter` retains valid movement through 160 km/h, rejects invalid/old/inaccurate fixes and isolated material spikes, and drains its delayed two-fix buffer on stop or before a long-gap segment reset |
| Provider ownership | Application-owned GpsEventSource; GPS (chip, mock or native NMEA) for display with Network only as GPS-absent fallback; GNSS chip, mock receiver extras or native NMEA for track/walk recording |
| GPS gaps | Database v6 persists trackpoints.segment; gaps survive map reload and GPX export. Walk stores gps_paused and requires explicit reconnection |
| Recording flag | Durable preference `track_recording_enabled` is intent, not proof of an active service or track row. The menu and the walking-person button under the ruler distinguish Start, Starting, Recording and Error; neither shows a false active state before service confirmation. Explicit Stop persists pending_stop and clears recording intent only after all queued points and track closure are committed. A foreground/SQLite failure keeps the queue and warns without deleting unfinished data. |
| System location | Interactive Start is rejected before changing durable intent when Android system location is off. The app explains the requirement and opens Location settings; the service repeats the check to close lifecycle races. |
| Process ordering | `TrackerService` runs in the default application process. The toolbar Start lifecycle therefore executes before a later toolbar Stop, and both sides observe one in-process preference state; a stale delayed Start is rejected if the durable flag is already off |
| After reboot / cold start | `BootLoader` and `MainActivity` call `TrackerService.ensureRecordingRunningIfEnabled()` — silent auto-start, no dialog |
| Continuity of track id | Not required. Closing unfinished tracks and starting a new id after a crash is allowed; previous points remain in SQLite / on the map |
| Forbidden stop | Reboot, process death, and legacy `track_restore=false` must not stop recording while the durable flag is set |
| External receiver state | GATT subscription/write failure or Bluetooth permission revocation closes the transport without a callback crash. Late callbacks cannot affect a replacement session. Quality expires after eight seconds of silence and cannot retain an old FIX/FLOAT/Auto status |
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
Resume, Finish/Save and confirmed Cancel actions without an overflow menu.
The compact panel omits GPS accuracy and sits at the bottom of the map,
moving above the creation menu only while it is expanded. A second walk cannot start.

| Concern | Behavior |
|---------|----------|
| Durable owner | `WalkSessionStore` in `walkedit_temp` stores session UUID, active map path, complete WKT, target member/ring/insertion, revision, phase and GPS pause state alongside legacy part-only keys |
| Geometry | `WalkGeometrySnapshot` changes only the selected part of a private copy, retaining other members and holes; WKT restoration removes the selected ring's synthetic closing duplicate, including a one-node ring |
| Live preview | Independent `walk-preview-source` shows confirmed geometry without borrowing the point editor. Root CRS is restored on the private copy before conversion from metres to WGS84; full/lite style reload restores the cached preview |
| Point start | A durable point UUID is acquired before the layer chooser. Only one Point/MultiPoint creation session may accompany the recording; stage progresses through choose, geometry and form |
| Complete control lock | From point start until successful point Save or explicit Cancel/Discard, all walk controls and notification/backend Pause/Resume/Finish/Discard are disabled. GPS processing and geometry persistence continue |
| Lifecycle | Camera, backgrounding, screen off, failed Save and process recreation retain the point lock. A GPS gap may automatically pause recording but cannot allow manual reconnection before the point session ends |
| Finish | The disk button confirms finishing and opening attributes. A matching command changes RECORDING to FINISHING; the service flushes its validated tail, persists full geometry and acknowledges FINISHED before the UI hands geometry to the editor and automatically opens its attribute form. No new point can begin during this transition |
| Manual pause | The confirmed Pause command flushes already accepted fixes and persists gps_paused under the same owner. New recording callbacks cannot append while paused. Confirmed Resume resets the sampler and warns that the next point connects to the last recorded point. A stale confirmation still checks UUID, phase and point lock in the service |
| Panel | One themed 48dp row contains layer name and disk/pause-or-play/cross, all with 48dp touch targets, accessibility names and long-press hints. No ordinary status/accuracy label or overflow. Point lock and persistence errors add a readable notice; final geometry keeps Save/Cancel available and disables Pause |
| Minimum geometry | After FINISHED, every line requires two distinct vertices and every polygon ring three, excluding WKT closure duplicates. Empty collections or short members/rings close the walk with “Собрано недостаточно точек, выхожу без сохранения”, without a feature row or attribute form; existing features are unchanged |
| Final save | The active Save panel button and toolbar Save use the same geometry validation/repair and open attributes for new or existing walk features. An unrelated editor or point owner still locks controls. The walk draft is retained through validation/form errors and cleared after successful feature Save; cancelling final editing keeps the finished draft available |
| Unexpected end | Service death keeps the private geometry. Sticky recovery with existing vertices pauses insertion; the panel offers explicit Resume/Discard. FINISHING recovers as FINISHED, never as a new recording |
| Startup reconciliation | A RECORDING owner at initial revision with no service, point lock or persisted snapshot is an unacknowledged start and is removed silently. A stopped owner with a real snapshot is offered Continue/Discard. A session belonging to another map is never hidden: startup offers an emergency reset. |
| Emergency reset | General settings expose a confirmed reset that releases a point lock, stops the matching service and removes only `walkedit_temp` plus a walk-owned form checkpoint. Projects, layers, features, accounts and unrelated drafts are untouched. |
| Owner validation | Commands carry the session UUID and check map identity, phase and point lock. Old notification intents cannot control a later walk |
| Layer/project protection | The active walk and point layers (and containing groups) are reserved against removal/rebuild. Project-changing operations are deferred while a session owns geometry |
| Legacy draft | The previous part-only journal is reconstructed using its owning feature, adopted once into the full-geometry store, and shown through the panel; a live legacy service adopts the UUID without resetting GNSS |

GNSS-only acquisition, gap pause, permission recovery and optional recording
sound follow [the location pipeline](location-pipeline.md). The screen does
not stop the recorder when its view is destroyed. No pending raw location is
used to extend the preview or final geometry.

Key types: `WalkEditService`, `WalkSessionStore`, `WalkSessionPolicy`,
`WalkSessionRecoveryPolicy`,
`WalkGeometrySnapshot`, `WalkRecordingPanel`, `MapFragment`, `MapDrawable`.

Phone, landscape and tablet resources include the same map content and mandatory
walk panel. `MapFragment` also listens directly to `WALKEDIT_CHANGE` while resumed
and reloads the current-map snapshot on resume, so preview does not depend on the
panel callback. Reflowing tools cannot cover walk controls. This UI repair neither
resets nor migrates recorded geometry: an existing current-map session is shown
from the same durable store after updating the application.

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
| Contents | Map path, operation UUID, layer/feature ids, geometry WKT, typed control state including signature strokes, pending photo paths, form/meta paths and optional point/walk session UUIDs |
| Write | Session-owned point/final-walk forms checkpoint before launch; all editable forms checkpoint every three seconds, onPause, before Save and after assigning feature id. Unchanged snapshots avoid another disk write |
| Clear | Successful Save, Discard in form dialog, Discard in recovery hub. Save/Discard marks the Activity terminal before `finish()`, so the following `onPause()` cannot recreate the draft |
| Restore | Recovery hub → `LayerUtil.showEditFormFromDraft` with `apply_form_draft` |
| Validation | A draft for an existing feature is offered only while that feature row still exists; typed control values retain their Bundle type |
| Save result after cold restore | The result carries layer id and whether the row was newly inserted. `MapFragment` resolves the layer from the active map and reloads the persisted feature without assuming that the pre-crash `mSelectedLayer` or temporary MapLibre edit object still exists. When the new id is absent from the process-local GeoJSON list, `MapDrawable` performs a full layer-data reload instead of a style-only refresh, so the object becomes visible without restarting the app. Every successful result then terminates creation/existing-feature editing, clears edit and view selection, and returns the standard `MODE_NORMAL` map UI |

No layer insert until the user explicitly Saves.

NGFP [cascading lists](cascading-form-lists.md) store a SHA-256 reference to the
complete immutable snapshot under the owning layer, selected stable keys,
managed field names and original values in typed state via `saveAdditionalFormState`.
Large tables stay out of the Activity Binder parcel. Rotation and durable recovery
verify the pinned file even after NGFP changes; missing/corrupt snapshots block Save
and retain the previous selection in the draft. Cleared descendants are
explicit null values, so retry cannot restore obsolete children from SQLite.
Cascade membership validation and the common required gate run before writes;
an invalid selection leaves the recoverable draft intact.

## Recovery hub (`MainActivity.maybeOfferCrashRecovery`)

Order after map resume:

1. Track auto-start if its durable recording flag is enabled.
2. An owned point/final-walk form checkpoint takes precedence over a duplicate geometry handoff checkpoint.
3. Reconcile the durable walk owner: silently remove an unacknowledged empty start,
   finalize an interrupted FINISHING transition, offer Continue/Discard for a real
   stopped draft, or offer emergency reset when its map is not active.
4. Legacy interrupted walk reconstruction, then manual geometry/form recovery as applicable.
5. A point owner without a recoverable geometry/form offers explicit Continue/Discard; chooser recreation does not silently release the lock.

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

## Failure boundaries

Автоматическая [диагностика](error-reporting.md) сохраняет отдельные SDK envelopes
и не меняет содержимое или время очистки draft journals. HyperLogCrashHandler
делегирует Sentry и Android; fatal процесс завершается штатно. WorkManager retry
не открывает GIS базы и не перезапускает SDK/handlers. Контекст отчёта состоит
из stack/version/device и фиксированных стадий, без значений формы и геометрии.

The form Save worker checkpoints before database work and after assigning a new id.
Its UUID resolves through `FeatureSaveJournal`; retry after a lost reply returns the
same row. A failed photo or edited signature keeps the form and checkpoint, including
the assigned id. SQLite rows/outbox are atomic; database and filesystem are a
recoverable workflow, not one cross-store transaction. Backup and all destructive
layer actions reserve form/walk owners. Back uses AndroidX callbacks and preserves
Save/Discard confirmation; an in-flight Save blocks another Save or Back. A destroyed/finishing Activity refuses
old-worker UI capture or terminal draft clearing; its UUID checkpoint stays recoverable.

`PendingTrackPoints` has capacity2048 and pauses acquisition near capacity until
drain succeeds. UUID point records prevent duplication after committed writes with
lost acknowledgements. A corrupt spool file is retained and blocks silent recovery.
On disk failure the live process retains the queue in RAM and reports failure;
process death before a durable write cannot preserve RAM-only points. Stop/split
retain the owning map, pending_stop and recording flag until the tail is committed.
A revoked location permission can defer a cold pending-Stop retry until permission
returns. New spool instances/retry are tested; actual reboot/process-kill and OS
permission transitions still require device smoke.

Walk checkpoint exceptions produce a plain panel/notification warning; a later
successful checkpoint clears it. Failed final persistence cannot hand off geometry.
Sequential linked-list traversal preserves WKT and cuts long-walk checkpoint cost;
large snapshots still run synchronously and may stall the main thread. See the
[measured results and remaining checks](../reference/mobile-reliability-audit.md).

## Версия правил в черновике

Форма хранит полную `scriptReference` в Bundle и FeatureFormDraftStore. Пустая
строка закрепляет отсутствие правил; null в старом черновике сохраняет legacy
поведение. Перед транзакцией Save выполняет закреплённый `before_save`; warning
можно подтвердить, block/closed-ошибка оставляют форму и черновик. Журнал Save
восстанавливает уже созданный ID до hook, чтобы retry не считал объект новым.
Пакеты старых pin автоматически не удаляются. [Контракт](project-scripts.md).

Декларативные обязательность и видимость NGFP закреплены одним `lisa_required_pin`
в owning layer/form_rules. Видимость после восстановления вычисляется заново по
сохранённым значениям; скрытие не удаляет текст, вложения или состояние каскада.
Обновление meta не меняет восстановленный черновик;
повреждение снимка блокирует запись и сохраняет pin и значения. Пустой pin
закрепляет отсутствие условий. [Контракт](conditional-form-rules.md).

Bundle и durable draft считывают зарегистрированные `mFields`, независимо от
контейнеров оформления. Прямые Tabs дополнительно сохраняют выбранную вкладку;
подписи собираются и со скрытых страниц. Обход только прямых детей controls_list
неполон: обычное поле теперь находится внутри FieldContainer.

## Project sync recovery

ProjectSyncRunner persists every planned project/account pair before starting.
Successful pairs are acknowledged independently after all scoped async children
and service deliveries finish. Recovery matches the registry identity even when
another map is open; an extra never supplies a trusted database path.
Collector import journals are partitioned by canonical project path and migrate
the legacy marker only to its matching UID. Cancellation retains pending work
and does not close a database with an unresolved write or queued child.

See [sync/storage](ngw-sync-and-storage.md) and the
[user guide](../guides/project-synchronization-user-guide.md).

Выбранная по стилю категория нового обхода хранится в walk_initial_values
того же атомарного снимка WalkSessionStore, что и геометрия/владелец записи.
Finish переносит её в initial_values GeometryEditDraftStore и затем в durable
checkpoint формы. При создании по местоположению категория сразу попадает
в checkpoint формы. Старые записи без начальных значений читаются; существующие
объекты эти defaults не получают. Контракт: [тип объекта](feature-type-creation.md).

## Отправка трека после регистрации

track_send по умолчанию true. Старые установки однократно включают отправку
через track_send_default_enabled_v1; последующее ручное выключение сохраняется.
Регистрация UID больше не снимает галочку. Неотправленные точки сохраняются
для следующей живой или фоновой попытки, включая Stop и process restart.
WorkManager jobs принадлежат сохранённому пути проекта и не переходят на другую
карту. Полный контракт и индикация: [доставка трека](ngw-sync-and-storage.md#доставка-трека-и-регистрация-uid).
