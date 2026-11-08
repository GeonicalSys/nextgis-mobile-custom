---
id: android-20261106-app-readme-knowledge
area: Документация
kind: internal
at: '2026-11-06T04:20:08+04:00'
title: Корневой README приложения перенесён в запись владельца
why: Разделить вход ЛИСА и БЕЛКА и унаследованную документацию NextGIS
covers:
  - README.md
  - agent-system/README.md
  - agent-system/knowledge/documentation/repository-readme.json
  - agent-system/knowledge/documentation-evidence/repository-readme-original.json
  - agent-system/provenance/documentation.json
  - agent-system/source-dispositions.json
  - docs/scripts/prose_knowledge.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_prose_knowledge.py
---

В рамках MOB-DEV-02 корневой README формируется из записи App и сохраняет
адреса разделов. Установка и документация ЛИСА и БЕЛКА ссылаются на материалы
форка. Google Play обозначен как канал официального NextGIS Mobile. Ссылка
на лицензию ведёт к файлу LICENSE этого репозитория.

Прежние 5 126 байт сверены на ветке my-maplibre и в черновике. Отдельная
запись доказательств закрепляет исходный объект и решение для каждого
раздела. Локальный валидатор проверяет проекцию и относительные ссылки README.
