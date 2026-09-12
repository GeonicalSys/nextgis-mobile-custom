---
title: MapLibre rendering и порядок слоёв
type: architecture
last_verified: 2026-09-13
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerGroup.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWRasterLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/util/MbTilesInfo.java
  - maplib/src/main/java/com/nextgis/maplib/util/LegacyTileMbtilesMath.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureGeometryValidator.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerIdentifyPolicy.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorRasterLayerHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/ReorderedLayerView.java
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
---

# MapLibre rendering и порядок слоёв

## Ownership

- `MapDrawable` строит и обновляет MapLibre style, sources и layers.
- `MPLFeaturesUtils` содержит операции style и определение sibling anchors.
- `VectorLayerRenderCache` ускоряет подготовку векторных слоёв при cold start.
- `LayerFillService` загружает и вставляет импортированные слои в `LayerGroup`.
- `ReorderedLayerView` синхронизирует порядок UI и модели.
- `MapFragment` — host `MaplibreMapInteraction` и точка reload/lite reload.
- Gradle-файлы `app`, `maplibui` и `maplib` совместно задают один native
  MapLibre backend для конечного APK.

## Контракты

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
   растров, подписей и edit overlays. `iconAllowOverlap` и
   `iconIgnorePlacement` не позволяют collision detection скрывать курсор.
9. Пользовательский слой «Мои треки» остаётся последним элементом внутреннего
   `LayerGroup` и первой строкой перевёрнутого UI-списка. Collector batch
   вставляет project-managed слои ниже этой границы, а открытие существующей
   карты исправляет ранее сохранённый неверный порядок.
10. Collector vector и поддерживаемые QGIS style resources используют один
    `collector_order`. Style materializes как authenticated `NGWRasterLayer`,
    поэтому `computeCollectorOrderedInsertIndex()` учитывает и vector, и raster
    NGW layers; remote id стиля отвечает за tile identity, parent resource id —
    только за extent.
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
    сервер ограничен двумя worker и очередью 16; тайлы одного слоя строятся
    последовательно, устаревшая очередь закрывается при смене поколения карты,
    а нехватка heap/перегрузка возвращает retryable `503`, не создавая новые
    потоки и не меняя rendering mode слоя.
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
25. MapLibre Android `13.0.2` разрешается только через явный артефакт
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
    Редактор точки владеет своими selected/vertex sources, не забирая линию
    обхода. Панель обхода сохраняется при обычном режиме карты; все её команды
    блокируются от выбора слоя точки до успешного Save либо явного Cancel.
    После подтверждённого FINISHED геометрия передаётся обычному редактору.

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

Минимальный regression набор: `SMOKE-MAP-COLD-START`, `SMOKE-LOCATION-CURSOR-TOP`, `SMOKE-NGRC-ORDER`,
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

`user-location-source` содержит свежие Point и Polygon accuracy. Заливка в метрах
лежит непосредственно под верхним курсором; при expiry оба очищаются. Трек
рисуется только по сохранённым сегментам, без линии к текущему display fix.
Подробности: [GPS pipeline](location-pipeline.md).
