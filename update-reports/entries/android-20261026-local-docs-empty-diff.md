---
id: android-20261026-local-docs-empty-diff
area: Локальная автоматическая приёмка
kind: internal
at: 2026-10-26T21:20:00Z
title: Локальная проверка документации принимает чистую target ветку после merge
why: Первый Full после merge остановился из-за --enforce-diff без изменённых путей
covers:
  - tools/docs-check.ps1
  - tools/tests/test_mobile_docs_scope.py
---

MOB-01/REL-03. Strict diff flag передаётся только вместе с реальными путями;
полный validator и report/docs regressions выполняются и на чистой ветке.
Новая техническая regression запускает настоящий PowerShell wrapper в owned
temporary Git repo с interface-only Python doubles: clean target и новый путь
с пробелами. Это проверка helper, не native/business acceptance. Full10 сохранён
как FAIL до native, cleanup PASS; повторный Full обязателен, старый gate не возвращается.
