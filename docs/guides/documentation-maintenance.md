---
title: Сопровождение документации агентами
type: guide
last_verified: 2026-07-23
related_code:
  - docs/scripts/validate_docs.py
  - docs/scripts/scaffold_module_docs.py
---

# Сопровождение документации агентами

## Размещение

- Сценарий/ограничение одного модуля → `<module>/docs/README.md`.
- Entry points, packages, interfaces, settings, smoke → module manifest.
- Межмодульная связь → `registry/dependencies.yaml`.
- Межпроектная связь с `standart_profiles`/QGIS Plugins →
  `registry/ecosystem.yaml` + `architecture/lisa-ecosystem.md`.
- Неизменяемое правило продукта → `registry/invariants.yaml` + explanation.
- Blast radius и DoD → `registry/change-impact.yaml`.
- Операционная процедура → `runbooks/`.
- История конкретного merge/release → `history/` или `WHATS_NEW.md`.
- Текущее пользовательское отличие от official для передачи upstream →
  `reference/official-differences.md`; история и уже устранённые отличия туда не попадают.

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
контракт не изменился. CI передаёт изменённые пути validator и включает
`--enforce-diff`, поэтому strict change-impact trigger должен сопровождаться
одним из указанных `must_update_docs`.

Если внешний проект доступен, validator проверяет его `docs_entry` и
`external_docs`; если недоступен — остаётся переносимый HTTPS `docs_url`.
Абсолютный current-machine path не используется как Markdown link и никогда не
попадает в Android runtime.
