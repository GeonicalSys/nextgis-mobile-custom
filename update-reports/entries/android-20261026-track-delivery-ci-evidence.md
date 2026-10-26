---
id: android-20261026-track-delivery-ci-evidence
area: Проверки отправки треков
kind: internal
at: 2026-10-26T17:05:00Z
title: Свидетельства регистрации и сети при непостоянном сбое CI
why: CI терял positive registration badge после ожидаемой ошибки пакета без достаточной диагностики
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
  - tools/mobile-test-plan.json
---

MOB-01/REL-03. Сохраняются bounded loopback request/response sequence, число
проверок регистрации, Android network snapshot и interrupt flag. Проверяется,
что обе ожидаемые регистрации действительно дошли до сервера; прежние
проверки intent/badge/packet acknowledgements сохранены. Новый адресный запуск
track-delivery выполняет прежние9 методов. Это диагностика, не заявление
об устранённой причине CI; результат и ограничения — в квитанции выпуска.
