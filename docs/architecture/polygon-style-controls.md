---
title: Штриховки, контуры полигонов и живой образец
type: architecture
last_verified: 2026-10-24
related_code:
  - maplib/src/main/java/com/nextgis/maplib/display/PolygonPatternOptions.java
  - maplib/src/main/java/com/nextgis/maplib/display/PolygonOutlineOptions.java
  - maplib/src/main/java/com/nextgis/maplib/display/ConfigurablePolygonPatternRegistry.java
  - maplib/src/main/java/com/nextgis/maplib/display/SimplePolygonStyle.java
  - maplib/src/main/java/com/nextgis/maplib/display/FieldStyleRule.java
  - maplib/src/main/java/com/nextgis/maplib/display/MplStyleMapper.java
  - maplib/src/main/java/com/nextgis/maplib/map/MplFeatureStyleProps.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileProvider.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/MplLayerStyleVars.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/PolygonLayerFactory.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/PolygonOutlineLayerFactory.java
  - maplib/src/test/java/com/nextgis/maplib/display/PolygonStyleOptionsTest.java
  - maplib/src/test/java/com/nextgis/maplib/map/PolygonStyleTileSupportTest.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/StylePreviewView.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/PolygonStyleControls.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FeatureTypePreview.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - tools/mobile-test-plan.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleTileFallbackTest.java
---

# Штриховки, контуры полигонов и живой образец

В низком альбомном окне закреплённый образец имеет высоту 40 dp, в портретном —
100 dp. Это оставляет место для целой кнопки цвета при fontScale 2 и 500×280 dp:
на API 36 прежние 48 dp оставляли viewport 199 px для кнопки высотой 207 px.
Related `polygon-style-ui` сохраняет строгое сравнение полной видимой рамки и
диагностирует точный профиль/высоты, затем проверяет реальные Save/Cancel.

## Параметры и совместимость

SimplePolygonStyle имеет два необязательных объекта JSON v1: `pattern_options`
и `outline_options`. Отсутствие сохраняет прежние bundled PNG/alpha и простой
контур. Каждый объект копируется независимо при clone и наследуется целиком
из «Прочие» только при отсутствии у категории. `enabled:false` — явное отключение,
которое сохраняется после reload и не заменяется настройками «Прочие».
Открытие редактора не создаёт новые параметры до включения соответствующего раздела.

Встроенные шесть узоров получают угол (по часовой стрелке от горизонтали карты),
шаг 4–64 px, толщину 0,5–16 px, цвет и отдельную непрозрачность. У перекрёстной
и лесной штриховок есть независимое второе направление. Если толщина достигает шага, линии закрывают фон; у точек это диаметр.
Заливка фона может быть выключена или прозрачна при видимой штриховке.
Custom image сохраняет прежнюю семантику и приоритет; новые генераторные настройки
к нему не применяются. Существующее смещение заливки продолжает сдвигать геометрию,
а не только фазу узора. Штриховка имеет фиксированный экранный шаг: geometry zoom
curve её не растягивает. Редактор проверяет диапазоны и блокирует Save при ошибке.

Для бесшовного повторения используются целочисленные волновые векторы на текстуре
512×512 (для кирпичной перевязки — чётная решётка). Угол и шаг слегка привязываются
к этой решётке; максимальный шаг имеет более заметное округление направления.
Имена изображений зависят от параметров стиля, а не от отдельных объектов.
RGBA texture не содержит общей alpha: pattern opacity и layer opacity применяются
ровно по одному разу. CPU LRU ограничен 4 MiB; native images сохраняются только
до удаления/смены стиля последнего использующего их логического слоя.

Контур: сплошной, пунктир, точки, штрихпунктир или 2–16 положительных длин штрихов
и пропусков. Основной цвет, толщина и alpha остаются прежними полями полигона.
Новые настройки: cap/join/miter, смещение −64…64 px, просвет 0…64 px между
параллельными линиями. Дополнительная сплошная обводка рисуется под основным
контуром: собственные цвет/alpha и добавочная ширина 0…32 px с каждой стороны.
Геометрические границы и picking не меняются. Native dash/cap/join задаются
константами в фильтрованных `-layout-contour-*` children общего GeoJSON source;
видимость, порядок, zoom limits, restyle и удаление идут через логический слой.
Новые параметры требуют classic renderer: настроенный MVT-слой при смене стиля
переходит через существующий полный reload к GeoJSON. Возможности MVT не расширены.

## Живой образец

StylePreviewView — небольшой offline MapLibre MapView над прокруткой настроек.
Он отображает геометрический символ точки, линии или полигона через те же
Point/Line/PolygonLayerFactory, общий генератор узоров и FireflyLayerFactory.
Подписи объектов в этом образце не показываются. Изменения определяются по JSON
перед отрисовкой окна, без таймера или доступа к рабочей карте/сети. MapView и
его изображения освобождаются при закрытии; исходный стиль и слой не меняются.
Чекбоксы имеют явный native widget style, чтобы overlay-тема сохраняла индикатор состояния и первое касание. Save/Cancel сохраняют обычные callbacks редактора. Клавиатура работает штатно.
Миниатюры категорий используют тот же procedural bitmap и настройки контура;
точный живой образец с масштабом/смещением — отдельный native MapView редактора.

## Проверки и доставка

PolygonStyleOptionsTest проверяет JSON/clone/presence, независимую alpha,
периодичность всех шести узоров на обеих осях при разных углах и шагах,
реальные bitmap pixels, конечные параметры и byte budget. Native
PolygonStyleRenderingTest проверяет сохранённые категории, GPU pixels, отверстия,
Polygon/MultiPolygon, дробный zoom, picking, видимость, restyle и image cleanup.
PolygonStyleTileFallbackTest начинает с настоящего VectorSource.
PolygonStyleEditorTest проверяет 72 локальных русских theme/orientation/font
1/1,3/2/display normal/320/280 профиля, живой native образец и реальные
configure/casing/Save/Cancel touches. Системные настройки телефона не меняются.

Все классы включены в Related styles и полный native каталог. Related PhoneOnly
разрешён только для `com.nextgis.mobile.verification.debug`; Full остаётся
отдельным обязательным этапом перед публикацией. Текущие Draft PR: maplib #60 →
maplibui #44 → app #78. До будущего выпуска библиотеки сливаются Merge Commit,
затем consumers закрепляют remote merge commits; приложение сливается Squash.
Публикация, merge и выпуск сейчас не выполняются. Фактический результат текущего
прогона записывается отдельно, наличие теста не означает PASS.

Native runner после каждого rendering case сохраняет bounded PNG из фиксированных QA-папок verification-приложения: до 4 MiB на файл и 16 MiB на case, только безопасные PNG-имена, SHA-256 index. Рабочие файлы приложения не читаются. Отдельный case живого образца проверяет реальные pixels точек/линий/полигонов и изменение alpha настоящим жестом до Save.

Фактический результат: [проверка 2026-10-24](../reference/polygon-style-verification-2026-10-24.md).

При входе из списка категорий RuleFeatureRendererUI открывает независимую копию эффективного стиля, включая наследованные параметры «Прочие». Cancel не меняет исходный rule JSON; Save заменяет только выбранную категорию и закрепляет показанный effective snapshot. Native case actualCategoryEntryUsesInheritedPreviewAndCancelCannotMutateTheRule проходит именно этот вход реальными touches.

Адресная область style-categories проверяет настоящую строку категории, наследованные настройки, Cancel/Save и отдельную кнопку удаления. NativeStyleTouch перед targeted инъекцией ждёт фокус окна, полную видимость и стабильные физические координаты; UID guard Android сохраняется.
