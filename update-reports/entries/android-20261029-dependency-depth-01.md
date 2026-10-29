---
id: android-20261029-dependency-depth-01
area: Документация и сопровождение
kind: internal
at: 2026-10-29T18:32:24+01:00
title: Владение фоновой синхронизацией и независимые границы её подтверждения
why: Продолжение глубокого аудита экосистемы по поручению владельца; обобщённые sync-контракты скрывали разные ключи, транспортные границы и порядок durable ACK
covers:
  - app/AGENTS.md
  - docs/architecture/ngw-sync-and-storage.md
  - docs/changelog.md
  - docs/guides/change-checklist.md
  - docs/registry/dependencies.yaml
  - docs/registry/invariants.yaml
  - docs/registry/ecosystem.yaml
  - docs/registry/change-impact.yaml
  - docs/registry/smoke-tests.yaml
  - docs/roadmap/mobile.md
---

MOB-02, продолжение после MOB-UI-01 (Android85/LISA275). Подробный source-разбор
в текущем Android root и закреплённых maplib/maplibui прослеживает trusted
project inventory, сохранение всего плана, session scope, main-thread callbacks,
provider URI, уведомления, service tickets и leases до drain и закрытия БД.
Углублены существующие канонические контракты; новый индекс/scanner не создан.

Разделены remote Collector UID, recovery target UID/path/mapName, canonical-path
full clock, path-partitioned import journal и live token/ticket. Untagged provider
URI остаётся UI-scoped даже на background-scoped thread; expired token не имеет
fallback. Lease workspaceKey не подменяет owning map. Per-account pair ACK
происходит до финального cleanup/full clock, поэтому пустая очередь не доказывает
полный проход. Только unclaimed delivery имеет60s expiry; claimed child держит
owner до фактического окончания. Проверка onCreate отвергла ложный вывод о
первом foreground-start по одному раннему return в onStartCommand.

Исправлена подтверждённая source/doc несогласованность: старое ожидание
SMOKE-MULTI-ACCOUNT-SYNC запрещало pull неактивного проекта, хотя нынешний
sync_all_projects по умолчанию true. Новые independent oracles различают true,
false, layer-only и сохранённый recovery target. Product behavior не меняется.

Потеря процесса/ошибка cleanup между pair ACK и clock, liveness claimed child
и миграция workspace keys остаются NOT_RUN/UNKNOWN. Они ссылаются на единый
LISA registry/dependency-audit-todo.yaml через AUDIT-TODO-SYNC-ACK-RECOVERY,
AUDIT-TODO-SYNC-CLAIMED-LIVENESS и AUDIT-TODO-WORKSPACE-KEY-MIGRATION, не на
параллельную очередь. Source-тесты прочитаны как witnesses, не исполнены.

Product bytes, Gradle/версии, library gitlinks, настройки, схемы, feature/outbox,
NGW/Collector и deployed services не меняются; миграции нет. Native/AVD/device,
DB/Office, сборки, публикации и приостановленные этапы не запускались. Проверки
этого этапа ограничены стандартными docs/report validators; результаты и
source_context фиксируются в PR. Документационный PASS не закрывает native TODO.
