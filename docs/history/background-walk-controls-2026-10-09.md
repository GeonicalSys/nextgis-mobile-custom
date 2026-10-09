---
title: Завершение и панель фонового обхода — проверка 9 октября 2026
type: reference
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/res/layout/layout_map_content.xml
  - maplibui/src/main/java/com/nextgis/maplibui/view/WalkRecordingPanel.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/WalkGeometrySnapshot.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ControlHelper.java
---

# Проверка фонового обхода

По выбору пользователя доработка добавлена в существующие Draft PR приложения
[#54](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/54) и библиотеки
[#34](https://github.com/GeonicalSys/android_maplibui/pull/34), на их ветках
`codex/feature-type-empty-lists`. Исправления типов и длинных списков сохранены.

## Поведение

- После подтверждения финального снимка каждая линия требует две разные точки,
  каждое кольцо — три. WKT-замыкание не является дополнительной вершиной;
  пустая коллекция или недостаточная часть не создаёт объект/форму.
- При нехватке точек обход освобождается с сообщением «Собрано недостаточно
  точек, выхожу без сохранения». При дополнении существующего объекта исходная
  сохранённая геометрия не изменяется.
- Компактная панель прижата к низу карты; точности GPS и меню обхода нет.
  Подтверждаемая отмена находится рядом с завершением. При раскрытии меню
  создания панель поднимается над ним, после закрытия возвращается вниз.
- После Finish активное «Сохранить» панели и toolbar Save передают актуальную
  геометрию в атрибуты нового/существующего объекта. Сохраняются обычная проверка,
  GTMultiPolygon repair, durable handoff и блокировка на время создания точки.
- Alpha/tint иконок изолированы; пересозданное меню получает текущие состояния
  Save/Undo/Redo. Пункты без иконок поддерживаются.

## Фактически выполнено

JDK: Android Studio JBR 21.0.9; Gradle 9.3.1. Изолированный emulator-5556:
Android 16 / API 36, x86_64, Medium Phone, read-only без сохранения snapshots.
Рабочий Samsung SM-A566B не использовался.

- `:maplibui:testDebugUnitTest --tests ...WalkGeometrySnapshotTest`: 5/5.
- `:app:testLisaDebugUnitTest --tests ...MapLayoutContractTest`: 1/1.
- `:maplibui:assembleDebug`, `:app:assembleLisaRelease`, `:app:assembleBelkaRelease`,
  `:app:assembleLisaDebug`, `:app:assembleLisaDebugAndroidTest`: успешно.
- `tools/docs-check.ps1 -RunTests`: validator и 7/7 documentation tests.
- Native `MapEditingToolsTest`: 10 разных сценариев проверены. Девять прошли
  в общем запуске; Cancel проверен отдельно после исправления тестового
  выбора актуального окна подтверждения и ожидания терминальной команды.
  Проверены отсутствие строки/формы при нехватке точек, Save новой линии,
  обновление существующей с двух до трёх вершин через атрибуты, возврат в
  обычный режим, вид Save после пересоздания toolbar, независимые alpha
  разных кнопок и пункты без иконки, положение панели при раскрытии меню,
  отказ/подтверждение отмены, прежние selection/ruler/azimuth/type-picker сценарии.

Первый запуск тестового пакета выявил отсутствующий Espresso; тест использует
системный WindowInspector, без новой зависимости. Системный ANR System UI
эмулятора перекрывал диалог и первый screenshot; приложение продолжало выполнять
Save. Ошибки harness не объявлялись успешной проверкой Cancel.

## Зависимости и ограничение выпуска

Инвентаризация после fetch: открыты только app #54 и maplibui #34; в maplib,
easypicker и upload_mobile открытых PR и дополнительных опубликованных
`codex/*` нет. До новых коммитов app tip — `7cb8cb342ee1df2bf625ec5361738f1214c34d32`,
maplibui tip — `eb08352b54ca1e71df28b2abe8a149aab8417a89`.

| Требование | Владелец / PR / база | Присутствие в проверенном checkout | Порядок |
|---|---|---|---|
| Смешанные формы и доступная отмена выбора типа | maplibui #34 / master | Коммиты 7af9adae, eb08352b — ancestors HEAD библиотеки | Merge Commit #34 |
| Стандартные формы и их regression coverage | app #54 / my-maplibre | Коммиты 7850bbe, 283c105 и последующие — ancestors HEAD приложения | После библиотеки |
| Минимум точек, панель и независимые иконки обхода | maplibui #34 / master | Коммит 871c3874 на той же ветке; unit/native проверены с этой рабочей копией | В составе #34 |
| Завершение, форма атрибутов и нижнее положение панели | app #54 / my-maplibre | Новая scoped правка и gitlink библиотеки на той же ветке | Pin remote merge commit #34, затем Squash #54 |

Оба PR остаются Draft. Текущий gitlink на голову открытой библиотеки нужен для
review и тестов; он не является закрытой release-зависимостью. Проверочные
assemble-задачи не означают выпуск. Перед выпуском нужны remote merge commit #34,
обновление app pin, squash #54 и повторный аудит удалённой цепочки.
Версии не повышались; APK не публиковался и на рабочий телефон не устанавливался.

Остаются полевые проверки реального GNSS и выключенного экрана, process death
на границе Finish/form, camera/ошибка сохранения, landscape/tablet/split screen,
крупный шрифт и обе темы на целевом устройстве. Документируемый desktop/mobile
формат не меняется; maplib, easypicker, desktop и publisher не требуют правок.
