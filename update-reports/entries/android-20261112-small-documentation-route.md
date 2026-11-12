---
id: android-20261112-small-documentation-route
area: Инструкции и документация мобильного приложения
kind: internal
at: '2026-11-12T09:39:39+04:00'
title: Упрощённый маршрут для малых правок документации
why: Полная сборка контекста и графа зависимостей несоразмерна правкам текста и планов
covers:
  - agent-system/authored-policy.json
  - agent-system/knowledge/policy/workspace.local-checks-and-cloud-boundary.json
  - agent-system/knowledge/documentation/app-knowledge-home.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-12-small-documentation-route.json
  - AGENTS.md
  - agent-system/README.md
  - docs/changelog.md
  - update-reports/entries/android-20261112-small-documentation-route.md
---

По прямому поручению владельца от 12.11.2026 добавлен узкий маршрут: читать
затронутые канонические записи, layout и ссылки, собирать документы и запускать
их валидатор без SQLite, глобального discovery и полного набора тестов.

При изменении работающих контрактов, кода инструментов или полномочий действует
обычный маршрут; изменение обязательных правил требует отдельного поручения
владельца на конкретную правку процесса. Защита чужих изменений, report guard,
установленные Git hooks и правила ветки сохранены. Коммит/push/PR зависят
от поручения на текущую задачу; отложенные функции остаются отложенными.

Эта правка меняет только инструкции и собранные документы. Код генераторов,
валидаторов, hooks, приложения и серверные настройки не изменены.
