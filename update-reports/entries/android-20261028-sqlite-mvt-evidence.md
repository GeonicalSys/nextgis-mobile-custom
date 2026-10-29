---
id: android-20261028-sqlite-mvt-evidence
area: Проверки памяти карты
kind: internal
at: 2026-10-28T05:12:00+01:00
title: Независимый разбор PBF между SQLite provider и native SDK
why: Native03 сохранил правильные rich original paints, но rendered Fill и Symbol расходятся, поэтому требуется отдельное свидетельство реального PBF без вызова encoder helper
covers:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/SqliteMvtEvidence.java
---

MOB-04. Новый test-only `SqliteMvtEvidence.summarize(byte[])` читает wire MVT
независимым минимальным parser. Логика protobuf reader перенесена из независимого
`NativeVectorTileEncoderTest` и усилена bounds checks; runtime import теста или
production encoder helpers отсутствует. В output: layers name/version/extent,
количество записей и типов1Point/2Line/3Polygon/0unknown. Для логических
`featureid`31/62/99 сохраняется максимум12 details с отдельным unsigned wire ID
и только `featureid`, `fillcolor`, `opacity`, `geometry_layout_key`,
`_label_anchor`, `signature`. Native wire ID не принимается за business FID,
если свойство featureid присутствует. Координаты/geometry commands/произвольные
properties, raw bytes и сообщения исключений не выводятся.

Bounded input4MiB; не более8layers,65536records,8192keys и8192values на layer,
262144wire fields и1048576varints. Проверяются wire type/length/varint overflow,
packed и unpacked tag pairs, dictionary indexes, scalar types и finite numbers.
Geometry packed varints проверяются без декодирования координат. Двухпроходное
чтение учитывает dictionaries после records, сохраняя views исходного buffer
без копий geometry. JSON≤12KiB; при необходимости убираются только optional
focus details с явным `focus_details_omitted`, counts не прореживаются.
`status`/`complete` отделяют decoded от missing/oversized/invalid; unknown wire,
malformed input или budget limit не выдаются за успешную пустую карту.

Это диагностический helper для существующего технического verification corpus,
не новая бизнес-фикстура и не самостоятельное доказательство корректной
отрисовки. Вызов из NativeSQL test и actual failure evidence интегрирует другой
batch. **Свежие compile/unit/native проверки helper ещё не запускались**;
исторический encoder unit PASS не является его новым executed result.
Production API/geometry/style/cache/data/sync/draft/настройки не меняются,
helper не входит в production APK, миграций нет. Причина SIGABRT224 этим не
устанавливается; APK не опубликован.

Whole-contour read-only preplan: `sqlite-mvt-evidence-impact01.json` в evidence
текущей задачи на Q, UNKNOWN/REFRESHED_IN_MEMORY. Dirty foreign desktop owner
не редактируется; root сохраняет обязательный canonical-index follow-up.
Test closure: NativeSQL failure witness → real provider.load PBF → independent
helper → bounded JSON evidence. Локальные app README/manifest обновлены.
Новый source fingerprint требует общей проверки после source freeze.
