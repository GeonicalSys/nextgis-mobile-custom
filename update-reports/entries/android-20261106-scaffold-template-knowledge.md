---
id: android-20261106-scaffold-template-knowledge
area: Документация
kind: internal
at: '2026-11-06T05:04:26+04:00'
title: Шаблоны модульной документации читаются из записей App
why: Дать штатному генератору один редактируемый источник шаблонов
covers:
  - agent-system/README.md
  - agent-system/knowledge/documentation/scaffold-agents-template.json
  - agent-system/knowledge/documentation/scaffold-readme-template.json
  - agent-system/knowledge/documentation/scaffold-manifest-template.json
  - agent-system/knowledge/documentation/documentation-maintenance.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/guides/documentation-maintenance.md
  - docs/scripts/prose_knowledge.py
  - docs/scripts/scaffold_module_docs.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_documentation_tools.py
---

В рамках MOB-DEV-02 генератор читает три шаблона из записей App.
Проектор воспроизводит исходные байты для отдельной папки docs.
Валидатор сверяет эти файлы с записями. Поля подстановки, пробный вывод
и создание только отсутствующих файлов сохраняют прежний смысл.
