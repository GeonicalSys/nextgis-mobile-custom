---
title: Проверки и доставка «Светлячка» 24 октября 2026
type: reference
last_verified: 2026-10-24
related_code:
  - maplib/src/main/java/com/nextgis/maplib/display/FireflyMarkerRegistry.java
  - maplib/src/main/java/com/nextgis/maplib/map/mpl/FireflyLayerFactory.java
  - maplibui/src/main/java/com/nextgis/maplibui/fragment/StyleFragment.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/VectorStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyTileFallbackTest.java
  - tools/mobile-test-plan.json
---

# Проверки «Светлячка»

[Контракт и настройки](../architecture/point-firefly.md). Выпуск и публикация
не выполнялись. Рабочее приложение смартфона не переустанавливалось; использован
отдельный `com.nextgis.mobile.verification.debug` 3.1.2.23/218 (проверено aapt2).

## Проверенный состав

| Изменение | Репозиторий и точный source commit | PR / база | Включение |
|---|---|---|---|
| Ядро, внешнее свечение, независимые alpha, native ownership и fallback | maplib `2be9772dd73e6ee6b1e74270b66ba02522f09ea5` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60), master | Gitlink root source commit совпадает |
| Отдельный тип в редакторе, образец и preview | maplibui `9bb084fb704f878dbbf8d1e7e5254ed725cf116c` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44), master | Gitlink root source commit совпадает |
| Интеграция, native tests и маршрутизация | приложение `066573a2f503de89af85f41f1a6ea2c2d45f09be` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78), my-maplibre | Этот source commit содержит оба gitlink |
| Проверка документации по всему PR, включая предыдущие commits | приложение `32c466919745c34287be6fa4508bd3b18495ae18` и его предшественник | #78, my-maplibre | Ancestry до source commit проверена |

Все три PR остаются Draft. Следующий порядок: #60 Merge Commit → #44 Merge
Commit → обновление обоих root pins на удалённые merge commits → #78 Squash.
Текущие pins указывают на опубликованные task commits открытых PR, поэтому это
проверяемая интеграция для review, а не завершённый выпуск. Сборка release APK
и публикация ждут отдельного запроса и закрытия зависимостей.

## Фактические проверки

| Проверка | Результат |
|---|---|
| `:maplib:testDebugUnitTest` | 593 tests, 0 failures/errors/skips; включает 4 Firefly bitmap/JSON/limit случая |
| `:maplibui:testDebugUnitTest` | 93 tests, 0 failures/errors/skips |
| `:maplibui:assembleDebug` | PASS на окончательном исходнике библиотеки |
| Компиляция Lisa/Belka Release Kotlin и Java | PASS; сборка APK Белки не выполнялась |
| Verification Lisa Debug и AndroidTest APK | PASS с fieldVerificationChecks/ciReliabilityChecks; только изолированный application id |
| `Related -Area styles -PhoneSerial <serial> -PhoneOnly -SkipBuild` | **18/18 native PASS**, 0 skip, после явной сборки окончательных APK |
| Редактор | 72 конфигурации × Save/Cancel = **144 прохода** настоящего диалога с touches; preview pixels, полные подписи, отсутствие ellipsis и границы кнопок |
| Native bitmap/renderer | Point/MultiPoint, blur 0–3, прозрачное ядро, независимые alpha, layer opacity, zoom, неизменные pixels старого circle blur, категории, picking/visibility/icon overlay и image cleanup |
| Отдельный live tile fallback | Native VectorSource точки → GeoJsonSource с видимым Firefly через публичный style reload, без перезапуска приложения |
| Документация и реестр карточек | PASS; каждый owner содержит новую карточку с точными covers |

Смартфон SM-A566B, Android 16 / API 36, firmware A566BXXSECZI2.
MapLibre Android **13.0.2 OpenGL**, Gradle **9.3.1**, JBR **21.0.9+-14787801-b1163.94**;
JVM Android-тесты используют существующий Robolectric 4.16.1 и его установленные
SDK inputs. UI проверен по-русски: обе темы, portrait/landscape, fontScale
1/1,3/2, normal/320/280 dp; конфигурации принадлежат тестовому окну, системные
font/display настройки смартфона сохраняются.

Окончательный source fingerprint:
`9179a00eb426b0ad41dcf2fdbf26cfedc2a0e9025ea804f5cd14ed8afc6c7392`.
Он повторно совпал с рабочими исходниками после тестов; документальные изменения
и commits не меняют проверенный runtime. Каталог теперь содержит 45
classes / 227 methods; Related не выдаётся за весь каталог.

APK SHA-256:

- Verification application: `f7a069d5075d61ef932c8c2c2f6a91801da63a715970ad88d5d1f8c5d50cd3bc`.
- AndroidTest: `0f6b83af7a100a0e3963c55faa19b21fe2daf804586f1d954a044f46006266c2`.

Native evidence и точные исходы: локальный
`Q:/Codex/Scratch/point-firefly-20261023/evidence/phone-related-final/result.json`;
10 PNG pixels сохранены отдельно до штатного reset следующего test case.
Предварительная ошибка window token была в тестовом configured Context и
исправлена сохранением Activity token. Повтор после исправления прошёл.
Один старт повтора застал краткое исчезновение USB/ADB; повтор начат только
после подтверждённого возвращения устройства. Эти логи сохранены рядом.

## Ограничения и хранение

Full не запускался: он обязателен отдельно перед публикацией после слияния и
окончательной версии. Эмулятор в этой сессии не проверен: восстановление Auto
в загрузчике ожидает отложенной пользователем перезагрузки. Native GUI результаты
этой квитанции относятся к смартфону и synthetic offline слоям verification APK.

Новых SDK, эмуляторных образов, runtime copies и тестовых серверных сред не
создавалось. Переиспользованы установленные SDK/JBR и Gradle cache на Q.
Task-owned disposable каталог пуст; удалять временные среды в этой задаче нечего.
Компактные логи и снимки (около 0,8 MiB) сохранены в указанном Scratch для review,
их можно удалить после слияния по storage-manifest.json. Готовые APK/AAR остаются
в переиспользованных workspace build outputs для следующей проверки; это не
отдельные task copies. Доставленные ранее release APK и общие caches сохранены.
Verification процесс после тестов остановлен; fixtures удаляются finally каждого
сценария. Эта задача не меняла старые pending-cleanup записи других задач.
