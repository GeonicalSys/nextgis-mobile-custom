---
id: android-20261029-storage-move-ownership
area: Карта и синхронизация
kind: user
at: 2026-10-29 21:33:21+00:00
title: Перенос карты и сброс настроек согласованы с фоновыми операциями
why: Прежний перенос запускался поверх работающей синхронизации, а сброс менял настройки до окончания перемещения
  карты
covers:
- app/src/main/java/com/nextgis/mobile/MainApplication.java
- app/src/main/java/com/nextgis/mobile/fragment/SettingsFragment.java
- app/src/main/java/com/nextgis/mobile/util/SelectMapPathPreference.java
- app/src/main/res/values/strings.xml
- app/src/main/res/values-ru/strings.xml
- app/src/test/java/com/nextgis/mobile/fragment/SettingsWorkspaceMoveTest.java
- app/AGENTS.md
- app/docs/README.md
- app/docs/manifest.yaml
- docs/architecture/ngw-sync-and-storage.md
- docs/architecture/ui-contracts.md
- docs/changelog.md
- docs/registry/change-impact.yaml
- docs/registry/invariants.yaml
- docs/registry/smoke-tests.yaml
- docs/registry/ui-contracts.json
- docs/roadmap/mobile.md
- docs/reference/official-differences.md
---

Состояние: адресная source-доработка завершена в собственной ветке; merge,
поставка библиотек и native-приёмка остаются отдельными этапами. Основание — прямое поручение владельца
29 октября исправлять явные ошибки при углублении аудита, существующий пункт MOB-02.

До любых изменений оба входа — выбор хранилища и сброс настроек — должны
получить исключительное владение картой. Занятый проект сохраняет настройки,
черновики, отображение и работающую операцию. Владение продолжается до окончания
перемещения и перепривязки интерфейса; одно cancelSync не является ожиданием
завершения чужой работы. Путь публикуется по фактической карте, а не по
выбранному заранее каталогу. Сброс не выполняет оставшиеся действия над картой,
если перемещение не завершилось в целевом каталоге.

Возврат RESULT_OK означает запрос перепривязки экрана, а не подтверждение
успешного переноса. После retirement при обычном отказе/отмене перепривязка
требуется и для восстановления отображения; она допустима только для точного
владельца при согласованных actual path и текущих cached preferences. Исключён
публикуемый объект карты с одним путём при preferences другого пути.

На прежнем продукте фактически выполнены6 Robolectric-проверок:5 адресных FAIL,
1 штатный контроль PASS, errors0. Проверялись реальные preference/reset callbacks,
настоящий coordinator и очередь AsyncTask; данные лесоустройства и GIS/SQLite
движок не использовались. Offline unit-resource archive AGP нужен для JUnit;
обычный APK, его assemble/sign/install, эмулятор и устройство не запускались.
После исправления21 actual-body JVM-проверка PASS, errors/skips0,5.032с; LisaDebug
Java/Kotlin compile входит в этот прогон, отдельный BelkaRelease compile PASS31с.
Независимый разбор финальных шести SHA — CLEAR. Дополнительные проверки покрывают
разрешённые preference/Reset callbacks, запрет нового sync внутри UI effects,
ожидание реально начавшегося worker при отмене, обычный false после изменения
cache, недостигнутый target, старые callbacks и неожиданный worker Runtime/Error.
Также проверены direct-field owner guards без factory getMap, atomic rebind,
orientation до/после получения lock и вычисление default directory после lease.
Модели renderer/DB проверяют порядок и принадлежность вызовов, не файловые байты.

Report guard и26 policy tests PASS. Полный текущий UI/docs gate NOTPASS: один
известный WF-SYNC-CLOCK-STORE drift из-за live maplib clock fix при сохранённом
app F61 pin. Отдельный docs suite:57 выполнено,53 PASS/4 FAIL, все четыре — тот же
clock drift. Прежние IDs/history и clock row сохранены; источник несовпадения
устраняется обычной library Merge Commit→app gitlink/semantic pin доставкой.

Retirement запрещает новые запросы рендера, но не доказывает ожидание каждого
чтения БД. Частичный файловый rollback, durable path при отказе записи/рестарте,
миграция registry/очередей/черновиков и полная native-приёмка остаются отдельными
вопросами общего аудита. Library clock PR63 и последующая доставка app gitlink/
UI pins не подменяются этой доработкой; merge и выпуск не выполнялись.
