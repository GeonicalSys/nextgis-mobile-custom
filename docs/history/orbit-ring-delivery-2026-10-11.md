---
title: Доставка «Орбиты-кольца» и проверка точки под курсором
type: history
last_verified: 2026-10-11
related_code:
  - maplib
  - app/src/androidTest/java/com/nextgis/mobile/rendering/LocationOrbitTest.java
---

# Доставка «Орбиты-кольца» и проверка точки под курсором

Запрос: реализовать согласованный курсор с прозрачным окном, синей точкой
диаметром 6 dp и синим ободом толще на 1 dp наружу за счёт белого. Треугольник,
сектор точности компаса, приоритет компаса и резервный курс движения сохраняются.
Этот цикл завершается кодом и связанными Draft PR; новых разрешений на merge
или release APK в нём нет.

## Инвентаризация и матрица

После fetch проверены app, maplib, maplibui, easypicker и upload_mobile.
В начале работы открытых PR в этих owners не было. Текущие собственные Draft:
[maplib #49](https://github.com/GeonicalSys/android_maplib/pull/49) и
[app #58](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/58),
matching branch `codex/orbit-ring-marker` от соответствующих default branches.
Других требуемых открытых исправлений или согласованно отложенных PR нет.
Maplibui, easypicker и publisher не меняются.

| Требование | Owner / PR / base | Commit | Состояние и присутствие |
|---|---|---|---|
| Предыдущая «Орбита» с compass/course | maplib #47 / master | `fa11fc72226b776ed528e207772ac26575180577` | Merged; ancestor библиотечной базы и #49 |
| Контраст и сектор 56 logical px | maplib #48 / master | `f2c676a6ea0e35dfb97f539ee3b5a9b653b273ab` | Merged; ancestor #49; текущий committed consumer pin |
| Предыдущий consumer и исправления приложения | app #56 / my-maplibre | `2e9f2b9bbc1ce5773248f6ff6d366de246c9e9eb` | Squashed; ancestor базы и #58 |
| Consumer контрастной библиотеки | app #57 / my-maplibre | `376d3c8c45d5fb7f0ed6f1c78e351616c011b42b` | Squashed; API подтверждает MERGED; ancestor #58 |
| Кольцо, синий центр и прозрачные служебные заливки | maplib #49 / master | `1d62493b52af687ec6debae53f16f27a4c6bd42b` | Open Draft; включено в local/published library tip, ещё отсутствует в committed app gitlink |
| Проверка точки под курсором и контракт приложения | app #58 / my-maplibre | `4dd534be702f112c6abab1a89a4da6e9b6e1e785` | Open Draft; native smoke/docs включены в app tip; pin ожидает remote merge #49 |

Присутствие прежних merge/squash-коммитов подтверждено через
`git merge-base --is-ancestor`, а не по именам веток. `HEAD:maplib` сохраняет
`f2c676a`; local maplib checkout — `1d62493`. Поэтому root status показывает
изменённый gitlink, который намеренно не включён в app commit до merge библиотеки.
Это состояние для локальной проверки, а не готовая интеграция или release.

## Проверки

- `:maplib:testDebugUnitTest`: 526 тестов, 0 failures/errors.
- `:app:assembleLisaDebug` и `:app:assembleLisaDebugAndroidTest`: успешны
  с local maplib `1d62493`; consumers компилируются.
- `LocationOrbitTest`: OK (1 test), 10.018 с, отдельный read-only API 36
  emulator-5580, host EGL. Предварительно выданы GPS, account и notification
  permissions пустому Debug package; эмулятор остановлен после проверки.
- Проверены bitmap alpha в окне, синий центр 6 dp, утолщённый обод, настоящий
  красный символ 14 dp ниже всех service layers без подкрашивания GPS/компасом,
  anchor, zoom 16/18/19, hot/lite reload и compass/course/plain transitions.
- Docs validator и 7 документационных unit checks проходят.
- Lisa/Belka Release с новой библиотекой не выполнялись до закрытия merge/pin
  цепочки. Физический компас, реальные тематические/спутниковые подложки и
  ручной smoke на телефоне пока не проверены. Headless reliability CI не
  запускает графический smoke и не подтверждает визуальную проверку.

## Оставшийся порядок интеграции

1. Проверить итоговый CI и слить maplib #49 через Merge Commit после разрешения
   пользователя, включая обычную проверку дочерних PR.
2. Fetch remote library target и закрепить полученный **merge SHA** в app #58;
   не использовать feature/branch commit вместо слитой ревизии.
3. Проверить итоговый consumer, Lisa/Belka Release source sets и CI после pin.
4. После разрешения пользователя выполнить Squash app #58, fetch и проверить
   включение всех строк матрицы в remote target и итоговый library pin.
5. Только после закрытия зависимостей и запроса на APK собирать release artifact.
