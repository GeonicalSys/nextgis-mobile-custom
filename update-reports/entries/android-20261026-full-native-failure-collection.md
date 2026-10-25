---
id: android-20261026-full-native-failure-collection
area: Проверки мобильного приложения
kind: internal
at: 2026-10-25T23:52:00Z
title: Полный сбор независимых native ошибок без ложного PASS
why: Прежний Full останавливался на первом JUnit сбое и не достигал оставшихся обязательных классов
covers:
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/tests/test_mobile_native_failures.py
  - tools/tests/test_mobile_test_support.py
---

MOB-01/REL-03. Full сохраняет failed native attempt в receipt и продолжает
остальные независимые проверки, в том числе методы изолированного класса.
Перед каждым отдельным вызовом сохраняется штатный reset только verification
workspace. Любая неуспешная попытка оставляет итог FAIL даже при успешном
повторе; publication gate проверяет это дополнительно. UI matrix с ошибкой
не получает успешный check. Build/setup/transport/locked-device ошибки
останавливают запуск как прежде, Related остаётся fail-fast.

Пять новых regressions проверяют продолжение после JUnit сбоя, запрет ложного
PASS при повторе, штатный успешный путь и остановку при транспортной ошибке.
Общий runner suite: 32/32 PASS. Публикация и merge не выполнялись.
