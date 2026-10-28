---
id: android-20261028-render-memory-notice
area: Память и карта
kind: user
at: 2026-10-28T01:35:00+01:00
title: Одно уведомление о нехватке свободных ресурсов и ручной статус карты
why: Пользователь требует сообщать один раз за сессию о применённом замедлении или ограничении ресурсов, сохраняя данные и отрисовку
covers:
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/res/menu/main.xml
  - app/src/main/java/com/nextgis/mobile/util/RenderMemoryNotice.java
  - app/src/main/res/values/render_memory.xml
  - app/src/main/res/values-ru/render_memory.xml
  - app/src/test/java/com/nextgis/mobile/util/RenderMemoryNoticeTest.java
---

MOB-04, app82/maplib61. Atomic process-session claim предотвращает повтор
автоматического окна после callbacks, смены слоёв/карт и Activity recreation.
MapFragment принимает callback только у текущего видимого resumed host;
lifecycle включает/останавливает resource watch и освобождает dialog с view.
MainActivity показывает независимый ручной статус «Загрузка», пока guard
сообщает economy. Retry повторно оценивает ресурсы/кэши и не пересоздаёт все
GeoJSON источники. Русский/английский текст объясняет возможное замедление;
данные, точность геометрии, visibility и sync не переписываются.

Последнее требование владельца: economy/уведомление зависят от фактического
свободного system/Java остатка и OS low-memory state. Абсолютные used/native/RSS
и номинальный RAM class не являются основанием предупреждения/замедления;
RAM class задаёт только обычный disk-cache default и allocation reserve.
Неизвестные счётчики не считаются нулевым свободным остатком. Числовые process
observations остаются диагностикой, не hard cap и не гарантия отсутствияOOM.
Переполнение рабочей очереди само по себе не считается нехваткой памяти и
не вызывает это предупреждение.

RenderMemoryNoticeTest содержит2независимые проверки повторного claim и20
конкурентных callers. Native production delivery проверяется отдельным
RenderMemorySafetyTest: первое окно без ручного host callback, отсутствие
повторов после recreation/другого слоя и ручной статус в8componentprofiles.
Наличие/компиляция теста не названы nativePASS; GPU/эмулятор/2GiB/Full и
последний free-headroom source требуют собственных текущих результатов.

Адресные maplib проверки последнего free-only/queue fix:23focused tests PASS
(Guard2/Policy8/MapRuntime13), evidence/free-memory-notice-units01.json.
Roomy queue не даёт ложное memory warning; это JVM/Robolectric, не native gate.

Semantic app MapFragment ↔ MapDrawable/RenderMemoryGuard/MaplibreMapInteraction
и process notice contract записаны в app registries. Расширенный whole-contour
read-only plan: dependency-plan-app-memory-docs02.json в Qtask evidence,
итог UNKNOWN/path-contract-unmapped; owner source/model coverage не принята
как end-to-endPASS. Чужой dirty desktop index здесь не изменялся. Saved style/
project schema и producer NGW/desktop формат не меняются, миграции нет.
Merge/release/publication не выполнялись.

Локальный docs-check -RunTests: validator/strict impact,26report-guard и24docs
regressions PASS. Квитанция app-memory-docs-check01.log; это не native приёмка.
