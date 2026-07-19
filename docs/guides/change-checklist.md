---
title: Чеклист изменения Android-форка
type: guide
last_verified: 2026-07-19
related_code:
  - AGENTS.md
  - docs/registry/change-impact.yaml
---

# Чеклист изменения Android-форка

## До правки

- [ ] Проверить status/branch root и каждого затрагиваемого сабмодуля.
- [ ] Прочитать root и ближайший `AGENTS.md`, README и manifest модуля.
- [ ] Найти path trigger в `registry/change-impact.yaml`.
- [ ] Выписать invariant IDs и upstream hotspots.
- [ ] Определить владельца контракта: `app`, `maplibui`, `maplib` или central.
- [ ] Выбрать минимальные unit/build/device smoke.

## По типу изменения

| Область | Обязательно проверить |
|---|---|
| Публичный API `maplib` | consumers в `maplibui` и `app`, dependency registry |
| MapLibre/style/order | model order, style order, cold start, hot add, reorder |
| Collector | workspace isolation, composition diff, backup gate |
| Sync/storage | data-loss path, retries, process death, last-sync UI |
| Preferences/config | одинаковые key/default в XML и Java/Kotlin fallback |
| Flavor/version | Lisa и Belka, update flavor, app/maplib version coupling |
| Upstream | inventory, overlap decision, report, submodule pointer |

## После правки

- [ ] Unit/build checks завершены.
- [ ] Нужный device smoke выполнен или явно указан как непроверенный.
- [ ] Module README/manifest обновлены, если изменился локальный контракт.
- [ ] Central registry/docs обновлены, если изменение cross-cutting.
- [ ] `last_verified` и `docs/changelog.md` обновлены для central docs.
- [ ] Validator и docs tests проходят.
- [ ] В финальном ответе разделены фактически выполненные и оставшиеся проверки.
