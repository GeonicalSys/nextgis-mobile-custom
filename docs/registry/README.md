---
title: Машиночитаемые реестры
type: reference
last_verified: 2026-07-23
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
| `ecosystem.yaml` | Владельцы и контракты между Android, `standart_profiles` и QGIS Plugins |
| `invariants.yaml` | Непереговорные продуктовые контракты |
| `change-impact.yaml` | Path triggers, blast radius, docs и проверки |
| `config-keys.yaml` | Preferences, metadata, storage и config keys |
| `upstream-overlaps.yaml` | Конфликтные области форка |
| `smoke-tests.yaml` | Стабильные IDs автоматических и device checks |

Локальные пути POSIX-relative от workspace root, без `..`, drive prefix и
секретов. В `ecosystem.yaml` внешнее расположение хранится отдельно как
операционная подсказка, а переносимый вход — HTTPS `docs_url`; доступные на
текущей машине external docs дополнительно проверяются validator. IDs уникальны
в пределах соответствующего файла; межфайловые ссылки проверяет
`validate_docs.py`.
