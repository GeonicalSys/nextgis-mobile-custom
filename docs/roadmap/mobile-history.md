---
title: История дорожной карты мобильного приложения
type: reference
last_verified: 2026-11-13
related_code:
  - docs/roadmap/mobile.md
---

# История дорожной карты мобильного приложения

Архив датированных свидетельств и прежних планов. Текущая очередь и незавершённая приёмка находятся в [дорожной карте](mobile.md). Старые Draft-статусы и FAIL/NOT_RUN сохранены как свидетельства своего времени; они не означают текущую активность или новую приёмку.

## Исторические этапы и свидетельства

## MOB-DEV-01 Стабильные переносы строк в Git и экспорте

Прямое поручение владельца02.11: согласовать `.gitattributes` во всех наших
репозиториях и вынести нормализацию в отдельный commit. Технический приоритет
P1, очередь продуктовых задач не меняется. Общий default LF, Windows-скрипты
CRLF при checkout; бинарные ресурсы и upstream QuickJS остаются побайтными.
Зависимости: согласованные task PR в lisa, maplib/maplibui/easypicker и
upload_mobile. Приёмка: одинаковый общий блок, только EOL diff `gradlew.bat`
в отдельном app commit, отсутствие изменений ресурсов/логики и source docs
checks. Data/sync/map/background/battery не меняются; APK не публикуется.
Доставка02.11 по новой команде владельца: maplib67, maplibui49 и easypicker6
слиты Merge Commit `7152e3cd`, `69cccf20`, `d4405ab4`. App90 закрепляет эти
fetched merged pins; source trees библиотек равны проверенным task tips.
Статус: библиотеки слиты, app подготовлен к Squash Merge; device acceptance
и прежние native стадии остаются отдельно/paused. APK не публикуется.

## MOB-REL-01 Подготовка выпуска 3.1.2.31/225

Доставка02.11 по новой команде владельца: библиотеки maplib66/maplibui48 слиты
Merge Commit `21b285a9` / `73a9c42e`, app89 закрепляет эти fetched merged pins
для Squash Merge. Source trees библиотек равны прежним проверенным tips.
Mobile не публикуется. Это закрывает Git-зависимости, сохраняя native FAIL,
NOT_RUN Related/Full/final APK matrix и отдельную будущую приёмку MOB-REL-01.
Версии production3.1.2.31/225 и Debug3.1.2.23/218 прежние.

Уточнение владельца2ноября2026: перед выпуском удалить obsolete loopback vector
server/ветку renderer; прежние `mobile_render_mode: local_vector_tiles` конфиги
кварталов/выделов принимает новый direct SQLite source. Общие cache/полная
геометрия/подписи сохраняются. Добавлен разбор переноса подложек из прежнего Debug:
жалоба на отсутствие прогресса и10мин без видимого результата, затем Cancel.
Причина задержки пока UNKNOWN; подтвердить source defects и подготовить исправление.
Смартфон отключён, новые live/ADB/emulator проверки отложены до новой команды.
Сейчас source/compile/docs/Draft PR, device acceptance — отдельный следующий этап.

## MOB-REL-01 Подготовка выпуска 3.1.2.31/225

Историческая подготовка выпуска 225. Этот выпуск уже сменён опубликованным 3.1.2.32/226; отдельную подготовку 225 больше не вести. Состояние публичных каналов проверено 13.11.2026: [ЛИСА](https://apps-geonical.ru/lisa-mobile/lisa/manifest.json), [БЕЛКА](https://apps-geonical.ru/lisa-mobile/belka/manifest.json).

Прежние FAIL/NOT_RUN и различия кандидатов остаются в свидетельствах ниже: публикация не превращает их в PASS. Оставшиеся проверки тяжёлой карты и памяти относятся к MOB-03/MOB-04; новые требования к отправке и диагностике — к REL-01/REL-02 и MOB-REL-03. Старый выпуск не даёт права пропустить проверки следующего.

[Подготовка и доказательства 225](../reference/mobile-release-3.1.2.31-2026-11-01.md), [передача выпуска](../handoffs/2026-11-01-mobile-release-31231.md). Карточки: android-20261101-release-31231, maplib-20261101-release-31231.


Первый release checkpoint: library65 и app88 слиты, version matrix обеих
release и Debug PASS, подписанные кандидаты сохранены. Локальный Full на
`b165046d`:256 native PASS/17FAIL, chooser36 и real-window6 PASS; A56 cold
stress PASS. Публикации/установки рабочего release нет. SM-X115 исключён
владельцем, не засчитывается как PASS. Продолжение — адресный разбор FAIL,
без ослабления frozen expectations; [контрольная точка](../handoffs/2026-11-01-mobile-release-31231.md).

29 октября, Git-доставка по прямому поручению владельца: библиотека maplib
слита Merge Commit f61ba783 (PR62), документационный bridge — bf16ea71 (PR83).
Приложение в PR84 закреплено exact fetched library merge, runtime-tree библиотеки
равен проверенному13887f34. Исходные функции не меняются при закреплении;
исторические native/device/Full пробелы не закрываются Git-слиянием. Это новое
поручение снимает прежнее ожидание merge, но не разрешает установку/публикацию APK.


## MOB-REL-02 Восстановление входа в ЛИСЕ и БЕЛКЕ

Исторический корректирующий выпуск 3.1.2.32/226 опубликован 10.11.2026. Публичные манифесты обоих брендов проверены 13.11.2026: [ЛИСА](https://apps-geonical.ru/lisa-mobile/lisa/manifest.json), [БЕЛКА](https://apps-geonical.ru/lisa-mobile/belka/manifest.json). Он больше не находится в работе.

Исправлялся `client_id=null` в опубликованных APK 225: введены проверка сборочного входа и независимая проверка фактического запроса в подписанном APK. AUTH-01 остаётся самостоятельной диагностической задачей, MOB-REL-03 — исправлением отсутствующей статистики и контролем отчётов.

Тестовые прогоны 226 были пропущены по прямому поручению владельца. Их состояние остаётся NOT_RUN; это не разрешение пропускать Full и физическую проверку будущих выпусков. Публичный манифест подтверждает доступность версии, а не выполнение всей матрицы приёмки.


## MOB-04 Ограничение памяти карты на устройствах от2GiB

28 октября, следующий адресный этап по прямому поручению владельца: убрать
полный scan/sort текущего cache на каждом новом тайле и разбирать отменённую
работу до IO dispatch. Состояние: реализация готова,70focused JVM/9classes,
три normal variants и version matrix PASS. Собран обычный Lisa APK для владельца:
private profiler/field verification выключены, установка и новые замеры не
выполнялись агентом. Владелец после самостоятельной проверки сообщил
«Почти моментально отрисовывается всё»; формальную проверку полноты отложил.
Full/публикация/новый merge остаются отложены; долговременный кэш
не добавляется. Карточка: maplib-20261028-tile-cache-queue.

28 октября: новый адресный этап по поручению владельца — приватный opt-in сборщик
ресурсов для ручного прогона T40 по масштабам/плотности/подписям. Приоритет —
корневые расходы холодного пути; долговременный кеш исключён. Состояние: приватная
сборка установлена на T40, ручной сеанс завершён пользователем через19мин,
3×180с CPU/native и полный app export проверены. Подтверждены расходы полного
scan/sort/stat текущего disk cache и очередь; геометрия дешевле. Overhead heap
profiler существенен, native owners частично UNKNOWN; оптимизации ещё не выполнены.
Результаты и ограничения: [разбор T40](../reference/mobile-resource-profile-t40-2026-10-28.md).
Full/публикация остаются отложены. Контракт и ограничения:
[map-performance](../architecture/map-performance.md#приватное-исследование-холодной-отрисовки-28-октября-2026),
[manual runbook](../runbooks/manual-resource-profiling.md).

Приоритет P1, прямое поручение владельца27 октября после нового ng-logs11.
Включено в текущую задачу app82/maplib61; обычная очередь остаётся отложенной.
Владельцы: maplib — бюджет и GIS/render pipeline; app — lifecycle, предупреждение,
системная память и приёмка. Зависимости MOB-02/03, точные library pins до выпуска.
Состояние: реализация завершена, выполняется слияние исходников по поручению
владельца28 октября. Полный прогон и публикация отложены; приёмка на физическом
устройстве2GiB не завершена. Указатели приложения закрепляются на удалённых merge
SHA maplib61/maplibui45; фактический merge-state — в связанных PR и
[checkpoint слияния](../reference/mobile-crash-224-2026-10-27.md#слияние-исходников-без-выпуска-28-октября-2026).

Последний PRIVATE D101: normalall3 PASS62s/versionmatrix PASS, sourcefp62de8740…
59ec91 frozen/71focused PASS/independent review без blockers. Actual T40/API30
проверил inline/PFD reads; сопоставимые121/110-feature BLOB stages83.57→13.28ms
и134.11→15.37ms, но очередь остаётся и SDKreturn не полный кадр. FreshCURRENT787
cab9→D10142+42 samples/6+6taps:lateRSS820.42→643.06MiB(-21.62%), sampledpeak
896.19→751.16(-16.18%); Graphicslate143.36→210.14MiB вырос. Current configs/data
изменились после старойABBA, actorUNKNOWN; серии не объединять. Полная видео
приёмка sharp17→12/newextent,2–3s target/2GiB/Full остаются отдельными gates.
Подробные ограничения — [разбор224](../reference/mobile-crash-224-2026-10-27.md).

Конкретный strict full-scene video gate dense1403/11.7z:32.45–34.09s843d →
13.68–15.65s6039 →10.33–12.27sD101, oneprocesscold run/build/diskretained;
late map ROI MAE0 с обоими предыдущими. Это наблюдаемый выигрыш одной сцены,
не повторная статистика/все стили и не2–3s target. Final allmaplib750PASS/59s,
132classes/0fail/error/skip,source unchanged. TrackLayer по-прежнему полными
GeoJSON snapshots, вне этого ordinary-vector улучшения; trackRAM не измерена.

Подтверждены std::bad_alloc в main и RenderThread27 октября17:10:23UTC на
SM-X115, OS SIGABRT и исторические LOW_MEMORY с RSS до5816068KiB. Java headroom
сам по себе недостаточен. На проекте787 реальные sampled malloc-стеки показали
673MiB в GeoJSONVT, включая не менее564MiB словарей свойств, из907MiB net retained;
растры128MiB. Это удержание копий свойств обычных векторов, не свидетельство
сложности исходной геометрии. Точный триггер223→224 и все native abort не установлены.

Реализованный первый этап: компактные native свойства отдельно от rich editor
features, полный geometry/ID/unknown/style contract, точный повторяющийся мотив
штриховки, полный MVT на диске32/128MiB, два builder/один при малом свободном остатке,
отмена ненужных jobs и cache trim. Ни прореживания объектов, ни упрощения геометрии,
ни отключения слоёв. SQLite/draft/sync/outbox/model visibility не меняются.
По уточнению владельца28 октября warning/замедление/cache economy определяются
ТОЛЬКО свободным остатком системной памяти/Java heap и системным low-memory
сигналом. RSS/native used и номинальные2/8/12GiB не являются такими триггерами.
Один автоматический диалог на процесс; ручной статус остаётся доступным.

Частный experimentalec71 APK на787 дал373MiB sampled retained против907MiB;
GeoJSONVT265MiB, словари свойств199MiB. Профили180s/18dumps без ошибок,38 одинаковых
команд, но разное время начала маршрута и прежняя RSS-политика candidate мешают
считать это строгой приёмкой нового tip или выделить эффект одной оптимизации.
Обычный GeoJSONVT ещё хранит посещённые участки без eviction; абсолютной гарантии
против OOM нет. Исходные трассы и неуспешные прогоны сохранены.

Второй этап в той жеMOB-04 реализован в текущем source tip: полная GeoJSON-копия
поддерживаемого ordinary слоя, включая startup, заменена SQLite bbox/RTree→полные
MVT→CustomVectorSource13.7.1 без HTTP. API есть в установленном SDK;
собственная сборка MapLibre пока не потребовалась. Это ещё не опубликованный
APK и не завершённая приёмка. Сохраняются original DB picking/copy/edit,
rule/template/scale/labels/patterns, source owner/cancellation/content/style epochs.
Необходима полная style parity: существующий прежний HTTP MVT polygon path допускает
rule renderer, но кодирует не все его свойства. Нельзя просто включить его для
всех слоёв или оставить глобальную GeoJSON-копию подписей. Низкомасштабный плотный
тайл сам может быть большим; это отдельный peak-memory gate. JSON настроек,
Collector/NGW interchange и сохранённые проекты сохраняют формат.
Проверять cold start/hot add/restyle/recreate, MVT и classic, редактирование,
направление/GPS во время жестов, освобождение источников и повтор после разгрузки.

28 октября после возобновления собраны обычные приватные Debug/Lisa Release/
Belka Release: build02 PASS83s, version matrix всех трёх PASS. Runtime source
`1b0c09ce…`, app `dbbd453f77f3` с точными maplib8d41588/maplibui edc189cc pins
не менялся. Первый обычный SQLite B diagnostic на SM-A566B завершил300s:
total RSS460.7–614.3MiB, до включения владельцем подписей выделов. Это не
строгий A/B и не физическая2GiB приёмка. Отдельная A-B-B-A серия с одинаково
включёнными подписями, реальным PK3688 и камерой14.5→11.5→14.5 дала168 raw
samples: A1/B1/B2/A2 retry по42/6/210s. Среднее двух run-level statistics:
late60 RSS958.12→659.66MiB (−31.15%), sampled peak1043.82→792.76MiB (−24.05%).
A1 collector FAIL сохранён; исходный A2 transport5037 partial исключён,
успешный retry отдельный. Все DB bytes неизменны,16 configs совпадают с labels-ON
basis; exact camera UNKNOWN, f80920px CENTER/TOP label difference остаётся.
Phone video medians примерно0.2–0.5s перекрываются, ускорение не установлено.
Tablet Collector1403 delay3–10s около12.1 не закрыта; требуются sharp17→12 и
полностью новая область. Небольшие warm11.7 pans не являются его приёмкой.
[Точная основа сборки, измерения и ограничения](../reference/mobile-crash-224-2026-10-27.md).
Merge, публичный выпуск и Full этим checkpoint не закрыты.

## MOB-02 Инцидент завершений после обновления 224

29 октября, адресное продолжение по поручению «разобрать ошибки в глитче»:
после первого520/516получен свежий mobile-only snapshot02:8групп,593записи/
589SDK ID,59новых ID относительно тех же восьми групп28октября.68/88 —
прежняя локальная политика штатных GPS ожиданий;45/64/69/90/96/117 — diagnostics.
Поздняя030/224native/OS пара не устанавливает новый crash последней сборки,
исходный exception или OOM. Для текущих224GPS пауз причина неизвестна:
добавлено редкое fail-open сравнение cached framework fix и app raw без
координат, новой подписки или изменения фильтров/thresholds/владельцев.
877JVM tests,docs50,три normal variants, instrumentation compile и versionmatrix
PASS; два emulator-only native GPS case не запускались, полевой cause остаётся
UNKNOWN. Частный обычный APK517c941d…0db8b01 собран, не установлен/не опубликован.
Source commits и guarded server comments/resolve/readback завершены:
8resolved,599записей/595SDK ID; причины неизвестных сбоев не объявлены исправленными. Full,
профилирование, новые native/device действия не выполнялись. Это уточнение
свидетельств MOB-02/MOB-03, не исправление приёма GPS и не возобновление других
этапов карты. Владельцы app/maplib, Draft84/62; карточка
android-20261029-glitchtip-gps-evidence.
[Решения и ограничения](../reference/glitchtip-triage-2026-10-29.md).

Приоритет P1. Состояние: В Draft PR app82/maplib61; memory failure подтверждён,
конкретный триггер регрессии и остальные abort ожидают свидетельств/проверок.
Основание — поручение пользователя27 октября,
логи повторных запусков Samsung Galaxy Tab при движении карты и GlitchTip53/67.
Владельцы — app и maplib; зависимости — сохранённые pins MOB-01. Внеплановая
причина — инцидент опубликованной224; на время разбора откладывается следующая
обычная задача очереди, приоритеты остальных пунктов не меняются.

Объём: убрать доказанно пустые marker/vertex native queries, буферизовать чтение
прежнего индекса и добавить ограниченную OS exit metadata к явному экспорту ZIP.
Данные/sync/фон/GPS не меняются. Полный async startup, переработка MVT и новое
обновление SDK сюда не входят. Критерии: legacy byte-compatible persistence,
пустой handle без native access, прежнее редактирование/линейка, bounded export,
Related на изолированном AVD, обе release build-проверки и строгие docs.
Прямая причина вылетов224 без fatal stack остаётся неизвестной; выпуск отдельно.
[Свидетельства, карточки и порядок доставки](../reference/mobile-crash-224-2026-10-27.md).

Версия от 25 октября 2026 года. Область — мобильные приложения ЛИСА и БЕЛКА, библиотеки maplib и maplibui и необходимые контракты с PostgreSQL и NextGIS Web.

Срез source/docs01.11 в прежнем MOB-UI-01 разделяет стандартный и NGFP
контролы даты: UI-FORM-DATE-STANDARD-VALUE/UI-FORM-DATE-NGFP-VALUE.
Подтверждены отдельные selector namespaces, millisecond/state/default/null
и ContentValues boundaries; UTC-midnight DATE, фиксированный TIME baseline,
actual zone/locale, private формы и native Cancel не объявлены проверенными.
В owning каталоге добавлены2контракта/22method guards/5file witnesses;
старые44контракта/191guards/64XMLwidgets/49file witnesses сохранены.
Root LISA Draft278 содержит сквозную карту; app delivery остаётся отдельной
собственной docs-веткой/Draft, не merge/APK. Product fixes/ranking/native
не выполняются; приоритеты и очередь MOB не меняются. Свидетельства source-only
и результаты actual validators/delivery фиксируются в карточке и PR отдельно.


Продолжение source/docs01.11 в прежнем MOB-UI-01: UI-FORM-CHECKBOX-NGFP-VALUE
разделяет NGFP Checkbox, Boolean state/cache, Integer0/1 getter и обычный
FTInteger TextEdit/String. Значения NULL/empty, absent control и выбранный
present-checkbox copy-retention gate различаются. Добавлены1контракт,11новых
method guards и4file witnesses;11старых guards переиспользуются. Старые
Date46/213/64/54 записи сохранены; projected47/224/64/58 подтверждаются
actual catalog/validator receipts после применения. Source7records/4routes
и98passive metadata checks не являются native tests или currenttenant proof.
Это та же own app Draft87 и LISA Draft278, без изменения очереди/приоритетов.
Product fixes/ranking/native NOT_RUN; merge/APK отдельно от source доставки.

Текущий глубокий срез: `android-20261029-dependency-depth-01` дополняет
существующие sync/storage контракты и исправляет устаревший active-project-only
oracle в SMOKE-MULTI-ACCOUNT-SYNC. Приёмка среза — проверяемая по source цепочка
owner→scope→provider/service→drain→pair ACK→cleanup→full clock, разные persisted
keys и независимые ожидания. Статус: source/docs в собственной ветке, runtime
NOT_RUN; три открытые ветви ведутся в едином LISA dependency-audit-todo по IDs
AUDIT-TODO-SYNC-ACK-RECOVERY, AUDIT-TODO-SYNC-CLAIMED-LIVENESS,
AUDIT-TODO-WORKSPACE-KEY-MIGRATION. Продукт, БД, NGW, APK и библиотечные gitlinks
не меняются; этот список не становится параллельной очередью работ.

Следующее углубление той же карточки прослеживает окно после последнего pair
ACK до cleanup/full clock, manual service против framework SyncAdapter,
startup recovery против следующего enabled schedule и отсутствие накопления
account-only успехов между запусками.21 методов в5 focused source test files
только прочитаны, выполнено0; точной post-ACK fault injection нет. AOSP14/16
подтверждает возможное расхождение failed-commit memory/disk: cached clock не
доказывает durable completion. Независимые disk/restart и retry oracles остаются
NOT_RUN в AUDIT-TODO-SYNC-ACK-RECOVERY; library/source fix требует отдельного
impact и решения владельца. Это не новый roadmap item и не закрытие runtime TODO.


Адресное исправление по дополнительному прямому поручению владельца29 октября
«явные баги исправлять сразу»: карточка
[android-20261029-storage-move-ownership](../../update-reports/entries/android-20261029-storage-move-ownership.md).
В существующем MOB-02 выявлен обход operation exclusion в legacy storage move
и Reset Settings; baseline фактически дал5 FAIL/1 штатный PASS без GIS/БД/device.
Оба входа исправляются до изменения preferences и сохраняют владельца через
асинхронное завершение/перепривязку. RESULT_OK здесь refresh, не move ACK.
Это адресная source-доработка по поручению владельца; прежняя граница docs-only
выше относится к исходному entry bridge и не отменяет новое поручение.
Приоритет продуктовых функций и native/Related/Full/публикация не возобновляются.
Состояние — source-реализация завершена:21 actual-body JVM PASS, LisaDebug и
BelkaRelease Java/Kotlin compile PASS, независимый разбор CLEAR; Git/docs closure
продолжается. Merge, поставка библиотек и device-приёмка отмечаются отдельно. Политика миграции
11 stores, disk/restart и renderer drain остаётся в едином LISA audit TODO.

## MOB-01 Завершение текущего пакета стилей и треков

29 октября: внеплановая регрессия по сообщению владельца после обычной быстрой
MOB-04 сборки — field lines/points потеряли приоритет над треками. Приоритет P1,
владельцы maplib (planner/current alias/cached wrappers/canonical model order)
и app (native consumer/маршрутизация), зависимость от текущего SQLite native
route и прежних cache/queue оптимизаций. Обычная очередь остаётся отложенной.
Объём: вернуть whole-group stack без изменения данных/sync/фона/батареи,
источники alias брать у текущего style controller, относительный порядок —
у canonical flattened LayerGroup независимо от bind/hot-add arrival.
Миграции saved state нет. Четыре standalone rebind пути также восстанавливают
порядок. Первые81 JVM/20 Python PASS; первый focused native FAIL выявил реальный
reverse-source-cache relative-order defect, oracle не ослаблялся. После canonical
traversal fix13 planner unit и focused native case1/1 PASS на T40/API30
(`priority-related-t40-02`), затем весь класс10 distinct методов PASS на той же
source fingerprint (`sqlite-related-t40-03/result.json`). Exact final83 JVM/
10classes PASS, 0fail/error/skip. Обычные LisaDebug/LisaRelease/BelkaRelease PASS71с,
version matrix PASS. Lisa224/3.1.2.30 SHA256
`57a495854c9c18a5265bf42074101b9dd6d5750ad0fe5e3d6e606c5ef10984bd`
установлен поверх T40 с прежней подписью и сохранением данных; profiler/verification
выключены, profileable отсутствует. Runtime fingerprint неизменен до/после сборки;
карта14.2 восстановлена и screenshot просмотрен. Это normal smoke, не приёмка
полноты/пикселей реального проекта787 или Full. T40 не
поддержал `logcat --uid`, UID collector остаётся diagnostic gap. Nested groups
и Activity recreation в новом root-child case не приняты.
Критерии: current aliases/provider/source/wrapper/filter identity, полный набор
body/Firefly/icon/labels/SQL originals, все saved/current track passes, canonical
relative order и pixels после full/lite/reorder/style/data/hide/Undo. Отдельно
проверить nested/recreation и полноту реальной карты; Full и публикация отложены.
Новые карточки: android/maplib-20261029-sqlite-layer-display-priority,
android-20261029-sqlite-priority-native-regression. Library Merge Commit перед
consumer pin/app Squash; локальный build и device result не означают merge/release.

Приоритет P1. Состояние: app [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78), [#79](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/79), [#80](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/80), [maplib #60](https://github.com/GeonicalSys/android_maplib/pull/60) и [maplibui #44](https://github.com/GeonicalSys/android_maplibui/pull/44) слиты; consumer pins подтверждены. ЛИСА3.1.2.30/224 опубликована27 октября по явному пользовательскому исключению без зелёного Full планшета; versioned/latest HTTP-хэши проверены. Адресные проверки и ограничения записаны в [квитанции выпуска](../reference/mobile-release-fixes-2026-10-26.md). Владелец — app с зависимостями maplib/maplibui. Это учёт текущей работы, а не новое поручение на её исполнение.

25 октября пользователь дополнительно потребовал общий tap chooser для всех
пересекающихся треков/векторов и приоритет «Полевые линейные» под точками.
Внеплановая причина — обнаруженный конфликт выбора перед выпуском. Эти два
изменения включены в MOB-01/UX-03; адресные проверки пройдены, Full выполняется
после них. Приоритет прочих задач не меняется.

26 октября в эту же приёмку включены test-only ожидания стабильного окна/сети
и полный сбор независимых native failures. Причина — непостоянные сбои
обязательного Full до достижения остальных классов. Production sync/constraints
не меняются; адресные результаты и открытые причины записаны в квитанции Full.

Итог фиксированного набора после67f0125: Full09 смартфон+AVD253/253 PASS;
после пользовательского отключения VPN Full02 планшета+AVD завершил все253
метода с249 PASS/4 FAIL. Три ошибки тестового API30 кода, unresolved azimuth
word-wrap и отдельный CI TrackUpload assertion требуют разбора; MVT-01
имеет подтверждённый offline блокер. В этой проверке код/набор не менялся,
исправления и выпуск не объявлены. [Полная квитанция](../reference/mobile-full-2026-10-25.md).

26 октября пользователь поручил устранить эти ошибки и довести пакет до
публикации ЛИСЫ. Работа продолжается в тех же PR: исправление API30 проверок,
разбор переноса инструкции азимута и TrackUpload CI, восстановление локальных
тайлов без сети (MVT-01/REL-03/UX-03). Перед выпуском обязательны закрытие
зависимостей и новый Full на окончательном коде смартфона и планшета.
Смартфон пользователь подключит позже; пока разрешены планшет и owned AVD.
Публикация БЕЛКИ и отдельная переделка загрузки/тайлов сюда не входят.

Library closure26 октября: maplib PR60 MERGED61a0a602, maplibui PR44
MERGEDc3253726; original head ancestry и tree equality подтверждены после
fetch, consumer pins обновлены. App PR78/CI и новые Full254 остаются открытыми.

По следующему поручению пользователя автоматические Android/docs Actions
переносятся на ноутбук ради лимитов GitHub. Cloud остаётся manual-only;
local Full получает недостающие6 real-window profiles и строгий docs/report
guard. Набор native254 не сокращается. API30 word-fit/reminders3/3 PASS,
TrackUpload verification9/9 PASS. Merge и выпуск принимаются по локальным
проверкам; облачный запуск без отдельного запроса не является условием.

После app merge769629c первый Full10 остановился до native: локальный docs
wrapper передал --enforce-diff без изменённых путей на уже чистой target ветке.
Исправление этого helper и адресная clean/dirty regression слиты в PR79/d60a768.
После этого Full11 смартфон+AVD прошёл254/254. Tablet Full12 отменён до native,
Full13 остановлен на host ADB, Full14 завершён с тремя native-fixture failures.
27 октября MOB-01/REL-03 продолжаются test-only follow-up: main-thread lifecycle
Stage, согласованная camera и OnDismiss/input witnesses. Набор254 и production
поведение сохраняются. Related20 прошёл27 emulator/11 tablet, после чистой
установки verification Related22/23 прошли ещё9 tablet cases. Full21 остановлен
на Android process attach, Full24 незавершён.27 октября пользователь разрешил
выпуск ЛИСЫ224 без зелёного планшета. Рабочий код равен зелёному Phone Full11,
но текущий автоматический fingerprint gate не пройден. Общая политика следующих
выпусков не ослаблена; полные ограничения и доставка находятся в квитанции.

## MVT-01 Корректность и актуальность

Приоритет P1. Состояние: Исследуется. Основание: риск в исходниках.

26 октября на source67f0125 отдельно подтверждён выпускной блокер: исходный
native MVT fallback case проходит при наличии active network и не загружает
локальный VectorSource при wifi/data off. SDK откладывает HTTP loopback вместе
с внешней сетью. Причастность обновления SDK не установлена; исправление ещё
не внесено, переделка загрузки не начиналась. Это дополнение к MOB-01/REL-03,
а не подтверждение следующего риска инвалидации. [Доказательства и состояние
приёмки](../reference/mobile-full-2026-10-25.md#подтверждённый-дефект-локальные-mvt-без-внешней-сети).

Карта и регламент включены в текущий Draft PR #78 по явному решению владельца. MOB-00 находится в PR; после слияния нужно отдельно подтвердить, что стартовые инструкции, чеклист и шаблон PR доступны из целевой ветки. Автоматическая блокировка PR не включалась.

# Источники и границы оценки

Исходные направления: [обсуждение офлайн обмена](https://chatgpt.com/share/6ade3bcc-7f68-83eb-81ff-872ae76c43a0), обсуждение «Оптимизация синхронизации приложения» (Codex chat ID `01a13e76-cfab-7a82-a71d-a74c05b3fc0f`, ссылка доступна в обзорной странице), запрос оптимизации векторных тайлов и дополнение об удобстве.

Проверены документация и исходники app 8cc630ad, maplib 7e7055e5, maplibui ff4a1cce с учётом незавершённых изменений checkout на 25 октября 2026 года. Точные ссылки на неизменённые участки приведены возле выводов. Это анализ для планирования; новые device, серверные и нагрузочные прогоны в данной работе не выполнялись.

Дополнительная навигация: [архитектура синхронизации](https://github.com/GeonicalSys/nextgis-mobile-custom/blob/8cc630ad2abd04d49470ea54715f7f69f9d32618/docs/architecture/ngw-sync-and-storage.md), [аудит надёжности](https://github.com/GeonicalSys/nextgis-mobile-custom/blob/8cc630ad2abd04d49470ea54715f7f69f9d32618/docs/reference/mobile-reliability-audit.md), [карта Collector](https://github.com/GeonicalSys/nextgis-mobile-custom/blob/8cc630ad2abd04d49470ea54715f7f69f9d32618/docs/roadmap/collector.md). Старые отчёты подтверждают только своё время и объём проверки.

Публичная224 отозвана по отдельному поручению,223 восстановлена и проверена.
Ручной диагностический APK отдельно разрешён пользователем, публикация запрещена.
Причина регрессии13.0.2→13.7.1/новые стили пока не локализована. Обычные задачи
очереди отложены на время инцидента; это не новый автономный roadmap run.

После сообщения о трёх host hard hangs AVD были остановлены. Позднее27 октября
владелец разрешил software-only продолжение, аппаратный режим запретил.
Карточка android-20261027-emulator-host-safety: software-only runner,
явный admission и запрет actual host/unknown GLES. Проверка нового маршрута
source/unit PASS; новый реальный boot подтвердил actual SwiftShader. Итог
native проверки фиксируется отдельно. Software-related01:60 actual pan,
20 actual zoom changes,2 recreate, но FAIL по отсутствию конечной MVT отрисовки.
Следующий Full оборвался при AVD startup после ещё одного hard hang; владелец
отключил андервольтинг и явно поручил продолжить в software-only режиме.
Это мера изоляции риска,
а не установленная причина Windows hang или мобильного224 crash.

## Продолжение MOB-02: подтверждение journal — 2026-10-30

В том же существующем MOB-02 по прямому поручению владельца29 октября
исправлять явные баги выявлен failed ACK, незаметно включавшийся в следующую
успешную запись очереди. Карточка [android-20261030-sync-journal-confirmation](../../update-reports/entries/android-20261030-sync-journal-confirmation.md).
Адресная source-реализация защищает согласованный queue/legacy cohort внутри
процесса; product priorities и native/Related/Full/публикация не возобновляются.
Git delivery, source/JVM checks, merge и device/restart verification разделены
в карточке. Исходный docs-only entry bridge и исторические результаты выше
сохранены; вопросы post-ACK cleanup и restart остаются в прежнем LISA ACK TODO.

### Продолжение MOB-02: значения формы NGFP — 2026-10-30

Сохранены11 source-backed связей от nullable SQL/NGFP/catalog/private resolver/publisher через Collector first-form selection к remote Field metadata, restored pair и Save. Default Прочие / прочие действующий; нарушение не установлено. UI-FORM-TEXT-LINE-CAP отделяет requested lines от текста и resource IDs. Интегрированный docs-only каталог39/134/17 сохраняет прежние38/128/14 и clock F61; проверки текущих docs ещё NOT_RUN, существующий clock drift не скрывается. Четыре root NGFP TODO принадлежат единой очереди; native/UI/data policy не возобновляется.

### Продолжение MOB-02: ключи lookup-кеша и источник цели — 2026-10-30

В собственном maplib устранён NPE при равном числе пар с другим набором ключей;
новая внутренняя App карточка android-20261030-lookup-cache-regression сохраняет
9 actual-body JVM и точную offline recipe, библиотечная user-карточка
maplib-20261030-lookup-cache-keys объясняет поведение. GREEN9 и BelkaRelease37с
относятся к live8ca, не к поставке с committed App F61. Clock19/journal22/storage21
не перезапускались; native/NGW/restart и savefalse policy не приняты.

Отдельная internal карточка android-20261030-stakeout-target-contract сохраняет
source-only цепочку selected geometry/copy/3857 nearest facet/4326 inverse и
Bundle ownership. UI-каталог39/142/19 дополняет старые134/17 без repin F61,
product math/lifecycle не меняется. Существующий FID TODO и новый общий
AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE остаются открыты. Docs/guard проверки
после интеграции ещё NOT_RUN; новая App или параллельная root-очередь не создаётся.


### Продолжение MOB-02: prefetch без расширения удаления — 30 октября2026

По тому же поручению исправлять явные source-баги подготовлена отдельная maplibui
API для двух prefetch callers: nested/orientation bindings и per-call dedup. Старые17
строк public reader и весь deletion file сохранены; account cast ждёт backup/owner policy.
Новые карточки maplibui-20261030-ngfp-lookup-prefetch и android-20261030-ngfp-lookup-prefetch-regression
разделяют user behavior и12 technical JVM. Baseline3FAIL/9PASS →12GREEN2.149с/49с,
Belka0/41с; classifier/sequence NOT_PASS и postproof/fail-fast recovery явно сохранены,
allgatesPASS нет. Старые9/19/22/21/desktop1117/1192 не перезапускались. UI39/142/19/F61
и parent UI E66 сохранены; library Merge Commit до App pointer, без APK/device stage.
Новые metadata official validators после C-интеграции NOT_RUN; shared-cache/hash
retry/durability/account safety остаются в единой Root очереди.


### Продолжение MOB-02: допуск полного pull и save(false) —30 октября2026

В том же поручении исправлены только два proven source omission: явный206 на
no-Range/no-combination full downloader и отсутствие IO сигнала от двух save(false).
Shared19 (Core4+App15) прошёл после4 named assertion RED; old9 включён исторически,
не суммируется с15. Belka source compile0/39с, exact100 set/orderdifferent,7PIDstop.
Первый dry capture UNKNOWN/NOT_PASS сохранён; recovery dry и единственный actual
compile не превращают старую попытку в PASS. Новые3 карточки разделяют Core206,
Core save-signal и App internal15; полная recipe в Core PR63.

Disk/retry риск26, same-hash cache readiness/shared deletion, account backup27 и
partial200/tracked/initial-import полнота остаются в единой Root очереди. UI39/142/19/
F61 не меняется. Старые clock19/journal22/storage21/prefetch12 и desktop1117/1192 не
запускались. Device/Related/Full/APK/release и новые metadata checks NOT_RUN;
изменение установленного приложения появится после обычного выпуска. Новая
roadmap priority или параллельная очередь не создаётся.


### MOB-02 / MOB-UI-01 — сообщения об отказах, source checkpoint30 октября

По прямому поручению владельца исправляется потеря причин server failure и
неуместные/отсутствующие сообщения sync, resource selector, layer import и legacy
form list. Работа входит в существующие MOB-02/MOB-UI-01; приоритеты остальных
этапов не меняются. Related/Full/device/AVD не возобновлены. Acceptance различает
положительно известный server-only cause, offline, auth/rights/local/mixed/unknown,
late child/cancel и private diagnostics; текст не обещает серверное обслуживание,
доставку всех правок, disk/restart durability или release.

Scope и technical fixtures описаны в AUTO-NGW-USER-MESSAGES, новых UI message
контрактах и карточке android-20261030-user-facing-errors. Статус здесь source
prepared:89 source/model PASS, Belka source compile0/59с; native/device NOT_RUN.
Linked Draft Core63/UI46 должны предшествовать App pointers/semantic clock review;
App86 остаётся Draft до отдельного merge/release решения.


### MOB-UI-01 — флажки ресурсов ВебГИС, source checkpoint30 октября

По текущему поручению углубления аудита подготовлен bounded selector slice:
ANY-by-localID/mode rendering, symmetric false callback, no-amplification true,
восстановление type1 label при recycling и OR duplicate flags только в owned
JSON restore после каждого прежнего resolve. Source ownership — maplibui;
28 новых +9 protected browser models в App: actual37 PASS; Belka source compile0/BUILD47s; native acceptance отдельно NOT_RUN. Это не refresh/tree policy,
external setter/Parcel normalization или generic import dedup; неизвестные
свидетельства и следующий шаг едины в Root `AUDIT-TODO-NGW-RESOURCE-BROWSER-REFRESH`. Карточки `android-20261030-ngw-resource-checkbox-selection` и
`maplibui-20261030-ngw-resource-checkbox-binding` сохраняют source/model/native границы. Приоритеты других этапов,
device/Related/Full и Wiki не возобновлены. Message89/Belka59 — исторический
срез с другим Adapter, без rerun/пересуммирования. F61 clock/pins ещё held.


## MOB-04: cache lifecycle при импорте и восстановлении — 30 октября2026

Source closure связывает GPX/KML/GeoJSON и initial NGW/Collector/form imports с
готовностью полного spatial index, properties/config reread и persisted old-file
recovery. Cache reset/cardinality и verified-empty create исправляются отдельно
от caption/layout и historical message89/selector37. Core29 фактически исполнен: исходный код —15 assertion FAIL/14 control PASS, после двух исправлений —29 PASS/0 failures/errors/skips; те же пять наборов и все Core test bytes/oracles сохранены. Это23 новые проверки (12 lifecycle +11 RTree) и6 прежних контролей (legacy1, concurrency1, spatial4):16 Robolectric API28 с технической SQLite и13 plain JUnit. Финальный запуск:5 XML/5.653s, Gradle exit0/BUILD40s (наблюдалось42.537s),105 входов неизменны во время запуска,10 собственных процессов завершены и отсутствие подтверждено. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c. Это не55 PASS; App legacy25 PASS/1 path assertion не воспроизвёл обрезание подписей. Device/installed30/real GPX20wpt/NGW import/restart/native map acceptance не выполнялись.
User controls restart/style/toggle/only-manual-rebuild остаются обязательными
независимыми device expectations; installed30 artifact/source identity UNKNOWN,
private GPX20wpt не становится versioned input. Canonical risk
AUDIT-TODO-VECTOR-SPATIAL-CACHE-RECOVERY сохраняется до scoped acceptance. Ни новый
source test, ни совпадение counts не закрывает весь MOB-04/real-data/native.


### MOB-UI-01 — полная строка ресурса, повторное измерение и конец пути

Три caption width=wrap_content сохраняют полный owned RU/EN текст. NgwResourceRowLayout сначала измеряет обычную горизонтальную строку; лишь фактический glyph clipping имени переводит две статические header/mode группы друг под друга. Перед обычным и full-width измерением локально forceLayout для каждого descendant обновляет getLayout до решения о размещении; parent requestLayout/reparent нет. При расширении та же строка возвращается в горизонтальную компоновку. Шрифт, IDs, порядок controls, listeners/CheckState/local-vs-remote identity/JSON/Parcel/import/refresh прежние. Shared breadcrumb layout переносит5dp child margin в5dp padding HorizontalScrollView: API28 clamp учитывает padding, а child margin не учитывает. Визуальный отступ сохранён, конец пути достижим в технической модели.
Фактически5 caption layout methods PASS/0 failures/errors/skips, XML10.06s. Ораклы и whole fixture SHA256 cc1fb961f2459d6b9e71097f403f5898b82aaa2425093b6157ee76edf03d06ac неизменны относительно предыдущего4PASS/1FAIL. Finalized Root receipt SHA256 68f7f6bdc072f9f25fd2af02e0584ac467a4dbb1a0c1b1e1959341b2126630ef. В этом запуске общий cohort10 НЕ PASS: 6 PASS/4 отдельные Panel assertion FAIL, Gradle exit1; 120 входов без drift, все13 owned processes settled absent, наблюдалось110.0625s. Это технические API28 CPU native Paint/text measurements, без Android device/OEM/GPU/display/GIS native/touch/rotation acceptance. Исторический cohort10 checkpoint предшествовал consumer compile. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.
Первый combined55 attempt: Core29 реально исполнился (15 assertion FAIL/14 controls), App0: fixture не скомпилировался из-за missing RobolectricTestRunner import. Старый Checkbox22 XML был STALE и исключён; предварительные51 copied cases не являются51 выполненной проверкой. V3 добавил ровно один импорт. Legacy App26 затем реально25 PASS/1 path FAIL; первые3 caption methods PASS на старом XML, поэтому это НЕ crop RED. NativeV4 class-only CPU glyph/font backend с setup calibration воспроизвёл4 assertion FAIL на старом XML. После3 caption width attrs Native4 дал2 PASS/2 assertion FAIL: имя Collector при280dp/font2RU и конец пути. Static responsive groups и перенос5dp margin→scroll padding дали4 PASS/1 transition FAIL в frozen5 fixture. Новая локальная инвалидация descendant measure cache проверяется теми же5: fixturecc1f, все assertions/setup неизменны. Core29 PASS относится к отдельной cache-проверке, combined55 PASS не заявляется. Исторические37/89 и их source pins не перезапускались.
Полная owned copy и CPU glyph bounds не принимают фактическое число доступных строк, touch targets/scroll/OEM/font/theme/rotation/display-size/installed APK/phone performance. font1.3 и реальные orientation/display combinations не исполнены. Единственный narrow Root risk AUDIT-TODO-MOBILE-RESOURCE-ROW-LAYOUT; broader UI audit продолжается, не all-screens PASS. F61 clock/Wiki/pointer blockers прежние.
Карточки `maplibui-20261030-resource-caption-layout` / `android-20261030-resource-caption-layout-regression` отделены от selector37.


### MOB-UI-01 — поделиться выбранным треком

Прямое поручение владельца добавляет Share к существующей нижней track panel
до отложенного merge/repo_load. Используется общий exporter, исправляются два
source-confirmed дефекта XMLname/raw filename. Работа не создаёт новую очередь
или roadmap priority; source/model результаты записываются отдельно после факта.
Cache/concurrency/snapshot/lifetime ведутся в Root `AUDIT-TODO-MOBILE-TRACK-GPX-EXPORT-OWNERSHIP`.
Исторические89/37 и cache29/caption checks не перезапускаются и не пересуммируются.
Device/Related/Full/AVD/Office/PG/Wiki не возобновляются.


Actual acceptance is compositional: Binding8 andExporter10 all18PASS inearlierfirstgreen23 on637a; itsfourPaneloverlapfailures werekept. Diagnostic-only64ac changedfailuremessage/helper only, notfivepredicates/oracles/setup.31Octactualcohort10 hadCaption5PASS+Panel1PASS4FAIL andexit1, NOTwhole10PASS; observedRename/Visibilityrowoverflow3px. Oneproductionline disablesbaselinealignment onlyownedactionRows. FinalONLYsamePanel5/64ac on0069 PASS0fail/errors/skips, XML19.913s, Gradle0/BUILD1m17s,81.6374157sobserved,120unchangedinputs/all13ownedsettledabsent. Thus23distinctmethodsacceptedACROSS18+5batches; noonefinal23rerun, no18/Caption5rerun, noMIME2executionclaim. Финальная consumer source compile Belka: Gradle exit0, BUILD SUCCESSFUL in 1m 23s, 86.8108238s observed, 100 source-only tasks,120 inputs unchanged/all12 owned processes naturally stopped. Java/Kotlin source compilation; no APK/AAB/AAR/dex/NDK/install/publish or native Android/GPU/device acceptance. Receipt Q:\Codex\Scratch\lisa-ecosystem-depth-audit-20261029\evidence\resume-20261031-source-checkpoint\belka-resume-compile\final-result-source-stop.json SHA256 be7f54baa369662c2cd44a509c745fbc099f357a0df91c9a30a7d69a12008d1c.; CPUfont/JVM isnotnativeAndroid/device/OEM/wholewindow/GPU/recipientacceptance.

Baseline23=18assertFAIL5controls/0errors/skips:13missingShareID assertionsacrossBinding8/Panel5 arefeatureabsence, NOTproofoldstalelaunchbugs; Exporter5XML/pathcounterexamples+5controls independentlydemonstrateactualdefects. Firstgreen23=19PASS4PaneloverlapFAIL, XML11.393s,Gradle1; nofullPASS. DiagnosticV3 9ec/523f LFops/CRLFcandidate rejectedBEFOREC/runtime byRootguard; retainedinvalidhistory. CorrectedRAWV4 c093/64ac exactreplay/inverse78eff, Bridge73edf; onlymessage/helper changed. Diagnostic10 captured3pxbaselineoffset, one-line637a→0069 preservesIDs/callbacks/copy/widthlogic/all5oracles; finalPanel5accepted. Oldmessage89/selector37/cache29/caption5 resultsremain separatehistoricalcohorts, notrerun/resummed.


## MOB-SYNC-01 Подтверждённые source fixes: вложения и class metadata

Текущий порядок задан явным поручением владельца01.11: завершить начатые audit
blocks и исправить подтверждённые ошибки прежде новых audit frontiers. Это
адресная доработка, не автоматическое возобновление paused native stages.

Состояние: implementation/source regression подготовлены в отдельных library
branches; commits/связанные Draft PR и merge фиксируются по фактической доставке.
App87 пока сохраняет прежние library gitlinks, поэтому не объявляется исправленным.

- Attachment failure state: sole404 завершает matching work marker; request-owned
  staging публикуется после завершённого200/close через atomic move без copy
  fallback. Failure сохраняет прежний final и соседние файлы. Одинаковый final
  fixture: old source7 assertion FAIL/0ERROR/0skip → fixed7PASS/0/0/0, XML2.703s.
- Class-only schema metadata: известная смена vector/PostGIS class не должна
  теряться при совпадающих geometry/SQLite fields. Отдельный owning maplib
  пакет: 14GREEN (11новых+3прежних); runtime/currenttenant не доказывается.
- MOB-UI-01 сохраняет source meaning: добавлен attachment UI contract, а прежний
  typed HTTP fetch guard уточнён до двух неизменных overload с сохранением всей
  старой whole-file evidence в history. Existing expectations/checker не менялись.

Доставка: library Merge Commit → fetch удалённых merge commits → App gitlink
update → compile/адресные проверки по фактическим pointers → отдельно разрешённая
device/native acceptance. Незамерженный локальный pin не заменяет этот порядок.
Library PR/commit references и App adoption обновляются по завершённым шагам;
наличие Draft/компиляции live checkout не означает APK, merge или выпуск.

Границы: без NGW/customer API, реальных фото/данных, Office, DB, GUI/AVD/device
проверок. Legacy positive-size partial может по-прежнему пройти heuristic
PhotoPicker; автоматическая миграция/удаление cache не добавлена. Silent-truncated
200 integrity, checksum/Content-Length и concurrent preview/same-final ownership
остаются отдельными неизвестностями. Влияние на фон/батарею не измерялось; новых
scheduler/lease/cancel policy или task counters не вводится. Следующие source
frontiers не начинаются до закрытия этого started fixes checkpoint.

## MOB-UI-01 — Ясные названия мобильных проектов, 2026-11-01

По прямому уточнению владельца работа ведётся в нашем мобильном приложении,
отдельное приложение Collector не используется. EN/RU подписи списка и сообщений
называют ресурс мобильным проектом; server API `collector_project`, client IDs,
keys, placeholders и логика остаются прежними. Новый узкий
`UI-MOBILE-PROJECT-LOAD` фиксирует уже существующий маршрут «Загрузить проект»:
account/catalog `lisa` → выбранный remote ID → NGW GET → own workspace/fill,
отдельно от chooser сохранённых рабочих областей.

Владелец — Android root для App copy/каталога; maplibui владеет общими сообщениями.
Это текущий MOB-UI-01 и явное уточнение UI, без новой очереди/приоритета и без
изменения data/sync/map/background/battery поведения. Приёмка: правильные
семантические bindings, прежние keys/placeholder cohorts, XML/resource и docs
проверки; сборка ресурса не доказывает читаемость окна. Device/font/rotation,
NGW/customer, native/GUI и выпуск NOT_RUN; фактические проверки и Git-доставка
отмечаются в карточке `android-20261101-mobile-project-labels` и own Draft87.

# Исходные формулировки до пересмотра 13.11.2026

Исторический снимок изменённых описаний. Он сохраняет требования и основания прежнего плана; актуальные статусы и оставшаяся работа находятся в [текущей карте](mobile.md). Эти цитаты не дают новых полномочий на запуск или выпуск.

## mobile-roadmap-roadmap-overview-and-authority

> Этот файл — основной источник текущих статусов и решений. [Обзорная страница](https://chatgpt.com/space/page_15a75af7f3608191a47d98024e3fda7e) сохраняет продуктовый контекст; при расхождении актуализируется по этому файлу. Не вести две независимые очереди.
>

## mobile-roadmap-queue-order

> # Очередность и состояния
>
> | Этап | Задачи | Результат перехода |
> |---|---|---|
> | Основа | MOB-00, REL-01, REL-02, PERF-01; текущий MOB-01 учитывается отдельно | Правки не теряются молча, есть путь восстановления и измеримый исходный уровень |
> | Первые улучшения | SYNC-01, MVT-01, UX-01, UX-02 | Неизменённые PostgreSQL слои пропускаются безопасно; карта обновляется; пользователь понимает состояние данных |
> | Производительность | SYNC-02, MVT-02, REL-03 | Передаются изменения; тайлы и длинные операции укладываются в измеренные бюджеты |
> | Проверка офлайн обмена | P2P-01, P2P-02 | Два доверенных устройства обмениваются без интернета и без потери/дублирования операций |
> | Распространение через другие устройства | P2P-03 | Повторные встречи и маршруты A → B → C → сервер сходятся к согласованным данным |
> | Развитие полевой работы | GEO-01, UX-03, UX-04, EXT-01 | Более понятные измерения и меньше действий в повседневных сценариях |
>
> Этапы задают зависимости, а не календарные обещания. MVT и небольшие UX задачи могут идти параллельно серверной синхронизации. P2P использует общий контракт операций; прототип транспорта можно исследовать раньше, но нельзя выдавать его за готовую синхронизацию.
>

## mobile-roadmap-started-work-heading

> # Уже начатая работа
>

## mobile-roadmap-reliable-server-ack

> ## REL-01 Достоверное подтверждение отправки
>
> Приоритет P0. Состояние: Исследуется. Основание: подтверждено кодом. Владельцы реализации — maplib, интеграция app/maplibui.
>
> В NGWVectorLayer успешный HTTP ответ создания с JSON без поля id возвращается как успех; вызывающий код после этого удаляет NEW из очереди. При пустом cursor для создания/изменения также действует политика «обработано». Это требует исправления и адресной проверки; потеря данных на конкретном телефоне этим аудитом не установлена.
>
> Приёмка: отсутствующий, неверный и недопустимый server ID не очищает правку; remap локального ID и подтверждение очереди согласованы. Временная ошибка чтения, действительно осиротевшая запись и повреждение БД различаются; неопределённые операции сохраняются для восстановления, обычная очередь продолжает работу.
>
> Отдельно проверить «сервер принял POST, ответ потерялся» и смерть процесса между ответом и локальной фиксацией. Пока backend не даёт подходящей поддержки, нельзя обещать однократное создание при любых повторах. Нужен явный способ сверки неопределённого результата.
>
> Основание: [создание и ACK](https://github.com/GeonicalSys/android_maplib/blob/7e7055e54002b116e7a147fcd2791486913ddd9a/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java#L3883), [очистка очереди](https://github.com/GeonicalSys/android_maplib/blob/7e7055e54002b116e7a147fcd2791486913ddd9a/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java#L1357).
>

## mobile-roadmap-project-recovery-and-draft-preservation

> ## REL-02 Восстановление проекта и сохранение повреждённых черновиков
>
> Приоритет P0 для сохранения оригинала черновика, P1 для полного восстановления. Состояние: Исследуется. Основание: подтверждено кодом и действующим описанием backup.
>
> FeatureFormDraftStore очищает повреждённый/некорректный черновик при чтении. Сначала нужно сохранять исходные байты в карантин и давать повторную попытку или экспорт. Нельзя обещать автоматическое восстановление любого повреждённого содержимого.
>
> LayerBackup format2 уже сохраняет данные слоя и вложения, но не является полным архивом проекта: config/forms/assets включены не полностью, готового универсального restore нет. Нужны проверяемый формат полного архива, совместимость схемы и мастер восстановления на отдельной копии.
>
> Приёмка: проверить архив до destructive действия; восстановить проект офлайн с объектами, геометрией, фото, формами, настройками и несинхронизированными правками. Сравнить ID, значения и хеши вложений. Чужая identity, повреждённый архив, нехватка места и несовместимая версия отклоняются без изменения рабочей копии. Восстановленная очередь не отправляется на сервер до согласованной сверки.
>
> Основание: [FeatureFormDraftStore](https://github.com/GeonicalSys/android_maplibui/blob/ff4a1cce63d11fea864843550173ec22a508d334/src/main/java/com/nextgis/maplibui/util/FeatureFormDraftStore.java#L115), [границы восстановления](https://github.com/GeonicalSys/nextgis-mobile-custom/blob/8cc630ad2abd04d49470ea54715f7f69f9d32618/docs/runbooks/incident-and-rollback.md#L41).
>

## mobile-roadmap-sync-and-changes-center

> ## UX-02 Центр изменений и синхронизации
>
> Приоритет P1. Состояние: Предложена. Использует существующие очередь и статусы.
>
> Показать не только число слоёв с правками, а сами ожидающие объекты/вложения: «сохранено на устройстве», «ожидает отправки», «нужна сверка», «подтверждено сервером». Для сбоя — причина и одна подходящая команда. В P2P добавить отдельный этап доставки через другое устройство.
>
> Приёмка: пользователь может найти конкретную неотправленную правку, понять её судьбу и безопасно повторить. Частичная синхронизация не выглядит полным успехом; повтор не требует заново открывать и пересохранять форму. Простой список выбора проекта остаётся простым — подробности открываются отдельно.
>

## mobile-roadmap-release-observability

> ## MOB-REL-03 Обязательная статистика и отчёты в production APK
>
> Приоритет P1. Состояние: Предложена; согласованный план записан по поручению владельца 12.11.2026. Реализация не начата. Требование владельца: статистика и отчёты об ошибках должны быть включены в production ЛИСЫ и БЕЛКИ, а публикация обязана это проверять.
>
> Основание: при сборке опубликованной 3.1.2.31/225 из отдельной копии проекта не перенесли приватный `usage.properties`. При исправлении входа в 3.1.2.32/226 отсутствие статистики ошибочно приняли за правильное прежнее состояние: файл намеренно пропустили, а проверка требовала пустых `USAGE_ENDPOINT` и `USAGE_UPLOAD_TOKEN`. Оба опубликованных выпуска имеют пустые параметры; рабочая локальная 225 содержит заполненные. На подключённом Samsung APK 226 подтверждён по ADB и SHA-256; сервер сохраняет версию 225 из последнего события перед обновлением. Настройки в основном проекте сохранились. MOB-REL-02 и AUTH-01 сохраняют собственные статусы и область работ.
>
> Владельцы будущей реализации: App — обязательная конфигурация Release, инициализация и доставка; Publisher (`android_projects/upload_mobile`) — независимая проверка подписанного APK и публичной выдачи; серверы статистики, GlitchTip и диагностических вложений — подтверждение получения. Изменения этих компонентов, библиотек и регламентов определяются при реализации по фактическим зависимостям.
>
> План работ:
>
> 1. **Обязательная конфигурация production.** Сборка ЛИСЫ и БЕЛКИ должна останавливаться при отсутствии или некорректности адреса статистики, токена отправки, настроек GlitchTip либо необходимых настроек диагностических вложений. Исключить пустые значения по умолчанию и обход этого требования через профиль тестовой сборки. Debug и изолированные автотесты сохраняют отдельную конфигурацию и не отправляют тестовые события в production.
> 2. **Подготовка отдельной сборочной копии.** Ввести единый перечень обязательных приватных входов и проверять полноту их переноса из доверенной конфигурации до сборки. Отсутствие настройки в предыдущем APK не является основанием отключать функцию. Секреты не коммитить и не выводить в журналы; фиксировать только безопасные результаты сверки и происхождение входов.
> 3. **Независимый запрет публикации.** Штатный издатель проверяет именно готовый подписанный APK: эффективная конфигурация статистики и отчётов включена, адреса и проекты соответствуют доверенным настройкам, параметры заполнены, отключающие флаги отсутствуют. Ожидаемые значения задаются независимо от APK. Контроль обязателен при dry-run и реальной публикации каждого бренда; чтение исходного файла настроек само по себе проверкой APK не считается. Существующие проверки NextGIS ID, версии, бренда и подписи сохраняются.
> 4. **Привязка к точному файлу.** Сохранять SHA-256, версию, бренд и результаты проверок без секретов. Изменение APK после проверки блокирует загрузку. После публикации сверять серверный APK, текущий канал и манифест с проверенным файлом; результат одного бренда не подменяет проверку другого.
> 5. **Фактическая доставка.** В отдельном тестовом окружении подтвердить поступление события статистики с новой версией и диагностического отчёта, включая предусмотренные вложения. Проверить сохранение очередей без сети и отправку после её восстановления. Результат связать с проверяемой сборкой; использовать синтетические данные и не изменять рабочие данные смартфона. Проверка включённости в APK и подтверждение доставки учитываются отдельно.
> 6. **Защита от повторения.** Добавить отрицательные проверки: отсутствующие файлы, пустые или некорректные параметры, неправильный сервер или проект, отключение через тестовые настройки, подмена APK после проверки. Каждый случай должен блокировать сборку или выпуск на соответствующем этапе. Положительные проверки выполнить отдельно для подписанных ЛИСЫ и БЕЛКИ; отсутствие проверки не обозначать PASS.
> 7. **Исправленный выпуск.** Подготовить новый номер версии и возрастающий versionCode после текущего опубликованного выпуска, не заменяя содержимое 225/226. При обычном обновлении сохранить пользовательские настройки, проекты и очереди. Подтвердить отправку новой версии со смартфона и её появление в статистике. Требуемые локальные проверки и отдельное разрешение на публикацию сохраняются; прежний пропуск тестов выпуска 226 на новую задачу не переносится.
>
> Критерий завершения будущей реализации: штатно опубликовать APK с выключенной статистикой или отчётами невозможно; включённость и доставка подтверждены отдельно для ЛИСЫ и БЕЛКИ, а публичные файлы совпадают с проверенными. Требование относится к допуску выпуска: временный сетевой сбой у пользователя сохраняет очереди и не блокирует обычную работу приложения. Объём собираемых данных и действующие ограничения приватности не расширяются.
>
> Границы текущей записи: сохранён только план. Код, работающие правила сборки и публикации, приватная конфигурация, серверы и установленные приложения не изменены; новая сборка, проверки на устройствах, merge и публикация не выполнены.
>

## mobile-roadmap-release-225

> Приоритет P1, прямое поручение владельца1 ноября2026: повысить версию и
> подготовить приложение к публикации с подключённым смартфоном. Владелец
> отдельно разрешил программный эмулятор локально; GitHub Actions запрещены.
> Это подготовка выпуска поверх уже слитых MOB-02/04, MOB-UI-01 и MOB-SYNC-01,
> без изменения очереди продуктовых доработок.
>
> Владельцы: maplib (release diagnostic version) → app (production metadata,
> независимая APK matrix, release notes и проверки) → upload_mobile (dry-run
> после приёмки). Data/schema/sync API/фоновая политика и батарея bump-ом не
> меняются; пользовательское состояние должно сохраняться при обычном обновлении.
>
> Приёмка: закрытая матрица исходников и merged pins, обе release flavors и
> Lisa Debug с согласованной maplib version, ожидаемая release подпись,
> локальные Full и Check-Publication, затем publisher tests/dry-run.
> Для Full требуется нагрузка на физическом смартфоне и другом физическом планшете.
> 10 ноября владелец поручил публикацию после прохождения выпускных проверок.
> Состояние и доказательства:
> [подготовка выпуска](../reference/mobile-release-3.1.2.31-2026-11-01.md).
> Карточки: android-20261101-release-31231, maplib-20261101-release-31231.
>

## mobile-roadmap-release-ngid-226

> ## MOB-REL-02 Восстановление входа в ЛИСЕ и БЕЛКЕ
>
> Приоритет P1. Состояние: В работе по прямому поручению владельца. Корректирующий выпуск `3.1.2.32` / `226`.
>
> Публичные APK 225 передавали `client_id=null` из-за отсутствующего `client.id` в отдельной рабочей копии. В этом выпуске проверяется входной параметр сборки и фактический запрос внутри каждого подписанного APK, который будет опубликован.
>
> Владельцы: App — допуск сборки и версия; Maplib — связанная диагностическая версия; Publisher — проверка подписанного APK и выдачи сервера. AUTH-01 остаётся отдельной задачей диагностики.
>
> Приёмка: обе стабильные сборки имеют ожидаемые версию, подпись и NextGIS ID; прежний APK 225 не проходит новый контроль; публичные manifest и APK совпадают с проверенными файлами. Тестовые прогоны 226 пропущены по прямому поручению владельца; общее правило Full для будущих выпусков не меняется.
>

## mobile-roadmap-agent-knowledge

> ## MOB-DEV-02 Типизированная система знаний агентов
>
> Прямое поручение владельца02–03.11: перестроить ВСЮ систему знаний шести
> репозиториев, сохранив читаемые owner-local Git records как редактируемый
> источник и производную SQLite вне Git. Доработка охватывает знания, входы
> клиентов и закрепление проверенных версий библиотек для чтения их записей.
> Связанный общий план: `C:/dev/lisa/agent-system/migration-plan.md`.
>
> Цель ACTIVE/не завершена. Реализован перенос шести полных instruction sources
> в132 логических значения/134 проекции плюс три явных вопроса миграции.
> Проверка исходных символов/условий, независимых ожидаемых context IDs/полей и
> полной выдачи ведётся отдельно от слияния, запуска на устройстве и выпуска.
> Рабочий вход App направляет Codex и Cursor к общему читателю ЛИСА.
>
> В Draft App #91 переключены все девять YAML-реестров центральной документации.
> Локальная проверка и ручной workflow используют общий валидатор с записями
> владельца. Scaffolder использует те же записи состава. Производные YAML
> сохраняют автономный вход для документации. Каталог UI использует записи App
> и совместимое представление JSON. Точка входа и чеклист изменений читаются
> из записей App и доступны как Markdown по прежним адресам. Инвентаризация
> разделяет остальные документы, исторические материалы и точки входа клиентов.
> Оглавления документации и реестров, руководство по сопровождению и пять
> пользовательских руководств используют тот же маршрут. К ним добавлены
> безопасные паттерны, глоссарий и справочники сборки, совместимости,
> flavors и настроек. Сравнение с official собирается из отдельных тем
> и датированных свидетельств по прежнему Markdown-адресу. Архитектура
> синхронизации и хранения использует тот же порядок редактирования.
> App закрепляет три полученных с GitHub Merge Commit. Их деревья совпадают
> с проверенными версиями EasyPicker #7, Maplib #68 и Maplibui #50.
> Приёмка ВСЕЙ цели: полный migration map/lifecycle, фактические Codex/ChatGPT/
> Cursor/custom clients, authoring/loading/limits/agreement и индивидуальная
> очистка. Новый source checkpoint или Draft не закрывает цель. Native/AVD/device,
> cloud, merge и публикация этим пунктом не запускаются.
>

## mobile-roadmap-ui-knowledge

> ## MOB-UI-01 Проверяемые знания об интерфейсе и источниках значений
>
> Приоритет P1. Состояние: source/docs review в собственной ветке, runtime NOT_RUN.
> Основание — прямое поручение29.10 после слияния текущих веток: агент принял
> GPS-координаты нижней панели за центр проекта. Владелец — Android root для
> UI-каталога/проверок, LISA geonical-docs для сквозных связей и общего порядка.
> Зависимости: MOB-02, merged app/maplib/maplibui, существующие формы/Collector/
> sync/location contracts. Доставка — связанные Draft PR LISA и Android; APK,
> БД, NGW, настройки, продуктовые приоритеты и runtime не меняются.
>
> Приёмка: из входных AGENTS доступен source-backed каталог основных UI групп с
> producer/transform/widget, action/state/side effects, variants/conditions,
> units/CRS/freshness, сохранённым состоянием и независимыми ожиданиями. Проверки
> ловят потерю/переназначение reviewed methods и source fragments. Пробелы и
> неисполненная runtime матрица явные; missing contract агент исследует и сохраняет
> в этой же задаче. Исторический source review не равен полной приёмке интерфейса.
>
>

## mobile-roadmap-dependency-discovery

> ## MOB-02 Самостоятельное обнаружение и сохранение зависимостей
>
> Приоритет P1. Состояние: исходный entry bridge слит29.10 в PR83; shared LISA
> PR270 также слит. MOB-UI-01 принят в Android PR85 и LISA PR275; его runtime
> пилоты остаются NOT_RUN. Следующий source/docs шаг29.10 — глубокий разбор
> workspace/sync ownership, сохранённых ключей, pair ACK/full clock и cancellation
> drain в прежнем MOB-02, по новой команде владельца продолжать аудит до остановки.
> Изменения продуктовой очереди и возобновления native этапов нет.
> Основание — поручение пользователя27 октября: агент пропустил зависимый ресурс;
> пользователь не должен перечислять связи или отдельно просить сохранить найденное.
> Владелец — Android root для входных инструкций; общий source index/процедура
> принадлежат LISA tooling и geonical-docs, локальные контракты — их владельцам.
>
> Объём: обязательный самостоятельный discovery всего доступного desktop/mobile
> контура до изменения поведения/API/данных/ресурсов/config; реестры и известные
> примеры только начальная точка. Все новые подтверждённые связи автоматически
> сохраняются в той же задаче; ambiguous/dynamic refs — с source/UNKNOWN/next step.
> Общая процедура и индекс единственные, Android не получает их дубликат.
>
> Зависимости: общий механизм LISA в PR #270, доступные исходники владельцев и
> их точные root/revision; для отдельного checkout отсутствие/несовпадение внешних
> исходников остаётся UNKNOWN. Порядок доставки: shared tooling/contracts LISA,
> затем этот Android entry bridge; это не изменение runtime-контракта APK.
> Техническая документация выполняется параллельно MOB-01 и не меняет приоритет
> продуктовых задач. Product-код, Gradle, server data, native проверки и выпуск
> в этот объём не входят; локальные packs сабмодулей здесь не изменяются.
>
> Приёмка: root и app ведут к discovery без предварительного угадывания границы;
> источники и направления влияния сверяются самостоятельно; новые связи и
> неопределённости сохраняются без отдельной команды; перед source commit индекс
> проверяется на свежесть. Недоступные owners/другой worktree не скрываются за
> чужим индексом или словом PASS. Проверки — source-only docs/report validators;
> доставка и фактические результаты фиксируются в PR отдельно от device/release.
>

## mobile-roadmap-roadmap-adoption

> ## MOB-00 Привязка работы к дорожной карте
>
> Приоритет P1. Состояние: Слито в PR #78; регламент добавлен в документацию, автоматический контроль пока не реализован. Владелец — Android root; бизнес очередь согласует владелец продукта.
>
> Закрепить правило в Android AGENTS.md, docs/START-HERE.md, change-checklist и шаблоне PR. Приёмка: автор обычной и внеплановой доработки может найти карту из стартовых инструкций, указать существующий ID либо добавить новый, объяснить влияние и приложить проверку; ревьюер сверяет смысл и зависимости. Документационная доработка не запускает тяжёлые native проверки сама по себе.
>
> Автоматическую проверку существования ID и обязательных полей выделить следующим шагом: локальный pre-push даёт раннюю обратную связь, серверный PR check охватывает также сторонние и веб-коммиты. Обязательное прохождение check и ревью задаётся правилом слияния целевой ветки; изменение такой настройки требует отдельного полномочия. До внедрения этих мер контроль остаётся регламентом ревью. Один заполненный ID смыслового соответствия не доказывает.
>

## mobile-roadmap-styles-and-tracks

> Пакет включает принятые в текущем PR настройки стилей и управление треками, согласование MapLibre, а также связанные доработки Stop и прежней истории. Детальный объём и фактические проверки принадлежат PR и его квитанциям. Новые исправления в этом пакете сверяются также с REL-01/REL-03, GEO-01 или UX-03, если затрагивают соответствующий путь.
>
> Приёмка: закрытые зависимости библиотек и правильные pins, адресные проверки текущего объёма, явные непроверенные сценарии. Merge, проверка на устройстве и выпуск учитываются отдельно. Изменение приоритетов или объёма этого пакета требует обычной сверки с картой.
>

## mobile-roadmap-map-memory

> Приёмка: независимые unit границ1/2/4/8GiB и нескольких слоёв, actual software
> AVD с2GiB, настоящий длительный pan/pinch+heading workload, видимое предупреждение
> в обеих темах/ориентациях и при крупном интерфейсе; native memory/RSS/PSS evidence.
> Перед публикацией обязательны Full и нагрузка на двух физических устройствах.
> Подтверждение физического устройства с 2 GiB относится к отдельной проверке памяти.
>

## mobile-roadmap-heavy-map-diagnostics

> ## MOB-03 Приёмка тяжёлой карты и гарантии диагностического сбора
>
> Приоритет P1, по прямому поручению пользователя27 октября после инцидента224.
> Состояние: В Draft PR app82/maplib61/publisher7; отзыв224 выполнен, диагностический
> APK передан, полевой отчёт ожидается. Владельцы app/maplib и publisher для публичного отзыва.
> Первая цель — получить OS native stack после реального SIGABRT и после kill,
> не пережившего SDK envelope. Вторая — cold-process MVT карта и длительные жесты
> с живыми UI/renderer и hardware identity. Критерии и аппаратная матрица —
> [автотесты](../runbooks/automated-tests.md). Выпуск требует подтверждённую
> нагрузку на физическом смартфоне и отдельном планшете. Для приёмки клиентской
> карты нужен собственный прогон на разрешённом проекте.
>
> 11 ноября: подзадача MOB-03 восстанавливает ограниченный software emulator после Windows
> hard hangs и classic SwiftShader GLSL UBO ошибки. Рабочее сочетание: WHPX, Emulator37.2.13,
> API36 Google APIs revision7, системный GuestAngle/SkiaVK над CPU Lavapipe. Все11 прежних
> graphics Related cases прошли диагностический запуск; окончательный штатный runner
> проверяется отдельно. Историческая единственная причина hard hang не доказана.
> Подробности, проверки и границы приёмки: `android-20261111-emulator-software-angle`.
> Выпускная физическая матрица сохраняется; публикация и подъём версии в эту задачу не входят.
>

## mobile-roadmap-stale-data-and-update-reminders

> ## UX-05 Напоминание об устаревших данных и обновлении
>
> Приоритет P1. Состояние: В PR #78; адресные emulator/unit/UI проверки пройдены, Full смартфона и планшета ещё выполняется. Владелец — Android app.
> Причина: полевые устройства неделями работают без интернета или в авиарежиме.
> Зависимости — существующие ProjectSyncState и проверенный self-hosted updater;
> нового серверного контракта нет. Полная приёмка смартфона/планшета выполняется
> после этого изменения; остальные задачи очереди не запускаются и не расширяются.
>
> Приёмка: более семи суток без успешного полного sync активного онлайн-проекта
> и без успешной проверки обновлений различаются. У локального проекта sync не
> проверяется. Известное более недели обновление даёт разные сообщения до загрузки
> и после проверенной загрузки; новое издание не сбрасывает отсчёт уже ожидающего
> обновления. Предупреждение не блокирует работу, не запускает sync/установку само
> и не повторяется при повороте/сетевом retry в том же процессе. Неизвестная старая
> история получает неделю наблюдения без выдуманной даты успеха. Unit, native UI
> в обеих темах/ориентациях и крупном интерфейсе; затем Full на обоих устройствах.
>

## mobile-roadmap-mvt-correctness-and-freshness

> При обновлении существующего источника используется прежний URL тайлов. Нужно проверить обновление уже видимой области после изменения/удаления объекта и синхронизации, без перезапуска и движения карты. Смена проекта с одинаковыми layer ID также не должна показывать данные другого проекта.
>
> Приёмка: геометрия, подпись и identify согласованы; известна версия данных, по которой построен тайл; принята явная инвалидация. Отдельная матрица покрывает multipart, holes, границы тайлов, тонкие полигоны, подписи, порядок/видимость, смену стиля и lifecycle. Пока воспроизведения нет, устаревшее изображение учитывается как риск, а не установленный пользовательский дефект.
>

## mobile-roadmap-mvt-performance-and-bounded-cache

> ## MVT-02 Производительность и ограниченный кэш
>
> Приоритет P1. Состояние: Предложена. Зависимости: PERF-01 и MVT-01.
>
> Сократить повторное чтение полной геометрии и атрибутов, измерить batch чтение и безопасное упрощение для отображения. Добавить ограниченный RAM/disk кэш с identity проекта, слоя и ревизией данных, а при необходимости — стиля. Общие запросы одного тайла не должны повторять сборку.
>
> Приёмка: повторный просмотр области уменьшает DB/encode работу; sync немедленно делает старый кэш непригодным; быстрый pan/смена проекта отменяют устаревшую активную работу. Бюджеты времени/вершин/байтов соблюдаются без выдачи неполного тайла как успешного. Исходная бизнес геометрия сохраняется.
>
> Уже существуют два worker, очередь 16, защита по свободной heap памяти, сериализация одного слоя и classic fallback. Их нужно сохранить. Расширение на редактируемые слои — отдельное решение после проектирования edit/sync. Обычные стили, custom icons и rule styles продолжают работать через поддерживаемый путь.
>
> Основание: [LocalVectorTileProvider](https://github.com/GeonicalSys/android_maplib/blob/7e7055e54002b116e7a147fcd2791486913ddd9a/src/main/java/com/nextgis/maplib/map/LocalVectorTileProvider.java#L71), [MapDrawable](https://github.com/GeonicalSys/android_maplib/blob/7e7055e54002b116e7a147fcd2791486913ddd9a/src/main/java/com/nextgis/maplib/map/MapDrawable.java#L1424).
>

## mobile-roadmap-skip-unchanged-postgres-layers

> ## SYNC-01 Пропуск неизменённых PostgreSQL слоёв
>
> Приоритет P1. Состояние: Предложена; нужен прототип серверного контракта. Зависимости: REL-01 и PERF-01.
>
> Служебный маркер изменения слоя и поколения источника должен учитывать все изменения результата, включая удаления и зависимые таблицы. Проверить возможность чтения служебных данных через обычные разрешённые PostGIS ресурсы и API установленного NGW. Начать с одного настоящего слоя и параллельных транзакций.
>
> Приёмка: неизменённый слой не скачивает объекты; изменённый использует безопасную полную загрузку; исходящие правки отправляются независимо. Изменения атрибута/геометрии при прежнем количестве, delete+insert с прежним количеством, длинная транзакция, смена фильтра/прав/схемы и пересоздание источника не пропускаются. Отсутствие достоверного маркера возвращает к полной проверке.
>
> Количество объектов и время последнего запуска приложения не являются достаточным доказательством актуальности. Протокол отдельно доказывает, что его граница не пропускает поздно завершившиеся транзакции.
>
