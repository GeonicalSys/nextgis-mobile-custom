---
id: android-20261104-map-performance-knowledge
area: Мобильное приложение
kind: internal
at: 2026-11-04T13:11:11+04:00
title: Производительность карты по темам и свидетельствам
why: Редактировать действующие правила отдельно от датированных замеров и их границ
covers:
  - agent-system/README.md
  - agent-system/knowledge/documentation/documentation-maintenance.json
  - agent-system/knowledge/documentation/map-performance-6039-attribute-read-comparison-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-current-cab9-d101-memory-pair-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-first-b-diagnostic-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-geojsonvt-allocation-profile-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-labels-on-abba-memory-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-observed-source-ids-20261028.json
  - agent-system/knowledge/documentation/map-performance-787-private-build-20261028.json
  - agent-system/knowledge/documentation/map-performance-843d-t40-phase-capture-20261028.json
  - agent-system/knowledge/documentation/map-performance-bounded-original-read-transport.json
  - agent-system/knowledge/documentation/map-performance-cache-and-sdk-recovery-limits.json
  - agent-system/knowledge/documentation/map-performance-cache-queue-acceptance-boundary.json
  - agent-system/knowledge/documentation/map-performance-cache-queue-apk-build-and-owner-observation-20261028.json
  - agent-system/knowledge/documentation/map-performance-cache-queue-discovery-invalid-20261028.json
  - agent-system/knowledge/documentation/map-performance-cache-queue-follow-up-20261028.json
  - agent-system/knowledge/documentation/map-performance-classic-editor-and-native-payload.json
  - agent-system/knowledge/documentation/map-performance-classic-render-cache-and-uri-gate.json
  - agent-system/knowledge/documentation/map-performance-d101-api30-read-transport-20261028.json
  - agent-system/knowledge/documentation/map-performance-d101-dense-1403-frame-and-maplib-20261028.json
  - agent-system/knowledge/documentation/map-performance-derived-pbf-cache-ledger.json
  - agent-system/knowledge/documentation/map-performance-direct-sqlite-render-pipeline.json
  - agent-system/knowledge/documentation/map-performance-ec71-native-allocation-candidate-20261028.json
  - agent-system/knowledge/documentation/map-performance-geojsonvt-plateau-and-crash-trigger.json
  - agent-system/knowledge/documentation/map-performance-implemented-rendering-boundaries.json
  - agent-system/knowledge/documentation/map-performance-map-stress-fixture-oracle.json
  - agent-system/knowledge/documentation/map-performance-memory-notice-a56-result-20261102.json
  - agent-system/knowledge/documentation/map-performance-memory-notice-process-scope.json
  - agent-system/knowledge/documentation/map-performance-memory-pressure-data-and-lifecycle.json
  - agent-system/knowledge/documentation/map-performance-memory-pressure-policy.json
  - agent-system/knowledge/documentation/map-performance-memory-safety-fixture-oracle.json
  - agent-system/knowledge/documentation/map-performance-open-candidates-heading.json
  - agent-system/knowledge/documentation/map-performance-open-geojsonvt-ownership.json
  - agent-system/knowledge/documentation/map-performance-open-lazy-render-preparation.json
  - agent-system/knowledge/documentation/map-performance-open-native-style-and-memory-matrix.json
  - agent-system/knowledge/documentation/map-performance-open-tablet-replay.json
  - agent-system/knowledge/documentation/map-performance-original-data-and-label-safety.json
  - agent-system/knowledge/documentation/map-performance-performance-acceptance-matrix.json
  - agent-system/knowledge/documentation/map-performance-pipeline-heading.json
  - agent-system/knowledge/documentation/map-performance-private-profile-route-20261028.json
  - agent-system/knowledge/documentation/map-performance-private-profiler-aggregation.json
  - agent-system/knowledge/documentation/map-performance-private-profiler-export-and-stop.json
  - agent-system/knowledge/documentation/map-performance-private-profiler-export-fields.json
  - agent-system/knowledge/documentation/map-performance-private-t40-capture-plan-20261028.json
  - agent-system/knowledge/documentation/map-performance-private-t40-capture-result-20261028.json
  - agent-system/knowledge/documentation/map-performance-private-t40-install-and-smoke-boundary-20261028.json
  - agent-system/knowledge/documentation/map-performance-related-full-and-release-gates.json
  - agent-system/knowledge/documentation/map-performance-shared-io-admission.json
  - agent-system/knowledge/documentation/map-performance-sqlite-phase-observation.json
  - agent-system/knowledge/documentation/map-performance-tablet-diagnostic-acceptance-boundary.json
  - agent-system/knowledge/documentation/map-performance-tablet-diagnostics-heading.json
  - agent-system/knowledge/documentation/map-performance-track-render-snapshot.json
  - agent-system/knowledge/documentation/map-performance.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/guides/documentation-maintenance.md
---

MOB-DEV-02. Штатный проектор собирает прежний Markdown из записей App.
Каждый замер D101 и 787 сохраняет свой набор входов и границы проверки.
Открытые вопросы связаны с MOB-04. Изменение относится к документации.
