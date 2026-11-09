---
id: android-20261109-polygon-hide-native-regression
area: Мобильное приложение
kind: internal
at: '2026-11-09T17:38:36+04:00'
title: Нативная проверка скрытия всех частей полигона
why: Планшет обнаружил оставшийся контур после скрытия слоя
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleRenderingTest.java
  - agent-system/knowledge/documentation/polygon-style-controls-classic-outline-layout-and-picking.json
  - agent-system/knowledge/documentation/polygon-style-controls.json
---

Тест проверяет нативную видимость заливки и контуров и отсутствие пикселей скрытого контура.
