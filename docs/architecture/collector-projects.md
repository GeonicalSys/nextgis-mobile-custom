---
title: Collector projects, composition sync и backups
type: architecture
last_verified: 2026-07-20
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/LayerContentProvider.java
  - maplib/src/main/java/com/nextgis/maplib/map/CollectorProjectMetadata.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorProjectCompositionSync.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorImportJournal.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorFormFileTransaction.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
---

# Collector projects, composition sync и backups

## Поток импорта

```text
NGW Collector resource
  → SelectNGWResourceActivity/Dialog
  → CollectorProjectMetadata (maplib)
  → CollectorProjectRegistry (maplibui)
  → isolated workspace + map
  → LayerFillService batch
  → composition sync / removal policy
  → project switch in MainActivity
```

`Connection.NGWResourceTypeCollector` — обязательный тип ресурса форка.
Collector project идентифицируется стабильным `project_uid`, построенным из
account и remote project id. Registry хранится в
`collector_projects_registry.json`, workspaces — в `collector_projects/`.

Импорт начинается только после полного чтения дерева Collector и всех ссылок на
векторные ресурсы. HTTP/JSON-ошибка в середине snapshot отменяет импорт до
создания новой рабочей области: частичный проект не считается допустимым
результатом. Повторный импорт различает слои по `account + remote_id`, поэтому
одинаковые отображаемые имена не приводят к пропуску разных слоёв.

## Изоляция

- У каждого проекта собственный map/workspace.
- `map.ngm`, `layers.db`, история треков и точки треков относятся к этому workspace;
  операции через `LayerContentProvider` каждый раз разрешают текущую карту приложения и не
  используют экземпляр, оставшийся от ранее открытого проекта.
- Project metadata хранит identity, district, composition sync state и время
  последней проверки.
- Ручные NGW-слои должны маршрутизироваться в активный проект предсказуемо.
- Переключение проекта сначала сохраняет текущую карту, затем активирует другую.
- Во время активной записи трека переключение запрещено. Это сохраняет весь сеанс в одной
  проектной базе и исключает попадание следующих точек в другой workspace.
- Ошибка подготовки нового workspace не должна разрушать существующий проект.
- Registry записывается атомарно с резервной копией. Если registry утрачен или
  повреждён, он восстанавливается сканированием существующих `map.ngm` в
  `collector_projects/`; пути за пределами этого каталога отвергаются.
- После переключения активный account ставится на ближайшую синхронизацию.

## Незавершённый импорт и обновление приложения

Полное описание текущей партии (`remote_id`, имя, form/config, порядок и число
оставшихся repair-попыток) хранит `CollectorImportJournal` в app-private storage.
Эти данные и сами workspaces переживают обычное обновление APK. Если Android
убил процесс во время загрузки, следующий запуск проверяет уже созданные
локальные таблицы и повторно ставит в очередь только отсутствующие или
повреждённые слои. Успешно загруженные тяжёлые слои повторно не скачиваются.
Если durable journal нельзя записать, новая партия или repair-wave не запускается:
существующие слои остаются на месте, а операция может быть безопасно повторена позже.

`LayerFillService` фиксирует target group в каждой задаче: партии для разных
групп не могут попасть в последнюю открытую группу. Замена слоя при изменении
схемы сначала полностью строится в новом каталоге; рабочий старый слой удаляется
только после успешного заполнения замены — это правило действует и на повторных
repair-проходах.

## Composition sync

Composition sync сравнивает серверный состав проекта с локальным. Добавление,
обновление и удаление имеют разные риски. Удаление локального слоя или schema
rebuild являются разрушительными действиями и подчиняются
`INV-BACKUP-BEFORE-DESTRUCTION`.

Если обязательный backup не создан, локальные данные сохраняются и
разрушительная операция отменяется. Backups создаёт `LayerBackupManager` в
`LayerBackups/`; архив для передачи — `ng-layer-backups.zip` с manifest.

Форма обновляется отдельной файловой транзакцией: проверяются серверный hash и
hash распакованных файлов, новая пара `form.json`/`ngfp_meta.json` ставится через
stage/backup/marker, а при прерывании восстанавливается прежняя согласованная
пара. Неполный remote snapshot вообще не применяется к локальной композиции.

## Config и feature data

Configuration sync и feature-data sync — разные контракты. `SYNC_NONE` для
данных не должен автоматически запрещать безопасное чтение конфигурации,
необходимое для отображения/форм, если конкретный flow это поддерживает.

Ручная `.ngrc`-подложка не является managed-слоем Collector и не участвует в
destructive composition apply. После импорта в её `config.json` сохраняются имя
исходного архива, SHA-256, время импорта и политика `immutable_local`. До
появления согласованного контракта нового сервера синхронизация не заменяет и не
удаляет распакованные тайлы.

## Проверки

- импорт Collector resource и создание отдельного workspace;
- переключение между двумя проектами в одном процессе без смешивания слоёв и треков;
- проект с сохранёнными треками → проект без треков → обратно: список, карта и новая запись
  используют базу текущего проекта без принудительного перезапуска приложения;
- добавление/переупорядочивание состава;
- backup и отказ от удаления при искусственной ошибке backup;
- district filter и form/render configuration;
- запуск/возврат после screen off во время большого layer fill.
- убийство процесса в середине партии и автоматическая докачка после запуска;
- отказ от импорта неполного snapshot и сохранение существующего workspace;
- ошибка/прерывание обновления формы с восстановлением прежней пары файлов;
- обновление APK поверх проекта с сохранением SQLite, форм и `.ngrc`-тайлов.

Практическая настройка: [collector-project-setup.md](../runbooks/collector-project-setup.md).
Незавершённые задачи: [collector.md](../roadmap/collector.md).
