---
id: android-20261025-track-stop-history
area: Мобильное приложение
kind: user
at: 2026-10-25T17:25:00+01:00
title: Подтверждение остановки записи и различимые старые треки
why: Случайное нажатие завершало запись, старые треки оставались одноцветными, а линия была слишком широкой
covers:
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/NativeStyleTouch.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LegacyTrackColorsNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackerStopRecoveryTest.java
  - tools/mobile-test-plan.json
  - maplib
  - maplibui
---

Кнопка карты, меню и действие уведомления завершают запись после подтверждения.
Cancel и Back сохраняют запись; старое окно не завершает новый сеанс.
Зависимые библиотеки уменьшают линию вдвое и однократно раскрашивают прежние
треки без собственного цвета. Пользовательские цвета, точки и отправка сохранены.

В локальном журнале 25 октября обе найденные остановки инициированы интерфейсом:
13:33:11 и 16:08:57 по времени устройства. По журналу нельзя определить,
было ли нажатие случайным. Ошибок вставки и недописанного хвоста при них нет.
Безопасный разбор и проверки: `docs/reference/track-followup-2026-10-25.md`.
Порядок интеграции: maplib #60 → maplibui #44 (Merge Commit) → app #78 (Squash).
APK готовится только локально по разовому исключению пользователя; публикации нет.
