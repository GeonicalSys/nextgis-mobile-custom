---
title: Матрица совместимости
type: reference
last_verified: 2026-07-19
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
---

# Матрица совместимости

| Связка | Статус |
|---|---|
| app `3.0.3.9` ↔ maplib `3.0.3.9` | Требуемая версия текущего workspace |
| app ↔ maplibui pointer из root | Проверять совместной сборкой |
| maplibui ↔ maplib pointer из root | Публичный API проверять compile + smoke |
| Android API 26–36 | Gradle declaration; device coverage зависит от выполненной матрицы |
| MapLibre `13.0.2` ↔ текущий rendering fork | Проверено кодовой базой; upgrade — high risk |
| Root ↔ upstream NextGIS tips | Не гарантируется без cycle integration |

Таблица не утверждает device-покрытие, если соответствующий smoke не был
выполнен. Результаты конкретных устройств фиксируются в release/incident notes.
