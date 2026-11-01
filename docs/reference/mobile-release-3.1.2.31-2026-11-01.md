---
title: Подготовка мобильного выпуска 3.1.2.31/225
type: reference
last_verified: 2026-11-01
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - tools/verify-mobile-tests.ps1
---

# Подготовка 3.1.2.31/225

Текущий статус: версии и Git-доставка закрыты, подписанные кандидаты собраны;
первый локальный Full завершён **FAILED: 256 PASS, 17 FAIL**. К публикации
сборка пока не допущена. Причины части FAIL ещё не установлены; число FAIL
не является числом подтверждённых продуктовых ошибок. Следующий этап —
адресные исправления и регрессии, [контрольная точка](../handoffs/2026-11-01-mobile-release-31231.md).

Поручение1 ноября2026: повысить версию и подготовить приложение к публикации.
Смартфон Samsung A56 подключён по ADB. Владелец отдельно разрешил программный
эмулятор на ноутбуке и запретил GitHub Actions. Реальная публикация и обновление
рабочего приложения не запрошены. Roadmap: MOB-REL-01.

## Матрица исходников до bump

Fetch preflight: app, три библиотеки и publisher clean/ahead0/behind0.
Во всех пяти репозиториях открытых PR и опубликованных codex branches нет.
База предыдущей224 — app29e87d03; последующий429e9804 только фиксирует публикацию.

| Владелец | Итоговый tip до bump | Закрытая цепочка после224 | Проверка включения |
| --- | --- | --- | --- |
| app, my-maplibre | 0f55e62fe27cc0f16b48080a7c35495b58e0e583 | #81–87, Squash | Merge commits присутствуют в ancestry целевой ветки; содержание squash tip проверяется по Git |
| maplib, master | 8cca5f4d6723bd6722a7f0c830b3a626a83d942a | #61–64, Merge Commit | App pin равен remote master; каждый merge находится в ancestry |
| maplibui, master | 2969d55e695befcda879f0fe916d10cf2f8b8eda | #45–47, Merge Commit | App pin равен remote master; каждый merge находится в ancestry |
| easypicker, master | 5947984e4617bb85839e610cc315434dd0a26678 | #5, Merge Commit | App pin равен remote master; fix terminal404/staging включён |
| upload_mobile, main | ed5f12da7d6a26de53992e80821c30cf1d9c6f32 | Текущий publisher | Remote main совпадает с clean checkout; source changes не нужны |

Version-only [maplib PR65](https://github.com/GeonicalSys/android_maplib/pull/65)
слит по отдельному разрешению владельца через Merge Commit
`6c2eb0fa9633087904627cdfa18eb944b0cf5313`. После fetch подтверждены ancestry
`c04f723e` и полное равенство merge tree проверенной library head.
[App PR88](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/88)
получил именно этот remote merge pin и слит по отдельному разрешению владельца:
Squash `b165046d03156a9e8664f4b9442ff05b98b022a6`. После fetch дерево squash
полностью совпало с проверенной head `0c892bf8`; приложение и три библиотеки
чисты и совпадают с удалёнными целевыми ветками.

Проверка всей матрицы после merge:17 PR (app81–88, maplib61–65,
maplibui45–47, easypicker5). Все исходные library heads присутствуют в ancestry;
для всех app squash совпали независимые patch IDs исходной и слитой правки.
Открытых релевантных PR/неинтегрированных remote branches не осталось.

Новый порядок: version-only maplib PR (Merge Commit), fetch и app pin,
затем version app PR (Squash), повторный fetch/closure, version matrix,
Full/Check-Publication и publisher dry-run. Пока нужная версия библиотеки
не слита и не закреплена, финальный release candidate не готов.

## Контракт и зависимости

Production Lisa/Belka3.1.2.31/225; Lisa Debug3.1.2.23/218.
maplib release diagnostic version3.1.2.31; debug3.1.2.23.
Независимые ожидания version script обновлены намеренно под новый выпуск.
Application/provider/account identity, signing, update flavor, backend,
NGW/SQLite schemas и saved projects/drafts/queues bump-ом не меняются.
Desktop/PostgreSQL producers не читают эти version constants; связь с ними
сохраняется через прежние NGW/form/import contracts, без новой миграции данных.

Адресный LISA dependency plan выполнен до правки для app/build.gradle,
maplib/build.gradle и version matrix. Его широкий source closure — кандидаты
на semantic review, не разрешение paused desktop/native стадий.
Общий LISA checkout занят codex/function-etalon-roundtrip / PR279: этот выпуск
не изменяет и не переключает его. Обновление canonical source index остаётся
cross-owner follow-up. `dependency_discovery --check` исполнен: DRIFTED_OR_MISSING,
5790 файлов, coverage UNKNOWN/inventory_complete=false. Source basis: live `.`,
app/maplib/maplibui/easypicker; publisher committed
`ed5f12da7d6a26de53992e80821c30cf1d9c6f32`. Итоговый shared index требуется
обновить владельцу canonical checkout после Git-доставки; старый hash не
сертифицирует новые version bytes. Новых API/resource links bump не добавляет.

## Приёмка

Статус при создании карточки: версия изменена; проверки и Git-доставка
выполняются. Старый Full не подтверждает новый source fingerprint.

Финальный исходник первого Full — чистый `b165046d`, pins maplib `6c2eb0fa`,
maplibui `2969d55e`, easypicker `5947984e`. Все проверки выполнялись локально;
GitHub Actions не запускались.

| Проверка | Фактический результат |
| --- | --- |
| app/maplib/maplibui unit | 433 +899 +93 PASS, failures/errors/skips0 |
| Проверки runner | 198 PASS |
| docs-check -RunTests | PASS, report guard26 +docs57, UI source contracts49 |
| APK version matrix | Lisa/Belka3.1.2.31/225, Debug3.1.2.23/218; coupling и OpenGL dependency graph PASS |
| Release identity/signature | Обе flavors, ожидаемый certificate, SHA-256 и clean source revision проверены; DEBUG/FIELD_VERIFICATION/PRIVATE_RESOURCE_PROFILING=false |
| Весь native каталог | 273 сценария:256 PASS,17 FAIL; полный результат FAILED |
| UI chooser profiles | 36 PASS |
| Real-window620dpi/font200% | 6 PASS |
| Локальная доставка диагностики | Все9 phases PASS, включая expected fatal, offline replay и503 retry |
| A56 SQLite | Все11 адресных сценариев PASS |
| Software SQLite Related | 8 PASS/3 FAIL из11; source generation, priority pixels, MultiPoint custom marker; причины расследуются |
| A56 cold render stress | PASS:90.313s,60pan/21pinch/2recreate,3545frames;0missing/invalid/unexpected IDs |
| Software cold render stress | FAIL:1911 missing IDs в большом слое;0missing в малом; actual SwiftShader,1264frames, причина ещё не установлена |
| Publisher | 32 unit tests PASS; dry-run NOT_RUN до допуска публикации |
| SM-X115 | NOT_RUN по прямому исключению владельца |

В A56 stress требовалось3920/49 полностью видимых объектов, native queries
вернули4000/50 без пропусков. Peak PSS1319608KiB — наблюдение одного технического
workload, не доказательство ресурсоёмкости всех проектов или приёмка2GiB.
Различие phone/software само по себе не устанавливает драйверную причину.

Группы17 FAIL первого Full:

- Два ожидания source readiness в MapEditingToolsTest используют прежний
  logical path/GeoJsonSource; текущий renderer использует отдельный native ID.
  Семантический разбор подтвердил устаревший waiter, исходные form/gesture/undo
  утверждения должны сохраниться при исправлении.
- LocationOrbitTest после прямого SDK hot-add ожидает перестановку стека от
  очередного heading sample. Текущий контракт переносит такую работу в
  структурные операции; необходим корректный structural scenario без возврата
  полного обхода слоёв на каждый sensor tick.
- Software stress и LocalVectorTileLabelTest, семь pixel/label случаев
  VectorStyleRenderingTest: причины ещё не подтверждены.
- На телефоне: диалог памяти не прошёл проверку полной ширины строки,
  mixed chooser в TrackControlsTest не дождался native idle, два legacy tiled
  fallback сценария не установили ожидаемый source, один mixed polygon alpha
  случай сохранил лишние окрашенные pixels. Нельзя автоматически менять их
  независимые ожидания; нужны точный effective backend и наблюдения состояния.

После Full штатно восстановлены настройки собственного эмулятора, закрыта
console и завершён его процесс. Четыре отслеживаемых новых qcow2-файла на Q
отсутствуют при readback; свободное место выросло примерно на1.9GiB.
Рабочий телефонный package остаётся3.1.2.30/224; release кандидат не установлен.
Сейчас на неизменном source fingerprint выполняется адресный software
`Related -Area sqlite-render -SkipBuild`, использующий уже имеющийся расширенный
PBF/provider diagnostic collector. Это самостоятельный разбор различия с A56,
не повтор Full и не исправление ранее полученного FAIL.

Локальные результаты перед app merge: maplib899 JVM/144 suites,
0failures/errors/skips; docs-check -RunTests PASS (report guard26 + docs57),
49 UI source contracts; publisher32 tests PASS. Первоначальный Gradle вызов
из PowerShell неверно разделил dotted property, исправленный quoted вызов
завершился успешно. Два UI Gradle file witnesses пересмотрены по version-only
diff; все contract entries и остальные manifest values сохранены сравнением с базой.

Обязательные gates: обе release flavors/Lisa Debug, maplib coupling,
release certificate/SHA-256, unit/docs, весь native catalogue, UI и real-window
matrices, render stress phone/tablet/emulator с физическим SM-X115,
Check-Publication, publisher tests и dry-run. Отсутствующее свидетельство
остаётся NOT_RUN/UNKNOWN, не PASS. Один подключённый A56 не закрывает tablet gate.

Владелец явно исключил физический SM-X115 из текущего выпуска: подключить его
невозможно, тесты на нём не выполнять. Phone/software emulator выполняются
локально; tablet/SM-X115 остаётся NOT_RUN по этому решению. Штатный Full/
Check-Publication всё ещё требует полную hardware matrix: отсутствие планшета
не превращается в зелёный receipt и не скрывается изменением ожиданий.

## Хранение

Installed SDK/JBR/API36 AVD и существующие Gradle/build caches используются
повторно; новые SDK/runtime/image copies не создаются. TEMP/TMP/TMPDIR на Q.
Owned task evidence и storage manifest находятся на Q вне Git; APK deliverables
хранятся отдельно от disposable данных. Пробная ручная очистка отклонена
tool policy до запуска PowerShell, без созданных probe files; отказ не обходится.
Итоговые размеры и остатки фиксируются после завершения.

Проверенный lifecycle cleanup тестов не означает исправления ручного executor
отказа. Старые emulator qcow2 других запусков не удалялись по возрасту или имени.
Full receipts, observed-failures и scope/stop readback хранятся в owned evidence
на Q; пути и состояние перечислены в handoff. До закрытия расследования state
storage manifest остаётся active, результаты не объявляются completed ради pruning.
