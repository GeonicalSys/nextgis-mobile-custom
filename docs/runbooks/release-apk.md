---
title: Выпуск Lisa и Belka APK
type: runbook
last_verified: 2026-10-23
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - app/src/main/java/com/nextgis/mobile/util/StartupUpdateCheckScheduler.java
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/AndroidManifest.xml
  - app/src/lisa/res/values/launcher_icon.xml
  - app/src/belka/res/values/launcher_icon.xml
---

# Выпуск Lisa и Belka APK

## Конфигурация багрепортов

Обычная локальная сборка читает `sentry.dsn` из игнорируемого `sentry.properties`.
Это публичный SDK DSN нужного GlitchTip project; административный пароль и auth
token для отправки ошибок APK не нужны. Не печатать DSN и не добавлять его в Git.
Проверять host/project установленного приёмника для всех variants. В явном CI
режиме допускается отсутствие private file и пустой DSN — отчёты отключены.
`diagnosticDeliveryChecks=true` нужен только test APK, а не production конфигурации.
Перед обновлением SDK выполнить [delivery checks](../architecture/error-reporting.md).

## Версия

Подготавливаемый выпуск `3.1.2.30`: production `versionCode 224`, Lisa Debug —
`3.1.2.23` / `versionCode 218`; диагностический release
`maplib.VERSION_NAME 3.1.2.30`, debug — `3.1.2.23`.

1. Определить, меняется production release или только Lisa Debug. Нельзя
   подменять debug-only задачу глобальным bump.
2. Для production изменить `productionVersionCode` и
   `productionVersionName` в `app/build.gradle`, production version в
   `maplib/build.gradle`, затем согласованно обновить обе flavors.
3. Для debug-only изменить `debugVersionCode`/`debugVersionName` приложения и
   `debugVersionName` maplib. Production constants остаются прежними.
4. AGP `9.1.0` не поддерживает application `versionCode`/`versionName` внутри
   `buildTypes`. Debug app override находится в
   `androidComponents.onVariants`; maplib debug переопределяет только
   `BuildConfig.VERSION_NAME` через `buildConfigField`.
5. Обновить ожидаемые package/version значения в
   `tools/verify-apk-version-matrix.ps1`: это намеренно независимый regression
   oracle, а не автоматическое чтение тех же Gradle constants.
6. Обновить build/compatibility docs. Production `WHATS_NEW.md` меняется только
   для production release; для debug-only публикации достаточно её release
   notes, если пользователь не запросил иное.

## Обязательная сборка и проверка версии

Запрос 2026-10-23: повысить production до 3.1.2.30/224, собрать подписанный
локальный APK только ЛИСА, без публикации. Belka APK пользователь отложил.
После закрытия library/app PR выполнить version matrix с `-LisaOnly`;
проверить Lisa Debug/Release, diagnostic versions maplib, release identity,
подпись и SHA-256. Этот выбор не меняет default matrix для будущего Full.
Full на смартфоне/эмуляторе и Check-Publication обязательны перед публикацией;
локальная release-сборка сама по себе их не требует. Publisher, включая
dry-run, в этот запрос не входит. [Матрица закрытия](../history/mobile-release-3.1.2.30-2026-10-23.md).

Выпуск 3.1.2.29 / 223: maplib #50 и maplibui #36 (Merge Commit) →
maplib #51 с production `3.1.2.29` (Merge Commit
`28a4300ae308c6f531c9b8073382f09c1d6ad052`) → fetched pins в app #59 →
зелёный CI → app #59 (Squash) → APK version matrix → dry-run → публикация
только `lisa` и `belka`. Debug сохраняет 3.1.2.23 / 218.

Локальная сборка Lisa Release 17 октября 2026 без повышения версии и без публикации: maplib #55 (Merge Commit `3f1db55dea715a873f6a073854a0aca1b7154eaf`) и maplibui #38 (Merge Commit `9c4b8c82f388ad2d040ba59d72b3cf431e1460a7`) → fetched pins в app #67 → Squash. Версии остаются 3.1.2.29 / 223 и debug 3.1.2.23 / 218. Publisher и установка на рабочий телефон не входят в этот шаг.

Выпуск «Орбиты-кольца» 3.1.2.28 / 222: maplib #49 (Merge Commit) → fetched
remote merge pin в app #58 → зелёный CI итогового app head → app #58 (Squash)
→ APK version matrix → подпись/identity → publisher dry-run → публикация
только `lisa` и `belka`. Debug сохраняет 3.1.2.23 / 218 и не публикуется.
Перед сборкой сверить [матрицу доставки](../history/orbit-ring-delivery-2026-10-11.md).

«Контрастная орбита»: maplib #48 (Merge Commit
`f2c676a6ea0e35dfb97f539ee3b5a9b653b273ab`) → fetched pin в app #57 →
зелёный CI итогового app head → app #57 (Squash). Для локального Lisa Release
проверить все строки [матрицы закрытия](../history/contrast-orbit-delivery-2026-10-11.md),
включение merged библиотеки и предыдущих исправлений. #48 слит и fetched pin
обновлён; до зелёного CI и Squash #57 release-сборка заблокирована.

«Орбита» и исправление завершения перетаскивания слоёв: maplib #47
(Merge Commit `fa11fc72226b776ed528e207772ac26575180577`) → fetched pin в app #56
→ зелёный CI на новом app head → app #56 (Squash). По запросу 11 октября нужен
локальный Lisa Release APK для проверки компаса на телефоне. Перед сборкой
сверить remote tip, pin и включение всех исправлений; затем выполнить version
matrix для Lisa Debug и обеих release. Версии остаются прежними; публикация и
установка на рабочий телефон не входят в этот шаг.

Исправления offline sync, уведомлений и GPS обхода: maplibui #35 (Merge Commit
`795f2c681fbed53e0ef496775a10a05e1a6a7124`) → fetched pin в app #55 → app #55
(Squash). По запросу 10 октября нужен локальный Lisa Release APK без повышения
версии, публикации и установки на рабочий телефон. Все required изменения
в app base #54 / maplib #46 / maplibui #34 и этой цепочке должны входить в remote
tip; только затем запускать version matrix. Детали:
[проверка исправлений](../history/sync-offline-walk-verification-2026-10-10.md).

Перед итоговой сборкой закрыть все требуемые PR из delivery matrix. Для исходной
задачи порядок: maplib #45 (Merge Commit) → maplibui #33 (Merge Commit) → app #53
(Squash). Исправление пустых списков: maplibui #34 (Merge Commit) → app #54
(Squash), [матрица исправления](../history/feature-type-empty-lists-2026-10-09.md).
После library merges app закрепляет fetched remote merge commits;
после app merge сверяются remote tip, содержимое и все строки
[матрицы закрытия](../history/feature-type-delivery-2026-10-09.md).
Затем выполнить APK version matrix. Запрос от 9 октября — локальный Lisa Release
APK без повышения версии и публикации: publisher, включая dry-run, не запускается.
Предыдущая цепочка форм и sync: [историческая матрица](../history/mobile-form-sync-delivery-2026-10-08.md).
Предыдущий debug-only цикл: [историческая матрица](../reference/debug-3.1.2.23-delivery.md).

```powershell
Set-Location C:\dev\lisa\android_projects\android_gisapp
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-version-matrix.ps1
```

Скрипт собирает `lisaDebug`, `lisaRelease`, `belkaRelease`, читает APK metadata
через `aapt` и проверяет сопряжённые debug/release значения
`maplib.BuildConfig.VERSION_NAME`, а также разрешённый Lisa Release dependency
graph: OpenGL artifact MapLibre присутствует, generic/Vulkan artifact отсутствует.
Version change нельзя передавать дальше,
если этот скрипт не запускался или завершился ошибкой.

`base.archivesName` использует production version, поэтому локальное имя debug
APK может содержать production basename. Это не версия артефакта. Источники
истины — `output-metadata.json` и результат `aapt dump badging`; publisher после
проверки создаёт каноническое имя вида
`ngmobile-<actual-version>-lisa-debug.apk`.

Дополнительно проверить для каждого APK:

- display name и applicationId;
- launcher, intro и about используют иконку соответствующего flavor: Lisa —
  `ic_launcher_lisa`, Belka — `ic_launcher_belka`;
- embedded `UPDATE_FLAVOR` совпадает с flavor;
- versionCode/versionName;
- подпись ожидаемым release certificate;
- Sentry manifest policy: auto-init, interaction breadcrumbs, view hierarchy
  и crash screenshot выключены, trace/profile sample rate равен `0`;
  AppDiagnostics инициализирует SDK после isolated-script guard;
- JTS Core присутствует в обеих release-сборках, а
  `MultiPolygonGeometryRepairTest` проходит в `:maplib:testDebugUnitTest`;
- runtime dependency graph разрешает `org.maplibre.gl:android-sdk-opengl:13.7.1`
  и не содержит generic/Vulkan MapLibre artifact;
- запуск поверх существующего профиля.

### Выпуск bridge для старого Debug-профиля

Для устройств со старой картой `com.nextgis.mobile.debug` порядок отдельный:

1. Собрать и опубликовать/установить `lisaDebug` с exporter bridge, подписанный
   тем же сертификатом, что уже установленный Debug. Если встроенного updater в
   старой версии нет, использовать штатный MDM либо ручную/ADB-установку APK.
2. Установить актуальный Geonical release с importer bridge и ожидаемым
   production certificate.
3. В Geonical вручную загрузить или выбрать правильный проект: версии
   `3.0.3.2`/`3.0.3.3` не дают надёжной source-project identity.
4. Запустить перенос из хранилища подложек («Импортировать из старого приложения»), проверить подложки после cold start и
   повторить команду для проверки отсутствия дубликатов.
5. Не удалять Debug и его данные, пока пользователь не подтвердил полноту
   результата. Сам bridge ничего не удаляет.

Release smoke должен включать trusted-pair и negative-пару: APK с другим
package/certificate не должен получить URI или поток подложки.

## Self-hosted update manifest

`AppUpdateWorker` планирует уникальную проверку раз в шесть часов через WorkManager.
Она выполняется без открытого окна и восстанавливается после перезапуска процесса
или телефона. Android выбирает точное время; принудительная остановка приложения
приостанавливает фоновые задачи до следующего открытия. Проверка при запуске сохранена.

APK скачивается по Wi-Fi или мобильному интернету, включая тарифицируемую сеть,
только без роуминга (`NetworkType.NOT_ROAMING`). Сокеты и DNS привязаны к выбранной
сети; смена/потеря сети, переход в роуминг и остановка worker отменяют активный HTTP.
Ограничение действует и после начала передачи. Незавершённый файл сохраняется
в private `files/updates/*.part`; повтор использует Range, проверяет Content-Range
и полный SHA-256. Если сервер игнорирует Range, файл скачивается заново без append.
Сетевые ошибки не показывают диалогов из фонового процесса.

После проверки размера, hash, product/version и сертификата APK остаётся в
private `files/updates/`, а `app_update_state.ready_manifest` фиксируется синхронно.
При запуске сначала проверяется локальное состояние, включая offline: только
проверенный APK даёт диалог «Обновление готово» с «Обновить» и «Позже».
«Позже» оставляет файл до следующего запуска; фон сам не вызывает установщик.
Перед установкой файл проверяется повторно. Старый `cache/updates` поддерживается
только для незавершённого install-permission перехода прежней версии.
Verification APK не планирует запросы к production release repository.

Автопроверка запускается в resumed главном окне с фокусом через 2,5 секунды.
`StartupUpdateCheckScheduler` сохраняет незавершённую попытку при отсутствии
сети, ошибке запроса, busy/pending установке, companion UI и потере фокуса.
Повтор после сетевой ошибки: 10, 30, 120, затем максимум один раз в 300 секунд;
изменение default network или Resume приближает попытку до 2,5 секунды.
Пауза/закрытие окна снимает timers и network callback. После process death
восстановленный Bundle не отключает новую проверку; успешный результат или
показанное предложение сохраняется только при пересоздании в том же процессе.
Pending установка остаётся приоритетной отдельной процедурой.
Очищенный невалидный/устаревший pending manifest, уже установленная версия или
отказ в install permission не считаются новой выполненной проверкой:
`resumePendingInstallation()` возвращает true только при начале продолжения.

Сеть должна быть active и иметь INTERNET; системный VALIDATED записывается
для диагностики и не блокирует реальный HTTP request. Manifest request имеет
общий timeout 45 секунд, connect/read — 30 секунд; HTTP cache отключён.
В HyperLog искать `Updater startup` и `Updater check`/`Updater manifest`:
reason/delay, mode, branch, current/available versionCode, признаки сети,
request id, HTTP status, publishedAt, exception и elapsedMs. `result=current`
означает валидный manifest без новой версии; `result=offer_shown` — реальный
показ предложения; `result_deferred` оставляет попытку для foreground retry.
Автоматические сетевые ошибки пишутся локально без диалога/отправки багрепорта.

Локальная regression-проверка: `:app:testLisaDebugUnitTest` включает
`StartupUpdateCheckSchedulerTest` и `AppUpdateNetworkStateTest`.
`AppUpdatePendingInstallationTest` проверяет, что очищенная continuation не
подавляет проверку следующего выпуска, сохраняя product validation.

Публичная база: `https://apps-geonical.ru/lisa-mobile`. Ветки и manifest:

| Variant | Ветка | Manifest |
|---|---|---|
| Lisa Release | `lisa` | `/lisa-mobile/lisa/manifest.json` |
| Belka Release | `belka` | `/lisa-mobile/belka/manifest.json` |
| Lisa Debug | `debug` | `/lisa-mobile/debug/manifest.json` |

Дополнительного сегмента `stable` в URL нет. Manifest содержит
`applicationId`, конечные `flavor` и `channel`, `versionCode`, `versionName`,
`minSdk`, `targetSdk`, versioned `apkUrl`, размер APK, SHA-256 APK, SHA-256
signing certificate и release notes.

Updater должен отклонить неверные schema, flavor/channel, application ID,
version, URL, размер, hash или certificate. После скачивания те же identity и
integrity значения сверяются с реальным APK и установленным приложением.
На Android 9–10 архивный `SigningInfo` бывает пустым, поэтому PackageManager
запрашивается одновременно с `GET_SIGNING_CERTIFICATES` и `GET_SIGNATURES`.
Legacy-поле является только запасным источником байтов сертификата: SHA-256 из
manifest по-прежнему обязан присутствовать и у APK, и у установленного пакета.

На Android 8+ при отсутствии разрешения «Установка неизвестных приложений»
updater сохраняет проверенный manifest в app-private `app_update_state`, открывает
экран специального доступа и при возврате в `MainActivity.onResume()` повторно
проверяет разрешение, manifest и APK. После успешной выдачи разрешения установка
продолжается автоматически; валидный APK из `cache/updates` не скачивается
повторно. Pending-состояние одноразовое и очищается при отказе, ошибке проверки
или перед возобновлением установки.

Значения signing keys/cert private data в docs не публикуются. Допустим только
публичный fingerprint в защищённой release-инфраструктуре.

Перед публикацией обновления для поддерживаемого Android 9/10 выполнить
`SMOKE-SELF-UPDATE` на реальном устройстве или сохранить его как явно
невыполненный device-smoke; одной проверки APK на компьютере недостаточно.

## Публикация

Publisher находится в `C:\dev\lisa\android_projects\upload_mobile`. Он принимает явный путь к
APK и извлекает package/flavor/version/certificate из APK; ручной manifest не
является входом.

Сначала обязательный dry-run:

```powershell
Set-Location C:\dev\lisa\android_projects\upload_mobile
.\publish_apk.bat "ПУТЬ_К_APK" --branch lisa --notes "Описание" --dry-run
```

После проверки повторить без `--dry-run`. Для других вариантов заменить branch
на `belka` или `debug`. Для Lisa Debug передаётся APK из
`app\build\outputs\apk\lisa\debug\`, даже если его локальный basename содержит
production version: publisher читает реальную версию из APK и переименовывает
артефакт. Publisher требует SSH deploy-ключ и проверенный host key, не принимает
SSH-пароли и не устанавливает server scripts.

Server scripts устанавливаются отдельно в `/usr/local/bin`, повторно проверяют
APK через `aapt`/`apksigner`, сериализуют операции общей блокировкой ветки и
пишут `manifest.json` последним. Точные пути, rollout и rollback-семантика
описаны в operational contract ниже.

Полный operational contract, rollback и выборочное удаление старых APK хранятся
отдельно от исходников приложения:
`C:\dev\lisa\android_projects\upload_mobile\apk_version_system.md`.
Prune не меняет `manifest.json` / `latest.apk` и не является обновлением
приложения: он только снимает выбранные исторические `releases/<versionCode>/`
и оставляет надгробие `versionCode`.

## Завершение

- выполнить release smoke IDs;
- проверить download/install flow отдельно для Lisa и Belka;
- проверить public manifest, versioned `apkUrl`, `latest.apk` и `releases.json`;
- зафиксировать артефакты и checksums в разрешённом release-хранилище;
- commit/push/tag/publish — только по явной команде пользователя.


## Debug companion и каталог

При выпуске изменений хранилища сначала закрыть library/app dependency chain, затем публиковать Debug с export Activity: companion отвергает старый APK без exporter, даже при валидном manifest. Сама реализация не разрешает публикацию или bump. Проверить permission-return и отмену установки, неизменность данных и отдельное подтверждение переноса. См. [контракт](../architecture/shared-underlays.md).

## Gate для native правил проекта

При первом выпуске с project scripts проверить pinned NDK/CMake, четыре ABI,
16 KiB ELF alignment, отдельный UID, Unicode, timeout/OOM и фактический form Save.
Обязателен closure maplib → maplibui → app с fetched merge-commit pins; до него
не собирать/не публиковать release APK и не давать команды публикации.
[Архитектура](../architecture/project-scripts.md),
[текущая проверка](../reference/project-scripts-verification.md).

## Обязательная автоматическая приёмка перед публикацией

После закрытия app/library зависимостей и последнего изменения версии/кода
выполнить `tools\verify-mobile-tests.ps1 -Scope Full` на смартфоне и эмуляторе,
затем `-Scope Check-Publication` непосредственно перед publisher dry-run.
Related нужен после обычной доработки на эмуляторе и не заменяет эту приёмку.
Failure, skip required case, неполный отчёт, отсутствие телефона или изменение
source fingerprint блокируют публикацию. При отсутствии разрешённого проекта
1403 сначала спросить пользователя о другом тестовом проекте.
Порядок dependency closure и отдельное разрешение на публикацию сохраняются.
Подробности: [автотесты](automated-tests.md).

После добавления GlitchTip regression cases 2026-10-17 новый Full catalogue
содержит 207 native cases. Related bugreports и unit checks не заменяют Full и
Check-Publication перед выпуском. Порядок поставки: maplib Merge Commit → fetch
library merge commit и root pin → app Squash → согласованный выпуск APK.
