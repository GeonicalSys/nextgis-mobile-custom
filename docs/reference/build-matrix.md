---
title: Матрица сборки и версий
type: reference
last_verified: 2026-08-26
related_code:
  - build.gradle
  - gradle/wrapper/gradle-wrapper.properties
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Матрица сборки и версий

| Компонент | Текущее значение |
|---|---|
| Gradle wrapper | `9.3.1` |
| Android Gradle Plugin | `9.1.0` |
| Kotlin plugin | `2.2.10` |
| compileSdk | `36` |
| targetSdk | `36` |
| minSdk | `26` |
| App versionCode (release) | `212` |
| App versionName (release) | `3.1.2.18` |
| App versionCode (debug) | `213` |
| App versionName (debug) | `3.1.2.18` |
| maplib VERSION_NAME (release) | `3.1.2.18` |
| maplib VERSION_NAME (debug) | `3.1.2.18` |
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

На API 26–28 MapLibre view использует `TextureView`, чтобы старый Android не
оставлял полноэкранный чёрный `SurfaceView` после background/sleep. API 29–36
сохраняют более производительный `SurfaceView`; backend в обоих случаях OpenGL.

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
