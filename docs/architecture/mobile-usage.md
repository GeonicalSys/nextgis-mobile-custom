---
title: Централизованная статистика мобильного приложения
type: architecture
last_verified: 2026-10-17
related_code:
  - app/src/main/java/com/nextgis/mobile/usage
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/build.gradle
---

# Централизованная статистика

`MainApplication` после isolated-UID guard и основной GIS initialization
подключает `UsageAnalytics` только в default process production Release.
Debug/verification имеют пустые BuildConfig endpoint/token и не создают
production-событий. Конфигурация — игнорируемый `usage.properties` в root:
`usage.endpoint` и write-only `usage.uploadToken`; содержимое не коммитить.
Пустая конфигурация отключает функцию. Не требуется доработка NextGIS Web.

Собираются модель/производитель/Android, полный ANDROID_ID (fallback UUID
установки), бренд/версия APK, связанные server/login, состав проектов и даты
открытий. `app_open` — первое появление Activity UI в процессе,
`app_resume` — последующее возвращение из фона. Смена Activity и rotation
сами по себе не создают открытий. Задержка 700 ms используется только для
устранения переходных lifecycle callbacks. `UsageOpenPolicy` не читает часы.
Длительность работы, интервалы foreground/background и heartbeat не собираются.

`project_open` относится к активному project UID в момент UI-события.
Переход фиксирует UID и timestamp перед IO-очередью; worker не подставляет
текущий проект вместо исторического владельца. Снимок `inventory` читает только
`CollectorProjectRegistry` и разрешённые app accounts, без GIS feature DB,
геометрии, атрибутов, фото или паролей. Аккаунты нормализованы по server URL
без userinfo/query/fragment и login; проекты — server+remote resource ID,
локальные — installation+UID. Удалённая/недоступная учётная запись не мешает
учёту остальных проектов. FileObserver следит за атомарной записью реестра;
UI-события, account callbacks и upload worker также сверяют состав.

`UsageStore` хранит installation UUID, следующий seq, hashable snapshot и
неизменяемые события в private noBackup SQLite WAL с FULL synchronous.
Снимок ставится в outbox только при изменении; snapshot checkpoint и событие
коммитятся одной транзакцией. Предел 100000 событий; при заполнении новые
события не вытесняют старые, увеличивается `dropped_events` для оператора.
Удаление данных приложения/переустановка удаляют неотправленную очередь;
Android backup не клонирует identity/outbox на другой телефон.

`UsageUploadWorker`: immediate connected job + уникальная periodic работа
15 минут, exponential backoff. `UsageDelivery` посылает до 50 записей/пачку,
не следует redirects и удаляет только явно подтверждённые ID той же пачки.
Offline, 503, auth/conflict и неверные ACK сохраняют исходные записи. Force-stop
и Doze могут отложить доставку до разрешённого Android запуска/открытия UI.
Никакого GPS/foreground service ради аналитики нет. Ошибка аналитики не
должна блокировать приложение, синхронизацию или очередь crash reports.

Канонический сервер/протокол: `C:\dev\lisa\server\mobile_usage` в `lisa`,
публичный route `https://apps-geonical.ru/mobile-stats/`. Отдельная SQLite
база и read-only операторский доступ; GlitchTip сохраняет прежнее назначение.
Поставка: серверный PR и проверка HTTPS → отдельный Android PR поверх #65
→ штатные Full/Check-Publication перед выпуском APK. Submodule pointers и
версии не меняются. Старую статистику до включения восстановить нельзя.

Проверки: `UsageOpenPolicyTest`, native `UsageDeliveryTest` в изолированном
verification APK, `tools\verify-mobile-tests.ps1 -Scope Related -Area usage`.
Native проверяет сохранность UUID/seq после reopen, точные ACK, HTTP 503,
повтор с прежним UUID, привязку проекта к аккаунту, исключение credentials
и отсутствие duration. Серверные проверки — в owner repo.
[Результаты](../history/mobile-usage-verification-2026-10-17.md).
