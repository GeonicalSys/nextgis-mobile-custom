---
id: android-20261030-ngfp-lookup-prefetch-regression
area: Регрессии подготовки форм
kind: internal
at: '2026-10-30T05:30:49.581349Z'
title: Регрессии prefetch и неизменённого legacy reader NGFP
why: Сохранить независимые input/output ожидания без опасного расширения удаления
covers:
- app/src/test/java/com/nextgis/mobile/util/NgfpLookupPrefetchTest.java
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/architecture/ngw-sync-and-storage.md
- docs/architecture/conditional-form-rules.md
- docs/architecture/cascading-form-lists.md
- docs/guides/form-behavior-user-guide.md
- docs/registry/invariants.yaml
- docs/registry/smoke-tests.yaml
- docs/registry/change-impact.yaml
- docs/roadmap/mobile.md
- docs/changelog.md
- docs/reference/official-differences.md
---

Восемь prefetch expectations исполняют actual helper, E66 fallback только при отсутствии
ForPrefetch; исходные Exception/Error не подавляются. Четыре direct legacy controls:
nested exclusion/rootobject refusal/duplicate-1-2 outputs/present-null error. Oracles
после RED не менялись, production test hooks/business fixtures нет. Два caller rename
доказаны отдельно byte inverse; actual import execution helper тестами не подменяется.

Центральные открытые вопросы: AUDIT-TODO-NGFP-LOOKUP-PREFETCH,
AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE, AUDIT-TODO-NGW-PARTIAL-SNAPSHOT,
AUDIT-TODO-NGW-ACCOUNT-REMOVAL-BACKUP. Old delete reader не принимается как правильная
shared-GC policy, per-form dedup не исправляет межзадачные retry/constructor duplicates.
Installed same-hash repair и malformed inactive orientation отдельно не приняты.

Actual SDK28 reader: baseline12/3 named AssertionFAIL/9PASS/0errors/skips; после A3
тот же test86e3 и12 names —12PASS/0errors/skips, XML2.149с, Gradle0/49с. LisaDebug
changed Java/Kotlin compile включён. BelkaRelease Java/Kotlin0/41с;87 actionable,
6executed/81up-to-date. Technical Q JSON без account/map/SQLite/NGW/device/native.
Старые cache9/clock19/journal22/storage21 и desktop1117/1192 не перезапускались.

Operational event остаётся NOT_PASS: Debug-only local JAR classifier отметил четыре
Release bundleLibCompileToJar, а PowerShell продолжил разрешённую компиляцию после
Python exit1 до corrected receipt. Post-readback whole100 task list exact approved;
cached AGP9.1.0/output archives подтвердили4 classes.jar (28/530/669/45 class files,
0dex/so/APK), а не product/native задачи. Это не retroactive prelaunch gate PASS.
Checked-stage wrapper проверяет LASTEXITCODE после каждого native call; controlled
exit7 →outer1, would-launch marker отсутствует/новый JVM не запускался. Rerun для
сокрытия события не было. Все owned процессы естественно stopped:
greenDry6/green12/compileDry5/compile7, technical JSON finally удалены.

Own-live source b645/11ee/d231 +test86e3 отдельно от committed App UI E66/maplib F61.
Library Merge Commit → App pointer после merge. Routine source/docs commit/push/Draft
разрешены; это не merge/APK/release/deploy/native acceptance. App production новую API
не вызывает; test reflection не принимает parent E66. UI39/142/19/F61 неизменён.
Новые metadata official docs/report checks после C-интеграции NOT_RUN, старый clock
drift не снят, общего validation/allgatesPASS нет.

Текущие source hashes:

- `maplibui/src/main/java/com/nextgis/maplibui/util/LayerUtil.java`: `b645f163289ad2c3b0377d69f41cea4efe94aa597a55a53bcf42731d50e2a2fa`
- `maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java`: `11ee22a1b746f157a7b2be246380cad33e87f925753afb1a1fd686482fc3168c`
- `maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java`: `d2316383cdad4a138a093f0baff269a54ca4d548338a7a98348ecf27c7b5fe64`
- App test: `86e389dd733f680091a9f46a3f6f0d538468ef02d35bf1502e775d920eed5c12`

Из Android root, с existing JBR21.0.9+-14787801-b1163.94/Gradle9.3.1/AGP9.1.0/
Robolectric4.16.1/JUnit4.13.2/API28, сначала отдельно --dry-run тех же arguments и
review actual source/JVM graph. Java JAR и необходимые AGP unit-resource archives
допустимы; CMake/NDK/native/assemble/install/sign/minify/device/product APK/download
не входят в этот адресный прогон. Native command failure обязан остановить следующий
этап: ErrorActionPreference не заменяет LASTEXITCODE.

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
& '.\gradlew.bat' '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:testLisaDebugUnitTest' '--tests' 'com.nextgis.mobile.util.NgfpLookupPrefetchTest'
if($LASTEXITCODE -ne 0){throw 'Filtered unit stage failed'}
& '.\gradlew.bat' '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:compileBelkaReleaseJavaWithJavac' ':app:compileBelkaReleaseKotlin'
if($LASTEXITCODE -ne 0){throw 'Belka source compilation failed'}
```

Эти2 quoted argument arrays exact actual; нет in-process Kotlin override или prose
после native command. Twelve runtime inputs reused по SHA без новых зависимостей/
downloads/copies. SDK28 JAR SHA256:
`dbb1c88987b54cef3132695a1b4d2f4851ba06286a6fa7d15772f98fd1f0cdf7`;
JBR java.exe: `029d680acfdc306efa91252f98f3f9c9069bfa21ce3f917ea0dbe2b641077011`.
Компактные logs/XML/source/runtime/stop/inverse receipts удерживаются до PR review;
нового bulk runtime0, общие Q build/cache junctions не удалялись как task garbage.


### Проверки после интеграции metadata — 30 октября2026

Предыдущая пометка NOT_RUN относится к source freeze до интеграции. Теперь App
report guard127cards/10sourcepaths PASS; policy26 PASS18.312с. Полный docs-check
NOT_PASS: единственная ошибка WF-SYNC-CLOCK-STORE по сохранённому F61. Отдельные
57 docs tests:53PASS/4FAIL,44.767с suite/45.292с process; все четыре FAIL содержат
только тот же guard. Новых ошибок metadata/NGFP-trigger не выявлено. UI report
guard34cards/3sourcepaths PASS. UI39/142/19/F61 и source22 не менялись.

Общий index сохранён с fourOWN-live root/App/maplib/maplibui, foreign easy/publisher
committed HEAD. Отдельный versioned --check actual0/CURRENT:5741 input; semantic
completeness UNKNOWN/knowngeo.json parse limit. Root full docs NOT_PASS ровно
excluded Wiki REVERT.md frontmatter; checkers/expectations не ослаблены. Исторические
JVM12/Belka0 и failed prelaunch sequence не перезапускались и не переобозначены.
