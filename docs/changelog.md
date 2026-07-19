---
title: История документационной системы
type: changelog
last_verified: 2026-07-19
related_code:
  - docs
---

# История документационной системы

## 2026-07-19

- Документирован selective upstream 3.1.2 cycle: прямой импорт NGW-ресурса по URL,
  permission/read-only contract, критические crash/form fixes и production Sentry policy.
- Матрицы, module contracts и release notes обновлены до версии форка 3.1.2.1 / 192.
- `upstream-sync.ps1` теперь использует command-scoped `safe.directory` и не
  маскирует ненулевые exit codes Git.
- Удалены невоспроизводимые upstream diff snapshots; полезные команды, выводы и результаты
  перенесены в `docs/history/upstream/`.
- `CUSTOMIZATIONS.md` сокращён до compatibility-указателя, а актуальный тематический индекс
  перенесён в `docs/reference/fork-customizations.md`.
- Collector setup/roadmap и анализ map startup перенесены из корня в проверяемую структуру docs;
  roadmap очищен от уже реализованных milestone.
- `WHATS_NEW.md` актуализирован до 3.0.3.9 / `versionCode` 187.
- Создан автоматический agent entry через root/module `AGENTS.md` и Cursor rules.
- Добавлена центральная структура architecture/guides/runbooks/reference.
- Введены машиночитаемые repositories, modules, dependencies, invariants,
  change-impact, config, upstream overlap и smoke registries.
- Добавлены module packs, scaffold, validator, unit tests и CI workflow.
- `CONTEXT_INSTRUCTION.md` переведён в совместимый redirect на новую систему.
