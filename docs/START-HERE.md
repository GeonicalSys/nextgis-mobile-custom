---
title: С чего начать — GeonicalSystem NextGIS Mobile
type: guide
last_verified: 2026-07-23
related_code:
  - app/build.gradle
  - settings.gradle
---

# С чего начать

## Ментальная модель

Этот репозиторий — мобильная часть экосистемы ЛИСА. Две соседние части:

| Проект | Владеет |
|---|---|
| `Q:\standart_profiles` | запуском QGIS, брендами, ролями и доставкой desktop-профиля |
| проект Plugins в `%APPDATA%\QGIS\QGIS3\profiles\develop\python\plugins` | исходниками QGIS-плагинов, подготовкой и публикацией GIS-ресурсов |
| этот workspace | Android-приложением, локальными полевыми данными, NGW/Collector import и sync |

Между desktop workspace и Android нет скрытого файлового импорта. Связь
проходит через ресурсы NextGIS Web/Collector или явный пользовательский импорт
поддерживаемого переносимого артефакта; одинаковый бренд `LISA` не делает
`Q:\standart_profiles` runtime-зависимостью APK. Полная карта:
[`architecture/lisa-ecosystem.md`](architecture/lisa-ecosystem.md), точные
машиночитаемые связи: [`registry/ecosystem.yaml`](registry/ecosystem.yaml).

Внутри Android-форка четыре уровня:

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
3. `docs/registry/ecosystem.yaml` — владельцы и границы между тремя проектами
   ЛИСА.
4. Architecture/runbooks/reference — объяснения для людей.
5. Локальный module pack — навигация внутри одного компонента.
6. `docs/history` — контекст завершённых циклов, но не действующий контракт.

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
| формат/права/состав NGW или Collector между QGIS и Android | `architecture/lisa-ecosystem.md`, `registry/ecosystem.yaml`, затем docs проекта-владельца |
| launcher, профиль QGIS или доставка desktop-плагина | docs `standart_profiles`; Android docs нужны только при изменении общего server contract |

## Быстрый цикл агента

1. Определи репозитории и модули в scope.
2. Открой ближайшие `AGENTS.md` и manifests.
3. Найди trigger в `change-impact.yaml`.
4. Проверь invariant IDs и upstream hotspots.
5. Измени код, выполни автоматические и ручные проверки.
6. Обнови docs по Definition of Done и запусти validator.

Для end-to-end задачи сначала определи владельца изменения. Код launcher и
профиля меняется в `standart_profiles`, код QGIS-инструмента — только в проекте
Plugins, код мобильного клиента — здесь. Не исправляй plugin mirror в
`standart_profiles` и не добавляй Android-зависимость от current-machine пути.

## Основные карты знаний

- [`reference/fork-customizations.md`](reference/fork-customizations.md) — каталог отличий форка.
- [`architecture/lisa-ecosystem.md`](architecture/lisa-ecosystem.md) — общая
  модель desktop QGIS, плагинов, NGW и мобильного клиента.
- [`reference/official-differences.md`](reference/official-differences.md) — краткое актуальное
  описание возможностей форка, которых ещё нет в official; предназначено для передачи upstream.
- [`history/upstream/README.md`](history/upstream/README.md) — завершённые upstream cycles.
- [`../WHATS_NEW.md`](../WHATS_NEW.md) — пользовательские release notes.
- [`runbooks/collector-project-setup.md`](runbooks/collector-project-setup.md) — настройка Collector.
- [`architecture/map-performance.md`](architecture/map-performance.md) — состояние и план производительности.
- [`roadmap/collector.md`](roadmap/collector.md) — только ещё не завершённые Collector-задачи.
