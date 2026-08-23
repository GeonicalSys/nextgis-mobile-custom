---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-08-23
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/service/NGWSyncService.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/SyncAdapter.java
  - maplib/src/main/java/com/nextgis/maplib/util/NGWResourceUrl.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/ResourceGroup.java
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/SyncAccountWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/NGWResourceImportHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ProjectOperationCoordinator.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SchemaRebuildRetryGuard.java
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
Выпуск `3.1.2.12` использует production tuple `206` / `3.1.2.12`, а отдельный
debug остаётся `203` / `3.1.2.9`; application/account/provider identity не
меняется.
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
- удаление локальной копии активного проекта — full backup каждого editable
  NGW-слоя, в котором остались несинхронизированные изменения.

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

Открытие или перелистывание свойств NGW-слоя без пользовательского изменения
не записывает новое направление синхронизации. Начальный callback списка
направлений является no-op. Для project-managed слоя доступность этого списка
определяется Collector editable policy, поэтому текущий server-only режим или
generic mobile `is_editable=false` не блокируют возврат в двусторонний режим.

Планировщик хранит период каждого account отдельно, восстанавливает удалённые
Android `PeriodicSync` registrations при старте и поддерживает интервалы короче
15 минут через самоперезапускаемую WorkManager-задачу. Включение sync из любого
экрана одновременно включает account и ставит ближайший запуск; отключение
отменяет его unique work.

Ручная синхронизация работает только с текущей `IGISApplication.getMap()`, то
есть с активным проектом. Она выбирает Android account, для которых в этой карте
есть NGW-слои, и выполняет их последовательно; закрытые проекты не обходятся.
Активный Collector account выполняется первым. Для каждого account создаются
отдельные adapter/result objects, поэтому ошибка одного не переходит в следующий.
Полностью молчащее HTTP-чтение ограничено тремя минутами; это inactivity timeout
и не обрывает большой ответ, пока данные продолжают поступать.

`ProjectOperationCoordinator` резервирует active workspace ещё при нажатии
ручной sync и удерживает lease до конца всех account, включая промежутки между
ними. Поэтому project switch/create/rename/delete не может попасть в окно между
двумя адаптерами. Периодический adapter также получает lease; второй полный sync
того же workspace отклоняется. Layer fill и schema rebuild могут заранее
зарезервировать только тот же workspace, но доступ к SQLite получают строго после
завершения full sync; это не даёт синхронизации и перезаливке писать в одну БД
одновременно и одновременно закрывает окно для переключения проекта.

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

### Инкрементальный pull и пространственный индекс

Инкрементальный pull одного `NGWVectorLayer` является одной bulk-операцией.
Вставки, изменения и удаления продолжают выполняться в SQLite с обычной
проверкой backup/change-table, но не отправляют отдельный Android broadcast на
каждую строку. После успешного применения всех серверных изменений слой один раз
перестраивает R-tree из итоговой SQLite и публикует один reload карты.

Публичные операции `GeometryRTree` сериализованы. `VectorLayer.notifyInsert`,
`notifyUpdate` и `notifyDelete` не изменяют индекс во время bulk/rebuild, а
receiver дополнительно перехватывает и логирует локальный cache callback failure,
чтобы исключение из `BroadcastReceiver.onReceive()` не завершало процесс.
Незавершённый `GeoEnvelope` имеет нулевые dimensions/area и не разыменовывает
`null` при защитной проверке. Это закрывает гонку, когда sync-worker выполнял
`tighten()`/`rebuildCache()`, а main thread одновременно обрабатывал сотни
`notify_insert`.

MapLibre style refresh использует независимые объекты `Feature`, загруженные из
geometry render cache, и выполняется в общей последовательной очереди vector
reload. Live `sourceFeaturesHashMap` на worker-потоке не мутируется; при cache
miss запускается полный data reload. Поэтому Gson `LinkedTreeMap` свойств одного
`Feature` не изменяется одновременно main и worker потоками.

ID контракта: `INV-SPATIAL-CACHE-CONSISTENCY`.

## Несовпадение схемы и тяжёлый rebuild

`NGWVectorLayer` передаёт приложению fingerprint причины mismatch: отсутствующая
таблица, hash server metadata/config либо hash SQLite-ошибки. Guard хранится по
`workspace + account + remote_id` и допускает для неизменного fingerprint не
более двух rebuild-попыток за 24 часа с интервалом не менее 10 минут. Изменившийся
fingerprint или истёкшее окно разрешает новую попытку. Число остановленных
слоёв и явный сброс guard доступны в «Настройки → Проект».

Rebuild является staged replacement. Старый слой и его SQLite остаются в карте,
пока новая копия полностью не загружена в отдельный каталог и не сохранена в
`LayerGroup`. Только после этого старая копия удаляется. Ошибка fill удаляет
только stage, поэтому сломанный server config не превращает рабочий локальный
слой в потерю данных. Первый неуспешный SQLite insert завершает fill и откатывает
транзакцию вместо повторения всех следующих записей.

Полный fill разбирает WKT `MULTIPOLYGON` с учётом вложенности скобок, поэтому
внутренние кольца и следующие polygon members сохраняются. При первом сбое
чтения или записи HyperLog получает production-safe запись `NGW feature fill
failed`: имя слоя, remote id, нулевой индекс элемента исходного массива,
класс/ограниченное сообщение ошибки и ограниченный стек. Геометрия, значения
полей и credentials не журналируются; объект не пропускается, транзакция слоя
откатывается и staged replacement не подменяет рабочую копию.

`LayerFillService` возвращает `START_NOT_STICKY`: пустой/null redelivery не
создаёт бесконечный foreground service. На Android 15+ `onTimeout()` очищает
текущую очередь и останавливает FGS, но сохраняет durable Collector journal,
чтобы следующий запуск мог проверить и докачать партию.

Проверить отдельно:

- pull, push и конфликты;
- sync-enabled и `SYNC_NONE`;
- повторный запуск после process death/account drift;
- корректность last-sync UI только после успешного результата;
- остановку sync spinner после normal, cancel и exception finish, включая
  уход/возврат в layer drawer во время синхронизации;
- post-push refresh и сохранение локальных данных;
- foreground-service требования Android 14+.
- отказ от project switch во время всей ручной sync и layer fill;
- staged schema rebuild, лимит неизменного fingerprint и ручной reset guard;
- массовый incremental pull с одной итоговой R-tree rebuild, без построчных
  notify и без `LinkedTreeMap` style errors;
- null-intent/system-timeout `LayerFillService` без
  `ForegroundServiceDidNotStopInTimeException`, с сохранённым import journal.

Связанные tests: `NgwPullDecisionTest`, `NGWUtilFeaturesUrlTest`,
`NgwResmetaUtilTest`, `LayerConfigUtilTest`, `NgwSyncRetryPolicyTest`,
`NetworkUtilTransientNgwTest`.
