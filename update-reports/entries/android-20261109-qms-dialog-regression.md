---
id: android-20261109-qms-dialog-regression
area: Мобильное приложение
kind: internal
at: '2026-11-09T14:06:14+04:00'
title: Регрессия жизненного цикла каталога QMS
why: Воспроизвести поздний ответ каталога при GlitchTip №143
covers:
  - app/src/test/java/com/nextgis/mobile/util/QmsCatalogDialogTest.java
---

Robolectric доставляет синтетические ответы после закрытия диалога и после смены запроса. Действующий запрос заполняет строки. Сценарий зависит от исправления MaplibUI.
