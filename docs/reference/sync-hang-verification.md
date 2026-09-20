---
title: Проверка доработки зависаний синхронизации
type: reference
last_verified: 2026-09-18
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/NgwResourceSelectionState.java
  - app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
---

# Проверка доработки зависаний синхронизации

## Основание

В диагностическом логе 17 сентября 2026 года последняя незавершённая загрузка
`SYNC_NONE` оставалась активной более 94 минут до перезапуска процесса. На пути
snapshot отсутствовали connect/read timeouts. Отдельно зарегистрирован
`TransactionTooLargeException`: около 850 КБ приходилось на `connections` в Bundle.
Точного ANR stack в логе нет; блокировка main thread на badge SQLite query —
проверяемая гипотеза, а не доказанная единственная причина ANR. Исходный лог
содержит credentials и в репозиторий не включается.

На отдельной Lisa Debug (`com.nextgis.mobile.debug`, 3.1.2.20/215) на Samsung
SM-A566B, Android 16/API 36 17.09 в 20:29 воспроизведён настоящий ANR при
открытии «Добавить из Веб ГИС» с активным GPS. Android dropbox фиксирует:
`main → MapFragment.applyLocationFixToMap → TrackerService.hasUnfinishedTracks
→ TrackLayer.query → SQLiteConnectionPool.waitForConnection`.
В том же report `SyncAdapterThread-1` выполняет `NGWVectorLayer.applyFullSnapshot
→ compareFeature → Feature.equalsData → getFieldValueIndex → LinkedList.get`.
Таким образом доказана конкретная блокировка в Debug; тождество с исходным
пользовательским ANR без его стека не утверждается. Исправление переносит оба
incremental track reads с main thread, а сравнение полей убирает вложенные
indexed LinkedList scans. Запись точек/сегментация и backup/transaction gate
не изменены.

## Автоматические проверки

- `NgwSyncIoTest`: HTTP/HTTPS defaults; молчание заголовков и тела на loopback
  HTTP server с укороченным тестовым timeout; сохранение interrupt-флага.
- `NGWSyncServiceStateTest`: finish другого потока не завершает активный worker.
- `CoalescingRefreshTest`: 10 000 GPS-событий дают только один повтор; завершение
  и отбрасывание результата освобождают слот.
- `FeatureComparisonTest`: широкие linked schemas без indexed get, порядок,
  переименование, первое дублирующееся имя, missing/null и numeric/date equality.
- `NgwSnapshotCheckpointTest`: scope/count/TTL/clock/force/corrupt state и
  отсутствие ложного учёта пропущенной геометрии с существующей локальной строкой.
- `NgwResourceSelectionStateTest`: 10 000 невыбранных ресурсов не увеличивают
  JSON; credentials не сохраняются; remote path и оба флага переходят на новые
  process-local IDs; отсутствующий account/ресурс/неполная загрузка дают ошибку.

18.09.2026: `:maplib:testDebugUnitTest` — 323 tests, `:maplibui:testDebugUnitTest`
— 70 tests; failures/errors/skipped = 0. `:maplibui:assembleDebug`,
`:app:assembleLisaDebug`, `:app:compileLisaReleaseJavaWithJavac` и
`:app:compileBelkaReleaseJavaWithJavac` завершились успешно.
`tools/docs-check.ps1 -RunTests` и проверка полного changed-file списка с
`--enforce-diff` прошли; 7 tests документационного инструментария.
Это компиляция release sources, не полные production APK gates; release-сборки
отложены до закрытия library→app dependency chain.

## Фактическая проверка устройства

18.09 Debug обновлён через `adb install -r --user 0` без очистки данных, затем
принудительно остановлен и запущен (Activity start Status ok, WaitTime 3025 ms).
Автоматический sync после запуска дошёл до commit небольших snapshots;
count check «Выдела» 31407/31407 и «Квартала» 931/931 дал KEEP. Большие слои
уже были согласованы до этой проверки, поэтому это не замер ускорения их
полного повторного apply. Production packages, версии, аккаунты и проекты
вручную не изменялись; серверный импорт и ручной forced sync не запускались.

## Обязательные проверки на Android — пока не выполнены

`SMOKE-NGW-SYNC-RECOVERY`, `SMOKE-NGW-SELECTOR-RESTORE`,
`SMOKE-MULTI-ACCOUNT-SYNC`, `SMOKE-NGW-LARGE-PULL-CACHE`,
`SMOKE-LAYER-SYNC-SETTINGS`. Проверить реальные HTTP→HTTPS redirect, медленную
сеть/потерю сети, нехватку диска, отмену во время загрузки и DB apply, убийство
процесса во время транзакции, rollback и повтор после запуска с сохранёнными
pending edits/вложениями и блокировкой смены проекта до выхода worker.
Для selector: поворот/фон/process recreation, выбранные ресурсы из нескольких
ветвей, сетевой отказ с повтором, закрытие во время restore и фактический размер
Android Parcel. JVM JSON-тест не заменяет эти lifecycle/SQLite/Binder проверки.
При повторении ANR сохранить bugreport/thread dump до закрытия приложения;
сопоставить main-thread stack с `NGW snapshot attempt/stage/elapsedMs`.

## Доставка

Изменения владельцев входят в выпуск `3.1.2.22`: maplib Merge Commit #32,
maplibui Merge Commit #20, затем pin remote merge commits в app. Device gates
ниже остаются открытыми. Publishing repository и desktop/QGIS код не изменяются.
