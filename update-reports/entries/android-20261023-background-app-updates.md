---
id: android-20261023-background-app-updates
area: Мобильное приложение
kind: user
at: 2026-10-23T00:16:05+02:00
title: Обновление скачивается заранее и предлагается готовым при запуске
why: Пользователь может установить заранее загруженное обновление без ожидания скачивания при открытии приложения
covers:
  - app/src/main/java/com/nextgis/mobile/MainApplication.java
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateManager.java
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateDownload.java
  - app/src/main/java/com/nextgis/mobile/util/AppUpdateWorker.java
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-ru/strings.xml
  - app/src/main/res/xml/provider_paths.xml
  - app/src/test/java/com/nextgis/mobile/util/AppUpdateDownloadTest.java
  - app/src/test/java/com/nextgis/mobile/util/AppUpdateNetworkStateTest.java
  - app/src/test/java/com/nextgis/mobile/util/AppUpdateNetworkGuardTest.java
  - app/src/test/java/com/nextgis/mobile/util/AppUpdateReadyStateTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/AppUpdateNativeTest.java
  - tools/mobile-test-plan.json
  - app/docs/manifest.yaml
---

Приложение проверяет и скачивает обновление в фоне по Wi-Fi и мобильному
интернету без роуминга. При обрыве связи загрузка продолжится после появления
подходящей сети. Проверка при запуске сохранена.

После проверки файла при открытии показывается «Обновление готово» с кнопками
«Обновить» и «Позже». Отложенное обновление остаётся в приложении и доступно
без повторной загрузки, в том числе без интернета. Установка начинается только
по нажатию пользователя, с обычным подтверждением Android.

Android может отложить фоновую работу; принудительная остановка приложения
приостанавливает её до следующего открытия. Это не обещание готового обновления
к каждому запуску. Подлинность приложения, подпись и целостность APK проверяются
до предложения установки и повторно перед передачей файла Android.
