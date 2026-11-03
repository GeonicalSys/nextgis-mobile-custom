---
id: android-20261104-keyed-registry-readers
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:00:00+04:00
title: Валидатор читает инварианты, пересечения и проверки из записей App
why: Обновлять связанные контракты документации в одном редактируемом источнике
covers:
  - agent-system/README.md
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/registry/README.md
  - docs/registry/invariants.yaml
  - docs/registry/upstream-overlaps.yaml
  - docs/registry/smoke-tests.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/keyed_registry_knowledge.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_documentation_tools.py
---

MOB-DEV-02. Валидатор восстанавливает 224 определения и сверяет производные
YAML по прежним адресам. Отдельная копия документации читает эти YAML.
Scaffolder использует свою пару реестров при работе с шаблонами модулей.
