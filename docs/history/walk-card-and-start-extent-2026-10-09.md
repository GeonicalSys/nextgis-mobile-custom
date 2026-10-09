---
title: Компактные значки обхода и GPS-опора — проверка 2026-10-09
type: history
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - maplibui/src/main/java/com/nextgis/maplibui/view/WalkRecordingPanel.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/WalkEditService.java
---

# Панель обхода и GPS-опора создания

Панель сокращена до одной строки 48 dp: название слоя, дискета, пауза/плей,
крестик. Touch targets 48 dp, подсказки долгого нажатия и accessibility names;
светлая/тёмная поверхность, мягкая граница и основной цвет текущей темы.
Длинное название сокращается с доступом к полному тексту. В штатном состоянии
нет второй подписи, GPS accuracy или меню. Point lock и ошибка checkpoint
сохраняют понятное исключительное сообщение.

Все три действия требуют подтверждения. Дискета получает FINISHED от сервиса,
проверяет достаточность разных вершин и сразу открывает атрибуты. При нехватке
точек owner закрывается без строки/формы. PAUSE сбрасывает принятый хвост,
сохраняет прежние geometry/owner и gps_paused, не добавляет новые fixes до RESUME.
Продолжение предупреждает о соединении с последней точкой; UUID, phase и point
lock повторно проверяются сервисом после подтверждения. Уведомление о паузе
не утверждает потерю GPS при ручной паузе.

Начало создания по местоположению или нового обхода после категории перемещает
камеру к точной GPS-опоре той же геометрии, сохраняя zoom. Восстановление
черновика не вызывает этот стартовый pan.

## Выполненные проверки

- JBR OpenJDK 21.0.9 / Gradle 9.3.1: maplibui Debug, Lisa Debug/test APK,
  Lisa Release и Belka Release собраны; release variants — проверка кода,
  выпуск не выполнялся. Версии прежние, production 3.1.2.27/221.
- maplib 508/508, maplibui 92/92 unit; MapLayoutContractTest пройден.
- Изолированный read-only AVD Medium_Phone_API_36.0, Android 16/API 36 x86_64,
  emulator-5556: итоговая native regression 10/10 вместе (90.167 s).
- Проверены: insufficient Finish без объекта/формы; одна дискета и реальная
  форма/SQLite для нового и существующего обхода; отмена подтверждения Save;
  нижний anchor и подтверждение Cancel; Pause/Resume с отказом подтверждения,
  запретом роста геометрии и point lock, появившимся при открытом диалоге;
  точное центрирование 55/37 при zoom 11.5 после category picker/recreate;
  отмена категории точки во время обхода; перенос категории через interruption;
  прежний путь карандаш → категория → атрибуты → SQLite; независимые icon states.
- Нативно измерены и отрисованы обе темы при обычном шрифте на 360 dp и
  200% на 280 dp: панель 48 dp, каждый hit target 48 dp, границы не выходят
  за view, имя доступно, enabled controls полностью непрозрачны. Debug использует
  зелёную палитру; production берёт свою палитру из темы.
- Первая 8-case попытка: функциональные проверки прошли, cleanup живого сервиса
  пытался удалить fixture layer до асинхронного DISCARD. Исправлено ожидание
  terminal owner; итоговая 10-case попытка прошла. После native visual review
  восстановлен padding дискеты после background assignment; новый снимок проверен.
- docs-check -RunTests: validator и 7/7 tests пройдены.

Рабочий телефон не использовался. Fixtures и их drafts удаляются только по
созданным тестом идентификаторам. Не выполнены полевая GNSS/screen-off проверка,
реальный process death в переходе Save/form, camera/save failure, physical phone,
landscape/tablet и production GUI. Структура layout configurations проверена unit.

## Матрица присутствия и порядок интеграции

Повторный fetch всех Android owners успешен. Открыты только свои Draft app #54
(base my-maplibre) и maplibui #34 (base master); в maplib, easypicker и publisher
нет open PR или дополнительных published codex branches. Все предыдущие семь
app commits от 7850bbe до aa6e73a и четыре library commits от 7af9adae до ae8ddcb3
остаются ancestors. Ни одна нужная правка не отложена.

| Требование | Владелец / commit | PR / base | Присутствие в proposed tip |
|---|---|---|---|
| Mixed forms, category defaults, длинные списки | maplibui 7af9adae, eb08352b, ae8ddcb3 | #34 / master | Предки ca1bac81; сохранены |
| Минимальные вершины, icon isolation, direct Cancel | maplibui 871c3874 | #34 / master | Предок ca1bac81; сохранён |
| Однострочная панель, PAUSE и подтверждаемое управление | maplibui ca1bac8127d976d3cfdea5dd415541164d0af82a | #34 / master | Exact consumer gitlink; Draft dependency |
| Все creation routes и прежнее завершение | app aa6e73a, 64b37de и их пять предков | #54 / my-maplibre | Предки текущего follow-up; сохранены |
| Стартовый GPS pan, подтверждения и немедленная форма | app follow-up на aa6e73a; MapFragment blob 3f3a8bfb873ba6ca5e5a35e7e08cd2a01ff2769d | #54 / my-maplibre | Exact code blob; итоговый commit указан в PR |
| Maplib / easypicker dependencies | 0fff279ca88baa735faaa8ef01b78c6bb7973863 / d6327f3de7a6d488a18d1896f8c97c60cd28d2a8 | remote master | Pins сохранены, входят в удалённые master |

Сначала Merge Commit библиотеки #34, fetch её remote merge commit, repin app,
затем Squash #54 и повторный аудит remote inclusion. Текущий gitlink на Draft
head пригоден для review/tests, но не закрывает release-зависимость. APK
не публиковался, publisher и desktop/mobile форматы не менялись.

Official app f11d38f77e4caf1b569526c5f620b2ec513c5f9d, maplibui
d9f5241c0e8a4904b6359bba9fae4a56bd62dd33 и maplib
b8f3e3e6bf4bad56f8ce910c885ea6af1b898998 проверены повторно 9 октября.
Независимая WalkRecordingPanel/WalkSessionStore остаётся отличием форка.
