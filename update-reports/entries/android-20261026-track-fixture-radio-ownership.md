---
id: android-20261026-track-fixture-radio-ownership
area: Проверки отправки треков
kind: internal
at: 2026-10-26T17:20:00Z
title: Один владелец состояния сети в native проверке отправки треков
why: Внутренний сценарий отправлял асинхронные radio команды поверх общего network fixture
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
---

MOB-01/REL-03. Удалена избыточная пара fire-and-forget svc wifi enable/disable:
@Before уже устанавливает connected Wi-Fi, @After ожидает восстановление.
Сценарий WorkManager retry после регистрации и все его assertions сохранены.
Это устранение обнаруженного нарушения владения radio state в тесте;
принадлежность исходного CI badge failure этой гонке пока не доказана.
