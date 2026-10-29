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

Продолжение той же MOB-02/карточки: read-only discovery наблюдает все текущие
деревья, а воспроизводимый generated index использует live root + foreign HEAD.
OWN mobile working repos явно включаются через `--live-repository`; PR/evidence
сохраняют source_context/basis/SHA и отдельно provisional foreign observations.
HEAD не подтверждает публикацию/приёмку или чужие uncommitted bytes. `--check`
повторяет stored basis; логический ID не подменяет физический Q-worktree.
Shared --write автоматически сохраняет исторические provisional source observations
в dependency-observations.json с source/hash/HEAD proof, status/next step;
проверка структуры истории не является freshness/runtime-приёмкой. Android
registry ссылается на этот общий файл, не создавая его копию.

Обычный вход упрощён до одного `plan --changed PATH`: global current manifest/
hashes проверяются до impact и discovery обновляется при новых источниках,
включая неизвестного consumer до изменения producer. Standalone readonly
inventory необязателен; manual semantic closure и автоматическое сохранение
новых подтверждённых связей остаются обязательными.

Полная source-only проверка `tools/docs-check.ps1 -RunTests` выполнена после
подключения трёх библиотек на точных закреплённых Git-коммитах: validator0errors,
24 docs tests и26 reportguard tests PASS;94 карточки проверены. Прежние821 ошибки
неполной копии исчезли. Указатели библиотек и занятые рабочие деревья не изменены;
Gradle/native/device-приёмка этим результатом не подтверждается.
