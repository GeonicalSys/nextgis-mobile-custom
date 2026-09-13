---
title: Инцидент и безопасный rollback
type: runbook
last_verified: 2026-08-27
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
---

# Инцидент и безопасный rollback

## Сначала сохранить данные

1. Остановить повторяющуюся destructive/sync операцию.
2. Зафиксировать flavor/version, Android/device, профиль и точные шаги.
3. Сохранить logcat/Sentry event ID без credentials и персональных данных.
4. При риске данных экспортировать `LayerBackups` через штатный UI.
   Каталог ограничен `layer_backup_max_gb` (default 5 ГБ); при переполнении
   удаляются самые старые ZIP, поэтому экспорт делать до очистки.
   ZIP содержит все таблицы слоя и только те файлы вложений, которые
   физически находились на этом устройстве; server-only payload нужно получать из NGW.
5. Не очищать app data и не переустанавливать приложение до копирования нужных
   локальных данных.

## Классификация

- crash/lifecycle;
- rendering/order без потери данных;
- sync/account drift;
- schema/composition destructive path;
- release/update/signature;
- regression после upstream merge.

## Rollback

Rollback APK допустим только после проверки совместимости storage/schema и
version downgrade. Git rollback не выполняется destructive reset: подготовить
обычный revert/fix в правильном репозитории, проверить совместимые submodule
pointers и обе flavors.

После инцидента новый подтверждённый риск или invariant обновляется в central
docs/registry в той же задаче.
