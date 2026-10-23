---
title: Настройки и конфигурационные ключи
type: reference
last_verified: 2026-10-23
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppSettingsConstants.java
  - app/src/main/java/com/nextgis/mobile/activity/MainActivity.kt
  - app/src/main/java/com/nextgis/mobile/stakeout/StakeoutSettings.java
  - app/src/main/res/xml/preferences_general.xml
  - app/src/main/res/xml/preferences_map.xml
  - app/src/main/res/xml/preferences_location.xml
  - app/src/main/res/xml/preferences_tracks.xml
  - app/src/main/AndroidManifest.xml
  - maplib/src/main/java/com/nextgis/maplib/util/LayerConfigUtil.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/GeometryEditDraftStore.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/BackgroundRecordingSoundMonitor.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SettingsConstantsUI.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/MapViewBase.java
---

# Настройки и конфигурационные ключи

Центр курсора положения: синяя точка 5 dp с белой каймой 2 dp в обоих вариантах; отдельной настройки нет.

## Диагностика GPS-эпизодов

Новых настроек нет. Пороги и границы snapshot v2 принадлежат GpsRecoveryPolicy;
`last_callback_age_ms` и `last_callback_delivery_lag_ms` описывают разные времена.
Короткий no-fix сам по себе не создаёт GlitchTip event. См. [GPS pipeline](../architecture/location-pipeline.md).

## Общие треки и настройки подписей

История и видимость слоя «Мои треки» общие для установки, включая новые проекты.
track_send и registration gate прежние. tracks_temp.track_map теперь указывает
на общий каталог; URI активного сеанса переводится после миграции.
У обычных rule-style слоёв explicit false/default optional label settings
сохраняются при reload; отсутствующие наследуются из Other.
[Треки](../architecture/shared-tracks.md), [матрица стилей](../architecture/classic-style-verification.md).

## Расписание и быстрая отправка

Выключение расписания NGW account отключает полные scheduled проходы.
Сохранённые правки синхронизируемых слоёв отправляет независимый LocalChangesWorker;
полностью запретить отправку слоя позволяют его SYNC_NONE/направление синхронизации.
Full-project timestamp внутренний, хранится по canonical map path, не является
настройкой account и не меняется после быстрой доставки или трека.
Вынос сохраняет настройки compass/audio и пороги; строка коррекции показывается
только в отдельном окне измерения азимута.

## Фоновый GPS и сохранение сетевой точки

`background_gps_enabled` — «GPS в фоне» в настройках местоположения, boolean,
default **true** в XML и runtime. Автоматически получать координаты после открытия
приложения, в формах и при выключенном экране; отключать через два часа вне
приложения. При выключенном фоне GPS продолжает работать в открытом приложении,
включая формы. Выключение не останавливает отдельно запущенные Track/Walk/stakeout.
Смахивание из последних приложений или подтверждённый выход двойным Back сразу
отключают автоматический GPS до нового открытия. Обычное сворачивание сохраняет фон;
активные трек и обход продолжают запись при обоих способах закрытия.
Тихое уведомление обозначает действующий foreground service. Background worker
и boot без открытия интерфейса не считаются пользовательским запуском GPS.
Последняя сетевая позиция остаётся видимой с исходной точностью и приблизительным
возрастом; это display estimate, а не свежая позиция для записи.


Маркер местоположения «Орбита» применяется во всех flavors без отдельного
переключателя. Компас приоритетен; при его отсутствии или недоступности
треугольник следует курсу движения, на остановке без направления скрывается.
В «Настройки → Местоположение» чекбокс «Показывать сектор точности компаса»
сохраняет `show_compass_accuracy_sector` (boolean, default false в XML
и runtime). Выбор применяется при возврате на карту без перезапуска
и сохраняется при reload. `MapDrawable.setCompassAccuracySectorVisible`
переключает только заливку и оба контура. Стрелка направления и круг GPS
остаются активными. Полуугол сектора 5–90° задаётся оценкой неопределённости,
длина — 56 логических пикселей. Расчёты и стили сохранены для доработки.

«Орбита-кольцо» имеет прозрачное окно 21 dp с синей точкой 5 dp и белой каймой
2 dp; синий обод
утолщён наружу на 1 dp до радиуса 13 dp за счёт белого обода с внешним радиусом
15.2 dp. Тёмная кромка, треугольник и двойная граница сектора сохранены.
Служебные заливки GPS и компаса освобождают окно, оставляя видимой точку слоя
под курсором. Настройки цвета или размера не добавляются.
Метрический круг accuracy GPS остаётся отдельным. Новый ключ — `show_compass_accuracy_sector`.

`show_sync` — «Сообщать о начале и завершении синхронизации», по умолчанию false
в XML и app fallback. Явный выбор сохраняется; ошибки очереди показываются
независимо от этого пункта. Тихий системный индикатор фоновой службы отображается
только во время работы, включая отмену до безопасного завершения. При старте
основного процесса снимаются старые индикаторы 519/520, но не уведомления ошибок.

Без активного сетевого соединения ручная синхронизация сообщает «Отсутствует
подключение к интернету» до запуска очереди. Такой же текст используется в диалоге
и уведомлении при отключении сети. Проверяется штатная доступность сети, без
требования внешней Internet validation: локальные/on-premise NGW и VPN остаются доступны.
У обхода `gps_paused` хранит явную ручную паузу; отсутствие GPS её не меняет.

Фоновые account и track jobs ждут подключения. Потеря сети до отправки не
создаёт уведомление/багрепорт; recovery queue и неотправленные точки сохраняются.

sync_all_projects — «Синхронизировать все проекты» в общих настройках,
по умолчанию true. Определяет охват ручного и scheduled account запуска:
все загруженные проекты либо только открытый. Не меняет период/включение
подключений, отдельную команду слоя и owner уже записанной recovery pair.
Действия пользователя: [руководство синхронизации](../guides/project-synchronization-user-guide.md).

Предупреждение об энергосбережении GPS читает системный PowerManager и текущий
`gnss_input`; отдельной настройки «не показывать» нет. Старое
`battery_dont_show_pref` относится только к запросу Doze exemption, не к
глобальному ограничению GPS при выключенном экране. Переход открывает настройки
Battery Saver; приложение не меняет системный режим самостоятельно.

Полный структурированный список находится в
[`../registry/config-keys.yaml`](../registry/config-keys.yaml).

## Правила

Диагностические фильтры не добавляют пользовательских настроек. Штатные local
tile GET 503 с queue_full/heap_headroom и ожидание спутникового решения остаются
локальными. Длительность проблемной причины GPS, интервалы observer/сна и OS
process_exit metadata описаны в [контракте отчётов](../architecture/error-reporting.md).
Доступ оператора хранится вне Git; адрес и проект для CLI — в tools/glitchtip.json,
команды — в [runbook](../runbooks/glitchtip-triage.md).

Режим трека выбирается в выпадающем меню кнопки записи и сохраняется как
`track_recording_mode` перед запросом разрешений. Значения и миграция описаны
в registry; порог, разрывы и восстановление — в
[контракте GPS](../architecture/location-pipeline.md#режим-записи-трека).

Существующий `track_recording_enabled` также разрешает фоновое обновление
текущего трека из GPS callback карты. Нового preference нет; default `false`,
восстановление записи и явный Stop сохраняются, SQLite на GPS/main thread не
используется для определения этого состояния. Этот флаг означает намерение,
а не факт успешного запуска: меню показывает активную запись только после
подтверждения foreground service и созданной строки трека. Маркеры
`permission_requested_*` позволяют при повторном отказе предложить системные
настройки приложения вместо бесполезного повторного Android prompt.
`POST_NOTIFICATIONS` не входит в эту обязательную цепочку и не проверяется
при запуске карты или возврате в неё. Разрешение запрашивается только при
включении функции уведомлений в настройках.

- Key/default в constants, XML и runtime fallback должны совпадать.
- Новый preference получает owner, type, default, UI/source и migration note.
- Build property и manifest metadata документируются без secret value.
- Layer JSON/metadata keys меняются только с backward-compatible parser или
  явной миграцией.

## Группы

- App map/UI: location, compass, info, zoom controls, layer list. Internal
  `map_rotation_enabled=false` keeps two-finger rotation locked by default;
  `map_bearing=0` stores the last bearing only while the user has explicitly
  enabled rotation. Neither key is shown in the general settings screen: the
  state is controlled by the toolbar button next to location. When enabled,
  MapLibre keeps pinch and rotate detectors concurrent and uses a `0.5°` rotate
  threshold so immediate simultaneous two-finger contact is responsive.
  The camera floor is `7.5`; enabled raster underlays are visible from zoom `7`.
  The `light` map background uses a solid MapLibre background color
  (`#FFFFFF`) without a repeating pattern, including after switching from
  neutral or dark. Raster sources are
  rendered directly without display-side tile transformations.
- Фото-вложения: `photo_overlay_enabled=true` и
  `photo_overlay_use_object_coords=true` включены по умолчанию. Миграция
  `photo_overlay_defaults_enabled_v1` один раз включает оба ключа на уже
  установленной версии; после этого ручной выбор пользователя сохраняется.
- Карта: `keep_screen_on=true` держит экран включённым, пока открыта карта.
  Миграция `keep_screen_on_default_enabled_v1` один раз включает ключ на уже
  установленной версии; после этого ручное выключение сохраняется.
- Tracking/location: интервалы, distance, foreground service toggles и
  `background_recording_sound=true`. Последний ключ включает короткое звуковое
  подтверждение по фиксированному расписанию раз в 10 секунд, когда UI приложения
  скрыт либо экран выключен и общий GNSS-поток продолжает получать свежие пригодные
  координаты. Контроль работает до прореживания точек и не добавляет точки в
  геометрию, поэтому неподвижный GPS продолжает пикать; при прекращении доставки
  координат сигнал замолкает. Явная ошибка сохранения получает отдельный более
  длинный сигнал не чаще раза в минуту. Звук использует alarm stream и поэтому
  не зависит от минимальной громкости уведомлений. Если alarm stream выключен
  или его громкость равна нулю, heartbeat заменяется короткой вибрацией, а ошибка
  сохранения — короткой двойной вибрацией. Выключение самого preference подавляет
  и звук, и вибрацию. Partial wake lock принадлежит GPS-записи,
  удерживается и при выключенном звуке и освобождается последним recorder.
- Вынос координат: начальное состояние звука и четыре строго убывающих порога
  `stakeout_far_distance`, `stakeout_medium_distance`, `stakeout_near_distance`,
  `stakeout_reached_distance` в метрах и сохраняемая коррекция склонения
  `stakeout_declination_correction` в градусах (восток положителен, диапазон
  ±180°, шаг `0,1°`, сброс в `0`). Некорректный набор порогов не применяется;
  runtime использует безопасные значения `5 / 1 / 0,5 / 0,1`. Эти настройки
  применяются также к измерению от текущего положения до выбранной точки. Режим
  «между двумя точками» является статическим, не использует GPS/звук и не создаёт
  отдельных ключей, но читает ту же коррекцию склонения. Магнитное склонение
  рассчитывается встроенной WMM2025; ручная поправка `C` добавляется к модельному
  `D`. Виджет во время измерения показывает эффективное `D + C` и отдельно `C`.
  Live-измерение требует доступа к местоположению; Bluetooth-разрешение для
  штатного GPS не требуется. Отказ запуска завершает измерение с сообщением и
  освобождает ресурсы; после выдачи разрешения можно повторить запуск. Настройки
  звука и порогов не меняют Android service type `location`.
- Updates: `check_updates`, update flavor metadata, release repository fields.
- Backups: `layer_backup_max_gb` (Общие → Другое, default 5 GB) caps `LayerBackups/`;
  каждый ZIP хранит таблицы слоя и только локальные файлы вложений, без
  скачивания server-only payload по метаданным.
- Логи: `save_log` (Общие → Другое, «Логирование», default true) включает файл
  HyperLog и пункт «Поделиться логом». `verbose_log` (там же, «Расширенное
  логирование», default false) пишет каждое измерение GPS/NMEA, включая
  координаты; только для диагностики.
  Общий `LocalLogInitializer` сохраняет локальный журнал и формат, удаляя при
  старте устаревшую настройку `HyperLog/URL`; фиктивный endpoint не записывается.
- Автоматические отчёты GlitchTip независимы от локального логирования.
  map-runtime-diagnostics.json — private bounded snapshot до 16 KiB для slow
  queries/tile pressure, не пользовательская настройка. Быстрые операции не
  записываются; event timestamps предотвращают присоединение чужого startup state.
  `sentry.dsn` из игнорируемого `sentry.properties` задаёт приёмник; APK не содержит
  пароль администратора. `io.sentry.auto-init=false`, screenshots/view hierarchy
  и performance sampling отключены. SDK queue в private files ограничена 256
  обычными envelopes; connected WorkManager retry не запускает GIS sync.
  Автоматические HTTP 5xx ограничены десятью минутами на одинаковую группу;
  fatal и replay сохранённых отчётов сохраняют прежний путь доставки.
  Контракт диагностики 2 сохраняет URL/query, headers/cookies, доступные данные
  события и пути без маскирования. Ограничитель размера не читает HTTP streams
  или файлы проекта и не влияет на точность ключа подавления повторов.
  [Доставка и границы](../architecture/error-reporting.md),
  [разбор ошибок](../guides/bug-reports-user-guide.md).
- При открытии/Resume `BackgroundLocationWarning` проверяет GPS-политику
  энергосбережения, системный запрет фоновой работы и наличие исключения из
  оптимизации батареи. Предупреждение с «Настроить» показывается без записи трека;
  при одном доступном действии сразу открывается системный запрос или страница,
  при нескольких ограничениях сначала предлагается выбор нужной настройки;
  одна неизменная ситуация не повторяется при screen-on или rotation в текущем
  запуске. Старое `battery_dont_show_pref` эту проверку не отключает. Настройки
  меняет пользователь; возврат не запускает трек/обход.
- Collector: project registry JSON, project metadata, composition state.
- Layer config: `feature_label_field`, `mobile_render_mode`, `render_mode`,
  `layer_origin`, `mobile`.
- Local storage: Collector workspaces и `LayerBackups`.
- Crash journals: `track_recording_enabled`, `walkedit_temp`,
  `geometry_edit_draft`, `feature_form_draft`; это app-private runtime state, а
  не пользовательские настройки UI. `track_recording_enabled` читается панелью
  карты и `TrackerService` в одном основном процессе, чтобы Start/Stop не
  расходились из-за межпроцессного кэша SharedPreferences.

`mobile_render_mode: "local_vector_tiles"` применяется только как явный opt-in:
для read-only polygon/multipolygon либо простого `Point` с круговым маркером
`MarkerStyleCircle` (`type = 2`) и
подписью из одного поля/фиксированного текста. Неподдерживаемый точечный стиль,
rule renderer, custom icon, template или editable слой автоматически остаётся
на classic rendering.

`feature_label_field` — верхнеуровневое поле `config.json`, которое задаёт
отображаемое имя объекта в верхней панели, таблице атрибутов и списке выбора,
когда под тапом оказалось несколько объектов. Например:

```json
"feature_label_field": "num_line"
```

Это не поле подписи на карте: последнее по-прежнему задаётся отдельно в
`renderer_properties.style.value`. Выбор в «Настройки слоя → Поля» сохраняется
сразу в `config.json`; для старых слоёв значение читается из локального
preference `layer_label` и переносится в JSON после первого успешного открытия
слоя новой версией.
Отсутствующее поле или пустое значение объекта даёт fallback на `_id`.

`local.properties`, real Sentry DSN, NGID client secrets и signing credentials
не являются документационными данными.

Базовый URL APK repository задан константой `APK_VERSION_UPDATE` и равен
`https://apps-geonical.ru/lisa-mobile`. Он не является preference: изменение
host/path требует новой подписанной сборки. Ветка выбирается из build variant и
проверяется повторно по metadata загруженного APK.

Внешний Pigo Lite/Lite Plus по BLE не требует Mock Location и предварительного
запуска PiMock. Профиль ComNav `3A20` использует отдельные write/read каналы;
тихой сессии добавляется только BESTPOSB. Поправки/УКВ и сохранённая конфигурация
не меняются. HUD показывает решение приёмника, которое исчезает через 8 с без
данных, а не факт соединения или работающего foreground service.

## Источники и интервалы GPS

Карта использует свежий GPS (чип, mock или native NMEA) и Network только если GPS нет;
запись трека и обхода — GNSS чипа, mock приёмника или native NMEA. `gnss_input`
выбирает системный GNSS или внешний приёмник. Старые переключатели источников мигрируют в пояснения. Интервалы времени
и расстояния задают сохранение точек после фильтра, а в разделе местоположения
относятся к обходу. Для mock и native NMEA прореживание не грубее 2 с и 1 м. Карте достаточно approximate permission; записи системного GNSS требуется
fine permission. Native NMEA не требует Mock Location. См. [GPS pipeline](../architecture/location-pipeline.md).

`walkedit_temp` дополнительно хранит UUID обхода, путь активной карты, полную WKT,
фазу RECORDING/FINISHING/FINISHED, ревизию и UUID/этап/слой/инструмент создаваемой
точки. `WalkSessionStore` владеет этими ключами. Это внутренний журнал сессии;
пользовательские интервалы GNSS и звуковая настройка не меняются. UUID точки
сохраняется до её успешного Save или явного Cancel; формы переносят UUID через
durable checkpoint до запуска Activity. Точные имена ключей определены в
`WalkSessionStore`, сценарии — в [crash recovery](../architecture/crash-recovery.md).

Пункт «Сбросить зависший обход» в общих настройках — действие, а не сохраняемая
настройка. Он активен только при наличии состояния обхода и после подтверждения
удаляет `walkedit_temp` и принадлежащий этому обходу checkpoint формы. Другие
черновики, проекты, слои, объекты и учётные записи не сбрасываются.

## Хранилище подложек

В общих настройках `shared_underlays` открывает каталог с использованием по проектам. Удаление payload требует подтверждения списка проектов; обычное удаление слоя означает только «Убрать из проекта». Сброс настроек не удаляет каталог. Внутренние `shared_underlay_id`, `shared_underlay_migration` и `debug_companion_install` описаны в [контракте](../architecture/shared-underlays.md).

## Служебные ключи надёжности

`tracks_temp` хранит owning track_uri, track_map и pending_stop; последний
переживает ошибку Stop и снимается только после выгрузки очереди/закрытия сессии.
`feature_form_draft` остаётся совместимым v1 JSON, дополненным map path и UUID
операции. Sign snapshot хранит размеры холста и штрихи. `pending-track-points`
и `feature-save-journal` — локальные storage contracts, не пользовательские
настройки и не server schema. Их повреждение нельзя лечить автоматическим
удалением. `ciReliabilityChecks` — opt-in Gradle property для проверки без
private Sentry DSN; в обычной сборке требования config остаются прежними.

## Отправка трека

track_send имеет default=true, а track_send_default_enabled_v1 однократно
мигрирует прежнее автоматическое выключение. После миграции ручной opt-out
сохраняется. Регистрация UID управляет готовностью доставки и отметкой трека, но не галочкой.
track_registered_device хранит SHA-256 точной пары сервер/UID после registered=true.
До подтверждения или после registered=false отметка скрыта. Сетевые ошибки
сохраняют прежнее подтверждение; смена сервера/UID требует своего подтверждения.
Контракт: [доставка трека](../architecture/ngw-sync-and-storage.md#доставка-трека-и-регистрация-uid).

## Диагностика после разбора 2026-10-17

diagnostics_contract=3 сохраняет прежнюю политику содержимого и общий bounded
budget, резервируя место для последних действий и GPS/startup/sync context.
map-runtime-diagnostics остаётся приватным журналом до 16 KiB; добавлен startup
и сохранение предыдущего процесса до новых записей. sync_recovery_journal
сохраняет прежние ключи/формат и использует подтверждённую запись собственным
disk writer с восстановлением interrupt вызывающего потока. Пользовательских
переключателей или новых credentials нет; учёт длительности работы не добавлен.
