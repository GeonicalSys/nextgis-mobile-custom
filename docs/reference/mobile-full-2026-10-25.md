---
title: Full на смартфоне и планшете — 25 октября 2026
type: reference
last_verified: 2026-10-25
related_code:
  - tools/run_mobile_tests.py
  - tools/mobile-test-plan.json
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
---

# Область и состояние

Пользователь запросил полный набор тестов перед будущей публикацией, затем
отдельный полный прогон на планшете. Публикация и merge этим запросом не
разрешены. Рабочую ЛИСУ не переустанавливают и не очищают; native suites
используют `com.nextgis.mobile.verification.debug` и технические fixtures.
Смартфон — Samsung SM-A566B, Android 16/API 36, serial R5GL130K54D.
Проект 1403 «Для инженеров геоникл» найден однозначно; с телефона читаются
только политики 18 слоёв, без features/accounts/credentials.

## Первый Full: FAIL, не приёмка

На source `af61f6da85dfb1d5367a2bef15deb004376762f3` (код эквивалентен APK f06ccfb)
прошли 887 unit (614 + 93 + 180), runner tests, полная APK version matrix,
docs validator/tests и 42 native сценария на изолированном API 36 emulator.
Full остановился на `rulerAcceptsFingerDriftAndKeepsPanCancelAndLongPressSeparate`:
отложенный single-tap вызвал SDK queryPointAnnotations после уничтожения MapView.
Ошибка строгой проверки SDK выявила неправильный lifetime; strict mode не
отключался. Телефонные native классы этого прогона ещё не выполнялись.

Evidence: `Q:\Codex\Scratch\mobile-full-20261025-f06ccfb\evidence\phone-full-01`.
Исходный fingerprint: `8e2104ae6214c16a84146ff65609d9f1a48513b313bba38ae168fe99460553e1`.
Прежнего успешного Full gate этот неуспешный запуск не оставляет.
Owned emulator остановлен runner после восстановления настроек.

## Исправление и адресная проверка

MapFragment перед освобождением native renderer снимает edit touch listener
и передаёт ACTION_CANCEL прямо SDK. Это отменяет ожидающее подтверждение
тапа, которое очистка app listeners не снимала. Новый native метод
`pendingNativeTapIsCancelledBeforeViewDestruction` повторяет тап/recreate три
раза и ждёт окна отложенного callback при включённом strict mode.

Related `map-teardown`: **2/2 PASS**, включая исходный сценарий линейки.
Evidence: `...\evidence\map-teardown-01`. В Full теперь 249 требуемых native
методов; все они остаются обязательными. Адресный успех не заменяет Full.
Повтор Full на смартфоне и отдельный Full на планшете остаются необходимыми;
конечные исходы фиксируются после их завершения.

## Дополнение UX-05 перед повторным Full

25 октября пользователь добавил недельные напоминания об устаревшем полном
sync онлайн-проекта и проверке/установке обновления; локальные проекты
исключены из sync. Новый код и native класс включаются в окончательный
Full (53 класса / 251 required methods). Первая Related maintenance проверка
прошла updater 3 и durable clocks 1; UI остановилась на незавершённом
intro route тестового MainActivity. Fixture теперь явно отмечает intro
в verification preferences до запуска, не меняя production либо assertions.
Итоговые Related и Full ещё не заявлены PASS.

Related maintenance-02: часы и updater прошли; UI regression обнаружила
выход естественных bounds кнопки за границы при общем FittedPanel масштабировании.
По уточнению пользователя длинное сообщение переведено на штатную bounded
прокрутку AlertController без уменьшения шрифта с закреплёнными действиями.
Матрица теперь также проверяет отсутствие scale и достижение конца сообщения;
четыре худших профиля сохраняются в bounded PNG evidence verification пакета.

Визуальная проверка PNG maintenance-04 выявила обрезанную «Позже» при
280 dp / 200%: проверка до этого учитывала только положительную кнопку.
Private ReminderPanels теперь резервирует полную высоту кнопок, текст
прокручивается, native assertions охватывают оба действия. Maintenance-05
прошёл 5/5, оба просмотренных portrait/landscape PNG имеют доступные действия.
Финальный адресный повтор проверяет также первый абзац предупреждения;
после него исходники фиксируются для полного Full. Принудительное включение
scrollbar отклонено native проверкой API 36 (framework null drawable при draw);
сохранена штатная прокрутка без такого вмешательства.

Финальный Related maintenance-07: **5/5 native PASS**, включая updater 3
и новые clocks/UI 2. Матрица 288 окон сохраняет крупный шрифт, прокрутку до
конца и обе кнопки внутри viewport; просмотрены итоговые PNG 280 dp portrait
и landscape в светлой/тёмной теме. Scoped unit: 33 PASS (24 API28/36 policy
и 9 ready-state), docs validator + 24 tests PASS. Каталог проверен: 53 класса,
251 required native methods. Эти адресные результаты не заменяют Full.
Evidence: `...\evidence\maintenance-native-07`, `maintenance-unit-summary.json`.

## Матрица включения второго Full

После fetch все Android owner деревья чистые, без ahead/behind своих удалённых
веток. Android source tip `45bd6c86f36e8227df7906e1fb49a399fdf59d57`.
Открыты только требуемые собственные Draft PR #78/#60/#44; опубликованные
unmerged codex heads совпадают с текущими tips и включены по ancestry.
Easypicker и publisher не имеют открытых PR/неучтённых codex heads.

| Изменение / владелец | Commit / PR / base | Включение / состояние |
|---|---|---|
| Стили, MapLibre 13.7.1 OpenGL, треки/подписи / app | f06ccfb, #78, my-maplibre | Ancestor текущего app tip; PR открыт |
| GIS стили/отрисовка / maplib | 3c8020a0f9b550db36baee8b7234fea6ee6325e6, #60, master | Root gitlink и checked-out SHA совпадают с PR head; открыт |
| Редакторы и UI / maplibui | d4b8f27337f0549f25353f9b28e7810371f00ae5, #44, master | Root gitlink и checked-out SHA совпадают с PR head; открыт |
| Pending native tap teardown / app | 1756ad5, #78, my-maplibre | Ancestor 45bd6c8 |
| UX-05 напоминания и scroll / app | 45bd6c8, #78, my-maplibre | Текущий source tip |
| Easypicker | 55aad0e0f92329ea4c273a83e541d170d24d7b57, master | Ранее merged dependency; pin совпадает |
| Publisher | 99e3cfde7841b749b83be6005842b96e7d140d00, main | Не изменён и не запускался |

Успешные предыдущие updater изменения b07c512 и 7e794a4 также проверены
как ancestors текущего app tip. Исключение пользователя разрешает локальный
signed Release APK из этих открытых PR без merge/публикации. Оно не закрывает
цепочку выпуска: #60 Merge Commit → #44 Merge Commit → fetched remote merged
pins в app → #78 Squash. Эта цепочка и будущая публикация остаются отложенными.
Десктоп, серверные метаданные трека (пункт 5), переделка загрузки/тайлов отложены
по прежним решениям пользователя и не включались в этот пакет.

Второй Full запущен со всеми 251 обязательными native методами на смартфоне
R5GL130K54D + изолированном API36 AVD. Промежуточно пройдены 912 units
(614 maplib +93 maplibui +205 app), runner units, нормальная version matrix
Lisa Debug/Lisa Release/Belka Release и docs validator/24 tests. Native ещё
выполняются; промежуточные успехи не означают общий Full PASS.
Evidence: `...\evidence\phone-full-02` и `phone-full-02-delivery-matrix.json`.

## Второй Full: FAIL на native курсоре

Исходники 45bd6c8, fingerprint
`6d01c67eb9493335ea0d37bc8d00cd006f936c804c67df33094fefe292d7a519`.
912 unit, runner tests, version matrix и docs/24 tests прошли. Пройдены
82 distinct native сценария, включая прежнюю линейку и новую регрессию
MapView teardown, GPS/recording/track upload. Таймаут 30 секунд на первом
awaitMarker в `LocationOrbitTest#orbitUsesCompassThenCourseWithACenteredAnchorAndConfigurableSector`.
Телефонные классы, оставшиеся emulator styles/sync, diagnostics и chooser
matrix ещё не достигнуты. Общего Full PASS нет; gate удалён. Owned emulator
остановлен в finally; ADB readback показывает только смартфон.

Проверяется гонка graphics fixture с завершающим full-style callback,
который штатно вызывает updateLastLocation. Это пока гипотеза по коду,
не установленная причина: нужны фактические source/cache/camera diagnostics.
Baseline Related cursor выполняется до исправления. Смартфон/планшет Full
после исправления остаются обязательными.
