---
title: Full на смартфоне и планшете — 25 октября 2026
type: reference
last_verified: 2026-10-26
related_code:
  - tools/run_mobile_tests.py
  - tools/mobile-test-plan.json
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/androidTest/java/com/nextgis/mobile/reliability/MapEditingToolsTest.java
---

# Область и состояние

Пользователь запросил полный набор тестов перед будущей публикацией, затем
отдельный полный прогон на планшете. Публикация и merge этим запросом не
разрешены. Рабочую ЛИСУ не переустанавливают и не очищают; native suites
используют `com.nextgis.mobile.verification.debug` и технические fixtures.
Смартфон — Samsung SM-A566B, Android 16/API 36, serial R5GL130K54D.
Проект 1403 «Для инженеров геоникл» найден однозначно; с телефона читаются
только политики 18 слоёв, без features/accounts/credentials.

## Первый Full: FAIL, не приёмка

На source `af61f6da85dfb1d5367a2bef15deb004376762f3` (код эквивалентен APK f06ccfb)
прошли 887 unit (614 + 93 + 180), runner tests, полная APK version matrix,
docs validator/tests и 42 native сценария на изолированном API 36 emulator.
Full остановился на `rulerAcceptsFingerDriftAndKeepsPanCancelAndLongPressSeparate`:
отложенный single-tap вызвал SDK queryPointAnnotations после уничтожения MapView.
Ошибка строгой проверки SDK выявила неправильный lifetime; strict mode не
отключался. Телефонные native классы этого прогона ещё не выполнялись.

Evidence: `Q:\Codex\Scratch\mobile-full-20261025-f06ccfb\evidence\phone-full-01`.
Исходный fingerprint: `8e2104ae6214c16a84146ff65609d9f1a48513b313bba38ae168fe99460553e1`.
Прежнего успешного Full gate этот неуспешный запуск не оставляет.
Owned emulator остановлен runner после восстановления настроек.

## Исправление и адресная проверка

MapFragment перед освобождением native renderer снимает edit touch listener
и передаёт ACTION_CANCEL прямо SDK. Это отменяет ожидающее подтверждение
тапа, которое очистка app listeners не снимала. Новый native метод
`pendingNativeTapIsCancelledBeforeViewDestruction` повторяет тап/recreate три
раза и ждёт окна отложенного callback при включённом strict mode.

Related `map-teardown`: **2/2 PASS**, включая исходный сценарий линейки.
Evidence: `...\evidence\map-teardown-01`. В Full теперь 249 требуемых native
методов; все они остаются обязательными. Адресный успех не заменяет Full.
Повтор Full на смартфоне и отдельный Full на планшете остаются необходимыми;
конечные исходы фиксируются после их завершения.

## Дополнение UX-05 перед повторным Full

25 октября пользователь добавил недельные напоминания об устаревшем полном
sync онлайн-проекта и проверке/установке обновления; локальные проекты
исключены из sync. Новый код и native класс включаются в окончательный
Full (53 класса / 251 required methods). Первая Related maintenance проверка
прошла updater 3 и durable clocks 1; UI остановилась на незавершённом
intro route тестового MainActivity. Fixture теперь явно отмечает intro
в verification preferences до запуска, не меняя production либо assertions.
Итоговые Related и Full ещё не заявлены PASS.

Related maintenance-02: часы и updater прошли; UI regression обнаружила
выход естественных bounds кнопки за границы при общем FittedPanel масштабировании.
По уточнению пользователя длинное сообщение переведено на штатную bounded
прокрутку AlertController без уменьшения шрифта с закреплёнными действиями.
Матрица теперь также проверяет отсутствие scale и достижение конца сообщения;
четыре худших профиля сохраняются в bounded PNG evidence verification пакета.

Визуальная проверка PNG maintenance-04 выявила обрезанную «Позже» при
280 dp / 200%: проверка до этого учитывала только положительную кнопку.
Private ReminderPanels теперь резервирует полную высоту кнопок, текст
прокручивается, native assertions охватывают оба действия. Maintenance-05
прошёл 5/5, оба просмотренных portrait/landscape PNG имеют доступные действия.
Финальный адресный повтор проверяет также первый абзац предупреждения;
после него исходники фиксируются для полного Full. Принудительное включение
scrollbar отклонено native проверкой API 36 (framework null drawable при draw);
сохранена штатная прокрутка без такого вмешательства.

Финальный Related maintenance-07: **5/5 native PASS**, включая updater 3
и новые clocks/UI 2. Матрица 288 окон сохраняет крупный шрифт, прокрутку до
конца и обе кнопки внутри viewport; просмотрены итоговые PNG 280 dp portrait
и landscape в светлой/тёмной теме. Scoped unit: 33 PASS (24 API28/36 policy
и 9 ready-state), docs validator + 24 tests PASS. Каталог проверен: 53 класса,
251 required native methods. Эти адресные результаты не заменяют Full.
Evidence: `...\evidence\maintenance-native-07`, `maintenance-unit-summary.json`.

## Матрица включения второго Full

После fetch все Android owner деревья чистые, без ahead/behind своих удалённых
веток. Android source tip `45bd6c86f36e8227df7906e1fb49a399fdf59d57`.
Открыты только требуемые собственные Draft PR #78/#60/#44; опубликованные
unmerged codex heads совпадают с текущими tips и включены по ancestry.
Easypicker и publisher не имеют открытых PR/неучтённых codex heads.

| Изменение / владелец | Commit / PR / base | Включение / состояние |
|---|---|---|
| Стили, MapLibre 13.7.1 OpenGL, треки/подписи / app | f06ccfb, #78, my-maplibre | Ancestor текущего app tip; PR открыт |
| GIS стили/отрисовка / maplib | 3c8020a0f9b550db36baee8b7234fea6ee6325e6, #60, master | Root gitlink и checked-out SHA совпадают с PR head; открыт |
| Редакторы и UI / maplibui | d4b8f27337f0549f25353f9b28e7810371f00ae5, #44, master | Root gitlink и checked-out SHA совпадают с PR head; открыт |
| Pending native tap teardown / app | 1756ad5, #78, my-maplibre | Ancestor 45bd6c8 |
| UX-05 напоминания и scroll / app | 45bd6c8, #78, my-maplibre | Текущий source tip |
| Easypicker | 55aad0e0f92329ea4c273a83e541d170d24d7b57, master | Ранее merged dependency; pin совпадает |
| Publisher | 99e3cfde7841b749b83be6005842b96e7d140d00, main | Не изменён и не запускался |

Успешные предыдущие updater изменения b07c512 и 7e794a4 также проверены
как ancestors текущего app tip. Исключение пользователя разрешает локальный
signed Release APK из этих открытых PR без merge/публикации. Оно не закрывает
цепочку выпуска: #60 Merge Commit → #44 Merge Commit → fetched remote merged
pins в app → #78 Squash. Эта цепочка и будущая публикация остаются отложенными.
Десктоп, серверные метаданные трека (пункт 5), переделка загрузки/тайлов отложены
по прежним решениям пользователя и не включались в этот пакет.

Второй Full запущен со всеми 251 обязательными native методами на смартфоне
R5GL130K54D + изолированном API36 AVD. Промежуточно пройдены 912 units
(614 maplib +93 maplibui +205 app), runner units, нормальная version matrix
Lisa Debug/Lisa Release/Belka Release и docs validator/24 tests. Native ещё
выполняются; промежуточные успехи не означают общий Full PASS.
Evidence: `...\evidence\phone-full-02` и `phone-full-02-delivery-matrix.json`.

## Второй Full: FAIL на native курсоре

Исходники 45bd6c8, fingerprint
`6d01c67eb9493335ea0d37bc8d00cd006f936c804c67df33094fefe292d7a519`.
912 unit, runner tests, version matrix и docs/24 tests прошли. Пройдены
82 distinct native сценария, включая прежнюю линейку и новую регрессию
MapView teardown, GPS/recording/track upload. Таймаут 30 секунд на первом
awaitMarker в `LocationOrbitTest#orbitUsesCompassThenCourseWithACenteredAnchorAndConfigurableSector`.
Телефонные классы, оставшиеся emulator styles/sync, diagnostics и chooser
matrix ещё не достигнуты. Общего Full PASS нет; gate удалён. Owned emulator
остановлен в finally; ADB readback показывает только смартфон.

Проверяется гонка graphics fixture с завершающим full-style callback,
который штатно вызывает updateLastLocation. Это пока гипотеза по коду,
не установленная причина: нужны фактические source/cache/camera diagnostics.
Baseline Related cursor выполняется до исправления. Смартфон/планшет Full
после исправления остаются обязательными.

Baseline cursor-01: 2/2 PASS без изменений, то есть сбой непостоянный.
Graphics fixture теперь дополнительно ждёт завершения app layer apply/pending
reload до технического fix. Это закрывает установленный пробел ожидания, но
не доказывает причину прошлого таймаута. Добавлено фактическое состояние
при повторном сбое; production GPS код и assertions не менялись.
Related cursor-readiness-01: оба метода **2/2 PASS** отдельно, как в Full.
Пользователь затем добавил обязательное исправление общего выбора
треков/векторных объектов: новый Full выполняется после него.

## Дополнения общего выбора и полевых линий

Пользователь обнаружил конфликт выбора на пересечениях. Убраны ранний
short-circuit трека в app и подавление track hits точкой в библиотеке.
Обычный tap собирает один список всех разрешённых SQL vector hits и
видимых native track IDs. Сегменты дедуплицируются; layer/feature и track
identity различаются. Один кандидат выбирается напрямую, несколько — через
MapSelectionDialog с переносом строк/прокруткой/Cancel и dismiss по view lifecycle.
Порядок рисования независимо дополнен: ordinary data → tracks → named
field_lines/«Полевые линейные» с labels → actual points → tools → GPS.
Имя целевого слоя подтверждено read-only snapshot проекта 1403.

Related mixed-pick-native-01 / track-candidates: **4/4 native PASS** —
пять кандидатов (два трека, point/line/polygon), каждый выбирается также поверх
прежнего выбора, hidden/lone cases, прежние actions, 72 locale-local UI
profiles длинных строк и Cancel, body/label priority после full/lite/reorder
и GPU pixel точки поверх трека. Maplib **615 unit PASS**, consumer/native
compilation и docs validator PASS. Новых required методов два: итоговый
catalogue 53 класса /253 methods. Все старые методы остаются обязательными.
Evidence: `...\evidence\mixed-pick-native-01`,
`mixed-pick-maplib-unit-summary.json`, `mixed-pick-compile-units-03.log`.

Смартфонный Full с этими дополнениями ещё должен завершиться, затем нужен
отдельный планшетный Full на том же коде. Предыдущие 82 native PASS относятся
к прежнему tip и не заменяют новую полную приёмку.

## Третий Full: FAIL при перелистывании формы на Samsung

App `3b5338ac68c67cbe898723cd84ebfecc3beac8a3`, maplib
`3ad5d49d3fc015555578360ca5dd62446a70235a`, maplibui `d4b8f27`.
Fingerprint `e41e31b772e538e4557aeb9e99129b0ade9e88b3fe5a47070c18e5f3c36bb573`.
913 unit (615 maplib /93 maplibui /205 app), runner 27, version matrix,
docs/24 tests, весь emulator roster, GPS grant/deny, diagnostic delivery
и 36 системных UI-профилей прошли. Оба cursor метода также прошли.

На SM_A566B прошли недельные напоминания, линейные подписи, подтверждение Stop,
legacy цвета, все шесть TrackControlsTest (включая смешанные кандидаты,
каждый выбор и порядок field lines/points), CompassTouchTest. Следующий класс
CascadingFormsTest: 22 PASS /1 FAIL. В
`swipesWorkAcrossSelectorsCheckboxCommentsAndBlankSpace` после свайпа вправо
поверх checkbox ожидается вкладка 0, осталась 1 (строка 605).
Остальные смартфонные классы не достигнуты; это **Full FAIL**.

Причина пока не установлена: добавляется фактическая геометрия target/ancestors,
видимая область и горизонтальная прокрутка. Системные настройки Samsung
(fontScale 1.15, density 420) сохраняются. Emulator завершён, PID runner
отсутствует. Evidence: `...\evidence\phone-full-03` и
`phone-full-03-delivery-matrix.json`. Следующие полные прогоны смартфона
и планшета остаются обязательными.

Четыре nav/gesture метода отдельно на Samsung прошли без рабочего исправления
(`form-navigation-diagnosis-01`, 4/4). Попытка всего класса на новом read-only
AVD (`form-navigation-class-01`) завершилась 7/23 FAIL: во время native ввода
активным оказалось системное окно `Application Error: com.google.android.bluetooth`.
Сохранён filtered dumpsys focus; targeted input отвергнут вне UID verification APK,
проверки accessibility искали другое активное окно. Это внешнее препятствие
данного emulator запуска, не доказательство дефекта приложения и не PASS.
AVD остановлен штатным finally. Рабочий код Bluetooth/формы не менялся.

Весь класс отдельно на том же Samsung прошёл **23/23 PASS** до изменения
ожидания (`form-navigation-class-phone-01`). Причина исходного swipe FAIL
не доказана. Fixture затем усилен: фокус, завершённая раскладка предков,
стабильная видимая область 120 мс до одного жеста (лимит 10 с). Ввод не
переигрывается, направление/повторения и все state/data assertions сохранены.
Изменён только код проверки; production FormScrollView/Tabs не менялись.

Первый вариант ожидания (`form-navigation-ready-01`) прошёл 22/23 на AVD,
но ошибочно переводил local visible rect самого прокрученного ScrollView
как обычного контрола: его собственный scroll offset давал неверную область.
Этот дефект нового test helper исправлен через root-global visible rect
с реальным origin окна. Проверка с заранее прокрученной страницей сохранена.

Related `form-navigation-ready-02`: **23/23 на API36 AVD и 23/23 на
SM_A566B PASS**, без required skips. AVD остановлен, настройки рабочего
телефона и production version/lastUpdateTime совпадают с baseline.
Docs validator и 24 tests PASS. Далее нужен новый Full на окончательных
исходниках, затем отдельный Full на планшете; эти Related результаты
не закрывают полную приёмку.

## Матрица включения четвёртого Full

После свежего fetch все пять owner деревьев чистые, без ahead/behind своих
удалённых веток. App source `2a19481279790458deb398406e644bceeb703bdb`.
Открыты только собственные Draft #78/#60/#44; все неслитые опубликованные
codex heads включены по ancestry. Pins в app и checked-out library HEAD совпадают.

| Требование / владелец | Commit / PR / base | Состояние включения |
|---|---|---|
| Стили, MapLibre 13.7.1 OpenGL, треки/подписи / app | f06ccfb, #78, my-maplibre | Ancestor 2a19481; PR открыт |
| Pending tap teardown / app | 1756ad5, #78, my-maplibre | Ancestor 2a19481 |
| UX-05 недельные напоминания / app | 45bd6c8, #78, my-maplibre | Ancestor 2a19481 |
| Cursor readiness diagnostics / app | 5c3c936, #78, my-maplibre | Ancestor 2a19481 |
| Общий tap и полевая линия под точками / app | 3b5338a, #78, my-maplibre | Ancestor 2a19481 |
| Form gesture readiness diagnostics / app | 2a19481, #78, my-maplibre | Текущий source tip |
| GIS стили, mixed hit и field-line priority / maplib | 3ad5d49d3fc015555578360ca5dd62446a70235a, #60, master | Точный gitlink, прежний 3c8020a ancestor; PR открыт |
| Редакторы и UI / maplibui | d4b8f27337f0549f25353f9b28e7810371f00ae5, #44, master | Точный gitlink; PR открыт |
| Easypicker | 55aad0e0f92329ea4c273a83e541d170d24d7b57, master | Merged dependency, pin совпадает |
| Publisher | 99e3cfde7841b749b83be6005842b96e7d140d00, main | Не менялся, не запускался |

Прежние background-updater commits b07c512/7e794a4 также включены по ancestry.
Evidence: `...\evidence\phone-full-04-delivery-matrix.json`,
`phone-full-04-preflight.log`. Full с полным catalogue 53/253 запущен заново,
результат пока ожидается. Исключение разрешает только локальный signed APK;
цепочка merge #60 → #44 → remote merged pins → #78 и публикация остаются
отложенными. Десктоп, серверные метаданные трека и переделка загрузки/тайлов
по прежним решениям пользователя не включаются.

## Четвёртый Full: FAIL на планировании после возврата сети

Source 2a19481, fingerprint
`f7fa4add7f0facb9c855e6adfdcaa00874d440695c4e61401d5e705c84f71156`.
913 unit, runner 27, version matrix и docs/24 tests PASS. Успешно записаны
53 distinct native cases, включая все MapEditingToolsTest и guidance service.
SyncOfflineAndWalkGpsTest: 4 PASS /1 FAIL —
`scheduledAccountWorkWaitsForNetworkWithoutStartingSync`, строка 159:
после Wi-Fi и 20 секунд ожидания ENQUEUED вместо SUCCEEDED.
Смартфонные классы, оставшиеся emulator cases, diagnostics и UI matrix
не достигнуты; это **Full FAIL**, gate отсутствует. AVD остановлен.

Исходный тест не сохранял runAttemptCount, stop reason, eligible time и
ConnectivityManager capabilities в момент сбоя. Поэтому причина не доказана.
Проверяются переход сети/JobScheduler и штатный WorkManager retry backoff,
который может превышать исходные 20 секунд. Source/runtime sync не меняется
до получения достаточных свидетельств. Evidence: `...\evidence\phone-full-04`.

Перед следующим Full runner дополнен сбором всех независимых native failures:
failed attempt остаётся в receipt и запрещает PASS даже при успешном повторе;
ошибки сборки/подготовки/транспорта прерывают запуск. 32/32 runner units PASS,
включая новые continuation/false-green regressions.

Исходные 20 секунд короче документированного default WorkManager retry delay
30 секунд ([Android Developers](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work#retries_backoff)).
Test budget расширен до 60 с для одного обычного retry и scheduler latency,
с diagnostics attempts/stop reason/next eligible time/connected/validated.
Offline ENQUEUED/zero attempts/no queue и final SUCCEEDED сохранены;
после успеха дополнительно проверяется отсутствие запущенной sync queue.
Related offline-sync-readiness-01: 5/5 PASS. Это не доказывает исходную причину.

Найден и устранён старый пробел fixture: он сам переключал радио без ожидания
готовности адаптера/именно Wi-Fi и без завершения восстановления между тестами.
Используется существующий EmulatorNetworkFixture с close в finally; worker,
его constraints и реальные account/data не меняются. Адресный повтор идёт
на окончательном варианте перед новым полным прогоном.

Related offline-sync-readiness-02: **5/5 PASS** с общим network fixture;
owned AVD остановлен. Runner32 и docs/24 tests PASS. Production worker не менялся.
Окончательный Full и отдельный планшетный Full всё ещё обязательны.

## Пятый Full: полный список результатов и четыре test failures

Source `0103a75dd598229b8c998d26be8f11672804a2f9`, fingerprint
`65e7feef3f467cbaa90ad2e34c90f35c0d29f0f7109d210673f1a62736d638a6`.
Свежая матрица пяти owners: `phone-full-05-delivery-matrix.json`; все прежние
требования/ветки/pins включены. 913 units, runner32, version matrix,
docs/24 tests, diagnostic delivery и все 36 системных chooser profiles PASS.

Все **253 required метода начаты: 249 PASS /4 обычных FAIL** в трёх группах.
Missing и эффективных skipped cases нет. Преднамеренный fatal diagnostic
сценарий подтверждён отдельно; permission-denied case после initial grant-only
assumption выполнен явным deny проходом. Receipt учитывает успешные группы
как 226 distinct cases; остальные 23 успешных метода внутри двух failed
классов подтверждены raw instrumentation. Общий результат — **Full FAIL**,
full-passed.json не создан. Evidence: `...\evidence\phone-full-05`,
`native-complete-summary.json` и все индивидуальные logs.

| Метод | Фактический сбой / вывод |
|---|---|
| LocationOrbitTest.phoneAccuracyFloorMatchesThePanelAndNativeCircleWithoutChangingFixes | Generic 30s radius timeout, строка95; исходный kind/radius не сохранён, причина не доказана |
| CascadingFormsTest.swipesWorkAcrossSelectorsCheckboxCommentsAndBlankSpace | Galaxy в окне2340x1080: checkbox y1030 ниже form viewport331..849; target невидим, новый helper честно отказался посылать жест |
| CascadingFormsTest.verticalScrollAndSelectedCommentKeepTheirOwnGestures | Selected EditText выше видимой прокрученной области; это не доказательство ошибки production tab navigation |
| FormAppearanceTest.clearFieldsHavePermanentCaptionsWrappedValuesAndReadableThemes | Тест требовал минимум2 строки даже когда имя помещалось в широком landscape picker |

Все остальные группы выполнены, включая недельные сообщения, видимую часть
линейных подписей, Stop/старую историю/все шесть TrackControls, mixed candidates
и field-line/point order, classic/native styles, polygon holes/patterns,
SDK/SQLite persistence, project selection, scripts, required fields,
signature/draft recovery и diagnostics. Смартфонная production версия224,
font1.15/density420 не менялись. После прогона смартфон был отключён пользователем.

## Адресные исправления после пятого Full

Production GPS/renderer/form код не менялся. Gesture fixture раскрывает
центральную touch band обычной прокруткой перед одним прежним жестом;
selected-text/signature/vertical/blank-area assertions сохранены. Имя проверяется
полностью на фактической ширине с Layout/ellipsis/height oracle; на 320/280dp
компонентах строгий wrap остаётся обязательным в обеих темах.

Accuracy fixture отключает heading callbacks как соседний Orbit case,
ждёт zoom22 и app scene после камеры. .08m tolerance, 30s deadline и проверки
неизменности raw fix сохранены. При timeout теперь видны kind/expected,
cached accuracy, native radius, camera и source state. Related PASS не
объявляется доказательством исходной production причины.

Пользователь переключил ADB на **T40 Plus_ROW**, serial59843808167874,
Android11/API30, hardware ums512_1h10. Production **3.1.2.29/223**,
font1.0/density280, secure location_mode3 сохранены. Новые Related:

- `adaptive-fixtures-tablet-01`: **26/26** form cases PASS;
- `adaptive-fixtures-emulator-02`: **28/28** form/cursor cases PASS;
- текущий runner suite: **43/43 PASS**.

Первый emulator Related успел пройти23 form cases, затем ADB потерял
авторизацию при смене устройства (`adaptive-fixtures-emulator-01`). Его owned
read-only AVD завершён через console; PID25128/16444 отсутствуют по readback.
Исправлена финализация: owned console останавливается даже при restore error,
launcher сохраняется и join-ится, cleanup.json содержит readback. Успех/gate
пишутся после cleanup. Второй emulator Related завершился с закрытым console
и exited launcher; borrowed AVD и общий ADB server не останавливаются.

На планшете standard Collector discovery не нашёл1403/других доступных
проектов. По app/AGENTS.md задан обязательный вопрос о проекте. Подготовлен
явный `TestProjectFixture` для согласованного metadata-only snapshot:
schema1, matching ID, максимум256KiB/500 layers, только policy fields,
без accounts/features/coordinates, с source/output SHA256/provenance.
Автоматического fallback нет. Прежний phone1403 snapshot содержит18 слоёв,
SHA256 `08f3a44f84d8bb22701d25d466e957829b22fb40074144bbc3ac751de59935d8`;
его применение на планшете пока ожидает ответа пользователя.

Новый Full планшета выполняется после выбора проекта и фиксации исходников.
Повторный Full смартфона на окончательном коде остаётся обязательным;
сборка локального APK разрешена прежним исключением, публикация/merge отложены.

## Локальный подписанный APK и состояние handoff

Code source `bf10dfd09d5b5562975dc4f2d88c5657af43f71c` отправлен в Draft #78;
maplib `3ad5d49` /Draft #60 и maplibui `d4b8f27` /Draft #44 включены точными
gitlinks. После нового fetch все пять Android owner trees чистые, published
required heads и прежние commits присутствуют по ancestry.
Evidence: `bf10dfd-preflight.log`, `bf10dfd-delivery-matrix.json`.
Desktop checkout другой задачи не менялся. Merge/deployment order по-прежнему
#60 Merge Commit → #44 Merge Commit → remote merged pins → #78 Squash;
цепочка остаётся открытой, публикация не разрешена.

Normal **Lisa Release 3.1.2.30/224** успешно собран. Подтверждены package
`com.nextgis.mobile.geonical`, label `NextGIS ЛИСА`, min26/target36,
четыре ABI, DEBUG=false/FIELD_VERIFICATION=false/FLAVOR=lisa. Подпись проверена,
certificate SHA256 `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`.

- APK: `Q:\Codex\Artifacts\lisa-mobile\2026-10-26\lisa-3.1.2.30-224-bf10dfd.apk`;
- размер70493937 bytes;
- SHA256 `fbf260e226df79b798e00c68af8c04603854d9e7fa44bb5750745af12290e91f`;
- рядом сохранены `.metadata.json` и `.sha256`, hash копии совпадает с build output.

Первая ручная команда сборки остановилась до компиляции из-за разбора
`-Pkotlin.incremental=false` PowerShell; повтор с literal argument array
успешен (`bf10dfd-release-02.log`). Это локальная сборка по исключению
пользователя, не принятие Full и не публикация. Последующие docs-only commits
не меняют product/test sources данного APK.

Последние адресные результаты: emulator28/tablet26, runner43/docs24 PASS.
Финальные Full остаются pending: для планшета ожидается выбор разрешённого
проекта/снимка; смартфон R5GL130K54D после тестовых изменений ещё не подключён.
`full-passed.json` отсутствует; успешная сборка/Related не заменяют эту приёмку.

Собственные AVD остановлены: console закрыт, launcher exited, старые
PID25128/16444 отсутствуют. Новых SDK/runtime copies или downloads нет.
Compact evidence около9MiB сохраняется в
`Q:\Codex\Scratch\mobile-full-20261025-f06ccfb\evidence` до приёмки PR;
storage manifest остаётся active/waiting user choice. Отдельных runtime/cluster
copies в task не создавалось; временные read-only AVD overlays принадлежат
жизненному циклу SDK. Собственные процессы завершены, отдельная инвентаризация
всего SDK temp не выполнялась. Shared SDK/Gradle/build caches сохраняются как reusable inputs.
Durable APK и прежний f06ccfb APK не входят в disposal scope.

Последний handoff preflight (`handoff-preflight.log`) fetched все репозитории:
все пять Android owners чистые, без ahead/behind своих веток. Общий exit2
вызван 17 изменениями другой desktop-задачи на codex/roadmap-admission;
эти пути вне Android scope и не трогались. Это не общий preflight PASS.
Tablet after-readback сохранил production223/lastUpdateTime, font1.0,
density280 и secure location_mode3; verification resources остановлены.

## Шестой Full: блок среды после перезагрузки 26 октября

Смартфон R5GL130K54D снова подключён; проект1403 найден, read-only snapshot
18 политик имеет прежний SHA256. Fetch/preflight прошёл для всех owners,
включая чистый desktop другой задачи. Матрица `phone-full-06-delivery-matrix.json`
подтвердила прежние commits, открытые PR и точные pins. App HEAD `ec6ad79`
отличается от code/APK source `bf10dfd` только документацией; текущий source
fingerprint `9cf1f27d6795c00e5223b71ab836051929d1a4b2c650d0b87b0ef3b0f3bba8e5`.

`phone-full-06` остановился **до сборок и native tests**: emulator35.6.11
сообщил об отсутствии CPU acceleration. Это не повторное падение Android-кода
и не выполненная приёмка смартфона. `runs=[]`, Full FAIL, gate отсутствует.
Cleanup подтвердил закрытый own console и exited launcher; живых own AVD нет.
Телефон сохранил production224/lastUpdateTime, font1.15/density420/location3.

По новой просьбе пользователя сначала исследовано постоянное отключение
Windows Hyper-V. Выполнены только read-only CIM/registry/event/SDK проверки
и отдельный read-only BCD/feature probe через Windows UAC. Настройки не менялись.
Факты текущей загрузки 26 октября09:34 Europe/Warsaw:

- `HypervisorPresent=false`, BIOS VT-x включён; VBS status0, security services0;
- HypervisorPlatform, VirtualMachinePlatform и WSL — Disabled;
- в текущей BCD-записи `vsmlaunchtype Off`, но явного `hypervisorlaunchtype` нет;
- последний найденный успешный запуск Hyper-V — 24 октября18:58 CEST;
- servicing log фиксирует запрос отключения HypervisorPlatform 25 октября22:29
  и завершение26 октября09:33 CET; повторное самовключение после этого не доказано;
- AEHD/GVM services отсутствуют (SC1060), хотя SDK уже содержит подписанный
  installer AEHD2.2.0.0; catalog Authenticode Valid, signer Microsoft Windows
  Hardware Compatibility Publisher. Новые downloads/runtime copies не нужны.

`vsmlaunchtype Off` не заменяет запрет запуска самого гипервизора.
Для явного постоянного boot setting используется административная команда
`bcdedit /set hypervisorlaunchtype off`, затем проверка BCD и состояния после
обычной перезагрузки. Это сохраняемый параметр, не абсолютная защита от будущего
изменения администратором/установщиком. Microsoft описывает
[BCD launch settings](https://learn.microsoft.com/en-us/windows-hardware/drivers/devtest/bcdedit--set)
и [зависимые компоненты Hyper-V](https://learn.microsoft.com/en-us/troubleshoot/windows-client/application-management/virtualization-apps-not-work-with-hyper-v).
Дополнительно отключать уже неработающие security/Windows features не требуется.

При оставленном выключенным Hyper-V для ускоренного emulator нужен другой
драйвер — имеющийся AEHD. Google рекомендует WHPX и объявил завершение поддержки
AEHD31 декабря2026: [официальные требования](https://developer.android.com/studio/run/emulator-acceleration).
Изменение BCD/установка kernel driver пока не выполнялись: последняя просьба
пользователя — сначала разобраться. Native проверки приостановлены на время
этого разбора; старое предположение «достаточно перезагрузки с Auto» к текущим
отключённым Windows features неприменимо. Следующий Full требует рабочей среды;
планшетный Full дополнительно ожидает устройство и выбор проекта/снимка.

Evidence: `...\evidence\hypervisor-20261026` (read-only BCD, feature receipt),
`...\evidence\phone-full-06` и baseline смартфона. Небольшие диагностические
файлы сохраняются вместе с прежними evidence до приёмки PR; новых bulk inputs
не создавалось, код приложения и ранее выданный локальный APK не менялись.

После разбора пользователь явно разрешил «закрепить Off и установить AEHD».
Через UAC выполнено только это изменение, с readback исходной/новой BCD,
проверкой hashes INF/SYS/CAT и Valid catalog signature. `hypervisorlaunchtype Off`
сохранён, AEHD2.2 running/System; `emulator -accel-check` вернул exit0 и
`AEHD (version 2.2) is installed and usable`. `HypervisorPresent=false`.
Windows features, VBS, BIOS не менялись, перезагрузка не выполнялась.
Долговечность boot setting после следующей перезагрузки требует отдельного
readback; для текущих тестов ускорение уже доступно. Квитанции:
`approval-scope.json`, `change-result.json`, `bcd-before-change.txt`,
`bcd-after-change.txt`, `accel-after.txt` в том же evidence каталоге.
Новый Full будет отдельным запуском: неуспешный Full06 не переименовывается
и не объявляется успешным задним числом.

## Седьмой Full: один оставшийся blank-area subcase

Source HEAD `573d84d69b72f9b26eaf4cbea55089112b91615c`, тот же product/test
fingerprint `9cf1f27d6795c00e5223b71ab836051929d1a4b2c650d0b87b0ef3b0f3bba8e5`.
AVD с AEHD2.2/OpenGL host загрузился за37 секунд. Все913 units, runner43,
version matrix, docs24, diagnostic delivery и все36 chooser profiles PASS.
Все253 native метода выполнены: **252 PASS/1 FAIL**, missing нет.
Receipt содержит230 успешных distinct cases целиком успешных групп;
ещё22 успешных sibling метода failed CascadingFormsTest подтверждены raw log.
Квитанция — `...\evidence\phone-full-07\native-complete-summary.json`.

Один FAIL: `swipesWorkAcrossSelectorsCheckboxCommentsAndBlankSpace`,
`Empty area belongs to page navigation too expected:0 but was:1`.
Все остальные формы, включая прежний wrap и selected-text subcases, прошли.
Также прошли все новые подписи, Stop/история/track controls, mixed chooser,
field-line/point order и недельные сообщения. Итог **Full FAIL**, gate не создан;
после восстановления настроек own console закрыт, launcher exited.

Тест предполагал, что центр ScrollView ниже страницы Tabs. В коротком окне
это не гарантирует пустую область; исходный лог не фиксировал точный дочерний
hit, поэтому причина первоначального непройденного жеста не доказана.
Fixture теперь создаёт некликабельную72dp область вне Tabs и раскрывает её
перед единственным жестом. Пять повторений на остальных контролах и все
проверки checkbox/comment/selection сохранены; assertion включает геометрию
и screen touch point. Production FormScrollView не менялся. Требуются Related
полного CascadingFormsTest на эмуляторе/смартфоне и новый окончательный Full.

## Related после Full07 и Wi-Fi планшета

`blank-area-native-01`: весь CascadingFormsTest **23/23 emulator +23/23 Samsung
PASS**, own AVD finalized/console closed/process exited. На вновь подключённом
по Wi-Fi T40 Plus **23/23 cascade +3/3 appearance PASS** (`blank-area-tablet-01`).
Последний Related целиком FAIL: подготовка ProjectLayerSelectionTest остановилась
на `mkdir .../Android/data/com.nextgis.mobile.verification.debug/files: Permission denied`,
сам native project case не начат. Это отдельный setup failure, не failed assertion
26 уже выполненных form cases.

Пользователь подтвердил, что1403 загружен на планшете, и отдельно разрешил
сохранённый metadata-only snapshot18 слоёв со смартфона. Выяснилось, что Android11
этой прошивки запрещает shell обход app-owned каталога: UID2000 не имеет
ext_data_rw, каталог production принадлежит UID10497; verification UID10501
может читать собственные private files через run-as. Поэтому прежнее отсутствие
результата discovery не доказывает отсутствия1403. Root, chmod, SELinux,
production grants/данные не менялись. Wi-Fi connection/identity и baseline
сохранены в evidence; production223/font1.0/density280/location3 прежние.

Runner передаёт whitelist policy JSON через `exec-in run-as`/stdin в private
files verification APK, проверяет собственный package/path и SHA-256. Native
test проверяет canonical getFilesDir. Общедоступного transfer файла нет;
пять новых regressions (всего48 PASS) покрывают чужой путь, обрыв, hash и UTF-8.
Требуется адресная проверка private transport на API30/36, затем новые Full
на смартфоне и планшете на окончательном source fingerprint.

Адресная проверка private transport завершилась: `private-policy-tablet-01`
**1/1 PASS** на API30 через Wi-Fi и `private-policy-phone-01` **1/1 PASS**
на Samsung/API36. Оба используют fingerprint
`dcc6e55bc707909bf147b722bf77f1615cf8e1f165cf18b70f522fe551f80dc9`.
Согласованный snapshot18/SHA256 прежний; теперь ни подготовка, ни native чтение
не требуют shell-доступа к Android/data. Runner48/docs validator PASS.
Исходники фиксируются перед Full08 смартфона и последующим Full планшета.
