---
title: GlitchTip — разбор открытых Android-отчётов 2026-10-13
type: reference
last_verified: 2026-10-13
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/main/res/layout/status_panel.xml
  - app/src/main/java/com/nextgis/mobile/stakeout/StakeoutForegroundService.kt
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsHttpPolicy.java
  - maplib/src/main/java/com/nextgis/maplib/map/MapDrawable.java
  - maplib/src/main/java/com/nextgis/maplib/map/LocalVectorTileServer.java
  - maplib/src/main/java/com/nextgis/maplib/util/LocalLogInitializer.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/NGPreferenceActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
---

# Открытые Android-отчёты

Read-only ORM-снимок GlitchTip 2026-10-13 03:28:43 UTC: **8 открытых групп / 55
событий** NextGIS Mobile. Desktop и Setup verification не имеют открытых групп.
Проверены свежие события, устройства, release/source revision, mechanism и стеки;
счётчики не означают число уникальных пользователей или падений. Статусы и
события сервера сохранены. Персональные данные, credentials и сырые события
в этот документ не входят.

| Issue | События | Причина и результат проверки |
|---|---:|---|
| 45 | 47 | HTTP 503; свежие production-события `3.1.2.28+222` направлены в loopback `/tiles/` локального vector-tile server. Это handled SDK HTTP report, а не uncaught crash. Сервер выдаёт 503 при заполнении очереди либо недостаточном heap headroom; определить конкретную ветку по событию нельзя. Ограничение повторов действует. |
| 53 | 1 | Production ANR на realme RMX3930 / Android 15. Главный поток в ACTION_DOWN ждёт `MapLibreMap.queryRenderedFeatures` → native renderer future/condition_variable. Стек потока рендера без имён функций; причина остановки рендера и версия приложения не установлены. |
| 58 | 1 | Debug emulator, historical AppExitInfo: `HyperLog.initialize/pushLogs` сериализует backlog на main thread. Текущие entrypoints используют уже исправленный `LocalLogInitializer`, очищающий устаревший upload URL до первого initialize. |
| 59 | 2 | Тот же старый Debug startup path, остановка в `JSONObject.checkName`; исправление initializer уже присутствует. |
| 60 | 1 | Debug: `fillStatusPanel` вызывал setAlpha для отсутствующего background. В app `3dc1359` status_container получил собственный background; реальный старт/возврат карты и обе темы проверены. |
| 61 | 1 | Debug: отложенный запрос фонового разрешения пытался открыть окно после уничтожения Activity. App `3dc1359` проверяет added/resumed/finishing/destroyed; maplibui `5bab1d9f` также отклоняет неработающий host перед диалогом. |
| 62 | 1 | Debug: posted invalidatePreferences запускался после очистки listeners в закрытом экране. maplibui `5bab1d9f` проверяет lifecycle и null listeners; реальные настройки/повороты проверены. |
| 63 | 1 | Debug denied-location fixture: Android отверг location foreground service. SecurityException обработан, событие имеет failure_kind=handled; сервис освобождает ресурсы и уведомляет owner. Это проверенный отказ разрешения, не падение процесса. |

## Что остаётся открытым

Для 45 нужна нагрузочная проверка конкретной пользовательской карты с журналом
LocalVectorTileServer: queue full и heap headroom различаются только в локальном
журнале. Убирать защиту очереди/памяти либо скрывать все локальные 503 нельзя:
это лишило бы приложение backpressure и диагностического сигнала.

Для 53 нужно повторение на устройстве с профилем native renderer и состоянием
карты/режима. Синхронное ожидание на UI подтверждено, но один такой стек не
доказывает ошибку конкретного слоя, тайла или lock. Данных для выбора безопасного
изменения этого пути пока недостаточно.
Оба production issue остаются непроверенными в отношении устранения причины.

Ошибки 60–62 уже устранены в [app PR #59](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/59)
и [maplibui PR #36](https://github.com/GeonicalSys/android_maplibui/pull/36), но
ещё не выпущены: library Merge Commit → fetch/pin её remote merge SHA → app
Squash → отдельно согласованный выпуск. Повторных source changes для уже
исправленных Debug случаев в ходе этого разбора не потребовалось.

Проверки текущего UI: 11 native layout tests и 4 дополнительных real-window
сценария; font/display scale, реальные настройки и X/Cancel проверены в обеих
ориентациях. Все текущие fixtures используют явный synthetic DSN через
DiagnosticsTestRunner. [Политика диагностики](../architecture/error-reporting.md),
[прошлый разбор startup/503](../history/mobile-diagnostics-verification-2026-10-10.md).
