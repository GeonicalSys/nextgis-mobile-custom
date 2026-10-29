---
id: android-20261028-memory-counter-parser
area: Мобильное приложение
kind: internal
at: 2026-10-28T01:33:00+01:00
title: Достоверные счётчики памяти и проверяемое восстановление диагностического GPS
why: Локальный meminfo может содержать нулевые заглушки allocator, а разные Android версии имеют разные столбцы RSS/PSS
covers:
  - tools/profile_mobile_memory.py
  - tools/tests/test_profile_mobile_memory.py
---

MOB-04. Developer-only измеритель сопоставляет значения с двухстрочным заголовком
Android meminfo. RSS не берётся из Private Clean/Swap соседней версии; layout без
RSS сохраняет его неизвестным. Нулевая тройка Heap Size/Alloc/Free, встречающаяся
при --local, отмечается недоступной и не становится доказательством нулевого heap.
Измеренные RSS/PSS и итоговые resident counters сохраняются отдельно. Старые
cold02/прочие evidence не переписываются. Cold-start маршрут не расширяется.

Idle/pan/zoom используют только заданные наблюдавшиеся координаты, ограниченное
время и исходный foreground/PID; неверные параметры отклоняются до ADB. Временный
mock GPS по явному запросу восстанавливает provider и исходный appop независимо:
один отказ не пропускает второе восстановление. PASS требует readback отсутствия
mock provider и точного исходного appop; неизвестное создание/неполная очистка
остаются явной ошибкой квитанции.

Независимые literal fixtures покрывают full/local/старые7- и9-столбцовые Android
форматы, разные значения PSS/RSS, нулевые placeholders и повреждённый заголовок.
Подменённый host transport проверяет ограниченные stages, PID/порог остановки и
восстановление GPS без настоящего ADB. Это техническая проверка инструмента,
не native приёмка приложения и не доказательство устранения memory crash.

Проверено15 новых unit tests и13 tests импортирующего profile_native_heap.
Readback46 сохранённых meminfo (cold02=17, idle01=12, pan01=17) сохранил RSS
во всех образцах; у17 local cold02 allocator counters остаются неизвестными.
Отдельная квитанция memory-counter-readback01.json содержит hash исходных
свидетельств и счётчики, исходные файлы/старые JSON не изменены.

Preflight fetch выполнен; dirty Android/maplib — текущая OWN задача. Whole-contour
read-only plan сохранён в memory-counter-dependency-plan01.json: coverage UNKNOWN,
inventory incomplete, source index REFRESHED_IN_MEMORY. Подтверждённые consumers:
profile_native_heap -> parse_memory/PACKAGE и native_heap_map_route -> native heap
tool. Новый unit импортирует profile_mobile_memory. Desktop/NGW/Collector/schema/
геометрия/storage приложения не меняются. Generated/curated root registry остаётся
за владельцем отдельной ветки desktop; необходимый follow-up перед source commit
не выдаётся за уже сохранённое/проверенное полное покрытие.
