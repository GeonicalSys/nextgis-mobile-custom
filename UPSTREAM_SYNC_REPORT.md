# Отчёт: синхронизация с официальным NextGIS Mobile (`nextgis_mobile_android`)

Дата интеграции: по состоянию репозитория после `git fetch` и merge.

## Официальный ref

| Параметр | Значение |
|----------|-----------|
| Remote | `https://github.com/nextgis/nextgis_mobile_android.git` (ранее `android_gisapp`) |
| Ветка | `upstream/master` |
| Вершина (после fetch) | `a2e1748` — *3.0.2 173 app version. fix for sync added* |
| `versionCode` в официальном `app/build.gradle` | **173** (сборка **174** из анонса в публичном `master` на момент merge **не найдена** — возможно внутренняя или ещё не запушена) |

## Инвентаризация расхождений (до merge)

### Родительский репозиторий (`nextgis-mobile-custom`)

- **Только upstream (не было у форка):** 2 коммита — `6a7988c` (3.0.2 Release), `a2e1748` (173 + sync fix).
- **Только форк:** 22 коммита поверх общего предка (flavors lisa/belka, CUSTOMIZATIONS, MapLibre/коллектор/обход и т.д.).
- **Симметричный diff** (`upstream/master...HEAD`): ~34 файла, +3257 / −254 строк (включая ссылки на сабмодули).

### Сабмодули (до merge)

| Модуль | Коммиты только upstream | Коммиты только форк (кратко) |
|--------|-------------------------|------------------------------|
| **maplib** | `55c40a1` — ISO dateformat для POST/PUT; `f2f61ba` — sync geometry / после загрузки в NGW | Много кастомов (LocationTrackFilter, layer fill, GeoJSON cache, …) |
| **maplibui** | `f1cf34ec` — fix TableView update (атрибуты после редактирования) | Walk GPS, Layer fill UI, стабильность, `app_name` для flavors |

## Выполненная интеграция (merge)

1. **maplib:** `git merge upstream/master` → коммит `519c68a` (разрешён конфликт в `SyncAdapter.java`: сохранены ранний `gisApp`, проверка `isLayerFillServiceBusy()`, лог `SSYNC` из upstream).
2. **maplibui:** `git merge upstream/master` → коммит `e35d7b5` (без конфликтов).
3. **Корень:** `git merge upstream/master` → коммит `35ece00`:
   - `app/build.gradle`: `versionCode` **173**, `versionName` **3.0.2.1** (номер сборки от upstream, патч `.1` форка сохранён); flavors **lisa/belka** и `base.archivesName` сохранены.
   - `OfflineSyncIntentService.java`: объединены try/catch + HyperLog (форк) и лог `onPerformSync call` (upstream).
   - Подтянуты автослиянием: `MainApplication.java`, `app/.../SyncAdapter.java` (приложение).

Указатели сабмодулей в корне: **maplib** → `519c68a…`, **maplibui** → `e35d7b5…`.

## Классификация изменений upstream (A / B / C)

### A — влито целиком с upstream

- ISO `dt_format` / параметры POST/PUT в **NGWUtil** (maplib).
- Исправления синхронизации геометрии / после upload (**NGWVectorLayer**, maplib).
- Обновление таблицы атрибутов (**AttributesActivity**, **TableViewAdapter**, maplibui) — по смыслу совпадает с пунктом анонса про атрибуты в таблице.
- Родительские правки релиза 3.0.2 / sync (**MainApplication**, **SyncAdapter** приложения) — через merge.

### B — ручное слияние (сделано)

- `maplib/.../ngw/SyncAdapter.java` — гард layer fill + логи upstream.
- `OfflineSyncIntentService.java` — структура try/catch форка + лог вызова sync upstream.
- `app/build.gradle` — версия 173 + сохранение flavors и схемы `archivesName`.

### C — сомнительное / на контроль

- **174 vs 173:** если появится официальный коммит с `versionCode` 174 — повторить `fetch` и при необходимости cherry-pick/merge.
- Регрессии в **мультиполигонах**, **маркере**, **GPS на главном**, **GPX**, **демо-проектах**, **Help** — в diff между `f2f61ba` и вашей предыдущей вершиной maplib часть могла уже быть в линии master; полный прогон сценариев из анонса всё равно желателен.
- **easypicker:** официальный корень указывает на `c5c42a3`; у форка другой SHA — отдельный merge не выполнялся (нет новых коммитов upstream-only в отчёте по easypicker в этом цикле).

## Рекомендации после merge

1. Сборка на машине с **JDK 17+**: `./gradlew :app:assembleLisaRelease` (и при необходимости `belkaRelease`). В среде агента сборка не прогонялась (Gradle 9 требует JVM 17+).
2. Ручной чек-лист по анонсу (таблица атрибутов, GPS, демо, маркер, мультиполигон, NGW strings, Help, GPX, авторизация, дубли запросов слоя).
3. Запушить **три** репозитория: `android_maplib`, `android_maplibui`, `nextgis-mobile-custom` (ветка `my-maplibre`).

## Команды push (после проверки)

```bash
cd maplib && git push origin my-maplibre
cd ../maplibui && git push origin my-maplibre
cd .. && git push origin my-maplibre
```

---

# Цикл 2026-05-15 — upstream 3.0.3 (`versionCode` 178)

Backup-теги перед merge: `pre-upstream-sync-2026-05-15-root|maplib|maplibui|easypicker` на
ветке `my-maplibre` каждого репо.

## Upstream tips

| Репозиторий | До merge (наш merge-base) | После fetch (upstream/master tip) |
|-------------|----------------------------|-----------------------------------|
| root `nextgis-mobile-custom` | `a2e1748` (3.0.2 / 173) | `2371e54` (some refactor поверх 3.0.3 / 178) |
| `maplib`    | `f2f61ba`                  | `d943c07` (changes for collector use) |
| `maplibui`  | `f1cf34ec`                 | `b225a8f8` (changes for collector use) |
| `easypicker`| `c5c42a3`                  | `c1bb1fc` (dependencies updated) |

## Что прилетело из upstream

### 1. Add Geometry By Walk — крупная фича апстрима
- **root**: коммиты `f95c07e` Add geometry ByWalk added (181 строк в `MapFragment.kt`,
  3 layout с FAB, +43 в `MainActivity.kt`, +34 в `MainApplication.java`); `b9dd9d6` 3.0.3
  Release дополняет walk + Refresh offline tiles.
- **maplib** `147262e`: `MapDrawable.java` +243 (`updateHistoryByWalkEnd`, walk restore поля),
  `MPLFeaturesUtils.java` +40 (`getFeatureFromNGFeature`, `getPolygonSeparFromNGFeaturePolygon`),
  все классы `MLP/*EditClass.java` — новый `addNewFlowPoint(LatLng, boolean)` и
  `addNewPolygonFrom3Points` для walk-старта.
- **maplibui** `584acce5` + `ab0d5341`: `EditLayerOverlay.java` (12+10),
  `WalkEditService.java` (+1), `ChooseLayerDialog(useCreatePoint, startFillByWalk)`
  signature, edit_*.xml (4 menu) восстанавливают `menu_edit_by_walk`.

### 2. Сollector + map hooks
- **maplib** `d943c07/db92ced/005c717`: `MapDrawable.java` +160 (collector path),
  `MaplibreMapInteraction.java` +2 (`setMapLayersLoaded`, `checkCreateIfNeed`).
- **maplibui** `b225a8f8/7530aad5`: `GISApplication.java`, `BottomToolbar.java`,
  `MapViewBase.java`, `LayersListAdapter.java` для collector wiring.

### 3. Same-name NGW vector layer fix + refresh offline после download
- **maplib** `6d461e1`: `IGISApplication` (+9 — `setLayerToRefresh / removeLayerToRefresh /
  getlayersToRefresh / checkTracksLayerExist`), `Connection.java` (+7 — `loadChildren(boolean
  skipSubLoad)`), `ResourceGroup.java`, `NGWUtil.java`, `Constants.java`
  (+`MESSAGE_INTENT_RELOAD`).
- **maplibui** `ab0d5341`: `SelectNGWResourceActivity.java` (+89), `NGWResourcesListAdapter.java`
  (+46), `TileDownloadService.java`, `TrackerService.java`, `color/drawable grey_button.xml`,
  `activity_resources.xml`, strings ru/en.

### 4. Q-tiles scheme
- **maplib** `5ce888a` + `383a9ab`: `AuthInterceptorNG.java` (+47), `MPLFeaturesUtils.java` URL
  rewrite `{q} -> quadtiles{z}/{x}/{y}`, `RemoteTMSLayer.java`.
- **maplibui** `0eee1970`: `CreateFromQMSLayerDialog.java` (+5).

### 5. «save zoom» в TMS JSON
- **maplib** `ec77b49`: `LocalTMSLayer.toJSON` теперь явно пишет `JSON_MAXLEVEL_KEY` /
  `JSON_MINLEVEL_KEY` из `mMaxZoom/mMinZoom` (стало совпадать с нашим §14 намерением; форк
  принял upstream-вариант).

### 6. Зависимости и AGP
- **root** `8310455` + **maplib** `d7373ed` + **maplibui** `49b03a51` + **easypicker** `c1bb1fc`:
  AGP 9.x, google-services 4.4.4, maplibre 13.0.2, okhttp 5.3.2, gson 2.13.2, proguard-android-optimize.

### 7. Refactor
- root `2371e54` (`MainApplication` -69 строк cleanup), maplibui `d8c14090` (GISApplication +116
  cleanup), maplib `18ac6b9`.

## Что НЕ было применено (и почему)

| Что upstream предлагал | Решение | Обоснование |
|------------------------|---------|-------------|
| `NGWResourceTypeCollector` (закомментировал) и `case "collector_project":` (закомментировал) в `maplib/Connection.java` | **Оставили fork** | Используется в 6 файлах (`ResourceGroup`, `SelectNGWResourceActivity`, `NGWResourcesListAdapter`, `LayerFactoryUI`) для §13 Collector Import. |
| `tracksFlagsSource` / `track-flag-source` / иконки старт/конец трека в `MapDrawable` (3 места: setStyle секция, MapLite, `reloadTrackListToMap`) | **Не применено** | §14 «Tracks: no start/end flag icons». Сохранён только `checkLayerVisibility(trackLayerFinal.getId())`. |
| `addGeometryByWalk` single-layer: `newGeometryByWalk` + `createPointFromOverlay(true)` + повторный `newGeometryByWalk` | **Оставили fork** | Наш pipeline (`applyInitialWalkGeometryAtStartLocation` + `prepareMaplibreSessionForNewWalkGeometry`) детерминирован, явно стартует MapLibre session и ставит правильный GPS-anchor вместо камеры. См. §17. |
| `resValue 'string', 'APP_NAME', 'NextGIS Mobile'` в `release` buildType `app/build.gradle` | **Не применено** | `APP_NAME` задаётся через `productFlavors lisa/belka` (§14), upstream-вариант переопределил бы flavor. |
| `versionCode 178` / `versionName '3.0.3'` напрямую | **Заменено** | §16 fork-схема: `versionCode 179`, `versionName 3.0.3.1`. |

## Разрешения конфликтов

| Файл | Природа | Решение | Ссылка |
|------|---------|---------|--------|
| `easypicker/build.gradle` | upstream сделал те же AGP 9.x правки, что наш `445e7b7` | auto-merge принял (3 закомментированных `support:*` deps удалены) | §1 |
| `maplib/build.gradle` | upstream убрал `versionName '3.0.2.3'`, мы держим schema | fork (поднят до `3.0.3.1` отдельным коммитом 574f8c0) | §16 |
| `maplib/datasource/ngw/Connection.java` | const `NGWResourceTypeCollector` закомментирована upstream'ом | fork | §13 |
| `maplib/api/IGISApplication.java` | imports + методы | union (наши §4/§13 + upstream `setLayerToRefresh/checkTracksLayerExist`) | §4, §13 |
| `maplib/map/LocalTMSLayer.java` | upstream явно пишет min/max zoom в JSON | upstream | §14 (совпадает по смыслу), добавили static imports `JSON_MAXLEVEL_KEY/MINLEVEL_KEY` |
| `maplib/map/MapDrawable.java` (10 hunks) | walk + collector + наша async loadLayers protection + null safety | hybrid: см. подробности в commit `307dc8c` | §3, §10, §14 |
| `maplibui/GISApplication.java` | imports | union | §13 |
| `maplibui/activity/SelectNGWResourceActivity.java` | type-mask с/без collector | fork (с `NGWResourceTypeCollector` в обеих ветках) | §13 |
| `maplibui/overlay/EditLayerOverlay.java` | indent + null check | hybrid: наш indent + upstream null-check (`mDrawItems == null || mSelectedItem == null`) | §10 |
| `build.gradle` (root) | google-services version | upstream (`4.4.4`) | — |
| `app/build.gradle` | versionCode / versionName / APP_NAME resValue | fork версия `179/3.0.3.1`, fork flavors | §14, §16 |
| `app/.../layout/fragment_map.xml` | FAB indent | fork (8-space, согласовано с окружением) | §2 |
| `app/.../fragment/MapFragment.kt` (7 hunks) | retry поля, walk-методы, dialog signature, дубликат when-case | hybrid: см. подробности в commit `d3e8291` | §2, §13 |

После merge — semantic fixups в MapFragment.kt:
- `setMapFragment` → `setMapContext` (3 места) и `mapFragment.get()` → `mapContext.get()` в
  maplib (`MapDrawable.java`, 9 вхождений) — upstream переименовал поле.
- Два вызова `startFeatureSelectionForEdit(...)` получили 6-й аргумент `isFillByWalking=true`.
- В MapFragment добавлены `override fun checkCreateIfNeed()` и `override fun
  setMapLayersLoaded()` как documented no-op (fork использует свой deferred reload).

Сборка после merge: `:app:assembleLisaRelease` + `:app:assembleBelkaRelease` зелёные, только
deprecation/unchecked notes от javac.

## Классификация (A / B / C)

- **A — влито целиком**: maplibre 13.0.2 / okhttp 5.3.2 / gson 2.13.2 / google-services 4.4.4;
  Q-tiles scheme; same-name NGW layer fix; refresh offline tiles; `loadChildren(boolean
  skipSubLoad)`; new interface methods `setLayerToRefresh / checkTracksLayerExist /
  setMapLayersLoaded / checkCreateIfNeed`; `MultiPolygonEditClass` walk additions;
  `MapDrawable.updateHistoryByWalkEnd`; `LocalTMSLayer` save zoom JSON.
- **B — ручное слияние**: `MapDrawable.java` (10 hunks), `MapFragment.kt` (7 hunks), walk
  reconciliation (§17), `IGISApplication.java` imports/methods, `Connection.java`
  collector const, `app/build.gradle` (version + APP_NAME), `LocalTMSLayer` static imports.
- **C — отвергнуто/задокументировано**: track start/end flag icons; upstream'овский empty
  `addGeometryByWalk` pipeline; upstream APP_NAME resValue в release buildType.

## Финальные SHA my-maplibre (после цикла)

| Репозиторий | SHA | Commit subject |
|-------------|-----|----------------|
| `easypicker` | `f98b10f` | Merge upstream/master @c1bb1fc into my-maplibre (dependencies updated) |
| `maplib` | `574f8c0` | chore(maplib): bump versionName to 3.0.3.1 (§16) on top of `307dc8c` merge |
| `maplibui` | `7dc86906` | Merge upstream/master @b225a8f8 into my-maplibre (maplibui) |
| `nextgis-mobile-custom` (root) | `41739dd` | chore: bump maplib submodule pointer (versionName 3.0.3.1) on top of `d3e8291` parent merge |

