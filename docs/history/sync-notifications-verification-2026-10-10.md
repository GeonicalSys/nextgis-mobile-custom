---
title: Проверка уведомлений синхронизации
type: reference
last_verified: 2026-10-10
related_code:
  - app/src/main/java/com/nextgis/mobile/util/OfflineSyncIntentService.java
  - app/src/main/java/com/nextgis/mobile/util/SyncNotifications.java
  - app/src/main/java/com/nextgis/mobile/datasource/SyncService.java
  - app/src/main/java/com/nextgis/mobile/datasource/SyncAdapter.java
  - app/src/test/java/com/nextgis/mobile/util/SyncNotificationsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SyncNotificationsTest.java
---

# Уведомления синхронизации

По явному указанию пользователя после подготовки предыдущего merge создана
новая `codex/sync-notification-cleanup` от remote-fresh `my-maplibre`.
Preflight всех шести workspace repositories прошёл: clean, ahead/behind 0/0;
после fetch открытых Android PR и published codex heads не было.

## Диагноз на телефоне

Read-only ADB Samsung SM_A566B подтвердил production 3.1.2.27/221 и отсутствие
работающих sync-служб/active Android sync. Ручной проход завершился без ошибки
2026-10-10 в 11:32:02.059 CEST. Системный event log удалил notification 519 в
11:32:02.077, затем тот же process повторно опубликовал его в 11:32:02.091 уже
без FOREGROUND_SERVICE flag. Это гонка финального progress update с удалением
foreground-индикатора; остался ложный бесконечный spinner, а не работающий sync.
Телефон, данные, настройки и установленное приложение в этой задаче не менялись.

## Исправление

Manual receiver не публикует inactive snapshot и блокирует late callback после
onDestroy. Обе службы закрывают notifications до unregister, снимают foreground
и отдельно отменяют свой ID. MainApplication очищает orphan 519/520 только при
старте основного процесса, до создания служб. Error/GPS notifications сохраняются.

`show_sync` остаётся false в XML/fallback, без сброса явного выбора пользователя.
Ошибка очереди уведомляет через единый error ID 518 независимо от этого пункта;
raw exception message не показывается. Успешный повтор снимает ошибку, старт и
отмена её не скрывают. Предпочтение подписано как start/finish messages; тихий
системный индикатор foreground service остаётся лишь на время работы.
Queue, owning database, cancellation/leases, schedules и версии не меняются.

## Проверки

Пройдены app unit 70/70 (новые lifecycle cases на API 26 и 36), native 3/3 на
Android 16/API 36 x86_64, Lisa Debug/test APK и Lisa/Belka Release сборки.
Native проверяет реальный NotificationManager и bound account service:
finish/cancel снимает foreground, late progress/start не возвращает карточку.
Первая попытка native fixture ожидала уведомление 5 секунд и не учла системную
задержку показа foreground-карточки Android 12+. Лимит ожидания теста увеличен
до 15 секунд; финальный запуск прошёл за 11.166 секунды. Код foreground запуска
не менялся ради теста. Физический телефон в тестах не использовался.

`verify-apk-version-matrix.ps1 -SkipBuild` проверил metadata уже собранных APK:
Lisa Debug 3.1.2.23/218, Lisa/Belka Release 3.1.2.27/221; MapLibre OpenGL 13.0.2
и coupling maplib совпадают. Maplib unit task: 519/519, UP-TO-DATE без изменений
библиотеки; maplibui unit 92/92 и его сборка прошли. Документация прошла
validator и 7/7 tests tooling; strict changed-file routing проверяет полный diff.

Robolectric 4.16.1 уже используется maplib; добавлен только в app test configuration
с Android resources на API 26/36. SHA-256 новых test artifacts проверяются при
включённой dependency verification; runtime APK dependency graph не меняется.
Четыре добавленных SHA-256 независимо сверены с Maven Central:
error_prone_annotations 2.36.0 jar/pom, error_prone_parent 2.36.0 pom и
guava 33.4.8-jre jar. Ошибки verification не обходились.
Native fixture не имеет NGW accounts и запускается только на собственном read-only
AVD Android 16/API 36 x86_64 с отдельной временной data image, не на рабочем телефоне.

## Зависимости и поставка

| Предшественник | Состояние / включение в базу |
|---|---|
| App #54 | Squash MERGED, `a7df33f5b6f7ef143d787b4be9fef8ca32177bd2`; это база новой ветки |
| Maplib #46 | Merge Commit MERGED, `7525d38d79e9e6201a64f1fc404c9d6eff4e2d41`; exact app gitlink |
| MaplibUI #34 | Merge Commit MERGED, `acd9b3c82ec9bf66befd59c382e88ef7eba231ad`; exact app gitlink |
| Easypicker / publisher | Чистые default branches, без открытых PR и новых published codex heads |

Новая правка принадлежит app; код/документы библиотек и gitlinks не меняются.
Достаточно Squash нового app Draft PR после review. Проверочные сборки не являются
релизом; merge и публикация APK этой задачей не выполняются. Физическая повторная
синхронизация после установки исправления и OEM battery behavior остаются непроверенными.
Полный NGW multi-account/large-pull/recovery smoke и GUI проверки Lisa/Belka Release,
OpenGL и добавления реального NGW account в рамках этой правки не повторялись:
очередь, сети, account identity и runtime renderer не менялись.
