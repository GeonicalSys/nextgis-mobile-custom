---
id: android-20261113-ux06-menu-maintenance
area: Интерфейс и настройки
kind: internal
at: '2026-11-13T10:52:35.393628+00:00'
title: 'UX-06: убрать трек из главного меню и перенести подтверждаемые очистки в общие
  настройки'
why: Согласованные завершённые изменения рабочей сессии
covers:
- app/src/androidTest/java/com/nextgis/mobile/reliability/SettingsMaintenanceTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
- app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
- app/src/main/java/com/nextgis/mobile/fragment/SettingsFragment.java
- app/src/main/java/com/nextgis/mobile/util/MaintenanceActions.kt
- app/src/main/res/menu/main.xml
- app/src/main/res/values-ru/maintenance.xml
- app/src/main/res/values/maintenance.xml
- app/src/main/res/xml/preferences_general.xml
- app/src/test/java/com/nextgis/mobile/util/MaintenanceActionsTest.java
- tools/mobile-test-plan.json
- update-reports/entries/android-20261113-ux06-menu-maintenance.md
---

UX-06: убрать трек из главного меню и перенести подтверждаемые очистки в общие настройки

Решения и зависимости:

- Main menu drops track/cleanup routes; map rail retains permission continuation and UUID stop confirmation. Settings General invokes confirmed maintenance without MainActivity; cleanup is limited to LogFiles or LayerBackupManager backup root. No field/project/queue/prefs migration.
- Seven cleanup unit tests passed; native tests compiled apart from unavailable Espresso usage, replaced with existing UiAutomation. Final combined emulator acceptance pending; physical Full deferred by user. Complete scoped context delivered, relevant menu/settings/backup/track-stop source and contracts read; unrelated UNKNOWN memberships remain unclaimed.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261113-ux06-menu-maintenance. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
