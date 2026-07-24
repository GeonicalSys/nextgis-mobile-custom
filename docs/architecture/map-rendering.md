---
title: MapLibre rendering и порядок слоёв
type: architecture
last_verified: 2026-07-24
related_code:
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerGroup.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWRasterLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
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
11. Rule-based векторный стиль: слойные MapLibre-дефолты (size/text stops,
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
    tap-envelope (±20dp), не пустой угол bbox линии.

IDs: `INV-LAYER-ORDER`, `INV-HOT-ADD-CONSISTENCY`, `INV-NO-TRACK-FLAGS`,
`INV-NGRC-PRESERVE`, `INV-LOCATION-CURSOR-TOP`, `INV-DEFAULT-OSM-BOTTOM`,
`INV-TRACK-LAYER-TOP`, `INV-COLLECTOR-RASTER-STYLES`.

## Изменение rendering pipeline

Перед правкой определить:

- cold start это, hot add, reorder или edit overlay;
- меняется модель `LayerGroup`, MapLibre style или оба уровня;
- кто вызывает `loadLayersLite`/full reload;
- существует ли style sibling в момент вставки;
- не теряется ли deferred reload после batch layer fill.

Минимальный regression набор: `SMOKE-MAP-COLD-START`, `SMOKE-LOCATION-CURSOR-TOP`, `SMOKE-NGRC-ORDER`,
`SMOKE-NGRC-PRESERVE`, `SMOKE-HOT-RASTER`, `SMOKE-LAYER-REORDER`,
`SMOKE-COLLECTOR-IMPORT`.

## Производительность

Текущий анализ, уже реализованные механизмы и следующие гипотезы находятся в
[map-performance.md](map-performance.md). Документ не является разрешением
включать native URI, progressive preparation или viewport loading без отдельного
профилирования и regression matrix.
