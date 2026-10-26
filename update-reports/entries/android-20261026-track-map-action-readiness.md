---
id: android-20261026-track-map-action-readiness
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T11:21:40Z
title: Действия выбранного трека после подтверждения стартового предупреждения
why: Full08 нажимал перекрывающий кнопку Цвет startup Snackbar вместо кнопки; требуется закрытие уведомления перед проверкой фонового экрана
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - tools/mobile-test-plan.json
---

MOB-01/REL-03. Full08:252 native PASS/1 FAIL на Samsung, остальные классы
включая исправленные23 формы завершились. Unchanged tablet track-controls7/7
PASS, включая тот же map action. Первоначальный лог не доказывал причину.

Перед pointer записываются actual node bounds, screen centre и геометрия
Snackbar/его родителей с проверкой попадания. Следующая ошибка включает
предыдущее нажатие, bounded сообщения доступны под TrackTouchQA. Жесты,
таймауты, ожидание палитры и assertions не ослаблены; production не меняется.
Адресная область track-map-actions проверяет один исходный метод без остальных
долгих UI matrices и не заменяет Full.

Адресное воспроизведение подтвердило перекрытие: Color rect53,1812..248,1980,
pointer150.5,1896 лежал внутри Snackbar text32,1822..734,2214. Нажатие не
достигало кнопки. Fixture перед проверкой фонового экрана закрывает только
три известных предупреждения GPS/батареи штатным ACTION_DISMISS собственного
приложения, затем ждёт исчезновения overlay и устойчивой цели. Потерянное
нажатие не переигрывается, системные battery settings не меняются. Проверка
самого предупреждения остаётся отдельным обязательным native сценарием.

После подтверждения предупреждения исходный map action1/1 PASS. Полный
Related track-controls: emulator8/8, Samsung7/7 и T40 Plus7/7 PASS. Увеличенные
диалоги, общий выбор и native point/field-line order не исключались. Owned AVD
finalized, settings restored/console closed/launcher exited. Требуется новый Full.
