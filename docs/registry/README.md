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
| `invariants.yaml` | Непереговорные продуктовые контракты |
| `change-impact.yaml` | Производное представление path triggers, связанных документов и проверок |
| `config-keys.yaml` | Preferences, metadata, storage и config keys |
| `upstream-overlaps.yaml` | Конфликтные области форка |
| `smoke-tests.yaml` | Стабильные IDs автоматических и device checks |

Локальные пути POSIX-relative от workspace root, без `..`, drive prefix и
секретов. В `ecosystem.yaml` внешнее расположение хранится отдельно как
операционная подсказка, а переносимый вход — HTTPS `docs_url`; доступные на
текущей машине external docs дополнительно проверяются validator. IDs уникальны
в пределах соответствующего файла; межфайловые ссылки проверяет
`validate_docs.py`.

Репозитории и модули редактируются в `agent-system/knowledge/repositories/` и
`agent-system/knowledge/modules/`. После правки обнови оба YAML командой
`python -B docs/scripts/keyed_registry_knowledge.py --write` из корня App.
Валидатор и `scaffold_module_docs.py` сверяют представления с записями.
Отдельное дерево документации, выбранное через `--docs-root`, читает свои YAML,
если рядом с ним нет источника App.

Триггеры change-impact редактируются в `agent-system/knowledge/change-impact/`.
После правки обнови YAML командой
`python -B docs/scripts/change_impact_knowledge.py --write` из корня App.
В отдельной поставке документации YAML можно читать по прежнему пути.
