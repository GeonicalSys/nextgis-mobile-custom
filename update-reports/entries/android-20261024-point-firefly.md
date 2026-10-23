---
id: android-20261024-point-firefly
area: Мобильное приложение
kind: internal
at: 2026-10-24T00:06:05+02:00
title: Подключён новый точечный стиль с проверками отрисовки и крупного интерфейса
why: Сохранение параметров само по себе не доказывает видимый чёткий край и доступность настроек
covers:
  - maplib
  - maplibui
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyTileFallbackTest.java
  - tools/mobile-test-plan.json
---

Подключены библиотеки с отдельным стилем «Светлячок» и его редактором.
Добавлены адресные проверки реального изображения точки, прозрачного ядра,
смешанных категорий и прежнего размытия. Редактор проверяется в обеих темах
и ориентациях, при обычном и крупном шрифте и размере интерфейса.
Смартфон использует отдельное тестовое приложение; полный прогон остаётся
обязательным перед публикацией.
