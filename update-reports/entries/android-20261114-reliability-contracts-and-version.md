---
id: android-20261114-reliability-contracts-and-version
area: Документация и подготовка мобильной версии
kind: user
at: '2026-11-14T03:05:57.852963+00:00'
title: Актуальные контракты REL-01/REL-02/UX-02/MOB-REL-03 и подготовка версии 227
  без публикации
why: Согласованные завершённые изменения рабочей сессии
covers:
- agent-system/knowledge/config-keys/config-keys-build-inputs-cireliabilitychecks.json
- agent-system/knowledge/config-keys/config-keys-build-inputs-diagnostics-properties.json
- agent-system/knowledge/config-keys/config-keys-build-inputs-sentry-properties.json
- agent-system/knowledge/config-keys/config-keys-build-inputs-usage-properties.json
- agent-system/knowledge/config-keys/config-keys-registry.json
- agent-system/knowledge/dependencies/dependencies-runtime-contracts-app-error-diagnostics.json
- agent-system/knowledge/documentation/build-matrix.json
- agent-system/knowledge/documentation/mobile-roadmap-project-recovery-and-draft-preservation.json
- agent-system/knowledge/documentation/mobile-roadmap-release-observability.json
- agent-system/knowledge/documentation/mobile-roadmap-reliable-server-ack.json
- agent-system/knowledge/documentation/mobile-roadmap-sync-and-changes-center.json
- agent-system/knowledge/documentation/mobile-usage-production-default-process-and-private-config.json
- agent-system/knowledge/documentation/release-apk.json
- agent-system/knowledge/ui-contracts/ui-contracts-registry.json
- agent-system/knowledge/ui-contracts/ui-contracts-ui-changes-centre.json
- agent-system/knowledge/ui-contracts/ui-contracts-ui-project-recovery.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-0f2bde2988c58932bc54041c.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-4a8815f9c3c363b96babbbb1.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-66447f73f07d22eb763cd5f7.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-changes-centre-screen.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-changes-copy-ru.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-fb87c1c299a5598abcd742f1.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-project-recovery-copy-ru.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-recovery-screen.json
- agent-system/knowledge/ui-contracts/ui-sources-memory-warning-menu.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-changes-centre-actions.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-changes-centre-view.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-form-draft-store.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-form-save.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-local-changes-readonly.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-map-storage-menu.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-project-recovery-create.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-project-recovery-restore.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-recovered-form-completion.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-04d36974e4eef80b8c521efb.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-691a0fd49c08f2840fa5a78c.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-ac53c9b98167988d67fd6cc0.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-ed10f810e8e4d74a43eda8e4.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-changes-project.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-changes-recovery.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-changes-release.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-changes-send.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-menu-changes-centre.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-project-changes.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-project-recovery.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-recovery-create.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-recovery-import.json
- agent-system/knowledge/ui-contracts/ui-widget-evidence-reliability-recovery-source-export.json
- agent-system/provenance/structured-views.json
- app/build.gradle
- docs/architecture/mobile-usage.md
- docs/reference/build-matrix.md
- docs/registry/config-keys.yaml
- docs/registry/dependencies.yaml
- docs/registry/ui-contracts.json
- docs/roadmap/mobile.md
- docs/runbooks/release-apk.md
- tools/verify-apk-version-matrix.ps1
- update-reports/entries/android-20261114-reliability-contracts-and-version.md
---

Актуальные контракты REL-01/REL-02/UX-02/MOB-REL-03 и подготовка версии 227 без публикации

Решения и зависимости:

- Reviewed native roadmap bodies, build/release/usage/Publisher docs, private input declarations, UI catalogue schema, guarded form/draft methods, both screens and registry metadata. Full context exports 03/04 delivered separately from model reading. Both public manifests were read as 226/3.1.2.32; prepare only source 227/3.1.2.33 and retain Debug 218. Canonical existing values/order preserved by exact subtract-new-record audits; original records retained on Q. Existing four menu declarations and maplib JTS build file reproduce exact prior reviewed hashes after subtracting only the new menu item/version constant. Source preservation is not runtime acceptance.
- App docs validator PASS after public contract is used as diagnostics private-input source, with no manufactured private file. Native-10 is running against frozen App/maplib/maplibui sources. Required signed positive Lisa/Belka admission remains NOT_RUN pending approved diagnostics.properties; Full/phone/merge/publish not authorized at this stage.
- Native-10 PASS 21/21 distinct selected emulator cases, including UI, lost/invalid ACK, real process death/restart, full recovered-form Save, stale sibling ACK and source suppression. App SHA256 5c5682739ff79ffdb99075727ee9b5201a8b10a27654a0001d26cca824a1f851; test dc8fd01caddb32338128cdc1891e71de214ce44dc7e8c2be2f86f3189c5da13e; tested source fingerprint aa1c8f2d8c449f42bafb1343e50654367631be62cfce865faa2ae1742d5f83b9. Owned emulator settings restored, process/job closed, zero remaining; cleanup PASS. All 2205 executable/test input hashes unchanged after prose receipt updates. Full/physical/production positive still NOT_RUN.
- Updated the old MapFragment UI-boolean literal to the actual process-owned lifecycle, adding strict source witnesses and preserving every unrelated curated value. Existing broad selected-FID/full-snapshot acceptance UNKNOWN stays explicit. Dependency write/check rebuilt explicit own live owner frame plus pinned foreign HEADs; all 234 old provisional observation objects preserved. Input digest 683fdbc5148455643818a35ca4174515584ac2255f9a49bcb81de54a6b2aecd2; metadata-only final docs follow this frame.
- App documentation PASS and Publisher projection check PASS. Broad Desktop docs initially reported own mobile stale literal (now fixed/structurally checked), a pending own observation report card, and foreign Desktop Vologda-move/newline obligations. Desktop owner confirms these are its earlier work and is read-only until writer handoff. Do not weaken or silently exempt foreign obligations. No commit/push/merge/publication performed.
- MOB-REL-03 source controls and isolated transport tests are implemented; positive real signed Lisa/Belka admission needs the existing approved diagnostics.properties, whose path was requested and remains unanswered. No fabricated token, live production event or phone test. Storage on Q retained as compact evidence, failed native-02 APK pair on diagnostic hold; shared SDK/AVD/cache reused, no new runtime/image copies.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261114-reliability-contracts-and-version. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
