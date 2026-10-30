---
id: android-20261030-sync-admission-regression
area: Надёжность синхронизации NGW
kind: internal
at: '2026-10-30T07:59:34.596633+00:00'
title: Регрессии допуска полного снимка и ошибки сохранения справочника
why: Зафиксировать независимые scoped oracles15-class и совместного19 без расширения native приёмки.
covers:
- app/src/test/java/com/nextgis/mobile/util/NgwLookupCacheSyncTest.java
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/architecture/ngw-sync-and-storage.md
- docs/guides/project-synchronization-user-guide.md
- docs/registry/invariants.yaml
- docs/registry/change-impact.yaml
- docs/registry/smoke-tests.yaml
- docs/changelog.md
- docs/roadmap/mobile.md
- docs/reference/official-differences.md
---

Новый15-class сохраняет actual-public NGWLookupTable.sync oracle для current false-save
IO signal:8 старых controls побайтово прежние, один false-save case уточнён и6 добавлены.
Новый ID не дописывает поведение старой key-set карточки. App не переносит GIS/storage
producer из Maplib и не меняет production UI, resources, pins или catalog.

Actual shared19 имеет12 native args с --continue и двумя per-task filters:
Core4 private downloader +App15 public sync. Baseline19:4 named AssertionFAIL/
15controls/0technical (Core1.968с/App2.277с), Gradle1/57с. Core failed в первом
assertNull, поэтому более поздние206 assertions в RED не исполнялись. App3 доходят
до additive IO assertions после memory/identity/writecount/other-stat controls.
Те же fixtures/oracles GREEN19PASS/0fail/error/skips (Core1.905с/App2.298с),
Gradle0/35с; standalone15 отдельно не запускался.155 baseline event rows дают153unique:
оба test start+FAILED дублируются; BUILD FAILED57с — stderr. Не149 и не ошибка harness.

C0a4 два false-save checks и ca59 четыре строки206 admission имеют whole-file
inverses8ca/f20a. ТестApp SHA7d697ccd89a791e68829a968e78f8d43d128ce8dfc91000daf8d08a3d57fba69;
Core SHA7ae829c25e4465dac18b18f1d65771d538d59e01839f82d14c008f1796e78867.
Core206 guard относится к этому полному apply: prior push/prefix cleanup до GET прежние.
App15 сохраняет new cache memory/owner/one save/throws/equal-next behavior. Physical
save, SQL/full apply, реальные NGW/аккаунты/геометрия, native/device не проверялись.

BelkaRelease Java/Kotlin source compile0/39с,87actionable6executed81up-to-date:
100 unique tasks SET равен reviewed100, порядок другой; App Java/Kotlin и changed
Maplib Java исполнились.4 Release Java JAR28/530/669/45classes не содержат dex/so/
APK/AAB/AAR; все7 owned PID завершились естественно, source24/runtime12/UI3981 прежние.
Первый compile dry observer не сохранил exit из-за postdiagnostic Get-FileHash:
UNKNOWN/NOT_PASS и stop9 сохранены. Исправлен только owned Q helper, затем dry0/11с,
затем actual compile один раз. Старое событие не стало PASS, full CI/native не запускались.

Полная public recipe с точными shared12/compile7 argument arrays, JBR21.0.9,
Gradle9.3.1/AGP9.1.0/Robolectric4.16.1/JUnit4.13.2/API28 и Q temp/cache находится в
[Core Draft PR #63](https://github.com/GeonicalSys/android_maplib/pull/63),
репозиторий GeonicalSys/android_maplib, точный файл
`update-reports/entries/maplib-20261030-full-snapshot-http206.md`.
Эта ссылка не проходит через held F61 submodule, где новой карточки ещё нет;
ни Q-only helper, ни scratch receipt не являются зависимостью воспроизведения.
Executable registry command дублирует только argv. Standalone App filter остаётся
валидным для15, но новый actual результат получен через shared19, не отдельным запуском.

[Центральный контракт](../../docs/architecture/ngw-sync-and-storage.md) сохраняет
AUDIT-TODO-NGW-PARTIAL-SNAPSHOT (partial200/прочие routes),
AUDIT-TODO-NGW-LOOKUP-CACHE-PERSISTENCE (disk/retry),
AUDIT-TODO-NGFP-LOOKUP-PREFETCH (samehash/readiness/shared deletion) и
AUDIT-TODO-NGW-ACCOUNT-REMOVAL-BACKUP (account27). Текущий IO блокирует pair/full-clock
подтверждение в source ProjectSyncRunner; это не отдельный runtime orchestration test.
Ранее acknowledged push/другойaccount не откатывается; новый network text/retry не обещан.

Old keyset9/clock19/journal22/storage21/prefetch12 и desktop1117/source1192prepared
не перезапускались. UI whole39816578/39contracts142methods19files/F61 clock row неизменны.
Metadata closure: App report128cards/10source PASS,26 policy tests PASS19.097с;
App full docs NOT_PASS только на прежнем WF-SYNC-CLOCK-STORE. Отдельные57 docs
tests53PASS/4same clock failures48.191с. Root full docs NOT_PASS только на
excluded Wiki REVERT.md frontmatter; Core report56/5 PASS. Plan26 exit0/UNKNOWN,
все4 source/test paths MAPPED. Shared source index --write0/80.673с, --check0/
77.941с:5742files/source2cd5db27;4 own live repos и2 foreign committed HEAD,
188 historical observations byte-exact. Эти результаты не являются native PASS.
Current own library source до Merge Commit/pointer отдельно от
принятия App с parent F61. Source Draft доставка разрешена; merge/APK/release/deploy/
Related/Full/native требуют обычных отдельных gates и не исполнялись этим срезом.
