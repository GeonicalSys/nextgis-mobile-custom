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
