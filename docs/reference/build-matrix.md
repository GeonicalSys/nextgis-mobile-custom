---
title: Матрица сборки и версий
type: reference
last_verified: 2026-10-10
related_code:
  - build.gradle
  - gradle/wrapper/gradle-wrapper.properties
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Матрица сборки и версий

App lifecycle unit tests используют Robolectric `4.16.1` (как maplib) и Android
resources на API 26/36. Это test-only dependency; runtime APK зависимости и
версии не меняются. Отсутствующие SHA-256 test artifacts добавляются в
`gradle/verification-metadata.xml`, dependency verification остаётся включённой.

Диагностика использует Sentry Android `8.37.1` и WorkManager `2.11.2`, одинаково
в Debug/Lisa/Belka. `diagnosticDeliveryChecks=true` меняет только instrumentation
runner test APK для изолированной проверки доставки; версии, application/account
IDs, signing и production APK от этого флага не меняются.
`BuildConfig.SOURCE_REVISION` фиксирует Git HEAD и dirty marker для багрепортов;
изменение этого поля не повышает versionName/versionCode.

Production остаётся `3.1.2.27` / `221`, Debug — `3.1.2.23` / `218`.
Закрытие текущей задачи: Merge Commit maplib #45 → maplibui #33 → app #53
с fetched remote library pins, затем Squash app и APK version matrix.
Запрошен только локальный Lisa Release APK, без публикации и повышения версии.
Состав: [матрица закрытия](../history/feature-type-delivery-2026-10-09.md).
Исправление пустых списков поверх этого выпуска: maplibui #34 → app #54;
[матрица исправления](../history/feature-type-empty-lists-2026-10-09.md).
Предыдущая цепочка форм/sync сохранена в [исторической матрице](../history/mobile-form-sync-delivery-2026-10-08.md).
Предыдущий debug-only цикл сохранён в [исторической матрице](debug-3.1.2.23-delivery.md).

| Компонент | Текущее значение |
|---|---|
| Gradle wrapper | `9.3.1` |
| Android Gradle Plugin | `9.1.0` |
| Kotlin plugin | `2.2.10` |
| compileSdk | `36` |
| targetSdk | `36` |
| minSdk | `26` |
| App versionCode (release) | `221` |
| App versionName (release) | `3.1.2.27` |
| App versionCode (debug) | `218` |
| App versionName (debug) | `3.1.2.23` |
| maplib VERSION_NAME (release) | `3.1.2.27` |
| maplib VERSION_NAME (debug) | `3.1.2.23` |
| MapLibre Android SDK | `13.0.2`, `android-sdk-opengl` (OpenGL ES) |
| JTS Core | `1.20.0` |
| OkHttp | `5.3.2` |
| Release application/account | `com.nextgis.mobile.geonical` / `com.nextgis.account.geonical` |
| Debug application/account | `com.nextgis.mobile.debug` / `com.nextgis.account.debug` |
| Lisa launcher resource | `@drawable/ic_launcher_lisa` |
| Belka launcher resource | `@drawable/ic_launcher_belka` |

Значения фиксируют проверенное состояние на `last_verified`, но код остаётся
источником истины. Production version задаётся `defaultConfig`, debug app
override — `androidComponents.onVariants`, debug maplib version — отдельным
`buildConfigField`. Application version DSL внутри `buildTypes` для AGP 9.1.0
запрещён.

`org.locationtech.jts:jts-core:1.20.0` — общая runtime-зависимость `maplib`,
используемая при сохранении для проверки и исправления топологии только
`GTMultiPolygon`. Она одинакова для Lisa/Belka и debug/release и не меняет
variant identity.

MapLibre `13.0.2` подключается во всех трёх consuming-модулях через
`org.maplibre.gl:android-sdk-opengl`. Generic `android-sdk` начиная с MapLibre
13 использует Vulkan и не входит в production runtime: на устройствах без
совместимого Vulkan-драйвера он завершает процесс при открытии карты.

Все API26–36 используют `SurfaceView` с восстановлением EGL и коротким render
burst после возвращения. API26–28 не включают tile prefetch. Постоянные ограничения
5/30FPS и прежняя TextureView ветка удалены; backend остаётся OpenGL.

## Основные задачи

```powershell
.\gradlew.bat :maplib:testDebugUnitTest
.\gradlew.bat :maplibui:assembleDebug
.\gradlew.bat :app:assembleLisaRelease
.\gradlew.bat :app:assembleBelkaRelease
```

Любое изменение app/maplib version проверяется одной матрицей из корня:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-version-matrix.ps1
```

Она собирает все три поддерживаемых APK и проверяет реальные package/version
через `aapt`, а также debug/release `maplib.BuildConfig.VERSION_NAME`.
При изменении flavor resources дополнительно собираются обе release-flavors и
вручную проверяются launcher, intro и about каждого бренда.

## Проверки надёжности и воспроизводимость

Дополнение 2026-10-08: `StakeoutForegroundServiceTest` проверяет location-only
тип службы, работу без Bluetooth, фон, освобождение GPS, отказ запуска и
устаревшие команды. На API36 локально прошли пять сценариев с разрешённым GPS;
сценарий отказа GPS в этом проходе пропущен по предусловию и отдельно успешно
выполнен после фактического отзыва coarse/fine location. CI повторяет этот
отдельный проход и проверяет instrumentation output, поскольку код возврата
`adb` сам по себе не доказывает успех. Разрешения после проверки восстанавливаются.
После connected suite UTP удаляет app/test packages. Перед отдельным проходом
workflow повторно устанавливает оба APK и возвращает доступ к тестовому
хранилищу и уведомлениям; затем отзывает fine/coarse и проверяет результат.
Без повторной установки весь workflow падал с package not found при успешно
пройденных 87 native tests (один ожидаемый permission skip).
Проверка на физическом устройстве Android15 остаётся ручной.

Дополнение 2026-10-04: Pigo BLE/GATT/session regressions проверены на API 26/36.
Локально прошли 574 unit tests (451 maplib, 82 maplibui, 41 app), 28 native
API36/WHPX checks и Lisa/Belka release Kotlin/Java source compilation. То же
содержимое прошло Linux CI f580bd8: 574 units, 28 native checks, без пропусков
или ошибок. После library merges подтверждено совпадение Git trees с этими
протестированными source heads. Это не release APK matrix; её запуск следует
за app merge и повторной проверкой всей цепочки.

JDK21 обязателен. Gradle wrapper distributionSha256Sum фиксирует проверенный
дистрибутив9.3.1. Hyperlog master-SNAPSHOT заменён **теми же байтами AAR** из
`com.github.barsrb:hyperlog-android:master-0.0.10-g855ebf8-12@aar` (JitPack build
commit855ebf83b373cade434939f5279bf6b138f753af). Root exclusiveContent repository
использует artifact-only metadata: старый POM сам объявляет master-SNAPSHOT.
SHA256 AAR: `6d05f3da6b5d6bd05bb14c416c6d68ea47f2115c39b31121409cf96adc20c996`.
Standalone consumer библиотек обязан повторить эту artifact-only настройку.

`gradle/verification-metadata.xml` фиксирует SHA256 разрешённых Gradle artifacts
и metadata. Первичный набор создан из проверяемой локальной сборки: это baseline
первого доверия, не независимая проверка происхождения каждого артефакта.
Linux aapt2 classifier дополнительно сверен по artifact и опубликованному
checksum Google Maven для CI. Чистый Linux build также потребовал parent POM
Guava33.4.8-jre: он добавлен после сверки с опубликованным checksum Maven Central;
бинарные зависимости и версии не менялись. Robolectric SDK downloads имеют
отдельный механизм и не охватываются Gradle XML.
Обычная проверка выполняется без --write-verification-metadata; новые checksum
добавляются только после проверки конкретного источника/изменения зависимости.

`.github/workflows/android-reliability.yml` запускает unit suites трёх owners,
maplibui/debug APK, обе release source sets и native API36 suite. CI использует
`-PciReliabilityChecks=true`: только при отсутствующем sentry.properties допускает
пустой DSN для тестов. Обычные builds сохраняют обязательный private config;
секреты не публикуются. SDK setup явно запрашивает platform-tools, не удалённый
legacy tools package. PR запускает regression один раз, push — только на
my-maplibre. Удалён устаревший MaxPermSize JVM flag, мешавший чистому JDK21
запустить Gradle daemon; local user properties ранее скрывали эту ошибку.
Workflow не собирает/не публикует release APK.
Перед native UI suite выдаются location и GET_ACCOUNTS: обязательное стартовое
окно доступа к учётным записям не должно перекрывать сценарии карты. Новый
`BackgroundLocationWarningTest` проверяет реальные Battery Saver/background/Doze
настройки на API36 без записи и после screen-off/on; ограничения меняются после
Pause, как при переходе пользователя в системные настройки. Диагностические
native проверки SDK HTTP URL/query/headers и limiter выполняются отдельно с
opt-in runner и явным loopback DSN. См.
[проверку 10 октября](../history/mobile-diagnostics-verification-2026-10-10.md).

Library dependency closure завершена; приложение закрепляет оба remote merge
commits. На Windows и в чистой Linux CI выполнены обе release Kotlin/Java
compilation, debug APK,574 units и28 native API36 fault/UI checks. Release
APK проверяются отдельно после app merge; точный успешный run, delivery matrix
и runtime versions в [отчёте](mobile-reliability-audit.md).

## Native project-script runtime

Добавлены NDK `28.2.13676358` и CMake `3.22.1` для QuickJS `2026-06-04`.
CI устанавливает эти pinned SDK packages. Сборка четырёх ABI использует 16 KiB
ELF alignment. AGP/Kotlin/SDK/версии APK не изменены. Debug/native и обе release
source sets проверяются до merge; APK release matrix выполняется после закрытия
библиотечных PR. [Результаты](project-scripts-verification.md).
