---
id: android-20261113-rel04-edit-recovery
area: Редактирование и восстановление
kind: internal
at: '2026-11-13T11:03:13.179486+00:00'
title: 'REL-04: owner-scoped Discard and working layer pencil'
why: Согласованные завершённые изменения рабочей сессии
covers:
- app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
- app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
- app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
- app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
- app/src/main/res/values-ru/edit_session.xml
- app/src/main/res/values/edit_session.xml
- tools/mobile-test-plan.json
- update-reports/entries/android-20261113-rel04-edit-recovery.md
---

REL-04: owner-scoped Discard and working layer pencil

Решения и зависимости:

- Current user explicitly authorizes implementation and emulator checks, superseding planning-only scope. Preserve active tracking/walk/stakeout, map/layer/feature and point/session identity. No journal schema migration or global startup unlock. Complete context delivered; relevant recovery ownership source and invariants read, unrelated UNKNOWN obligations retained.
- Recovery Discard captures operation/map/layer/feature identity; terminal editor cleanup releases only matching layer after excluding durable form/geometry/walk owners. Pencil resolves clicked ListView row and routes to its current form/editor/walk, or releases proven unowned row. No editing-rights or schema migration.
- Compiled app and native sources; emulator Related includes real form interruption/recreate/Discard/new Save, old callbacks, existing geometry preservation and proven unowned pencil. Final emulator results pending. Active recording/walk/stakeout remain independent owners.

Полный журнал, входные хэши и результаты проверок: сессия mobile-geo-ux-rel-01a1d22d, задача android-20261113-rel04-edit-recovery. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
