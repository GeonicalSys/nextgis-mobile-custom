---
title: Машиночитаемые реестры
type: reference
last_verified: 2026-11-04
related_code:
  - docs/scripts/validate_docs.py
---

# Машиночитаемые реестры

Registry — структурированный источник истины для агентов и validator.

| Файл | Назначение |
|---|---|
| `repositories.yaml` | Производное представление Git boundaries, remotes, branches, docs policy |
| `modules.yaml` | Производное представление Gradle modules, entry points, ownership |
| `dependencies.yaml` | Compile/runtime/API связи |
| `ecosystem.yaml` | Владельцы и контракты между Android, `standart_profiles` и QGIS Plugins |
| `invariants.yaml` | Производное представление продуктовых контрактов |
| `change-impact.yaml` | Производное представление path triggers, связанных документов и проверок |
| `config-keys.yaml` | Preferences, metadata, storage и config keys |
| `upstream-overlaps.yaml` | Производное представление конфликтных областей форка |
| `smoke-tests.yaml` | Производное представление стабильных IDs автоматических и device checks |

Локальные пути POSIX-relative от workspace root, без `..`, drive prefix и
секретов. В `ecosystem.yaml` внешнее расположение хранится отдельно как
операционная подсказка, а переносимый вход — HTTPS `docs_url`; доступные на
текущей машине external docs дополнительно проверяются validator. IDs уникальны
в пределах соответствующего файла; межфайловые ссылки проверяет
`validate_docs.py`.

Репозитории, модули, инварианты, пересечения с upstream и проверки
редактируются в одноимённых каталогах `agent-system/knowledge/`.
После правки обнови YAML командой
`python -B docs/scripts/keyed_registry_knowledge.py --write` из корня App.
Валидатор сверяет все пять представлений с записями. `scaffold_module_docs.py`
сверяет репозитории и модули.
Отдельное дерево документации, выбранное через `--docs-root`, читает свои YAML,
если рядом с ним нет источника App.

Триггеры change-impact редактируются в `agent-system/knowledge/change-impact/`.
После правки обнови YAML командой
`python -B docs/scripts/change_impact_knowledge.py --write` из корня App.
В отдельной поставке документации YAML можно читать по прежнему пути.
