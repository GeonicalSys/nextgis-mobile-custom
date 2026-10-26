---
id: android-20261025-recording-intent-recovery
area: Мобильное приложение
kind: user
at: 2026-10-25T19:10:00+01:00
title: Восстановление записи после отказа запуска с выключенной геолокацией
why: Автоматический resume не должен подменять подтверждённую остановку пользователя
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackerStopRecoveryTest.java
  - tools/mobile-test-plan.json
  - maplibui
---

Эмуляторный native сценарий включает запись, неожиданно завершает recorder,
проверяет настоящий GPS-off, сохранение intent/UUID/незавершённой строки при
отказе resume, возврат записи после GPS-on и окончательный подтверждённый Stop.
Изменяются только verification данные; системная геолокация восстанавливается.
На физический телефон этот сценарий не маршрутизируется.
