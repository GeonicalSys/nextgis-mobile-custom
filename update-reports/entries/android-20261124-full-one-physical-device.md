---
id: android-20261124-full-one-physical-device
area: "Сопровождение"
kind: internal
at: 2026-11-24T10:52:08Z
title: "Полная проверка Android на одном физическом устройстве"
why: "Для выпускной проверки можно выбрать доступный смартфон или планшет вместе с эмулятором"
covers:
  - AGENTS.md
  - agent-system/authored-policy.json
  - agent-system/knowledge/app-module-manifest/app-module-key-components-verificationtestrunner-cd98e31c3f.json
  - agent-system/knowledge/documentation/app-module-readme-deferred-tab-resume-and-owner-state.json
  - agent-system/knowledge/documentation/app-module-readme-verification-runner-and-publication-receipt.json
  - agent-system/knowledge/documentation/app-module-readme-verification-startup-and-underlay-lease-barrier.json
  - agent-system/knowledge/documentation/automated-tests-approved-project-and-policy-only-snapshot.json
  - agent-system/knowledge/documentation/automated-tests-emulator-software-admission-and-phone-only.json
  - agent-system/knowledge/documentation/automated-tests-full-and-check-publication-entry.json
  - agent-system/knowledge/documentation/automated-tests-full-variant-matrix-and-native-routing.json
  - agent-system/knowledge/documentation/automated-tests-maintenance-related-variant-and-ui.json
  - agent-system/knowledge/documentation/automated-tests-manual-reliability-ci-and-local-phone-gate.json
  - agent-system/knowledge/documentation/automated-tests-phone-lock-wait-and-full-order.json
  - agent-system/knowledge/documentation/automated-tests-physical-phone-identity-and-fixed-package-guard.json
  - agent-system/knowledge/documentation/automated-tests-render-stress-publication-matrix-boundary.json
  - agent-system/knowledge/documentation/automated-tests-stress-full-device-matrix-and-incident-model.json
  - agent-system/knowledge/documentation/automated-tests-stress-required-load-device-matrix-and-failure.json
  - agent-system/knowledge/documentation/automated-tests-verification-startup-recovery-and-lease-barrier.json
  - agent-system/knowledge/documentation/automated-tests.json
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/change-checklist.json
  - agent-system/knowledge/documentation/device-smoke-tests-stationary-phone-versus-movement-emulator.json
  - agent-system/knowledge/documentation/flavors-and-versioning.json
  - agent-system/knowledge/documentation/map-performance-map-stress-fixture-oracle.json
  - agent-system/knowledge/documentation/mobile-roadmap-heavy-map-diagnostics.json
  - agent-system/knowledge/documentation/mobile-roadmap-interruption-and-resource-exhaustion.json
  - agent-system/knowledge/documentation/mobile-roadmap-map-memory.json
  - agent-system/knowledge/documentation/mobile-roadmap-roadmap-review-process.json
  - agent-system/knowledge/documentation/mobile-roadmap-stale-data-and-update-reminders.json
  - agent-system/knowledge/documentation/mobile-roadmap-started-work-heading.json
  - agent-system/knowledge/documentation/release-apk-full-and-check-publication-order.json
  - agent-system/knowledge/documentation/release-apk-publication-blockers-and-test-project-consent.json
  - agent-system/knowledge/documentation/release-apk.json
  - agent-system/knowledge/invariants/invariants-inv-mobile-publication-acceptance.json
  - agent-system/knowledge/policy/application.form-native-isolation.json
  - agent-system/knowledge/policy/application.local-verification-isolation.json
  - agent-system/knowledge/policy/workspace.full-before-publication.json
  - agent-system/knowledge/policy/workspace.local-checks-and-cloud-boundary.json
  - agent-system/knowledge/policy/workspace.permitted-test-project.json
  - agent-system/knowledge/policy/workspace.publication-receipt-freshness.json
  - agent-system/knowledge/policy/workspace.verification-isolation.json
  - agent-system/knowledge/smoke-tests/smoke-tests-auto-maintenance-reminders.json
  - agent-system/knowledge/smoke-tests/smoke-tests-auto-mobile-full.json
  - agent-system/knowledge/smoke-tests/smoke-tests-auto-mobile-publication-gate.json
  - agent-system/knowledge/smoke-tests/smoke-tests-auto-render-memory.json
  - agent-system/knowledge/smoke-tests/smoke-tests-smoke-layer-chooser.json
  - agent-system/knowledge/smoke-tests/smoke-tests-smoke-render-memory-native.json
  - agent-system/provenance/structured-views.json
  - app/docs/README.md
  - app/docs/manifest.yaml
  - docs/architecture/map-performance.md
  - docs/guides/change-checklist.md
  - docs/reference/build-matrix.md
  - docs/reference/flavors-and-versioning.md
  - docs/registry/invariants.yaml
  - docs/registry/smoke-tests.yaml
  - docs/roadmap/mobile.md
  - docs/runbooks/automated-tests.md
  - docs/runbooks/device-smoke-tests.md
  - docs/runbooks/release-apk.md
  - tools/mobile_render_acceptance.py
  - tools/mobile_test_support.py
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_phone_only.py
  - tools/tests/test_mobile_render_acceptance.py
  - tools/tests/test_mobile_test_support.py
---

Full выполняется на эмуляторе и одном выбранном смартфоне или планшете. На том же физическом устройстве проверяются формы, графика, нагрузка карты и реальные офлайн-тайлы. Для офлайн-проверки устройство остаётся доступным по USB при включённом авиарежиме и выключенном Wi-Fi.

Правила актуальности исходников и APK, проверка разрешённого проекта, полнота каталога и оконные профили сохраняются. Check-Publication сверяет нагрузку и физические сценарии с устройством из квитанции полного прогона. Само изменение регламента не подтверждает прохождение выпуска.
