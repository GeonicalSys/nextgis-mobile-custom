---
id: android-20261110-physical-offline-cursor-recheck
area: Мобильное приложение
kind: internal
at: '2026-11-10T01:40:00+04:00'
title: Проверять офлайн-подпись и указатель положения на физическом устройстве
why: Программная графика Windows-эмулятора искажает снимки символов
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/DirectVectorTilesOfflineTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - agent-system/knowledge/documentation/app-module-readme-built-in-gps-floor-and-independent-orbit-fixtures.json
  - agent-system/knowledge/documentation/app-module-readme-direct-native-tile-label-and-offline-geometry-oracle.json
  - agent-system/knowledge/app-module-manifest/app-module-key-components-directvectortilesofflinetest-0e88ef420c.json
  - agent-system/knowledge/app-module-manifest/app-module-key-components-locationorbittest-5af5bfd7be-2.json
---

Обе проверки используют отдельный verification APK на физическом телефоне.
Оператор вручную включает авиарежим и выключает Wi-Fi для офлайн-проверки.
Тест сохраняет проверки единственного якоря, глифов, заливки и контура.
Сценарий указателя вводит координаты и направление внутри тестовой карты.
Эмулятор сохраняет собственную изолированную сетевую фикстуру для разработки.
При проверке включённого сектора тест добавляет слой штатным способом и
проверяет прежний порядок пяти слоёв. Частые показания компаса обновляют
данные курсора без перестановки слоёв.
