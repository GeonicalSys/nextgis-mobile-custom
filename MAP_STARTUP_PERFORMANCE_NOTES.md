# Map Startup Performance Notes

Дата: 2026-06-30

Контекст: приложение долго показывает "подготовку карты" при старте, особенно если в векторных слоях несколько тысяч объектов. В коде уже есть попытка ускорения через `VectorLayerRenderCache`, но пользователь отметил, что чтение/валидация кэша занимает почти столько же времени, сколько обычная подготовка.

Этот файл фиксирует результаты изучения без внесения изменений в код, чтобы позже быстро вернуться к теме.

## Краткий вывод

Текущий MapLibre-пайплайн устроен как "прочитать все объекты всех видимых слоев -> собрать Java `FeatureCollection` -> передать source в MapLibre". Поэтому даже хороший disk cache не убирает главный архитектурный расход: приложение все равно готовит большой GeoJSON/Feature list в Java перед нормальным отображением карты.

От этой прогрузки можно уйти. Самые перспективные направления:

1. Быстро показывать карту со стилем и пустыми/лениво наполняемыми sources, не ждать подготовки всех слоев до `setStyle`.
2. Для read-only слоев на cache HIT отдавать MapLibre готовый `features-styled.geojson` как native file URI, вообще не парся его в Java.
3. Перейти на viewport/lazy loading: грузить только объекты текущего экрана + буфер, обновляя source на `onCameraIdle`.
4. Для больших read-only слоев в перспективе строить локальные vector tiles/MBTiles и дать MapLibre читать их как tiled source.

## Где сейчас тратится время

### Стартовый вход

- `app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt`
  - `onMapReady()` грузит `ngwstyle.json`, берет `mapRef.getAllLayers()` и вызывает:
    - `mapDrawable.loadLayersToMaplibreMap(styleJson, allLayers, true, true)`

### Главный full-load pipeline

- `maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java`
  - `loadLayersToMaplibreMap(...)` сначала в worker-потоке готовит все данные слоев, и только потом на main thread вызывает `mapForStyle.setStyle(...)`.
  - Для каждого `VectorLayer`:
    - пробует `VectorLayerRenderCache.tryLoadFeatures(layer)`;
    - если MISS, делает `createFeatureListFromLayer(layer)`;
    - кладет результат в `sourceFeaturesHashMap`;
    - уже после `onDidFinishLoadingStyle()` вызывает `createSourceForLayer(...)` и `createFillLayerForLayer(...)`.

Важное следствие: базовая карта/стиль стартуют после подготовки данных, а не до нее.

### Сбор FeatureCollection

- `maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java`
  - `createFeatureListFromLayer(VectorLayer layer)` выбирает `getPointFeatures`, `getLineFeatures`, `getPolygonFeatures` и т.д.
  - Каждый `get*Features` вызывает `layer.getFeatures()`.

- `maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java`
  - `getFeatures()` сейчас идет в `getFeaturesLARGEDATA()`.
  - `getFeaturesLARGEDATA()` читает атрибуты cursor'ом, но геометрию достает отдельно по каждой строке через `readLargeBlob(...)`.

Для тысяч объектов это превращается в большой проход по SQLite + N отдельных чтений geometry BLOB.

## Почему текущий render cache помогает слабо

- `maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java`
  - `tryLoadFeatures(layer)` читает `features-geom.geojson`;
  - парсит весь GeoJSON обратно в Java `List<Feature>`;
  - затем вызывает `MPLFeaturesUtils.refreshMaplibreStyleOnFeatures(layer, geom)`.

Проблема: `refreshMaplibreStyleOnFeatures` для rule-style/подписей достает исходный `ngFeature` по `featureid`, то есть может снова ходить в SQLite по каждой фиче. Это убирает полный geometry scan, но не убирает:

- Java JSON parse большого файла;
- создание большого `List<Feature>`;
- применение стиля по всем объектам;
- потенциальные N SQLite reads для атрибутов/лейблов/rule-style.

Дополнительно:

- `USE_MAPLIBRE_NATIVE_GEOJSON_URI` выключен.
- `tryLoadAsUri(layer)` вызывается уже после `tryLoadFeatures(layer)`, то есть native URI путь не является настоящим fast path.
- В `tryLoadAsUri` условие выглядит подозрительно: комментарии говорят про read-only слои, но код возвращает `null`, если `!layer.isEditingAllowed()`.

## Уже существующие полезные механизмы

### Spatial cache / RTree

- `VectorLayer.reloadCache()` грузит `RTREE`.
- `VectorLayer.query(GeoEnvelope env)` умеет быстро вернуть feature ids по bbox.

Этот механизм сейчас почти не используется MapLibre-startup pipeline'ом: `createFeatureListFromLayer` берет все объекты, а не только bbox.

### Simplified geometries by zoom

- `VectorLayer.prepareGeometry(...)` пишет колонки `geom_18`, `geom_16`, ... через `Constants.FIELD_GEOM_ + zoom`.
- Есть `getGeometryForId(rowId, zoom, db)`.

MapLibre full-load pipeline сейчас обычно берет полную геометрию, а не zoom-aware simplified geometry.

### Lite reload

- `MapDrawable.loadLayersToMaplibreMapLite(...)` пересобирает слои без повторного чтения user-layer features.

Но это работает только после того, как `sourceFeaturesHashMap` уже заполнен в текущем процессе. Для холодного старта не решает проблему.

## Low-hanging fruit

### 1. Пропуск слоев вне текущего zoom

Сейчас `loadLayersToMaplibreMap(..., skipInvisibleLayers=true)` пропускает invisible layers, но не видно раннего skip по `layer.getMinZoom()/getMaxZoom()` относительно текущего MapLibre zoom.

Если слой на текущем zoom все равно невидим, не надо читать его features при старте. MapLibre layer можно создать пустым или отложить до входа в zoom range.

Риск низкий, эффект зависит от проектов и настроек zoom.

### 2. `setStyle` до тяжелой подготовки

Сейчас `setStyle` вызывается после worker-подготовки. Можно поменять порядок:

1. Сразу применить `ngwstyle.json`.
2. Добавить edit/location/track служебные sources/layers.
3. Добавить пустые sources/layers пользовательских слоев.
4. В фоне наполнять sources по мере готовности.

Это не всегда уменьшит суммарное CPU, но сильно улучшит perceived startup: карта появляется раньше, а слои догружаются постепенно.

### 3. Переставить native URI cache HIT в начало

Для подходящих read-only слоев:

1. Проверить meta/styleFp/geomGeneration.
2. Если есть `features-styled.geojson`, вернуть URI.
3. Не читать `features-geom.geojson` в Java.
4. Не создавать `List<Feature>` для такого слоя.

Нужно отдельно решить:

- polygon label centroid source;
- selection/edit fallback;
- проверку, почему раньше native URI мог давать empty source;
- условие `isEditingAllowed()`.

## Среднесрочный вариант: viewport/lazy loading

Идея: не грузить весь слой при старте. Грузить только текущий viewport + буфер.

Техническая опора уже есть:

- текущий viewport можно взять через `maplibreMap.getProjection().getVisibleRegion().latLngBounds`;
- bbox надо конвертировать в WebMercator `GeoEnvelope`;
- `VectorLayer.query(GeoEnvelope)` вернет ids через RTree;
- геометрию можно читать через `getGeometryForId(id, zoom, db)`, используя simplified columns;
- source обновлять через `GeoJsonSource.setGeoJson(...)`;
- `MapFragment.onCameraIdle()` уже существует.

Пример поведения:

1. На старте создать пустые sources/layers.
2. После `onDidFinishLoadingStyle` запустить загрузку viewport.
3. На `onCameraIdle` с debounce проверить, вышли ли за loaded envelope.
4. Если вышли, асинхронно догрузить новый envelope с буфером.
5. Держать per-layer LRU/set загруженных feature ids.
6. На zoom change выбирать другую generalized geometry колонку и обновлять слой.

Риски:

- pop-in при быстром перемещении карты;
- подписи на границах viewport;
- rule-style и label template требуют атрибутов;
- выбор/редактирование объекта, которого нет в текущем source;
- нужно аккуратно синхронизировать `sourceFeaturesHashMap`, потому что много edit/select кода опирается на него как на полный список.

Компромисс: включить viewport loading только для больших read-only слоев, а editable слои оставить на старом full-load до отдельной доработки.

## Долгосрочный вариант: локальные vector tiles

Самый правильный способ полностью уйти от full-layer preload для больших read-only данных:

1. После импорта/синхронизации слоя строить локальный MBTiles/MVT cache.
2. Подключать его в MapLibre как vector tile source.
3. Стиль задавать MapLibre expressions/layers поверх vector source.
4. При изменении данных инвалидировать/пересобирать тайлы нужных zoom/bbox.

Плюсы:

- MapLibre сам грузит только нужные тайлы;
- холодный старт почти не зависит от общего количества объектов;
- меньше Java heap и меньше `FeatureCollection` на старте.

Минусы:

- крупная работа;
- нужна библиотека/реализация MVT writer;
- сложнее редактирование;
- rule-style/labels надо перевести в tile properties;
- нужно хранить/обновлять tile cache.

Практичный гибрид: vector tiles только для read-only NGW/collector слоев, editable слои оставить GeoJSON/lazy.

## Что измерить перед изменениями

Добавить/включить временные тайминги по этапам:

- `MapFragment.onMapReady -> loadLayersToMaplibreMap start`;
- worker prep total;
- per-layer:
  - cache meta check;
  - cache parse;
  - style refresh;
  - DB build;
  - feature count;
  - geometry type;
  - GeoJSON bytes;
- `setStyle` wait;
- style apply body;
- `createSourceForLayer` per layer;
- `FeatureCollection.fromFeatures(...)` time;
- first map visible / progress dismissed.

Без этих цифр легко оптимизировать не тот этап.

## Рекомендуемый порядок работ

### Этап A: безопасные быстрые улучшения

1. Ранний skip по visibility + min/max zoom.
2. Показ карты до подготовки user layers.
3. Layer-by-layer progressive source population.
4. Тайминги и логирование.

Ожидаемый эффект: лучшее ощущение старта, меньше работы на проектах с zoom-limited слоями.

### Этап B: настоящий fast path render cache

1. Исправить/проверить read-only native URI path.
2. Не вызывать `tryLoadFeatures` перед `tryLoadAsUri`.
3. Для URI path не создавать Java `List<Feature>`.
4. Отдельно кэшировать polygon label points или временно fallback для подписей.
5. Включать только для `!isEditingAllowed()` / collector display-only после проверки.

Ожидаемый эффект: большой выигрыш на повторном старте read-only слоев.

### Этап C: viewport/lazy loading для больших слоев

1. Ввести per-layer mode: full-load vs viewport-load.
2. Начать с read-only слоев и/или слоев больше порога feature count.
3. Использовать RTree bbox и generalized geometry.
4. Обновлять source на camera idle.
5. Сохранить full-load fallback.

Ожидаемый эффект: старт перестает зависеть от общего размера слоя, зависит от текущего экрана.

### Этап D: vector tiles для read-only

После стабилизации B/C оценить MVT/MBTiles. Это самая сильная, но самая дорогая архитектурная замена.

## Главные точки входа для будущей задачи

- `app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt`
  - `onMapReady()`
  - `onCameraIdle()`
  - `reloadMapStyleAndLayersAfterLayerFillBatch()`

- `maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java`
  - `loadLayersToMaplibreMap(...)`
  - `prepareVectorLayerForMaplibre(...)`
  - `loadLayersToMaplibreMapLite(...)`
  - `sourceFeaturesHashMap`
  - `sourceNativeUriMap`

- `maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java`
  - `createFeatureListFromLayer(...)`
  - `refreshMaplibreStyleOnFeatures(...)`
  - `createSourceForLayer(...)`
  - `convertToPointFeatures(...)`

- `maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java`
  - `tryLoadFeatures(...)`
  - `tryLoadAsUri(...)`
  - `save(...)`

- `maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java`
  - `getFeaturesLARGEDATA()`
  - `query(GeoEnvelope env)`
  - `getGeometryForId(rowId, zoom, db)`
  - `prepareGeometry(...)`
  - `reloadCache()`

## Осторожность

- `sourceFeaturesHashMap` сейчас используется не только для рендера, но и для selection/edit/update paths. Если перейти на URI или lazy source, нужно добавить fallback: читать feature из SQLite по id при выборе/редактировании.
- Для polygon signatures сейчас нужен отдельный point source, который строится из полного списка polygon features. При lazy/URI path его надо кэшировать или строить только для viewport.
- Rule-style и label template требуют атрибуты. Если грузить только geometry shell, стиль может потребовать дополнительные поля из SQLite.
- Предыдущая история проекта уже отмечала MapLibre `Expression.toArray()` NPE family; при включении отключенных flags нужен on-device regression.

