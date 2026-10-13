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

# Разобранные Android-отчёты

ORM-снимок GlitchTip 2026-10-13 10:35:32 UTC: **10 открытых групп / 65
событий** NextGIS Mobile.
Проверены свежие события, устройства, release/source revision, mechanism и стеки;
счётчики не означают число уникальных пользователей или падений. По поручению
пользователя все десять групп отмечены resolved как обработанные
2026-10-13 12:43 Europe/Warsaw (10:43 UTC) штатным update_issue_status.
После операции открытых мобильных групп нет; события и счётчики сохранены.
Закрытие означает завершённый разбор и подготовленную диагностику,
а не выпуск либо доказанное устранение неизвестной production причины.
Персональные данные, credentials и сырые события
в этот документ не входят.

| Issue | События | Причина и результат проверки |
|---|---:|---|
| 45 | 53 | HTTP 503; свежие production-события `3.1.2.28+222` направлены в loopback `/tiles/` локального vector-tile server. Это handled SDK HTTP report, а не uncaught crash. Сервер выдаёт 503 при заполнении очереди либо недостаточном heap headroom; определить конкретную ветку по событию нельзя. Ограничение повторов действует. |
| 53 | 1 | Production ANR на realme RMX3930 / Android 15. Главный поток в ACTION_DOWN ждёт `MapLibreMap.queryRenderedFeatures` → native renderer future/condition_variable. Стек потока рендера без имён функций; причина остановки рендера и версия приложения не установлены. |
| 58 | 1 | Debug emulator, historical AppExitInfo: `HyperLog.initialize/pushLogs` сериализует backlog на main thread. Текущие entrypoints используют уже исправленный `LocalLogInitializer`, очищающий устаревший upload URL до первого initialize. |
| 59 | 2 | Тот же старый Debug startup path, остановка в `JSONObject.checkName`; исправление initializer уже присутствует. |
| 60 | 1 | Debug: `fillStatusPanel` вызывал setAlpha для отсутствующего background. В app `3dc1359` status_container получил собственный background; реальный старт/возврат карты и обе темы проверены. |
| 61 | 1 | Debug: отложенный запрос фонового разрешения пытался открыть окно после уничтожения Activity. App `3dc1359` проверяет added/resumed/finishing/destroyed; maplibui `5bab1d9f` также отклоняет неработающий host перед диалогом. |
| 62 | 1 | Debug: posted invalidatePreferences запускался после очистки listeners в закрытом экране. maplibui `5bab1d9f` проверяет lifecycle и null listeners; реальные настройки/повороты проверены. |
| 63 | 1 | Debug denied-location fixture: Android отверг location foreground service. SecurityException обработан, событие имеет failure_kind=handled; сервис освобождает ресурсы и уведомляет owner. Это проверенный отказ разрешения, не падение процесса. |
| 64 | 3 | Production: исходное исключение при enqueue было заменено общим Cannot persist sync queue. Теперь сохранены cause, phase, counts, JSON size и storage free; убрано лишнее чтение legacy inventory. |
| 65 | 1 | Production: ещё один local vector-tile 503 с последней operation=SYNC. Покрывается теми же explicit rejection headers и stage/load diagnostics, что 45. |

## Что диагностировано и подготовлено

Для 45/65 нужна нагрузочная проверка конкретной пользовательской карты с журналом
LocalVectorTileServer: queue full и heap headroom различаются только в локальном
журнале. Убирать защиту очереди/памяти либо скрывать все локальные 503 нельзя:
это лишило бы приложение backpressure и диагностического сигнала.

Для 53 нужно повторение на устройстве с профилем native renderer и состоянием
карты/режима. Синхронное ожидание на UI подтверждено, но один такой стек не
доказывает ошибку конкретного слоя, тайла или lock. Данных для выбора безопасного
изменения этого пути пока недостаточно.
Production renderer/pressure причины остаются непроверенными в отношении
устранения. Подготовлен independent observer с map mode, lifecycle/scene/frame
state, heap, активными tile stages, caller stack и private snapshot для delayed
AppExitInfo. Local 503 явно различает queue_full и heap_headroom с request id.
Эта диагностика реализована в maplib f6735d2, [PR #50](https://github.com/GeonicalSys/android_maplib/pull/50),
и app 79938ca, PR #59.

Issue 64 теперь сохраняет исходную причину и фазу отказа enqueue, размеры
очереди и свободное место вместо общего Cannot persist sync queue.
Необязательный повторный legacy inventory устранён; прежние pending pairs
сохраняются при ошибке. Без исходной причины прежних событий нельзя объявлять
конкретную storage/JSON проблему исправленной.

Проверки: 533 maplib + 123 app unit tests; native observer записал свежий снимок
при заблокированном main looper и передал его в Sentry context. Реальная полная
очередь выдала правильные headers, 7 project/account sync и 2 ruler/azimuth
gesture scenarios passed. Lisa Debug/test APK и Lisa/Belka Release source compile
прошли. Пользовательские production устройства и native profile не проверялись.

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
