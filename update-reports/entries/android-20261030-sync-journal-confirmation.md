---
id: android-20261030-sync-journal-confirmation
area: Синхронизация и восстановление
kind: user
at: 2026-10-30T01:04:20.8265615+01:00
title: Неудачное подтверждение синхронизации сохраняет согласованную очередь
why: После ошибки записи пара могла исчезнуть из памяти и незаметно попасть в следующую успешную запись как завершённая
covers:
- app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
- app/src/test/java/com/nextgis/mobile/util/SyncRecoveryJournalConfirmationTest.java
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/architecture/ngw-sync-and-storage.md
- docs/architecture/error-reporting.md
- docs/architecture/crash-recovery.md
- docs/guides/project-synchronization-user-guide.md
- docs/registry/invariants.yaml
- docs/registry/change-impact.yaml
- docs/registry/smoke-tests.yaml
- docs/registry/config-keys.yaml
- docs/roadmap/mobile.md
- docs/changelog.md
- docs/reference/official-differences.md
---

Существующий MOB-02; прямое поручение владельца29 октября исправлять явные баги
при глубоком аудите. При A+B failed complete(A) менял cache до B; следующий
successful complete(B) сохранял пустую очередь, хотя Runner считал A незавершённой.
Исправление сохраняет принятый API план и все семь typed/presence queue/legacy
ключей точного preferences owner. Только true публикует следующий cohort.
Unknown keys, JSON/target/account identity, manual OR, legacy branch semantics,
один существующий owned-thread commit и caller interruption сохранены.

Baseline: 16 actual-body JVM, 12 FAIL,
errors0; это meaningful cache-new/false oracle, не ошибка
сборки. После исправления 22 JVM PASS,
failures/errors/skips0, 3.778с. LisaDebug Java/Kotlin compile
входит в unit run; BelkaRelease source compile PASS33с.
GREEN состав:16 новых методов и6 неизменённых Journal/helper методов.
Независимый actual-source/test review: CLEAR: independent source2/22 XML review audit_frontier; root actual BelkaRelease log PASS33s.
Stable technical preferences моделируют cache/disk раздельно; они не доказывают
физический отказ накопителя, remote replay или restart recovery. AGP unit-resource
archives нужны JUnit; обычный APK/assemble/sign/install, устройство/AVD,
GIS/SQLite engine, NGW и business data не запускались.

Первый raw baseline имеет неизвестную disk provenance. Snapshot не откатывает
cache/файл; false может оставить old/new disk, а другой-key commit сохранить
rejected cache. Нет compensation, новой resend policy или full-clock promotion.
Post-ACK cleanup/restart остаются в AUDIT-TODO-SYNC-ACK-RECOVERY. Library63 ещё
не слит; committed app gitlink и весь WF-SYNC-CLOCK-STORE UI pin F61 сохранены,
а собственный live child CEA остаётся unstaged delivery dependency. Полный
UI/docs gate имеет прежний clock drift; source fix не объявляется его PASS.
Source compile выполнен с live CEA; это не приёмка APK с committed F61 gitlink.
Все новые связи сохранены в едином LISA index/существующей workspace family.
Точный source basis/RED-GREEN/stop receipts — активная task evidence,
Root329720/Appda4b/Librarycea pre-change; merge/release отдельно, не выполнены.

Воспроизводимый offline JVM/source маршрут (не native/AVD/публикация):

Robolectric4.16.1/API28, JBR21.0.9+-14787801-b1163.94, Gradle9.3.1, AGP9.1.0, JUnit4.13.2; существующие Q caches, без downloads.
Из корня Android сначала проверить --dry-run граф тех же задач: CMake/NDK,
assemble/install/sign/minify и device tasks недопустимы. Только необходимые
unit-resource archives AGP разрешены для JUnit. LisaDebug включает source compile;
BelkaRelease ниже компилирует Java/Kotlin без сборки обычного APK.

```powershell
$env:TEMP='Q:\Codex\Temp'
$env:TMP='Q:\Codex\Temp'
$env:TMPDIR='Q:\Codex\Temp'
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:GRADLE_USER_HOME='Q:\Codex\Cache\gradle'
$env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=Q:/Codex/Temp -Drobolectric.offline=true -Drobolectric.dependency.dir=Q:/Codex/Cache/maven/org/robolectric/android-all-instrumented/9-robolectric-4913185-2-i7'
& .\gradlew.bat '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:testLisaDebugUnitTest' '--tests' 'com.nextgis.mobile.util.SyncRecoveryJournalConfirmationTest' '--tests' 'com.nextgis.mobile.util.SyncRecoveryJournalTest' '--tests' 'com.nextgis.mobile.util.SyncJournalCommitTest'
& .\gradlew.bat '--offline' '--no-daemon' '-Pkotlin.incremental=false' '-PciReliabilityChecks=true' '--console=plain' ':app:compileBelkaReleaseKotlin' ':app:compileBelkaReleaseJavaWithJavac'
```
Переиспользован android-all-instrumented9-robolectric-4913185-2-i7.jar, SHA256 dbb1c88987b54cef3132695a1b4d2f4851ba06286a6fa7d15772f98fd1f0cdf7; копии/downloads не создавались.


Report guard:123 cards/8 source paths PASS;26 policy tests PASS18.178s.
Полный текущий UI/docs validator FAIL ровно на прежнем WF-SYNC-CLOCK-STORE.
Отдельные57 docs tests:53 PASS/4 FAIL43.711s, каждый тот же clock drift;
новых исключений/ослабления checker нет. Это не полный docs PASS.
