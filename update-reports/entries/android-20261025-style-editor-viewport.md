---
id: android-20261025-style-editor-viewport
area: Мобильное приложение
kind: internal
at: 2026-10-25T12:40:00+01:00
title: Проверка границ редактора стиля учитывает точный профиль и высоту прокрутки
why: На эмуляторе обнаружено окно с областью прокрутки 199 px и кнопкой 207 px
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleEditorTest.java
  - tools/mobile-test-plan.json
---

Ошибка native-матрицы теперь сохраняет font/viewport/theme, размер прокрутки,
образца и видимую рамку кнопки. Добавлен адресный Related polygon-style-ui;
остаются реальные нажатия и строгое требование полной видимости. Исправление
компактного образца принадлежит maplibui; 72 профиля перепроверяются совместно.
