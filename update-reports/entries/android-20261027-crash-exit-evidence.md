---
id: android-20261027-crash-exit-evidence
area: Мобильное приложение
kind: user
at: 2026-10-27T14:00:00+01:00
title: Системные причины завершения приложения в отправляемом журнале
why: После обновления 224 пользователи сообщают о вылетах Samsung Galaxy Tab при перемещении карты, но в ZIP нет стека падения
covers:
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsExitInfo.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsExitInfoTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/MapRuntimeDiagnosticsNativeTest.java
  - tools/mobile-test-plan.json
  - maplib
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsNativeTrace.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporter.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsNativeTraceTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsProcessExitReporterTest.java
---

MOB-02. Явная отправка журнала дополняет ZIP файлом process-exits.json:
до восьми системных записей основного процесса с причиной, status/signal,
временем, PID, importance и PSS/RSS. Данные формируются при экспорте, без чтения
бизнес-хранилищ, OS trace streams и без автоматического нового события GlitchTip.
Модель, Android и версия экспортирующего APK указаны отдельно: версия прежнего
упавшего процесса неизвестна. Android до 11 и недоступный API отмечаются явно.

Связанная библиотечная правка убирает пустые editor hit-tests при касании и
буферизует загрузку индекса. Это адресная работа с подтверждёнными ANR;
причина завершений 224 без стека остаётся неподтверждённой. Версии не повышены,
APK не опубликован. Проверки и ограничения — в безопасном разборе инцидента.

Дополнительное поручение пользователя: автоматическая диагностика после
падения. До запуска SDK сохраняется маленький process owner marker; при новом
запуске фоновый reporter сопоставляет OS termination с PID/именем/временем
прежнего процесса. Исходная APK identity сохраняется; при первом обновлении
без marker она явно unknown. Четыре pending owner удерживаются до подтверждения
фактической записи SDK envelope. Stable event ID исключает новый ID при retry.

Android 12+ native tombstone читается с пределом 1 MiB; передаются только
ограниченные crashing-thread frames, имена библиотек, rel_pc/build_id и безопасная
abort message. Регистры, память, карты памяти, OS logs и open files не экспортируются.
ANR содержит только main-thread frames. Недоступный/большой trace отмечается
явно; отсутствие OS истории не объявляется отсутствием падения.

Пользователь отдельно разрешил приватный подписанный APK поверх224 для ручной
диагностики, без публикации. Прежний renderer13.7.1 сохраняется для воспроизведения.
