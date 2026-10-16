---
title: Проверка централизованной статистики мобильного приложения
type: reference
last_verified: 2026-10-17
related_code:
  - app/src/main/java/com/nextgis/mobile/usage
  - app/src/androidTest/java/com/nextgis/mobile/usage/UsageDeliveryTest.java
  - app/src/test/java/com/nextgis/mobile/usage/UsageOpenPolicyTest.java
  - tools/mobile-test-plan.json
---

# Проверка статистики — 2026-10-17

Работа изолирована в отдельных Git worktree сервера и Android, обе ветки
`codex/mobile-usage-stats`. Исходные checkout и незакоммиченные изменения
параллельной задачи не переключались, не очищались и не перезаписывались.
Android основан на согласованном пользователем commit `c738c945` PR #65;
статистика оформляется отдельным PR к этой ветке. Перед squash/merge #65
нужно retarget дочернего PR на `my-maplibre` с проверкой состава изменений.

Related `-Area usage` на изолированном API 36 emulator прошёл три native case:

- UUID/sequence и очередь сохраняются после закрытия/reopen SQLite;
  неизвестный ACK не удаляет события, точный ACK удаляет только свои записи;
- настоящий loopback HTTP 503 сохраняет исходный UUID, повтор с 200 очищает
  подтверждённую очередь; duration в payload отсутствует;
- настоящий Android AccountManager и Collector registry сохраняют remote ID,
  server/login проекта, убирают userinfo/query с секретами и не создают
  дубликат inventory при неизменившемся составе.

Ранее в общем checkout один повтор instrumentation завершился `Process crashed`
без результатов тестов. В новом worktree два последовательных Related прогона
успешны (сначала два, затем три case); причина раннего сбоя не установлена.
Физический телефон не использовался. Production endpoint/token отключены
в verification APK; native тесты не отправляют данные на рабочий сервер.
Область usage также добавлена в GitHub regression runner; случайная привязка
этих тестов к области location удалена.

Серверные проверки в owner `lisa/server/mobile_usage`: десять unit/HTTP тестов,
реальные HTTPS ingest/повтор, закрытый отчёт, история после restart только
нового сервиса. Три синтетических события удалены адресно после проверки.
Браузер: три представления, карточка/история устройства, выбор аккаунта,
поиск, CSV и узкий экран 390 px; снимок предпросмотра содержит тестовые данные.

Длительность foreground, GPS и содержимое объектов не собираются. Submodule
pointers, production/debug versions и текущая публикация APK не меняются.
Выпуск APK требует отдельного штатного Full и Check-Publication; Related
и сборки этого изменения не заменяют release gate.
