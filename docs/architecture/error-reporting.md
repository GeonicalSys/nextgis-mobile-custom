---
title: Доставка диагностических отчётов
type: architecture
last_verified: 2026-10-10
related_code:
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTransport.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsRetryWorker.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsPolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/util/LocalLogInitializer.java
---

# Доставка диагностических отчётов

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

BeforeSend удаляет user/request/server name/extra contexts, variables/absolute
paths в frames и произвольные breadcrumbs. В сообщениях удаляются credentials,
URL/email, локальные пути, SQL и точные координаты/NMEA; строки ограничены по длине.
Сохраняются класс, функция и номер строки — их нельзя заменять общим «Ошибка».

Selected handled failures вызывают `AppDiagnostics.report`: ошибки очереди
sync, расчёта азимута/выноса и foreground запуска. Обычные offline/timeouts и
cancel не создают handled bug report. Повтор одинаковой операции/класса/места
ограничен десятью минутами; fatal exceptions SDK этот фильтр не проходят.
Новые catch-ветви подключать явно, не превращать каждое предупреждение в событие.

Автоматические `SentryHttpClientException` от `SentryOkHttpInterceptor` проходят
отдельный фильтр `DiagnosticsHttpPolicy` в BeforeSend. Первый HTTP 5xx сохраняется,
повторы в течение десяти минут подавляются по status/method/operation/месту вызова
и endpoint. Числовые сегменты пути тайлов/ресурсов объединяются, query отбрасывается;
endpoint хранится только как SHA-256 в ограниченном process-local наборе (64 ключа).
После окна допускается следующий отчёт; после перезапуска лимит начинается заново.
Другие endpoint, серверы, статусы и операции остаются отдельными. Fatal/unhandled
exceptions этот фильтр не проходят, cached replay не фильтруется повторно.

До удаления request добавляются только `http_status`, `http_method` и `http_source`
(`local` для loopback, `remote` или `unknown`). Адрес, query и путь в отчёт не входят.
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
