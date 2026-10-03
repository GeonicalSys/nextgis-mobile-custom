---
title: Инцидент и безопасный rollback
type: runbook
last_verified: 2026-10-03
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

## LayerBackup format2: восстановление только на копии

1. Экспортировать ZIP до quota cleanup. Скопировать текущий project workspace,
   layers.db вместе с WAL/SHM и layer directory после остановки всех writers.
   Сохранить исходную копию отдельно; не работать SQL по живому приложению.
2. Проверить ZIP CRC, manifest format/version, map_path, layer_path, account/remote
   identity и selective/feature_ids. Не применять архив чужой карты или слоя.
3. На отдельной recovery copy существующего совместимого проекта администратор
   сверяет schema с columns из tables/*.json. Rows содержат обычные JSON values;
   BLOB представлен объектом type=blob/base64. features table обязателен,
   exists=false для optional table не означает удаление текущих данных.
4. В одной SQLite транзакции импортировать согласованные rows только в таблицы
   соответствующего слоя; selective backup касается перечисленных feature ids.
   Не отправлять восстановленный changes outbox на сервер до ручной сверки remote
   ids и текущего состояния NGW. Из attachments/{featureId}/ восстановить payload
   и local meta в папку того же feature; проверить bytes и соответствие metadata.
5. Проверить integrity_check, количества/id/geometry/attachment bytes, открыть
   изолированную копию offline и сравнить объекты/фотографии. Только затем решать
   перенос в рабочий проект и server reconciliation.

ZIP не содержит полного schema/config/forms/assets проекта и не является готовым
архивом автоматического восстановления. Универсального import/restore UI или
скрипта в этом изменении нет. Native suite проверила чтение backup rows/photo и
уникальность ZIP; описанная ручная процедура целиком ещё не выполнена. Черновики
форм, walkedit_temp и pending-track-points дополнительно сохраняются из workspace,
если они нужны для инцидента; повреждённые checkpoint не удаляются молча.
