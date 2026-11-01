---
id: android-20261102-debug-underlay-progress
area: Мобильное приложение
kind: user
at: 2026-11-02T01:54:51+04:00
title: Показывать этапы переноса подложек из прежнего Debug
why: Пользователи сообщили об отсутствии видимого прогресса и10мин ожидания до отмены
covers:
  - app/src/main/java/com/nextgis/mobile/util/LegacyUnderlayImporter.java
  - app/src/main/java/com/nextgis/mobile/util/LegacyUnderlayProgressText.java
  - app/src/main/java/com/nextgis/mobile/activity/UnderlayCatalogActivity.kt
  - app/src/main/res/layout/activity_underlay_catalog.xml
  - app/src/main/res/values/underlays.xml
  - app/src/main/res/values-ru/underlays.xml
  - app/src/test/java/com/nextgis/mobile/util/LegacyUnderlayTransferSessionTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/LegacyUnderlayProgressNativeTest.java
---

MOB-REL-01, прямое дополнение владельца2ноября. В существующем коде есть
byte/tile progress и90s stream timeout с одним retry, но после complete record
watchdog останавливался до checkpoint/валидации/hash/publish. Счётчики могли
долго оставаться одинаковыми без видимого этапа местной работы. Это конкретный
source gap наблюдаемости, не установленная причина всей десятиминутной задержки.

Добавлены этапы подготовки, получения, проверки/сохранения, подключения и
неотвечающего источника, время текущей попытки по monotonic clock и compact
счётчики. Heartbeat/progress живёт до закрытия transfer session; remote stream
timeout не применяется к уже завершённой передаче и локальному attach.
Поздний descriptor после timeout закрывается до чтения/публикации. Cancellation
до старта queued worker освобождает lease и возвращает UI; stale/canceled
callbacks не перезаписывают состояние текущей операции.

Текст прогресса прокручивается отдельно, Cancel остаётся фиксированным;
на время переноса список и другая кнопка импорта скрыты. Byte count не объявляет
полную миграцию законченной. Оригиналы Debug, wire schema, signing/URI grants,
atomic stage/catalog/attach, повтор/дедупликация и прочие бизнес-данные сохраняются.
Lifetime/UI регрессии подготовлены и скомпилированы; execution NOT_RUN.
Source compile и фактические результаты записываются в handoff. По поручению владельца новые live проверки
отложены до следующей команды; реальная причина задержки требует device evidence.
