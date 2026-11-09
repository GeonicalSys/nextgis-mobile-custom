---
id: android-20261110-full-physical-graphics-routing
area: Мобильное приложение
kind: internal
at: '2026-11-10T02:10:00+04:00'
title: Выполнять графику и нагрузку Full на физических устройствах
why: Программная графика эмулятора искажала отрисовку и результаты проверки нагрузки
covers:
  - tools/mobile-test-plan.json
  - tools/mobile_test_support.py
  - tools/mobile_render_acceptance.py
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_test_support.py
  - tools/tests/test_mobile_render_acceptance.py
  - tools/tests/test_mobile_native_failures.py
  - agent-system/knowledge/invariants/invariants-inv-mobile-publication-acceptance.json
  - agent-system/knowledge/smoke-tests/smoke-tests-smoke-render-memory-native.json
  - agent-system/knowledge/documentation/automated-tests.json
  - agent-system/knowledge/documentation/automated-tests-full-variant-matrix-and-native-routing.json
  - agent-system/knowledge/documentation/automated-tests-stress-required-load-device-matrix-and-failure.json
  - agent-system/knowledge/documentation/automated-tests-render-stress-publication-matrix-boundary.json
  - agent-system/knowledge/documentation/automated-tests-stress-full-device-matrix-and-incident-model.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-10-physical-full-graphics.json
  - agent-system/knowledge/documentation/release-apk.json
  - agent-system/knowledge/documentation/mobile-roadmap-release-225.json
  - agent-system/knowledge/documentation/mobile-roadmap-map-memory.json
  - agent-system/knowledge/documentation/mobile-roadmap-heavy-map-diagnostics.json
  - agent-system/knowledge/documentation/automated-tests-prepared-direct-vector-tile-related.json
  - agent-system/provenance/structured-views.json
---

Выпускная проверка требует реальные результаты отрисовки на выбранном телефоне
и одинаковую техническую нагрузку на двух разных физических устройствах.
Выбор планшета определяется типом устройства. Пороговые значения и проверки
полноты отрисовки сохраняются. Автопроверка офлайн проходит через USB и читает
вручную установленные параметры сети. Сценарии GNSS, разрешений, доставки
диагностики и окна разных размеров сохраняют свою часть Full на эмуляторе.
