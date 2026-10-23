---
title: Подготовка локального Lisa Release 3.1.2.30
type: history
last_verified: 2026-10-23
related_code:
  - app/build.gradle
  - maplib/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

# Подготовка локального Lisa Release 3.1.2.30

Запрос: повысить production версию, собрать подписанный Lisa Release APK и
сохранить локально. Пользователь отдельно отложил Belka APK и публикацию.
Production — 3.1.2.30/224; Lisa Debug — 3.1.2.23/218.
Full на смартфоне/эмуляторе требуется перед публикацией, а не для локальной
сборки. Сейчас выполняются version matrix с LisaOnly, проверка подписи/identity
и SHA-256. Publisher, dry-run и установка на рабочий телефон не запрошены.

## Инвентаризация

После fetch проверены все открытые PR и опубликованные codex/* ветки пяти
репозиториев. В app открыта только #77, в maplib #59, в maplibui #43;
в easypicker/upload_mobile открытых PR и опубликованных codex/* нет.
Все три открытых PR authored by geoglyth — текущая CLI identity.
Дочерних PR со base на этих task branches нет. Новая параллельная ветка не создаётся.

App base — origin/my-maplibre 1de3ef8165c54a4900d9823dac8fcf7c81848873,
maplib base — origin/master b2c280b94efbd643001eccb97ebbd27f06eab7d3,
maplibui base — origin/master 9e064c3830124b033039e91da7b93ff24e6b0f0d.
Базовое повышение до 3.1.2.29 было app #59 (7e794a4); все последующие
завершённые строки ниже проверены из Git.

## Требуемая цепочка

| Требование | Репозиторий / PR / base | Проверенная исходная revision | В текущем consumer tip | Merge state |
|---|---|---|---|---|
| GPS diagnostics, copy policy, whole-object gestures; версия maplib 3.1.2.30 | maplib [#59](https://github.com/GeonicalSys/android_maplib/pull/59), master | c6919d82ed410e56d86b27c5fbbf629505fd6ae2 | Review pin включает production 3.1.2.30 | OPEN, требуется Merge Commit |
| Copy form defaults, NULL/int64, transform toolbar/history, compass guard | maplibui [#43](https://github.com/GeonicalSys/android_maplibui/pull/43), master | 5d32e6a488f7250f37def1f1d3dbb965e1d12b2f | Feature pin присутствует | OPEN, требуется Merge Commit после maplib |
| Updater/GlitchTip, копирование/сдвиг/поворот, versionCode 224 | app [#77](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/77), my-maplibre | d113472c7331b2e5ec3135e55ca60adbab86325f + подготовленное повышение версии | Все изменения в текущей ветке | OPEN, требуется Squash после fetched library merge pins |
| Report guard governance без runtime diff | easypicker #4, master | 55aad0e0f92329ea4c273a83e541d170d24d7b57 | Pin обновлён с d6327f3 на remote merge commit | MERGED |
| Publisher guard и Full publication contract | upload_mobile #4/#5, main | 99e3cfde7841b749b83be6005842b96e7d140d00 | Не входит в APK; checkout содержит обе строки | MERGED, публикация отложена |

До merge трёх открытых PR и повторной проверки remote tips release-сборка
заблокирована правилом Integration and release closure. Разрешение на merge
запрашивается отдельно; изменение версии и описание PR подготавливаются заранее.

## Уже включённые предшественники

Для каждой строки: merge commit является предком remote target; дополнительно
сравнены полные Git diff от первого parent merge commit до PR head и до merge
commit. Они равны (squash/content comparison, а не вывод по названию ветки).
В app/maplib/maplibui target входит в текущую task branch; easypicker закреплён
на последнем remote merge. Изменения easypicker #4 касаются только governance,
docs и report tooling; runtime diff пуст.

| Репозиторий | PR / изменение | Merge revision | Проверка |
|---|---|---|---|
| app | [#76](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/76) Исправить реестр и автоматически подключать контроль карточек | `1de3ef8165c54a4900d9823dac8fcf7c81848873` | ancestry + content PASS |
| app | [#75](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/75) Await retained native sector geometry after visibility changes | `5050495aff9286b0b644c14de868b4d92a082b9d` | ancestry + content PASS |
| app | [#74](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/74) Await real verification startup before native fixtures | `ac5d6d43ef04784bec32226d2eb83ab10c6666dd` | ancestry + content PASS |
| app | [#73](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/73) Wait for startup migration before creating sync test projects | `58f4e2e58a04ed1ce7c01987e54be89e75460c3e` | ancestry + content PASS |
| app | [#72](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/72) Document pinned emulator in build matrix | `1cee81d5c4044e957b66005be8bf13da0aaa7db2` | ancestry + content PASS |
| app | [#71](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/71) Isolate location graphics fixtures and pin verified emulator | `51d9488cfa97772c35e43aad348a6178e69a1348` | ancestry + content PASS |
| app | [#70](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/70) Безопасные вкладки формы и готовность телефона к UI-тестам | `477c9057c1f41308cfa0599e1b0c9e14537cff79` | ancestry + content PASS |
| app | [#69](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/69) Отключать автоматический GPS при явном закрытии приложения | `3179a6fb9ad3b1e6c9532d82f75d7dcc767ce295` | ancestry + content PASS |
| app | [#68](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/68) Улучшить GPS-диагностику и настройки курсора | `0dbdfb812c8c7224f38c32fa76ae60397b860dde` | ancestry + content PASS |
| app | [#67](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/67) Исправить запись очереди при отмене и дополнить диагностику GlitchTip | `687f6c1f81db31614ed25bf37087fac6cea4eb43` | ancestry + content PASS |
| app | [#66](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/66) Статистика использования: устройства, аккаунты и проекты | `e0d4e78a6decdac56b7c26357a863543ea4fcade` | ancestry + content PASS |
| app | [#65](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/65) Убрать значок MapLibre и показать короткую подпись OSM | `520497db38f8a5d44cdca8de3356ff3bbcb2a08a` | ancestry + content PASS |
| app | [#64](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/64) Одна подпись квартала в тех же тайлах без дополнительных объектных слоёв | `ea8da9f3ea496ea10cb3f3eddc27faeff330ad6b` | ancestry + content PASS |
| app | [#63](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/63) Восстановить старые треки из всех проектов после обновления | `66d35deb7cce6d8786b462f7e29feae07c16a3e0` | ancestry + content PASS |
| app | [#62](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/62) Обновить контакты и отображение точности GPS в мобильном приложении | `e34686ce1e8d13d24e66f65dcfd18e4a69b93536` | ancestry + content PASS |
| app | [#61](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/61) Проверять отрисовку подписей в CI через host OpenGL | `1075439d00c3231f433176b6afc5ce375322476a` | ancestry + content PASS |
| app | [#60](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/60) Фоновый GPS, общие треки, стили и доставка правок | `c2f799f8cb1d3d46357b1a71b3d68af9e30a602b` | ancestry + content PASS |
| app | [#59](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/59) Крупный интерфейс, автопроверка обновлений и диагностика карты | `7e794a440b846af5b07f593f9fee2e9d545d394f` | ancestry + content PASS |
| maplib | [#58](https://github.com/GeonicalSys/android_maplib/pull/58) Исправить реестр и автоматически подключать контроль карточек | `b2c280b94efbd643001eccb97ebbd27f06eab7d3` | ancestry + content PASS |
| maplib | [#57](https://github.com/GeonicalSys/android_maplib/pull/57) Уточнить независимость записи GPS от явного закрытия приложения | `033a48165a70d2f0cc70e229c3b20c8f4d7c33fa` | ancestry + content PASS |
| maplib | [#56](https://github.com/GeonicalSys/android_maplib/pull/56) GPS-эпизоды и настройки курсора местоположения | `40f4e0d37d283bb290230075edb4ee3ecf0f2a4e` | ancestry + content PASS |
| maplib | [#55](https://github.com/GeonicalSys/android_maplib/pull/55) Сохранять диагностику зависшего запуска для отложенного ANR | `3f1db55dea715a873f6a073854a0aca1b7154eaf` | ancestry + content PASS |
| maplib | [#54](https://github.com/GeonicalSys/android_maplib/pull/54) Одна внутренняя подпись исходного квартала в локальных MVT | `5693f63fd188cb72f338249e3e4dd945e1f2e4f2` | ancestry + content PASS |
| maplib | [#53](https://github.com/GeonicalSys/android_maplib/pull/53) Восстановить перенос старых треков из проектных баз | `f97e161558ed9844ac314f594c07494529a9671e` | ancestry + content PASS |
| maplib | [#52](https://github.com/GeonicalSys/android_maplib/pull/52) Общие треки, стили по правилу и фоновый GPS | `527291e0c426c8f32c06813cd799c81d87c6e542` | ancestry + content PASS |
| maplib | [#51](https://github.com/GeonicalSys/android_maplib/pull/51) Production maplib 3.1.2.29 | `28a4300ae308c6f531c9b8073382f09c1d6ad052` | ancestry + content PASS |
| maplib | [#50](https://github.com/GeonicalSys/android_maplib/pull/50) Диагностировать задержки карты и причины отказа локальных тайлов | `6f398acb59d98fdb5a858754404646c3dfe73f4f` | ancestry + content PASS |
| maplibui | [#42](https://github.com/GeonicalSys/android_maplibui/pull/42) Исправить реестр и автоматически подключать контроль карточек | `9e064c3830124b033039e91da7b93ff24e6b0f0d` | ancestry + content PASS |
| maplibui | [#41](https://github.com/GeonicalSys/android_maplibui/pull/41) Не переключать вкладку формы после сохранения состояния экрана | `058d0c1073128f17623dd7b32c180a0bad7cf909` | ancestry + content PASS |
| maplibui | [#40](https://github.com/GeonicalSys/android_maplibui/pull/40) Сохранить контракт записи трека и обхода после закрытия интерфейса | `7142d4e89c48a76010214f4cfde1836c530d2df4` | ancestry + content PASS |
| maplibui | [#39](https://github.com/GeonicalSys/android_maplibui/pull/39) Уточнить контракт GPS-эпизодов для UI | `ee60a04c83fe0cc97d845bf02c6e357c2b3881a4` | ancestry + content PASS |
| maplibui | [#38](https://github.com/GeonicalSys/android_maplibui/pull/38) Описать границы диагностики sync и startup после разбора GlitchTip | `9c4b8c82f388ad2d040ba59d72b3cf431e1460a7` | ancestry + content PASS |
| maplibui | [#37](https://github.com/GeonicalSys/android_maplibui/pull/37) Общая запись и отправка треков, доставка правок и выбор слоёв | `ac19458c3fffa8d99fc820b4b6a729ae33b48856` | ancestry + content PASS |
| maplibui | [#36](https://github.com/GeonicalSys/android_maplibui/pull/36) Крупный интерфейс: полные надписи и доступные элементы | `d5313944b9ca776819cfc3a28f99e5a691fe4a09` | ancestry + content PASS |
| easypicker | [#4](https://github.com/GeonicalSys/easypicker/pull/4) Исправить реестр и автоматически подключать контроль карточек | `55aad0e0f92329ea4c273a83e541d170d24d7b57` | ancestry + content PASS |
| publisher | [#5](https://github.com/GeonicalSys/upload_mobile/pull/5) Исправить реестр и автоматически подключать контроль карточек | `99e3cfde7841b749b83be6005842b96e7d140d00` | ancestry + content PASS |
| publisher | [#4](https://github.com/GeonicalSys/upload_mobile/pull/4) Требовать полный мобильный прогон перед публикацией APK | `0072d0a09d737476beac0046db89c3c35be0fc12` | ancestry + content PASS |

## Проверки и артефакт

Signing credentials и keystore обнаружены в существующей локальной конфигурации;
значения не выводились. До сборки новая version matrix не считается PASS.
Предыдущие 3/3 stationary phone cases и 72 UI configurations относятся к
функциональному срезу до повышения версии; это не Full текущего релиза.
Полный тестовый прогон, Belka APK и публикация остаются отложенными.
