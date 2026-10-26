---
id: android-20261024-maplibre-13-7-1
area: Мобильное приложение
kind: internal
at: 2026-10-24T18:06:00+02:00
title: MapLibre обновлён до 13.7.1 с сохранением OpenGL
why: Нужны актуальные исправления движка без изменения существующих стилей и редактора
covers:
  - app/build.gradle
  - gradle/verification-metadata.xml
  - tools/verify-apk-version-matrix.ps1
  - maplib
  - maplibui
---

Все три модуля используют MapLibre 13.7.1 через явный OpenGL artifact.
Версии и идентификаторы приложения сохраняются. Version-matrix gate проверяет
новую версию и запрещает generic, Vulkan и dual-backend artifacts независимо
от их номера. Подготовка GeoJSON, тайлы, фабрики стилей, подписи и бизнес-редактор
остаются прежними; новые API SDK не включаются автоматически.

Фактические проверки и ограничения:
`docs/reference/maplibre-13-7-1-verification-2026-10-24.md`.
