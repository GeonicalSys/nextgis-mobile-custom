---
title: Архитектура Android-форка
type: architecture
last_verified: 2026-07-19
related_code:
  - settings.gradle
  - app/build.gradle
  - maplibui/build.gradle
  - maplib/build.gradle
---

# Архитектура Android-форка

## Репозитории

| Repository | Содержимое | Граница изменений |
|---|---|---|
| `android_gisapp` | `app`, `wizardpager`, root build, release и central docs | Пользовательский продукт и координация сабмодулей |
| `maplibui` | UI, services, Collector registry, layer fill | Android/UI orchestration поверх GIS model |
| `maplib` | GIS model, storage, NGW, MapLibre rendering | Библиотечные контракты и данные |
| `easypicker` | Picker widget | Вспомогательная upstream-библиотека |

Точные remotes и политика документации:
[`../registry/repositories.yaml`](../registry/repositories.yaml).

## Gradle-зависимости

```text
app ───────────────► maplibui ─────────────► maplib
 │                       │
 ├───────────────────────┴───────────────► easypicker-module
 └───────────────────────────────────────► wizardpager
```

`maplibui` экспортирует `maplib` как `api`, поэтому часть типов `maplib`
доступна `app` транзитивно. Изменение публичного API в `maplib` нужно проверять
как минимум в `maplibui` и `app`.

## Основные runtime-потоки

- Запуск: `MainApplication` → `MainActivity` → `MapFragment`.
- Карта: `MapFragment` реализует `MaplibreMapInteraction`, а `MapDrawable`
  управляет MapLibre style/sources/layers.
- NGW import: выбор ресурса в `maplibui` → `LayerFillService` → GIS objects в
  `maplib` → reload карты через интерфейсы `maplib`/`app`.
- Collector: metadata в `maplib`, registry/workspaces и orchestration в
  `maplibui`, переключение проекта в `app`.
- Sync: Android sync/service layer вызывает NGW/storage логику библиотек.
- Release/update: flavor/version задаются в `app/build.gradle`; self-hosted
  updater находится в `app`.

## Правило границ

- `maplib` не должен зависеть от `maplibui` или `app`.
- `maplibui` не должен импортировать классы из `app`; обратный вызов идёт через
  интерфейсы `maplib`.
- Root координирует совместимые pointers сабмодулей и central docs.
- Новая cross-repository связь фиксируется в `dependencies.yaml` и
  `change-impact.yaml`.
