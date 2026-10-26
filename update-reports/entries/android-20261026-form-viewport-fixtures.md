---
id: android-20261026-form-viewport-fixtures
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T00:53:00Z
title: Проверки форм в широком альбомном окне и прокрученной области
why: Full показал невидимые gesture targets и имя, полностью помещающееся на одной строке планшетного по ширине окна
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/CascadingFormsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/FormAppearanceTest.java
  - tools/mobile-test-plan.json
---

MOB-01/REL-03. В Full05 Samsung имел окно 2340x1080: checkbox находился ниже
видимой области формы, selected EditText — выше. Fixture сначала штатно
прокручивает touch band контрола в viewport, затем ждёт стабильных границ
и выполняет единственный исходный жест. Сценарии преднамеренной прокрутки
самого ScrollView, направление, повторения и проверки значений сохраняются.

Для выбранного имени учитывается реальная ширина текста и контрола: одна
строка допустима, когда весь текст помещается. Строгий native wrap проверяется
дополнительно на 320/280dp компонентах в обеих темах. Проверяются все строки,
отсутствие ellipsis, полная высота Layout и завершённость раскладки. Системные
настройки рабочего устройства и production FormScrollView/controls не меняются.
Related form-layout включает оба класса на эмуляторе и явно выбранном устройстве.
