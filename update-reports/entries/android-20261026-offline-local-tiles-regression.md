---
id: android-20261026-offline-local-tiles-regression
area: Проверки мобильной карты
kind: internal
at: 2026-10-26T16:40:00Z
title: Native проверка локальных тайлов и смены стиля без внешней сети
why: Исходная проверка зависала при отключённых Wi-Fi и мобильных данных
covers:
  - maplibui
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyTileFallbackTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocalVectorTileLabelTest.java
  - tools/mobile-test-plan.json
---

MOB-01/MVT-01/REL-03. Новая проверка использует тот же сценарий настоящего
SQLite→MVT→native point→classic Firefly; дополнительно проверяет видимый MVT
до редактирования. Только owned emulator выключает оба радио и восстанавливает
их через fixture. Реальный NetworkUtil остаётся offline во время отрисовки.
Добавлен один обязательный метод Full и адресная область offline-tiles;
на физических устройствах настройки сети не меняются.
