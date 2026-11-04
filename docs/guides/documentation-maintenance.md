---
title: Сопровождение документации агентами
type: guide
last_verified: 2026-07-23
related_code:
  - docs/scripts/validate_docs.py
  - docs/scripts/scaffold_module_docs.py
---

# Сопровождение документации агентами

## Размещение

- Сценарий/ограничение одного модуля → `<module>/docs/README.md`.
- Entry points, packages, interfaces, settings, smoke → module manifest.
- Межмодульная связь → `registry/dependencies.yaml`.
- Межпроектная связь с `standart_profiles`/QGIS Plugins →
  `registry/ecosystem.yaml` + `architecture/lisa-ecosystem.md`.
- Неизменяемое правило продукта → `registry/invariants.yaml` + explanation.
- Blast radius и DoD → `registry/change-impact.yaml`.
- Операционная процедура → `runbooks/`.
- История конкретного merge/release → `history/` или `WHATS_NEW.md`.
- Текущее пользовательское отличие от official для передачи upstream →
  `reference/official-differences.md`; история и уже устранённые отличия туда не попадают.

## Дорожная карта

Обновляйте задачи и датированные свидетельства по
[порядку редактирования App](../../agent-system/README.md). После правки
запустите `python -B docs/scripts/prose_knowledge.py --write` из корня App.
Проверяйте итог по [прежнему адресу карты](../roadmap/mobile.md).

## Архитектура отрисовки

Правьте тему или датированное свидетельство по
[порядку редактирования App](../../agent-system/README.md). После генерации
проверяйте [архитектуру карты](../architecture/map-rendering.md).

## Производительность карты

Правьте правила кэша, очереди и отрисовки отдельно от датированных замеров.
Следуйте [порядку редактирования App](../../agent-system/README.md) и
проверяйте [проекцию по прежнему адресу](../architecture/map-performance.md).

## Стили и подписи

Правьте правила отрисовки и критерии проверки по отдельным темам.
Датированные результаты и прежний переход с HTTP храните в свидетельствах.
После проекции проверьте [матрицу стилей](../architecture/classic-style-verification.md),
[«Светлячок»](../architecture/point-firefly.md),
[полигоны](../architecture/polygon-style-controls.md) и
[линейные подписи](../architecture/visible-line-labels.md).

## NGFP и правила проекта

Правьте общую проверку формы, закрепление каскада и разрешения скриптов
в отдельных записях. После проекции проверьте
[правила NGFP](../architecture/conditional-form-rules.md),
[зависимые списки](../architecture/cascading-form-lists.md) и
[скрипты проекта](../architecture/project-scripts.md).

## Копирование, преобразование и тип объекта

Правьте права источника и назначения, отмену жеста и закрепление каскада
в записях соответствующих тем. Старые результаты телефона, настройки загрузки
и порядок интеграции храните в датированных свидетельствах. После проекции
проверьте [копирование и преобразования](../architecture/feature-copy-transform.md)
и [выбор типа](../architecture/feature-type-creation.md).

## Архитектура и сопровождение форка

Правьте границы владельцев и зависимостей отдельно от порядка upstream merge.
После проекции проверьте [архитектуру](../architecture/overview.md),
[модель форка](../architecture/upstream-fork-model.md),
[каталог кастомизаций](../reference/fork-customizations.md) и
[регламент синхронизации](../runbooks/upstream-sync.md).

## Maturity

- `draft`: разрешены подтверждённые TODO, документ ещё не полон.
- `maintained`: TODO запрещены; `last_verified` соответствует проверке по коду.
- `upstream`: локальный pack не обязателен, пока компонент не кастомизирован.

## Bootstrap

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-scaffold.ps1 `
  -ModuleId maplib -DryRun
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-scaffold.ps1 `
  -ModuleId maplib
```

Scaffold не перезаписывает существующие файлы. После генерации агент обязан
проверить код, заменить TODO и только затем выставлять `maintained`.

## Проверка

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1
python docs/scripts/validate_docs.py --workspace-root . --module maplib --require-pack
```

При central docs change добавить строку в `docs/changelog.md`. В changelog
документации не записываются обычные module-local исправления, если central
контракт не изменился. CI передаёт изменённые пути validator и включает
`--enforce-diff`, поэтому strict change-impact trigger должен сопровождаться
одним из указанных `must_update_docs`.

Если внешний проект доступен, validator проверяет его `docs_entry` и
`external_docs`; если недоступен — остаётся переносимый HTTPS `docs_url`.
Абсолютный current-machine path не используется как Markdown link и никогда не
попадает в Android runtime.
