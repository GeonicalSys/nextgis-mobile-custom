---
id: android-20261028-native-heap-map-route
area: Диагностика native памяти
kind: internal
at: 2026-10-28T01:35:00+01:00
title: Ограниченный38-input маршрут карты внутри принадлежащего задаче heap capture
why: Для воспроизводимого диагностического облёта787 нужны запись действий и остановка при потере PID/foreground/capture ownership без выдачи input success за native приёмку
covers:
  - tools/native_heap_map_route.py
---

MOB-04, developer-only diagnostic workload. Оператор заранее проверяет
фактический проект787 и наблюдаемые map/control coordinates. Script требует
active capture receipt с project_id787 и нужнымserial; перед каждым input
проверяет foreground app, исходныйPID и Perfetto command/config/trace ownership.
Маршрут —3zoom-out taps,32pan swipes и3zoom-in taps. `map-route.json` сохраняет
действия и stopped/error при утрате ownership. Настройки, business records,
установкаAPK и завершение процессов не выполняются.

Это не Full/native GPU test:38успешных inputs не доказывают изменения camera,
правильные pixels, полноту объектов, bounded memory или качество heap trace.
Trace/callsites/stacks/loss/overrun/client-error и source/build identity
проверяют отдельно. CandidateRSSоколо1GB с5dump без allocationstacks не
назван qualified attribution или PASS. RSS остаётся числовой диагностикой,
не основанием предупреждения по абсолютному расходу.

Код прочитан и сопоставлен с profile_native_heap ownership API; unit/native
приёмка route этой карточкой не заявляется. Связь и scope записаны в
app change-impact/smoke registries и automated-tests runbook. Расширенный
read-only dependency plan сохранён в dependency-plan-app-memory-docs02.json;
UNKNOWN/unmapped не назван отсутствием consumers, foreign desktop index
не перезаписывался. Выходы остаются в capture evidence наQ, не в Git;
новых runtimes/bulkdownloads не создаётся. Публикация и merge не выполнялись.

Локальный docs-check -RunTests: validator/strict impact,26report-guard и24docs
regressions PASS. Квитанция app-memory-docs-check01.log; это не route/native приёмка.
