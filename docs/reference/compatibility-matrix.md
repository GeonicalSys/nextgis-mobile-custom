---
title: Матрица совместимости
type: reference
last_verified: 2026-08-26
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Матрица совместимости

| Связка | Статус |
|---|---|
| app `3.1.2.18` ↔ maplib `3.1.2.18` (release) | Подготовленный Lisa/Belka Release |
| app `3.1.2.18` ↔ maplib `3.1.2.18` (debug) | Подготовленный Lisa Debug |
| app ↔ maplibui pointer из root | Проверять совместной сборкой |
| maplibui ↔ maplib pointer из root | Публичный API проверять compile + smoke |
| Android API 26–36 | Gradle declaration; device coverage зависит от выполненной матрицы |
| Android API 26–28 ↔ MapLibre TextureView | Targeted resume compatibility; требует device smoke после сна/background |
| Android API 29–36 ↔ MapLibre SurfaceView | Основной производительный render view |
| MapLibre `android-sdk-opengl:13.0.2` ↔ текущий rendering fork | OpenGL backend обязателен; generic `android-sdk` MapLibre 13 (Vulkan) несовместим с частью целевых устройств |
| Root ↔ upstream NextGIS tips | Не гарантируется без cycle integration |

Variant coupling реализован разными механизмами: app debug использует
`androidComponents.onVariants`, а maplib debug — override
`BuildConfig.VERSION_NAME`. Совместимость считается подтверждённой только после
`tools/verify-apk-version-matrix.ps1`, который также доказывает, что debug bump
не изменил production APK metadata.

Таблица не утверждает device-покрытие, если соответствующий smoke не был
выполнен. Результаты конкретных устройств фиксируются в release/incident notes.
