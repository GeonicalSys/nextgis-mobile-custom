---
id: android-20261024-native-style-visible-input
area: Мобильное приложение
kind: internal
at: 2026-10-24T15:58:57+02:00
title: Исправлено касание высоких кнопок в матрице редактора
why: CI требовал полной видимости высокой кнопки внутри прокрутки и останавливался до реального касания
covers:
  - tools/mobile-test-plan.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/NativeStyleTouch.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
---

Тестовый ввод выбирает стабильную видимую область элемента в физических координатах, ограниченную прокруткой, окном и экраном. Фокус и штатная targeted UID защита Android остаются обязательными; для SeekBar проверяется доступность настоящего минимального значения. Регрессия создаёт кнопку выше viewport и подтверждает доставку реального касания. Продуктовый APK и поведение жестов пользователя не менялись.

Адресная область style-editors выполняет обе матрицы, pixels предпросмотра и category Cancel/Save, чтобы проверять изменения тестового ввода до длительного прогона остальных стилей.
