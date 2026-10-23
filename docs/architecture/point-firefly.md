---
title: Точечный стиль «Светлячок»
type: architecture
last_verified: 2026-10-24
related_code:
  - maplib/src/main/java/com/nextgis/maplib/display/SimpleMarkerStyle.java
  - maplib/src/main/java/com/nextgis/maplib/display/FireflyMarkerRegistry.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/FireflyLayerFactory.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/PointLayerFactory.java
  - maplib/src/main/java/com/nextgis/maplib/map/MplFeatureStyleProps.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FeatureTypePreview.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyTileFallbackTest.java
---

# Точечный стиль «Светлячок»

В настройках стиля точки выбрать тип **«Светлячок»**. Размер и цвет ядра задают
чёткую середину; цвет, ширина и размытие свечения — окружающий ореол.
Непрозрачность ядра и свечения настраивается отдельно. Свечение начинается
у края ядра и убывает наружу. Даже прозрачное ядро остаётся свободным от ореола.
Нулевое размытие даёт резкую внешнюю границу, нулевая ширина отключает ореол.
При масштабировании по zoom ядро и весь ореол увеличиваются вместе.

Сам выбор типа сохраняет существующие параметры. Кнопка **«Образец „Полевые
точки“»** явно задаёт белое ядро, розовый `#FF6685` ореол, размер 6, ширину 14
и размытие 2, сохраняя выбранную непрозрачность и подписи. Это мобильный образец
по категории «прочие» эталона `stand_project/styles/Основная/Полевые точки.qlr`:
в QGIS используются большой размытый розовый диск 5 мм и чёткий белый диск
1,66667 мм. Образец передаёт характер символа и отношение размеров; единицы
и фильтр размытия Android отличаются от QGIS.

## Формат и отрисовка

`SimpleMarkerStyle.type=9` — новый самостоятельный тип. Номера 1–8 и существующий
`circle_blur` обычных точек сохраняются. Для ядра используются прежние
`color`, `size`, inner alpha, для ореола — `out_color`, `width`, outer alpha;
`circle_blur` задаёт мягкость только для нового типа. Дополнительной миграции
JSON нет. Clone, category resolver и renderer JSON сохраняют тип и параметры.

`FireflyMarkerRegistry` строит RGBA sprite с независимыми покрытиями ядра и
ореола. Покрытие ореола исключает всю внутреннюю область. Для blur > 0 наружу
идёт Gaussian falloff; края сглажены в пределах одного sample. Память bitmap
ограничена 512 × 512 и LRU 4 MiB. Размеры конечны и ограничены только в новом
рендерере; модель обычных точек не меняется. Прежнее преобразование размеров
через `getMPLThinkness` сохраняется, но нулевая ширина действительно даёт 0.

Регистрируются только изображения effective стилей, а не варианты на каждый
объект. Native Style хранит изображения, пока хотя бы один логический слой их
использует. Restyle и удаление освобождают лишние native images; общий CPU LRU
ограничен независимо. Непрозрачность слоя применяется один раз к готовому sprite.

`FireflyLayerFactory` создаёт отдельные non-SDF `SymbolLayer` с идентификаторами
`<geometry-id>-layout-firefly-*`. Обычные Circle/SDF слои исключают type=9;
их blur/paint не меняются при добавлении категории «Светлячок». Collision flags
собираются в обычные группы констант layout. Дочерние слои разделяют GeoJSON
source, zoom, порядок, видимость и picking логического слоя. Подписи и custom
icon overlay сохраняют собственные существующие настройки.

Canvas preview использует тот же sprite; список категорий вписывает его целиком.
Поддерживаются обычные Point/MultiPoint, простые и категорийные стили. Возможности
configured local-vector-tiles не расширены: новый тип не входит в их набор
простых кругов. Если изменённый стиль уже загруженного tile-слоя больше не
поддерживается provider, MapDrawable просит штатную полную перезагрузку host.
Она согласованно заменяет source на GeoJSON и восстанавливает порядок слоёв.
Переход проверяется отдельным `FireflyTileFallbackTest`; общая матрица обычных
стилей по-прежнему исключает configured vector tiles.

## Проверки и доставка

`FireflyMarkerTest` проверяет реальные bitmap pixels, прозрачное ядро, независимые
alpha, нулевую ширину, ограничение памяти, JSON/clone и удаление устаревших props.
Два native случая `VectorStyleRenderingTest` проверяют Point/MultiPoint,
мягкость 0–3, реальные пиксели, layer opacity, zoom, сохранность старого blur,
смешанные категории, picking, visibility, icon overlay и cleanup.
`FireflyStyleEditorTest` проверяет настоящий редактор и реальные touches в 72
локальных конфигурациях: обе темы/ориентации, fontScale 1/1,3/2, ширина
360/320/280 dp. Для каждого профиля выполняются оба исхода Save/Cancel
(144 прохода диалога), а preview проверяется по реальным bitmap pixels.
Системные параметры смартфона не меняются.
`FireflyTileFallbackTest` начинает с настоящего native VectorSource точки,
меняет стиль через публичный reload и требует GeoJsonSource с видимым
«Светлячком» без перезапуска приложения.

Команда: `tools\verify-mobile-tests.ps1 -Scope Related -Area styles`;
для явно выбранного verification смартфона разрешены `-PhoneSerial <serial>
-PhoneOnly`. Это адресные проверки; Full обязателен отдельно перед публикацией.
Состояние фактического выполнения записывается в квитанции runner, а не выводится
из наличия тестового класса.

Порядок будущего слияния: maplib Merge Commit → maplibui Merge Commit → root
PR #78 Squash с pins на удалённые merge commits библиотек. Текущие Draft PR
не являются выпуском; версия и публикация в этой доработке не меняются.
