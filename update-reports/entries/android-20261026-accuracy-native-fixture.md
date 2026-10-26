---
id: android-20261026-accuracy-native-fixture
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T00:53:00Z
title: Изоляция сенсора и диагностика native проверки круга точности
why: В Full05 accuracy test завершился generic timeout без номера sample и фактического радиуса
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
---

MOB-01/REL-03. Accuracy fixture, как соседний Orbit test, отключает реальные
heading callbacks перед техническими fixes; ждёт zoom22 и завершения сцены
после перемещения камеры. Сохранены raw accuracy/coordinates, panel text,
native polygon-radius tolerance .08m и прежний 30-секундный deadline.
Геометрия не подменяется и input не переигрывается при ошибке.

Timeout теперь содержит sample kind, ожидаемый/измеренный native radius,
cached accuracy, camera/source/lifecycle state. Причина прежнего generic
timeout без этих данных не доказана. Production GPS/renderer код не менялся;
это изоляция graphics fixture и улучшение доказательств, не production repair.
