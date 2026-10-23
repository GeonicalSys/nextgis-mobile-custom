---
id: android-20261023-glitchtip-triage
area: Мобильное приложение
kind: user
at: 2026-10-23T12:32:23+02:00
title: Разобраны мобильные ошибки и уточнены причины будущих багрепортов
why: Штатное ожидание GPS и защитные ответы тайлов не засоряют список, а реальные сбои получают больше сведений
covers:
  - maplib
  - maplibui
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsExitInfo.java
  - app/src/test/java/com/nextgis/mobile/util/AppDiagnosticsTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsHttpPolicyTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsExitInfoTest.java
  - app/src/test/java/com/nextgis/mobile/util/CompassTouchPolicyTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/CompassTouchTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/GpsRecoveryNativeTest.java
  - tools/mobile-test-plan.json
  - app/docs/manifest.yaml
---

Разобраны 12 открытых групп GlitchTip, 312 событий. У компаса исправлено падение
при отсутствии вибрации в связанной библиотеке. Порог предупреждения GPS
теперь отсчитывает непрерывное проблемное состояние; обычный no-fix остаётся
локально. Защитный local tile GET 503 с известной причиной не отправляется
как отдельная ошибка. Неизвестные ANR/native crash не заглушены: добавлены
OS process-exit metadata и исходная operation/phase в native scope.
Связанные library PR должны быть merged до выпуска приложения.
