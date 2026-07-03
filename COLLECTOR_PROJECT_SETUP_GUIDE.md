# Collector Project Setup Guide

Краткая памятка по структуре проектов NGW Collector для мобильного приложения.
Это не полная инструкция по NGW, а список элементов, которые нужно сразу заложить,
чтобы приложение могло синхронизировать состав проекта, формы и config без
повторной загрузки тяжелых данных.

## Collector project

- Основная рабочая схема загрузки слоев: импорт ресурса `collector_project`.
- Один Collector project должен импортироваться в отдельную группу слоев в приложении.
- На ресурсе Collector project желательно хранить `resmeta.items.district`, если проект должен
  фильтровать PostGIS-слои по району.
- Несколько проектов на устройстве допустимы: разные `district` в одной Web GIS или разные Web GIS.
- Тяжелые растры `.ngrc` остаются отдельным ручным импортом и не считаются частью сверки состава Collector.

## Project identity

Приложение сохраняет на локальной группе блок:

```json
"collector_project": {
  "schema_version": 1,
  "project_uid": "collector:<account>:<collector_project_remote_id>",
  "account": "<NGW account name>",
  "project_remote_id": 123,
  "name": "Project name",
  "district": "vologda",
  "composition_sync": true,
  "imported_at": 1780000000000
}
```

Важно: будущая сверка состава проекта опирается именно на `project_uid`, а не на имя группы.

## Layer config

Для каждого векторного или PostGIS-слоя, который входит в Collector project:

- мобильный `config.json` должен храниться в `resource.description` слоя NGW;
- config должен содержать поля, renderer/style, видимость, zoom, sync settings;
- если слой read-only или только для отображения, sync можно отключить через config;
- если слой в будущем должен рисоваться локальными vector tiles, добавить в config/metadata
  режим `render_mode = local_vector_tiles` после реализации соответствующего render path.

## Layer origin

Новые импорты сохраняют на локальном NGW-слое:

```json
"layer_origin": {
  "schema_version": 1,
  "type": "collector_project",
  "project_uid": "collector:<account>:<collector_project_remote_id>",
  "managed_by_project": true,
  "collector_order": 0,
  "form_id": 456,
  "render_mode": "classic"
}
```

Ручные дополнительные NGW-слои сохраняются как:

```json
"layer_origin": {
  "schema_version": 1,
  "type": "manual_ngw",
  "managed_by_project": false,
  "form_id": 456,
  "render_mode": "classic"
}
```

Правило: будущая сверка состава Collector может добавлять/удалять/переупорядочивать только
`managed_by_project = true`. Ручные слои не трогать, но продолжать синхронизировать их config.

## Forms

- Формы NGW FormBuilder (`ngfp`) должны быть связаны с исходным слоем через `form_id`.
- При изменении формы на сервере будущая синхронизация должна обновлять форму отдельно от геоданных.
- Если слой пересоздается автоматически, `form_id` должен сохраняться и передаваться в rebuild/fill.

## Safety

- До реализации backup gateway нельзя автоматически удалять project-managed редактируемые слои,
  если у них есть несинхронизированные изменения.
- Любая автоматическая перезаливка редактируемого слоя должна сначала создать локальный backup.
- Слои без `layer_origin` считаются legacy/manual-safe и не должны участвовать в destructive
  сверке состава проекта.
