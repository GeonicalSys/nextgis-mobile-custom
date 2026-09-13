---
title: Выпуск Lisa и Belka APK
type: runbook
last_verified: 2026-09-13
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/AndroidManifest.xml
  - app/src/lisa/res/values/launcher_icon.xml
  - app/src/belka/res/values/launcher_icon.xml
---

# Выпуск Lisa и Belka APK

## Версия

Проверяемый выпуск `3.1.2.18`: production `versionCode 212`, Lisa Debug —
`3.1.2.18` / `versionCode 213`; диагностический release
`maplib.VERSION_NAME 3.1.2.18`, debug — `3.1.2.18`.

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
- Sentry manifest policy: interaction breadcrumbs и view hierarchy выключены,
  crash screenshot включён, trace/profile sample rate равен `0.05`;
- JTS Core присутствует в обеих release-сборках, а
  `MultiPolygonGeometryRepairTest` проходит в `:maplib:testDebugUnitTest`;
- runtime dependency graph разрешает `org.maplibre.gl:android-sdk-opengl:13.0.2`
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
4. Запустить перенос из настроек проекта, проверить подложки после cold start и
   повторить команду для проверки отсутствия дубликатов.
5. Не удалять Debug и его данные, пока пользователь не подтвердил полноту
   результата. Сам bridge ничего не удаляет.

Release smoke должен включать trusted-pair и negative-пару: APK с другим
package/certificate не должен получить URI или поток подложки.

## Self-hosted update manifest

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

Полный operational contract и rollback хранится отдельно от исходников
приложения: `C:\dev\lisa\android_projects\upload_mobile\apk_version_system.md`.

## Завершение

- выполнить release smoke IDs;
- проверить download/install flow отдельно для Lisa и Belka;
- проверить public manifest, versioned `apkUrl`, `latest.apk` и `releases.json`;
- зафиксировать артефакты и checksums в разрешённом release-хранилище;
- commit/push/tag/publish — только по явной команде пользователя.


## Debug companion и каталог

При выпуске изменений хранилища сначала закрыть library/app dependency chain, затем публиковать Debug с export Activity: companion отвергает старый APK без exporter, даже при валидном manifest. Сама реализация не разрешает публикацию или bump. Проверить permission-return и отмену установки, неизменность данных и отдельное подтверждение переноса. См. [контракт](../architecture/shared-underlays.md).
