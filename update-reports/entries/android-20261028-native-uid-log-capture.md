---
id: android-20261028-native-uid-log-capture
area: Диагностика native проверки карты
kind: internal
at: 2026-10-28T05:42:00+01:00
title: Непрерывный ограниченный журнал только verification UID
why: После NativeSQL assertion необходимо сохранить сообщения SDK с самого старта instrumentation, а поздний dump может уже не содержать исходную ошибку
covers:
  - tools/mobile_native_log.py
  - tools/tests/test_mobile_native_log.py
---

MOB-04. Новый test-tools helper `start_native_log_capture(adbPath, serial,
outputFile)` допускает только fixed package `com.nextgis.mobile.verification.debug`.
Read-only `am get-current-user` определяет точный Android user, затем
`cmd package list packages --user <id> -U <fixed-package>` читает только его
пакеты. Android substring filter законно возвращает instrumentation sibling
`.debug.test` вместе с `.debug`: выбирается ровно одна exact app строка;
разрешён только этот известный sibling, его UID не захватывается. Duplicate/
conflicting/missing exact package, lookalike/другие строки, системный appId,
UID другого user и malformed/oversized/permission-error output отклоняются.
Разделение UID/user/appId сверено с установленным Android36 SDK UserHandle/
Process, current Android user сохраняется в metadata. Все subprocess
аргументы передаются отдельным argv; Windows использует CREATE_NO_WINDOW.
Command — только `logcat --uid=<uid> -T1 -vthreadtime`, без whole-system dump,
очистки журналов, app interactions, remote writes или ADB-server stop.

Owned host output на Q не перезаписывается. Первые2MiB и последние2MiB сохраняются
в одном файле с пределом4MiB; tail работает как disk ring, при stop один раз
приводится в хронологический порядок. Частая полная перезапись2MiB при каждом
chunk не нужна. Caller обязан вызвать `capture.stop()` в finally; он останавливает
только свой host logcat client, bounded wait→kill этого же client при необходимости,
join/close reader и output handles. Метаданные фиксируют actual uid/path/start-stop,
seen/retained/dropped bytes, layout/cursor, return code, reader/file closure,
fixed stage/error types. Reader/finalize/stop failure сохраняет partial file и
явный diagnostic_failure; исходный assertion не превращается в PASS.

Start UID/process/reader failure поднимает `NativeLogStartError` с fixed code/type,
без ADB output/exception message. Stop idempotent, возвращает независимый snapshot
metadata. Unexpected ранний выход client виден отдельно. Если reader не остановился
после bounded join, это явно отражается; полного успешного shutdown не выдумывать.
UID filter не гарантирует получение system tombstone/debuggerd строк другого UID.
Это дополнение к OS/Sentry collector, не его замена и не доказательство причины224.

Первый набор10/10 Python fake-process unit cases прошёл за0.046s, но не
моделировал реальный substring-filter protocol. В Native05 все9 capture starts
завершились diagnostic failure: прежнее предположение «весь output — одна
строка» оказалось неверным. Read-only phone proof root показал sibling UID10428,
app UID10426 и Samsung refusal при попытке читать user10 без explicit --user.
Это дефект capture admission, не новое приложение-падение и не разрешение на
whole-system fallback.9 native cases отдельно сохранили4PASS/5FAIL.

Уточнённый mock набор14cases включает actual sibling-first/two-line output,
оба порядка строк, current-user0/10, недоступный профиль, invalid current user,
missing/duplicate/conflicting exact UID и sibling-only output.14/14cases PASS
за0.061s,0failures/errors; `evidence/native-uid-log-user-units01.log`.
Сохранены проверки реального4MiB bound и точных first/last bytes, malformed/другого/lookalike
UID, невмешательство в existing file, blocked reader join, timeout→owned kill,
read/finalize/start errors с partial retention и handle closure, повторный stop.
Все subprocesses в unit suites fake: **в этом source/test batch реальных
ADB/native/Gradle/Git действий не было**. Новый live capture после protocol fix
ещё не проверен; исторические Native05 failures сохранены.
Лог исходного набора: `evidence/native-uid-log-units01.log` текущей active задачи на Q;
protocol follow-up preplan: `native-uid-log-user-impact01.json`, UNKNOWN/
REFRESHED_IN_MEMORY, только3owned paths. Root owns runner metadata/error-code
integration и обновление остальных module docs; здесь их не перезаписывали.
Runner integration/Related продолжение выполняется отдельным root batch;
native device acceptance и получение нового полного стека остаются непроверенными.
Production APK/data/sync/geometry/style/versions не меняются, миграции нет.

Whole-contour preplan `native-uid-log-impact01.json`: UNKNOWN/REFRESHED_IN_MEMORY,
compact receipt без duplicate fullplan. Dirty foreign desktop index не изменялся;
root delivery follow-up обязателен. Semantic closure: verification runner finally
→ fixed UID query/logcat capture → bounded host file/metadata → native failure
evidence. Новые app README/manifest entries сохранены; shared SDK/ADB уже установлен,
дополнительные runtime/download не создавались. Unit temporary files имеют normal
TemporaryDirectory finalization на Q, compact receipts сохраняются отдельно.
