---
id: android-20261112-project-layer-form-roadmap
area: Дорожная карта мобильного приложения
kind: internal
at: '2026-11-12T09:10:14+04:00'
title: Сохранить план назначения NGFP-форм на уровне Collector-проекта
why: Общая настройка слоя не позволяет назначить разные формы двум мобильным проектам
covers:
  - agent-system/knowledge/documentation/mobile-roadmap-project-layer-form-binding.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-12-project-layer-forms-roadmap.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - docs/roadmap/mobile.md
  - docs/changelog.md
  - update-reports/entries/android-20261112-project-layer-form-roadmap.md
---

[FORM-01](../../docs/roadmap/mobile.md#form-01-явное-назначение-ngfp-форм-слоям-в-collector-проекте)
сохраняет согласованный план: в метаданных проекта строка JSON сопоставляет
keyname слоя с keyname формы; отсутствие назначения сохраняет прежний выбор.
Описаны единый выбор при импорте/синхронизации, изоляция локальных форм,
сохранность правок и проверки повторной синхронизации.

Статус «Отложена» по просьбе владельца. Приоритет и текущая очередь не меняются.
Код, настройки живого NGW и LISA Web не изменяются; реализация, проверки
на устройстве и выпуск не начаты.
