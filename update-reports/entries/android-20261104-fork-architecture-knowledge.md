---
id: android-20261104-fork-architecture-knowledge
area: Мобильное приложение
kind: internal
at: 2026-11-04T14:06:49+04:00
title: Архитектура и сопровождение форка из записей App
why: Разделить границы владельцев, каталог отличий и процедуру upstream merge
covers:
  - agent-system/README.md
  - agent-system/knowledge/documentation/documentation-maintenance.json
  - agent-system/knowledge/documentation/fork-customizations-account-isolation-identity-and-versioning.json
  - agent-system/knowledge/documentation/fork-customizations-backup-and-render-order-invariants.json
  - agent-system/knowledge/documentation/fork-customizations-build-editing-and-rendering-routes.json
  - agent-system/knowledge/documentation/fork-customizations-catalog-maintenance-and-official-review.json
  - agent-system/knowledge/documentation/fork-customizations-catalog-scope-and-official-handoff.json
  - agent-system/knowledge/documentation/fork-customizations-composition-isolation-and-recovery-invariants.json
  - agent-system/knowledge/documentation/fork-customizations-diagnostic-queue-owner-route.json
  - agent-system/knowledge/documentation/fork-customizations-ngw-account-import-and-layer-config-routes.json
  - agent-system/knowledge/documentation/fork-customizations-preferences-underlays-upstream-and-delivery-routes.json
  - agent-system/knowledge/documentation/fork-customizations-project-composition-backup-and-workspace-routes.json
  - agent-system/knowledge/documentation/fork-customizations-project-script-route-and-open-work.json
  - agent-system/knowledge/documentation/fork-customizations-project-sync-owner-route.json
  - agent-system/knowledge/documentation/fork-customizations-stakeout-and-local-editing-invariants.json
  - agent-system/knowledge/documentation/fork-customizations.json
  - agent-system/knowledge/documentation/overview-dependency-direction-and-registry-updates.json
  - agent-system/knowledge/documentation/overview-desktop-ngw-and-offline-import-boundaries.json
  - agent-system/knowledge/documentation/overview-gradle-api-and-transitive-consumers.json
  - agent-system/knowledge/documentation/overview-repository-roles-and-remotes.json
  - agent-system/knowledge/documentation/overview-runtime-owner-routes.json
  - agent-system/knowledge/documentation/overview.json
  - agent-system/knowledge/documentation/upstream-fork-model-conflict-classification-and-invariant.json
  - agent-system/knowledge/documentation/upstream-fork-model-library-commit-and-root-pointer.json
  - agent-system/knowledge/documentation/upstream-fork-model-origin-upstream-and-effective-branch.json
  - agent-system/knowledge/documentation/upstream-fork-model.json
  - agent-system/knowledge/documentation/upstream-sync-backup-tags-and-library-merge-order.json
  - agent-system/knowledge/documentation/upstream-sync-compatibility-checks-and-delivery-authority.json
  - agent-system/knowledge/documentation/upstream-sync-conflict-rationale-and-smoke-contract.json
  - agent-system/knowledge/documentation/upstream-sync-heads-and-cleanliness-gate.json
  - agent-system/knowledge/documentation/upstream-sync-inventory-and-conflict-inputs.json
  - agent-system/knowledge/documentation/upstream-sync-windows-powershell-inventory-and-safe-directory.json
  - agent-system/knowledge/documentation/upstream-sync.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/guides/documentation-maintenance.md
---

MOB-DEV-02. Четыре штатные проекции сохраняют исходный Markdown.
Правки границ API и порядка проверки принадлежат разным темам.
