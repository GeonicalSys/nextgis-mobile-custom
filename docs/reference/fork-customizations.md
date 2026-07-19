---
title: Каталог кастомизаций форка
type: reference
last_verified: 2026-07-19
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

| Область | Где описано | Основные владельцы |
|---|---|---|
| Сборка, AGP, flavors и версии | [build-matrix.md](build-matrix.md), [release-apk.md](../runbooks/release-apk.md) | `app/build.gradle`, `maplib/build.gradle` |
| Walk-by-geometry | [map-rendering.md](../architecture/map-rendering.md) | `MapFragment`, `MapDrawable`, `EditLayerOverlay`, `WalkEditService` |
| MapLibre rendering, hot reload и порядок слоёв | [map-rendering.md](../architecture/map-rendering.md) | `MapDrawable`, `MPLFeaturesUtils`, `VectorLayerRenderCache` |
| Производительность карты и локальные vector tiles | [map-performance.md](../architecture/map-performance.md) | `VectorLayer`, `LocalVectorTileProvider`, `LocalVectorTileServer` |
| NGW sync, layer fill и schema rebuild | [collector-projects.md](../architecture/collector-projects.md), [change-impact.yaml](../registry/change-impact.yaml) | `NGWVectorLayer`, `SyncAdapter`, `LayerFillService`, `GISApplication` |
| NGW resource UI и batch import | [collector-project-setup.md](../runbooks/collector-project-setup.md) | `SelectNGWResourceActivity`, `NGWResourcesListAdapter`, `LayerFillProgressDialogFragment` |
| Config из NGW description и `SYNC_NONE` | [settings-and-config.md](settings-and-config.md), [collector-projects.md](../architecture/collector-projects.md) | `NgwLayerConfigAdapter`, sync classes |
| Collector metadata, district filter и composition apply | [collector-projects.md](../architecture/collector-projects.md) | `CollectorProjectMetadata`, `CollectorProjectCompositionSync` |
| Backup перед удалением/перезаливкой | [collector-projects.md](../architecture/collector-projects.md) | `LayerBackupManager`, `GISApplication` |
| Изолированные Collector workspaces | [collector-projects.md](../architecture/collector-projects.md) | `CollectorProjectRegistry`, `MainActivity` |
| Незавершённые Collector задачи | [collector.md](../roadmap/collector.md) | владельцы указаны в roadmap |
| Настройки, базовые слои, треки и `.ngrc` | [settings-and-config.md](settings-and-config.md), [map-rendering.md](../architecture/map-rendering.md) | `Constants`, preferences XML, `TrackerService`, `LocalTMSLayer` |
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
- Composition sync управляет только слоями с `managed_by_project = true`; `manual_ngw` и legacy
  слои не удаляются автоматически.
- `versionName` приложения и `maplib` выравниваются, а `versionCode` форка остаётся уникальным.

## Как поддерживать каталог

Не добавляйте сюда подробный журнал изменений. Новое поведение описывается в ближайшем
architecture/reference/runbook, точные зависимости — в `docs/registry/*.yaml`, пользовательские
изменения — в [`../../WHATS_NEW.md`](../../WHATS_NEW.md). В эту таблицу добавляется только новый
долгоживущий класс кастомизации.
