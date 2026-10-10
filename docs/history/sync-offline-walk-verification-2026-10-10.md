---
title: Синхронизация без сети и продолжение обхода после GPS gap
type: reference
last_verified: 2026-10-10
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
  - app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/SyncAccountWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/TrackWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/TrackUploader.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/WalkEditService.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SyncOfflineAndWalkGpsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/ProjectSyncRunnerTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
---

# Проверка 2026-10-10

Задача продолжает собственный Draft app #55 на `codex/sync-notification-cleanup`.
Preflight с fetch всех шести repositories перед правками: clean, 0/0.
Identity — `geoglyth`; открытых чужих Android PR и пересечений путей нет.
Для нового изменения owning maplibui создана одноимённая ветка от fresh master.
Рабочий телефон не изменялся; ADB в этом цикле видел только отдельный эмулятор.

## Диагноз и результат

Ручной запуск раньше начинал service/queue без проверки сети и показывал общую
ошибку. Теперь tap, retry и long press при отсутствии подключения сообщают
«Отсутствует подключение к интернету» до queue/lease/track scheduling и изменения
режима offline sync. Ошибка при исчезновении сети использует тот же текст в
диалоге и notification 518. При наличии подключения HTTP 503 остаётся серверной
ошибкой. Проверка не делает внешний HTTP probe и не требует Google validation.

WorkManager account/track jobs уже имели CONNECTED constraint. Добавлены guards
в account worker до requestSync, в runner до lease/progress/diagnostic START и
в track worker/uploader до workspace lease/registration/следующего пакета.
Offline ожидание не создаёт уведомление/багрепорт, journal и sent=0 сохраняются.
Android scheduler получает retry; если сеть пропала во время уже выполняемого
запроса, действует штатное восстановление. Отправка треков — отдельная очередь
при включённом upload intent; это не изменение её сервера, периода или настройки.

WalkEditService включал `gps_paused` при unavailable/gap/unexpected destruction
и при sticky restart с уже записанными узлами. Теперь потеря GPS сохраняет
принятый хвост, сбрасывает sampler и ждёт следующий пригодный fix; первый fix
не теряется. Пауза задаётся пользователем и восстанавливается из stored state.
Владелец, полная геометрия, point lock, Finish/Discard fence и GNSS filters
сохраняются. Старое stored paused=true не сбрасывается, поскольку это может
быть ручная пауза. Между последней и следующей пригодными координатами сохраняется
обычная геометрия обхода; пропущенные измерения не реконструируются.

## Проверки

- Windows 11, Android Studio JBR 21.0.9, Gradle 9.3.1, API 36 Android 16 x86_64
  `Medium_Phone_API_36.0`, own read-only emulator on port 5556 с отдельным userdata.
- app unit API 26/36: **72/72**; maplib: **519/519**; maplibui: **92/92**,
  failures/errors/skips 0. Debug/library/test APK assembly и compile Lisa/Belka
  Release Java/Kotlin source sets прошли. Новых dependencies/version bump нет.
- Native **23/23**, 136.149 s: SyncOfflineAndWalkGpsTest (5), SyncNotificationsTest
  (3), ProjectSyncRunnerTest (7), TrackUploadTest (8). Real Android network radios,
  WorkManager, dialog/notification, queue/workspace/SQLite/service используются
  на isolated Debug, только synthetic data и loopback endpoint/DSN.
- Background account work без сети: ENQUEUED, runAttemptCount=0, нет sync state/
  lease/error; после reconnect absent synthetic account спокойно завершается.
- Dispatched background runner: account Pass не вызван, journal/last-sync неизменны;
  после reconnect все три synthetic projects выполняются и pending queue очищается.
- Track worker и live uploader offline: нет registration/packet requests, точки
  не ACK; после reconnect реальные loopback packets ACK ровно свои rowids.
- Walk callbacks: unavailable и timestamp gap, первый пригодный fix, duplicate
  rejection, сохранение point lock, unexpected service end, manual Pause и sticky
  `onStartCommand(null)` branch. Это не проверка настоящего OS process kill.
- Physical/OEM screen-off GNSS, production NGW/network switching и полный
  reliability package не проверялись. Release APK/release/publish не выполнялись.
- Документация проходит strict changed-path validator и tooling tests; CI после
  push сообщается отдельно. Предыдущий Android CI app #55 был cancelled на timeout;
  он не считается успешной проверкой текущего изменения.

Воспроизведение native: explicit emulator serial, Debug/test APK с
`-PdiagnosticDeliveryChecks=true`, grants storage/location/accounts/notifications,
DiagnosticsTestRunner с `diagnosticsDsn=http://synthetic@127.0.0.1:9/1` и классами
из строки выше. Helper меняет только network radios изолированного эмулятора
и восстанавливает исходные settings; production account credentials не нужны.

## Состав и порядок поставки

| Изменение | Owner / commit | PR / base | Состояние / включение |
|---|---|---|---|
| Startup HyperLog, HTTP diagnostic noise/context, canonical form hash, power warning | app `a7df33f5b6f7ef143d787b4be9fef8ca32177bd2`, maplib `7525d38d79e9e6201a64f1fc404c9d6eff4e2d41`, maplibui `acd9b3c82ec9bf66befd59c382e88ef7eba231ad` | app #54 / my-maplibre, maplib #46 / master, maplibui #34 / master | MERGED; содержатся в app base и library bases |
| Stale notification cleanup/default error-only messages | app `bc879f54696b9272a790770839dd343d058ef959` | [#55](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/55) / my-maplibre | OPEN Draft; ancestor текущего app branch |
| Walk GPS continuation; account/track wait for network | maplibui `e697b07d71b8b03bb0b542ea2d762e011b0a85e6`, ancestor `ddf7e8a810540aea6055152fa146c2d0f67b6b9a` | [#35](https://github.com/GeonicalSys/android_maplibui/pull/35) / master | OPEN Draft; app временно закрепляет tested branch tip для review/CI |
| Readable manual offline sync, background runner guard, native checks/docs | app scoped follow-up to `bc879f5` | [#55](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/55) / my-maplibre | OPEN Draft; присутствует в текущем PR diff |

Порядок: **Merge Commit maplibui #35 → fetch remote master → pin его merge commit
в app #55 → Squash app #55 → closure audit → Release APK**. Branch-tip pin для
review/CI не означает завершённую зависимость. Merge/release/publication
заблокированы до обновления consumer pin и проверки remote inclusion. В этом
цикле разрешены правки/commits/Draft PR, merge пользователем не запрошен.
