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

Реализация подготовлена в matching ветках `codex/mobile-project-scripts` четырёх
owners: maplib, maplibui, app, desktop `lisa`. Production APK ещё не выпущен.
Порядок интеграции: maplib **Merge Commit** → maplibui **Merge Commit** → app
pins на fetched remote merge commits и **Squash Merge** → desktop/активация →
release version/APK matrix. До закрытия цепочки release APK не собирается и
не публикуется. Текущие submodule pins Draft PR не являются merged dependencies.

## Матрица review и интеграции

Все четыре PR открыты как Draft от `geoglyth`; целевые ветки пока не содержат
новый механизм. Строки ниже относятся к code commits, последующие docs-only
коммиты этой же ветки их не заменяют.

| Требование / owner | Code commit | PR / base | Зависимости и присутствие |
|---|---|---|---|
| Формат, изолированный runtime и native read API / maplib | `b208231be9ec3d77145fe74d75c42d3767350cb6` | [#40](https://github.com/GeonicalSys/android_maplib/pull/40), `master` | В task tip; Merge Commit первым |
| Hooks формы, warning/block и pinned draft / maplibui | `6161a796890f9860600e81e8637ce85b627fee51` | [#28](https://github.com/GeonicalSys/android_maplibui/pull/28), `master` | В task tip; зависит от maplib #40; Merge Commit вторым |
| Audit pilot, Application guard, интеграционные тесты / app | `c1d40646625cb05adb87707178bb00c576b6a244` | [#48](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/48), `my-maplibre` | В task tip; pins равны code commits двух строк выше; после merge заменить на fetched merge commits |
| Publisher, portable collect/create/clone, UI / desktop | `e7ef358a1a9efb3c9d15e6062e221998a7a6cebb` | [#159](https://github.com/GeonicalSys/lisa/pull/159), `main` | В task tip; общий API v1; активация после app integration |

Ранее согласованные maplib #39, maplibui #27 и app #47 уже слиты; их remote
результаты `a247a16d`, `57e28a60`, `0b871f71` являются предками этих task tips.
Новые четыре PR в этой задаче **не сливались**. Перед выпуском повторить fetch,
инвентаризацию открытых PR/веток и проверку включения по Git; наличие этой
таблицы не заменяет release closure audit.

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
