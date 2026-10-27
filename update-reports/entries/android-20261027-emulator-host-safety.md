---
id: android-20261027-emulator-host-safety
area: Локальное мобильное тестирование
kind: internal
at: 2026-10-27T16:45:00+01:00
title: Блокировка аппаратного GPU эмулятора после зависаний Windows
why: Два дневных аппаратных запуска AVD оборвались рядом с подтверждёнными Windows hard hangs и принудительными перезагрузками
covers:
  - tools/mobile_emulator_safety.py
  - tools/run_mobile_tests.py
  - tools/verify-mobile-tests.ps1
  - tools/tests/test_mobile_emulator_safety.py
---

MOB-03. Runner больше не запускает GPU host: для установленного Emulator35.6.11
задан SwiftShader indirect с отключённым Vulkan. Проверяется фактическая GLES
строка SurfaceFlinger; host/неизвестный renderer, включая заимствованный AVD,
отклоняется до test workload. Запуск эмулятора требует явного AllowEmulator;
stationary PhoneOnly не затрагивается. Это admission после оценки владельцем
риска, а не гарантия стабильности Windows.

Kernel-Power41/6008 подтверждают три неожиданных завершения27 октября, но
BugcheckCode=0 не устанавливает причину. Дневные delivery05/stress09 оборваны
при запуске host GPU; GTX1660Ti/528.49 — кандидат, не доказанный виновник.
Новые AVD исполнения остановлены. Новый software маршрут проверяется только
unit doubles; реальный запуск, обновление драйвера и настройки Windows не выполнены.
