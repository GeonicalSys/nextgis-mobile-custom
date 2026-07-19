---
title: С чего начать — GeonicalSystem NextGIS Mobile
type: guide
last_verified: 2026-07-19
related_code:
  - app/build.gradle
  - settings.gradle
---

# С чего начать

## Ментальная модель

Это форк Android-приложения NextGIS Mobile с четырьмя уровнями:

1. `app` — бренд, Android lifecycle, экран карты, preferences, release/update.
2. `maplibui` — UI слоёв, NGW import, фоновые операции, Collector workspaces.
3. `maplib` — модель GIS, MapLibre rendering, storage и NGW protocol/sync.
4. `easypicker` и `wizardpager` — вспомогательные UI-библиотеки.

Root, `maplib`, `maplibui` и `easypicker` — разные git-репозитории. Центральная
документация версионируется в root; локальные packs живут внутри соответствующих
репозиториев и доступны при их отдельном открытии.

## Источники истины

1. Код и Gradle/manifest — фактическое поведение.
2. `docs/registry/*.yaml` — стабильные IDs, зависимости, инварианты и проверки.
3. Architecture/runbooks/reference — объяснения для людей.
4. Локальный module pack — навигация внутри одного компонента.
5. Старые root-документы — мигрируемые источники и история; при конфликте
   перепроверять по коду и registry.

## Маршрутизация задачи

| Если меняется | Читать |
|---|---|
| `MapDrawable`, style/source/layer | `architecture/map-rendering.md`, `INV-LAYER-ORDER`, `INV-HOT-ADD-CONSISTENCY` |
| `LayerFillService`, import NGRc/Collector | map rendering + collector docs + `maplibui` pack |
| `GISApplication`, schema rebuild, removal | collector docs + `INV-BACKUP-BEFORE-DESTRUCTION` |
| `MaplibreMapInteraction` или `IGISApplication` | dependencies + change-impact + packs `app`, `maplib`, `maplibui` |
| `app/build.gradle` | flavors/versioning + release runbook |
| `.gitmodules`, submodule pointers, upstream | upstream runbook + overlaps registry |
| preferences/manifest metadata | settings registry + owning module pack |

## Быстрый цикл агента

1. Определи репозитории и модули в scope.
2. Открой ближайшие `AGENTS.md` и manifests.
3. Найди trigger в `change-impact.yaml`.
4. Проверь invariant IDs и upstream hotspots.
5. Измени код, выполни автоматические и ручные проверки.
6. Обнови docs по Definition of Done и запусти validator.

## Важные существующие документы

- [`../CUSTOMIZATIONS.md`](../CUSTOMIZATIONS.md) — полный legacy-каталог кастомизаций; мигрируется постепенно.
- [`../UPSTREAM_SYNC_REPORT.md`](../UPSTREAM_SYNC_REPORT.md) — исторические циклы синхронизации.
- [`../WHATS_NEW.md`](../WHATS_NEW.md) — пользовательские release notes.
- [`../COLLECTOR_PROJECT_SETUP_GUIDE.md`](../COLLECTOR_PROJECT_SETUP_GUIDE.md) — legacy-инструкция Collector.
- [`../MAP_STARTUP_PERFORMANCE_NOTES.md`](../MAP_STARTUP_PERFORMANCE_NOTES.md) — исследование производительности.
