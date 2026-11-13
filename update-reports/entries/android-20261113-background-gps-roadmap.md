---
id: android-20261113-background-gps-roadmap
area: Дорожная карта мобильного приложения
kind: internal
at: '2026-11-13T03:58:48+04:00'
title: Записать план управления обычным фоновым GPS GEO-02
why: Нужны остановка обычного GPS при смахивании и временное отключение фона из уведомления
covers:
  - agent-system/knowledge/documentation/mobile-roadmap-background-gps-control.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-13-background-gps-roadmap.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - docs/roadmap/mobile.md
  - docs/changelog.md
  - update-reports/entries/android-20261113-background-gps-roadmap.md
---

[GEO-02](../../docs/roadmap/mobile.md#geo-02-управление-обычным-фоновым-gps)
описывает остановку автоматических подписок при смахивании приложения и
действие уведомления «Отключить GPS в фоне» до нового запуска после закрытия.
Возврат из фона и пересоздание экрана сохраняют временный запрет; постоянная
настройка не меняется. Активные трек, обход и вынос продолжаются.

В план включены установление версии и воспроизведения, разделение владельцев,
защита от отложенных и устаревших команд, критерии unit/Related и отдельные
границы проверки устройства и выпуска. Уже существующий обработчик смахивания
не выдан за исправление установленного APK.

Состояние «Предложена», приоритет P1; текущая очередь не меняется.
Сохраняется только план, реализация, устройство и выпуск не начаты.
Прежние незавершённые изменения сохранены по прямому поручению владельца;
их отдельные статусы и доставка остаются без изменений.
