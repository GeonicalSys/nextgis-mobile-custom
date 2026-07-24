---
title: Экосистема ЛИСА — desktop, плагины и Android
type: architecture
last_verified: 2026-07-23
related_code:
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - maplib/src/main/java/com/nextgis/maplib/datasource/ngw/ResourceGroup.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/NGWResourceImportHelper.java
---

# Экосистема ЛИСА — desktop, плагины и Android

## Владение

| Система | Канонический источник | Ответственность |
|---|---|---|
| Desktop profile | `Q:\standart_profiles` | launcher/bootstrap, QGIS runtime, бренды, роли, эталонный профиль и доставка |
| QGIS Plugins | `%APPDATA%\QGIS\QGIS3\profiles\develop\python\plugins` | исходники плагинов, desktop workflows, подготовка и публикация GIS-ресурсов |
| Android Mobile | этот workspace | полевой клиент, локальное GIS storage, NGW/Collector import и sync, APK release |

`profiles/*/python/plugins/` внутри `standart_profiles` — deployment mirrors, а
не четвёртый источник кода. Изменение плагина выполняется и проверяется в
профиле `develop`, затем отдельно доставляется в desktop profile.

## Поток

```mermaid
flowchart LR
    source["Plugins/develop<br/>исходники QGIS-плагинов"]
    profiles["standart_profiles<br/>launcher, roles, profile"]
    mirror["brand plugin mirror"]
    qgis["Desktop QGIS ЛИСА/БЕЛКА"]
    ngw["NextGIS Web<br/>resources, permissions, Collector"]
    package["Portable offline package<br/>MBTiles / ZIP / supported import"]
    mobile["Android ЛИСА/БЕЛКА<br/>offline workspace и sync"]
    release["apps-geonical.ru<br/>APK manifests и artifacts"]

    source -->|"явная синхронизация"| mirror
    profiles --> qgis
    mirror -->|"launch delivery"| qgis
    qgis -->|"stand_project / sync_ngw / nextgis_connect"| ngw
    ngw <-->|"NGW API, Collector import, feature sync"| mobile
    qgis -->|"qtiles_geonical, explicit export"| package
    package -->|"явный пользовательский import"| mobile
    release -->|"identity-checked update"| mobile
```

Desktop и Android не обмениваются рабочими файлами напрямую. Основная
интеграционная граница — NextGIS Web; offline package является осознанным
переносимым артефактом, а не ссылкой APK на desktop workspace.

## Межпроектные контракты

### NGW/Collector resource

QGIS-инструменты могут создавать, оформлять и синхронизировать ресурсы,
которые затем открывает Android. Совместимыми должны оставаться:

- server URL и стабильная resource identity;
- тип ресурса и дерево ссылок Collector;
- schema полей, права `data.read`/`data.write` и sync direction;
- стили, формы, composition и lifecycle управляемых слоёв;
- различие между server-managed ресурсом и локальной immutable `.ngrc`-подложкой.

Изменение publisher-side логики в `stand_project`, `sync_ngw`,
или `nextgis_connect` требует consumer-smoke в Android, если меняется этот
договор. Внутренний refactoring плагина без изменения ресурса
Android-документацию не затрагивает.

### Offline basemap artifact

`qtiles_geonical` создаёт MBTiles/ZIP на desktop и не публикует результат в NGW
автоматически. Передача в Android — отдельное явное действие пользователя или
release-процедуры. Producer и consumer должны согласовать поддерживаемый формат,
CRS, zoom range и lifecycle; путь к файлу в desktop workspace частью контракта
не является. Нельзя подменять smoke фактического Android import утверждением,
что QGIS успешно создал файл.

### Идентичность и секреты

Desktop credentials, `variables.py`, QGIS auth DB, Android AccountManager и
signing credentials принадлежат разным trust boundaries. В документации
фиксируются имена ключей, server/resource identity и способ проверки, но не
секретные значения. Наличие рабочего desktop login не доказывает, что Android
account type, permission или token настроены правильно.

### Независимый выпуск

`standart_profiles` доставляет desktop profile и плагины; он не доставляет APK.
Мобильный release публикуется независимо и проверяет flavor, application ID,
version, artifact hash и signing certificate. Совместимость с серверными
ресурсами подтверждается отдельным end-to-end smoke, а не совпадением названия
версии desktop и Android.

## Маршрутизация задачи

| Изменение | Проект-владелец | Дополнительный контекст |
|---|---|---|
| launcher, QGIS runtime, role, brand profile | `standart_profiles` | его `AGENTS.md` и `docs/START-HERE.md` |
| исходник QGIS-плагина или WebGIS publisher | Plugins/develop | root `AGENTS.md`, `geonical-docs/START-HERE.md`, pack плагина |
| Android import, Collector, sync, storage, updater | этот workspace | root/module `AGENTS.md`, Android registries |
| общий NGW resource contract | publisher и consumer | docs/registry обоих владельцев + end-to-end smoke |
| offline basemap для Android | QGIS plugin + Android importer | qtiles pack, app rendering/storage docs и import smoke |

На текущей машине соседние входы находятся в
`Q:\standart_profiles\docs\START-HERE.md` и
`%APPDATA%\QGIS\QGIS3\profiles\develop\python\plugins\geonical-docs\START-HERE.md`.
Канонические web-входы:
[standart_profiles](https://github.com/GeonicalSystem/standart_profiles/blob/main/docs/START-HERE.md)
и
[geonical-docs](https://github.com/GeonicalSystem/geonical-docs/blob/main/START-HERE.md).

Точные IDs и проверяемые пути: [ecosystem.yaml](../registry/ecosystem.yaml).

## Правило изменения

1. Определить publisher, consumer и фактически изменяемый контракт.
2. Читать инструкции и registry каждого затронутого владельца.
3. Менять код только в его каноническом source workspace.
4. Обновить app-side `ecosystem.yaml` и основной документ владельца, если
   изменилась внешняя семантика.
5. Выполнить локальные проверки обоих проектов и end-to-end NGW/Collector smoke
   либо явно зафиксировать, что он не выполнен.

Не делать вывод о совместимости по plugin mirror, совпадающему имени слоя,
успешному desktop login или одной только Android-сборке.
