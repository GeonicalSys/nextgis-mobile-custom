---
id: android-20261024-polygon-style-controls
area: Мобильное приложение
kind: internal
at: 2026-10-24T13:27:26+02:00
title: Подключены новые стили полигонов и живой предпросмотр с проверками на смартфоне
why: Нужно доказать реальную отрисовку новых параметров и доступность редактора
covers:
  - tools/run_mobile_tests.py
  - tools/mobile_style_evidence.py
  - tools/tests/test_mobile_style_evidence.py
  - maplib
  - maplibui
  - tools/mobile-test-plan.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleTileFallbackTest.java
---

Подключены библиотеки нового редактора и отрисовки. Адресные native-проверки проходят путь SQL → JSON категорий → настоящая карта: прозрачный фон, цветные узоры, пунктир, дополнительный контур, отверстия, Polygon/MultiPolygon, смена стиля, очистка изображений и fallback. Отдельная матрица редактора проверяет реальные нажатия, Save и Cancel в 72 русских профилях интерфейса. Full выполняется перед публикацией; сейчас публикация отложена.

Runner автоматически сохраняет только ограниченные QA PNG и их хеши перед сбросом verification-данных; ручной сбор изображений больше не нужен.
