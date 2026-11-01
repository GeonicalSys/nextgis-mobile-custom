---
id: android-20261101-form-date-values-map
area: Документация и сопровождение
kind: internal
at: '2026-11-01T12:15:45+04:00'
title: Карта источников даты и времени в формах
why: Различать контролы, значения и сохранение даты при доработке форм экосистемы
covers:
- docs/registry/ui-contracts.json
- docs/architecture/ui-contracts.md
- docs/roadmap/mobile.md
- docs/changelog.md
---

MOB-UI-01: два новых контракта связывают стандартную и NGFP форму с реальными
factory/layout, selector namespaces, nullable millisecond carriers, default/last,
Bundle/cursor и ContentValues. Прежние44 UI контракта и их ожидания сохранены.
Это source/docs карта; два условных наблюдения и actual runtime policy неизвестны.

Сквозные storage/Save/broker связи относятся к LISA Draft PR278. Доставка этой
карточки идёт через отдельную app Draft; merge, версия и APK не выполняются.
Библиотеки, данные, schema, runtime и software behavior не менялись; миграции нет.
Исправления и ранжирование отложены. Нативные/AVD/JVM/NGW/DB проверки NOT_RUN.
Source/docs validators и Git delivery указываются отдельно по фактическим receipt.
