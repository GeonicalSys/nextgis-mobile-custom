---
title: Проверка MapLibre 13.7.1 OpenGL
type: reference
last_verified: 2026-10-24
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - maplibui/build.gradle
  - gradle/verification-metadata.xml
  - tools/verify-apk-version-matrix.ps1
---

# Проверка MapLibre 13.7.1 OpenGL

## Область изменения

По запросу пользователя обновляется только MapLibre Android SDK: `13.0.2` →
`13.7.1`, во всех трёх модулях остаётся `android-sdk-opengl`. Существующие
GeoJSON и локальные MVT, фабрики стилей и подписей, формы и редактирование
сохраняют прежний код. Загрузка, постоянный tile cache, Vulkan, SDF и
feature-state в эту доработку не входят. Версии Lisa/Belka и account identity
не меняются.

## Исходное состояние и доставка

Preflight с fetch: все репозитории чистые, актуальные относительно tracking refs.
Пользователь явно выбрал продолжение текущих Draft PR:

- app: [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78),
  `codex/docs-push-range`;
- maplib: [#60](https://github.com/GeonicalSys/android_maplib/pull/60),
  `codex/point-firefly`, исходный tip `118d1399578d62f35ddd6a21bf332b6165e74664`;
- maplibui: [#44](https://github.com/GeonicalSys/android_maplibui/pull/44),
  `codex/point-firefly`, исходный tip `b3fd7ba2acfb0306433a79eaa3a2ee306ef8f087`.

Порядок merge: обе библиотеки через Merge Commit, затем root pointers на их
remote merge commits и Squash Merge app. Merge и публикация сейчас не запрошены.
Pins на открытые library PR не означают готовый выпуск.

Обновлённые library tips: maplib `ee59471c31e53f82c06da2757bfb58e0fea4e0e9`,
maplibui `d8bcf9fd6555af6ed66c0f3fe9969c1c68302e54`. Оба содержат исходные tips
стилей по ancestry. Текущая root-ветка сохраняет предыдущие изменения стилей,
редакторов, категорий и test input; новые pointers продолжают ту же цепочку.
Других открытых PR в easypicker и upload_mobile при сверке нет.

## Проверки

Сборки и unit-тесты прошли на установленном SDK/JBR с существующими build
junctions и Gradle cache на Q:

| Проверка | Результат |
|---|---|
| maplib unit | 602 PASS, 0 failures/errors/skips, 106 suites |
| maplibui unit и assembleDebug | 93 PASS, 0 failures/errors/skips, 23 suites; сборка PASS |
| app unit | 180 PASS, 0 failures/errors/skips, 30 suites |
| Lisa Debug, Lisa Release, Belka Release | Полные assemble PASS |
| Version matrix с чтением APK через aapt | PASS; прежние package/versionCode/versionName и maplib diagnostic versions |
| Runtime dependency graph всех трёх APK variants | Только MapLibre `android-sdk-opengl:13.7.1`; generic/Vulkan/dual-backend отсутствуют |
| docs-check -RunTests | Validator и 24 tests PASS |

Основной Gradle run: `BUILD SUCCESSFUL`, 448 actionable tasks,
155 executed / 293 up-to-date. Verification app/test APK отдельно собраны
с `fieldVerificationChecks=true` и `ciReliabilityChecks=true`, без генерации
checksum metadata. Version-matrix и dependency reports также выполнялись
с обычной включённой dependency verification.

Новые транзитивные зависимости SDK: Fragment/Fragment KTX `1.8.9` и Coroutines
`1.10.2`. SDK декларирует Kotlin stdlib `2.2.10`; фактическое разрешение всех
вариантов сохранено в runtime reports. В checksum metadata добавлено 12
artifact/metadata записей; каждая сверена с опубликованным SHA256 Maven Central
либо Google Maven. Проверки не отключались и trust-all исключения не добавлялись.

### Native результат

Samsung SM-A566B, Android 16 / API 36, hardware `s5e8855`:
**27/27 native methods PASS, 0 failures/skips**, только отдельный
`com.nextgis.mobile.verification.debug`.

```powershell
.\tools\verify-mobile-tests.ps1 -Scope Related -Area styles,feature-editing `
  -PhoneSerial <explicit-serial> -PhoneOnly -SkipBuild -OutputPath <Q-evidence>
```

`-SkipBuild` использован после успешной отдельной сборки verification app/test
APK на тех же исходниках с включённой dependency verification. Runner проверил
package metadata APK до установки. Проверка source fingerprint до/после и
после library commits подтвердила неизменность проверенного кода:
`3876de726fd322facd0edf8491784d6de520d6846e3979fbf26aa6c92b923e3b`.

- 24 случая области styles: настоящие пиксели точек/линий/полигонов, категории,
  цвет/alpha/масштабные кривые, подписи и шрифты, native layout groups, отверстия,
  pattern/dash/дополнительные контуры, picking, restyle/visibility/cleanup,
  «Светлячок», оба редактора и переход существующего MVT к classic fallback.
- Оба редактора прошли по 72 локальных русских theme/orientation/font/display
  профиля с реальными Save/Cancel touches; polygon preview проверен настоящими
  opacity gestures для точек, линий и полигонов.
- 3 stationary случая feature-editing: копирование read-only объекта в
  подходящий редактируемый слой с категорией и сохранением атрибутов, сдвиг и
  поворот целой геометрии при неподвижной камере с Undo каждого жеста,
  доступность действий при крупном интерфейсе.

Прежние receipts на 13.0.2 сохранены как история и новой версии не засчитываются.
SHA256 `phone-native/result.json`:
`1573231b4084a07ca160c193e8f11b79818c8ceb13b271ee08cbff95d65bdaa4`.

Текущие APK SHA256 (обычный debug output после native build содержит именно
verification variant; basename не определяет application ID или version):

| Артефакт | SHA256 |
|---|---|
| Verification app | `50449a2c12665c1f141c68579ac97f0b63f1ad44de9e9c155479bd27ea0f76b3` |
| Verification test | `611e8941e9e974986f08dc0ab6af927d785ee30f2b6c9b627139d4292e659891` |
| Lisa Release | `667daf764a96c3b81534dc4c4c8c069308134da4c4937905aae605da14ae3552` |
| Belka Release | `821ee12614f192ba78c9b94c2012eb2443e767b390176173f67403bee7e7e693` |

JBR: `21.0.9+-14787801-b1163.94`; Gradle: `9.3.1`. Build и текущие dependency
graphs не требуют изменений Java/Kotlin кода ЛИСА либо ослабления assertions.

### Ограничения

Аппаратное ускорение эмулятора в текущем Windows-сеансе недоступно:
`HypervisorPresent=false`, `emulator -accel-check` возвращает 6. Пользователь
выбрал подключение разблокированного телефона для stationary verification.
Эмуляторные/GPS/radio сценарии не подменяются телефонными.
Native Android 9/11 и устройства без Vulkan в этой доработке не запускались;
выбор OpenGL доказан artifact/runtime graph и native проверкой API 36.
Полный MapEditingToolsTest, отдельный LocalVectorTileLabelTest и Full не
выполнялись. Это адресная совместимость стилей/подписей и stationary editing,
а не полная приёмка или разрешение публикации. Время холодного старта/FPS
не измерялось; ускорение загрузки здесь не заявляется.

## Хранение доказательств

Логи и компактные native-снимки:
`Q:\Codex\Scratch\maplibre-13-7-1-20261024\evidence`.
В этой папке есть storage manifest с владельцем, состоянием и retention;
перед сборками проверен tiny create/delete/readback на Q. SDK/JBR, общий Gradle
cache и существующие workspace build junctions переиспользуются; их удаление
не входит в cleanup этой задачи.

После native runner verification process остановлен, его disposable workspace
очищен (`pm clear`), отсутствие процесса проверено. Установленный verification
APK оставлен для повторного использования. Рабочее приложение не
переустанавливалось и не очищалось; системные font/display/radio настройки
телефона не менялись. Компактные логи/receipts/QA PNG сохраняются до review;
они отделены от disposable work и могут быть удалены после review по manifest.
Объём компактных доказательств — около 4 MiB. Новых emulator images, копий SDK,
инсталляторов или DB clusters не создавалось. Переиспользованные build outputs
и общий package cache сохраняются по своим правилам хранения.
