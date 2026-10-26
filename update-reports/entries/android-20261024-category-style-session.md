---
id: android-20261024-category-style-session
area: Мобильное приложение
kind: internal
at: 2026-10-24T14:27:02+02:00
title: Проверено редактирование через настоящий список категорий
why: Изолированный тест самого диалога не обнаруживал передачу исходного объекта из настоящего списка категорий
covers:
  - tools/mobile-test-plan.json
  - app/src/androidTest/java/com/nextgis/mobile/rendering/NativeStyleTouch.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
  - maplibui
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleEditorTest.java
---

Native-проверка нажимает реальную строку категории, проверяет унаследованные поля, изменяет шаг и выполняет реальные Cancel/Save. Cancel сохраняет исходный JSON, а Save заменяет только выбранную категорию; сериализация и «Прочие» проверяются отдельно.

Перед касанием helper требует фокус своего окна, полную видимость в физическом viewport и стабильные координаты после поворота/прокрутки. Штатная targeted UID защита Instrumentation не обходится.
