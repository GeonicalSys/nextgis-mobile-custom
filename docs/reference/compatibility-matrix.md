---
title: Матрица совместимости
type: reference
last_verified: 2026-07-30
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Матрица совместимости

| Связка | Статус |
|---|---|
| app `3.1.2.7` ↔ maplib `3.1.2.7` (release) | Текущий Lisa/Belka Release |
| app `3.1.2.7` ↔ maplib `3.1.2.7` (debug) | Текущий Lisa Debug |
| app ↔ maplibui pointer из root | Проверять совместной сборкой |
| maplibui ↔ maplib pointer из root | Публичный API проверять compile + smoke |
| Android API 26–36 | Gradle declaration; device coverage зависит от выполненной матрицы |
| MapLibre `13.0.2` ↔ текущий rendering fork | Проверено кодовой базой; upgrade — high risk |
| Root ↔ upstream NextGIS tips | Не гарантируется без cycle integration |

Variant coupling реализован разными механизмами: app debug использует
`androidComponents.onVariants`, а maplib debug — override
`BuildConfig.VERSION_NAME`. Совместимость считается подтверждённой только после
`tools/verify-apk-version-matrix.ps1`, который также доказывает, что debug bump
не изменил production APK metadata.

Таблица не утверждает device-покрытие, если соответствующий smoke не был
выполнен. Результаты конкретных устройств фиксируются в release/incident notes.
