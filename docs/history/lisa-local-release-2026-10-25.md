---
title: Разовый локальный Lisa Release APK — 25 октября 2026
type: reference
last_verified: 2026-10-25
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Локальная Release-сборка из текущих PR

Пользователь запросил APK релизной ЛИСЫ без публикации и явно разрешил
**разовое исключение для этого APK**: собрать из открытых PR без их слияния.
Это исключение не меняет обычный порядок dependency closure и не разрешает
публиковать данный артефакт. Версии не повышались. Desktop не изменялся.

## Состав и инвентаризация

После fetch проверены все открытые PR и опубликованные `origin/codex/*`,
не являющиеся предками целевой базовой ветки, во всех пяти owner repositories.
Нет параллельных опубликованных изменений, отсутствующих в текущем составе.
Все открытые PR ниже принадлежат `geoglyth` и остаются Draft.

| Владелец | Ревизия | PR/base | Включение |
|---|---|---|---|
| app | `77e12272d5aa36dcf83238172aae3e8ddd2e541b` | #78 / my-maplibre | exact APK source, clean tree |
| maplib | `ab59ad28ed0f74161e7e4146e506ae8d4ea9fa94` | #60 / master | exact app submodule pin, unmerged |
| maplibui | `ddc4dbdf58fbaf94b0cd88e2038119980cba75ca` | #44 / master | exact app submodule pin, unmerged |
| easypicker | `55aad0e0f92329ea4c273a83e541d170d24d7b57` | master, открытых PR нет | exact pin, merged |
| upload_mobile | `99e3cfde7841b749b83be6005842b96e7d140d00` | main, открытых PR нет | только инвентаризация, publisher не запускался |

APK включает стили точек/полигонов, живой образец, MapLibre 13.7.1 OpenGL и
управление треками/приоритет точек. Для публикации по-прежнему нужны #60 Merge
Commit → #44 Merge Commit → fetched merged pins → #78 Squash, повторная сверка
состава и обязательный Full/Check-Publication. В этой задаче merges не выполнялись.

## Проверки

Использованы установленный SDK/JBR, `Q:\Codex\Cache\gradle` и существующие
workspace build junctions. Сборка `tools/verify-apk-version-matrix.ps1 -LisaOnly`
в обычной production-конфигурации прошла; выполнена и отдельная readback-проверка
`-SkipBuild -LisaOnly`. Belka APK не собирался. Исходники/Gradle не менялись.

- Lisa Release: `com.nextgis.mobile.geonical`, **3.1.2.30 / 224**, label `NextGIS ЛИСА`.
- Lisa Debug: `com.nextgis.mobile.debug`, 3.1.2.23 / 218.
- Диагностические версии maplib: release 3.1.2.30, debug 3.1.2.23.
- Runtime содержит только `org.maplibre.gl:android-sdk-opengl:13.7.1`.
- `apksigner verify`: PASS, APK Signature Scheme v2; сертификат совпадает с
  существующей настроенной Lisa Release signing configuration.
- SHA-256 сертификата: `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`.
- `debuggable=false`, `FIELD_VERIFICATION=false`; source revision — точный
  `77e12272d5aa36dcf83238172aae3e8ddd2e541b`, без dirty-суффикса.
- Обычные diagnostics/usage build inputs присутствуют; их значения и signing
  secrets не выводились и в Git не добавлялись.
- Min SDK 26, target SDK 36; ABI arm64-v8a, armeabi-v7a, x86, x86_64.

Подтверждена актуальная зелёная GitHub reliability/docs проверка исходного
`77e1227`. [876 unit и 62 native проверки функционального кода](../reference/track-controls-verification-2026-10-25.md)
сохраняют свой исходный scope; новый Full не запускался и для этого локального
APK не заявляется. Установка на рабочее устройство не выполнялась.

## Артефакт

Постоянная локальная копия вне disposable scratch:
`Q:\Codex\Artifacts\lisa-mobile\2026-10-25\lisa-3.1.2.30-224-77e1227.apk`.
Рядом находится `.build.json` с составом и проверками. Копия сверена по hash
с непосредственным Gradle output.

- Размер: **70 471 737 bytes**, 67.21 MiB.
- SHA-256: `c354f7ece5555158343819ea713a9ec2a4e0263a72787595e7bdb24af145d8d3`.

Компактные логи/matrix/signature receipts находятся в
`Q:\Codex\Scratch\lisa-release-apk-20261025\evidence`. Служебный cleanup probe
удалён с проверкой отсутствия; SDK/Gradle caches и APK сохраняются. Первый внешний
PowerShell wrapper остался после завершения всех Gradle/metadata проверок;
остановлены только этот owned wrapper и его пустой console host. Повторная
readback version matrix завершилась с exit 0; общие Gradle процессы не останавливались.

Публикация, publisher dry-run, GitHub Release, загрузка APK и merges не выполнялись.
