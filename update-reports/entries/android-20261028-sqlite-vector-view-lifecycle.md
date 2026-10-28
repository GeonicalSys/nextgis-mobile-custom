---
id: android-20261028-sqlite-vector-view-lifecycle
area: Жизненный цикл карты
kind: internal
at: 2026-10-28T05:30:00+01:00
title: Отмена SQLite заданий карты до уничтожения её экрана
why: Новые источники читают базу асинхронно и не должны доставлять поздние ответы в уничтоженный native MapView
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
---

MOB-04, app82 после maplib61 и consuming maplibui project-retirement.
`MapFragment.onDestroyView` вызывает `closeSqliteVectorSourcesBeforeDetach`
у того же сохранённого renderMemoryOwner до освобождения ссылки и MapView.
Это terminal cancellation поколения source, а не удаление SQLite/user данных.
Activity recreation подключает новое поколение; background sync, drafts и
Walk service сохраняют собственный жизненный цикл.

Передача поздних tiles/ошибок проверяется bridge JVM сценариями и isolated
NativeSqliteVectorRenderingTest. Последние не заменяют полную Walk/Save/Full
приёмку и длительные787 memory/render benchmarks. Текущий код ещё не выпущен.
