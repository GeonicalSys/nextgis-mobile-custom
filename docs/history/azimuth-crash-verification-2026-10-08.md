---
title: Проверка исправления вылета азимута
type: history
last_verified: 2026-10-08
related_code:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/nextgis/mobile/stakeout/StakeoutForegroundService.kt
  - app/src/main/java/com/nextgis/mobile/stakeout/StakeoutController.kt
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/StakeoutForegroundServiceTest.kt
---

# Проверка исправления вылета азимута

В девяти переданных файлах журналов найдено 17 вылетов с одной причиной:
служба азимута/выноса запрашивала `location|connectedDevice`, когда разрешения
для connectedDevice отсутствовали. Ошибка возникала асинхронно внутри
`onStartCommand`, после возврата из UI-вызова запуска.

Служба теперь запрашивает только location. Отказ запуска возвращается
владельцу измерения, который освобождает GPS listener/lease, heading и звук.
MapFragment возвращает карту в обычный режим и показывает понятное сообщение.
Идентификаторы сеансов защищают нового владельца от запоздалых start/stop;
отмена ещё не запущенной foreground-службы сначала выполняет системный
контракт promotion, затем останавливает её без восстановления GPS/audio.

## Проверенное окружение и результаты

- Windows, Android Studio JBR21.0.9, Gradle9.3.1.
- Изолированный `emulator-5554`: Android16/API36, ranchu,
  `google/sdk_gphone64_x86_64/emu64xa:16/BP22.250325.006/13344233:user/release-keys`.
- Bluetooth Connect/Scan запрещены. Служба работает с разрешённым GPS,
  остаётся активной в фоне и освобождает собственные ресурсы после Stop.
- Все шесть новых native-сценариев проверены. В проходе с разрешённым GPS
  пять прошли, один пропущен по предусловию. После фактического отзыва fine
  и coarse location отдельно прошёл сценарий отказа, очистки и повторного
  запуска; разрешения восстановлены.
- Девять существующих native-сценариев MapEditingToolsTest и
  TrackRecordingModesTest прошли, включая выбор цели азимута.
- Unit suites: maplib505, maplibui90, app41 — всего636, без failures/errors/skips.
  Неизменённые suites использовали актуальные Gradle cached/up-to-date results.
- Собраны Lisa Debug, test APK, Lisa Release, Belka Release и maplibui Debug.
- Строгая проверка документации и семь тестов её инструментов прошли.

Первоначальные новые native-тесты выявили дополнительный Android16-сбой при
остановке foreground-службы до promotion. Итоговые повторные проходы проверили
и unowned start, и быстрый start/stop/start без этого вылета. Успех определяется
instrumentation output, а не только кодом возврата adb.

## Границы проверки и доставка

Исправление относится только к приложению и включается в Draft
[PR51](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/51),
`codex/mobile-form-sync` → `my-maplibre`. Библиотечные pins не менялись:
maplib `0cd7d39b8bdfe34d391bdf6c5f118b0709e549a8`,
maplibui `76f417e08bc99f1e270f1a4c4bbc2376839580ad`.
Они по-прежнему принадлежат открытым Draft PR43/31; сборки здесь проверочные,
выпуск и dependency closure ещё не завершены. Версии и публикация не менялись.

Физическое устройство Android15, ручной текст сообщения и возврат карты после
отказа разрешения не проверены. Полная native suite не повторялась локально;
её запуск остаётся в CI. Универсальное восстановление после необработанных
исключений не добавлялось: существующий глобальный crash handler сохранён.
Сетевые сбои и пропускаемая невалидная геометрия из журналов этим исправлением
не устраняются. [Контракт службы](../architecture/stakeout.md),
[ручные шаги](../runbooks/device-smoke-tests.md#азимут-и-вынос).
