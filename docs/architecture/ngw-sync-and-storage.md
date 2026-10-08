---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-10-08
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/GeoMultiPolygon.java
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapContentProviderHelper.java
  - maplib/src/main/java/com/nextgis/maplib/map/Table.java
  - maplib/src/main/java/com/nextgis/maplib/util/DatabaseContext.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureGeometryValidator.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwFeatureCountParser.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwSyncNoneReloadDecision.java
  - maplib/src/main/java/com/nextgis/maplib/service/NGWSyncService.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwSyncIo.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwSyncProgress.java
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
  - app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
  - app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
  - app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java
  - maplib/src/main/java/com/nextgis/maplib/util/SyncWorkspaceSession.java
  - app/src/main/res/xml/syncadapter.xml
---

# NGW sync, локальное хранение и восстановление

## Обязательность полей

Управляемый каскадом legacy `double_combobox` отображается двумя отдельными
полями с подписями. Каждое сохраняет своё имя, required и typed value в общем
Save gate; преобразование не переписывает NGFP, SQLite или outbox.

Источник — штатный feature_layer.fields[].required в настройках поля NGW.
NGWUtil читает его при импорте, Field сохраняет в JSON/Parcel; отсутствие
флага в старой конфигурации означает false. Следующая успешная синхронизация
настроек обновляет флаг и подписи без пересоздания SQLite или сброса объектов,
outbox и вложений. Изменение флага не является изменением физической схемы и
не входит в fingerprint разрушительного rebuild. Offline используется последнее
успешно сохранённое описание слоя.

Стандартная и NGFP-форма используют одну проверку перед локальным insert/update
и обработкой вложений, включая Save через Back. Пустое значение, пробельная
строка и «Нет значения» (краевые пробелы и регистр игнорируются) недопустимы;
«не применимо», числовой ноль и логическое false допустимы. Необязательные
поля сохраняют прежнее поведение. Проверяются все страницы и оба поля
зависимого списка. Обязательность не означает подтверждение автоматически
подставленного корректного значения при каждом новом аудите.

Значение существующего объекта, отсутствующее в форме, проверяется по SQLite
и не перезаписывается. Для нового объекта отсутствие обязательного поля в форме
блокирует Save с сообщением о необходимости исправить форму. Черновик
записывается до проверки и может оставаться незавершённым: ошибка валидации
не теряет геометрию, фотографии и введённые значения.

Новых свойств NGFP, переопределения last, миграции таблиц PostGIS или
изменений QGIS publisher для этого consumer-only расширения не требуется.
Правило относится к пользовательскому Save в обновлённом приложении; старые
APK, прямые обращения к БД/API и ограничения PostgreSQL — отдельные контракты.
Развёртывание: Merge Commit библиотек → root submodule pointers → выпуск APK;
настройки обязательности применяются только к явно выбранным NGW-ресурсам.

Проверка 07.10.2026: 592 JVM-теста трёх модулей и 9 native-тестов обязательных
полей/восстановления/подписи на Android 16, API36 x86_64 прошли. Metadata round-trip
и отсутствие rebuild проверены Robolectric на API26 и API36. Собраны debug APK
и AAR, скомпилированы обе release source sets. Полные production APK и проверка
на физическом телефоне не выполнялись; библиотечные PR должны быть слиты до
release-сборки. Документация прошла validator и 7 тестов инструментов.

При ограничении GPS энергосбережением предупреждение записи не закрывает
сессию и не меняет очередь SQLite: ранее принятые точки сохраняются штатно,
а отсутствие новых измерений образует обычный сегментный разрыв. Политика
и диагностика описаны в [location pipeline](location-pipeline.md).

## Ответственность

Режим трека хранится в `track_recording_mode`, без миграции БД. Пропуски
быстрого движения в режиме «Пешеход» используют существующий
`trackpoints.segment`; очередь принимает только разрешённые режимом точки.
Правила сохранения хвоста и восстановления описаны в
[контракте GPS](location-pipeline.md#режим-записи-трека).

Подтверждённый device ANR 17.09: GPS/main thread ждал SQLite, пока sync worker
сравнивал `Feature.equalsData` внутри snapshot-транзакции. GPS-проверка состояния
теперь использует durable flag; обе incremental перерисовки треков читают БД в
фоне. Сравнение атрибутов строит временный name→index за линейный проход по
полям, сохраняя перестановки, первый duplicate name и прежние null/number/date
правила. Это не отменяет атомарную транзакцию и backup gate.

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

ID контракта: `INV-NGW-URL-IMPORT`. Пункт меню «Добавить слой NGW по URL» скрыт;
код разбора URL и импорта сохранён.

## Восстановление выбора ресурсов

`SelectNGWResourceActivity` и `SelectNGWResourceDialog` не сериализуют `Connections`
в saved state; новые launch intents тоже не содержат дерева. Сохраняются имена
account/server, пути remote ID и только выбранные флаги raster/vector. Credentials
повторно запрашиваются из AccountManager. `NgwResourceSelectionState` загружает
необходимые ветви в фоне и сопоставляет новые process-local IDs; отсутствующий
account/ресурс или ошибка сети не считаются успешным восстановлением. Исходное
компактное состояние сохраняется во время restore и после ошибки для повтора,
импорт до успеха заблокирован. Закрытие экрана отменяет restore и закрывает его
диалог ошибки. Размер состояния не зависит от числа загруженных, но не выбранных
ресурсов (не является жёстким лимитом для произвольно большого числа выбранных).

Сценарии проверки: `SMOKE-NGW-SELECTOR-RESTORE`.

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
Подготовленный выпуск `3.1.2.27` использует production tuple `221` / `3.1.2.27`, а отдельный
debug — `218` / `3.1.2.23`; application/account/provider identity не
меняется.
Library defaults нельзя считать достаточными: app variant обязан перекрывать оба
account resource keys. Иначе HTTP-аутентификация проходит, но Android отклоняет
`addAccountExplicitly()` как аккаунт незарегистрированного типа.

Экран входа закрывается только после фактического создания и повторного чтения
account. Отказ Android оставляет форму открытой, показывает отдельную ошибку и
пишет в HyperLog имя сервера и account type без логина, пароля или token.

ID контракта: `INV-NGW-ACCOUNT-IDENTITY`.

## Безопасная мутация данных

Для NGW vector layers с сохраняемыми локальными данными действует fail-closed gate
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

Read-only состояние NGW vector слоя не отменяет backup его имеющихся данных.
Воссоздаваемый raster-style tile cache не требует data backup.

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

Ручной и автоматический запуск используют один ProjectSyncRunner. Настройка
sync_all_projects по умолчанию true: обходятся все загруженные Web GIS/local
projects из CollectorProjectRegistry и текущая карта, без повторения одной базы.
При false выполняется только открытый проект. Запуск для конкретного слоя всегда
ограничен открытым проектом. Ручной запуск берёт все Android accounts; автоматический
tick обходит проекты только для своего account, сохраняя его включение и период.
Accounts/project passes выполняются последовательно с отдельными SyncResult;
ошибка одной пары не останавливает остальные. Активный Collector account
сохраняет приоритет при ручном запуске. Кнопка не ставит дублирующие framework jobs.

Карта закрытого проекта открывается MapDrawable(..., activate=false) без renderer.
SyncWorkspaceSession временно предоставляет её только своему потоку через
MapBase.getInstance/GISApplication.getMap. MapBase.getActiveInstance, mMap и
preferences выбранного проекта не меняются. Следовательно, открытая карта,
форма и её pinned draft продолжают принадлежать прежнему проекту.

Каждая отложенная операция формы, composition apply, schema rebuild и LayerFillService
сохраняет владельца через Session.capture/post/execute/start. Service intent несёт
opaque live token и delivery ticket; ContentProvider URI — sync_workspace token.
Неверный/просроченный token не имеет fallback к активной базе. Untagged UI URI
всегда обслуживает открытый проект, даже на scoped потоке. Вложения используют
такой же owning URI. Изменения spatial cache фоновой карты применяются синхронно
после commit, до закрытия её базы; уведомления другой карты не обновляют UI.

Один DATA_SYNC lease охватывает всю очередь. Зависимые fill/rebuild резервируются
со своим session token и ждут разрешения awaitChildren после синхронного прохода.
Unrelated fill и второй full sync отклоняются. Следующий проект начинается только
после завершения всех callbacks, service tickets и mutating requests. Недоставленный
service ticket истекает через 60 секунд, закрывает reservation, оставляет пару pending;
поздняя доставка не обращается к базе. При отмене закрываются чтения и сохраняется
исходная cancellation generation во всех дочерних задачах. Уже отправленная запись
дожидается результата; закрывать её базу или освобождать lease по таймеру запрещено.

Перед feature sync выполняется backup-gated integrity repair именно owning проекта:
managed layers группируются по account + project_uid + remote_id. Копии без
локальных правок сначала резервируются и сводятся к одной копии одним map commit;
edited-дубликаты блокируют проход без удаления.

SyncRecoveryJournal.pending_v2 атомарно сохраняет весь план project/account до
первого прохода, без credentials/feature data. Только завершённая пара снимается;
ошибка, отмена или process death оставляет её для идемпотентного повтора. При старте
идентичности разрешаются по текущему реестру, а не по переданному пути. Удалённые
project/account исключаются; отключённый auto account автоматически не запускается.
Recovery явно записанной пары сохраняется при смене открытого проекта и при
отключении общего охвата. Legacy marker мигрирует только к совпадающему owner.

CollectorImportJournal имеет отдельные preferences на SHA-256 canonical map path.
Ранее общий journal мигрирует только при совпадении project UID. Общие runtime
поля GISApplication переключаются на main thread в сериализованной границе проекта;
durable journals при этом не очищаются, callbacks проверяют owning path.
Незавершённый импорт восстанавливается до нового composition batch, а исчерпание
repair waves в фоновой сессии оставляет journal для следующей попытки. Schema retry
guard и form/walk reservations также используют owning project, включая одинаковые
числовые layer/group/remote IDs в разных базах.

Ручной и системный sync сохраняют dataSync foreground execution. Это повышает
вероятность завершения при выключенном экране; расписание Android не гарантирует
точное время запуска. Пользовательские действия описаны в
[руководстве синхронизации](../guides/project-synchronization-user-guide.md).

Завершение bound `NGWSyncService` не ждёт worker на Android main thread:
незавершённый проход фиксируется journal и повторяется после запуска, вместо
прежнего блокирующего ожидания, которое само могло вызвать ANR.

`ProjectOperationCoordinator` резервирует active workspace ещё при нажатии
ручной sync и удерживает lease до конца всех project/account, включая промежутки между
ними. Поэтому project switch/create/rename/delete не может попасть в окно между
двумя адаптерами. Если пользователь запускает такое действие, загрузку слоя или
подложки во время sync, UI предупреждает об активной синхронизации, предлагает
прервать её и ждёт закрытия всех lease перед продолжением. Gate выполняется
повторно перед фактической подготовкой Collector workspace, закрывая окно
длительного выбора ресурса. Cancel handlers принадлежат своим reservations и
не перезаписывают друг друга. Периодический adapter
также получает lease; второй полный sync того же workspace отклоняется. Layer fill
и schema rebuild в scoped очереди сохраняют session token и получают доступ к SQLite
только в фазе ожидания собственных зависимых операций. Legacy unscoped clients
сохраняют ожидание освобождения full-sync lease.

Process-wide признак активности обновляет сам `SyncAdapter` перед `SYNC_START`
и во всех normal/cancel/exception finish-путях. Broadcast остаётся событием для
UI, но не является единственным владельцем состояния: receiver фрагмента может
быть снят во время lifecycle-перехода. Пока fragment видим, `LayersFragment`
периодически сверяет анимацию с `NGWSyncService.isSyncStarted()` и
`ProjectOperationCoordinator.isDataSyncActive()` в обе стороны: запускает
пропущенный spinner и останавливает устаревший только после фактического
завершения адаптера и освобождения lease.

Вокруг крутящейся иконки sync `LayersFragment` показывает кольцо без процентов.
`NgwSyncProgress` считает одну сессию на все account ручного прохода (или один
периодический account). Вес листа равен `10 + min(число локальных правок, 30)`;
внутри слоя шкала идёт по отправленным change records и TUS-байтам, затем по
`Content-Length` полного snapshot и apply объектов. Если остаток неизвестен,
доля слоя не двигается, пока слой не завершён. Deferred retry не закрывает слой.
Composition, map reload и LayerFill в кольцо не входят: после последнего слоя
остаётся резерв около 5% до `finishSession`. Если позже добавилась работа,
отображаемая доля не откатывается. Broadcast `SYNC_PROGRESS` троттлится; UI
берёт snapshot и при reconcile. Текста процентов нет; FGS-уведомление может
повторить ту же determinate-полоску.

Состояние хранит владельцев worker-потоков: early finish отклонённого параллельного
запуска не снимает активность другого потока, а `Service.onCreate` и запоздалые
broadcast не сбрасывают её. Foreground receiver и анимация используют текущее
состояние worker. SQLite-проверка локальных правок для badge выполняется в одной
фоновой очереди с отбрасыванием результата прежней карты/уничтоженного view;
main thread не ждёт завершения snapshot-транзакции ради этого badge.

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

Инкрементальный pull одного `NGWVectorLayer` является одной атомарной
SQLite-транзакцией и bulk-операцией.
Вставки, изменения и удаления продолжают выполняться в SQLite с обычной
проверкой backup/change-table, но не отправляют отдельный Android broadcast на
каждую строку. После успешного применения всех серверных изменений слой один раз
перестраивает R-tree из итоговой SQLite и публикует один reload карты. Отмена
между объектами откатывает весь tracked apply до прежнего состояния. Файловые
каталоги вложений удалённых объектов очищаются только после commit, чтобы
rollback не восстановил строку без локальных байтов.

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

Слои с `SYNC_NONE` не входят в обычный feature-sync. После config refresh
сравнивается локальный SQLite `COUNT(*)` с `GET /api/resource/{id}/feature_count`
при том же `mServerWhere` (для района — `filtered_count`, не resource-meta
`total_count`). Если на сервере больше 0 объектов и числа не совпадают, слой
пересобирается тем же потоковым snapshot. Серверный 0 или ошибка count локальные
данные не трогает.

### Тайм-ауты, отмена и объяснённые пропуски

`NGWVectorLayer.getConnection` задаёт 45 с на соединение и 180 с без новых байтов
до первого `getResponseCode`, в том числе на повторном HTTPS-соединении. При
ошибке открытия соединение закрывается. Это не общий deadline большого snapshot:
пока байты поступают, загрузка может продолжаться. Существующее число retry
ограничено; interrupt не запускает сетевой retry, сохраняется для account adapter
и проверяется при download, parse, scan, apply, delete, reconcile и перед commit.
Явное прерывание меняет generation активных sync-session и закрывает их
зарегистрированные read-only GET, поэтому зависшее чтение не ждёт 180 секунд.
POST/PUT/DELETE намеренно не разрывается после отправки: завершается только один
текущий mutation request, локальная change queue обновляется по подтверждённому
ответу, а следующий record уже не начинается. UI отдельно сообщает об этом
безопасном ожидании. SQLite освобождается только после commit/rollback.

`DatabaseContext` не выставляет `journal_mode=OFF` и `synchronous=OFF`: используются
штатные настройки Android SQLite. Backup gate, pending edits, одна транзакция
и recovery journal остаются обязательными. WAL и разбиение атомарного apply на
независимые commit в этой доработке не вводятся.

После успешного commit `NgwSnapshotCheckpoint` может сохранить объяснённое
расхождение: `remote - local == число уникальных невалидных remote ID без локальной
строки`. Невалидный объект с сохранённой локальной копией в это число не входит.
Checkpoint действует менее часа только при совпадении layer/workspace path,
account/server, remote ID, фильтра, CRS/геометрии/полей и обоих count. Отсутствие,
повреждение, истечение срока, перевод часов назад и явная перепроверка запрещают
его использование. Это не серверная revision: исправление геометрии при прежнем
count обнаруживается по истечении срока либо длительным нажатием sync с
подтверждением. Новое evidence не записывается при rollback/backup failure.

`NgwSyncTrace` пишет attempt UUID, remote ID, этап, elapsedMs и не чаще раза в
10 с количество обработанных bytes/features, без credentials и feature payload.
Это диагностические этапы, не обещание завершения к определённому времени.
Точный источник Android ANR определяется по main-thread stack/bugreport, не по
одному продолжительному spinner. См. [проверки доработки](../reference/sync-hang-verification.md).

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
- кольцо прогресса вокруг иконки: рост на push мелкого слоя, удержание на
  большом snapshot без Content-Length, пауза deferred retry, скрытие после
  cancel/finish;
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

## Локальный commit, backup и реквизиты

Feature CRUD и его outbox используют одну owning-map SQLite транзакцию;
`LayerDatabaseTransaction` откладывает уведомления и физическое удаление файлов
до commit. Неудачный вложенный вызов делает managed outer transaction неуспешной,
даже если вызывающий код перехватил ошибку. Raw внешняя транзакция сама отвечает
за финальное обновление UI: промежуточные callback не публикуются.
Строгие запросы FeatureChanges не превращают ошибку базы в пустой outbox.
Совместимые legacy UI wrappers при ошибке чтения считают изменения имеющимися.
`FeatureSaveJournal` — отдельная локальная таблица UUID/id без изменения NGW
protocol/schema. Form retry использует UUID и карту; перенос server feature id
переносит соответствующий journal owner.

ZIP format2 хранит owning map_path, features/changes/attachments row dumps и
локальные attachment files. JSON rows пишутся последовательно из согласованного
SQLite snapshot; отсутствие обязательной features table — ошибка. Уникальный
.partial архив проверяется на обязательные entries и CRC, затем публикуется
уникальным ZIP. Quota применяется к завершённым архивам. Формы/config проекта
в такой backup не включаются; автоматического restore нет. См.
[восстановление на копии](../runbooks/incident-and-rollback.md).

Ручное удаление слоя выполняет backup на worker, оставляет слой доступным для
Undo и при окончательном подтверждении вновь проверяет owning map, parent,
резервирование формы/обхода и generation данных. Поздняя правка отменяет
удаление. Ошибка DROP откатывает все таблицы слоя и сохраняет папку.
Удаление NGW-вложений записывает outbox до удаления photo/metadata; при ошибке
outbox оба файла остаются. File cleanup после commit не является атомарным с DB:
ошибка очистки сохраняет файл для диагностики и не отменяет committed outbox.

`AuthInterceptorNG` публикует неизменяемые snapshots реквизитов; точное совпадение
origin/resource/server prefix предотвращает отправку пароля другому хосту или
ресурсу1 вместо10. Project switch и смена аккаунта обновляют/удаляют owning entries.
NextGIS ID нормализует только login/email; Web GIS passwords и identifiers
сохраняют свои прежние правила. В resource tree folder/up icon назначается на
каждую bind независимо от того, была ли строка раньше кнопкой добавления аккаунта.

## Правила проекта и чтение истории

Жёлтая точка в строке слоя и на кнопке синхронизации строятся из одного
фонового снимка `isChanges()` для текущей карты. Проверяются все векторные слои,
в том числе скрытые; группа отмечается при изменениях в дочернем слое.
Outbox учитывает удалённые объекты и изменения вложений, даже когда объектов
в слое уже нет. `LayersListAdapter` получает только набор layer ID и не читает
SQLite при отрисовке. Изменения слоя, возврат на экран и завершение/отмена sync
обновляют снимок; lifecycle generation и owning map отбрасывают устаревший ответ.
Переработанная строка всегда устанавливает/снимает точку для нового слоя.

NGFP может содержать декларативный блок
[`meta.json.lisa_form_dependencies`](cascading-form-lists.md). Он задаёт общие
таблицы и несколько родителей поля; фильтрация выполняется нативно без запросов
при выборе. Значения существующих STRING-полей и схема NGW не меняются.
Смена родителя очищает всех потомков явным SQL NULL, включая второе поле
`double_combobox`. Общий Save после правил проекта проверяет допустимость новых
сочетаний и затем обязательность, до записи объекта и вложений. Исходные
исторические значения разрешены при неизменных предках. Определение и ключи
выбора закреплены за открытой формой и её черновиком; обновление NGFP влияет
на новые формы.

Collector import/composition sync на worker читает только namespaced ссылку
`mobile_json_config.lisa_project_scripts`, проверяет ZIP/SHA-256 и активирует
проверенный кеш своей карты. Ошибка обновления оставляет старую версию с явным
статусом. Broker читает SQLite через owning layer/map и grants manifest; он не
вызывает NGW, не меняет feature/outbox и не получает Android accounts в sandbox.
[Формат и ограничения](project-scripts.md).

`meta.json.lisa_form_rules` доставляется вместе с NGFP и задаёт
[зависимую обязательность](conditional-form-rules.md) без изменения схемы слоя
или feature/outbox. Для видимого поля статический required остаётся обязательным
при любом условии. V2 того же namespace добавляет visible для field/element;
скрытый контейнер не блокирует UI required/cascade gate, но его значение
сохраняется в контролах, draft и обычном Save. Схема/flag слоя не меняются.
Default launch передаёт парные numbered или legacy form/meta файлы.

## Диагностика ошибок очереди

ProjectSyncRunner сообщает выбранные неожиданные исключения через AppDiagnostics,
без account names, project paths и значений объектов. Offline/timeouts/cancel не
создают handled bug report. Это независимая SDK cache/WorkManager доставка;
она не использует sync lease, не открывает GIS базу и не меняет pending pairs.
[Контракт диагностики](error-reporting.md).