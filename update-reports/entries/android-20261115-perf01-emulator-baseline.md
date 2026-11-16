---
id: android-20261115-perf01-emulator-baseline
area: Мобильное приложение — PERF-01
kind: internal
at: '2026-11-15T22:12:44.075975+00:00'
title: 'PERF-01: ordinary/heavy technical emulator map baseline; only targeted checks'
why: Согласованные завершённые изменения рабочей сессии
covers:
- agent-system/knowledge/app-module-manifest/app-module-key-components-perf01mapbenchmarktest.json
- agent-system/knowledge/app-module-manifest/app-module-key-components-rel03resourcesafetytest.json
- agent-system/knowledge/app-module-manifest/app-module-manifest.json
- agent-system/knowledge/documentation/app-module-readme-perf01-technical-baseline.json
- agent-system/knowledge/documentation/app-module-readme.json
- agent-system/knowledge/documentation/automated-tests-perf01-technical-baseline.json
- agent-system/knowledge/documentation/automated-tests.json
- agent-system/knowledge/documentation/documentation-changelog-2026-11-15-rel03-resource-safety.json
- agent-system/knowledge/documentation/documentation-changelog-2026-11-16-perf01-technical-baseline.json
- agent-system/knowledge/documentation/documentation-changelog.json
- agent-system/knowledge/documentation/map-performance-perf01-technical-baseline.json
- agent-system/knowledge/documentation/map-performance.json
- agent-system/knowledge/documentation/mobile-roadmap-field-measurement-baseline.json
- agent-system/knowledge/documentation/mobile-roadmap-interruption-and-resource-exhaustion.json
- agent-system/knowledge/documentation/mobile-roadmap-queue-order.json
- agent-system/knowledge/documentation/mobile-roadmap-rel03-20261115.json
- agent-system/knowledge/documentation/mobile-roadmap-roadmap-overview-and-authority.json
- agent-system/provenance/structured-views.json
- app/docs/README.md
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01MapBenchmarkTest.java
- docs/architecture/map-performance.md
- docs/changelog.md
- docs/roadmap/mobile-history.md
- docs/roadmap/mobile.md
- docs/runbooks/automated-tests.md
- tools/mobile-test-plan.json
- tools/perf01_emulator_benchmark.py
- tools/tests/test_perf01_emulator_benchmark.py
- update-reports/entries/android-20261115-perf01-emulator-baseline.md
---

PERF-01: ordinary/heavy technical emulator map baseline; only targeted checks

Решения и зависимости:

- User selected technical ordinary/heavy maps on emulator first. Only directly related checks; general Full/tablet/physical/sync/energy remain deferred. Previous Core#72/UI#53/App#100/Root#298/Web#7 all actually merged, all81 non-report REL03 raw hashes unchanged; temporary context DBs827355136 bytes removed with absence receipt. New session is ACTIVE with accepted engine and clean synchronized bases.
- New test-only consumer uses unchanged MapDrawable/SQLite providers/native rendered-feature and frame APIs, then writes bounded JSON consumed by the guarded benchmark/statistics tool. No production API, server resource, saved project schema, permissions or Desktop/Web behavior changes. Synthetic database/root and exact verification package are fenced; warm source/provider identity and complete original-ID/label coverage are independently required.
- Ordinary400+80 and heavy6400+80 original65-coordinate circles; fixed11z/0,0/north-up/no tilt and recorded viewport/density/font. Require current CustomVectorSource and empty global lists, circle-fit original FIDs and placed123 labels per source, same input hashes after timing. Per-cell20 trials, median and nearest-rank p95; smoke1 never claims p95. Reject duplicates, missing/NaN/boolean, changing fixture/window, wrong fresh processes or replaced warm providers. Ready bound explicitly includes observer validation; SDK candidate alone is not acceptance.
- Bounded diagnostics have no all-tile denominator: no tile p95/cache-hit rate inferred. OS/font caches uncontrolled; ActivityScenario timer excludes Application/TestRunner startup. Sampled memory includes observer, not allocation attribution or leak/cap proof. Real-data SYNC/field/energy/phone/tablet and general Full remain open. Existing global syntax/candidate UNKNOWN and customer.json parse gap stay visible.
- Canonical methodology/runbook/module, actual REL03 merged roadmap facts and current PERF01 stage are updated with original unrelated values preserved. Test plan changes only new emulator/isolated entry and one new Related area; every old phone/area value matches. Source compile PASS and6 statistics units PASS; runtime measurements have not yet been claimed. Full metadata finalization/report generation follow the session route.
- Calibration correction, additive to prior notes: schema1 attempts01/02 FAILED and are not speed measurements. Screenshot shows oversized coincident literal labels; attempt02 also used too-short30s window. Schema2 keeps480/6480 circles65coords and strict ready/geometry oracle, but fixes two sparse123 labels in distinct positions (mainFID1/2431,overlay80), font2.5, and hashes label input too. Exact control FIDs are now required for both live sources. Ready window restored120s; no production code or readiness weakening. This technical series isolates geometry density; label-heavy/real projects/energy/general Full remain open. Seven host statistics/profile checks cover the new harness. Source/API dependencies remain test-only, no saved state migration.
- Smoke03 schema2 ordinary fresh passed with480 original geometries and two exact control-label FIDs. Warm initial opening FAILED strict camera oracle: UID SQLite zoom7 and screenshot tiny grid; no warm samples accepted. Preferences alone did not control restored view state. Harness now declares camera11/0 after current MapView attaches, using public map API within measured opening; diagnostics name mismatched camera. Warm-away frame must be newer than its move timestamp. Ready/source/geometry/label assertions preserved. Attempt03 exact source snapshot retained; guarded cleanup completeJob0. A host guard test failed due missing args in its mocked base initializer; test setup corrected and all7 direct tests PASS.
- Smoke04 PASS, stable_inputs true,4 measurements/6nativeattempts/2JUnitmethods: both480/6480 fresh openings and same-live-provider returns. Exact source archive29files retained; four PNGs independently viewed, polygons/overlay/two123labels visible; warm fade is first visible-label state, no pixel-perfect settled-opacity claim. APK build PASS; seven host statistics/profile units directPASS. Native fixture cleanup, settings/ANGLE restore, owned console/process exit allPASS, Jobremaining0, no forced_stop or resource_guard_error. Prior failed calibration is excluded. Canonical docs record scoped smoke only; whole PERF01 field/sync/energy remainsOPEN. Final20/cell quantitative report will be appended in Q and journal without claiming general tests.
- First baseline20 aborted at ordinary fresh05: frame/input assertions passed, but sampler finalization interrupted its own in-flight interruptible /proc read and surfaced ClosedByInterruptException. Failed series preserved with exact29file source archive; not pooled or accepted as baseline. Guarded cleanupPASS/Job0. Observer close now publishes stop flag and bounded2s join (100ms cadence) without interrupting I/O; genuine read errors and hung thread still fail. No production changes, no new thresholds or measurement/oracle changes. Next attempt reruns complete80samples on a new run nonce; no mixed-source pooling. Existing scopedsmoke04 fact remains pinned to its original source, final native receipt binds new source.
- Test-only closure: Perf01MapBenchmarkTest reuses VectorStyleRenderingTest.Fixture for isolated seed setup, VerificationEnvironment fences and unchanged public native/source APIs. Host composes unchanged Verification driver and mobile-test-plan Related catalog;7 host units protect summary/profile rejection. Plan exact preservation receipt keeps all old phone/area values. No Desktop/Web producer, saved user format, released APK or server contract changed. Source graph session refresh/current check input digest retains global UNKNOWN and foreign committed basis; no absence-of-link or native-acceptance claim from graph.
- COMPLETED agreed first technical PERF01 stage only: baseline20-02 PASS stable_inputs=true;44native attempts/2distinct methods/80samples, no skips; seven final host units PASS stable_inputs=true; docs/UI static guards55/274/79 PASS. Independently recalculated all raw metrics and viewed four PNGs. Ordinary fresh p50/p95=6467.955/9996.652ms, warm285.448/585.463ms; heavy fresh15179.881/15664.298ms, warm2197.998/3579.317ms. Max sampled RSS ordinary330.7/355.7MiB, heavy495.7/699.7MiB; instrumented process, not layer budget/leak proof or phone energy. Exact29file snapshot/raw hashes unchanged at final review. Source fingerprint3e6ddbe52d3deb76d89fe3a06e6d15dca02fcd4ff52676888a39a912caeac341. Normal guarded cleanup complete, no remaining owned Job/process, no forced stop. Earlier calibration/aborted series excluded. OS/font/background updater/network uncontrolled. Whole PERF01 real-project/sync/energy matrix remains OPEN; general Full/tablet/physical/publication deferred. No production optimization or release claimed.

Полный журнал, входные хэши и результаты проверок: сессия perf01-emulator-01a1d9ac, задача android-20261115-perf01-emulator-baseline. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
