---
id: android-20261023-feature-copy
area: Мобильное приложение
kind: user
at: 2026-10-23T19:23:46+02:00
title: Копирование объекта в другой совместимый слой
why: Готовую геометрию и совпадающие атрибуты можно перенести без повторного рисования
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
  - app/src/main/res/menu/select_action.xml
  - app/src/main/res/menu/select_action_view.xml
  - app/src/main/res/drawable/ic_action_copy.xml
  - app/src/main/res/values/feature_copy_strings.xml
  - app/src/main/res/values-ru/feature_copy_strings.xml
---

Кнопка доступна и для источника только для чтения. Выбор назначения учитывает права и совместимость геометрии без потери частей; категория назначения имеет приоритет. Копия проходит обычную форму, Save/Cancel и durable checkpoint. Нужны связанные maplib #59 и maplibui #43; APK не публикуется.
