---
id: android-20261026-private-policy-fixture-transport
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T10:14:33Z
title: Передача тестового снимка через UID verification APK
why: Android11 планшета запрещает ADB доступ даже к внешнему files каталогу тестового APK
covers:
  - tools/run_mobile_tests.py
  - tools/tests/test_mobile_project_transfer.py
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectLayerSelectionTest.java
---

MOB-01/REL-03. На T40 Plus по Wi-Fi прошли26 form cases, но подготовка
ProjectLayerSelectionTest не смогла создать внешний files каталог из shell.
Runner передаёт согласованный metadata-only snapshot через stdin `exec-in
run-as` непосредственно в private files verification APK и сверяет SHA-256.
Временных общедоступных копий нет. Привязка к точному debug package и
разрешённому private parent отклоняет production/посторонний путь до записи.

Native тест проверяет canonical owning filesDir. Системные storage grants,
SELinux и production ЛИСА не меняются; доступ к её проектам не расширяется.
Пять runner regressions проверяют UID/path, hash, обрыв и UTF-8 transport.

Проверено: runner48 PASS, project-policy Related1/1 на T40 Plus/API30 по Wi-Fi
и1/1 на Samsung/API36. Полный набор остаётся отдельным обязательным этапом.
