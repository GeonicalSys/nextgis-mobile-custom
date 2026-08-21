---
title: Flavors и версионирование форка
type: reference
last_verified: 2026-08-21
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

## Версия

- Текущий выпуск: Lisa/Belka Release `3.1.2.10` / `versionCode 204`, Lisa Debug
  остаётся `3.1.2.9` / `versionCode 203`; maplib сообщает соответствующее
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
