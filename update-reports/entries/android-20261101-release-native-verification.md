---
id: android-20261101-release-native-verification
area: Мобильное приложение
kind: internal
at: 2026-11-01T23:58:00+04:00
title: Исправить размещение предупреждения памяти и ожидания native источников
why: Локальный Full выпуска выявил устаревшие ожидания источника и неописанный layout FAIL
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/RenderMemorySafetyTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - tools/mobile-test-plan.json
  - tools/mobile_style_evidence.py
  - tools/tests/test_mobile_style_evidence.py
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/java/com/nextgis/mobile/util/MaintenanceReminders.java
  - app/src/main/java/com/nextgis/mobile/util/ScrollableAlertDialogs.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
---

MOB-REL-01, follow-up к уже слитым maplib65/app88. Два сценария реального
редактирования теперь ждут текущий SQLite provider и его CustomVectorSource
по generation-specific native ID. Утверждения форм, категорий, camera/gesture,
сохранения и Undo не изменены. Старый logical path/GeoJsonSource не являлся
ожидаемым renderer для этих поддерживаемых геометрий/стилей.

RenderMemorySafety сохраняет full-text/ellipsis/scroll/button gates,
добавляя в ошибку профиль окна, widget, текст и измеренные размеры. Ширина
видимого текста берётся из Layout.getLineMax: getLineWidth включал конечный
пробел на границе переноса. Дополнительно проверяется наличие layout line для
каждого символа. Это исправление неверного измерения, не изменение диалога.
Источник API: https://developer.android.com/reference/android/text/Layout.
Узкие
Related areas используют штатную проверку пары APK/source identity и обычный
изоляционный lifecycle; `editing-source` остаётся emulator-only, `memory-dialog`
разрешена в отдельном verification package на A56. Full roster не сокращён.

LocationOrbit проверяет восстановление cursor stack настоящим structural
hot-add технического VectorLayerUI, сохраняя SDK BackgroundLayer и все исходные
geometry/bitmap/heading/stack assertions. Sensor tick не должен выполнять
O(layer count) перестройку. Temporary layer удаляется в finally владельца fixture;
`orbit-structure` выполняет этот сценарий отдельно на software emulator.

Проверки и оставшиеся blockers фиксируются в
[release evidence](../../docs/reference/mobile-release-3.1.2.31-2026-11-01.md).
После устранения ошибочного измерения воспроизведён отдельный выход кнопки за
границы диалога на A56 и software emulator. Общий bounded message layout
выделен из существующего MaintenanceReminders без изменения его sizing/scroll
алгоритма; новый ScrollableAlertDialogs дополнительно переносит neutral action.
MapFragment использует его для предупреждения памяти: заголовок и полные
действия получают место прежде прокручиваемого сообщения. MaintenanceReminders
сохраняет package API и делегирует прежнему алгоритму; частота напоминаний,
таймеры update/full sync и действия кнопок не меняются.

Не меняются данные, sync, production rendering, фон/батарея, подписи или версия.

Classic fixture проверяет сохранение explicit classic source после native idle
и до cleanup сохраняет bounded JSON source/paint witness плюс PNG в существующий
firefly-qa collector. Это адресная диагностика неизвестных pixel/idle FAIL,
не объявление причины устранённой. Новый collector сохраняет только allowlisted
JSON object<=64KiB и bounded PNG из verification package; остальные JSON
не извлекаются. Неполные/невалидные/oversized файлы отклоняются.
Публикация не выполнялась; SM-X115 исключён владельцем, cloud runs запрещены.
