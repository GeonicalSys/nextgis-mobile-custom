---
id: android-20261111-emulator-software-angle
area: Мобильное приложение
kind: internal
at: '2026-11-11T09:56:39+04:00'
title: Восстановить программную графику эмулятора и ограничить зависшие процессы
why: Classic SwiftShader искажает GLSL uniform blocks, а прежние запуски эмулятора сопровождались полными зависаниями Windows
covers:
  - docs/reference/official-differences.md
  - docs/architecture/ui-accessibility.md
  - agent-system/knowledge/documentation/ui-accessibility-native-large-text-layout-and-touch-acceptance.json
  - agent-system/knowledge/documentation/ui-accessibility.json
  - agent-system/knowledge/documentation/official-differences-emulator-software-angle.json
  - agent-system/knowledge/documentation/official-differences.json
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
  - tools/tests/test_mobile_native_failures.py
  - agent-system/knowledge/documentation/build-matrix.json
  - docs/reference/build-matrix.md
  - tools/tests/test_mobile_restore.py
  - tools/mobile_angle_driver.py
  - tools/emulator-angle-driver.json
  - tools/fixtures/emulator-gles/GlesUboProbe.java
  - tools/fixtures/emulator-gles/ProbeActivity.java
  - tools/fixtures/emulator-gles/AndroidManifest.xml
  - tools/mobile_emulator_safety.py
  - tools/run_mobile_tests.py
  - tools/verify-mobile-tests.ps1
  - tools/tests/test_mobile_angle_driver.py
  - tools/tests/test_mobile_emulator_safety.py
  - agent-system/knowledge/documentation/automated-tests-emulator-software-admission-and-phone-only.json
  - agent-system/knowledge/documentation/mobile-roadmap-heavy-map-diagnostics.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-11-software-angle.json
  - docs/runbooks/automated-tests.md
  - docs/roadmap/mobile.md
  - docs/changelog.md
  - agent-system/knowledge/documentation/automated-tests.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/documentation/app-module-readme.json
  - agent-system/knowledge/documentation/app-module-readme-verification-runner-and-publication-receipt.json
  - agent-system/knowledge/documentation/map-performance.json
  - agent-system/knowledge/documentation/map-performance-map-stress-fixture-oracle.json
  - agent-system/knowledge/app-module-manifest/app-module-manifest.json
  - agent-system/knowledge/app-module-manifest/app-module-key-components-verificationtestrunner-cd98e31c3f.json
  - agent-system/provenance/structured-views.json
  - app/docs/README.md
  - app/docs/manifest.yaml
  - docs/architecture/map-performance.md
---


MOB-03. Classic SwiftShader independently reproduces a GLSL ES3 uniform-block
error: fragment-only block before a shared block produces [102,178,178,255],
while the independent expectation remains [51,77,179,255], tolerance1. The
unchanged control and reverse-order shaders distinguish the corruption.

The validated Windows profile uses WHPX, official Emulator37.2.13/build16740729,
API36 Google APIs x86_64 revision7, system GuestAngle/SkiaVK and CPU Lavapipe.
The app and MapLibre remain OpenGL ES; no product Java/Kotlin, shaders, library
version, release version, resource formats or saved-state migrations change.
Runtime, system-image fingerprint and loaded GLES library have pinned identities.
The reused runtime and image/cache live on Q; machine paths stay out of Git.

Actual host and guest CPU-device identities, real /proc/self/maps library path,
library SHA256 and three independent pixel oracles are mandatory before native
checks. An earlier diagnostic incorrectly inferred a packaged ANGLE identity;
the final implementation uses and verifies system ANGLE, with no external
driver package or global/per-app driver-setting mutations.

Windows starts the SDK suspended inside an owned Job before children can run.
Admission, 2 CPU/2560MiB guest, actual WHPX and guest RAM bounds are checked.
A live Windows memory-pressure guard stops only this owned process tree;
console ownership is checked before ADB emu kill, bounded cleanup waits for all
owned processes. Independent T40 observation remains available. The historical
whole-PC hard hang has no kernel dump, so a unique root cause is not claimed.
The NVIDIA+AEHD combination is removed by WHPX and CPU-only rendering.

Primary failures and cleanup_error remain distinct. An interrupted native
attempt is atomically checkpointed before executing its command, with expected
cases/selectors and phase. Disconnected/unstarted attempts cannot contribute
passed cases. Unrelated disk stress receipts cannot inject current-run evidence.
Existing cleanup tests now reach the intended failure stage rather than passing
because an earlier incomplete fixture failed. New regressions exercise the
original shader corruption, false driver identity, unknown package preservation,
owned/foreign console attribution, hung-child shutdown and interrupted receipts.

Dependency closure: host runner -> owned AVD -> CPU Vulkan -> system GLES ANGLE
-> unchanged verification APK/MapLibre graphics assertions. Build/cache receipts
bind their source/toolchain/APKs. Native mobile-test-plan.json/expectations and
physical Full routing are preserved: graphics/stress on smartphone and tablet,
full offline only on USB smartphone, Wi-Fi T40 remains online, no SM-X115.
Desktop/NGW/Collector/SQLite data contracts are unchanged; Web integration is a
foreign workspace observation, not an edited owner or runtime dependency here.
Ambiguous/dynamic dependency closure stays UNKNOWN/UNPROVEN, not a proof of no
dependency. Registered context delivery and targeted model reading are separate.

Pre-edit original context and foreign-owner changes are retained under
Q:/Codex/Scratch/emulator-host-recovery-20261111/evidence. The current context
block preserves its own before/after baseline; earlier former obligations and
the original checkpoint are not discarded when the Web owner is enrolled by
another chat.

Diagnostic evidence already passed all11 unchanged polygon-style/line-label
cases in run20261111T102715Z-4f670a25, min available Windows RAM2.15GiB, all owned
SDK processes exited, no matching Windows fault event. This prototype receipt
does not substitute acceptance of the final repository implementation.
Staged harness regressions:236 passed. Real Windows Job/console smoke passed;
an injected OS low-memory signal stopped all owned parent/child processes and
preserved an unrelated synthetic process.

Final source-bound unit/docs/native acceptance is recorded in the PR and in
evidence/final-acceptance.json under the diagnostic task directory. The final
repository CLI must pass independently of the earlier prototype.
This is a harness/developer workflow change. No publication or release tag is
authorized; the existing app version is retained. Merge is explicitly requested.
No release Full receipt or hardware GPU/performance acceptance is claimed.

Local diagnostics and storage disposition:
Q:/Codex/Scratch/emulator-host-recovery-20261111/evidence and storage.json.

The final emulator qualification exposed a test race at density620/font200%:
realMapCloseAndDialogActionsStayReachable cached the close button's screen
coordinates before PNG capture. The first real GPS callback arrived during
that gap and reflowed the lower status panel, so the injected touch missed.
Run20261111T133742Z-7a97f0c9 retains this failure, all11 graphics passes and
successful bounded cleanup; it is not relabeled a successful Related run.

The test now resolves the current transformed view after the latest committed
frame immediately before a single physical DOWN/UP gesture, verifies both
events reach that same action and retains the original mode/dialog assertions.
A synthetic first GPS fix deliberately reflows the actual map status after
PNG capture. This protects against the discovered stale-coordinate regression.
Cancel uses the same current-view tap. No direct performClick, click retries,
relaxed pixel/layout expectation or production implementation change is used.

The native observation change is documented in the UI accessibility guide and
official fork differences. All unrelated canonical fields and relation order
are preserved; the new dated profile gets its own record. Library runtime and
library documentation contracts are unchanged because no library implementation
or public behavior is modified. Owned ordinary text is normalized to repository
LF policy; the Windows PowerShell entrance keeps its prescribed CRLF checkout.
