---
title: Проверка HyperLog, HTTP-шума и синхронизации форм
type: reference
last_verified: 2026-10-10
related_code:
  - maplib/src/main/java/com/nextgis/maplib/util/LocalLogInitializer.java
  - maplib/src/main/java/com/nextgis/maplib/util/LayerFormHashUtil.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - app/src/androidTest/java/com/nextgis/mobile/util/DiagnosticsNoiseTest.java
---

# HyperLog, HTTP-шум и синхронизация форм

Работа продолжена по указанию пользователя в существующих Draft app #54,
maplib #46 и maplibui #34 на `codex/feature-type-empty-lists`. Версии не меняются,
проверочные APK не выпускаются. Рабочий телефон читался через явно адресованный
ADB; приложение и данные на нём не менялись.

## Диагноз и изменение

Пять Background ANR в GlitchTip — historical AppExitInfo из Android 16 Debug
эмулятора. Их main-thread stacks проходят через HyperLog.initialize/pushLogs,
JSON и создание Volley request. В закреплённом HyperLog
`master-0.0.10-g855ebf8-12` initialize всегда вызывает pushLogs и читает сохранённый
URL. Фиктивный loopback адрес не отключает подготовку backlog. Все production
entrypoints теперь используют общий initializer: до первого вызова удаляется
устаревший URL, последующие вызовы сохраняют app-формат без повторного initialize.
Записи и локальный экспорт сохраняются, crash handler продолжает делегировать.

HTTP issue 45 содержал 28 SDK-generated 503 за 1.317 секунды в production MAP.
Это не uncaught crash. Из старого редактированного event установить адрес нельзя;
503 допускается и локальным tile provider при перегрузке. Новый BeforeSend
ограничивает одинаковые HTTP 5xx десятью минутами, сохраняя первый отчёт и только
безопасные status/method/source теги. Fatal, другие ошибки и cached replay не
теряются; транспорт диагностических envelopes и его retry не менялись.

В приложенном логе layer sync завершился, но все три обновления форм были
отклонены до установки: snapshot/download NGFP hash mismatch. Свежая повторная
попытка на подключённом телефоне показала тот же отказ у двух форм, третья прошла.
Прежние формы сохранены; это отдельная ошибка от HTTP 503.

Первый hash использовал сырые form.json bytes. Проверенные официальные
[model.py](https://github.com/nextgis/nextgisweb_formbuilder/blob/bdcdb3b192199d545c2c2539f47f992df8294cf8/nextgisweb_formbuilder/model.py)
и [element.py](https://github.com/nextgis/nextgisweb_formbuilder/blob/bdcdb3b192199d545c2c2539f47f992df8294cf8/nextgisweb_formbuilder/element.py)
показывают legacy export из struct и `legacy_specs`, полученного из set. Порядок
JSON attributes может различаться между workers. Это вероятная причина повторного
несовпадения; два реальных NGFP payload недоступны, поэтому их семантическое
равенство не объявляется доказанным. Исправление канонизирует JSON form/meta по
упорядоченным ключам, сохраняет все значения/массивы/идентичности/правила и старое
исключение верхнего ngw_connection. Hash guards и файловая транзакция не ослаблены.
Старый hash может вызвать однократное безопасное обновление формы.

## Проверки

- JBR 21.0.9, Gradle 9.3.1, compileSdk 36: maplib unit 519/519 и app unit 54/54;
  без skipped/failures. HyperLog проверен на API 26/36 через Robolectric.
- Debug maplibui, Lisa Debug и opt-in test APK собраны. JSON регрессии подтверждают
  равенство при другом порядке ключей/пробелах и отличие при изменении значений,
  правил, lisa_id и порядка массивов; download/unpacked hash согласованы.
- maplibui unit 92/92, включая 4 form transaction/rollback cases; оба validation
  flavor Release APK собраны (Lisa/Belka), production версия остаётся 3.1.2.27/221.
- Изолированный read-only AVD, Android 16/API 36 x86_64, WHPX/SwiftShader:
  seed 6000 строк прошёл (45.067 s); cold start сохранил backlog/формат/handler
  (0.319 s test body, не полное время запуска); SDK 28 HTTP503 + другой источник
  дали ровно 2 redacted envelopes (0.297 s); receiver 503 -> WorkManager retry
  -> 200 прошёл (0.523 s); actual Android JSON hash snapshot/download/staging
  и различение реальных изменений прошли (0.053 s на итоговом guard).
- Первая cold проверка обращалась к несуществующему десятому batch: число
  означает batch, не limit строк. После исправления на batch 1 проверка прошла;
  production код при этом не менялся.
- Документационный validator, 7/7 его tests и строгая проверка списка
  изменённых файлов прошли.

Полный полевой Collector sync после исправления пока не проверен. Рабочий телефон
сохраняет установленную production 3.1.2.27/221. Проверка охватывает алгоритм hash,
а не изменение ресурсов или исправление данных сервера. GUI Collector import,
backup/refill/project switching и полный delivery smoke с настоящим test GlitchTip
не повторялись, потому что соответствующие пути не изменялись.

## Зависимости и включение

После fetch во всех пяти Android owner repositories открыты только собственные
Draft #54/#34/#46; других published codex heads нет. Предыдущие app category,
walk, measurements и track-delivery commits остаются предками текущей ветки;
maplib и maplibui сохраняют прежние коммиты. Ancestry проверена через
`git merge-base --is-ancestor` для app `2c92b353`, maplib `06474004`,
maplibui `d70a5572`. Ничего требуемого не отложено.

| Исправление | Owner / commit | PR / base | Включение |
|---|---|---|---|
| Shared local-only HyperLog, NGFP canonical hash; прежняя track intent migration | maplib `007001c84e49c1aaf908688898cd8740151be020` | #46 / master | Exact app gitlink, open Draft |
| Base initializer; прежние category/walk/track UI fixes | maplibui `dcc4487d4e88614c23bf71ea5d7bcbe28298d97b` | #34 / master | Exact app gitlink, open Draft |
| HTTP policy, app initializer, native checks/docs и прежние creation/measurements/track flows | app `0babee94293839386ba0ef802a118d16e2065344` | #54 / my-maplibre | Код и exact gitlinks присутствуют; open Draft |

## Дополнение: содержимое отчётов и GPS без записи

По явному указанию владельца контракт диагностики 2 сохраняет предоставленные
URL/query, headers/cookies, response status/headers, контекст, значения, пути и
сообщения без маскирования, включая credentials. Размер/глубина ограничены;
потоковые HTTP-тела и business storage для отчёта не читаются. SDK OkHttp hint
позволяет сохранить исходные URL/headers, которые сам SDK иначе скрывает.
Ограничение повторных HTTP 5xx, fatal delegation, cache/retry остаются прежними.
Предыдущие redacted native envelopes выше описывают прежний контракт 1.

При открытии/Resume без трека проверяются Battery Saver GPS policy, пользовательский
запрет фоновой работы и отсутствие исключения из оптимизации батареи. Неблокирующее
предупреждение предлагает нужные системные страницы. Подтверждение хранится после
реального показа/нажатия, переживает rotation только текущего процесса; queued
предупреждение не считается показанным, Pause закрывает его. Новое ограничение
предупреждает снова, снятие ограничений сбрасывает подтверждение. Native тест
обнаружил ранний acknowledgement до показа при переходе lifecycle; это исправлено.

Проверены 58/58 app unit tests, SDK cache с 28 одинаковыми HTTP 503 и другим
источником (ровно два отчёта, адреса сохранены), настоящий SDK capture с исходными
URL/query/Authorization/Cookie и Response/Retry-After. Native Android 16/API 36
проверил запуск без записи, три вида ограничений, маршруты настроек, rotation,
сброс подтверждения и настоящий screen-off/screen-on (22.644 s на итоговом APK); существующие
два TrackPowerWarningTest, включая реальные LocationManager fixes и SQLite,
прошли (46.428 s). GPS после снятия энергосбережения продолжает запись в тот же трек.
Рабочий телефон и его ограничения не менялись; OEM-поведение и полный полевой
Collector sync после установки остаются непроверенными.

Прежний app CI run 38036347049 упал в двух MapEditingToolsTest сценариях обхода.
Оба целевых сценария локально повторены успешно (22.422 s). CI fixture теперь
выдаёт GET_ACCOUNTS вместе с location grants, чтобы обязательное стартовое окно
разрешения не перекрывало UI-тесты; полный удалённый suite требует нового CI run.
Оба Lisa/Belka Release validation APK и Debug/test APK собраны; версии не менялись.
Документационный validator, его 7 tests и strict changed-file проверка прошли.
Обновлены diagnostic/location/settings docs, app pack/contract, registries и две
карточки отчёта. Дополнение остаётся в том же app Draft #54; library gitlinks прежние.

Сначала Merge Commit maplib #46, затем Merge Commit maplibui #34; после fetch
удалённых merge commits обновить app gitlinks, затем Squash app #54 и повторить
closure audit перед выпуском. Текущие gitlinks на Draft heads служат для проверки,
не закрывают release dependencies. Merge, APK publication и publisher изменения
в этой задаче не выполнялись.
