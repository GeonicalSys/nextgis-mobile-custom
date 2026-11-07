---
id: android-20261104-sectioned-registry-readers
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:29:00+04:00
title: Валидатор читает настройки, зависимости и границы из записей App
why: Завершить переключение центральных YAML-реестров на единый источник
covers:
  - agent-system/README.md
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/registry/README.md
  - docs/registry/config-keys.yaml
  - docs/registry/dependencies.yaml
  - docs/registry/ecosystem.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/keyed_registry_knowledge.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_documentation_tools.py
---

MOB-DEV-02. Штатный валидатор восстанавливает все словари и списки из
записей App. YAML сохраняют порядок, пояснения и доступ по прежним адресам.
Отдельная копия документации может читать эти YAML автономно.
