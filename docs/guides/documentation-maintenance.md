---
title: Сопровождение документации агентами
type: guide
last_verified: 2026-07-19
related_code:
  - docs/scripts/validate_docs.py
  - docs/scripts/scaffold_module_docs.py
---

# Сопровождение документации агентами

## Размещение

- Сценарий/ограничение одного модуля → `<module>/docs/README.md`.
- Entry points, packages, interfaces, settings, smoke → module manifest.
- Межмодульная связь → `registry/dependencies.yaml`.
- Неизменяемое правило продукта → `registry/invariants.yaml` + explanation.
- Blast radius и DoD → `registry/change-impact.yaml`.
- Операционная процедура → `runbooks/`.
- История конкретного merge/release → `history/` или `WHATS_NEW.md`.

## Maturity

- `draft`: разрешены подтверждённые TODO, документ ещё не полон.
- `maintained`: TODO запрещены; `last_verified` соответствует проверке по коду.
- `upstream`: локальный pack не обязателен, пока компонент не кастомизирован.

## Bootstrap

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-scaffold.ps1 `
  -ModuleId maplib -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-scaffold.ps1 `
  -ModuleId maplib
```

Scaffold не перезаписывает существующие файлы. После генерации агент обязан
проверить код, заменить TODO и только затем выставлять `maintained`.

## Проверка

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1
python docs/scripts/validate_docs.py --workspace-root . --module maplib --require-pack
```

При central docs change добавить строку в `docs/changelog.md`. В changelog
документации не записываются обычные module-local исправления, если central
контракт не изменился.
