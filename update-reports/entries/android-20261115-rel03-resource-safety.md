---
id: android-20261115-rel03-resource-safety
area: Мобильное приложение — REL-03
kind: user
at: '2026-11-15T16:11:49.931037+00:00'
title: Ограничены ресурсы полной синхронизации, обход сохраняется упорядоченно в фоне
why: Снизить риск потери работы при нехватке места и прерывании Android
covers:
- maplib
- maplibui
- agent-system/knowledge/app-module-manifest/app-module-key-components-rel03resourcesafetytest.json
- agent-system/knowledge/app-module-manifest/app-module-manifest.json
- agent-system/knowledge/config-keys/config-keys-preferences-walkedit-temp.json
- agent-system/knowledge/dependencies/dependencies-registry.json
- agent-system/knowledge/dependencies/dependencies-runtime-contracts-independent-walk-session.json
- agent-system/knowledge/dependencies/dependencies-runtime-contracts-ngw-sync-resource-budget.json
- agent-system/knowledge/documentation/app-module-readme-crash-recovery-ownership-and-geometry.json
- agent-system/knowledge/documentation/crash-recovery-rel03-ordered-walk-checkpoints.json
- agent-system/knowledge/documentation/crash-recovery-walk-finish-and-tail-commit.json
- agent-system/knowledge/documentation/crash-recovery-walk-journal-and-full-geometry.json
- agent-system/knowledge/documentation/crash-recovery.json
- agent-system/knowledge/documentation/documentation-changelog-2026-11-15-rel03-resource-safety.json
- agent-system/knowledge/documentation/documentation-changelog.json
- agent-system/knowledge/documentation/location-pipeline-walk-checkpoint-failure-and-invalid-sampling-defaults.json
- agent-system/knowledge/documentation/mobile-roadmap-history-layout.json
- agent-system/knowledge/documentation/mobile-roadmap-interruption-and-resource-exhaustion.json
- agent-system/knowledge/documentation/mobile-roadmap-layout.json
- agent-system/knowledge/documentation/mobile-roadmap-queue-order.json
- agent-system/knowledge/documentation/mobile-roadmap-rel03-20261115.json
- agent-system/knowledge/documentation/mobile-roadmap-roadmap-overview-and-authority.json
- agent-system/knowledge/documentation/mobile-roadmap-roadmap-review-process.json
- agent-system/knowledge/documentation/mobile-roadmap-started-work-heading.json
- agent-system/knowledge/documentation/ngw-sync-and-storage-destructive-mutation-backup-gate.json
- agent-system/knowledge/documentation/ngw-sync-and-storage-rel03-resource-budgets.json
- agent-system/knowledge/documentation/ngw-sync-and-storage-untracked-streamed-snapshot.json
- agent-system/knowledge/documentation/ngw-sync-and-storage.json
- agent-system/knowledge/documentation/project-synchronization-user-guide.json
- agent-system/knowledge/invariants/invariants-inv-backup-before-destruction.json
- agent-system/knowledge/invariants/invariants-inv-crash-draft-recovery.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-4a8815f9c3c363b96babbbb1.json
- agent-system/knowledge/ui-contracts/ui-non-method-evidence-cf6d88239faca089e51547bf.json
- agent-system/provenance/structured-views.json
- app/docs/README.md
- app/docs/manifest.yaml
- app/src/androidTest/java/com/nextgis/mobile/reliability/Rel03ResourceSafetyTest.java
- app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
- docs/architecture/crash-recovery.md
- docs/architecture/location-pipeline.md
- docs/architecture/ngw-sync-and-storage.md
- docs/changelog.md
- docs/guides/project-synchronization-user-guide.md
- docs/registry/config-keys.yaml
- docs/registry/dependencies.yaml
- docs/registry/invariants.yaml
- docs/registry/ui-contracts.json
- docs/roadmap/mobile-history.md
- docs/roadmap/mobile.md
- tools/mobile-test-plan.json
- update-reports/entries/android-20261115-rel03-resource-safety.md
---

Интеграция app: предпросмотр учитывает checkpointSequence, сохраняет видимый owner при ошибке журнала; новая emulator-only Rel03ResourceSafetyTest и отдельная Related area. Прежние selectors/phone/isolation guards сохранены. Строгие NGW/MapFragment UI source guards обновлены после содержательного review конкретного diff; остальные guards и oracles не менялись.

REL-03, по прямому поручению владельца. Только относящиеся к изменению проверки; общая регрессия, Full, планшет и публикация отложены. FORM-01 не возобновлялась. Изменения сохраняются локально; merge/push/release не выполняются. Parent pointers не переводятся на неслитые библиотечные ветки.

Изменённый контракт: частный поток полного NGW snapshot и частный журнал Android обхода. Публичный IGISApplication backup API, HTTP206 admission, SQLite/outbox atomicity, point-owner lock, другие части/отверстия геометрии и исходные категории формы сохранены. Новые owner/sequence/root keys и журнал format1 читаются поверх прежних full-WKT/part-only настроек; существующий UUID/sequence0 — миграционная база. Журнал внутри owning map входит в прежний проектный архив. Android-only: Web, Desktop/Qt, NGW publication metadata и save scripts не меняются; syntactic/semantic closure прослежена до app preview, worker, backup и архивирования. Новые подтверждённые связи внесены в own dependency/config/invariant/module records. Неподтверждённые динамические ссылки не объявляются покрытыми.

Standalone route: прежняя общая сессия CLOSED; Desktop содержит чужие изменения lab-regression, они не редактируются и не включаются. Android owners начали с clean/synchronized pinned heads, fetch и open-PR проверки проведены, hooks установлены. Собственная предыдущая сверка дорожной карты сохранена отдельным локальным docs commit86d9c5ca. Источники библиотек на своих codex/rel03-resource-safety branches.

Контекст: live owners `.`, App, maplib, maplibui; source baseline и input digests сохранены в evidence. Перед записью подтверждены 525 страниц manifest/content, независимые required IDs/fields и CURRENT (generation e873ac51b8ac025a4eecf43c8696965d7a2f800e9736bd40c58ac4a02540ff87). Зарегистрированная доставка отделена от чтения релевантных тел. Глобальный full_context_complete=false и прежние UNKNOWN/foreign observations сохраняются; это не полная семантическая приёмка экосистемы. Финальная source-frame сверка записывается отдельно.

Адресные результаты и границы: см. Q:/Codex/Scratch/rel03-resource-safety-01a1d9ac/evidence/ и final-result.json. XML, исходные неуспешные попытки, APK/input hashes и cleanup receipts сохраняются. Injected SQLite/space failure проверяет отказ/rollback, не физическое заполнение пользовательского диска. Source/JVM/emulator checks не являются физической матрицей, общим Full или выпуском. Поведение при физическом power cut и реальные low-storage/long-day устройства остаются для финальной приёмки.

Итог адресных проверок: 66/66 unit (maplib39, maplibui27; 9 классов), ноль ошибок/пропусков. LisaRelease и BelkaRelease source compile PASS с kotlin.incremental=false. Native Related: 7 различных сценариев/12 запусков PASS на Android36 Google APIs r7, Emulator37.2.13 GuestAngle/Lavapipe/WHPX, 2 CPU/2560MiB. Реальные process death, kernel reboot и отзыв GPS permissions восстановили принятый журнал; три прежние сервисные GPS-регрессии PASS. Owned VM/Job/console завершены, Job remaining0, shared ADB не перезапускался. Предыдущие host admission/environment отказы сохранены как NOT_RUN native, не скрыты.

Исходный native fingerprint: 4e69ebc6ad5e761dcab7f365c0a9281f6801dfc75c25d0d2f4dd5d258bce838a. Последующие правки ограничены knowledge/documentation/contract status; runtime/test bytes проверяются native-doc-closeout-delta.json. Этот Related receipt не объявляется актуальным Full/publication gate. Обязательная финальная сверка frame/graph/context и unknowns фиксируется в context-final-receipt.json и final-result.json; временные базы удаляются после закрытия, компактные доказательства сохраняются.

Доставка после адресной приёмки: владелец15.11.2026 прямо поручил commit/push/merge текущей работы и проверенных общих файлов. Ранние ограничения «локально/без merge» выше описывают предыдущий этап, новым поручением они заменены только для доставки; release/deployment/Full остаются отдельно. Библиотеки сливаются Merge Commit, App — Squash Merge после обновления pins до слитых commit. Runtime/test bytes с исходной квитанцией не меняются. Связанный общий реестр: lisa-20261115-rel03-resource-contract; общий Desktop/Web набор сохраняется в собственных репозиториях.

Библиотеки фактически слиты15.11.2026: Core https://github.com/GeonicalSys/android_maplib/pull/72 (`44643da7966abd33a05528a53c63ab45ef744151`), UI https://github.com/GeonicalSys/android_maplibui/pull/53 (`a2de77233b1a33accadb68c16970bf64a40b6638`). Parent pins переведены только на эти commits; runtime/test bytes совпали с исходной проверенной ведомостью.
