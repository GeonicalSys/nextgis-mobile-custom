---
id: android-20261109-direct-style-render-readiness
area: Мобильное приложение
kind: internal
at: '2026-11-09T17:38:36+04:00'
title: Пространственный индекс для теста прямых SQLite-слоёв
why: Повторное уведомление добавляло вторую запись в индекс тестового слоя
covers:
  - agent-system/knowledge/documentation/map-rendering-ordinary-sqlite-vector-source.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/DirectVectorTilesTest.java
---

Тест использует штатный режим пакетного импорта и проверяет единственность записи в индексе. Сценарий смены стиля сохраняет проверки отрисовки в видимой области карты, смены поколения источника и побайтного совпадения исходной строки.
