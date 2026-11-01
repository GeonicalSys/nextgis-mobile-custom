---
id: android-20261101-attachment-state-contract
area: Мобильное приложение
kind: internal
at: 2026-11-01T15:00:00+04:00
title: Контракт состояния вложения и явная граница доставки mobile fixes
why: Индикатор ожидания, metadata и готовность кеша должны проверяться по реальным producers, без преждевременного объявления App исправленным.
covers:
  - docs/registry/ui-contracts.json
  - docs/architecture/ui-contracts.md
  - docs/changelog.md
  - docs/roadmap/mobile.md
  - docs/reference/official-differences.md
---

MOB-UI-01: новый `UI-ATTACHMENT-DOWNLOAD-STATE` связывает настоящие widget IDs,
URL markers, terminal status и cache-size consumers. Прежние47 contracts
и их ожидания сохранены. Для существующего typed HTTP contract whole-file
witness сохранён в history и заменён двумя неизменными method guards;
checker не ослаблен, прежний App gitlink остаётся проверяемым.

MOB-SYNC-01: библиотечные attachment7 и class metadata14 JVM-проверки отделены
от App adoption. Root App87 не получает код до Merge Commit библиотек и
последующего обновления pointers. APK/deployment/device/native/GUI не выполнены.
Автоматическая очистка старого повреждённого cache и checksum/Content-Length
guarantee не добавлены.
