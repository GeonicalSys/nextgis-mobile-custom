---
id: android-20261119-merge-validation-notes
area: Мобильное приложение
kind: internal
at: '2026-11-19T22:24:44.290443+04:00'
title: Уточнены проверка обновления карты и результаты адресных прогонов
why: Сопроводить исправления мобильной карты описанием проверки и её границ
covers:
  - agent-system/knowledge/documentation/automated-tests-perf01-technical-baseline.json
  - agent-system/knowledge/documentation/official-differences-sqlite-render-attribute-reads.json
  - docs/reference/official-differences.md
  - docs/runbooks/automated-tests.md
  - update-reports/entries/android-20261119-merge-validation-notes.md
---

В автотестах описано, как проверяется обновление видимой карты после записи в
SQLite и как отличить данные проектов с совпадающими идентификаторами.
В справке форка описана передача изменений от SQLite к карте. Дорожная карта
сохраняет результаты адресных прогонов, включая незавершённые серии синхронизации
и отрисовки.
