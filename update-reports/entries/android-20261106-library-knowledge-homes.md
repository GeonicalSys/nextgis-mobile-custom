---
id: android-20261106-library-knowledge-homes
area: Документация
kind: internal
at: '2026-11-06T13:44:51+04:00'
title: App проверяет объявленные страницы знаний библиотек
why: Использовать обычное сопровождение пакетов EasyPicker, maplib и maplibui для их страниц знаний
covers:
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-06-library-entrances.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - docs/changelog.md
  - docs/reference/build-matrix.md
  - docs/scripts/prose_knowledge.py
  - docs/tests/test_prose_knowledge.py
---

MOB-DEV-02. Генератор поддерживает необязательный `agent-system/README.md` в
пакетах трёх библиотек. Валидатор проверяет объявленное представление и его
ссылки. Прежние адреса остаются обязательными, а App продолжает использовать
те же закреплённые версии библиотек.
