---
title: Модель форка и взаимодействие с upstream
type: architecture
last_verified: 2026-07-19
related_code:
  - .gitmodules
  - tools/upstream-sync.ps1
  - docs/reference/fork-customizations.md
---

# Модель форка и взаимодействие с upstream

Root и три сабмодуля имеют write remote `origin` в GeonicalSystem и read-only
remote `upstream` в NextGIS. Рабочая линия форка — `my-maplibre`; `.gitmodules`
описывает clone URLs, но не заменяет фактическую проверку branch/remotes.

## Классы изменений

- `upstream` — принимается без изменения поведения форка;
- `ours` — upstream-фрагмент отклоняется ради зафиксированного инварианта;
- `hybrid` — API/исправление upstream объединяется с поведением форка.

Решение должно ссылаться на invariant ID или явно создавать новый. История
одного merge cycle не превращается автоматически в вечный контракт.

## Атомарность

Изменение сабмодуля состоит из двух уровней:

1. commit в repository сабмодуля, включая его локальный docs pack;
2. root commit с новым pointer, central docs и совместимыми app-изменениями.

Root не должен указывать на непроверенную или недоступную ревизию сабмодуля.

Операционная процедура: [`../runbooks/upstream-sync.md`](../runbooks/upstream-sync.md).
