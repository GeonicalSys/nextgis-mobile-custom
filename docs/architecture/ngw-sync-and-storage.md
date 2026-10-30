---
title: NGW sync, локальное хранение и восстановление
type: architecture
last_verified: 2026-10-30
related_code:
  - app/src/test/java/com/nextgis/mobile/fragment/SettingsWorkspaceMoveTest.java
  - app/src/main/java/com/nextgis/mobile/util/SelectMapPathPreference.java
  - app/src/main/java/com/nextgis/mobile/fragment/SettingsFragment.java
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
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/util/MaintenanceReminders.java
  - maplib/src/main/java/com/nextgis/maplib/util/SyncWorkspaceSession.java
  - app/src/main/java/com/nextgis/mobile/util/SyncJournalCommit.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/LayerContentProvider.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapBase.java
  - maplib/src/main/java/com/nextgis/maplib/util/ProjectSyncState.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorImportJournal.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/LocalChangesWorker.java
  - app/src/androidTest/java/com/nextgis/mobile/util/ProjectSyncRunnerTest.java
  - app/src/test/java/com/nextgis/mobile/util/SyncRecoveryJournalTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/SyncRecoveryJournalNativeTest.java
  - maplib/src/test/java/com/nextgis/maplib/util/ProjectSyncStateTest.java
  - app/src/test/java/com/nextgis/mobile/util/SyncJournalCommitTest.java
  - maplib/src/test/java/com/nextgis/maplib/reliability/SyncWorkspaceIsolationTest.java
  - app/src/main/res/xml/syncadapter.xml
---

# NGW sync, локальное хранение и восстановление

## Владение workspace и границы подтверждения

Углублённый source-разбор29.10.2026 продолжает MOB-02 после UI-каталога
MOB-UI-01. Он подтверждает цепочку по текущим app/maplib/maplibui источникам,
но не выполняет Android, HTTP, SQLite или device-проверки. Описанные ниже
независимые runtime ожидания этого этапа имеют статус **NOT_RUN**.

### Разные ключи одной операции

| Назначение | Фактическая идентичность | Владелец |
| --- | --- | --- |
| Collector-проект | `collector:<accountName>:<remote project ID>` | `CollectorProjectMetadata.buildProjectUid` |
| Сохранённая цель account sync | `UID\|canonical directory\|mapName` | `ProjectSyncRunner.Target.key` и `SyncRecoveryJournal` |
| Подтверждённый полный проход | UUID-хеш canonical directory, без UID и mapName | `ProjectSyncState.key` |
| Незавершённый Collector import | preferences с SHA256 canonical directory | `CollectorImportJournal.preferences/workspacePath` |
| Живой provider/service owner | случайный session token; отдельный ticket на доставку сервиса | `SyncWorkspaceSession` |
| Запрет конкурентной операции | process-global lease; его workspaceKey читается из active preferences | `ProjectOperationCoordinator` |

Имя проекта, remote resource ID, local layer/group ID, canonical directory,
mapName, session token и lease — разные части контракта. Lease сам по себе
не выбирает БД фонового проекта. Даже при работе с проектом B его workspaceKey
может описывать активный проект A; реальное владение обеспечивают scoped map,
parent chain слоя и live token. При переносе каталога, изменении mapName или
новой схеме восстановления отдельно определить миграцию/инвалидацию recovery
queue, import journal, full clock и черновиков. Их одинаковая судьба не следует
из слова «проект»; обычная смена display name не доказывает смену этих ключей.

Обратная граница desktop — `stand_project/ngw_collector_projects.py`,
`_etalon_item_to_live_child`: keyname разрешается в NGW resource_id, передаются
editable/visible/syncable/zoom. Мобильный UID добавляет account и remote project
ID, а локальная работа — directory/mapName. Эти ключи не подменяют друг друга;
desktop не имеет runtime-доступа к session token или телефонной БД.

### От очереди к дочерним работам

[`ProjectSyncRunner.run`](../../app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java)
получает собственный DATA_SYNC lease либо использует уже удерживаемый lease
вызывающей очереди, строит trusted inventory и сохраняет полный план
project/account pairs **до первого прохода**. Фоновая `MapDrawable(..., false)`
не активирует UI singleton. Scope направляет `MapBase.getInstance()` и
`GISApplication.getMap()` к owning map; `MapBase.getActiveInstance()` остаётся
источником видимой карты. `DatabaseContext.getMapForLayer` сначала проходит
parent chain слоя и только затем использует scoped fallback.

Scope должен переходить через каждую async границу. `SyncWorkspaceSession`
capture/post/execute/start учитывают pending children и наследуют исходную
cancellation generation. Rejected/discarded task освобождает pending ровно
один раз; удаление задачи из executor без `discard` не равно её завершению.
`ProjectSyncRunner.onMain` переносит scope на main thread через tracked post,
а `awaitChildren` допускает только принадлежащие session fill/rebuild стадии.
Обычный новый callback без owner propagation может разрешить совпавший layer
path в A вместо B; совпадение ID не является oracle владения.

`LayerFillService.startFillIntent` резервирует operation lease и external ticket
до отправки foreground-service intent. `onStartCommand` проверяет live token,
отмену и однократное claim ticket; чужой import не заимствует уже занятую
фоновую карту. Worker исполняется в tracked scope. Drain, завершение callback,
release ticket, release lease и закрытие background map — отдельные события,
которые нельзя заменить одним флагом «сервис не busy».

### Provider URI и уведомления

[`SyncWorkspaceSession.bindUri`](../../maplib/src/main/java/com/nextgis/maplib/util/SyncWorkspaceSession.java)
проверяет parent owner и существующий token. Для неактивной карты нужен live
token; expired token не имеет fallback на UI. `LayerContentProvider.getLayerByUri`
разрешает tagged URI только через этот owner. **Untagged URI относится к active
UI map даже внутри background scope**: `getActiveMap` явно использует
`MapBase.getActiveInstance`. Исправления общего `getMap` недостаточно — каждый
provider callsite должен явно сохранить owner.

Broadcast использует другую границу: `bindNotification` добавляет owning map
path; `MapContentProviderHelper.VectorLayerNotifyReceiver` принимает tagged
notification только своей карты, а untagged — только active map. Path/ID в
уведомлении и token в provider URI выполняют разные задачи.

### Account ACK раньше полного clock

Порядок в `ProjectSyncRunner.run`:

1. Выполнить account pass и дождаться его детей.
2. Проверить account error, session failure/cancel, готовый account outbox и
   незавершённый Collector import. Лишь затем удалить точную пару через
   `SyncRecoveryJournal.complete`.
3. После accounts проверить весь обязательный набор account names, отсутствие
   layer-only ограничения и готовых правок всего проекта.
4. Ещё раз дождаться детей, закрыть session и background map, восстановить
   runtime bookkeeping открытой карты.
5. Только после успешного cleanup и при неизменной cancellation generation
   вызвать `ProjectSyncState.markComplete` и потребовать успешный результат
   записи; legacy account clocks обновляются лишь после этого результата.

Следовательно, пустой recovery journal **не доказывает полный project ACK**:
pair ACK записывается раньше финального cleanup/clock. Это разные commit
boundaries. Process death или cleanup failure между ними не разрешают агенту
переносить часы вперёд по отсутствию очереди. Быстрый `LocalChangesWorker`
использует owner/drain/lease, но не пишет full clock. `LayersFragment` читает
clock только active UI map; особенности неуспешной записи описаны ниже.

### Окно после последнего pair ACK и реальные повторные запуски

В `ProjectSyncRunner.run` вызов `SyncRecoveryJournal.complete` находится внутри
account loop. При успешной записи последнего ACK journal уже не содержит этих
пар, а `awaitChildren`, `session.close`, `closeSyncWorkspace` и
`prepareProjectSyncState` ещё впереди. Исключение финального cleanup снимает
`fullProjectComplete`, увеличивает `numIoExceptions` и добавляет проект в
`failedProjects`; пара обратно не ставится и `markComplete` не вызывается.
При старом исходном clock он остаётся старым. Это условный source-контрпример
утверждению «пустая очередь означает завершённый полный обмен», а не выполненный
crash-тест или доказанная потеря feature/outbox.

| Событие | Recovery pairs | Full-project clock | Следующий запуск |
| --- | --- | --- | --- |
| Account pass отказал до ACK | Точная пара сохраняется | Не записывается этим проходом | Startup recovery может запросить сохранённую пару |
| Последний ACK сохранён, затем cleanup отказал | Завершённые пары уже удалены | `markComplete` не вызывается | Startup journal не воссоздаёт отсутствующую пару |
| Процесс погиб после durable ACK до clock | Удалённые пары не возвращаются от старого clock | На диске прежний clock, если новая запись ещё не началась | Восстанавливаются оставшиеся пары; новый обычный запуск строит новый план |
| Cleanup завершён, запись clock вернула false | Это отдельная запись после pair ACK | Память и диск могут различаться | Нельзя решать исход по одному cached getter или одному boolean |

`MainApplication` планирует `schedulePendingIfNeeded` через3s после старта.
Этот метод разрешает только сохранённые пары через trusted inventory/accounts;
старый clock не является сканером пропущенной финализации. Не-manual пару с
выключенным automatic sync он не запускает. Обычное восстановление расписаний
в `GISApplication` — другой путь, а `SyncAccountWorker` вызывает `requestSync`
и планирует следующий tick только в соответствующей ветви. `Result.success()`
также возвращается без отправки при удалённом account или выключенном auto sync.
В ветви `requestSync` это результат доставки запроса, не завершение синхронизации.

Ручной повтор из `LayersFragment.showSyncFailureDialog` идёт через
`startManualSync` и `OfflineSyncIntentService` с новым планом. Service передаёт
свой `new SyncResult()` непосредственно runner, поэтому framework не получает
этот объект для собственного retry. Scheduled `datasource.SyncAdapter` передаёт
настоящий framework `SyncResult`. Оба пути заново сохраняют выбранный план до
работы. Это возможность повторного прохода, не гарантия срока при выключенном
расписании, отсутствии сети или отсутствии нового ручного действия.

Полный clock требует весь `LocalChangesWorker.projectAccounts(map)` в
`completedAccounts` **одного запуска**, без layer-only ограничения. Успешные
account-only ticks разных запусков не складываются в полный project completion.
`SYNC_FINISH` означает окончание очереди: runner может отправить его и при
ошибке cleanup/clock, используя `SYNC_CHANGES` для error notification.
`failedProjects` дополнительно даёт ручной incomplete alert; ошибка записи clock
попадает во внешний queue catch и сама по себе не добавляет туда имя проекта.

### Неуспешный commit: память отдельно от диска

Описание backend ниже относится к app pin maplib F61. Отдельный own library
PR63 исправляет публикацию process-visible clock; до Merge Commit → app gitlink
→ semantic UI pin review это не считается поставленным app-исправлением.
Существующий F61 UI fingerprint здесь намеренно не переписывается.

`ProjectSyncState.markComplete` вызывает обычный `SharedPreferences.Editor.commit`,
а `lastComplete` читает тот же process-cached preferences. По
[Android API](https://developer.android.com/reference/android/content/SharedPreferences.Editor#commit())
true подтверждает persistent write. В AOSP
[Android14, `SharedPreferencesImpl`](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-14.0.0_r1/core/java/android/app/SharedPreferencesImpl.java)
`commitToMemory`477–546 изменяет map до disk write; `commit`551–573 не откатывает
её при false. Например, отказ rename723–727 оставляет прежний файл, но новый
`getLong`301–305 видит память. Порядок сохраняется в
[Android16](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-16.0.0_r1/core/java/android/app/SharedPreferencesImpl.java)
(`getLong`309, `commit`559). Это source-подтверждённый пробел между намерением
«подтверждённый clock» и текущим cached UI; на устройстве он **NOT_RUN**.

False из interrupted await имеет другую ветвь: writer уже запущен и может
сохранить файл. Поэтому false не доказывает ни прежний диск, ни новый диск.
`SyncJournalCommit` изолирует journal writer от interrupt вызывающего потока,
но сам по себе не возвращает SharedPreferences memory к прежнему состоянию при
ошибке записи. Пустой cached journal также не заменяет durable oracle.

Независимая будущая проверка должна разделять return value, getter текущего
процесса, physical XML/backup после окончания writer и fresh-process reload.
Proxy, возвращающий false **до** настоящего commit, не моделирует этот исход.
Не исправлять его записью `now` или удалением очереди по UI/расписанию.

### Отмена и liveness

`NgwSyncIo.requestCancellation` увеличивает process-wide generation и закрывает
зарегистрированные read-only HTTP. Уже отправленная mutation намеренно не входит
в этот набор: сначала разрешается её ответ, затем принимается решение об outbox;
следующую mutation запрещает проверка cancellation. Это не обещание, что любой
запрос на запись немедленно прервётся.

`awaitChildren` временно снимает interrupt для drain и возвращает факт прерывания
очереди. Та восстанавливает interrupt в финальном cleanup, перед этим закрывая
собственный lease; при already-held пути внешний lease остаётся во владении
вызывающего кода. Session не закрывается с pending work. 60-секундный
`expireUndelivered` относится **только к unclaimed service deliveries**; claimed
child/service не получает этим общего timeout. Принудительно закрывать его БД
или отдавать lease по возрасту нельзя. Liveness такой работы остаётся отдельным
неподтверждённым runtime свойством.

Разобран и отвергнут ложный вывод: ранний token rejection в `onStartCommand`
сам по себе не доказывает нарушение первого foreground-start. `LayerFillService.onCreate`
уже вызывает `startForegroundWithSessionAwareNotification`. Проверять нужно весь
lifecycle, включая повторную доставку уже существующему сервису; source-разбор
не подтверждает каждый native исход.

### Независимые ожидания и открытые ветви

Существующие `SyncWorkspaceIsolationTest` и `ProjectSyncRunnerTest` содержат
witnesses для одинаковых layer/group IDs в A/B, tagged provider update только B,
expired token без fallback, rejected/discarded children, сохранности active map
и draft, отмены с удерживаемым child, per-project clock и реального background
fill. Здесь прочитаны исходники этих тестов; их наличие не является выполнением.

`SMOKE-SYNC-OWNER-ACK-BOUNDARY` закрепляет адресную будущую проверку. Новые ветви
имеют NOT_RUN, а задачи хранятся **в едином LISA реестре**
`plugins/geonical-docs/registry/dependency-audit-todo.yaml`:

- `AUDIT-TODO-SYNC-ACK-RECOVERY`: process death/cleanup failure между pair ACK
  и full clock, реальный retry и commit-false memory/disk divergence; определить
  политику финализации без повторного подтверждения уже ACKed данных по догадке.
- `AUDIT-TODO-SYNC-CLAIMED-LIVENESS`: claimed child, который не возвращает
  управление; определить наблюдение/восстановление без раннего закрытия БД.
- `AUDIT-TODO-WORKSPACE-KEY-MIGRATION`: перенос directory/mapName и судьба всех
  независимых persisted keys, включая legacy journals и draft owner.

Непроверенные внешние async callsites, effective tenant/server/device варианты
и native lifecycle остаются UNKNOWN. Исходный ACK source-аудит не менял product bytes,
очереди, схемы, серверные данные, настройки или миграцию и не возобновляет
приостановленные runtime этапы.

## Storage move и Reset: lease, actual path и REFRESH — 2026-10-29

`SettingsFragment` объединяет два входа, сохраняя разные эффекты. Storage chooser
всегда получает false от listener, поэтому не пишет proposed path/card до worker.
Reset confirmation только запускает admission. Публичный `tryBegin(PROJECT_SWITCH)`
атомарно отказывает при занятом coordinated owner или walk draft; default
resolver, prefs/defaults, retirement/reset и result не исполняются при отказе.
Это исправляет подтверждённый source bypass общей защиты sync/fill, а не выбирает
политику переноса всех saved-state stores.

После admission caller/UI захватывает exact map. Worker использует её и вызывает
`MainApplication.retireMapForStorageMove` до первого physical rename. Helper
проверяет прямое поле владельца, не null-ит и не создаёт карту. `MapBase.moveTo`
меняет mPath после всех rename; IOException пытается rollback и может вернуть
старый path. Worker сохраняет actual path, не желаемый destination. Отсутствие
достигнутого target или false commit — отдельный неуспех; false обычного
SharedPreferences после memory update не означает unchanged cache/disk.

Terminal callback удерживает lease до завершения UI effects. Direct-field
predicate и atomic rebind проверяют exact map плюс raw cached path=actual path,
не вызывая factory `getMap` на отказе. `RESULT_OK` для `RELOAD_ACTIVITY_DATA`
просит MainActivity/MapFragment перепривязать drawable; он допустим после
failed/cancelled retired move и **не подтверждает успешный перенос**. Такие
ветви не выполняют Reset defaults/district/init/delete. Только нормальный
Reset target/path completion запускает прежние preference removals/default XML,
resetMap и существующий district/init/delete/init порядок с исключением OSM/tracks.

Queued cancellation не трогает map; started cancellation освобождает lease
после реального unwind worker и terminal refresh/cleanup. Late duplicate callback
не меняет нового owner. Pre-lock failure не unlock-ит неприсвоенную orientation.
Unexpected RuntimeException/Error до достоверного cached actual state сохраняет
прежнее worker propagation/finally release; произвольная atomic recovery не
выдумывается. Main-thread join/ожидание worker, новый synchronous path commit и factory не добавлены.

Source-проверка:21 actual-body Robolectric/JVM cases PASS на frozen app bytes,
LisaDebug source compilation и BelkaRelease Java/Kotlin compilation выполнены
отдельно. Это не native move/DB/renderer/Reset acceptance. Retirement отменяет
публикацию новых render generations и кооперативную работу, но не ждёт active
blocking reads. Renderer drain, physical partial rollback, process restart,
registry/project.json и независимые path-key journals/drafts остаются UNKNOWN
в `AUDIT-TODO-WORKSPACE-MOVE-LEASE-DRAIN` и `AUDIT-TODO-WORKSPACE-KEY-MIGRATION`.
Адресный `SMOKE-WORKSPACE-STORAGE-MUTATION` — будущий NOT_RUN, не разрешение на запуск.

App UI clock anchor остаётся закреплённым на maplib F61. Отдельное исправление
clock process-view в own library не считается поставленным до library Merge
Commit → app gitlink → semantic UI pin review. Полная текущая UI/docs проверка
с известным clock drift не называется PASS и не фильтруется.

## Буферизация существующего индекса — 2026-10-27

GeometryRTree.load сохраняет synchronized контракт и читает существующий
бинарный файл через BufferedInputStream 64 KiB перед DataInputStream. Это
сокращает мелкие filesystem/Sentry reads в startup, но не переносит загрузку
модели с main thread. Try-with-resources закрывает поток и при IOException.
Версия файла, ID, envelopes, поиск, запись, rebuild и бизнес-данные не меняются.
Независимый legacy fixture из 6000 элементов проходит несколько узлов/буферов,
проверяет точные координаты, поиск и byte-identical roundtrip без записи в input.
[Инцидент и непроверенные случаи](../reference/mobile-crash-224-2026-10-27.md).

Миграция общей истории читает настоящий проектный `layers.db`: расширение
добавляет DatabaseContext к логическому имени `layers`. Общий resolver имени
не открывает/не создаёт источник. Пропущенные первым обновлением истории
переносятся рядом с новыми треками; atomic checkpoint читается без изменения
исходных `.bak`/`.new` файлов. См. [общее хранилище](shared-tracks.md).

## Быстрая отправка правок и часы полного проекта

`NGWVectorLayer` сообщает `IGISApplication.onLocalChangesCommitted(owningMap)`
после managed commit feature/outbox и снятия Save-флагов. Rollback не планирует
отправку. `FeatureChanges.getChangeCount/getFirstChangeFromRecordId` учитывают
отдельные TEMP/NOT_SYNC строки всего feature и конкретного attachment: черновик
не становится готовым только потому, что NEW/CHANGED записан другой строкой.
Это правило используется и полным SyncAdapter. Очередь и файлы не очищаются
при отсутствии сети или неуспешном ответе сервера.

`LocalChangesWorker` объединяет события одного owning map за секунду и сохраняет
one-off работу с `NetworkType.CONNECTED`. APPEND_OR_REPLACE оставляет следующий
проход для commit, пришедшего во время отправки. Expedited-запуск Android 12+
имеет fallback без expedited quota; Android всё ещё может отложить исполнение.
Старт приложения восстанавливает задания всех зарегистрированных проектов,
а отдельный 15-минутный periodic job подбирает очередь после пропущенных событий.
Эта отправка не зависит от включения расписания account sync, но соблюдает
SYNC_NONE, remote read-only и направление to-server. Отправляются готовые
создания, изменения, удаления и вложения штатным sendLocalChanges; полного
pull данных/форм/состава проекта нет. TrackWorker сохраняет свою независимую доставку.

Глобальный DATA_SYNC lease, SyncWorkspaceSession и ожидание дочерних работ
сохраняют owning map; фоновые карты не меняют mMap или active preferences.
Потеря сети/ошибка дают retry, не успешное подтверждение очереди.

`ProjectSyncState` предназначен для подтверждённого full-sync clock по canonical map path
в full-project-sync-state и используется [недельным напоминанием](maintenance-reminders.md) только для
активных онлайн-проектов. Локальные карты исключены; проверка manifest updater
имеет собственные часы и не подтверждает синхронизацию данных.

`ProjectSyncRunner` пытается сохранить его после всех account
проходов текущего проекта, ожидания children, проверки отсутствия готовых
local edits и успешной очистки session. Отказ до этого gate, отмена, layer-only
запуск, другая account и track-only обмен не запускают запись project clock.
Неуспешный commit имеет отдельный memory/disk исход, разобранный выше. Для проекта с
несколькими account полный запуск должен охватить их все. Успех другого проекта
может быть сохранён независимо от ошибки одного проекта общей очереди.

`LayersFragment` читает MapBase.getActiveInstance и только clock видимой карты,
включая вызов внутри фонового SyncWorkspaceSession на UI thread; до первого подтверждённого
полного прохода показывает «Полная синхронизация ещё не выполнена». Старый global
last_sync_timestamp не мигрируется: он мог означать чужой account или только трек.
Быстрые local/track uploads часы не пишут. Tracked NGW pull также использует
owning project clock; неизвестный clock даёт since=0, не watermark чужой карты.
Account summary сохраняет отдельный legacy account clock после полной очереди.

Регрессии: ProjectSyncStateTest, LocalChangesCommitTest, ProjectSyncRunnerTest,
ProjectSyncDisplayNativeTest (реальный drawer в background scope),
LocalChangesUploadTest (сохранённая линия, offline/503 retry, edit/delete,
TEMP Save и равные layer paths разных проектов; только isolated emulator/loopback).

Перед ручным запуском `LayersFragment` проверяет `NetworkUtil.isNetworkAvailable`.
При отсутствии активного connected network он показывает «Отсутствует подключение
к интернету» и не начинает service, очередь, track work или flip offline-sync mode.
Диалог retry и уведомление ошибки также различают отсутствие сети. Это не проверка
Google Internet validation и не HTTP probe: доступный LAN/VPN NGW не блокируется,
а HTTP 503 при подключённой сети остаётся обычной серверной ошибкой.

Фоновые account/track задания WorkManager требуют `NetworkType.CONNECTED` и
не исполняются без сети. Дополнительная проверка `NetworkUtil` в
`SyncAccountWorker.doWork`, `ProjectSyncRunner.run` и `TrackWorker` закрывает
потерю подключения между разрешением задания и выполнением. Account worker
возвращает retry до `requestSync`; общая очередь просит Android повторить
отложенный запуск до lease, progress, диагностического START и journal enqueue.
Уведомлений и багрепортов от этого ожидания нет. Существующий recovery journal
остаётся неизменным; уже поставленные Android account задания ждут сети.
`TrackUploader` проверяет сеть перед регистрацией и очередным пакетом, включая
live recorder. Неотправленные точки и настройка отправки сохраняются. Сеть,
пропавшая во время уже отправленного запроса, обрабатывается штатным retry;
HTTP 503 при наличии подключения не маскируется отсутствием интернета.

[Выбор типа нового объекта](feature-type-creation.md) использует локальный
FieldStyleRule и существующие NGFP-справочники. Новых запросов, изменений
серверного формата или схемы SQLite нет; выбранные значения попадают в штатный
Save/outbox только после общей проверки формы.

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

SyncRecoveryJournal.enqueueOrThrow сохраняет исходный exception как cause и
добавляет этап read_queue/legacy_inventory/merge_pairs/commit, число project/account
и записей, размер JSON и доступное место. Runner передаёт эту причину в GlitchTip
и полный stack в HyperLog вместо общего boolean failure. Legacy inventory
читается только при наличии legacy active marker. Ошибочная очередь не сбрасывается.

SyncRecoveryJournal.pending_v2 атомарно сохраняет весь план project/account до
первого прохода, без credentials/feature data. Только подтверждённое снятие
завершённой пары меняет принятый Journal API план; ошибка/отмена не разрешает
незаметно включить её удаление в следующую успешную запись. После process death
читается фактически сохранённый файл; failed-commit disk outcome остаётся отдельным
UNKNOWN, а не обещанием rollback. При старте
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

`show_sync=false` в XML и app fallback отключает обычные start/finish/cancel
сообщения; явный выбор пользователя не мигрируется. Ошибка общей очереди
показывает одно заменяемое уведомление 518 с понятным текстом независимо от
`show_sync`. Raw exception/HTTP message туда не попадает; ручной in-app диалог
с перечнем не обновившихся проектов сохраняется. Успешный повтор снимает прежнее
уведомление ошибки при выключенных обычных сообщениях.

Служебные индикаторы manual 519 и account 520 остаются тихими и не зависят от
optional messages: Android требует уведомление для foreground service
([контракт Android](https://developer.android.com/develop/background-work/services/fgs)).
Manual receiver не публикует inactive snapshot; уничтоженные службы блокируют
поздние callbacks до unregister/stopForeground и отдельно отменяют свой ID.
Это исключает повторный бесконечный spinner после удаления итогового индикатора.
MainApplication очищает оставшиеся 519/520 только при старте основного процесса,
до создания служб; ошибки и GPS не очищаются. Формат данных, очередь, lease,
расписания и правила завершения самого sync не меняются.

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

## Доставка трека и регистрация UID

Отправка треков включена по умолчанию и не зависит от регистрации UID.
Однократная миграция track_send_default_enabled_v1 включает старое значение,
которое прежняя проверка регистрации могла снять автоматически; последующий
явный отказ пользователя сохраняется. В настройках переключатель доступен
до регистрации. Проверка /registered выполняется при доставке, не меняя track_send;
при registered=false, отсутствии сети или ошибке точки остаются sent=0.
После создания трекера следующая попытка отправляет накопленные точки без
повторного посещения настроек.

TrackRegistrationState сохраняет track_registered_device — SHA-256 точного
device endpoint (Tracker Hub URL + UID), только после успешного JSON registered=true.
Отсутствие/несовпадение подтверждения и registered=false скрывают отметку трека;
false отзывает matching подтверждение. HTTP/сетевая ошибка или некорректный ответ
не снимает известную регистрацию. Поэтому offline у зарегистрированного трекера
не скрывает backlog. Cache не разрешает обход проверки /registered при отправке
и не меняет track_send, sent или расписание повторов. При смене сервера/UID
подтверждение другого endpoint не применяется. UI читает cache без сети.

Живой recorder и TrackWorker используют общую private tracks.db через
SharedTrackStore, TrackUploader и один delivery lock. [Общая история](shared-tracks.md).
Пакет содержит до 100 точек; после успешного ответа /packet подтверждаются
только IDs этого пакета в общей БД. Новые точки и ошибки сохраняют sent=0.
WorkManager использует одну unique immediate/15-minute periodic очередь установки
с network constraint. Старые project jobs перепланируют общую работу и не читают
проектные rollback-таблицы. После Stop/startup доставка сохраняется при другой
открытой карте и удалении проекта; project lease и SyncWorkspaceSession для
треков больше не требуются. Full-project clock остаётся проектным.

Контракт Tracker Hub: [registered и packet](https://docs.nextgis.com/tracker_hub_dev/source/main.html).

## Правила проекта и чтение истории

Жёлтая точка в строке слоя и на кнопке синхронизации строятся из одного
фонового снимка `isChanges()` для текущей карты. Проверяются все векторные слои,
в том числе скрытые; группа отмечается при изменениях в дочернем слое.
Outbox учитывает удалённые объекты и изменения вложений, даже когда объектов
в слое уже нет. `LayersListAdapter` получает только набор layer ID и не читает
SQLite при отрисовке. Для TrackLayer снимок проверяет sent=0 через owning layer
только при track_send=true и подтверждённой регистрации текущей пары сервер/UID;
общий sync доступен и без векторных аккаунтов, когда
включена отправка трека. ContentObserver trackpoints и preference listener обновляют
отметки после новых точек, подтверждения/отзыва регистрации, смены сервера,
успешного ACK и opt-out без открытия настроек. Stale guard повторно проверяет
оба условия перед применением фонового снимка.
Изменения слоя, возврат на экран и завершение/отмена sync
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

## Изоляция verification account

Opt-in verification Debug app имеет отдельный account type/provider authority.
Phone integration получает только read-only snapshot layer policies
разрешённого проекта; настоящие accounts/credentials/features не импортируются.
Синхронизационные автоматические сценарии продолжают использовать synthetic
loopback receivers. Обычные Lisa/Belka/Debug account identities не меняются.

## Отмена потока и подтверждение recovery journal — 2026-10-17

enqueue, complete и миграция pending sync journal выполняют SharedPreferences.commit
на одном собственном disk writer. Вызывающий поток ждёт подтверждения долговечной
записи, даже если отменён, и возвращает флаг interrupt перед выходом. Сетевые проходы
после отмены этим не возобновляются. Общий synchronized read/merge/write сохраняется;
нет apply() вместо commit и нет удаления остальных project/account pairs.
Настоящий commit=false остаётся ошибкой, с исходной причиной и structured sync_journal.
Две native регрессии проверяют физический shared_prefs XML на API 36: interrupted
enqueue и acknowledgment одной пары при сохранении другой. Оба сценария падали
на исходном коде. Это воспроизводимая причина части ошибок, а не доказательство
одинаковой причины всех исторических групп 64/76/77.

## Process view recovery journal — 2026-10-30

Подтверждённый источник дефекта: при A+B в очереди complete(A) мог вернуть
false после изменения Android cache до B. Runner считал A незавершённой,
но последующий успешный complete(B) читал B и сохранял пустой план. Это
расхождение reported ACK и следующего API плана; удаление remote features
этой цепочкой не доказано.

SyncRecoveryJournal фиксирует preferences owner один раз на API-вызов. Общий
typed/presence snapshot содержит pending_v2, active, account, workspace, manual,
started_at и attempt. До edit/commit доступен прежний принятый snapshot; true
публикует новый, false/Throwable оставляет прежний. После первой неуспешной
записи view остаётся привязанным к точному owner даже при последующих true.
До первой ошибки здоровая transient view освобождается после true. Weak identity
не удерживает Context/preferences; JSONObject/JSONArray не являются shared state.

Все последующие обычные editor writes переносят семь известных ключей вместе,
включая отсутствие ключа и поддерживаемый raw тип. Unknown keys не очищаются.
enqueue сохраняет manual OR и прежнюю миграцию совпавшего legacy owner; schedule
сохраняет свою прежнюю схему удаления active и отправляет requests только после
true. Повреждённый JSON или применимый неверный тип остаётся ошибкой с прежней
причиной/фазой, без [] fallback и без новой миграционной политики.

load использует короткий state lock и повторную проверку после raw-cache miss;
при установленной view он не ждёт class monitor или удерживаемый commit.
Первичная загрузка preferences сама может ждать чтения файла. Сериализация
mutators и SyncJournalCommit owned writer/interrupt policy не меняются. Реальные
listeners writer-потока публикуются Android на Main; новый контракт вложенных
синхронных writers не вводится.

Первый raw snapshot — as-read baseline с неизвестной disk provenance. Нет
compensating commit, retry, cache/disk rollback или изменения resend policy.
Физический файл после false может остаться старым либо новым; запись другого
ключа тем же backend может сохранить его cache. Native/restart witnesses,
post-ACK cleanup и full clock остаются в AUDIT-TODO-SYNC-ACK-RECOVERY.
Actual-body JVM результаты и source basis: [android-20261030-sync-journal-confirmation](../../update-reports/entries/android-20261030-sync-journal-confirmation.md).

## Содержимое lookup-кеша: замена ключей — исторический срез8ca/9 от30 октября2026

`NGWLookupTable.sync` сравнивает key/value содержимое набора, принятого
текущим parser, а не только число пар. Это не независимое доказательство
полноты NGW snapshot. При равном размере новый ключ раньше приводил к
`null.equals` из старого кеша. Теперь сравнение идёт от ненулевой строки
remote `Hashtable`: отсутствующий локальный ключ означает различие, после
чего прежние `clear/putAll/save` обновляют тот же объект кеша. Обычная
`SyncAdapter` граница перехватывала прежний NPE как ошибку/прерванный проход;
это не свидетельство падения процесса или потери данных.

`Combobox` и `AutoTextEdit` читают lookup по account и remote ID из уже
найденного локального слоя. Исправление сравнения не создаёт отсутствующий
lookup, не меняет NGFP `getLong/-1`, prefetch, shared-cache deletion или
account-removal cast. Эти границы остаются в едином Root TODO
`AUDIT-TODO-NGFP-LOOKUP-PREFETCH`. В sync обработанные IOException/NGException/JSONException
сохраняют прежний кеш; технический partial-fill тест явно выбрасывает
IOException. Успешно разобранный неполный/silent partial ответ этим не
обнаруживается: AUDIT-TODO-NGW-PARTIAL-SNAPSHOT остаётся UNKNOWN. Равное содержимое в другом порядке не меняет порядок/identity
старого кеша и не вызывает save; изменённые значения/число и пустой полный
ответ сохраняют прежние ветви.

Возврат `save()==false` по-прежнему не откатывает опубликованную память и не
устанавливает новый SyncResult error: последующий одинаковый ответ может
не вызвать повторной записи. Это сохранённая политика, а не подтверждённая
надёжность диска или исправленная retry-семантика. Вопросы принадлежат
`AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE`; компенсационных записей и новой
политики повторов здесь нет.

Адресные actual-body JVM:9 методов, baseline1 AssertionFAIL/8 PASS,
затем9 PASS/0 ошибок/пропусков (2.066с XML). Использованы технические read/save
seams без HTTP/SQLite/business data. LisaDebug source compile входит в этот
прогон; BelkaRelease Java/Kotlin PASS37с. Это не повторный запуск прежних
clock19/journal22/storage21, не часть desktop1117/1192 и не native acceptance.
Own live library source8ca применяется в проверке отдельно от committed
App gitlink F61; поставка библиотеки и последующий pointer остаются отдельны.
Точная public offline recipe — в новой внутренней карточке
[регрессии lookup](../../update-reports/entries/android-20261030-lookup-cache-regression.md).


## NGFP lookup prefetch: отдельный reader — 30 октября2026

Только два maplibui callers — GISApplication.fillMissingLookupTablesForCollectorForm (Collector
form-only update) и LayerFillService.UnzipForm.execute (connected initial form) —
переведены на LayerUtil.fillLookupTableIdsForPrefetch(File). Typed walker читает
combobox.attributes.ngw_id: root array; рекурсивные type=tabs pages[].elements;
root object tabs[] через оба declared album_elements/portrait_elements. input_search
не меняет resource binding. Android getLong, malformed-present error и sentinel -1
сохранены; Long.toString и LinkedHashSet дают normalized first-seen dedup одного
вызова. Numeric options/defaults/field names/metadata не являются новыми NGW IDs.

Старые17 строк public fillLookupTableIds и весь NGWVectorLayerUI, включая два
legacy destructive reader вызова, побайтово прежние. Новый walker нельзя применять
к удалению shared lookup без отдельной политики. Далее кеш ищется по account+remote
ID, Combobox/AutoTextEdit читают уже локальный lookup. Helper test не доказывает actual
import/cache availability. UnzipForm.execute обычно пишет numbered form; старое удаление
читает unprefixed legacy form, а its otherIds loop rereads own form. Эти границы не исправлены.

AUDIT-TODO-NGFP-LOOKUP-PREFETCH остаётся открыт: same-hash installed missing-cache repair,
form/hash publication, partial retry/межзадачные дубли, numbered/shared GC, malformed
inactive layouts/native form lifecycle. Per-form dedup не global cache uniqueness.
AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE и AUDIT-TODO-NGW-PARTIAL-SNAPSHOT сохранены.
Account Table→Layer cast и целостный backup/exclusive owner gate не исправлены:
AUDIT-TODO-NGW-ACCOUNT-REMOVAL-BACKUP. Valid form draft/Walk reservation может оставить
слой при delete=false; это не доказывает безопасное account removal. Реальные
user/tenant/network/SQLite/native в этом срезе не использовались.

Actual SDK28: baseline12/3 named AssertionFAIL/9PASS → те же12PASS/0errors/skips,
XML2.149с, Gradle0/49с; LisaDebug source compile включён. Belka Java/Kotlin0/41с
отдельно подтверждён. Prelaunch classifier и последовательность NOT_PASS: Debug-only
JAR allowlist ошибочно отметил4 Release Java JAR, PowerShell продолжил разрешённую
компиляцию после Python exit1. Post-readback exact100 совпал с approved list; cached
AGP9.1.0/output classes.jar подтвердили Java archives, без product/native tasks.
Это не retroactive gate PASS. Checked-stage wrapper теперь проверяет LASTEXITCODE
после каждого native call; exit7 control не создал would-launch marker/новый JVM.
Все owned процессы завершились естественно; technical JSON удалены finally.
[Public recipe и source hashes](../../update-reports/entries/android-20261030-ngfp-lookup-prefetch-regression.md).

Own-live UI source отдельно от committed App UI E66/maplib F61. Merge Commit
библиотеки до App pointer; actual обе import routes/device/Related/Full/выпуск APK
NOT_RUN. Старые cache9/clock19/journal22/storage21 и desktop1117/1192 не перезапускались.
Новые metadata official checks после C-интеграции NOT_RUN. UI39/142/19/F61 сохранён;
прежний clock drift не снят, общего validation/allgatesPASS нет.


## Явный206 и ошибка сохранения lookup — текущий Draft source30 октября2026

Изменения подготовлены в собственном [Core Draft PR #63](https://github.com/GeonicalSys/android_maplib/pull/63);
установленное приложение получит их после обычного выпуска. Core source c0a4/ca59
и тесты7d697/7ae829 принимаются отдельно от committed App library F61.

Канонический Desktop producer lh_mery читает lh_mera.lh_mery. Его managed idqgs
типа BIGINT копирует целочисленный PG PK, тогда как NGW назначает отдельный
физический feature ID. Android читает top-level id в Feature/local FID; fields.idqgs
не становится алиасом этого FID. Canonical mobile tracked=false, sync_type14,
direction FROM2 и CRS3857 допускают incoming pull при UI editable=false:
последнее не равно resource-class isRemoteReadOnly. Исходные34 inputs/13 links
сохранены в общей source-аудитной цепочке; данные и remote deployment не проверялись.

Полный streamed GET не посылает Range и не собирает диапазоны. RFC9110§15.3.7
требует определить Content-Type/Content-Range;206 может охватывать всю запись,
но данный route не доказывает это. JSON array и Content-Length не доказывают
полный inventory. Поэтому только downloadFullSnapshot при явном206 добавляет
один numIoExceptions и возвращает failed206 до error/input stream, length и
нового snapshot. Другие2xx,404/error/transient retry и tracked/diff routes прежние.
Ранняя prefix-очистка временных файлов до GET и прежняя отправка local changes
остаются: отказ не предшествует всем SQL/cleanup. Outer null-file gate не допускает
scan/backup/SQL применения именно этого полного снимка.

В обычном apply отсутствие NGW physical FID в принятом полном наборе может выбрать
локальный объект на удаление; существующие NEW/flags/invalid-geometry/backup gates,
transaction и post-commit attachment cleanup сохранены. Этот source trace не доказывает
actual206, потери данных или принятия SQL/full apply. Успешный неполный200, tracked,
initial import и count/revision races остаются AUDIT-TODO-NGW-PARTIAL-SNAPSHOT.

В двух существующих ветвях NGWLookupTable.sync false отsave теперь добавляет один
numIoExceptions к текущему SyncResult. Уже опубликованная память остаётся новой
в том же map; один save, RuntimeException/Error и equal-next no-write прежние.
Ни компенсирующей записи, ни гарантии диска/повтора не добавлено: риск остаётся
AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE. Готовность same-hash form/cache и shared
lookup GC остаются AUDIT-TODO-NGFP-LOOKUP-PREFETCH; account cleanup/backup27 —
AUDIT-TODO-NGW-ACCOUNT-REMOVAL-BACKUP. Эти отдельные вопросы не объявлены исправленными.

ProjectSyncRunner требует !part.hasError перед SyncRecoveryJournal.complete и
полного успеха проекта перед ProjectSyncState.markComplete. Новый IO error
блокирует ACK этой пары и обновление полного clock в текущем проходе, не откатывая
ранее подтверждённый push или успешный другой account. Это source-backed consumer
контракт, а не исполненный тест очереди/диска/restart. Queued manual path сообщает
project_sync_incomplete; legacy SyncResultUtil без иных flags возвращает generic
unknown IO. Нового обещания network text/automatic retry нет.

Те же19 SDK28 technical methods: baseline4 named AssertionFAIL/15controls/0technical,
затем19PASS/0fail/error/skips — Core4 XML1.905с и App15 XML2.298с, Gradle0/35с.
Core baseline остановился на первом assertNull; прочие206 guards исполнились только
в GREEN. App15 включает8 старых controls, revised false-save и6 новых; standalone15
отдельно не запускался. LisaDebug compile входит в unit graph; Belka source compile
0/39с,100 unique task SET равен reviewed100, порядок иной;7 owned PID завершились.
Первый compile dry observer потерял native-exit capture после Get-FileHash postprocess:
его UNKNOWN/NOT_PASS сохранён. Root исправил только owned Q helper, повторил dry0/11с,
после чего actual compile исполнен один раз. Это не retroactive/allgates PASS.

Полная public recipe находится в Core PR #63, файл
`update-reports/entries/maplib-20261030-full-snapshot-http206.md`.
[App internal свидетельства](../../update-reports/entries/android-20261030-sync-admission-regression.md)
сохраняют exact fixtures/baseline/GREEN. Старые keyset9/clock19/journal22/storage21/
prefetch12 и desktop1117/1192 не перезапускались. UI39/142/19/F61 не перепривязан;
новые metadata checks после этой записи ещё NOT_RUN. Прежние App clock drift и
исключённый Root wiki frontmatter остаются: общего validation/native/device/APK
acceptance нет. Merge Commit Core до App pointer; source Draft не равен release.
