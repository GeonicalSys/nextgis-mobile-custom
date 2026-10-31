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
  - docs/architecture/ui-contracts.md
  - docs/changelog.md
  - docs/guides/change-checklist.md
  - docs/registry/dependencies.yaml
  - docs/registry/invariants.yaml
  - docs/registry/ecosystem.yaml
  - docs/registry/change-impact.yaml
  - docs/registry/smoke-tests.yaml
  - docs/registry/ui-contracts.json
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

Дополнительный bounded срез той же области проследил последний durable pair ACK
до финального cleanup/clock: cleanup catch не возвращает пары, а startup recovery
читает только retained pairs. Обычный manual повтор строит новый план; scheduled
worker подтверждает request dispatch и next tick, не выполнение. Полный clock
требует всех required accounts одного запуска. SYNC_FINISH и ring lifecycle сами
по себе не удостоверяют full completion.

По первичным AOSP14/16 источникам failed SharedPreferences commit не откатывает
in-memory map; поэтому markComplete=false может сочетаться с новым cached getter
и старым диском. Interrupted await отдельно может закончиться поздней записью.
Описаны независимые return/memory/physical XML+backup/fresh-process oracles;
исходники библиотеки не менялись. Прочитаны21 методов5 focused test files,
исполнено0. В них нет точной post-last-ACK cleanup/full-clock fault injection;
false-before-delegate proxy не доказывает реальное failed-write поведение.
Новый source gap и runtime ветви ведутся в прежнем AUDIT-TODO-SYNC-ACK-RECOVERY.

UI-каталог сохраняет прежние36 IDs и80 method fingerprints;26 новых reviewed
methods и bindings углубляют три существующих sync контракта и добавляют
UI-SETTINGS-MAP-STORAGE-MOVE (итого37). Кольцо — weighted lifecycle progress,
badge — raw pending query, clock — cached значение с текущим failed-write gap;
они не объединяются в доказательство полного durable обмена. Формат часов
прослежен до dd MMM/HH:mm с default locale/timezone. `map_path` подтверждён как
Preference key, не widget ID; перенос относится к current application map,
не ко всем проектам по подписи.8 новых non-method наблюдений сливаются с тремя
существующими XML/string rows; новые progress constants отдельно защищены.
Library fix, gitlink update, APK/resource merge, move/state/native приёмка не
выполнены. Runtime неизвестности остаются в прежних общих TODO.
