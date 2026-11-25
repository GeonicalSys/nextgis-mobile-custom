---
id: android-20261125-project-schema-rebuild
area: Синхронизация мобильных проектов
kind: user
at: '2026-11-25T12:38:00+04:00'
title: Проверить пересборку слоя с формой и неотправленной правкой
why: Для скачанного проекта нужно проверить путь обновления схемы без потери уже набранной очереди.
covers:
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-staged-layer-replacement.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-pre-sync-integrity-repair-and-diagnostics.json
  - agent-system/knowledge/documentation/official-differences-authoritative-schema-check.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-0458813e3ca84ebb708a0015.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-7ba14f0c9d6c71b1093a02b2.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-8ed5ed3f294a909e81a0f10a.json
  - agent-system/provenance/structured-views.json
  - docs/architecture/ngw-sync-and-storage.md
  - docs/reference/build-matrix.md
  - docs/reference/official-differences.md
  - docs/registry/ui-contracts.json
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectRecoveryColdSyncTest.java
  - update-reports/entries/android-20261125-project-schema-rebuild.md
---

Сценарии используют изолированный проект с локальной SQLite и тестовый сервер. Они проверяют передачу позиции из задачи с формой, сохранение старого слоя при ожидающей правке и одну отправку перед установкой новой копии на ту же позицию. Отдельная проверка воспроизводит две уже сохранённые копии и сохраняет ту, в которой осталась локальная правка.
