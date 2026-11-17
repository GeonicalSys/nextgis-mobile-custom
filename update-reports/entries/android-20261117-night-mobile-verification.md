---
id: android-20261117-night-mobile-verification
area: Мобильное приложение — PERF-01, MVT-01, AUTH-01, SCRIPT-UI-01
kind: internal
at: '2026-11-17T07:30:00+04:00'
title: Проверить ночной мобильный пакет на собственных данных и обновить дорожную карту
why: Прямое поручение владельца выполнить автономный ночной план и зафиксировать фактический остаток
covers:
- app/src/androidTest/java/com/nextgis/mobile/reliability/NGIDLoginDiagnosticsNativeTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/MvtCommittedFreshnessTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/NativeSqliteVectorRenderingTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01MapBenchmarkTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01NightSyncTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/Perf01TileObservation.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/ScriptNoticeDialogTest.java
- tools/mobile-test-plan.json
- tools/perf01_night_benchmark.py
- tools/perf01_owned_gateway.py
- tools/perf01_tile_profile_summary.py
- tools/tests/test_perf01_night_benchmark.py
- tools/tests/test_perf01_owned_gateway.py
- tools/tests/test_perf01_tile_profile_summary.py
- agent-system/knowledge/documentation/mobile-roadmap-layout.json
- agent-system/knowledge/documentation/mobile-roadmap-queue-order.json
- agent-system/knowledge/documentation/mobile-roadmap-roadmap-overview-and-authority.json
- agent-system/knowledge/documentation/mobile-roadmap-field-measurement-baseline.json
- agent-system/knowledge/documentation/mobile-roadmap-mvt-correctness-and-freshness.json
- agent-system/knowledge/documentation/mobile-roadmap-login-diagnostics.json
- agent-system/knowledge/documentation/mobile-roadmap-script-notice-scroll.json
- agent-system/knowledge/documentation/mobile-roadmap-night-verification-20261117.json
- docs/roadmap/mobile.md
- update-reports/entries/android-20261117-night-mobile-verification.md
---

PERF-01 получает ограниченный observer существующего RenderResourceProfiler,
строгую сводку тайлов, physical harness и loopback HTTP gateway только к новым
собственным NGW-ресурсам. Учитываются payload bytes/hashes/status, cache hit/miss,
queue/lock/SQL/blob/decode/style/clip/PBF/Main, Java/native/PSS/RSS. Потоки фаз
не объявляются wall latency; неполная популяция не получает p95. HTTP gateway
не измеряет TLS/TCP или прямую сетевую задержку телефона. TUS допускает только
созданные собственные upload sessions. GET oracle имеет ограниченный transport
retry; мутации автоматически не повторяются. Состояние чужих проектов/аккаунтов
и production-приложение сохраняются, generic prepare/pm clear запрещены.

4 законченные sync ячейки планшета (1/10 объектов, incoming/outgoing, n20):
p50/p95 мс соответственно 1567.57/1976.07; 9075.02/9632.70;
14432.35/14995.31; 9306.88/9976.50. Порядок: outgoing1, incoming1,
outgoing10, incoming10. Итог сверяется независимо по FID, полям/NULL и геометрии.
100-object outgoing: 18 успешных наблюдений, 19-я попытка FAILED на upstream500
(GET FID95, PUT FID96/97). Неудача не исключается из статистики; p95 не принят.
Калибровочные 22 native-фазы create/delete/photo/TUS/hold/retry/ambiguous-create
прошли, но последний host GET имел DNS-ошибку: общий receipt FAILED.

Карта: синтетические10080/50256 полигонов65 вершин, независимо заданная сетка,
камера, геометрия/FID/подписи, свежий provider cache namespace вместо удаления
чужого кэша. Единичные fresh/warm на смартфоне/планшете PASS. Длинный phone
прогон FAILED: 3176 partial/0 full кадров за120с, текущий видимый observer,
независимая сцена PASS, system_low_memory=false. Причина не доказана, full-frame
gate сохранён. Tablet20:13 fresh PASS, 14-я попытка оборвана Wi-Fi; soak NOT_RUN.
В проверочном приложении планшета остался только собственный синтетический
проект run8bc24a5e04c04fd5aaeab24514a1fe62; требуется связь для native cleanup
и восстановления прежнего active project/пяти предпочтений. Точный handoff сохранён.

AUTH native9, script native4/24profiles, MVT committed-freshness и прежние10
SQLite cases проверены адресно согласно отдельным receipts. В старом custom
marker test авторский bitmap приведён с16 к64px canvas: исходный16px давал
ровно4 solid-cyan пикселя на компонент при строгом пороге5. Порог, геометрии,
FID и production marker code не изменены; это исправление fixture, не принятие
всех размеров внешних иконок. Обнаруженные прежние content/style timing FAIL
сохранены, даже после успешного финального CPU-прогона.

92 адресных Java unit и 49 Python tool unit PASS; Related CPU/A56/T40 receipts
сохранены с каждым точным source/APK basis. Каталог69классов/331случай — это
каталог, не заявление о выполнении331теста. Общий Full не выполнялся.
Дорожная карта обновлена в native records и Markdown отдельно от code tests;
AUTH/SCRIPT исключены из новой разработки, сохранены review/merge/Full/release.
PERF и остаток MVT открыты; SYNC-01/02, FORM-01, P2P не запускались.

Собственные NGW1548–1551 и серверные PG17 база/роль/relay удалены с readback.
180 серверных read/update/delete измерений выполнены с ROLLBACK и проверкой
1000строк. Связь PG→NGW не проверялась: автоматический review отклонил хранение
нового временного пароля в NGW; переноса секрета не было. Смартфон отключён
владельцем; отклонённый после этого новый запуск не выполнялся. Общие AGENTS,
Desktop WIP, существующие сервисы и production APK не менялись.

Пакет остаётся uncommitted в собственной общей ветке; push/merge/release нет.
Сессия Desktop не финализируется. Источники, отрицательные результаты,
контекст, receipts и pending-cleanup:
`Q:/Codex/Scratch/mobile-night-20261117-01a1e5ee/retained`.
