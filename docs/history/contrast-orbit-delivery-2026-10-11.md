---
title: Закрытие цепочки «Контрастной орбиты» для Lisa Release
type: history
last_verified: 2026-10-11
related_code:
  - maplib
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/BackgroundLocationWarningTest.java
  - tools/verify-apk-version-matrix.ps1
---

# Закрытие цепочки «Контрастной орбиты» для Lisa Release

Запрос: локальный Lisa Release APK с контрастным кругом/треугольником и
видимым сектором компаса. Версия остаётся 3.1.2.27 / 221; publisher и
установка на рабочий телефон в этот цикл не входят.

## Инвентаризация после fetch

Проверены все пять owner repositories: app, maplib, maplibui, easypicker и
upload_mobile. При первичной проверке открыты только собственные maplib #48 и app #57. Оба основаны
на default branches; дочерних PR с base `codex/contrast-orbit` нет.
Опубликованные `origin/codex/*` существуют только для этих двух PR.
Иных необходимых незакрытых исправлений и согласованно отложенных PR нет.

| Требование | Owner / PR / base | Commit | Статус и присутствие |
|---|---|---|---|
| Предыдущая «Орбита», направление и исправление завершения drag | maplib #47 / master | `fa11fc72226b776ed528e207772ac26575180577` | Merged; ancestor нового library head |
| Consumer предыдущей библиотеки и надёжная изоляция TrackUpload CI | app #56 / my-maplibre | `2e9f2b9bbc1ce5773248f6ff6d366de246c9e9eb` | Squashed; прямой ancestor текущего app head, pin #47 сохранён |
| Контрастный маркер и сектор с двумя контурами | maplib #48 / master | Merge `f2c676a6ea0e35dfb97f539ee3b5a9b653b273ab`; feature `f5488318972d7a0f1a5542f75a689d369b6fd2ae` | Merged; feature ancestor remote master, diff деревьев пуст |
| Graphics smoke, docs и consumer контрастной библиотеки | app #57 / my-maplibre | первоначальный head `de49ad870070c8a15d86cfd6f8d71d0b58149e4b`, fixture `e3f1193` | Open; fetched gitlink #48 закреплён; ожидаются итоговый CI и Squash |
| Синхронизация/уведомления предыдущего выпуска | maplibui #35 / master | `795f2c681fbed53e0ef496775a10a05e1a6a7124` | Merged; root pin совпадает с remote master |
| Easypicker | easypicker / master | `d6327f3de7a6d488a18d1896f8c97c60cd28d2a8` | Root pin совпадает с remote master; открытых PR нет |
| Publisher | upload_mobile / main | `ff3ab8d1404ce5ec52b9b61f19b9297d466f52e5` | Без изменений; открытых PR нет |

Порядок закрытия: Merge Commit #48 → fetch и pin именно remote merge-коммита
в #57 → зелёные проверки итогового app head → Squash #57 → fetch и сравнение
remote default tip с содержимым PR → APK version matrix → проверка подписи и
встроенного sourceRevision → локальный APK.

## Проверки перед слиянием

- 524 maplib unit tests: без ошибок; Lisa/Belka Release source sets компилируются.
- LocationOrbitTest: OK (1 test) на отдельном API 36 эмуляторе с host EGL.
  Проверены реальные пиксели, anchor, пять overlay layers, сектор 56 logical px
  и compass/course fallback. Физический компас пока не проверен.
- CI app head `de49ad8`, run `38136505745`: unit/compile проходят; reliability
  падает на BackgroundLocationWarningTest.java:144 при возврате из CREATED
  в RESUMED после RUN_ANY_IN_BACKGROUND=ignore. Сбой воспроизведён локально:
  OK не получен, 1 failure, та же строка и состояние STOPPED.
- Fixture заменяет внутренний resume broadcast на системную кнопку Back из
  foreground helper Activity, затем по-прежнему ждёт RESUMED и проверяет
  реальные предупреждения, маршруты настроек и отсутствие запуска записи.
  Production logic и assertions не ослаблены.
- Исправленный BackgroundLocationWarningTest: OK (1 test), 9,516 с, на том же
  изолированном API 36 эмуляторе. Docs validator и 7 unit checks документации
  проходят; после добавления этой матрицы validator повторно пройден.

Maplib #48 слит 2026-10-11 14:24 UTC; remote merge fetched, дерево равно
протестированному feature tip. Root закрепляет этот SHA в #57. App merge и
release-сборка ещё не выполнены. Матрица обновляется результатами проверок
до выдачи APK.
