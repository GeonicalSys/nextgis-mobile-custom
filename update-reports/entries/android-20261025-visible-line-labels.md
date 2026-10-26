---
id: android-20261025-visible-line-labels
area: Мобильное приложение
kind: user
at: 2026-10-25T18:25:00+01:00
title: Читаемые линейные подписи для текущего экстента
why: Слова ломались на изгибах и ориентировались без учёта видимой части объекта
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VisibleLineLabelsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - tools/mobile-test-plan.json
  - tools/mobile_style_evidence.py
  - tools/tests/test_mobile_style_evidence.py
  - maplib
---

По выбранному пользователем правилу остаётся одна цельная подпись каждого
объекта на текущий экран, ориентированная по преобладающему направлению
видимых сегментов. Буквы не изгибаются и могут пересекать сегменты линии.
Технические native проверки проходят winding geometry, pan/zoom/rotation,
MultiLineString/category и picking. PNG сохраняются до очистки verification
данных. Контракт: `docs/architecture/visible-line-labels.md`.
