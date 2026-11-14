---
id: android-20261114-diagnostics-artifacts-publisher
area: Надёжность выпуска
kind: user
at: '2026-11-14T01:32:56.190136+00:00'
title: Доставка диагностических архивов и независимый контроль APK издателем
why: Согласованные завершённые изменения рабочей сессии
covers:
- app/src/androidTest/java/com/nextgis/mobile/util/DiagnosticArtifactsNativeTest.java
- app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
- app/src/main/java/com/nextgis/mobile/util/DiagnosticArtifacts.java
- app/src/main/java/com/nextgis/mobile/util/DiagnosticsRetryWorker.java
- app/src/main/java/com/nextgis/mobile/util/DiagnosticsTransport.java
- tools/mobile-test-plan.json
- update-reports/entries/android-20261114-diagnostics-artifacts-publisher.md
---

Доставка диагностических архивов и независимый контроль APK издателем

Решения и зависимости:

- Implemented immutable already-approved SDK envelope ZIPs with strict event UUID/SHA ACK, no redirects, offline durable queue, separate bounded SQLite ACK receipts preventing a changed ZIP on event replay. Main App initializes the production config; transport persists archives before HTTP; actual WorkManager drains independently of Sentry acceptance. No new project/log/photo scanning or server changes.
- Native-09 2 artifact cases PASS with actual SDK disk cache, transport and WorkManager offline/online, repeat after new instance and false ACK/302/503/409. Native-08 passed 27 cases: background GPS 18, recovery GPS 2, changes centre 3, usage 3, negative archive 1; its 1 failure was fixture missing SDK cache directory (SDK CacheStrategy disassembly verified), fixed and native-09 repeated both archive cases. Both emulators/settings/ANGLE cleaned. Publisher 40 tests PASS. Actual verification APK correctly rejected as production; DEX decoder shape checked against 9 actual executing classes plus 2 explicitly separate synthetic flag/literal fixtures, not production acceptance.
- Positive independently pinned signed Lisa/Belka admission and new production release version remain NOT_RUN pending the approved private diagnostics.properties input requested from owner. No production events, physical phone, Full, publication or merge. App library recovery acceptance also needs explicit completion evidence before an archived failed form hold can be safely released; separate next source stage.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261114-diagnostics-artifacts-publisher. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
