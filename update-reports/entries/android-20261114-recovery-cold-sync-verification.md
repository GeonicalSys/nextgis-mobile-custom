---
id: android-20261114-recovery-cold-sync-verification
area: Восстановление и фоновая синхронизация
kind: internal
at: '2026-11-14T14:20:00+00:00'
title: Проверка восстановления до открытия карты на планшете
why: Закрыть непокрытый текущей native-квитанцией путь ProjectRecoveryGuard в REL-02
covers:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ProjectRecoveryColdSyncTest.java
  - app/src/androidTest/java/com/nextgis/mobile/util/VerificationTestRunner.java
  - tools/mobile-test-plan.json
  - agent-system/knowledge/documentation/automated-tests-verification-startup-recovery-and-lease-barrier.json
  - agent-system/knowledge/documentation/app-module-readme-verification-startup-and-underlay-lease-barrier.json
  - agent-system/knowledge/app-module-manifest/app-module-key-components-verificationtestrunner-cd98e31c3f.json
  - agent-system/knowledge/documentation/mobile-roadmap-project-recovery-and-draft-preservation.json
  - agent-system/knowledge/documentation/mobile-roadmap-implementation-checkpoint-20261114.json
  - agent-system/knowledge/documentation/official-differences-cold-recovery-verification-20261114.json
  - agent-system/knowledge/documentation/official-differences.json
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/provenance/structured-views.json
  - docs/runbooks/automated-tests.md
  - app/docs/README.md
  - app/docs/manifest.yaml
  - docs/roadmap/mobile.md
  - docs/roadmap/mobile-history.md
  - docs/reference/official-differences.md
  - docs/reference/build-matrix.md
  - update-reports/entries/android-20261114-recovery-cold-sync-verification.md
---

Добавлен отдельный stationary native-класс из пяти сценариев. Явно выбранный класс получает probe в VerificationTestRunner до Application.onCreate: активной модели карты действительно нет, старый singleton accessor бросает исключение, guard читает unparented layer без ошибки и создания карты. Живая модель не сбрасывается и не подменяется. Каждый метод запускается в свежем процессе.

Фоновые сценарии используют настоящий archive restore, SQLite, очереди, account/provider API и loopback HTTP без открытия Activity. Проверяют parent/thread-scope precedence, запрет full/config/push для удержанной копии без HTTP, сохранение исходной строки/очереди/фото/form sidecar, выбранного одного отправителя, запрет повторного POST исходника и read-only reconciliation позднего ACK перед допуском копии. Активная модель/UID сохраняются. Production исходники и форматы данных не менялись; миграция saved state не нужна.

Новый класс включён в per-method isolation, Related recovery-cold-sync и phone Full. Все прежние значения плана, маршруты и emulator-only guards сохраняются. Endpoint не является живым NGW и не устанавливает backend exactly-once. Тестовые synthetic account/projects/archive закрываются и удаляются своим fixture, registry возвращается к прежним bytes; внешний finally очищает только verification packages. Системные radio/font/display настройки не меняются.

Dependency closure: App runner → новый native-класс → действующие maplib ProjectRecoveryGuard/MapBase/SyncWorkspaceSession/NGWVectorLayer и maplibui archive/registry/review/worker; plan → Related/Full selector expansion. Обратные consumers сохраняют startup barrier: early probe выполняется только для нового класса. Desktop, Web, Publisher, UI layout, permissions и внешние resource schemas не затронуты. Broad RENDER-MEMORY-ACCEPTANCE/LAYER-CHOOSER triggers прочитаны: новые renderer/dialog поведения отсутствуют, проверяются существующие stationary controls; Full/publication и old layer-chooser suites остаются отдельными gates. Канонические runbook, app README/manifest и roadmap обновлены; чужие library docs и policy не переписаны ради trigger.

Registered context delivery подтверждён независимо для actual test/runner/plan/producer и документов; model reading учитывается отдельно. Оставшиеся UNKNOWN группы общего discovery не объявлены семантически полными. Source context: live working trees verified session epoch8, foreign Desktop changes provisional and preserved; receipts recovery-cold-impact-before-02.log, recovery-cold-context-final-receipt.json, recovery-docs-context-receipt.json.

Native attempt01: первый сценарий PASS, затем штатный 60s before-test-start timeout. Attempt02: все пять actual assertions PASS, но final binding отклонён из-за изменения документационных JSON во время прогона. Обе ошибки сохранены, лимиты/admission/oracles не ослаблены. Финальная проверка неизменных входов и regression controls записывается в recovery-cold-native-03/result.json. UID logcat на T40 не поддерживается; diagnostic_failure учитывается отдельно от instrumentation assertions.

Attempt03: completed/passed, 7/7 distinct native cases, без failures/skips. Пять новых сценариев плюс существующие SQLite marker и memory-dialog controls. Source fingerprint 408fd087c6610db4e5664178877b789ec9f0124baa10f04ba748c5dd0b0b9477; точный набор 6436 входов сохранён отдельно. Последующая проверка документов потребовала заметку official-differences и регенерацию structured view proof; прежняя историческая запись сохранена без изменений. Все прежние edges layout сохраняют значения и взаимный порядок.

Локальные проверки: 244 runner unit tests PASS; docs-check -RunTests PASS (26 проверок report guard, validator и 88 проверок документации), prose projection/structured view proof согласованы. Независимое сравнение подтверждает сохранность всех прежних plan nodes/values, historical native fixtures, emulator guards, library worktrees, production кода и old official-differences edges. Ошибки последовательных docs-check attempts сохранены; guards не отключались.

После последнего обновления записей документации attempt04 собрал текущую APK-пару, первый сценарий PASS, затем Android вновь остановил процесс до старта второго теста. Package-scoped exit-info: INITIALIZATION FAILURE, PSS/RSS0, description start timeout. Сохранены startup-timeout и exit-info; assertions не начинались, это не PASS. Повтор с теми же финальными входами — recovery-cold-native-05/result.json.

На финальных inputs attempt05 прошёл все пять recovery cases и SQLite control (6 actual PASS), затем получил before-test-start timeout последнего memory control. Только оставшийся существующий memory-dialog selector завершён в attempt06: 1 PASS, completed/passed receipt. Проверено равенство обеих APK-пар, build basis и текущего fingerprint d13018b7cf88c2505a292d8b90410655c3c95f2e9dc5da1a3331a310a6200ec9. Сводка recovery-cold-summary.json содержит 7 разных завершённых сценариев на итоговой сборке, 5+2; это совокупность scoped receipts, не один успешный Full. В recovery-final-tested-inputs.json.gz сохранены SHA256 всех 6437 входов. App SHA26015ce2bb4becbf1899df6dc1f14b6d6ecbc30558875e68be00851741ecb53f; test SHAa71f5375d1f5e0af8d6a836d2ca30d41f59b28f1529e09000f4f6e8481aa9d19.

После тестов оба exact verification package остановлены и pm clear вернул Success. Процессов нет, internal data readback по4KiB, APK остаются установленными. Собственные fixture cleanup assertions проверили удаление source/copy и восстановление registry/account. Рабочие приложения не затронуты, общий ADB server сохранён. Cleanup receipt: recovery-tablet-cleanup.json. Disposable на Q пуст; retained evidence/session сохраняются до приёмки и завершения общей сессии, с проверкой необходимости 21.11.2026.

В активном остатке REL-02 снят только guard без открытой карты. Full, полный архив/офлайн-подложки/подмена/отказ записи, живой сервер, merge и публикация не объявлены завершёнными. Evidence: Q:/Codex/Scratch/mobile-closeout-01a1d9ac/evidence/.
