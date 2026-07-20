---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-07-20
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

Для schema mismatch или удаления слоя сначала попытаться отправить допустимые
локальные изменения, затем создать backup, и только после успеха выполнять
разрушительную операцию. Неудачный backup — это stop condition, а не warning.

Backup содержит данные слоя и manifest, но не заменяет серверную синхронизацию.
Пользователь может экспортировать или удалить сохранённые backups через app UI.

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

Проверить отдельно:

- pull, push и конфликты;
- sync-enabled и `SYNC_NONE`;
- повторный запуск после process death/account drift;
- корректность last-sync UI только после успешного результата;
- post-push refresh и сохранение локальных данных;
- foreground-service требования Android 14+.

Связанные tests: `NgwPullDecisionTest`, `NGWUtilFeaturesUrlTest`,
`NgwResmetaUtilTest`, `LayerConfigUtilTest`.
