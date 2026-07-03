# Collector Architecture Roadmap

План доработок вокруг Collector-проектов. Этот файл нужен как рабочая память для
следующих задач: новые изменения должны учитывать уже заложенные `collector_project`
и `layer_origin` metadata.

## Already laid foundation

- `LayerGroup` может хранить `collector_project`.
- `NGWVectorLayer` может хранить `layer_origin`.
- Новый импорт Collector пишет `project_uid` на группу и на каждый project-managed слой.
- Новый ручной импорт NGW-слоя помечается как `manual_ngw`.
- `LayerFillService` протаскивает origin metadata через обычный NGW fill и через `VECTOR_LAYER_WITH_FORM`.
- Schema rebuild сохраняет origin metadata, чтобы слой не выпадал из будущей сверки проекта.
- `render_mode` уже есть в `layer_origin`, пока используется `classic`.

## Next milestone: Collector composition dry-run

Добавить `CollectorProjectSyncManager` в режиме dry-run:

- найти локальные группы с `collector_project`;
- скачать актуальный snapshot `collector_project` из NGW;
- сравнить список слоев проекта с локальными `layer_origin.project_uid`;
- посчитать diff:
  - `add`: слой есть в Collector project, но нет локально;
  - `remove`: локальный managed слой удален из Collector project;
  - `reorder`: порядок отличается;
  - `update_form`: изменился form id;
  - `update_config`: изменился config hash;
- только логировать diff, ничего destructive не выполнять.

## Composition sync rules

- Трогать только слои с `layer_origin.managed_by_project = true`.
- Слои `manual_ngw` и слои без `layer_origin` не удалять при сверке состава Collector.
- `.ngrc` растры остаются вне Collector composition sync.
- Если слой добавлен в Collector project, загрузить его тем же fill path, что и первичный импорт.
- Если слой удален из Collector project, сначала проверить backup/sync safety, потом скрыть или удалить
  в зависимости от выбранной политики.

## Backup gateway

Перед любой автоматической перезаливкой или удалением редактируемого слоя:

- проверить `sync_direction` и наличие локальных changes/attachments;
- создать backup manifest;
- сохранить `config.json`, `layer_origin`, формы, attachments;
- сохранить SQLite-таблицу слоя, changes table и attachments table;
- только после успешного backup выполнять rebuild/delete.

Нужный отдельный компонент: `LayerReplacementManager`.

## Form sync

Добавить отдельную синхронизацию FormBuilder:

- хранить на слое `form_id` и `form_hash`/`form_updated`;
- при sync скачивать `/resource/<form_id>/ngfp` во временную папку;
- проверять наличие `form.json` и `ngfp_meta.json`;
- атомарно заменять локальные form files;
- не перезагружать геоданные, если изменилась только форма.

## Config sync cleanup

Текущий config sync уже обновляет и `SYNC_NONE` NGW-слои. Нужно уточнить:

- единый hash для server description/config;
- отдельная классификация soft/hard изменений;
- hard change для редактируемого слоя должен идти через backup gateway;
- soft change не должен сбрасывать `layer_origin`;
- config sync должен учитывать manual NGW layers, но не включать их в Collector composition.

## Multi-project UX

Будущая UI-часть:

- список импортированных Collector projects;
- активный проект/группа;
- переключение видимости групп;
- диагностика: account, project remote id, district, last composition check, diff summary.

## Local vector tiles plan

Для тяжелых read-only слоев:

- включать только по metadata/config конкретного NGW-слоя;
- сохранять `layer_origin.render_mode = local_vector_tiles`;
- источник истины остается локальная БД слоя;
- MapLibre получает локальные vector tiles через локальный tile provider/cache;
- редактируемые слои сначала не переводить на tile-render path.

## Safety defaults

- Любой новый destructive sync path сначала должен быть dry-run.
- Если нет `collector_project` или `layer_origin`, слой считается неуправляемым проектом.
- При ошибках сверки состава проекта лучше пропустить операцию и оставить локальные данные,
  чем удалить или перезалить слой.
