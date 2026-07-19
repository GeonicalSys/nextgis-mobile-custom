---
title: Сверка layer fill с upstream — 2026-03-30
type: history
last_verified: 2026-07-19
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java
  - maplib/src/main/java/com/nextgis/maplib/util/GeoJSONUtil.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/LayerFillService.java
---

# Сверка layer fill с upstream — 2026-03-30

Старые полные diff-снимки удалены: они были построены на прежних submodule SHA и не применяются
обратно к текущему коду. Git history сохраняет исходные файлы, а новую сверку нужно получать
непосредственно из выбранного upstream ref.

```bash
cd maplib
git fetch https://github.com/nextgis/android_maplib.git <upstream-ref>
git diff FETCH_HEAD HEAD -- \
  src/main/java/com/nextgis/maplib/map/NGWVectorLayer.java \
  src/main/java/com/nextgis/maplib/util/GeoJSONUtil.java

cd ../maplibui
git fetch https://github.com/nextgis/android_maplibui.git <upstream-ref>
git diff FETCH_HEAD HEAD -- "**/LayerFill*.java"
```

## Выводы исторической сверки

- Fork использовал writable DB, bulk import и SQLite transaction с пакетными commit для больших
  NGW/GeoJSON fill; прогресс обновлялся с ограниченной частотой.
- HTTP timeouts и локальный размер SQLite batch — разные настройки и не должны смешиваться.
- `LayerFillService`/диалог и связанная batch-логика заметно отличались от upstream.
- Ручная проверка слоя примерно на 28 тыс. объектов с включённым экраном не выявила ANR на fill.

Эти выводы являются историческим контекстом, а не гарантией текущего поведения. При изменении fill
проверяйте код и актуальный upstream заново по [runbook](../../runbooks/upstream-sync.md).
