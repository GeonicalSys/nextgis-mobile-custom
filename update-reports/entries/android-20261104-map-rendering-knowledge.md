---
id: android-20261104-map-rendering-knowledge
area: Мобильное приложение
kind: internal
at: 2026-11-04T12:24:14+04:00
title: Контракты отрисовки из отдельных записей App
why: Обновлять договорённости по темам и хранить исторические результаты с их границами
covers:
  - agent-system/README.md
  - agent-system/knowledge/documentation/documentation-maintenance.json
  - agent-system/knowledge/documentation/map-rendering-background-map-ownership.json
  - agent-system/knowledge/documentation/map-rendering-batch-fill-applied-style.json
  - agent-system/knowledge/documentation/map-rendering-bounded-original-reads.json
  - agent-system/knowledge/documentation/map-rendering-bulk-pull-index-and-render-refresh.json
  - agent-system/knowledge/documentation/map-rendering-camera-rotation-and-location-action.json
  - agent-system/knowledge/documentation/map-rendering-canonical-layer-order-and-style-aliases.json
  - agent-system/knowledge/documentation/map-rendering-classic-native-payload-handoffs.json
  - agent-system/knowledge/documentation/map-rendering-classic-native-payload-projection.json
  - agent-system/knowledge/documentation/map-rendering-classic-style-firefly.json
  - agent-system/knowledge/documentation/map-rendering-classic-style-verification-scope.json
  - agent-system/knowledge/documentation/map-rendering-cold-index-rebuild-and-config-safety.json
  - agent-system/knowledge/documentation/map-rendering-cold-sketch-and-walk-preview.json
  - agent-system/knowledge/documentation/map-rendering-collector-order-and-workspace-lease.json
  - agent-system/knowledge/documentation/map-rendering-common-creation-category.json
  - agent-system/knowledge/documentation/map-rendering-compass-accuracy-preference.json
  - agent-system/knowledge/documentation/map-rendering-consumer-verification-profile.json
  - agent-system/knowledge/documentation/map-rendering-contracts-heading.json
  - agent-system/knowledge/documentation/map-rendering-copy-and-transform.json
  - agent-system/knowledge/documentation/map-rendering-cursor-fixture-camera.json
  - agent-system/knowledge/documentation/map-rendering-cursor-fixture-readiness.json
  - agent-system/knowledge/documentation/map-rendering-cursor-fixture-related-result.json
  - agent-system/knowledge/documentation/map-rendering-cursor-geometry-and-direction.json
  - agent-system/knowledge/documentation/map-rendering-d101-cold-frame-and-maplib-results-20261028.json
  - agent-system/knowledge/documentation/map-rendering-d101-read-transport-results-20261028.json
  - agent-system/knowledge/documentation/map-rendering-direct-sqlite-geometry-and-paint.json
  - agent-system/knowledge/documentation/map-rendering-direct-vector-tiles-compile-checkpoint.json
  - agent-system/knowledge/documentation/map-rendering-edit-rights-and-hidden-layers.json
  - agent-system/knowledge/documentation/map-rendering-empty-editor-hit-test-and-index.json
  - agent-system/knowledge/documentation/map-rendering-failed-underlay-import-cleanup.json
  - agent-system/knowledge/documentation/map-rendering-field-line-and-point-priority.json
  - agent-system/knowledge/documentation/map-rendering-first-project-owned-map.json
  - agent-system/knowledge/documentation/map-rendering-free-memory-policy-and-notice.json
  - agent-system/knowledge/documentation/map-rendering-full-and-lite-reloads.json
  - agent-system/knowledge/documentation/map-rendering-gps-display-accuracy-floor.json
  - agent-system/knowledge/documentation/map-rendering-gps-edit-camera-state.json
  - agent-system/knowledge/documentation/map-rendering-identify-refinement-and-label.json
  - agent-system/knowledge/documentation/map-rendering-immutable-ngrc-import.json
  - agent-system/knowledge/documentation/map-rendering-incremental-track-reloads.json
  - agent-system/knowledge/documentation/map-rendering-invariant-registry-links.json
  - agent-system/knowledge/documentation/map-rendering-legacy-render-mode-and-offline-routing.json
  - agent-system/knowledge/documentation/map-rendering-legacy-underlay-transfer-validation-checkpoint.json
  - agent-system/knowledge/documentation/map-rendering-line-label-placement.json
  - agent-system/knowledge/documentation/map-rendering-local-startup-and-http-diagnostics.json
  - agent-system/knowledge/documentation/map-rendering-location-and-camera-actions.json
  - agent-system/knowledge/documentation/map-rendering-map-action-touch-and-attribution.json
  - agent-system/knowledge/documentation/map-rendering-map-gesture-cancellation.json
  - agent-system/knowledge/documentation/map-rendering-map-indicator-producers.json
  - agent-system/knowledge/documentation/map-rendering-map-panel-accessibility.json
  - agent-system/knowledge/documentation/map-rendering-mapview-lifecycle-and-surface-recovery.json
  - agent-system/knowledge/documentation/map-rendering-measurements-menu.json
  - agent-system/knowledge/documentation/map-rendering-multipolygon-repair-and-crs.json
  - agent-system/knowledge/documentation/map-rendering-native-allocation-profiles-787-and-ec71.json
  - agent-system/knowledge/documentation/map-rendering-network-cursor-between-fixes.json
  - agent-system/knowledge/documentation/map-rendering-new-feature-style-category.json
  - agent-system/knowledge/documentation/map-rendering-no-track-endpoint-flags.json
  - agent-system/knowledge/documentation/map-rendering-opengl-backend-compatibility.json
  - agent-system/knowledge/documentation/map-rendering-opengl-sdk-dependency.json
  - agent-system/knowledge/documentation/map-rendering-operation-mode-diagnostics.json
  - agent-system/knowledge/documentation/map-rendering-ordinary-sqlite-vector-source.json
  - agent-system/knowledge/documentation/map-rendering-original-reads-heading.json
  - agent-system/knowledge/documentation/map-rendering-osm-at-stack-bottom.json
  - agent-system/knowledge/documentation/map-rendering-performance-investigation-boundaries.json
  - agent-system/knowledge/documentation/map-rendering-polygon-patterns-and-outlines.json
  - agent-system/knowledge/documentation/map-rendering-private-cold-render-profiling.json
  - agent-system/knowledge/documentation/map-rendering-raster-hot-add-order.json
  - agent-system/knowledge/documentation/map-rendering-raster-mbtiles-and-legacy-transfer.json
  - agent-system/knowledge/documentation/map-rendering-raster-visibility-and-background.json
  - agent-system/knowledge/documentation/map-rendering-release-221-preparation.json
  - agent-system/knowledge/documentation/map-rendering-release-225-native-validation-20261102.json
  - agent-system/knowledge/documentation/map-rendering-rendering-change-acceptance-matrix.json
  - agent-system/knowledge/documentation/map-rendering-rendering-ownership.json
  - agent-system/knowledge/documentation/map-rendering-reorder-gesture-cleanup.json
  - agent-system/knowledge/documentation/map-rendering-responsive-tool-rail.json
  - agent-system/knowledge/documentation/map-rendering-rule-handler-process-isolation.json
  - agent-system/knowledge/documentation/map-rendering-rule-style-defaults-and-zoom.json
  - agent-system/knowledge/documentation/map-rendering-selected-feature-actions.json
  - agent-system/knowledge/documentation/map-rendering-selected-track-gpx.json
  - agent-system/knowledge/documentation/map-rendering-separate-stakeout-and-azimuth-panels.json
  - agent-system/knowledge/documentation/map-rendering-shared-underlay-dedup-format.json
  - agent-system/knowledge/documentation/map-rendering-shared-underlay-import.json
  - agent-system/knowledge/documentation/map-rendering-sketch-insertion-form-and-edit-history.json
  - agent-system/knowledge/documentation/map-rendering-spatial-cache-consumers-and-rebuild.json
  - agent-system/knowledge/documentation/map-rendering-spatial-cache-field-observations-20261030.json
  - agent-system/knowledge/documentation/map-rendering-spatial-cache-import-readiness.json
  - agent-system/knowledge/documentation/map-rendering-spatial-cache-load-and-clear.json
  - agent-system/knowledge/documentation/map-rendering-spatial-cache-source-checkpoint-20261031.json
  - agent-system/knowledge/documentation/map-rendering-sqlite-phase-observation.json
  - agent-system/knowledge/documentation/map-rendering-sqlite-phase-terminal-signals.json
  - agent-system/knowledge/documentation/map-rendering-sqlite-priority-t40-results.json
  - agent-system/knowledge/documentation/map-rendering-staged-schema-replacement.json
  - agent-system/knowledge/documentation/map-rendering-stakeout-and-azimuth-overlays.json
  - agent-system/knowledge/documentation/map-rendering-startup-and-delayed-anr-journal.json
  - agent-system/knowledge/documentation/map-rendering-style-sibling-anchors.json
  - agent-system/knowledge/documentation/map-rendering-tap-tolerance-and-placement.json
  - agent-system/knowledge/documentation/map-rendering-tile-admission-and-derived-cache.json
  - agent-system/knowledge/documentation/map-rendering-top-location-overlay.json
  - agent-system/knowledge/documentation/map-rendering-touch-and-tile-observation.json
  - agent-system/knowledge/documentation/map-rendering-track-and-point-render-order.json
  - agent-system/knowledge/documentation/map-rendering-track-snapshot-boundary.json
  - agent-system/knowledge/documentation/map-rendering-track-stroke-and-shared-color.json
  - agent-system/knowledge/documentation/map-rendering-ui-source-review-checkpoint.json
  - agent-system/knowledge/documentation/map-rendering-underlay-insertion-position.json
  - agent-system/knowledge/documentation/map-rendering-user-track-layer-order.json
  - agent-system/knowledge/documentation/map-rendering-visible-layer-live-style-check.json
  - agent-system/knowledge/documentation/map-rendering-visible-only-identify.json
  - agent-system/knowledge/documentation/map-rendering.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/guides/documentation-maintenance.md
  - docs/tests/test_prose_knowledge.py
---

MOB-DEV-02. Контракты отрисовки хранятся в записях App, а датированные
результаты выделены в свидетельства. Оба пункта 12 имеют отдельные ID
и остаются в прежнем порядке. Проекция сохраняет адрес, заголовки,
ссылки и текст. Проверки сравнивают представление с Git и контролируют
адресную правку. Изменение относится к документации App.
