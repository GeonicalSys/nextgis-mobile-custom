---
id: android-20261017-verification-startup-wait
area: Сопровождение
kind: internal
at: '2026-10-17T20:26:33+03:00'
title: Ожидание реального старта тестового приложения
why: Тестовые данные можно готовить только после завершения настоящего запуска приложения
covers:
- .github/workflows/android-reliability.yml
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestApplication.java
- app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
source_commits:
- ac5d6d43ef04784bec32226d2eb83ab10c6666dd
---
Подготовка тестовых данных начинается после подтверждённой готовности приложения; обновлены тестовый запуск и конфигурация автоматической проверки.
