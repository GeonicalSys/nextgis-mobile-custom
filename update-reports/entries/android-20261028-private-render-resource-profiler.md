---
id: android-20261028-private-render-resource-profiler
area: Мобильное приложение
kind: internal
at: 2026-10-28T20:17:35+01:00
title: Приватная запись потребителей памяти и времени при ручной работе с картой
why: После ускорения SQLite основными остаточными расходами видны очередь и native/graphics; короткие meminfo и cap16 phase summaries не позволяют надёжно ранжировать все запросы и графические ресурсы
covers:
  - app/build.gradle
  - app/src/main/java/com/nextgis/mobile/util/PrivateRenderResourceProfiler.java
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - app/src/test/java/com/nextgis/mobile/util/PrivateRenderResourceProfilerTest.java
---

MOB-04, отдельный диагностический этап по прямому поручению владельца.
Долговременный кеш не рассматривается и не меняется. Явный private build flag
запускает локальный collector; normal release/debug и verification route по
умолчанию не меняются. Profileable-копия остаётся отдельной developer операцией.

App связывает maplib per-request/per-layer observer с bounded nonblocking writer,
MediaStore Downloads и независимыми process/frame/scene records. Числовые
MapLibre 13.7.1 SDK stats раскрывают texture/buffer/index/vertex/uniform memory,
draw calls и SDK durations без expensive renderedFeatureInfo/feature IDs/XYZ.
Существующий presentation/recovery frame listener сохранён. Новые listeners
снимаются при уничтожении view; retirement не зависит от диагностического успеха.

Лимиты: до 60 минут, 64 МиБ, очередь 512 records/2 МиБ estimated data и 256 КиБ
резерва на footer. Memory/light CPU — примерно каждую секунду, PSS — каждые
пять секунд по фактическому времени; sleep/observer gaps не выдумываются как
равномерный поток. Отдельно записаны затраты observer и потери; bounded final
summary не зависит от заполненности raw queue. На storage failure карта
продолжает работать, private fallback не выдаётся за доступный export.
Первый trial рассчитан примерно на 30 минут, полный час при достижении file cap
не обещается. Через 10 секунд и затем каждые 30 секунд writer сохраняет bounded
aggregate checkpoint вне raw очереди с фактической временной границей. После
process kill это только последний полный checkpoint, а не completed capture.
Header хранит actual process 64-bit marker и точные kernel start ticks/clock tick
frequency; parser учитывает `)` и пробелы в comm. Process.getStartElapsedRealtime
не используется как kernel process birth: его bind-time semantics отличаются.
Недоступные поля остаются unknown и блокируют строгую external identity gate.
Для сопоставления слоёв выбирается
один проект на session; временная корреляция не доказывает ownership native RAM.

Остановка по protected shell broadcast требует exact session и DUMP sender;
не останавливает приложение/трек. Ни startup APK hash, ни бизнес-атрибуты,
координаты, URI аккаунтов или project paths в app collector не читаются.
Нет permissions/manifest receiver, изменений проекта, оригинальных геометрий,
schema/sync/outbox/GPS, public upload, новой cache policy или feature filtering.
Native allocation ownership не приписывается слою по process memory delta.

Адресные JVM tests покрывают bounds, SDK conversion/invalids, failure и unknown
значения. Физические sample/export/SDK counters и внешние heap/CPU profiles
проверяются отдельно на T40. До такого smoke наличие кода не означает готовый
полевой профиль; Full и публикация APK владельцем отложены. Подробности в
app module README и текущем task evidence.

Проверка подготовки на 28 октября 2026:

- Финальная адресная JVM-проверка `PrivateRenderResourceProfilerTest`:
  15 tests, 0 failures/errors/skips (`app-focused-final02.log` и сохранённый
  JUnit XML). Она включает exact kernel process identity, приоритет terminal
  summary, bounded checkpoint/footer и invalid/unknown numeric значения.
- Обычные Lisa Debug/Lisa Release/Belka Release с default-OFF flag прошли
  ранее (`app-focused-normal-build01.log`, 1 мин 46 с), **до** последнего
  добавления exact kernel identity header. Этот прежний результат не выдаётся
  за повторную сборку всех normal variants последних исходников. Последняя
  private Lisa Release после этих правок прошла отдельно
  (`private-release01.log`, 29 с).
- `profileable-apk-prepare01.json` проверил приватный APK
  `3f3dd5b53918b38630fda7a692c0941f41a6a719599d3849eafaea02721d4d20`
  (70 736 248 bytes), package `com.nextgis.mobile.geonical`,
  224 / 3.1.2.30, non-debuggable. Сертификат совпал с исходной release-подписью:
  `ecd04c936532000eea8cb06ca6a3c1a3ae482844cacce2b84926c635429c3e18`.
  Добавлен только `profileable/shell=true` и обновлены подписи; 1 934 остальных
  ZIP members, включая dex/native/resources, побайтово сохранены.
- В контрольном `control04` на T40 / API 30 завершённый app segment
  `be24f6c85e40419c8bffb7da859205bc`, PID 1420, содержит 1 015 JSONL records.
  Его `app_end`: `external_stop`, `complete_footer=true`,
  `summary_available=true`, `resource_end_persisted_in_footer=true`,
  `raw_app_stream_complete=true`; queue dropped / file rejected /
  observer errors — 0 / 0 / 0. Header подтвердил process 64-bit, точные
  kernel start ticks и clock frequency 100 Hz. Это готовность app-сборщика и
  его экспорта, не измеренный выигрыш производительности и не Full.
- Предыдущий принудительно завершённый segment
  `2197f1b3db4d4c959c73add49cbb0091`, фактический PID 32527, footer не имеет.
  Он сохранён как неполный prefix/checkpoint и не заменён успешным segment.

Сырые контрольные данные и квитанции находятся вне Git в
`Q:/Codex/Scratch/mobile-resource-profiler-20261028-01a1817f/`.
Фактическая revision APK — `4bd9f7d981ebd444a6821891b6a2b4ec962efcc8+dirty`;
она не переименовывается в новую clean HEAD. Привязка к frozen source hashes
и итоговая source-provenance сверка остаются в области головной задачи.
Приватная установка и контроль app-экспорта не означают публикацию, успешный
Full или завершение анализа пользовательского ручного прогона. Долговременный
кеш и политика отрисовки этим диагностическим этапом не менялись.
