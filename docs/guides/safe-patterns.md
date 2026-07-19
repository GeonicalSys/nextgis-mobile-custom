---
title: Безопасные паттерны разработки
type: guide
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/MaplibreMapInteraction.java
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
---

# Безопасные паттерны разработки

## Cross-module callback

Добавлять абстракцию в нижний слой (`maplib`), реализацию — в верхний (`app`),
а orchestration — в `maplibui`. Не вводить обратный import `maplib -> app`.

## Layer mutation

Сначала изменить модель `LayerGroup`, затем синхронизировать MapLibre style
подходящим full/lite flow. Не считать UI list единственным источником порядка.

## Разрушительные операции

```text
оценить несохранённые данные
  → попытаться sync/send, если допустимо
  → создать и проверить backup
  → mutation
  → reload + пользовательское уведомление
```

При ошибке backup остановиться до mutation.

## Preferences

Один key и один default должны совпадать в constants, preference XML и runtime
fallback. Изменение default проверяется на clean install и existing profile.

## Upstream overlap

Сначала понять намерение обеих сторон. `ours` выбирается только со ссылкой на
актуальный invariant; `hybrid` должен иметь smoke для обеих сохранённых сторон.

## Документация

Не копировать точные списки consumers/IDs в Markdown. Markdown объясняет
причину, registry хранит структуру, module manifest обеспечивает навигацию.
