---
title: Автоматические проверки на смартфоне и эмуляторе
type: runbook
last_verified: 2026-10-15
related_code:
  - tools/verify-mobile-tests.ps1
  - tools/run_mobile_tests.py
  - tools/mobile_test_support.py
  - tools/mobile-test-plan.json
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestApplication.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationEnvironment.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LayerSelectionDialogTest.java
---

# Автоматические проверки

Один runner разделяет связанные проверки после доработки и обязательную полную
приёмку перед публикацией. Рабочий проект телефона не является тестовой базой.
Опциональный `fieldVerificationChecks=true` создаёт отдельный Debug APK «ЛИСА
автотесты»: `com.nextgis.mobile.verification.debug`, отдельные provider/account
IDs, пустой production DSN и собственные SQLite/preferences/files. Profile не
создаёт Release variants. Обычные Lisa/Belka/Debug и их версии сохраняются.

VerificationTestRunner допускает физический телефон только с явным serial и
`verificationPhone=true`; его Application проверяет BuildConfig и точный package.
Runner устанавливает/очищает только эту фиксированную identity. Между классами
восстанавливается пустая синтетическая база; реальные accounts не импортируются.
В phone suites нет переключений radios, глобального font/display или настоящего
движения. Требующие этих воздействий сценарии идут на эмулятор.

## Треки и обычные стили

Related поддерживает область `styles`; она проверяет synthetic обычные векторы
после сохранения rule JSON, native properties и pixels с bundled fonts/images.
Configured local-vector-tiles исключены по режиму и GeoJsonSource guard.
Графические проверки требуют host OpenGL: headless SwiftShader не отображает
glyph/bitmap symbols. CI поднимает Xvfb/Mesa llvmpipe и сохраняет сведения о
renderer; локальный runner использует `-gpu host`. В styles fixture стартовый
geo-dialog отключён только в verification preferences, camera и скрытый
underlay синхронизированы с моделью карты.
SharedTracksTest проверяет общую историю и сохранённый switch recording guard.
Пример: `tools\verify-mobile-tests.ps1 -Scope Related -Area tracks,styles`.
Контракты: [треки](../architecture/shared-tracks.md),
[матрица стилей](../architecture/classic-style-verification.md).

## После доработки

```powershell
.\tools\verify-mobile-tests.ps1 -Scope Related -Area layers
```

Это собирает verification APK, выбирает/запускает read-only API 36 эмулятор,
выполняет нужные классы и для выбора слоёв проверяет крупный интерфейс.
Области: `layers`, `forms`, `sync`, `location`, `tracks`, `styles`, `map`, `scripts`,
`diagnostics`. Можно передать несколько `-Area`, явный `-EmulatorSerial`,
`-Avd`, `-SdkPath`, `-PythonPath` и `-OutputPath`. Для локального повтора уже
собранного тестового APK существует `-SkipBuild`; он не разрешён для Full.
Не нужно повторять весь Full после каждого небольшого изменения.

## Перед публикацией

Подключить смартфон по USB с разрешённой отладкой, разблокировать и оставить для
UI-тестов. Нужны Python 3, Android SDK/API 36 AVD и JDK сборки; Windows wrapper
использует установленный Python launcher и при необходимости JBR Android Studio.

```powershell
.\tools\verify-mobile-tests.ps1 -Scope Full
.\tools\verify-mobile-tests.ps1 -Scope Check-Publication
```

Если устройств несколько, передать `-PhoneSerial SERIAL -EmulatorSerial SERIAL`.
Полный прогон включает все unit tests app/maplib/maplibui, tests самого runner,
APK version matrix Lisa Debug/Lisa Release/Belka Release и документационный
validator. Затем все обнаруженные native `@Test` маршрутизируются по
`mobile-test-plan.json`: формы/SQLite/подписи/выбор/обычные UI и диагностика
на смартфон; mock GNSS/трек/обход/permissions/radio и graphics — на эмулятор.
Phone и emulator suites используют одинаковую verification identity и разные
устройства; стираются только disposable fixtures.

Diagnostic delivery полностью локальный: offline handled/fatal/restart/reconnect
и HTTP 503 retry, synthetic DSN и loopback receiver через временный adb reverse.
Crash phase считается пройденной только в заранее выбранном сценарии вместе
с последующим подтверждением сохранённого отчёта. Реальные серверы не нужны.
Эмулятор для screenshot graphics использует working EGL/GPU host. GNSS fixes,
speed gates, gap/pause/recovery проверяются синтетическими координатами; RF,
глушение спутников, реальный холодный старт и расход аккумулятора требуют
отдельной полевой проверки.

Выбор слоя проверяется при density 420/540/617, font 1.0/1.3/2.0, обеих темах
и ориентациях: native viewport, полный текст, последняя строка, реальные выбор
и Cancel. Стандартные matrices панелей/настроек/форм проходят соответствующие
native классы. Эмуляторные системные настройки восстанавливаются в finally;
автоматически созданный runner эмулятор завершается без сохранения snapshot.

## Результат и gate

Логи каждого класса и профиля, `progress.json` и `result.json` находятся в
игнорируемом `build/verification/<UTC-run>/`. Только полностью успешный Full
пишет `build/verification/full-passed.json`. В отчёте записаны device SDK/hardware,
source fingerprint, выполненные cases и unit/docs/version/UI acceptance.
Raw production базы/credentials не входят в отчёт или Git.

`Check-Publication` сверяет отчёт с текущим кодом, тестами и build recipe всех
потребляемых модулей. Изменение версии/источников/плана требует нового Full.
Source fingerprint учитывает рабочие правки, не только Git HEAD; doc-only
правки не инвалидируют выполненную проверку кода. Зелёный Related/CI, `adb`
exit code или `OK (0 tests)` не подтверждают Full. Assumption skip не является
выполненным case: все требуемые cases должны пройти хотя бы в одном запуске.
Новый класс без маршрутизации блокируется сразу, новые методы класса входят
в ожидаемый набор автоматически.

Gate дополняет существующий аудит merge/dependency closure. Review pins
незамерженных библиотек по-прежнему блокируют выпуск даже при зелёном Full.
APK publication этим runner не выполняется.

## Разрешённый проект для интеграционной проверки

По разрешению пользователя используется проект 1403 «Для инженеров Геоникл».
Full ищет его в Collector workspaces подключённого телефона, читает только
`project.json`, состав `map.ngm` и политики векторных слоёв. Whitelist snapshot
содержит названия, geometry type и edit/sync flags: без accounts, endpoints,
credentials, features, координат и форм. ProjectLayerSelectionTest создаёт
пустые локальные аналоги и вызывает штатный `MapFragment.addNewGeometry`;
проверяет все разрешённые слои, исключение read-only, многорядный viewport и
реальное касание Cancel. Проект телефона остаётся исходным тестовым источником.

Если 1403 отсутствует или совпал в нескольких workspaces, runner **спрашивает,
на каком проекте можно протестировать**. Интерактивный терминал принимает
ID/название/каталог; non-interactive возвращает вопрос до проектных действий.
Агент должен передать вопрос пользователю и после ответа повторить:
`tools\verify-mobile-tests.ps1 -Scope Full -TestProject <разрешённый-проект>`.
Не подставлять другой проект автоматически. Явный выбор хранится локально в
игнорируемом `build/verification/authorized-test-project.json`. Полный receipt
содержит ID и checksum snapshot. Related на эмуляторе использует синтетические
fixtures и не зависит от доступности телефонного проекта.

Перед каждым классом runner будит тестовый экран и пытается закрыть уже
доступный keyguard; защищённый PIN не обходится. Только verification Activities
удерживают экран включённым до закрытия; системный timeout телефона не меняется.

Сопутствующие source guards и parser/selection tests проверяют отсутствие
неявной замены проекта, неоднозначный remote ID, incomplete instrumentation,
assumption skips, explicit fatal phase и stale full receipt.

ProjectScriptsTest запускает настоящие JNI/Binder правила на смартфоне,
включая быстрые последовательные вызовы. Test Application обязана сохранить
ранний isolated-process guard до prefs/permissions/diagnostic initialization;
script services не получают основную GIS Application initialization.

Классы из isolated_methods в mobile-test-plan.json выполняются по одному методу
в новом процессе с чистой verification базой. MapEditingToolsTest использует
этот режим: служебные prefs/сервисы и состояние камеры предыдущего сценария
не должны определять следующий тест. Все 17 assertions остаются обязательными;
это разделение тестовых fixtures, без пропуска сценариев или ослабления проверок.

ProjectSyncRunnerTest также использует per-method isolation: изменения схемы
и outbox одной фикстуры не наследуются следующим сценарием. Межпроцессная
диагностическая цепочка остаётся исключением и сохраняет данные намеренно.

Emulator-only Android reliability CI запускает связанные map/layers проверки
через этот же runner, вместо наследования map state между методами. Проверка
разрешённого телефонного проекта остаётся обязательной в local Full и не
подменяется CI.

CI reliability invokes Related map/layers/tracks/styles. SharedTracksTest runs
only through its per-method verification profile; the broad legacy CI package
excludes it. The loopback delivery suite now requires nine successful cases,
including the delayed-ACK/replacement-point scenario. Current Full catalog has
194 required native cases; historical 177-case receipts cannot admit this runtime.
