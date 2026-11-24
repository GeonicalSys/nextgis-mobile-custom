---
id: android-20261124-field-points-pgs-fixture
area: Мобильная ЛИСА
kind: user
at: 2026-11-24T08:45:52Z
title: Значок ПГС для стандартных полевых точек
why: Чтобы отличать точки ПГС на карте и в списке типов объектов класса «Транспорт»
covers:
  - agent-system/knowledge/app-module-manifest/app-module-key-components-standardfeaturetypestest-b697d9c93c.json
  - agent-system/knowledge/documentation/app-module-readme-feature-category-draft-and-walk-point-cancel.json
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/feature-type-creation-standard-category-fixture-and-boundaries.json
  - agent-system/knowledge/documentation/official-differences-point-style-editor.json
  - agent-system/provenance/structured-views.json
  - app/docs/README.md
  - app/docs/manifest.yaml
  - app/src/androidTest/assets/feature-types/README.md
  - app/src/androidTest/assets/feature-types/standard-field-types.json
  - app/src/androidTest/java/com/nextgis/mobile/reliability/StandardFeatureTypesTest.java
  - app/src/main/assets/marker_icons/pgs.png
  - docs/architecture/feature-type-creation.md
  - docs/reference/build-matrix.md
  - docs/reference/official-differences.md
---

Для типа «ПГС» добавлен значок жёлтого самосвала. На карте он показывается на белом круге с синей обводкой по настройкам «труба». В списке типов отображается сам значок. Фикстура сверена с Desktop-формой и мобильным конфигом, тест проверяет наличие PNG в APK, рисование в превью и заполнение пары «транспорт / ПГС» в форме.
