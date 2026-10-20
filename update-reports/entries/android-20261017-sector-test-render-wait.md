---
id: android-20261017-sector-test-render-wait
area: Сопровождение
kind: internal
at: '2026-10-17T20:48:13+03:00'
title: Ожидание геометрии сектора компаса в тестах
why: Выполненная доработка отсутствовала в реестре и могла не попасть в отчёт
covers:
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
source_commits:
- 5050495aff9286b0b644c14de868b4d92a082b9d
---

Графическая проверка ждёт фактического изменения геометрии сектора после переключения видимости, а не только изменения настройки.
