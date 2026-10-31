---
id: android-20261031-merged-library-contract-delivery
area: Поставка мобильных зависимостей
kind: internal
at: '2026-10-31T03:06:28.819881+00:00'
title: Закрепить смерженные библиотеки и подтвердить текущий контракт clock
why: Функции и инструкции должны ссылаться на опубликованные merge-коммиты; F61 контракт
  проверяется по смыслу перед обновлением.
covers:
- maplib
- maplibui
- docs/registry/repositories.yaml
- docs/registry/ui-contracts.json
- docs/architecture/ui-contracts.md
- docs/architecture/ngw-sync-and-storage.md
---

Core63 Merge Commit `baa94f361101051b09f762d7eda1f36466436d5c` и UI46 Merge Commit `be0ed0c4933573b99752b4e447f088951b88120b` проверены по ancestry и точному дереву принятой ветки. App gitlinks указывают на эти фактические ревизии.

В каталоге рассмотрены getter, publisher и private-file clock contract. Whole
прежний F61 source row, UI contract и source context сохранены как история.
API того же SharedPreferences owner не публикует собственную false/throwing
попытку; вложенный успех и поздняя успешная меньшая дата допустимы. UUID key,
файл и long format прежние. Физический failed-write/restart и APK UNKNOWN.

Три expected_branch в central repositories.yaml согласованы с .gitmodules и
read-only GitHub default_branch=master. Чужой checkout EasyPicker не изменён.
Git checkout преобразовал смешанные окончания строк ProjectSyncState в CRLF;
reviewed normalized full-file791c, merged Git blob и semantic methods совпадают.

Свежие UI source check и full docs validator: exit0. Документационные57 тестов
PASS49.269s. Сохранённые19 clock regressions и текущий Belka compile exit0 относятся
к своим точным исходникам; повторного запуска19 после byte-equivalent checkout
не было. Старые F61 failures сохранены с исходным результатом.

Эта карточка фиксирует pre-commit snapshot App86; фактический Squash Merge App
проверяется отдельно по PR/delivery receipt. Wiki/phone/native runs/APK release
не выполнялись.
