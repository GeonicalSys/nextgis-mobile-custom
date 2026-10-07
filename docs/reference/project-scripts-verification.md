---
title: Project scripts — проверки и поставка
type: reference
last_verified: 2026-10-07
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectScriptsTest.java
  - maplib/src/test/java/com/nextgis/maplib/scripts/ScriptPackageTest.java
  - .github/workflows/android-reliability.yml
---

# Project scripts — проверки и поставка

Реализация принадлежит maplib, maplibui, app и desktop `lisa`. Production APK
не публикуется. Пользователь 7 октября разрешил слить накопившиеся изменения
и собрать **Lisa Debug без изменения версии**: `3.1.2.23`, `versionCode 218`.
Версии production Lisa/Belka остаются `3.1.2.27` / 221. Публикация APK не запрошена.

## Матрица интеграции, подготовленная перед merge

Инвентаризация всех owners выполнена после fetch: пять открытых PR, все от
`geoglyth`, без дочерних PR. Easypicker и publisher не имеют открытых PR или
неучтённых codex-веток. Отложенных строк нет. В desktop дополнительно входит
независимый PR #160; он не должен потеряться при интеграции publisher.

| Требование / owner | Исходный code commit | PR / target | Зависимость и gate |
|---|---|---|---|
| Формат, isolated runtime и read API / maplib | `b208231be9ec3d77145fe74d75c42d3767350cb6` | [#40](https://github.com/GeonicalSys/android_maplib/pull/40), `master` | Merge Commit `9b0aa6f1e05592d5e578e86ca0f4983f295eb9a7` уже получен через fetch; исходный tip является предком |
| Hooks формы, warning/block и draft / maplibui | `6161a796890f9860600e81e8637ce85b627fee51` | [#28](https://github.com/GeonicalSys/android_maplibui/pull/28), `master` | После maplib; Merge Commit `04d61abee1e9dfa1eed245d7b195c6fa18c075f4` уже fetched; исходный tip является предком |
| Audit pilot, Application guard, tests / app | `c1d40646625cb05adb87707178bb00c576b6a244` | [#48](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/48), `my-maplibre` | Pin двух fetched merge commits выше, Squash; после merge проверить tree/patch включение |
| Publisher и portable collect/create/clone / desktop | `e7ef358a1a9efb3c9d15e6062e221998a7a6cebb` | [#159](https://github.com/GeonicalSys/lisa/pull/159), `main` | Общий API v1; Squash после app integration; сохранить также #160 |
| T2 через установленный Chrome / desktop | `4c0d2fc3dc2b6fcefd511919c02a8261d93b25e5` | [#160](https://github.com/GeonicalSys/lisa/pull/160), `main` | Независимый Squash; проверить включение в общий desktop target |

App pins теперь указывают на **удалённые merge commits** библиотек, а не на
незамерженные review heads. Деревья merged библиотек совпадают с проверенными
review tips; добавлены только merge parents. Старые maplib #39, maplibui #27
и app #47 включены по ancestry (`a247a16d`, `57e28a60`, `0b871f71`).

Перед готовностью APK повторно fetch всех owners и проверить каждую строку:
ancestry для библиотек, tree/patch comparison для squash, точные app gitlinks,
отсутствие ещё открытых обязательных PR. Только после этого собрать debug из
слитого remote target и прочитать реальную package/version metadata из APK.
Полная production version matrix остаётся gate отдельного release.

## Автоматические результаты, 2026-10-07

Windows developer clone, JDK 21, AGP 9.1.0 / Gradle 9.3.1, compileSdk 36,
NDK 28.2.13676358 / CMake 3.22.1. Эмулятор Android 16 / API 36 / x86_64.

| Проверка | Результат |
|---|---|
| `:maplib:testDebugUnitTest` | 471 passed, 0 failures/errors/skips; 9 новых package/date/message тестов |
| `:maplibui:testDebugUnitTest` | 89 passed |
| `:app:testLisaDebugUnitTest` | 41 passed |
| `:maplibui:assembleDebug`, `:app:assembleLisaDebug` | passed |
| Lisa Release Kotlin + Java compile | passed, без release APK assembly |
| Belka Release Kotlin + Java compile | passed, без release APK assembly |
| Native `ProjectScriptsTest` | 7 passed |
| Native вместе с `RequiredFieldsTest` и `FormSaveRecoveryTest` | 14 passed, 0 failures/errors/skips |
| Полный Android reliability CI до merge | [run 37617250719](https://github.com/GeonicalSys/nextgis-mobile-custom/actions/runs/37617250719), success; полный reliability package |
| `llvm-readelf -lW` QuickJS | LOAD alignment `0x4000` у arm64-v8a, armeabi-v7a, x86, x86_64 |
| Desktop fake-NGW publication/clone/mobile-config tests | 64 passed, включая 9 новых package/delivery тестов |
| QGIS 3 dialogs | 3.44.14-Solothurn / Qt 5.15.13 / PyQt 5.15.11, passed |
| QGIS 4 dialogs | 4.2.2-Belém do Pará / Qt 6.11.0 / PyQt 6.11.0, passed |
| QGIS official compatibility checker | dry_run, 8 changed runtime Python files, 0 code findings; installed-PyQt5 warning |

Native сценарии проверяют другой Android UID, Unicode/emoji, отсутствие
require/fetch/std/os, новую VM, бесконечный цикл/OOM и следующий успешный запуск.
SQLite fixtures проверяют иной тип аудита, границы месяца, будущие даты,
`Нет значения`, существующий объект, SQL-injection строку, foreign ownership и
недопустимое поле. Реальная NGFP проверяет debounce/stale выбор, предупреждение,
recreate pin после изменения metadata, Return без insert, Continue с одним
insert и сохранение pin в черновике. Испорченный пакет не заменяет хороший кеш.
Все объекты синтетические и локальные; тесты не обращаются к PostGIS/NGW.

При разработке найден и исправлен Sentry app-start ASM probe, выполнявшийся до
Application guard: isolated UID не имеет права на getHistoricalProcessStartReasons.
Отключены только автоматические app-start metrics. Также исправлено завершение
Qt5 smoke: widgets уничтожаются до `exitQgis`, итоговый process exit — 0.
Первый общий JVM прогон выявил нестабильный cleanup существующего underlay-теста;
его повтор и итоговый полный maplib прогон успешны. Эти отказы не скрыты как pass.
Push CI после docs-only library pins отдельно потребовал обновления
`official-differences.md`; пропуск исправлен без изменения runtime. Проверка
полного PR diff до этого уже проходила, но не подменяет проверку каждого push.

## Живая WebGIS-проверка

Разрешённая группа [803](https://svetopaper.nextgis.com/resource/803).
Созданы только [Collector 953](https://svetopaper.nextgis.com/resource/953) и
служебный [vector carrier 954](https://svetopaper.nextgis.com/resource/954) с одной
точкой и ZIP attachment **12847**. Десять Collector items имеют `editable=false`.
Записи объектов подключённых PostGIS и изменения NGFP не выполнялись.

Пакет `contractor-audit` **1.0.0**, SHA-256 ZIP:
`314c409674cc7122779ece4f7e52eeac2f2b60d07e2c0764aa796f619da63342`.
Публикация выполнена новым desktop API, ZIP скачан обратно, hash/manifest/version
и полный Collector reference совпали; состав проекта сохранён.
Bindings: 807, 809, 811, 813, 815, 817, 819, 821, 823, 825.
Вопросы аудита сохранены. История проверяется локально на телефоне, не серверным
запросом из JS; живой проект проверяет доставку и не разрешает тестовый Save.

Автоматическая approval review отклонила первоначальное предложение создать
живой Collector с `editable=true`, поскольку оно открывало возможность записи
в запрещённый PostGIS. Заменено на проект только для чтения; отдельное разрешение
на редактирование данных не запрашивалось и не использовалось.

## Границы проверки

Не выполнены: физические ARM-телефоны, фактическая 16 KiB page-size загрузка,
весь Android reliability package на этой станции, mobile live account import
сервера, полный plugin load/unload в QGIS main window, launcher/UNC smoke,
production release/APK version matrix и публикация в парк. CI Draft PR
дополнительно выполняет полный reliability package; его результат нужно
проверить перед merge. Generic green CI не заменяет эти отдельные gates.

Пространственные пересечения, запрос отдельного объекта, нормативные таблицы
APK, lookup пакетных таблиц, присвоение полей и after-save пока отсутствуют.
[Архитектура и план расширения](../architecture/project-scripts.md).
