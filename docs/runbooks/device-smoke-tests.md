---
title: Ручные проверки на Android-устройстве
type: runbook
last_verified: 2026-07-19
related_code:
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
---

# Ручные проверки на Android-устройстве

Выбирать тесты по `registry/change-impact.yaml`; точные IDs и шаги находятся в
`registry/smoke-tests.yaml`.

## Базовая матрица

1. Clean start: установка, запуск, выдача разрешений, открытие карты.
2. Existing profile: обновление APK поверх существующих данных и открытие карты.
3. Layer order: OSM + пользовательские vector/raster + NGRc import.
4. Hot add/reorder: карта совпадает со списком без перезапуска.
5. Offline edit/sync: правка объекта, offline, восстановление сети, успешный sync.
6. Collector: импорт, переключение проекта, composition update и backup guard.
7. Walk edit: старт, пауза/возврат, завершение geometry.
8. Tracking: запись трека без start/end flag layers на карте.
9. Update: manifest flavor/version/signature validation и безопасный отказ.

## Фиксация результата

Записать flavor/build, Android version/device, профиль (clean/existing), test ID,
результат и наблюдаемое ограничение. Если smoke не выполнен, не писать
«проверено»; явно указать, что осталась ручная проверка.
