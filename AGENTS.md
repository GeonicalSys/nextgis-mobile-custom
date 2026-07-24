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

Для задач на границе desktop/mobile дополнительно прочитай
[`docs/architecture/lisa-ecosystem.md`](docs/architecture/lisa-ecosystem.md) и
[`docs/registry/ecosystem.yaml`](docs/registry/ecosystem.yaml). Связанные
проекты экосистемы:

- `Q:\standart_profiles` — launcher, QGIS runtime, брендовые профили и доставка;
- `%APPDATA%\QGIS\QGIS3\profiles\develop\python\plugins` — канонические
  исходники QGIS-плагинов и `geonical-docs`;
- этот workspace — Android-клиент ЛИСА/БЕЛКА.

Android не читает профили, plugin mirrors, `variables.py` или другие файлы
desktop workspace напрямую. Между desktop-плагинами и приложением общий
runtime-контракт проходит через NextGIS Web/Collector либо через явный импорт
поддерживаемого переносимого артефакта (например, offline basemap); скрытой
общей папки нет. Если меняется формат, идентичность, права, состав или семантика
такого ресурса, обнови документацию проекта-владельца и app-side ecosystem
contract в одной задаче.

Для обзорной read-only задачи достаточно `START-HERE.md` и относящихся к теме
registry/docs. Общую карту отличий открывай в
[`docs/reference/fork-customizations.md`](docs/reference/fork-customizations.md),
а затем переходи в более узкий документ. Для сравнения с текущим официальным
приложением и материалов, передаваемых upstream, используй
[`docs/reference/official-differences.md`](docs/reference/official-differences.md).

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
- Production-версия хранится в `defaultConfig`, а debug-only override — в
  `androidComponents.onVariants`. Для AGP 9.x запрещено добавлять application
  `versionCode`/`versionName` в `buildTypes`: такой DSL не поддерживается.
  В `maplib` debug меняет только явный `BuildConfig.VERSION_NAME`, а не library
  `buildTypes.versionName`.
- Любое изменение версии считается незавершённым, пока из корня
  `android_gisapp` не выполнен
  `tools\verify-apk-version-matrix.ps1`. Проверка обязана собрать Lisa Debug,
  Lisa Release и Belka Release, прочитать package/version из APK через `aapt`
  и подтвердить variant-specific версию maplib. Имя APK не является источником
  версии: production basename может сохраниться у debug, а publisher назначает
  каноническое имя только после чтения APK metadata. При намеренном bump обнови
  независимые ожидаемые значения в самом verification script.
- Не возвращай start/end flag layers треков без явного решения пользователя.
- Порядок `LayerGroup`: индекс `0` — нижний слой; импортированный NGRc/TMS не
  должен оказаться над всем пользовательским стеком.
- Каждая карта, включая изолированный Collector workspace, сохраняет один
  дефолтный `OpenStreetMap Standard aka Mapnik` прямым дочерним слоем с индексом
  `0`; composition sync не управляет и не удаляет его.
- После выдачи специального Android-разрешения updater автоматически продолжает
  установку проверенного cached APK; повторная ручная проверка обновлений не
  является допустимым штатным сценарием.
- После hot-add raster список слоёв и MapLibre style должны совпасть без
  перезапуска.
- `NGWResourceTypeCollector` остаётся доступным в выборе NGW-ресурсов.
- Collector импортирует vector/PostGIS как локальные векторные слои, а уже
  штатно распознаваемые `qgis_vector_style` и `qgis_raster_style` — как
  не редактируемые authenticated NGW raster layers в общем проектном порядке.
  Не расширяй таблицу типов `Connection.java` новыми классами стилей без
  отдельного продуктового запроса; поддержка Collector не является поводом
  «на всякий случай» добавлять неизвестные серверные типы.
- Разрушительное удаление/пересоздание слоя не выполняется, если обязательная
  резервная копия не создана.

Полный список и ссылки на код:
[`docs/registry/invariants.yaml`](docs/registry/invariants.yaml).

## Definition of Done

1. Изучить затронутый код, локальный docs pack и change-impact.
2. Внести минимальное изменение и выполнить релевантные unit/build/smoke. Нельзя
   отдавать handoff с непроверенным Gradle DSL: хотя бы минимальная затронутая
   задача должна быть фактически запущена и завершиться успешно.
3. Обновить локальный `docs/README.md`, если изменились workflow, ограничение,
   диагностика или пользовательское поведение.
4. Обновить локальный `docs/manifest.yaml`, если изменились entry point,
   ключевой модуль, публичный интерфейс, setting, storage contract или smoke.
5. Обновить central docs/registry, если изменились межмодульные зависимости,
   инварианты, конфигурация, release/upstream процесс или blast radius.
6. При изменении central docs добавить запись в `docs/changelog.md` и обновить
   `last_verified` затронутых документов.
7. При изменении пользовательского поведения или submodule pointer заново
   проверить актуальный official upstream и обновить
   `docs/reference/official-differences.md`. Уже принятые upstream или удалённые
   из форка возможности из файла удаляются, а не переносятся в историю.
8. При изменении desktop/mobile контракта проверить
   `docs/registry/ecosystem.yaml`, документацию `standart_profiles` и
   `geonical-docs`; локальные файлы внешних проектов не подменять ссылками на
   deployment mirrors.
9. Запустить:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1 -RunTests
   ```

10. В финальном ответе явно перечислить обновлённые docs либо объяснить, почему
   изменение не затронуло документируемое поведение.

## Сборка по области изменения

| Область | Минимальная автоматическая проверка |
|---|---|
| Только `maplib` | `.\gradlew.bat :maplib:testDebugUnitTest` |
| `maplibui` | `.\gradlew.bat :maplibui:assembleDebug` |
| `app`, общие API, flavors | `.\gradlew.bat :app:assembleLisaRelease :app:assembleBelkaRelease` |
| Gradle/SDK/dependencies | обе release-сборки + затронутые unit tests |
| Любая версия app/maplib, включая debug-only | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-version-matrix.ps1` |

Ручные device-smoke выбирай из
[`docs/registry/smoke-tests.yaml`](docs/registry/smoke-tests.yaml).

## Безопасность документации

- Не записывай значения DSN, токенов, client secrets, паролей, ключей подписи
  или содержимое `local.properties`/секретных properties.
- Можно документировать только имя ключа, источник и безопасный способ
  проверки.
- Не копируй один контракт в несколько мест: human explanation хранится в
  Markdown, точный ID/связь — в `registry/`, локальная навигация — в manifest.
