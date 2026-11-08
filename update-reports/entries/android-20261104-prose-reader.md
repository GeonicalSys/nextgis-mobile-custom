---
id: android-20261104-prose-reader
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:56:41+04:00
title: Валидатор App читает точку входа и чеклист из записей владельца
why: Сохранить обычные Markdown-адреса и проверять представления вместе с записями
covers:
  - agent-system/README.md
  - agent-system/knowledge/change-impact/change-impact-documentation-ci.json
  - agent-system/source-dispositions.json
  - docs/changelog.md
  - docs/reference/build-matrix.md
  - docs/registry/change-impact.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/prose_knowledge.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_prose_knowledge.py
---

MOB-DEV-02. Штатный валидатор проверяет frontmatter и относительные ссылки из
записей и обнаруживает устаревший Markdown. Автономная поставка читает
Markdown по прежним адресам.
