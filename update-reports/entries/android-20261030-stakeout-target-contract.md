---
id: android-20261030-stakeout-target-contract
area: Контракты интерфейса
kind: internal
at: '2026-10-30T03:19:38.383994Z'
title: Источник геометрии цели и сохранённый выбор выноса
why: Отделить скопированную цель от текущей строки/FID, камеры и позиции устройства
covers:
- docs/architecture/ui-contracts.md
- docs/registry/ui-contracts.json
- docs/architecture/stakeout.md
- docs/registry/invariants.yaml
- docs/registry/change-impact.yaml
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/roadmap/mobile.md
- docs/changelog.md
- docs/reference/official-differences.md
---

Source-only аудит existing selected-target реализации: обычный ID selection
читает полную геометрию, Feature/Bundle/draft может передать saved snapshot.
Конструктор делает private copy/index3857; nearest facet зависит от device fix,
затем4326 inverse даёт метры/true bearing с явным sphere fallback при отсутствии
сходимости. App владеет XML/controller/restore, maplibui selection/view classes,
maplib math/index. isLocked — presentation, не DATA_SYNC lease.

Один existing UI target record уточнён, добавлены8 method/2 file guards:
39/142/19. Все старые134/17 объекты/fingerprints, остальные38 contracts,
widget evidence и historical/source_context/F61 clock row сохраняются.
Шесть geometry test bodies лишь прочитаны: здесь нет новых JVM/Gradle/geometry
результатов. Совпадение с concurrently passed lookup9 не доказывает guidance.
После интеграции официальный UI checker принял39/142/19/11 groups.
Полный App validator NOT_PASS: ровно прежний WF-SYNC-CLOCK-STORE drift;
Docs57 дали53 PASS/4 FAIL46.177с, все четыре — тот же clock guard.
Report126 cards/9 source paths и policy26 PASS18.495с. Source index CURRENT;
его полнота остаётся UNKNOWN. Старый F61 pin и expectations не менялись.
Новых guidance/native/device/geometry-JVM результатов здесь нет.

Текущая polygon boundary policy, global accuracy/domain, stale row/FID/workspace
freeze/follow/invalidate и native/device/process-return остаются unverified в
existing AUDIT-TODO-NGW-REPLACE-FID. Поведение/defaults/resources/source code не
изменены; нового runtime/upstream delta или устранённого default bug нет.
