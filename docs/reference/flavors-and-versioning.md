---
title: Flavors и версионирование форка
type: reference
last_verified: 2026-09-13
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - app/src/main/AndroidManifest.xml
  - app/src/lisa/res
  - app/src/belka/res
---

# Flavors и версионирование форка

## Product flavors

| Flavor | Product name | Назначение |
|---|---|---|
| `lisa` | NextGIS ЛИСА | Основной бренд форка |
| `belka` | NextGIS Белка | Второй бренд с отдельными brand resources |

`APP_NAME`/`app_name` принадлежат product flavor. Release build type не должен
перезаписывать их одним общим значением. Debug может иметь отдельное тестовое
имя и application ID suffix.

`app_launcher_icon` также разрешается на уровне flavor и используется manifest,
intro и about. Lisa ссылается на `ic_launcher_lisa`, Belka — на собственный
`ic_launcher_belka`; для обеих иконок поддерживаются `mdpi`, `hdpi`, `xhdpi`,
`xxhdpi` и `xxxhdpi`. Artwork Belka живёт только в `app/src/belka/res/drawable-*`
и не должна попадать в Lisa. Общая `app/src/main`-иконка не должна подменять
branding release-варианта.

## Variant identity

| Variants | Application ID | GIS provider authority | NGW account type |
|---|---|---|---|
| `lisaRelease`, `belkaRelease` | `com.nextgis.mobile.geonical` | `com.nextgis.mobile.geonical.provider` | `com.nextgis.account.geonical` |
| `lisaDebug` | `com.nextgis.mobile.debug` | `com.nextgis.mobile.provider.debug` | `com.nextgis.account.debug` |

Account type — единый контракт runtime/authenticator/sync adapter. При изменении
`applicationIdSuffix` необходимо проверить также provider authority, FileProvider,
service permission, updater identity и оба account resource keys в merged APK.
Durable sync-recovery journal хранит account name и active workspace, но account
type и authority всегда получает через текущий `IGISApplication`; новых variant
identity или меж-flavor данных он не вводит.

Разные application ID образуют разные Android sandboxes. Для одноразового
переноса старых локальных подложек `lisaDebug` публикует отдельный read-only
content bridge, а production Geonical вызывает его явным intent. Обе стороны
сверяют точное имя пакета и SHA-256 signing certificate; grant действует только
на выбранные content URI и не открывает весь каталог приложения. Публичные
fingerprints допустимы в коде проверки, private signing key — нет. Старый Debug
можно обновить bridge-сборкой только APK, подписанным тем же Debug-сертификатом;
production APK не может обновить пакет Debug и не получает его private files
напрямую.

## Версия

- Подготовленный выпуск: Lisa/Belka Release `3.1.2.18` / `versionCode 212`,
  Lisa Debug использует `3.1.2.18` / `versionCode 213`; maplib сообщает соответствующее
  variant-specific значение.
- `versionName = <upstream-base>.<fork-patch>`.
- `versionCode` увеличивается для каждого публикуемого APK.
- Production constants приложения находятся в `defaultConfig`; debug-only
  constants применяются к `lisaDebug` через публичный
  `androidComponents.onVariants` API.
- В AGP `9.1.0` `versionCode`/`versionName` нельзя задавать в application
  `buildTypes`. Для maplib debug не задаётся library `versionName`: меняется
  только явный `BuildConfig.VERSION_NAME`.
- `maplib` BuildConfig version синхронизируется с app для каждого variant,
  поскольку используется в user-agent/диагностике.
- Обе flavors одного релиза должны иметь согласованную версию.
- Общая зависимость `maplib` от JTS Core `1.20.0` входит во все варианты
  одинаково; исправление мультиполигонов не является flavor-specific feature.
- Debug-only bump обязан оставить обе production release metadata без
  изменений. Проверка — `tools\verify-apk-version-matrix.ps1` из root.

Имя локального APK не является version contract: общий
`base.archivesName` основан на production default и может дать debug APK
basename с production version. `output-metadata.json`, `aapt dump badging` и
publisher являются источниками истины; publisher формирует каноническое имя по
фактической metadata.

## Update flavor

Flavor передаётся updater через manifest metadata
`com.nextgis.mobile.UPDATE_FLAVOR` и сверяется с update manifest и APK archive.
Нельзя разрешать установку APK другого бренда через автоматическое обновление.

Repository branches: Lisa Release — `lisa`, Belka Release — `belka`, Lisa Debug
— `debug`. Для production manifest содержит `channel=stable`, для Debug —
`channel=debug`. URL manifest имеет вид
`https://apps-geonical.ru/lisa-mobile/<branch>/manifest.json` без отдельного
сегмента `stable`.


## Отдельный установщик Debug

Production Geonical может обновить доверенный установленный Debug перед переносом подложек. DebugCompanionInstaller не использует flavor self-updater: разрешены только debug package/channel, pinned/current Debug signer и APK с exporter. Lisa Debug и Belka не показывают это предложение. Версии этой задачей не изменяются; серверный канал и continuation описаны в [контракте](../architecture/shared-underlays.md).
