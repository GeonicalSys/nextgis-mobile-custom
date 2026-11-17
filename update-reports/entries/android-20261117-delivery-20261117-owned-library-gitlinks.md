---
id: android-20261117-delivery-20261117-owned-library-gitlinks
area: Версии мобильных библиотек
kind: internal
at: '2026-11-17T05:15:25.068627+00:00'
title: Связать App с собственными изменяемыми библиотеками общей сессии перед их коммитами
why: Согласованные завершённые изменения рабочей сессии
covers:
- maplib
- maplibui
- update-reports/entries/android-20261117-delivery-20261117-owned-library-gitlinks.md
---

Связать App с собственными изменяемыми библиотеками общей сессии перед их коммитами

Решения и зависимости:

- Current user explicitly authorized commit/push of all mobile libraries. Both child checkouts are owned session branches by geoglyth at their original expected heads; only their already-attributed working sources are dirty. Adopt the parent Git dirty indicators at unchanged base pins; actual new pins will be separately attributed by the standard checkpoint after child commits. No foreign branch or source is adopted.
- Attribute only dirty indicators of the two owned App Gitlinks at their unchanged original heads; no file/checkout edit. Current user authorizes commit/push of these owned libraries. Standard checkpoint remains responsible for child commits and exact actual pin tasks.

Полный журнал, входные хэши и результаты проверок: сессия karniilp-01a1e5c4, задача delivery-20261117-owned-library-gitlinks. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
