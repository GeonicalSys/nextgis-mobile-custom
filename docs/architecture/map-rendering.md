---
title: MapLibre rendering и порядок слоёв
type: architecture
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
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

## Контракты

1. В `LayerGroup` индекс `0` означает низ стека.
2. Для NGRc/local TMS слой помещается над OSM, если OSM существует; иначе — в
   нижнюю позицию, а не поверх всего пользовательского стека.
3. `signaturesRootLayer` и raster sibling anchor учитываются при вставке style
   layer; нельзя полагаться только на порядок обхода.
4. Hot-add raster обязан привести список и карту к одному порядку без restart.
   Текущий путь включает `loadLayersLite()` после добавления raster.
5. Полный reload нельзя без необходимости подменять lite reload: у них разные
   гарантии по пересозданию style и сохранению UI/edit state.
6. Start/end flag sources/layers для треков намеренно не включаются.

IDs: `INV-LAYER-ORDER`, `INV-HOT-ADD-CONSISTENCY`, `INV-NO-TRACK-FLAGS`.

## Изменение rendering pipeline

Перед правкой определить:

- cold start это, hot add, reorder или edit overlay;
- меняется модель `LayerGroup`, MapLibre style или оба уровня;
- кто вызывает `loadLayersLite`/full reload;
- существует ли style sibling в момент вставки;
- не теряется ли deferred reload после batch layer fill.

Минимальный regression набор: `SMOKE-MAP-COLD-START`, `SMOKE-NGRC-ORDER`,
`SMOKE-HOT-RASTER`, `SMOKE-LAYER-REORDER`.

## Производительность

Исторический анализ находится в
[`../../MAP_STARTUP_PERFORMANCE_NOTES.md`](../../MAP_STARTUP_PERFORMANCE_NOTES.md).
Он является исследованием, а не разрешением включать parallel preparation,
native URI или viewport loading без отдельного профилирования и regression
matrix.
