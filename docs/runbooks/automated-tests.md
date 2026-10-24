---
title: Автоматические проверки на смартфоне и эмуляторе
type: runbook
last_verified: 2026-10-24
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

## Новые стили полигонов

Для полного адресного покрытия стилей: Related -Area styles. Быстрый пакет polygon-styles включает три новых класса с шестью методами. Явные -PhoneSerial и -PhoneOnly используют только отдельный verification APK. Текущую сборку проверять до -SkipBuild; runner сохраняет fingerprint всех владельцев. Full не запускается при обычной сборке и остаётся обязательным перед публикацией. Контракт: [настройки полигонов](../architecture/polygon-style-controls.md).

## Точечные стили и «Светлячок»

`tools\verify-mobile-tests.ps1 -Scope Related -Area styles` выполняет 16 native
rendering cases, сценарий редактора с 72 локальными профилями и отдельный переход
живого точечного tile-слоя к classic fallback (18 native cases). Явные
`-PhoneSerial <serial> -PhoneOnly` разрешают только неподвижные проверки в
verification APK. Тесты не меняют системные font/display настройки смартфона.
Pixel evidence сохраняется в `files/firefly-qa` изолированного приложения;
квитанция runner содержит точные исходы. [Контракт](../architecture/point-firefly.md).
Full по-прежнему выполняется отдельно перед публикацией.

## Проверки копирования и преобразований

`tools\verify-mobile-tests.ps1 -Scope Related -Area feature-editing` проверяет новые команды на эмуляторе. Добавить `-PhoneSerial <serial>` для трёх неподвижных проверок на verification APK смартфона; `-PhoneOnly` выполняет только эту разрешённую часть с обязательным serial. Системные настройки смартфона не меняются. Native исходы/полнота сохраняются по каждому сценарию; Related не заменяет Full. Каталог/маршрутизация находятся в mobile-test-plan.json. [Контракт и покрытие](../architecture/feature-copy-transform.md).


## Ускорение эмулятора в Windows

Если `emulator -accel-check` сообщает об отсутствии Android Emulator hypervisor
driver, проверить и WHPX: это может быть сообщение о fallback после отключения
основного Windows hypervisor. Read-only диагностика:

```powershell
Get-CimInstance Win32_ComputerSystem | Select-Object HypervisorPresent
Get-CimInstance Win32_Processor | Select-Object VirtualizationFirmwareEnabled
Get-CimInstance Win32_OptionalFeature -Filter "Name='HypervisorPlatform'" | Select-Object InstallState
(Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control').SystemStartOptions
```

На проверенном ноутбуке 2026-10-23: BIOS virtualization=true, WHP InstallState=1,
но HypervisorPresent=false и текущие boot options содержат
`HYPERVISORLAUNCHTYPE=OFF`. Журнал Hyper-V-Hypervisor фиксирует успешный запуск
2026-10-09; текущая Windows загружена 2026-10-20 с запретом запуска. Момент и
инициатор изменения boot option не установлены. SDK emulator 35.6.11 установлен
с 2025-07-25; свидетельств его недавнего обновления нет.

Возврат `bcdedit /set hypervisorlaunchtype auto` требует Administrator/UAC и
последующей перезагрузки. Выполнять по явному выбору пользователя, затем повторить
`emulator -accel-check` и изолированный Related. До повторного запуска не считать
ускорение восстановленным. Не заменять это установкой AEHD или отключением
защитных функций вслепую. [Android WHPX](https://developer.android.com/studio/run/emulator-acceleration#vm-windows),
[Microsoft BCDEdit](https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/bcdedit--set).

По явному выбору пользователя 2026-10-23 выполнен возврат Auto через Windows UAC;
BCDEdit readback подтвердил `Off → Auto`. Перезагрузка отложена пользователем.
Текущий загруженный сеанс сохраняет Off/HypervisorPresent=false до перезапуска;
проверка ускорения и emulator Related после перезагрузки остаются открытыми.

## Регрессии GlitchTip 2026-10-23

`bugreports` включает технический `CompassTouchTest`: actual CompassImage touch
rotation при null Vibrator, отдельный verification APK, без GPS/учётных записей
и изменения системных настроек. Класс внесён в phone roster. GPS native tests
сохраняются на эмуляторе: ordinary no-fix не создаёт отчёт даже после долгого
ожидания и восстановления; sustained callback stall сохраняет retry/recording
и snapshot v3 timing. С учётом копирования и преобразований каталог — 43 classes / 223 methods.
Unit policy/OS matching и CLI protocol tests не заменяют native delivery/release gate.
Подключение к сервису и безопасный export: [runbook](glitchtip-triage.md).

Updater transfer regression удерживает ответ до явного DESTROYED ActivityScenario,
проверяет terminal state, затем отпускает байты и сверяет файл/отсутствие .part.
Первый MainActivity может закончить startup route самостоятельно: тест больше
не пытается перевести уничтоженный экземпляр в CREATED или RESUMED. Это проверка
технического transfer helper, а не фактического скачивания production APK через Doze.

VerificationTestRunner начинает native scenarios после реальных отложенных
startup callbacks (+3 секунды), pending миграции подложек и workspace leases.
Отдельный marker тестового Application стоит за callback восстановления журнала,
поэтому recovery не удаляет synthetic project/account pairs, создаваемые тестом.
Общий лимит ожидания — 30 секунд; ошибка не превращается в skip. Startup workers
продолжают выполняться, production Application и рабочие данные не меняются.

ProjectSyncRunnerTest ждёт завершения pending стартовой миграции подложек и
активных workspace leases до подготовки синтетических проектов, с лимитом
30 секунд. Проверка только isBusy недостаточна: миграция запланирована на
три секунды после старта и может ещё не получить lease. Таймаут — ошибка,
а не пропуск проверок; реальные migration/sync/creation функции не подменяются.

Методы LocationOrbitTest запускаются отдельно с очисткой verification workspace,
как остальные независимые графические fixtures. Orbit smoke пересоздаёт Activity,
меняет камеру и оставляет MapDrawable в Application; следующий тест точности не
должен получить прежний renderer/style до готовности нового экрана. Все методы,
проверки пикселей, native circle и исходного Location остаются обязательными.
Ожидание переключения сектора проверяет также radius native source geometry
56 dp (прежний допуск 1,5 dp). При выключении layers исчезают раньше завершения
setGeoJson, поэтому пустая rendered query сама по себе не означает готовность
скрытой геометрии. Утверждения о сохранности geometry и видимости не ослаблены.

Перед каждым телефонным классом runner проверяет deviceLocked текущего Android
пользователя через dumpsys trust. При блокировке он выводит WAIT и до десяти минут
ждёт ручной разблокировки до очистки verification workspace и старта UI-тестов.
PIN не обходится, системные timeout/font/display/radio настройки телефона не
меняются. Неизвестный формат lock state завершает проверку явной ошибкой.
Full сначала выполняет независимые emulator suites, diagnostics и UI matrix,
затем телефонные классы. Поэтому заблокированный телефон не останавливает
эмуляторную часть; публикационный receipt по-прежнему требует оба устройства.

Linux CI компилирует Kotlin внутри build daemon и завершает его перед запуском
эмулятора. Host OpenGL/Mesa сохраняется для glyph/image assertions, Vulkan
эмулятора отключён через -feature -Vulkan по
[официальной рекомендации для GLES](https://developer.android.com/studio/run/emulator-troubleshooting).
Это настройка среды тестирования; все native cases и pixel assertions обязательны.
Emulator binary закреплён на 35.6.11 / build 13610412, используемом в local Full.
Незакреплённый 37.2.12 / 16428233 терял host EGL process после native suite
при переходе к enlarged-UI profile. Обновлять pin только с полным graphics/UI
прогоном; параметр emulator-build описан в
[android-emulator-runner](https://github.com/ReactiveCircus/android-emulator-runner#configurations).

BackgroundGpsNativeTest проверяет Home без остановки GPS, двойной Back,
настоящее удаление задачи из recents, disarm до повторного открытия и сохранность
отдельного recording listener, реальных TrackerService и WalkEditService.
Возврат после Home использует AppTask.moveToFront для настоящего фокуса задачи.
Новые методы этого существующего emulator-класса автоматически входят в
Related gps-recovery/location и Full; их пропуск не допускает публикацию.

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
Дополнительно проверяются `layers.db` из настоящего DatabaseHelper, сохранность
новых треков при импорте старой истории и recovery owner/URI/pending Stop при
обновлении и импорт из нескольких неактивных проектов с одинаковыми ID. Full каталог содержит 198 cases; новые методы входят через существующий
isolated SharedTracksTest в Related tracks/map и emulator Full автоматически.
Пример: `tools\verify-mobile-tests.ps1 -Scope Related -Area tracks,styles`.
Контракты: [треки](../architecture/shared-tracks.md),
[матрица стилей](../architecture/classic-style-verification.md).

## После доработки

Related `-Area updater` запускает `AppUpdateNativeTest` на изолированном эмуляторе:
сохранение проверенного файла при остановленном Activity, сетевую политику и
настоящий диалог «Обновление готово» / «Обновить» / «Позже» при крупном интерфейсе.
Unit `AppUpdate*` дополнительно проверяют Range, обрывы/роуминг, размер/hash,
сертификат и offline ready state. Эти технические проверки не заменяют
`SMOKE-SELF-UPDATE` с опубликованным APK и системным установщиком реального телефона.

Отдельный `LocalVectorTileLabelTest` в Related `map` проверяет локальные MVT:
одна подпись исходного квартала на границе тайлов, zoom/style reapply, реальные
пиксели текста/заливки/контура и один VectorSource с прежними тремя style layers.
Это не часть матрицы classic стилей. Общий native каталог содержит 198 cases.

```powershell
.\tools\verify-mobile-tests.ps1 -Scope Related -Area layers
```

Это собирает verification APK, выбирает/запускает read-only API 36 эмулятор,
выполняет нужные классы и для выбора слоёв проверяет крупный интерфейс.
Область `gps-recovery` проверяет GPS episode snapshots, парное восстановление и bounded listener retry, затем native background lifecycle; новые cases включены в Full.

Области: `cursor` (графический smoke курсора поверх синего трека, переключение сектора компаса после resume/hot/lite reload и настоящий чекбокс местоположения в обеих темах), `accuracy` (порог встроенного GPS в панели/круге и крупный интерфейс), `help` (ссылки помощи и крупный интерфейс), `layers`, `forms`, `sync`, `location`, `tracks`, `styles`, `map`, `scripts`,
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

## Регрессии из GlitchTip — 2026-10-17

`tools/verify-mobile-tests.ps1 -Scope Related -Area bugreports` запускает isolated
SyncRecoveryJournalNativeTest, MapRuntimeDiagnosticsNativeTest и DiagnosticsNoiseTest.
Проверяются interrupted disk enqueue/ACK, blocked startup/query, реальная local
queue_full, envelope HTTP details и cold-start backlog. Runner выполняет seed и
restart отдельно, а остальные HTTP cases — с чистым cache. Область включена в
штатный GitHub related runner; дублирующиеся cases map/bugreports не повторяются.
Full catalogue содержит 207 native cases; прежние Full receipts это новое
содержимое не подтверждают. APK пока не опубликован.

Native runner после каждого rendering case сохраняет bounded PNG из фиксированных QA-папок verification-приложения: до 4 MiB на файл и 16 MiB на case, только безопасные PNG-имена, SHA-256 index. Рабочие файлы приложения не читаются. Отдельный case живого образца проверяет реальные pixels точек/линий/полигонов и изменение alpha настоящим жестом до Save.

Адресная область style-categories проверяет настоящую строку категории, наследованные настройки, Cancel/Save и отдельную кнопку удаления. NativeStyleTouch перед targeted инъекцией ждёт фокус окна и стабильную видимую область в физических координатах. Касание высокой кнопки допускается только внутри пересечения с viewport прокрутки и экраном; для SeekBar дополнительно видима настоящая точка минимального значения. Регрессия создаёт частично обрезанную кнопку и проверяет доставку реального касания. UID guard Android сохраняется.

Адресная область `style-editors` запускает FireflyStyleEditorTest и PolygonStyleEditorTest: обе матрицы и два отдельных случая живого образца/реальной категории, 4 native метода. Она предназначена для изменения тестового ввода и не заменяет остальные rendering-сценарии `styles` или Full перед публикацией.
