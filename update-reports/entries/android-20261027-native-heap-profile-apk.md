---
id: android-20261027-native-heap-profile-apk
area: Мобильное приложение
kind: internal
at: 2026-10-27T23:00:00+01:00
title: Приватная сборка для определения владельцев нативной памяти без изменения кода приложения
why: На проекте787 процесс удерживает около1GiB нативной памяти уже после холодного запуска; экспериментальные ограничения памяти не должны скрыть исходную причину при профилировании
covers:
  - tools/prepare_native_heap_apk.py
  - tools/NativeHeapManifest.java
  - tools/tests/test_prepare_native_heap_apk.py
---

MOB-04. Developer-only команда подготавливает отдельный приватный APK из явно
проверенного по SHA-256 ранее установленного APK. Через уже установленные AAPT2,
protobuf и apksig изменяется только манифест: один application/profileable с
android:shell=true. Dex, native libraries, resources.arsc, layouts, assets и
исходная version-control metadata проверяются побайтно по SHA-256. Смысл остальных
узлов манифеста, package/version/SDK, non-debuggable и исходный сертификат сохраняются.
APK подписывается существующим production key; секреты читаются напрямую из
приватных Gradle properties и не попадают в аргументы, окружение или квитанцию.

Команда не собирает текущие app/maplib исходники, не устанавливает APK, не
управляет телефоном и не публикует выпуск. Поэтому ещё не принятые memory policy,
упрощение/прореживание геометрии и прочие изменения рабочей копии не влияют на
профилируемый baseline. Временные копии принадлежат одной команде, лежат наQ,
удаляются в finally; результат и компактная квитанция хранятся отдельно.

Реальный технический прогон27 октября подтвердил AAPT2 roundtrip,
сертификат/16KiB выравнивание и все неизменные члены архива. Сохранена квитанция
profile-apk-prepare02.json; исходный f22e08 APK сохранён отдельно, телефон этим
инструментом не затронут. Первый прогон безопасно остановился из-за нового имени
minSdkVersion в выводе AAPT2; поддержаны обе формы, исходная ошибка сохранена.
Unit-тесты проверяют
отказ при изменении bytecode/native/resource/assets/provenance, дублировании
ZIP paths, неизвестной идентичности и утечке приватного вывода инструмента.
Подготовка APK не является доказательством устранения падения или native PASS.

Связи и ограничения: вход — ровно сохранённый APK и существующая конфигурация
подписи; выход — отдельный локальный диагностический APK, без изменения NGW,
Collector schema, SQLite, геометрии, черновиков, outbox, GPS или desktop producers.
Root dependency discovery/plan сохранены в текущей задаче; покрытие остаётся
UNKNOWN, source inventory неполон, индекс DRIFTED. Desktop checkout
codex/roadmap-admission принадлежит другой задаче и dirty: обновление его
generated/curated registry требует продолжения владельцем checkout, здесь оно
не выполняется и не объявляется завершённым.

Контракт внешних инструментов:
https://developer.android.com/guide/topics/manifest/profileable-element
https://perfetto.dev/docs/data-sources/native-heap-profiler
https://developer.android.com/tools/aapt2
