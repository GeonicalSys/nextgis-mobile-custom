---
title: Фоновый GPS — проверка и доставка 2026-10-14
type: history
last_verified: 2026-10-14
related_code:
  - app/src/main/java/com/nextgis/mobile/location/BackgroundLocationController.kt
  - maplib/src/main/java/com/nextgis/maplib/location/GpsEventSource.java
---

# Фоновый GPS — проверка и доставка

## Инвентаризация перед validation builds

После свежего `workspace-preflight.ps1 -Fetch` 2026-10-14 открытых PR и
опубликованных `codex/*` веток во всех пяти Android-репозиториях нет. Это
техническая проверка задачи; release, merge и публикация APK не запрошены.
Новые library commits до merge являются незакрытой зависимостью для выпуска.

| Repository | Проверенный base | Требование / присутствие в проверяемом checkout | Merge-state / зависимости |
|---|---|---|---|
| android_gisapp | `7e794a440b846af5b07f593f9fee2e9d545d394f` | Контроллер, FGS, setting, диагностика, статус Network — task diff | Draft; maplib API, затем merged library pins |
| maplib | `28a4300ae308c6f531c9b8073382f09c1d6ad052` | Независимый lease, display-only Network, recovery snapshot — task diff | Draft; Merge Commit до root |
| maplibui | `d5313944b9ca776819cfc3a28f99e5a691fe4a09` | Документация shared-owner contract — task diff; runtime не меняется | Draft; Merge Commit до root |
| easypicker | `d6327f3de7a6d488a18d1896f8c97c60cd28d2a8` | Без изменений | Уже в base, pin не меняется |
| upload_mobile | `ff3ab8d1404ce5ec52b9b61f19b9297d466f52e5` | Без изменений | Publisher не запускается |

Base root содержит указанные library commits (проверяется `git ls-tree`);
validation builds используют содержимое изменённых submodule checkouts.
Чистота полного workspace не заявляется: desktop monorepo содержит чужие
изменения на `codex/reforest-xml-boundary-spikes`, вне Android scope. Их файлы
и ветка не изменяются. In-scope dirty-файлы принадлежат этой GPS-задаче.

## Приёмка

Проверки выполнены на Java 21 / Android SDK 36:

- 543 unit-теста maplib, 92 maplibui и 126 app: 761, без ошибок и пропусков.
- `assembleLisaDebug`, `assembleLisaDebugAndroidTest`, `assembleLisaRelease` и
  `assembleBelkaRelease` успешно завершились. Версии не менялись.
- Изолированный API 36 эмулятор: реальные сон/пробуждение экрана, формы с
  включённым и выключенным фоном, моментальное переключение setting без остановки
  отдельного recorder, двухчасовой deadline и возобновление после возвращения.
  Двухчасовой срок проверяется инъекцией монотонного времени в controller и unit
  policy, без заявления о двухчасовом ожидании на устройстве.
- Настоящий экран местоположения: все 36 профилей font scale 1/1.3/2 × density
  420/540/617 (ширина portrait ≈411/320/280 dp) × light/dark × portrait/landscape.
  Проверены полный native Layout всех строк адаптера, отсутствие ellipsis и
  включённый по умолчанию реальный switch. Нативная status-panel матрица:
  24 профиля, русские координаты, ±4214 м и возраст 7200 с. PNG просмотрены.
- `tools/docs-check.ps1 -RunTests`: validator и семь тестов прошли.

Реальный Samsung Galaxy Tab, лес, поведение OEM при принудительном ограничении
фоновой работы, поиск спутников и расход батареи не проверены: эмулятор не
подтверждает радиоприём и автономность в этих условиях. Боевой diagnostic endpoint
не использовался; native проверки идут с изолированной тестовой конфигурацией.

## Связанные Draft dependencies

- [app #60](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/60), основной
  code commit `68065557ad1f81774fe8ac346423d859e8816ded`, base `my-maplibre`, Draft,
  unmerged. Последующий docs commit добавляет только ссылки/матрицу; ancestry
  подтверждает присутствие code commit, а `git ls-tree` — два task pins ниже.
- [maplib #52](https://github.com/GeonicalSys/android_maplib/pull/52), commit
  `725ebc6f02227582fe84bb12faa0d21579bf798c`, base `master`, Draft, unmerged.
- [maplibui #37](https://github.com/GeonicalSys/android_maplibui/pull/37), commit
  `a025415f0ccfcf47bf11a3c27387fbd8cd5c43b3`, base `master`, Draft, unmerged;
  документация, runtime не меняется.
- Root task pin указывает на эти два commits для совместного review. Проверка
  content diff library bases и root `git diff --submodule=short` подтверждает
  включение именно этой задачи; pin не является merged dependency.

Порядок интеграции: Merge Commit библиотек → fetch → root обновляет оба pins на
удалённые merge commits → Squash Merge app. Перед выпуском повторить обязательный
полный inventory/ancestry/content audit и release gates. Эта задача не сливает PR,
не меняет версию и не публикует APK.
