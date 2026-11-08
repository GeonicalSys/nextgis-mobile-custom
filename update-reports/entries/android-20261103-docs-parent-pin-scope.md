---
id: android-20261103-docs-parent-pin-scope
area: Мобильное приложение
kind: internal
at: 2026-11-03T12:00:00+04:00
title: Локальная проверка документации различает работу библиотеки и parent pin
why: Незамерженная работа в отдельном владельце не требует ложного описания изменений приложения, а настоящий gitlink сохраняет обязательный review.
covers:
  - tools/docs-check.ps1
  - docs/scripts/check_change_impact.py
  - docs/tests/test_change_impact_ci.py
---

Найдено при MOB-DEV-02: Related source-only docs-check ошибочно включал известный
незакреплённый EasyPicker child в scope приложения. Один общий helper собирает
полный local net diff без сети. Staged/committed parent pins всё ещё запускают
strict official-differences review; внутренние child edits/HEAD сами pin не меняют.
Семь адресных Git/CLI/PowerShell регрессий защищают изоляцию, полноту путей,
отказ базы и передачу отдельных Unicode-аргументов реальному launcher.

Проверки и неуспешный исходный прогон сохранены отдельно. Product code/data,
APK/version, исходные инструкции, parent gitlinks и branch protection прежние.
Синтетические технические Git-истории не являются business/native приёмкой.
Merge, APK публикация, устройства, AVD и GitHub Actions не запускались.
