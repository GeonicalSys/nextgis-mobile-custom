---
title: Состав debug выпуска 3.1.2.23
type: reference
last_verified: 2026-10-06
related_code:
  - app/build.gradle
  - maplib
  - maplibui
  - tools/verify-apk-version-matrix.ps1
---

# Debug 3.1.2.23 / 218

Публикация разрешена пользователем только для debug. Production metadata
Lisa/Belka остаются 3.1.2.27 / 221; обязательная APK matrix собирает их только
для проверки отсутствия случайного изменения версии.

## Инвентаризация после fetch

На 6 октября в app/maplib/maplibui открыты только PR46/38/26 этой задачи,
автор geoglyth. В easypicker и upload_mobile открытых PR нет. Единственные
origin/codex/* refs в пяти owners — эта задача в app/maplib/maplibui;
дополнительных stacked/parallel изменений нет. Ничего не отложено.

| Требование | Owner / исходный commit | PR / base | Проверка включения | Состояние до merge |
|---|---|---|---|---|
| Предыдущий опубликованный Debug и накопленные исправления | app b9e4fd6 → 5686d8c | app45 / my-maplibre | b9e4fd6 ancestor origin/my-maplibre; app45 mergeCommit = 5686d8c | Merged |
| Предыдущий library baseline | maplib 1d81e8a; maplibui 675c16b1; easypicker f91abdf | maplib37; maplibui25 / master | gitlinks 5686d8c совпадают; library baselines ancestor task heads | Merged |
| Bluetooth RSSI, track-only boundary | maplib 232d69f, включает 3038de1 | maplib38 / master | ancestor новой version commit; затем Merge Commit | Open |
| Режимы трека, 30 км/ч, сегменты и значок | maplibui fab7933a | maplibui26 / master | Merge Commit с тем же tree | Open |
| Меню, RSSI UI, тесты, central docs | app 8c96d05, включает 07cb118 | app46 / my-maplibre | ancestor version/repin commit; squash проверяется по tree | Open |
| Debug 3.1.2.23 / 218 | app и maplib version commits этой задачи | app46 / maplib38 | APK metadata + variant maplib BuildConfig через version matrix | Pending |

Порядок: bump maplib → Merge Commit maplib38 и maplibui26 → fetch и точный
repin app → CI → Squash app46 → fetch и сравнение tree/проверка gitlinks →
APK version matrix → publisher dry-run → публикация только debug → проверка
публичных manifest/versioned APK/latest/history. Незамерженные library heads
не разрешают выпуск. Итоговые SHA и результаты сохраняются в описании app46.

## Проверки до выпуска

Library PR38 и PR26 объединены Merge Commit по явному разрешению пользователя.
После fetch получены maplib `1b1f4e87edd037185ca94a06e1340d0be24d5c1d` и
maplibui `159f0bcf8bae3e40db12ad07da5ed7b2fc07c2d9`. Проверены ancestry
`bb33760`/`fab7933a` и полное равенство trees с соответствующими task heads.
Приложение закрепляет именно эти удалённые merge commits; версия maplib debug
включена в `bb33760`. Далее остаются app46 CI/Squash, remote-tree проверка и
APK matrix; итоговые результаты доступны в app46.

Feature commit 8c96d05 прошёл CI: 583 unit и 34 native API36 tests. Версия
требует повторного CI и фактической APK matrix после закрытия зависимостей.
Физический PiGo/Classic, маршрут со screen-off, планшет/landscape, GPX и
установка через updater на реальном Android 9/10 остаются непроверенными.
