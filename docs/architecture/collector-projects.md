---
title: Collector projects, composition sync и backups
type: architecture
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/CollectorProjectMetadata.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorProjectCompositionSync.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
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

## Изоляция

- У каждого проекта собственный map/workspace.
- Project metadata хранит identity, district, composition sync state и время
  последней проверки.
- Ручные NGW-слои должны маршрутизироваться в активный проект предсказуемо.
- Переключение проекта сначала сохраняет текущую карту, затем активирует другую.
- Ошибка подготовки нового workspace не должна разрушать существующий проект.

## Composition sync

Composition sync сравнивает серверный состав проекта с локальным. Добавление,
обновление и удаление имеют разные риски. Удаление локального слоя или schema
rebuild являются разрушительными действиями и подчиняются
`INV-BACKUP-BEFORE-DESTRUCTION`.

Если обязательный backup не создан, локальные данные сохраняются и
разрушительная операция отменяется. Backups создаёт `LayerBackupManager` в
`LayerBackups/`; архив для передачи — `ng-layer-backups.zip` с manifest.

## Config и feature data

Configuration sync и feature-data sync — разные контракты. `SYNC_NONE` для
данных не должен автоматически запрещать безопасное чтение конфигурации,
необходимое для отображения/форм, если конкретный flow это поддерживает.

## Проверки

- импорт Collector resource и создание отдельного workspace;
- переключение между двумя проектами без смешивания слоёв;
- добавление/переупорядочивание состава;
- backup и отказ от удаления при искусственной ошибке backup;
- district filter и form/render configuration;
- запуск/возврат после screen off во время большого layer fill.

Legacy-планы: [`../../COLLECTOR_ARCHITECTURE_ROADMAP.md`](../../COLLECTOR_ARCHITECTURE_ROADMAP.md)
и [`../../COLLECTOR_PROJECT_SETUP_GUIDE.md`](../../COLLECTOR_PROJECT_SETUP_GUIDE.md).
