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
| Project switch/registry | реализовано, включая atomic write и recovery scan | поиск, фильтр и действия диагностики по проекту |
| Resumable Collector import | реализовано | device fault-injection: kill процесса на разных стадиях большой партии |
| Form sync | реализовано: hash + файловая транзакция + rollback | end-to-end проверка реальной смены/удаления формы |
| Config sync | частично | унифицировать hash и классификацию soft/hard changes |
| `.ngrc` lifecycle | локальный immutable contract реализован | remote update только после спецификации нового сервера |
| Account sync scheduling | реализовано | длительный device soak с несколькими account и плохой сетью |
| Local vector tiles | foundation включён | clipping/simplification, cache, style parity и lifecycle |

## Ближайшие задачи

1. Провести rollout fault-injection на реальном большом проекте: process kill,
   reboot, потеря сети, нехватка места и обновление APK поверх распакованной `.ngrc`.
2. Покрыть composition apply сценариями add/remove/reorder/form/config, повторным запуском и
   сетевым сбоем в середине операции.
3. Добавить экран/действия проекта: поиск, последняя проверка, diff summary, отдельный sync,
   диагностика и безопасное архивирование workspace.
4. Определить политику удаления project workspace: успешный backup, отсутствие активной операции,
   закрытие текущей карты, обновление registry и возможность ручного восстановления.
5. После получения спецификации нового сервера определить manifest/version/hash и атомарную
   замену `.ngrc`; до этого подложка остаётся `immutable_local`.
6. Довести local vector tiles до production-уровня на тяжёлом read-only слое: корректный MVT,
   cache invalidation, identify, style parity, memory/disk limits и server lifecycle.

## Safety defaults

- Автоматизация управляет только `managed_by_project = true`.
- `manual_ngw`, legacy-слои и `.ngrc` не участвуют в destructive composition apply.
- Неполный remote snapshot не создаёт workspace и не применяется к существующему проекту.
- После process death повторно загружаются только отсутствующие/повреждённые слои.
- Ошибка remote snapshot, diff, backup, form/config parse или fill сохраняет локальные данные.
- Editable слой не переводится в tile-render path без отдельного edit/sync design.
- Новые destructive операции сначала проходят dry-run/preview и fault-injection тесты.
