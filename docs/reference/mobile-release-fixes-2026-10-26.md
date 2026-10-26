---
title: Исправления блокеров выпуска ЛИСЫ 26 октября
type: reference
last_verified: 2026-10-26
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/GISApplication.java
  - maplibui/src/main/java/com/nextgis/maplibui/view/WrappingLabel.java
  - app/src/androidTest/java/com/nextgis/mobile/util/MaintenanceRemindersNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationIo.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocalVectorTileLabelTest.java
---

# Исправления перед публикацией

Пользователь поручил разобрать ошибки и довести пакет до публикации ЛИСЫ.
Работа продолжается в app PR78, maplib PR60, maplibui PR44. Desktop и публикация
БЕЛКИ исключены. Финальный порядок: merged libraries → consumer pins → app
merge → свежий Full смартфона+AVD → Full планшета+AVD → Check-Publication →
publisher tests/dry-run → публикация и readback. Сейчас выпуск ещё не принят.

## Установленные причины и изменения

| Ошибка | Причина и изменение | Ограничение |
|---|---|---|
| API30 ZIP/photo/category readers | InputStream.readAllBytes требует API33; тесты используют переносимый буфер, entry streams закрываются | Product Files.readAllBytes API26 не заменяется |
| Screenshot предупреждения | decor.draw до WindowManager attach; show и capture разделены UI traversal barrier | Полная матрица288 и прежние assertions сохранены |
| Слово азимута разрывается | На API30 font200%, viewport280×500: слово753px при доступных752px; hinting делает пропорциональный расчёт неточным | Одно удаление порога0.5px не помогло; применяется поиск по измеренным advances |
| MVT не стартует offline | SDK Offline откладывает HTTP loopback; GISApplication разрешает SDK transport | Реальная Android сеть и WorkManager guards не меняются; внешние URL могут дать transport error/backoff |
| CI TrackUpload badge | Исходный stack Assert.assertTrue указывает на canShowPending после второго upload, не на unexpected true от uploadProject | Причина исходного CI пока не доказана; добавлены request/registration/network witnesses |
| Radio state в TrackUpload fixture | Вложенные fire-and-forget svc команды конфликтуют с владельцем сети @Before/@After | Дублирование удалено; принадлежность исходного CI этой гонке не доказана |

Добавлен один emulator-only offline MVT метод; каталог теперь254. Исходный
Firefly fallback дополнительно проверяет реальную отрисовку MVT до смены
стиля. Старые Full253 — исторические результаты, не новая приёмка.

## Проверки этой стадии

Общий каталог/код не меняется во время каждого запуска; fingerprints и
authoritative result/cleanup сохраняет versioned runner. Корень локальных
свидетельств: `Q:\Codex\Scratch\mobile-full-20261025-f06ccfb\evidence`.

| Запуск | Результат |
|---|---|
| azimuth-api30-baseline-01 | Воспроизведён исходный перенос753/752 с точными параметрами и PNG |
| api30-fixed-01 | Backup9, types1, reminders2 PASS; первый word-fit вариант FAIL |
| offline-azimuth-fixed-02 | Ошибка ADB установки до native;0 методов, owned AVD остановлен |
| emulator-release-fixes-03 | 11/11 PASS: azimuth1, TrackUpload9, offline-MVT1; cleanup PASS |
| api30-fixed-04 | Планшет не подключился по прежнему порту; native не начинался |
| backend-integration-fixed-05 | 19/19 PASS: project-policy1, TrackUpload9, mixed selection/order4, reminders2/updater3; cleanup PASS |
| maplibui units | 93 PASS,0 failures/errors |
| Runner units | 48 PASS |
| Publisher units | 23 PASS; только временные локальные каталоги, production не изменялся |
| Docs | 24 PASS; окончательная документация дополнительно проверяется перед commit |

Native устройство — только отдельный verification APK; production данные и
системные настройки физического устройства не изменяются. Смартфон пользователь
подключит позже. Планшет после короткой проверки потерял Wi-Fi ADB порт;
запрошены разблокировка и текущий IP:порт. Новый API30 word-fit пока не принят.

## Хранение

Используются установленные SDK/JBR/AVD и существующие Q cache/build junctions.
Компактные отчёты сохраняются до приёмки PR. Два прежних disposable каталога
общим размером29282499bytes остаются после executor-policy rejection; повторной
попытки удаления или обхода не было. Их точные пути/причина уже находятся в
общем pending-cleanup.json. Доставляемые APK и shared caches не удаляются.
