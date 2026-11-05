---
id: android-20261105-unused-geo-asset
area: Документация
kind: internal
at: 2026-11-05T10:39:39+04:00
title: Убран неиспользуемый пустой ассет geo.json
why: Убрать из упаковки пустой файл без потребителя и закрыть ошибку разбора в MOB-DEV-02
covers:
  - app/src/main/assets/geo.json
  - agent-system/knowledge/documentation/official-differences-batch-load-and-hot-add.json
  - docs/reference/official-differences.md
  - update-reports/entries/android-20261105-unused-geo-asset.md
---

MOB-DEV-02, пункт 4 общего плана миграции. Файл стал пустым в коммите
`b9dd9d628c0bb3790be93d3965e09d56cf34631a`. Этот Git-объект и диагностическое сообщение
сохраняются в исторической сверке.

Оба вызова загрузчика стиля используют `ngwstyle.json`. Шрифты читаются из
`fonts/`, иконки маркеров из `marker_icons/`. Сверка исходников App и
библиотек охватила закреплённые ревизии и текущие Draft. Lisa Debug,
Lisa Release и Belka Release берут ассеты из общего `main/assets`.
