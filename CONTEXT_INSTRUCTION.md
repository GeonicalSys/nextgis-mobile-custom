# AI context moved to the automatic documentation system

Этот файл сохранён как совместимый путь для старых чатов и закладок. Вручную
вставлять его в новый чат больше не требуется.

ИИ-агент автоматически начинает с [`AGENTS.md`](AGENTS.md), затем использует:

1. [`docs/START-HERE.md`](docs/START-HERE.md) — модель проекта и маршрутизация;
2. [`docs/registry/change-impact.yaml`](docs/registry/change-impact.yaml) —
   blast radius, обязательные документы и проверки;
3. локальный `AGENTS.md` и `docs/manifest.yaml` затронутого модуля;
4. [`docs/registry/invariants.yaml`](docs/registry/invariants.yaml) — стабильные
   продуктовые контракты;
5. [`docs/registry/upstream-overlaps.yaml`](docs/registry/upstream-overlaps.yaml)
   для синхронизации с NextGIS.

Каталог долговременных отличий форка:
[`docs/reference/fork-customizations.md`](docs/reference/fork-customizations.md).

Человеческий вход: [`docs/README.md`](docs/README.md).

Проверка системы:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1 -RunTests
```

Последняя миграция контекста: 2026-07-19.
