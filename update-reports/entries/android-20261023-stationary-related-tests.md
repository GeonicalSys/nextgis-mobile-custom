---
id: android-20261023-stationary-related-tests
area: Мобильное приложение
kind: internal
at: 2026-10-23T19:23:46+02:00
title: Адресные проверки редактора на отдельном тестовом приложении смартфона
why: Проверить новые команды на реальном устройстве без изменения рабочего приложения
covers:
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/mobile-test-plan.json
  - tools/verify-mobile-tests.ps1
  - tools/tests/test_mobile_test_support.py
  - tools/tests/test_mobile_phone_only.py
---

Related feature-editing имеет явную классификацию неподвижных phone-сценариев. PhoneOnly требует serial и не заменяет Full. Runner сверяет полноту native исходов, собирает независимые phone failures, фильтрует диагностические строки emulator inventory и прекращает ожидание завершившегося эмулятора. System font/display/radio смартфона не меняются.
