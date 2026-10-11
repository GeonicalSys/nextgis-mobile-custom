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
После просмотра Draft PR пользователь запросил merge, повышение production
версии и публикацию обеих release: Lisa/Belka 3.1.2.28 / 222. Debug остаётся
3.1.2.23 / 218 и не публикуется. Ниже — состояние подготовки до app merge;
итоговые APK metadata и публичные manifests проверяются после закрытия цепочки.

## Инвентаризация и матрица

После fetch проверены app, maplib, maplibui, easypicker и upload_mobile.
В начале работы открытых PR в этих owners не было. Maplib #49 слит Merge Commit,
текущий consumer PR —
[app #58](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/58),
matching branch `codex/orbit-ring-marker` от соответствующих default branches.
Других требуемых открытых исправлений или согласованно отложенных PR нет.
Maplibui, easypicker и publisher не меняются.

Публичный baseline Lisa/Belka: 3.1.2.27 / 221, source
`5686d8cafa0373363424d4134eacc54a8ae81e71` (4 октября). App #46–#57
уже входят в baseline ветки #58 по Git ancestry; изменения после последней
публикации включены в WHATS_NEW 3.1.2.28, ни один требуемый predecessor не отложен.
Публичный Debug до выпуска: 3.1.2.23 / 218,
SHA-256 `b7f4d128750be792df6280aea347ad96dec77dc5198b67fc3e1db72249f19229`.

| Требование | Owner / PR / base | Commit | Состояние и присутствие |
|---|---|---|---|
| Предыдущая «Орбита» с compass/course | maplib #47 / master | `fa11fc72226b776ed528e207772ac26575180577` | Merged; ancestor библиотечной базы и #49 |
| Контраст и сектор 56 logical px | maplib #48 / master | `f2c676a6ea0e35dfb97f539ee3b5a9b653b273ab` | Merged; ancestor #49 и нового consumer pin |
| Предыдущий consumer и исправления приложения | app #56 / my-maplibre | `2e9f2b9bbc1ce5773248f6ff6d366de246c9e9eb` | Squashed; ancestor базы и #58 |
| Consumer контрастной библиотеки | app #57 / my-maplibre | `376d3c8c45d5fb7f0ed6f1c78e351616c011b42b` | Squashed; API подтверждает MERGED; ancestor #58 |
| Кольцо, синий центр и прозрачные служебные заливки | maplib #49 / master | feature `1d62493b52af687ec6debae53f16f27a4c6bd42b`; merge `4abe395e01a97bdc91c79c8f54223c39d13e68ee` | Merged; feature ancestor remote master и consumer pin; дерево равно tested release head |
| Сопряжённая production версия библиотеки 3.1.2.28 | maplib #49 / master | `0be3058582817b1be47f45482f28f08dcb5fd048` | Merged; ancestor `4abe395`; debug version не меняется |
| Проверка точки, consumer pin и app production 3.1.2.28 / 222 | app #58 / my-maplibre | native smoke/docs `4dd534be702f112c6abab1a89a4da6e9b6e1e785`; release prep в итоговом head #58 | Pin закрепляет fetched merge `4abe395`; version/checker/docs включены в PR; ожидается итоговый CI и Squash |

Присутствие прежних merge/squash-коммитов подтверждено через
`git merge-base --is-ancestor`, а не по именам веток. После Merge Commit #49
выполнен fetch, remote master и local maplib checkout равны `4abe395`.
Полный diff деревьев между tested release head `0be3058` и remote merge пуст.
Root закрепляет именно merge SHA в #58. Release APK блокируется до Squash app.

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

1. Проверить библиотеку и слить разрешённый maplib #49 через Merge Commit,
   включая обычную проверку дочерних PR; настроенных checks у library PR нет.
2. Fetch remote library target и закрепить полученный **merge SHA** в app #58;
   не использовать feature/branch commit вместо слитой ревизии.
3. Проверить итоговый consumer, Lisa/Belka Release source sets и CI после pin.
4. После зелёного CI выполнить разрешённый Squash app #58, fetch и проверить
   включение всех строк матрицы в remote target и итоговый library pin.
5. После закрытия зависимостей собрать APK version matrix, проверить подпись,
   source revision и flavor обеих release, выполнить publisher dry-run и
   разрешённую публикацию `lisa`/`belka`; Debug manifest/hash должны остаться прежними.
