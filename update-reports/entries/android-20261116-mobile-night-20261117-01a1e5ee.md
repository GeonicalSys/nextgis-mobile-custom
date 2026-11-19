---
id: android-20261116-mobile-night-20261117-01a1e5ee
area: Мобильное приложение
kind: internal
at: '2026-11-17T03:49:58.690070+00:00'
title: PERF-01, MVT-01, AUTH-01 and SCRIPT-UI-01 overnight implementation and targeted
  physical-device verification
why: Согласованные завершённые изменения рабочей сессии
covers:
- agent-system/knowledge/documentation/mobile-roadmap-field-measurement-baseline.json
- agent-system/knowledge/documentation/mobile-roadmap-layout.json
- agent-system/knowledge/documentation/mobile-roadmap-login-diagnostics.json
- agent-system/knowledge/documentation/mobile-roadmap-mvt-correctness-and-freshness.json
- agent-system/knowledge/documentation/mobile-roadmap-night-verification-20261117.json
- agent-system/knowledge/documentation/mobile-roadmap-queue-order.json
- agent-system/knowledge/documentation/mobile-roadmap-roadmap-overview-and-authority.json
- agent-system/knowledge/documentation/mobile-roadmap-script-notice-scroll.json
- app/src/androidTest/java/com/nextgis/mobile/reliability/NGIDLoginDiagnosticsNativeTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/MvtCommittedFreshnessTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/NativeSqliteVectorRenderingTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01MapBenchmarkTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01NightSyncTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01TileObservation.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/ScriptNoticeDialogTest.java
- docs/roadmap/mobile.md
- tools/mobile-test-plan.json
- tools/perf01_night_benchmark.py
- tools/perf01_owned_gateway.py
- tools/perf01_tile_profile_summary.py
- tools/tests/test_perf01_night_benchmark.py
- tools/tests/test_perf01_owned_gateway.py
- tools/tests/test_perf01_tile_profile_summary.py
- update-reports/entries/android-20261116-mobile-night-20261117-01a1e5ee.md
- update-reports/entries/android-20261117-night-mobile-verification.md
---

PERF-01, MVT-01, AUTH-01 and SCRIPT-UI-01 overnight implementation and targeted physical-device verification

Решения и зависимости:

- Implement the four authorized overnight roadmap items on the idle verified session; preserve all Desktop and shared AGENTS work
- MVT-01 native RED: committed insertion followed by SQLite-source attachment leaves a nonempty legacy Java feature collection on A56/API37. No stale-pixel diagnosis inferred.
- A committed DELETE must notify rendering even when the independent legacy spatial-cache entry is absent. A missing cache item does not mean the database row was not deleted.
- AUTH-01 and SCRIPT-UI-01 have passed targeted physical checks on the current source candidate; retain exact original failures and test-oracle corrections separately.
- Retain native findings, correct the asynchronous export oracle and host-only upload/setup transport, and keep broader acceptance boundaries explicit.
- Finish the mobile night stage at owner request; preserve implemented AUTH/SCRIPT/MVT, failed PERF findings, updated roadmap and exact pending device cleanup; leave shared Desktop session active.
- Correct only unsupported roadmap enum spellings before task end; preserve partial product scope and failed/unknown runtime acceptance.

Полный журнал, входные хэши и результаты проверок: сессия karniilp-01a1e5c4, задача mobile-night-20261117-01a1e5ee. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
