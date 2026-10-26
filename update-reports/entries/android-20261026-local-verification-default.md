---
id: android-20261026-local-verification-default
area: Локальная автоматическая приёмка
kind: internal
at: 2026-10-26T21:00:00Z
title: Проверки Android и документации выполняются локально без автоматических Actions
why: Пользователь потребовал не расходовать лимиты GitHub на проверки, доступные на ноутбуке
covers:
  - .github/workflows/android-reliability.yml
  - .github/workflows/docs-validate.yml
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/mobile-test-plan.json
  - tools/docs-check.ps1
  - tools/tests/test_mobile_test_support.py
  - tools/tests/test_mobile_ci_routing.py
  - tools/tests/test_mobile_real_windows.py
---

MOB-01/REL-03. Оба Actions workflow оставлены workflow_dispatch-only; локальный
Full остаётся обязательным перед публикацией, Related — после доработки.
В Full перенесены6 прежних cloud-only real-window profiles:620dpi/font200%,
portrait/landscape, native/compat map dialog и settings. Publication gate
требует отдельный real_window_matrix. Docs gate локально проверяет report
cards/guard regressions и полный task diff с change-impact, затем docs tests.
Linux Mesa/Xvfb остаётся отдельной ручной средой, не приравнивается к Windows
host OpenGL. Настройки branch protection не меняются. Native каталог254
сохранён; изменения маршрута не удаляют ни один сценарий.

Адресно:8 native запусков окна (2distinct methods/6 profiles) PASS;
runner53/report guard26/docs24 PASS; Android units913 PASS, обе release source
sets compile PASS. Смартфон и планшет подключены для нового Full после app merge.
