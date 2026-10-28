---
title: MapLibre rendering и порядок слоёв
type: architecture
last_verified: 2026-10-28
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerGroup.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWRasterLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapEventSource.java
  - maplib/src/main/java/com/nextgis/maplib/util/SyncWorkspaceSession.java
  - maplib/src/main/java/com/nextgis/maplib/map/UserLocationGeometry.java
  - maplib/src/main/java/com/nextgis/maplib/util/MbTilesInfo.java
  - maplib/src/main/java/com/nextgis/maplib/util/CameraZoom.java
  - maplib/src/main/java/com/nextgis/maplib/util/LegacyTileMbtilesMath.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureGeometryValidator.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/NativeFeaturePayload.java
  - maplib/src/main/java/com/nextgis/maplib/map/RenderMemoryGuard.java
  - maplib/src/main/java/com/nextgis/maplib/map/RenderTileWorkGate.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileDiskCache.java
  - maplib/src/main/java/com/nextgis/maplib/display/PatternTilePeriod.java
  - app/src/main/java/com/nextgis/mobile/util/RenderMemoryNotice.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/RenderMemorySafetyTest.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerIdentifyPolicy.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorRasterLayerHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/ReorderedLayerView.java
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/java/com/nextgis/mobile/location/DeviceHeadingProvider.kt
  - app/src/main/java/com/nextgis/mobile/location/HeadingConeAccuracy.kt
---

# MapLibre rendering и порядок слоёв

MOB-04 follow-up28октября сохраняет содержимое и SDK13.7.1 demand: shared
cancellable admission2 ставится до native blocking IO, canceled waiters не
заполняют dispatcher payloads; leases живут до actual unwind, освобождаются до
Main. Текущие parent/fade/adjacent tiles не теряются по эвристике масштаба/возраста.
Производный PBF cache имеет общий bounded canonical-root ledger с отдельной
bootstrap/reconciliation вместо полного scan/sort перед каждой записью. Полные
PBF/ID/геометрии/подписи/scale gates, source/style/content fences и original edit
route не меняются. HTTP использует тот же indexed cache, его queue/peer protocol
и line-label scheduler этим не переделываются. См.
[cache/queue contract](map-performance.md#удаление-общих-накладных-расходов-cachequeue--28-октября2026).

## Ограниченные original reads — 2026-10-28

Final D101 strict observed dense1403/11.7z video gate принят отдельно от phase
log:10.33–12.27s против6039 13.68–15.65s и843d32.45–34.09s. Last upper pink
strip/названные contents присутствуют; late map ROI вне HUD/controls MAE0 с
обоими. Один processcold run/build/diskretained, не fullstyle catalogue/2–3s.
Final allmaplib750PASS/132classes/0fail/error/skip;71focused — поднабор.
TrackLayer остаётся вне ordinary vectors: saved/active complete GeoJSON
snapshots не менялись, track RAM/throughput не принимались.

Direct tile и visible-line visitors читают прежний RTree candidate набор через
attributes-only cursors по32 eligible IDs. CASE order сохраняет исходный
порядок после dedup/hidden/excluded; attributes projection не содержит geometry.
API28+ добавляет отдельный geometry-only cursor с явным1MiB window: не более32
whole BLOB<=16KiB, nested CASE не материализует large/TEXT payload. Large BLOB
и API26/27 сохраняют полный PFD путь. Не более512KiB small payload в window
сосуществует с одним Java full original/decoded geometry; sorter/native RAM
не входит в эту границу и требует отдельного PSS измерения.
Каждый original проходит прежние style/clip/anchor/PBF операции;
один ленивый BLOB statement принадлежит только этому visit. Оба cursors/statement
закрываются при cancellation/error. Missing/extra/reordered ID, null/bad BLOB
дают ошибку всего тайла, не partial success/cache; реальные SQLite ошибки
сохраняются. Safely unsupported driver переключает только ещё не доставленные
IDs на PFD; cleanup/SQL/resource errors не становятся fallback success.
Current проверяется между attrs и geometry queries и после полного read.
Default Access visitor совместим со старыми read fixtures;
public editor/sync original API не меняется. Epoch/current/free-only guards,
gate/IO workers/cache и typed phase boundaries сохранены. Ordinary PRIVATE D101
и actualAPI30 cold1403 проверили оба transport пути:1350inline+2PFD среди10
terminal representatives, без retained read/window/SQLite failure markers.
Same9counter121/110features сохранили BLOB/PBF/label counts и дали меньший
BLOB stage;35xxms IOqueue и ограниченная telemetry не принимают полный кадр.
Это native read-path witness, ещё не paint/label/pixel/Full acceptance.

## Фазовая диагностика SQLite pipeline — 2026-10-28

Typed scope начинается до IO dispatch в `SqliteVectorSourceBridge`, проходит
совместимый overload backend и существующие чтения `VectorLayer`, а фаза Main
dispatch отмечается ещё на IO. Она разделяет ожидания queue/lock/gate/cache,
SQLite attributes/BLOB, full decode, style/clip/labels и PBF encode/write.
`LISA-SqlitePhase` имеет общий bounded observer/writer, максимум16 scopes,
32 layer summaries и10s/слой. Наблюдение не меняет geometry, gates, кэши,
owner/current guards или free-only policy. Последний current guard остаётся
перед возвратом без нового suspension/SDK вызова.

`provider_returned`, `returned_to_sdk`, `cancelled` и `failed` различаются;
завершение Java provider не доказывает native content ready. Последнее требует
отдельного наблюдения реальной карты. Scope не содержит URI, tile X/Y,
координаты или бизнес-поля и не делает дополнительных DB/memory/native reads.
Подробности ограничений — [доставка диагностики](error-reporting.md).

## Память без потери геометрии — MOB-04, 2026-10-28

Поддерживаемые ordinary Simple/Field Rule слои используют SQLite/RTree → MVT8192
→ штатный CustomVectorSource13.7.1 без HTTP и полного startup Java/GeoJSON слоя.
Редактор/selection читает по одному полному DB original; transient GeoJSON
нужен только малым overlays. Все factories/rule groups сохраняют paint/layout
и получают source-layer `features`. Polygon Fill/Line допускают только Polygon,
Symbol — inline Point anchors, чтобы query/picking не смешивал разные роли
одного FID. Source owner/content/style epochs и terminal cancel защищают
смену стиля/проекта/Activity. Это реализованный исходник, не завершённая native
приёмка или опубликованный APK; benchmark и2GiB/полный styles/edit gates открыты.

В сохранённом classic compatibility path полный rich Java снимок `sourceFeaturesHashMap` остаётся источником для
редактора/selection/update. Отдельный `NativeFeaturePayload` сохраняет исходные
геометрию, bbox, IDs, неизвестные поля и используемые paint/label значения,
но не дублирует literal layout свойства, уже заданные native factories.
Polygon geometry не несёт label-настроек; отдельные label points создаются
только для непустой подписи и не несут geometry paint. Point payload сохраняет
совместные paint/label поля, line payload — inputs для native-query fallback
`VisibleLineLabelController`. Custom zoom curves, layout keys, label zoom gates
и явные false/null overrides сохраняются. Exact uniform polygon defaults
сворачиваются только в контексте тех же SimpleFeatureRenderer/SimplePolygonStyle;
rule-style и unknown контекст используют консервативную проекцию.

Проекция применяется во всех logical-vector native handoffs: создание,
hot load, style/data reload, ID/edit/save/hide/show. Rich Java original не
меняется; публичный `convertToPointFeatures` сохраняет прежний anchor API.
Старый styled URI не обходит проекцию, native URI cache flag остаётся false.
Style JSON, SQLite, sync/outbox, saved visibility и геометрия не мигрируют.

`RenderMemoryGuard` реагирует на фактический свободный остаток RAM, системный
порог/OS low-memory state и Java headroom. Used/native/RSS сохраняются только
как диагностика, не как условие замедления или предупреждения. Номинальный
класс2GiB/Android low-RAM сам по себе экономию не включает; он задаёт обычный
disk cache и резерв32/64MiB. При малом свободном остатке отключается
межмасштабный кэш и запрашивается SDK `onLowMemory`.
Верхний app показывает одно автоматическое сообщение за процесс, включая
другую карту/слой и пересоздание Activity; ручной статус доступен повторно.
Callback проверяет map/view/host/lifecycle epoch, pause/destroy прекращают
watcher. Retry переоценивает cache policy без полной GeoJSON перезагрузки.
Геометрия и полный набор объектов не прореживаются, слои не отключаются,
`restrictions()` пуст. Текущая защита не вводит новые LOD.

Это не жёсткий предел native памяти: внутренний GeoJSONVT index SDK остаётся
без добавленного здесь eviction. Профиль787 выделил673MiB/74,2% из907MiB
sampled retained allocations на index, включая≥564MiB property maps;
около128MiB — растровые данные. Это не весь RSS и не доказательство утечки
по одному высокому плато. Триггер223→224 неизвестен. Экспериментальныйec71
показал уменьшение sampled retained allocations, но его старый RSS-trigger и
разный startup/route timing не принимают финальную free-only policy/2GiB.
См. [производительность и границы](map-performance.md),
[карточку payload](../../maplib/update-reports/entries/maplib-20261028-native-geojson-payload.md)
и [карточку cache economy](../../maplib/update-reports/entries/maplib-20261027-render-memory-budget.md).
Related/Full native приёмка остаётся обязательной; отсутствие OOM не гарантируется.

## Пустые editor hit-tests и индекс — 2026-10-27

При ACTION_DOWN пустой markerFeatureCollection и vertex-layer без editingObject
дают пустой результат до любых обращений к MapLibre/style. Это убирает ненужный
синхронный nativeQueryRenderedFeaturesForBox в обычном перемещении карты.
Если handle существует, сохраняются native picking, допуски, изменение вершины
и drag; обычные слои этим условием не исключаются. Причина ожиданий внутри
renderer для непустого запроса не установлена. Startup GeometryRTree получает
буфер 64 KiB без изменения формата и синхронизации; подробности хранения —
[NGW/storage](ngw-sync-and-storage.md).
[Свидетельства и проверки MOB-02](../reference/mobile-crash-224-2026-10-27.md).

SDK MapLibre разрешает HTTP transport даже без внешней сети: локальные SQLite
тайлы поступают через HTTP loopback LocalVectorTileServer. Глобальный SDK
Offline иначе откладывает и эти запросы. Это override только SDK transport;
NetworkUtil и WorkManager сохраняют фактическое состояние Android сети.
Внешние URL карты без сети могут получить transport error и backoff SDK.
Native offline regression выключает оба радио только owned AVD, проверяет
отрисовку MVT и живой переход к classic Firefly и восстанавливает радио.

Перед уничтожением MapView host отправляет ACTION_CANCEL прямо в SDK после
снятия edit touch listener. Это завершает ожидающие single-tap/long-press
callbacks до освобождения native renderer; очистка только app map listeners
их не отменяет. [MapView 13.7.1](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapView.java)
при onDestroy освобождает renderer, а [MapGestureDetector](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapGestureDetector.java)
передаёт ACTION_CANCEL gesture manager. Strict mode не подавляется. Адресная
проверка — Related map-teardown; после неё обязателен повтор Full.

Треки рисуются тонкой тёмной сердцевиной и свечением исходного насыщенного цвета.
Выбранные параметры и производный цвет сердцевины описаны в контракте треков;
дополнительных источников или проходов для нового вида нет. Настоящие
Point/MultiPoint группы вместе с их подписями находятся над обычными данными и
треками, сохраняя свой порядок в списке; инструменты и GPS выше. Служебный слой
треков остаётся первым в UI. Подробности: [управление треками](track-controls.md).

## MapLibre 13.7.1 с OpenGL

Все три consuming-модуля используют `org.maplibre.gl:android-sdk-opengl:13.7.1`.
Обновляется только SDK: прежние GeoJSON/MVT источники, фабрики стилей, подписи,
выбор и редактирование объектов сохраняются. API `feature-state`, SDF patterns
и `CustomVectorSource` доступны в новом SDK, но их использование потребует
отдельных изменений приложения. Выбран OpenGL. Переработка загрузки и тайлов —
отдельная задача. Результаты сборок и native регрессий:
[проверка обновления SDK](../reference/maplibre-13-7-1-verification-2026-10-24.md).

## Штриховки и расширенный контур

[Контракт нового пакета](polygon-style-controls.md): необязательные параметры сохраняют прежний вид старых полигонов. Узоры получают отдельные цвет/alpha и бесшовную решётку; расширенный контур рисуется штатными children. Настроенный MVT-слой с новыми параметрами переходит к classic fallback. Native образец в редакторе использует те же фабрики, без сети и рабочей карты.

`PatternTilePeriod` сохраняет исходную512×512ARGB решётку и уменьшает bitmap
только до точного прямоугольного повторяемого мотива, проверенного по каждому
пикселю. Density160, округление, color/alpha, native IDs/ownership и LRU4MiB
сохраняются; без доказанного периода остаётся полный размер. Геометрия и
saved style прежние. [Карточка](../../maplib/update-reports/entries/maplib-20261027-pattern-exact-motif.md).

## Копирование и преобразование объекта

[Контракт](feature-copy-transform.md): копирование сохраняет исходный объект и открывает форму назначения. В отдельном режиме целиком сдвигается/поворачивается текущая геометрия при неподвижной камере; один жест — один Undo, включая все части и отверстия. Панель вписывает все команды без overflow по фактической density/insets.


## Круг точности встроенного GPS

App-side `LocationDisplayAccuracy` передаёт в `MapDrawable.updateLocation`
радиус не меньше 3 м для встроенного GPS. То же значение выводится в нижней
панели. MapDrawable, метрическая геометрия, центр маркера и прозрачное отверстие
сохраняют прежнюю реализацию. Внешний NMEA/mock GPS и Network не получают порога.
Контракт источников и проверок: [GPS pipeline](location-pipeline.md).

## Матрица обычных стилей

[«Светлячок»](point-firefly.md) — отдельный тип 9 для обычных Point/MultiPoint.
RGBA rendering children с префиксом `-layout-firefly-` участвуют в существующем
порядке, picking и visibility логического слоя. Native images освобождаются
после restyle/удаления последнего владельца; CPU bitmap cache ограничен 4 MiB.
Прежний `circle-blur` других категорий сохраняет прежнюю отрисовку.

[Native матрица](classic-style-verification.md) проверяет обычный GeoJSON renderer
и подписи после rule JSON reload. Configured local-vector-tiles исключены по
render-mode/source, их поддержка не расширяется. Explicit optional label settings
сохраняются и наследуют Other только при отсутствии ключа.

При создании точки/геометрии по GPS центр меняется через MapDrawable.setZoomAndCenter:
native камера и zoomSaved/centerSaved обновляются вместе с сохранением дробного
зума. Прямой native moveCamera оставлял старый центр в модели, и отложенная
750 ms MapView.invalidate возвращала его после пересоздания экрана/формы.
MapEditingToolsTest вызывает эту перерисовку и проверяет видимую камеру после формы.

## Отдельные панели выноса и измерения

MapFragment переключает layout_stakeout_panel и layout_azimuth_panel независимо.
GPS callback обновляет только панель текущего режима. Вынос сохраняет compass/
map-bearing стрелку, расстояние, точность и звук. В обоих измерениях отсутствуют
стрелка, звук и GPS accuracy; временные MapLibre точки/линия и их перетаскивание
сохраняются. FittedPanelLayout удерживает Close и коррекцию измерения в usable bounds.

## Сетевой курсор между обновлениями

Карта и status panel используют display snapshot общего `GpsEventSource`.
Последняя отображённая Wi-Fi/сотовая позиция не очищает `user-location-source`
между редкими callbacks: исходный accuracy circle остаётся вокруг курсора,
координаты — в панели, вместе с ±accuracy и возрастом измерения. Более новый
свежий GPS заменяет её. Скрытый Network-кэш старше последнего GPS не возвращается
при потере GPS. Снятие map listener при форме или сне не снимает app/service lease.
Этот контракт не меняет layer order или tool hit testing. Для записи и создания
геометрии сохранённая старая Network-точка не является свежим location snapshot.
Подробности и проверки: [GPS pipeline](location-pipeline.md#автоматическое-получение-и-сетевое-положение).


Touch queries marker/vertex/azimuth проходят через MapRuntimeDiagnostics:
перед синхронным SDK вызовом записывается trace, независимый watcher фиксирует
ожидание, finally завершает trace при результате или исключении. Native вызов
сохраняет поток и прежнее поведение. LocalVectorTileServer диагностирует
queue wait, layer-lock wait, запрос IDs, чтение/обрезку features и MVT encoding;
503 указывает точную причину и request id. Два workers, очередь16 и прежний
heap headroom64MiB дополняются фактическим system/Java admission и fair gate;
подробности ниже. [Контракт диагностики](error-reporting.md).

Панели и инструменты карты адаптируются к font/display scale по [контракту UI](ui-accessibility.md). Компас/GNSS HUD освобождают место на низком экране; азимут располагается под toolbar/компасом слева от инструментов, значения статуса переносятся. View transforms вписывают панели и доставляют touch к отображаемым действиям; MapView сохраняет свою геометрию.

Свёрнутый `MapActionsMenu` передаёт карте DOWN/MOVE/UP вне видимого плюса:
устаревшая AnimatorProxy-анимация скрытых кнопок больше не создаёт мёртвую
область около масштабной линейки. Нижняя панель отображает координаты,
масштаб, GPS/спутники и точность; короткие показатели объединяются при переносе
координат. Значок MapLibre и кнопка сведений SDK на карте скрыты. Если включён
штатный слой OSM, у левого края над панелью статуса показывается одна строка
«© OpenStreetMap»; при открытой нижней панели она поднимается над ней, а линейка
масштаба остаётся выше строки. Пустой footer не добавляется. Высота и скорость в этой панели удалены.

`ReorderedLayerViewAnimated` завершает жест без анимации, если после fling
перетаскиваемая строка больше не видна или adapter снят. Cancel очищает hover,
идентификаторы и ожидание прокрутки, восстанавливает видимость доступной строки
и доступность списка. Перестановки сохраняет прежний LayersListAdapter.endDrag;
порядок LayerGroup/MapLibre не меняется.
См. [разбор GlitchTip 52](../reference/glitchtip-layer-reorder-2026-10-10.md).

Перед открытием карты app и GISApplication используют общий local-only
`LocalLogInitializer`: накопленный HyperLog не сериализуется для фиктивной
удалённой отправки на main thread. Сам pipeline rendering не меняется.
HTTP 503 от локального tile provider и от удалённого сервера различаются только
безопасным `http_source` в диагностике, повторные 5xx ограничены. Collector form
sync сравнивает canonical JSON и сохраняет прежнюю форму при реальном mismatch.
См. [диагностику](error-reporting.md) и [формы Collector](collector-projects.md).

Новый объект сначала получает [категорию стиля](feature-type-creation.md).
Превью использует копию effective Style, штатные PNG-маркеры и fill patterns,
не изменяя карту или исходный символ. Категория переносится в форму вместе
с необходимыми зависимыми значениями; lifecycle и порядок MapLibre прежние.

Изменение режима `MapFragment.setNewMode` записывает только enum операции в
[диагностическую историю](error-reporting.md): карта, геометрия, обход, азимут
или вынос. Координаты, имена/атрибуты объектов и provider URI не добавляются.
Эта запись не меняет gesture handling, camera/style/source lifecycle или порядок
слоёв; ошибки расчёта передаются отдельно с исходным stack trace.

## Ownership

- `MapDrawable` строит и обновляет MapLibre style, sources и layers.
- `MPLFeaturesUtils` содержит операции style и определение sibling anchors.
- Для Polygon и MultiPolygon он создаёт отдельный Point source подписи:
  внутренняя точка берётся с учётом отверстий, у MultiPolygon — на наибольшей
  пригодной части. Вырожденный контур без внутренней точки не получает подпись.
  Полный helper сохраняет исходные свойства feature; native точка получает
  компактную label-проекцию и создаётся только при непустой подписи.
- `VectorLayerRenderCache` ускоряет подготовку векторных слоёв при cold start.
- `LayerFillService` загружает и вставляет импортированные слои в `LayerGroup`.
- `ReorderedLayerView` синхронизирует порядок UI и модели.
- `MapFragment` — host `MaplibreMapInteraction` и точка reload/lite reload.
- Gradle-файлы `app`, `maplibui` и `maplib` совместно задают один native
  MapLibre backend для конечного APK.

## Контракты

Подготовка production `3.1.2.27` / `221` меняет version metadata, сохраняя общий
OpenGL backend MapLibre13.0.2. Release APK/version matrix выполняются после
merge library dependencies и проверки их точного включения в app.

Incremental track reload (`reloadCurrentTrackToMap`, `reloadTrackListToMap`)
читает записи/сегменты в общей фоновой очереди, по одной активной и одной
отложенной задаче каждого вида. Чтение использует общую SQLite SharedTrackStore, независимо от project
ContentProvider и mutable cache `TrackLayer` ([контракт](shared-tracks.md)). Main thread
только устанавливает готовый GeoJSON, проверив identity карты, view и style.
Ошибка чтения сохраняет предыдущую отрисовку; устаревший результат отбрасывается.
Завершённые видимые треки остаются отдельно от незавершённых, сегменты не
соединяются, start/end flags не возвращаются. Full style build остаётся отдельным
существующим фоновым сценарием.

1. В `LayerGroup` индекс `0` означает низ стека. Дефолтный OSM/Mapnik существует
   в каждой карте, включая Collector workspace, и нормализуется в эту позицию
   без сброса пользовательской видимости.
2. Для NGRc/local TMS слой помещается над OSM, если OSM существует; иначе — в
   нижнюю позицию, а не поверх всего пользовательского стека.
3. `signaturesRootLayer` и raster sibling anchor учитываются при вставке style
   layer; нельзя полагаться только на порядок обхода.
4. Hot-add raster обязан привести список и карту к одному порядку без restart.
   Текущий путь включает `loadLayersLite()` после добавления raster.
5. Полный reload нельзя без необходимости подменять lite reload: у них разные
   гарантии по пересозданию style и сохранению UI/edit state.
6. Start/end flag sources/layers для треков намеренно не включаются.
7. `.ngrc` распаковывается только внутри нового каталога слоя с защитой от
   archive path traversal. После успешного импорта `config.json` хранит SHA-256
   и политику `immutable_local`; Collector sync не управляет этой подложкой.
8. `user-location-layer` — служебный overlay, а не элемент `LayerGroup`. После
   cold load, lite reload и горячего обновления style он должен быть последним
   MapLibre layer и поэтому отображаться выше треков, пользовательских векторов,
   растров, подписей и edit overlays. Непосредственно под ним — контур сектора
   `user-location-heading-outline`, затем его белая подложка
   `user-location-heading-halo`, заливка `user-location-heading` и круг
   `user-location-accuracy`. Контуры фильтруют только Polygon с `role=heading`.
   `iconAllowOverlap` и `iconIgnorePlacement` не позволяют collision detection
   скрывать курсор.
9. Пользовательский слой «Мои треки» остаётся последним элементом внутреннего
   `LayerGroup` и первой строкой перевёрнутого UI-списка. Collector batch
   вставляет project-managed слои ниже этой границы, а открытие существующей
   карты исправляет ранее сохранённый неверный порядок.
10. Collector vector и поддерживаемые QGIS style resources используют один
    `collector_order`. Style materializes как authenticated `NGWRasterLayer`,
    поэтому `computeCollectorOrderedInsertIndex()` учитывает и vector, и raster
    NGW layers; remote id стиля отвечает за tile identity, parent resource id —
    только за extent.
    Перед фактической подготовкой workspace import повторно проверяет active
    sync; layer/raster hot-add не начинается, пока отменённый worker и его
    database lease не завершили commit либо rollback.
11. Первый `MapDrawable` основного процесса всегда открывается внутри
    зарегистрированного проекта. На чистой установке до загрузки карты создаётся
    и активируется «Локальный проект»; при обновлении прежняя штатная
    standalone-карта один раз копируется в его изолированный workspace без
    удаления оригинала.
12. Rule-based векторный стиль: слойные MapLibre-дефолты (size/text stops,
    scale-with-zoom, opacity подписей, SymbolLayer min/max) берутся из
    «стиля для прочих (по умолчанию)», не из базового `mStyle` рендерера.
    `FieldStyleRule` при apply мержит категорию с прочими для type-default
    полей (зум подписей, zoom-stops, флаги scale, opacity=255, шаблон/поле,
    иконка и т.п.). Per-feature `label_min_zoom`/`label_max_zoom` режут
    видимость через data-driven `text-opacity`; SymbolLayer clamp сбрасывается
    явно. Data-driven scale — {@code interpolate(zoom)} наверху, {@code case} только
    в значениях stops (zoom нельзя вкладывать в case — MapLibre spec). Ограничение:
    категория с `scale=false` наследует `true` от прочих (false = unset).
12. Identify/select: RTree даёт кандидатов по envelope; refine через
    `EditLayerOverlay.notContains` — полигоны PIP, линии/точки пересечение с
    tap-envelope (±20dp), не пустой угол bbox линии. Названия кандидатов и
    выбранного объекта берутся из одного `VectorLayer.getFeatureLabel()` по
    `feature_label_field`; renderer label на этот выбор не влияет.
13. Если при cold load число записей R-tree не совпало с SQLite, индекс
    перестраивается и сохраняется отдельно. `VectorLayer.fromJSON()` не сохраняет
    конфигурацию слоя во время этой перестройки: NGW-поля подкласса в этот момент
    ещё не прочитаны, и запись частичного `config.json` недопустима.
    Все публичные чтения/изменения `GeometryRTree` сериализованы; `tighten()` не
    скрывает `ConcurrentModificationException` после обнуления envelope.
14. `local_vector_tiles` сохраняет тот же style-order contract. Помимо
    polygon/multipolygon, локальный `VectorSource` допускается для read-only
    `GTPoint` только с простым круговым маркером и подписью из одного поля либо
    фиксированного текста. Rule-style, custom icon, label template, editable и
    прочие геометрии остаются на classic `GeoJsonSource` fallback. Loopback-
    сервер ограничен двумя worker и очередью16; тайлы одного слоя строятся
    последовательно. Fair two-permit gate сохраняет два builder при нормальных
    ресурсах, а при малом измеренном свободном system/Java остатке один builder
    занимает оба permits. Устаревшая очередь закрывается при смене поколения,
    старые активные запросы проверяют отмену. Coarse prefetch local MVT выключен,
    source volatile. Полные derived PBF read-only источников кэшируются приватно
    в пределах32/128MiB,1024 файлов и4MiB на запись; ключ process/map/content/style
    исключает старый source. Это не межпроцессный reuse и не native swap file.
    Нехватка памяти/перегрузка даёт явный retryable503, без новых потоков,
    изменения rendering mode или неполного200/кэша. Текущий успешный тайл содержит
    все подходящие объекты с прежними MVT clipping/encoding, без ID thinning.
    Сам ответ503 не доказывает восстановление SDK тайла после освобождения RAM;
    этот переход — отдельный native gate.
    Подпись квартала вычисляется по исходному Polygon/MultiPolygon до clipping:
    одна внутренняя точка на наибольшей пригодной части с учётом отверстий.
    Полигональные фрагменты остаются без `signature`; единственная служебная
    Point запись `_label_anchor=true` с исходным ID и текстом входит в тот же
    MVT `features` только у owner tile (без buffer, границы east/south включаются
    ровно один раз). Прежние FillLayer/LineLayer фильтруют Polygon, SymbolLayer
    фильтрует label anchor. Дополнительных объектных слоёв, sources или native
    style layers нет; коллекция внутренних точек всего слоя не загружается.
    После округления в сетку MVT точка повторно проверяется на строгое попадание
    внутрь исходного полигона. На зуме без представимой внутренней точки подпись
    пропускается; вне текущего экрана позиция не переносится. Расчёт выполняется
    из актуальной прочитанной геометрии, без stale anchor cache.
    Соседние тайлы вычисляют общий край идентичным выражением: накопление
    floating-point ошибки через `min + tileSize` не создаёт перекрытий owner
    bounds. Повторяющиеся IDs пространственного индекса дедуплицируются до
    чтения/обрезки/генерации подписей.
    После bbox-запроса генератор не отбрасывает полигон по legacy пересечению
    его колец с envelope: тайл может целиком лежать внутри квартала. Отсечение
    обрабатывает такие тайлы наряду с пересечением границ; native regression
    проверяет сохранность заливки и подписи при увеличении с зума 12 до 13.
15. Completion пакетного fill означает не «full reload был поставлен в очередь»,
    а «новый MapLibre style применён и каждый видимый vector layer имеет source
    и хотя бы основной либо symbol style layer». До этой проверки application
    pending-флаг не очищается. Неполный apply вызывает один ограниченный полный
    retry; бесконечный reload loop запрещён.
16. Векторный слой с `visible=false` может намеренно отсутствовать в live
    MapLibre style после оптимизированного full load. При локальном включении
    наличие старой записи в `sourceFeaturesHashMap` не доказывает, что source и
    render layer существуют в текущем style. Если хотя бы одного из них нет,
    `checkLayerVisibility()` запускает data reload; менять server config или
    выполнять sync для появления слоя не требуется.
17. Выбор слоя для нового объекта немедленно создаёт точку либо первый узел
    LineString/Polygon и их Multi-вариантов в экранной проекции центра камеры,
    без промежуточной кнопки `+`; нижняя панель выбора объекта также не дублирует
    эту кнопку. Отмена нового скетча крестиком требует подтверждения и только
    после него удаляет черновик и возвращает обычную карту.
    Следующие тапы вставляют узел после выбранного. Направление вставки видно до
    действия: выбранный узел красный, его следующий узел в той же части/кольце
    оранжевый и увеличенный, а соединяющий их сегмент утолщён и окрашен тем же
    цветом. У последнего узла незамкнутой линии следующего маркера и сегмента нет;
    у кольца полигона последний узел указывает на первый узел того же кольца. На рёбрах линий,
    полигонов и линейки доступны промежуточные узлы. Панели LineString/Polygon не
    публикуют режим дополнения касанием и overflow-кнопку; команды частей и
    отверстий полигона также отсутствуют. Новый MultiPolygon принимает только
    стартовую часть, но загрузка существующей многосоставной геометрии не
    упрощает её структуру. Обход доступен уже на первом узле и вставляет поток GPS
    между выбранным узлом и его прежним следующим узлом; live preview использует
    ту же позицию вставки. Кнопка формы нового Point/Line/Polygon и их
    Multi-вариантов активируется при наличии геометрии и вызывает общий
    `saveEdits()`, включая валидацию, repair и один form handoff.
    Успешный update существующей геометрии и успешный результат формы для нового
    или существующего объекта являются terminal-событиями edit session:
    `MapFragment` завершает временный MapLibre edit object, очищает overlay feature,
    снимает view-selection, закрывает открытую панель атрибутов и возвращает
    `MODE_NORMAL` со стандартными панелями. Ошибка записи этого перехода не
    выполняет; явная отмена формы следует штатному cancel-пути. При переводе
    GeoPolygon/GeoMultiPolygon в GeoJSON кольцо замыкается явно: это сохраняет
    заливку и индексы вершин после восстановления WKT-черновика. При холодном
    продолжении обхода один property-bearing edit feature атомарно обновляет
    `selected-poly-source`, заливку, контур и скрытый на время обхода кэш вершин;
    после Stop тот же кэш снова публикуется для редактирования. Общий edit
    FillLayer допускается только для Polygon/MultiPolygon и удаляется при каждом
    обновлении LineString/MultiLineString, включая cold Continue. WKT parser
    выделяет кольца Polygon и отдельные members MultiPolygon по уровню скобок,
    не создаёт из внешнего кольца ложную дырку и не теряет следующие части,
    поэтому восстановленная геометрия снова имеет заливку и исходное число узлов. Площадь,
    измеренная линейкой, выводится в гектарах. Преобразование экранных вершин
    LineString/Polygon в координаты карты выполняется через актуальную
    MapLibre-проекцию, поэтому перемещение карты перед созданием не оставляет
    стартовую вершину в прежнем центре. Самопересекающийся обычный Polygon не
    сохраняется как заведомо невалидная геометрия и получает отдельное сообщение
    о самопересечении; «Недостаточно точек» остаётся только для короткого контура.
    История редактирования хранит 100 отмен и добавляет снимок только при реальном
    изменении координатного WKT. Выбор узла и MapLibre callbacks, отличающиеся
    только CRS, не создают пустого Undo/Redo-шага, а достижение границы не сдвигает
    курсор истории. Инструмент измерения использует те же видимые кнопки Undo/Redo:
    добавление или перенос точки линейки создаёт шаг, отмена возвращает предыдущую
    длину/площадь, а новая правка после Undo удаляет недоступную ветку Redo. Источник
    этих снимков — активный MapLibre `MeasurmentLine` в `MapDrawable`, а не legacy
    `RulerOverlay`; восстановление шага заменяет точки того же активного edit-object.
18. При сохранении невалидной геометрии автоматическое исправление топологии
    включено только для слоя с точным типом `GTMultiPolygon`. JTS
    `GeometryFixer` может разделить самопересекающееся кольцо на несколько
    полигональных частей либо объединить перекрывающиеся части, но результат
    остаётся одним `GeoMultiPolygon` того же feature: форма атрибутов открывается
    один раз, а введённые значения принадлежат всему объекту. CRS и валидные
    отверстия сохраняются. Если исправление даёт пустой, неполигональный или
    всё ещё невалидный результат, сохранение останавливается и редактор остаётся
    открыт. Контур MultiPolygon с менее чем тремя различными позициями отделяется
    до topology repair и получает «Недостаточно точек», а не сообщение об ошибке
    исправления. `GTPolygon`, `LineString` и `MultiLineString` в эту ветку не входят.
    MapLibre → `GeoMultiPolygon` conversion обязан назначать Web Mercator CRS
    контейнеру, полигонам и кольцам. Repair дополнительно восстанавливает
    отсутствующий CRS контейнера из дочерней геометрии для ранее созданных
    edit/draft-объектов; количество вершин на это поведение не влияет.
19. Виджет выноса/азимута является обычным Android overlay над картой. Вынос к
    выбранному feature сохраняет штатный view-selection. Свободное измерение
    добавляет только непостоянные `azimuth-measurement-*` GeoJSON source и line/
    point layers: они восстанавливаются после full/lite style reload, находятся
    выше пользовательских слоёв, но ниже всегда верхнего `user-location-layer`,
    поддерживают drag доступных для корректировки начальной/целевой точек через
    host callback и очищаются при закрытии режима. Ни один из вариантов не меняет `LayerGroup`,
    не создаёт прикладной feature и не требует пересоздания style. Геодезический
    расчёт описан в [`stakeout.md`](stakeout.md).
20. Видимость векторного слоя не является правом редактирования. Список слоя для
    нового объекта включает все слои подходящего типа с `isEditingAllowed=true`,
    в том числе `visible=false`. Перед запуском редактора выбранный скрытый слой
    получает `visible=true`, сохраняется и обновляется в live MapLibre style через
    штатный `onLayerVisibleChanged()`/`checkLayerVisibility()`.
21. Жест вращения MapLibre выключен по умолчанию и включается только отдельной
    кнопкой в верхней панели. Наклон и встроенный MapLibre compass остаются
    выключенными. При разрешённом вращении scale detector не отключает rotate
    detector: одновременное быстрое касание двумя пальцами начинает поворот без
    выдержки, последовательной постановки пальцев и потери pinch zoom. Порог
    старта поворота равен `0.5°`. Запрет вращения плавно возвращает bearing `0`; разрешение и
    последний bearing сохраняются между открытиями карты. Кнопка текущего
    положения всегда возвращает север вверх, не уменьшает текущий zoom и при
    zoom ниже `12` повышает его до `12`. Если текущей координаты нет, она берёт
    первый слой с конечным и инициализированным охватом, центрирует карту по
    середине этого охвата и выставляет zoom `12` независимо от его размера. Если
    нет ни координаты, ни пригодного слоя, остаётся обычное сообщение об
    отсутствии местоположения.
22. Schema/composition rebuild является staged replacement: новая
    `NGWVectorLayer` создаётся в отдельном UUID-каталоге, полностью заполняется,
    затем на main thread одним сохранением заменяет прежние копии той же
    `account + project_uid + remote_id`. Промежуточная композиция old+new не
    сохраняется; физические каталоги старых копий удаляются только после commit.
    Ошибка fill/save оставляет прежний слой и удаляет только stage. Оставшиеся от
    старого поведения дубликаты чинятся pre-sync repair после backup gate.
    `default.ngm` и layer configs записываются через `AtomicFile`, поэтому
    process death видит последнюю целую старую либо новую композицию.
23. Инкрементальный NGW pull не публикует insert/update/delete broadcast для
    каждой строки: после полного SQLite-apply выполняется одна R-tree rebuild.
    Публичные операции R-tree сериализованы, а notify callback не меняет индекс
    во время bulk/rebuild. Hot style refresh берёт отдельные `Feature` из
    `VectorLayerRenderCache`, последовательно вычисляет props и публикует готовый
    snapshot на main thread; live `Feature.properties` на worker не изменяется.
    Полный NGW fill отдельно валидирует Polygon и members MultiPolygon через JTS
    без legacy квадратичного сравнения пар сегментов. Число features в progress
    остаётся числом серверных объектов, независимо от количества polygon parts и
    координат внутри них. Последующий untracked sync пропускает отдельную
    отсутствующую или невалидную геометрию, сохраняет её remote ID от ошибочного
    локального удаления и продолжает единый SQLite-apply остальных объектов.
24. Выключенный слой с сохранённым render mode `local_vector_tiles` продолжает
    участвовать в tap/long-press identify через локальную SQLite/R-tree копию,
    не включая MapLibre source и не меняя visibility. Выключенный классический
    vector layer по-прежнему пропускается. Решение централизовано в
    `LayerIdentifyPolicy` и одинаково для всех активных веток identify.
25. MapLibre Android `13.7.1` разрешается только через явный артефакт
    `org.maplibre.gl:android-sdk-opengl` одновременно в `app`, `maplibui` и
    `maplib`. Generic `org.maplibre.gl:android-sdk` этой major-версии использует
    Vulkan и не является допустимым fallback: на устройствах без совместимого
    Vulkan-драйвера карта завершает процесс при создании surface. Смена backend
    требует проверки runtime dependency graph, cold start карты после
    авторизации и повторного запуска на Android 9 без Vulkan.
26. `MapFragment` владеет MapLibre `MapView` в пределах своего view lifecycle:
    после `onCreate` ему передаются `onStart/onResume/onPause/onStop`, сохранение
    состояния и low-memory callback, а `onDestroyView` уничтожает native renderer
    и очищает только ещё актуальные ссылки `MapDrawable`. После resume
    запрашивается repaint и HyperLog фиксирует первый полученный кадр. После
    фактического применения project style host запускает короткий continuous-
    render burst, invalidates Android presentation и один раз проводит камеру
    через native camera transaction без смещения. Успех фиксируется только для
    полного кадра при уже отсутствующем foreground: счётчик callback-ов host не
    подменяет внутренний `InitialRenderCallback` MapLibre. Если style/sources уже
    применены, но opaque loading foreground остался, host снимает только этот
    foreground и продолжает burst до ограниченного финала. Recovery отменяется
    при pause/destroy и не запускает бесконечный цикл. Нельзя
    заменять этот контракт безусловным full style reload: он не восстанавливает
    потерянный render surface и создаёт лишнюю нагрузку на большие проекты.
    Отложенное событие касания после `onDestroyView` отбрасывается в
    `MapDrawable`, если `MapLibreMap`, `MapView` или host context уже очищены;
    уничтоженный native renderer не участвует в identify или edit-жесте.
    Layout на всех API использует `SurfaceView`: его render thread обрабатывает
    `EGL_CONTEXT_LOST` внутренним пересозданием EGL context/surface. В MapLibre
    OpenGL 13.0.2 `TextureView` после ошибки swap обнуляет известный
    `SurfaceTexture` и ждёт нового `onSurfaceTextureAvailable`, которого у
    оставшегося attached view может не быть; поэтому callback `fully=true` до
    swap не является доказательством показанного кадра. На API 26–28 prefetch
    дополнительных tiles выключен. Искусственный потолок FPS и постоянный
    `CONTINUOUS` на Android 8–9 не используются: в покое renderer остаётся в
    `WHEN_DIRTY`, recovery на всех API — короткий `CONTINUOUS` burst, затем
    прежний dirty-режим. Полный style reload заранее освобождает предыдущий
    Java GeoJSON snapshot, а после `setStyle` — detached source/layer wrappers,
    чтобы большой Collector-проект не удваивал пиковую память. Тип renderer,
    SDK и prefetch policy фиксируются в HyperLog при создании view.
    Во время account sync тяжёлые reload откладываются и схлопываются в один
    финальный проход. Data-change выключенного vector layer очищает его прежний
    GeoJSON source, но не читает всю SQLite-таблицу; включение запускает обычный
    on-demand reload.
27. Холодное восстановление ручного скетча проверяет принадлежность всех edit
    sources текущему `Style` и откладывает привязку до готовности источников.
    Независимый обход использует отдельный `walk-preview-source`: полный WKT
    из сервиса превращается в Point/LineString/Polygon, включая незавершённые
    кольца. Перед проецированием приватной копии явно восстанавливается CRS;
    иначе copy-конструкторы теряют корневой CRS и передают метры как lon/lat.
    Полная и облегчённая загрузка style восстанавливают cached preview.
    Смена экземпляра MapDrawable также требует повторной привязки снимка.
    `MapFragment` слушает `WALKEDIT_CHANGE` напрямую во время resume и перечитывает
    снимок при возврате на экран: preview не зависит от наличия панели.
    Все phone/landscape/tablet layouts включают один `layout_map_content` с
    обязательными кнопкой трека и панелью обхода. Кнопка трека открывает
    «Пешеход»/«Пешеход + машина», а при активной записи сразу завершает её.
    Активный значок — оранжевый человек с увеличенной красной точкой на белом фоне.
    `MapControlRail` пересчитывает
    число строк по доступной высоте при каждом measure, в том числе при повороте
    без пересоздания Activity, и переносит инструменты в столбцы справа налево.
    Меню создания раскрывается влево в отдельной нижней строке; панель обхода
    прижата к нижнему краю и слева от инструментов, поднимаясь над строкой
    лишь при раскрытом меню создания. Обычная панель — строка 48 dp с названием слоя
    и значками дискеты, паузы/плей и крестика. Каждый требует подтверждения;
    зоны нажатия 48 dp, светлая/тёмная поверхность и цвет бренда. Точность GPS
    и меню обхода отсутствуют. Пауза сохраняет геометрию и прекращает вставку.
    Начало создания по местоположению/обходом центрирует карту по GPS-опоре,
    сохраняя zoom; восстановление черновика не выполняет этот pan повторно.
    Редактор точки владеет своими selected/vertex sources, не забирая линию
    обхода. Панель обхода сохраняется при обычном режиме карты; все её команды
    блокируются от выбора слоя точки до успешного Save либо явного Cancel.
    После подтверждения дискеты и FINISHED геометрия передаётся обычному редактору
    с немедленным открытием атрибутов.
    Недостаточный контур (две разные вершины линии или три каждого кольца)
    завершается с сообщением без объекта. Активное Save панели и дискета открывают
    атрибуты с текущей геометрией, включая дополнение существующего объекта.
    Enabled и alpha Save/Undo/Redo обновляются после построения toolbar;
    `ControlHelper` изолирует drawable state перед изменением alpha/tint.
    При cold start владелец без первого подтверждённого снимка и без живого
    сервиса удаляется как несостоявшийся запуск; реальный остановленный черновик
    предлагается восстановить, а владелец другой карты — аварийно сбросить.

28. Растровый MBTiles хранится одним файлом `map-mbtiles.mbtiles` внутри
    каталога локального TMS-слоя и подключается к MapLibre через `mbtiles:///`.
    До публикации слоя проверяются SQLite header, обязательные таблицы и поля,
    `quick_check`, raster `format`, zoom и bounds. Vector MBTiles этим путём не
    принимается. При переносе старой распакованной подложки Debug → Geonical
    тайлы читаются framed-потоком без центрального каталога и записываются
    пакетами прямо в новую MBTiles SQLite: промежуточный ZIP и второе дерево
    мелких файлов не создаются, а память не растёт с числом тайлов. Строка тайла
    переводится из OSM `y` в MBTiles/TMS один раз. После завершения выполняются
    sync файла и атомарное переименование; неполный stage удаляется, исходная
    Debug-подложка остаётся на месте. Provenance в `config.json` делает повторный
    запуск идемпотентным, а имя, видимость и взаимный порядок подложек сохраняются.
    Inactivity watchdog, явная отмена и подтверждённый выход с экрана хранилища
    закрывают cross-package descriptor до cleanup и не позволяют transfer lease
    блокировать последующий sync. Pre-registry Debug сохраняет исходную карту и
    папки тайлов до явного экспорта: фоновая shared-catalog migration там не
    запускается. Пользователь запускает этот перенос из «Хранилище подложек»,
    а не с экрана проекта.

IDs: `INV-LAYER-ORDER`, `INV-HOT-ADD-CONSISTENCY`, `INV-NO-TRACK-FLAGS`,
`INV-NGRC-PRESERVE`, `INV-LOCATION-CURSOR-TOP`, `INV-DEFAULT-OSM-BOTTOM`,
`INV-TRACK-LAYER-TOP`, `INV-COLLECTOR-RASTER-STYLES`,
`INV-COLLECTOR-LAYER-IDENTITY`, `INV-MULTIPOLYGON-REPAIR`,
`INV-GEOMETRY-SKETCH-WORKFLOW`, `INV-STAKEOUT-GUIDANCE`, `INV-MAP-CAMERA-CONTROLS`,
`INV-SPATIAL-CACHE-CONSISTENCY`, `INV-HIDDEN-VECTOR-TILE-IDENTIFY`,
`INV-MAPLIBRE-BACKEND-COMPATIBILITY`, `INV-MAPLIBRE-VIEW-LIFECYCLE`,
`INV-LEGACY-UNDERLAY-MIGRATION`.

## Изменение rendering pipeline

Перед правкой определить:

- cold start это, hot add, reorder или edit overlay;
- меняется модель `LayerGroup`, MapLibre style или оба уровня;
- кто вызывает `loadLayersLite`/full reload;
- существует ли style sibling в момент вставки;
- не теряется ли deferred reload после batch layer fill.
- не подтверждается ли асинхронный reload раньше `setMapLayersLoaded()` после
  проверки фактических MapLibre sources/layers.

Минимальный regression набор: `SMOKE-MAP-COLD-START`, `SMOKE-POLYGON-LABEL-ANCHOR`,
`SMOKE-LOCATION-CURSOR-TOP`, `SMOKE-NGRC-ORDER`,
`SMOKE-NGRC-PRESERVE`, `SMOKE-HOT-RASTER`, `SMOKE-LAYER-REORDER`,
`SMOKE-COLLECTOR-IMPORT`, `SMOKE-MULTIPOLYGON-REPAIR`, `SMOKE-GEOMETRY-SKETCH-WORKFLOW`,
`SMOKE-MAP-CAMERA-CONTROLS`, `SMOKE-NGW-LARGE-PULL-CACHE`,
`SMOKE-DEBUG-UNDERLAY-MIGRATION`.
Для изменения MapLibre dependency/backend дополнительно обязателен
`SMOKE-MAP-OPENGL-COMPATIBILITY`.
Для изменения `MapFragment` lifecycle дополнительно обязателен
`SMOKE-MAP-SURFACE-LIFECYCLE`.

## Производительность

Текущий анализ, уже реализованные механизмы и следующие гипотезы находятся в
[map-performance.md](map-performance.md). Документ не является разрешением
включать native URI, progressive preparation или viewport loading без отдельного
профилирования и regression matrix.

## Текущая позиция и точность

Сектор точности компаса отключён по умолчанию и управляется чекбоксом
«Настройки → Местоположение → Показывать сектор точности компаса».
`show_compass_accuracy_sector=false` совпадает в XML и runtime fallback.
MapFragment на resume передаёт выбор в
`MapDrawable.setCompassAccuracySectorVisible(boolean)`: заливка и оба
контура получают общую видимость. Выбор хранится отдельно от геометрии
и применяется также при создании style layers после reload. Стрелка
направления и метрический круг GPS независимы от переключателя. Расчёт
неопределённости и стили сохранены для доработки. Related `cursor` проверяет
default off, обе позиции чекбокса после resume, hot/lite reload и наличие
или отсутствие rendered features при сохранённой геометрии.

`user-location-source` содержит свежие Point, Polygon accuracy и при наличии
компаса Polygon heading. Круг в метрах лежит под сектором, сектор — под верхним
курсором; при expiry source очищается. Ширина сектора следует за неопределённостью
heading, а не за фиксированным углом. Геодезическая длина сектора рассчитывается
для 56 логических пикселей карты и обновляется после смены zoom. Курсор «Орбита-кольцо»
использует центрированный векторный ресурс с прозрачным окном диаметром 21 dp,
синей центральной точкой 5 dp с белой обводкой 2 dp и синим ободом с внешним радиусом 13 dp.
Обод утолщён на 1 dp наружу за счёт белого обода с прежним внешним радиусом
15.2 dp и тонкой тёмной кромкой. Сохранён
треугольник 16 × 14 dp с двойной обводкой в общем canvas 72 dp. Сектор имеет
заливку `#137DE8` / 0.24 и независимые LineLayer: белый 2.8 px / 0.75 под
синим 1.2 px / 0.85. Контур сохраняет заметность на светлых картах и пёстрых
снимках, не увеличивая метрический круг GPS. Accuracy Polygon получает отверстие
радиусом 10.5 логических пикселей, сектор начинается с радиуса 13 пикселей,
скрывая внутренний halo под непрозрачным ободом. Радиусы переводятся в метры
через текущую проекцию; наружная граница GPS accuracy остаётся метрической.
Заливка accuracy, полностью помещающаяся в окно, не создаётся. Так меньший
точечный объект на фиксе виден через окно без подкрашивания служебными заливками
и без перестановки слоёв. Кольцо остаётся на фиксе, треугольник
вращается по истинному компасному heading; без компаса — по GPS-курсу движущегося
фикса. Нет обоих направлений — только круг. `has_direction` выбирает ресурс,
`bearing` задаёт угол; legacy `type=stand/go` сохраняется. Выравнивание вращения
по карте учитывает bearing камеры, центр иконки закреплён явно. Трек рисуется
только по сохранённым сегментам, без линии к
текущему display fix.
Подробности: [GPS pipeline](location-pipeline.md).

## Общий payload офлайн-подложек

Новые NGRc преобразуются непосредственно в raster MBTiles общего каталога. Тот
же путь используют «Новая подложка из файла» и «Открыть локальный» после
классификации файла (NGRc, MBTiles, ZIP с NGRc-конфигом или одним MBTiles).
Диалог fill показывает имя файла, а не идентификатор документа, и прогресс
конвертации. MapLibre URL и Canvas tile directory разрешаются через `shared_underlay_id`, legacy слои сохраняют fallback. Слой подключается над OSM, hot-add и visibility остаются проектными. Хранилище, Y-flip, bounds и recovery описаны в [shared-underlays](shared-underlays.md).

Камера не опускается ниже `7.5`. Любая включённая raster-подложка доступна с
zoom `7`; выключенный слой остаётся выключенным. Raster-источники подключаются
напрямую, без proxy, производных копий и преобразования тайлов. В режиме фона
карты «Светлый» нижний MapLibre `BackgroundLayer` использует
`background-color: #FFFFFF` без текстуры. При смене режима фона слой
пересоздаётся, поэтому прежний `background-pattern` не остаётся активным;
нейтральный и тёмный режимы сохраняют свои узоры.

При dedup дерева NGRc с готовым MBTiles ссылка меняет `tms_type` вместе с ID:
MapLibre выбирает `mbtiles://` по формату целевого payload. Старые `levels`
удаляются, bounds берутся из ассета, а проектные min/max zoom сохраняются.
Ошибочные ссылки старых сборок исправляются до `LocalTMSLayer.fromJSON`.

При отказе или отмене загрузки `LayerFillWorker` может вызвать
`MapDrawable.deleteLayerByID` из фонового потока. Метод ставит удаление
источников, слоёв MapLibre и Java-реестров в main queue; вызов из UI остаётся
синхронным. Ошибка распознавания NGRc не должна превращаться в падение native
карты во время cleanup. Штатный `Mapnik.json` и пути JPEG/PNG/WebP обрабатываются
тем же потоковым импортом, без изменения географической схемы тайлов.

## Выбор объекта, допуск тапа и тесная панель

Выбор editable feature сразу включает его action mode, сохраняя highlighting
и предлагая форму/удаление/геометрию. Это не изменяет вершины автоматически.
Read-only feature показывает просмотр; сброс выбора завершает action mode и
освобождает layer lock. AndroidX Back закрывает активные инструменты/выбор до
обычного выхода с карты.

`MapTapGesture` на down фиксирует точку/время; maximum travel, включая историю
MOVE и возвращающийся drag, должен укладываться в max(12dp, system touch slop),
а время — быть короче system long-press timeout. Cancel, второй палец и UP без
DOWN не создают точку. В режиме placement host повышает MapLibre move threshold
до того же допуска, чтобы микросдвиг не сдвигал камеру; normal mode возвращает
исходный threshold. Правило общее для drawing, ruler и azimuth. Перетаскивание
вершин/азимутных endpoints использует прежние обработчики.

Линейка и азимут используют один `action_measurements` в общем map content.
Кнопка со значком линейки и подписью/tooltip «Измерения» открывает native PopupMenu
с тремя прямыми действиями: линейка, азимут от текущего местоположения и A→B.
Выбор не проходит через второй dialog. Меню не изменяет режим до нажатия;
callback проверяет живой Fragment, normal mode и отсутствие активной линейки.
Линейка/азимут скрывают общий вход до завершения; восстановленная линейка
также не показывает вторую кнопку. Старое show_ruler_measuring управляет
видимостью пункта линейки, оба режима азимута остаются доступны при false.

`MapControlRail` рассчитывает вместимость по обеим сторонам доступного прямоугольника,
сохраняет 48dp touch targets и переносит непоместившиеся действия в overflow;
пункт вызывает исходный control click. Состояние/visibility исходных кнопок
сохраняется. Физический планшет, display size/font scale, split screen и системные
insets ещё требуют [device smoke](../runbooks/device-smoke-tests.md).

## Изолированный обработчик правил

`ProjectScriptService` не создаёт карту, renderer, GPS и accounts. Ранние guards
в GISApplication/MainApplication выполняются до их обычной инициализации.
Основной процесс и OpenGL/MapLibre contract сохранены; QuickJS — отдельная
native-библиотека, не второй renderer. [Контракт](project-scripts.md).

## Синхронизация закрытых проектов

`ProjectSyncRunner` открывает фоновые карты с `activate=false`: они не заменяют
карту экрана и не создают MapLibre renderer. У каждого `MapEventSource` свой
main-thread handler; создание фонового экземпляра не перехватывает события
открытой карты. Фоновые add/delete и завершение импорта обновляют модель и
SQLite своего проекта без reload, выбора объектов или edit overlays на экране.
Отложенные callbacks сохраняют владельца через `SyncWorkspaceSession`;
уведомление векторного кэша применяется до закрытия фоновой базы.

Регрессия проверяется provider-тестами с одинаковыми именами слоёв в двух
проектах и `SMOKE-ALL-PROJECT-SYNC` с открытым черновиком. Ручные сценарии
отрисовки, инструментов и тяжёлого сетевого pull остаются отдельными проверками
из [device smoke](../runbooks/device-smoke-tests.md).

Создание карандашом, по местоположению и обходом использует общий предварительный
выбор категории FieldStyleRule. Выбор слоя не запускает геометрию или recorder
до принятия категории; при простом стиле сразу продолжается исходный способ
создания. Подробности: [тип объекта](feature-type-creation.md).

## Приёмка consumer без изменения renderer

Verification profile добавляет отдельную app identity и test Application,
сохраняя MapLibre renderer/runtime dependencies. LocationOrbitTest и native
map checks остаются частью Full на эмуляторе; firmware/RF accuracy искусственный
маршрут не подтверждает. Порядок тестов: [автотесты](../runbooks/automated-tests.md).

## Startup snapshot и delayed ANR — 2026-10-17

MapRuntimeDiagnostics.beginStartup использует тот же независимый observer, что
query/tile diagnostics. MainApplication отмечает фиксированные bootstrap stages;
наблюдатель не вызывает GIS, SQLite или renderer. Старый process snapshot читается
до первой новой записи, а общий journal startup/query/tile ограничен 16 KiB с
удалением старейшей записи. SDK cached/Backfillable события сопоставляются по их
времени, без подмены исходной сборки текущим reporter. Задержки загрузки R-tree и
native SIGABRT требуют новых данных; алгоритмы загрузки карты и лимиты local tiles
по прежним несимволицированным стекам не меняются.

Треки с 2026-10-25 используют чёткую линию 2 px, свечение 6 px/blur 2 и
выделение 4 px. Два общих sources и постоянное число style layers сохраняются.
Прежние NULL цвета становятся постоянными при upgrade shared store; подробнее
[управление треками](track-controls.md).

Линейные подписи — одна цельная надпись на видимую часть каждого SQL-объекта. Отдельный point source получает anchor/direction после camera settle без SQLite; clipping и predominant direction работают в фоне. Длина прямого отрезка больше не ограничивает слово. Контракт: [видимые линейные подписи](visible-line-labels.md).

## Готовность graphics fixture курсора

LocationOrbitTest перед прямым техническим fix ждёт завершения текущего app
layer apply и pending reload, готового Style и layout MapView. Post-load hook
штатно перечитывает GPS и может очистить преждевременно подставленную позицию.
При таймауте fixture сообщает camera, lifecycle/focus, loading flags, cached
fix и native source/rendered marker. Оба метода запускаются отдельно,
предел 30 секунд и pixel/heading/sector assertions сохранены. Настройки
и runtime GPS не изменены; одиночный Full timeout пока не доказывает
производственный дефект. Related cursor-rendering: оба метода PASS.

## Приоритет полевых линий и tap candidates

Data stack: обычные данные → треки → «Полевые линейные»/field_lines →
Point/MultiPoint → инструменты → GPS. Body и отдельные label sources
перемещаются вместе, включая logical groups. Это порядок рисования;
при обычном тапе все допустимые векторы и треки составляют один список,
без short circuit трека или точки. [Контракт и native проверки](track-controls.md).

## Камера графической fixture

LocationOrbitTest согласует native test viewport и сохранённую app camera через
MapDrawable.setZoomAndCenter: стартовый MapView invalidate через750ms использует
сохранённое положение. Это fixture setup для source/pixel assertions. Допуск
радиуса.08m, исходные provider fixes и30s readiness остаются прежними; timeout
показывает native и saved camera. Production camera API не меняется.

## Приватное исследование холодной отрисовки, 28 октября 2026

MOB-04: добавлен default-OFF локальный сборщик потребителей, включаемый только
`-PprivateResourceProfiling=true` в приватной сборке. Подробные tile wall/same-TID
CPU/counts, SDK frame/texture/buffer stats и процессная память сохраняются с явными
потерями/лимитами. Нативные allocations и CPU samples собираются отдельными
ограниченными окнами. Это диагностика, без долгосрочного кеша/изменения геометрии,
бизнес-данных или новой приёмки. Full/публикация остаются отложенными.
Контракт/ограничения: [производительность карты](../architecture/map-performance.md#приватное-исследование-холодной-отрисовки-28-октября-2026).
Порядок ручного сеанса: [runbook](../runbooks/manual-resource-profiling.md).
