---
id: android-20261122-change-impact-reviewed-scopes
area: Реестр правил проверки изменений Android
kind: internal
at: '2026-11-22T10:43:55+04:00'
title: Уточнить применимость 68 правил Android
why: У проверенных правил были исходные пути без области применимости в каноническом реестре
covers:
  - agent-system/knowledge/change-impact/change-impact-background-gps-recovery.json
  - agent-system/knowledge/change-impact/change-impact-background-location-warning.json
  - agent-system/knowledge/change-impact/change-impact-backup-and-destructive-layer-change.json
  - agent-system/knowledge/change-impact/change-impact-build-and-flavors.json
  - agent-system/knowledge/change-impact/change-impact-cascading-form-lists.json
  - agent-system/knowledge/change-impact/change-impact-collector.json
  - agent-system/knowledge/change-impact/change-impact-compass-optional-vibration.json
  - agent-system/knowledge/change-impact/change-impact-compass-sector-setting.json
  - agent-system/knowledge/change-impact/change-impact-conditional-form-rules.json
  - agent-system/knowledge/change-impact/change-impact-documentation-ci.json
  - agent-system/knowledge/change-impact/change-impact-feature-copy-transform.json
  - agent-system/knowledge/change-impact/change-impact-feature-type-creation.json
  - agent-system/knowledge/change-impact/change-impact-geometry-edit-history.json
  - agent-system/knowledge/change-impact/change-impact-gps-cache-witness.json
  - agent-system/knowledge/change-impact/change-impact-layer-fill-and-reorder.json
  - agent-system/knowledge/change-impact/change-impact-layer-pending-indicator.json
  - agent-system/knowledge/change-impact/change-impact-legacy-underlay-migration.json
  - agent-system/knowledge/change-impact/change-impact-lisa-ecosystem-contract.json
  - agent-system/knowledge/change-impact/change-impact-local-delivery-project-clock.json
  - agent-system/knowledge/change-impact/change-impact-local-log-startup.json
  - agent-system/knowledge/change-impact/change-impact-local-tile-cache-and-io-admission.json
  - agent-system/knowledge/change-impact/change-impact-local-vector-layers.json
  - agent-system/knowledge/change-impact/change-impact-local-vector-tile-labels.json
  - agent-system/knowledge/change-impact/change-impact-local-vector-tile-offline-transport.json
  - agent-system/knowledge/change-impact/change-impact-local-write-and-track-reliability.json
  - agent-system/knowledge/change-impact/change-impact-maintenance-reminders.json
  - agent-system/knowledge/change-impact/change-impact-map-base-layers.json
  - agent-system/knowledge/change-impact/change-impact-map-runtime-diagnostics.json
  - agent-system/knowledge/change-impact/change-impact-maplibre-backend.json
  - agent-system/knowledge/change-impact/change-impact-maplibre-rendering.json
  - agent-system/knowledge/change-impact/change-impact-mixed-map-candidates-and-field-lines.json
  - agent-system/knowledge/change-impact/change-impact-multipolygon-repair.json
  - agent-system/knowledge/change-impact/change-impact-native-heap-map-diagnostic-route.json
  - agent-system/knowledge/change-impact/change-impact-ngfp-lookup-prefetch.json
  - agent-system/knowledge/change-impact/change-impact-ngrc-local-underlay.json
  - agent-system/knowledge/change-impact/change-impact-ngw-account-identity.json
  - agent-system/knowledge/change-impact/change-impact-ngw-full-snapshot-admission.json
  - agent-system/knowledge/change-impact/change-impact-ngw-lookup-cache-content.json
  - agent-system/knowledge/change-impact/change-impact-ngw-resource-checkbox-selection.json
  - agent-system/knowledge/change-impact/change-impact-ngw-resource-row-text-fit.json
  - agent-system/knowledge/change-impact/change-impact-ngw-resource-selection-lifecycle.json
  - agent-system/knowledge/change-impact/change-impact-ngw-url-import.json
  - agent-system/knowledge/change-impact/change-impact-point-firefly.json
  - agent-system/knowledge/change-impact/change-impact-polygon-style-controls.json
  - agent-system/knowledge/change-impact/change-impact-private-resource-profiling.json
  - agent-system/knowledge/change-impact/change-impact-reliability-ci.json
  - agent-system/knowledge/change-impact/change-impact-render-memory-acceptance.json
  - agent-system/knowledge/change-impact/change-impact-render-memory-pressure-and-notice.json
  - agent-system/knowledge/change-impact/change-impact-repository-topology.json
  - agent-system/knowledge/change-impact/change-impact-required-fields.json
  - agent-system/knowledge/change-impact/change-impact-self-hosted-updater.json
  - agent-system/knowledge/change-impact/change-impact-shared-map-api.json
  - agent-system/knowledge/change-impact/change-impact-shared-tracks-and-classic-styles.json
  - agent-system/knowledge/change-impact/change-impact-spatial-cache-consistency.json
  - agent-system/knowledge/change-impact/change-impact-sqlite-bounded-original-reads.json
  - agent-system/knowledge/change-impact/change-impact-sqlite-native-layer-priority.json
  - agent-system/knowledge/change-impact/change-impact-sqlite-native-vectors.json
  - agent-system/knowledge/change-impact/change-impact-sqlite-phase-diagnostics.json
  - agent-system/knowledge/change-impact/change-impact-sync-journal-cancellation.json
  - agent-system/knowledge/change-impact/change-impact-sync-network-feedback.json
  - agent-system/knowledge/change-impact/change-impact-sync-notifications.json
  - agent-system/knowledge/change-impact/change-impact-tool-tap-and-selection.json
  - agent-system/knowledge/change-impact/change-impact-track-controls.json
  - agent-system/knowledge/change-impact/change-impact-track-delivery.json
  - agent-system/knowledge/change-impact/change-impact-track-selected-gpx-share.json
  - agent-system/knowledge/change-impact/change-impact-track-stop-and-legacy-colors.json
  - agent-system/knowledge/change-impact/change-impact-vector-spatial-cache-recovery.json
  - agent-system/knowledge/change-impact/change-impact-visible-line-labels.json
  - agent-system/provenance/structured-views.json
---

Для 68 действующих правил Android сохранены точные пути файлов и корни каталогов. Выбор проверен по закреплённым исходникам App, maplib, maplibui и EasyPicker. Для каждого правила сверены совпадения и исключения.
