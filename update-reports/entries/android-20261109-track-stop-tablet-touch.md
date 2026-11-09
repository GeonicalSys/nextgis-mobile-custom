---
id: android-20261109-track-stop-tablet-touch
area: Мобильное приложение
kind: internal
at: '2026-11-09T17:38:35+04:00'
title: Проверка касания Cancel в диалогах трека на планшете
why: Альбомный профиль T40 Plus направлял касание мимо масштабированной кнопки
covers:
  - agent-system/knowledge/documentation/automated-tests-track-stop-confirmation-and-colour-migration.json
  - agent-system/knowledge/documentation/automated-tests.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-09-tablet-track-stop.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - agent-system/knowledge/documentation/track-controls-track-controls-regression-matrix.json
---

Тест измеряет реальную видимую кнопку с учётом масштаба её контейнера. Он проверяет одно нажатие Cancel, закрытие диалога и продолжение записи.

Rename, Color и Delete проверяются после стабилизации окна и фактического закрытия по одному касанию.
