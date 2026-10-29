---
id: android-20261028-resource-profiler-tools
area: Диагностика ресурсов карты
kind: internal
at: 2026-10-28T20:27:40+01:00
title: Ограниченный сбор памяти и времени во время ручного прогона карты
why: Нужен непрерывный контекст ручных перемещений и несколько ограниченных native CPU/allocation окон вместо одного короткого старого профиля
covers:
  - tools/profile_render_resources.py
  - tools/tests/test_profile_render_resources.py
---

MOB-04. Developer-only start/status/watch/window/stop/pull используют явно
выбранное физическое устройство и точную verified-квитанцию profileable APK.
Приложение и проекты не устанавливаются/очищаются; жесты, GPS, длительный кеш,
публикация и эмуляторы не управляются. Cold restart разрешён только отдельными
явными флагами с main/Track/Walk guard и подтверждением отсутствия редактора.

Лёгкие processCPU/meminfo/uptime данные имеют device deadline и32МиБ cap.
До5окон native sampling1МиБ + CPU99Гц FP ограничены duration и32МиБ каждый:
основной сеанс20–30мин, до480МиБ data плюс metadata reserve в пределах512МиБ.
Host watch распределяет окна или запускает последовательно; его потеря не
снимает device bounds. Реальное покрытие, смены PID, несовпадение APK, gaps,
неподтверждённый startup attach и size/loss ограничения остаются явными.

Каждая остановка проверяет PID/start ticks/команду/exact owned paths, без
global killall/pkill. OS lock сериализует status/window/stop/pull и действия
supervisor. Неудавшийся старт сохраняет FAIL и пытается завершить только свои
внешние collectors. App exports отбираются по session header/package/source/
private flag и исходному inventory. App STOP защищён DUMP и session ID;
отсутствующий app_end не объявляется завершённым журналом. Начало требует
активный journal текущего PID, будущий deadline и отсутствие terminal footer:
после app STOP новый опыт требует нового процесса. Один app-файл64МиБ,
все выбранные process segments128МиБ; budget overflow не усекается молча.

Trace/light/JSONL выгружаются с hash/size readback в новые каталоги наQ.
Неизвестные exports и прежние partial evidence не перезаписываются. Remote
копии удерживаются до проверки; удаления app Downloads/проектов/shared inputs
нет, disposition явно retained. Существующие SDK, Perfetto58.2 и MapLibre13.7.1
symbols переиспользуются без новых runtime copies.

App export не дублируется на устройстве: changing source явно отклоняется,
стабильный файл переносится напрямую. Не более2pull attempts (до960МиБhost
evidence), сprogress/FAIL receipts даже приabort после частичного переноса.
Detached launch отмечается доотправкикоманды; потерянный ACK не даётSTOPPED.
Start/status/stop/window/pull сериализованы OSlock, app PIDreuse проверяется
через точные kernel start ticks/CLK_TCK из header и /proc текущего процесса.

Первый device control обнаружил race создания owner.pid до записи PID.
ACK теперь ждёт одновременно полный положительный PID, сохранённый start stat,
session header и живую exact-owned команду. Empty→valid PID/stat/header и timeout
покрыты адресными техническими regressions; новая device проверка остаётся
отдельным gate, прежняя неудачная квитанция не перезаписывается.

OEM Android11 не разрешает perfetto domain читать shell tmp config, а системный
configs directory отсутствует. Новые окна используют host stdin config bytes
с native `--background -c -` и exact owned trace в existing perfetto-traces;
native shell pipeline/setsid/nohup на T40 не прошёл ACK и не используется.
CLI возвращает actual daemon PID, который сверяется по cmdline/start ticks.
Старые пары путей
оставлены только для recovery прежних квитанций. Config hash/session/window
проверяются отдельно. UI/recorder/cold stop выполняются после подготовки config.
CPU использует `--app PACKAGE`: bare shell -p получил perf_event denied,
штатный app runner routes profileable release и принимает output FD от parent.
SIGINT адресуется проверенному shell-parent, который закрывает stop pipe и
ждёт child; прямой kill in-app процесса отсутствует.
На T40 setsid делает fork: возвращённый `$!` — launcher, а не actual parent.
Launch PID сохраняется отдельно; bounded discovery находит ровно один
simpleperf с exact package/output и фиксирует его PID/start ticks. Старый
неподтверждённый PID без start ticks не используется для сигнала. Эту развилку
покрывает регрессия launcher1995→parent1996 с исключённым in-app child1997.
Короткий vendor probe stdin/--app завершился exit0 с непустыми файлами;
native --background отдельно сохранил живой actual PID после возврата ADB.
Sample span/loss/native stacks/startup и полный stop требуют отдельных проверок.

Адресные технические regressions проверяют PID reuse/чужой cmdline, строгие
пути/headers/caps, schedule coverage reserve и hash-transfer failures на
технических fixtures. Реальный OEM smoke/attach/stack quality, native cold
readiness и user workload acceptance выполняются отдельно родительской задачей;
источник не объявляет их PASS. Transfer не равен profile acceptance.

Родительская проверка 28 октября: 36 актуальных helper tests PASS; native
allocation probe содержит 110 heap allocation rows, CPU probe — 492 samples.
В control04 CPU записал 1617 samples/0 lost, но старт правильно считался
неуспешным из-за неверного launcher PID; последующий owned stop/pull сохранён
с этой оговоркой. После исправления launcher discovery основной manual01
подтвердил одновременно живые light/native/CPU owners и новый app session.
Первое 180-секундное CPU окно штатно завершилось: 5444 samples/0 lost.
Остальные окна, реальные span/stacks, app footer и overhead ручного прогона
ещё не приняты; источник не выдаёт старт за полную приёмку сеанса.

Контракт: [ручной сеанс](../../docs/runbooks/manual-resource-profiling.md).
Imports повторно используют штатные profile_native_heap/prepare_native_heap_apk;
старые helpers, runtime app/maplib и publisher этим пакетом не изменены.
Root canonical dependency index/UNKNOWN принадлежит владельцу общей задачи;
NGW/SQLite schema, исходная геометрия и записи проектов не меняются.

Сеанс остановлен по команде пользователя после19мин:3из5плановых180сокон,42990CPU samples/0lost,3×18native dumps,полный14МБapp export. Ownership/stop/immutable transfer PASS, stack attribution частично UNKNOWN, heapprofd существенно влияет на профиль. Итог и конкретный existing-cache bottleneck: [разбор T40](../../docs/reference/mobile-resource-profile-t40-2026-10-28.md). Остатки и review04.11 отражены в storage manifest; новых runtime/download копий нет.
