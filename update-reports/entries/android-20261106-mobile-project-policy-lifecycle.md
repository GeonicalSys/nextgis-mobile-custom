---
id: android-20261106-mobile-project-policy-lifecycle
area: Документация
kind: internal
at: '2026-11-06T02:06:53+04:00'
title: Сверены задачи мобильных проектов и решения App
why: Сохранить границы существующей реализации, исторических распоряжений и будущей приёмки
covers:
  - agent-system/policy-migration.json
  - agent-system/knowledge/documentation/collector-roadmap-composition-apply-and-idempotency.json
  - agent-system/knowledge/documentation/collector-roadmap-config-hash-and-change-classification.json
  - agent-system/knowledge/documentation/collector-roadmap-end-to-end-form-replacement-and-removal.json
  - agent-system/knowledge/documentation/collector-roadmap-fault-injection-rollout.json
  - agent-system/knowledge/documentation/collector-roadmap-local-vector-tile-production-acceptance.json
  - agent-system/knowledge/documentation/collector-roadmap-multi-account-device-soak.json
  - agent-system/knowledge/documentation/collector-roadmap-project-search-diagnostics-and-archive.json
  - agent-system/knowledge/documentation/collector-roadmap-real-attachment-backup-recovery.json
  - agent-system/knowledge/documentation/collector-roadmap-underlay-server-specification.json
  - agent-system/knowledge/documentation/collector-roadmap-workspace-deletion-policy-and-recovery.json
  - agent-system/knowledge/policy/workspace.incident-retry-order.json
  - agent-system/knowledge/policy/workspace.incident-software-order.json
---

MOB-DEV-02. Для десяти задач мобильных проектов сверены существующий код,
поставка и оставшиеся проверки. Удаление локального workspace, резервная
копия, архив и ручное восстановление учтены раздельно. Приёмка тяжёлых
слоёв относится к принятому прямому SQLite-маршруту.

Два распоряжения об эмуляторе связаны с историческими результатами
своих запусков. Карта воспроизведения закрепляет обе проверенные записи
и сохраняет исходные фрагменты. Три действующих решения сверены с формулировками
и сохранены по существующим идентификаторам.
