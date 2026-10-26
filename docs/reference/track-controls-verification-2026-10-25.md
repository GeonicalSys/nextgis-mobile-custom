---
title: Проверка управления треками — 25 октября 2026
type: reference
last_verified: 2026-10-25
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SharedTracksTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LargeTextLayoutTest.java
  - tools/mobile-test-plan.json
  - tools/run_mobile_tests.py
---

# Проверка управления треками

Реализованы пункты 1–4 и 6: переименование, сохранённые яркие цвета новых
треков, линия со свечением, выбор тапом и нижние действия, Point/MultiPoint
поверх треков и прочих данных. Серверный формат/метки пункта 5 исключены.
Desktop по последнему указанию пользователя не изменялся. Загрузка GeoJSON,
перестройка тайлов, start/end flags и версия приложения не переделывались.
MapLibre остаётся 13.7.1 OpenGL.

## Проверенный состав

| Владелец | Ревизия кода | Draft PR |
|---|---|---|
| maplib | `ab59ad28ed0f74161e7e4146e506ae8d4ea9fa94` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60) |
| maplibui | `ddc4dbdf58fbaf94b0cd88e2038119980cba75ca` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) |
| app | `d19fd11bd12e009edb66bbf82737093bb5cd0781` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) |

App pins совпадают с указанными библиотеками. Проверена ancestry прежних
`ee59471` / `d8bcf9fd` / `7e44811`: предыдущие изменения стилей и SDK включены.
Последующая публикация этой квитанции меняет только документацию.

Fingerprint всех проверенных исходников/тестов/скриптов, без Markdown/docs:
`6dfd0485979c30ba7b800511229b45faeb49c233455dd8194128b0a0e11bb234`.

## Результаты

| Проверка | Результат |
|---|---|
| maplib JVM/Robolectric | 603 PASS, 0 failures/errors/skips |
| maplibui JVM/Robolectric | 93 PASS, 0 failures/errors/skips |
| app JVM/Robolectric | 180 PASS, 0 failures/errors/skips |
| maplibui assembleDebug | PASS |
| Lisa Release / Belka Release Kotlin consumers | обе компиляции PASS; production APK для выпуска не собирался |
| docs validator + docs tests | PASS, 24 tests |
| mobile runner/evidence unit tests | PASS, 26 tests |
| update-reports перед commit/push | PASS во всех трёх репозиториях |
| Related native | 62 различных случая PASS, пропусков нет |

Gradle 9.3.1; JBR OpenJDK 21.0.9 (`21.0.9+-14787801-b1163.94`). Для текущего
процесса сборки отключено старое Kotlin incremental-состояние через
`ORG_GRADLE_PROJECT_kotlin.incremental=false`; исходники ради этого не менялись.
Verification APK и его tests собраны с `fieldVerificationChecks=true` и
`ciReliabilityChecks=true`.

Native выполнен на существующем `Medium_Phone_API_36.0` в read-only AVD,
Android 16/API 36, `ranchu`, x86_64,
`google/sdk_gphone64_x86_64/emu64xa:16/BP22.250325.006/13344233:user/release-keys`.
Использованы отдельные `com.nextgis.mobile.verification.debug` и `.test`.

| Native-класс | PASS |
|---|---:|
| LargeTextLayoutTest | 2 |
| MapEditingToolsTest | 3 |
| SharedTracksTest | 7 |
| TrackPersistenceTest | 6 |
| TrackPowerWarningTest | 2 |
| TrackRecordingModesTest | 4 |
| TrackUploadTest | 9 |
| TrackerStopRecoveryTest | 1 |
| WalkCheckpointTest | 1 |
| FireflyStyleEditorTest | 1 |
| FireflyTileFallbackTest | 1 |
| PolygonStyleEditorTest | 3 |
| PolygonStyleRenderingTest | 2 |
| PolygonStyleTileFallbackTest | 1 |
| TrackControlsTest | 4 |
| VectorStyleRenderingTest | 16 |

Треки проходят настоящий provider → виджеты/жесты → MapLibre source/GPU:
Save/Cancel и пустое имя; цвет после повторного открытия; скрытие/возврат после
recreate; совпавшие треки; подтверждение удаления; запрет перехвата азимута;
сегменты; сохранность другой записи; отказ SQLite при удалении активного ID и
успешный retry. Скрытие активного трека оставляет recorder работающим.

Пиксели подтверждают цветное ядро и внешнее свечение, Point/MultiPoint над
линиями; native-стек проверяется после lite/full/reorder. У точек сохраняются
подписи и взаимный порядок. Панель проходит 48 русских сочетаний темы,
fontScale 1/1.3/2 и viewport 320/280 dp в обеих ориентациях. Три track-диалога
проверены в 24 профилях настоящего окна — 72 открытия и нажатия Cancel.
Системные font/density настройки физического устройства не менялись.

Редакторы/стили проверяют live preview, категории/Other/NULL, подписи, opacity,
геометрии, узоры, dash, picking и очистку. Три проверки бизнес-редактора покрывают
копирование с сохранением полей, сдвиг/поворот с Undo и read-only действия.

## Найденная и исправленная проблема редактора

На эмуляторе profile font=2, 500×280 dp оставлял прокрутке 199 px при кнопке
цвета 207 px. Закреплённый альбомный образец уменьшен с 48 до 40 dp;
портретный остаётся 100 dp. Повторная матрица полигонального редактора прошла
все 72 профиля с прежним строгим сравнением полной видимой рамки и настоящими
Save/Cancel. Допуск проверки не увеличивался. Ошибка теперь сохраняет точные
profile/scroll/preview bounds; адресный запуск — Related `polygon-style-ui`.

## Воспроизведение и свидетельства

В обычном окружении проекта, с TEMP/TMP/TMPDIR на Q и установленным SDK/JBR:

Ниже фактический каталог выполненного прогона; для повтора следует выбрать
новый каталог свидетельств, сохранив принятый результат.

```powershell
py -3.13 -B tools/run_mobile_tests.py --scope related --area track-controls --area tracks --area feature-editing --area styles --output Q:\Codex\Scratch\track-controls-20261025\evidence\accepted-native
```

Unit/consumer-команда: Gradle с `-PciReliabilityChecks=true`, задачами
`:maplib:testDebugUnitTest :maplibui:testDebugUnitTest :app:testLisaDebugUnitTest
:maplibui:assembleDebug :app:compileLisaReleaseKotlin :app:compileBelkaReleaseKotlin`.

Компактные локальные свидетельства:
`Q:\Codex\Scratch\track-controls-20261025\evidence` — финальный `accepted-native/result.json`,
логи каждого случая, сохранённые разрешённые QA PNG стилей с SHA256, unit/docs
логи, delivery matrix и диагностика первоначальных отказов. Временный AVD
остановлен runner-ом; `adb devices` после прогона пуст. Рабочая LISA на телефоне
не устанавливалась и не очищалась. Постоянные копии SDK/AVD/БД не создавались:
использованы установленные SDK, исходный AVD и существующие build junctions.

## Ограничения и доставка

Это Related, не Full/Check-Publication. Подключённый пользователем планшет ADB
не увидел; новая функциональность физически не проверена. Native Android 9/11,
полный MapEditingToolsTest и отдельный LocalVectorTileLabelTest не запускались.
FPS/холодный старт не измерялись. Архитектура подготовки данных остаётся прежней;
количество общих track style layers не растёт с числом сеансов.

Точки в серверном растровом изображении отдельно поднять невозможно; правило
относится к настоящим Point/MultiPoint VectorLayer, в том числе их native
дочерним слоям. Глобальная видимость «Мои треки» остаётся самостоятельной.

Библиотеки ещё не merged. Порядок будущей интеграции: #60 Merge Commit → #44
Merge Commit → pins удалённых merge commits в app → #78 Squash. Merge/публикация
не запрошены. [Предыдущая квитанция SDK](maplibre-13-7-1-verification-2026-10-24.md)
содержит отдельную version-matrix/phone проверку обновления; её результаты не
подменяют текущую native-проверку треков. Автоматические GitHub checks доступны
в PR и отслеживаются отдельно от этих локальных результатов.
