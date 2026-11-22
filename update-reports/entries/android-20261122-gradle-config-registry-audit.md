---
id: android-20261122-gradle-config-registry-audit
area: Android конфигурация
kind: internal
at: '2026-11-22T07:23:32+04:00'
title: Уточнить действующие контракты параметров Gradle
why: После удаления чтения ciReliabilityChecks реестр и справка продолжали относить ключ к app/build.gradle
covers:
  - agent-system/knowledge/config-keys/config-keys-build-inputs-cireliabilitychecks.json
  - agent-system/knowledge/config-keys/config-keys-build-inputs-diagnosticdeliverychecks.json
  - agent-system/knowledge/config-keys/config-keys-build-inputs-fieldverificationchecks.json
  - agent-system/knowledge/config-keys/config-keys-build-inputs-privateresourceprofiling.json
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/settings-and-config.json
  - agent-system/provenance/structured-views.json
  - docs/reference/build-matrix.md
  - docs/reference/settings-and-config.md
  - docs/registry/config-keys.yaml
---

Сверены фактические чтения диагностического, полевого и локального профильного параметров в app/build.gradle.
В канонических записях указана применимость к исходнику, который читает параметры.

Старый аргумент ciReliabilityChecks сохранён в локальном runner и идентичности сборки.
Справка и реестр отражают текущую схему и обязательные входы production Release.
Нативная проверка сборки и устройства относится к отдельным сценариям.
