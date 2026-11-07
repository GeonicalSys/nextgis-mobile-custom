---
id: android-20261106-pr-template-knowledge
area: Документация
kind: internal
at: '2026-11-06T03:33:36+04:00'
title: Шаблон PR App перенесён в запись владельца
why: Редактировать шаблон через систему знаний и сохранять обычный маршрут GitHub
covers:
  - agent-system/README.md
  - agent-system/knowledge/documentation/pull-request-template.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/scripts/prose_knowledge.py
  - docs/tests/test_prose_knowledge.py
---

Четыре раздела и 14 полей шаблона хранятся в одной записи. Штатный
генератор воспроизводит исходный объект Git по действующему адресу
`.github/PULL_REQUEST_TEMPLATE.md`, включая ссылку на дорожную карту.
История содержит точную редакцию и полный диапазон её байтов. Проверка
документации выявляет расхождение между записью и файлом.
