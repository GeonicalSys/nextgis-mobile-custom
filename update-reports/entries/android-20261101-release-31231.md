---
id: android-20261101-release-31231
area: Мобильное приложение
kind: internal
at: 2026-11-01T22:27:00+04:00
title: Подготовить production 3.1.2.31/225 к публикации
why: Собрать актуальные слитые исправления в новой версии и проверить готовность локально
covers:
  - app/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - maplib
---

MOB-REL-01. Production app/maplib3.1.2.31/225; Debug3.1.2.23/218.
Независимая matrix читает действительную APK metadata трёх variants.
Порядок: maplib Merge Commit → fetched app pin → app Squash → финальные
локальные проверки и publisher dry-run. Изменение версии не меняет
application/provider/account IDs, ключи подписи, schemas, sync или battery policy.
Старые projects/drafts/queues сохраняются обычным update contract.

Владелец разрешил локальный software emulator; GitHub Actions запрещены.
Фактические результаты, source basis, closure и оставшиеся ограничения:
[подготовка выпуска](../../docs/reference/mobile-release-3.1.2.31-2026-11-01.md).
Реальная публикация не запрошена.
