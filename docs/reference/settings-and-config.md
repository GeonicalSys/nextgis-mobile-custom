---
title: Настройки и конфигурационные ключи
type: reference
last_verified: 2026-07-29
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/res/xml/preferences_general.xml
  - app/src/main/AndroidManifest.xml
  - maplib/src/main/java/com/nextgis/maplib/util/LayerConfigUtil.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/GeometryEditDraftStore.java
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
- Backups: `layer_backup_max_gb` (Общие → Другое, default 5 GB) caps `LayerBackups/`.
- Collector: project registry JSON, project metadata, composition state.
- Layer config: `feature_label_field`, `mobile_render_mode`, `render_mode`,
  `layer_origin`, `mobile`.
- Local storage: Collector workspaces и `LayerBackups`.
- Crash journals: `track_recording_enabled`, `walkedit_temp`,
  `geometry_edit_draft`, `feature_form_draft`; это app-private runtime state, а
  не пользовательские настройки UI.

`mobile_render_mode: "local_vector_tiles"` применяется только как явный opt-in:
для read-only polygon/multipolygon либо простого `Point` с круговым маркером
`MarkerStyleCircle` (`type = 2`) и
подписью из одного поля/фиксированного текста. Неподдерживаемый точечный стиль,
rule renderer, custom icon, template или editable слой автоматически остаётся
на classic rendering.

`feature_label_field` — верхнеуровневое поле `config.json`, которое задаёт
отображаемое имя объекта в верхней панели, таблице атрибутов и списке выбора,
когда под тапом оказалось несколько объектов. Например:

```json
"feature_label_field": "num_line"
```

Это не поле подписи на карте: последнее по-прежнему задаётся отдельно в
`renderer_properties.style.value`. Выбор в «Настройки слоя → Поля» сохраняется
сразу в `config.json`; для старых слоёв значение читается из локального
preference `layer_label` и переносится в JSON после первого успешного открытия
слоя новой версией.
Отсутствующее поле или пустое значение объекта даёт fallback на `_id`.

`local.properties`, real Sentry DSN, NGID client secrets и signing credentials
не являются документационными данными.

Базовый URL APK repository задан константой `APK_VERSION_UPDATE` и равен
`https://apps-geonical.ru/lisa-mobile`. Он не является preference: изменение
host/path требует новой подписанной сборки. Ветка выбирается из build variant и
проверяется повторно по metadata загруженного APK.
