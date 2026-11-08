---
id: android-20261104-app-module-manifest-order
area: Документация
kind: internal
at: 2026-11-04T17:06:17+04:00
title: Порядок полей манифеста App после чтения SQLite
why: Восстановить исходный порядок YAML при сортировке ключей в JSON
covers:
  - docs/scripts/module_manifest_knowledge.py
  - docs/tests/test_documentation_tools.py
---

Явный порядок полей восстанавливает прежнее представление.
Адресная проверка воспроизводит сортировку ключей перед генерацией YAML.
