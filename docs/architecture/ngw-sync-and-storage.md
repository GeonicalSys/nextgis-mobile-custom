---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/service/NGWSyncService.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/SyncAdapter.java
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
---

# NGW sync, локальное хранение и восстановление

## Ответственность

- `maplib` содержит NGW protocol, layer model, sync decisions и локальное GIS
  storage.
- `maplibui.GISApplication` координирует фоновые fill/rebuild/removal операции и
  пользовательские уведомления.
- `app` регистрирует Android sync/service/provider компоненты и показывает
  продуктовый UI.

## Безопасная мутация данных

Для schema mismatch или удаления слоя сначала попытаться отправить допустимые
локальные изменения, затем создать backup, и только после успеха выполнять
разрушительную операцию. Неудачный backup — это stop condition, а не warning.

Backup содержит данные слоя и manifest, но не заменяет серверную синхронизацию.
Пользователь может экспортировать или удалить сохранённые backups через app UI.

## Изменение sync

Проверить отдельно:

- pull, push и конфликты;
- sync-enabled и `SYNC_NONE`;
- повторный запуск после process death/account drift;
- корректность last-sync UI только после успешного результата;
- post-push refresh и сохранение локальных данных;
- foreground-service требования Android 14+.

Связанные tests: `NgwPullDecisionTest`, `NGWUtilFeaturesUrlTest`,
`NgwResmetaUtilTest`, `LayerConfigUtilTest`.
