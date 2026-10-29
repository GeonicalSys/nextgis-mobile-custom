---
id: android-20261028-background-gps-start-evidence
area: Диагностика фонового GPS
kind: internal
at: 2026-10-28T01:40:25+01:00
title: Состояние экрана и разрешений между запросом и отказом запуска фонового GPS
why: В группе118 Android отказал location FGS после stopped Activity, но прежний handled отчёт не сохраняет eligibility witnesses на двух стадиях
covers:
  - app/src/main/java/com/nextgis/mobile/location/BackgroundLocationService.kt
  - app/src/main/java/com/nextgis/mobile/location/BackgroundLocationController.kt
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsBackgroundLocationStart.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsBackgroundLocationStartTest.java
  - docs/architecture/error-reporting.md
  - app/docs/manifest.yaml
---

MOB-02; собственная app82. Группа118:223/5050495, Samsung/Android16,
27 октября17:03:22.891UTC, failure_kind=handled. MainActivity stopped в17:03:22.841,
отказ location startForeground в17:03:22.889; стек включает AppDiagnostics.report.
Это не crash и не OOM. Перехват RuntimeException/снятие GPS lease уже существовали;
новый catch или подавление ошибки этой правкой не добавляются.

В существующий handled отчёт добавлен bounded background_gps_start schema1:
фиксированный stage, монотонный request_age_ms, независимые request/failure
снимки visible/resumed activity counts, coarse/fine/background permissions и
process importance. Неизвестность явно unknown, API28 background permission
not_required. Request не удерживает Context/Activity и не меняется после
onPause/onStop или отзыва permission. Только scalar allowlist; нет координат,
SQL, accounts, Intent extras, бизнес-файлов или нового периодического сбора.
Временный SDK scope изолирует соседние события; diagnostic-priority sanitize
и cached replay используют прежний общий16KiB budget.

Protected behavior: location FGS type, promotion до завершения pending start,
existing permission gate/report limiter, START_NOT_STICKY, session ownership,
lease/wake-lock cleanup, двухчасовой фон, запуск/повтор только прежним controller,
Track/Walk/Stakeout и foreground GPS не меняются. Нового runtime setting,
storage/schema/config migration нет. Это диагностическое улучшение, не
объявленное устранение eligibility race или Android/OEM ограничения.

Closure: MainApplication регистрирует прежний BackgroundLocationController;
его callbacks публикуют только counts. Существующий service request получает
scalar snapshot, оба прежних catch передают witness в прежний AppDiagnostics
через временный SDK scope. AppDiagnostics меняется только whitelist/priority
одного context. NGW/Collector/desktop/PostgreSQL не являются producers или
consumers этих process-local метаданных; бизнес-контракт и persisted слой не
меняются. Неизвестное фактическое eligibility состояние исходного118 не
восстанавливается догадкой. Следующий report с новым APK нужен отдельно.

До edits выполнен обязательный full-contour read-only plan с5 исходниками:
foreground-gps-impact01.log внеGit в evidence текущей задачи. Analysis UNKNOWN,
все5 путей PLUGIN_ONLY, index REFRESHED_IN_MEMORY. Source context — current
working trees корня и всех пяти Android owner repositories; это наблюдение,
не зафиксированный reproducible index или runtime PASS. Чужой dirty desktop
checkout/его semantic registry не менялись; основной task сохраняет итоговый
source index и central registry closure перед commit.

Добавлены7 технических unit regressions: delayed promotion после reported
Activity stop, независимые permission changes/none/coarse/fine/background,
API28 not_required, неизвестные context/stage/clock, partial platform failure,
actual sanitizer под соседним большим payload/replay и scoped handled capture
без утечки context. Android FGS/native/device выполнение ими не заменяется.
Общий root Gradle33132 завершён PASS за1m4s: witness suite7 tests,
0 failures/errors/skips, включая реальную Sentry scoped capture и sanitizer.
Это Robolectric/source acceptance; фактический Android FGS/native сценарий
ещё не выполнялся и не объявляется PASS. Основная задача владеет Related
слотом. Merge/публикация и закрытие118 не выполнялись.

Хранение: новых runtime/download копий нет; full read-only plan около35MiB
сохранён наQ как evidence активного расследования. Его можно свернуть/удалить
после сохранения основным task итогового source basis и квитанции доставки;
общие Gradle/SDK caches не являются мусором этой проверки.
