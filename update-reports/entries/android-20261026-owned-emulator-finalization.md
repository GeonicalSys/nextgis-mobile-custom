---
id: android-20261026-owned-emulator-finalization
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T01:06:00Z
title: Остановка собственного эмулятора при потере ADB и gate после cleanup
why: Обрыв авторизации ADB прервал восстановление настроек и оставил owned read-only AVD работающим
covers:
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_restore.py
---

MOB-01/REL-03. Финализация всегда обращается к консоли собственного AVD,
даже если восстановление настроек через ADB завершилось ошибкой. Проверяются
закрытие console port и завершение сохранённого Popen launcher. Заимствованные
эмуляторы и общий ADB server не останавливаются. cleanup.json содержит
фактические restore/stop результаты, неизвестное не объявляется успехом.

PASS и full-passed.json записываются после финализации; cleanup error сохраняет
failed receipt. Шесть новых runner regressions проверяют early failure,
transport error, borrowed resource, process join и запрет ложного PASS.
Owned PID 25128/16444 от прерванного запуска остановлены через штатную console;
отсутствие процессов проверено. Production приложения не изменялись.
