---
id: android-20261122-pin-reviewed-easypicker-registry
area: Модуль EasyPicker
kind: internal
at: '2026-11-22T15:59:46+04:00'
title: Закрепить реестр EasyPicker после проверки и слияния изменений библиотеки
why: При получении Android вместе с подмодулями нужен тот же реестр EasyPicker, который просмотрен в составе аудита
covers:
  - agent-system/knowledge/documentation/official-differences.json
  - easypicker
  - docs/reference/official-differences.md
---

Приложение закрепляет коммит EasyPicker из PR №8. Сверены прежняя и новая ссылки Git, состав изменений библиотеки и контрольная сумма дерева. Проверка выполнялась по документации, реестрам и исходникам обеих версий.
