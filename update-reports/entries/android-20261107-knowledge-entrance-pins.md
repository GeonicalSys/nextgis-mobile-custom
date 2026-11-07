---
id: android-20261107-knowledge-entrance-pins
area: Документация
kind: internal
at: '2026-11-07T03:45:27+04:00'
title: Рабочий вход App и версии библиотек
why: Связать рабочий вход App с проверенными записями библиотек
covers:
  - .cursor/rules/lisa-knowledge-context.mdc
  - AGENTS.md
  - agent-system/README.md
  - agent-system/knowledge/documentation/app-knowledge-home.json
  - agent-system/knowledge/documentation/app-knowledge-module-and-delivery.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-07-app-knowledge-cutover.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/mobile-roadmap-agent-knowledge.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - docs/changelog.md
  - docs/roadmap/mobile.md
  - easypicker
  - maplib
  - maplibui
---

MOB-DEV-02. Вход App направляет Codex и Cursor к общему читателю. Проверка из чистой копии опирается на три полученных с GitHub Merge Commit.

- easypicker — 66a3607ce20b8b9abe37e9c7964271e0eca1ee71
- maplib — cd2836dd47a002dea003420dce5ae9aa3ad23ba0
- maplibui — f2a89a63aad8051d36838ac6e254192ce26a75b5

Изменение охватывает правила входа, редакторские записи и указатели на библиотеки. Деревья библиотек сверены с проверенными Draft. Миграция пользовательских проектов и настроек не требуется.

Независимый набор проверяет полные исходные инструкции, поля владельцев и отклонение обрезанной выдачи. Условия, оставшиеся неразрешёнными в анализе зависимостей, сохраняют статус UNKNOWN. Хэш входов — 675421642e06729e956f6683e8fd47958fe0cf651fc1f578758d25e490b3b734.

После переключения проверены 64 обязательных идентификатора, 272 поля и 1 095 страниц. Codex загрузил полный вход App один раз. Независимая сверка подтвердила актуальность. Обрезанная выдача отклонена. Штатный план прошёл по 29 путям. Хэш входов — 3ec76184cc6eb284d517ef75a7d088ecd88aa2caeead3b97f6f7f5da28897681.
