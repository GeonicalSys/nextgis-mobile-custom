---
id: android-20261112-release-observability-plan
area: Дорожная карта мобильного приложения
kind: internal
at: '2026-11-12T18:08:37+04:00'
title: Записать план обязательной статистики и отчётов при выпуске
why: При выпуске 225 потеряли конфигурацию статистики, а в 226 ошибочно закрепили её отсутствие
covers:
  - agent-system/knowledge/documentation/mobile-roadmap-release-observability.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-12-release-observability-plan.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - docs/roadmap/mobile.md
  - docs/changelog.md
---

В MOB-REL-03 сохранён согласованный план из семи этапов: обязательная
конфигурация Release, перенос приватных входов, контроль подписанного APK
издателем, привязка результата к SHA-256, подтверждение доставки, отрицательные
проверки и новый исправленный выпуск. Область — ЛИСА и БЕЛКА; статистика,
GlitchTip и предусмотренные диагностические вложения.

Это только план. Код, действующие правила сборки и публикации, приватная
конфигурация и серверы не меняются. Устройственные проверки и выпуск не выполнены.
