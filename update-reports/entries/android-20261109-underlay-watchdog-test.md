---
id: android-20261109-underlay-watchdog-test
area: Мобильное приложение
kind: internal
at: '2026-11-09T11:35:00+04:00'
title: Проверка таймаута переноса подложек
why: Убрать ложный отказ обязательной предрелизной проверки
covers:
  - agent-system/knowledge/documentation/official-differences-raster-mbtiles-legacy-debug.json
  - app/src/test/java/com/nextgis/mobile/util/LegacyUnderlayTransferSessionTest.java
---

Тест задаёт время последней активности относительно часов, которые использует перенос.
Проверки перехода в STALLED и отмены открытия потока сохранены.
