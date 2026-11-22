---
id: android-20261122-docs-git-fixture-environment
area: Проверки документации Android
kind: internal
at: '2026-11-22T08:41:07+04:00'
title: Изолировать окружение GitHub в тестах документации Android
why: Глобальная подмена окружения стирала пустое значение Git в Windows и ломала следующие двадцать фикстур
covers:
  - docs/tests/test_change_impact_ci.py
  - agent-system/knowledge/documentation/build-matrix.json
  - docs/reference/build-matrix.md
---

Тесты подставляют переменные GitHub в проверяемый модуль. Дочерние Git-процессы получают исходное окружение, включая допустимые пустые значения. Порядок общего запуска `docs-check.ps1 -RunTests` сохранён.
