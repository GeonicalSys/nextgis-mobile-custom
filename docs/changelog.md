---
title: История документационной системы
type: changelog
last_verified: 2026-08-22
related_code:
  - docs
---

# История документационной системы

## 2026-08-22

- Cold Continue незавершённого дополнения полигона обходом теперь сохраняет
  property-bearing MapLibre edit feature, атомарно восстанавливает заливку,
  красный контур и скрытый vertex cache. Контур не мерцает при новых GPS-точках,
  а после Stop вершины снова сразу доступны для редактирования.

## 2026-08-21

- История скетча расширена с 10 до 100 отмен; снимки с одинаковым координатным WKT
  от выбора узла и MapLibre callbacks, отличающиеся только CRS, больше не занимают
  шаги. Undo/Redo меняет одну реальную правку за одно нажатие без пустого шага на
  границе или переполнении.
- Одноточечный скетч MultiPolygon теперь распознаётся до topology repair и при
  сохранении показывает «Недостаточно точек» вместо ошибки исправления геометрии.
- Восстановление Polygon из WKT теперь разделяет кольца по уровню скобок и не
  дублирует внешний контур как дырку: после сбоя скетч сохраняет одну заливку и
  исходные узлы. Невалидный обычный Polygon остаётся открыт для исправления, а
  самопересечение сообщается отдельно вместо ложного «Недостаточно точек».
- Отмена нового скетча крестиком теперь требует подтверждения; преобразование
  экранных вершин линий и полигонов переведено с устаревающего legacy display на
  актуальную MapLibre-проекцию, чтобы первая вершина следовала текущему центру.
- Первая вершина нового линейного/полигонального скетча теперь берётся из
  экранной проекции центра камеры; отмена нового объекта сразу возвращает карту,
  дублирующая нижняя кнопка `+` удалена, площадь линейки выводится в гектарах.
- `INV-GEOMETRY-SKETCH-WORKFLOW` переведён со стартовых примитивов на один
  центральный узел и tap/midpoint-вставку для линий, полигонов и линейки;
  промежуточная кнопка `+`, touch-append и overflow убраны. Обход теперь доступен
  с первого узла, хранит part/ring/insertion target и вставляет GPS после
  выбранной вершины с актуальным live-хвостом.
- Ручное восстановление Polygon/MultiPolygon замыкает GeoJSON-кольца до
  извлечения вершин, поэтому заливка и порядок узлов не расходятся после crash.
  `INV-MAP-CAMERA-CONTROLS` дополнен параллельным pinch/rotate и порогом `0.5°`
  для немедленного двухпальцевого поворота.
- Production-версия Lisa/Belka поднята до `3.1.2.10` / `versionCode 204` после
  публикационного dry-run, подтвердившего, что `202` уже занят; debug остаётся
  `3.1.2.9` / `203`, release `maplib.VERSION_NAME` обновлён синхронно.
- `INV-PROJECT-OPERATION-EXCLUSION` и `SMOKE-PROJECT-MANAGEMENT` дополнены
  атомарным отказом импорта Collector во время sync/fill с модальным сообщением,
  а также успешным удалением workspace без повторного открытия карты из worker.
- Добавлены `INV-HIDDEN-VECTOR-TILE-IDENTIFY` и
  `SMOKE-HIDDEN-VECTOR-TILE-IDENTIFY`: выключенный `local_vector_tiles` остаётся
  доступным для локального просмотра атрибутов, не включая отрисовку; выключенный
  classic layer по-прежнему исключён.
- Актуальный official `nextgis_mobile_android/master` повторно проверен
  21 августа 2026 года: его identify всё ещё безусловно пропускает
  `visible=false`, а проектного registry и `local_vector_tiles` в official нет.

## 2026-08-20

- Добавлены `INV-GEOMETRY-SKETCH-WORKFLOW` и
  `SMOKE-GEOMETRY-SKETCH-WORKFLOW`: новый Polygon/MultiPolygon начинается с
  одного квадрата, полигональные меню повторяют LineString без команд частей и
  отверстий, а кнопка формы нового объекта использует общий Save/repair handoff.
- Закреплён no-op контракт свойств NGW-слоя: просмотр вкладок не меняет
  двустороннюю синхронизацию на «только с сервера», а editable Collector-слой
  можно вернуть из server-only режима без удаления и повторного импорта;
  добавлен `SMOKE-LAYER-SYNC-SETTINGS`.
- Добавлен `INV-SPATIAL-CACHE-CONSISTENCY` и
  `SMOKE-NGW-LARGE-PULL-CACHE`: массовый incremental NGW pull не создаёт
  построчный broadcast storm, R-tree сериализует операции и пересобирается один
  раз, а MapLibre style refresh работает с независимым snapshot свойств.
- Повторно проверен официальный `nextgis/android_maplib`: по состоянию на
  20 августа incremental bulk, полная синхронизация R-tree и безопасный
  `tighten()` в official отсутствуют.
- Добавлен `INV-PROJECT-OPERATION-EXCLUSION`: ручная sync работает только с
  активной картой, резервирует workspace до конца всех последовательных account,
  блокирует switch/mutation и повторный полный запуск; fill/rebuild могут
  присоединиться только к тому же проекту.
- Registry проектов переведён на schema `2` с `WEBGIS`/`LOCAL` и атомарными
  `project.json`: зафиксированы создание пустого local workspace, локальное
  переименование, backup-gated device-only delete и fallback после удаления
  последнего проекта. Быстрый picker содержит только имена, реквизиты находятся
  в «Настройки → Проект».
- Schema rebuild описан как staged replacement с сохранением старого слоя до
  успешной записи нового и circuit breaker: две попытки неизменного fingerprint
  за 24 часа, cooldown 10 минут, статус и reset в настройках проекта.
- Добавлены `SMOKE-PROJECT-MANAGEMENT`, `SMOKE-SCHEMA-REBUILD-GUARD` и
  `SMOKE-NGW-IMPORT-BACK`; уточнены current-project sync, FGS timeout/resume и
  переход toolbar Back по дереву NGW.
- Official NextGIS Mobile/MapLib/MapLibUI/EasyPicker HEAD повторно проверены;
  hashes от 30 июля не изменились.

## 2026-08-16

- Artwork Belka в `ic_launcher_belka` заменена на круговую эмблему белки;
  идентификаторы ресурсов и номер версии не менялись.

## 2026-08-15

- Belka получила новую artwork launcher-иконки в `ic_launcher_belka`
  (`mdpi`–`xxxhdpi`); идентификаторы ресурсов, Lisa-брендинг и номер версии
  не менялись.
- Версия форка поднята до `3.1.2.9`: Lisa/Belka Release `versionCode` 202,
  Lisa Debug `versionCode` 203; maplib `VERSION_NAME` синхронизирован с
  приложением для debug и release.
- Lisa и Belka получили обновлённые отдельные launcher-иконки для
  `mdpi`–`xxxhdpi`; flavor-ссылка `app_launcher_icon` использует их также на
  intro/about, а Belka больше не наследует общий launcher из `app/src/main`.
- Версия форка поднята до `3.1.2.8`: Lisa/Belka Release `versionCode` 200,
  Lisa Debug `versionCode` 201; maplib `VERSION_NAME` синхронизирован с приложением
  для debug и release. Пользовательский раздел «В разработке» оформлен как
  release notes `3.1.2.8`.
- Добавлен контракт `INV-MAP-CAMERA-CONTROLS` и полевой
  `SMOKE-MAP-CAMERA-CONTROLS`: вращение карты по умолчанию запрещено и включается
  отдельной кнопкой; запрет и геолокация возвращают север вверх, геолокация
  поднимает zoom минимум до `12`, а без координаты открывает охват первого
  пригодного слоя, центрируется по нему и ставит zoom `12` независимо от размера
  охвата. Для выноса без компаса стрелка учитывает bearing карты, сохраняя
  абсолютную текстовую сторону света.
- Зафиксировано разделение прав редактирования и видимости: выключенный допустимый
  векторный слой остаётся в выборе для нового объекта, а после выбора автоматически
  включается и сохраняет видимость до запуска редактора.
- Добавлены `INV-LOCAL-VECTOR-LAYERS`, `LOCAL-VECTOR-LAYERS` и
  `SMOKE-LOCAL-VECTOR-LAYERS`: ручные/локальные vector layers редактируемы по
  умолчанию, а импорт GeoJSON принимает неявный WGS 84, CRS84 и распространённые
  записи EPSG:4326/3857 при сохранении отказа для других систем координат.
  Зафиксирован Android 16-контракт: bulk-write PRAGMA выполняются только до
  начала SQLite-транзакции.
- Контракт локальных слоёв расширен упрощённым импортом KML/GPX: потоковый parser
  сохраняет порядок всех валидных WGS 84 координат как отдельные точки одного
  редактируемого слоя, переносит доступные name/time/elevation, блокирует DTD и
  не обещает сохранение исходной геометрии, стиля или структуры маршрута.
- Зафиксирован lifecycle-контракт ошибок выбора NGW-ресурсов: сетевой сбой
  сообщает об ошибке через живую Activity, а закрытый экран не получает новый
  диалог; добавлен отдельный smoke для ответа `5xx` и выхода во время запроса.
- Зафиксирован контракт foreground-выноса координат: ближайшая точка/граница,
  WGS84-расстояние и азимут, временный частый GPS, видимая reported accuracy,
  дистанционные звуковые зоны для GPS/mock fix и компас телефона без GNSS course. Lifecycle не
  прерывает GPS и звук при выключенном экране: foreground service, постоянное уведомление и
  partial wake lock живут до явной остановки; полевой smoke охватывает RTK-приёмник,
  Bluetooth/audio mode, фоновую работу и резерв по сторонам света.
- Добавлены дефолты и одноразовая migration для `photo_overlay_enabled=true` и
  `photo_overlay_use_object_coords=true`; после migration прямой выбор пользователя не перезаписывается.
- Высокий тон выноса заменён на затухающий низкий PCM-щелчок с дополнительным цифровым
  усилением `×3` и ограничением на полной шкале;
  диагностическая запись координат и полей mock `Location` отключена.
- В registry добавлены `INV-STAKEOUT-GUIDANCE`, `STAKEOUT-GUIDANCE`, пять
  preference-ключей, `AUTO-APP-DEBUG` и `SMOKE-STAKEOUT`; обновлены module packs
  `app` и `maplib` и каталог отличий от официального приложения.

## 2026-08-14

- Уточнён publisher-contract `vector_only`: служебное `idqgs BIGINT` входит в
  mobile `fields[]` как `LONG` (`type: 13`), чтобы локальная схема совпадала с
  NGW. Android-код не меняется; направление остаётся NGW → Android.

## 2026-08-12

- Git-регламент ИИ-агентов унифицирован в root и Android-библиотеках; проверка
  новых submodule pointers подтвердила отсутствие изменений runtime и перечня
  отличий от официального приложения.
- Канонические GitHub-ссылки Android root и submodule переведены из личного
  namespace `GeonicalSystem` в организацию `GeonicalSys`; upstream NextGIS не
  изменён.

## 2026-08-01

- Canonical desktop, Plugins и Android workspaces перенесены под
  `C:\dev\lisa`; старые пути оставлены только для rollback.
- Agent workflow теперь начинает межкомпонентную работу с общего Git preflight.

## 2026-07-30

- Версия форка поднята до `3.1.2.7`: Lisa/Belka Release `versionCode` 198,
  Lisa Debug `versionCode` 199; maplib `VERSION_NAME` `3.1.2.7` для debug и
  release.
- Общий GPS-фильтр трека и обхода больше не ограничен пешеходными 25 км/ч:
  валидные последовательности сохраняются до 160 км/ч без профилей движения.
  Проверка идёт от последнего принятого фикса, длинный интервал не удаляет
  буфер, а одиночные выбросы отбрасываются с учётом accuracy. Источники трека и
  обычного местоположения теперь строго следуют своим настройкам. При совместно
  включённых GPS и Network свежий пригодный GPS имеет приоритет, а Network
  автоматически возвращается как резерв через 12 секунд без GPS.
- Невалидная геометрия слоя `GTMultiPolygon` перед формой атрибутов исправляется
  через JTS в один валидный мультиполигон: самопересечение может стать несколькими
  частями, но feature и форма остаются одними. Неисправимый результат остаётся в
  редакторе; простые Polygon и линейные слои намеренно не затронуты. Исправлена
  потеря CRS контейнера при ручном MapLibre-редактировании, из-за которой ранее
  отбрасывался результат исправления любого самопересечения.
- Локальное включение vector layer, который был `visible=false` при import,
  сверяется с текущим MapLibre style: при отсутствии live source/render layer
  выполняется data reload даже при наличии старой process-cache записи. Для
  появления точек больше не требуется менять server `visible` и запускать sync.
- Collector batch fill атомарно резервирует уникальные UUID-каталоги слоёв и
  прекращает задачу на первой SQL-ошибке вместо продолжения по общей/неверной
  таблице. Post-fill reload подтверждается только после фактического появления
  видимых vector sources/layers в MapLibre и имеет один ограниченный полный retry.
- Sync adapter напрямую публикует process-wide started/finished state во всех
  путях завершения; layer drawer сверяет с ним анимацию, поэтому пропущенный
  lifecycle broadcast больше не оставляет бесконечный spinner.
- Временный сбой NGW/external PostGIS при pull векторного слоя больше не обрывает
  весь проход: после остальных слоёв выполняется отложенный повтор только
  проблемных слоёв с минимальной паузой 15 секунд; исчерпанный серверный retry
  получает отдельное пользовательское сообщение.
- Выбранное в «Настройки слоя → Поля» поле имени объекта сохраняется в
  `config.json` как `feature_label_field`; identify-список нескольких объектов,
  верхняя панель и таблица атрибутов используют один резолвер с fallback на
  `_id`. Старый per-layer `layer_label` остаётся совместимым.

## 2026-07-29

- `local_vector_tiles` расширен на read-only `GTPoint` с простым круговым
  маркером и подписью из одного поля/фиксированного текста; rule-style, custom
  icon, template и editable варианты сохраняют classic fallback.
- Collector layer identity защищена от потери при восстановлении R-tree:
  `config.json` не записывается до полной загрузки NGW-полей, а последняя
  целая identity хранится в per-layer backup.
- Managed-layer HTTP 404 больше не переводит слой в локальный unmanaged.
  Composition sync сверяет все физические слои по `account + remote_id`,
  восстанавливает единственную потерянную origin-метку и блокирует apply при
  неоднозначности вместо повторного импорта.

## 2026-07-25

- Crash recovery: Save from a cold-restored new-feature form now returns layer and
  new-row identity to `MapFragment`; the map resolves the active layer and reloads
  the persisted feature without dereferencing the pre-crash selection or temporary
  MapLibre edit session. If the new row is missing from the in-memory GeoJSON list,
  `MapDrawable` reloads layer data from SQLite rather than refreshing stale styles,
  so the saved geometry appears without restarting the app.
- Crash recovery: normal vertex/touch geometry editing now synchronously journals
  the latest WKT with map/layer/feature identity, offers Continue/Discard after
  task/process death, waits for cold MapLibre sources, and logs recovery decisions
  without logging coordinates. Existing-feature geometry drafts clear only after
  one row was actually updated.
- Crash recovery: cold walk drafts now win over stale form drafts even when Android
  already restarted `WalkEditService`; successful attribute Save cannot recreate a
  ghost draft from `onPause()`, and stale feature drafts are rejected before edit.
- Identify линий: refine RTree-кандидатов через пересечение геометрии с
  tap-envelope (±20dp), а не bbox объекта; `GeoLineString.intersects` учитывает
  вершины внутри envelope (иначе короткий сегмент внутри tap давал miss).
- Режим редактирования геометрии (`MODE_EDIT` / walk / touch): на нижней панели
  стандартный крестик навигации → `cancelEdits()` (как верхний X); без пункта
  в толстых `edit_*.xml`.
- Identify (`MODE_INFO`): кнопка формы атрибутов в нижней панели при
  `VectorLayer.isEditingAllowed()` (политика коллектора); lean-меню
  `attributes` / `attributes_editable`, BottomToolbar ALWAYS до 3 пунктов;
  тап → сеанс редактирования слоя + форма.

## 2026-07-24

- Исправлена регрессия zoom-выражений: `interpolate`/`step` по zoom снова
  верхний уровень (scale в stop outputs; label zoom gate через `step(0..24)`),
  иначе MapLibre отвергал property и ломал масштаб/opacity.
- Rule-based: слойные дефолты MapLibre (stops, scale, opacity, SymbolLayer
  clamp) берутся из «прочих»; merge наследует scale flags и opacity; zoom-scale
  expression — outer switchCase; явный reset min/max подписей.
- Lisa Debug поднят до `versionCode` 196 / `versionName` 3.1.2.5 (maplib debug
  VERSION_NAME синхронизирован); Lisa/Belka Release остаются на `195` /
  `3.1.2.4`.
- Rule-based стили: зум видимости подписей работает per-category через feature
  props; незаданные опциональные поля наследуются из «Стиль для прочих
  (по умолчанию)»; zoom-stops слоя в rule-режиме берутся из прочих.
- Версия форка унифицирована до `versionCode` 195 / `versionName` 3.1.2.4 для
  Lisa Release, Belka Release и Lisa Debug; maplib VERSION_NAME сопряжён для
  debug и release.
- Collector project теперь импортирует уже штатно распознаваемые
  `qgis_vector_style` и `qgis_raster_style` как authenticated read-only raster
  tile layers, сохраняет их общий порядок с vectors и синхронизирует
  добавление, свойства, порядок и удаление.
- Зафиксирована граница поддержки: `Connection.java` не расширяется
  дополнительными современными style classes без отдельного продуктового
  решения; Activity/Dialog используют единый import helper.

## 2026-07-23

- Self-hosted updater сохраняет одноразовое pending-состояние и автоматически
  продолжает установку после возврата с Android-экрана специального разрешения;
  повторный ручной запуск проверки обновлений больше не требуется.
- Дефолтный `OpenStreetMap Standard aka Mapnik` теперь создаётся для каждого
  Collector workspace и нормализуется внизу списка без сброса видимости;
  добавлены invariant, change-impact и smoke-контракт этого порядка.
- Исправлен debug-only versioning для AGP 9.1: Lisa Debug получает
  `194`/`3.1.2.3` через Variant API, production Lisa/Belka остаются на
  `193`/`3.1.2.2`, а maplib version сопрягается отдельно для debug/release.
- Добавлена обязательная автоматическая APK version matrix и усилены инструкции
  агентов: неподдерживаемый version DSL, handoff без сборки и вывод версии из
  имени APK теперь явно запрещены.
- Исправлена политика редактирования project-managed Collector-слоёв:
  приложение использует галочку элемента Collector и исходящее направление
  синхронизации, не блокируя полевые слои общим `is_editable` из mobile config.
- «Мои треки» закреплён наверху списка: Collector batch вставляет слои ниже него,
  а ранее сохранённый неверный порядок исправляется при открытии карты.
- Android-приложение включено в общую модель экосистемы ЛИСА вместе с
  `standart_profiles` и проектом QGIS Plugins; добавлены единый маршрут для
  агентов, карта владельцев, end-to-end поток через NextGIS Web/Collector и
  отдельный контракт явного offline basemap handoff.
- Добавлен проверяемый `registry/ecosystem.yaml`: внешние docs entries,
  межпроектные контракты и граница, запрещающая прямую Android-зависимость от
  desktop profiles, plugin mirrors и `variables.py`.
- Validator проверяет локальные ссылки экосистемы и, когда соседний workspace
  доступен, существование его входных и контрактных документов.

## 2026-07-20

- Версия выпуска поднята до `3.1.2.2` / `versionCode` 193 с синхронной
  диагностической версией maplib; production-палитра переведена на более
  насыщенные рыже-оранжевые оттенки.
- Зафиксирован variant-specific NGW account contract: runtime, authenticator и
  sync adapter обязаны использовать один account type; добавлены invariant,
  smoke и диагностика отказа Android AccountManager. Package-name проверка
  rebuild-cache UI заменена на application capability для `.geonical`/`.debug`.
- Self-hosted updater переведён с `wiki-geonical.ru/mobile/<flavor>/stable` на
  отдельные ветки `apps-geonical.ru/lisa-mobile/{lisa,belka,debug}`; release
  contract теперь требует строгий channel/versioned URL и повторную сверку
  versionName, size, hash и signing certificate загруженного APK.
- Исправлена маршрутизация треков при переключении Collector-проектов без перезапуска процесса:
  `LayerContentProvider` разрешает текущую карту для каждой операции, карта безопасно публикуется
  между потоками, а активная запись трека блокирует смену workspace.
- `INV-COLLECTOR-ISOLATION` и `SMOKE-COLLECTOR-SWITCH` теперь явно проверяют раздельные истории
  треков, возврат в проект с сохранёнными треками и отсутствие записи в соседнюю базу.

## 2026-07-19

- Курсор текущего местоположения закреплён последним MapLibre style layer после
  cold/lite/hot reload и больше не перекрывается треками или пользовательскими слоями.
- Зафиксирован rollout-контракт Collector: неполный remote snapshot не импортируется,
  незавершённая партия переживает process death и продолжает verify/repair только для
  отсутствующих слоёв, а schema rebuild сохраняет старый слой до успешной подготовки замены.
- Реестр Collector workspace переведён на атомарную запись с backup/recovery scan;
  формы — на hash-проверку и восстанавливаемую транзакцию пары form/meta.
- Для `.ngrc` документирован и реализован `immutable_local` lifecycle с SHA-256,
  безопасной распаковкой и сохранением подложки при APK/project update. Remote lifecycle
  отложен до спецификации нового сервера.
- Синхронизация account изолирует результаты, обслуживает активный Collector account первым,
  ограничивает молчащее HTTP-чтение и восстанавливает фоновые расписания единым путём.
- Добавлен поддерживаемый агентами handoff-документ
  `reference/official-differences.md`: только актуальные пользовательские отличия от official,
  без истории и отменённых решений. Для каждой возможности описаны назначение, пользовательский
  сценарий и принцип работы. Новый strict change-impact trigger требует его пересмотра при изменении
  app behavior или submodule pointers; CI теперь применяет strict-требования к diff.
- Документирован selective upstream 3.1.2 cycle: прямой импорт NGW-ресурса по URL,
  permission/read-only contract, критические crash/form fixes и production Sentry policy.
- Матрицы, module contracts и release notes обновлены до версии форка 3.1.2.1 / 192.
- `upstream-sync.ps1` теперь использует command-scoped `safe.directory` и не
  маскирует ненулевые exit codes Git.
- Удалены невоспроизводимые upstream diff snapshots; полезные команды, выводы и результаты
  перенесены в `docs/history/upstream/`.
- `CUSTOMIZATIONS.md` сокращён до compatibility-указателя, а актуальный тематический индекс
  перенесён в `docs/reference/fork-customizations.md`.
- Collector setup/roadmap и анализ map startup перенесены из корня в проверяемую структуру docs;
  roadmap очищен от уже реализованных milestone.
- `WHATS_NEW.md` актуализирован до 3.0.3.9 / `versionCode` 187.
- Создан автоматический agent entry через root/module `AGENTS.md` и Cursor rules.
- Добавлена центральная структура architecture/guides/runbooks/reference.
- Введены машиночитаемые repositories, modules, dependencies, invariants,
  change-impact, config, upstream overlap и smoke registries.
- Добавлены module packs, scaffold, validator, unit tests и CI workflow.
- `CONTEXT_INSTRUCTION.md` переведён в совместимый redirect на новую систему.
