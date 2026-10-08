---
title: Закрытие форм, общей синхронизации и диагностики 8 октября 2026
type: reference
last_verified: 2026-10-08
related_code:
  - .github/workflows/android-reliability.yml
  - tools/verify-apk-version-matrix.ps1
  - maplib
  - maplibui
---

# Закрытие форм, общей синхронизации и диагностики

Пользователь запросил merge всей накопленной задачи, закрытие её веток и
локальный Lisa Release APK без повышения версии или публикации. GitHub identity
`geoglyth` — designated merger. Desktop checkout с пользовательскими изменениями
эталона не редактируется.

## Инвентаризация перед merge

После fetch/prune всех пяти Android owners открыты только app #51, maplib #43
и maplibui #31, все authored by geoglyth, без дочерних PR на их task heads.
Опубликованные codex-ветки есть только для этих трёх PR. У easypicker и
upload_mobile нет открытых PR или опубликованных codex-веток; изменений этих
owners в задаче нет. Дополнительного отложенного predecessor нет.

Desktop docs #168 уже MERGED в `lisa/main`:
`ea8f15d1254c74e450d2faca71dde1ae48aa31f5`. Содержимое пяти файлов PR
сравнено с исходной головой `67418eb0e7ea90ef72e1025d4e684215a88800a2`:
diff пуст. Это документация, каталог публикуемых QGIS-плагинов не менялся.

## Delivery matrix

| Требование | Owner / PR / base | Исходный commit | Включение / merge |
|---|---|---|---|
| Изоляция фоновой синхронизации | maplib #43 / master | `0a2e3e6ba9f8dc289c5e098771e846d135920f11` | ancestor merge `d80df6c9464de5cc2b7cd4501de9db76a38a5c01` |
| Conditional visibility parser | maplib #43 / master | `0cd7d39b8bdfe34d391bdf6c5f118b0709e549a8` | ancestor того же merge; Git tree совпадает с проверенным head |
| Раздельные каскады и жесты | maplibui #31 / master | `251d5e7b939e89171d803b79d8d962ada4dae1d5` | ancestor merge `2bfc5ddb2d66e22e4a041b8ea0bd724d66f1dbe3` |
| Owning workspace и aliases | maplibui #31 / master | `bc4a5198e506fe8208d3836e308ee45973a01aae` | ancestor того же merge |
| Чёткие поля, visibility, сохранение containers | maplibui #31 / master | `111f48a2c0ee0744c9ff8bac0b9d822814ef5458`, `76f417e08bc99f1e270f1a4c4bbc2376839580ad` | ancestor того же merge; Git tree совпадает с проверенным head |
| Каскады и проверка жестов | app #51 / my-maplibre | `89501432211fb2ac93dd630391b85ad2a1b1f1ce` | ancestor app task tip |
| Опциональная общая sync с recovery | app #51 / my-maplibre | `d665e26e09a33f6b7c811715e212fcba7577397f` | ancestor app task tip |
| Чёткие поля и visibility integration | app #51 / my-maplibre | `eddec108ea286d35b5bfe0c819b42d0a125b73b5` | ancestor app task tip |
| Исправление азимута / foreground failure | app #51 / my-maplibre | `e18a8db586f92a310b3f3cf4286175739bc51dd6` | ancestor app task tip |
| Offline багрепорты / WorkManager / 503 retry | app #51 / my-maplibre | `df9894bbc089765c1ce6580afce484f3956c2399` | ancestor app task tip |
| Fetched library pins и самостоятельный GPS denial CI | app #51 / my-maplibre | заключительный merge-prep commit этого PR | проверяются перед Squash и по remote tree после него |
| Desktop/mobile документация | lisa #168 / main | squash `ea8f15d1254c74e450d2faca71dde1ae48aa31f5` | уже в remote main; содержимое PR сохранено |

Служебные app commits `1a61f1a` и `98589a1` с предыдущими результатами также
являются ancestors task tip. Предыдущая цепочка #42/#30/#50 уже в baseline;
новая parallel/integration branch не создаётся.

## Порядок и приёмка

1. maplib #43 Merge Commit, fetch remote master, проверить ancestry и tree.
2. maplibui #31 Merge Commit, fetch remote master, проверить ancestry и tree.
3. App закрепляет оба remote merge commits; library heads уже не являются pins.
4. После проверок app #51 Squash в my-maplibre. Fetch, сравнить app tree с
   подготовленной головой: ancestry после squash не доказывает включение.
5. Перейти на default branches, fast-forward, удалить только merged task refs.
6. На clean remote app tip выполнить version matrix и проверить Lisa Release
   metadata, подпись и diagnostic source revision. Publisher не запускается.

Версии сохраняются: Lisa Release и Belka Release `3.1.2.27` / `221`, Debug
`3.1.2.23` / `218`; maplib BuildConfig.VERSION_NAME соответствует variant.
Итоговый receipt с точными remote tips, SHA256 APK и результатами команд
сохраняется локально в `build/merge-closure-2026-10-08/`, статус — в app PR #51.

## Проверки до заключительного commit

CI run `37768334136` собрал source sets, unit suites и успешно завершил native
suite: XML содержит 87 tests, 0 failures/errors, один ожидаемый skip для GPS
denial. Затем workflow упал на revoke с package not found: UTP удалил APK после
connected suite. Исправлен harness — перед standalone GPS denial оба пакета
устанавливаются повторно, возвращаются storage/notification permissions.
Отдельный denied-location сценарий ранее прошёл локально после фактического
отзыва fine/coarse; исправленный workflow должен подтвердить это в CI.

Доставка багрепортов из clean `df9894b` подтверждена отдельным API36 emulator:
handled event `e51195a90c5340198239c5a2e4fe88c3` и fatal event
`016b7ede73744a03a50abdc2d1a5ebbb` получены test GlitchTip со стеком,
source_revision, app/device/os и breadcrumbs. Production project не использовался
для synthetic сообщений. Подробности: [проверка диагностики](error-reporting-verification-2026-10-08.md).

Физический Android15, реальные NDK crash/ANR, Doze и scheduled sync нескольких
настоящих проектов остаются непроверенными device smoke. Локальный Release APK
и автоматические проверки не подменяют эти проверки.
