---
title: Flavors и версионирование форка
type: reference
last_verified: 2026-07-20
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - app/src/main/AndroidManifest.xml
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

## Variant identity

| Variants | Application ID | GIS provider authority | NGW account type |
|---|---|---|---|
| `lisaRelease`, `belkaRelease` | `com.nextgis.mobile.geonical` | `com.nextgis.mobile.geonical.provider` | `com.nextgis.account.geonical` |
| `lisaDebug` | `com.nextgis.mobile.debug` | `com.nextgis.mobile.provider.debug` | `com.nextgis.account.debug` |

Account type — единый контракт runtime/authenticator/sync adapter. При изменении
`applicationIdSuffix` необходимо проверить также provider authority, FileProvider,
service permission, updater identity и оба account resource keys в merged APK.

## Версия

- `versionName = <upstream-base>.<fork-patch>`.
- `versionCode` увеличивается для каждого публикуемого APK.
- `maplib` BuildConfig version синхронизируется с app, если контракт использует
  её для протокола, диагностики или совместимости.
- Обе flavors одного релиза должны иметь согласованную версию.

## Update flavor

Flavor передаётся updater через manifest metadata
`com.nextgis.mobile.UPDATE_FLAVOR` и сверяется с update manifest и APK archive.
Нельзя разрешать установку APK другого бренда через автоматическое обновление.

Repository branches: Lisa Release — `lisa`, Belka Release — `belka`, Lisa Debug
— `debug`. Для production manifest содержит `channel=stable`, для Debug —
`channel=debug`. URL manifest имеет вид
`https://apps-geonical.ru/lisa-mobile/<branch>/manifest.json` без отдельного
сегмента `stable`.
