---
title: Выпуск Lisa и Belka APK
type: runbook
last_verified: 2026-07-19
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
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

Manifest содержит `applicationId`, `flavor`, `versionCode`, `versionName`,
`apkUrl`, размер APK, SHA-256 APK, SHA-256 signing certificate и release notes.
Updater должен отклонить неверный flavor, application ID, размер, hash,
certificate или неувеличившийся versionCode.

Значения signing keys/cert private data в docs не публикуются. Допустим только
публичный fingerprint в защищённой release-инфраструктуре.

## Завершение

- выполнить release smoke IDs;
- проверить download/install flow отдельно для Lisa и Belka;
- зафиксировать артефакты и checksums в разрешённом release-хранилище;
- commit/push/tag/publish — только по явной команде пользователя.
