---
id: android-20261104-large-docs-impact-input
area: Мобильное приложение
kind: internal
at: 2026-11-04T02:02:00+04:00
title: Проверка документации принимает большие списки изменённых путей
why: Валидатор запускается при переносе сотен записей и учитывает полный диапазон изменений
covers:
  - docs/reference/build-matrix.md
  - docs/scripts/check_change_impact.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_change_impact_ci.py
  - tools/docs-check.ps1
---

MOB-DEV-02. Перенос 224 определений воспроизвёл ошибку запуска PowerShell
при превышении ограничения Windows на длину командной строки. Штатный
PowerShell и проверка PR теперь передают полный список JSON через стандартный
ввод. Регрессии сверяют пути длиннее прежнего ограничения и путь с кириллицей
и пробелом. Строгая проверка применяется один раз ко всему списку.
