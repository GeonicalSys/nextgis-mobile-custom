---
title: Локальный Lisa Release со свечением треков 6.5 px
type: reference
last_verified: 2026-10-25
related_code:
  - app/build.gradle
  - maplib/src/main/java/com/nextgis/maplib/map/TrackMapStyle.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
---

# Уточнённый образец трека

Повторным выбором пользователь увеличил ширину свечения с 4.5 до **6.5 px**.
Сердцевина **1.4 px**, затемнение **73%**, blur **3**, opacity **.83** и два
обычных видимых уровня сохранены. История и текущая запись используют один
стиль; цвета в БД, исходный оттенок свечения, SQL/sync и число sources/layers
не меняются. Это уточнение той же доработки и тех же карточек update-reports.

Пользовательский запрос локального Release без публикации и разрешение
пропустить локальные тесты остаются в силе. Сохраняется прежнее разовое
исключение для APK из текущих Draft PR без merge. Unit/native/UI/FPS проверки
в этом цикле не запускались. На рабочее устройство APK не устанавливался.

## Подписанный артефакт

- `Q:\Codex\Artifacts\lisa-mobile\2026-10-25\lisa-3.1.2.30-224-f06ccfb.apk`.
- **NextGIS ЛИСА**, `com.nextgis.mobile.geonical`, **3.1.2.30 / 224**.
- **70 488 453 bytes**, **67.22 MiB**.
- SHA-256: `9184c9ce6a14858d0be73c5b34f25e6ba3b2bba0fcb5755cbfbfa41368fc229c`.
- APK Signature Scheme v2: PASS. Certificate SHA-256:
  `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`.
- `debuggable=false`, `FIELD_VERIFICATION=false`, min SDK 26, target SDK 36;
  ABI arm64-v8a, armeabi-v7a, x86, x86_64. Sentry/usage inputs сохранены.
- Clean source `f06ccfb0b1dd8ecb7e29acdc1b797dcada2348ac` найден в DEX;
  производный `track_core_color` также присутствует в упакованном коде.

`:app:assembleLisaRelease --console=plain`: BUILD SUCCESSFUL, 218 tasks.
Aapt identity, подпись, SHA-256 и точная копия output проверены отдельно.
Belka/Debug и полная version matrix не запускались, версии не менялись.
Рядом с APK сохранён `.build.json`. Предыдущий [кандидат 280b78b со свечением
4.5 px](lisa-local-release-dark-core-2026-10-25.md) и более ранние APK сохранены.

## Состав

После fetch проверены все open PR и опубликованные `origin/codex/*` пяти
repositories. Матрица 21 требований подтверждает ancestry предыдущих стилей,
MapLibre, треков и подписей; первоначальная ширина 4.5 явно заменена выбором
6.5. Пропущенных необходимых parallel/stacked изменений нет, pins точные.

| Владелец | Ревизия APK | PR / base | Состояние |
|---|---|---|---|
| app | `f06ccfb0b1dd8ecb7e29acdc1b797dcada2348ac` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) / my-maplibre | exact APK source, unmerged |
| maplib | `3c8020a0f9b550db36baee8b7234fea6ee6325e6` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60) / master | exact pin, unmerged |
| maplibui | `d4b8f27337f0549f25353f9b28e7810371f00ae5` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) / master | exact pin, unmerged |
| easypicker | `55aad0e0f92329ea4c273a83e541d170d24d7b57` | master | exact pin, merged |
| upload_mobile | `99e3cfde7841b749b83be6005842b96e7d140d00` | main | inventory only |

Все PR Draft. Будущий порядок: #60 Merge Commit → #44 Merge Commit → fetched
merged pins → #78 Squash, затем closure/Full/Check-Publication перед согласованной
публикацией. Merges, publisher/dry-run и публикация здесь не выполнялись.
Desktop, загрузка/тайлы и серверные имя/метки треков остаются отложенными.
Документационный коммит с этой квитанцией следует после APK source.

Evidence: `Q:\Codex\Scratch\track-dark-core-20261025\evidence\glow-6_5`;
компактные matrix/build/signature/metadata receipts удерживаются до PR review.
Новых runtime/emulator/profile/download copies нет. Общие SDK/cache/build
junctions переиспользованы; disposable остаётся пустым. Durable APK outputs
сохраняются для проверки/сравнения, примерно 67.2 MiB каждый. Встроенный образец
на C и его исходник на Q сохранены по прежнему согласованному правилу.
