---
id: android-20261026-ci-track-verification-route
area: Автоматическая приёмка мобильного приложения
kind: internal
at: 2026-10-26T20:10:00Z
title: CI выполняет отправку треков через тот же изолированный runner, что и Full
why: Дублирующий plain-runner запуск обходил существующие startup/workspace barriers и завершался непостоянными ранними false или таймаутом hub
covers:
  - .github/workflows/android-reliability.yml
  - tools/tests/test_mobile_ci_routing.py
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
---

MOB-01/REL-03. Удалён только повторный plain AndroidJUnitRunner запуск
TrackUploadTest из normal Debug. Все прежние9 методов остаются в CI через
уже существующую область tracks verification runner, с теми же assertions;
их включение проверяет отдельная regression. Native Full каталог254 не
сокращён. Timeout hub сообщает requests/registration/network/interrupt state,
а не один raw TimeoutException. Код отправки и сетевые constraints не меняются.
Исходный transport trigger CI не объявляется доказанным только по отсутствию
повтора; достоверно устранено различие маршрута запуска и улучшены witnesses.
