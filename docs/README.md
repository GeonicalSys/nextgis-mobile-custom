---
title: GeonicalSystem NextGIS Mobile — документация
type: index
last_verified: 2026-10-27
related_code:
  - settings.gradle
  - .gitmodules
---

# Документация GeonicalSystem NextGIS Mobile

- [Дорожная карта мобильного приложения и согласование всех доработок](roadmap/mobile.md).
- [Управление треками и точки над линиями](architecture/track-controls.md).
- [Квитанция проверки треков от 25 октября](reference/track-controls-verification-2026-10-25.md).
- [Выпуск ЛИСЫ224: исправления, приёмка и пользовательское исключение](reference/mobile-release-fixes-2026-10-26.md).

## Копирование и преобразования

[Копирование, сдвиг и поворот объектов](architecture/feature-copy-transform.md): пользовательский путь, schema/geometry compatibility, draft/Undo и проверки.


Единый источник знаний для людей и ИИ-агентов о форке NextGIS Mobile.
Человекочитаемые объяснения находятся в Markdown, точные связи и контракты — в
`registry/*.yaml`.

## Вход по роли

| Задача | Начать с |
|---|---|
| Устройства, учётные записи, проекты и запуски | [architecture/mobile-usage.md](architecture/mobile-usage.md) |
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
| Долгий CI и loopback-тесты отправки треков | [reference/build-matrix.md](reference/build-matrix.md) |
| Отличия форка | [reference/fork-customizations.md](reference/fork-customizations.md) |
| Передача отличий official-разработчикам | [reference/official-differences.md](reference/official-differences.md) |
| Плановая и внеплановая доработка | [roadmap/mobile.md](roadmap/mobile.md) |
| Детализация Collector-задач | [roadmap/collector.md](roadmap/collector.md), сверять с общей картой и кодом |
| Blast radius | [registry/change-impact.yaml](registry/change-impact.yaml) |

## Разделы

- [architecture/](architecture/overview.md) — устройство приложения и критичные потоки.
- [guides/](guides/change-checklist.md) — правила безопасной разработки.
- [runbooks/](runbooks/workstation-and-build.md) — воспроизводимые операции.
- [reference/](reference/build-matrix.md) — версии, flavors, настройки и совместимость.
- [registry/](registry/README.md) — машиночитаемые источники истины.
- [history/](history/README.md) — исторические отчёты, не действующие контракты.
- [roadmap/](roadmap/mobile.md) — общая очередь и технические детализации; факты кода, риски и идеи разделены.
- [changelog.md](changelog.md) — история самой документационной системы.

## Проверка

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\docs-check.ps1 -RunTests
```

Push-check учитывает документацию из всех коммитов PR. Выбор базы, strict
проверка и регрессии описаны в [матрице проверок](reference/build-matrix.md#проверка-документации).

## Правила мобильных проектов

[Руководство пользователя](guides/project-scripts-user-guide.md),
[архитектура и API v1](architecture/project-scripts.md),
[проверки и ограничения поставки](reference/project-scripts-verification.md).
