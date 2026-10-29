---
title: GlitchTip — мобильные ошибки и уточнение диагностики
type: reference
last_verified: 2026-10-29
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsGpsPlatform.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsGpsFixWitness.java
  - app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
  - app/src/main/java/com/nextgis/mobile/util/SyncJournalCommit.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTracePolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsNativeTrace.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsExitInfo.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporter.java
  - maplib/src/main/java/com/nextgis/maplib/location/GpsEventSource.java
  - maplib/src/main/java/com/nextgis/maplib/location/GpsRecoveryPolicy.java
---

# Разбор мобильных событий 29 октября 2026

По новому поручению пользователя прочитаны все страницы событий и комментарии
мобильных групп. Первый срез содержал пять открытых групп: 520 серверных записей,
516 уникальных SDK event ID и31 новый ID относительно тех же групп28октября.
После перерыва получен и полностью разобран свежий snapshot02: **8 открытых
групп, 593 серверные записи, 589 уникальных SDK event ID**, export complete=true.
В этих же восьми группах прежний проверенный срез28октября содержал534записи/
530ID, поэтому итоговая дельта — **59 новых записей и59 новых SDK ID**.
Прежние payload сохранены и не изменились; сравнение выполнено по ID и
содержимому, а не только по count. Возврат в список открытых уже известных
групп не превращает их старую историю в новые события.
Число записей, стадии одного GPS-эпизода и повторная доставка одного SDK ID
не означают столько же падений или пользователей.

Разбор ограничен mobile. Проверка другого проекта остановлена до изменений.
Сырые события, журналы, клиентские идентификаторы и точные пути остаются вне Git;
этот документ содержит только безопасные выводы. Предыдущая обработка 13 групп
и независимый readback 28 октября описаны в
[инциденте 224](mobile-crash-224-2026-10-27.md#итог-обработки-и-незавершённый-полевой-профиль-28-октября-2026).

## Решения по группам

Решения ниже подготовлены для индивидуальной обработки. **Новые комментарии
и закрытие групп ещё не выполнены**; перед ними необходимы свежий guard
count/lastSeen и чтение новых поступлений, после — независимый readback.

| Группа | Сейчас / новых записей | Установлено | Подготовленный исход |
|---|---:|---|---|
|45, HTTP 503|201 / 6|Четыре новых события222 не имеют различающего reason; два223 содержат согласованные queue_full tag/map_runtime с16queued и2active workers. Причина исторического большинства503 неизвестна; всё семейство не является штатным шумом.|`diagnostics`: прежняя узкая политика применима к подтверждённому backpressure subset, неизвестные и remote5xx сохраняют отчёт.|
|64, очередь синхронизации|75 / 7|Все семь — 222/3.1.2.28, revision83fb8c83…, contract2. Только общий IOException без cause/phase/sync_journal; часть старых envelope доставлена с задержкой.|`diagnostics`: применимы существующий owned writer и сбор исходной причины; причина каждого исторического отказа не доказана.|
|68, acquisition|37 / 1|Новое223/schema2 delayed: raw/callback около9,8с, status отсутствует, used0, retry не было. Во всей группе acquisition; у15 старых записей нет structured episodeID, поэтому12 известных episodes — не полный их подсчёт.|`already-fixed`: прежняя политика оставляет обычное acquisition локально; приём GPS этим не объявляется исправленным.|
|69, SDK SIGABRT|6 / 1|Поздний envelope030f303/224, создан28октября12:35UTC, доставлен29октября04:54UTC. Символический профиль указывает на worker rethrow/terminate, исходный C++ exception неизвестен.|`diagnostics`: применимы существующий bounded AUTO trace и сохранение исходной identity; подтверждённого устранения abort нет.|
|88, no_fix|97 / 13|Все223/schema2. Во втором срезе добавились4delayed сused0 и3recovered; обычное отсутствие решения не устанавливает ошибку приложения.|`already-fixed`: прежняя политика оставляет NO_FIX и его окончания локально; это исправление отправки, не приёма GPS.|
|90, provider_silent|119 / 13|Свежая дельта содержит5пар delayed/recovered, в том числе4эпизода224/schema3 наSM-A566B. Main observer жив, callbacks/status отсутствуют при доступных permissions/provider; причина неизвестна.|`diagnostics`: нужен system cached-fix timestamp для проверки границы framework/app, без подавления неизвестного симптома.|
|96, callback_stalled|53 / 17|Ранее прочитаны13новых224/schema3, включаяT40 сrevision5e85446…+dirty; во втором срезе добавились2пары223. Retry/cancelled не доказывают исправление, прежний симптом224 сохраняет значимость.|`diagnostics`: не объявлять устранённым прежним timer fix; добавляется пассивное наблюдение framework cache.|
|117, OS exit|5 / 1|Поздний030f303/224 report reason2/status6 согласуется с69 по версии/устройству/времени; SDK PID отсутствует. RSS около1,34GiB не доказывает OOM.|`diagnostics`: текущий reason2 AUTO trace уже реализован и проверен JVM; новые данные нужны для установления причины.|

В группе64 всего72уникальных SDK ID, в45 —200; три записи64 и одна45 —
повторные серверные записи уже известных SDK ID. Подготовлены **6 исходов
diagnostics и2 already-fixed**. Ни fixed, ни blanket noise для неизвестной
причины не заявлены. SDK и OS сообщения69/117 нельзя автоматически считать
двумя независимыми падениями.

## Что новые события исключают и чего не доказывают

В новых событиях64 перед ошибкой есть отказ входа в foreground service и уничтожение
сервиса при ещё активном sync adapter. Это согласуется с кандидатом отмены
caller; interrupt state в этих событиях не записан. Device snapshot показывает
60–95 ГБ свободного места, но не результат конкретного commit. Нельзя объявлять
все отказы ни отменой, ни заполнением диска. Уже реализованный SyncJournalCommit
дожидается записи на собственном потоке и возвращает флаг отмены caller.
enqueueOrThrow сохраняет cause, этап, размеры и доступное место; настоящий
commit=false остаётся ошибкой. Очередь не очищается и commit не заменяется apply.

Первые три новых события45 и одно последующее222 не имеют явного reason ответа.
Два последующих223 содержат queue_full одновременно в tag и map_runtime:
16queued,2active workers, в снимках доступно около354/418МБ Java heap.
Это подтверждает конкретный backpressure subset, не общую причину группы.
Существующие оптимизации cache/queue снижают известные расходы карты, но сами
не доказывают устранение исторических HTTP503.
Узкий фильтр применяется только при подтверждённом local tile GET и известном
reason самого ответа; неизвестные, remote и прочие реальные ошибки сохраняются.

В новых224событиях90/96 уже получен прежний platform context: разрешения и provider доступны,
ограничение background/power-save не показано; observer Main около 500 мс,
sleep gap мал или нулевой в момент снимка. Это ослабляет эти гипотезы для
наблюдённых интервалов, но не проверяет каждую предыдущую секунду, OEM routing
или наличие нового готового position fix.

На T40 gnss_started=false встречается и в recovered с first_fix_ms и живым
satellite status. Поле отражает полученный onStarted/onStopped, поэтому не
доказывает остановку чипа или регистрации. Флаг
[GnssStatus.usedInFix](https://developer.android.com/reference/android/location/GnssStatus#usedInFix(int))
относится к последнему position fix; сам satellite flag не доказывает свежую
доставку Location. Для вывода нужен timestamp самого fix, с учётом поведения
API 30/vendor. Не утверждается, что любое устаревшее used-состояние нормативно.

Timer уже отсчитывает непрерывные 30 с callback_stalled; новые initial retry
snapshots показывают 30,1–30,3 с. Возврат requestLocationUpdates означает возврат
из запроса регистрации, а не получение следующего fix. В одном новом эпизоде
до первого fix input=0, затем input=1; в другом input остаётся неизменным до
снятия владельцев. Снятие владельцев и cancelled не считаются recovery.
Обычный NO_FIX/ACQUISITION/FILTER_WAIT остаётся локальным по прежней политике.

Во втором срезе четыре новых эпизода224 наSM-A566B имеют паузы callback около
80,7–85,5с и status75,5–75,8с при observer501–513мс и sleep0–1мс.
Парное восстановление наступило через82,3/125,4/264,6/614,4с ожидания. Это
подтверждает длительную паузу, но не выбирает между RF, provider/OEM и
границей доставки приложения. Ещё четыре записи96 относятся к двум эпизодам
старой223, а не к новому повтору224 наT40. Ни одного schema4 witness в срезе нет.

## Поздняя пара native/OS отчётов69/117

Оба новых envelope созданы28октября около12:35UTC на030f303/224 и доставлены
29октября04:54UTC, более чем через16часов. Они согласуются по source/device/
времени и признакам SIGABRT; SDK PID не передан, поэтому связь не является
самостоятельным доказательством идентичности процессов. Это не новый abort
установленной57a/source5e сборки и не доказательство регрессии новых ускорений.

Повторно использованы существующие проверенные официальные символы
MapLibre13.7.1OpenGL. Те же relative PCs разворачиваются в ThreadedScheduler
worker lambda → `__cxa_rethrow` → terminate/default handler → abort_message.
Исходный тип/what C++ исключения отсутствует; ни OOM, ни driver defect не
установлены. Новые envelope не содержат image BuildID; сопоставление основано
на известном provenance030binary и совпавшем relative-PC профиле, без нового
доказательства image identity. RSS1405176KiB (~1,34GiB) не устанавливает OOM.

Старая030сборка оставляла reason2 trace как not_available_for_reason. Текущий
DiagnosticsTracePolicy уже запрашивает bounded AUTO trace и различает native,
возможный прежний recovered-ANR и missing/unavailable/unknown. Связь
may_precede_exit сохранена. Это сбор недостающего материала, не устранённый
abort; соответствующие существующие проверки повторены в этом этапе.

## Новое наблюдение вместо предположения о причине

Для групп 90/96 в исходники добавлено редкое чтение
[LocationManager.getLastKnownLocation(GPS_PROVIDER)](https://developer.android.com/reference/android/location/LocationManager#getLastKnownLocation(java.lang.String))
при уже отправляемом GPS-report. Оно читает сохранённый framework fix и не
включает датчик, не создаёт новую подписку и не подаёт Location обратно в
processLocation. В отчёт передаются только enum и числа: статус/время чтения,
монотонный timestamp и возраст cached fix, сравнение с последним app raw fix.
Координаты, speed/bearing, provider extras, URI и объекты Location не сохраняются.

Более новый framework fix при старом app raw сужает поиск до доставки,
подписки или OS routing, но ещё не выбирает виновника. Старый, отсутствующий
или недоступный cache не доказывает поломку callback channel: остаются
provider/RF/acquisition и другие причины. App raw может включать startup cache
seed; он не называется точным временем входа listener callback.

Чтение fail-open, редкое и без новых потоков подписки; синхронный Binder getter
**не имеет гарантированной верхней границы времени исполнения**. Отказ
дополнительного наблюдения не должен отменять исходный report. Объём полей
ограничен десятью ключами в `platform.gps_fix_witness`: schema_version,
read_status, probe_started_elapsed_realtime_ms,
probe_finished_elapsed_realtime_ms, probe_duration_ms,
cached_fix_elapsed_realtime_ms, cached_fix_age_ms, app_raw_timestamp_status,
cache_vs_app_raw и cache_minus_app_raw_ms. Отказ разрешения/чтения, отсутствующее
решение, некорректное или будущее время не объявляются свежим fix.
Schema 4 добавляет `raw_fix_elapsed_realtime_ms`; старые snapshot без него
остаются допустимыми, отсутствующий raw не превращается в timestamp ноль.

Сбор не меняет 8 с freshness, фильтры записи, внешние источники, Network,
точность/интервал, владельцев GPS, wake lock, thresholds/rate limits,
доставку точек, phase grouping или saved-state. Исправление причины
provider_silent/callback_stalled этим изменением **не заявляется**.

## Проверки и доставка

Проверки исполнены: **877 JVM tests PASS,0fail/error/skip**. В первом batch
Gradle-фильтр применился только к app: библиотечная задача фактически выполнила
все803maplib tests/134класса, app —54адресных tests/8классов. Отдельно повторены
16проверок NativeTrace/ExitInfo/ProcessExitReporter в3app классах и4TracePolicy
cases. Это Java/Robolectric проверки, не877native или полевых испытаний.
Docs50tests PASS. Consumer LisaDebug/LisaRelease/BelkaRelease и компиляция
instrumentation Java PASS101с; version matrix PASS.

Тесты покрывают timestamp arithmetic, ошибки/permissions/clock, старую schema
без raw, fixed numeric wire contract после sanitize/replay и privacy на API30/36.
В двух существующих isolated native GPS cases добавлены schema4 и независимый
timestamp технического cache seed. Эти два emulator-only case **не запускались**;
их компиляция не является native приёмкой. RF/vendor callback stall наT40 не
воспроизведён, его причина остаётся UNKNOWN. Нового emulator/native/Full,
профилирования и действий с устройствами не было.

Собран и отдельно сохранён обычный частный LisaAPK224/3.1.2.30,
70580085байт, SHA256
`517c941d7054cf4e789ae2f7513ddc1072e7b8fdb2797fdd6854725430db8b01`.
DEBUG/FIELD_VERIFICATION/PRIVATE_RESOURCE_PROFILING false, profileable отсутствует,
исходная подпись сохранена. APK **не установлен и не опубликован**.
Runtime fingerprint1659файлов
`f0ef5d2f41d0f18a9de8d00b908bfcb518c8976ebdddb5ae8ad33efd48dfa586`
совпал до/после сборки. SOURCE_REVISION
`f3068cf0f917d3bb7cdc07823f0989ee667a2d7b+dirty`: сборка предшествует текущему
source commit. Runtime bytes подтверждены после source commit; source/Git и
серверный итог закрыты отдельной независимой квитанцией ниже.
Версии и форматы проектов не меняются.

Уже установленный ранее обычный private APK 57a49585…10984bd имеет
224/3.1.2.30 и SOURCE_REVISION 5e85446…+dirty; profiling/verification выключены.
События 96 с такой ревизией нельзя назвать старыми 223, но revision с +dirty
сам по себе не доказывает равенство APK bytes: хеша установленного APK в
событии нет. Уже известный build не является результатом новой диагностики и
не содержит вновь добавленный schema4 cache witness.

Работа продолжается в существующих собственных Draft
[app 84](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/84) и
[maplib 62](https://github.com/GeonicalSys/android_maplib/pull/62), roadmap
MOB-02/MOB-03. [Новая внутренняя карточка](../../update-reports/entries/android-20261029-glitchtip-gps-evidence.md)
перечисляет точные app source/test paths. App pin остаётся на ранее merged maplib fe93eb3c… до отдельного
слияния библиотеки. Новый library working tree не объявляется закрытой
release-зависимостью. Merge, новая установка и публикация требуют отдельного
поручения; завершённый разбор и resolve не означают выпуск или исправление GPS.

Приватные raw события/комментарии и компактные проверки delta удерживаются
в task evidence на Q до итогового readback и пересмотра хранения по manifest.
События не удаляются. Полный порядок:
[runbook](../runbooks/glitchtip-triage.md).


## Фактический итог обработки

После source checkpoint app `b96dec3174ecd936ca0a49e626c4b4724c06b76d`,
maplib `13887f34f095a0627a908db637aa0b09736d6c36` и canonical contracts
`010451dbd35136458c4947d45f350647b299f53a` изменения отправлены в существующие
Draft PR84/62/274. Root generated index write/check и pre-push CURRENT;
source impact exit0, static completeness остаётся UNKNOWN с прежним legacy
geo.json/dynamic gap. Это не возобновление общего аудита и не desktop работа.

Свежий snapshot03 добавил четыре GPS-события к snapshot02: всего597записей/
593SDK ID. Во время guarded close группа96 получила ещё одну старую223
delayed/recovered пару. CLI отказал **до комментария**, оставив её открытой.
Все55прежних payload и три комментария были неизменны; два новых события
прочитаны отдельно, outcome diagnostics сохранён. Повтор с57-событийным
snapshot04 добавил ровно один комментарий и закрыл только прочитанную группу.

Итог обработанного окна — **8 групп,599записей/595SDK ID**; относительно тех
же восьми групп исходного среза28октября534/530 это65новых SDK ID.
Счётчики не означают число пользователей или независимых падений. Парные GPS
стадии и коррелированные SDK/OS отчёты69/117 не суммируются как независимые сбои.

|Группа|Записей|SDK ID|Исход|Новый комментарий|
|---|---:|---:|---|---:|
|45|201|200|diagnostics|86|
|64|75|72|diagnostics|87|
|68|37|37|already-fixed|88|
|88|98|98|already-fixed|89|
|90|120|120|diagnostics|90|
|96|57|57|diagnostics|93|
|69|6|6|diagnostics|91|
|117|5|5|diagnostics|92|

Восемь независимых canonicalCLI show после закрытия подтвердили resolved,
точные eventID sets, неизменность всех payload и ровно один новый комментарий
каждой группы. Текст совпал с canonical outcome prefix и подготовленным телом;
повторных/чужих новых комментариев не было. Финальный read-only list пуст,
list --all подтвердил34resolved/0unresolved. События/комментарии/архивы не удалялись.
Это состояние в контрольном окне, не обещание отсутствия будущих поступлений.

Закрытие шести diagnostics означает завершённый разбор и конкретное улучшение
следующих свидетельств, **не доказанное устранение GPS/native/записи/503**.
Два already-fixed относятся к существующей политике штатных GPS-ожиданий,
а не к исправлению спутникового приёма. Новый passive witness требует обновления
APK; частная сборка517c941d…db8b01 сохранена локально, не установлена/не опубликована.
Нового merge, версии, server deploy/restart, cloud dispatch или desktop изменений нет.

Приватные raw exports, полные comment/readback receipts и hashes остаются наQ
в `glitchtip-triage-20261029-01a1817f`; пересмотр хранения5ноября по manifest,
без автоматически запущенного мониторинга или удаления. Исходники, этот отчёт
и компактные доказательства сохраняются; shared SDK/Gradle/Maven/symbol caches
повторно использованы без новых runtime copies.
