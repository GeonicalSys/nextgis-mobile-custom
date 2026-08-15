---
title: app — Android-приложение Lisa/Belka
module_id: app
last_verified: 2026-08-15
---

# app — Android-приложение Lisa/Belka

## Назначение

Продуктовый Android-модуль: запускает GISApplication, предоставляет основной
UI и Map host, управляет брендами, preferences, release и self-hosted update.
Launcher и экраны intro/about получают иконку через flavor-ресурс
`app_launcher_icon`: Lisa использует `ic_launcher_lisa`, Belka — отдельный
`ic_launcher_belka` во всех пяти Android density buckets.

## Основные сценарии

- запуск приложения и открытие карты;
- вращение карты двумя пальцами только после явного разрешения кнопкой рядом с
  текущим местоположением; состояние и bearing сохраняются, запрет возвращает
  север вверх. Кнопка местоположения также возвращает север, поднимает zoom до
  `12`, если он был меньше, а без координаты переходит к охвату первого
  пригодного слоя, центрирует карту по нему и выставляет zoom `12`;
- управление слоями, edit/walk/track через библиотеки;
- ручное создание редактируемых точечных, линейных и площадных слоёв, импорт
  редактируемого локального GeoJSON в WGS 84, включая стандартные записи EPSG:4326,
  и упрощённый импорт каждой координаты KML/GPX как отдельной точки одного слоя;
  выбор слоя для нового объекта не скрывает выключенные редактируемые слои, а выбранный
  выключенный слой автоматически становится видимым и сохраняет это состояние;
- вынос выбранной точки, линии или границы полигона: виджет показывает стрелку,
  расстояние и переданную Android-точность без имени объекта/источника; стрелка всегда
  следует компасу телефона, а без компаса показывает сторону света и учитывает
  bearing явно повёрнутой карты при отрисовке стрелки;
  темп мягкого звука задаётся четырьмя порогами и не блокируется грубой accuracy-заглушкой
  mock-поставщика; foreground service сохраняет GPS и звук при выключенном экране;
- запись трека и геометрии обходом при валидном движении до 160 км/ч без выбора
  профиля, с отбрасыванием плохих и одиночных выбросов GPS; Network остаётся
  резервом и не смешивается со свежим пригодным GPS-потоком;
- crash recovery: запись трека возобновляется без диалога, затем recovery hub
  последовательно предлагает черновик обхода, обычной геометрии и формы атрибутов;
- редактирование геометрии: выход крестиком на нижней панели (`cancelEdits`);
- сохранение `GTMultiPolygon`: невалидный контур исправляется в один
  многокомпонентный feature до единственной формы атрибутов; простой Polygon и
  линии автоматически не исправляются; ручной MapLibre-конвертер сохраняет CRS
  контейнера и колец;
- идентификация объекта: список совпадений и верхняя панель используют
  `feature_label_field`; форма атрибутов в нижней панели доступна только если
  слой допускает редактирование (`isEditingAllowed`);
- выбор и переключение Collector projects;
- сохранение «Мои треки» наверху списка слоёв при создании и открытии карты;
- сохранение дефолтного `OpenStreetMap Standard aka Mapnik` внизу списка каждой
  карты, включая новый Collector workspace;
- завершение batch import только после фактического MapLibre style/source apply;
- индикатор синхронизации сверяется с прямым состоянием адаптера и останавливается,
  даже если lifecycle фрагмента пропустил финальный broadcast;
- добавление vector/raster NGW-слоя по прямому URL, включая проверенный guest fallback;
- получение ресурсов, подготовленных desktop QGIS-плагинами, только через
  NextGIS Web/Collector или явный import поддерживаемого portable artifact, без
  чтения desktop workspace;
- настройки приложения и Android permissions/services;
- проверка и установка Lisa/Belka updates с автоматическим продолжением после
  выдачи Android-разрешения на установку;
- экспорт/очистка сохранённых layer backups.

## Ограничения

- `lisa` и `belka` — отдельные product flavors.
- `MapFragment` должен реализовывать актуальный `MaplibreMapInteraction`.
- Не дублировать GIS model/storage из `maplib`.
- Не читать `Q:\standart_profiles`, `variables.py` или QGIS plugin mirrors:
  межпроектный runtime contract — NGW API/Collector либо явный portable import.
- Self-hosted update принимается только для того же flavor/application/signing
  identity и с увеличенным versionCode.
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
  отключать проверку для обхода ошибки.
- После выдачи разрешения update не продолжился: проверить
  `AppUpdateManager.resumePendingInstallation()`, `app_update_state` и вызов из
  `MainActivity.onResume()`/`SettingsActivity.onResume()`.
- Collector переключается неверно: `CollectorProjectRegistry` и project UID/map
  path, а не только UI dialog.
- «Мои треки» оказался внизу: проверить прямой порядок `LayerGroup` и
  `MainApplication.checkTracksLayerExist()`.
- После crash открылась форма вместо незавершённого обхода: проверить
  `MainActivity.maybeOfferCrashRecovery()`, `MapFragment.hasInterruptedWalkDraft()`
  и отсутствие silent cold restore в `onViewStateRestored()`.
- После crash пропала линия из обычного редактора: проверить HyperLog-события
  `MapFragment mode`, `GeometryDraft saved`, `CrashRecovery` и
  `GeometryDraft resumed`; координаты в журнал намеренно не попадают.
- Трек или обход перестал расти в автомобиле: проверить причины
  `LocationTrackFilter` и filter stats; provider должен соответствовать
  отдельной настройке режима, валидная скорость до 160 км/ч не отбрасывается,
    а `networkSuppressed` растёт только при свежем пригодном GPS.
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
