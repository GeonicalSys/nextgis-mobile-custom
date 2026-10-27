---
id: android-20261027-dependency-discovery-bridge
area: Документация зависимостей и инструкции агентов
kind: internal
at: 2026-10-27T21:54:29Z
title: Обязательный самостоятельный поиск зависимостей до доработки Android
why: Условный вход только для уже распознанной desktop/mobile задачи оставлял пропуски зависимых компонентов
covers:
  - AGENTS.md
  - app/AGENTS.md
  - docs/guides/change-checklist.md
  - docs/architecture/lisa-ecosystem.md
  - docs/registry/ecosystem.yaml
  - docs/roadmap/mobile.md
  - docs/changelog.md
---

MOB-02. Любое изменение поведения/API/данных/ресурсов/config начинается с общего
автономного discovery и разбора влияния, а не с просьбы перечислить связи.
Новые подтверждённые связи сохраняются в той же задаче автоматически; кандидаты
и dynamic refs остаются с source/UNKNOWN/next step. Shared LISA procedure/index
единственные; Android registry ссылается на них без runtime-зависимости APK.

Различаются actual root/revision текущей ветки и другой доступный checkout;
недоступный владелец или отсутствующее покрытие остаётся UNKNOWN. Перед source
commit проверяется свежесть индекса, независимые ожидания под дефект не меняются.
Документация подготовлена в отдельной чистой ветке от429e980; live мобильная
задача и сабмодули не редактировались. Product/native/NGW/DB/AVD и выпуск не входят
в объём; результаты source-only docs/report проверок фиксируются в PR.
