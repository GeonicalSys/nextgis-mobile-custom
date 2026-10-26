---
id: android-20261026-api30-verification-fixes
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T16:20:00Z
title: Совместимость native проверок с Android 11 и диагностика переноса инструкции
why: Полный планшетный прогон выявил API33 чтение потоков и capture окна до attach
covers:
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationIo.java
  - app/src/androidTest/java/com/nextgis/mobile/util/MaintenanceRemindersNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/FeaturePersistenceTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/StandardFeatureTypesTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/EmulatorNetworkFixture.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
  - tools/mobile_style_evidence.py
  - tools/run_mobile_tests.py
  - tools/mobile-test-plan.json
  - docs/reference/mobile-release-fixes-2026-10-26.md
---

MOB-01/REL-03/UX-03. Потоки читаются переносимым буфером с прежними проверками
содержимого ZIP/фото/каталога; вызывающий код закрывает ресурс. Диалог снимается
после attach UI окну, с сохранением полной матрицы и проверок действий/прокрутки.
Для азимута сохраняются точные параметры неудачного переноса и PNG до assertion;
адресные области не меняют состав Full. Результаты фиксируются в квитанции выпуска.
