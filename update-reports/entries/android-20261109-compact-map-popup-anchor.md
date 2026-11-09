---
id: android-20261109-compact-map-popup-anchor
area: Мобильное приложение
kind: user
at: '2026-11-09T23:18:06+04:00'
title: Меню записи и измерений остаются доступны в тесном окне карты
why: Кнопка внутри меню дополнительных действий может иметь нулевой размер
covers:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/java/com/nextgis/mobile/view/MapControlRail.java
  - agent-system/knowledge/ui-contracts/ui-sources-map-click-actions.json
  - agent-system/knowledge/ui-contracts/ui-sources-map-init-autopan.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-cf6d88239faca089e51547bf.json
  - docs/registry/ui-contracts.json
  - agent-system/provenance/structured-views.json
---

Когда панель переносит команды в меню, выбор режима трека и инструменты
измерения открываются у видимой кнопки меню. При прямом касании меню
по-прежнему привязано к собственной кнопке.
