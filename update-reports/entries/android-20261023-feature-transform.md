---
id: android-20261023-feature-transform
area: Мобильное приложение
kind: user
at: 2026-10-23T19:23:46+02:00
title: Сдвиг и поворот целого объекта жестами
why: Положение и ориентацию существующего объекта можно исправить без перемещения отдельных вершин
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
---

В редакторе один палец сдвигает весь объект, два поворачивают без масштабирования и движения камеры. Сохраняются части, отверстия, один Undo на жест, точная отмена и штатное сохранение. Панели проверяются с крупным интерфейсом. Зависимости: maplib #59 → maplibui #43 → app #77.
