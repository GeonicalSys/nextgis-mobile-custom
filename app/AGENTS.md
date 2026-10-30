# app — инструкции для ИИ-агентов

Перед интерпретацией UI или выбором GUI-действия читать
[`../docs/architecture/ui-contracts.md`](../docs/architecture/ui-contracts.md)
и соответствующий ID в `../docs/registry/ui-contracts.json`. Проверять источник
значения, формат/CRS, режим и последствия обработчика по текущему коду. Положение
элемента и его подпись не доказывают назначение: device/display location,
camera center, saved viewport и selected geometry — разные состояния.
Недостающий контракт исследовать и сохранить самостоятельно; проверку строить
на независимо различающихся значениях. `check_ui_contracts.py` — source drift
gate, не реальное выполнение Android. Продуктовые/native ограничения остаются.

Перед изменением `app` прочитай:

1. [`docs/README.md`](docs/README.md) и [`docs/manifest.yaml`](docs/manifest.yaml).
2. [`../docs/registry/change-impact.yaml`](../docs/registry/change-impact.yaml).
3. Связанные invariant IDs из [`../docs/registry/invariants.yaml`](../docs/registry/invariants.yaml).
4. Для release/update — [`../docs/runbooks/release-apk.md`](../docs/runbooks/release-apk.md).
5. До любого изменения поведения/API/данных/ресурсов/config самостоятельно
   выполнить общий discovery и разбор зависимостей из root `AGENTS.md`, а не
   ждать, что пользователь назовёт cross-project связь. Автоматически сохранить
   все новые подтверждённые связи и ambiguous/dynamic refs в той же задаче;
   проверить source index перед commit. Общий маршрут и UNKNOWN для недоступного
   внешнего root/другого worktree —
   [`../docs/architecture/lisa-ecosystem.md`](../docs/architecture/lisa-ecosystem.md)
   и [`../docs/registry/ecosystem.yaml`](../docs/registry/ecosystem.yaml).

`app` владеет Android lifecycle, Map host implementation, product flavors,
preferences, release и updater. Он не должен переносить GIS/storage реализацию
из `maplib`/`maplibui`.

Проверки без движения доступны на смартфоне только в opt-in verification APK:
`-PfieldVerificationChecks=true`, отдельные application/provider/account IDs,
VerificationTestRunner и синтетический workspace. Команды и полная release gate:
[`../docs/runbooks/automated-tests.md`](../docs/runbooks/automated-tests.md).
Related на эмуляторе обязателен после доработки; Full на смартфоне и эмуляторе
обязателен перед публикацией, Check-Publication сверяет актуальность результата.
По поручению пользователя26 октября2026 автоматические Android/docs Actions
отключены ради лимитов GitHub. Эти проверки выполняются локально; workflow_dispatch
сохраняется только для отдельно запрошенной облачной проверки. Не запускать,
не перезапускать и не включать cloud workflows автоматически перед merge/publish.
`tools/docs-check.ps1 -RunTests` включает report guard и change-impact; Full
включает бывшие cloud-only реальные окна620dpi/font200% в обоих orientations.
Не менять системные настройки рабочего смартфона и не запускать mutating suites
в production либо обычном Debug-профиле.

Любая правка панелей, диалогов и подписей проходит проверки крупного шрифта
и display size из корневого AGENTS.md. Для карты особенно проверять полные
инструкции азимута/выноса, числовую коррекцию, GPS-статус и предупреждения,
включая portrait/landscape, обе темы и совместное увеличение до 200% шрифта
при ширине 320 и 280 dp. Сначала переносить элементы и разумно сокращать текст,
затем при реальном переполнении адаптивно уменьшать конкретный шрифт/компонент.
При закрытой клавиатуре Close/Stop/Save/Cancel остаются в видимой области и
нажимаются. Не требовать одновременного вписывания всего интерфейса с открытой
клавиатурой; после закрытия клавиатуры доступность восстанавливается. Регрессии включать в native
reliability suite, проверяя реальные границы строк и кнопок.

При обычном масштабе проверять компактные строки без искусственно пустого footer.
В свёрнутом меню создания прозрачные слоты передают карте DOWN/MOVE/UP,
а видимая кнопка добавления продолжает открывать меню. Высота и скорость
в нижней панели не отображаются. Если масштаб не помещается рядом с координатами,
он переходит к GPS/спутникам и точности; короткие показатели не разделять на
строки, если они помещаются вместе. Временные уведомления могут перекрывать
карту: их текст и действия должны помещаться, а действия карты проверяются
отдельно без уведомления поверх них.

При открытии любой карты `MainApplication` обязан нормализовать базовые слои:
дефолтный OSM/Mapnik — прямой дочерний слой с индексом `0`, «Мои треки» —
последний внутренний слой. Это правило действует и для нового Collector
workspace, а существующая видимость OSM не должна сбрасываться.

При изменении `MapFragment` проверить `MaplibreMapInteraction` и consumers. При
изменении Gradle/resources/manifest проверить Lisa и Belka. При изменении
updater проверить identity, version, size, APK hash и signing certificate
validation. Если updater отправляет пользователя за специальным разрешением
Android, он сохраняет только проверенный pending manifest, повторно валидирует
его после возврата и не требует повторного ручного запуска проверки обновлений.

## Диагностические отчёты

Читать `../docs/architecture/error-reporting.md` и
`../docs/guides/bug-reports-user-guide.md`. AppDiagnostics запускается после
isolated-script guard, до HyperLog handler. Не глотать fatal exceptions и не
переинициализировать SDK ради retry. DiagnosticsTransport сохраняет SDK envelopes
при offline, 5xx и rate limiting; стандартный AsyncHttpTransport 8.37.1 теряет
отчёты при HTTP 503. После обновления SDK перепроверять hints/cache/WorkManager.
Не читать business storage, raw HyperLog или фото из crash callback. По явному
требованию владельца сохранять уже предоставленные event URL/query/headers,
контекст и значения без маскирования, с ограничением размера/глубины; HTTP streams
не читать и не расходовать ради отчёта.
Новые operation/phase — enum; новые handled captures должны исключать обычную
offline/cancel ситуацию. Реальные DSN/пароли не коммитить. Delivery test runner
включается только `-PdiagnosticDeliveryChecks=true`, тесты — на отдельном эмуляторе.

## Азимут и вынос

Азимут и вынос используют только location foreground service, даже при
запрещённом Bluetooth. Отказ внутри onStartCommand приходит асинхронно: он
должен остановить owning controller, снять GPS lease/listener, звук и уведомление.
Service session ID живёт только в процессе; stale start/stop не затрагивает нового
владельца. При unowned redelivery выполнить foreground-start contract перед
остановкой, не восстанавливая GPS/audio. StakeoutForegroundServiceTest запускать
только на изолированном Android 14+ эмуляторе, отдельно с GPS grant и deny.

## Версионирование variants

- Production Lisa/Belka получают `versionCode`/`versionName` из
  `defaultConfig`.
- Единственный debug variant — `lisaDebug`; его версия задаётся через
  `androidComponents.onVariants(selector().withBuildType("debug"))`.
- На AGP 9.x не добавляй `versionCode` или `versionName` в `buildTypes`.
- В `maplib` debug coupling задаётся override поля
  `BuildConfig.VERSION_NAME`; не используй `buildTypes.debug.versionName`.
- Debug-only bump не должен менять metadata Lisa/Belka Release. Обязательная
  проверка из корня репозитория:

  ```powershell
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-version-matrix.ps1
  ```

  Она должна реально завершиться успешно до handoff. Не считай имя файла
  доказательством версии: проверяй APK metadata, а публикацию начинай только с
  `--dry-run`. При намеренном bump обновляй ожидаемую matrix в verification
  script вместе с Gradle constants.

Не добавляй чтение `Q:\standart_profiles`, QGIS plugin workspace,
`variables.py` или plugin mirrors в Android runtime. Данные между desktop и
mobile проходят через явный NGW/Collector contract или поддерживаемый
пользовательский import переносимого артефакта.

Обновляй README при смене пользовательского поведения или troubleshooting;
manifest — при смене entry point, key component, contract, setting или smoke.
Central cross-module docs обновляй в той же root-задаче.

## Project scripts

NGFP-формы: читать `../docs/architecture/conditional-form-rules.md`. Native
regression-тесты `CascadingFormsTest` и `ConditionalRequiredFieldsTest` запускаются
на изолированном эмуляторе или в отдельном verification APK смартфона;
production и обычный Debug смартфона не используются.
Перед выводом о default/required/высоте поля прочитать source-backed classobj/typeobj chain в conditional-form-rules и UI-FORM-TEXT-LINE-CAP: actual form/hash, remote schema, restored state и cap различаются. Корневые NGFP namespace/coercion/prefetch/old-clone риски вести только в общем LISA TODO.

Проверять публичный запуск формы со связанным meta, вкладки, Save/Back Save,
pin черновика и отсутствие SQLite-записи при нарушении условий.
FormAppearanceTest проверяет обе темы, перенос выбранного имени и нижний Save.
V2 visibility: hide/show/rotation/durable recovery не стирают текст; hidden static
required не блокирует Save, а видимый required остаётся обязательным.

Владеет ранним Application guard изолированного процесса, pinned native build и тестовым примером contractor-audit. Правила не меняют flavors/версии/accounts. Sentry app-start injection отключён; остальные crash/tracing hooks сохранены.

Перед доработкой читать `../docs/architecture/project-scripts.md` и пользовательское
руководство. Новый host API добавлять с capability/grants/типами/бюджетами/тестами
сначала в APK. Не поставлять arbitrary SQL, Java reflection, сеть или GIS-движок
внешним JS. Старые пакеты сохранять для pin черновиков. Cross-repo schema/API
обновлять одновременно с stand_project и central registries.

## Общая синхронизация проектов

Читать consuming root docs/architecture/ngw-sync-and-storage.md и
docs/guides/project-synchronization-user-guide.md. sync_all_projects по умолчанию true;
ручной и scheduled account запуск используют ProjectSyncRunner. Не переключать
mMap/active prefs ради фонового проекта. Владельца переносить через
SyncWorkspaceSession во все async callbacks, service tickets и provider URI;
untagged UI URI всегда относится к активной карте, expired token не имеет fallback.
Очередь держит глобальный lease до завершения дочерних работ и mutating HTTP.
Полный pending project/account план сохраняется до первого прохода; Collector
journals разделены по canonical map path. Проверять cancellation, equal layer/group
IDs, сохранность draft и реальный fill на изолированном эмуляторе.
До изменения async/provider/service или project storage прочитать раздел
«Владение workspace и границы подтверждения» в том же architecture документе:
lease не выбирает БД, pair ACK не равен full clock, а 60s expiry не ограничивает
claimed child. При изменении recovery journal читать также его process-view contract:
точный preferences owner и все семь известных ключей образуют один typed cohort.
commit=false/throw не публикует новый план через Journal API; очередная обычная
запись должна исходить из принятого cohort, сохраняя неизвестные ключи.
Первое чтение raw cache не доказывает disk confirmation. Не заменять этот
контракт compensating writes, [] fallback или обещанием rollback после restart. Directory/mapName/UID/token имеют разные ключи и миграции;
открытые риски ведутся в едином LISA dependency-audit-todo, не в отдельном списке.
Legacy storage move и Reset Settings получают исключительный PROJECT_SWITCH
lease до вычисления default directory, изменения prefs и постановки AsyncTask.
Lease сохраняется через worker и UI completion; cancelSync не заменяет владение.
Chooser listener возвращает false: proposed path/card не публикуются заранее.
Проверять точный mMap без factory getMap; retirement — запрет новых запросов,
не ожидание всех renderer reads. Rebind проверяет exact owner и cached actual
path под тем же monitor, что resetMap. RESULT_OK здесь refresh Activity, не move
ACK: после обычного отказа/отмены с retirement тоже нужен безопасный rebind, но
без Reset defaults/delete. Runtime до подтверждённого cache не превращать в
выдуманный rollback; disk/restart и миграции остаются отдельной границей.
Подписи разделённых double_combobox брать из meta.fields keyname/display_name,
затем layer alias; field key используется только при отсутствии обоих.

Разрешённый интеграционный проект — 1403 «Для инженеров Геоникл».
ProjectLayerSelectionTest использует read-only policy snapshot этого проекта
в отдельной verification базе. При отсутствии/неоднозначности проекта спросить
пользователя о разрешённом замещающем проекте. Не выбирать первый доступный.

Перед выводом о цели выноса различать выбранную geometry snapshot, текущий DB/FID, device fix и camera state. Читать `../docs/architecture/stakeout.md` и `UI-NAVIGATION-STAKEOUT-TARGET`: nearest facet выбирается в3857, inverse имеет явный spherical fallback, а layer isLocked не блокирует sync. Политику freeze/follow/invalidate при replacement/workspace return вести в существующем root AUDIT-TODO-NGW-REPLACE-FID; source trace не означает подтверждённый баг или разрешение native run.

Lookup-кеш читает общий maplib producer; сравнение принятого parser key/value
набора защищено NgwLookupCacheSyncTest, а не совпадением размеров. При
обработанных IOException/NGException/JSONException sync сохраняет старый кеш;
silent partial completeness остаётся Root AUDIT-TODO-NGW-PARTIAL-SNAPSHOT UNKNOWN. Перед анализом
Combobox/AutoTextEdit различать существующий cache owner, отсутствующий
prefetch и сохранённую save(false) политику. INV-NGW-LOOKUP-CACHE-CONTENT и
Root AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE не подтверждают disk/retry/native
приёмку. Не менять NGFP coercions и shared-cache deletion в этой регрессии.


NGFP lookup prefetch проверяет NgfpLookupPrefetchTest:8 actual-method prefetch и4
прямых legacy-reader controls. Reflection выбирает старый E66 API лишь при отсутствии
ForPrefetch; InvocationTargetException разворачивается без подавления Exception/Error.
Не переносить helper в app, не менять oracles ради green на старом pin. Новая API и
два production callers принадлежат одной maplibui библиотеке: Merge Commit до App
pointer. Live-source JVM/compile не принимают APK с committed E66; UI39/142/19 и
clock F61 не перепривязывать автоматически.
