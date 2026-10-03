---
title: Аудит надёжности мобильного приложения — PR45
type: reference
last_verified: 2026-10-04
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/FeaturePersistenceTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/FormSaveRecoveryTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackerStopRecoveryTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - maplib/src/main/java/com/nextgis/maplib/util/PendingTrackPoints.java
  - maplib/src/main/java/com/nextgis/maplib/util/LayerDatabaseTransaction.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/ModifyAttributesActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - .github/workflows/android-reliability.yml
---

# Аудит надёжности PR45

Проверяется текущая codex/tablet-map-controls вместе с исходными доработками
планшетной панели PR45. Главный приоритет — сохранность объектов/вложений и
записанного трека, затем устойчивость lifecycle и удобство инструментов.
Код изменён с разрешения пользователя после первоначального read-only аудита.
В app новая ветка/PR не создаётся; отдельные owners maplib/maplibui требуют
связанных Draft PR. Сам аудит не менял версии, NGW schema, flavors и sampling/filter правила.
Следующий запрос пользователя готовит production `3.1.2.27` / `221`; release APK
и publication instructions требуют закрытия PR37/25/45 и APK version matrix.

## Дополнение: Pigo Lite/Lite Plus, 2026-10-04

В двух полевых журналах 17–23 сентября найдено 26 BLE-сеансов с notify `3A22`,
но `rx=none`; все 104 отправки initialization commands отклонены. Встроенный
приёмник даёт CNB BESTPOSB, обычного NMEA в архиве нет. Протокол проверен по
официальному PiSatel 1.0.9.0 (`BleDeviceManager`, `NewMockService`): отдельный
write `3A21` в service `3A20`, запуск `log bestposb`. Добавлен только этот
профиль и additive log, без `unlogall`/смены поправок/радио/config save.

Ошибки подписки/записи и permission revocation больше не выдаются за готовый
transport; фрагменты последовательны, write timeout 5 с. Старые callbacks не
закрывают новую сессию. HUD перестаёт показывать прежнее качество при обрыве
или 8 с тишины. В архиве есть и реальные autonomous `q=1`, и FLOAT/FIX, поэтому
переименовывать «Авто» в «FLOAT» без данных приёмника нельзя. PiMock start/stop
не имеют timestamp в журнале: последовательность действий установлена лишь
частично. Пользовательские координаты/адреса в Git не внесены.

Добавлены 57 регрессий GNSS/BLE, новые Robolectric сценарии выполнены на
API 26/36. Итог локально: **574 unit tests** (451/82/41), 0 failures/errors/skips;
**28 native tests** API36/WHPX, 41.54 с. Lisa/Belka release Kotlin/Java source
sets с подготовленной 3.1.2.27/221 компилируются; Debug сохраняет 3.1.2.22/217.
Linux CI этой итерации проверяется отдельно. Физического Pigo/PiRat здесь нет;
нужен cold-start smoke без PiMock, обрыв и повторное соединение.

## Находки и исправления

| ID | Проблема | Принятое изменение и проверка |
|---|---|---|
| A01 | Feature Save мог подтвердить успех после ошибки фото/подписи | Worker проверяет весь Save, форма и draft остаются для retry; native real-form отсутствующее фото → успешный retry без дубликата, signature state/output checks |
| A02 | Feature CRUD и NGW outbox писались раздельно | Owning-map managed SQLite transaction, строгие outbox calls, notifications/file deletion after commit; native и Robolectric API26/36 insert/update/delete rollback |
| A03 | Окно потери формы и id после частичного Save | Periodic/pre-Save/post-id typed checkpoints, owning map/operation UUID и dedup journal; повторный insert возвращает прежний id |
| A04 | Ошибка trackpoint insert теряла точку; Stop мог терять хвост | AtomicFile ordered bounded spool, UUID idempotent drain, durable pending_stop; native реальный TrackerService не завершает ошибочный Stop и после восстановления пишет хвост по порядку |
| A05 | Backup gate зависел от текущего права редактирования | Existing local NGW data защищаются и при read-only; form/walk reservations и owning map проверки сохранены |
| A06 | ZIP одной секунды мог перезаписать прежний backup | Unique partial/final names, mandatory entries/CRC verification; native два backup сохраняют отдельные ZIP с теми же rows/photo |
| A07 | Ошибка чтения outbox могла восприниматься как отсутствие изменений | Strict DB APIs throw; legacy UI wrappers fail conservatively; error не очищает очередь upload |
| A08 | Ручной backup блокировал UI и собирал rows в RAM | Worker, streaming JSON из SQLite snapshot, повторные generation/map/parent gates перед final delete; фактическое чтение ZIP проверено |
| A09 | dataSync foreground timeout и blocking shutdown | onTimeout cancellation/stopSelf, cache worker без main/self join, tile pool bounded wait; release source compile, реальный OS timeout ещё не проверен |
| A10 | Внутренние recorder/fill/cache/tile службы экспортированы | exported=false для internal components; authenticator/sync adapter публичный контракт сохранён |
| A11 | Запуск ссылки без обработчика мог падать | http(s)/host validation, caught launch errors и обычное сообщение; browser/device smoke остаётся |
| A12 | Stale/racy auth map и неточное совпадение ресурсов | Immutable snapshot, exact origin/server prefix/resource, cleanup on account/map lifecycle; unit проверки1/10 и чужого host |
| A13 | Rail ограничивался высотой и выходил за доступную ширину | Capacity по обеим сторонам, 48dp и overflow исходных actions; native bounds, физический tablet smoke остаётся |
| A14 | Старый onBackPressed обходил Android16 navigation contracts | Lifecycle AndroidX callbacks на карте/форме с сохранением confirm/fallback; compile и selection checks, реальный predictive gesture остаётся |

Дополнительные пользовательские исправления: immediate editable-selection actions
и окончание mode при clear; NextGIS ID «Почта или логин», Locale.ROOT lowercase
только identifier; повторная bind folder/up icon в первом/recycled Web GIS row;
допуск max(12dp, system slop) для drawing/ruler/azimuth и matching tool pan threshold.
Native UI проверки подтверждают selection/read-only, ruler/azimuth tap vs drag,
long press/cancel и реальный NGID wire username/password. Для drawing всех geometry
типов используется общий handler; полный пальцевой сценарий пока не выполнен.

Дополнительно закрыта ранняя потеря файлов при удалении NGW-вложений: failed
outbox оставляет photo/JSON metadata и старые change rows; retry удаляет после commit.
Проверены отдельное и массовое attachment delete на API26/36 и native36.
Walk checkpoint ошибки стали видны в panel/notification; linked-list WKT и snapshot
проходы стали последовательными. Dependency snapshot закреплён прежними байтами
Hyperlog AAR; wrapper SHA256, Gradle verification XML и Android CI added.

## Фактическая проверка

Runtime: Windows11 WHPX, Android Emulator35.6.11, API36 Google Play x86_64,
SwiftShader, JDK21 Android Studio JBR, Gradle9.3.1/AGP9.1.0. Исходный AVD и
подключённый Samsung не изменены; отдельный emulator-5556/data directory.
После включения WHPX и перезагрузки emulator работает, ещё одна перезагрузка
для выполненных проверок не нужна.

Linux CI: GitHub hosted Ubuntu, Temurin21.0.12+1, Gradle9.3.1/AGP9.1.0,
Android Emulator37.2.12.0 / API36 google_apis x86_64 / KVM / SwiftShader.

| Проверка | Результат |
|---|---|
| maplib units, включая real SQLite Robolectric API26/36 |394 tests;0 failures/errors/skipped |
| maplibui units |82 tests;0 failures/errors/skipped |
| app Lisa Debug units |41 tests;0 failures/errors/skipped |
| Общая native suite на финальном коде |28 tests PASS: Windows36.575s, Linux CI89.144s;0 failures/errors/skipped |
| maplibui assemble / Lisa Debug app + AndroidTest APK |PASS |
| Lisa и Belka Release Kotlin/Java compilation |PASS; release APK не собраны |
| Повторная Gradle сборка без write-verification-metadata |PASS, strict checksums active |
| Docs validator/tests и git diff check |PASS: validator,7 docs tests, enforced changed-path check и diff whitespace check |
| Новый GitHub Android workflow |PASS: [run37140933263](https://github.com/GeonicalSys/nextgis-mobile-custom/actions/runs/37140933263), app4764810:517 units, debug APKs, обе release source sets и28 native API36 tests; strict dependency verification active |

Native suite использует isolated test layers, real SQLite triggers, real form menu
Save, real TrackerService Stop и localhost server; не авторизует настоящий NGW
аккаунт и не меняет server data. Covered: feature/outbox rollback и project ownership,
failed DROP preserving files, raw outer rollback, unique backup rows/photo,
track retry/order/lost reply/new spool instance/corrupt checkpoint/AtomicFile backup,
photo Save retry, signatures, folder recycling, NGID request, rail/selection/gestures,
walk checkpoint и terminal revision fence.

Полный lint не прошёл: baseline run завис в FragmentRecursiveMethodVisitor;
частичный отчёт содержит270 errors/311 warnings, преимущественно251 legacy
MissingTranslation. Это не green lint; blanket suppression не добавлялся.
Unit/build/native pass не доказывает отсутствие всех крашей.

## Длинный обход

Реальный WalkSessionStore checkpoint с сериализацией полного WKT, API36:

| Вершины | До | После |
|---|---|---|
|1000 |49–92ms |31–53ms |
|10000 |403–581ms |166–240ms |
|50000 |5900–6523ms |737–836ms |

LinkedList индексировался внутри циклов, давая квадратичный рост; now foreach
сохраняет точный WKT/closing rules. Максимальный snapshot по-прежнему синхронный:
50k vertices могут дать заметный UI stall. Перенос checkpoint на serial worker
с revision/phase fencing — следующий отдельный этап, после device benchmark.

## План оставшейся приёмки

1. На изолированном устройстве повторить process kill/reboot с pending track Stop,
   реальный disk-full, permission revoke и partial form Save; проверить camera,
   rotation и predictive Back. Сопоставить feature/point counts и photo bytes,
   а не только визуальное сообщение.
2. На Android8–9 и целевом планшете проверить drawing всех типов, ruler/azimuth,
   реальные micro-drift fingers, drag/pinch, editable/read-only selection, clear,
   rail overflow clicks, large font/display, landscape/split screen/insets.
3. Запустить OS dataSync timeout/cancellation и foreground start rejection;
   проверить worker termination, retained recovery journals и project leases.
4. Выполнить [ручное восстановление ZIP на копии](../runbooks/incident-and-rollback.md),
   затем нагрузочные backup/long-walk checks на целевом телефоне.
5. После review и Merge Commit maplib → maplibui закрепить удалённые merge SHA
   в app45; повторить closure audit и required release/version matrix до выпуска.

Оставшиеся границы: RAM-only точки при полной невозможности записи не переживут
process death; revoked location может отложить cold pending Stop; filesystem
cleanup и SQLite не одна атомарная транзакция. Old Save worker UI capture/clearing is guarded after Activity destruction, but
Activity recreation/concurrent Save, SIGKILL/reboot, walk persistence-error UI injection и физическая GUI
матрица не проверены полностью. Legacy lookup-table cleanup и полноценный
ZIP restore остаются отдельными путями проверки. Private HTTP/custom CA и telemetry
контракты не изменены в рамках этого аудита.

## Доставка и зависимости

База app — my-maplibre6b47ce6 (merged app44); исходный app45 содержит94c5f93.
Базовые merged library pins: maplibf9ab155 (#36), maplibuid79f2e98 (#24),
easyPickerf91abdf. Перед доставкой fetched preflight: root/easyPicker/publisher
clean; dirty app/maplib/maplibui состоят из текущих изменений этой задачи,
0 ahead/behind. GitHub identitygeoglyth, app45 собственный Draft; посторонних
open Android PR не обнаружено.

| Требование/owner | PR/base | Включение/порядок |
|---|---|---|
| Исходная планшетная панель/app |#45 → my-maplibre |94c5f93 — предок текущего app tip |
| SQLite/outbox/track spool/tap/auth/WKT/Pigo/version/maplib |[maplib37](https://github.com/GeonicalSys/android_maplib/pull/37) → master, `040c3cd0a342f8f6c467bc303c819670857e32dd` |1. Опубликован Draft head, app gitlink совпадает; исходный `db7dfc6` — проверенный предок; требуется Merge Commit |
| Form/Tracker/backup/services/NGID/icons/integration docs/maplibui |[maplibui25](https://github.com/GeonicalSys/android_maplibui/pull/25) → master, `4cb67b136cd04a49bc4b7d79389a77065d9aab3b` |2. Опубликован Draft head, app gitlink совпадает; исходный `780eb95e` — проверенный предок; зависит от maplib37, требуется Merge Commit |
| App integration/selection/rail/Back/CI/docs/version221 |#45 → my-maplibre |3. Draft pins указывают на опубликованные commits обоих library PR; перед Squash нужны fetched remote merge SHA |
| easyPicker/publisher/desktop |Без изменений |Не создают новых зависимостей |

Точные app commit и remote checks записаны в body [app45](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/45).
Опубликованные codex branches сверены: app45, maplib37 и maplibui25; иных
требующих включения открытых Android PR не обнаружено на момент доставки.
Pin на незамерженный library head — reviewable integration, **не завершённый
release dependency**. Production version подготовлена `3.1.2.27` / `221`;
merge, release APK/version matrix и publication ещё не выполнены.
