---
id: android-20261026-merged-style-track-dependencies
area: Подготовка выпуска мобильного приложения
kind: internal
at: 2026-10-26T19:34:00Z
title: Приложение закрепляет слитые зависимости стилей и треков
why: Перед публикацией consumer должен ссылаться на удалённые merge commits библиотек
covers:
  - maplib
  - maplibui
  - docs/reference/mobile-release-fixes-2026-10-26.md
---

MOB-01/MVT-01/REL-03/UX-03. Maplib PR60 слит Merge Commit61a0a602;
maplibui PR44 — Merge Commitc3253726. После fetch подтверждены remote master,
ancestry всех source commits и равенство Git trees исходных heads и merges.
App pins направлены на эти commits. Easypicker и publisher не меняются.
App PR78, текущий CI и окончательные Full смартфона/планшета ещё не закрыты;
публикации нет. Матрица14 requirement-owner rows включает всех предшественников.
