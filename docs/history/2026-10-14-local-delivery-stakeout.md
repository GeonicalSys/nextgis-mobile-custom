---
title: Доставка правок, время проекта и независимый вынос — проверка 2026-10-14
type: history
last_verified: 2026-10-14
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/LocalChangesWorker.java
  - maplib/src/main/java/com/nextgis/maplib/util/ProjectSyncState.java
  - app/src/main/java/com/nextgis/mobile/util/ProjectSyncRunner.java
  - app/src/main/res/layout/layout_stakeout_panel.xml
  - app/src/main/res/layout/layout_azimuth_panel.xml
---

# Продолжение GPS Draft: доставка и независимый вынос

## Подтверждённый случай телефона

Read-only ADB Samsung SM_A566B / Android 16, production 3.1.2.28/222:
открытый Collector проект относится к подключению A. В его полевом линейном
слое сохранён объект с готовой NEW записью outbox, без TEMP/NOT_SYNC.
Android account schedule подключения A выключен. Показанные 04:46 записал
успешный обмен подключения B с «Мои треки»; целевой линейный слой пропущен
как принадлежащий другой account. Имена сервера/проекта и бизнес-данные
не включены в опубликованный отчёт проверки.
База и настройки телефона не менялись, production sync не запускался.

## Аудит перед validation builds

Fetch/preflight и GitHub inventory 2026-10-14: во всех пяти Android repositories
открыты только свои Draft #60/#52/#37 ветки codex/gps-background-recovery,
author geoglyth. Других codex branches после release base нет. Это продолжение
тех же Draft по прямому запросу пользователя, не новая integration/release ветка.

| Repository | Проверенный base / Draft head до расширения | Требование в проверяемом checkout | Merge-state / dependency |
|---|---|---|---|
| android_gisapp | 7e794a440b846af5b07f593f9fee2e9d545d394f / c95ba232be14decd1818fe7d215cc689d73c5460, #60 | Предыдущий background GPS плюс project full clock, startup local delivery, независимые панели и native regressions | Draft, unmerged; использует изменённые maplib и maplibui |
| maplib | 28a4300ae308c6f531c9b8073382f09c1d6ad052 / 725ebc6f02227582fe84bb12faa0d21579bf798c, #52 | Предыдущие GPS API плюс owning-map commit callback, отдельные Save-флаги и ProjectSyncState | Draft, unmerged; Merge Commit до consumers |
| maplibui | d5313944b9ca776819cfc3a28f99e5a691fe4a09 / a025415f0ccfcf47bf11a3c27387fbd8cd5c43b3, #37 | Ранее docs-only; теперь GISApplication callback и LocalChangesWorker runtime | Draft, unmerged; зависит от нового maplib API |
| easypicker | d6327f3de7a6d488a18d1896f8c97c60cd28d2a8 | Не меняется | Уже присутствует в release base |
| upload_mobile | ff3ab8d1404ce5ec52b9b61f19b9297d466f52e5 | Не меняется | Publisher не запускается |

Проверка ancestry сохраняет исходные GPS commits в тех же branches. Content diff
target bases → working checkouts включает оба этапа. Validation builds используют
содержимое этих checkouts; незамерженные library pins не разрешают publication.
Desktop C:\dev\lisa main чистый, но после fetch отстаёт от origin/main на один
чужой commit; это вне Android scope, desktop branch и файлы не изменялись.
Preflight exit 2 также отмечает собственные ahead Draft commits и task dirty edits,
которые не являются foreign overlap. Эти состояния учтены, а не сброшены.

## Проверка

Первый native delivery/UI проход: 18 tests passed на isolated API 36 emulator,
включая saved line HTTP POST при disabled full account schedule, offline/503
retry, PUT/DELETE, TEMP Save, owner isolation, full-project clocks и text/touch
матрицы независимых панелей. Последующий итоговый проход дополняет это commit
во время работающего HTTP job и отсутствие звука измерения без смены preference.

Документационный validator полного base→task diff с --enforce-diff и семь его
unit tests прошли, включая повторную проверку после возобновления задачи.
Версии приложения не меняются.

Финальные unit results: maplib 550, maplibui 92, app 126 — 768, failures=0.
Version matrix успешно собирает Lisa Debug 3.1.2.23/218 и Lisa/Belka Release
3.1.2.29/223, проверяет package, coupled maplib VERSION_NAME и OpenGL dependency.
maplibui assembleDebug также прошёл. Native regressions: 38 tests passed,
включая commit во время реального HTTP job, outbox/pending badges, offline sync,
track delivery и background GPS. StakeoutForegroundServiceTest прошёл с grant
и deny; условные permission cases выполняются в соответствующем запуске.

Отдельный native проход (2 tests passed) проверяет реальный список слоёв при
background scope другого проекта и реальное окно измерения/Close/Cancel.
Время видимого проекта берётся из активной карты, а не из scoped background map.
Изолированный fixture временно отключает интро и location/battery warnings и
восстанавливает исходные настройки; production поведение предупреждений не
меняется. Это устраняет перекрытие проверяемого диалога другим окном.

Повторный fetch/inventory при возобновлении подтвердил те же собственные
Draft #60/#52/#37, без дополнительных открытых PR или codex branches в пяти
Android repositories. maplib/maplibui и publisher чистые; root dirty содержал
только перечисленные правки этой задачи. Прерванный UI проход сохраняет
успешные результаты первых 19 профилей; остальные выполняются после нового
запуска изолированного эмулятора с теми же проверенными APK.

Итог: все 36 профилей реального русского UI прошли — density 420/540/617,
font scale 1.0/1.3/2.0, светлая/тёмная темы, portrait/landscape. Отдельные
native матрицы независимых панелей и статуса полной синхронизации покрывают
ширины 320 и 280 dp. Проверяются glyph/line bounds, неперекрытие действий и
реальные нажатия Close/Cancel. Финальные screenshots при density 617,
font scale 2.0 в обеих ориентациях просмотрены: текст и кнопки доступны,
измерение не содержит стрелки/звука/точности выноса.

Повторная version matrix после выбора видимой активной карты также прошла.
Проверки выполнялись на isolated API 36 emulator с synthetic diagnostics DSN
и loopback NGW; production данные и серверы не участвовали. Реальный расход
аккумулятора и восстановление GNSS в лесу на Galaxy Tab этим проходом не измерялись.

Публикуемые library task tips: maplib 8ed861af72c2a79315de7c8f96f562467d09b3a5,
maplibui 76cc581bb84866026b46a9ebd0b3a73fb58e3240. Ancestry исходных GPS commits
проверен; runtime расширения входит в эти commits. Root pin остаётся review pin
на unmerged Draft, не разрешением выпуска.

Порядок integration сохраняется: Merge Commit maplib → maplibui → fetch → root
pins на удалённые merge commits → app Squash Merge. Merge, version bump,
production phone install и APK publication не выполняются этой задачей.
