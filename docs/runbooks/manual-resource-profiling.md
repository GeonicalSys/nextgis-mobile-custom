---
title: Ручной сеанс измерения ресурсов карты
type: runbook
last_verified: 2026-10-28
related_code:
  - tools/profile_render_resources.py
  - tools/tests/test_profile_render_resources.py
  - tools/profile_native_heap.py
  - tools/prepare_native_heap_apk.py
  - app/src/main/java/com/nextgis/mobile/util/PrivateRenderResourceProfiler.java
---

# Ручной сеанс измерения ресурсов карты

Инструмент предназначен для ручного движения карты в приватной диагностической
сборке. Он не устанавливает APK, не очищает данные, не вводит map gestures,
не меняет GPS и не публикует результаты. Обычные проекты и исходные геометрии
остаются у приложения. Долговременный кеш не является способом ускорения в этой
задаче; режим фактического кеша фиксирует app-side диагностика.

Требуются явно выбранное физическое устройство API29+, установленный APK с
`profileable/shell=true`, исходная verified-квитанция
`prepare_native_heap_apk.py`, exact `source_revision` из session header и
доступный `Download/LISA-Diagnostics`. Profileable не даёт доступа к private
app files или `run-as`. Root/отключение SELinux не нужны и не используются.
На API30 native capture допускается только для подтверждённого64bit app.

Все host evidence и TEMP/TMP/TMPDIR находятся наQ. Используются уже установленные
ADB, системные Perfetto/simpleperf и pinned Trace Processor/MapLibre symbols.
Перед capture должны быть проверены реальные возможности конкретного OEM:
profileable attach, simpleperf size limit и пригодность call stacks. Само наличие
процесса профилировщика ещё не доказывает появление корректных samples.

## Подготовка и начало

`start` создаёт новый каталог; повтор на существующий путь запрещён. В обычном
режиме пользователь заранее открывает диагностическое приложение. Ни Home, ни
force-stop не выполняются. Скрипт сохраняет APK SHA-256, source/version identity,
исходный PID, export inventory и точные пути/identity собственных collectors.

```powershell
$env:TEMP='Q:\Codex\Temp'; $env:TMP=$env:TEMP; $env:TMPDIR=$env:TEMP
py -3.13 -B tools/profile_render_resources.py --adb <adb.exe> start `
  --serial <explicit-serial> --output Q:/Codex/Scratch/<task>/manual01 `
  --apk-receipt Q:/Codex/Scratch/<task>/profileable-apk-receipt.json `
  --source-revision <40hex-or-40hex+dirty> --duration 1800 `
  --heavy-seconds 180 --windows 5 --schedule spread --cpu-callgraph fp
```

Лёгкий collector работает на устройстве до30мин по умолчанию, максимум60мин:
process CPU/raw stat/status каждые5с, `dumpsys meminfo` каждые15с с timeout,
uptime и отсутствие/PID changes сохраняются без выдуманной равномерной сетки.
Он переживает завершение запускающей ADB shell, не держит wakelock и ограничен
32МиБ. Замена установленного APK при наблюдаемом новом PID завершает sampler.

Первое тяжёлое окно запускается сразу; остальные планирует явный host supervisor:

```powershell
py -3.13 -B tools/profile_render_resources.py --adb <adb.exe> watch `
  --receipt Q:/Codex/Scratch/<task>/manual01/receipt.json
```

`spread` распределяет до5окон по всему сроку. `continuous` начинает следующее
после предыдущего. Supervisor должен оставаться запущенным и иметь связь с ADB;
при потере связи он сохраняет gap и прекращает планирование. Уже начатые device
collectors всё равно имеют собственные duration/size bounds. Его можно запустить
заново по той же квитанции: проверяются записанные owners, дубликат активного окна
не создаётся. Все app-side события записываются независимо, включая промежутки
между тяжёлыми окнами. Это не непрерывный CPU flamegraph всего30мин-сеанса.

Каждое тяжёлое окно: native heapprofd sampling1МиБ/dumps10с/block_client=false,
системный simpleperf `--app PACKAGE`/`task-clock:u`/99Гц/FP. Штатный app runner
даёт profileable release доступ к perf events; bare shell `-p PID` на T40
получил permission denied. PID/process segments в итоговых samples проверяются
отдельно, app selector не объявляется строгим main-PID фильтром. Каждый trace
ограничен32МиБ. Пять пар — максимум320МиБ; light32МиБ и выбранные app exports128МиБ
дают480МиБ плюс небольшой резерв metadata в пределах512МиБ. Достижение size cap
не считается покрытием всей запрошенной длительности.

`--cpu-callgraph dwarf,8192` доступен для отдельной проверки полноты call stacks,
но дороже FP; нельзя менять этот режим молча между сравниваемыми сериями.
В очередном `window --receipt ... --seconds 180` можно явно начать окно вручную,
если прежние collectors уже завершены и общее ограничение5окон не исчерпано.

При180с на окно это15мин native/CPU samples, распределённых по30мин ручного
сеанса. Global app-side phases/checkpoints и лёгкие метрики пишутся весь сеанс.
Один app-файл имеет cap64МиБ;128МиБ резервируются для нескольких процессов.
Превышение общего app budget — явная ошибка, без тихого усечения файлов.
Это cap выбранных для выгрузки exports, а не общий предел всех app-файлов
на устройстве при произвольном числе перезапусков. Первый30мин опыт проводится
в одном процессе; каждый дополнительный процесс получает отдельный session ID.

## Холодный запуск

Только явные `start --cold-start --confirm-idle-no-editor` разрешают завершить
прежний процесс перед native capture и нормально открыть MainActivity.
Оператор предварительно подтверждает отсутствие несохранённого редактора;
скрипт дополнительно требует обычный main screen и отсутствие Track/Walk services.
Никакой project data/cache не очищается. Без этих двух флагов force-stop запрещён.

Perfetto получает точные проверенные config bytes через host stdin subprocess,
`perfetto --background --txt -c -` сам возвращает actual daemon PID. Не используются
native shell pipeline, setsid или nohup: эта комбинация на T40 дала пустой log
и уже завершившийся PID. Фиксируется `NATIVE_BACKGROUND_PID_ACK_NOT_DATASOURCE_READY`.
Exact owned `/data/local/tmp/lisa-render-<session16>/lisa-heap-<window16>.pbtxt`
сохраняется для hash/provenance, но perfetto не открывает его. Output — existing writable
`/data/misc/perfetto-traces/lisa-heap-<window16>.pftrace`. Перед этим config
проверяется по hash, сохраняются layout/read-only directory probes.
На T40 чтение config напрямую из tmp отвергнуто SELinux, а системного configs
каталога нет; writable shell tmp не является разрешением для perfetto domain.
Старые standard/tmp пары принимаются лишь для recovery прежних квитанций;
новые окна используют `stdin_standard`. Произвольные пути/разные nonce запрещены.
Root/chmod/создание системного каталога не применяются. Подготовка config
заканчивается до явного cold stop; затем повторно проверяются UI/Track/Walk.
Короткие device probes подтвердили native host-stdin foreground, затем native
`--background` с actual живым PID после возврата ADB, и simpleperf --app.
Native trace foreground имеет непустые heap rows; реальное покрытие/полнота
стека и холодного старта проверяются отдельно в полном control.
В AOSP `OpenOutputFile`/`OnConnect` открывают output и передают FD сервису;
[исходник](https://android.googlesource.com/platform/external/perfetto/+/refs/heads/android10-dev/src/perfetto_cmd/perfetto_cmd.cc).
Это не подтверждение готовности heapprofd. Даже при cold request фактический
`from_startup` и покрытие должны быть проверены в trace. CPU app attach начинается
после запуска приложения и не принимает раннюю startup часть. При обычном late attach
прежние allocations намеренно не входят в native профиль.

## Остановка и получение

```powershell
py -3.13 -B tools/profile_render_resources.py --adb <adb.exe> status --receipt <receipt.json>
py -3.13 -B tools/profile_render_resources.py --adb <adb.exe> stop --receipt <receipt.json>
py -3.13 -B tools/profile_render_resources.py --adb <adb.exe> pull --receipt <receipt.json>
```

Контроль одного сеанса сериализован OS lock. Перед сигналом проверяются PID,
Linux start ticks, executable, exact output/config paths и session nonce.
Нет глобальных killall/pkill. При transport interruption во время старта
допускается поиск только команды с точными записанными путями. Неизвестное владение
оставляет файлы и ошибку. При неудавшемся start finalization пытается остановить
свои внешние collectors, сохраняя исходный FAIL; app journaling не выключается.

App exports выбираются по filename `lisa-resources-<32hex>.jsonl`, точному
`app_session` header, source_revision/package/private flag, исходному inventory
и PID. Старые посторонние файлы не удаляются и не скачиваются «все по префиксу».
`stop` посылает private DUMP-protected STOP broadcast с точным session ID;
успех shell команды не заменяет финальный `app_end`. После STOP в том же PID
app-сборщик не включается заново: перед новым опытом нужен безопасный новый
процесс. `start` требует активный app_session текущего PID с будущим deadline
и без app_end; завершённый старый файл не даёт успешного начала опыта.
При отсутствии ACK
локальный перенос допустим только при стабильном hash, с явными incomplete/active
flags; changing source остаётся transfer failure до следующего STOP/deadline.

`pull` создаёт новый immutable каталог, сверяет SHA-256 и размеры до/после
переноса. Незавершённая последняя строка JSONL сохраняется как partial; битая
внутренняя строка не называется корректной. Пустой trace или достигнутый cap
отмечаются отдельно. Перенос ещё не означает profile quality PASS: затем нужны
проверки loss/overrun, реального sample span, PID segments, Build ID и symbols.

Remote копия app-файла не создаётся. После STOP переносится исходный bounded
export; если запись продолжилась во время pull, hash/size validation отклоняет
его и сохраняет partial. Повтор возможен после app_end/deadline. Допускаются
не более2неперезаписываемых pull attempts на сеанс; прогресс и FAIL receipt
сохраняются даже при ошибке inventory или budget. До480МиБ — данные одного
capture, а не общий host disk cap с retry: две попытки могут сохранить до960МиБ
наQ. Перед каждой проверяется свободное место, дальнейшие копии требуют review.

Remote configs/traces/light намеренно удерживаются до проверенной
выгрузки и просмотра. App Downloads не очищаются этим инструментом. В квитанции
пишется `NOT_PERFORMED_RETAINED_UNTIL_REVIEW`, а не ложная успешная очистка.
После подтверждённого pull собственные remote пути можно убрать отдельной
адресной операцией после новой проверки отсутствия writers; неизвестные файлы
и shared SDK/caches в scope удаления не входят. На host новые bulk inputs не
копируются; компактные receipts сохраняются с историей задачи.

CPU ownership относится к shell-parent с точными `--app PACKAGE`, output path,
PID/start ticks. На T40 setsid fork возвращает короткоживущий launcher PID;
`$!` сохраняется отдельно и никогда не используется как доказанный owner.
До5с проверяются реальные simpleperf процессы, выбирается единственный parent
с точным package/output, затем сохраняются его actual PID/start ticks.
Старая квитанция с PID без start ticks требует такого же повторного discovery.
In-app child использует `--out-fd`/stop pipe и не выбирается для
прямого kill. SIGINT parent штатно закрывает pipe и ждёт child; это подтверждается
отдельной полной проверкой stop на устройстве.
[AOSP app runner](https://android.googlesource.com/platform/system/extras.git/+/0744f4b1eb07920150f5561dfbcb38fb11b5b89c/simpleperf/environment.cpp).

## Что означают данные

App tile phases/scene/frame identity и результаты всех этапов остаются отдельными
от global process memory, sampled native allocation и CPU call stacks. Native
heap profile не является полной памятью GPU/Graphics или allocator RSS.
Перезапуски разделяются по PID/start time; retained sizes умерших и живых
процессов не складываются в «текущую» память. Retained bytes разных heapprofd
attach окон также нельзя суммировать: в каждом окне видны только новые после
attach allocations. Периодический app_checkpoint даёт промежуточную агрегированную
картину после kill, но не заменяет финальный app_end и не делает prefix полным.
ART allocation churn, если будет
добавлен на API32+, также нельзя складывать с native net retained.

Результаты profiler smoke/технических fixtures не принимают реальные карты,
весь каталог, Full,2GiB, отсутствие OOM или целевую задержку2–3с. Для публикации
остаются штатные отдельные gates. Здесь не выполняются публикация/merge/deploy.

Техническая адресная проверка, без устройств и бизнес-данных:

```powershell
py -3.13 -B -m unittest discover -s tools/tests -p test_profile_render_resources.py -v
```

Источники: [profileable](https://developer.android.com/guide/topics/manifest/profileable-element),
[heapprofd](https://perfetto.dev/docs/data-sources/native-heap-profiler),
встроенные NDK28.2 simpleperf docs и исходники уже существующих capture helpers.
