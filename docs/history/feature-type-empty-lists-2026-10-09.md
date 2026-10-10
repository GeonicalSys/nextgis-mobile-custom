---
title: Закрытие исправления пустого списка типов
type: history
last_verified: 2026-10-09
related_code:
  - maplibui
  - app/src/androidTest/java/com/nextgis/mobile/reliability/StandardFeatureTypesTest.java
---

# Закрытие исправления пустого списка типов

База уже выданного APK: app `5b800e86d4b4f4a6d609c1aa00c5792f1338af93`,
maplib `0fff279ca88baa735faaa8ef01b78c6bb7973863`, maplibui
`20a033c01aa3c4bac197c2ef805a88145dee362d`. На подключённом телефоне при
открытии «Полевые точки» воспроизведён StringIndexOutOfBoundsException:
FeatureTypeDefaults.legacyChoices нормализовал пустое имя поля текстовой подписи.
Обычный combobox также ошибочно требовал field_level2.

Аудит пяти owner repositories после fetch: единственные открытые PR/remote
codex branches относятся к исправлению ниже; easypicker и upload_mobile без
новых открытых PR/веток. Чужие desktop changes не входят в Android scope.

| Требование | Owner / commit | PR / base | Порядок и состояние |
|---|---|---|---|
| Категории и зависимые родители | maplib `c248fbf03f701fdd1aefd8c6df03b932e5803233` | #45 / master | Уже merged; ancestry в origin/master подтверждён |
| Picker, начальные значения и восстановление | maplibui `d41ad4828d4fa6af45ed134ea34c07f676c6d408` | #33 / master | Уже merged; ancestry в origin/master подтверждён |
| Plus → category → geometry → form | app `5b800e86d4b4f4a6d609c1aa00c5792f1338af93` | #53 / my-maplibre | Уже squash-merged; эта база является предком #54 |
| Смешанные формы, обычные combobox и видимая отмена длинного списка | maplibui `eb08352b54ca1e71df28b2abe8a149aab8417a89` (включает `7af9adae`) | #34 / master | Draft; обычный порядок интеграции — Merge Commit, затем fetch remote merge SHA |
| Pin исправленной UI-библиотеки и регрессии стандарта | app, head #54 | #54 / my-maplibre | Draft; после #34 закрепить remote merge SHA и выполнить Squash |

Предшественники #45/#33/#53 не отложены: исходная реализация включена в обе
ветки исправления. Остальные библиотеки сохраняются: easypicker
`d6327f3de7a6d488a18d1896f8c97c60cd28d2a8`; publisher
`ff3ab8d1404ce5ec52b9b61f19b9297d466f52e5`. Отложенных изменений нет.

Проверки: 90 unit maplibui; две native регрессии сначала воспроизвели исключение,
после исправления прошли. CascadingFormsTest (22) и MapEditingToolsTest (6)
прошли; StandardFeatureTypesTest отдельно проверил все 40 стандартных категорий,
символы, канонические classobj/typeobj и заполнение стандартной формы (1).
Ошибка первоначального test Intent с int вместо long feature ID исправлена
в самом тесте; тест затем прошёл. Для проверки malformed form добавлена парная
meta, чтобы тест использовал штатный default form resolver; этот сценарий
после уточнения fixture прошёл отдельно. Synthetic suite запускался только на эмуляторе.
Регрессия 40 категорий воспроизвела скрытую кнопку Cancel до исправления
компоновки и прошла после него; отмена не создаёт строку объекта.

Пользователь явно оставил #34/#54 в Draft и затем разрешил **локальную
Lisa Release сборку из этих Draft-веток без merge** как исключение из правила
release closure, с установкой обновлением на подключённый телефон для проверки.
Это не закрытая интеграция: app закрепляет remote head #34
`eb08352b54ca1e71df28b2abe8a149aab8417a89`, оба PR остаются открытыми.
Перед сборкой повторяются fetch, проверка точного включения строк, version
matrix и проверка подписи. Версия остаётся `3.1.2.27` / `221`.
Публикация и последующие releases этим исключением не разрешены.
Точные финальные remote SHA, APK hash и результаты phone smoke сохраняются
в delivery receipt и итоговом PR handoff после закрытия цепочки.
