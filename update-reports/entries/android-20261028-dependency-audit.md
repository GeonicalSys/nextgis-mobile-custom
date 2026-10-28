---
id: android-20261028-dependency-audit
area: Межкомпонентные source-контракты ЛИСЫ
kind: internal
at: 2026-10-28T01:25:20Z
title: Связать Android с общими сценарными и ресурсными зависимостями всей ЛИСЫ
why: Одних imports и цепочки NGW недостаточно для учета roles, templates, SQL resources, diagnostic queues и release saved state
covers:
  - docs/architecture/lisa-ecosystem.md
  - docs/registry/ecosystem.yaml
  - docs/changelog.md
---

MOB-02. Добавлена source-only граница ECO-SHARED-SOURCE-CONTRACTS со ссылками на
общие scenario/resource registries и layer-resource-audit.json. Обзор включает
условия и защищённое состояние desktop install/roles, SQL/QLR/resources,
форм/drafts, diagnostics/usage и Android build/release/update. Это отдельные
контракты, не новая runtime-последовательность или файловый импорт desktop в APK.

Source proof/UNKNOWN, текущие observations, delivery, native приёмка и публикация
разделены. Работа выполнена в изолированном Q checkout с pinned библиотеками;
занятые C Android инструкции/код не редактировались. Старая карточка
android-20261027-dependency-discovery-bridge не расширялась новым scope.
Проверки — существующий source-only tools/docs-check.ps1 -RunTests; фактические
результаты фиксируются отдельно. Product/Gradle/AVD/PG/Office/network services
и публикация не входят в эту документационную доработку.
