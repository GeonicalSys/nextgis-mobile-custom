---
id: android-20261114-production-telemetry
area: Надёжность выпуска
kind: user
at: '2026-11-14T01:00:31.012632+00:00'
title: Обязательные приватные входы выпускной сборки и изоляция Debug
why: Согласованные завершённые изменения рабочей сессии
covers:
- .gitignore
- app/build.gradle
- app/src/main/java/com/nextgis/mobile/usage/UsageAnalytics.java
- app/src/main/java/com/nextgis/mobile/usage/UsageUploadWorker.java
- app/src/main/java/com/nextgis/mobile/util/ProductionTelemetry.java
- gradle/production-telemetry.gradle
- tools/production-telemetry-contract.json
- tools/telemetry_inputs.py
- tools/tests/test_telemetry_inputs.py
- update-reports/entries/android-20261114-production-telemetry.md
---

Обязательные приватные входы выпускной сборки и изоляция Debug

Решения и зависимости:

- Completed build-input stage only: mandatory Release validator for Lisa/Belka; fieldVerification variants disabled plus direct validator rejects this flag; ciReliabilityChecks cannot bypass input requirements. Debug embeds all five empty production values. Trusted-source inventory/copy checks all files before copying, refuses differing existing inputs and records only hashes/provenance. No credential values printed or modified. Runtime artifact sender and independent APK Publisher validation are the next source stage; MOB-REL-03 is not complete.
- 5 input tests PASS, including actual Groovy validator against synthetic accepted/rejected DSNs; real both-brand Release generation rejected missing diagnostics.properties; verification bypass rejected; App Debug compile PASS and all five values empty. Evidence Q:/Codex/Scratch/mobile-reliability-01a1d22d/evidence/mobrel03/admission-receipt.json. Positive signed production admission NOT_RUN pending approved private diagnostics input.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261114-production-telemetry. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
