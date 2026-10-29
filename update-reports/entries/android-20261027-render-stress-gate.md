---
id: android-20261027-render-stress-gate
area: Приёмка мобильного приложения
kind: internal
at: 2026-10-27T14:00:00+01:00
title: Длительная нагрузка карты и проверяемая матрица устройств перед публикацией
why: Короткие обязательные UI проверки не выявили массовые завершения 224 на Samsung Galaxy Tab после загрузки и движения карты
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/MapRenderStressTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/DiagnosticsDeliveryTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestApplication.java
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/mobile_render_acceptance.py
  - tools/tests/test_mobile_render_acceptance.py
  - tools/mobile-test-plan.json
  - tools/docs-check.ps1
---

MOB-03. Отдельный процесс подготавливает техническую SQLite карту из двух MVT
слоёв: 6480 полигонов, 65 вершин на объект и подписи. Новый процесс открывает
её штатно, выполняет не менее 90 секунд pan/pinch и два пересоздания Activity.
Проверяются живой UI, продолжение кадров, прежнее количество объектов и
фактическая отрисовка после нагрузки; модель/Android/OpenGL renderer и счётчики
сохраняются в квитанции. Это техническая нагрузка, не приёмка клиентских данных.
Проверяется фактическая привязка обоих MVT sources; сохраняются peak PSS,
Java/native memory и снимок owned AVD до закрытия неуспешного экрана. State
до закрытия и через5/10s различает reload и продолжительное отсутствие кадров;
поздний recovery не превращает исходный FAIL в PASS.
Отправленные MotionEvent не считаются доказательством нагрузки: требуется
минимум50 независимых native center changes и15 native zoom changes.
Эта проверка выявила игнорируемые SDK короткие pinch; screen-relative
reciprocal траектория исправлена, но новое исполнение остановлено из-за host
hard hangs. Старый формальный stress07 PASS не принимается как pan+pinch приёмка.

Check-Publication требует текущий source fingerprint и отдельные подтверждения
телефона, планшета, эмулятора и проблемного физического Samsung SM-X115.
Отсутствие устройства остаётся блокером обычного выпуска. Related не заменяет
общий Full; ручной диагностический APK без публикации учитывается отдельно.

Реальные SIGKILL без SDK envelope и SIGABRT на эмуляторе проверяют durable
fallback после перезапуска, исходную identity, OS native frames и приём локальным
сервером после возврата сети. Обычные User/update stops не объявляются crash.
Fresh verification workspace не импортирует OS историю прежних независимых
fixture; owning restart phases её сохраняют. Docs wrapper не создаёт pycache
в техническом Git fixture и исходном дереве.
