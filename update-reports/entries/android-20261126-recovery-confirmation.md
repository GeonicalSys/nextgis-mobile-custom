---
id: android-20261126-recovery-confirmation
area: Восстановление проектов
kind: user
at: '2026-11-26T08:16:18.198953+00:00'
title: Подтверждение восстановления сохраняет действие при обновлении списка
why: Фоновый refresh мог включить loading во время открытого окна и заблокировать положительный ответ пользователя
covers:
  - app/src/main/java/com/nextgis/mobile/activity/ProjectRecoveryActivity.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectRecoveryNativeTest.java
  - agent-system/knowledge/ui-contracts/ui-contracts-ui-project-recovery.json
  - agent-system/knowledge/ui-contracts/ui-sources-wf-project-recovery-confirmation.json
  - agent-system/knowledge/ui-contracts/ui-contracts-registry.json
  - agent-system/knowledge/documentation/crash-recovery-recovery-dialog-and-host-lifecycle.json
  - agent-system/knowledge/documentation/app-module-readme-crash-recovery-ownership-and-geometry.json
  - docs/architecture/crash-recovery.md
  - app/docs/README.md
  - docs/registry/ui-contracts.json
  - agent-system/provenance/structured-views.json
  - update-reports/entries/android-20261126-recovery-confirmation.md
---

Пока открыто подтверждение восстановления, обновление списка откладывается.
Положительная кнопка входит в прежнюю операцию восстановления; после закрытия
окна refresh ставится в очередь главного потока, с проверкой lifecycle Activity.
Busy/loading и активные track/walk/stakeout/project-operation guards сохраняются.
Архив проходит прежние проверки, исходный проект сохраняется, копия не открывается
автоматически и остаётся удержанной до отдельной сверки.

Детерминированная регрессия в существующем native сценарии удерживает executor,
пытается обновить список при открытом подтверждении и проверяет реальный запуск
восстановления после положительного ответа. Бюджеты и критерии результата
не увеличиваются. Неуспешный screen case native02 сохраняется; новая компиляция
и native приёмка этого исправления ещё ожидаются.

Добавлены отдельный bounded UI source guard подтверждения и связь с прежним
контрактом восстановления. Все старые UI facets, lifecycle, unknowns и порядок
источников сохранены. Обновлены документация и производные представления.

Публикация 229 не выполнена. Стандартный Full — NOT_RUN; физические проверки
WAIVED только для 229 по текущему отдельному поручению пользователя. Этот
источник и карточка не являются универсальным runtime или выпускным PASS.

После изменения прошло восстановление через реальный экран с попыткой обновить список во время открытого подтверждения. Отдельная копия зарегистрирована с удержанной очередью, исходный проект сохранён. Проверка зафиксирована в `native-release229-03`.
