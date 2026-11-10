---
id: android-20261110-ngid-release-guard
area: Вход в мобильное приложение
kind: user
at: '2026-11-10T16:54:09+04:00'
title: Восстановить вход в NextGIS ID в ЛИСЕ и БЕЛКЕ
why: В версии 3.1.2.31 обе опубликованные сборки отправляли неверный идентификатор клиента
covers:
  - app/build.gradle
  - tools/verify-apk-version-matrix.ps1
  - maplib
  - agent-system/knowledge/config-keys/config-keys-build-inputs-maplibui-client-id.json
  - agent-system/knowledge/invariants/invariants-inv-mobile-publication-acceptance.json
  - agent-system/provenance/structured-views.json
  - docs/registry/config-keys.yaml
  - docs/registry/invariants.yaml
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/flavors-and-versioning.json
  - agent-system/knowledge/documentation/release-apk.json
  - agent-system/knowledge/documentation/official-differences-signed-apk-update.json
  - agent-system/knowledge/documentation/official-differences.json
  - agent-system/knowledge/documentation/release-note-3.1.2.32.json
  - agent-system/knowledge/documentation/release-notes.json
  - agent-system/knowledge/documentation/documentation-changelog-2026-11-10-ngid-release-guard.json
  - agent-system/knowledge/documentation/documentation-changelog.json
  - agent-system/knowledge/documentation/mobile-roadmap-release-ngid-226.json
  - agent-system/knowledge/documentation/mobile-roadmap-layout.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-fb87c1c299a5598abcd742f1.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-66447f73f07d22eb763cd5f7.json
  - docs/registry/ui-contracts.json
---

MOB-REL-02. Версия Release 3.1.2.32 / 226. Сборка требует действительный
`client.id`; штатный издатель проверяет конфигурацию внутри подписанного APK.
Версия Debug и правила входа не меняются. Локальная диагностика — AUTH-01.
Тестовые прогоны этого выпуска не выполняются по поручению владельца;
отдельно проверяются подписанные артефакты, dry-run и публичная выдача.
