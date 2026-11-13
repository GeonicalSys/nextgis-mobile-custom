---
id: android-20261113-edit-session-recovery-roadmap
area: Дорожная карта мобильного приложения
kind: internal
at: '2026-11-13T04:44:23+04:00'
title: Спланировать завершение редактора после отказа от восстановления
why: После отказа от незавершённого скетча слой может оставаться с недействующим карандашом
covers:
  - agent-system/knowledge/documentation/mobile-roadmap-edit-session-recovery.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-13-menu-edit-recovery-roadmap.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - docs/roadmap/mobile.md
  - docs/changelog.md
  - update-reports/entries/android-20261113-edit-session-recovery-roadmap.md
---

[REL-04](../../docs/roadmap/mobile.md#rel-04-завершение-редактирования-после-отказа-от-восстановления)
планирует согласованное завершение Discard для формы, геометрии и владельца
блокировки, а также действенное управление карандашом конкретного слоя.
Выделены source-точки возможного разрыва, проверка установленной версии,
защита сохранённых данных, активного обхода и новых сеансов от старых callbacks.

Состояние «Предложена», приоритет P1. Причина пользовательского сценария на
устройстве пока не подтверждена; реализация, рабочие черновики, устройство
и выпуск APK не затрагивались. Все прежние изменения сохранены.
