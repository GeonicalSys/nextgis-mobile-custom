---
id: android-20261028-related-native-continuation
area: Локальные Android проверки
kind: internal
at: 2026-10-28T06:40:00+01:00
title: Сохранение всех независимых Related ошибок и полного начала native журнала
why: Первый assertion в изолированном классе прерывал остальные методы Related, а общий системный logcat tail терял нужное начало сценария
covers:
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_native_failures.py
---

MOB-04, app82. Завершённый instrumentation assertion/protocol FAIL сохраняется
в runs/progress ещё до исключения. Related класс продолжает остальные независимые
методы с прежним отдельным verification reset каждого; общая Related проверка
остаётся FAIL. Зависимые seed/workload цепочки не продолжаются без успешного
seed. Setup/transport/неизвестная runtime ошибка по-прежнему прерывает прогон.
Это не retry до зелёного результата и не разрешение продолжать потерянный host.

Для SQLite native методов runner начинает ограниченный UID-only logcat до
instrumentation и завершает только собственный adb client в finally до сбора
JSON/PNG и следующего reset. Реализация fixed verification package/UID/4MiB
кольца и её проверки принадлежат отдельной карточке helper. Ошибка диагностики
записывается отдельно и не заменяет исходный native/transport failure.
Working Geonical/Debug, проекты и системные настройки телефона не затрагиваются.

Новые технические проверки требуют продолжения после assertion с сохранённым
FAIL, остановки после transport failure, остановки UID capture до artifacts и
сохранения исходной transport ошибки при ошибке cleanup. Фактический запуск
ожидает общего tool suite; fake Popen/runner проверки не являются native PASS.

Whole-contour dependency plan выполнен до edits: related-failure-continuation-
plan01.json, UNKNOWN/REFRESHED_IN_MEMORY. Чужой canonical desktop index/registry
остаётся явным owner follow-up; source discovery не выдаётся за native acceptance.
