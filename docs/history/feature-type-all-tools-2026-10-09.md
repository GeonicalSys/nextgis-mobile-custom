---
title: Выбор типа для местоположения и обхода — проверка 2026-10-09
type: history
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/WalkSessionStore.java
---

# Выбор типа для местоположения и обхода

Карандаш, местоположение и новый обход используют один выбор категории после
выбора слоя. Dialog arguments/result содержат исходный tool; при простом стиле
выполняется тот же путь без диалога. Местоположение передаёт явную GPS-геометрию
в форму, не обращаясь к ещё загружаемым MapLibre edit sources после recreate.

Новый обход записывает typed walk_initial_values в той же commit-транзакции,
что и полную геометрию/owner. После Finish значения переходят в geometry draft
и durable checkpoint формы. При редактировании существующего объекта defaults
не записываются. Отмена категории точки снимает только point UUID, не обход.

## Проверки

JBR OpenJDK 21.0.9, Gradle 9.3.1; изолированный read-only AVD
Medium_Phone_API_36.0, Android 16/API 36 x86_64, emulator-5556. Рабочий телефон
не использовался. Fixtures — копии форм/схем/стилей стандарта без NGW-подключений
и объектов; tests удаляют только свои слои и созданные ими черновики.

- maplib unit: 508/508; maplibui unit: 92/92; MapLayoutContractTest пройден.
- maplibui Debug, Lisa Debug/test APK, Lisa Release и Belka Release собраны.
- Три новых native сценария прошли вместе: picker recreate для местоположения;
  отмена категории и Save точки во время фонового обхода; interruption сервиса
  с сохранением категории до Finish, geometry draft, формы и SQLite.
- Последний native regression: 5/5 вместе — три новых сценария, прежний путь
  карандаш → категория → форма → SQLite и Save нового/существующего обхода.
- Проверяются реальные category list clicks, форма, classobj/typeobj в SQLite,
  GPS-координаты точки, отсутствие преждевременной строки и очистка владельцев.
  Прерывание recorder имитируется Stop service; следующий durable geometry
  snapshot задан тестом, это не полевая проверка GNSS-фильтра.
- docs-check -RunTests: validator и 7/7 тестов пройдены.

Ранняя версия native harness не выбирала слой на карте с несколькими слоями;
это исправлено. После recreate обнаружено обращение current-location к ещё
отсутствующим edit sources; путь исправлен на прямую передачу GPS-геометрии
форме. Callback отмены диалога ожидается до проверки point lock. Synthetic fix
начинает отдельный filter state каждого сценария, не зависит от прежнего recorder.

## Присутствие изменений и порядок интеграции

После fetch открыты только собственные Draft app #54 (base my-maplibre) и
maplibui #34 (base master); в maplib, easypicker и upload_mobile открытых PR и
дополнительных опубликованных codex веток нет. Предыдущие type-picker/walk
коммиты остаются ancestors; maplib/easypicker pins принадлежат remote master.

| Требование | Владелец / PR | Проверенное присутствие | Порядок |
|---|---|---|---|
| Тип категории, родители и durable owner нового обхода | maplibui #34 / master | ae8ddcb3ee9ea5b832611cb2f8c68dd56512e49c; remote branch содержит его | Merge Commit #34 |
| Маршруты создания и перенос в форму, native regression/docs | app #54 / my-maplibre | Scoped follow-up на 64b37debe8a9f4af2aa4391850c9a12d8bef0eb8; gitlink ae8ddcb3 | После remote merge commit библиотеки, затем Squash #54 |

Ни одна нужная предыдущая правка не отложена. Тестовый gitlink на голову открытого
PR не закрывает release-зависимость. Перед выпуском — merge библиотеки, fetch,
repin app на remote merge commit, squash app и повторный аудит удалённой цепочки.
Версии сохранены (production 3.1.2.27/221); APK не публиковался.

Не проверены полевые GNSS/screen-off, настоящий process death на границе выбора,
Finish и формы, camera/save failure, целевое устройство, landscape/tablet,
крупный шрифт и обе темы. Desktop/mobile форматы и publisher не меняются.
