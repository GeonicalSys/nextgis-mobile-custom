---
title: Локальный Lisa Release после исправлений треков и подписей
type: reference
last_verified: 2026-10-25
related_code:
  - app/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - maplib/src/main/java/com/nextgis/maplib/map/VisibleLineLabelController.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
---

# Подписанный APK без публикации

По повторному запросу пользователя собран обновлённый Lisa Release. Действует
его явное разовое разрешение: «Разово собрать из текущих PR без слияния и
публикации — разрешаю исключение для этого APK». Обычные closure/Full gates
перед публикацией сохраняются. Merge, publisher, dry-run, GitHub Release и
установка в рабочую ЛИСУ не выполнялись. Desktop не изменялся.

## Артефакт

- Файл: `Q:\Codex\Artifacts\lisa-mobile\2026-10-25\lisa-3.1.2.30-224-caed88b.apk`.
- Production identity: `com.nextgis.mobile.geonical`, **NextGIS ЛИСА**, **3.1.2.30 / 224**.
- Размер: **70 472 069 bytes**, **67.21 MiB**.
- SHA-256: `0a84917e2cd5396017d1168d910d508da68b8c89f3e12e49ef466ac0dde3539f`.
- Подпись: APK Signature Scheme v2, `apksigner verify` PASS; точное совпадение
  с существующей настроенной Lisa Release signing configuration.
- SHA-256 сертификата: `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`.
- Min SDK 26, target SDK 36; ABI arm64-v8a, armeabi-v7a, x86, x86_64.
- `debuggable=false`, `FIELD_VERIFICATION=false`; обычные Sentry/usage inputs
  присутствуют. Значения credentials не выводились и в Git не добавлялись.
- `SOURCE_REVISION=caed88b405ffd3c5b91c2624f8fda47bb321fd1d` без dirty suffix;
  эта ревизия дополнительно найдена в упакованном DEX.

Копия сверена по SHA-256 с непосредственным Gradle output. Рядом сохранён
`lisa-3.1.2.30-224-caed88b.build.json` с составом и проверками. Первый кандидат
`lisa-3.1.2.30-224-77e1227.apk` и его [историческая квитанция](lisa-local-release-2026-10-25.md)
сохранены отдельно; настоящие исправления входят в новый `caed88b`.

## Состав и закрытие цепочки

После fetch инвентаризированы все открытые PR и опубликованные
`origin/codex/*` в пяти owner repositories. Все три открытых PR — Draft,
автор `geoglyth`. В каждой истории единственная ещё не включённая в target base
опубликованная task branch полностью входит в выбранный tip; пропущенных
параллельных/stacked изменений нет. В easypicker/publisher открытых PR и таких
веток нет. Проверка выполнена через Git ancestry и точные submodule pins.

| Владелец | Точная ревизия APK | PR / base | Включение и merge-state |
|---|---|---|---|
| app | `caed88b405ffd3c5b91c2624f8fda47bb321fd1d` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) / my-maplibre | clean APK source; unmerged |
| maplib | `761820bd1b706423ce9716232878c8ac62d84a8e` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60) / master | exact consumer pin; unmerged |
| maplibui | `d4b8f27337f0549f25353f9b28e7810371f00ae5` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) / master | exact consumer pin; unmerged |
| easypicker | `55aad0e0f92329ea4c273a83e541d170d24d7b57` | master | exact pin, ancestor of remote master |
| upload_mobile | `99e3cfde7841b749b83be6005842b96e7d140d00` | main | только read-only inventory; publisher не запускался |

Матрица требований содержит 17 строк в `release-inventory.json` на Q.
Основные группы, проверенные на ancestry:

| Требование | Владелец / commits | В итоговом составе |
|---|---|---|
| «Светлячок», полигоны, live preview, независимые категории | maplib `2be9772`, `118d139`; maplibui `67045668`, `b3fd7ba2`; app `066573a`, `604bb24` | да |
| MapLibre 13.7.1 OpenGL | maplib `ee59471`, maplibui `d8bcf9fd`, app `7e44811` | да |
| Выбор/переименование/цвет/видимость/удаление треков и приоритет точек | maplib `ab59ad2`, maplibui `ddc4dbdf`, app `d19fd11` | да |
| Тонкая линия и однократная раскраска старой истории | maplib `7e7055e` | да |
| Подтверждение Stop и защита от старых действий | maplibui `ff4a1cce`, app `caed88b` | да |
| GPS resume intent, активный карандаш и удаление лишнего preset | maplibui `d4b8f273`, app `caed88b` | да |
| Одна цельная подпись видимой части линейного объекта | maplib `761820b`, app `caed88b` | да |
| Дорожная карта и регламент изменений | app `f8c0346` | да |

Согласованно отложены серверные имя/метки трека (пункт 5), desktop и переделка
загрузки/тайлов. Неполное закрытие merge-зависимостей разрешено только для
этого локального APK. Будущий порядок: maplib #60 Merge Commit → maplibui #44
Merge Commit → fetch удалённых merge commits и новые consumer pins → app #78
Squash → повторная closure/Full/Check-Publication. Эти действия не выполнялись.

## Проверки

`tools/verify-apk-version-matrix.ps1` завершился успешно: Lisa Debug,
Lisa Release и Belka Release собраны и проверены через APK metadata; версии
не повышались. Дополнительный Belka output служит проверке общего кода;
пользовательский артефакт сохранён только для ЛИСЫ. Runtime содержит только
`org.maplibre.gl:android-sdk-opengl:13.7.1` без generic/Vulkan/dual backend.
Использованы установленный JBR 21/SDK, общий Gradle cache и существующие build
junctions на Q. Сборка — обычная production configuration, без включённых
field verification/CI/diagnostic delivery режимов.

Текущий код: **887 unit PASS**, компиляция обоих Release consumers PASS,
**11/11 финальных адресных native PASS** на эмуляторе API 36, 27 runner tests
и 24 docs tests PASS. Native source fingerprint:
`28b1524d67a226161894e403d4a926b568db4a18f495668de77dbd73ee2e28a7`.
Подробности, 24/72 UI профиля, исходные неуспешные batches и **частичные 9 PASS
на Samsung** перечислены в [отчёте треков и подписей](../reference/track-followup-2026-10-25.md).
Телефон отключился до полного финального повторения. Full/Check-Publication,
Android 9/11 и FPS/cold start не проверялись.

Документационный коммит с этой квитанцией следует после APK source и не меняет
содержимое собранного приложения; повторная сборка ради текста квитанции не нужна.

## Хранение

Текущий и первый APK — постоянные локальные outputs в `Q:\Codex\Artifacts`,
примерно по 67.21 MiB. Task evidence находится в
`Q:\Codex\Scratch\track-followup-20261025`: около 1.7 MiB отчётов, логов и PNG
для review плюс около 73.1 MiB приватного device export с пересмотром хранения
до 1 ноября. Экспорт не добавлен в Git. Manifest отделяет evidence от disposable.

Удалены ненужный первоначальный logcat (40 256 504 bytes), инспекционный JAR
(813 243 bytes) и пустой остановленный capture. Отсутствие проверено после
удаления, cleanup receipts сохранены. Owned emulator/build/test clients завершены;
общие SDK, caches, AVD и существующие build directories сохранены для повторного
использования. Неразрешённых остатков этой задачи нет.
