---
title: Контракты значений и действий мобильного интерфейса
type: architecture
last_verified: 2026-11-04
related_code:
  - app/src/test/java/com/nextgis/mobile/fragment/SettingsWorkspaceMoveTest.java
  - app/src/main/java/com/nextgis/mobile/util/SelectMapPathPreference.java
  - app/src/main/java/com/nextgis/mobile/fragment/SettingsFragment.java
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/activity/ProjectChooserDialog.kt
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
  - app/src/main/java/com/nextgis/mobile/fragment/AttributesFragment.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/ModifyAttributesActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/TrackActions.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerEditingPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/util/NgwSyncProgress.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ControlHelper.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LoadLisaCollectorProject.java
  - maplib/src/main/java/com/nextgis/maplib/util/LisaCatalogLookup.java
  - maplib/src/main/java/com/nextgis/maplib/util/LisaCatalog.java
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-ru/strings.xml
  - maplibui/src/main/res/values/strings.xml
  - maplibui/src/main/res/values-ru/strings.xml
---

# Что означает мобильный интерфейс

При подготовке3.1.2.31/225 повторно просмотрены два Gradle file witnesses:
app меняет только production versionCode/versionName, maplib — только release
diagnostic version. Brand/sourceSets, application/provider/account IDs,
JTS1.20.0 и цепочки выбора цели/карты прежние. Обновлены только соответствующие
file hashes после сравнения diff; UI method/resource expectations не менялись.
Это проверка смысла version-only diff, не новый native результат.

Для каждого значения и действия нужно знать его смысл, источник, единицы,
условия появления и обновления, а также судьбу сохранённой работы. Видимая
подпись сама по себе этого не доказывает: время полного обмена отличается
от времени нажатия кнопки, выбранный проект — от его имени, черновик — от
сохранённого объекта, координата устройства — от центра камеры.

Машиночитаемый каталог находится в
[ui-contracts.json](../registry/ui-contracts.json). Он связывает пользовательский
контракт с конкретными телами методов и XML resource IDs, условиями Lisa/Belka,
защищённым состоянием, независимыми ожиданиями и явными неизвестными.
Каталог строится из записей `agent-system/knowledge/ui-contracts/`.
После подтверждённой правки записей выполни
`python -B docs/scripts/ui_catalogue_knowledge.py --write`. Проверка App
сверяет JSON с записями. Автономная копия и явный `--registry` читают JSON.
Repository IDs совпадают с существующим `registry/repositories.yaml`:
`root` означает корень Android checkout (модуль app находится в `app/`),
`maplib` и `maplibui` — отдельные закреплённые репозитории библиотек.
UI ID, module ID, repository ID, project/layer/FID и resource ID не взаимозаменяемы.
Связанные правила: `INV-UI-VALUE-PROVENANCE` в
[invariants.yaml](../registry/invariants.yaml), `AUTO-UI-SOURCE-CONTRACTS` и
`SMOKE-UI-VALUE-PROVENANCE` в реестре проверок. Этап — `MOB-UI-01`
[мобильной дорожной карты](../roadmap/mobile.md).

## Основные экраны и границы

### Показания и действия карты

| Элемент и ID контракта | Подтверждённый смысл | Различающий пример |
| --- | --- | --- |
| `tv_latitude` / `tv_longitude`, `UI-MAP-STATUS-COORDINATES` | WGS84 широта/долгота display Location устройства, с выбранным форматом/точностью текста | Переместить камеру при неподвижной позиции: панель не становится координатой камеры |
| `tv_zoom`, `UI-MAP-ZOOM` | Дробный уровень zoom камеры с суффиксом `z` | Это не масштаб чертежа 1:N и не точность GPS |
| `iv_ruler` / `tv_ruler`, `UI-MAP-SCALE-RULER` | Расстояние карты под экранным отрезком линейки, преобразованное через projection и preferred_units | Оно меняется при масштабе/проекции, а не является дальностью до цели или длиной объекта |
| `menu_locate`, `UI-MAP-LOCATE` | Разовое перемещение камеры к доступной позиции устройства | Разобрать отдельно fallback к первой пригодной extent, отсутствие fix и разрешения |
| Условный overlay autopan, `UI-MAP-CONDITIONAL-AUTOPAN` | Другая ветвь, со своим flag/порогом движения/in-screen/lock | Само обновление GPS-курсора не доказывает постоянного центрирования камеры |
| «Слежение», `UI-NAVIGATION-STAKEOUT-TARGET` | Вынос к выбранной геометрии; для линии/полигона — к найденной ближайшей точке | Это не обещание follow-camera и не автоматический центр объекта |
| `fl_compass` / `azimuth`, `UI-MAP-COMPASS` | Legacy ориентация устройства с настройкой севера и display rotation | Отдельно от camera bearing, нового heading sector и направления на цель |
| `tv_source` и `gnss_hud`, `UI-LOCATION-SOURCE-GNSS-HUD` | Provider/extras display fix и отдельно состояние внешнего приёмника/качество | Подключённый приёмник и число спутников не доказывают свежую пригодную координату |

Имя `mCurrentCenter` в обследованном MapFragment тоже относится к положению
устройства; имя переменной не источник её смысла. Центр камеры, его сохранённый
viewport и охват проекта устанавливаются по соответствующему camera/project
producer. Их нельзя получить из координат GPS-панели. Действия, настройки и
непроверенные ветви каждого элемента перечислены в JSON по указанному ID.

| Экран | Смысл и источник | Что проверять отдельно |
| --- | --- | --- |
| Выбор проекта | Имя показывается пользователю; callback передаёт выбранную project identity | Совпадающие имена, отмена, восстановление диалога |
| Переключение проекта | Registry активирует owning workspace после проверок занятости и сохранения карты | Track/edit/walk/fill, ошибка сохранения, старые асинхронные callbacks |
| Слои | Строка относится к layer ID; глаз управляет видимостью; pending badge относится к очереди | Порядок модели и списка, переиспользование строк, реальная карта после toggle |
| Идентификация | Просмотр использует существующие DB IDs; Edit зависит от политики слоя | Разрывы между FID, границы навигации, изменение прав во время просмотра |
| Формы | Типизированные controls и schema metadata проходят общий Save gate | Required, поля вне формы, pins, потеря процесса, provider failure |
| Работа без сети | Ручной sync проверяет сеть до старта службы; повтор идёт тем же путём | Сохранность очереди, фактическая доступность offline ресурсов карты |
| Синхронизация | Полный clock относится к active map; pending badge показывает локальную очередь | Другой workspace, partial uploads, регистрация tracker, cancellation |
| Управление треком | Rename/Visibility/Delete обращаются к выбранным track IDs и общему storage | Cancel, пустое имя, подтверждённый набор удаления, активный сеанс |
| Карта и приборы | Отдельные контракты различают camera, device fix и состояния приборов | Единицы, свежесть fix, autopan и состояние без координаты |

Подробности уже выделенных подсистем:
[проекты](collector-projects.md), [обмен и хранение](ngw-sync-and-storage.md),
[управление треками](track-controls.md), [отрисовка карты](map-rendering.md).
Порядок строк слоёв не подтверждает всю цепочку drag/reorder, а наличие кнопки
синхронизации не подтверждает полноту загруженных данных.

### Часы, кольцо и метки синхронизации

Существующие `UI-MANUAL-SYNC`, `UI-SYNC-COMPLETION-CLOCK` и
`UI-SYNC-PENDING-BADGES` уточнены без новых параллельных IDs. Общий
`LayersFragment.onCreateView` связывает `fragment_layers.xml` с `sync`,
`sync_progress_ring`, `sync_pending_badge` и `info`; отдельного flavor override
этой цепочки под `app/src` не найдено. Общие main declarations применимы к
Lisa/Belka, но APK merge и native redraw здесь **NOT_RUN**.

| Widget → binding | Producer и преобразование | Граница значения |
| --- | --- | --- |
| `info` → `updateInfo` → `setText` | Active map path → `ProjectSyncState.lastComplete` → `ControlHelper.getSyncTime`: `dd MMM` и `HH:mm`, default locale/timezone | Unix ms; нет года/секунд или относительной давности. Exact-owner/canonical-key process shield возвращает подтверждённое API-visible время, публикуя попытку после commit=true. Physical disk/restart остаётся отдельным oracle |
| `sync_progress_ring` → `refreshSyncProgressRing` | `NgwSyncProgress.snapshot`: scale1000, pull10 + push до30, monotone display с5% reserve до finish | Взвешенная работа очереди, без percent text. `finishSessionLocked` завершает progress и при ошибке; inactive UI скрывает/сбрасывает кольцо |
| `sync` → `isSyncActive`/receiver | `NGWSyncService.isSyncStarted OR ProjectOperationCoordinator.isDataSyncActive`; lifecycle broadcast и повторная проверка состояния | Busy/activity и окончание очереди отдельно от полного project ACK; SYNC_FINISH не доказывает успешную запись clock |
| `sync_pending_badge` / `layer_pending_badge` → async query / row binding | `collectPendingLayers`: raw `VectorLayer.isChanges` либо разрешённый track pending; group IDs распространяются вверх, row проверяет `mPendingChanges.contains(layer.getId())` | Булева локальная очередь, не число ошибок и не тот же ready-outbox gate полного clock. Результат принимает только прежний map/generation |

При cleanup failure до `markComplete` запись clock не начинается. После full-project
account/ACK/outbox, final-child-drain и cleanup gates выполняется финальная проверка
generation/interruption. Только затем sole writer вызывает `markComplete`; progress
finish и `SYNC_FINISH` не заменяют этот ACK. Getter читает видимый active map, а не
scoped background project.

Текущая библиотека защищает API-visible время в этом процессе для точного
SharedPreferences owner и canonical key: shield устанавливается до editor callbacks,
getter проверяет его до и после raw read; false/exception не публикуют новую попытку.
Последняя успешная invocation выигрывает даже при меньшем положительном timestamp.
Файл `full-project-sync-state`, canonical-path UTF-8 UUID и long-формат прежние;
миграции legacy global/account clock нет. Это не откат cache/physical XML: другой
owner или fresh process может прочитать failed cached value, попавшее на диск.
Whole исходные F61 source row/UI contract сохранены в новом coverage history;
датированные прежние drift/check results ниже не переписаны. API28 fault-model19
с теми же source/test hashes дал10 assertion FAIL до исправления и19 PASS после;
это исторический source checkpoint, не реальный failed-write/restart. Первичные AOSP
и disk/backup/restart oracles находятся в [sync/storage](ngw-sync-and-storage.md);
`AUDIT-TODO-SYNC-ACK-RECOVERY` остаётся открытой для этих исходов.

### Путь к картам и сброс настроек

`UI-SETTINGS-MAP-STORAGE-MOVE` и `UI-SETTINGS-RESET-WORKSPACE` описывают два
разных входа общего Lisa/Belka SettingsActivity. `menu_settings` открывает
GENERAL, а MAP header меняет fragment той же Activity. `map_path` и
`reset_settings` — Preference keys, у них нет `android:id`.

Chooser строит `chosenRoot/map` из `getExternalFilesDirs`. Тот же storage index
закрывает диалог без move; другой выбор требует доступного storage либо current
path вне перечисленных roots. Listener всегда возвращает false, в том числе
после admission: requested path/card/summary не становятся сохранённым результатом.
Оба входа сначала получают публичный `PROJECT_SWITCH` lease. Busy refusal
показывает wait message, сохраняя whole prefs, карту, result и чужого owner;
default destination для Reset тоже разрешается только под lease.

После admission caller/UI захватывает exact current map. Worker использует
её, retire-ит SQLite rendering sources до `MapBase.moveTo`, затем commit-ит actual path. Direct-field predicate
не создаёт отсутствующую карту; проверка owner/raw path и `resetMap` выполняются
под одним app monitor. Lease остаётся до terminal UI callback и cleanup.
`RESULT_OK` для `RELOAD_ACTIVITY_DATA` означает **REFRESH**, а не ACK переноса:
MainActivity перезапускается, а MapFragment также проверяет identity при resume.

Reset positive callback сам не меняет prefs. После нормального target/path
completion применяются прежние remove/default XML правила, resetMap,
district/init/delete/district/init. Существующая feature удаляет слои кроме
OSM/tracks; объём удаления не расширен. Failed target/commit и cancel после
retirement допускают refresh только при exact owner/raw actual path, без Reset defaults,
init или удаления. Ordinary commit false моделируется после cache mutation;
unexpected before-cache RuntimeException/Error сохраняет worker failure policy.

`MapBase.moveTo` переносит referenced layer directories, database sidecars и
map entry last. Registry/project.json, независимые journals/clock/draft keys
не входят в этот список. Partial rollback, restart и migration policy остаются
`AUDIT-TODO-WORKSPACE-KEY-MIGRATION`. Retirement — cancellation/generation fence,
не доказанный drain active renderer reads.21 actual-body JVM cases и source
compile не принимают native storage, визуальное восстановление или удаление данных.

### Доступность редактирования

Для managed Collector слой использует project edit policy и разрешённое
направление отправки; generic `is_editable` имеет отдельную роль. Проверка
выполняется при построении меню и повторно перед действием. Наличие vector
слоя или общего метода Save/PUT не означает доступность записи в конкретный слой.

По уточнению пользователя `lh_mera.lh_mery` и `lh_mera.nep` в текущем мобильном
приложении только для чтения. `area_origin` намеренно остаётся полем desktop/server
и не добавляется в мобильные конфиги ради совпадения списков колонок. Серверная
схема может попадать в локальный кэш независимо от статической формы; это не
разрешает редактирование. Будущее включение записи в эти слои потребует нового
разбора defaults, прежних значений и фактической серверной обработки.

## Что подтверждает проверка исходников

Из Android root:

```powershell
py -3.13 -B docs/scripts/check_ui_contracts.py
```

Проверка сопоставляет декларации и тела методов с просмотренными anchors,
структуру каталога и заявленные XML IDs. Она не запускает приложение,
не обращается к NGW/БД и не принимает геометрию, содержимое карты, расположение
кнопок, восстановление после сбоя или работу устройства.

Для первоначальных non-method фактов удерживаются семь file tripwires: getter
линейки/делегирование zoom, constants display freshness и Stakeout, два XML с
настройками карты/геопозиции и основной/русский каталоги подписей действий.
XML с reviewed widget IDs также удерживаются целиком, чтобы изменение binding,
handler или title при прежнем ID не считалось прежним контрактом.
После текущего source-only дополнения цели выноса каталог содержит39 контрактов,142 method
tripwires и19 non-method files. Исторический NGFP checkpoint39/134/17 добавил6 methods
и3 file tripwires, сохранив прежние38/128/14. Дополнение выноса добавляет8 адресных
method guards и2 file guards; остальные IDs, старые hashes, widget evidence,
coverage history и полный исходный F61 clock row сохранены. Для V4 вручную пересмотрены затронутые Settings/widget bodies и добавлены
admission/owner/terminal/reset/result-consumer anchors. General XML добавлен как
Preference-key evidence. В EN/RU изменён ровно один failure string: все прежние
378/375 значения и порядок независимо сохранены. Unaffected IDs/hashes удержаны.

`WF-SYNC-CLOCK-STORE` намеренно остаётся исходным F61 fingerprint: отдельный own
maplib clock fix ещё требует Merge Commit, app pointer и semantic pin review.
Предыдущая официальная проверка контура38/128/14 показала один известный
clock drift и **не являлась PASS**. Указанные39/134/17 относятся к сохранённому NGFP source-checkpoint. Дополнение
39/142/19 само по себе не является validator PASS: официальный полный запуск
должен сообщать реальные текущие результаты, включая отдельный F61 clock drift. Последний фактический запуск прежнего NGFP контура39/134/17 принят как
структурный source-каталог, но полный validator NOT_PASS ровно на прежнем
WF-SYNC-CLOCK-STORE;57 docs tests дали53 PASS/4 FAIL от того же clock guard.
Для нового39/142/19 полный запуск после интеграции ещё NOT_RUN. Actual lookup9
JVM относятся к отдельному cache producer, не к выполнению UI каталога. Другие app rows проверяются по текущим bytes;
альтернативных hashes, фильтра результата или обхода checker нет.
Они требуют semantic review при любом изменении
этих файлов; это осознанно более широкая проверка, чем fingerprint отдельного
метода. Формат/пороги не могут молча измениться при прежнем теле callback.
Обновление fingerprint вручную не заменяет сверку смысла и независимых ожиданий.

Программные controls могут иметь `setId` и подтверждённые обработчики без
`android:id` в layout. Для них нельзя выдумывать XML-свидетельство: смысл
подтверждается кодом, ограничение XML detector и отсутствие native-приёмки
указываются отдельно. То же относится к dynamically created project rows.
Общие main sources применимы к обоим flavors только при отсутствии отдельного
переопределения или guard; это не выполненная матрица Lisa/Belka.

## Независимые пилоты — NOT_RUN

Следующие ожидания задают будущую приёмку; перечисление не является прогоном:

- Движение камеры не меняет координату устройства. Autopan действует только
  в предусмотренном состоянии; missing fix и stale fix различаются.
- Busy track/edit/walk/fill сохраняет прежний проект. Успешное переключение
  меняет owning workspace, а запоздалый callback другого проекта не меняет UI.
- Read-only слой остаётся доступным для просмотра без Edit; текущие лесосеки
  и НЭП не получают мобильного writer-требования для `area_origin`.
- Навигация по разреженным FID выбирает следующую существующую запись,
  а не вычисляет следующий номер арифметически.
- Save не теряет черновик и поля вне формы; validation/provider failure
  не выдаётся за сохранение. Повтор одной операции не создаёт дубликат.
- Partial upload не обновляет полный clock; badges не переносятся между
  workspace, а неизвестная регистрация не считается подтверждённой отправкой.
- Cancel Rename/Delete сохраняет трек; пустое имя отвергается, подтверждённые
  IDs удаления не подменяются новым mutable selection.

Native проверки, устройства, AVD и сборки требуют отдельной авторизации и
штатных gates. Source PASS не возобновляет остановленные этапы.

## Как исследовать неизвестное самостоятельно

Начать с конкретного виджета и listener, затем проследить состояние и formatter
до исходного значения: model, owning map, SQLite, service, NGW metadata либо
device event. Зафиксировать единицы и преобразования, условия отсутствия и
устаревания, точки обновления, read-only и flavor guards. Не восстанавливать
смысл только по screenshot, resource name или совпавшему числу.

Для сохранённой работы проследить commit/journal/pins, повтор и отказ. Для
ресурса NGW пройти в обе стороны до Collector, mobile config и канонического
desktop/PostgreSQL производителя по
[общему маршруту зависимостей](lisa-ecosystem.md). APK не читает desktop workspace
напрямую. Новую подтверждённую связь сохранить в owner registry в той же задаче.

После source-разбора сформулировать независимое ожидание и контрпример, затем
выбрать проверку, которая их различает. Если источник, единицы или ветвь ещё
не установлены, оставить конкретный UNKNOWN и следующий шаг. Изменение body
hash не должно автоматически утверждать правильность нового поведения.

## Высота текстового поля и ссылки на ресурсы

`UI-FORM-TEXT-LINE-CAP` прослеживает `type=text_edit` через factory,
`formtemplate_edittext.xml` и normalized attributes.field до TextEdit.init.
`max_string_count` читает int и вызывает single-line при значении<2 либо
setMaxLines; единица — строка отображения, а не символ/NGW ID/значение поля.
Все3 canonical `descript` имеют cap4; label —1/10/1 для линий/точек/площадей.
Text/draft/cursor/default priority и login overrides самостоятельны. getValue и
saveState используют текст; layout cap сам не подтверждает Save или sync.
У dynamic field нет `android:id`; XML фиксирует класс, control map — field key.

Ресурсные bindings NGFP имеют собственную grammar: combobox.attributes.ngw_id
(regular/AutoTextEdit) и meta.ngw_connection.id. Server URL/account определяют
namespace, а числа в cap, defaults/options, field names, local rows, srs.id и
business GeoJSON к remap ресурсов не относятся. Скриптовые deployment bindings
Collector metadata и динамические photo attachment URLs — отдельные контракты.
Текущие неизвестные сохраняются в едином LISA dependency-audit-todo.yaml:
AUDIT-TODO-NGFP-EMBEDDED-CONNECTION, AUDIT-TODO-NGFP-REFERENCE-COERCION,
AUDIT-TODO-NGFP-LOOKUP-PREFETCH и AUDIT-TODO-NGFP-EXISTING-CLONES.
Ни source review, ни будущий remap fix не доказывают исправление прежних
артефактов или работоспособность nested lookup prefetch/native UI.


### Сообщения о сбоях: источник и границы

| Контракт | Подтверждённый путь | Различающий oracle |
| --- | --- | --- |
| UI-SYNC-FAILURE-MESSAGES | exact account/thread typed causes → local result Bundle → queue/late child gates → resource-only dialog/notification | SERVER-only против mixed/auth/local/late/foreign; offline после создания presentation |
| UI-NGW-RESOURCE-LOAD-ERRORS | structured children result → async adapter → existing fitted dialog | successful[] против500/503/403; connect failure остаётся отдельной ветвью |
| UI-LAYER-IMPORT-ERROR-MESSAGES | typed HTTP/owned resource allowlist → LayerFill progress/Toast | server503/raw URL/SQLite/JSON против собственных human errors; JSON missing field не403 |
| UI-FORM-LEGACY-LIST-ERRORS | existing list validation → integer resource Toast overload | три invalid cases выводят текст, valid-row контроль сохраняется |

Эти programmatic surfaces не получают выдуманных XML widget IDs. Каталог содержит
точные reviewed method/resource witnesses, old F61 clock row сохраняется целиком.
Shared copy сообщает о возможных техработах, не доказывает обслуживание всего
сервиса. Typed output local-only: четыре primitives плюс framework owner reference,
не UI payload/IPC/store. Existing partial ACK/edit/journal policy отдельна от текста.
Технические source/model89 PASS (Core46/App43); native windows,
large fonts, notification permission/channel, effective locale/variant merge и
foreground-only MESSAGE_ALERT delivery требуют отдельной приёмки.


## Выбор ресурсов ВебГИС: флажок, способ импорта и восстановление

`UI-NGW-RESOURCE-CHECKBOX-SELECTION` связывает `NGWResourcesListAdapter.getResourceView/setCheckBox` с
`CheckState` и обоими `SelectNGWResourceActivity/Dialog.createLayers`. Для строк
raster/WMS/webmap mode1 означает raster, mode2 у vector/PostGIS — vector, у
Collector — импорт проекта. Styled vector имеет оба режима. Physical `checkBox1`
не является номером режима: безстилевой vector и Collector используют его с
`checkNo=2`. Подпись `type1` задаётся при каждом relevant bind, включая возврат
vector/Collector → raster/WMS/webmap; XML default недостаточен при recycling.

Источник флажка — ANY положительный requested flag у совпавшего process-local
`CheckState.id`, не последний элемент списка и не remote NGW ID. Bind снимает
старый listener, один раз устанавливает полное состояние (пустой/несовпавший
список даёт false), затем возвращает listener; сам bind не меняет список выбора.
При false callback очищает requested mode у ВСЕХ записей того же localID и
удаляет только ставшие bothfalse. При true существующий положительный mode
не размножается; иначе меняется первая matching запись либо создаётся одна.
Other mode/IDs/order/object identity остаются. Это не общая canonicalization
внешнего setter/legacy parcel списка: положительные дубликаты могут сохраняться,
а оба import loops по-прежнему обходят записи отдельно; generic import dedup
этим исправлением не обещан. PostGIS login guard не изменён.

`Resource.getId()` берётся из process-local `Connections.getNewId()`. Оба import
consumer сначала делают `connections.getResourceById(check.getId())`, затем
используют отдельный `resource.getRemoteId()` для remote task/origin. JSON save
хранит account/server + remote path и raster/vector flags. Restore КАЖДОГО
элемента сначала проходит прежний resolve и чтение обоих boolean; затем одинаковый
resolved localID объединяется OR flags в первой новой owned записи с первым
порядком. Разные localID/accounts не объединяются. Missing resource, invalid later
flag, network/cancel failure не становятся successful restore из-за dedup.
Falsefalse-only record не получает новую фильтрацию.

Новый JSON формат и миграция не введены. Activity сохраняет поддержку legacy
Connections/CheckState Parcelable; CheckState parcel содержит local int ID и
два boolean. Dialog без нового JSON восстанавливает accounts/root и очищает
checks. Ни setter, ни Parcel не нормализованы. Refresh/tree reconciliation,
удалённые ресурсы и исчезающие flags остаются в едином Root TODO `AUDIT-TODO-NGW-RESOURCE-BROWSER-REFRESH`.
Это не UI для координат/выбранной геометрии и не разрешение редактирования слоя.

Текущий checkbox срез: actual37 API28 models PASS (22+6 new и9 protected browser);
Belka source compile0/BUILD47s; native acceptance отдельно NOT_RUN. Native Service/import writes, accounts/network, rotation и merged-flavor resource
acceptance NOT_RUN. Исторические message89/Belka59 не перезапущены и не принимают
новый Adapter source. Whole F61 clock row и остальные43 contracts/163 methods/
39 non-method rows сохраняются; новые semantic pins ниже только после source review.


### Полные подпись, имя и путь resource selector — layout checkpoint после37

`UI-NGW-RESOURCE-CHECKBOX-SELECTION` получает additive caption_layout/source evidence. Три caption width=wrap_content сохраняют полный owned RU/EN текст. NgwResourceRowLayout сначала измеряет обычную горизонтальную строку; лишь фактический glyph clipping имени переводит две статические header/mode группы друг под друга. Перед обычным и full-width измерением локально forceLayout для каждого descendant обновляет getLayout до решения о размещении; parent requestLayout/reparent нет. При расширении та же строка возвращается в горизонтальную компоновку. Шрифт, IDs, порядок controls, listeners/CheckState/local-vs-remote identity/JSON/Parcel/import/refresh прежние. Shared breadcrumb layout переносит5dp child margin в5dp padding HorizontalScrollView: API28 clamp учитывает padding, а child margin не учитывает. Визуальный отступ сохранён, конец пути достижим в технической модели.
AOSP API28 primary references объясняют LinearLayout uniform-width remeasure и View dimension-only
measure cache/deferred onMeasure; forceLayout действует локально без parent request. Ссылки и exact
reviewed attributes/hashes в каталоге; это reference, не установленный OS/build телефона.
Существующие177 methods/44non-method/F61 и whole source37 widget/XML history сохранены.
Новые method/file witnesses — manual семантический review, не автоматическая перепривязка.
Checker fingerprints/IDs ловят source drift; полную copy/bounds проверяет independent behavioral oracle.
Фактически5 caption layout methods PASS/0 failures/errors/skips, XML10.06s. Ораклы и whole fixture SHA256 cc1fb961f2459d6b9e71097f403f5898b82aaa2425093b6157ee76edf03d06ac неизменны относительно предыдущего4PASS/1FAIL. Finalized Root receipt SHA256 68f7f6bdc072f9f25fd2af02e0584ac467a4dbb1a0c1b1e1959341b2126630ef. В этом запуске общий cohort10 НЕ PASS: 6 PASS/4 отдельные Panel assertion FAIL, Gradle exit1; 120 входов без drift, все13 owned processes settled absent, наблюдалось110.0625s. Это технические API28 CPU native Paint/text measurements, без Android device/OEM/GPU/display/GIS native/touch/rotation acceptance. Исторический cohort10 checkpoint предшествовал consumer compile. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.
Первый combined55 attempt: Core29 реально исполнился (15 assertion FAIL/14 controls), App0: fixture не скомпилировался из-за missing RobolectricTestRunner import. Старый Checkbox22 XML был STALE и исключён; предварительные51 copied cases не являются51 выполненной проверкой. V3 добавил ровно один импорт. Legacy App26 затем реально25 PASS/1 path FAIL; первые3 caption methods PASS на старом XML, поэтому это НЕ crop RED. NativeV4 class-only CPU glyph/font backend с setup calibration воспроизвёл4 assertion FAIL на старом XML. После3 caption width attrs Native4 дал2 PASS/2 assertion FAIL: имя Collector при280dp/font2RU и конец пути. Static responsive groups и перенос5dp margin→scroll padding дали4 PASS/1 transition FAIL в frozen5 fixture. Новая локальная инвалидация descendant measure cache проверяется теми же5: fixturecc1f, все assertions/setup неизменны. Core29 PASS относится к отдельной cache-проверке, combined55 PASS не заявляется. Исторические37/89 и их source pins не перезапускались.
Полная owned copy и CPU glyph bounds не принимают фактическое число доступных строк, touch targets/scroll/OEM/font/theme/rotation/display-size/installed APK/phone performance. font1.3 и реальные orientation/display combinations не исполнены. Единственный narrow Root risk AUDIT-TODO-MOBILE-RESOURCE-ROW-LAYOUT; broader UI audit продолжается, не all-screens PASS. F61 clock/Wiki/pointer blockers прежние.


### Поделиться выбранным треком

UI-TRACK-SELECTION-PANEL включает Share в обеих марках. Настоящая связь:
queryTrackIds → TrackInfo.id → refreshSelectedTrack bind → singleton IDs copy →
ExportGPXTask → shared provider → GPX FileProvider grant. Это не selected feature
FID, map-layer ID, recorder session, upload ACK или NGW export. Контракт включает
stale view/Activity/selection/mode guard, decoded XML-valid name и contained
basename; независимые method tripwires дополняют прежний неизменный bind witness.


Actual acceptance is compositional: Binding8 andExporter10 all18PASS inearlierfirstgreen23 on637a; itsfourPaneloverlapfailures werekept. Diagnostic-only64ac changedfailuremessage/helper only, notfivepredicates/oracles/setup.31Octactualcohort10 hadCaption5PASS+Panel1PASS4FAIL andexit1, NOTwhole10PASS; observedRename/Visibilityrowoverflow3px. Oneproductionline disablesbaselinealignment onlyownedactionRows. FinalONLYsamePanel5/64ac on0069 PASS0fail/errors/skips, XML19.913s, Gradle0/BUILD1m17s,81.6374157sobserved,120unchangedinputs/all13ownedsettledabsent. Thus23distinctmethodsacceptedACROSS18+5batches; noonefinal23rerun, no18/Caption5rerun, noMIME2executionclaim. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.; CPUfont/JVM isnotnativeAndroid/device/OEM/wholewindow/GPU/recipientacceptance.

## Значение даты и времени в форме — исходники 2026-11-01

Контракты `UI-FORM-DATE-STANDARD-VALUE` и `UI-FORM-DATE-NGFP-VALUE` в
[каталоге](../registry/ui-contracts.json) разделяют две реализации. Стандартная
форма выбирает `control.DateTime` по типу Field; NGFP `type=date_time` использует
отдельный `formcontrol.DateTime` и `date_type`:0 — DATE,1 — TIME,2 — DATETIME.
Это не номера schema-type и не внутренние selector constants. У корней обоих
layout нет отдельного `android:id`: привязка идёт через class/layout и field name.

Оба getter/state передают nullable Long миллисекунд эпохи, а локализованный
текст — отдельное представление. DATE callback не обнуляет скрытое время,
TIME callback использует date ints экземпляра; UTC-midnight DATE и фиксированный
TIME baseline здесь не подтверждены. Saved key имеет приоритет; чтение cursor
не содержит собственного isNull, а официальный Cursor API оставляет NULL и
нецелые getLong outcomes реализации. Фактический installed результат неизвестен.

У NGFP new-value `text` разбирается fixed pattern, затем `last` может заменить
timestamp. Shared Calendar и mValue seed различаются: первый click может сбросить
calendar carrier до принятого callback, если seed/default отличаются. Это
условное source-наблюдение с неизвестной политикой; не текущий bug или доказанный
Cancel result. Explicit-null Bundle/getter, absent control, copied-empty retention
и итоговый ContentValues не равны очистке поля. Дальнейшие required/cascade,
script, draft, owning-map/provider и Save gates сохраняют существующие контракты.

Это ограниченная source-карта для общих Lisa/Belka исходников. Private NGFP,
tenant schema/значения, zone/locale/clock, диалоги/native/rotation/remote roundtrip
не проверялись; source fingerprints требуют semantic review при изменении и
не заменяют device acceptance. Прежние контракты и coverage остаются целиком.


## Флажок NGFP и обычное integer-поле

`UI-FORM-CHECKBOX-NGFP-VALUE` связывает NGFP `type=checkbox` с
`formcontrol.Checkbox` через `formtemplate_checkbox`. У XML root нет
`android:id`: источник — фактический factory/class/layout, а не выдуманный ID.
Обычная форма для `FTInteger` создаёт `control.TextEdit`, который возвращает
String; NGW resource-selection и `finish_beep` имеют другие назначения.

| Граница | Подтверждённое значение |
| --- | --- |
| NGFP widget | checked-state Boolean; `getValue` выдаёт Integer1 либо0 |
| Bundle / optional last cache | Boolean под prefixed field key / normalized field key |
| Cursor | при наличии столбца `getInt()!=0`; SQL NULL/nonintegral/out-of-int result/exception implementation-defined по официальному API |
| Новый control | `init_value` через `JSONObject.getBoolean`, затем optional last; missing/invalid property может бросить исключение |
| Raw copy | NULL/empty может отображаться unchecked; отдельный equal-typed-baseline gate сохраняет исходную пустоту |
| Capture / Save | runtime Integer в ContentValues, затем прежние validation/owner/persistence gates; это ещё не local/server ACK |

Приоритет init: saved key → existing cursor → только новый control default/last.
Missing column при существующем cursor даёт локальный false, но не доказывает
такой исход для SQL NULL и не включает fallback в new default. Getter не
предоставляет explicit-null state. В выбранном пути с присутствующим checkbox сохранение copied NULL/empty зависит
от точного baseline equality; оно не отслеживает всю историю переключений.

Boolean в UI/Bundle не добавляет Android BOOLEAN schema support: общий mobile
flag остаётся integer0/1. Actual Field.type, NGFP type, required/hidden rules,
permission и Save eligibility — отдельные границы. Existing Date/Save/draft/
unbound contracts сохранены. После успешного base Save optional last-value
cache использует apply; его outcome не заменяет feature/NGW acknowledgement.

Срез source-backed:7records/4routes/4value namespaces;3official Android API
contracts отделены от installed implementation. Actual private forms/schema,
Cursor/AppCompat/OS, device lifecycle, data/provider/upload и fleet adoption
остаются UNKNOWN; widget/native execution и product fixes здесь не выполнялись.


## Вложения: ожидание и конечный кеш

`UI-ATTACHMENT-DOWNLOAD-STATE` закрепляет `progressImageDownload` и
`ib_download` реального `PhotoPicker`. Spinner читает local URL-list; кнопка
зависит от него и положительного размера конечного cache file. Preview может
показать картинку, не подтверждая получение полного файла. Metadata/URI, bytes,
static service marker и local picker state — разные границы.

Обычный `END` удаляет matching URL и обновляет adapter; full404 сохраняет
своё сообщение об отсутствии изображения. Online metadata не входит в
offline-only `PhotoPicker` saved-state selection. Полные условия и source
bindings сохранены в каталоге, включая whole-file witness внешнего
`updateStatus`, чей заголовок совпадает с отдельным внутренним методом.

Исправленный library producer завершает404 и публикует request-owned staging
после завершённого200/закрытия output через atomic move. Неподдерживаемый move
отклоняет попытку без copy fallback. Старый final и соседние entries сохраняются
при failure. См. [контракт библиотеки](../../easypicker/docs/README.md).
App со старым gitlink55aad ещё не получает это исправление: library merge и
последующий pointer update — отдельная delivery/adoption граница.

JVM-регрессии с техническим HTTP double не проверяют реальные фото, устройство,
GUI, сервер или разрешения. Старые повреждённые positive-size files,
silent-truncated200 integrity и concurrent preview/same-final policy остаются
UNKNOWN; checksum/Content-Length guarantee не добавлена.

Уже существующий positive-size partial из старой версии всё ещё может
удовлетворять текущей проверке размера `PhotoPicker`; автоматическая миграция
или удаление таких файлов не добавлены.

### Guard опциональной HTTP metadata

Для прежнего `UI-SYNC-FAILURE-MESSAGES` whole-file guard
`NGWLayerSchemaCompat` сохранён целиком в history каталога. Active scope теперь
закрепляет оба полных overload `fetchResourceMetaJson` с прежними неизменными
method bytes и теми же typed HTTP/null/classifier требованиями. Причина —
отдельное исправление class-only schema predicate в том же файле; оно не меняет
эти overload. Ожидания сообщений и остальные source guards не менялись;
checker не получил dual-hash или исключение. Это позволяет проверять один
UI contract на прежнем App gitlink и на исправленной библиотеке, не объявляя
доставку новой class repair в App до merge/pointer update.

## Загрузка мобильного проекта из WebGIS

`UI-MOBILE-PROJECT-LOAD` описывает действие **«Загрузить проект»** в меню
добавления слоя нашего приложения. `LayersFragment` передаёт его в
`MainActivity.loadCollectorProject`, затем в `LoadLisaCollectorProject`.
Это внутренний импорт в ЛИСА/БЕЛКА, а не запуск отдельного приложения Collector.
Названия `collector_project`, `CollectorResource` и resource IDs остаются
именами серверного протокола и исходников; пользовательские подписи говорят
«мобильный проект».

После проверки прерывания синхронизации выбирается подключение WebGIS. При
отсутствии аккаунта предлагается собственный `NGWLoginActivity`; единственный
аккаунт используется сразу, несколько требуют выбора. Этот маршрут ищет
серверный каталог с keyname `lisa`, а не произвольный каталог заказчика.
Список строится из прочитанных дочерних ресурсов с ограниченным обходом групп;
он не подтверждает полноту всего WebGIS. Пользователь выбирает строку, затем
нажимает **«Загрузить»**. До выбора кнопка выключена, выбор строки сам импорт
не начинает. Обрабатывается выбранный `LisaCatalog.Ref.remoteId`, а не поиск
по отображаемому имени. Это другой сценарий, чем `UI-PROJECT-CHOOSER` для уже
сохранённых рабочих областей; прежний контракт chooser/switch сохранён.

Выбранный ресурс читается через NGW API и `CollectorResource`. Неполный snapshot
или отсутствие поддерживаемых элементов останавливает импорт. Затем owning
registry готовит workspace и operation lease; `CollectorProjectImportHelper`
добавляет задачи собственного `LayerFillService`. Передача batch и вызов
`workspace.save()` не являются подтверждением полного скачивания проекта,
доставки scripts или успешной синхронизации. Права, данные, URI, форматы,
повтор/отмена и классы ресурсов этой правкой подписей не меняются.

Ключи переводов и placeholders сохранены. Method/XML/file guards закрепляют
просмотренную связь и ловят source drift; это не literal-mirror тест текста
и не приёмка native окон. Effective Lisa/Belka locale merge, крупный шрифт,
rotation, устройство, аккаунты и реальные серверные результаты здесь NOT_RUN.

## Два независимых вида предупреждений — 2 ноября2026

`UI-MAINTENANCE-REMINDERS` фиксирует существующую частоту: строго больше7 суток
от доступности неустановленного обновления либо complete clock активного
онлайн-проекта. При неизвестном complete clock отсчёт идёт от первого наблюдения
этого проекта. Проверка обновлений имеет отдельный clock. Local-only карта не
получает sync reminder. Foreground callback повторно сверяет project identity;
показ подавляется на24 часа только внутри UUID текущего процесса. После нового
процесса возможен повтор в тот же календарный день. Это существующая политика,
не изменение её на глобальный «раз в день».

`UI-RENDER-MEMORY-WARNING` относится к текущему ресурсному режиму карты.
Его automatic claim один на процесс, включая Activity recreation и смену карты;
ручной пункт меню независим. Continue закрывает сообщение; Reload вызывает
прежнюю переоценку resource/cache policy владельца карты. Данные и точность
геометрии не уменьшаются. ScrollableAlertDialogs резервирует полные действия
прежде прокручиваемого текста: прежний maintenance layout выделен в общий helper,
добавлен перенос neutral action. Прежние методы MapFragment, ruler/zoom properties
и все unrelated registry nodes проверены на точное сохранение при source review.
Глобальный runtime status registry остаётся NOT_RUN: отдельная component matrix
не сертифицирует весь UI и hardware.

Source review2ноября добавляет UI-DEBUG-UNDERLAY-TRANSFER-PROGRESS:
phase/monotonic elapsed/received counters → current control → localized status
ScrollView, fixed Cancel, lease/queued-cancel и late descriptor. Полный transfer
не отождествляется с окончанием bytes. UI-MAP-IDENTIFY-VISIBLE-VECTORS закрепляет
visible-only policy трёх tap/long-press callers независимо от old render flag.
WF-PROJECT-ACTIVATE повторно просмотрен: удалены только четыре строки старого
server clear; save/path/preferences/closeMapObj и native retirement сохраняются.
Новые source guards не являются выполнением native UI или реального переноса.
