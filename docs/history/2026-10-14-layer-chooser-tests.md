---
title: Исправление выбора слоя и автоматическая приёмка на двух устройствах
type: history
last_verified: 2026-10-14
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/util/FittedDialogs.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LayerSelectionDialogTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectLayerSelectionTest.java
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/mobile_test_project.py
---

# Выбор слоя и мобильная приёмка

На подключённом Samsung API 36 с fontScale 1.15 список выбора показывал одну
строку и позволял прокруткой увидеть остальные. Adapter содержал все слои.
FittedDialogs не обнаруживал custom setView ListView через getListView и
оборачивал весь диалог в FittedPanelLayout с UNSPECIFIED height. Android
измерял вложенный список по первой строке. Исправление сохраняет bounded
native viewport любого вложенного AbsListView/RecyclerView.

До исправления native regression на изолированном verification APK телефона
показал `expected 3, actual 1` видимых строк. После исправления прошли три
chooser cases на смартфоне и эмуляторе: viewport, выбор всех трёх геометрий
настоящим касанием и последний элемент/Cancel. На эмуляторе прошли также
36 профилей density 420/540/617 × fontScale 1.0/1.3/2.0 × две темы × две
ориентации. В узком landscape крупный список прокручивается штатно.

Пользователь разрешил постоянное использование разработческого проекта 1403.
На телефоне его snapshot содержит 20 векторных слоёв: восемь разрешают создание,
в том числе три Collector-managed полевых слоя. ProjectLayerSelectionTest
прошёл через штатный MapFragment.addNewGeometry: все восемь предложены,
read-only исключены, viewport многорядный, Cancel принимает настоящий touch.
Snapshot содержит только whitelist layer policies. Accounts, credentials,
features, координаты и формы не копируются; проект телефона не изменён.
Отсутствующий или неоднозначный проект требует вопроса пользователю о замене.

Новый runner выполняет Related после доработки на эмуляторе, Full перед
публикацией на смартфоне и эмуляторе. Full включает core units, runner units,
docs, обычную APK version matrix, весь native catalog, диагностическую
offline/fatal/restart/reconnect/HTTP-retry цепочку и UI matrix. Required skips,
неполный native protocol и stale source receipt не принимаются. После Full
обязателен Check-Publication. Работа на телефоне идёт в отдельной verification
identity; глобальные настройки меняются только на эмуляторе и восстанавливаются.
Искусственные fixes не измеряют реальную RF accuracy и OEM поведение в лесу.

Изменения продолжают owned Draft app #60, maplib #52 и maplibui #37.
Publisher получает отдельный Draft только с инструкциями полного прогона.
Порядок закрытия: maplib #52 Merge Commit → maplibui #37 Merge Commit → fetched
merged library pins в app #60 Squash → publisher instructions Squash.
Локальный pin незамерженной library ветки не разрешает выпуск. Текущая задача
не включает merge или публикацию APK; все предыдущие GPS/delivery/stakeout
изменения сохранены в этой же цепочке.

Полный прогон дополнительно обнаружил повторный bind isolated-script instance
на Samsung Android 16: первый QuickJS вызов проходил, следующий терял binder
при завершении прежнего instance. Unique bindIsolatedService на API 29+
устранил гонку; все семь native script cases прошли на телефоне, включая
Unicode, отдельный UID, fresh VM, loops/OOM и реальную форму. Verification
Application также сохраняет ранний isolated-process guard до чтения prefs.
Fixture строит слой до добавления в видимую карту и выставляет own intro
marker перед проверкой real project clock.
