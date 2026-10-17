---
title: Чеклист изменения Android-форка
type: guide
last_verified: 2026-10-17
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
- [ ] Для desktop/mobile изменения определить publisher/consumer по
  `registry/ecosystem.yaml` и прочитать инструкции внешнего проекта-владельца.
- [ ] Выбрать минимальные unit/build/device smoke.

## По типу изменения

| Область | Обязательно проверить |
|---|---|
| Публичный API `maplib` | consumers в `maplibui` и `app`, dependency registry |
| MapLibre/style/order | model order, style order, cold start, hot add, reorder |
| Collector | workspace isolation, composition diff, backup gate |
| Sync/storage | data-loss path, retries, process death, last-sync UI |
| Preferences/config | одинаковые key/default в XML и Java/Kotlin fallback |
| Flavor/version | Поддерживаемый AGP API, Lisa Debug + обе production release, APK metadata через `aapt`, app/maplib coupling; не доверять имени APK |
| Upstream | inventory, overlap decision, report, submodule pointer |
| NGW/Collector между QGIS и Android | server resource identity/schema/permissions, publisher docs, consumer smoke |

## После правки

- [ ] Unit/build checks завершены.
- [ ] Для version change выполнен `tools\verify-apk-version-matrix.ps1`, включая
  debug и обе release; handoff не содержит непроверенный Gradle DSL.
- [ ] Нужный device smoke выполнен или явно указан как непроверенный.
- [ ] Module README/manifest обновлены, если изменился локальный контракт.
- [ ] Central registry/docs обновлены, если изменение cross-cutting.
- [ ] При изменении межпроектного контракта обновлены `ecosystem.yaml`,
  основной документ и docs фактического publisher/consumer.
- [ ] `last_verified` и `docs/changelog.md` обновлены для central docs.
- [ ] Validator и docs tests проходят.
- [ ] В финальном ответе разделены фактически выполненные и оставшиеся проверки.

## Проверки доработки и публикации

- После доработки — `tools\verify-mobile-tests.ps1 -Scope Related -Area <область>`
  на эмуляторе; полный прогон после каждой правки не требуется.
- До публикации, после последней версии/кода и dependency closure — Full на
  смартфоне и эмуляторе, затем Check-Publication. Required native skips и старый
  source receipt не подтверждают готовность.
- Новый native класс классифицировать в `tools/mobile-test-plan.json`.
- Если разрешённый проект 1403 не найден, спросить пользователя о замене,
  не выбирать другой проект самостоятельно.

Команды и изоляция: [автотесты](../runbooks/automated-tests.md).

При обработке error reports отдельно отмечать воспроизведение, исправление,
диагностическое улучшение и выпуск. Для delayed AppExitInfo сверять исходную
identity и report_origin, для sync cancellation проверять disk enqueue/ACK.
Related bugreports входит в CI; новый Full catalogue (207 cases) нужен перед
публикацией APK после этой доработки.
