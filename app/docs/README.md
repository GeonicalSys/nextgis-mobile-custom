---
title: app — Android-приложение Lisa/Belka
module_id: app
last_verified: 2026-09-13
---

# app — Android-приложение Lisa/Belka

## Назначение

Продуктовый Android-модуль: запускает GISApplication, предоставляет основной
UI и Map host, управляет брендами, preferences, release и self-hosted update.
Launcher и экраны intro/about получают иконку через flavor-ресурс
`app_launcher_icon`: Lisa использует `ic_launcher_lisa`, Belka — отдельный
`ic_launcher_belka` во всех пяти Android density buckets. Карта использует
MapLibre Android `13.0.2` с явным OpenGL backend вместо Vulkan-default artifact.

## Основные сценарии

- запуск приложения и открытие карты;
- прямой импорт raster MBTiles и явный перенос локальных TMS-подложек из
  установленного старого Lisa Debug в активный Geonical-проект; bridge проверяет
  оба package/signing certificate, не переносит project/account/vector/track
  data и не удаляет источник;
- сохранение отрисовки карты после возврата из настроек/другого приложения,
  выключения экрана и пересоздания view: `MapFragment` передаёт MapLibre полный
  lifecycle, освобождает старый native renderer и запрашивает repaint при resume;
  после фактического применения project style выполняется короткий continuous-
  render burst с presentation invalidation и камерой без смещения; recovery
  считается успешным только после реально исчезнувшего foreground, а оставшийся
  загрузочный foreground снимается без full style reload;
  все поддерживаемые версии используют `SurfaceView`, который умеет пересоздать
  потерянный EGL-контекст; на Android 8–9 выключен tile prefetch. Искусственный
  потолок 5/30 FPS и постоянный `CONTINUOUS` на Android 8–9 не используются:
  в покое renderer остаётся в `WHEN_DIRTY`, recovery на всех API — короткий
  burst и возврат к прежнему dirty-режиму. Позднее касание уничтоженного
  view отбрасывается до обращения к MapLibre;
- вращение карты двумя пальцами только после явного разрешения кнопкой рядом с
  текущим местоположением; состояние и bearing сохраняются, запрет возвращает
  север вверх, а разрешённый rotate начинается сразу при одновременном
  двухпальцевом касании и не отключается начавшимся pinch. Кнопка местоположения также возвращает север, поднимает zoom до
  `12`, если он был меньше, а без координаты переходит к охвату первого
  пригодного слоя, центрирует карту по нему и выставляет zoom `12`;
- управление слоями, edit/walk/track через библиотеки;
- тяжёлые opt-in `local_vector_tiles` обслуживаются ограниченным loopback-
  сервером: максимум две одновременные сборки и очередь 16, с отменой старого
  поколения карты и retry вместо неограниченного роста потоков/heap;
- новый скетч начинается сразу после выбора слоя: точка или первый узел линии/
  полигона ставится в экранную проекцию центра камеры, следующие узлы добавляются
  тапами; midpoint-вставка работает для линий, полигонов и линейки, дополнения
  касанием/overflow и дублирующей нижней кнопки `+` нет;
  кнопка формы активна после появления геометрии и проходит тот же путь
  проверки/сохранения, что верхняя кнопка «Сохранить»; повторное нажатие не
  открывает вторую форму; успешное сохранение нового или существующего объекта,
  включая подтверждение формы атрибутов, полностью завершает редактирование,
  снимает выделение и возвращает стандартный экран карты; Undo/Redo отменяет
  или возвращает одну реальную правку за одно нажатие и хранит до 100 изменений
  без служебных снимков выбора узла
  и callback-снимков, отличающихся только CRS; в инструменте измерения те же
  кнопки по одной отменяют и возвращают добавление или перенос точки активной
  MapLibre-линейки и сохраняют её состояние при пересоздании экрана;
- ручное создание редактируемых точечных, линейных и площадных слоёв, импорт
  редактируемого локального GeoJSON в WGS 84, включая стандартные записи EPSG:4326,
  и упрощённый импорт каждой координаты KML/GPX как отдельной точки одного слоя;
  выбор слоя для нового объекта не скрывает выключенные редактируемые слои, а выбранный
  выключенный слой автоматически становится видимым и сохраняет это состояние;
- вынос выбранной точки, линии или границы полигона и отдельные измерения
  «текущее положение → точка»/«точка A → точка B»: виджет показывает стрелку,
  геодезическое расстояние, числовой магнитный азимут по WMM2025 и переданную
  Android-точность без имени объекта/источника; магнитный rotation-vector heading
  сглаживается круговым фильтром, а без компаса стрелка учитывает bearing явно
  повёрнутой карты; свободные точки/линия являются временным MapLibre-overlay,
  точки A/B и live-цель корректируются перетаскиванием;
  темп мягкого звука задаётся четырьмя порогами и не блокируется грубой accuracy-заглушкой
  mock-поставщика; foreground service сохраняет GPS и звук при выключенном экране;
- карта показывает свежую позицию GPS/Network с кругом точности в метрах,
  поддерживает approximate permission и скрывает позицию после 8 секунд без
  пригодных измерений. Resume читает snapshot общего источника записи, без
  возврата к координате выключения экрана. Трек и обход сохраняют только GNSS;
  запись сглаживается с учётом остановок, пешеходов и автомобилей до 160 км/ч.
  Пропуски GPS разделяют трек на сегменты, а обход требует явного продолжения.
  Старые переключатели источников мигрируют в auto display / GNSS recording;
  настройки интервала и расстояния относятся к сохранению точек.
  См. [контракт GPS](../../docs/architecture/location-pipeline.md).
- crash recovery: запись трека возобновляется без диалога, затем recovery hub
  последовательно предлагает черновик обхода, обычной геометрии и формы атрибутов;
  восстановленный Polygon сохраняет заливку, внешнее кольцо и только реальные
  отверстия без удвоения узлов; после cold Continue дополнения обходом красный
  контур и заливка не мерцают, а Stop возвращает редактируемые вершины; раннее
  Continue ждёт принадлежности edit sources текущему MapLibre style, а вход в
  обход удаляет параллельный черновик обычного редактора того же скетча;
  восстановленные LineString/MultiLineString показываются без polygon fill;
- редактирование геометрии: выход крестиком на нижней панели (`cancelEdits`),
  причём для нового объекта сначала требуется подтвердить удаление скетча; после
  подтверждения приложение возвращает сразу на карту, а не в пустую панель
  выбора действия; у линии/полигона выбранная вершина красная, а её следующая
  вершина и редактируемый сегмент в пределах той же части/кольца оранжевые;
- измерение площади линейкой всегда подписывается в гектарах;
- сохранение `GTMultiPolygon`: невалидный контур исправляется в один
  многокомпонентный feature до единственной формы атрибутов; простой Polygon и
  линии автоматически не исправляются; ручной MapLibre-конвертер сохраняет CRS
  контейнера и колец; одноточечный/слишком короткий контур останавливается до
  repair с сообщением «Недостаточно точек»;
- невалидный обычный Polygon остаётся в редакторе; самопересечение получает
  отдельное сообщение «Обнаружено самопересечение», а не ложное «Недостаточно точек»;
- идентификация объекта: список совпадений и верхняя панель используют
  `feature_label_field`; форма атрибутов в нижней панели доступна только если
  слой допускает редактирование (`isEditingAllowed`); слой с режимом
  `local_vector_tiles` отдаёт локальные атрибуты даже при выключенной отрисовке,
  не включая её, тогда как выключенный классический слой пропускается;
- выбор и переключение проектов по списку только из имён; раздел
  «Настройки → Проект» с Web GIS-реквизитами, созданием пустого local workspace,
  локальным переименованием и удалением локальной копии; чистая установка до
  первой карты уже имеет активный «Локальный проект», а обновление один раз
  добавляет туда прежние standalone-слои без удаления их исходной копии;
- сохранение «Мои треки» наверху списка слоёв при создании и открытии карты;
- сохранение дефолтного `OpenStreetMap Standard aka Mapnik` внизу списка каждой
  карты, включая новый Collector workspace;
- завершение batch import только после фактического MapLibre style/source apply;
- продолжение незавершённого Collector import только внутри записанного project
  UID: чужой workspace не получает его SQLite-таблиц, а после возврата удаляются
  только app-marked unpublished layer stages; legacy unmarked каталоги остаются;
- индикатор синхронизации сверяется с прямым состоянием адаптера и останавливается,
  даже если lifecycle фрагмента пропустил финальный broadcast;
- ручная синхронизация запускается только для NGW-слоёв активного проекта;
  повторный запуск и переключение проекта блокируются на всё время sync/fill;
- перед account sync приложение безопасно сводит старые managed-дубликаты слоя
  к одной копии только после backup и только при отсутствии pending changes;
  edited-копии блокируют sync без автоматического удаления;
- ручной и системный sync используют `dataSync` foreground execution, а
  durable marker после process death запрашивает повтор только для того же
  account/workspace; clean finish снимает marker;
- массовый incremental pull не создаёт построчные spatial-cache уведомления:
  R-tree перестраивается один раз, а style props применяются к отдельному
  snapshot, не к live MapLibre feature;
- полный untracked snapshot потоково применяется из временного файла одной
  SQLite-транзакцией, отдельные невалидные геометрии пропускаются без удаления
  их прежних локальных копий, а MapLibre получает один reload после account-pass;
- добавление vector/raster NGW-слоя по прямому URL, включая проверенный guest fallback;
- получение ресурсов, подготовленных desktop QGIS-плагинами, только через
  NextGIS Web/Collector или явный import поддерживаемого portable artifact, без
  чтения desktop workspace;
- настройки приложения и Android permissions/services;
- проверка и установка Lisa/Belka updates с автоматическим продолжением после
  выдачи Android-разрешения на установку;
- экспорт/очистка сохранённых layer backups.

## Ограничения

- Geonical не может читать private storage Debug напрямую. Перенос доступен
  только после установки совместимой Debug bridge-сборки с прежним сертификатом
  и только в уже выбранный активный проект. Версии `3.0.3.2`/`3.0.3.3` не дают
  надёжной source-project identity для автоматического сопоставления.
- Export provider отдаёт только перечисленные локальные TMS-подложки через
  read-only content URI; accounts, credentials, registry, vector layers, tracks
  и feature data не входят в контракт.
- `lisa` и `belka` — отдельные product flavors.
- `app`, `maplibui` и `maplib` должны разрешать один MapLibre backend:
  `org.maplibre.gl:android-sdk-opengl:13.0.2`; generic `android-sdk` версии 13
  использует Vulkan и возвращать его в production нельзя.
- `MapFragment` должен реализовывать актуальный `MaplibreMapInteraction`.
- Каждый созданный MapLibre `MapView` должен получить согласованную пару
  `onCreate/onStart/onResume` и `onPause/onStop/onDestroy`; старый view нельзя
  оставлять привязанным к `MapDrawable` после `onDestroyView`.
  `maplibre_renderTextureMode=false` на всех API; на API 26–28 tile prefetch
  выключен. Потолок FPS и постоянный `CONTINUOUS` на Android 8–9 не
  используются.
- Не дублировать GIS model/storage из `maplib`.
- Не читать `Q:\standart_profiles`, `variables.py` или QGIS plugin mirrors:
  межпроектный runtime contract — NGW API/Collector либо явный portable import.
- Self-hosted update принимается только для того же flavor/application/signing
  identity и с увеличенным versionCode. На Android 9–10 пустой archive
  `SigningInfo` дополняется legacy `signatures`, после чего сертификат всё равно
  сверяется по SHA-256 с manifest и установленным пакетом.
- Ожидание специального разрешения на установку хранится как одноразовый
  app-private pending manifest; после возврата manifest и APK проверяются снова.
- Production version принадлежит `defaultConfig`; Lisa Debug переопределяет
  version через `androidComponents.onVariants`. `versionCode`/`versionName` в
  `buildTypes` запрещены на AGP 9.x.
- Update repository использует ветки `lisa`, `belka`, `debug` непосредственно
  под `https://apps-geonical.ru/lisa-mobile`; сегмента `stable` в URL нет.
- Для каждого build variant один account type обязан одновременно использоваться
  в runtime `MainApplication`, `AccountAuthenticator` и `SyncAdapter`; release
  использует `com.nextgis.account.geonical`, debug — `com.nextgis.account.debug`.
- Реальные DSN, client secrets и signing credentials не входят в docs.
- Sentry оставляет crash screenshots, но не собирает interaction breadcrumbs и
  view hierarchy; traces/profiling в production семплируются с долей `0.05`.

## Диагностика

- Карта/слои: сначала проверить callbacks `MapFragment` и состояние
  `GISApplication`, затем rendering docs.
- Чёрная карта при видимых Android-кнопках после возврата с другого экрана:
  проверить последовательность `MapLibreMapView.onStart/onResume`, первый кадр
  после resume, `MapLibre render recovery started/completed`, последующие
  `onPause/onStop/onDestroy` и отсутствие старой ссылки `MapDrawable` на
  уничтоженный view. `completed` не должен содержать одновременно
  `fully=false` и `loadingForeground=true`. Запись
  `cleared stale loading foreground` означает, что
  project layers уже были применены, но MapLibre не снял свой loading foreground
  после ограниченной серии repaint.
- На Android 8–9 после recovery не должно быть постоянного `CONTINUOUS` и
  `setMaximumFps`; в логе остаётся `tilePrefetch=false` и общий burst
  `MapLibre render recovery started/completed`.
- Полностью чёрный экран вместе с Android-панелями на Android 8–9: проверить
  `onLowMemory`, системные `GL_OUT_OF_MEMORY`/`EGL_CONTEXT_LOST`, запись
  `MapLibreMapView renderer=SurfaceViewMapRenderer` и `tilePrefetch=false`.
  `TextureViewMapRenderer` на этих API является неверной конфигурацией: его
  render thread не пересоздаёт surface после потери EGL-контекста.
- Crash `No Vulkan compatible GPU found` при открытии карты означает неверный
  MapLibre runtime artifact: штатный APK использует OpenGL и не требует Vulkan.
- Карта не вращается: проверить состояние кнопки вращения рядом с геолокацией и
  `map_rotation_enabled`; запрет вращения является штатным значением по умолчанию.
- Кнопка геолокации не перешла к слою без GPS: проверить, что хотя бы один слой
  имеет конечный и инициализированный `GeoEnvelope`; пустой проект сохраняет
  обычное сообщение об отсутствии местоположения.
- Неправильный бренд или launcher: проверить `app/build.gradle`,
  `app/src/<flavor>/res/values/launcher_icon.xml`, соответствующие
  `drawable-*` flavor resources и manifest metadata.
- Version change: запускать `tools\verify-apk-version-matrix.ps1`; не определять
  версию debug по basename APK.
- Update отклонён: проверить schema, branch/channel, identity, version,
  versioned URL, size/hash/certificate и доступность branch manifest; не
  отключать проверку для обхода ошибки. Запись `Updater APK validation rejected`
  показывает этап отказа; нулевое число archive certificates на Android 9–10
  означает сбой обоих PackageManager-представлений подписи.
- После выдачи разрешения update не продолжился: проверить
  `AppUpdateManager.resumePendingInstallation()`, `app_update_state` и вызов из
  `MainActivity.onResume()`/`SettingsActivity.onResume()`.
- Collector переключается неверно: `CollectorProjectRegistry` и project UID/map
  path, а не только UI dialog.
- После зависшего/оборванного Collector fill появились лишние `layer_*` или
  таблицы другого проекта: проверить project UID import journal, parent target
  group, `.layer-fill-partial` и обе `layers.db`; автоматически очищаются только
  новые помеченные unpublished stages, не legacy каталоги.
- После чистой установки нет «Локального проекта» или старые ручные слои не
  появились в его workspace: проверить ранний вызов
  `ensureInitialLocalProject()` до `GISApplication.onCreate()`, migration marker,
  `project.json` и наличие исходной `default.ngm`.
- Переключение доступно во время sync: проверить lease
  `ProjectOperationCoordinator` от `OfflineSyncIntentService.startActionFoo()` до
  конца последнего account и проверку в `MainActivity`.
- Импорт Collector во время sync показывает общую ошибку: ожидать модальное
  сообщение и отсутствие новой записи проекта до завершения операции.
- Удалённый проект исчез, но показана ошибка: fallback-карта не должна повторно
  открываться executor-потоком удаления; переход на `MainActivity` открывает её
  в UI-потоке после успешного результата.
- Вылет `notify_insert → GeoEnvelope.width/GeometryRTree`: это регрессия bulk
  incremental pull; проверить единственную итоговую cache rebuild и отсутствие
  `LinkedTreeMap` ошибок при обновлении style.
- Удаление проекта затронуло Web GIS: это регрессия — штатный путь удаляет только
  active workspace после backup gate и не вызывает remote delete/account API.
- «Мои треки» оказался внизу: проверить прямой порядок `LayerGroup` и
  `MainApplication.checkTracksLayerExist()`.
- Курсор положения движется, но линия трека не появляется: сначала проверить
  `TrackerService.onStartCommand`. Если до Stop нет фактического Start и
  `accepted=0`, это lifecycle запуска сервиса, а не отбрасывание GPS-фильтром.
- Форма точки поверх активного обхода восстанавливается первой по UUID владельца.
  Проверить `MainActivity.maybeOfferCrashRecovery()` и `WalkSessionStore`; старый
  part-only обход отдельно проходит legacy adoption.
- После crash пропала линия из обычного редактора: проверить HyperLog-события
  `MapFragment mode`, `GeometryDraft saved`, `CrashRecovery` и
  `GeometryDraft resumed`; координаты в журнал намеренно не попадают.
- Crash `GeoJsonSource.setGeoJson` сразу после раннего Continue означает регрессию
  current-style readiness: в журнале сначала допустим `renderer attach deferred`,
  затем обязателен `renderer attached after style load` без второго recovery prompt.
- Трек или обход перестал расти в автомобиле: проверить причины
  `LocationTrackFilter` и наличие свежего GNSS. Network никогда не записывается;
  проверить паузу обхода и согласованность последовательности после потери GPS.
- Вынос молчит или показывает «Ожидание GPS»: проверить возраст fix, provider/mock-флаг,
  системную громкость навигации, уведомление активного выноса и owner lease частого потока;
  accuracy mock GPS отображается, но не блокирует звуковую зону. Координаты и расширенные
  поля mock `Location` в прикладной debug-журнал не записываются.
- Save восстановленной формы упал после успешного insert: проверить
  `FormSave result ready`, `FormSave result received` и разрешение `layer_id`
  из активной карты; cold restore не должен требовать старый `mSelectedLayer`.
- Форма предлагается сразу после успешного Save: проверить terminal guard
  `ModifyAttributesActivity.clearFormDraft()` перед последующим `onPause()`.
- OSM исчез из Collector-проекта или поднялся выше остальных слоёв: проверить
  `MainApplication.ensureBaseOsmLayerAtBottom()` и индекс `0` активной карты.
- Sync завершён в журнале, но иконка продолжает вращаться: проверить
  `NGWSyncService.isSyncStarted()` и reconciliation в `LayersFragment`; состояние
  адаптера не должно зависеть от доставки broadcast.
- Полевые точки выбираются, но появились только после restart: проверить
  completion post-fill reload и `MapLibre post-load verification`; pending-флаг
  очищается только из `MapFragment.setMapLayersLoaded()`.
- Выключенный `local_vector_tiles` не идентифицируется: проверить
  `LayerIdentifyPolicy` и сохранённый render mode; identify не должен менять
  видимость слоя.
- Самопересекающийся мультиполигон не перешёл к форме: проверить
  `MultiPolygon geometry repair failed`; при отказе пользователь должен остаться
  в редактировании геометрии без частично созданного объекта. Сообщение
  `converted repair is empty or invalid` для обычной «бабочки» означает
  регрессию передачи CRS.
- URL не импортируется: проверить parser, совпадение server URL с аккаунтом,
  response code, тип ресурса и `data.read`; отсутствие `data.write` — read-only,
  а не ошибка импорта.
- Локальный GeoJSON сообщает о неподдерживаемой системе координат: проверить
  `crs.properties.name`; отсутствие `crs`, CRS84 и распространённые имена
  EPSG:4326 являются WGS 84 и должны импортироваться.
- На Android 16 локальный GeoJSON дошёл до SQLite, но импорт прерван сообщением
  о safety level/transaction: проверить порядок bulk-write PRAGMA в `maplib`;
  настройки БД должны применяться до транзакции.
- Ручной или импортированный локальный слой нельзя редактировать: проверить его
  `is_editable` в конфигурации; для нового обычного слоя значение должно быть `true`.
- NGW-слой стал read-only после простого просмотра свойств: направление sync не
  должно меняться от начального события списка; managed Collector-слой с
  разрешённым редактированием можно вернуть из «только с сервера» в режим «в
  обе стороны» без удаления.
- KML/GPX не загружается: проверить расширение документа и `CoordinatePointParser`.
  Поддерживаются KML `coordinates` и GPX `wpt`/`rtept`/`trkpt`; пустой или
  повреждённый XML должен дать обычное сообщение и не оставить неполный слой.
- Веб ГИС принимает логин, но не появляется: сверить merged-значения
  `nextgis_accounts_auth`, `nextgis_accounts_auth_type` и runtime account type.
  Локальный отказ `AccountManager` должен оставить форму открытой и попасть в HyperLog.

## Проверки

Структурированный список: [manifest.yaml](manifest.yaml). При изменении app API
или общих resources обязательны обе release-сборки; при изменении версии —
полная debug/release version matrix.

Экспорт трека использует `ExportFileProvider`: MIME самого URI и share Intent
совпадает с GPX, поэтому системный получатель не должен дописывать `.bin`.
Проверять нужно также имя файла и anonymous MIME lookup на Android 16.

## Независимый обход и создание точки

`MapFragment` возвращает обычные меню после передачи геометрии сервису,
подключает пассивный preview и отдельную `WalkRecordingPanel`. UUID точки
записывается до выбора слоя; все walk-команды остаются закрыты до Save/Cancel.
`MainActivity` восстанавливает принадлежащую этой сессии форму раньше
дублирующего черновика геометрии. Полный контракт и ограничения проверки:
[crash recovery](../../docs/architecture/crash-recovery.md).

Курсор следует текущей сглаженной позиции независимо от ожидания начала линии;
круг не обозначает расстояние от стоянки. Подтверждение ходьбы и новые регрессии:
[GPS pipeline](../../docs/architecture/location-pipeline.md).
