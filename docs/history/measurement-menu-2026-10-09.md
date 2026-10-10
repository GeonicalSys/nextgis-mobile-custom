---
title: Единая кнопка измерений — проверка 2026-10-09
type: history
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/res/layout/layout_map_content.xml
  - app/src/main/res/menu/menu_measurements.xml
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
---

# Единая кнопка измерений

Одна кнопка «Измерения» со значком линейки открывает PopupMenu с тремя прямыми
действиями: линейка, азимут от местоположения и азимут между точками. После
выбора запускается прежний инструмент; дополнительный диалог режима азимута
убран. Общий вход скрыт во время измерения/редактирования и возвращается после
завершения. Существующая настройка линейки скрывает только соответствующий
пункт; оба режима азимута сохраняют доступность. Accessibility name и tooltip
называют общую кнопку. Расчёты, GPS lease, service и форматы хранения не менялись.

## Проверки

- JBR OpenJDK 21.0.9 / Gradle 9.3.1: Lisa Debug/test APK, Lisa Release и
  Belka Release собраны. Release variants использованы для проверки кода;
  выпуск не выполнялся, production версия осталась 3.1.2.27/221.
- MapLayoutContractTest: 1/1, failures/errors/skipped = 0; общая разметка для
  portrait, landscape и tablet использует один вход в измерения.
- Read-only AVD Medium_Phone_API_36.0, Android 16/API 36 x86_64, emulator-5556:
  native 4/4 вместе, 50.466 s. Реальный PopupMenu проверен через его видимые
  строки: Back без выбора, запуск всех трёх инструментов, скрытие/возврат кнопки,
  настройка выключенной линейки после recreate. Пройдены прежние проверки
  finger drift, pan/cancel/long press линейки и размещения двух точек азимута.
- docs-check -RunTests: validator и 7/7 documentation tests пройдены;
  git diff --check не обнаружил ошибок.

Рабочий телефон не использовался. Ручные проверки планшета/landscape, production
GUI, реального GNSS/компаса и RTK не выполнялись в этом follow-up. Автоматические
проверки layout configurations и двух режимов азимута не заменяют эти field smoke.
Предыдущие проверки панели/категорий описаны в
[истории обхода](walk-card-and-start-extent-2026-10-09.md), здесь не повторялись.

## Матрица присутствия

После fetch по-прежнему открыты только свои Draft app #54 (base my-maplibre)
и maplibui #34 (base master). В maplib, easypicker и publisher нет открытых PR
или дополнительных published codex branches. Этот follow-up продолжает тот
же app HEAD; библиотека не менялась, предшествующие исправления не отложены.

| Требование | Владелец / commit | PR / base | Присутствие |
|---|---|---|---|
| Категории всех creation routes, прежнее сохранение, GPS pan, подтверждения обхода | app adac0a0134c507c5888f849944a2d0bd846e9aa3 и его предки | #54 / my-maplibre | Родитель текущего follow-up, сохранён |
| Общая кнопка измерений | app follow-up; MapFragment blob ec54c84384ebae0e3479fafbf9605e470f1c0472 | #54 / my-maplibre | Exact code blob; итоговый commit указан в PR |
| Формы/категории, минимум вершин, независимые значки, компактная панель и PAUSE | maplibui ca1bac8127d976d3cfdea5dd415541164d0af82a и его предки | #34 / master | Exact app gitlink, открытая Draft dependency |
| Неизменённые зависимости | maplib 0fff279ca88baa735faaa8ef01b78c6bb7973863; easypicker d6327f3de7a6d488a18d1896f8c97c60cd28d2a8 | remote master | Pins сохранены и присутствуют в remote master |

Порядок интеграции: Merge Commit maplibui #34, fetch remote merge commit,
repin app #54, Squash app и аудит remote inclusion перед выпуском. Текущий pin
на Draft head служит review/tests и не закрывает release-зависимость. Publisher
и desktop/mobile контракты не менялись; APK не публиковался.

Official app master f11d38f77e4caf1b569526c5f620b2ec513c5f9d повторно проверен
9 октября; общий выбор линейки и fork azimuth остаётся отличием форка.
