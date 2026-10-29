---
id: android-20261028-render-memory-acceptance
area: Приёмка памяти и отрисовки
kind: internal
at: 2026-10-28T01:35:00+01:00
title: Проверки полного native input, once-session окна и сохранения неуспешной приёмки
why: Прежние обязательные проверки не выявили полевые вылеты224, поэтому техническая нагрузка и property/geometry полнота должны иметь независимые assertions и честный статус
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/RenderMemorySafetyTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/MapRenderStressTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestApplication.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
  - tools/mobile-test-plan.json
  - tools/mobile_render_acceptance.py
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_native_failures.py
  - tools/tests/test_mobile_render_acceptance.py
  - tools/tests/test_mobile_test_support.py
  - docs/runbooks/automated-tests.md
  - docs/architecture/map-performance.md
  - app/docs/README.md
  - app/docs/manifest.yaml
---

MOB-03/MOB-04, отдельный verification APK без customer accounts/данных.
RenderMemorySafetyTest проверяет129technicalpolygonIDs и все65координат
каждого SQLite/rich Java/actual compact input до SDK quantization, полный
native ID набор и unchanged whole-row hashes. Первое автоматическое окно
ожидается от production bridge без ручного callback; позднейшие повторные
callbacks, recreation и другая карта/слой не повторяют его. Ручной статус
имеет8локальных русских componentprofiles, не подменяющих Full real windows.
Остальные isolated gesture/layout cases подтверждают известное окно только
в verification Application; dedicated memory case не получает этого bypass.
Fixture.bind ожидает фактический compact transport и возвращает прежние
rich Java features; GPU/style/layout/selection assertions сохранены.

Stress добавляет actual heading updates и peak RSS/Java/native observations
к существующим cold process,6480polygon/twoMVT,90s pan/pinch/recreate проверкам.
Отправленные input без actual camera movement не принимаются как нагрузка.
Hardware publication matrix требует current phone/tablet/emulator иSM-X115,
но имя модели само не подтверждает2GiB: фактическую RAM и такую физическую
приёмку фиксируют отдельно. Memory counters являются наблюдениями, не
RSS/used порогом предупреждения и не hard memory ceiling.

Full сохраняет содержательные native/receiver FAIL и продолжает независимые
cases, затем остаётся FAIL; setup/transport ошибка по-прежнему прерывает run.
RenderMemorySafetyTest в Full принадлежит смартфону; Related render-memory
может отдельно выполнять его на software эмуляторе. Дублирование класса в
обоих Full списках отклонено самим admission до ADB, исправлено без ослабления
guard. Новый unit сверяет реальный source catalogue с versioned routing,
поэтому такая ошибка обнаруживается и обычным запуском Python checks.
Изменившиеся fingerprints, missing/empty hardware evidence и незавершённые
обязательные этапы блокируют publication. Поздний recovery не отменяет FAIL.

Ранее до последних MLP/free-headroom правок зафиксированы963JVMunits,
93Python harness checks и AndroidTest build29s. После6MLP handoffs отдельно
повторён maplib651/651 (native-payload-units05.json). Это не native GPU,
эмулаторная/2GiB или Full приёмка усиленного SafetyTest и последнего source.
Прежние Full/softwareFAIL сохранены; нового общего PASS этим не объявлено.
Последний free-only/queue fix отдельно проверен23focused maplib tests
(Guard2/Policy8/MapRuntime13), free-memory-notice-units01.json; это не native
GPU/2GiB/Full и не повтор всего651-набора.
Малые технические fixtures не названы пользовательским проектом787 или
воспроизведением реального системного дефицита памяти.

App semantic registries и automated-tests runbook отражают полноту данных,
free-headroom policy, once-process/manual UI и ограничения evidence.
Whole-contour plan: dependency-plan-app-memory-docs02.json, UNKNOWN;
foreign dirty desktop index остаётся отдельным delivery block. Direct SQLite
CustomVectorSource paging реализован отдельным source batch; эта карточка
приёмки не является доказательством его native performance/style/2GiB PASS.
Merge/release/publication не выполнялись.

Локальный docs-check -RunTests: validator/strict impact,26report-guard и24docs
regressions PASS. Квитанция app-memory-docs-check01.log; это не native приёмка.

После Native06 исходники MapRenderStress переведены на фактический direct SQL
contract: before/каждый цикл/после workload проверяются effective source ID,
CustomVectorSource, isSqliteVectorLayer/current provider и nonnull empty global
feature cache обоих слоёв. Старый max-only VectorSource/logical-path counter
не соответствовал новому приоритету SQL route. Геометрия6480×65, minimum60cycles,
90s active phase,50pan/15pinch/300heading/2recreate и прежние UI/frame gates
не уменьшены. Seed PID сохраняется; равный workload PID отклоняется. Встроены
точные small-grid oracle assertions:25IDs для CW/CCW квадрата, другой25ID diamond
и пустой слишком малый viewport; expectations заданы независимо от renderer.

Финальный camera возврат11/0 подтверждается до вычисления expected ID sets из
четырёх viewport corners и authored grid/circumscribed radius140+2m margin.
Наборы полностью видимых polygons затем фиксированы, не пересчитываются по
rendered output. Все required FID должны присутствовать; все raw hits обязаны
быть Polygon/MultiPolygon без label-anchor role и иметь допустимый fixture FID.
Корректные крайние пересечения допустимы; у offscreen80row слоя required0
допустим, но общий required набор непуст. Это bounded viewport completeness,
не утверждение, что все offscreen6480 видны на каждом экране. Наборов полных
геометрий/бизнес-данных для ожиданий не создаётся.

Исправлена отдельная причина неверного timing evidence: последний sample
записывался до900ms sleep и мог остаться89.xs после реально завершённого>=90s
цикла. Schema2 фиксирует workload duration после loop, отдельно startup/total;
host требует согласованные actual90s. Старые failed artifacts не переименованы.
Publication validator отвергает schema1/legacy backend, cached Java features,
warm PID, неполную/неверную coverage, неизвестные FID/roles, несовпавшие totals и
ложную длительность. Новые адресные Python regressions проверяют эти отказы,
а также допустимые edge/offscreen cases. Memory peaks не превращены в leak/noOOM
или speed PASS; независимого retained-memory hard-limit oracle не добавлено.

Whole-contour read-only plan перед8paths: `sqlite-stress-impact01.json`,2460B,
SHA `008c694ca0930d2e8bd35ecc9f4dfb4097ce6d982e2c2412b16ed0185f4a325c`,
UNKNOWN/REFRESHED_IN_MEMORY, source_context записан. Общая source/semantic closure
остаётся за root; foreign desktop не изменялся. Focused Python10/10 PASS0.007s,
`sqlite-stress-python01.log`. Новый stress ещё не исполнялся на SDK/phone/tablet/
software emulator; Native06 другого SQL класса не является его PASS. Child не
запускал Gradle/ADB/Git.

Новых bulk/runtime данных нет. Компактный plan и focused source-unit квитанция
остаются наQ до общего delivery checkpoint; полный discovery обработан в памяти.
