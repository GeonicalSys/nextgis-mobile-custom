---
title: Настройки и конфигурационные ключи
type: reference
last_verified: 2026-08-27
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/java/com/nextgis/mobile/stakeout/StakeoutSettings.java
  - app/src/main/res/xml/preferences_general.xml
  - app/src/main/res/xml/preferences_map.xml
  - app/src/main/res/xml/preferences_tracks.xml
  - app/src/main/AndroidManifest.xml
  - maplib/src/main/java/com/nextgis/maplib/util/LayerConfigUtil.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/GeometryEditDraftStore.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/BackgroundRecordingSoundMonitor.java
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

- App map/UI: location, compass, info, zoom controls, layer list. Internal
  `map_rotation_enabled=false` keeps two-finger rotation locked by default;
  `map_bearing=0` stores the last bearing only while the user has explicitly
  enabled rotation. Neither key is shown in the general settings screen: the
  state is controlled by the toolbar button next to location. When enabled,
  MapLibre keeps pinch and rotate detectors concurrent and uses a `0.5°` rotate
  threshold so immediate simultaneous two-finger contact is responsive.
- Фото-вложения: `photo_overlay_enabled=true` и
  `photo_overlay_use_object_coords=true` включены по умолчанию. Миграция
  `photo_overlay_defaults_enabled_v1` один раз включает оба ключа на уже
  установленной версии; после этого ручной выбор пользователя сохраняется.
- Tracking/location: интервалы, distance, foreground service toggles и
  `background_recording_sound=true`. Последний ключ включает короткое звуковое
  подтверждение по фиксированному расписанию раз в 10 секунд, когда UI приложения
  скрыт либо экран выключен и health-подписка продолжает получать свежие пригодные
  координаты. Подписка не требует изменения координаты и не добавляет точки в
  геометрию, поэтому неподвижный GPS продолжает пикать; при прекращении доставки
  координат сигнал замолкает. Явная ошибка сохранения получает отдельный более
  длинный сигнал не чаще раза в минуту. Звук использует alarm stream и поэтому
  не зависит от минимальной громкости уведомлений. Если alarm stream выключен
  или его громкость равна нулю, heartbeat заменяется короткой вибрацией, а ошибка
  сохранения — короткой двойной вибрацией. Выключение самого preference подавляет
  и звук, и вибрацию. Partial wake lock удерживает стабильный интервал.
- Вынос координат: начальное состояние звука и четыре строго убывающих порога
  `stakeout_far_distance`, `stakeout_medium_distance`, `stakeout_near_distance`,
  `stakeout_reached_distance` в метрах. Некорректный набор не применяется;
  runtime использует безопасные значения `5 / 1 / 0,5 / 0,1`.
- Updates: `check_updates`, update flavor metadata, release repository fields.
- Backups: `layer_backup_max_gb` (Общие → Другое, default 5 GB) caps `LayerBackups/`;
  каждый ZIP хранит таблицы слоя и только локальные файлы вложений, без
  скачивания server-only payload по метаданным.
- Collector: project registry JSON, project metadata, composition state.
- Layer config: `feature_label_field`, `mobile_render_mode`, `render_mode`,
  `layer_origin`, `mobile`.
- Local storage: Collector workspaces и `LayerBackups`.
- Crash journals: `track_recording_enabled`, `walkedit_temp`,
  `geometry_edit_draft`, `feature_form_draft`; это app-private runtime state, а
  не пользовательские настройки UI. `track_recording_enabled` читается панелью
  карты и `TrackerService` в одном основном процессе, чтобы Start/Stop не
  расходились из-за межпроцессного кэша SharedPreferences.

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
