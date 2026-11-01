---
id: android-20261101-checkbox-value-contract-map
area: Документация и сопровождение
kind: internal
at: '2026-11-01T13:13:50+04:00'
title: Карта значения Checkbox и сохранения пустых полей
why: Не смешивать состояние флажка с типом поля и фактической записью значения
covers:
- docs/registry/ui-contracts.json
- docs/architecture/ui-contracts.md
- docs/roadmap/mobile.md
- docs/changelog.md
---

MOB-UI-01: источник checked-state, Boolean state/cache, Integer0/1 getter,
copy baseline и generic FTInteger TextEdit описаны раздельно. Source links
и проверки исходников добавлены к существующему owning каталогу; прежние
Date/Save/draft/unbound contracts и ожидания сохранены.

Связанные ongoing Draft PR87 и LISA PR278 содержат app/общую карту.
Границы present Checkbox, absent control и copied NULL/empty различаются;
actualtenant/schema/values/native outcome не доказаны. Баги не классифицированы.
Код, библиотеки, данные и версии не менялись; product fixes/ranking отложены.
Нативные/AVD/JVM/NGW/DB/Office проверки NOT_RUN. Actual source/docs проверки,
commit/push и merge/release фиксируются раздельно по receipts и PR.
