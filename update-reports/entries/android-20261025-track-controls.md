---
id: android-20261025-track-controls
area: Мобильное приложение
kind: user
at: 2026-10-25T11:30:00+01:00
title: Выбор трека на карте и управление его отображением
why: Нужны переименование, различимые цвета и доступные точки под плотной сетью треков
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/res/layout/layout_map_content.xml
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SharedTracksTest.java
  - tools/mobile-test-plan.json
  - maplib
  - maplibui
---

Тап по треку открывает нижнюю панель: цвет, название, скрыть/показать, удалить.
При совпадении разных треков предлагается выбрать нужный. Скрытый выбор
сохраняется при пересоздании экрана, а переход в инструмент закрывает панель.
Библиотеки добавляют переименование в списке, насыщенные цвета новых треков,
мягкое свечение и отрисовку точечных слоёв поверх линий.

Проверки проходят provider → настоящие виджеты и жесты → native source/GPU,
включают Save/Cancel, пересоздание, порядок Point/MultiPoint и retry удаления
текущей записи. Панель проверяется на русском с крупным шрифтом в обеих темах.
Пункт передачи имени/меток на сервер исключён. Desktop-палитра отложена по
последнему указанию пользователя. Порядок интеграции: maplib #60 (Merge Commit)
→ maplibui #44 (Merge Commit) → merged pins в app #78 (Squash). Выпуск не запрошен.

Фактическая проверка: `docs/reference/track-controls-verification-2026-10-25.md` —
876 unit и 62 native случая на эмуляторе API 36; физический планшет недоступен.
