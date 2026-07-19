---
title: app — Android-приложение Lisa/Belka
module_id: app
last_verified: 2026-07-19
---

# app — Android-приложение Lisa/Belka

## Назначение

Продуктовый Android-модуль: запускает GISApplication, предоставляет основной
UI и Map host, управляет брендами, preferences, release и self-hosted update.

## Основные сценарии

- запуск приложения и открытие карты;
- управление слоями, edit/walk/track через библиотеки;
- выбор и переключение Collector projects;
- добавление vector/raster NGW-слоя по прямому URL, включая проверенный guest fallback;
- настройки приложения и Android permissions/services;
- проверка и установка Lisa/Belka updates;
- экспорт/очистка сохранённых layer backups.

## Ограничения

- `lisa` и `belka` — отдельные product flavors.
- `MapFragment` должен реализовывать актуальный `MaplibreMapInteraction`.
- Не дублировать GIS model/storage из `maplib`.
- Self-hosted update принимается только для того же flavor/application/signing
  identity и с увеличенным versionCode.
- Реальные DSN, client secrets и signing credentials не входят в docs.
- Sentry оставляет crash screenshots, но не собирает interaction breadcrumbs и
  view hierarchy; traces/profiling в production семплируются с долей `0.05`.

## Диагностика

- Карта/слои: сначала проверить callbacks `MapFragment` и состояние
  `GISApplication`, затем rendering docs.
- Неправильный бренд: `app/build.gradle`, flavor resources и manifest metadata.
- Update отклонён: manifest identity, version, URL/size/hash/certificate; не
  отключать проверку для обхода ошибки.
- Collector переключается неверно: `CollectorProjectRegistry` и project UID/map
  path, а не только UI dialog.
- URL не импортируется: проверить parser, совпадение server URL с аккаунтом,
  response code, тип ресурса и `data.read`; отсутствие `data.write` — read-only,
  а не ошибка импорта.

## Проверки

Структурированный список: [manifest.yaml](manifest.yaml). При изменении app API
или общих resources обязательны обе release-сборки.
