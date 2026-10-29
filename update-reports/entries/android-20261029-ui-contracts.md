---
id: android-20261029-ui-contracts
area: Документация и сопровождение
kind: internal
at: 2026-10-29T14:30:00+01:00
title: Проверяемые источники значений и действий мобильного интерфейса
why: При диагностике координаты устройства ошибочно приняли за центр проекта; знания об интерфейсе должны быть связаны с реальными producers и состояниями
covers:
  - AGENTS.md
  - app/AGENTS.md
  - app/docs/README.md
  - app/docs/manifest.yaml
  - docs/START-HERE.md
  - docs/guides/change-checklist.md
  - docs/architecture/ui-contracts.md
  - docs/architecture/map-rendering.md
  - docs/architecture/location-pipeline.md
  - docs/registry/ui-contracts.json
  - docs/registry/change-impact.yaml
  - docs/registry/invariants.yaml
  - docs/registry/smoke-tests.yaml
  - docs/roadmap/mobile.md
  - docs/scripts/check_ui_contracts.py
  - docs/scripts/validate_docs.py
  - docs/tests/test_ui_contracts.py
---

MOB-UI-01, связан с lisa-20261029-mobile-ui-contracts. Входные AGENTS и checklist
требуют до действия/наблюдения UI установить widget, producer/transform,
variant/conditions/units/CRS/freshness, handler/state и сохранённую работу.
Missing contract агент исследует и сохраняет без отдельной команды пользователя.

36 source-backed контрактов в11 группах различают display/raw location и
camera/viewport/extent, locate/условный autopan/вынос, компас/heading, project
identity/имя, видимость/права записи, typed form/draft/SQL writes, полный sync
clock/pending/registration и выбранные track IDs. Детали подтверждены80 телами
методов и46 XML декларациями; динамические controls имеют отдельные ограничения.

Checker выполняется обычным docs validator: source drift, потеря/переназначение
producer, неверные ссылки/пути/XML IDs и ложное заявление runtime PASS блокируют
проверку. Отдельные source mutation tests ловят комментарий/литерал вместо кода,
expression body/соседний метод, callsite и boolean вместо schema version. Hashes
не обновляются автоматически и сами по себе не доказывают правильность поведения.

Продукт, Gradle/версии, library gitlinks, БД/NGW/Collector, sync, геопозиция и
данные не меняются; миграции нет. Лесосеки/НЭП сейчас mobile read-only,
area_origin не добавляется в JSON. Реальные UI/device/flavor пилоты NOT_RUN,
непроверенные ветви и полное покрытие остаются UNKNOWN. Проверки этого этапа —
source/docs, без APK/build/native/AVD или возобновления массовой приёмки.
