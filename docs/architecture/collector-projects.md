---
title: Collector projects, composition sync и backups
type: architecture
last_verified: 2026-09-13
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/LayerContentProvider.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorProjectItem.java
  - maplib/src/main/java/com/nextgis/maplib/map/CollectorProjectMetadata.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWRasterLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapContentProviderHelper.java
  - maplib/src/main/java/com/nextgis/maplib/util/DatabaseContext.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureGeometryValidator.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorProjectCompositionSync.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectImportHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorRasterLayerHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerFillStaging.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ProjectOperationCoordinator.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/SelectNGWResourceActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/dialog/SelectNGWResourceDialog.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SchemaRebuildRetryGuard.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorImportJournal.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorFormFileTransaction.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/activity/ProjectSettingsActivity.kt
---

# Collector projects, composition sync и backups

## Поток импорта

```text
NGW Collector resource
  → SelectNGWResourceActivity/Dialog
  → CollectorProjectMetadata (maplib)
  → CollectorProjectRegistry (maplibui)
  → isolated workspace + map
  → LayerFillService batch
  → composition sync / removal policy
  → project switch in MainActivity
```

`Connection.NGWResourceTypeCollector` — обязательный тип ресурса форка.
Collector project идентифицируется стабильным `project_uid`, построенным из
account и remote project id. Registry хранится в
`collector_projects_registry.json`, workspaces — в `collector_projects/`.
Schema registry `2` различает `WEBGIS` и `LOCAL`; каждый workspace дополнительно
получает атомарный `project.json`, поэтому локальный проект без server metadata
также восстанавливается после повреждения общего registry.

Импорт начинается только после полного чтения дерева Collector и всех ссылок на
поддерживаемые ресурсы. HTTP/JSON-ошибка в середине snapshot отменяет импорт до
создания новой рабочей области: частичный проект не считается допустимым
результатом. Повторный импорт различает слои по `account + remote_id`, поэтому
одинаковые отображаемые имена не приводят к пропуску разных слоёв.

Подготовка workspace сначала получает lease переключения и лишь затем вызывает
`ensureProject()`. Если текущий проект синхронизируется, загружает слой или
перестраивает схему, импорт не создаёт запись в registry и показывает отдельное
модальное сообщение с просьбой дождаться завершения фоновой операции.

## Поддерживаемые элементы проекта

- `vector_layer` и `postgis_layer` загружаются в существующий локальный
  vector/fill pipeline и могут быть редактируемыми согласно project policy.
- Уже штатно известные `Connection` типы `qgis_vector_style` и
  `qgis_raster_style` создаются как authenticated `NGWRasterLayer`: тайлы
  запрашиваются через render endpoint по remote id самого стиля.
- Для растрового представления remote id стиля остаётся identity слоя, а remote
  id родительского vector/raster resource хранится отдельно и используется
  только для extent. Поэтому исходный вектор и его стиль могут находиться в
  проекте одновременно.
- Style layer всегда read-only, не участвует в создании объектов и не
  маскируется под векторный слой.
- Другие современные style classes намеренно не добавляются в `Connection.java`
  этим контрактом. Расширение поддерживаемых серверных типов требует отдельного
  продуктового решения и тестовой матрицы.

Activity и Dialog используют общий `CollectorProjectImportHelper`, а initial
import и composition sync создают raster styles через один
`CollectorRasterLayerHelper`. Это исключает разные правила импорта в двух UI
точках.

## Изоляция

- У каждого Web GIS или пустого локального проекта собственный map/workspace.
- `map.ngm`, `layers.db`, история треков и точки треков относятся к этому workspace;
  операции через `LayerContentProvider` каждый раз разрешают текущую карту приложения и не
  используют экземпляр, оставшийся от ранее открытого проекта.
- Project metadata хранит identity, district, composition sync state и время
  последней проверки.
- Ручные NGW-слои должны маршрутизироваться в активный проект предсказуемо.
- Переключение проекта сначала сохраняет текущую карту, затем активирует другую.
- Общий process-wide coordinator удерживает active project identity на всё время
  ручной/периодической sync, layer fill и schema rebuild. Пока хотя бы одна такая
  операция использует workspace, switch/create/rename/delete запрещены; повторная
  полная sync того же проекта также не запускается. Зависимая цепочка
  `sync → staged layer fill` заранее резервирует тот же workspace, но SQLite-стадии
  выполняются последовательно: fill ждёт полного завершения sync.
- Во время активной записи трека переключение запрещено. Это сохраняет весь сеанс в одной
  проектной базе и исключает попадание следующих точек в другой workspace.
- Ошибка подготовки нового workspace не должна разрушать существующий проект.
- Registry и `project.json` записываются атомарно с резервной копией. Если общий
  registry утрачен или повреждён, он восстанавливается по sidecar, а старые
  Web GIS workspaces без sidecar — сканированием `map.ngm`; пути за пределами
  `collector_projects/` отвергаются.
- После переключения активный account ставится на ближайшую синхронизацию.

## Управление проектами

Раздел настроек «Проект» принадлежит приложению, а операции хранения —
`CollectorProjectRegistry`. В нём показываются пользовательское имя и тип,
количество NGW-слоёв и слоёв с несинхронизированными изменениями. Только для
Web GIS проекта там отображаются account, remote project id и district. В
быстром выборе на карте остаются только имена; проекты разделены крупными
нажимаемыми строками, активный отмечен индикатором без технических реквизитов.

До первого открытия `MapDrawable` основного процесса registry гарантирует
активный workspace «Локальный проект». Поэтому уже на чистой
установке ручные слои, треки и карта принадлежат проекту, который остаётся в
быстром выборе после импорта Web GIS/Collector.

При первом запуске обновлённой версии прежняя штатная `default.ngm` вне registry
копируется в этот локальный workspace вместе с перечисленными в карте
каталогами слоёв и базой треков. Исходные файлы не удаляются и остаются
rollback-копией. Маркер `collector_initial_local_project_created_v1` делает
миграцию одноразовой; стабильная внутренняя identity начального проекта
позволяет без дубликатов завершить инициализацию после
прерванной записи registry. Если в момент обновления уже активен корректный Web
GIS/local project, он остаётся активным, а локальный проект лишь добавляется в
список для возврата к старым локальным данным.

### Перенос старого Debug-профиля в Geonical

Версии `3.0.3.2` и `3.0.3.3` ещё не сохраняли устойчивую identity исходного
Collector-проекта: загруженный список проекта жил в процессе, а у слоёв
оставались только account/remote resource и синхронизированные конфиги. Поэтому
миграция не сопоставляет старую карту с Web GIS/Collector-проектом автоматически.
Пользователь сначала создаёт либо импортирует правильный проект в Geonical,
делает его активным и только затем запускает «Перенести подложки из Debug».

Между разными application ID Android не разрешает прямое чтение private storage.
Совместимое обновление Debug экспортирует только локальные TMS-подложки через
явный read-only content grant. Geonical принимает экспорт только от ожидаемого
пакета с закреплённым сертификатом; Debug так же принимает только ожидаемого
Geonical caller. Проекты, registry, accounts, credentials, vector layers, треки
и feature data через мост не передаются.

Распакованные тайлы не копируются как дерево файлов: Debug передаёт их
последовательным framed-stream без центрального каталога, а Geonical сразу
собирает raster MBTiles в каталоге нового слоя. Исходник не удаляется. Успешный
слой получает source
provenance; повторный запуск пропускает уже перенесённую подложку. Порядок,
видимость и имя сохраняются, а подложки вставляются над OSM активного проекта.
Этот bridge не заменяет ручной выбор правильного проекта.

Пользователь может создать пустой `LOCAL` workspace, переименовать локальное
отображаемое имя любого проекта и удалить активную локальную копию. Удаление не
вызывает NGW API и не удаляет Android account или серверный ресурс. Перед ним
все NGW-слои с несинхронизированными изменениями проходят обязательный full
backup gate. Workspace сначала атомарно переименовывается в tombstone, registry
и active preferences переключаются на последний открытый оставшийся проект,
и только затем tombstone удаляется. Если удалён последний проект, заранее
создаётся пустой локальный fallback с обычными OSM и «Мои треки».
Фоновая операция не открывает fallback-карту после удаления: экран проекта
возвращает пользователя в `MainActivity`, который открывает новую активную карту
на главном потоке. Поэтому уже успешное удаление не превращается в ложный
`STORAGE_FAILED` из-за Android `Looper`.

## Незавершённый импорт и обновление приложения

Полное описание текущей партии (`remote_id`, имя, form/config, порядок и число
оставшихся repair-попыток) хранит `CollectorImportJournal` в app-private storage.
Эти данные и сами workspaces переживают обычное обновление APK. Если Android
убил процесс во время загрузки, следующий запуск проверяет уже созданные
локальные таблицы и повторно ставит в очередь только отсутствующие или
повреждённые слои. Успешно загруженные тяжёлые слои повторно не скачиваются.
Если durable journal нельзя записать, новая партия или repair-wave не запускается:
существующие слои остаются на месте, а операция может быть безопасно повторена позже.

`LayerFillService` фиксирует target group в каждой задаче: партии для разных
групп не могут попасть в последнюю открытую группу. Замена слоя при изменении
схемы сначала полностью строится в новом каталоге; рабочий старый слой удаляется
только после успешного заполнения замены — это правило действует и на повторных
repair-проходах.

Target group теперь является также владельцем SQLite: новый слой получает parent
до `create()`/fill, `DatabaseContext` поднимается по parent chain до map, а сам
`MapContentProviderHelper` открывает `layers.db` рядом со своим map-файлом, не по
последней process-wide preference. Collector journal содержит project UID. Если
после restart открыт другой workspace, verify/repair не очищает journal и не
трогает его БД, а ждёт открытия целевого проекта. Это закрывает сценарий, при
котором каталог создавался в одном проекте, а таблицы и строки попадали в другой.

Каталог каждого нового слоя резервируется атомарным `mkdir` с UUID до открытия
SQLite. Время запуска, число слоёв и короткое случайное число не являются
identity: параллельные задачи одной партии не должны получить одну таблицу.
Если первый либо любой следующий batch insert вернул ошибку, задача немедленно
выходит через exception, транзакция откатывается, а неполный слой удаляется.
Продолжать тысячи вставок после первой ошибки схемы запрещено.

До первого обращения к данным новый каталог получает `.layer-fill-partial`.
Marker удаляется только после успешного сохранения слоя в `LayerGroup`. При
холодном продолжении приложение удаляет из целевого workspace лишь помеченные
каталоги, которые не указаны в загруженной карте, и одноимённые data/change/
attachment tables. Если map уже ссылается на помеченный каталог, слой сохраняется
и marker снимается. Любые старые непомеченные `layer_*` остаются нетронутыми:
автоматически отличить прежний мусор от тяжёлой локальной подложки или
пользовательских данных нельзя.

Стандартный WKT `MULTIPOLYGON` во время полного fill разделяется по уровню
скобок: внутреннее кольцо не обрывает polygon member, а следующие части не
теряются. Первый сбой чтения или записи объекта по-прежнему останавливает слой,
а не пропускает объект. HyperLog сохраняет имя слоя, remote id, нулевой индекс
элемента в исходном JSON-массиве, класс/сообщение ошибки и ограниченный стек.
Координаты, значения полей и credentials в эту запись не попадают; durable
journal позволяет безопасно повторить незавершённый импорт.

Число server features не является оценкой стоимости геометрии. Полученные от NGW
Polygon и каждый member MultiPolygon проверяются JTS `IsValidOp`, а не legacy
попарным сравнением всех сегментов кольца. Поэтому слой из нескольких объектов,
один из которых содержит десятки тысяч координат и сотни polygon parts, остаётся
валидируемым за ограниченное практическое время без изменения member-by-member
семантики старого импорта.

Android toolbar Back на экране импорта NGW использует тот же `goUp()`, что и
аппаратная кнопка: внутри дерева он поднимается к родительскому каталогу и
закрывает импорт только из корня доступного дерева.

## Composition sync

Composition sync сравнивает серверный состав проекта с локальным для vectors и
поддерживаемых raster styles. Добавление, обновление и удаление имеют разные
риски. Удаление локального vector layer или schema rebuild являются
разрушительными действиями и подчиняются `INV-BACKUP-BEFORE-DESTRUCTION`.
Удаление raster-style слоя очищает только воспроизводимый tile cache и не
требует data backup.

Identity project-managed слоя — единая тройка `account + remote_id +
layer_origin(project_uid)`. При загрузке vector layer восстановление R-tree может
перезаписать только файл индекса: сохранять `config.json`, пока подкласс ещё не
прочитал NGW identity, запрещено. Последняя полностью прочитанная identity
дублируется в per-layer SharedPreferences (`ngw_identity_backup`) и используется
для восстановления оборванной/частичной записи конфигурации.

Перед добавлением composition sync индексирует все физические NGW-слои проекта
по `account + remote_id`, а не только слои с сохранившимся managed-флагом.
Единственный совпавший слой без `layer_origin` получает метку проекта на месте,
без повторной загрузки. Два совпадения, manual-origin, неверный тип или account
дают `local_identity_conflict`: весь apply пропускается, поэтому неоднозначность
не размножает дубликаты. Пока активен durable import batch, новые additions
откладываются до следующей синхронизации.

До каждого account sync выполняется repair уже повреждённой композиции. Точная
identity включает `account + project_uid + remote_id`, поэтому одинаковый
ресурс в другом Collector-проекте или ручной слой не затрагивается. Несколько
managed-копий без pending changes/attachments проходят backup gate, после чего
все лишние ссылки удаляются одним сохранением карты, а их storage — только после
commit. Неоднозначность с локальными изменениями блокирует sync и требует
ручного разбора; автоматическое объединение объектов между копиями запрещено.

При schema refill замена использует ту же project identity. Main-thread swap
никогда не сохраняет old+new одновременно и не удаляет рабочую таблицу до
успешного сохранения replacement. Это устраняет окно, в котором process death
на каждом sync размножал один и тот же слой.

HTTP 404 при feature sync managed-слоя также не превращает его в локальный
неуправляемый слой. Решение об удалении принимает только composition sync по
полному snapshot проекта; если snapshot неполон, существующий слой сохраняется.

Если обязательный backup не создан, локальные данные сохраняются и
разрушительная операция отменяется. Backups создаёт `LayerBackupManager` в
`LayerBackups/` (полный слой или selective feature ZIP); архив для передачи —
`ng-layer-backups.zip` с manifest. Размер каталога ограничен
`layer_backup_max_gb` (default 5); при превышении удаляются самые старые ZIP.
Таблица attachment metadata попадает в backup целиком, но файлы берутся только
из локальных каталогов слоя. Серверные вложения, которые не собирались на
этом устройстве, не скачиваются во время backup и не мешают удалению слоя.

Форма обновляется отдельной файловой транзакцией: проверяются серверный hash и
hash распакованных файлов, новая пара `form.json`/`ngfp_meta.json` ставится через
stage/backup/marker, а при прерывании восстанавливается прежняя согласованная
пара. Неполный remote snapshot вообще не применяется к локальной композиции.

## Config и feature data

Configuration sync и feature-data sync — разные контракты. `SYNC_NONE` для
данных не должен автоматически запрещать безопасное чтение конфигурации,
необходимое для отображения/форм, если конкретный flow это поддерживает.

Для project-managed NGW-слоя возможность создания и изменения объектов задаёт
галочка `editable` у элемента Collector-проекта вместе с разрешённым исходящим
направлением синхронизации. Общий `is_editable` из mobile config не должен
перекрывать эту проектную политику. Для вручную импортированных и остальных
слоёв `is_editable` и серверное `data.write` по-прежнему остаются обязательным
ограничением.

Первичный full fill пропускает уже разобранную, но невалидную геометрию, но
остаётся fail-closed и откатывает слой при исключении чтения или записи объекта.
Последующий untracked feature sync обрабатывает уже разобранную,
но невалидную либо отсутствующую геометрию иначе: пропускает только этот объект,
сохраняет его remote ID и существующую локальную копию, а остальные объекты слоя
применяет одной транзакцией. Диагностика содержит ограниченные ID/индексы без
feature payload.

Экран свойств не является источником серверной политики: его начальные события
`Spinner` не изменяют сохранённое направление. Для managed-слоя доступность
самого выбора направления определяется галочкой Collector, а не generic
`is_editable` и не текущим направлением. Поэтому ошибочно сохранённый режим
«только с сервера» можно вернуть в двусторонний без удаления и повторного
импорта слоя.

Единый смешанный порядок vector и raster-style элементов Collector сохраняется
с учётом того, что индекс `0` в
`LayerGroup` — низ стека. Каждая проектная карта содержит дефолтный
`OpenStreetMap Standard aka Mapnik` прямым дочерним слоем с индексом `0`;
он не является managed-слоем Collector и не удаляется composition sync.
Слой «Мои треки» резервирует последнюю внутреннюю позицию и поэтому остаётся
первой строкой списка слоёв; project-managed слои вставляются между OSM и
треком. При открытии старого workspace отсутствующий OSM создаётся, а неверные
позиции OSM и слоя треков нормализуются без сброса сохранённой видимости.

Ручная `.ngrc`-подложка не является managed-слоем Collector и не участвует в
destructive composition apply. После импорта в её `config.json` сохраняются имя
исходного архива, SHA-256, время импорта и политика `immutable_local`. До
появления согласованного контракта нового сервера синхронизация не заменяет и не
удаляет распакованные тайлы.

## Проверки

- импорт Collector resource и создание отдельного workspace;
- переключение между двумя проектами в одном процессе без смешивания слоёв и треков;
- запрет переключения и второго запуска sync на всём интервале sync/fill, включая
  паузу между последовательными аккаунтами;
- импорт другого Collector-проекта во время sync: понятное модальное ожидание и
  отсутствие новой записи в registry до завершения операции;
- создание локального проекта, локальное переименование, удаление Web GIS
  workspace без удаления server resource, без ложного сообщения об ошибке и с
  созданием fallback после удаления последнего;
- picker содержит только имена, а account/id/district доступны в «Настройки → Проект»;
- проект с сохранёнными треками → проект без треков → обратно: список, карта и новая запись
  используют базу текущего проекта без принудительного перезапуска приложения;
- обновить старый Debug APK сборкой с тем же signing certificate, выбрать
  целевой Geonical-проект и перенести несколько подложек, включая большой набор
  мелких тайлов; проверить порядок/видимость, cold start, повторный запуск без
  дубликатов и сохранность исходной Debug-карты;
- добавление/переупорядочивание состава;
- импорт слоя с 15 MultiPolygon и примерно 140 тысячами координат, включая один
  объект около 60 тысяч координат: UI и fill продолжают отвечать, локальное число
  features равно серверному, а прогресс не интерпретирует вершины как объекты;
- оборвать fill, открыть другой проект и перезапустить приложение: journal ждёт
  исходный project UID, чужой `layers.db` не получает таблиц; после возврата в
  целевой проект удаляются только помеченные unpublished stages, а referenced и
  legacy unmarked каталоги/MBTiles сохраняются;
- backup и отказ от удаления при искусственной ошибке backup;
- repair трёх одинаковых managed-копий без pending changes до одной, а также
  fail-closed блокировку при change/attachment хотя бы в одной копии;
- два rebuild одной неизменной сломанной схемы за сутки, блокировка третьего,
  ручной сброс защиты и сохранение старого слоя при неуспешной staged-загрузке;
- district filter и form/render configuration;
- проект с vector, `qgis_vector_style` и `qgis_raster_style`: все элементы
  появляются в исходном смешанном порядке, style tiles используют account
  authentication, а стили не предлагаются для создания объектов;
- editable включён только у полевых элементов Collector: создавать объекты можно
  только в них, «Мои треки» остаётся наверху, а OSM — внизу списка после импорта;
- запуск/возврат после screen off во время большого layer fill;
- импорт слоя с многосоставным `MULTIPOLYGON` и внутренним кольцом без потери
  частей/дырки; при искусственной ошибке объекта — откат слоя и диагностическая
  запись с исходным индексом без feature payload;
- повторную untracked sync с отдельной невалидной серверной геометрией: слой
  завершается успешно, остальные объекты обновляются, прежняя локальная копия
  пропущенного remote ID не удаляется;
- сброс настроек и импорт проекта минимум с 13 слоями: все каталоги уникальны;
  точечный слой с начальным `visible=false` после локального включения виден
  сразу, а identify и карта согласованы без server config update, sync и restart;
- убийство процесса в середине партии и автоматическая докачка после запуска;
- отказ от импорта неполного snapshot и сохранение существующего workspace;
- ошибка/прерывание обновления формы с восстановлением прежней пары файлов;
- обновление APK поверх проекта с сохранением SQLite, форм и `.ngrc`-тайлов.

Практическая настройка: [collector-project-setup.md](../runbooks/collector-project-setup.md).
Незавершённые задачи: [collector.md](../roadmap/collector.md).

## Подложки как общие ассеты

NGRc/MBTiles принадлежат общему app-private каталогу, проект содержит только `shared_underlay_id`. Удаление проекта защищает legacy подложки и снимает ссылки; composition sync не управляет ассетами. Closed workspace unlink не создаёт новый `MapBase`. См. [полный контракт](shared-underlays.md).
