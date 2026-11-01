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
cross-owner follow-up; current source basis/check outcome фиксируется отдельно.

## Приёмка

Статус при создании карточки: версия изменена; проверки и Git-доставка
выполняются. Старый Full не подтверждает новый source fingerprint.

Обязательные gates: обе release flavors/Lisa Debug, maplib coupling,
release certificate/SHA-256, unit/docs, весь native catalogue, UI и real-window
matrices, render stress phone/tablet/emulator с физическим SM-X115,
Check-Publication, publisher tests и dry-run. Отсутствующее свидетельство
остаётся NOT_RUN/UNKNOWN, не PASS. Один подключённый A56 не закрывает tablet gate.

## Хранение

Installed SDK/JBR/API36 AVD и существующие Gradle/build caches используются
повторно; новые SDK/runtime/image copies не создаются. TEMP/TMP/TMPDIR на Q.
Owned task evidence и storage manifest находятся на Q вне Git; APK deliverables
хранятся отдельно от disposable данных. Пробная ручная очистка отклонена
tool policy до запуска PowerShell, без созданных probe files; отказ не обходится.
Итоговые размеры и остатки фиксируются после завершения.
