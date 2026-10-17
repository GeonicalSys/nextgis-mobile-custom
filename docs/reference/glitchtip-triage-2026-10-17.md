---
title: GlitchTip — очередь синхронизации и диагностика открытых Android-отчётов
type: reference
last_verified: 2026-10-17
related_code:
  - app/src/main/java/com/nextgis/mobile/util/SyncRecoveryJournal.java
  - app/src/main/java/com/nextgis/mobile/util/SyncJournalCommit.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - maplib/src/main/java/com/nextgis/maplib/util/MapRuntimeDiagnostics.java
  - tools/mobile-test-plan.json
---

# Разбор девяти открытых групп

Исходный ORM-снимок: **86 групп / 228 событий** во всех проектах;
**9 открытых групп / 133 события**, все в NextGIS Mobile. Остальные desktop,
mobile и Setup verification группы уже закрыты. Проверены исходные exception
frames, breadcrumbs, release/source revision, устройство, история и комментарии.
Число событий не равно числу падений или пользователей. Сырые события и данные
учётных записей в репозиторий не включены.

| Issue | События | Вывод и выполненная работа |
|---|---:|---|
| 45 / MOBILE-1 | 92 | HTTP 503, главным образом 3.1.2.28+222. Последние breadcrumbs указывают local tile queue_full: 2 workers / 16 queued. Это защитный backpressure локального сервера, не доказательство недоступности NGW. Причина/request id уже есть в headers; резерв бюджета теперь сохраняет последние действия и context. Лимиты ресурсов не снимаются. |
| 64 / MOBILE-F | 19 | Старые 3.1.2.28+222 сообщают общий Cannot persist sync queue без исходной причины. Новая диагностика сохраняет cause, phase/counts/bytes/free space и interrupt flags в sync_journal. Отмена потока воспроизведена как одна причина false commit, но причина каждого старого события не доказана. |
| 76 / MOBILE-L | 1 | 3.1.2.29+223: commit=false при большом свободном месте. Interrupted caller воспроизведён на Android API 36: старый enqueue бросает IOException, исправленный подтверждает физическую запись и восстанавливает interrupt. Нет оснований объявлять storage full. |
| 77 / MOBILE-M | 2 | 3.1.2.29+223: такой же commit=false. Долговечный enqueue/ACK теперь идёт на собственном writer; реальный commit=false остаётся ошибкой. Native test проверяет сохранность второй пары при подтверждении первой. Тождественность причины всех production событий не доказана. |
| 66 / MOBILE-H | 1 | Historical Debug emulator ANR: startup user-agent/account identity. Добавлены независимый startup observer, этапы и bounded main-thread stack; исходная старая сборка не заменяется текущей. Точного воспроизведения этого зависания нет. |
| 67 / MOBILE-I | 2 | Один production ANR без release/source в загрузке GeometryRTree, один старый Debug в Collector bootstrap. Добавлены startup stages и сохранение предыдущего process snapshot до новых записей. Нет оснований менять R-tree или объявлять его дефект доказанным. |
| 86 / MOBILE-N | 1 | Historical Debug emulator ANR в Logger/preferences. Тот же startup observer различает preferences/local log от GIS load; Backfillable AnrV2Hint получает исторический snapshot и отдельный report_origin. |
| 68 / MOBILE-J | 14 | GPS recovery delayed acquisition, 3.1.2.29+223. В доступном snapshot нет использованных спутников, raw fix устарел, callback свежий; это не доказанное зависание обработки или глушилка. Сохранение gps_recovery/последних действий усилено. Acquisition/filter/accuracy/запись трека не менялись. |
| 69 / MOBILE-K | 1 | SIGABRT 3.1.2.28+222: доступный native stack в libc/libsentry, включая unwind; исходная native причина не установлена. Actual scope tags теперь синхронизируют brand/source_revision/contract в NDK; typed native OS context сохраняется. Sentry остаётся 8.37.1, NDK handler не отключён. |

## Проверка и границы исправления

До изменения production кода две native регрессии enqueue/ACK падали на API 36
с SharedPreferences.commit returned false при прерванном caller. AOSP
[SharedPreferencesImpl](https://android.googlesource.com/platform/frameworks/base/+/android-8.0.0_r4/core/java/android/app/SharedPreferencesImpl.java)
подтверждает false при InterruptedException в ожидании disk latch. Owned serial
writer дожидается результата и восстанавливает отмену вызывающего потока;
перехода на недолговечный apply() нет. Это проверенное исправление класса ошибок,
но production отчёты прежних сборок не содержат interrupt state и не доказывают
общую причину всех commit=false.

Contract 3 резервирует прежний общий budget для critical context и последних
действий, отдельно ограничивает request/stack additions. Startup observer пишет
на прежнем bounded writer, не читает GIS и не вызывает renderer. Предыдущий
journal читается до перезаписи новым процессом. Cached и SDK Backfillable ANR
сохраняют исходную identity; report_origin описывает отправителя. Отсутствующая
исходная версия старого события не выдумывается. Actual scope tags поступают
NDK observer: это отличается от options tags в
[SDK 8.37.1](https://github.com/getsentry/sentry-java/blob/8.37.1/sentry-android-ndk/src/main/java/io/sentry/android/ndk/SentryNdk.java).

Регрессионная область `Related -Area bugreports` включена в GitHub runner;
полный каталог теперь содержит 207 native cases. Локально прошли **39 unit cases**
(10 maplib + 29 app), **10 native cases на API 36**, 16 проверок runner/project
fixtures, 7 проверок docs tooling и strict docs validator. Lisa/Belka Release
source sets скомпилированы, release APK не собирались. В подготовке прежнего
6000-row backlog test обнаружен timeout 60 s на медленном диске эмулятора:
ожидание увеличено до 180 s при сохранении всех 6000 строк и прежних assertions;
повторный полный Related bugreports прошёл. Новый Full/Check-Publication
потребуется перед выпуском; старый Full это новое содержимое не подтверждает.

## Поставка и обработка групп

Работа изолирована в `codex/glitchtip-triage-20261017`, параллельные checkout не
менялись. Порядок: maplib Merge Commit → fetched merge commit в root pin → app
Squash → согласованный APK выпуск. Временный feature pin в связанном Draft PR
служит проверке и остаётся незакрытой зависимостью до merge. Версии APK не
повышены, приложение пока не опубликовано; учёт времени работы не добавлен.

Библиотеки: [maplib #55](https://github.com/GeonicalSys/android_maplib/pull/55)
(startup implementation) и
[maplibui #38](https://github.com/GeonicalSys/android_maplibui/pull/38)
(только согласование documentation pack, runtime-код не менялся).

Группы закрываются по поручению пользователя с индивидуальными комментариями:
подтверждённое исправление или диагностическое улучшение, результаты проверок,
ссылки на код и явная пометка об отсутствии выпуска. Закрытие означает завершённый
разбор, а не доказанное устранение неизвестной причины. События, счётчики и
статусы ранее закрытых групп сохраняются. Повтор на новой сборке требует разбора.
