---
id: android-20261122-change-impact-directory-patterns
area: Правила проверки изменений Android
kind: internal
at: '2026-11-22T09:52:00+04:00'
title: Учитывать изменения файлов внутри каталогов Android
why: Правила с путями каталогов пропускали изменения вложенных исходников и тестов
covers:
  - agent-system/knowledge/change-impact/change-impact-geometry-sketch-workflow.json
  - agent-system/knowledge/change-impact/change-impact-gps-recording.json
  - agent-system/knowledge/change-impact/change-impact-layer-chooser-and-mobile-acceptance.json
  - agent-system/knowledge/change-impact/change-impact-mobile-usage.json
  - agent-system/knowledge/change-impact/change-impact-native-geojson-payload.json
  - agent-system/knowledge/change-impact/change-impact-stakeout-guidance.json
  - agent-system/knowledge/change-impact/change-impact-ui-large-text.json
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-22-android-path-triggers.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/provenance/structured-views.json
  - docs/changelog.md
  - docs/reference/build-matrix.md
  - docs/registry/change-impact.yaml
  - docs/scripts/validate_docs.py
  - docs/tests/test_change_impact_ci.py
---

Семь правил применяют рекурсивные шаблоны к девятнадцати путям. Валидатор сопоставляет пути от корня репозитория. Имена проверок, владельцы и обязательные документы сохранены. Применимость проверена по отслеживаемым путям Android, maplib, maplibui и EasyPicker на текущих коммитах.
