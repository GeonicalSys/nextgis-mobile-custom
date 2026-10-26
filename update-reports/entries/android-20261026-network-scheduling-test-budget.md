---
id: android-20261026-network-scheduling-test-budget
area: Проверки мобильного приложения
kind: internal
at: 2026-10-25T23:52:00Z
title: Учёт штатного WorkManager backoff в проверке возврата сети
why: Full ожидал SUCCEEDED через 20 секунд при возможном стандартном retry delay 30 секунд
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SyncOfflineAndWalkGpsTest.java
  - tools/mobile-test-plan.json
---

MOB-01/REL-03. Native проверка по-прежнему отключает сеть только на изолированном
эмуляторе, проверяет ENQUEUED/zero attempts и отсутствие sync без сети, затем
возвращает Wi-Fi и ждёт успешного завершения работы для заведомо отсутствующей
технической учётной записи. Бюджет 60 секунд допускает один штатный backoff
и ограниченную задержку OS scheduler. Constraints и production worker не менялись,
нет принудительного запуска jobs или mock scheduler.

Вместо неполного собственного переключения используется существующий
EmulatorNetworkFixture: ожидание именно готового Wi-Fi/полного offline и
закрытие с восстановлением радиомодулей в finally между тестами.

При ошибке видны attempt count, stop reason, eligible schedule time, connected
и validated flags. После успеха дополнительно проверяется отсутствие sync queue.
Исходная причина одиночного Full FAIL без этой диагностики остаётся неизвестной;
это исправление неполного test budget, не заявление о production sync repair.

Related offline-sync-readiness-02: 5/5 PASS на API36 AVD с общим network fixture,
все required assertions выполнены, эмулятор остановлен. Новый Full обязателен.
