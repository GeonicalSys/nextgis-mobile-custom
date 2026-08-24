---
title: Производительность запуска карты
type: architecture
last_verified: 2026-08-24
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileProvider.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileServer.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileRequestPolicy.java
---

# Производительность запуска карты

## Текущий pipeline

Классический путь для видимого векторного слоя читает все объекты, формирует Java
`FeatureCollection` и передаёт её в `GeoJsonSource`. `MapDrawable.loadLayersToMaplibreMap()` готовит
данные в worker-потоке до полного применения style. На больших слоях стоимость запуска поэтому
зависит от общего числа объектов, чтения geometry BLOB, стилизации и объёма Java-объектов.

`VectorLayerRenderCache` сокращает повторное чтение геометрии, но обычный cache hit всё ещё парсит
GeoJSON в Java и может повторно читать атрибуты для rule-style и подписей. Native GeoJSON URI не
считается production fast path: `USE_MAPLIBRE_NATIVE_GEOJSON_URI = false` до отдельной on-device
проверки.

## Уже реализовано

- spatial query через RTree и `VectorLayer.query(GeoEnvelope)`;
  identify затем уточняет hit: полигоны — PIP, линии — пересечение с
  tap-envelope (±20dp), не bbox объекта;
- zoom-aware упрощённые геометрии `geom_<zoom>`;
- render cache с безопасным fallback на построение из БД;
- lite reload для уже подготовленных source-данных;
- opt-in `local_vector_tiles` для read-only polygon/multipolygon и простых
  точечных слоёв;
- loopback `LocalVectorTileServer` и ленивый `LocalVectorTileProvider`; вместо
  `newCachedThreadPool` server использует максимум два worker и очередь 16,
  сериализует сборку тайлов одного слоя, закрывает ожидающие socket старого
  поколения карты и возвращает retryable `503` при переполнении либо остатке
  heap менее 64 МБ;
- fallback на классический `GeoJsonSource` для неподдерживаемой геометрии или ошибки provider.

Точечный fast path намеренно узкий: только `GTPoint`, простой renderer,
`SimpleMarkerStyle.MarkerStyleCircle` (`type = 2`), без custom icon, rule-style и label
template. Подпись поддерживает одно поле (`field`, включая `_id`) либо
фиксированный `text`. Редактируемый слой и любой неподдерживаемый вариант стиля
остаются на classic path.

## Следующие кандидаты

1. Добавить измерения по этапам: worker preparation, cache parse/style refresh, DB build,
   `setStyle`, создание source и время до первой видимой карты.
2. Рано пропускать слои вне текущего zoom и показывать style до подготовки тяжёлых user layers.
3. Проверить native URI как настоящий первый cache-hit путь для read-only слоёв, включая подписи,
   selection и fallback чтения объекта из SQLite.
4. Для больших read-only слоёв исследовать viewport/lazy loading с debounce на `onCameraIdle`, RTree
   и упрощённой геометрией.
5. Усилить local-vector-tile путь: дальнейшая simplification MVT, bounded
   memory/disk cache, style parity и метрики latency/overload.

## Ограничения безопасности

- `sourceFeaturesHashMap` используется не только rendering-кодом, но и selection/edit/update paths.
- Подписи полигонов требуют отдельного point source.
- Точечный MVT fast path не поддерживает `MultiPoint`, rule-style, custom icon
  и label template; эти случаи обязаны сохранять classic fallback.
- Rule-style и label templates требуют атрибутов, а не только геометрии.
- Progressive/lazy загрузка не должна менять порядок слоёв или терять deferred reload после batch fill.
- Любой новый режим сначала включается только для read-only слоя с явным metadata/config opt-in и
  сохраняет classic fallback.

## Проверка гипотезы

Минимальная perf-матрица: cold/warm start, cache hit/miss, 5–10 тыс. и около 50 тыс. полигонов
или точек, editable/read-only, подписи и rule-style, screen off/on и повторный вход. Сравнивать p50/p95 по
этапам, peak heap, отсутствие ANR и корректность identify/edit fallback.
