---
id: android-20261101-mobile-project-labels
area: Мобильное приложение
kind: user
at: 2026-11-01T18:38:57+04:00
title: Понятные названия мобильных проектов в нашем приложении
why: Видимые подписи Collector создавали впечатление отдельного приложения, хотя загрузка проекта выполняется внутри нашего mobile.
covers:
  - app/src/main/res/values/strings.xml
  - app/src/main/res/values-ru/strings.xml
  - app/docs/README.md
  - docs/architecture/ui-contracts.md
  - docs/registry/ui-contracts.json
  - docs/roadmap/mobile.md
  - docs/changelog.md
---

MOB-UI-01: пять EN и пять RU значений списка и сообщений называют ресурс
мобильным проектом. Прежние resource keys, placeholders, порядок, остальные
переводы, client logic, IDs, API и packages сохранены. Общие maplibui сообщения
меняются своим владельцем; их доставка и pointers фиксируются отдельно.

Новый `UI-MOBILE-PROJECT-LOAD` закрепляет существующий маршрут «Загрузить проект»:
выбор аккаунта/строки каталога `lisa`, NGW GET выбранного remoteId и импорт в
наш owning workspace/fill. Это другой сценарий, чем chooser уже сохранённых
проектов. Названия протокола `collector_project` и исходных классов не меняются.
Все48 прежних UI contracts и связанные source/history значения сохранены;
два App file witnesses пересмотрены только для указанных переводов, оригиналы
сохранены в coverage history. Новые guards — source drift checks, не тесты UI.

Семантический binding review, XML parse и точное сохранение прежних cohort
проверены отдельно от будущих build/docs receipts. Эта правка не меняет данные,
sync/map/background/battery поведение. Device/font/rotation/native/NGW/customer
acceptance, APK и выпуск не выполнялись; resource build и общий docs validator
ведёт Root по фактическим результатам, без обещания полного импорта.
