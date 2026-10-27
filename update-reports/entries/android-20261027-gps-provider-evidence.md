---
id: android-20261027-gps-provider-evidence
area: Диагностика GPS
kind: internal
at: 2026-10-27T15:00:00+01:00
title: Системные разрешения и ограничения фонового GPS в отчёте о тишине provider
why: Группа90 содержит реальные длительные provider_silent при background owner и screen off, но без данных о разрешениях и standby policy
covers:
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsGpsPlatform.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsGpsPlatformTest.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
---

MOB-02. Только к уже классифицированному редкому GPS отчёту добавляются
fine/coarse/background permissions, доступность GPS/global location,
background restriction, standby bucket, process importance и observer_on_main.
Нет чтения координат, accounts, бизнес-хранилища или новых категорий событий.
Порог, retry, владение GPS и подавление нормального ожидания не меняются.
API26 не объявляет отсутствующее тогда background permission отозванным;
ошибка необязательного платформенного чтения не теряет исходный отчёт.

Это улучшение сбора, не доказанное исправление OEM/provider silence.
В первый приватный диагностический APK030f303 оно ещё не включено;
выпуск и фактическое выполнение на проблемном устройстве учитываются отдельно.
