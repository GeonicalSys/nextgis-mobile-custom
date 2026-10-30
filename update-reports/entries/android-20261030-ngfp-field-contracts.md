---
id: android-20261030-ngfp-field-contracts
area: Документация и сопровождение
kind: internal
at: 2026-10-30T01:53:34.320667+00:00
title: Контракты полей и лимиты строк форм NGFP
why: Категориальные defaults, серверная обязательность и лимиты строк нужно различать от NGW resource IDs и состояния draft
covers:
  - docs/registry/ui-contracts.json
  - docs/architecture/ui-contracts.md
  - docs/architecture/conditional-form-rules.md
  - docs/architecture/cascading-form-lists.md
  - docs/registry/invariants.yaml
  - app/AGENTS.md
  - app/docs/README.md
  - app/docs/manifest.yaml
  - docs/roadmap/mobile.md
  - docs/changelog.md
  - docs/reference/official-differences.md
---

Продолжение существующего MOB-02: сохранены11 связей SQL/NGFP/catalog/publisher/
Collector/Android/Save и действующий default Прочие / прочие с приоритетом
explicit/draft/cursor перед template. Не установлена неисправность defaults.
Документированы nullable SQL, authoritative remote required, layer visibility и
local control state как разные контракты. Добавлен39-й UI контракт только для
TextEdit line cap с реальным dynamic field binding,6 новыми вручную проверенными
методами и3 non-method tripwires; прежние38/128/14/history и F61 clock row сохранены.

Интегрированы только docs/UI metadata текущего source audit, без product/library/pin изменений.
После интеграции Root выполнил tools/docs-check.ps1 -RunTests: report guard
124 cards/8 source paths PASS,26 policy tests PASS18.782s; каталог39 contracts/
134 method/17 non-method tripwires/11 groups принят. Полный validator NOT_PASS:
ровно прежний WF-SYNC-CLOCK-STORE drift live CEA против сохранённого F61 pin.
Отдельный запуск всех57 docs tests44.739s дал53 PASS/4 FAIL — каждый отказ
от того же clock guard; ожидания не ослаблялись и pin не обновлялся.
Независимый review12 подтвердил сохранность старого каталога и новых source
bindings; уточнено только имя callback родительского Spinner, заполняющего child.
Related/Full, устройство, SQLite/NGW/native, APK/выпуск NOT_RUN.
Четыре новые bounded проблемы remap/context/provenance принадлежат единому root
TODO, без app-only параллельной очереди. Все source paths в covers этой карточки
являются documentation inputs; исходники maplibui не изменяются.
