---
id: android-20261102-direct-vector-tiles
area: Мобильное приложение
kind: internal
at: '2026-11-02T03:00:00+04:00'
title: Проверять старые render конфиги в новой SQLite системе
why: Удаление HTTP renderer требует актуальных native regressions вместо прежних fallback fixtures
covers:
- maplib
- maplibui
- app/src/androidTest/java/com/nextgis/mobile/rendering/DirectVectorTilesOfflineTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/FireflyTileFallbackTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleTileFallbackTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/LocalVectorTileLabelTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/DirectVectorTilesTest.java
- app/src/androidTest/java/com/nextgis/mobile/rendering/NativeSqliteVectorRenderingTest.java
- app/src/androidTest/java/com/nextgis/mobile/util/MapRuntimeDiagnosticsNativeTest.java
- app/src/androidTest/assets/direct-vector-configs/kvartal.json
- app/src/androidTest/assets/direct-vector-configs/vydel.json
- app/src/androidTest/assets/direct-vector-configs/vertexfeature_recent.json
- app/src/androidTest/assets/direct-vector-configs/provenance.json
---

MOB-REL-01. DirectVectorTilesTest заменяет три удалённых server-specific
fixtures: точные опубликованные конфиги kvartal/vydel/vertexfeature_recent
с clean-source hash/provenance служат inputs. Технические SQLite rows не
являются бизнес-данными/полевой приёмкой. Независимые schema/visibility/rights,
полные row hashes и originals защищают отсутствие миграции authoritative data.
Live Firefly/advanced polygon style edit остаётся CustomVectorSource с новым
provider epoch; old generation не current. Offline quarter across tile seams
сохраняет single interior anchor и реальные red/green/blue glyph/fill/outline
expectations. NativeSqlite helpers обобщены до VectorLayer без смены oracles.
Observer diagnostics сохраняет три реальные observer/report tests; socket
queue case удалён вместе с самим сервером. Исторические HTTP report readers
оставлены для cached reports прежних APK, а не для запуска renderer.

Draft app pin закрепляет опубликованные803decb/389f3f65 из maplib66/maplibui48;
это provisional source для review, не merged final-release dependencies.
Test sources compilation PASS; execution NOT_RUN по поручению владельца.
Related/Full не подменяются компиляцией; ни merge библиотек, ни выпуск не сделаны.
