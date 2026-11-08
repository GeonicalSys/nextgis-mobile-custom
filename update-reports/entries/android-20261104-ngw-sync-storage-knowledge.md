---
id: android-20261104-ngw-sync-storage-knowledge
area: Мобильное приложение
kind: internal
at: 2026-11-04T11:24:36+04:00
title: Архитектура синхронизации и хранения из записей App
why: Редактировать правила и датированные свидетельства независимо, сохраняя прежний документ
covers:
  - agent-system/knowledge/documentation/ngw-sync-and-storage-account-ack-and-project-clock.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-account-authentication-readback.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-account-identity-and-variants.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-attachment-hydration.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-attachment-removal-and-id-ack.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-authoritative-schema-rebuild-decision.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-backup-content-and-local-attachments.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-backup-format-and-removal.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-backup-quota-and-read-only-layer.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-cancellation-and-child-drain.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-cascades-and-null-clearing.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-collector-composition-recovery-journal.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-credentials-and-normalization.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-desktop-producer-and-ngw-id-source-frame.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-destructive-mutation-backup-gate.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-error-message-regression-and-controls.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-explained-snapshot-gap-checkpoint.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-failed-clock-commit-f61.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-foreground-service-and-notifications.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-clock-api-keys-and-disk-limits.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-clock-pr86-delivery-and-f61-supersession.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-clock-prefs-owner-and-publication.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-clock-source-verification-and-replay.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-clock-writer-and-readers.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-project-clock-consumers.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-full-sync-workspace-scope-and-leases.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-gps-power-saving-segment-gap.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-historical-lookup-key-set-replacement.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-incremental-pull-and-spatial-index.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-interrupt-safe-journal-commit.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-interrupted-journal-native-regressions.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-journal-process-view-and-owner-state.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-journal-process-view-defect-witness.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-journal-process-view-verification.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-layer-config-and-account-scheduling.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-library-and-app-responsibilities.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-local-attachment-retention-after-push.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-local-crud-and-outbox-transaction.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-local-edit-commit-admission.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-local-outbox-worker-scheduling.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-lookup-and-snapshot-draft-basis.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-lookup-and-snapshot-source-verification.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-lookup-save-errors-and-ack-gates.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-multipolygon-import-validation.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-new-feature-type-save.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-ngfp-prefetch-reader-and-consumers.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-ngfp-prefetch-source-and-delivery-basis.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-offline-admission-and-retries.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-outbox-and-track-indicators.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-owning-layer-fill-and-recovery.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-post-pair-ack-recovery-and-retries.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-pre-sync-integrity-repair-and-diagnostics.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-production-221-account-frame.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-project-form-metadata-and-rules.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-provider-uris-and-notifications.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-queue-and-child-admission.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-queue-diagnostics.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-recovery-pair-journal.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-remote-retry-and-push-before-pull.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-required-fields-metadata-and-save.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-required-fields-october-verification.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-resource-selector-compact-state.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-resource-selector-recovery.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-rtree-buffered-legacy-read.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-safe-sync-tracing.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-september-device-anr-and-snapshot-comparison.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-service-cancellation-and-workspace-reservation.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-shared-history-database-resolution.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-shared-track-batch-ack.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sqlite-durability-gates.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-staged-layer-replacement.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-cancellation-and-release.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-clock-pin-f61.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-move-reset-admission.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-move-retire-and-actual-path.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-move-source-verification.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-storage-terminal-refresh-and-reset-effects.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-streamed-snapshot-http-206-admission.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-activity-and-progress.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-and-rebuild-regression-scope.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-none-and-config-refresh.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-regression-scope.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-timeouts-and-cancellation.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-track-mode-storage.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-track-send-setting-and-registration.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-typed-error-causes-and-user-messages.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-untracked-streamed-snapshot.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-url-resource-import.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-vector-cache-device-witness-and-source-verification.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-vector-spatial-cache-recovery.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-verification-account-isolation.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-workspace-identities.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-workspace-ownership-intro.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-workspace-source-review-and-open-witnesses.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage.json
  - agent-system/provenance/documentation.json
  - agent-system/README.md
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/roadmap/mobile.md
  - docs/tests/test_prose_knowledge.py
---

MOB-DEV-02. Отдельные записи сохраняют владельца workspace, границы
локального commit, pair ACK и полного project clock, правила lookup,
backup, вложений и треков. Прежние заголовки и версии воспроизводятся
по адресу архитектурного документа.
