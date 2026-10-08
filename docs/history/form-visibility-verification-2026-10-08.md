---
title: Проверка оформления и условной видимости NGFP
type: history
last_verified: 2026-10-08
related_code:
  - maplib/src/main/java/com/nextgis/maplib/forms/ConditionalRequiredRules.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FormFieldLayout.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ConditionalRequiredController.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/FormAppearanceTest.java
---

# Проверка оформления и условной видимости NGFP

Проверен вариант «Чёткие поля» и общий `lisa_form_rules` v2 с видимостью
полей/элементов. [Контракт](../architecture/conditional-form-rules.md),
[инструкция пользователя](../guides/form-behavior-user-guide.md).
Это проверка Draft PR, а тестовый APK не является опубликованным выпуском.

## Матрица доставки

| Требование | Владелец / commit | PR / base | Включение |
|---|---|---|---|
| Native v2 parser, типы и общие лимиты | maplib `0cd7d39b8bdfe34d391bdf6c5f118b0709e549a8` | [#43](https://github.com/GeonicalSys/android_maplib/pull/43), master, Draft | Точный gitlink app `eddec108ea286d35b5bfe0c819b42d0a125b73b5` |
| Оформление, видимость, сохранение состояния контейнеров | maplibui `76f417e08bc99f1e270f1a4c4bbc2376839580ad` | [#31](https://github.com/GeonicalSys/android_maplibui/pull/31), master, Draft | Точный gitlink того же app tip |
| Native regressions, инструкции и pins | app `eddec108ea286d35b5bfe0c819b42d0a125b73b5` | [#51](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/51), my-maplibre, Draft | Проверенный исходный tip APK; последующий docs commit не меняет код |
| Desktop contract, без QGIS-кода | lisa `0759603c` | [#168](https://github.com/GeonicalSys/lisa/pull/168), main, Draft | Отдельный worktree; Android runtime его не импортирует |

Предыдущая общая синхронизация включена через ancestry core `0a2e3e6`, UI
`bc4a5198` и app `d665e26`; прежние формы/жесты также являются предками.
[Предыдущая матрица](project-sync-verification-2026-10-07.md) сохраняет исходные
строки требований. Инвентаризация remote codex-веток вне основных веток показала
только эту Android-цепочку; easypicker/upload_mobile не имеют открытых PR или
неинтегрированных codex-веток. Отложенных требований этой задачи нет.
Параллельный desktop #167 относится к plan_cut и не пересекает эти пять docs-файлов.

Порядок завершения: maplib #43 Merge Commit → maplibui #31 Merge Commit → fetch
и точные удалённые merge pins → app #51 Squash → desktop #168 Squash.
Слияний/публикации в этом этапе не было. Текущие library pins указывают на
проверенные опубликованные Draft heads, поэтому выпуск ещё не закрыт.

## Проверки

- Windows, JDK 21.0.9, Gradle 9.3.1; emulator-5554, Android 16 / API 36.
- Unit: maplib **505**, maplibui **90**, app **41** — без ошибок и пропусков.
- Итоговый native прогон reliability/util: **87/87**. Настоящие controls,
  SQLite, Binder/QuickJS, ContentProvider и проектные журналы; синтетические данные.
- Видимость: shown/hidden required, статический flag, вложенные/неактивные Tabs,
  detached pinned header, view-only, field+element через И, координаты двумя controls,
  неправильные ID/цели, rotation/update pin/durable recovery/Back Save.
- Оформление: постоянная подпись, разные размеры/цвета значения, перенос длинного
  выбранного названия, минимум 56dp, обе темы, нижний Save и его общий required gate.
  Снимки светлой/тёмной темы с показанным/скрытым комментарием осмотрены.
- Bundle/durable draft сохраняют controls через registry, независимо от wrapper.
  Стандартная форма и NGFP без Tabs сохраняют текст/выбор после пересоздания.
  Предупреждение о прежних аудитах проверено вместе с pin и решениями Save.
- Синтетический script fixture использует уникальную идентичность проекта,
  проверяет владельца до открытия и не отключает production ownership guard.
- Lisa Debug, Lisa Release, Belka Release и test APK собраны после последней
  правки runtime. Version-matrix по aapt и MapLibre OpenGL 13.0.2 прошла.
- Строгий Android docs validator и семь его unit-тестов прошли; desktop docs
  validator и GitHub validate PR #168 прошли. QGIS-код/версии не менялись,
  новый runtime gate или миграция QGIS 3/4 не заявляются.
- На момент передачи GitHub regression приложения ещё выполняется;
  указанные выше unit/native результаты получены локально, отдельно от CI.

## Тестовая группа 803

Установлен только `meta.json.lisa_form_rules` v2 для **12 форм / 328 пар**.
Комментарий виден и обязателен при false/0 своего checkbox.

| Форма | Родительский слой | Название | Пар |
|---|---|---|---|
| 944 | 931 | 592 | 20 |
| 932 | 805 | 5S | 20 |
| 933 | 807 | Вывозка | 33 |
| 934 | 809 | Дорожные | 19 |
| 935 | 811 | Заготовка | 42 |
| 936 | 813 | Лесохозяйственные | 19 |
| 937 | 815 | Отводы | 19 |
| 938 | 817 | Офис | 29 |
| 939 | 819 | Охрана | 27 |
| 940 | 821 | Перевозка сотрудников | 19 |
| 941 | 823 | Пожарная команда | 19 |
| 942 | 825 | Терминал | 62 |

Все ссылки проверены по полям owning layer. В каждом архиве form.json,
data.geojson и остальные members сохранены побайтово; другие namespaces meta
сохранены структурно. Перед записью сделаны локальные backups и сверена
precondition ресурса. Readback всех 12 архивов совпал, повторный preview дал
**0 изменений**. Журнал содержит ровно 12 file uploads и 12 PUT только этих
formbuilder_form ресурсов. Ни feature API, ни изменение схемы/данных PostGIS
не использовались. Access frontend/backend и код синхронизатора не менялись.

Резервные копии и журналы находятся локально в
`build/phone-cascade-check/visibility-803`; сырые формы и учетные данные в Git
не добавлены. Для проверки нужен новый APK, sync проекта и новая форма аудита;
старый черновик сохраняет прежние правила.

## APK и границы проверки

Lisa Debug: `com.nextgis.mobile.debug`, **3.1.2.23 / 218**.
Lisa/Belka Release: **3.1.2.27 / 221**; версии не повышались.
Debug подпись проверена apksigner, схема v2.
Локальный тестовый APK:
`build/deliverables/2026-10-08-clear-fields/ngmobile-3.1.2.23-lisa-debug-test.apk`.
SHA-256: `e2515b33eb1fcfec0bea61550e05d41378f6610ceac88bbb0232908936657390`.

Физический телефон не обновлялся; ручной GUI smoke на нём не выполнен.
Native suite не отправляла реальные аудиты и не проверяла сетевой sync нескольких
серверных проектов по настоящему расписанию. Ограничения предыдущей проверки
общей синхронизации сохраняются. Desktop работал в отдельном worktree;
грязный основной checkout параллельного чата не изменялся.
