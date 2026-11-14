---
id: android-20261113-mobile-contracts-verification
area: Проверки и документация
kind: internal
at: '2026-11-13T12:08:36.583777+00:00'
title: 'GEO-02, UX-06, REL-04: документы, контракты и локальная проверка'
why: Согласованные завершённые изменения рабочей сессии
covers:
- agent-system/knowledge/app-module-manifest/app-module-key-components-backgroundlocationservice-6c5e1d3188.json
- agent-system/knowledge/app-module-manifest/app-module-key-components-layersfragment-b39c824ea1.json
- agent-system/knowledge/app-module-manifest/app-module-key-components-settingsfragment-31b04c3998.json
- agent-system/knowledge/app-module-manifest/app-module-key-components-stakeoutcontroller-4756aba044.json
- agent-system/knowledge/documentation/app-module-readme-background-gps-service-and-open-screen-lease.json
- agent-system/knowledge/documentation/app-module-readme-crash-recovery-ownership-and-geometry.json
- agent-system/knowledge/documentation/app-module-readme-settings-updater-and-layer-backups.json
- agent-system/knowledge/documentation/crash-recovery-cold-save-map-reload-and-mode-reset.json
- agent-system/knowledge/documentation/location-pipeline-automatic-foreground-and-service-owner-leases.json
- agent-system/knowledge/documentation/mobile-roadmap-background-gps-control.json
- agent-system/knowledge/documentation/mobile-roadmap-edit-session-recovery.json
- agent-system/knowledge/documentation/mobile-roadmap-menu-maintenance.json
- agent-system/knowledge/documentation/stakeout-automatic-background-gps-versus-navigation-owner.json
- agent-system/knowledge/documentation/track-controls-stop-confirmation-and-session-uuid.json
- agent-system/knowledge/invariants/invariants-inv-crash-draft-recovery.json
- agent-system/knowledge/invariants/invariants-inv-gps-track-filter.json
- agent-system/knowledge/ui-contracts/ui-contracts-ui-layer-visibility.json
- agent-system/knowledge/ui-contracts/ui-contracts-ui-navigation-stakeout-target.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-0f2bde2988c58932bc54041c.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-35ee898c245f227828c0176f.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-cf6d88239faca089e51547bf.json
- agent-system/knowledge/ui-contracts/ui-sources-map-resume-settings.json
- agent-system/knowledge/ui-contracts/ui-sources-map-stakeout-start.json
- agent-system/knowledge/ui-contracts/ui-sources-memory-warning-menu.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-map-storage-map-prefs.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-map-storage-menu.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-stakeout-selection-restore.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-04d36974e4eef80b8c521efb.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-691a0fd49c08f2840fa5a78c.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-ac53c9b98167988d67fd6cc0.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-ed10f810e8e4d74a43eda8e4.json
- agent-system/provenance/structured-views.json
- app/docs/README.md
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/SettingsMaintenanceTest.java
- app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
- docs/architecture/crash-recovery.md
- docs/architecture/location-pipeline.md
- docs/architecture/stakeout.md
- docs/architecture/track-controls.md
- docs/registry/invariants.yaml
- docs/registry/ui-contracts.json
- docs/roadmap/mobile.md
- update-reports/entries/android-20261113-mobile-contracts-verification.md
---

GEO-02, UX-06, REL-04: документы, контракты и локальная проверка

Решения и зависимости:

- Emulator attempt04: all18 BackgroundGpsNativeTest and all3 SettingsMaintenanceTest passed; FormSaveRecovery and stale-form/manual geometry identity cases passed. New end-to-end interrupted-form case sampled checkpoint before actual onPause; fix test ordering to verify after leaving the form and rerun. Phone/Full deferred by user.
- Canonical records and projections changed only in roadmap lifecycle/result, GPS/form/stakeout ownership, maintenance/track UI routes, reviewed method/XML/file tripwires, four module responsibilities and two invariants. Parsed before/after review found no added/removed keys and no unrelated values changed. All previous UNKNOWN target replacement obligations retained. Structured-view proof requires normal regeneration after renderers check projections.
- Attempt05 retained two fixture failures: ActivityMonitor can return before the form resumes and its real checkpoint exists; asynchronous accessibility click/Back can leave General settings before the modal window closes. Await real form checkpoint; use actual owned dialog window and wait for detachment. Do not relax deletion/data/lock expectations.
- Related emulator09 PASS:48 distinct cases and all6 real-window profiles at620dpi/font200%; own emulator/settings finalized. Targeted JVM17 PASS, retained XML. Final one-line follow-up notifies existing layer-list callback after releasing a proven unowned lock; only registered consumer is LayersFragment -> adapter.notifyDataSetChanged. New real-row test checks pencil disappearance/buttons without reopening, actual other-row pencil click, stale form identity and DB preservation. Affected edit-recovery4 rerun in10; GPS/Track/Walk/Stakeout handlers/resources unchanged after09. Phone/Full explicitly deferred by owner; no publication.
- Confirmed dynamic link: MapFragment.releaseUnownedLayerLock invokes existing mModeListener; only registration found is LayersFragment.onModeChangeListener -> LayersListAdapter.notifyDataSetChanged. Immediate rebind restores pencil/more/visibility for the affected row. Persisted in UI-LAYER-VISIBILITY and verified against actual attached rows and actual other-row pencil click.
- Final Native10 PASS4 affected recovery cases including real open drawer, no-op prevention, stale callbacks, old geometry and new Save. Native09 PASS48 +6 enlarged real windows; targeted JVM17 PASS with retained XML. Emulator settings restored and owned process/job0 in both successful receipts. Native08 unsupported low-memory fallback canceled after ANR; transport disappearance preserved in failed cleanup receipt; read-only owned VM exited, no phone/host settings touched. Current final source/projection changes after these receipts are prose lifecycle metadata only. Docs/report validation is the remaining local check, recorded in evidence.
- App-only UI/lifecycle/ownership implementation. No field/schema/permission/publication API changes; Desktop/Web consumers excluded on source evidence. Existing journal formats and source/FID replacement UNKNOWN retained. Current user defers physical phone and Full until publication. Source and all preexisting work preserved; no push, merge or publication in this implementation task. Complete registered context delivered; relevant owner records/source read, unrelated UNKNOWN memberships are not claimed model-read.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261113-mobile-contracts-verification. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
