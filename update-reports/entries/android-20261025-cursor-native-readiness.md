---
id: android-20261025-cursor-native-readiness
area: Проверки мобильного приложения
kind: internal
at: 2026-10-25T21:50:00Z
title: Готовность сцены и диагностика native проверки курсора
why: Full остановился на ожидании маркера, а отдельный повтор прошёл без изменений
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - tools/mobile-test-plan.json
---

Связь с дорожной картой — MOB-01/REL-03: надёжная приёмка текущей карты.
Graphics fixture ждёт не только появления Style, но и завершения текущего
app layer apply до подстановки технического fix. Штатный post-load hook
перечитывает настоящий GPS, поэтому ранняя инъекция может быть очищена;
эта гонка согласуется с кодом, но причина одиночного Full timeout без
дополнительного состояния не установлена.

При новом таймауте сохраняются фаза, lifecycle/focus, camera, flags загрузки,
cached fix и source/rendered marker. Лимит 30 секунд, исходные геометрические,
pixel/heading/visibility assertions и обязательность обоих методов сохранены.
Related cursor-rendering запускает методы отдельно, как Full. Это улучшение
fixture/диагностики, не заявление об исправлении производственного GPS дефекта.
