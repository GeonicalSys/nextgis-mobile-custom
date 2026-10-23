---
id: android-20261023-release-3-1-2-30
area: Мобильное приложение
kind: internal
at: 2026-10-23T20:52:00+02:00
title: Подготовить подписанный Lisa Release 3.1.2.30 без публикации
why: Выдать пользователю локальную сборку с новыми функциями и новой production версией
covers:
  - app/build.gradle
  - tools/verify-apk-version-matrix.ps1
---

Production версия 3.1.2.30/224 согласована с maplib; Debug остаётся 3.1.2.23/218.
LisaOnly проверяет только Lisa Debug/Release и версии maplib, сохраняя полный
default набор для будущего Full. Belka APK и публикация отложены пользователем.
Перед release-сборкой закрывается цепочка maplib #59 → maplibui #43 → app #77.
