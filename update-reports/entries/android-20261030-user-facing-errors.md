---
id: android-20261030-user-facing-errors
area: Понятные сообщения об ошибках
kind: user
at: '2026-10-30T12:48:03.922589+00:00'
title: Причина сбоя ВебГИС сохраняется до сообщения пользователю
why: Очередь теряла известную причину server failure; отдельные окна молчали или показывали технические строки.
covers:
- app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java
- app/src/main/java/com/nextgis/mobile/datasource/SyncAdapter.java
- app/src/main/res/values/sync_recovery_strings.xml
- app/src/main/res/values-ru/sync_recovery_strings.xml
- app/src/test/java/com/nextgis/mobile/util/SyncFailurePresentationTest.java
- app/src/test/java/com/nextgis/mobile/util/ProjectSyncLateFailureTest.java
- app/src/test/java/com/nextgis/maplib/datasource/ngw/ResourceChildrenMessagesTest.java
- app/src/test/java/com/nextgis/maplibui/util/LayerFillErrorMessageTest.java
- app/src/test/java/com/nextgis/maplibui/activity/FormLegacyListMessagesTest.java
- app/AGENTS.md
- app/docs/README.md
- docs/architecture/ngw-sync-and-storage.md
- docs/architecture/ui-contracts.md
- docs/guides/project-synchronization-user-guide.md
- docs/roadmap/mobile.md
- docs/changelog.md
- docs/reference/official-differences.md
- app/docs/manifest.yaml
- docs/registry/invariants.yaml
- docs/registry/change-impact.yaml
- docs/registry/smoke-tests.yaml
- docs/registry/ui-contracts.json
---

При известной ошибке ВебГИС приложение объясняет временную невозможность запроса,
возможные техработы/перегрузку и повтор позже. Причина проходит через account/queue
в safe dialog и notification; raw status/body/URL/password/Throwable не становятся
пользовательским текстом. Offline проверяется повторно при показе. Auth/rights,
local/mixed/late/unknown не выдаются за серверное обслуживание.

Отдельный late child failure ранее мог только заблокировать clock после account
outcome. Теперь previously clean noncancel target получает один IO/name outcome
после cleanup, с per-target state и без повтора уже записанной ошибки/normal cancel.
Это не откат прежних uploads, pair ACK или новая restart/resend политика.

App также владеет техническими regression fixtures UI browser/fill/form changes;
product ownership и отдельные пользовательские UI карточки остаются у maplibui.
Новые typed APIs не имеют meaningful old-API baseline: missing method/compile/setup
не объявляются RED. Source/model batch89 (Core46/App43) завершён PASS/0fail/errors/skips;
это не native или полный sync outcome. Literal form4 имеют
отдельный разрешённый pre-fix assertion baseline. Java/Kotlin source compile и
native/device/Service/SQLite/permission/large-font/реальный сервер различаются.

MOB-02/MOB-UI-01, прямое поручение владельца проверить уместность сообщений. Новые
UI source IDs покрывают typed sync, resource children, LayerFill и legacy list.
Только WF-SYNC-RUN намеренно пересмотрен; whole F61 clock row/остальные old objects
сохраняются. Old source19/clock/journal/storage/prefetch результаты не пересуммированы.

Core63/UI46 Merge Commit → App merged pointers и semantic clock review/docs check
→ App86 Squash по отдельной команде. Это Draft source delivery, не установленный APK,
release/deploy/native acceptance. Wiki и приостановленные native stages исключены.

Первый actual85 дал81PASS/4assertFAIL/0errors/skips. Core16/App13/Late7/literal4 уже PASS; historicalfailed85 сохранён. Root устраняет real408 attribution только gate body-only422, сохраняя retries. Один producer stale-cohort oracle8→12 явно согласуется с исходным known-prefix|OTHER контрактом; source/Core16 expectations ради него не меняются. Повтор89 с existing NetworkUtil4 PASS; полный native/device/whole-ecosystem PASS не заявлен.

### Проверено

Actual89 PASS/0fail/errors/skips: Core46 (37 API28 models+9 plain JUnit), App43 API28 models,11 XML10.995s, Gradle process0/BUILD50s. Reviewed153 actual task set and68 source/runtime inputs exact; all15 observed unit-owned PIDs settled absent. BelkaRelease Java/Kotlin source compile process0/BUILD59s (61.275s observed), actual100 task SET equals reviewed100 while order differs; CoreJava/UIJava/AppJava+Kotlin executed. Four Java JARs28/535/670/45 classes, required typed classes present and no dex/so/APK/AAB/AAR.68 inputs unchanged; all13 observed compile-owned PIDs settled absent. These are source/model results, not native/device/window/Service/SQLite/server/APK acceptance.

First85 remains historical NOT_PASS:81PASS/4assertFAIL/0errors/skips. One producer stale-cohort oracle8 was explicitly corrected to original knownSERVER|OTHER12; Core16/App13/all other fixture bodies stayed unchanged. Product repair was only the independent body-only422 gate preventing408 externalDB text from claiming server maintenance; retry classification unchanged. Literal4 independent old-source baseline3assertFAIL/1control/0errors and exact same6eb9 GREEN are separate, not89+4 methods.

### Воспроизведение технических source-проверок

Из обычного Android root с versioned gradlew.bat и11 versioned test classes.
Установлены JBR21.0.9+-14787801-b1163.94, Gradle9.3.1/AGP9.1.0,
Robolectric4.16.1/JUnit4.13.2, API28 android-all-instrumented9-robolectric-4913185-2-i7
(SHA256 dbb1c88987b54cef3132695a1b4d2f4851ba06286a6fa7d15772f98fd1f0cdf7).
Shared Q offline caches/runtime переиспользуются; task Scratch helper/fixture не
нужен для исполнения. Actual observer использовал собственный phase-specific Q
java.io.tmpdir; ниже выбран обычный Q Temp. Его имя не является test input.
Argument arrays точны: unit30 с11filters/--continue и compile7 без--continue.

Перед отдельно разрешённым запуском тот же graph с --dry-run должен содержать
только source/unit resources и reviewed Java JAR roles, без product APK/assemble/
sign/install/device/CMake/NDK/new downloads. --continue собирает только отфильтрованные
assertion outcomes после prelaunch gates, а не разрешает обход failed prerequisite.
После unit nonzero к compile не переходить. Эти команды не возобновляют native scope.

```powershell
$env:TEMP='Q:\Codex\Temp'
$env:TMP='Q:\Codex\Temp'
$env:TMPDIR='Q:\Codex\Temp'
$env:PYTHONDONTWRITEBYTECODE='1'
$env:PYTHONIOENCODING='utf-8'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='Q:\Codex\Cache\gradle'
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=Q:/Codex/Temp -Drobolectric.offline=true -Drobolectric.dependency.dir=Q:/Codex/Cache/maven/org/robolectric/android-all-instrumented/9-robolectric-4913185-2-i7'
& '.\gradlew.bat' '--offline' '--no-daemon' '--continue' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':maplib:testDebugUnitTest' '--tests' 'com.nextgis.maplib.util.SyncFailureSnapshotTest' '--tests' 'com.nextgis.maplib.map.NGWHttpFailureReasonProducerTest' '--tests' 'com.nextgis.maplib.map.NGWSyncNoneServerFailureTest' '--tests' 'com.nextgis.maplib.util.NgwHttpExceptionTest' '--tests' 'com.nextgis.maplib.util.NgwHttpResponseExceptionTest' '--tests' 'com.nextgis.maplib.util.NetworkUtilTransientNgwTest' ':app:testLisaDebugUnitTest' '--tests' 'com.nextgis.mobile.util.SyncFailurePresentationTest' '--tests' 'com.nextgis.mobile.util.ProjectSyncLateFailureTest' '--tests' 'com.nextgis.maplib.datasource.ngw.ResourceChildrenMessagesTest' '--tests' 'com.nextgis.maplibui.util.LayerFillErrorMessageTest' '--tests' 'com.nextgis.maplibui.activity.FormLegacyListMessagesTest'
$taskUnitExit=$LASTEXITCODE
if($taskUnitExit -ne 0){throw "User-message source tests failed with exit $taskUnitExit; compile was not started."}
& '.\gradlew.bat' '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:compileBelkaReleaseJavaWithJavac' ':app:compileBelkaReleaseKotlin'
$taskCompileExit=$LASTEXITCODE
if($taskCompileExit -ne 0){throw "Source compilation failed with exit $taskCompileExit."}
```

Exact current sources/tests/resources версионируются в owning repositories;
App held F61/E66 pointers отдельно от live-library source validation. Core63/UI46
Merge Commit должен предшествовать App pointers/clock semantic review/docs checks.
Source Draft delivery не означает merge, installed APK, publication или acceptance
телефона/реального сервера; новые runtime stages этим recipe не разрешены.

Фактическая квалификация моделей без повторного запуска: Core46 состоит из37 Robolectric/API28 и9 plainJUnit (NgwHttpExceptionTest5 + existing NetworkUtil4), App43 — Robolectric/API28. Итого80 моделей и9 plainJUnit;89PASS/Gradle0/все exact inputs и stop receipts неизменны. Первичный immutable Root receipt описал5 plain как модели; его JSON не переписан, эта пометка исправляет только подпись охвата.

### Закрытие source-среза сообщений — 30 октября2026

После semantic review документов global dependency index проверен CURRENT:
5761 files / 78853 edges, source SHA256
`3742037ad3e46bdbb2183c026b451b4abfa69a0d81ccc6c9aa8fa4b6207d71c1`.
Basis: own Root/App/Core/UI working trees, foreign easypicker/publisher committed
HEAD snapshots; 188 historical observations сохранены побайтно. Полнота связей
остаётся UNKNOWN. Final impact сопоставляет все36 changed source/test/resource
paths; это план, не исполнение дополнительных сценариев.

App docs-check:26 policy tests PASS; local UI validator оставляет ровно прежний
WF-SYNC-CLOCK-STORE. Отдельные57 docs tests:53PASS/4fail того же F61 clock contract.
Его ожидание не подгонялось под live unmerged library. Root docs validator:
ровно одна прежняя ошибка frontmatter в excluded Wiki backup REVERT.md; Wiki
не менялась. Поэтому полные docs suites не объявляются PASS.

После metadata review из68 inputs выполненных89/compile проверок неизменны67
behavioral/source/runtime inputs; единственное намеренное изменение — reviewed
UI contract catalogue. Код/тесты после89PASS и Belka source compile не менялись.
Fresh fetch всех6 repositories успешен, owned branches ahead/behind0/0 перед
коммитом. App gitlinks F61/E66 сохраняются до library Merge Commit; source Draft
не является готовым APK. Report guards пройдены отдельно от docs limitations.
