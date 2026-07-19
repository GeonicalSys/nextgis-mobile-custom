---
title: Roadmap Collector-проектов
type: roadmap
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/CollectorProjectCompositionSync.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileProvider.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/LayerBackupManager.java
---

# Roadmap Collector-проектов

## Статус

| Область | Статус | Следующее действие |
|---|---|---|
| `collector_project` / `layer_origin` metadata | реализовано | сохранять совместимость schema и legacy-safe fallback |
| Composition diff и apply | реализовано | расширять тесты ошибок и идемпотентности |
| Backup gateway перед rebuild/removal | реализовано | проверить восстановление backup на реальных attachment-наборах |
| Изолированные multi-project workspaces | реализовано | добавить безопасный archive/delete workspace |
| Project switch/registry | реализовано | поиск, фильтр и действия диагностики по проекту |
| Form sync | частично | ввести form hash, атомарную замену `ngfp` без reload геоданных |
| Config sync | частично | унифицировать hash и классификацию soft/hard changes |
| Local vector tiles | foundation включён | clipping/simplification, cache, style parity и lifecycle |

## Ближайшие задачи

1. Покрыть composition apply сценариями add/remove/reorder/form/config, повторным запуском и
   сетевым сбоем в середине операции.
2. Сделать полноценный form-only sync: временная загрузка, проверка `form.json`/`ngfp_meta.json`,
   атомарная замена и сохранение предыдущей формы при ошибке.
3. Добавить экран/действия проекта: поиск, последняя проверка, diff summary, отдельный sync,
   диагностика и безопасное архивирование workspace.
4. Определить политику удаления project workspace: успешный backup, отсутствие активной операции,
   закрытие текущей карты, обновление registry и возможность ручного восстановления.
5. Довести local vector tiles до production-уровня на тяжёлом read-only слое: корректный MVT,
   cache invalidation, identify, style parity, memory/disk limits и server lifecycle.

## Safety defaults

- Автоматизация управляет только `managed_by_project = true`.
- `manual_ngw`, legacy-слои и `.ngrc` не участвуют в destructive composition apply.
- Ошибка remote snapshot, diff, backup, form/config parse или fill сохраняет локальные данные.
- Editable слой не переводится в tile-render path без отдельного edit/sync design.
- Новые destructive операции сначала проходят dry-run/preview и fault-injection тесты.
