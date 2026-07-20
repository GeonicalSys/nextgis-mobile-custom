---
title: Выпуск Lisa и Belka APK
type: runbook
last_verified: 2026-07-20
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/AndroidManifest.xml
---

# Выпуск Lisa и Belka APK

## Версия

1. Определить upstream base и следующий fork patch.
2. Увеличить `app` `versionCode` монотонно.
3. Обновить `app` `versionName` и сопряжённый `maplib` `versionName`.
4. Обновить `WHATS_NEW.md` и соответствующий architecture/reference/runbook.
   Если появился новый долговременный класс отличий, дополнить
   [`../reference/fork-customizations.md`](../reference/fork-customizations.md).

## Сборка

```powershell
.\gradlew.bat :app:assembleLisaRelease :app:assembleBelkaRelease
```

Проверить для каждого APK:

- display name и applicationId;
- embedded `UPDATE_FLAVOR` совпадает с flavor;
- versionCode/versionName;
- подпись ожидаемым release certificate;
- Sentry manifest policy: interaction breadcrumbs и view hierarchy выключены,
  crash screenshot включён, trace/profile sample rate равен `0.05`;
- запуск поверх существующего профиля.

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

Значения signing keys/cert private data в docs не публикуются. Допустим только
публичный fingerprint в защищённой release-инфраструктуре.

## Публикация

Publisher находится в `Q:\android_projects\upload_mobile`. Он принимает явный путь к
APK и извлекает package/flavor/version/certificate из APK; ручной manifest не
является входом.

Сначала обязательный dry-run:

```powershell
Set-Location Q:\android_projects\upload_mobile
.\publish_apk.bat "ПУТЬ_К_APK" --branch lisa --notes "Описание" --dry-run
```

После проверки повторить без `--dry-run`. Для других вариантов заменить branch
на `belka` или `debug`. Publisher требует SSH deploy-ключ и проверенный host key,
не принимает SSH-пароли и не устанавливает server scripts.

Server scripts устанавливаются отдельно в `/usr/local/bin`, повторно проверяют
APK через `aapt`/`apksigner`, сериализуют операции общей блокировкой ветки и
пишут `manifest.json` последним. Точные пути, rollout и rollback-семантика
описаны в operational contract ниже.

Полный operational contract и rollback хранится отдельно от исходников
приложения: `Q:\android_projects\upload_mobile\apk_version_system.md`.

## Завершение

- выполнить release smoke IDs;
- проверить download/install flow отдельно для Lisa и Belka;
- проверить public manifest, versioned `apkUrl`, `latest.apk` и `releases.json`;
- зафиксировать артефакты и checksums в разрешённом release-хранилище;
- commit/push/tag/publish — только по явной команде пользователя.
