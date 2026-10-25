---
id: android-20261025-field-line-display-priority
area: Мобильное приложение
kind: user
at: 2026-10-25T22:20:00Z
title: Полевые линейные над данными и под точками
why: Пользователь просит видеть полевые линии поверх треков и других объектов
covers:
  - maplib
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
---

MOB-01 / UX-03. App принимает изменение библиотеки: тела и подписи слоя
«Полевые линейные» / field_lines подняты над обычными данными и треками,
но ниже Point/MultiPoint. Порядок между точками, инструментами и GPS
сохранён. Native проверяет body/label order после full/lite/reorder и
пиксели точки поверх трека. Это отдельная доработка от общего списка
кандидатов: приоритет отрисовки не исключает объект из выбора.
