---
id: android-20261124-walk-finish-recovery
area: Мобильное приложение
kind: user
at: '2026-11-24T14:40:27.490947+00:00'
title: Завершение обхода после остановки записи
why: Сохранить доступ к черновику, пока фоновая запись заканчивает сохранение точки
covers:
- agent-system/knowledge/dependencies/dependencies-runtime-contracts-independent-walk-session.json
- agent-system/knowledge/documentation/build-matrix.json
- agent-system/knowledge/documentation/crash-recovery-walk-finish-and-tail-commit.json
- agent-system/knowledge/documentation/crash-recovery.json
- agent-system/knowledge/documentation/documentation-changelog-2026-11-24-walk-finish-recovery.json
- agent-system/knowledge/documentation/documentation-changelog.json
- agent-system/knowledge/documentation/official-differences-walk-feature-capture.json
- agent-system/provenance/structured-views.json
- app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
- docs/architecture/crash-recovery.md
- docs/changelog.md
- docs/reference/build-matrix.md
- docs/reference/official-differences.md
- docs/registry/dependencies.yaml
- maplibui
- update-reports/entries/android-20261124-walk-finish-recovery.md
---

Карта сообщает о необходимости повторить сохранение, если остановленный обход ещё удерживает запись контрольной точки. Тест удерживает запись нового участка настоящим writer, проверяет состояние черновика и отсутствие преждевременной формы, затем сохраняет объект через штатную форму и сверяет геометрию и категорию в SQLite.

Область изменения охватывает карту Android и MaplibUI. Контракт не меняет формат черновика, порядок точек, ограничения владельца или сохранение категории. Применимые проверки, ревизии и результаты до выпуска фиксируются в квитанциях текущей задачи.
