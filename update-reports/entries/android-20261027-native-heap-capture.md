---
id: android-20261027-native-heap-capture
area: Мобильное приложение
kind: internal
at: 2026-10-27T23:40:00+01:00
title: Ограниченное профилирование владельцев нативной памяти на физическом устройстве
why: Необходимо отделить исходную загрузку проекта787, накопление при движении и реально удержанные allocations от общего RSS
covers:
  - tools/profile_native_heap.py
  - tools/tests/test_profile_native_heap.py
---

MOB-04. Одна developer-only команда проверяет явно выбранный физический телефон,
неизменный установленный profileable APK по подготовленной квитанции/SHA-256 и
метаданные только разрешённого проекта. В cold режиме она отказывается останавливать
реальную запись Track/Walk, включает только exact-package heapprofd до запуска
процесса и сохраняет семплы --local meminfo. Late attach явно не включает старые
allocations. Gesture UI выполняется оператором во время записи; команда не
устанавливает APK, не читает объекты/аккаунты/raw heap и не меняет GPS.

Duration20–300s, sampling1MiB, shared memory64MiB, buffer32MiB, trace64MiB,
неблокирующий клиент и snapshots10s ограничивают ресурсы. Владение Perfetto
проверяется по конкретному PID и двум собственным путям в cmdline; TERM никогда
не отправляется постороннему capture. После остановки trace переносится наQ с
SHA-256 readback, затем удаляются только два собственных remote файла с проверкой
отсутствия. Незавершённый stop/pull/cleanup сохраняется явно, без killall и ложного PASS.

Проверенный Windows Trace Processor58.2 формирует SQL/CSV/JSON: положительные
выделения, отрицательные frees, net retained по callsite, timeline, mapping/rel_pc/
build-id для символизации и ошибки heapprofd/потери. Пустые/неоднозначные данные,
ошибки профилировщика и незакрытая очистка не являются проверенным capture.
Это диагностический инструмент, а не доказательство исправления приложения.

Отказ запуска сохраняет конкретную стадию, exit code, команду и ограниченные
stdout/stderr Android/Perfetto. Отдельный --recover использует исходную неизменную
квитанцию, проверяет точные пути/устройство и hash config, находит только собственные
Perfetto PID по двум уникальным путям, сохраняет завершённый/пустой trace с hash
readback и удаляет свои файлы. Новая recovery-квитанция не заменяет исходный FAIL
и не объявляет неудавшийся профиль успешным. Неизвестное владение/живой writer/
неполная копия оставляют remote evidence и явный pending cleanup.

Реальный первый запуск обнаружил штатный SELinux отказ чтения config из
/data/local/tmp. Будущие captures используют разрешённые Android каталоги
/data/misc/perfetto-configs и /data/misc/perfetto-traces без изменения разрешений
или безопасности устройства. Legacy пара /data/local/tmp разрешена только
--recover по точной исходной квитанции; nonce config/trace должен совпадать.

Завершение Perfetto по duration между проверкой cmdline и TERM допускается
только после новой успешной проверки отсутствия /proc конкретного PID. Потеря
ADB-соединения не считается отсутствием. Capture02 выявил эту гонку; исходный
FAIL сохранён, recovery отдельно перенёс trace и подтвердил remote cleanup.
Точная регрессия гонки/transport failure входит в focused host tests.

Первый реальный180s профиль оборвался внутри heapprofd из-за client buffer
overrun, а не дал свидетельства о владельце гигабайта RSS. Исходные FAIL/trace
сохранены. Для следующего сбора увеличены shared memory до64MiB и интервал
семплирования до1MiB; block_client остаётся false. Flush каждые5s устраняет
ошибку config_write_into_file_no_flush. Приёмка требует минимум двух dump
timestamps и span не меньше duration−30s (не меньше10s):20s допускается на
startup/attachment и10s на последний dump. Один ранний snapshot и короткий
обрыв не становятся PASS даже при ненулевых allocations. Счётчики malloc
не объявляются общим RSS, GPU памятью или доказательством устранения утечки.

Проверено13 focused host tests. Реальный capture03 на SM-A566B, проект787:
180s,18 allocation dumps/span169.95s,0 profiler quality errors, проверенная
копия trace и remote cleanup. Это проверка диагностического маршрута; она не
заменяет Full/2GiB приёмку и не является исправлением причины памяти карты.

Новые синтаксические связи: profile_native_heap -> prepare_native_heap_apk
(проверка путей/SHA), profile_mobile_memory (exact package/разбор собственных
счётчиков), официальный PerfettoCLI/Trace Processor. Семантическая зависимость —
предварительно подтверждённый manifest-only APK; исходный код карты не пересобирается.
NGW/Collector schema, SQLite, геометрия, drafts/outbox и desktop producers не
меняются. Root discovery/plan выполнены, полнота покрытия UNKNOWN; чужой dirty
desktop checkout/source-index DRIFTED требует продолжения владельцем и здесь
не редактируется. Phone execution выполняется отдельно в основной задаче.

https://perfetto.dev/docs/data-sources/native-heap-profiler
https://perfetto.dev/docs/reference/perfetto-cli
https://perfetto.dev/docs/reference/trace-processor-cli
