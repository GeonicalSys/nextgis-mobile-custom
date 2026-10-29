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

## Удаление общих накладных расходов cache/queue — 28 октября2026

По следующему прямому поручению владельца реализован узкий follow-up к
[ручному разбору T40](../reference/mobile-resource-profile-t40-2026-10-28.md):
убрать scan/sort при каждом put и удалять отменённые ожидания до blocking IO.
Новый замерщик, установка и замеры не готовятся; нужен обычный APK для телефона.

Производный PBF cache сохраняет прежние имена/identity и byte/entry/1024-file
budgets. Общий canonical-root ledger с registry8roots и incremental recency
заменяет полный проход в горячем put. Bootstrap/reconciliation при изменении
каталога отдельно читают по одному metadata record; после этого один новый тайл
не компилирует regex и не сортирует/перечитывает все файлы. Старые providers
заново получают актуальный ledger при операции, safe miss/Android cache loss и
полные атомарные записи сохраняются. Нативный current guard проверяется внутри
записи; HTTP сохраняет прежние внешние guards, без1мс socket probe подdisklock.
Это обслуживание текущего кэша, без новой долговременной политики/индекса на диске.

До shared IO dispatch добавлен общий отменяемый допуск2. SDK cancel удаляет
ожидание до blocking payload; активное IO держит lease до настоящего unwind,
отдаёт его до Main, а predispatch failure освобождает один раз. Валидные
parent/fade/adjacent tiles не отсекаются по возрасту или масштабу. Existing memory
gate2/1 и владелец source/content/style/edited object остаются прежними;
HTTP/line-label scheduler и общее число SDK Job этим не изменяются.

Контракт `INV-LOCAL-TILE-CACHE-ADMISSION`, карточка
`maplib-20261028-tile-cache-queue`. Адресные source/build результаты принадлежат
новому APK receipt; прежние114мс и p95очереди8,59с не являются результатами
после исправления. Реальная скорость/полнота кадра и2–3с проверяются отдельно;
Full/публикация по-прежнему отложены.

Техническая проверка:70 JVM tests/9classes PASS50с, включая25 cache и7 admission;
0fail/error/skip. LisaDebug/LisaRelease/BelkaRelease PASS1мин50с, version matrix
PASS с прежними версиями. Source fingerprint1658 runtime/build files
`1ea9ac886867116d6e3d5c6707e83b74f6caad7644410e563441cdc37952de3d` неизменен
до/после сборки. Обычный Lisa APK224/3.1.2.30:
SHA256`b0f55cda77d4bc3965d00e12a316654f9a7de98c9eab060ded7e56337bb67e37`,
70 580 085 байт, прежний signer, DEBUG/FIELD_VERIFICATION/PRIVATE_RESOURCE_PROFILING
false, profileable element отсутствует. Никакого postprocessing/установки/профиля.
После самостоятельной проверки владелец сообщил «Почти моментально отрисовывается
всё». Это положительный субъективный отзыв, не численный benchmark и не проверка
полноты всех объектов/подписей. Её владелец явно оставил на потом.

Первая whole-contour проверка до правок завершилась exit0. Финальные refresh и
retry получили INVALID из-за контрактных документов чужого desktop checkout
(standart_profiles/AGENTS.md, geonical-docs scenario-dependencies/local-ci/
mobile-client). Это не PASS. Семантические связи сохранены в own app registries,
source/mobile input hashes стабильны; canonical root index write/check остаётся
UNKNOWN/follow-up владельцу, без изменения/заморозки чужих файлов. Частный APK
не объявляется сквозной release acceptance.

## Диагностика задержки планшета — 2026-10-28

Final same-dense1403/11.7z strict video content gate (включая последнюю верхнюю
розовую полосу):843d32.45–34.09s,6039 13.68–15.65s,D10110.33–12.27s с clock/
launch margin. Late map ROI вне HUD/controls попиксельно совпала с обоими(MAE0).
Один processcold run/build с сохранённым disk/system cache, не FPS/CI/проверка
всех объектов/стилей. Это отдельное positive native scene witness, не сумма
phase timings. Target2–3s пока не достигнут. Final ALLmaplib750PASS/132classes/
0fail/error/skip за59s; sourcefp62de8740…59ec91 неизменен.

Первый private843d фазовый capture на T40/Collector1403 подтвердил application
read hot path: один startup tile z11 занял9908.73ms для3051 объектов,
attributes query/cursor/materialization5281.16ms, BLOB1547.78ms, IO queue1367.68ms.
У других tiles очередь достигла3431.57ms; Main dispatch максимум0.596ms.
В опубликованных слоях39 scopes не наблюдались из-за diagnostic cap16, это
потеря telemetry, не rendering requests. У полностью unobserved слоя без
первого snapshot счётчики пока могут не публиковаться; суммы reported layers
не являются process-global denominator. Отсутствие native-ready witness явно.

Private6039 bounded attributes/PFD вариант сохранил observed3051 originals,
2,076,156B и356,630B PBF; BLOB stage всё ещё1458.11ms. Initial843d и cold6039
дают5281.16→791.25ms attributes для этого scope, но это не matched-cold/full-scene
оценка ускорения: тяжёлый scope отсутствует в cold843d telemetry.

Следующий source-кандидат сохраняет32-ID attributes-only cursors и один
compiled PFD statement на visit. Отдельный geometry-only cursor на API28+
материализует не более32 полных BLOB<=16KiB в явном1MiB window; large BLOB,
API26/27 и safely unsupported driver читаются прежним полным PFD путём.
Nested CASE typeof/length исключает large/TEXT payload, без truncation.
512KiB small payload — не общий RAM budget: sorter buffers, Java buffer и
decoded geometry остаются отдельными. Query/getCount/refill/getBlob отнесены к
BLOB_READ, attrs projection прежняя. Нет потери объектов/полей/NULL/geometry или
изменения style/order/epochs/gate/free-only policy. Обязательный повтор —
sharp17→12 и полностью новая область; полный кадр требует отдельного видео witness.

Последний ordinary PRIVATE D101 собран normalall3 PASS62s, matrixall3 PASS,
71focused JVM PASS/independent review. Actual API30 cold1403 подтвердил window
и PFD:1350inline+2PFD в10 terminal representatives/1352 consumed originals,
нулевые read/window/SQLite/fatal markers в retainedPIDlog. Same9logical-counter
cohorts121features/140772B/PBF22217: BLOB83.57→13.28ms;110features/41376B/
78labels/PBF16476:134.11→15.37ms;163line originals/47484B:42.24→8.59ms.
Это совпадение объёмов, не cryptographic equality/tileidentity.110-feature elapsed
при этом791.51→3775.86ms из-за IOqueue6.17→3374.41ms: каждый tile быстрее не
объявлен.3051 terminal detail D101 не попал в throttle snapshot, поэтому его
сравнение с6039 недоступно. Полностью unobserved layers/33published lost scopes
оставляют process-global denominator/max/p95 неизвестными.

Свежая CURRENT787 пара legacycab9→finalD101 после изменения2TMS/LVPC/bbox/data:
42+42 meminfo/6+6taps/210s каждый,lateRSS820.42→643.06MiB(-21.62%), sampledpeak
896.19→751.16(-16.18%),PSSlate700.41→522.28MiB. GraphicsPSSlate вырос143.36→
210.14MiB(+46.58%). Одна последовательная пара не pooledABBA/CI и не draw-speed;
старые31.15% относятся к другому прежнему состоянию. ExactcameraUNKNOWN,
visible14.5z/99.4m/основные landmarks совпадают, label placement отличается.

Для reported3–10s до полного кадра около12.1z на T40 Plus_ROW/API30 добавлены
локальные typed phase timings `LISA-SqlitePhase`. На планшете фактически открыт
Collector1403 (`collector_1403_f97c303e`), поэтому телефонные замеры787 не
подтверждают его скорость. Фазы различают IO queue, layer lock/gate/cache,
RTree, attributes/BLOB/full decode, style/clip/labels, PBF и Main dispatch;
счётчики показывают candidates, reads, BLOB/PBF bytes, labels, hits/misses и
retries без повторного чтения данных. Лог ограничен10s/слой,16 scopes и32
summaries, observation loss и недоступные timings явны.

Это сбор недостающих свидетельств. Геометрии, число объектов, scale gates,
кэши, workers и политика свободного остатка сохранены. После private normal
build нужен тот же планшет, проект и воспроизводимая камера; Java SDK return
не равен полной отрисовке. Причина всей задержки12.1 и эффект нового read-кандидата
на других сценах и sharp17→12/newextent требуют отдельной проверки.

## Текущий pipeline

TrackLayer не VectorLayer и не допускается в новый ordinary tile path.
Saved visible tracks держат все выбранные points/GeoMultiLineString в mTracks
и полном GeoJSON; active unfinished snapshot тоже полный, без viewport limit.
Это сохранённый прежний маршрут, не оптимизация этой итерации. Отдельная
bounded pending queue recording service не ограничивает RAM rendering snapshot;
реальная память длинного трека пока не измерена.

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

### Обычная приватная сборка и первые замеры787,28Oct2026

Runtime source `1b0c09ce…` (app `dbbd453f77f3`, maplib8d41588/maplibui edc189cc)
собран обычными Debug/Lisa Release/Belka Release без verification flags:
build02 PASS83s, полная version matrix PASS. Приватный Lisa Release имеет
SHA-256 `ada0f5b806bc3d98a67037bd9e6020a01dbe55bb62455f4468d0292c3fbc0cb2`;
это не merge, публикация или завершённый Full.

В исходной конфигурации787 —11 видимых ordinary vectors,3 скрытых vectors и2
видимых LocalTMS растра. Source gate направляет поддерживаемые ordinary layers
в SQLite; первый журнал прямо подтвердил7 native ID и3 line-label overlays,
но не перечислил четыре main source ID. Полнота всех объектов/подписей не
следует из этих counters или снимков. Legacy «classic fallback» remote729/733
печатается до SQLite apply и не является свидетельством полной GeoJSON копии.

Первый B diagnostic на SM-A566B выполнил300s idle/pan/zoom/idle с total
RSS460.7–614.3MiB. Он предшествует включению владельцем подписей выделов и
не принимается за строгое A/B сравнение или retained-allocation attribution.
Отдельная A-B-B-A серия обычных baselinecab9/SQLite APK с одинаковыми подписями
и камерой реального PK3688 дала168 samples: A1/B1/B2/A2 retry по42/6/210s.
Среднее двух run-level statistics A→B: median RSS последних60s958.12→659.66MiB
(−31.15%), sampled peak1043.82→792.76MiB (−24.05%). Graphics PSS late60
219.48→230.91MiB (+5.2%). Это не pooled median, confidence interval или
retained-ownership profile. Во время memory нет heapprofd/видеозаписи.
A1 итоговый UI export FAIL сохранён; original A2 partial после ADB5037 отказа
исключён, completed retry отдельный. Все168 raw meminfo проверены, вся DB
побайтно неизменна, все16 configs совпали с labels-ON basis. Exact camera UNKNOWN.
Отдельные A1/B1/B2 phone scene medians примерно0.2–0.5s перекрываются, speedup
не установлен. Остаётся f809 numeric20px legacy CENTER/generic TOP difference.
[Подробные факты и незакрытые проверки](../reference/mobile-crash-224-2026-10-27.md).

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

1. Проверить сообщённые3–10s планшета Collector1403 при резком zoom17→12 и
   переходе в полностью новую область. Две небольшие warm pan11.7 и быстрые
   phone787 scenes этого не принимают. Фазы нового private843d — диагностика;
   оптимизацию выбирать только по подтверждённому hot path и повторять тот же
   route. RSS/PSS сами по себе не устанавливают retained owners.
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

## Приватное исследование холодной отрисовки, 28 октября 2026

Внеплановый этап MOB-04 по прямому поручению владельца: собрать подробные локальные
данные во время ручного прогона на T40, затем выбирать оптимизации по измерениям.
Долговременный кеш не рассматривается и этим этапом не реализуется. Обычный
производственный путь сохраняется: `privateResourceProfiling` по умолчанию false.
Отдельная приватная Release-сборка включает `PRIVATE_RESOURCE_PROFILING`; только
её копия получает profileable/shell через существующий manifest-only signing helper.
Она сохраняет package/signing/data, не является новым публичным выпуском.

`RenderResourceProfiler` независимо от старых cap16/32 считает запросы, исходы,
объёмы BLOB/PBF/подписей и wall/CPU по этапам. CPU clock сравнивается только внутри
одного TID, но этого недостаточно через coroutine suspension/cancellation:
в trial28октября CPU `io_queue` признан непригодным и исключён из рейтингов;
до повторного использования wait/suspend phases требуют CPU-unknown. Handoff
wall не превращается в CPU. Глобальные/послойные агрегаты сохраняются
при потере raw: явны queue/active/layer overflow и пропущенные подробности.
Layer rows объединяют одинаковые numeric IDs; первая сессия ограничивается одним
проектом, raw source_epoch позволяет отличать поколения. Это не присвоение
нативной памяти конкретному слою по дельтам общей памяти процесса.

Приватный app-side JSONL в Downloads/LISA-Diagnostics содержит числовые SDK frame
stats, SDK-managed texture/buffer bytes, scenes/zoom, Java/native/RSS/PSS/Graphics,
затраты наблюдателя и контрольные aggregate snapshots (первый через10с, затем30с).
SDK durations seconds переводятся в ms; это не GPU time и не OS thread CPU.
Aggregate/subcategory bytes не суммируются; отрицательные int counters invalid.
Не включаются renderedFeatureInfo/feature-ID lists, координаты, URI и атрибуты.
`returned_to_sdk`, `fully` и `idle` различаются и не принимают полноту карты.

Экспорт ограничен64MiB/60мин на процесс, очередь512records/2MiB, footer имеет
резерв256KiB; фактическое окончание по размеру явное. STOP требует session nonce
и sender permission DUMP. Нет новых пользовательских permissions/GC/wakelock.
Raw локальные данные не отправляются в GlitchTip. При process death footer может
отсутствовать; checkpoints и raw остаются частичным свидетельством.

Первый ручной trial:30мин лёгких метрик и5 окон по180с native malloc/simpleperf
(15мин тяжёлого coverage, распределённых по сеансу). У каждого окна собственные
PID/start_ticks, APK/source/signing identity, предел размера и quality/loss flags.
Late attach не видит прежние native allocations; retained из разных окон не
складывается. Данные Graphics не выводятся из heapprofd. Подтверждение переноса
файла не равно пригодности профиля. Все профили локальны, без root/ADB permissions
changes и без остановки чужих jobs. Scoped-storage обхода через private app files нет.

Установка и короткий read-only diagnostic smoke выполняются только на выбранном
T40, с сохранением его проектов и очередей, после проверки отсутствия редактора
и записи трека. Это не Full и не новая style/OOM/physical2GiB приёмка. Full и
публикация APK по предыдущему поручению остаются отложены. Установка/первый запуск,
реальные SDK counters и native/CPU sample coverage подтверждаются отдельными
receipts; до них состояние — подготовлено, не принято. Никакие кеши, геометрии,
правила, схемы или бизнес-данные этим сборщиком не оптимизируются и не меняются.

Инструкция: [ручной сбор ресурсов](../runbooks/manual-resource-profiling.md).
Карточки: android-20261028-private-render-resource-profiler,
android-20261028-resource-profiler-tools и maplib-20261028-resource-profiler.

Первый ручной сеанс завершён пользователем через19мин; получены3окна по180с,
полный23270-record app footer и independently checked counts, loss0.
Подтверждён приоритет обслуживания текущего disk cache перед геометрией:
пустой/непустой новый тайл требует почти одинакового CPU, scan/regex/sort/stat
каталога выполняется перед каждым put. Native profiler заметно влияет на CPU;
эти времена не являются ordinary-build benchmark. Полные выводы/границы и
приоритет без нового долговременного кэша:
[разбор T40](../reference/mobile-resource-profile-t40-2026-10-28.md).
