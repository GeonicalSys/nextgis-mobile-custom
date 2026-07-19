---
title: Машиночитаемые реестры
type: reference
last_verified: 2026-07-19
related_code:
  - docs/scripts/validate_docs.py
---

# Машиночитаемые реестры

Registry — структурированный источник истины для агентов и validator.

| Файл | Назначение |
|---|---|
| `repositories.yaml` | Git boundaries, remotes, branches, docs policy |
| `modules.yaml` | Gradle modules, entry points, ownership |
| `dependencies.yaml` | Compile/runtime/API связи |
| `invariants.yaml` | Непереговорные продуктовые контракты |
| `change-impact.yaml` | Path triggers, blast radius, docs и проверки |
| `config-keys.yaml` | Preferences, metadata, storage и config keys |
| `upstream-overlaps.yaml` | Конфликтные области форка |
| `smoke-tests.yaml` | Стабильные IDs автоматических и device checks |

Все пути POSIX-relative от workspace root, без `..`, drive prefix и секретов.
IDs уникальны в пределах соответствующего файла; межфайловые ссылки проверяет
`validate_docs.py`.
