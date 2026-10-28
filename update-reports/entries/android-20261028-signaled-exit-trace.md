---
id: android-20261028-signaled-exit-trace
area: Мобильное приложение
kind: user
at: 2026-10-28T04:55:00+01:00
title: Ограниченный системный стек после завершения приложения сигналом
why: Группа117 имеет reason2/status6, но прежний restart reporter не пробовал прочитать доступный OS trace для SIGNALED
covers:
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporter.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTracePolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsNativeTrace.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporterTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsTracePolicyTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsNativeTraceTest.java
---

MOB-03, уточнение диагностического сбора текущего инцидента224. Публичный
`ApplicationExitInfo.getTraceInputStream()` не запрещает trace при reason2:
Android может сохранить предыдущий восстановленный ANR после смерти процесса
по другой причине. Это подтверждено Javadoc и implementation установленного
Android36 SDK, строки824–862; старое `not_available_for_reason` было политикой
приложения, а не доказанным отсутствием OS данных. Причина исходного SIGABRT117
остаётся неизвестной; нехватка памяти из signal6 не выводится.

Новая политика на API30+ пробует прочитать SIGNALED trace после перезапуска.
AUTO распознаёт crashing-thread native protobuf либо main-thread ANR text;
`association=may_precede_exit` сохраняет неопределённость связи стека с текущим
exit. Original reason/status, process/PID/lifetime match, historical APK identity,
stable fingerprint/event ID, durable transport/ACK и native handler не меняются.
API31 gate для прежнего native-crash пути и ANR путь сохранены. Null/malformed/
unknown/IO различаются, exception message не копируется; один stream закрывается.

Защищены прежние пределы:1MiB input плюс sentinel,24 frames,240 chars per field,
фиксированный protobuf field/depth/thread budget. Memory/register/log/open-file
поля и другие потоки не экспортируются. ANR scan больше не создаёт массив всех
строк. Business storage, sync/draft, saved state, настройки и ZIP metadata export
не затронуты; миграции нет. Фильтрация/доставка существующих SDK событий применима,
desktop/NGW/Collector не являются runtime consumers этой OS-only политики.

Подготовлены16 unit cases в трёх suites: gates API29/30/31/36; native и recovered
ANR AUTO; unknown/corrupt/oversized; byte/frame/privacy bounds и resource close;
null/getter/read IOException/SecurityException; raw reason2/status6 и association
после actual cached-send sanitizer. **Свежие unit/build/native проверки не
запускались этим batch**, root выполняет общую приёмку после source freeze.
Исторические delivery06/SIGABRT результаты не принимают новый reason2 путь.
APK не опубликован; наличие OS trace при следующем реальном сбое не гарантируется.

Dependency discovery до edits: compact receipts
`signaled-exit-trace-impact01.json` и `signaled-exit-trace-doc-impact01.json`
в task evidence на Q; UNKNOWN/REFRESHED_IN_MEMORY, весь доступный контур проверен
в памяти. Канонический desktop source index не изменён из-за dirty foreign owner;
обязательный delivery follow-up остаётся у root. Semantic owner — app:
MainApplication/AppDiagnostics → restart Reporter → TracePolicy/NativeTrace →
process_exit context → existing cached-send/transport. Сохранены связанные docs
и локальный manifest; новый source fingerprint требует повторной общей проверки.
