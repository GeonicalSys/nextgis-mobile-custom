---
title: Доставка выбора типа объекта 9 октября 2026
type: reference
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - tools/verify-apk-version-matrix.ps1
  - maplib
  - maplibui
---

# Выбор типа объекта и Lisa Release без повышения версии

Запрос: после выбора слоя предложить категории с проектными символами,
заполнить тип и правильных родителей в форме, затем собрать локальный Lisa
Release APK. Версия остаётся `3.1.2.27` / `221`. Публикация не запрошена.
GitHub identity `geoglyth` подтверждена; desktop checkout не редактируется.

## Инвентаризация и baseline

Перед изменениями и повторно перед подготовкой PR выполнен fetch/prune всех
пяти owners. Старых открытых PR и не включённых в target опубликованных
`codex/*` веток не было. Дополнительных predecessors или отложенных требований нет.
Новые PR принадлежат geoglyth, созданы с одинаковой task branch
`codex/feature-type-picker` от собственных target branches, без стека PR.

| Owner / target | Remote baseline |
|---|---|
| android_gisapp / my-maplibre | `c6b95831fb14cce32fde8d7bfc9a51b5fbf3905a` |
| maplib / master | `1fd2b96baa1a1a03d7a01695619cb797054fcc9a` |
| maplibui / master | `e7d1bb98d7598c3f5b03e3f985ed55e3a892ace7` |
| easypicker / master | `d6327f3de7a6d488a18d1896f8c97c60cd28d2a8` |
| upload_mobile / main | `ff3ab8d1404ce5ec52b9b61f19b9297d466f52e5` |

## Delivery matrix

| Требование | Owner / PR / base | Исходный commit | Зависимость / включение |
|---|---|---|---|
| Все допустимые родительские цепочки по категории, стабильные keys и AND-фильтры; общие pixels заливки | [maplib #45](https://github.com/GeonicalSys/android_maplib/pull/45) / master | `c248fbf03f701fdd1aefd8c6df03b932e5803233` | Draft; первым Merge Commit, затем fetch и ancestry/tree check |
| Выбор типов с символами; точные legacy/NGW/cascade значения; durable handoff и восстановление | [maplibui #33](https://github.com/GeonicalSys/android_maplibui/pull/33) / master | `d41ad4828d4fa6af45ed134ea34c07f676c6d408` | Зависит от #45; Draft, вторым Merge Commit, затем fetch и ancestry/tree check |
| Plus/layer → type → sketch → form → SQLite, geometry defaults, native tests и документация | [app #53](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/53) / my-maplibre | `661a06f1edb233b50f1129235be077e3750086ed` | Зависит от обеих библиотек; Draft, remote merge pins после library merges, затем Squash и сравнение app tree |
| Локальный Lisa Release APK без bump | app #53 / my-maplibre | Итоговый fetched remote app tip | Заблокирован до закрытия трёх строк выше и проверки pins; metadata, подпись и SHA-256 проверяются после сборки |

easypicker и upload_mobile не меняются. До library merges app gitlinks сохраняют
baseline. Локальная проверка выполнялась с точными task heads библиотек выше;
они не являются разрешёнными release pins. Наличие Draft PR не означает выпуск.

## Проверки реализации

- `CascadingListsTest`: 15 tests, 0 failures/errors.
- Весь `:maplibui:testDebugUnitTest`: 90 tests, 0 failures/errors.
- Lisa Debug и AndroidTest APK, Kotlin source sets Lisa/Belka Release: PASS.
- `CascadingFormsTest` + `MapEditingToolsTest`: 25 native tests, PASS, API 36
  isolated `emulator-5554`. Полный путь карты сохраняет правильную пару значений
  и возвращается на карту без оставшихся черновиков. Recreate/durable recovery
  и смена родителя проверены отдельными сценариями.
- `tools/docs-check.ps1 -RunTests`: PASS, 7 tests.
- Physical device, реальный проект полевых точек, installation/update поверх
  рабочего профиля и GNSS/UNC smoke не выполнены. Рабочий телефон не затрагивался.

Логи проверок и будущий APK receipt находятся в игнорируемом `build/`.

## Порядок закрытия

1. Проверить открытые дочерние PR на heads #45/#33/#53; при их наличии остановиться.
2. Merge Commit #45, fetch remote master, проверить ancestry и tree.
3. Merge Commit #33, fetch remote master, проверить ancestry и tree.
4. Закрепить оба remote merge commits в app #53 и проверить итоговый CI.
5. Squash #53 после разрешённого слияния; fetch и сравнение полного дерева с
   подготовленным task tip. Проверить pins по Git, а не названию веток.
6. Повторить аудит пяти owners. Все required rows должны быть включены; только
   затем version matrix и Lisa Release APK без повышения версии.

Текущий статус этой записи — подготовленные Draft PR. Итоговые merge SHAs,
проверка remote tips и receipt APK фиксируются при закрытии цепочки.
