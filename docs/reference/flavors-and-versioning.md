---
title: Flavors и версионирование форка
type: reference
last_verified: 2026-07-19
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
