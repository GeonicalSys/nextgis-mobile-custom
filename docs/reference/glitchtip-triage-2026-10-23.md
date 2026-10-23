---
title: GlitchTip — мобильные ошибки, штатный шум и уточнение диагностики
type: reference
last_verified: 2026-10-23
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsExitInfo.java
  - maplib/src/main/java/com/nextgis/maplib/location/GpsRecoveryPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/location/GpsEventSource.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/CompassFragment.java
  - tools/glitchtip.py
---

# Разбор 12 открытых мобильных групп

Исходный API export 2026-10-23: **105 групп / 458 событий** во всех проектах,
из них **12 открытых мобильных групп / 312 событий**. В NextGIS Mobile всего
29 групп; проверены все страницы 312 событий, исходные версии/стэки/context,
breadcrumbs и прежние комментарии. Сырые события и приватный доступ находятся
вне Git. Counts не означают число падений или пользователей.

| Issue | События | Вывод и обработка |
|---|---:|---|
| 101 / MOBILE-T | 2 | Подтверждённый fatal NPE, HONOR NDL2-W09, Android 16, 223/source 5050495. CompassFragment.onTouch:262 разыменовывает null Vibrator. Добавлены проверки service/hasVibrator, rotation и настройка сохранены. Точная причина отсутствия сервиса в исходном процессе не требуется для безопасного исправления. |
| 45 / MOBILE-1 | 160 | 132 события явно local HTTP; 28 старых без URL. 503 — защитный backpressure, свежие snapshots показывают queue_full (2 workers/16 queue). Только известный local tile GET с explicit queue_full/heap_headroom исключён из отдельных SDK reports. Нераспознанные/remote HTTP failures сохраняются. Это фильтр шума, не доказательство устранения любой задержки карты. |
| 65 / MOBILE-G | 2 | Local tile 503, та же узкая политика. Лимиты ресурсов не снимаются; MapRuntime log/snapshot остаётся. |
| 64 / MOBILE-F | 58 | 57 событий 222/contract 2, одно Debug 218/contract 1. Ни одного нового sync_journal context. Уже имеющийся owned writer исправляет воспроизведённый false commit при прерванном caller; эти старые события не доказывают другую причину или регрессию на новой сборке. Real write failures продолжают отправляться с cause/phase/counts/space/interrupt flags. |
| 88 / MOBILE-P | 31 | no_fix: в delayed snapshots нет used-in-fix, обычное отсутствие решения. Теперь локально при любой длине/повторяемости, без warning/recovery спама в GlitchTip. Фильтр, координаты и запись сохранены. |
| 68 / MOBILE-J | 15 | Старый acquisition, ещё до episode snapshot v2. Не доказывает app bug. Новый policy сохраняет reception/acquisition/filter wait локально, реальные processing/delivery faults отдельно. |
| 96 / MOBILE-R | 12 | Часть initial warnings ошибочно наследует время no_fix: на первой used-in-fix отметке возникает callback_stalled, fix приходит через 0.5–2.6 s. Теперь требуется 30 s именно непрерывного callback_stalled. Есть и настоящий длительный эпизод с retry/cancel: он не заглушен. Snapshot v3 показывает reason duration и observer/device-sleep gaps. |
| 90 / MOBILE-Q | 24 | Длительная тишина GPS provider, в том числе с recording owner. Причина Android/radio/main-thread scheduling не доказана. Reporting сохранён после 60 s непрерывной причины, добавлены timing поля в snapshot/history. Нет опасного перезапуска provider без свежих used-in-fix статусов. |
| 53 / MOBILE-8 | 2 | ANR: native renderer query wait и старый HWUI syncAndDrawFrame. Точного воспроизведения нет. Сохраняется прежний temporal MapRuntime observer, добавлены matching OS exit metadata и native operation/phase. |
| 67 / MOBILE-I | 3 | Исторические ANR старых сборок: startup I/O и JNI addSource/ART wait. Стек не доказывает единую причину. Улучшен контекст historical termination; неизвестные ANR не подавляются. |
| 87 / MOBILE-O | 1 | Старый Debug 218, startup HyperLog/Gson. Точной причины нет; нынешний startup observer и matching OS termination metadata помогают следующему случаю. Тестовый ANR не объявляется новым production дефектом. |
| 69 / MOBILE-K | 2 | Старый libsentry unwind и второй SIGABRT с unsymbolicated libmaplibre frames. Native первопричина неизвестна. Actual native scope получает operation/phase; matching OS description/reason/status/PSS/RSS может дать причину следующего abort. SDK/NDK handler не отключены, native crash не заглушен. |

## Проверки и ограничения

Прошли **34 Java unit cases**: GPS policy 13, HTTP filtering 8, native scope/event
context 7, OS matching 2 и compass API 26/36 4. CLI проверена **6 техническими тестами** и
живым read-only list/export с pagination (160 HTTP events, не только первые 100).
Lisa/Belka Release source sets компилируются; release APK не собирается и не
публикуется. Verification APK и instrumentation APK собираются. Strict docs
validator, 7 docs tests, 19 runner/support tests и 26 report-guard tests прошли;
маршрутизация покрывает текущий каталог 43 native classes / 220 methods.

Телефон сначала был доступен и разблокирован, перед native запуском исчез из ADB;
native результаты уточняются отдельно. Эмулятор предыдущей задачи не стартовал
из-за отсутствующего Hypervisor Driver; полный Related/native delivery не подтверждён.
Новый Full/Check-Publication обязателен перед выпуском. Неизвестные ANR/native
причины остаются неизвестными; новые поля не подтверждают исправление их причин.

## Поставка и закрытие

По выбору пользователя app изменения продолжают
[Draft PR #77](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/77)
вместе с предыдущей фоновой подготовкой APK. Библиотеки:
[maplib Draft PR #59](https://github.com/GeonicalSys/android_maplib/pull/59),
commit `3c71efe3fcc9e369874b577ab514e41bcf337687`, и
[maplibui Draft PR #43](https://github.com/GeonicalSys/android_maplibui/pull/43),
commit `b2ac71cfaeb6a04076aa20d92d30a2f21a6c3d14`. Библиотечные fixes требуют своих
Merge Commit PR и последующего pin merged revisions в app перед выпуском.
Временные feature pins в Draft служат проверке и не являются закрытыми зависимостями.
Порядок: maplib/maplibui Merge Commit → app pins/Squash → разрешённый APK выпуск.
Ни merge, ни выпуск, ни серверный deployment этим разбором не выполняются.

Каждой обработанной группе добавляется индивидуальный комментарий с выводом,
проверками, ссылками на PR и пометкой «APK не выпущен». Закрытие отмечает
завершённую обработку (исправление/диагностика/штатный шум); новые события на
новой сборке требуют разбора. Events/counts и остальные проекты сохраняются.
Фактический итог закрытия фиксируется после readback API.

Подключение и команды: [runbook](../runbooks/glitchtip-triage.md).
