---
id: android-20261023-updater-native-lifecycle
area: Мобильное приложение
kind: internal
at: 2026-10-23T13:00:00+02:00
title: Проверка загрузки обновления работает после закрытия начального экрана
why: Native regression должен проверять transfer без живого Activity независимо от первого запуска приложения
covers:
  - app/src/androidTest/java/com/nextgis/mobile/util/AppUpdateNativeTest.java
---

Первый MainActivity может завершить startup route. Native test явно закрывает
ActivityScenario, подтверждает DESTROYED и лишь затем отпускает transfer response;
после скачивания проверяются байты и отсутствие .part. Попытки восстановить
уничтоженный Activity больше нет. Production updater/runtime не меняется.
