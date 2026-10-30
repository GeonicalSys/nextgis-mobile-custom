---
title: Контракты значений и действий мобильного интерфейса
type: architecture
last_verified: 2026-10-30
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
---

# Что означает мобильный интерфейс

Для каждого значения и действия нужно знать его смысл, источник, единицы,
условия появления и обновления, а также судьбу сохранённой работы. Видимая
подпись сама по себе этого не доказывает: время полного обмена отличается
от времени нажатия кнопки, выбранный проект — от его имени, черновик — от
сохранённого объекта, координата устройства — от центра камеры.

Машиночитаемый каталог находится в
[ui-contracts.json](../registry/ui-contracts.json). Он связывает пользовательский
контракт с конкретными телами методов и XML resource IDs, условиями Lisa/Belka,
защищённым состоянием, независимыми ожиданиями и явными неизвестными.
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
| `info` → `updateInfo` → `setText` | Active map path → `ProjectSyncState.lastComplete` → `ControlHelper.getSyncTime`: `dd MMM` и `HH:mm`, default locale/timezone | Unix ms в storage; на экране нет года/секунд или относительной давности. Закреплённый app maplib F61 допускает новое cached значение после commit=false; это не durable oracle |
| `sync_progress_ring` → `refreshSyncProgressRing` | `NgwSyncProgress.snapshot`: scale1000, pull10 + push до30, monotone display с5% reserve до finish | Взвешенная работа очереди, без percent text. `finishSessionLocked` завершает progress и при ошибке; inactive UI скрывает/сбрасывает кольцо |
| `sync` → `isSyncActive`/receiver | `NGWSyncService.isSyncStarted OR ProjectOperationCoordinator.isDataSyncActive`; lifecycle broadcast и повторная проверка состояния | Busy/activity и окончание очереди отдельно от полного project ACK; SYNC_FINISH не доказывает успешную запись clock |
| `sync_pending_badge` / `layer_pending_badge` → async query / row binding | `collectPendingLayers`: raw `VectorLayer.isChanges` либо разрешённый track pending; group IDs распространяются вверх, row проверяет `mPendingChanges.contains(layer.getId())` | Булева локальная очередь, не число ошибок и не тот же ready-outbox gate полного clock. Результат принимает только прежний map/generation |

При cleanup failure до `markComplete` запись clock не начинается. Failed commit
после успешного gate имеет другую ветвь: cached getter и disk/restart могут
различаться. Текущий `WF-SYNC-CLOCK-STORE` остаётся закреплён на прежнем maplib;
планируемое исправление библиотеки этим каталогом не объявляется выполненным.
Первичные AOSP ссылки, реальные retry callers и независимые disk/backup/restart
oracles находятся в [sync/storage](ngw-sync-and-storage.md). Единая задача —
`AUDIT-TODO-SYNC-ACK-RECOVERY`; точные failure branches **NOT_RUN**.

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
