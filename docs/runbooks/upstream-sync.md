---
title: Синхронизация с upstream NextGIS
type: runbook
last_verified: 2026-07-19
related_code:
  - tools/upstream-sync.ps1
  - .gitmodules
  - docs/history/upstream
---

# Синхронизация с upstream NextGIS

## До merge

1. Проверить status и ветку в root, `easypicker`, `maplib`, `maplibui`.
2. Прочитать `registry/upstream-overlaps.yaml`, invariants и последний cycle в
   [`../history/upstream/README.md`](../history/upstream/README.md).
3. Запустить:

   ```powershell
   pwsh tools/upstream-sync.ps1 -Mode Inventory
   ```

4. Для каждого репозитория записать текущий HEAD, upstream tip, ahead/behind и
   dirty state. Не начинать merge поверх неразобранных изменений.

## Backup и порядок

После проверки инвентаризации:

```powershell
pwsh tools/upstream-sync.ps1 -Mode BackupTags -Date YYYY-MM-DD
pwsh tools/upstream-sync.ps1 -Mode MergeSubmodules
```

Обычный порядок разбора: `easypicker`, `maplib`, `maplibui`. После сборки и
коммитов сабмодулей:

```powershell
pwsh tools/upstream-sync.ps1 -Mode MergeRoot
```

Скрипт не выполняет push, force, hard reset или amend.

## Разбор пересечения

Для каждого конфликтного файла:

- определить намерение upstream;
- найти invariant/upstream-hotspot;
- выбрать `upstream`, `ours` или `hybrid`;
- записать решение и rationale в cycle report;
- добавить/обновить smoke ID;
- при новом долговременном правиле обновить registry, а не только историю.

## Проверка

- unit tests затронутых библиотек;
- обе release flavors;
- smoke из overlaps/change-impact;
- pointers root ведут на доступные commits;
- module packs и central docs синхронизированы;
- validator проходит.

Push и публикация выполняются только по отдельной явной команде пользователя.
