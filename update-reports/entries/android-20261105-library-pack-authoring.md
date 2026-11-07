---
id: android-20261105-library-pack-authoring
area: Документация
kind: internal
at: 2026-11-05T06:05:00+04:00
title: Штатное редактирование пакетов maplib и maplibui
why: Проверять представления библиотек по их собственным записям и текущему генератору App
covers:
  - agent-system/provenance/structured-views.json
  - docs/scripts/module_manifest_knowledge.py
  - docs/scripts/prose_knowledge.py
  - docs/scripts/structured_view_proof.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_prose_knowledge.py
  - update-reports/entries/android-20261105-library-pack-authoring.md
---

MOB-DEV-02. Штатные команды App поддерживают оба пакета. Валидатор проверяет
исходные версии библиотек из закреплённых ревизий и требует актуальное
доказательство для пакета с сгенерированным представлением.
