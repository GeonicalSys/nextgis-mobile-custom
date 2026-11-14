---
id: android-20261113-geo02-background-control
area: GPS
kind: internal
at: '2026-11-13T10:32:38.504551+00:00'
title: 'GEO-02: временное отключение обычного фонового GPS и безопасное закрытие пользовательского
  сеанса'
why: Согласованные завершённые изменения рабочей сессии
covers:
- app/src/androidTest/java/com/nextgis/mobile/reliability/BackgroundGpsNativeTest.java
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/nextgis/mobile/MainApplication.java
- app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
- app/src/main/java/com/nextgis/mobile/location/BackgroundLocationActionReceiver.kt
- app/src/main/java/com/nextgis/mobile/location/BackgroundLocationController.kt
- app/src/main/java/com/nextgis/mobile/location/BackgroundLocationPolicy.java
- app/src/main/java/com/nextgis/mobile/location/BackgroundLocationService.kt
- app/src/main/java/com/nextgis/mobile/stakeout/SharedStakeoutSession.kt
- app/src/main/res/values-ru/background_location.xml
- app/src/main/res/values/background_location.xml
- app/src/test/java/com/nextgis/mobile/location/BackgroundLocationPolicyTest.java
- update-reports/entries/android-20261113-geo02-background-control.md
---

GEO-02: временное отключение обычного фонового GPS и безопасное закрытие пользовательского сеанса

Решения и зависимости:

- GEO-02 implemented in App only: user-session nonce and temporary mute, unique immutable notification Broadcast PendingIntent, explicit/task-root closure, stale pending FGS promotion without acquisition. Main menu/working services unchanged in this task.
- Application-owned SharedStakeoutSession retains only the existing immutable geometry controller with weak UI handles; stale UI detach cannot stop new UI, explicit Stop releases resources. No new layer/FID/workspace token or automatic row-follow policy.
- Emulator: actual notification mute, foreground/rotation/navigation preservation, new session ignores old actions/task callback, canceled pending FGS acquires no owner, actual Track/Walk/Stakeout survive mute and swipe; guidance Stop reachable after reopening.
- At final stable solution update GEO-02 roadmap lifecycle, GPS/stakeout/settings actual contracts and UI catalogue. Preserve AUDIT-TODO-NGW-REPLACE-FID and no cold audible guidance restart.
- GEO unit/compile PASS: 10 BackgroundLocationPolicyTest cases, 0 failures/errors/skips; LisaDebug production source and Android test source compile PASS in geo02-compile-unit-03.log. Native emulator execution still pending combined final source. Physical device/Full/publication deferred by current user.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261113-geo02-background-control. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
