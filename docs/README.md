---
title: GeonicalSystem NextGIS Mobile — документация
type: index
last_verified: 2026-10-10
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
| Связь с `standart_profiles` и QGIS Plugins | [architecture/lisa-ecosystem.md](architecture/lisa-ecosystem.md) |
| Изменение кода | [guides/change-checklist.md](guides/change-checklist.md) |
| MapLibre/layer order | [architecture/map-rendering.md](architecture/map-rendering.md) |
| Map performance | [architecture/map-performance.md](architecture/map-performance.md) |
| Зависания журнала и повторные HTTP-ошибки | [architecture/error-reporting.md](architecture/error-reporting.md) и [проверка](history/mobile-diagnostics-verification-2026-10-10.md) |
| Тип нового объекта и зависимые поля | [architecture/feature-type-creation.md](architecture/feature-type-creation.md) и [guides/form-behavior-user-guide.md](guides/form-behavior-user-guide.md) |
| Вынос координат и звуковое наведение | [architecture/stakeout.md](architecture/stakeout.md) |
| Синхронизация всех проектов для пользователя | [guides/project-synchronization-user-guide.md](guides/project-synchronization-user-guide.md) |
| Уведомление остаётся после sync | [architecture/ngw-sync-and-storage.md](architecture/ngw-sync-and-storage.md) и [проверка уведомлений](history/sync-notifications-verification-2026-10-10.md) |
| Синхронизация без сети и обход после потери GPS | [проверка](history/sync-offline-walk-verification-2026-10-10.md) |
| Collector/NGW sync | [architecture/collector-projects.md](architecture/collector-projects.md) |
| Настройка Collector | [runbooks/collector-project-setup.md](runbooks/collector-project-setup.md) |
| Upstream merge | [runbooks/upstream-sync.md](runbooks/upstream-sync.md) |
| Release APK | [runbooks/release-apk.md](runbooks/release-apk.md) |
| Настройки/build | [reference/build-matrix.md](reference/build-matrix.md) и [reference/settings-and-config.md](reference/settings-and-config.md) |
| Отличия форка | [reference/fork-customizations.md](reference/fork-customizations.md) |
| Передача отличий official-разработчикам | [reference/official-differences.md](reference/official-differences.md) |
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

## Правила мобильных проектов

[Руководство пользователя](guides/project-scripts-user-guide.md),
[архитектура и API v1](architecture/project-scripts.md),
[проверки и ограничения поставки](reference/project-scripts-verification.md).
