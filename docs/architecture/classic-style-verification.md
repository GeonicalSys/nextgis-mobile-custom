---
title: Проверки обычных векторных стилей и подписей
type: architecture
last_verified: 2026-10-25
related_code:
  - maplib/src/main/java/com/nextgis/maplib/display/FieldStyleRule.java
  - maplib/src/main/java/com/nextgis/maplib/display/LabelAttributes.java
  - maplib/src/main/java/com/nextgis/maplib/display/Style.java
  - maplib/src/main/java/com/nextgis/maplib/display/MplStyleMapper.java
  - maplib/src/main/java/com/nextgis/maplib/display/MarkerIconRegistry.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/LineLayerFactory.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/MplLayerBuildContext.java
  - maplib/src/main/java/com/nextgis/maplib/map/RuleLabelLayouts.java
  - maplib/src/main/java/com/nextgis/maplib/map/RuleGeometryLayouts.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/PointLayerFactory.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/PolygonLayerFactory.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - tools/mobile-test-plan.json
---

# Проверки обычных векторных стилей и подписей

## Настраиваемые полигоны и живой образец

[Пакет](polygon-style-controls.md) добавляет PolygonStyleRenderingTest (смешанные сохранённые категории, независимая alpha, реальные цветные pixels, дырки, Polygon/MultiPolygon, дробный zoom, visibility/picking/cleanup), PolygonStyleTileFallbackTest и PolygonStyleEditorTest. Последний проверяет 72 русских профиля с живым native MapView и реальными Configure/Casing/Save/Cancel touches. Классы включены в Related styles; отдельная область polygon-styles предназначена для адресной разработки. Наличие сценариев не заменяет фактическую квитанцию.

## «Светлячок»

[Отдельный тип точки](point-firefly.md) проверяется через тот же SQL/JSON/native
путь: чёткое ядро при blur 0–3, отсутствие ореола внутри прозрачного ядра,
независимые alpha и layer opacity, Point/MultiPoint и общая zoom curve.
Смешанная категория сохраняет пиксели прежнего размытого круга. Picking,
видимость, custom icon overlay, смена типа и удаление не оставляют лишних
изображений. Редактор имеет отдельную матрицу 72 локальных профилей с реальными
touches, Save и Cancel. Оба класса включены в Related styles; явный PhoneOnly
разрешён только в отдельном verification APK. Эмулятор остаётся частью Full.
Переход уже загруженного точечного VectorSource к classic fallback проверяется
отдельным `FireflyTileFallbackTest` и не расширяет возможности tile renderer.

## Общая матрица

Матрица использует собственные synthetic VectorLayerUI в verification APK.
До проверок обязательно assert !LocalVectorTileRenderMode.isRequested и
!shouldUseLocalVectorTiles; native source обязан быть GeoJsonSource. Настроенные
через Web GIS local-vector-tiles слои не выбираются по названию/меню и не входят
в эту матрицу. Их renderer, список возможностей и fallback не расширяются.

Путь проверки: SQL attributes/geometries -> сохранённый renderer JSON ->
FieldStyleRule -> feature properties -> production MPLFeaturesUtils -> native
MapLibre -> snapshot pixels. Шрифты и marker/pattern images берутся из bundled
assets; реальные NGW accounts/данные/сервер не требуются.

Pixel assertions требуют working host OpenGL. Headless `swiftshader_indirect`
воспроизводит отсутствие подписей даже при правильных feature props, camera,
native visibility и zoom limits; дополнительный sleep это не исправляет.
CI запускает emulator `-gpu host` под Xvfb с Mesa llvmpipe; renderer information
сохраняется в `graphics-host.txt`. Все 16 rendering cases остаются обязательными,
включая реальные pixels на zoom 12/19 и отсутствие за category max на 21.
Fixture отключает только стартовый geo-dialog в disposable preferences,
восстанавливает его после закрытия и меняет camera/underlay visibility через
модель карты, чтобы delayed redraw не отменял подготовку.

| Область | Покрытие |
|---|---|
| Категории | trim/case, совпадение, NULL/прочие, renderer fallback, все шесть обычных геометрий |
| Подписи | поле/шаблон, русский текст, цвет/размер, halo, font, justify/transform, spacing/line height, padding/max width, overlap/optional/upright, max angle, placement/rotation, zoom limits, category zoom curves |
| Наследование | Все LabelAttributes наследуются из «Прочие», только отсутствующие настройки; explicit false/default/empty сохраняются после JSON reload |
| Точки | встроенные формы, цвет/размер/opacity, 8 направлений подписи, asset icon и его size/rotate/offset/anchor/collision flags, собственная category zoom curve и explicit scale=false |
| Линии | cap/join/miter, blur/offset/gap/outline multiplier, dash presets/custom array, fill/stroke opacity |
| Полигоны | fill on/off, fill/stroke opacity, translate, все bundled patterns |
| Реальный результат | snapshot pixel checks для zoom, opacity, layer opacity, marker sizes/shapes и polygon fill |

Модульные регрессии воспроизводили отсутствие halo/font/collision inheritance,
замену explicit false scale и 255 opacity значением Other. LabelAttributes хранит
наличие настройки из setter/JSON; clone копирует presence set. Type-default и
false имеют самостоятельный смысл при явном задании и сериализуются. Старый JSON
без ключа продолжает наследовать Other. Style отдельно сохраняет explicit size
scale=false. Изменение не добавляет возможностей vector-tile renderer.

Новая область `styles` входит в Related и весь native каталог Full. Это матрица
поддерживаемых настроек и важных сочетаний; она не доказывает все комбинации
произвольных внешних стилей, шрифтов и данных. Новые обнаруженные дефекты добавляются
как отдельные наблюдаемые native cases; одних JSON/getter checks недостаточно.

## Native layout constraints

[MapLibre symbol spec](https://maplibre.org/maplibre-style-spec/layers/) marks
line-height, padding, keep-upright, max-angle, allow-overlap, optional, spacing,
placement and rotation alignment as data-constant. Feature get expressions for
these fields were ignored by native rendering. Classic RuleLabelLayouts now sets
literal layout values and groups only categories with different constant layouts
into filtered native SymbolLayers sharing the original GeoJSON source. These are
rendering children, not extra logical/project layers. Native font discovery also
requires literal font stacks, so distinct category fonts participate in these
groups. Geometry opacity, text paint and label zoom remain data-driven. Rule label zoom no longer clamps all
categories to Other's range. Group visibility, style replacement and deletion
follow the logical layer; local-vector-tile builders are untouched.

Icon allow-overlap/ignore-placement and fill-translate are also native constants.
RuleGeometryLayouts groups their effective category values; classic factories use
literals, including the asset-icon overlay which previously forced overlap/ignore
on. Child layers preserve filters, paints, zoom and visibility. Restyle/deletion
remove children, order anchors include them, and feature picking queries all
geometry children and dashed strokes of the selected logical layer. These children share the original
source and do not change project menus or configured tile layers.

Pinned Android MapLibre 13.0.2 also rejects feature expressions for line-cap and
line-miter-limit. Ordinary line constants use filtered rendering children; native
rule dash layers are grouped by the effective array and layout, including custom
arrays. Changing dashed to solid explicitly clears the old native dash property.
Copied properties retain PaintPropertyValue/LayoutPropertyValue routing in the
Android SDK, so child text/geometry paints and opacity are applied to the proper
native property namespace. Native snapshots and category picking verify this.

The font matrix verifies visible Cyrillic glyphs in all four bundled Open Sans
faces and a changed raster result when the configured font changes. Getter
retention is insufficient: the SDK can retain an expression rejected by native
font discovery. Polygon snapshots explicitly enable fill and verify fill-off.

Category geometry/text zoom curves carry their effective stop spec in feature
properties. The renderer unions all curve breakpoints in one top-level interpolate;
its stop outputs choose each category multiplier without nesting zoom under case.
This preserves piecewise-linear interpolation and native expression constraints.
Explicit nullable label defaults (overlap/upright) survive JSON null reload.
Explicit scale=false remains fixed, and an explicit cleared geometry curve stays
cleared after JSON reload instead of inheriting Other. Non-finite stops are rejected.

Style snapshots prepared by hot add/full/style reloads carry the renderer
fingerprint. A changed renderer prevents their native commit; a stale hot-add
snapshot retries current data so its source cannot be lost. The delayed
4.5-second GeoJSON reapply refreshes current props instead of restoring old rules.
Native fixtures compare current source style properties (with numeric float
tolerance), await native idle for camera/placement and then a fresh fully rendered
frame, keeping pipeline completion distinct
from the SDK paint transition duration.

The marker pixel matrix also found disappearing cross strokes: generated SDF
images placed the boundary at 0.5 instead of the native shader's 0.75. The
registry now encodes the correct edge and eight-pixel distance range, retaining
thin shapes and their requested sizes. See the [native SDF shader](https://github.com/maplibre/maplibre-native/blob/main/shaders/symbol_sdf.fragment.glsl).

С 25 октября линейные подписи проверяются дополнительно Related line-labels: whole-word point layout для текущего экстента заменяет изгиб букв. Repeat/spacing/curvature сохраняются в JSON, но выбран новый runtime one-per-visible-object. Field/font/category/halo/alpha/zoom/collision и исходная геометрия сохраняются. См. [контракт](visible-line-labels.md).
