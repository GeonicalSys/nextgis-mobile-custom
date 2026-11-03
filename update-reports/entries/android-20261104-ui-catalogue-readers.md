---
id: android-20261104-ui-catalogue-readers
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:43:00+04:00
title: Проверка UI читает контракты и свидетельства из записей App
why: Вести UI-контракты в одном источнике и сохранить автономную проверку
covers:
  - agent-system/README.md
  - agent-system/source-dispositions.json
  - agent-system/knowledge/change-impact/change-impact-ui-value-provenance.json
  - docs/architecture/ui-contracts.md
  - docs/changelog.md
  - docs/registry/change-impact.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/check_ui_contracts.py
  - docs/scripts/ui_catalogue_knowledge.py
  - docs/tests/test_ui_contracts.py
---

MOB-DEV-02. Штатный checker и валидатор используют записи App. Автономный
JSON остаётся доступным по прежнему адресу, а `--registry` сохраняет выбор
отдельного каталога. Строгая проверка влияния охватывает правки источника.
