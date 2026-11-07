---
id: android-20261107-policy-source-retirement
area: Документация
kind: internal
at: "2026-11-07T17:16:48+04:00"
title: "Вход владельца и комплект документации App"
why: "Сохранить применимые правила при переходе на рабочие записи владельца"
covers:
  - ".cursor/rules/android-development-contracts.mdc"
  - ".cursor/rules/docs-maintenance.mdc"
  - ".cursor/rules/git-workflow.mdc"
  - ".cursor/rules/upstream-sync.mdc"
  - "AGENTS.md"
  - "agent-system/authored-policy.json"
  - "agent-system/knowledge/change-impact/change-impact-track-selected-gpx-share.json"
  - "agent-system/knowledge/documentation/change-checklist.json"
  - "agent-system/knowledge/documentation/documentation-maintenance.json"
  - "agent-system/knowledge/documentation/glossary.json"
  - "agent-system/knowledge/documentation/lisa-ecosystem-task-owner-routing-matrix.json"
  - "agent-system/knowledge/documentation/start-here.json"
  - "agent-system/knowledge/ecosystem/ecosystem-contracts-eco-dependency-discovery.json"
  - "agent-system/knowledge/policy/application.required-entry.json"
  - "agent-system/knowledge/policy/cursor-android-development-contracts.required-entry.json"
  - "agent-system/knowledge/policy/workspace.required-entry.json"
  - "agent-system/policy-migration.json"
  - "agent-system/provenance/structured-views.json"
  - "agent-system/source-dispositions.json"
  - "app/AGENTS.md"
  - "docs/START-HERE.md"
  - "docs/architecture/lisa-ecosystem.md"
  - "docs/glossary.md"
  - "docs/guides/change-checklist.md"
  - "docs/guides/documentation-maintenance.md"
  - "docs/registry/change-impact.yaml"
  - "docs/registry/ecosystem.yaml"
  - "docs/scripts/scaffold_module_docs.py"
  - "docs/scripts/validate_docs.py"
  - "docs/tests/test_documentation_tools.py"
source_commits:
  - 80e1423d4912908e4b4174fb201249387051df17
---

Ссылки и проверка комплектов используют правила владельца по путям изменения. Исторические источники проверяются по закреплённым адресам Git.

Замены объединяют проекторы, валидаторы и действующие ссылки. В независимой копии временно убираются только файлы из согласованного списка репетиции. Сохранённые значения и прежние обязательства проверяются отдельной квитанцией.

Проверка охватывает локальные источники. Состояние native, GUI, БД, Office/Access и устройств фиксируется отдельно.
