---
title: Проверка общей синхронизации и подписей NGFP
type: history
last_verified: 2026-10-07
related_code:
  - app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java
  - app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
  - maplib/src/main/java/com/nextgis/maplib/util/SyncWorkspaceSession.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/CascadingFormElements.java
---

# Проверка общей синхронизации и подписей NGFP

Это запись локальной проверки веток `codex/mobile-form-sync`. Изменения
закоммичены для Draft PR; библиотеки пока не слиты в `master`, приложение —
в `my-maplibre`. Тестовый APK не является опубликованным выпуском.
Действующий контракт находится в
[архитектуре синхронизации](../architecture/ngw-sync-and-storage.md),
действия пользователя — в
[инструкции](../guides/project-synchronization-user-guide.md).

## Матрица доставки

| Требование | Владелец и commit | PR / base | Присутствие в проверенном app tip |
|---|---|---|---|
| Явный владелец базы, provider URI, callbacks, отмена | maplib `0a2e3e6ba9f8dc289c5e098771e846d135920f11` | [#43](https://github.com/GeonicalSys/android_maplib/pull/43), `master`, Draft | Точный gitlink в `d665e26` |
| Раздельные списки и жесты | maplibui `251d5e7b939e89171d803b79d8d962ada4dae1d5` | [#31](https://github.com/GeonicalSys/android_maplibui/pull/31), `master`, Draft | Предок `bc4a5198`, проверен через ancestry |
| Псевдонимы NGFP, фоновые Collector/form/schema, журналы по проектам | maplibui `bc4a5198e506fe8208d3836e308ee45973a01aae` | [#31](https://github.com/GeonicalSys/android_maplibui/pull/31), `master`, Draft | Точный gitlink в `d665e26` |
| Проверка прежних исправлений формы | app `89501432211fb2ac93dd630391b85ad2a1b1f1ce` | [#51](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/51), `my-maplibre`, Draft | Предок `d665e26`, проверен через ancestry |
| Общая очередь, настройка default ON, восстановление и документация | app `d665e26e09a33f6b7c811715e212fcba7577397f` | [#51](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/51), `my-maplibre`, Draft | Реализация в проверенном tip |

Инвентаризация открытых PR и опубликованных `codex/*`, не включённых в основные
ветки, показала только эту цепочку. `easypicker` и `upload_mobile` не менялись,
отложенных обязательных предшественников нет. Ранее слитые maplib #42,
maplibui #30 и app #50 остаются предками этих tip.

Порядок завершения: maplib #43 **Merge Commit** → maplibui #31 **Merge Commit** →
fetch и замена обоих gitlinks приложения на удалённые merge commits → app #51
**Squash Merge**. До этого выпуск и публикация заблокированы незакрытыми
зависимостями. Desktop, Access и серверные данные в этой задаче не менялись.

## Среда и результаты

- Windows, JDK `21.0.9+14787801-b1163.94`, Gradle `9.3.1`.
- Изолированный `emulator-5554`: Android 16 / API 36. Рабочий телефон
  `R5GL130K54D` с новым несохранённым черновиком не обновлялся и не перезапускался.
- `:maplib:testDebugUnitTest`: **501** тест, без ошибок и пропусков.
  Десять workspace-тестов включают SDK 26/36, одинаковые имена слоёв в разных
  SQLite, просроченный URI, отложенный callback, билет сервиса и отмену.
- `:maplibui:testDebugUnitTest`: **90** тестов, без ошибок и пропусков.
- `:app:testLisaDebugUnitTest`: **41** тест, без ошибок и пропусков.
- Native suite `com.nextgis.mobile.reliability,com.nextgis.mobile.util`:
  **77** тестов прошли. В том числе alias из NGFP metadata, сохранение черновика,
  ручная/автоматическая очередь A/B/C, отказ B с продолжением C, отмена,
  точечный retry, две учётные записи и разделение Collector journals.
- После последней правки общих уведомлений очереди повторены **6** native
  тестов `ProjectSyncRunnerTest`: все прошли. Настоящий `LayerFillService`
  импортировал локальный GeoJSON только в SQLite фонового проекта.
- `:maplibui:assembleDebug`, `:app:assembleLisaDebug`,
  `:app:assembleLisaRelease`, `:app:assembleBelkaRelease` и test APK собраны.
- Строгая проверка changed-file документации и `tools/docs-check.ps1 -RunTests`
  прошли; семь тестов документационной системы успешны.

Account-pass в native очереди подменяет сеть синтетической работой; registry,
SQLite, ContentProvider, handlers, журналы и сервис импорта используются реальные.
Автоматический сценарий вызывает тот же runner с `manual=false`, без ожидания
настоящего интервала Android scheduler.

## APK

Метаданные прочитаны через `aapt`, а не из имени файла:

| Вариант | Package | versionName / versionCode |
|---|---|---|
| Lisa Debug | `com.nextgis.mobile.debug` | `3.1.2.23` / `218` |
| Lisa Release | `com.nextgis.mobile.geonical` | `3.1.2.27` / `221` |
| Belka Release | `com.nextgis.mobile.geonical` | `3.1.2.27` / `221` |

Версии не повышались. Debug APK проходит `apksigner verify`, схема v2.
Локальная копия:
`build/deliverables/2026-10-07-all-project-sync/ngmobile-3.1.2.23-lisa-debug-test.apk`.
SHA-256: `87B0D5AD1D4901A97118A39811E4D93F474CCA82D76DBA0D1F9CD52402961CB7`.

## Ещё не проверено вручную

Реальный сетевой sync нескольких WebGIS-проектов и нескольких серверных accounts,
запуск по настоящему расписанию, принудительное завершение процесса посреди
записи, тяжёлый pull и полный renderer/GUI smoke на физическом телефоне.
Шаги перечислены в [device smoke](../runbooks/device-smoke-tests.md).
Успешные synthetic-тесты и сборки не означают прохождение этих сценариев.
