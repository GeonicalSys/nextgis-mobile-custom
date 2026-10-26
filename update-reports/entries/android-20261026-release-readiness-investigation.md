---
id: android-20261026-release-readiness-investigation
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T13:51:33Z
title: Проверка готовности выпуска и подтверждение сбоя локальных тайлов без сети
why: Успех полного прогона со смартфоном не закрывает планшетную приёмку и исходную красную проверку CI
covers:
  - docs/reference/mobile-full-2026-10-25.md
  - docs/roadmap/mobile.md
  - app/docs/README.md
  - docs/changelog.md
---

MOB-01/MVT-01/REL-03. Full09 на67f0125:253/253 native PASS, все предварительные
проверки и cleanup PASS. Tablet Full01 имеет133 подтверждённых положительных
исхода,1 failed native method и119 незавершённых методов после timeout900s
Wi-Fi ADB. Это не полный PASS. Сбой Android11 расположен в тестовом
decor.draw до attach; обычное падение диалога не доказано.

Исходный FireflyTileFallbackTest без изменений выполнен на owned AVD online
и с полностью отключёнными wifi/data. Первый PASS, второй FAIL в начальной
загрузке native VectorSource. SDK Offline откладывает и HTTP loopback;
настройки радио восстановлены. Причастность апгрейда SDK и точная причина
оригинального CI без radio snapshot не объявлены доказанными.

Результаты сохранены в квитанции и дорожной карте. Код продукта/тестов не
исправлялся, source fingerprint прежний. Подготовлен подписанный локальный
APK3.1.2.30/224 по прежнему исключению; он содержит известный дефект и не
является готовым выпуском. Все3 связанные PR остаются Draft, публикации нет.
