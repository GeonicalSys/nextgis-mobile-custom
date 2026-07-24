---
title: Матрица сборки и версий
type: reference
last_verified: 2026-07-24
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
| App versionCode (release) | `195` |
| App versionName (release) | `3.1.2.4` |
| App versionCode (debug) | `196` |
| App versionName (debug) | `3.1.2.5` |
| maplib VERSION_NAME (release) | `3.1.2.4` |
| maplib VERSION_NAME (debug) | `3.1.2.5` |
| MapLibre Android SDK | `13.0.2` |
| OkHttp | `5.3.2` |
| Release application/account | `com.nextgis.mobile.geonical` / `com.nextgis.account.geonical` |
| Debug application/account | `com.nextgis.mobile.debug` / `com.nextgis.account.debug` |

Значения фиксируют проверенное состояние на `last_verified`, но код остаётся
источником истины. Production version задаётся `defaultConfig`, debug app
override — `androidComponents.onVariants`, debug maplib version — отдельным
`buildConfigField`. Application version DSL внутри `buildTypes` для AGP 9.1.0
запрещён.

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
