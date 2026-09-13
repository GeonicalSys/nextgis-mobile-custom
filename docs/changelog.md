---
title: История документационной системы
type: changelog
last_verified: 2026-09-13
related_code:
  - docs
---

# История документационной системы

## 2026-09-13

- Разделены текущий маркер и вершина стоянки: круг не растёт из-за удержания
  начала маршрута. Описаны быстрый старт при 3–5 м и устойчивое подтверждение
  при обычной уличной точности, новые регрессии и пределы полевой проверки.
- Обход получил независимого владельца полной геометрии, пассивный MapLibre
  контур и панель. Создание точки блокирует все команды обхода от выбора слоя
  до Save/Cancel; durable handoff и Finish требуют соответствующего UUID.
- Исправлена потеря CRS в копии скетча. На A54 проверена отрисовка во время
  обхода/создания точки и после формы, блокировка команд и подтверждение Finish.
- Обновлены storage, invariants, dependencies, manifests и smoke. Особенность
  имени GPX только в MAX отложена по указанию пользователя.
- Root закрепляет Merge Commit maplib PR #21 `761d7a2` и maplibui PR #13
  `223f8b04` вместо промежуточных feature pins.

## 2026-09-12

- ADB-замер неподвижного A54 с телефоном в руках выявил согласованный multipath
  свыше 100 м и ложную speed до 6.4 м/с. Усилено подтверждение движения по
  направлению, двум радиусам accuracy и достоверности скорости; начало пути
  буферизуется с последующим сохранением времён и ранних поворотов.
- Добавлены обезличенная регрессия на реальных GNSS измерениях, проверки отмены
  сомнительного начала и изоляции recorder. Исправлены GPX URI MIME/anonymous
  MIME против суффикса .bin и передача константы явного Stop для закрытия трека.

- По полевому отзыву Galaxy A54 исправлены перерегистрация GPS при возврате
  карты и зависимость фонового wake lock от включённого звука.
- Стоянки проверяются по коротким сенсорным/GNSS окнам; уточнение якоря
  корректирует одну вершину, сохраняя возможность начать ходьбу или поездку.
- Добавлены тесты длительного коррелированного дрейфа, датчиков, непрерывности
  подписок и сохранения уточнений; журнал GPS health и smoke требуют проверки
  фактической частоты/точности при выключенном экране без звука.

## 2026-09-11

- Описан общий источник GPS/Network, срок свежести позиции и метрический круг
  accuracy; сетевой fallback отделён от GNSS-only записи трека и обхода.
- Добавлены адаптивный фильтр движения, прореживание с сохранением поворотов,
  миграция trackpoints.segment в БД v6 и GPX-разрывы; обход хранит gps_paused
  и требует явного продолжения. Удалены raw preview/closing bypass.
- Обновлены API manifests, зависимости, инварианты и device smoke. Official HEAD
  четырёх NextGIS repositories повторно проверены и не изменились от baseline.

## 2026-09-08

- Инструмент азимута получил два свободных сценария: от текущего положения до
  точки карты и между двумя произвольными точками. Вынос выбранного объекта и
  оба новых режима показывают числовой магнитный азимут по встроенной WMM2025 и
  геодезическое расстояние WGS84; временные точки/линия не входят в проект.
- Контракт MapLibre дополнен непостоянными `azimuth-measurement-*` слоями под
  всегда верхним курсором местоположения, а device smoke — проверками WMM,
  повёрнутой карты, отсутствующего компаса и совпадающих точек.
- Точки A/B и live-цель можно корректировать перетаскиванием; GPS-начало остаётся
  фиксированным. Подпись магнитного азимута допускает вторую строку, поэтому
  числовое значение не обрезается на узком экране.

## 2026-08-27

- Layer backup перед ручным удалением, schema rebuild и другими destructive
  операциями больше не докачивает server-only attachment payload. ZIP сохраняет
  все таблицы слоя и только физически имеющиеся на устройстве файлы, что
  устраняет `NetworkOnMainThreadException` при удалении слоя.

## 2026-08-26

- Подготовлен неопубликованный выпуск Lisa/Belka `3.1.2.18` / `versionCode 212`
  и Lisa Debug `3.1.2.18` / `versionCode 213`. Full untracked NGW sync теперь
  пропускает отдельную отсутствующую или невалидную серверную геометрию,
  сохраняет её remote ID и прежнюю локальную копию, продолжая остальной слой;
  журнал ограничивает число детальных записей.
- Подготовлен выпуск Lisa/Belka `3.1.2.17` / `versionCode 211` и Lisa Debug
  `3.1.2.17` / `versionCode 212` поверх sync-recovery. App и maplib coupling
  обновлены согласованно; root закрепляет Merge Commit maplib PR #20
  `4323cb0` и maplibui PR #12 `5d48122`. Публичные каналы до публикации:
  Lisa/Belka `210` / `3.1.2.16`, Debug `204` / `3.1.2.10`.
- Без изменения версии добавлен pre-sync repair дубликатов managed NGW-слоя с
  backup gate и одним map commit; edited/ambiguous copies блокируют sync без
  удаления. Staged schema refill больше не сохраняет промежуточную композицию
  old+new, а map/layer JSON записывается через `AtomicFile`.
- Schema preflight стал трёхсторонним (`resource.cls`/geometry/fields NGW,
  serialized config, physical SQLite): metadata-only drift `idqgs` исправляется
  без refill, а legacy config без типа не считается PostGIS.
- Full untracked NGW snapshot загружается во временный файл и потоково
  применяется одной SQLite-транзакцией; reload MapLibre откладывается до конца
  account-pass, выключенные слои не материализуют полный GeoJSON.
- Account sync получил `dataSync` foreground execution и durable recovery
  marker для повтора после process death только в том же workspace. Локальные
  изменения отправляются до большого pull; неуспешный push блокирует remote apply.
- Удалено десятисекундное ожидание sync worker из `NGWSyncService.onDestroy()`,
  которое блокировало Android main thread и могло само приводить к ANR.
- Pull серверных attachment metadata больше не сравнивает их с пустым локальным
  `META` и не скачивает сотни файлов только ради ложного `sync_remote_apply`
  backup. Метаданные сохраняются в `FeatureAttachments`, в том числе для новых
  features; обычный pull по-прежнему не скачивает байты вложений.

## 2026-08-25

- С unpublished Lisa/Belka `3.1.2.16` / `210` и Lisa Debug `3.1.2.11` / `205`
  сняты искусственный потолок 5/30 FPS и постоянный `CONTINUOUS` на Android
  8–9: в покое renderer остаётся в `WHEN_DIRTY`, recovery на всех API — короткий
  burst. minSdk остаётся 26; Android 8–9 поддерживаются. Номера версии не
  менялись. `maplib` закреплён на Merge Commit PR #19 `b704187`, а не на tip
  ветки `e74c262`.

## 2026-08-24

- Подготовлен выпуск Lisa/Belka `3.1.2.16` / `versionCode 210` и Lisa Debug
  `3.1.2.11` / `versionCode 205` поверх интеграционного squash `#23`. App и
  maplib coupling обновлены согласованно; pointer `maplib` в Draft PR указывает
  на commit bump ветки `codex/release-3.1.2.16` и после Merge Commit библиотеки
  должен быть заменён на итоговый merge commit.
- Android delivery contract теперь требует перед integration/release полной
  матрицы открытых и stacked PR по root и библиотекам, проверки фактического
  включения каждого исправления и pin только на итоговые merge-коммиты. Аудит
  вернул в общую ветку ранее оставшиеся параллельно migration/MBTiles, alarm-
  feedback, crash-safe fill, track-start ordering и local-vector-tile OOM guard;
  Android 9 updater fallback снова имеет отдельный unit-test API boundary.
  Библиотечная часть закрыта Merge Commit maplib PR #18 и maplibui PR #11, а
  root закрепляет эти итоговые commits вместо feature-веток.
- Подготовлен выпуск Lisa/Belka `3.1.2.15` / `versionCode 209` и Lisa Debug
  `3.1.2.10` / `versionCode 204`; app/maplib coupling и независимая APK version
  matrix обновлены согласованно после исправления Android 9 EGL recovery.
  Версия `maplib` доставлена Merge Commit из PR #17, app/root закрепляет именно
  итоговый commit библиотеки.
- Последующие Android 9 логи опровергли TextureView workaround: MapLibre
  сообщал `fully=true` до `eglSwapBuffers`, а его TextureView render thread при
  `EGL_CONTEXT_LOST` обнулял surface и мог навсегда ждать нового callback.
  Все API возвращены на `SurfaceView`, который пересоздаёт EGL context/surface;
  на API 26–28 выключен tile prefetch и установлен предел 30 FPS. Полный reload
  теперь освобождает прежний GeoJSON snapshot и detached style wrappers, снижая
  пик памяти большого Collector-проекта. Версия приложения не изменялась.
- Device log выявил ошибочный критерий первой версии MapLibre recovery: три
  app-side callback-а могли завершить его при `fully=false` и всё ещё видимом
  loading foreground. Этот критерий удалён. Recovery временно включает
  continuous rendering, invalidates TextureView presentation, проводит камеру
  через transaction без смещения и завершается штатно только при полном кадре с
  уже снятым foreground; после лимита снимает зависший foreground и возвращает
  прежний refresh mode. Версия приложения не изменялась.
- MapLibre recovery перенесён с единственного раннего repaint в `onResume` на
  ограниченную серию после фактического применения project style. Если большой
  или offline-проект уже применён, но MapLibre не снял loading foreground, host
  снимает только его без full reload; lifecycle и результат пишутся в HyperLog.
  `SMOKE-MAP-SURFACE-LIFECYCLE` теперь проверяет открытие большого Collector-
  проекта без обязательного жеста по карте. Версия приложения не изменялась.
- Official NextGIS Mobile HEAD повторно проверен 24 августа 2026 года: его
  `setMapLayersLoaded()` остаётся пустым, полного MapView lifecycle и post-style
  render recovery в official app нет.
- По Android-логу пустого трека отделено движение обычного курсора от recorder:
  Start отдельного процесса был доставлен только вместе с нажатым через восемь
  минут Stop, поэтому сервис получил `raw=0`. `TrackerService` перенесён в
  основной процесс, использует обычные in-process preferences и отбрасывает
  запоздалый Start после уже выключенного durable intent; smoke расширен на
  фактическое появление location FGS до ухода с карты на Android 9–11.
- Android 16 crash после загрузки проекта и работы с картой подтверждён как OOM
  в `LocalVectorTileProvider`: неограниченный cached pool дошёл как минимум до
  `pool-24-thread-103`, параллельно разбирая тяжёлые геометрии слоя «Квартала».
  Loopback tile server ограничен двумя worker и очередью 16, сериализует слой,
  закрывает устаревшие запросы и возвращает throttled `503` при перегрузке либо
  остатке heap менее 64 МБ; добавлены policy unit tests и stress smoke.
- Диагностика A54 подтвердила, что проблемный NGW-слой содержит 15
  MultiPolygon, но около 140 тысяч координат; legacy попарная проверка сегментов
  заменена на JTS validation с unit-регрессией на 60 тысяч вершин.
- Collector fill теперь открывает SQLite только через map-владельца слоя и
  сверяет project UID durable journal. Новые unpublished каталоги получают
  `.layer-fill-partial`; после process death автоматически очищаются только
  помеченные orphan stages, тогда как referenced и legacy unmarked каталоги
  сохраняются.
- Звуковой контроль записи документирован в соответствии с реализацией: alarm
  stream не зависит от громкости уведомлений, а выключенный будильник даёт
  короткую вибрацию.
- Успешное сохранение нового или существующего объекта теперь полностью завершает
  edit session: прямой geometry Save и подтверждение формы атрибутов очищают
  MapLibre/overlay selection и возвращают стандартный `MODE_NORMAL` экран.
- Звуковой контроль фоновой записи отвязан от сохранения новых вершин: отдельная
  подписка без порога перемещения различает неподвижное устройство и прекращение
  доставки координат. При свежих пригодных фиксах скрытый recorder стабильно
  пикает каждые 10 секунд; stale/missing fixes гасят сигнал.
- Контрольный сигнал фоновой записи ускорен с 30 до 10 секунд.
  `TrackerService` и `WalkEditService` больше не вызывают запрещённый location
  `startForeground()` после отзыва coarse/fine permission: security race
  останавливает сервис без crash loop и сохраняет track intent / walk draft.
- Линейка подключена к общей истории геометрии и получила отдельную панель
  Undo/Redo; добавления и завершённые переносы измерительных точек отменяются и
  возвращаются по одному, с обновлением длины и площади. После device-feedback
  источник снимков перенесён с legacy `RulerOverlay` на фактически отображаемый
  MapLibre `MeasurmentLine`, а его геометрия добавлена в saved-instance state.
- Official app/maplibui HEAD повторно сверены: hashes не изменились, а official
  `RulerOverlay` по-прежнему не содержит истории или панели Undo/Redo.

## 2026-08-23

- Трек и обход получили общий post-persist звуковой контроль фоновой записи:
  enabled-by-default preference, 30-секундный success throttle, отдельный
  минутный failure throttle, suppression при видимом UI и unit policy tests.
- Первая карта теперь всегда принадлежит активному начальному local project:
  чистая установка создаёт его до первого `MapDrawable`, а обновление один раз
  копирует прежние map-owned слои и track DB без удаления исходной standalone-
  карты. `INV-COLLECTOR-ISOLATION` и `SMOKE-PROJECT-MANAGEMENT` расширены на
  clean install, возврат после Web GIS switch и одноразовую legacy migration.
- Official NextGIS Mobile `e098196` и MapLib UI `a426e0a` повторно проверены
  23 августа 2026 года; project registry и initial local workspace в них
  отсутствуют, поэтому отличие форка остаётся актуальным.
- Production Lisa/Belka подготовлен как патч `3.1.2.14` / `versionCode 208`.
  Холодное Continue скетча теперь ждёт edit sources текущего MapLibre style,
  walk/manual drafts взаимоисключаются, а кнопка настроек активного обхода
  заменена на завершение/сохранение с иконкой идущего человека.
- API 26–28 использует MapLibre `TextureView`, чтобы после background/sleep
  потерянный `SurfaceView` не перекрывал всю Activity чёрным слоем; API 29+
  сохраняет более быстрый `SurfaceView`. Renderer и SDK фиксируются в HyperLog.
- Редактор LineString/Polygon и Multi-вариантов показывает направление вставки:
  выбранный узел красный, его следующий узел и сегмент оранжевые; открытый конец
  линии не имеет цели, а кольцо замыкает направление только на собственный первый
  узел. Добавлены unit policy и device-smoke для границ частей и колец.
- Production Lisa/Belka подготовлен как патч `3.1.2.13` / `versionCode 207`;
  MapLibre `MapView` теперь получает полный Fragment view lifecycle, старый
  native renderer освобождается при `onDestroyView`, а resume запрашивает repaint
  и фиксирует первый кадр в HyperLog. Добавлены
  `INV-MAPLIBRE-VIEW-LIFECYCLE` и `SMOKE-MAP-SURFACE-LIFECYCLE`.
- Official NextGIS Mobile HEAD повторно проверен: его `MapFragment` по-прежнему
  вызывает только `MapView.onCreate`, поэтому lifecycle-исправление остаётся
  действующим отличием форка.
- Production Lisa/Belka подготовлен как выпуск `3.1.2.12` / `versionCode 206`;
  Lisa Debug остаётся `3.1.2.9` / 203, release `maplib.VERSION_NAME` обновлён
  синхронно.
- MapLibre Android `13.0.2` закреплён через явный `android-sdk-opengl` в `app`,
  `maplibui` и `maplib`. Добавлены `INV-MAPLIBRE-BACKEND-COMPATIBILITY`,
  `SMOKE-MAP-OPENGL-COMPATIBILITY`, cross-module dependency contract и upstream
  hotspot, чтобы generic MapLibre 13 Vulkan artifact не вернулся при синхронизации.
- Официальные NextGIS app/maplib/maplibui/easypicker HEAD повторно проверены и
  не изменились; official app сохраняет Vulkan-default `android-sdk:13.0.2`,
  поэтому OpenGL compatibility остаётся действующим отличием форка.

## 2026-08-22

- Полный NGW/Collector fill снова принимает стандартный `MULTIPOLYGON` с
  внутренними кольцами и несколькими polygon members: парсер разделяет части по
  уровню скобок. Ошибка отдельного объекта останавливает и откатывает слой, а
  HyperLog сохраняет слой/resource/нулевой индекс/класс/сообщение/ограниченный
  стек без координат и значений полей.
- Production Lisa/Belka подготовлен как новый монотонный выпуск `3.1.2.11` /
  `versionCode 205`; уже опубликованный `3.1.2.10` / 204 не переиспользуется.
  Lisa Debug намеренно остаётся `3.1.2.9` / 203.
- Cold Continue LineString/MultiLineString теперь явно снимает общий polygon
  FillLayer: восстановленная линия больше не выглядит замкнутым полигоном, при
  этом геометрия, вершины и сохранение остаются без изменений.
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
