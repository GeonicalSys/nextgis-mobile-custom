---
id: android-20261027-native-readiness
area: Локальная автоматическая приёмка
kind: internal
at: 2026-10-27T00:45:37+01:00
title: Native проверки наблюдают готовность Activity, камеры и закрытия диалога
why: Full14 выявил три сбоя native проверок после успешного смартфонного Full11
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/BackgroundGpsNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackControlsTest.java
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_startup.py
  - app/docs/manifest.yaml
---

MOB-01/REL-03. AndroidX Stage читается в main thread без ошибочного преобразования
RESTARTED. Camera fixture обновляет сохранённую app camera вместе с native viewport,
который использует источник проверяемого круга. Cancel layout ждёт настоящий
OnDismiss и подтверждает один DOWN/UP на кнопке без повторения принятого input.
Системное активное окно определяет физическую цель. Явно отклонённый до доставки
DOWN допускает повторное измерение в общем30s budget; отказ после принятого DOWN
остаётся ошибкой. Профиль и доставленные события сохраняются. Production код,254 метода,
численные допуски и прежние функциональные assertions сохранены. Full14 остаётся
неуспешным; Related и новая полная приёмка выполняются отдельно.

После Related19 контроллер различает native assertions и fatal setup/transport
errors, освобождает оба verification packages и ограничивает ожидание первого
test-start marker60 секундами при прежнем общем900s budget. Реальные owned child
processes и scoped doubles проверены семью новыми regressions; все61 runner unit
PASS. Android startup trigger остаётся неустановленным; operator-induced
Process crashed сохранён отдельно и не объявлен багом рабочего приложения.

Адресная проверка после изменений: Related20,27 emulator +11 Android11 tablet
PASS; штатная финализация и package/version/global-settings readback PASS.
Guard26/docs24 со strict task diff PASS. Полные прогоны обоих устройств и
публикация выполняются после закрытия follow-up PR.
