---
title: Производительность запуска карты
type: architecture
last_verified: 2026-10-28
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/MPLFeaturesUtils.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/VectorLayerRenderCache.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileProvider.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileServer.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileRequestPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/map/NativeFeaturePayload.java
  - maplib/src/main/java/com/nextgis/maplib/map/RenderMemoryGuard.java
  - maplib/src/main/java/com/nextgis/maplib/map/RenderMemoryPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/map/RenderTileWorkGate.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileDiskCache.java
  - maplib/src/main/java/com/nextgis/maplib/display/PatternTilePeriod.java
  - app/src/main/java/com/nextgis/mobile/util/RenderMemoryNotice.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/RenderMemorySafetyTest.java
---

# Производительность запуска карты

## Текущий pipeline

Обычные поддерживаемые Simple/Field Rule векторные слои всех шести geometry
types теперь подключаются через `SqliteVectorLayerController`: SQLite/RTree
читает нужные полные оригиналы на ограниченных IO workers, encoder формирует
MVT8192, штатный `CustomVectorSource13.7.1` передаёт PBF native без HTTP. Полного
стартового GeoJSON/Java списка слоя нет. Это не zero-copy: JNI и native PBF
decoder имеют свои allocations. Источник читает полную геометрию; редактор
получает один не обрезанный DB original, а не MVT координаты.
Polygon anchors закодированы в том же PBF с отдельной Point ролью; Fill/Line
исключают эти точки, Symbol использует их. Line labels читают только viewport
оригиналы. Малые edit/selection/cursor sources остаются GeoJSON. SQLite и
project/interchange JSON не меняются. Новый source lifecycle, все стили и
скорость требуют отдельной native приёмки; текущая реализация ещё не выпущена.

Классический путь для видимого векторного слоя читает все объекты и сохраняет полный
rich Java снимок для редактирования. `NativeFeaturePayload` формирует отдельную
компактную `FeatureCollection` для `GeoJsonSource`, сохраняя исходную геометрию,
bbox, IDs, неизвестные поля и фактически используемые paint/label значения.
`MapDrawable.loadLayersToMaplibreMap()` готовит данные в worker-потоке до полного
применения style. Стоимость зависит от числа объектов, чтения geometry BLOB,
стилизации, Java/native копий и внутреннего GeoJSONVT индекса SDK.

`VectorLayerRenderCache` сокращает повторное чтение геометрии, но обычный cache hit всё ещё парсит
GeoJSON в Java и может повторно читать атрибуты для rule-style и подписей. Native GeoJSON URI не
считается production fast path: `USE_MAPLIBRE_NATIVE_GEOJSON_URI = false` до отдельной on-device
проверки. Он сохранён для неподдерживаемых расширенных renderers и технических
compatibility bind, а не является обычным startup путём поддерживаемых слоёв.

## Что установлено профилем787

В квалифицированной выборке native retained allocations около673MiB из907MiB
(74,2%) принадлежат GeoJSONVT index, в том числе не менее564MiB — property maps;
около128MiB относятся к растровым данным. Это доли выборки allocator, а не
разложение всего RSS процесса и не размер исходных файлов. Подтверждён лишний
повторяемый payload атрибутов обычного GeoJSON пути. Тяжёлая геометрия, растры
или local MVT не объявляются единственной причиной по общему числу памяти.

Высокое плато при повторном обходе карты и монотонная утечка требуют разных
свидетельств. Удержание внутренних тайлов GeoJSONVT установлено; неограниченный
внутренний index не получил eviction от этих правок. Такой payload существовал
и в223, поэтому точный триггер резкого ухудшения223→224 остаётся неизвестным.
Для экспериментального кандидатаec71 уже получен qualified180s capture:
sampled retained net951044804→391105220bytes, sampled malloc peak
997182148→455068356bytes. Это измерение всего candidate bundle, не изолированная
доля одной правки. Последовательность входных жестов одинакова, но старт
маршрута после receipt различался:13,4s против74,3s; равенство камеры/времени
этим не установлено. Candidate ещё содержал прежний ошибочный RSS-trigger,
который мог отключить SDK cache при большом свободном остатке. Поэтому эти
числа не принимают финальную free-only policy, последующий editor follow-up,
FGS witness или устройство2GiB. Первый candidate capture с data loss остаётся
FAIL и не используется вместо qualified повторного. Счётчики malloc/RSS/graphics
имеют разные accounting и не складываются в общий расход.
[Разбор инцидента](../reference/mobile-crash-224-2026-10-27.md),
[карточка payload](../../maplib/update-reports/entries/maplib-20261028-native-geojson-payload.md).

## Уже реализовано

- spatial query через RTree и `VectorLayer.query(GeoEnvelope)`;
  identify затем уточняет hit: полигоны — PIP, линии — пересечение с
  tap-envelope (±20dp), не bbox объекта;
- legacy zoom-aware геометрии `geom_<zoom>` остаются отдельным механизмом;
  эта защита памяти не вводит дополнительного упрощения или отбора ID;
- render cache с безопасным fallback на построение из БД;
- lite reload для уже подготовленных source-данных;
- opt-in `local_vector_tiles` для read-only polygon/multipolygon и простых
  точечных слоёв;
- loopback `LocalVectorTileServer` с максимум двумя worker и16 ожидающими
  socket, последовательной сборкой тайлов одного слоя и отменой старого
  поколения карты; fair gate сохраняет два builder при нормальных ресурсах
  и один только при малом измеренном свободном остатке системной/Java памяти;
- полный derived PBF кэш только read-only источников:32MiB на low-RAM,
  128MiB на остальных, максимум1024 файлов и4MiB на запись; ключ включает
  процесс, поколение карты, canonical layer, content version, стиль и тайл;
- local MVT coarse prefetch выключен, source volatile; переполнение, прежний
  Java headroom guard64MiB и текущая нехватка памяти дают явный retryable503.
  Неполный тайл не выдаётся как200 и не записывается в кэш;
- компактные native свойства отдельно от rich Java снимка; только непустые
  polygon labels получают отдельные points с нужными label properties;
- точный повторяемый ARGB мотив штриховки вместо полной512×512 текстуры,
  если он восстанавливает каждый пиксель исходной решётки;
- fallback на классический `GeoJsonSource` для неподдерживаемого слоя либо
  недоступности provider на этапе выбора/регистрации source. Ответ503 уже
  работающего tile source сам по себе не переключает слой на classic.

Прежний HTTP точечный fast path намеренно узкий: только `GTPoint`, простой renderer,
`SimpleMarkerStyle.MarkerStyleCircle` (`type = 2`), без custom icon, rule-style и label
template. Подпись поддерживает одно поле (`field`, включая `_id`) либо
фиксированный `text`. Редактируемый слой и любой неподдерживаемый вариант стиля
остаются на classic path этого старого выбора. Новый direct SQLite выбор идёт
первым и поддерживает editable/multipart/Field Rule/standard icon/labels;
ограничения старого HTTP encoder не являются его capability contract.

## Экономия ресурсов и сообщение пользователю

`RenderMemoryGuard` принимает решения по текущему свободному остатку системной
памяти, её порогу/OS low-memory state и Java headroom. Занятое другими
приложениями устройство8GiB может перейти к экономии, а устройство2GiB с
достаточным остатком не замедляется только из-за номинального класса.
Installed RAM/Android low-RAM class задают лишь обычный disk cache и резерв
свободной памяти32/64MiB. Used/native/RSS — только диагностика; фиксированного
порога занятой памяти или доли установленной RAM для предупреждения нет.
Малый свободный остаток позволяет отключить межмасштабный cache и вызвать
SDK `onLowMemory`. Перегрузка очереди — отдельный факт задержки, который сам
по себе не устанавливает нехватку RAM.

Все исходные объекты/координаты, visibility, SQLite, saved style, draft и sync
сохраняются. Нет произвольного thinning, новых LOD, снятия регистрации или
фона, подменяющего полный source частичным viewport. Обычная MVT обрезка и
кодирование остаются прежними. Callback проверяет текущих map/view/host и
lifecycle epoch; pause/destroy останавливают watcher. Команда «Повторить»
пересматривает cache policy, без полной повторной загрузки всех GeoJSON.

App показывает одно автоматическое предупреждение за процесс, включая
пересоздание Activity, другие слои и проекты. Повторный просмотр состояния
возможен вручную; это не настройка и не постоянный запрет слоя.
Дисковый PBF кэш — производные тайлы, не межпроцессный cache и не файл подкачки
native heap. SDK GeoJSONVT внутренний index может пережить cache-off/onLowMemory.
Нет гарантии отсутствия OOM или системного завершения. Восстановление SDK тайла
после503 при возвращении памяти требует отдельной native проверки.

Карточки: [экономия](../../maplib/update-reports/entries/maplib-20261027-render-memory-budget.md),
[точный мотив](../../maplib/update-reports/entries/maplib-20261027-pattern-exact-motif.md).

## Следующие кандидаты

1. Повторить финальный free-only candidate на том же проекте787: cold/warm,
   фиксированный pan/zoom, возврат камеры, задержки, RSS/native retained owners.
2. Проверить полноту всех поддерживаемых styles/labels, picking/edit и recovery503
   на реальном SDK, затем устройства2GiB и8GiB при дефиците свободной памяти.
3. По профилю исследовать ограничение внутренних GeoJSONVT структур и точный
   триггер223→224. Простая повторная установка всей FeatureCollection под
   давлением недопустима без оценки пика old+new native allocation.
4. Развивать ленивую подготовку только с доказанной полнотой объектов,
   исходной геометрией, owner/version fences и прежними edit/identify контрактами.
   Упрощение геометрии сейчас не является выбранным решением.

## Ограничения безопасности

- `sourceFeaturesHashMap` остаётся rich editor cache только в classic compatibility;
  direct SQLite editor/selection читает один original, общий список пуст.
- Непустые polygon labels в direct SQLite — inline Point anchors PBF с отдельной
  ролью; classic сохраняет отдельный GeoJSON point source. Пустые подписи не
  создают anchor records, Fill/Line не используют Point роль.
- Ограничения старого HTTP point MVT (нет MultiPoint/rules/custom icon/template)
  относятся к прежнему provider; новый direct route имеет отдельный metadata-only
  capability contract и native parity gates для всех шести geometry types.
- Rule-style и label templates получают атрибуты из одного DB original за раз.
- Progressive/lazy загрузка не должна менять порядок слоёв или терять deferred reload после batch fill.
- По прямому поручению MOB-04 direct SQLite включён для поддерживаемых ordinary
  editable/read-only слоёв; неизвестные расширенные renderers сохраняют classic
  fallback. Старый HTTP opt-in не является переключателем нового direct пути.

## Проверка гипотезы

Минимальная perf-матрица: cold/warm start, cache hit/miss, 5–10 тыс. и около 50 тыс. полигонов
или точек, editable/read-only, подписи и rule-style, screen off/on и повторный вход. Сравнивать p50/p95 по
этапам, peak heap, отсутствие ANR и корректность identify/edit fallback.

`RenderMemorySafetyTest` использует отдельную техническую SQLite карту:
129 объектов с65 координатами, точные ID и координаты Java input, полный hash
SQL строк, native IDs, session notice/recreate/second layer/manual status.
Его ограниченная диалоговая матрица не заменяет Full real-window profiles,
а технический corpus не является приёмкой реального787/устройства2GiB.
`MapRenderStressTest` сохраняет6480×65 technical fixture и>=90s взаимодействия:
seed/workload PIDs различаются, effective source IDs ведут к current SQLite
CustomVectorSource, оба global feature cache пусты. Независимая known-grid
геометрия и actual viewport задают требуемые полностью видимые FID до native
query; потеря этих ID или попадание label Point в Fill role — FAIL. Schema2
отклоняет прежние HTTP/VectorSource counters и неполный pre-sleep таймер.
Peak RSS/PSS/Java/native остаются наблюдениями; без отдельного независимого
ownership/plateau oracle новые hard caps и leak PASS не вводятся. Этот stress
не измеряет p50/p95 появления корректного кадра и не подменяет парный787/Азимут
benchmark на plain release.

Unit/build результаты не доказывают native экономию или устранение исходного
std::bad_alloc. Текущая Related/Full приёмка на устройстве и эмуляторе остаётся
не завершённой; публикация — отдельный gate основной задачи.
