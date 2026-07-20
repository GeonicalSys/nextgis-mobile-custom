---
title: История документационной системы
type: changelog
last_verified: 2026-07-20
related_code:
  - docs
---

# История документационной системы

## 2026-07-20

- Версия выпуска поднята до `3.1.2.2` / `versionCode` 193 с синхронной
  диагностической версией maplib; production-палитра переведена на более
  насыщенные рыже-оранжевые оттенки.
- Зафиксирован variant-specific NGW account contract: runtime, authenticator и
  sync adapter обязаны использовать один account type; добавлены invariant,
  smoke и диагностика отказа Android AccountManager. Package-name проверка
  rebuild-cache UI заменена на application capability для `.geonical`/`.debug`.
- Self-hosted updater переведён с `wiki-geonical.ru/mobile/<flavor>/stable` на
  отдельные ветки `apps-geonical.ru/lisa-mobile/{lisa,belka,debug}`; release
  contract теперь требует строгий channel/versioned URL и повторную сверку
  versionName, size, hash и signing certificate загруженного APK.
- Исправлена маршрутизация треков при переключении Collector-проектов без перезапуска процесса:
  `LayerContentProvider` разрешает текущую карту для каждой операции, карта безопасно публикуется
  между потоками, а активная запись трека блокирует смену workspace.
- `INV-COLLECTOR-ISOLATION` и `SMOKE-COLLECTOR-SWITCH` теперь явно проверяют раздельные истории
  треков, возврат в проект с сохранёнными треками и отсутствие записи в соседнюю базу.

## 2026-07-19

- Курсор текущего местоположения закреплён последним MapLibre style layer после
  cold/lite/hot reload и больше не перекрывается треками или пользовательскими слоями.
- Зафиксирован rollout-контракт Collector: неполный remote snapshot не импортируется,
  незавершённая партия переживает process death и продолжает verify/repair только для
  отсутствующих слоёв, а schema rebuild сохраняет старый слой до успешной подготовки замены.
- Реестр Collector workspace переведён на атомарную запись с backup/recovery scan;
  формы — на hash-проверку и восстанавливаемую транзакцию пары form/meta.
- Для `.ngrc` документирован и реализован `immutable_local` lifecycle с SHA-256,
  безопасной распаковкой и сохранением подложки при APK/project update. Remote lifecycle
  отложен до спецификации нового сервера.
- Синхронизация account изолирует результаты, обслуживает активный Collector account первым,
  ограничивает молчащее HTTP-чтение и восстанавливает фоновые расписания единым путём.
- Добавлен поддерживаемый агентами handoff-документ
  `reference/official-differences.md`: только актуальные пользовательские отличия от official,
  без истории и отменённых решений. Для каждой возможности описаны назначение, пользовательский
  сценарий и принцип работы. Новый strict change-impact trigger требует его пересмотра при изменении
  app behavior или submodule pointers; CI теперь применяет strict-требования к diff.
- Документирован selective upstream 3.1.2 cycle: прямой импорт NGW-ресурса по URL,
  permission/read-only contract, критические crash/form fixes и production Sentry policy.
- Матрицы, module contracts и release notes обновлены до версии форка 3.1.2.1 / 192.
- `upstream-sync.ps1` теперь использует command-scoped `safe.directory` и не
  маскирует ненулевые exit codes Git.
- Удалены невоспроизводимые upstream diff snapshots; полезные команды, выводы и результаты
  перенесены в `docs/history/upstream/`.
- `CUSTOMIZATIONS.md` сокращён до compatibility-указателя, а актуальный тематический индекс
  перенесён в `docs/reference/fork-customizations.md`.
- Collector setup/roadmap и анализ map startup перенесены из корня в проверяемую структуру docs;
  roadmap очищен от уже реализованных milestone.
- `WHATS_NEW.md` актуализирован до 3.0.3.9 / `versionCode` 187.
- Создан автоматический agent entry через root/module `AGENTS.md` и Cursor rules.
- Добавлена центральная структура architecture/guides/runbooks/reference.
- Введены машиночитаемые repositories, modules, dependencies, invariants,
  change-impact, config, upstream overlap и smoke registries.
- Добавлены module packs, scaffold, validator, unit tests и CI workflow.
- `CONTEXT_INSTRUCTION.md` переведён в совместимый redirect на новую систему.
