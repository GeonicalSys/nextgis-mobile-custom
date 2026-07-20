---
title: app — Android-приложение Lisa/Belka
module_id: app
last_verified: 2026-07-20
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
- Update repository использует ветки `lisa`, `belka`, `debug` непосредственно
  под `https://apps-geonical.ru/lisa-mobile`; сегмента `stable` в URL нет.
- Для каждого build variant один account type обязан одновременно использоваться
  в runtime `MainApplication`, `AccountAuthenticator` и `SyncAdapter`; release
  использует `com.nextgis.account.geonical`, debug — `com.nextgis.account.debug`.
- Реальные DSN, client secrets и signing credentials не входят в docs.
- Sentry оставляет crash screenshots, но не собирает interaction breadcrumbs и
  view hierarchy; traces/profiling в production семплируются с долей `0.05`.

## Диагностика

- Карта/слои: сначала проверить callbacks `MapFragment` и состояние
  `GISApplication`, затем rendering docs.
- Неправильный бренд: `app/build.gradle`, flavor resources и manifest metadata.
- Update отклонён: проверить schema, branch/channel, identity, version,
  versioned URL, size/hash/certificate и доступность branch manifest; не
  отключать проверку для обхода ошибки.
- Collector переключается неверно: `CollectorProjectRegistry` и project UID/map
  path, а не только UI dialog.
- URL не импортируется: проверить parser, совпадение server URL с аккаунтом,
  response code, тип ресурса и `data.read`; отсутствие `data.write` — read-only,
  а не ошибка импорта.
- Веб ГИС принимает логин, но не появляется: сверить merged-значения
  `nextgis_accounts_auth`, `nextgis_accounts_auth_type` и runtime account type.
  Локальный отказ `AccountManager` должен оставить форму открытой и попасть в HyperLog.

## Проверки

Структурированный список: [manifest.yaml](manifest.yaml). При изменении app API
или общих resources обязательны обе release-сборки.
