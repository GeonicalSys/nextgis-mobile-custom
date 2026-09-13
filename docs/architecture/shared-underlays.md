---
title: Общее хранилище подложек и обновление Debug перед переносом
type: architecture
last_verified: 2026-09-13
related_code:
  - maplib/src/main/java/com/nextgis/maplib/util/SharedUnderlayCatalog.java
  - maplib/src/main/java/com/nextgis/maplib/util/SharedUnderlayStore.java
  - maplib/src/main/java/com/nextgis/maplib/util/RasterMbtilesWriter.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SharedUnderlayProjects.java
  - app/src/main/java/com/nextgis/mobile/activity/UnderlayCatalogActivity.kt
  - app/src/main/java/com/nextgis/mobile/util/DebugCompanionInstaller.java
---

# Хранилище подложек

Хранилище принадлежит одному Android application ID. Оно находится в
`externalFilesDir("map")/shared_underlays`; при отсутствии external files —
`filesDir/map/shared_underlays`. Это сосед `collector_projects`, а не его часть.
Lisa Debug, Geonical и Belka имеют отдельные каталоги. Desktop-форматы и
публикация APK не меняются; передача между пакетами остаётся явной операцией.

`catalog.json` содержит schema 1 и массив assets. Ассет имеет UUID, имя, kind,
SHA-256, размер, время создания и снимок raster-конфигурации. Его manifest и
payload лежат в `<UUID>/manifest.json` и `<UUID>/payload/`. Проектный `layer_*`
содержит конфигурацию с `shared_underlay_id`; имя слоя, видимость и положение
в стеке принадлежат проекту. Переименование в хранилище не переименовывает
существующие проектные слои.

Новый NGRc проходит два потоковых чтения. Первое находит `config.json` даже
в конце ZIP и вычисляет SHA-256 **всего архива**, включая central directory и
comment. При совпадении с готовым ассетом конвертация не нужна. Второе чтение
передаёт `z/x/y.tile` непосредственно в SQLite writer, максимум один тайл в
памяти (16 MiB). Нет распакованного дерева и промежуточного полного ZIP.
Повторная проверка хеша отвергает изменившийся между чтениями источник.

Схема берётся только из `tms_type`: OSM преобразуется в TMS один раз, TMS
остаётся без переворота. Поддержаны PNG/JPEG/WebP, один encoding на базу;
смешанный encoding, неверные координаты и пустой набор отвергаются. Writer
вычисляет bounds/minzoom/maxzoom, слой сохраняет прежний запас видимости ±2
уровня и provenance NGRc. Перед публикацией выполняются `MbTilesInfo.inspect`,
fsync и rename staging. Новый MBTiles проверяется и сохраняется без изменения
содержимого, его идентичность — SHA-256 базы. Новый пункт импорта поддерживает
также один MBTiles внутри ZIP, без временного распакованного файла.

Обычный ZIP-TMS, онлайн-OSM/QMS/NGW и их кэши не входят в каталог. Старый
универсальный импорт локальных файлов сохранён. Новые пункты «Новая подложка
из файла» и «Подложка из хранилища» добавляют подложку сразу над OSM; повторное
подключение того же ассета в один проект не создаёт второй слой.

## Перенос существующих подложек

Перед открытием локального raster-слоя eligible NGRc/MBTiles переносится
переименованием всей папки слоя в payload. На старом месте записывается тонкая
конфигурация. Тайлы не копируются и не перекодируются. Проверка eligibility
требует локальный TMS и NGRc provenance либо существующий MBTiles; простой
ZIP-TMS не становится NGRc по наличию дерева тайлов.

Журнал `moving` записывается до rename. После сбоя startup recovery завершает
операцию до загрузки карты; checkpoints покрыты тестами после записи журнала,
после rename и после записи ссылки. Если создать тонкую конфигурацию не удалось,
перенесённая папка по возможности возвращается на место. При невозможности
rename между носителями сохраняется legacy-путь; ошибка фиксируется в журнале,
перенос повторяется позже.

Отдельный отложенный проход с project-operation lease обрабатывает закрытые
проекты и legacy standalone map. Размеры и неизвестные хеши старых MBTiles
вычисляются в рабочем потоке. До этого размер помечен как неизвестный. Готовый
проход отмечен `shared_underlay_migration.complete_v1`; открытие старого слоя
и экран каталога остаются идемпотентными независимо от маркера.

Известные NGRc хеши объединяются сразу. Старые MBTiles с одинаковым вычисленным
хешем объединяются через redirect journal и смену ссылок во всех картах. Для
NGRc без исходного archive SHA создаётся отдельный ассет. Оригинальные дубликаты
удаляются рабочим потоком только после durable links. Брошенные import stages
удаляются при старте до появления новых импортов. Сбой публикации между rename
и записью index восстанавливается из per-asset manifest.

MapLibre и Canvas разрешают путь через shared ID, без ключа читают legacy
`mPath`. Debug exporter также разрешает payload через каталог. Debug importer
пишет поток сразу в каталог Geonical, затем подключает ассет к выбранному проекту;
source-key alias обеспечивает повторное использование при повторе переноса.

## Удаление

«Убрать из проекта» удаляет только ссылку и тонкую папку слоя; undo остаётся
штатным. Удаление проекта сначала защищает ещё не перенесённые подложки. Reset
настроек и смена пути карты не должны перемещать либо удалять общий каталог.

Удаление payload доступно только в настройках хранилища. Подтверждение перечисляет
имена всех использующих проектов. `deleting` фиксирует уже подтверждённое намерение;
затем активная карта снимает слои и сохраняется, закрытые карты редактируются
через JSON без создания глобального MapBase. Порядок остальных слоёв, неизвестные
поля и вложенные группы сохраняются. Только после отсутствия всех ссылок удаляется
payload. При прерывании запись остаётся в каталоге и позволяет повторить удаление.
Синхронизация, смена проекта и обход исключаются project-operation lease.

# Обновление Debug перед переносом

Только production Geonical предлагает обновить установленный доверенный Debug,
если у него ещё нет export Activity. При новом создании MainActivity предложение
повторяется; «Не сейчас» не сохраняется. Оно ждёт освобождения окна после crash
recovery. Pending self-update имеет приоритет. Показ предложения отменяет только
автоматическую проверку обновления Geonical в этом запуске; ручная доступна.

В настройках проекта кнопка переноса видна при доверенном установленном Debug
даже без exporter. После его установки этот путь возвращается к прежнему
подтверждению переноса, только если UID активного проекта совпал. Из стартового
предложения показывается сообщение о доступности переноса в настройках.

`DebugCompanionInstaller` использует отдельные preferences
`debug_companion_install` и cache `debug-companion/`. Состояния ready, permission
и installing сохраняют проверенный manifest и исходный project UID. После
выдачи unknown-sources permission проверенный cached APK валидируется снова и
передаётся штатному Android installer без повторной ручной проверки обновлений.
Смена процесса не подменяет project UID. Отмена установки завершает продолжение;
повторная попытка доступна пользователю. Обычный self-updater не принимает чужой
application ID; общими остаются только чтение versionCode и certificate digests.

Manifest: HTTPS `/lisa-mobile/debug/manifest.json`, schema 1, flavor/channel debug,
пакет `com.nextgis.mobile.debug`. APK допускается только из
`/lisa-mobile/debug/releases/<versionCode>/<filename>.apk`, без redirects,
query/fragment, path traversal или percent-encoded путей. Проверяются code/name,
SDK, размер, SHA-256, pinned Debug certificate и **текущая подпись установленного
Debug**, manifest flavor APK, а также наличие enabled/exported export Activity.
Archive signature flags сохраняют Android 9/10 legacy fallback.

На 2026-09-13 публичный debug manifest отдаёт `3.1.2.17/212`; публикация новой
версии с exporter является отдельным шагом после интеграции. Эта задача не
публикует APK и не повышает версии. Неподходящий серверный APK не устанавливается.

## Проверки и доставка

JVM-тесты покрывают полный archive hash, позднюю конфигурацию, схему координат,
изменение источника, cancellation, traversal, dedup, checkpoints миграции,
закрытые вложенные workspace и companion identity/URL/signature/version rules.
Android SQLite writer, actual installer и визуальный hot-add требуют device smoke.
На момент проверки 2026-09-13 `adb devices -l` не обнаружил устройств: GUI,
permission/install round trip, холодный запуск после move и совпадение тайлов
на телефоне пока не проверены.

Ветки `codex/shared-underlay-catalog` продолжают сохранённые изменения GPS/обхода:
maplib #21, maplibui #13, app #26. Новые Draft PR основываются на
`codex/skip-invalid-ngw-geometries`. Перед merge родительского PR нужно retarget
его child PR; удаление parent head иначе закроет child. Порядок интеграции:
maplib Merge Commit → maplibui Merge Commit → pin удалённых library merge commits
в app → app Squash Merge. Publisher не изменён. Пока predecessors открыты,
полученные APK — QA-артефакты, не завершённый выпуск.
