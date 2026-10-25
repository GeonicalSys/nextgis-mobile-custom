---
title: Треки и линейные подписи — проверка доработок 25 октября
type: reference
last_verified: 2026-10-25
related_code:
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - maplib/src/main/java/com/nextgis/maplib/map/SharedTrackStore.java
  - maplib/src/main/java/com/nextgis/maplib/map/TrackMapStyle.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/TrackStopConfirmationActivity.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/TrackStopConfirmationTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LegacyTrackColorsNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackerStopRecoveryTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VisibleLineLabelsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
---

# Журнал и результат

Пользователь разрешил чтение сегодняшнего лога через ADB и проверку на
подключённом устройстве. Прочитан уже существовавший HyperLog export рабочей
ЛИСЫ 3.1.2.30/224, выгруженный 25 октября около 16:11 local. SHA-256 исходного export: `9b8fb61de28d1d5232d20b270503eaaf8da8417909914bcd1103143e25d81e63`,
76 671 769 байт. Его последние записи
относятся к этому времени; это не полный журнал до окончания дня. Исходный
export хранится приватно на Q вне Git до пересмотра 1 ноября; извлечённые
события сохранены отдельно без пользовательских данных. Рабочую установку и её данные
тестовый runner не изменяет.

| UTC 2026-10-25 | Время устройства, Europe/Sarajevo | Свидетельство |
|---|---|---|
| 12:33:11.030 | 13:33:11 | stop requested source=menu → TRACK_STOP → stopTrack |
| 15:08:57.391 | 16:08:57 | stop requested source=menu → TRACK_STOP → stopTrack |

В обоих stopTrack: insertFail=0, pending=0. onDestroy следует уже после команды
Stop и нормального завершения. После первого события запись включена вновь
через 85 секунд, после второго — через 108 секунд. Старый source=menu общий
для меню и кнопки карты: различить эти два входа или доказать случайность
нажатия невозможно. Признаков самопроизвольного падения recorder в этих двух
цепочках нет. Это вывод по указанным событиям, не гарантия отсутствия иных проблем.

## Доработка

- Чёткая линия 2 вместо 4, свечение 6/blur 2 вместо 10/blur 3, выделение 4 вместо 7.
- Map/menu/notification требуют положительного ответа. Cancel/Back не создают
  pending_stop, не сбрасывают флаг записи и не закрывают текущую строку.
- Durable UUID отделяет пользовательский сеанс от его track ID. Split и
  recovery сохраняют UUID; предыдущие диалог/уведомление не останавливают новый.
- SharedTrackStore v1 → v2 транзакционно назначает цвета только SQL NULL.
  Первые 12 таких треков получают разные оттенки. Поздно обнаруженная история
  получает те же правила в транзакции импорта. Цвет сохраняется; повторный
  запуск не перекрашивает треки. Любой явно сохранённый цвет остаётся прежним.
- Отказ восстановления при выключенной системной геолокации сохраняет intent
  записи, UUID и незавершённую строку. Публичный запуск проверяет условия
  location FGS до запроса Android; защитная ветка сервиса также сохраняет intent.
  После включения геолокации запись можно восстановить в том же сеансе.
- [Линейная подпись](../architecture/visible-line-labels.md) остаётся цельным
  прямым блоком: одна на SQL-объект для его видимой части, по преобладающему
  направлению видимых сегментов. Она может пересекать собственную линию;
  длинный прямой сегмент не требуется. Pan/zoom/rotation обновляют размещение.
- Карандаш переименования использует штатный активный значок с alpha 80%
  вместо прежнего 48%; действие не меняется. Из редактора «Светлячка» удалена
  лишняя кнопка «Образец „Полевые точки“» и её preset handler. Живой образец,
  ручные параметры, Save и Cancel сохранены.
- Основная загрузка геометрии, тайлы, серверный поток, start/end flags,
  MapLibre 13.7.1 OpenGL и desktop не переделываются. Для подписей добавлен
  производный point source из уже загруженных объектов без нового чтения SQLite.

Объём относится к текущему MOB-01; сохранность Stop/recovery связана с REL-03,
восстановление GPS — с GEO-01, доступность действий — с UX-03. Приоритеты дорожной карты
не изменялись.

## Проверки

| Проверка | Фактический результат |
|---|---|
| `:maplib:testDebugUnitTest :maplibui:testDebugUnitTest :app:testLisaDebugUnitTest` | 887 PASS: 614 + 93 + 180; 0 failures/errors/skips |
| `:app:compileLisaReleaseKotlin :app:compileBelkaReleaseKotlin` | BUILD SUCCESSFUL на финальных исходниках |
| Related `line-labels,track-stop-ui,recording-intent,firefly-editor-ui`, Android 16 / API 36 emulator | 11 из 11 PASS, без skips; `accepted-native-final-02/result.json` |
| UI внутри этих native методов | 24 русских профиля Stop и 72 профиля редактора «Светлячка»: обе темы, обе ориентации, fontScale 1.0/1.3/2.0, включая ширину 320/280 dp |
| Скрипты маршрутизации/сбора evidence | 27 unit PASS |
| `tools/docs-check.ps1 -RunTests` | Документация валидна, 24 теста PASS |

Source fingerprint финального успешного native прогона и проверенного кода:
`28b1524d67a226161894e403d4a926b568db4a18f495668de77dbd73ee2e28a7`.
JBR 21, установленный Android SDK и общий Gradle cache переиспользованы.

Unit upgrade/retry и поздний импорт проверяют rollback, сохранность
ID/metadata/точек/сегментов/sent. Финальные native сценарии проверяют настоящие
map/menu/notification Stop, Cancel/Back, stale PendingIntent, split,
восстановление с GPS-off/on, цельное слово на полностью извилистой линии,
видимый экстент, скрытие/возврат слоя, категории и picking. Проверки GPU
подтверждают направление цельного текста; три PNG сохранены runner на Q.

Предшествующий расширенный emulator batch дал 20 отдельных PASS, но **batch
целиком был FAIL** из-за возврата подписей после hide/show. Этот дефект исправлен
учётом логической видимости слоя и проверен финальным успешным прогоном выше.
Другие пройденные случаи того batch включали реальную SQLite v1 → v2 миграцию,
тонкую линию, приоритет точек и существующие операции с общей историей треков.

На Samsung SM-A566B, Android 16 / API 36, пройдено 9 отдельных native случаев:
редактор «Светлячка» (72 профиля), SQLite upgrade, четыре сценария управления
треками, категории всех геометрий и две проверки новых подписей. **Это частичная
проверка, а не PASS всего phone batch**: hide/show и координаты тестового нажатия
Stop потребовали исправлений; до финального повтора устройство отключилось
от ADB. Финальная проверка этих путей выполнена на эмуляторе.

В ходе отладки исправлен тестовый ввод через матрицу FittedPanelLayout;
сохранён штатный UID guard `Instrumentation.sendPointerSync`. Экспериментальный
прямой location FGS с выключенным GPS нарушал требования Android и заменён
проверкой публичного preflight плюс обычным запуском защитной ветки сервиса
из foreground Activity. Его ранние сбои не выдаются за причину двух Stop в логе.

Все mutating native проверки использовали только
`com.nextgis.mobile.verification.debug` и технические fixtures. Рабочую ЛИСУ не
переустанавливали и не очищали. Системные настройки/owned emulator восстановлены
и остановлены runner. Full/Check-Publication, Android 9/11 и замеры FPS/cold start
не выполнялись; Related не заменяет эти проверки перед публикацией.

PR: [app #78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78),
[maplib #60](https://github.com/GeonicalSys/android_maplib/pull/60),
[maplibui #44](https://github.com/GeonicalSys/android_maplibui/pull/44).
Все Draft. Обновлённый подписанный APK собирается локально по ранее данному
разовому исключению без merge и публикации; предыдущий кандидат сохранён отдельно.
