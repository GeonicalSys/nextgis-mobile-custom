---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-08-27
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapContentProviderHelper.java
  - maplib/src/main/java/com/nextgis/maplib/map/Table.java
  - maplib/src/main/java/com/nextgis/maplib/util/DatabaseContext.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureGeometryValidator.java
  - maplib/src/main/java/com/nextgis/maplib/service/NGWSyncService.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/SyncAdapter.java
  - maplib/src/main/java/com/nextgis/maplib/util/NGWResourceUrl.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/ResourceGroup.java
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/SyncAccountWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/NGWResourceImportHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerFillStaging.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ProjectOperationCoordinator.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SchemaRebuildRetryGuard.java
  - maplibui/src/main/res/xml/authenticator.xml
  - app/build.gradle
  - app/src/main/java/com/nextgis/mobile/datasource/SyncAdapter.java
  - app/src/main/java/com/nextgis/mobile/datasource/SyncService.java
  - app/src/main/java/com/nextgis/mobile/util/OfflineSyncIntentService.java
  - app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
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
Выпуск `3.1.2.18` использует production tuple `212` / `3.1.2.18`, а отдельный
debug — `213` / `3.1.2.18`; application/account/provider identity не
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

- remote sync delete/overwrite атрибутов или геометрии
  (`NGWVectorLayer.getChangesFromServer` → selective feature ZIP);
- schema rebuild / Collector layer removal (полный ZIP слоя, если остались
  несинхронизированные правки или удаление из проекта);
- ручное удаление editable слоя из списка слоёв;
- ручное удаление объекта(ов) — backup сразу при delete, пока строка ещё в БД.
- удаление локальной копии активного проекта — full backup каждого editable
  NGW-слоя, в котором остались несинхронизированные изменения.

Backup содержит данные слоя (features/changes/attachments), фактически
хранящиеся на устройстве файлы вложений и manifest, но не заменяет
серверную синхронизацию и не делает auto-restore.
Пользователь может экспортировать или удалить backups через app UI.

В ZIP попадают только локальные `layerPath/{featureId}/` и их файлы. Строки
`FeatureAttachments` сохраняются в `tables/attachments.json`, но отсутствующий
локально payload не скачивается с NGW и не блокирует разрушительную операцию.
Ошибка чтения фактически имеющегося локального файла или ошибка записи ZIP
по-прежнему закрывает gate: локальные данные остаются без изменений, а пользователь
получает alert с причиной.

Обычный NGW pull получает метаданные серверных вложений, но не обязан скачивать
их байты в каталог слоя. Metadata-only refresh не удаляет геометрию, атрибуты
или локальные файлы и потому не является триггером backup. Серверные метаданные
сверяются с `FeatureAttachments`, а не с необязательным локальным `META`, и
пишутся туда также при первом создании feature. Размер файла в это сравнение не
входит. Удаление feature целиком остаётся разрушительной операцией и по-прежнему
проходит обязательный backup со всеми доступными байтами вложений.

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

Перед feature sync приложение выполняет repair активного проекта. Managed NGW
layers группируются по `account + project_uid + remote_id`. Если одна identity
представлена несколькими копиями без локальных правок и вложений, выбирается
полная опубликованная таблица, остальные копии обязательно архивируются,
удаляются из композиции одним сохранением карты и лишь затем физически очищаются.
Если хотя бы в одной копии есть несинхронизированные данные либо backup не
создан, sync прекращается с обычным понятным сообщением и ничего не удаляет.

Начатый account-pass отмечается app-private durable journal. Чистое завершение
снимает marker; process death оставляет его, и следующий запуск запрашивает один
идемпотентный проход только для того же account и active workspace. Ручной и
системный sync работают как `dataSync` foreground service на тяжёлой части
прохода. Это повышает вероятность завершения при screen off, но не заменяет
транзакции, backup gate и journal.
Завершение bound `NGWSyncService` не ждёт worker на Android main thread:
незавершённый проход фиксируется journal и повторяется после запуска, вместо
прежнего блокирующего ожидания, которое само могло вызвать ANR.

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
Локальные изменения одного слоя отправляются до большого remote pull. Если push
не завершён, pull этого слоя не начинается. Время успешной синхронизации
обновляется только после чистого завершения всего account-pass.

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

Полный untracked snapshot не материализуется целиком в Java heap. HTTP body
пишется во временный app-owned JSON рядом со слоем, первым потоковым проходом
собираются только remote IDs и план backup/delete, вторым — по одному feature
применяется одна SQLite-транзакция. После commit выполняются одна cache rebuild
и один отложенный MapLibre reload. Объект с отсутствующей или невалидной
геометрией не прерывает слой: его remote ID остаётся в snapshot identity, чтобы
не удалить прежнюю локальную копию, сам объект не применяется, а остальные
features продолжают транзакцию. HyperLog записывает ограниченное число ID и
индексов таких объектов без координат и атрибутов. При parse/IO/SQLite/OOM транзакция
откатывается, marker синхронизации остаётся для повтора, а временный файл
удаляется. Выключенный слой не строит полный GeoJSON snapshot до включения.

## Несовпадение схемы и тяжёлый rebuild

Сверка схемы трёхсторонняя: authoritative NGW resource metadata определяет
`resource.cls`, geometry и поля; локальный `config.json` является repairable
metadata; SQLite `PRAGMA table_info` подтверждает физические имена и affinities.
Если NGW и SQLite уже совпадают, а serialized fields устарели (например, нет
`idqgs`) либо vector/PostGIS class записан неверно, исправляется только metadata
без refill. Geometry или физическая таблица, несовместимые с authoritative NGW,
запускают staged rebuild. При импорте мобильный config не может перезаписать уже
проверенные NGW class/geometry/fields. Отсутствующий `ngw_layer_type` в legacy
config сначала остаётся неизвестным, затем восстанавливается из NGW metadata, а
не ошибочно считается PostGIS.

`NGWVectorLayer` передаёт приложению fingerprint причины mismatch: отсутствующая
таблица, hash server metadata/config либо hash SQLite-ошибки. Guard хранится по
`workspace + account + remote_id` и допускает для неизменного fingerprint не
более двух rebuild-попыток за 24 часа с интервалом не менее 10 минут. Изменившийся
fingerprint или истёкшее окно разрешает новую попытку. Число остановленных
слоёв и явный сброс guard доступны в «Настройки → Проект».

Rebuild является staged replacement. Старый слой и его SQLite остаются в карте,
пока новая копия полностью не загружена в отдельный каталог. На main thread
замена вставляется, все прежние копии той же project identity удаляются и карта
сохраняется ровно один раз; композиция old+new никогда не сохраняется как
промежуточное состояние. Физические данные старых копий удаляются только после
успешного commit. Ошибка fill/commit удаляет только stage и восстанавливает
старую композицию. Первый неуспешный SQLite insert завершает fill и откатывает
транзакцию вместо повторения всех следующих записей.
`Table.save/load` используют `AtomicFile`: process death во время записи
`default.ngm` или layer `config.json` восстанавливает последнюю полную версию,
а не оставляет обрезанный JSON.

Полный fill разбирает WKT `MULTIPOLYGON` с учётом вложенности скобок, поэтому
внутренние кольца и следующие polygon members сохраняются. При первом сбое
чтения или записи HyperLog получает production-safe запись `NGW feature fill
failed`: имя слоя, remote id, нулевой индекс элемента исходного массива,
класс/ограниченное сообщение ошибки и ограниченный стек. Геометрия, значения
полей и credentials не журналируются; объект не пропускается, транзакция слоя
откатывается и staged replacement не подменяет рабочую копию.

Входная топология Polygon и каждого member MultiPolygon проверяется через JTS
`IsValidOp`. Legacy `GeoLinearRing.isValid()` сравнивал пары сегментов и на
контурах с десятками тысяч координат имел квадратичную стоимость, поэтому даже
ответ из 15 features мог выглядеть как бесконечный fill. Member-by-member
проверка сохраняет прежнее отношение к перекрывающимся валидным частям одного
MultiPolygon, но не зависит квадратично от числа вершин.

Все create/insert/schema/delete операции fill используют `layers.db` карты,
владеющей слоем: layer получает parent target group до доступа к SQLite, а путь
БД выводится из map-файла владельца. Project UID из durable journal обязан
совпасть с metadata target group. При несовпадении задача отклоняется до работы с
файлами или БД, а journal сохраняется до открытия нужного workspace.

Каждый новый layer directory помечается `.layer-fill-partial` до загрузки и
снимает marker только после публикации в `LayerGroup`. После process death
убираются лишь помеченные, не referenced stages и их таблицы в БД владельца;
непомеченные legacy directories автоматически не удаляются.

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
- foreground-service требования Android 14+ и повтор account-pass после
  принудительного убийства процесса;
- отказ от project switch во время всей ручной sync и layer fill;
- staged schema rebuild, лимит неизменного fingerprint и ручной reset guard;
- массовый incremental pull с одной итоговой R-tree rebuild, без построчных
  notify и без `LinkedTreeMap` style errors;
- null-intent/system-timeout `LayerFillService` без
  `ForegroundServiceDidNotStopInTimeException`, с сохранённым import journal.
- полный fill малого числа очень больших MultiPolygon без квадратичного зависания;
- process death и открытие другого проекта без таблиц в чужом `layers.db`, с
  очисткой только помеченных unpublished stages после возврата к target UID.
- три копии одной managed layer без локальных изменений: pre-sync repair
  оставляет одну; с локальным change/attachment repair блокируется без удаления;
- расхождение description/config/SQLite по `idqgs` и ошибочный PostGIS class:
  metadata-only случай не скачивает слой, физическое/class расхождение делает
  одну атомарную staged replacement без сохранённой пары old+new;
- полный untracked snapshot под ограничением heap, включая interruption/OOM до
  commit, пропуск отдельных невалидных геометрий с сохранением прежних локальных
  копий и один итоговый reload после успешного повтора.

Связанные tests: `NgwPullDecisionTest`, `NGWUtilFeaturesUrlTest`,
`NgwResmetaUtilTest`, `LayerConfigUtilTest`, `NgwSyncRetryPolicyTest`,
`NetworkUtilTransientNgwTest`.
