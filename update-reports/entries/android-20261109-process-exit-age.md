---
id: android-20261109-process-exit-age
area: Мобильное приложение
kind: internal
at: '2026-11-09T15:14:16+04:00'
title: Возраст завершившегося процесса в отчёте
why: Связать системное завершение с известной отметкой запуска
covers:
  - agent-system/knowledge/documentation/ngw-sync-and-storage.json
  - agent-system/knowledge/documentation/official-differences-error-reporting-contract-3.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-owning-layer-fill-and-recovery.json
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporter.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporterTest.java
---

Поле observed_process_age_ms записывается для исходного процесса с достоверной отметкой запуска. Для старой записи без исходного маркера время не вычисляется. Данные дополняют сигналы, выходы по памяти и прочие системные завершения в №69, №117, №116 и №128.
