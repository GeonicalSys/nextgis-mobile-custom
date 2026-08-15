---
title: Каталог кастомизаций форка
type: reference
last_verified: 2026-08-15
related_code:
  - app
  - maplib
  - maplibui
  - tools/upstream-sync.ps1
---

# Каталог кастомизаций форка

Это компактная карта отличий GeonicalSystem от официального NextGIS Mobile. Код и
машиночитаемые registry-файлы остаются источниками истины; документ помогает быстро найти
владельца поведения и обязательные проверки. Полный прежний монолит сохранён в истории git до
коммита миграции документации.

Для передачи официальным разработчикам используйте отдельное краткое описание текущих
пользовательских отличий: [official-differences.md](official-differences.md). Этот каталог остаётся
внутренней технической навигацией и не заменяет handoff-документ.

| Область | Где описано | Основные владельцы |
|---|---|---|
| Сборка, AGP, flavors и версии | [build-matrix.md](build-matrix.md), [release-apk.md](../runbooks/release-apk.md) | `app/build.gradle`, `maplib/build.gradle` |
| Walk-by-geometry | [map-rendering.md](../architecture/map-rendering.md) | `MapFragment`, `MapDrawable`, `EditLayerOverlay`, `WalkEditService` |
| Вынос координат и звуковое наведение | [stakeout.md](../architecture/stakeout.md) | `StakeoutGeometryTarget`, `GpsEventSource`, `StakeoutController`, `MapFragment` |
| MapLibre rendering, hot reload и порядок слоёв | [map-rendering.md](../architecture/map-rendering.md) | `MapDrawable`, `MPLFeaturesUtils`, `VectorLayerRenderCache` |
| Производительность карты и локальные vector tiles | [map-performance.md](../architecture/map-performance.md) | `VectorLayer`, `LocalVectorTileProvider`, `LocalVectorTileServer` |
| NGW sync, account scheduling, layer fill и staged schema rebuild | [ngw-sync-and-storage.md](../architecture/ngw-sync-and-storage.md), [collector-projects.md](../architecture/collector-projects.md) | `SyncAdapter`, `SyncAccountWorker`, `LayerFillService`, `GISApplication` |
| Variant-specific Android account/provider identity | [flavors-and-versioning.md](flavors-and-versioning.md), [ngw-sync-and-storage.md](../architecture/ngw-sync-and-storage.md) | `app/build.gradle`, `MainApplication`, authenticator/sync adapter XML |
| NGW resource UI и batch import | [collector-project-setup.md](../runbooks/collector-project-setup.md) | `SelectNGWResourceActivity`, `NGWResourcesListAdapter`, `LayerFillProgressDialogFragment` |
| Прямой импорт NGW vector/raster по URL и read-only permissions | [ngw-sync-and-storage.md](../architecture/ngw-sync-and-storage.md) | `NGWResourceUrl`, `ResourceGroup`, `NGWResourceImportHelper`, `MainActivity` |
| Локальные редактируемые vector layers и GeoJSON/KML/GPX WGS 84 | module packs `maplib`/`maplibui`/`app`, `INV-LOCAL-VECTOR-LAYERS` | `VectorLayer`, `GeoJSONUtil`, `CoordinatePointParser`, `LayerFillService` |
| Config из NGW description и `SYNC_NONE` | [settings-and-config.md](settings-and-config.md), [collector-projects.md](../architecture/collector-projects.md) | `NgwLayerConfigAdapter`, sync classes |
| Collector metadata, complete snapshot, resumable import, form transaction и composition apply | [collector-projects.md](../architecture/collector-projects.md) | `CollectorResource`, `CollectorImportJournal`, `CollectorFormFileTransaction`, `CollectorProjectCompositionSync` |
| Backup перед удалением/перезаливкой | [collector-projects.md](../architecture/collector-projects.md) | `LayerBackupManager`, `GISApplication` |
| Изолированные и восстанавливаемые Collector workspaces | [collector-projects.md](../architecture/collector-projects.md) | `CollectorProjectRegistry`, `MainActivity` |
| Незавершённые Collector задачи | [collector.md](../roadmap/collector.md) | владельцы указаны в roadmap |
| Настройки, базовые слои, треки и immutable-local `.ngrc` | [settings-and-config.md](settings-and-config.md), [map-rendering.md](../architecture/map-rendering.md) | `Constants`, preferences XML, `TrackerService`, `TMSLayer`, `LocalTMSLayer` |
| Стабильность, lifecycle и диагностика | module packs и [change checklist](../guides/change-checklist.md) | `MainApplication`, `GISApplication`, сервисы и фрагменты |
| Upstream merges и конфликтные зоны | [upstream-fork-model.md](../architecture/upstream-fork-model.md), [upstream-sync.md](../runbooks/upstream-sync.md) | четыре git-репозитория |
| История upstream sync | [upstream history](../history/upstream/README.md) | исторические отчёты по циклам |
| Фото с координатной плашкой | [settings-and-config.md](settings-and-config.md) | map preferences и photo attachment flow |
| Self-hosted APK updates | [release-apk.md](../runbooks/release-apk.md) | updater/publisher, flavor metadata, подпись APK |

## Обязательные инварианты

- Локальные данные нельзя разрушать до успешного backup: `INV-BACKUP-BEFORE-DESTRUCTION`.
- Порядок модели слоёв и MapLibre style должен совпадать после cold start, hot add и reorder:
  `INV-LAYER-ORDER`, `INV-HOT-ADD-CONSISTENCY`.
- Start/end flags треков намеренно отключены: `INV-NO-TRACK-FLAGS`.
- Курсор текущего местоположения остаётся поверх треков и других объектов независимо от
  пользовательского порядка слоёв: `INV-LOCATION-CURSOR-TOP`.
- Composition sync управляет только слоями с `managed_by_project = true`; `manual_ngw` и legacy
  слои не удаляются автоматически.
- ContentProvider и сервис трека после смены Collector workspace работают только с текущей
  проектной базой; активная запись блокирует переключение: `INV-COLLECTOR-ISOLATION`.
- Неполный Collector snapshot не применяется; незавершённый импорт должен продолжаться без
  повторной загрузки уже исправных слоёв: `INV-COLLECTOR-RESUMABLE`.
- Формы заменяются транзакционно, а `.ngrc` сохраняется как локальная подложка:
  `INV-COLLECTOR-FORM-ATOMIC`, `INV-NGRC-PRESERVE`.
- Ошибка одного account не подавляет синхронизацию активного проекта:
  `INV-SYNC-ACCOUNT-ISOLATION`.
- Runtime, authenticator и sync adapter используют один account type каждого variant:
  `INV-NGW-ACCOUNT-IDENTITY`.
- `versionName` приложения и `maplib` выравниваются, а `versionCode` форка остаётся уникальным.
- Вынос использует ближайшую точку/границу, WGS84-расстояние, отображает accuracy,
  ведёт дистанционные звуковые зоны по GPS/mock fix, ориентирует стрелку по компасу телефона
  и сохраняет звук/частый GPS при выключенном экране через foreground service:
  `INV-STAKEOUT-GUIDANCE`.
- Ручные и импортированные локальные векторные слои редактируемы; GeoJSON
  принимает стандартные формы WGS 84/EPSG:4326, а KML/GPX разворачиваются в
  последовательность точек WGS 84. Выключенный редактируемый слой остаётся в
  выборе для нового объекта и автоматически включается после выбора:
  `INV-LOCAL-VECTOR-LAYERS`.

## Как поддерживать каталог

Не добавляйте сюда подробный журнал изменений. Новое поведение описывается в ближайшем
architecture/reference/runbook, точные зависимости — в `docs/registry/*.yaml`, пользовательские
изменения — в [`../../WHATS_NEW.md`](../../WHATS_NEW.md). В эту таблицу добавляется только новый
долгоживущий класс кастомизации. Одновременно необходимо проверить, изменилось ли внешнее отличие
от official, и актуализировать [official-differences.md](official-differences.md).
