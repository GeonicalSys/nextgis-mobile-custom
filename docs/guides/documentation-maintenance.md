---
title: Сопровождение документации агентами
type: guide
last_verified: 2026-11-06
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

## Мобильные проекты WebGIS

Правьте идентичность проекта, возобновление импорта и границы резервной
копии по отдельным темам. Датированные заявления и открытые задачи
дорожной карты сохраняют свои статусы. После проекции проверьте
[архитектуру](../architecture/collector-projects.md),
[настройку](../runbooks/collector-project-setup.md) и
[дорожную карту](../roadmap/collector.md).

## Треки

Правьте общее хранилище, перенос старых данных, текущую запись и выбор
трека по отдельным темам. Сохраняйте датированные результаты с границами
проверок. После проекции проверьте [общую историю](../architecture/shared-tracks.md)
и [управление треками](../architecture/track-controls.md).

## Восстановление, напоминания и статистика

Правьте владельцев черновиков, часы напоминаний и подтверждение статистики
по отдельным темам. После проекции проверьте
[восстановление](../architecture/crash-recovery.md),
[напоминания](../architecture/maintenance-reminders.md) и
[статистику](../architecture/mobile-usage.md).

## Диагностика и инциденты

Правьте политику событий, очередь доставки, разбор и восстановление
по отдельным темам. Датированные прогоны сохраняйте как свидетельства.
После проекции проверьте [отчёты](../architecture/error-reporting.md),
[инцидент и откат](../runbooks/incident-and-rollback.md) и
[разбор GlitchTip](../runbooks/glitchtip-triage.md).

## Сборка и выпуск

Правьте настройку среды, матрицу APK, правила обновления и приёмки
отдельно от датированных свидетельств. После проекции проверьте
[настройку рабочего места](../runbooks/workstation-and-build.md) и
[регламент выпуска](../runbooks/release-apk.md).

## Автоматизированные и устройственные проверки

Раздельно правьте область запуска, условия устройств, критерии сценариев
и приёмку. После проекции проверьте [автотесты](../runbooks/automated-tests.md)
и [проверки на устройстве](../runbooks/device-smoke-tests.md).

## Ручное профилирование карты

Правьте запуск, остановку и чтение данных раздельно.
После проекции проверьте [регламент профилирования](../runbooks/manual-resource-profiling.md).

## Хранилище подложек и перенос

Раздельно правьте идентичность ассета, ссылку проекта, журнал переноса,
доверие Debug и управление передачей. После проекции проверьте
[контракт хранилища подложек](../architecture/shared-underlays.md).

## Значения и доступность интерфейса

Правьте смысл действия, источник значения и доступность по отдельным темам.
После проекции проверьте [контракты UI](../architecture/ui-contracts.md) и
[крупный интерфейс](../architecture/ui-accessibility.md).

## Позиция и вынос

Правьте источник позиции, запись, расчёт и lifecycle наведения по отдельным темам.
После проекции проверьте [позицию и GPS](../architecture/location-pipeline.md) и
[азимут и вынос](../architecture/stakeout.md).

## Связи экосистемы

Правьте карту владельцев, обнаружение связей и контракты ресурсов раздельно.
После проекции проверьте [экосистему ЛИСА](../architecture/lisa-ecosystem.md).

The App module README at `app/docs/README.md` is also a generated view.
Edit the App module README topic records and regenerate with the same prose command.
The ordinary module validator checks its frontmatter and links.

For the App module manifest, edit the ordered records in
`agent-system/knowledge/app-module-manifest/` and run
`python -B docs/scripts/prose_knowledge.py --write`.
Keep the generated view at `app/docs/manifest.yaml` in the same commit.
Each repeated component path has a separate occurrence record.

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

Шаблоны App редактируются в записях `scaffold-*-template.json`.
Команда `python -B docs/scripts/prose_knowledge.py --write` обновляет три
файла для отдельной папки `docs` по прежним адресам.

Scaffold не перезаписывает существующие файлы. После генерации агент обязан
проверить код, заменить TODO и только затем выставлять `maintained`.

## Проверка

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1
python docs/scripts/validate_docs.py --workspace-root . --module maplib --require-pack
```

CI передаёт изменённые пути валидатору и включает `--enforce-diff`.
Для strict change-impact trigger обновите один из указанных `must_update_docs`.

Если внешний проект доступен, validator проверяет его `docs_entry` и
`external_docs`; если недоступен — остаётся переносимый HTTPS `docs_url`.
Абсолютный current-machine path не используется как Markdown link и никогда не
попадает в Android runtime.


## Журнал документации

При изменении центральной документации добавьте датированную запись.
Для правки одного модуля запись нужна при изменении центрального контракта.

1. В `agent-system/knowledge/documentation/` создайте запись по образцу
   `documentation-changelog-2026-11-06-owner-journal.json`. Дайте ей свой
   стабильный ID и дату. Имя файла должно совпадать с суффиксом ID.
2. В `documentation-changelog.json` добавьте связь `contains` сразу после
   подсказки по редактированию, перед первой датированной записью.
3. Выполните `python -B docs/scripts/prose_knowledge.py --write` и сверьте
   [журнал](../changelog.md). Сохраните запись и сгенерированный файл в одном коммите.

Диапазоны в `agent-system/provenance/documentation.json` описывают прежний
снимок журнала. Порядок новых записей задаёт список `contains`.
