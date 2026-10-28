---
id: android-20261028-native-sqlite-rendering-test
area: Проверки памяти карты
kind: internal
at: 2026-10-28T02:57:00+01:00
title: Проверка полного SQLite слоя через новый native источник без общей GeoJSON копии
why: Новый способ чтения обычных векторов должен сохранять все объекты, исходную геометрию, категории и подписи при обновлении источника
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/NativeSqliteVectorRenderingTest.java
  - tools/mobile-test-plan.json
  - tools/mobile_sqlite_evidence.py
  - tools/tests/test_mobile_sqlite_evidence.py
  - tools/run_mobile_tests.py
---

MOB-04, app82 после maplib61. Добавлен отдельный ограниченный Android native
класс с девятью методами. Первые три используют129 технических полигонов
по65координат с category/NULL/Other атрибутами в isolated verification SQLite. Это
технический corpus, не рабочие данные проекта787 и не подмена реальной приёмки.
Создание слоя использует существующий isolated Fixture только для Activity,
метаданных SQLite, камеры и snapshot; его classic `bind` и полная
`createFeatureListFromLayer` не вызываются.

Production `MapDrawable.reloadVectorLayerDataToMaplibre` должен выбрать
CustomVectorSource и оставить nonnull empty совместимый Java cache. После
камеры, вмещающей весь corpus, native rendered polygon IDs обязаны точно
совпасть с1..129; paint properties каждого ID проверяются по независимым
цветовым ожиданиям. Три реальные snapshot области подтверждают category,
NULL и Other заливки. Три русские подписи проверяются как фактически
размещённые native symbols с точным текстом и цветом. Все полные original
координаты и поля каждого FID перечитываются по одному через production DB
helper и сравниваются с исходными списками. Typed SHA-256 всех колонок/строк
должен остаться неизменным; tile clipping/quantization не является источником
для редакторского ожидания. Упрощения геометрии/прореживания ID нет.

Второй метод использует настоящий content notification, persisted renderer
смену без искусственного data notification, hide/show и нормальное удаление
своего слоя/Activity teardown. Каждый прежний provider должен стать terminal,
а сохранённый late request — отмениться до успешного ответа/кэширования.
После восстановления вновь требуются все129 объектов, подписи, пустой общий
cache и неизменные SQLite rows. Content notification здесь не меняет строки:
проверяется invalidation/lifecycle, а не весь протокол записи/sync редактора.

Класс включён в Full только один раз на phone route, отдельно на каждый метод;
Related `sqlite-render` маршрутизирует тот же класс на явно выбранный verification
смартфон/планшет. Existing catalog routing unit проверяет полный реальный каталог;
дубликат на emulator/diagnostics не добавлен. Production APK/обычный Debug,
проект787, системные настройки телефона, map schema/visibility persistence,
sync/draft/accounts и существующий Fixture не меняются.

Перед edits выполнен whole-contour read-only plan для двух исходников:
`sqlite-native-test-impact01.json` внеGit в evidence текущей задачи,
SHA-256 вывода `5dbcf24ea333ae6de5fff344a7b7dc0a683097c689be4130f34dc536835c2099`.
Analysis UNKNOWN/PLUGIN_ONLY, index REFRESHED_IN_MEMORY; source_context —
current working trees корня и всех пяти Android owner repositories. Это
наблюдение, не сохранённый воспроизводимый index или runtime PASS. Чужое
desktop дерево не редактируется; основной task сохраняет общий source/registry
closure перед commit. Новая цепочка: versioned test plan → VerificationTestRunner
→ isolated Fixture → SQLite reload → provider/CustomVectorSource → native query
и snapshot. Публичные NGW/Collector/desktop producer formats не меняются.

Source catalog validation PASS: после editor case фактический каталог266методов полностью
покрыт текущим versioned plan; все три метода принадлежат Related sqlite-render,
класс имеет ровно одно phone и одно per-method isolation назначение, отсутствует
на emulator/diagnostics route. Это read-only проверка исходников/JSON, не запуск
методов Android.

Первый native запуск реально выполнен на Samsung SM-A566B/API36, исходники
39d8ad24..., evidence `phone-sqlite-related01`: FAIL первого метода на initial
category paints. Предыдущий exact129ID gate прошёл; следующие labels/pixels,
original rows и lifecycle шаги не выполнялись. Это не PASS и не доказанное
отсутствие129объектов. По статическому разбору same source-layer содержит polygon
и label-anchor Point с одинаковым FID31/62/99; прежний test HashMap теряет
геометрический тип/происхождение duplicateFID. Может ли point заменять polygon
именно в фактическом FillLayer query, по initial log отдельно установить было нельзя.

Добавлен только failure witness, без фильтра геометрии, смены ожиданий или
product edits. Перед Fixture cleanup сохраняются per-FillLayer/per-SymbolLayer
native hit counts, geometry/paint histograms, raw ID/type/properties и all bounded
properties фокусных1/2/31/62/99, source/provider/camera/empty-cache state и PNG.
JSON ограничен64KiB, raw256hits/focus48,16native layers; снимок до1024px по длинной
стороне. `files/native-sqlite-qa/failure.json` и `failure.png` находятся только в
isolated verification APK; bounded summary входит в исходный failure stack.
Координаты/геометрия не выгружаются. Причина/первоначальный failure сохраняются.
До этой diagnostic-only правки выполнен read-only whole-contour plan:
`sqlite-native-test-diagnostic-impact01.json` (около2KiB), output SHA
`b2d5fc78b11818773f3265426316f20be5d5662fa2fc6573e371f6a30f3814e5`,
UNKNOWN/PLUGIN_ONLY, REFRESHED_IN_MEMORY/all live roots; чужие реестры не менялись.

Сбор failure файлов теперь versioned: `mobile_sqlite_evidence.py` вызывается из
finally `Verification.native` сразу после instrumentation, до разбора native
результата и следующего per-method reset. Только пакет
`com.nextgis.mobile.verification.debug` и два фиксированных private пути;
production/обычный Debug и произвольные имена/каталоги недоступны API. Нужны
точный native case, полный source fingerprint и явно выбранный serial.
Сначала подтверждаются существование/размер через stat, затем binary cat;
проверяются UTF-8 JSON schema1 и PNG signature,64KiB/2MiB limits/точный размер.
Только валидные bytes сохраняются с SHA-256, case/source/serial и status index.
Подтверждённое отсутствие, включая наблюдавшийся exit0 с точным `cat: ...:
No such file or directory`, записывается `absent_verified`, не retained.
Transport/permission/чужой путь/malformed/oversize — `diagnostic_failure`;
один валидный файл сохраняется даже при ошибке другого. Первоначальный native
FAIL/transport exception не заменяется ошибкой диагностики. Артефакты прошлого
run не перезаписываются. Новая настройка или изменение test oracle не вводятся.

До collector edits выполнен whole-contour read-only plan:
`sqlite-failure-collection-impact01.json`,2868B, output SHA
`be8f07472497029c8c5cfc21e8f56232595eaabef0852a33938196082311c16b`,
UNKNOWN/PLUGIN_ONLY, REFRESHED_IN_MEMORY/all live roots. Новые зависимости:
runner finally → fixed collector → binary ADB read → schema/signature/size
validation → owned Q output/index → receipt checks, до следующего pm clear.
Новых remote producers/consumer formats или database writes нет.

Collector/runner regression:37 Python units PASS,0failures/errors,0.067s;
`sqlite-failure-collection-units04.json/log`. Включены13 новых fake-ADB/finally
случаев и соседние native failure/catalog/receipt gates: exit0+cat error,
точные absence/permission/transport отличия, corrupt JSON/schema/PNG, размеры,
timeout, target guards, hashes/no-overwrite и порядок collection до следующего
Full reset при сохранённом native FAIL. Настоящий ADB/телефон/Gradle этим child
не запускались. Промежуточныйunits02 FAIL сохранён: валидный deeply nested JSON
был ошибочно включён в malformed test input; исправлен сам технический input
на реально незакрытый JSON, parser/production contract не ужесточался.

Следующий `software-sqlite-related01` реально завершён FAIL на том же paint gate,
с actual Google SwiftShader/API36 и source4a53cd96...; owned emulator cleanup
подтвердил settings restored/console closed/process exited=true. Versioned
collector сохранил настоящий failure.json/PNG с индексом. Основной FillLayer
query содержит142hits/129distinctFID:139Polygon и3Point, последние относятся
к anchor31/62/99 и не имеют fillcolor. Polygon свойства имеют ожидаемые
47red/92green цвета; SymbolLayer отдельно возвращает3Point с правильными
русскими text/color. Значит test map действительно терял polygon paint при
sameFID Point overwrite; отсутствие геометрического ограничения Fill query
также остаётся наблюдаемым native source-role дефектом. Initial FAIL не снят.

Test oracle усилен: SDK query остаётся на UI, затем каждый raw hit проверяется
на instrumentation thread ДО свёртки FID. Fill допускает только
Polygon/MultiPolygon без `_label_anchor`, Symbol — только Point с boolean
`_label_anchor=true`. Неверные роли не фильтруются и не игнорируются. Законные
tile fragments одного polygon сохраняют прежнюю FID дедупликацию; label anchor
больше не может незаметно заменить geometry role. Любая новая role assertion
по-прежнему попадает в bounded failure witness до Fixture cleanup. Product
geometry filters исправляет основной task, не эта test-only правка.

Четыре основные группы сохраняют строгие ожидания: CustomVectorSource и пустой
global Java cache; exact1..129ID и paints всех объектов;3exact native label
text/color/role и3независимые fill pixel области; все129полных originals×65coords,
атрибуты и whole-row hash плюс content/style/hide-show/teardown cancellation.
PNG подтверждает видимые red/green полигоны, но отдельного glyph pixel oracle
этот ограниченный класс не содержит: Symbol query не заменяет Full label/glyph
приёмку. Ни geometry simplification, ни снижение ожидаемого количества/цветов,
ни ограничение исходной геометрии для редактора не вводятся.

Перед усилением выполнен адресный read-only whole-contour plan:
`sqlite-role-oracle-impact01.json`, output SHA
`cd042545b0bdd79bcb0ad73a4e22a77f91326ba8086344d0dcb08994df5e74ce`,
UNKNOWN/PLUGIN_ONLY, REFRESHED_IN_MEMORY/all live roots. Child не запускал
Gradle/ADB/Git; повтор после production filter+test изменений ещё требуется.
Состояние: source batch подготовлен для общей compile/native проверки root.
Третий case проходит public feature selection и настоящую toolbar Edit action,
проверяет production editor с одним полным DB polygon,65точными координатами и
selected-poly-source только с выбранным FID31. Фон обязан иметь ровно128других
FID и две остальные подписи, общий Java cache остаётся пустым. Одна вершина
реально перемещается на2m/1m через публичный editor API и normal host geometry
callback; retained-editor branch `unselectFeatureFromEdit(false,true)` сохраняет
тот же editor и exclusion. Это точный branch перехода к обходу без GPS/FGS startup,
не физическая/GPS/walk приёмка и не gesture injection. Публичный cancel должен
вернуть129background FID и отпустить editor; все SQLite строки остаются исходными.
Парные model/native hide и Undo используют операции normal delete-prompt handlers,
проверяют128→129background и исключение/возврат подписи, скрытость только временная.
На всех стадиях сверяется whole-row hash, после cancel/Undo — все исходные
129×65coordinates/attrs. Save не выполняется и не объявляется проверенным:
его штатный form/draft/commit маршрут остаётся отдельной существующей приёмкой.

Reload readiness исправлена для нового effective predicate: вызов reload сначала
прикрепляет provider, только затем assert `isSqliteVectorLayer`; capability перед
созданием native источника не подменяет его фактическую готовность. Новых production
API/изменений источника эта test правка не добавляет. Class selectors автоматически
включают третий метод в тот же unique phone/isolated route; source catalog receipt
`sqlite-edit-oracle-source01.json` подтверждает266методов/3SQLcases без дубликата.
До этой правки plan `sqlite-edit-oracle-impact01.json`,2194B, output SHA
`3e2192db51c2f63468cc068e205f6d53135f2da3570d1c499586691d9f68aae3`,
UNKNOWN/PLUGIN_ONLY, REFRESHED_IN_MEMORY/all live roots. Новый Java compile/native
повтор child не запускал; это prepared source, не Save/Full/device PASS.

Все129/paint/label/pixel/DB assertions сохранены.
После `software-sqlite-related02` добавлен causal witness перед native style
ожиданием: у нового provider читаются rich originals31/62/99 и независимо
требуются GREEN/RED/RED и исходные signature. Только затем выполняется прежний
строгий reversed native paint oracle. Failure JSON содержит bounded
`backend_original_focus` для этих трёх FID: актуальность provider, ID, paint и
signature/textcolor; geometry/координаты/SQL не копируются. Это позволяет
различать неправильную подготовку свойств и устаревший native/PBF результат,
не объявляя причину только по fallback цвету слоя. Source plan
`sqlite-style-causal-impact01.json`,2194B, SHA вывода
`85fc4dc7b415cb93bc75c952d35c10386f03a4e225b77e9e1d53343b0cd7454c`,
UNKNOWN/PLUGIN_ONLY/REFRESHED_IN_MEMORY/all live roots.

При разделении logical layer path и actual native source ID все main source
lookup/query сравнения теста используют public `MapDrawable.getVectorRenderSourceId`.
Перед hide сохраняется точный actual ID удаляемого источника, затем требуется
его отсутствие; проверка не может пройти лишь из-за отсутствия logical alias.
В witness actual native ID и logical path различаются. Малый selected-poly-source
остаётся собственным стабильным overlay. Provider/API/factory producer меняет
основной task; новая six-geometry матрица пока только подготовлена к расширению,
её выполнение не объявляется. Causal/source-ID batch требует общего compile и
нового native повтора; child Gradle/ADB/Git не запускал.

После `software-sqlite-related03` исходные129/paint/label/pixel/DB ожидания
остались строгими: initial/content stages прошли, backend нового provider
перед SDK подтвердил reversed GREEN/RED/RED для31/62/99, но native source
`#sqlite-native-4` после restyle дал0Fill hits при3Symbols; PNG оказался чёрным.
Это сохранённый FAIL, не доказательство отсутствия PBF polygon и не native PASS.
Для следующего causal run добавлены только failure observations: actual/logical
source IDs, source family children/filters/source-layer/opacity/zoom gates,
owner/view/render-view состояния и source epoch/content version. У SDK13.7.1
нет публичного CustomVectorSource querySourceFeatures или свежего cumulative
frame counter; эти данные явно `unavailable`, listener/repaint не добавляются.

После native query failure и сохранения PNG instrumentation/IO thread читает
не более4canonical соседних XYZ tiles у текущего provider, z=floor(camera zoom)
с ограничением0..18. Каждый ответ не более4MiB передаётся независимому test-only
`SqliteMvtEvidence.summarize`; итог сохраняет MVT layer/type counts и bounded
focus properties без geometry/координат/SQL/произвольных exception messages.
Это наблюдение backend после сбоя, не доказательство, что SDK получил именно
эти4tiles или что они покрывают весь экран. Публичный backend load может заполнить
производный disk cache; поэтому native snapshot сохраняется до этих чтений.
JSON ограничен64KiB: сначала убираются raw detail, затем лишний focused detail;
tile headers/type counts остаются. Чтение не меняет SDK/style/camera/data и не
смягчает ни одного assertion. Six-geometry extension первоначально отложено до causal run.
Dependency receipt: `sqlite-native-empty-fill-impact01.json`,2675B, SHA вывода
`61effa681b9ddb2f8f39391eed03a92479a5bb08a020be2bdf10bc2f98c8beb0`,
UNKNOWN/PLUGIN_ONLY, REFRESHED_IN_MEMORY/all live roots. Child не запускал
Gradle/ADB/Git; source batch требует общей compile/native проверки.

После `software-sqlite-related04` сохранён native FAIL после restyle: bounded
независимый PBF decoder увидел polygons с новыми цветами и opacity1 во всех4
прочитанных tiles, тогда как SDK rendered queries дали0hits при живом view.
Это отделяет подготовленный backend payload от native показа, но не доказывает
приёмку этих bytes SDK. Failure JSON дополнен pure Java snapshot
`MapDrawable.getSqliteVectorDiagnostics`: requested/IO/returned/cancel/retry/failure
и owner job counters. `returned_to_sdk` означает возврат bytes из provider,
не подтверждение декодирования/показа SDK. Произвольных сообщений/данных нет.

После окончания04 добавлены шесть небольших SQL native smoke методов по3реальным
техническим SQLite rows: Point/simple circle; MultiPoint/Rule+custom cyan icon;
LineString/simple и MultiLineString/Rule с production viewport labels до/после
pan; Polygon с отверстием и blue procedural hatch; MultiPolygon/Rule с двумя
частями и отверстиями. Multi геометрии действительно имеют по2компонента.
Общие helpers создают только DB originals, не вызывают classic bind/full list.
Каждый case требует все3native IDs, paint каждого FID и согласованность paint
всех raw fragments до dedup, все3размещённые русские подписи и их цвета,
CustomVectorSource и пустой global Java cache. Независимые native pixel patches
проверяют обе части каждого multipart, actual cyan custom icon/category fallback
и actual hatch; во всех polygon holes несколько внутренних точек обязаны
показать белый технический background, учитывая возможное наложение glyph.
Для линий public projection screen center задаёт независимый ближайший anchor;
после pan420m все3anchors обязаны переместиться, angle=0 для прямых horizontal
оригиналов. Проверяется actual separate GeoJSON label source и native placement,
layout/provider helper для формирования ожиданий не вызывается. Вложенная
иерархия всех исходных координат/компонентов/колец и поля сверяются по одному
DB original, typed whole-row hash всех3rows обязан сохраниться. Старые129
assertions и causal lifecycle/style ожидания не смягчались.

Read-only plan `sqlite-six-geometry-impact01.json` завершён exit0, UNKNOWN,
REFRESHED_IN_MEMORY: reverse closure expanded to full ecosystem, path contract
остаётся unmapped и требует общей source/semantic closure основным task.
Полный вывод36539653B, SHA
`34ae113b6086c3cc122654caa01856058376c54775fab8f65b2888f0c159e241`;
короткая квитанция `sqlite-six-geometry-impact01-summary.json` сохранена рядом.
Исходный6case batch сначала подготовлен наQ без изменения running04 source,
после явного root handoff перенесён в этот же класс. Source catalog проверяет
текущий route автоматически; отдельный Full/emulator дубль не добавляется.
Read-only catalog receipt `sqlite-six-geometry-source01.json` подтверждает272
native методов, все9SQLcases, одно Full phone назначение класса, method isolation
и точное совпадение Related sqlite-render с этим классом без emulator/diagnostics
дубликата. Это проверка каталога, не запуск native. Новый batch ещё не
компилировался/не исполнялся child, native PASS не заявлен.

Native05 сохранён как4PASS/5FAIL: startup129, lifecycle/content/reversed style129,
MultiLine с pan и MultiPolygon с обоими отверстиями прошли. Это первый successful
style run после04; повтор/гонка остаются неподтверждёнными, прежние FAIL сохранены.
Три simple cases остановились в test oracle на отсутствующем per-feature paint:
native simple factory законно держит constants/fallback, что видно в сохранённых
native getter expressions. Для simple случаев теперь обязательно проверяется
реальный native geometry/text factory fallback с независимым no-overrides
вычислением red/magenta; любой присутствующий feature paint всё ещё обязан
совпасть. Rule per-feature paints остаются обязательными и строгими. Все3IDs,
русские тексты, обе multipart части/holes и actual pixel thresholds сохранены.

Editor05 выявил test NPE: selected-poly-source содержит служебный LineString с
boolean `edit_direction=true` без SQL FID. Новый oracle адресно валидирует этот
role: нет FID, ровно2различных finite endpoints; неизвестный non-FID feature
не допускается. Actual selected polygon всё ещё обязан иметь ровноFID31;
авторитетный текущий editor отдельно сверяется со всеми65точными координатами
до/после движения, чтобы accessory не подменял проверку геометрии. Выявленная
production stale-provider проблема после edit start исправляется отдельным
producer batch, эту test правку нельзя выдавать за её устранение.

MultiPoint05 сохранил2правильных native icon-layout hits и6circle/6label hits,
но failure PNG не содержит cyan/blue/magenta pixels. Причина пока неизвестна.
Custom bitmap16px, icon size и cyan>=5 threshold не изменены. Полный existing
pixel oracle теперь повторяет свежий snapshot до30s, чтобы normal async
upload/fade мог завершиться; camera/paint/source/repaint не меняются. Timeout
остаётся FAIL со всеми прежними component/hatch/hole ожиданиями. Это не fixed
sleep и не доказательство timing cause. Failure JSON дополнен bounded actual
icon-image/size/filter/overlap getters и максимум4technical custom image
presence/width/height/density/centerARGB, без bitmap dump. Backend original focus
для small3row cases теперь явно1/2/3, ожидаемое число уже было3; optional legacy
PBF focus31/62/99 обозначен отдельно и не выдаётся за отсутствие прочих FID.

Перед адресными oracle edits plan `sqlite-native05-oracle-impact01.json`,2058B,
SHA `edd36cc2894d16d93e6d5b161b85a43bb7afcfd58530dd3a27ab352c608b0634`,
UNKNOWN/REFRESHED_IN_MEMORY; полный graph обработан в памяти, дубликат bulk
index не записывался. Child не запускал Gradle/ADB/Git; исправленный oracle
нуждается в общей compile/native проверке, Native05 не переименован в PASS.

Адресная editor regression больше не принимает cached128/129native hits за
живой источник. До edit, после normal edit entry/keep-edit, Cancel, temporary
hide и Undo текущий зарегистрированный provider обязан иметь `isCurrent=true`.
В каждом из6состояний instrumentation/IO thread читает один полный rich DB
originalFID1 с65точными координатами и делает snapshot/load его canonical tile
z16/x32767/y32768. Independent decoder обязан получить complete MVT с polygon
records; request/provider остаются current и тем же registered owner после IO.
Существующие128/129ID/label/overlay/whole-row assertions сохранены. Не добавлены
data notifications, прямые model repairs, camera tricks, retries или новые UI
переходы. Это ловит конкретный наблюдавшийся stale-provider после изменения
hidden/contentVersion и не может пройти только на ранее загруженных SDK tiles.
Plan `sqlite-editor-provider-regression-impact01.json`,2023B, SHA
`2c73e85ec489dd07e0c389b5cb9cc278ec69c228687571e58a4447bebe472c66`,
UNKNOWN/REFRESHED_IN_MEMORY. Сохранена только компактная квитанция наQ;
данный batch ещё не компилировался/не исполнялся child.

Этот bounded gate не заменяет полную style/editor/line-label матрицу,
длительный реальный787 маршрут, memory pressure/recovery, физические2GiB,
Full или разрешение публикации. Допустимую скорость после измерения оценивает
владелец; абсолютной гарантии отсутствия OOM/замедления тест не даёт.

Хранение: новых runtime/download/ADB данных не создавалось. Компактная квитанция
плана около3KiB остаётся наQ в evidence активной задачи; можно свернуть после
общего delivery checkpoint. Будущие test SQLite/snapshot принадлежат disposable
verification APK и удаляются штатным Fixture/reset, не из рабочих данных.
Новые collector plan/unit logs/receipts около40KiB сохранены наQ как evidence
активной задачи; технические unit TemporaryDirectory используют Q TEMP и
контекстное завершение. Device artifacts будущего run сохраняются в его
`sqlite-evidence/<case-key>/` с index до verification reset; это сохранённые
свидетельства, которые можно свернуть после разбора и общего delivery checkpoint.
Максимум одного case —64KiB JSON+2MiB PNG и компактный index; shared caches и
старые environments не копировались и не очищались. Дополнительный полный
read-only plan около35MiB, compact summary/source receipt около5KiB остаются
наQ до общего source/semantic delivery checkpoint. Q-only draft/patch около114KiB
помечены held в `disposable/sqlite-six-geometry-draft-storage.json` для сверки
следующего build/run с базой04; после принятого общего source checkpoint их
можно удалить, сохранив квитанции и канонические исходники в репозитории.

После Native06 (8PASS/1FAIL) диагностика tiny custom PNG сохраняет failure.png
в исходном разрешении oracle, вместо уменьшения длинной стороны до1024.
Streaming output ограничен прежними2MiB; дополнительной encoded-image RAM copy
нет. JSON получает точные размеры и общие CYAN/BLUE/MAGENTA/RED/GREEN counts;
ошибка pixel assertion содержит фактическое число matches, sample и размеры.
Это позволяет отличить исчезновение от субпиксельного размера, не меняя цвет,
размер16x16/условия fixture и порог5пикселей. Отдельная Related область
sqlite-marker запускает этот существующий метод; Full и sqlite-render по-прежнему
требуют весь класс. Новый whole-contour plan sqlite-full-resolution-witness-plan01
сохранён наQ; canonical foreign desktop index не редактируется. Новый native
результат до повторного запуска не утверждается.

После Native07/build11 исходный PNG1080x2274 подтвердил реальное отсутствие
CYAN/BLUE/MAGENTA, а не потерю цвета при уменьшении свидетельства. Добавлен
только failure discriminator `literal_offset_probe`, без изменения исходного
assertion, fixture, геометрии, размеров изображения, paint, источника или камеры.
Сначала полностью сохраняются исходные state/PNG/PBF в прежние fixed files.
Затем у максимум16 SymbolLayer того же текущего native source временно задаются
буквальные icon-offset `[0,0]` и text-offset `[0,-2]`. Два snapshots через250/750ms
дают отдельные полные pixel counts; камера/Style/provider/source generation
проверяются до и после каждого snapshot. Отсутствие символов не переименовывается
в отсутствие feature или недопустимость зарегистрированного SDK `semiliteral`.

В finally восстанавливаются точные исходные expressions/literals/undefined
обеих offset properties; native getters сравниваются с сохранёнными полными
значениями. JSON хранит expected/restored property counts, mismatches и отдельные
pixel counts после восстановления. При смене камеры проба помечается
непригодной, но properties прежнего owner всё равно восстанавливаются; при
замене owner его новое состояние не переписывается. Число samples ограничено двумя
и одним restore snapshot; прежний snapshot timeout10s сохранён, ошибки — только
fixed class names, без новых business values. Исходный FAIL остаётся главным,
результат пробы не может сделать тест PASS. Исходные layer witnesses дополнены
offset/text-size/text-color/text-field/font/placement для точного сравнения.

Optional JSON append ограничен64KiB и выполняется atomic replace только после
успешного формирования; он не обрезает уже сохранённые original rows/PBF detail.
Если append не помещается или недоступен, исходные failure.json/failure.png
сохраняются, а diagnostic error остаётся в исходном failure summary. Probe PNG
не создаётся. В памяти один snapshot и одна строка pixels; временный JSON≤64KiB
принадлежит isolated verification package и исчезает при штатном reset, а при
успешном replace — сразу. Production APK/слои/SQLite/синхронизация не меняются.

До edits выполнен свежий read-only whole-contour plan по test/card:
`sqlite-literal-offset-probe-impact01.json` — компактная квитанция1993B,
36,554,807B stdout обработаны только в памяти, SHA-256
`47894b939f73b7a0501f0291fc0b3e5a9dae27df52573fc3d5087865d7434d20`,
analysis UNKNOWN. Сохранённый foreign desktop index не менялся; final source
index/registry closure остаётся у основного владельца задачи. Цепочка
NativeSQL→Fixture.snapshot/Style getters-setters→fixed evidence collector
проверена по исходникам; public desktop/NGW/Collector форматы и saved state
не меняются. Child не запускал Gradle/ADB/Git или native probe; compilation
и фактический literal/restore результат остаются pending до отдельного run.
