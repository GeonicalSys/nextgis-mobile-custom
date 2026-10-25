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
