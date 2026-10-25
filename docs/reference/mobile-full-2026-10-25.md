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
така, которое очистка app listeners не снимала. Новый native метод
`pendingNativeTapIsCancelledBeforeViewDestruction` повторяет тап/recreate три
раза и ждёт окна отложенного callback при включённом strict mode.

Related `map-teardown`: **2/2 PASS**, включая исходный сценарий линейки.
Evidence: `...\evidence\map-teardown-01`. В Full теперь 249 требуемых native
методов; все они остаются обязательными. Адресный успех не заменяет Full.
Повтор Full на смартфоне и отдельный Full на планшете остаются необходимыми;
конечные исходы фиксируются после их завершения.
