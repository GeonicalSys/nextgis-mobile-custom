---
id: android-20261025-maintenance-reminders
area: Мобильное приложение
kind: user
at: 2026-10-25T21:06:00Z
title: Напоминания о синхронизации и отложенном обновлении
why: Полевые устройства подолгу остаются без интернета и работают со старой версией
covers:
  - app/src/main/java/com/nextgis/mobile/util/MaintenanceReminders.java
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/res/values/maintenance_strings.xml
  - app/src/main/res/values-ru/maintenance_strings.xml
  - app/src/test/java/com/nextgis/mobile/util/MaintenanceRemindersTest.java
  - app/src/test/java/com/nextgis/mobile/util/AppUpdateReadyStateTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/MaintenanceRemindersNativeTest.java
  - tools/mobile-test-plan.json
  - tools/run_mobile_tests.py
  - tools/mobile_style_evidence.py
---

Связь с дорожной картой — UX-05, внеплановое поручение перед приёмкой выпуска.
При запуске приложение напоминает о более чем недельном отсутствии полной
успешной синхронизации онлайн-проекта и успешной проверки обновлений. Локальные
проекты исключены из проверки sync. Для давно известного обновления различаются
незавершённая загрузка и проверенный APK с предложением установки.
Предупреждение не мешает продолжить работу, не повторяется при повороте или
сетевом retry в том же процессе и не запускает установку/синхронизацию само.
Неизвестная старая история получает неделю наблюдения; фактический sync clock
не подменяется запуском, частичной отправкой либо неуспешной попыткой.
Проверки и выпуск фиксируются отдельно в квитанции Full.
