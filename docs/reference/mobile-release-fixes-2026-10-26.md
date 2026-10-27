---
title: Исправления блокеров выпуска ЛИСЫ 26–27 октября
type: reference
last_verified: 2026-10-27
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/view/WrappingLabel.java
  - app/src/androidTest/java/com/nextgis/mobile/util/MaintenanceRemindersNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationIo.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocalVectorTileLabelTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/BackgroundGpsNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_startup.py
---

# Исправления перед публикацией

Новый порядок по запросу пользователя о лимитах GitHub: автоматические
Android/docs Actions отключаются; проверки выполняются локально. Cloud
workflow_dispatch остаётся отдельной ручной возможностью. Недостающие6
real-window profiles и report guard/change-impact включаются в local Full.
Настройки защиты ветки не меняются: required status checks в ней отсутствуют,
enforce_admins/review policy сохранены. Активных/queued облачных запусков при
проверке не было; новых dispatch/rerun не делали.

Для предыдущих проверок подключались смартфон R5GL130K54D по USB и планшет59843808167874 через
192.168.0.17:45143. API30 connected-final06: azimuth1/reminders2 PASS;
измеряемый word fit подтверждён на прежнем проблемном Android11.
CI8e7c799 завершился raw timeout на await hub в plain-runner дубликате
TrackUploadTest.delayedAcknowledgementCannotMarkReplacementTrackPointAsSent.
Все9 методов сохраняются в versioned verification route; Related07:9/9 PASS.
Причина исходного transport trigger остаётся неподтверждённой. Новый helper
при timeout сохраняет фактические requests/registration/network witnesses.

Library closure: PR60 MERGED `61a0a6029c261e203ab1948613a4fd81e9d5b071`,
PR44 MERGED `c3253726ed9bc12d7adff7356ba3532f00e8d383`. Оба consumer pins
обновлены после fetch; original heads являются ancestors, Git trees равны.
App PR78 MERGED `769629c7ccf6f6e065006b7ebecb8d702ee0d369`, follow-up PR79
MERGED `d60a76883b1ab300f66ae43604d1b625a861ad6b`. Source trees до/после
squash равны; library heads являются ancestors remote merge commits, consumer
pins точные. После fetch у всех5 владельцев нет открытых PR или опубликованных
codex heads с невключёнными обязательными изменениями. Проверены blobs всех16
строк delivery matrix. Новое исправление native fixtures добавляется отдельным
app follow-up; final Full после него ещё нужен.

Пользователь поручил разобрать ошибки и довести пакет до публикации ЛИСЫ.
Основной пакет app PR78, maplib PR60, maplibui PR44 уже слит. Desktop и публикация
БЕЛКИ исключены. Финальный порядок: merged libraries → consumer pins → app
merge → свежий Full смартфона+AVD → Full планшета+AVD → Check-Publication →
publisher tests/dry-run → публикация и readback. Сейчас выпуск ещё не принят.

## Full11–14 и исправление ожиданий native fixtures

На `d60a768` и fingerprint
`4d9e986efec23477ca9f90aa8a7d90a4839492bb4291594443c80d9e8d62b4d3`:

| Запуск | Фактический исход |
|---|---|
| Phone Full11: Samsung Android16 + API36 AVD | PASS254/254,913 Android units,54 runner units,26 report-guard +24 docs, version matrix,36 chooser и6 real-window profiles; cleanup/readback PASS |
| Tablet Full12 | Отменён пользователем до install/native;0 native, owned AVD остановлен; Full PASS не заявлен |
| Tablet Full13 | FAIL до native на host ADB: попытка второго daemon при занятом5037;0 native, cleanup PASS |
| Tablet Full14: Teclast Android11 + API36 AVD | FAIL3 native runs,241 принятый distinct метод, units/version/docs/UI matrices PASS; cleanup и production/global readback PASS |

В Full14 ещё10 методов прошли внутри неуспешного BackgroundGpsNativeTest;
они не входят в241 принятый метод целого прогона. Все прежние ошибки сохранены.
После одного штатного restart ADB и повторного подключения точного tablet
serial весь Full14 завершился без транспортного прерывания. Это восстановление
инструмента на ноутбуке; Windows/Android settings не менялись.

Изменения ограничены AndroidTest:

- BackgroundGpsNativeTest опрашивает фактический AndroidX Stage в main thread.
  getState ActivityScenario выбросил NPE на промежуточном RESTARTED. Home должен
  достичь STOPPED, возврат — RESUMED, выход — DESTROYED; assertions GPS, wake lock,
  независимых recorder owners и прежний20-секундный предел сохранены.
- LocationOrbitTest устанавливает test camera через MapDrawable.setZoomAndCenter,
  согласуя native viewport и сохранённую камеру приложения. Ранее native-only
  zoom22 мог быть заменён стартовым MapView invalidate через750ms на сохранённые
  0/0/7.5. Численные проверки исходного fix, радиуса с допуском.08m и30-секундное
  ожидание сохраняются. Timeout теперь содержит обе камеры.
- TrackStopConfirmationTest ждёт настоящий OnDismiss после одного физического
  Cancel. Отдельные observers подтверждают ровно один DOWN и UP на кнопке,
  возвращают false и сохраняют штатный click handler. Проверяются24 прежних
  profiles и неизменность записи; сбой содержит профиль и доставленные события.
  Related15 показал0 button DOWN/UP при уже закрытом окне. Диагностический16
  воспроизвёл отсутствие input в самом окне на568x320/density540. Существующий
  Ui.tap использует bounds активного focused/unobscured accessibility window;
  probe17 подтвердил все24 profiles с одним DOWN/UP и OnDismiss. Численная точка
  в проблемном profile совпала с прежней; конкретный внутренний переход input
  surface не установлен, поэтому это исправление readiness, а не доказанный
  дефект координат или product Cancel.
- TrackControlsTest.Ui.tap сохраняет UID-защиту и общий30s budget. Между
  accessibility snapshot и injection окно может смениться. Только явный отказ
  Android до доставки DOWN разрешает заново найти окно и измерить bounds.
  Ошибка после принятого DOWN остаётся FAIL; принятые жесты не переигрываются.

Правило main-thread stage query задано
[AndroidX ActivityLifecycleMonitor](https://developer.android.com/reference/androidx/test/runner/lifecycle/ActivityLifecycleMonitor).
Production код, версии,254 метода и маршрутизация не изменяются. После этих
правок выполняется Related gps-recovery/cursor-rendering/track-stop-ui/track-controls
и затем новые Full текущего содержимого на смартфоне и планшете. Старый Full11
gate не восстанавливается.

| Адресный запуск | Фактический исход |
|---|---|
| Related15 | Emulator19 PASS, tablet3 PASS и layout FAIL: жест не дошёл до кнопки; cleanup PASS |
| Diagnostic16, phone-only | Layout FAIL, Window.Callback не получил input; emulator cleanup N/A, owned AVD не запускался |
| System-bounds17, phone-only | Layout PASS,24 уникальных profiles с реальными DOWN=1/UP=1/OnDismiss; emulator cleanup N/A |
| Related18 | Emulator16 PASS, затем UID-rejected input в menu Stop; cleanup PASS; исходная фаза DOWN/UP не была записана старым helper |
| Related19 | Emulator27 PASS; tablet scope incomplete: listRename instrumentation900s timeout, затем контроллер ошибочно перешёл к следующему классу; cleanup PASS |
| Operator abort Related19 | Force-stop только verification namespaces пришёл уже после перехода к menu Stop и создал один Process crashed; это вмешательство оператора, а не product finding |
| Runner startup regression | Все61 unit PASS; настоящие owned child processes проверяют heartbeat, startup/runtime deadlines и освобождение; doubles проверяют exact package scope и остановку после timeout |
| Related20 на новом controller | PASS27 emulator +11 API30 tablet; все проверки треков, Stop, cursor/GPS и enlarged layouts завершены; cleanup и production/global readback PASS |

## Установленные причины и изменения

| Ошибка | Причина и изменение | Ограничение |
|---|---|---|
| API30 ZIP/photo/category readers | InputStream.readAllBytes требует API33; тесты используют переносимый буфер, entry streams закрываются | Product Files.readAllBytes API26 не заменяется |
| Screenshot предупреждения | decor.draw до WindowManager attach; show и capture разделены UI traversal barrier | Полная матрица288 и прежние assertions сохранены |
| Слово азимута разрывается | На API30 font200%, viewport280×500: слово753px при доступных752px; hinting делает пропорциональный расчёт неточным | Одно удаление порога0.5px не помогло; применяется поиск по измеренным advances |
| MVT не стартует offline | SDK Offline откладывает HTTP loopback; GISApplication разрешает SDK transport | Реальная Android сеть и WorkManager guards не меняются; внешние URL могут дать transport error/backoff |
| CI TrackUpload badge | Исходный stack Assert.assertTrue указывает на canShowPending после второго upload, не на unexpected true от uploadProject | Причина исходного CI пока не доказана; добавлены request/registration/network witnesses |
| Radio state в TrackUpload fixture | Вложенные fire-and-forget svc команды конфликтуют с владельцем сети @Before/@After | Дублирование удалено; принадлежность исходного CI этой гонке не доказана |

Добавлен один emulator-only offline MVT метод; каталог теперь254. Исходный
Firefly fallback дополнительно проверяет реальную отрисовку MVT до смены
стиля. Старые Full253 — исторические результаты, не новая приёмка.

## Проверки этой стадии

Общий каталог/код не меняется во время каждого запуска; fingerprints и
authoritative result/cleanup сохраняет versioned runner. Корень локальных
свидетельств: `Q:\Codex\Scratch\mobile-full-20261025-f06ccfb\evidence`.

| Запуск | Результат |
|---|---|
| azimuth-api30-baseline-01 | Воспроизведён исходный перенос753/752 с точными параметрами и PNG |
| api30-fixed-01 | Backup9, types1, reminders2 PASS; первый word-fit вариант FAIL |
| offline-azimuth-fixed-02 | Ошибка ADB установки до native;0 методов, owned AVD остановлен |
| emulator-release-fixes-03 | 11/11 PASS: azimuth1, TrackUpload9, offline-MVT1; cleanup PASS |
| api30-fixed-04 | Планшет не подключился по прежнему порту; native не начинался |
| backend-integration-fixed-05 | 19/19 PASS: project-policy1, TrackUpload9, mixed selection/order4, reminders2/updater3; cleanup PASS |
| API30 connected-final06 | 3/3 PASS: whole-word azimuth и288 reminder configurations; cleanup PASS |
| CI route native07 | Все9 delivery методов PASS через versioned verification runner; cleanup PASS |
| Local real-windows08 | 8 успешных native запусков,2 разных метода: прежние6 global profiles и2 базовых; real_window_matrix=true, cleanup PASS |
| Все Android units | maplib615/maplibui93/app205:913 PASS,0 failures/errors/skips; обе Release Kotlin/Java source sets compile PASS |
| Runner units | 53 PASS, включая manual-only policy, все delivery cases и обязательные реальные окна |
| Publisher units | 23 PASS; только временные локальные каталоги, production не изменялся |
| Docs | Report guard26 и docs24 PASS; строгий impact полного task diff PASS; финальные docs дополнительно валидируются перед commit |
| Phone Full10 на merged769629c | FAIL до native: empty --enforce-diff на чистой target ветке; units913/runner53/version matrix PASS, native0, cleanup PASS |
| Empty-diff helper regression | Настоящий PowerShell wrapper принимает clean target и сохраняет strict changed path с пробелом; runner54 PASS |

Native устройство — только отдельный verification APK; production данные и
системные настройки физического устройства не изменяются. Смартфон пользователь
подключил по USB. Планшет восстановлен на45143; новый API30 word-fit прошёл
адресную приёмку. Full11 прошёл; Full14 выявил три следующих native-fixture сбоя.
После их исправления полные прогоны254 обоих устройств выполняются заново.

## Хранение

Используются установленные SDK/JBR/AVD и существующие Q cache/build junctions.
Компактные отчёты сохраняются до приёмки PR. Два прежних disposable каталога
общим размером29282499bytes остаются после executor-policy rejection; повторной
попытки удаления или обхода не было. Их точные пути/причина уже находятся в
общем pending-cleanup.json. Доставляемые APK и shared caches не удаляются.

## Зависший запуск проверочного процесса

В Related19 до первого статуса listRename Linux PID target-приложения отсутствовал,
но AMS сохранял ActiveInstrumentation/Starting ProcessRecord и живой test invoker.
Планшет отвечал, был awake; crash buffer пуст. Причина Android process-start stall
не установлена; это не доказанное исправление runtime crash. Прежний Related
phone loop поймал900s timeout как обычный native fail и перешёл к следующему классу.
Ручная остановка двух проверочных packages затем прервала уже новый menu case;
этот Process crashed явно помечен как operator-induced.

Контроллер теперь разделяет NativeCheckFailure и setup/transport exceptions.
Последние сразу завершают прогон через штатную очистку. Independent prepare и
finalize завершают target и test invoker namespaces; reset=False остаётся прежним.
Перед первым INSTRUMENTATION_STATUS_CODE:1 действует отдельный60s budget.
При истечении завершается только owned ADB command client, записывается receipt
с его PID/exit, а finally освобождает verification sessions и owned AVD.
После handshake остаётся прежний900s overall limit. Семь новых unit regressions
не заменяют новый device run. Native каталог по-прежнему254.

Финальные адресные результаты перед follow-up merge: Related20 **27 emulator /
11 tablet PASS**, runner **61 PASS**, report guard **26 PASS**, docs **24 PASS**
со strict diff по всему task scope. Fingerprint
`852c32483cf1bed35b0cabdf6b02c9764c487734f12d1358790d675ebfecfced`.
Native каталог53 classes/254 methods сохранён. Это Related, полный выпускной
Full на обоих физических устройствах ещё не принят; APK не опубликован.
