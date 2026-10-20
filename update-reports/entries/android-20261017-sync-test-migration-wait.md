---
id: android-20261017-sync-test-migration-wait
area: Сопровождение
kind: internal
at: '2026-10-17T20:16:01+03:00'
title: Ожидание миграции перед тестовой синхронизацией
why: Выполненная доработка отсутствовала в реестре и могла не попасть в отчёт
covers:
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/util/ProjectSyncRunnerTest.java
source_commits:
- 58f4e2e58a04ed1ce7c01987e54be89e75460c3e
---

Тесты создают проекты синхронизации после завершения стартового переноса данных, чтобы не получать ложные отказы.
