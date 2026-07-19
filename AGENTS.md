# GeonicalSystem NextGIS Mobile fork — инструкции для ИИ-агентов

Этот workspace содержит Android-приложение `android_gisapp` и отдельные
git-сабмодули `maplib`, `maplibui`, `easypicker`. Основной язык общения с
пользователем — русский; имена классов, Gradle-задач и файлов сохраняй как в
коде.

## Обязательный вход

Перед изменением кода прочитай:

1. [`docs/START-HERE.md`](docs/START-HERE.md) — ментальная модель и маршрутизация.
2. [`docs/guides/change-checklist.md`](docs/guides/change-checklist.md).
3. Локальные `AGENTS.md`, `docs/README.md` и `docs/manifest.yaml` затронутых
   модулей.
4. [`docs/registry/change-impact.yaml`](docs/registry/change-impact.yaml) —
   blast radius, обязательные документы и проверки.
5. [`docs/registry/invariants.yaml`](docs/registry/invariants.yaml) и, для
   upstream-задач,
   [`docs/registry/upstream-overlaps.yaml`](docs/registry/upstream-overlaps.yaml).

Для обзорной read-only задачи достаточно `START-HERE.md` и относящихся к теме
registry/docs. Общую карту отличий открывай в
[`docs/reference/fork-customizations.md`](docs/reference/fork-customizations.md),
а затем переходи в более узкий документ.

## Репозитории и git

- Root: `android_gisapp`; сабмодули: `maplib`, `maplibui`, `easypicker`.
- Рабочая ветка форка обычно `my-maplibre`; всегда проверяй фактическую ветку.
- У каждого репозитория `origin` — GeonicalSystem, `upstream` — NextGIS.
- Не выполняй `commit`, `push`, merge, rebase, изменение `git config` или
  разрушительные git-команды без явного запроса пользователя.
- Workspace может быть dirty. Сохраняй пользовательские изменения и не
  подмешивай к ним несвязанные правки.
- Upstream merge: сначала инвентаризация и backup tags, затем
  `easypicker -> maplib -> maplibui -> root`; каждое пересечение классифицируй
  как `upstream`, `ours` или `hybrid` и документируй.

## Критические инварианты

- Сохраняй flavors `lisa` и `belka`, их имена и application IDs.
- `versionName` форка: `<upstream-base>.<fork-patch>`; `app` и `maplib`
  синхронизируются согласно `INV-VERSION-COUPLING`.
- Не возвращай start/end flag layers треков без явного решения пользователя.
- Порядок `LayerGroup`: индекс `0` — нижний слой; импортированный NGRc/TMS не
  должен оказаться над всем пользовательским стеком.
- После hot-add raster список слоёв и MapLibre style должны совпасть без
  перезапуска.
- `NGWResourceTypeCollector` остаётся доступным в выборе NGW-ресурсов.
- Разрушительное удаление/пересоздание слоя не выполняется, если обязательная
  резервная копия не создана.

Полный список и ссылки на код:
[`docs/registry/invariants.yaml`](docs/registry/invariants.yaml).

## Definition of Done

1. Изучить затронутый код, локальный docs pack и change-impact.
2. Внести минимальное изменение и выполнить релевантные unit/build/smoke.
3. Обновить локальный `docs/README.md`, если изменились workflow, ограничение,
   диагностика или пользовательское поведение.
4. Обновить локальный `docs/manifest.yaml`, если изменились entry point,
   ключевой модуль, публичный интерфейс, setting, storage contract или smoke.
5. Обновить central docs/registry, если изменились межмодульные зависимости,
   инварианты, конфигурация, release/upstream процесс или blast radius.
6. При изменении central docs добавить запись в `docs/changelog.md` и обновить
   `last_verified` затронутых документов.
7. Запустить:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1 -RunTests
   ```

8. В финальном ответе явно перечислить обновлённые docs либо объяснить, почему
   изменение не затронуло документируемое поведение.

## Сборка по области изменения

| Область | Минимальная автоматическая проверка |
|---|---|
| Только `maplib` | `.\gradlew.bat :maplib:testDebugUnitTest` |
| `maplibui` | `.\gradlew.bat :maplibui:assembleDebug` |
| `app`, общие API, flavors | `.\gradlew.bat :app:assembleLisaRelease :app:assembleBelkaRelease` |
| Gradle/SDK/dependencies | обе release-сборки + затронутые unit tests |

Ручные device-smoke выбирай из
[`docs/registry/smoke-tests.yaml`](docs/registry/smoke-tests.yaml).

## Безопасность документации

- Не записывай значения DSN, токенов, client secrets, паролей, ключей подписи
  или содержимое `local.properties`/секретных properties.
- Можно документировать только имя ключа, источник и безопасный способ
  проверки.
- Не копируй один контракт в несколько мест: human explanation хранится в
  Markdown, точный ID/связь — в `registry/`, локальная навигация — в manifest.
