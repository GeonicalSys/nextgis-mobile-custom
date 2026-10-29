---
id: android-20261029-glitchtip-gps-evidence
area: Диагностика мобильного приложения
kind: internal
at: 2026-10-29T04:47:29Z
title: Уточнены сведения для разбора мобильных ошибок и GPS
why: Чтобы отличать отсутствие нового решения от задержки его доставки приложению
covers:
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsGpsPlatform.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsGpsFixWitness.java
  - app/src/test/java/com/nextgis/mobile/util/AppDiagnosticsTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsGpsPlatformTest.java
  - app/src/test/java/com/nextgis/mobile/util/DiagnosticsGpsFixWitnessTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/GpsRecoveryNativeTest.java
---

Разобраны события восьми открытых мобильных групп, включая поздно доставленный
отчёт о завершении приложения. Неизвестные причины ошибок не объявлены
устранёнными. Обычное ожидание GPS остаётся в местном журнале, длительные
паузы продолжают сообщаться.

Для следующих отчётов добавлены сведения о времени последнего решения,
сохранённого Android, и его сравнении с решением, полученным приложением.
Координаты в эти сведения не включаются. Запись треков, фильтр и порядок
восстановления GPS сохраняются. Причина полевых пауз пока не установлена.

Технические проверки нового наблюдения пройдены. Обработка групп на сервере
и фиксация исходников ожидают завершения.
Новая сборка не установлена и не опубликована.
