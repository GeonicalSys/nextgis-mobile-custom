# app — инструкции для ИИ-агентов

Перед изменением `app` прочитай:

1. [`docs/README.md`](docs/README.md) и [`docs/manifest.yaml`](docs/manifest.yaml).
2. [`../docs/registry/change-impact.yaml`](../docs/registry/change-impact.yaml).
3. Связанные invariant IDs из [`../docs/registry/invariants.yaml`](../docs/registry/invariants.yaml).
4. Для release/update — [`../docs/runbooks/release-apk.md`](../docs/runbooks/release-apk.md).
5. Для NGW/Collector связи с desktop QGIS —
   [`../docs/architecture/lisa-ecosystem.md`](../docs/architecture/lisa-ecosystem.md)
   и [`../docs/registry/ecosystem.yaml`](../docs/registry/ecosystem.yaml).

`app` владеет Android lifecycle, Map host implementation, product flavors,
preferences, release и updater. Он не должен переносить GIS/storage реализацию
из `maplib`/`maplibui`.

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
Не отправлять raw HyperLog, NMEA, accounts, field values, geometry и фото.
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
только на изолированном эмуляторе; никогда не запускать suite на рабочем телефоне.
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
Подписи разделённых double_combobox брать из meta.fields keyname/display_name,
затем layer alias; field key используется только при отсутствии обоих.
