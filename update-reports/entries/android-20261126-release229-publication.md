---
id: android-20261126-release229-publication
area: Android publication
kind: user
at: '2026-11-26T09:38:36.812420+00:00'
title: Verify and publish Android release 229 for LISA and BELKA
why: Согласованные завершённые изменения рабочей сессии
covers:
- WHATS_NEW.md
- agent-system/knowledge/documentation/app-module-readme-crash-recovery-ownership-and-geometry.json
- agent-system/knowledge/documentation/app-module-readme-verification-startup-and-underlay-lease-barrier.json
- agent-system/knowledge/documentation/automated-tests-verification-startup-recovery-and-lease-barrier.json
- agent-system/knowledge/documentation/build-matrix.json
- agent-system/knowledge/documentation/crash-recovery-recovery-dialog-and-host-lifecycle.json
- agent-system/knowledge/documentation/flavors-and-versioning.json
- agent-system/knowledge/documentation/fork-customizations-ngw-account-import-and-layer-config-routes.json
- agent-system/knowledge/documentation/ngw-sync-and-storage-local-crud-and-outbox-transaction.json
- agent-system/knowledge/documentation/ngw-sync-and-storage-sync-regression-scope.json
- agent-system/knowledge/documentation/ngw-sync-and-storage.json
- agent-system/knowledge/documentation/official-differences-lisa-history-and-startup-229-20261126.json
- agent-system/knowledge/documentation/official-differences.json
- agent-system/knowledge/documentation/project-synchronization-user-guide.json
- agent-system/knowledge/documentation/release-apk.json
- agent-system/knowledge/documentation/release-note-3.1.2.34.json
- agent-system/knowledge/documentation/release-note-3.1.2.35.json
- agent-system/knowledge/documentation/release-notes.json
- agent-system/knowledge/ui-contracts/ui-contracts-registry.json
- agent-system/knowledge/ui-contracts/ui-contracts-ui-project-recovery.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-4a8815f9c3c363b96babbbb1.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-66447f73f07d22eb763cd5f7.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-changes-copy-ru.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-fb87c1c299a5598abcd742f1.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-changes-centre-view.json
- agent-system/knowledge/ui-contracts/ui-sources-wf-project-recovery-confirmation.json
- agent-system/provenance/structured-views.json
- app/build.gradle
- app/docs/README.md
- app/src/androidTest/java/com/nextgis/mobile/reliability/ChangesCentreNativeTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/DefaultBasemapStartupTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/LocalChangesUploadTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectRecoveryNativeTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/SharedTracksTest.java
- app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
- app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
- app/src/main/java/com/nextgis/mobile/MainApplication.java
- app/src/main/java/com/nextgis/mobile/activity/ChangesCentreActivity.kt
- app/src/main/java/com/nextgis/mobile/activity/ProjectRecoveryActivity.kt
- app/src/main/java/com/nextgis/mobile/util/DefaultBasemapInitializer.java
- app/src/main/res/values-ru/local_changes.xml
- app/src/main/res/values/local_changes.xml
- app/src/test/java/com/nextgis/mobile/util/DefaultBasemapInitializerTest.java
- docs/architecture/crash-recovery.md
- docs/architecture/ngw-sync-and-storage.md
- docs/guides/project-synchronization-user-guide.md
- docs/reference/build-matrix.md
- docs/reference/flavors-and-versioning.md
- docs/reference/fork-customizations.md
- docs/reference/official-differences.md
- docs/registry/ui-contracts.json
- docs/runbooks/automated-tests.md
- docs/runbooks/release-apk.md
- tools/mobile-test-plan.json
- tools/verify-apk-version-matrix.ps1
- update-reports/entries/android-20261126-basemap-startup.md
- update-reports/entries/android-20261126-history-uploads.md
- update-reports/entries/android-20261126-legacy-recovery-229.md
- update-reports/entries/android-20261126-recovery-confirmation.md
- update-reports/entries/android-20261126-release229-publication.md
---

Verify and publish Android release 229 for LISA and BELKA

Решения и зависимости:

- Only the two reviewed mobile owners were committed and pushed. The Desktop index and unrelated work are preserved. Android native acceptance is scoped to the user's emulator-only instruction for release 229.
- Published both stable flavors as version 3.1.2.35 (229). Public manifests, history and both downloadable APK hashes match the accepted signed artifacts. Previous release notes remain included. User-scoped emulator acceptance and owned scratch cleanup are recorded.

Полный журнал, входные хэши и результаты проверок: сессия 2026-11-25, задача android-20261126-release229-publication. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
