---
title: GeonicalSystem NextGIS Mobile — документация
type: index
last_verified: 2026-07-19
related_code:
  - settings.gradle
  - .gitmodules
---

# Документация GeonicalSystem NextGIS Mobile

Единый источник знаний для людей и ИИ-агентов о форке NextGIS Mobile.
Человекочитаемые объяснения находятся в Markdown, точные связи и контракты — в
`registry/*.yaml`.

## Вход по роли

| Задача | Начать с |
|---|---|
| Первый раз в проекте | [START-HERE.md](START-HERE.md) |
| Изменение кода | [guides/change-checklist.md](guides/change-checklist.md) |
| MapLibre/layer order | [architecture/map-rendering.md](architecture/map-rendering.md) |
| Map performance | [architecture/map-performance.md](architecture/map-performance.md) |
| Collector/NGW sync | [architecture/collector-projects.md](architecture/collector-projects.md) |
| Настройка Collector | [runbooks/collector-project-setup.md](runbooks/collector-project-setup.md) |
| Upstream merge | [runbooks/upstream-sync.md](runbooks/upstream-sync.md) |
| Release APK | [runbooks/release-apk.md](runbooks/release-apk.md) |
| Настройки/build | [reference/build-matrix.md](reference/build-matrix.md) и [reference/settings-and-config.md](reference/settings-and-config.md) |
| Отличия форка | [reference/fork-customizations.md](reference/fork-customizations.md) |
| Открытые Collector-задачи | [roadmap/collector.md](roadmap/collector.md) |
| Blast radius | [registry/change-impact.yaml](registry/change-impact.yaml) |

## Разделы

- [architecture/](architecture/overview.md) — устройство приложения и критичные потоки.
- [guides/](guides/change-checklist.md) — правила безопасной разработки.
- [runbooks/](runbooks/workstation-and-build.md) — воспроизводимые операции.
- [reference/](reference/build-matrix.md) — версии, flavors, настройки и совместимость.
- [registry/](registry/README.md) — машиночитаемые источники истины.
- [history/](history/README.md) — исторические отчёты, не действующие контракты.
- [roadmap/](roadmap/collector.md) — только незавершённые и проверенные по коду планы.
- [changelog.md](changelog.md) — история самой документационной системы.

## Проверка

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1 -RunTests
```
