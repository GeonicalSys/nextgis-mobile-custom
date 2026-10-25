---
title: Цельные подписи видимой части линейных объектов
type: architecture
last_verified: 2026-10-25
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/VisibleLineLabelLayout.java
  - maplib/src/main/java/com/nextgis/maplib/map/VisibleLineLabelController.java
  - maplib/src/main/java/com/nextgis/maplib/map/RuleLabelLayouts.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VisibleLineLabelsTest.java
---

# Правило размещения

Пользователь явно выбрал одну подпись на SQL-объект **для видимой части в текущем
экстенте**, а не одну на всю геометрию. Текст — цельный прямой блок, повёрнутый
по преобладающему направлению видимых сегментов. Его длина не обязана помещаться
на прямом отрезке; допускается пересечение надписью нескольких сегментов своего
объекта. Запрет изгиба через text-max-angle отвергнут: он скрывал бы слова на
полностью извилистой линии.

## Расчёт

VisibleLineLabelController привязан к конкретным MapDrawable/MapLibreMap/MapView.
После остановки камеры/native idle он берёт уже загруженный Java snapshot либо
native source features, без повторного SQLite/GeoJSON-файлового чтения. Камера
и четыре угла MapView фиксируются в том же поколении. Geometry clipping и
направление вычисляются в общем background worker; новые движения камеры
отменяют устаревший расчёт. Старый стиль, карта или View не получают результат.
Пересчёта на каждом frame нет; GPS/track и собственный label source его не
запускают. После применения point source повторный native idle не создаёт цикл.

В Mercator сегменты клиппируются выпуклым четырёхугольником истинного экрана,
включая поворот/pitch. Переход через 180° разворачивается относительно viewport.
Для одного ID объединяются MultiLineString и native tile fragments; одинаковая
геометрия не учитывается повторно. Направление — length-weighted axial mean:
противоположное направление оцифровки не разворачивает надпись. При отсутствии
преобладания используется длинная ось видимого bbox. Точка привязки — ближайшая
к центру экрана точка видимого сегмента, поэтому она остаётся на самом объекте.

Label source `<geometry source>-text` хранит один Point на ID и текущие style
properties. Геометрия основного источника, SQL, editing/picking и данные для
синхронизации не меняются. Layout styles/categories сохраняют прежние grouping
keys для совместимости с cached feature metadata, но native symbol-placement
становится point. Text rotation — data-driven world angle с keep-upright после
settle; явно горизонтальный стиль остаётся viewport/0. Text-max-width=0 сохраняет
слово цельным. На повороте карты block автоматически вращается вместе с map;
после остановки пересчитываются видимый фрагмент и upright.

Сохраняются field/template, fonts, halo, цвет/alpha, category rules, layer order,
zoom limits и стандартные collision/optional settings. Native collision может
скрыть пересекающиеся подписи, как и прежде; изгиб/длина прямого участка больше
не являются причиной их отсутствия. Старые repeat/spacing/max-angle остаются
в переносимом JSON для совместимости, но новый runtime явно показывает одну
прямую подпись на видимый объект. Запечённые в серверном raster подписи клиент
переориентировать не может. Загрузка слоёв/формат тайлов не переделываются.

## Проверки

Pure layout regression: извилистая линия, offscreen direction, дедупликация,
multipart, настоящий повёрнутый viewport, crossing без внутренних вершин,
camera upright, dateline и отмена устаревшего расчёта. Related `line-labels`
проверяет реальные SQL/source/camera/GPU, длинное русское слово без прямого
участка, pan/zoom, rotation/category и прежнюю геометрию picking. Компактные
PNG из verification `line-label-qa` сохраняются runner перед reset.

Семантика native point/line, rotation и ограничения max-angle сверены с
[официальной спецификацией](https://maplibre.org/maplibre-style-spec/layers/).
Фактические результаты фиксируются в квитанции задачи; отсутствие измерений
FPS/cold start не считается performance PASS.
