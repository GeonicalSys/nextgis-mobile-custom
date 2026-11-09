---
id: android-20261109-restore-measurement-ready-map
area: Мобильное приложение
kind: internal
at: '2026-11-09T15:14:16+04:00'
title: Восстановление линейки на готовой карте
why: Устранить потерю измерения при смене экрана
covers:
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-cf6d88239faca089e51547bf.json
  - agent-system/knowledge/documentation/map-rendering.json
  - agent-system/knowledge/documentation/ui-contracts.json
  - agent-system/knowledge/ui-contracts/ui-sources-wf-stakeout-selection-save.json
  - agent-system/knowledge/documentation/official-differences-initial-field-sketch.json
  - agent-system/knowledge/documentation/ui-contracts-currentcenter-device-coordinate-and-camera-producers.json
  - agent-system/knowledge/documentation/map-rendering-measurements-menu.json
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
---

Сохранённая линейка ждёт готовых камеры и источников карты. Тесты проверяют отложенное восстановление точек и отмену правки при недоступном рендерере. Сценарии GlitchTip №129 и №130 используют совместимый Maplib.
