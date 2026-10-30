---
id: android-20261030-ngw-resource-checkbox-selection
area: Выбор ресурсов мобильного приложения
kind: user
at: '2026-10-30T14:16:51.978883+00:00'
title: Выбор ресурса соответствует его галочкам после прокрутки и восстановления
why: Старый binder зависел от последнего CheckState, пустая recycled строка сохраняла чужую отметку; duplicate
  callbacks и restore расходились с видимым выбором.
covers:
- app/src/test/java/com/nextgis/maplibui/dialog/ResourceSelectionCheckboxTest.java
- app/src/test/java/com/nextgis/maplibui/util/NgwResourceSelectionDedupTest.java
- docs/architecture/ui-contracts.md
- docs/architecture/ngw-sync-and-storage.md
- docs/roadmap/mobile.md
- docs/changelog.md
- docs/runbooks/collector-project-setup.md
- docs/registry/invariants.yaml
- docs/registry/change-impact.yaml
- docs/registry/smoke-tests.yaml
- app/docs/manifest.yaml
- docs/registry/ui-contracts.json
---

Подготовлены28 адресных технических моделей настоящего UI adapter/compact restore:
22 binder/listener/recycled label cases и6 restore identity cases. Новые источники
принадлежат maplibui; App владеет fixtures, каталогом `UI-NGW-RESOURCE-CHECKBOX-SELECTION`, central invariant/
impact/check links, user-facing selector explanation и roadmap MOB-UI-01 checkpoint.
Ни координаты, ни remote resource ID не интерпретируются по месту галочки.

Исправление связывает requested mode с process-local resource ID. Public setter/
legacy Parcel не нормализуются; import loops по-прежнему обходят каждую запись.
Callback false-all/true-no-amplification не является общей гарантией отсутствия
повторного импорта внешнего duplicate списка. JSON restore отдельно OR-merges
same resolved localID firstorder после resolve/parse КАЖДОЙ исходной записи;
different accounts/localIDs и late failure остаются различимы. Refresh policy
не выбрана; единственный canonical риск — Root `AUDIT-TODO-NGW-RESOURCE-BROWSER-REFRESH`.

Actual37 API28 models PASS:22+6 new и9 protected browser. Baseline13assertFAIL/24controls
→37GREEN с теми же fixtures; Belka source compile0/BUILD47s; full metadata checks пока NOT_RUN. Fixture22 c6838ab7 и6 f46e5bec не менялись ради syntax repair:
invalid Adapter9f25 отозван доC, corrected606 отдельно прочитан whole-class с
активным if и listener внутри метода; restore3f832 reviewed source-only.
Исторические message89PASS/Belka59 относятся к Adapterb703 и не перезапускались.
Это не native/server/SQLite/Activity lifecycle/rotation/import/large-font acceptance,
не установленный APK и не полный аудит экосистемы. F61 clock/pins и Wiki прежние.

Actual focused37 source command (Root, после graph/input gates):
`& '.\gradlew.bat' '--offline' '--no-daemon' '--continue' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:testLisaDebugUnitTest' '--tests' 'com.nextgis.maplibui.dialog.ResourceSelectionCheckboxTest' '--tests' 'com.nextgis.maplibui.util.NgwResourceSelectionDedupTest' '--tests' 'com.nextgis.maplib.datasource.ngw.ResourceChildrenMessagesTest'`
Используются уже установленные JBR21/Gradle9.3.1/AGP9.1.0/API28 и Q offline caches;
TEMP/TMP/TMPDIR/java.io.tmpdir на Q. Exact actual13 unit arguments взяты из Root selection-green/plan.json; baseline/green
results/stop receipts сохранены отдельно. Compile receipt selection-compile/final-result-source-stop.json закрыт0/BUILD47s.
При nonzero к compile не переходить. Native/device/AVD/DB/Office/cloud и89 rerun
не возобновляются. Core63/UI46 Merge Commit → App pointers/semantic clock review/
docs check → App86 Squash и Root276 Squash только по отдельной команде.


Фактическая source-проверка37: Actual same37 API28 models PASS/0fail/errors/skips:22 checkbox/label +6 owned restore +9 protected browser messages,3XML6.846s,Gradle0/BUILD45s. Meaningful old-source baseline37 has13assertFAIL/24controls/0errors/skips,XML6.493s,Gradle1/BUILD57s. Same c683/f46 fixture bytes/method sets unchanged. Baseline134 raw events/133unique includes duplicate FAILED status; green133/133 exact reviewed SET with different order.81 protected inputs exact,all13 baseline/all14 green owned processes settled absent. First dry prefix-classifier false positive and first raw-event133 observer assumption remain explicit diagnostic history, not retroactive gate PASS; actual tests were not rerun for those observer corrections. Invalid9f25 source was held beforeC and replaced by reviewed606 without oracle changes. BelkaRelease source compile exit0/BUILD47s (49.136s observed),81 inputs exact,all10 owned processes settled absent. Actual100 task SET matches reviewed100 with different order; UIJava/AppJava+Kotlin executed, CoreJava UP-TO-DATE. Four Java JAR class counts28/535/670/45; no dex/so/APK/AAB/AAR. This is source compilation, not installed-app/native/Service/DB/server/import/layout-fit acceptance. Native/device/Activity lifecycle/rotation/realserver/DB/import writes/largefont/APK/refresh acceptance NOT_RUN; screenshot layout fit not covered. Historical full message89/Belka59 not rerun and used old Adapterb703.


Actual source-compile recipe: `& '.\gradlew.bat' '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:compileBelkaReleaseJavaWithJavac' ':app:compileBelkaReleaseKotlin'`
Unit exit проверяется до compile; других runtime stages команда не разрешает.

### Закрытие source-среза выбора ресурсов — 30 октября2026

Index --write/--check завершены0/CURRENT:5763 files/78874 edges, source SHA256
`147d131eb7b353470a58168bab7f5db06dc1c239fbb7f7f71cbafb8d5dbba8e8`.
Basis — четыре own working trees и два foreign committed HEAD; прежние188
observations побайтно сохранены. Полнота связей/geo.json/exclusions остаётся UNKNOWN.
После source37/compile неизменны80 из81 input; единственный намеренный delta —
reviewed UI catalogue44/177/44 (включая9 XML witnesses). Код/тесты не переписаны.

App docs-check26 policy tests PASS15.845s. Final local UI/docs validator оставляет
ровно прежний WF-SYNC-CLOCK-STORE; отдельные57 docs tests —53PASS/4fail того же
F61 clock contract. Первый docs проход дополнительно нашёл запрещённый placeholder
substring в новом maintained README: исправлено только указание на canonical risk
через consuming contract; исходные failed logs сохранены, checker не ослаблялся.
Root docs validator оставляет одну прежнюю missing-frontmatter ошибку excluded
Wiki backup REVERT.md. Wiki и clock expectations не менялись, full docs не PASS.
Report guards Root377/35 App130/21 Core57/20 UI38/13 пройдены отдельно.

Обычные процессы JVM завершились, новые temporary JVM dirs остаются на Q:
6,208,233 bytes в пяти owned paths. Native PowerShell cleanup отклонён executor
policy до запуска при full access/approval never; ничего не удалено и обходов
не было. Пять записей добавлены в общий pending-cleanup с сохранением прежних42.
Компактные source/XML/stop отчёты удерживаются до окончания аудита/PR review;
shared caches и пользовательские GPX/изображение не являются task garbage.

Это source Draft checkpoint. Отдельные новые сообщения владельца о невидимости
local/WebGIS слоёв и обрезанном caption ещё расследуются; source37 проверяет
selection/value binding, не реальную отрисовку/fit/native lifecycle или эти fixes.
Library pins F61/E66 и порядок Core/UI Merge Commit → App pin/semantic clock review
→ App/Root Squash остаются до отдельной команды на merge/release.
