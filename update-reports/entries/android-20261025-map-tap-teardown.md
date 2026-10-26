---
id: android-20261025-map-tap-teardown
area: Мобильное приложение
kind: user
at: 2026-10-25T20:36:01Z
title: Отмена отложенного тапа перед закрытием карты
why: Full выявил обращение SDK к уничтоженному renderer при подтверждении тапа
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
  - tools/mobile-test-plan.json
---

Перед освобождением MapView host передаёт ACTION_CANCEL непосредственно
детектору SDK: отложенное подтверждение тапа больше не должно обращаться
к уже уничтоженному native renderer. Проверки strict mode сохраняются.
Новый native сценарий повторяет тап и пересоздание Activity три раза;
Related map-teardown включает его и исходный сценарий линейки. Фактические
результаты адресной проверки и последующего Full фиксируются отдельно.
