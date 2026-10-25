---
id: android-20261025-mixed-map-candidates
area: Мобильное приложение
kind: user
at: 2026-10-25T22:15:00Z
title: Общий выбор треков и векторных объектов на карте
why: Ранний выбор трека либо точечного объекта скрывал остальные совпадения
covers:
  - maplib
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/java/com/nextgis/mobile/util/MapSelectionDialog.java
  - app/src/main/res/values/map_candidate_strings.xml
  - app/src/main/res/values-ru/map_candidate_strings.xml
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - tools/mobile-test-plan.json
---

MOB-01 / UX-03, обязательное поручение пользователя перед выпуском. Обычный
тап сначала собирает кандидатов из векторных слоёв и треков. Один результат
выбирается сразу, несколько показываются в общем прокручиваемом списке.
В смешанном списке трек помечен типом; слои сохраняют своё имя и описание
объекта. Геометрия и feature ID связаны с собственным слоем, а ID треков
не смешиваются с feature ID. Выбор доступен также после уже выбранного
объекта/трека; инструменты сохраняют свои жесты. Строки переносятся при
крупном шрифте; Cancel ничего не выбирает, старый диалог закрывается с view.
Native проверяет два трека + Point/Line/Polygon, выбор каждого и lone cases,
длинные строки/темы/font/orientation и приоритет полевых линий под точками.
