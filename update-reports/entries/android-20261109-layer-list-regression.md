---
id: android-20261109-layer-list-regression
area: Мобильное приложение
kind: internal
at: '2026-11-09T14:06:14+04:00'
title: Регрессия фонового обновления списка слоёв
why: Воспроизвести рассогласование списка и модели при GlitchTip №144
covers:
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/official-differences.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-09-mobile-glitchtip.json
  - agent-system/provenance/structured-views.json
  - agent-system/knowledge/ui-contracts/ui-sources-wf-layer-order.json
  - agent-system/knowledge/ui-contracts/ui-contracts-ui-layer-order.json
  - agent-system/knowledge/documentation/official-differences-layer-drag-cancellation.json
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LayerPendingChangesTest.java
---

Тест проверяет состав строк до уведомления, перенос к показанному слою и применение уведомления об удалении. Используются локальные синтетические слои в отдельном тестовом APK. Сценарий зависит от исправлений Maplib и MaplibUI.
