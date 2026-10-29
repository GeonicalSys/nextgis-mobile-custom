---
title: Доставка диагностических отчётов
type: architecture
last_verified: 2026-10-28
related_code:
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTransport.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsRetryWorker.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsPolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsPayloadLimiter.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporter.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTracePolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsNativeTrace.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsBackgroundLocationStart.java
  - app/src/main/java/com/nextgis/mobile/location/BackgroundLocationController.kt
  - app/src/main/java/com/nextgis/mobile/location/BackgroundLocationService.kt
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsBackgroundLocationStartTest.java
  - maplib/src/main/java/com/nextgis/maplib/util/LocalLogInitializer.java
---

# Доставка диагностических отчётов

## Локальные фазы SQLite rendering — 2026-10-28

`MapRuntimeDiagnostics.SqliteScope` даёт обычной приватной сборке локальный
logcat tag `LISA-SqlitePhase` через существующий bounded writer. Отдельные
интервалы охватывают IO queue, layer lock, cache read/write, gate, RTree query,
attributes/BLOB read, full geometry decode, style, clip, polygon/line label,
PBF encode/finish и IO→Main dispatch. У активной операции виден текущий этап;
provider return, SDK return, cancellation и failure считаются отдельно.
Geometry-only query, initial getCount/prefill, refill и full getBlob/PFD read
измеряются в BLOB_READ; attributes-only query/materialization остаются в
SQL_ATTRIBUTES. Новые inline_blobs/pfd_blobs считают полные buffers, отданные
decode; BLOB_BYTES сохраняет прежний логический объём consumed originals.
Window может prefetch до32 small BLOB до отмены, поэтому эти counts не являются
физическим объёмом SQLite IO или суммой всех prefetched bytes.
`returned_to_sdk` означает возврат Java provider, а native content ready остаётся
`unverified`. Подтверждён read hot path отдельных scopes; полная задержка
планшетной сцены и эффект small-BLOB кандидата остаются отдельной проверкой.

Контракт ограничен16 активными scopes,32 layer summaries и одним observer.
Локальная запись производится не чаще10s/слой; финальный suppressed outcome
получает отложенный flush. Saturation явно учитывает потерянные наблюдения,
завершённые summaries переиспользуются после throttle interval. Clock failure
обозначает недоступное timing, а не0ms. Сбой clock/writer/watch не меняет
rendering/cancellation и не удерживает завершённый scope.

В scope только фиксированные phase/count enums, числовые layer/source/content
IDs, zoom без tile X/Y, counts/bytes и длительности. URI, координаты, значения
полей, error messages и произвольные phase strings не принимаются. Новых
SQLite/geometry/memory/MapLibre reads для диагностики нет; Main получает только
записанные счётчики. Существующий16KiB private snapshot сохраняет прежнее
владение process/version; отдельного сетевого отправителя или настройки нет.

Известная граница первого tablet capture: слой, у которого все scopes попали
под cap16 до первого наблюдения, может остаться с `last=null`; тогда его
счётчики ещё не публикуются. `layer_totals` — сумма только опубликованных
слоёв, не глобальный denominator процесса. В cold capture3 native terminal
snapshots не показывают всю работу. Граница сохранена в evidence; исправление
диагностики требует отдельного scoped изменения. Duration уже наблюдённого
scope остаётся фактом, но отсутствие записи не означает отсутствие работы.

## Обработанный отказ фонового GPS — группа118, 2026-10-28

Событие118 от27 октября17:03:22.891UTC относится к223/5050495 на Samsung/Android16,
имеет `failure_kind=handled` и цепочку SecurityException/RemoteException.
MainActivity уже получила stopped за48ms до отказа `startForeground`.
Это свидетельство гонки eligibility при доставке pending service launch,
а не подтверждённое падение или OOM. Отказ уже перехватывался сервисом.

К прежнему допущенному handled LOCATION report добавлен `background_gps_start`
schema1. В нём только фиксированный `stage`, монотонный `request_age_ms` и два
снимка `request`/`failure`: `visible_activities`, `resumed_activities`,
`fine_permission`, `coarse_permission`, `background_permission`,
`process_importance`. Снимок request создаётся до `startForegroundService`,
failure — в существующем catch. Request хранит независимые scalar значения,
не Context/Activity и не живой изменяемый lifecycle снимок. По нему можно увидеть
остановку экрана или изменение разрешений между запросом и promotion.

Допустимы только пять этапов: launch, notification, start_foreground,
acquire_background_updates, schedule_permission_check. Неизвестные stage,
утраченный owner/request, отрицательный clock delta и недоступные отдельные
платформенные сведения отмечаются `unknown`; до API29 background permission
имеет `not_required`. Нет координат, SQL, accounts, Intent payload или файлов
проекта. Context прикладывается в отдельном временном SDK scope и не остаётся
у соседних событий; sanitize/replay сохраняет его с диагностическим приоритетом
в прежнем общем16KiB бюджете. Отдельной категории/отправки событий нет.

Наблюдение не принимает решений: FGS type, разрешения, eligibility, promotion,
pending-cancel fulfillment, START_NOT_STICKY, session/GPS lease cleanup и
политика повторов сохраняются. Существующий permission-gated report и его
rate limit тоже прежние. Если optional witness недоступен, исходный отчёт
не должен теряться. Это улучшение сбора, не доказанное устранение Android/OEM
отказов. Нужен следующий полевой report после установки соответствующего APK.

Технические unit regressions проверяют delayed promotion после сообщённого
onPause/onStop, отдельные permission states/unknown/API28, scoped handled
delivery и сохранность witness при настоящем sanitize с большим соседним
payload и повторным replay. Они не запускают Android FGS и не являются native
приёмкой гонки. Результат исполнения и факт выпуска фиксируются отдельно в
[карточке](../../update-reports/entries/android-20261028-background-gps-start-evidence.md).

## Системная история в отправляемом ZIP — 2026-10-27

При явной команде отправки журнала MainActivity добавляет process-exits.json
непосредственно в ZIP. На Android 11+ читаются максимум восемь записей OS history,
затем выбирается основной package process. У каждой записи только timestamp,
PID, reason, status/signal, importance, PSS/RSS в KiB. Нормальные причины тоже
сохраняются как системные факты, а не объявляются crash. Сам явный ZIP export
не создаёт автоматических событий; restart fallback описан отдельно ниже.
Описания, trace streams, accounts,
карта, файлы объектов и фотографии здесь не читаются.

Модель/Android и reporter_version/build стоят на уровне экспорта; историческим
записям текущая версия не назначается. Android ниже 30 возвращает unsupported,
отсутствие сервиса/отказ API — unavailable, доступная пустая история — available
с пустым exits. Это отсутствие свидетельства, не отсутствие падений.
Сведения нужны и когда ни один SDK envelope не пережил завершение.
[Контракт Android ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo).
[Текущий инцидент и ограничения](../reference/mobile-crash-224-2026-10-27.md).

## Автоматическая диагностика восстановления GPS

Явный выход двойным Back и удаление задачи прекращают только автоматическое
получение GPS. При отсутствии других владельцев источник завершает свой recovery
эпизод обычным снятием подписки; ожидание GPS после закрытия не запускается.
Активная запись трека/обхода продолжает получать GPS и диагностироваться отдельно.

`GpsRecoveryPolicy` различает отсутствие спутникового решения (`no_fix`),
недоставленные callbacks при свежем used-in-fix status (`callback_stalled`),
тишину всего provider, stale/invalid fixes, ожидание фильтра и display processing.
Локальный эпизод фиксируется через 30 s первичного / 15 s повторного ожидания
после истечения прежней 8 s свежести. No-fix/acquisition/filter wait остаются
локальными независимо от длительности и повторов. В GlitchTip уходят непрерывные
callback_stalled от 30 s, provider_silent от 60 s и stale/invalid/display_processing
от 15 s именно этой причины. Предыдущее ожидание спутников не считается временем
сбоя callbacks. Не более одной
начальной отправки за 10 min. Recovery/cancel пары отправляются только для уже
отправленного эпизода, с тем же UUID и причиной группировки, даже если причина
позже изменилась; recovery имеет INFO, задержка WARNING.

Snapshot v3 добавляет `reason_duration_ms`, `observed_reason` (fresh_fix при парном
восстановлении), `diagnostic_tick_gap_ms` и `device_sleep_gap_ms`. Разрыв
main-thread observer без сна помогает отличить задержку обработки от сна
устройства; сам по себе он не устанавливает причину. Snapshot содержит
`last_callback_age_ms` (время с последнего callback) отдельно
от `last_callback_delivery_lag_ms` (возраст координаты при той доставке).
История — максимум 24 компактных строки раз в 5 s: monotonic time, raw/callback/status
ages, used/visible, C/N0, решение фильтра, screen и owners. Координат в истории нет.
Один `callback_stalled`, непрерывный 30 s при свежих used-in-fix statuses, разрешает
одну повторную регистрацию того же listener с прежним интервалом, не чаще раза
за 10 min. Network, recorders, precision leases, wake lock и фильтр сохраняются.
При `no_fix`, отсутствии разрешения, отключённом provider или external input
повторной регистрации нет. Provider-off/permission loss не открывают новый эпизод;
остановка существующего даёт cancelled, а не выдуманное recovery.

Существующий durable transport сохраняет warning и парный итог offline.

MapRuntimeDiagnostics добавляет адресную диагностику локальных тайлов и
touch queries MapLibre. Быстрые успешные операции не пишутся в журнал.
Slow/failed операции содержат request id, map mode, lifecycle/scene state,
возраст последнего полученного кадра, heap, число workers/queued requests,
этап и длительность queue/lock/index query/feature-read-and-clip/MVT encoding.
Наблюдатель запускается через 1 s независимо от UI; заканчивающаяся операция
с длительностью от 500 ms тоже фиксируется. Повтор одного вида сообщения
ограничен 10 s и содержит число подавленных повторов.

Watchdog не обращается к MapLibre или бизнес-хранилищу. Он снимает только
заранее записанное состояние и ограниченный Java stack вызывающего потока.
Его scheduler отделён от записи HyperLog/файла; writer имеет 16 queued tasks
и фиксирует количество dropped diagnostic tasks. Снимок до 16 KiB атомарно
сохраняется в private map-runtime-diagnostics.json. BeforeSend прикладывает
его как map_runtime, включая прежние session/pid/version/source_revision,
только если время события относится к записанной операции (grace 15 s).
Таким образом AppExitInfo после перезапуска не получает случайные сведения
нового процесса. Это небольшое диагностическое хранилище, не raw HyperLog DB.

Ответ local vector tile 503 включает X-Lisa-Tile-Reason (queue_full либо
heap_headroom) и X-Lisa-Tile-Request. SDK HTTP hint сохраняет эти headers;
DiagnosticsHttpPolicy добавляет local_tile_reason/local_tile_request.
Причины ограничиваются независимо; request id не размножает limiter keys.
Последняя глобальная operation может быть SYNC при HTTP-запросе тайла —
назначение запроса определять по endpoint и диагностическим headers.
Причина ожидания внутри native renderer по одному Java snapshot всё ещё
может требовать native/system trace.

Приёмник — собственный GlitchTip, проект NextGIS Mobile. Администратор смотрит
ошибки в [веб-интерфейсе](https://apps-geonical.ru/errors/). Уведомления на сервере
отключены. Пароль администратора не нужен APK. Реальный публичный DSN хранится
только в игнорируемом `sentry.properties`, ключ `sentry.dsn`; в Git/docs он не входит.

## Владение и запуск

`app` владеет политикой диагностики, `maplib.LocalLogInitializer` — общей
инициализацией локального журнала в app/maplibui. NGW-ресурсы не меняются.
`MainApplication.initializeDiagnostics` запускается после раннего guard
изолированного script UID, до HyperLog и `GISApplication.onCreate`.
`io.sentry.auto-init=false` предотвращает вторую автоматическую инициализацию.
Пустой DSN в явно разрешённой CI-конфигурации отключает отчёты и retry job.

Sentry Android 8.37.1 собирает JVM exceptions, поддерживаемые NDK crashes и ANR,
системные сведения об устройстве и lifecycle. HyperLogCrashHandler остаётся
внешним обработчиком: пишет локальный crash log и делегирует Sentry/Android.
Фатальное исключение завершает процесс; диагностика не пытается продолжить
выполнение повреждённого приложения. Существующие черновики и recovery journals
сохраняют прежнее поведение. Background worker не переинициализирует SDK или handlers.

Все точки запуска HyperLog используют один process-wide `LocalLogInitializer`.
Перед первым initialize удаляется только устаревший `HyperLog/URL`: фиктивный
loopback endpoint вызывал синхронную подготовку всего журнала к отправке даже
при повторном initialize. У закреплённой версии библиотеки пустой URL прекращает
push до чтения backlog. Файлы/SQLite журнала, семидневное хранение, пользовательский
формат и crash handler сохраняются. Повторный вызов базового GISApplication не
заменяет app-формат и не запускает initialize ещё раз.

## Очередь и транспорт

Используется один SDK envelope cache в app-private `files/diagnostic-reports/`,
с разделением по DSN. Это files storage, не очищаемый Android cache directory.
Предел — 256 обычных envelopes; SDK вытесняет старые при переполнении. Очередь
HTTP в памяти ограничена 128 задачами, история — 80 breadcrumbs.

`DiagnosticsTransport` — расширение `ITransportFactory`. Оно использует SDK
serializer, envelope cache, Retryable/SubmissionResult/DiskFlushNotification
hints, rate limiter и стандартный HTTPS с проверкой сертификата. Отдельной
очереди ZIP/JSON и второго протокола доставки нет. Обычный event записывается
на диск до постановки HTTP-задачи; fatal hint подтверждается после записи.
Cached/NDK outbox подтверждает и удаляет штатный SDK directory processor.

У стандартного AsyncHttpTransport 8.37.1 ответ `>=400` удаляет envelope, включая
503. Поэтому применять его здесь нельзя. Наш транспорт сохраняет отчёт при
сетевом I/O, 5xx, 408/425/429, 401/403 и неожиданных redirects; учитывает
`Retry-After`/`X-Sentry-Rate-Limits`, не пересылает auth headers по redirect.
Невосстановимые ответы для некорректного запроса (например 400/404/413/422)
завершают попытку; статус пишется в технический журнал без payload.

SDK повторяет отправку при старте и смене доступности сети.
`DiagnosticsRetryWorker` — уникальная periodic WorkManager job каждые 15 минут
с constraint CONNECTED и exponential backoff от 30 секунд. Android может
запустить её без Activity. Она вызывает существующие SDK disk integrations,
дожидается очереди replay через SDK executor, затем HTTP flush; наличие оставшихся
envelopes возвращает retry. Один `Sentry.flush()` сам по себе не читает disk cache.
При пустой очереди job не обращается к серверу. Синхронизация GIS не запускается.

Доставка как минимум один раз: потерянное HTTP-подтверждение допускает повтор
того же event UUID. Приёмник группирует события; новый UUID для replay не создаётся.
Android Doze/ограничения батареи могут отложить job. После force-stop пользователем
Android возобновит фоновую работу только после открытия приложения. Нет гарантии
сохранения при заполненном/повреждённом диске, удалении данных приложения или
вытеснении старейших отчётов; SIGKILL/low-memory kill сами по себе не являются
перехватываемым Java exception.

## Полезные сведения и ограничения данных

Отчёт содержит тип/причину исключения и stack frames с файлами и строками,
release/versionCode, environment debug/production, brand, source_revision (Git HEAD
с `+dirty` при локальных изменениях), Android/модель/память,
последнюю операцию и структурированные lifecycle breadcrumbs. Состояния операций
передаются enum `Operation`/`Phase`, без произвольных пользовательских строк.
Формат HyperLog дополняет историю только фиксированными стадиями FormDraft,
FeatureSave, GeometryDraft, CrashRecovery; полный текст журналов не пересылается.

Скриншоты, view hierarchy, session replay, performance traces/profiles и generic
attachments отключены. GlitchTip 6.2.6 не хранит generic ZIP/screenshots; основная
диагностика находится внутри event, а не во вложении. Не читать HyperLog DB,
учётные записи, геометрию, атрибуты формы или файлы фотографий из crash callback.
`save_log`/`verbose_log` управляют локальным экспортом и не отключают error reports.

Контракт `diagnostics_contract=3` сохраняет содержимое уже собранного события:
URL с путём и исходным userinfo, отдельные query/fragment, метод, HTTP headers/cookies,
response status/headers, доступные request/response data, extras, custom contexts,
user и breadcrumbs, variables/absolute paths/source context в frames. Пароли,
токены, координаты, SQL и значения полей не маскируются по явному требованию
владельца системы. Для автоматических HTTP-ошибок оригинальные URL/headers берутся
из SDK OkHttp hint: SDK самостоятельно скрывает часть этих полей в event.
Потоковые тела HTTP не читаются и не расходуются ради диагностики; сохраняются
данные, уже предоставленные событию. `sendDefaultPii=false` не меняется, но это
не фильтр данных, явно переданных приложением/OkHttp hint.

`DiagnosticsPayloadLimiter` ограничивает строки 4096 символами, дополнительные
структурированные данные — общим бюджетом 32768 символов/256 узлов, глубиной 6
и 64 элементами каждой коллекции. Циклы заменяются маркером; неизвестные объекты
не обходятся reflection и не вызывают произвольный toString. Основной stack,
номер строки, устройство и URL не удаляются при исчерпании бюджета дополнительных
данных. Пустые значения и типы чисел/boolean сохраняются. Бюджет разделён: 16 KiB/128 узлов
для report_origin, sync_journal, gps_recovery и map_runtime (в этом порядке),
8 KiB/64 для остальных данных, 4 KiB/16 для дополнительных полей stack и
4 KiB/48 для breadcrumbs. Последние операции/lifecycle получают бюджет первыми;
порядок breadcrumbs в событии сохраняется. Большие HTTP headers или frame vars
не вытесняют GPS-контекст и последние действия. Typed native os_linux сохраняется.

Selected handled failures вызывают `AppDiagnostics.report`: ошибки очереди
sync, расчёта азимута/выноса и foreground запуска. Обычные offline/timeouts и
cancel не создают handled bug report. Повтор одинаковой операции/класса/места
ограничен десятью минутами; fatal exceptions SDK этот фильтр не проходят.
Новые catch-ветви подключать явно, не превращать каждое предупреждение в событие.

Автоматические `SentryHttpClientException` от `SentryOkHttpInterceptor` проходят
отдельный фильтр `DiagnosticsHttpPolicy` в BeforeSend. Штатный GET локального
`/tiles/<layer>/<z>/<x>/<y>.pbf` с HTTP 503 и явным X-Lisa-Tile-Reason
queue_full/heap_headroom не создаёт отдельного багрепорта. Bounded MapRuntime
журнал, причины и resource guards сохраняются. Неизвестная причина, иной endpoint,
remote response и fatal/unhandled событие не проходят это исключение.
Для остальных HTTP 5xx первый отчёт сохраняется,
повторы в течение десяти минут подавляются по status/method/operation/месту вызова
и endpoint. Только в ключе лимитера числовые сегменты пути тайлов/ресурсов
объединяются, query отбрасывается;
endpoint хранится только как SHA-256 в ограниченном process-local наборе (64 ключа).
После окна допускается следующий отчёт; после перезапуска лимит начинается заново.
Другие endpoint, серверы, статусы и операции остаются отдельными. Fatal/unhandled
exceptions этот фильтр не проходят, cached replay не фильтруется повторно.

Дополнительно к request сохраняются `http_status`, `http_method` и `http_source`
(`local` для loopback, `remote` или `unknown`). Адрес и query доступны в самом
отчёте; фильтр не удаляет их из первого допущенного события.
503 при загрузке тайлов сам по себе не доказывает падение приложения или недоступность
NGW: ответ может дать локальный tile provider. Это отдельный поток от 503 при доставке
самих envelopes; transport/retry policy сохраняется.

## Проверка и обновление SDK

Обновление Sentry обязательно повторяет native delivery checks: disk persistence,
fatal delegation, offline process restart, network return, временный 503 и
WorkManager без сетевого переключения. Проверять реальные события в отдельном
GlitchTip test project, включая stack/device/version/operation; HTTP 200 ещё
не доказывает обработку worker на сервере.

Opt-in `-PdiagnosticDeliveryChecks=true` выбирает только в test APK
`DiagnosticsTestRunner`. Runner подставляет test Application и явно заданный
test DSN, отказывается работать на физическом устройстве или вне Debug.
Обычный APK, production DSN и обычный instrumentation runner не получают тестовых
настроек. Команда сборки:

```powershell
.\gradlew.bat :app:assembleLisaDebug :app:assembleLisaDebugAndroidTest -PdiagnosticDeliveryChecks=true
```

Фазы `DiagnosticsDeliveryTest` запускаются отдельно: queueHandledWhileOffline,
crashWhileOffline (ожидаемое завершение процесса), reportsSurviveOfflineRestart,
deliverWhenNetworkReturns. Отдельный workerRetriesTemporaryServerFailureWithoutNetworkChange
использует локальный synthetic HTTP endpoint. Рабочий телефон не используется.
См. [проверку](../history/error-reporting-verification-2026-10-08.md) и
[руководство администратора](../guides/bug-reports-user-guide.md).

Регрессии запуска и HTTP-шум проверяет `DiagnosticsNoiseTest`: отдельные invocations
синтетически создают 6000 строк (~12 MB), затем проверяют cold start/локальный журнал
и SDK BeforeSend с 28 повторными 503. Требуются тот же opt-in runner, явный test DSN
и изолированный эмулятор. Рабочий телефон используется только для чтения диагностики.

## Разбор 2026-10-17 и исторические ANR

Независимый map-runtime observer наблюдает также startup после script guard:
инициализацию SDK, account/user-agent, preferences/локальный журнал, underlay,
Collector bootstrap, shared-track migration, GISApplication load, upgrade и workers.
Записываются только фиксированные имена этапов, elapsed/stage times и Java stack
заблокированного потока; быстрый успешный старт не пишет снимок. Это диагностика
ошибок запуска, не сбор длительности работы пользователя.

Прежний journal читается bounded writer до первой записи нового процесса и
сохраняется в памяти: новый медленный старт не стирает контекст delayed AppExitInfo.
Общий файл остаётся до 16 KiB; при переполнении удаляется старейшее наблюдение.
Идентичность snapshot соответствует исходному процессу. Cached и Backfillable
(Android AnrV2Hint) события не получают текущую operation. Их исходные release/tags
сохраняются, отдельно report_origin содержит reporter_version/build/revision.
Отсутствующая в старом отчёте исходная версия не восстанавливается догадкой.

brand/source_revision/diagnostics_contract устанавливаются и в actual SDK scope
с enableScopeSync: options tags сами по себе не поступают NDK scope observer в
закреплённом SDK 8.37.1. Версия SDK и обработчики native crash не меняются.

Типизированная operation/phase теперь тоже обновляет actual scope, чтобы NDK
сохранял последнюю операцию исходного процесса. `DiagnosticsExitInfo` дополняет
cached/Backfillable failures полем `process_exit`: timestamp, PID, reason/status,
importance, description, PSS/RSS в KiB. Android 11+ OS history читается только
на фоновом отправителе; требуется то же имя процесса, соответствующий тип
завершения (ANR/JVM/native) и расстояние timestamp не более 10 s. Обычный handled
отчёт, иной процесс, stale history или нормальное завершение не получают этот
контекст. API ниже 30 и отсутствие OS history остаются отсутствием данных.
OS trace streams, business storage и HyperLog DB здесь не читаются; crash callback
не делает новых Binder/file запросов. `process_exit` имеет приоритет после
report_origin в прежнем 16 KiB бюджете диагностических context.

Готовая CLI и доступ: [runbook](../runbooks/glitchtip-triage.md).
Текущий разбор и границы исправлений: [2026-10-23](../reference/glitchtip-triage-2026-10-23.md).

Sync PersistenceFailure добавляет sync_journal: phase, projects/accounts, before/after,
bytes, storage_available_bytes, caller_interrupted_at_entry/caller_interrupted и
write_mode=owned_thread. Причина IOException сохраняется, сами пары и credentials
не копируются. [Результаты разбора](../reference/glitchtip-triage-2026-10-17.md).

## Автоматический restart fallback — 2026-10-27

До SDK/загрузки карты DiagnosticsProcessExitReporter атомарно сохраняет
маленький owner marker с PID, временем, исходной версией и source revision.
После запуска фоновый поток сопоставляет предыдущие процессы с OS exit history
по имени, PID и lifetime. Ненормальный exit получает отдельный ERROR event
process_exit_fallback; User/update/permission/self stops его не создают.
В очереди максимум четыре owner; удаление из неё — после DiskFlushNotification
от существующего durable transport. Event ID детерминирован по process/PID/time,
поэтому retry не создаёт новую identity. Возможен отдельный fallback рядом с
основным SDK crash: число отчётов не считается числом независимых падений.

Первое обновление может прочитать до восьми OS записей последних24h без owner
старого APK. Их версия и source revision явно unknown, текущая версия не
приписывается. История/trace могут отсутствовать; Android до11 не предоставляет
этот fallback. Это ограничение сбора, не гарантия обнаружить любое завершение.

Для ANR читается main-thread stack. Android12+ native tombstone читается с
пределом1MiB; allowlist parser оставляет до24 crashing-thread native frames,
function/library basename/rel_pc/build_id и ограниченную безопасную abort message.
Регистры, raw memory, memory maps, OS log buffers и open files исключены.
Malformed/oversized/unavailable/missing trace имеют явный статус. Никаких новых
Binder/file операций в fatal callback: сбор только после следующего запуска.

Уточнение28 октября: `REASON_SIGNALED` тоже запрашивает trace начиная с API30.
Прежний reason gate пропускал эти записи целиком и выдавал
`not_available_for_reason`, хотя публичный Android API не гарантирует отсутствие
trace. Javadoc/implementation `ApplicationExitInfo.getTraceInputStream()` в
установленных Android36 SDK sources допускает сохранённый прежний ANR после
восстановления процесса и последующей смерти по другой причине; getter не имеет
проверки reason. Native protobuf документирован с API31; null допустим, в том
числе после перезаписи системного circular buffer.

`DiagnosticsTracePolicy` сохраняет прежний ANR/native API gate, добавляя для
SIGNALED режим `requested_format=auto`. Parser принимает native format только
при наличии crashing TID и непустых allowlisted frames; иначе ищет только main
ANR stack. `format` равен `native_protobuf`, `anr_text` либо `unknown`.
`association=may_precede_exit` у signal trace означает возможную связь с более
ранним ANR, а не установленную причину текущего завершения. Исходные reason/status,
owner identity и fingerprint остаются прежними; reason2/status6 не доказывает OOM.
Null даёт `missing`, ошибка getter/read — `unavailable` с ограниченным именем
класса ошибки без её сообщения, нераспознанные bytes — `unrecognized_format`,
переполнение — `oversized` либо `captured_truncated`. Пропуск политикой теперь
называется `not_requested_for_reason`, API ниже30 — `unsupported_api`.
Byte/frame/privacy budgets прежние; никакие дополнительные native handlers не
устанавливаются. Новые unit cases и реальная OS-проверка SIGNALED ещё не запускались;
исторический native result ниже относится к прежнему ANR/native reason gate.

Проверено на API36 AVD: реальный SIGKILL без SDK envelope и SIGABRT, restart,
исходная identity, наличие OS native_frames в durable envelope. В delivery06
все9 native phases PASS, включая приём fallback локальным HTTP сервером после
возврата сети и WorkManager retry после503. Owned AVD остановлен с cleanup
readback PASS. Первый delivery01 оставлен как FAIL на HTTP retry; новый результат
его не переписывает. Fresh verification fixture исключает OS историю прежних
независимых тестов. Клиентский224 crash не устранён; физический Samsung не проверен.


К классифицированным редким GPS отчётам добавлен platform snapshot: fine/coarse/
background permission, GPS/global location enabled, app standby bucket,
background restriction, process importance и observer_on_main. Это данные
недостающие в provider_silent после screen off; они не устанавливают причину.
Координаты/accounts/storage не читаются; необязательная ошибка сбора не теряет
исходный отчёт. Пороги, retry и подавление нормального no_fix/acquisition/filter
wait не изменены. Card android-20261027-gps-provider-evidence; первый ручной
crash APK030f303 этого GPS addon ещё не содержит.
