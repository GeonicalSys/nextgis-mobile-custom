---
title: Настройки и конфигурационные ключи
type: reference
last_verified: 2026-07-19
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/res/xml/preferences_general.xml
  - app/src/main/AndroidManifest.xml
  - maplib/src/main/java/com/nextgis/maplib/util/LayerConfigUtil.java
---

# Настройки и конфигурационные ключи

Полный структурированный список находится в
[`../registry/config-keys.yaml`](../registry/config-keys.yaml).

## Правила

- Key/default в constants, XML и runtime fallback должны совпадать.
- Новый preference получает owner, type, default, UI/source и migration note.
- Build property и manifest metadata документируются без secret value.
- Layer JSON/metadata keys меняются только с backward-compatible parser или
  явной миграцией.

## Группы

- App map/UI: location, compass, info, zoom controls, layer list.
- Tracking/location: интервалы, distance, foreground service toggles.
- Updates: `check_updates`, update flavor metadata, release repository fields.
- Collector: project registry JSON, project metadata, composition state.
- Layer config: `mobile_render_mode`, `render_mode`, `layer_origin`, `mobile`.
- Local storage: Collector workspaces и `LayerBackups`.

`local.properties`, real Sentry DSN, NGID client secrets и signing credentials
не являются документационными данными.
