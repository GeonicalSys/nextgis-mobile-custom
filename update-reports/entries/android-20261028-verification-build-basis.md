---
id: android-20261028-verification-build-basis
area: Достоверность проверки мобильной сборки
kind: internal
at: 2026-10-28T07:41:00+01:00
title: Привязка native результатов к собранной паре verification APK
why: Related SkipBuild мог исполнить старые APK и приписать результат fingerprint текущих файлов при тех же именах cases
covers:
  - tools/run_mobile_tests.py
  - tools/mobile_build_identity.py
  - tools/mobile_test_support.py
  - tools/tests/test_mobile_build_identity.py
  - tools/tests/test_mobile_test_support.py
  - tools/tests/test_mobile_render_acceptance.py
  - docs/runbooks/automated-tests.md
---

MOB-04, самостоятельный source-confirmed P1 в тестовой автоматизации. Старый
Related --skip-build обходил assemble, проверял только package APK, а result
получал текущий source fingerprint. Case names A/B могли совпадать при другой
реализации/assertions; прежние APK A могли дать PASS, помеченный fingerprint B.
Это воспроизводимый путь в source, а не доказательство, что прежние incident
Native05/06 фактически исполняли старую сборку. Full уже запрещал SkipBuild и
вызывал assemble; новая привязка закрывает также overwrite/mixed-pair окно.

Новый offline mobile_build_identity владеет успешным build dataflow и atomic
receipt build/verification/apk-build.json. Перед assemble и после него sources
совпадают с ожидаемым fingerprint; после aapt package inspection и hash inventory
проверяются ещё раз. Только успешный Gradle callback создаёт basis. Фиксируются
точные verification flags/tasks, wrapper/launcher hashes, app/test package/path,
размеры и SHA256; canonical basis digest защищает от случайной порчи metadata.
Это trusted local receipt, не security signature. Gradle private config/signing
секреты не читаются/не копируются ради этой проверки.

SkipBuild только загружает известный успешный basis. Missing/legacy receipt,
другие sources/recipe, отсутствие половины, другой package/path, mixed/tampered
APK отклоняются до select_devices и первого ADB. Normal build переносится перед
select_devices; действующие software-only/явные разрешения по-прежнему проверяют
именно запуск устройства. Повторные isolated методы переиспользуют ту же пару,
никаких extra builds/copies по методу нет. Пара hashes повторно проверяется
перед/после install, перед cleanup и перед окончательным PASS. Ошибки сохраняют
FAIL receipt; существующие UID finally/SQLite artifacts/Related continuation
не изменены. Progress/result schema2 несут build_basis/hash; publication checker
отклоняет legacy Full и проверяет соответствие реальных текущих файлов пары.

15 новых focused unit tests: compileA→sourcesB при том же case name; exact reuse
без повторной сборки; missing/legacy; same-size tampering обеих половин; mixed
path/recipe; failed Gradle invalidates old basis без inspection; source mutation
до/во время assemble/inspection; atomic-write failure cleanup; package/half/
metadata corruption; stale skip до любого device action; Full skip до Gradle;
install ordering и concurrent APK overwrite. Existing Full/stress fixtures
получили корректный basis и отдельное legacy/other-build rejection expectation.
Offline mobile tool suite:127 tests PASS за5.752s, без product Gradle/ADB/native;
имитированные logs с FAIL/starting emulator принадлежат техническим mock tests.
Это проверка guard и сохранения соседних контрактов, не native/OOM acceptance.

Перед edits fresh preflight Fetch OK/own ahead0-behind0; parent owns app dirty
changes и прежние maplib buffer7paths, foreign desktop не менялся. Два read-only
whole-contour plans verification-build-basis-dependency-plan01/02.json exit0,
coverage UNKNOWN. Source→Gradle→APK pair→install→native result→Full gate и reverse
publisher integrity boundary проверены. Никакие SQLite/NGW/Collector/desktop data
форматы/пользовательские проекты не меняются. Semantic build-basis contract
сохранён в canonical runbook; parent обновляет canonical registries/source index
в своей delivery closure. Library/SDK/publisher code не менялись.

Реальный первый guarded runner build/установка и native результат требуют
родительского запуска без SkipBuild; вручную штамповать прошлые APK запрещено.
Installed PackageManager bytes самостоятельно не аттестуются, hermetic toolchain
и release candidate SHA↔Full↔publisher pairing остаются явными границами.
Production publication/release здесь не разрешались и не выполнялись.
Новые2plans около63MiB остаются incident evidence на Q до общего closure;
временные tiny unit directories/atomic parts удаляются владельцами в finally,
SDK/runtime/APK bulk copies/downloads не создавались.
