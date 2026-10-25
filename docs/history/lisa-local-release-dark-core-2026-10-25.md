---
title: Локальный Lisa Release с выбранной тёмной сердцевиной треков
type: reference
last_verified: 2026-10-25
related_code:
  - app/build.gradle
  - maplib/src/main/java/com/nextgis/maplib/map/TrackMapStyle.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
---

# APK для проверки пользователем

Это кандидат `280b78b` со свечением 4.5 px. После повторного выбора пользователя
собран [новый APK `f06ccfb` со свечением 6.5 px](lisa-local-release-track-glow-2026-10-25.md).
Ниже сохранены исходные параметры и квитанция `280b78b`.

По повторному запросу пользователя собран только Lisa Release без публикации.
Сохраняется его явное разовое исключение для локального APK из текущих Draft
PR без merge. Локальные unit/native/UI прогоны пользователь разрешил пропустить;
в этом цикле они не запускались. Сходство нового свечения с browser образцом
и поведение на устройстве пока не проверены. Рабочая установка не менялась.

## Артефакт

- `Q:\Codex\Artifacts\lisa-mobile\2026-10-25\lisa-3.1.2.30-224-280b78b.apk`.
- Package `com.nextgis.mobile.geonical`, label **NextGIS ЛИСА**, **3.1.2.30 / 224**.
- **70 488 453 bytes**, **67.22 MiB**.
- SHA-256: `af1b18b9c27fc06a9dda6ad6a67acd1bb37becb9e5a6af7d9d657e93000261b2`.
- APK Signature Scheme v2: `apksigner verify` PASS.
- Certificate SHA-256: `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`,
  совпадает с прежним production Lisa Release.
- `debuggable=false`, `FIELD_VERIFICATION=false`, min SDK 26, target SDK 36;
  ABI arm64-v8a, armeabi-v7a, x86, x86_64.
- Source `280b78b7517a1a45445a4f0d1990a7423eae1351` без dirty suffix найден
  также в упакованном DEX; новый ключ `track_core_color` присутствует в DEX.
- Обычные Sentry/usage inputs сохранены; их значения и signing secrets не
  печатались и в Git не добавлялись.

Рядом находится одноимённый `.build.json`. Постоянная копия сверена по hash
с непосредственным Gradle output. Предыдущие `77e1227` и `caed88b` APK сохранены
отдельно для сравнения; новый вариант не перезаписывает эти outputs.

## Выбранный стиль и состав

Два обычных видимых уровня: сердцевина **1.4 px**, каждый sRGB-канал затемнён
на **73%**; свечение исходного насыщенного цвета **4.5 px / blur 3 / opacity .83**.
История и текущая запись используют одинаковые настройки. Белое выделение
выбранного трека остаётся прежним. Сохранённые цвета/точки/имена, схема SQLite,
sync, число источников/слоёв, MapLibre 13.7.1 OpenGL и desktop не меняются.
[Выбор и ограничения](../reference/track-dark-core-2026-10-25.md).

Перед сборкой выполнены fetch и инвентаризация всех открытых PR и опубликованных
`origin/codex/*` во всех пяти repositories. Матрица из 19 требований проверена
через Git ancestry и точные consumer pins; в tip присутствуют прежние стили,
подписи, MapLibre и управление треками плюс выбранный вариант сердцевины.
Пропущенных необходимых parallel/stacked изменений нет.

| Владелец | Ревизия APK | PR / base | Включение |
|---|---|---|---|
| app | `280b78b7517a1a45445a4f0d1990a7423eae1351` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) / my-maplibre | exact clean APK source; unmerged |
| maplib | `4083f2403d89580a84a86f783ee32f38d839bf6b` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60) / master | exact pin; unmerged |
| maplibui | `d4b8f27337f0549f25353f9b28e7810371f00ae5` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) / master | exact pin; unmerged |
| easypicker | `55aad0e0f92329ea4c273a83e541d170d24d7b57` | master | exact pin; merged in remote master |
| upload_mobile | `99e3cfde7841b749b83be6005842b96e7d140d00` | main | inventory only; publisher не запускался |

Все три PR остаются Draft. Новые изменения: maplib `4083f24` с выбранным
стилем → app `280b78b` с этим pin и исправленными native expectations. Merges
не выполнялись. Будущий порядок closure: #60 Merge Commit → #44 Merge Commit →
fetched merged pins → #78 Squash. Публикации по-прежнему нужны closure и
Full/Check-Publication; этот локальный кандидат их не заменяет. Серверные
имя/метки трека, desktop и переделка загрузки/тайлов согласованно отложены.

## Выполненные проверки и хранение

`:app:assembleLisaRelease --console=plain`: **BUILD SUCCESSFUL**, 218 tasks,
без включения verification/CI/diagnostic delivery режимов. Версия/label/package
прочитаны из APK через aapt; подпись, hash и наличие source revision проверены
отдельно. Документационный validator и реестр карточек прошли. Belka/Debug APK,
полная version matrix и локальные тестовые suites в этом цикле не запускались.
Прежние 887 unit/11 native относятся к [предшествующему APK](lisa-local-release-tracks-labels-2026-10-25.md).
Документационный коммит с квитанцией следует после APK source и не меняет код.

Compact evidence/manifest: `Q:\Codex\Scratch\track-dark-core-20261025`.
Сохранены отчёты, небольшой build log, matrix, подпись, два PNG образца и его
QA script — менее 0.5 MiB; пересмотр после выбора/PR acceptance. Disposable
wrapper удалён ранее с readback, новых эмуляторов/профилей/runtime downloads
нет. Переиспользованы SDK/JBR, общий Gradle cache и прежние build junctions.
Durable APK outputs — около 67.2 MiB каждый. Встроенный образец 16.3 KiB на C
сохранён по разрешённому пользователем исключению без directory redirects;
оригинал на Q оставлен как исходник совместимой копии. Незавершённого cleanup нет.
