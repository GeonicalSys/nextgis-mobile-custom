---
id: android-20261104-repository-topology-readers
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:00:00+04:00
title: Общий источник состава App для валидатора и шаблонов
why: Проверки и шаблоны документации используют один и тот же состав репозиториев и модулей
covers:
  - agent-system/README.md
  - agent-system/source-dispositions.json
  - docs/START-HERE.md
  - docs/changelog.md
  - docs/registry/README.md
  - docs/registry/modules.yaml
  - docs/registry/repositories.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/keyed_registry_knowledge.py
  - docs/scripts/scaffold_module_docs.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_documentation_tools.py
---

MOB-DEV-02. Оба штатных инструмента читают записи App и сверяют с ними
производные YAML. Отдельное дерево документации использует собственные
YAML через `--docs-root`. Проверки охватывают создание пакета, повторный
запуск, устаревшее представление и пропавшую запись.
