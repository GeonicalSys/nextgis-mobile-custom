---
title: Подготовка NGW Collector-проекта
type: runbook
last_verified: 2026-07-29
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/CollectorProjectMetadata.java
  - maplib/src/main/java/com/nextgis/maplib/map/LayerOriginMetadata.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CollectorProjectRegistry.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
---

# Подготовка NGW Collector-проекта

Это чек-лист структуры NGW, необходимой мобильному приложению. Один импортированный
`collector_project` соответствует одному изолированному workspace; переключение проекта меняет
`map_path`/`map_name`, а не видимость общей смеси слоёв.

## Ресурс проекта

1. Создайте/выберите NGW-ресурс типа `collector_project` и добавьте в него нужные vector/PostGIS
   слои в требуемом порядке.
2. Для фильтрации по району задайте на проекте `resmeta.items.district`.
3. Тяжёлые `.ngrc` растры импортируйте отдельно: они не входят в composition sync.
4. Не используйте имя проекта как identity. Приложение формирует стабильный `project_uid` из
   account и remote project id и хранит registry в `map/collector_projects/`.

## Конфигурация слоя

- Мобильный config хранится в `resource.description` слоя NGW и задаёт поля, renderer, visibility,
  zoom и sync settings.
- Для project-managed слоя включайте галочку редактирования именно у элемента
  Collector-проекта. Для display-only элемента отключите эту галочку и
  исходящую синхронизацию; config sync при `SYNC_NONE` остаётся отдельным
  разрешённым потоком.
- Общий `is_editable` в mobile config управляет обычными/вручную
  импортированными слоями и не заменяет галочку элемента Collector.
- Для тяжёлого read-only polygon/multipolygon или простого точечного слоя можно
  явно задать `mobile_render_mode = local_vector_tiles`. Точка должна
  использовать простой renderer, `MarkerStyleCircle` (`type = 2`) без custom icon и подпись
  из одного поля либо фиксированного текста. Приложение сохраняет значение в
  `layer_origin.render_mode`; неподдерживаемый случай откатывается к classic rendering.
- FormBuilder-форма связывается со слоем через `form_id`. При rebuild/fill связь должна
  сохраняться.

## Локальные metadata

Project workspace хранит `collector_project` с `project_uid`, account, remote id, district и
состоянием composition sync. Управляемый слой хранит `layer_origin` с тем же `project_uid`,
`managed_by_project = true`, `collector_order`, `form_id` и `render_mode`.

Дополнительный NGW-слой, импортированный вручную в активный workspace, должен оставаться
`type = manual_ngw` и `managed_by_project = false`. Composition sync не удаляет такие слои и слои
без `layer_origin`.

## Проверка после импорта

- проект появился в registry и открывается в отдельном workspace;
- порядок project-managed слоёв совпадает с NGW composition;
- «Мои треки» находится наверху списка слоёв;
- создание объекта предлагает только элементы Collector с включённым
  редактированием и исходящей синхронизацией;
- district subset, renderer, zoom, form и sync mode применены;
- ручной слой остаётся вне project-managed состава;
- изменение состава добавляет/переупорядочивает управляемые слои;
- перед destructive rebuild/removal создаётся backup, а при ошибке backup операция отменяется;
- `local_vector_tiles` polygon либо простая read-only точка отображается и
  имеет classic fallback при ошибке provider или неподдерживаемом стиле.

Подробные контракты: [Collector architecture](../architecture/collector-projects.md). Открытые
задачи: [Collector roadmap](../roadmap/collector.md).
