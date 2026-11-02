---
id: android-20261102-git-eol
area: Сопровождение
kind: internal
at: '2026-11-02T12:00:00+04:00'
title: Общие правила переносов строк и отдельная нормализация Gradle launcher
why: Устранить зависимость текстовых файлов от Windows core.autocrlf
covers:
  - .gitattributes
  - gradlew.bat
  - docs/roadmap/mobile.md
  - docs/reference/official-differences.md
  - maplib
  - maplibui
  - easypicker
---

MOB-DEV-01: общий блок согласован с lisa и библиотеками. Тексты LF,
Windows-скрипты CRLF при checkout/export, ресурсы binary. gradlew.bat хранится
с LF в Git отдельным normalization commit; команды и результат запуска не
меняются. После Merge Commit библиотек maplib67/maplibui49/easypicker6 pins
указывают на fetched merged commits `7152e3cd`/`69cccf20`/`d4405ab4`.
Trees библиотек побайтно равны проверенным task tips; дополнительных
изменений исходников или ресурсов при переходе на merged pins нет.
Карта, данные, sync, фон и батарея не меняются. APK не публикуется.
