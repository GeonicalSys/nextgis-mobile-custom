---
id: android-20261126-basemap-startup
area: Запуск мобильного приложения
kind: user
at: '2026-11-26T07:05:30+00:00'
title: Подготовка встроенной базовой карты перенесена с главного потока
why: Распаковка встроенной карты при холодном запуске задерживала обработку интерфейса и приводила к ANR до начала проверок
covers:
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/util/DefaultBasemapInitializer.java
  - app/src/test/java/com/nextgis/mobile/util/DefaultBasemapInitializerTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/DefaultBasemapStartupTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SharedTracksTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
  - tools/mobile-test-plan.json
  - agent-system/knowledge/documentation/app-module-readme-verification-startup-and-underlay-lease-barrier.json
  - agent-system/knowledge/documentation/automated-tests-verification-startup-recovery-and-lease-barrier.json
  - agent-system/knowledge/documentation/fork-customizations-ngw-account-import-and-layer-config-routes.json
---

MainApplication передаёт подготовку встроенного OSM-архива отдельному
инициализатору. Распаковка выполняется на рабочем потоке в собственный staging,
а публикация — после повторной проверки проекта, слоя, пути, URL и типа
подложки. Координация операций удерживает принадлежащее задаче рабочее
пространство. Подготовленные файлы публикуются с сохранением уже существующего
кэша, затем staging очищается и освобождается lease.

Обычные инструментальные сценарии начинают работу после завершения фоновой
подготовки. Проверка первого запуска наблюдает её в процессе: проверяет
отклики главного потока и прогресс, затем сверяет 1436 записей реального
архива, состав файлов, хеш контрольной плитки и очистку временных файлов.
В сценариях переключения проектов и отправки треков тестовые данные и разрешённые
действия ждут готовности рабочего пространства. Запрет на переключение во время
записи проверяется отдельно. Утверждения о доставке и регистрации не менялись.

Исходный ANR обнаружен до входа в FormSaveRecoveryTest: главный поток
находился в FileUtil.copyStream / TMSLayer.fillFromZip, а instrumentation
ждала стартового idle-барьера. Предыдущая попытка уже показывала ту же
ветвь подготовки базовой карты; другой стартовый ANR был в загрузке JNI
MapLibre. Наличие этих свидетельств не означает воспроизведение ошибки
синхронизации и не закрывает все возможные причины задержек запуска.

Это самостоятельное исправление запуска, отдельное от восстановления
старой очереди. Финальные JVM/native и холодный запуск текущего 229 ещё
ожидают подтверждения. Проверка телефона и стандартный Full не выполнялись;
для текущего выпуска действует отдельное поручение emulator-only.

Холодный запуск с настоящим архивом и проверки треков прошли в `native-release229-03`. Девять JVM-проверок фонового запуска прошли в `jvm-migration-08`.
