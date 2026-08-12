---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-07-30
related_code:
  - maplib/src/main/java/com/nextgis/maplib/service/NGWSyncService.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/SyncAdapter.java
  - maplib/src/main/java/com/nextgis/maplib/util/NGWResourceUrl.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/ResourceGroup.java
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/SyncAccountWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/NGWResourceImportHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - maplibui/src/main/res/xml/authenticator.xml
  - app/build.gradle
  - app/src/main/res/xml/syncadapter.xml
---

# NGW sync, локальное хранение и восстановление

## Ответственность

- `maplib` содержит NGW protocol, layer model, sync decisions и локальное GIS
  storage.
- `maplibui.GISApplication` координирует фоновые fill/rebuild/removal операции и
  пользовательские уведомления.
- `app` регистрирует Android sync/service/provider компоненты и показывает
  продуктовый UI.

## Импорт ресурса по URL

`NGWResourceUrl` принимает HTTP(S)-адрес вида `<server-path>/resource/<id>`,
отбрасывает credentials/fragment/лишний path и возвращает нормализованный server
URL, account name и positive remote ID. Это поддерживает как `*.nextgis.com`, так
и self-hosted NGW с портом или path prefix.

`Connection.connect(guest, remoteId)` и `ResourceGroup.loadTargetResource()`
получают только целевой ресурс. URL QGIS vector/raster style разрешается до
родительского слоя. UI сначала использует существующий аккаунт с тем же server
URL; guest account создаётся только после успешного ответа и проверки ресурса.

`NGWResourceImportHelper` отправляет vector в существующий `LayerFillService`, а
raster добавляет через `LayerGroup` и запрашивает стандартный map reload. Наличие
`data.read` обязательно. При отсутствии `data.write` vector сохраняется как
read-only и с направлением sync только server-to-device. Это правило действует и
для обычного ручного выбора NGW-ресурса, если permission payload был загружен.

ID контракта: `INV-NGW-URL-IMPORT`.

## Идентичность Android account

NGW account привязан не только к серверным credentials, но и к системной
регистрации Android. Для каждого build variant три значения обязаны совпадать:

| Consumer | Источник значения |
|---|---|
| `MainApplication.getAccountsType()` | `BuildConfig.nextgismobile_accounts_auth` |
| `AccountAuthenticator` | `@string/nextgis_accounts_auth` |
| `SyncAdapter` | `@string/nextgis_accounts_auth_type` |

Release ЛИСА/Белка используют `com.nextgis.account.geonical`, debug —
`com.nextgis.account.debug`. GIS provider аналогично должен совпадать между
`BuildConfig.providerAuth`, manifest provider и `SyncAdapter.contentAuthority`.
Library defaults нельзя считать достаточными: app variant обязан перекрывать оба
account resource keys. Иначе HTTP-аутентификация проходит, но Android отклоняет
`addAccountExplicitly()` как аккаунт незарегистрированного типа.

Экран входа закрывается только после фактического создания и повторного чтения
account. Отказ Android оставляет форму открытой, показывает отдельную ошибку и
пишет в HyperLog имя сервера и account type без логина, пароля или token.

ID контракта: `INV-NGW-ACCOUNT-IDENTITY`.

## Безопасная мутация данных

Для editable NGW vector layers действует fail-closed gate
`INV-BACKUP-BEFORE-DESTRUCTION`: без успешного backup разрушительная операция
не выполняется.

Триггеры backup:

- remote sync delete/overwrite атрибутов, геометрии или вложений
  (`NGWVectorLayer.getChangesFromServer` → selective feature ZIP);
- schema rebuild / Collector layer removal (полный ZIP слоя, если остались
  несинхронизированные правки или удаление из проекта);
- ручное удаление editable слоя из списка слоёв;
- ручное удаление объекта(ов) — backup сразу при delete, пока строка ещё в БД.

Backup содержит данные слоя (features/changes/attachments + файлы вложений) и
manifest, но не заменяет серверную синхронизацию и не делает auto-restore.
Пользователь может экспортировать или удалить backups через app UI.

Файлы вложений в ZIP обязательны: сначала берутся локальные
`layerPath/{featureId}/{attachId}`, иначе скачиваются с NGW по meta из
`FeatureAttachments`. Если у объекта есть вложение, а байты файла сохранить
нельзя — backup fail-closed, разрушительная операция отменяется, пользователю
показывается alert с конкретной причиной.

Квота каталога `LayerBackups/` задаётся preference `layer_backup_max_gb`
(по умолчанию 5 ГБ, настройки Общие → Другое). При превышении удаляются самые
старые ZIP.

Non-editable Collector слои и raster-style tile cache не требуют data backup.

## Вложения после push

После успешной загрузки вложения на сервер локальный файл **не удаляется**:
`setNewAttachId` переименовывает его под server attach id, и сразу пишется
строка в `FeatureAttachments`. Идентификация/галерея видит фото уже после
первой sync (offline + online без дубля одного id). Раньше файл удалялся без
online-meta, поэтому UI «оживал» только после второго pull — это не замена
текущему контракту.

ID контракта: `INV-BACKUP-BEFORE-DESTRUCTION`.

## Изменение sync

Планировщик хранит период каждого account отдельно, восстанавливает удалённые
Android `PeriodicSync` registrations при старте и поддерживает интервалы короче
15 минут через самоперезапускаемую WorkManager-задачу. Включение sync из любого
экрана одновременно включает account и ставит ближайший запуск; отключение
отменяет его unique work.

Ручная синхронизация нескольких account остаётся последовательной ради одной
карты и SQLite, но активный Collector account выполняется первым. Для каждого
account создаются отдельные adapter/result objects, поэтому ошибка одного не
переходит в следующий. Полностью молчащее HTTP-чтение ограничено тремя минутами;
это inactivity timeout и не обрывает большой ответ, пока данные продолжают
поступать.

Process-wide признак активности обновляет сам `SyncAdapter` перед `SYNC_START`
и во всех normal/cancel/exception finish-путях. Broadcast остаётся событием для
UI, но не является единственным владельцем состояния: receiver фрагмента может
быть снят во время lifecycle-перехода. Пока анимация sync-кнопки запущена,
`LayersFragment` периодически сверяет её с `NGWSyncService.isSyncStarted()` и
останавливает устаревший spinner после фактического завершения адаптера.

Получение изменений векторного слоя сначала делает до трёх коротких HTTP-попыток.
Если последняя попытка завершилась временной сетевой ошибкой, HTTP `408`/`429`/`5xx`
или NGW `ExternalDatabaseError`, адаптер не останавливает проход и не повторяет уже
успешные слои: проблемный слой ставится в отдельную очередь. После завершения
основного прохода и не ранее чем через 15 секунд после последнего такого сбоя
адаптер повторяет только отложенные слои, ещё максимум по три HTTP-попытки.
Ошибка первого прохода не попадает в итоговый `SyncResult`, если повторный проход
успешен. Если он тоже исчерпан, результат содержит обычную IO-ошибку и отдельное
пользовательское сообщение о временной недоступности сервера или внешней базы.
Отправка локальных изменений и обновление времени успешной синхронизации не
выполняются, пока pull слоя не завершился успешно.

Проверить отдельно:

- pull, push и конфликты;
- sync-enabled и `SYNC_NONE`;
- повторный запуск после process death/account drift;
- корректность last-sync UI только после успешного результата;
- остановку sync spinner после normal, cancel и exception finish, включая
  уход/возврат в layer drawer во время синхронизации;
- post-push refresh и сохранение локальных данных;
- foreground-service требования Android 14+.

Связанные tests: `NgwPullDecisionTest`, `NGWUtilFeaturesUrlTest`,
`NgwResmetaUtilTest`, `LayerConfigUtilTest`, `NgwSyncRetryPolicyTest`,
`NetworkUtilTransientNgwTest`.
