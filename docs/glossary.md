---
title: Глоссарий
type: reference
last_verified: 2026-07-19
related_code:
  - docs/registry/invariants.yaml
---

# Глоссарий

- **Collector project** — NGW-проект с metadata, composition и изолированным локальным workspace.
- **Composition sync** — сверка серверного состава Collector project с локальными слоями.
- **LayerGroup order** — порядок модели слоёв, где индекс `0` является нижним.
- **Hot add** — добавление слоя в уже созданный MapLibre style без полного restart.
- **Lite reload** — облегчённая синхронизация layers/style без полного пересоздания всего flow.
- **NGRc** — импортируемый локальный raster/TMS resource с особыми правилами вставки.
- **Invariant** — продуктовый контракт с постоянным ID в `registry/invariants.yaml`.
- **Upstream hotspot** — файл/область, где изменения NextGIS часто пересекаются с форком.
- **Module pack** — `AGENTS.md`, `docs/README.md`, `docs/manifest.yaml` компонента.
