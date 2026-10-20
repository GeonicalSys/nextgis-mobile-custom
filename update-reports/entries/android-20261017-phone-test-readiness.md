---
id: android-20261017-phone-test-readiness
area: Сопровождение
kind: internal
at: '2026-10-17T19:11:54+03:00'
title: Готовность телефона к мобильным тестам
why: Неготовый телефон и состояние окон не должны давать ложные отказы мобильной приёмки
covers:
- .github/workflows/android-reliability.yml
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/reliability/CascadingFormsTest.java
- maplibui
- tools/mobile_test_support.py
- tools/run_mobile_tests.py
- tools/tests/test_mobile_test_support.py
source_commits:
- 477c9057c1f41308cfa0599e1b0c9e14537cff79
---
Обновлены запуск мобильной проверки, подготовка телефона, ожидания интерфейса и связанные проверки форм.
