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

Current implementation note:

- Multi-project support is based on isolated map workspaces, not layer visibility switching.
- Imported Collector projects are persisted in `collector_projects_registry.json`.
- Each project owns a separate map workspace under `map/collector_projects/...`.
- `active_collector_project_uid` marks the last selected project.
- Switching writes `map_path` / `map_name`, closes the current map object, and recreates the main UI.
- Sync applies only to the currently loaded workspace because only one `MapBase` is active.
- Manual NGW layers imported while a project is active are project-local but still `manual_ngw`.
- Still missing for the future full screen: search/filter, per-project sync/diagnostics actions, and safe delete/archive of a project workspace from the registry.

Базовая UI-часть реализована:

- список импортированных Collector projects;
- сохранение `active_collector_project_uid`;
- переключение видимости групп;
- диагностика: account, project remote id, district, last composition check, diff summary.

Остается для будущего полноценного экрана:

- поиск/фильтрация при большом количестве проектов;
- отдельные команды синхронизации/диагностики по выбранному проекту;
- более наглядный статус ошибок composition/form/config sync.

## Local vector tiles plan

Current implementation note:

- `mobile_render_mode` and `render_mode` are now parsed from mobile layer config.
- Imported Collector/manual NGW layers persist the value in `layer_origin.render_mode`.
- Config sync applies render-mode changes as soft updates.
- `Constants.LOCAL_VECTOR_TILES_ENABLED = true`.
- `LocalVectorTileServer` exposes loopback `.pbf` tiles, and MapLibre reads them through a
  `VectorSource`.
- `LocalVectorTileProvider` builds tiles lazily from the current local SQLite layer store and spatial
  query path.
- First implementation scope is polygon/multipolygon read-only layers. Other geometry types and
  provider failures fall back to the classic `GeoJsonSource` path with diagnostics.
- Remaining work: geometry clipping/simplification, memory/disk tile cache, broader style parity,
  and stronger lifecycle cleanup for the local server.

Для тяжелых read-only слоев:

- включать только по metadata/config конкретного NGW-слоя;
- сохранять `layer_origin.render_mode = local_vector_tiles`;
- источник истины остается локальная БД слоя;
- MapLibre получает локальные vector tiles через локальный tile provider/cache;
- редактируемые слои сначала не переводить на tile-render path.

### Цель первого этапа

Проверить новую схему на одном тяжелом слое Collector-проекта без изменения поведения остальных слоев.
Ориентир для проверки: слой около 50 000 полигонов и около 50 атрибутивных полей.

Первый production-критерий не style-паритет, а устойчивость:

- Collector-проект по-прежнему импортируется из NGW;
- тяжелый слой скачивается вместе с проектом;
- слой быстро появляется на карте без загрузки всех объектов в один `GeoJsonSource`;
- tap/identify открывает атрибуты объекта;
- текущая архитектура остается основной и может быть возвращена отключением флага.

Редактирование и синхронизация такого слоя на первом этапе не рассматриваются. Для слоя должен быть
read-only режим, аналогичный текущему сценарию, где синхронизация отключена.

### Включение режима

Режим включается только явно, по metadata/config конкретного NGW-ресурса. Рабочий вариант ключа:

- `mobile_render_mode = local_vector_tiles`

При импорте Collector-проекта:

- обычные слои идут по текущему пути `classic`;
- слой с `mobile_render_mode = local_vector_tiles` получает `layer_origin.render_mode = local_vector_tiles`;
- глобальный feature flag в приложении может принудительно отключить всю tile-схему;
- при выключенном feature flag слой должен импортироваться или отображаться старым способом, либо быть
  безопасно заморожен/скрыт в зависимости от выбранной политики.

### Хранение данных

Для первого прототипа не начинать с перехода на GeoPackage. Это добавит слишком много переменных.

Начальный вариант:

- данные скачиваются из NGW тем же Collector fill path;
- атрибуты и геометрии сохраняются в текущую локальную SQLite-структуру слоя;
- рядом строится SQLite RTree companion index по bbox объектов;
- файловый `rtree`/полный MapLibre feature-list не должен быть обязательной частью tile-render path;
- локальная БД остается источником истины для отрисовки, identify и просмотра атрибутов.

GeoPackage можно рассмотреть отдельным этапом после доказательства tile-render path.

### Локальный tile provider

MapLibre не читает SQLite/GPKG напрямую. Для него слой должен выглядеть как обычный vector tile source.

Приложение поднимает локальный provider, например:

`http://127.0.0.1:<port>/tiles/<layerId>/{z}/{x}/{y}.pbf`

На запрос тайла provider:

- вычисляет bbox тайла в EPSG:3857;
- выбирает кандидатов через SQLite RTree;
- читает геометрию и только поля, нужные для отрисовки/подписи;
- упрощает геометрию под zoom;
- клипует геометрию по bbox тайла с buffer;
- кодирует результат в MVT;
- возвращает `.pbf` MapLibre.

Векторные тайлы генерируются лениво, по запросу. Обязательной полной предгенерации после загрузки из NGW
быть не должно.

### Кэш тайлов

Схема кэша:

- memory cache для текущего viewport;
- disk cache для уже построенных `.pbf`;
- ключ кэша: `layerId / z / x / y / dataVersion / styleVersion`.

После первичного импорта:

- не строить все тайлы всех zoom заранее;
- можно прогреть текущий viewport и соседние тайлы;
- остальное строить on-demand.

При полной перезагрузке read-only слоя из Collector:

- увеличить `dataVersion`;
- старые tile-cache entries станут недействительными;
- при необходимости удалить старый cache фоном.

### Отрисовка и стили

Для первого этапа держать style scope узким:

- polygon fill;
- outline;
- opacity;
- простая подпись, если нужна.

Практичный вариант: на этапе генерации тайла вычислять style properties из текущего renderer-а и класть их
в properties тайла:

- `fillColor`;
- `strokeColor`;
- `strokeWidth`;
- `label`;
- дополнительные свойства только по мере необходимости.

MapLibre style тогда остается простым и читает готовые properties через expressions. Полный паритет текущих
паттернов, символов и подписей считать отдельной задачей, а не условием первого прототипа.

### Identify и просмотр атрибутов

Для tap/identify не полагаться только на `queryRenderedFeatures`.

Надежный путь:

- tap на карте переводится в небольшой bbox допуска в EPSG:3857;
- SQLite RTree возвращает кандидатов;
- приложение делает точную проверку попадания точки в геометрию;
- получает `featureId`;
- существующий экран атрибутов открывается по `featureId` из локальной БД.

Атрибутивная таблица может продолжать читать строки из текущей локальной таблицы слоя. Тайлы нужны только
для массовой отрисовки.

### Изоляция от текущей архитектуры

Tile-render path должен быть боковой веткой, а не заменой всего `VectorLayer`:

- отдельный render mode в metadata слоя;
- отдельный MapLibre source/layer creation path;
- отдельный tile provider;
- отдельный tile cache;
- общий storage layer можно использовать повторно;
- обычные слои не должны зависеть от нового кода.

Если tile provider не стартовал, MVT encoding падает или MapLibre не смог добавить source:

- записать диагностический лог;
- не ломать загрузку остальных слоев;
- скрыть проблемный слой или fallback-нуть на `classic`, если это разрешено настройкой;
- оставить возможность отключить режим без удаления локальных данных.

### Оценка сложности

Вертикальный прототип для одного read-only polygon слоя:

- metadata flag и routing импорта: 2-4 дня;
- SQLite RTree companion index: 3-5 дней;
- локальный tile provider и MapLibre vector source: 5-8 дней;
- MVT encoding, clipping, simplification: 7-12 дней;
- базовое style mapping: 3-6 дней;
- SQL identify и открытие атрибутов: 3-5 дней;
- диагностика, fallback, smoke/perf tests: 5-8 дней.

Итого: 3-5 недель для прототипа, 6-10 недель для production-уровня.

### Оценка надежности

Надежность подхода высокая при сохранении узкого scope:

- включение только по metadata одного слоя;
- sync/edit для tile-слоя не трогаются;
- текущие `classic` слои не меняются;
- отключение feature flag возвращает приложение к старому поведению;
- источник истины остается локальная БД, а тайлы являются производным кэшем.

Главные риски:

- корректность MVT geometry clipping/simplification;
- производительность первого построения тайлов в плотных областях;
- неполный паритет сложных стилей;
- lifecycle локального provider-а на Android;
- диагностика проблем, которые раньше проявлялись как MapLibre/GeoJSON reload, а теперь будут tile-cache/provider issues.

Первый milestone считать успешным, если тяжелый слой отображается без полного `GeoJsonSource`, карта остается
отзывчивой, а tap открывает атрибуты по SQL identify.

## Safety defaults

- Любой новый destructive sync path сначала должен быть dry-run.
- Если нет `collector_project` или `layer_origin`, слой считается неуправляемым проектом.
- При ошибках сверки состава проекта лучше пропустить операцию и оставить локальные данные,
  чем удалить или перезалить слой.
