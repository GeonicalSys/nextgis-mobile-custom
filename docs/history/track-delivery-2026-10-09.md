---
title: Индикация и автоматическая доставка трека — проверка 2026-10-09
type: history
last_verified: 2026-10-09
related_code:
  - maplib/src/main/java/com/nextgis/maplib/util/TrackSendSettings.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/TrackWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/TrackUploader.java
  - app/src/main/java/com/nextgis/mobile/fragment/LayersFragment.java
  - app/src/main/java/com/nextgis/mobile/fragment/SettingsFragment.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/TrackUploadTest.java
---

# Индикация и автоматическая доставка трека

Жёлтая отметка общего sync и строки треков использует owning-layer sent=0
только при включённой отправке и подтверждённой регистрации текущего сервера/UID.
До регистрации отметка скрыта; HTTP/сетевая ошибка сохраняет известное
подтверждение, registered=false отзывает его. Commit observer и preference listener обновляют
снимок без повторного открытия drawer/settings. Отправка включена по умолчанию,
переключатель доступен до регистрации; старый автоматически снятый false
включается один раз, последующий ручной opt-out сохраняется.

Регистрация проверяется при доставке и не сбрасывает настройку. Живой recorder
и durable worker делят один uploader/lock и подтверждают только rowid принятого
пакета. Unique immediate/15-minute periodic WorkManager задания сохраняют
canonical workspace directory; background map filename берётся из trusted
registry. Задача не переходит на активную карту при удалённом/неизвестном owner.
Подробный контракт: [sync/storage](../architecture/ngw-sync-and-storage.md).

## Проверки

- JBR 21.0.9 / Gradle 9.3.1: Lisa Debug/test APK, Lisa Release и Belka Release
  собраны. Это validation builds, production остался 3.1.2.27/221, выпуск не выполнялся.
- maplib 514/514 unit, включая шесть default/migration/opt-out проверок на
  Android 26/36; maplibui 92/92; app MapLayoutContractTest 1/1. Без skipped/failures/errors.
- Isolated read-only AVD Medium_Phone_API_36.0, Android 16/API 36 x86_64,
  emulator-5556: итоговая native regression 6/6 вместе, 27.25 s.
- Реальная WorkManager очередь получила registered=false, затем registered=true
  от loopback endpoint и отправила точки без изменения настройки/посещения settings.
  Проверены также сохранность точек до регистрации, обновление/снятие badge,
  opt-out без отправки, ошибка второго пакета при 101 одинаковом timestamp,
  owning background project при другой активной карте и прежний failed Stop/tail retry.
- Fixtures отключают real-hub отправку; временный endpoint, tracks и registry
  восстанавливаются/удаляются только по созданным тестом идентификаторам. Native
  test кратко включает Wi-Fi для network-constrained work и выключает обратно.
- Первый запуск обнаружил неверную трактовку workspace directory как файла;
  исправлены scheduler/input resolution. Второй обнаружил неполный synthetic map
  без name; fixture приведён к штатному map JSON. После этого все шесть сценариев
  вместе прошли. Production logic не обходит неудачный map.load.
- Docs validator, 7/7 tests и strict changed-file validation; результаты в PR.

Рабочий телефон не использовался. Не выполнены production GUI, реальный WebGIS
tracker с полевым GNSS, длительный screen-off/Doze и фактическое ожидание periodic
15 минут. Native подтверждает автоматические immediate/retry jobs, но не отменяет
право Android отложить фоновые задания. APK не публиковался, версии/UID/protocol
и desktop/mobile ресурсы не менялись.

## Матрица включения и порядок

Повторный fetch всех Android owners успешен. Открыты только свои Draft app #54 (my-maplibre), maplibui #34 (master)
и новый maplib #46 (master); easypicker и publisher не имеют иных PR/codex
branches. Все exact library commits записаны в consumer gitlinks.
Предыдущие a21f74c396156d7ed58855639bf8c3cf887c3e2a app и
028e1c01fcc75afa95cb1d13b3478ac938331f48 maplibui остаются предками; все категории,
обход, GPS pan и объединённое меню измерений сохранены. Отложенных требований нет.

| Требование | Владелец / commit | PR / base | Включение |
|---|---|---|---|
| Default и сохранение намерения отправки, граница с индикацией | maplib 0647400484de22f593a1fcd472dbfa0a3d845328 | #46 / master | Exact gitlink consumer; commit указан в #54 |
| Все прежние формы/категории/обход + durable upload, registration state и ACK | maplibui d70a5572980340b6cd64fb894f728d4a80b3d4e9 | #34 / master | Exact gitlink consumer; предыдущие commits сохранены |
| Все прежние creation/walk/measurements + default UI, badge, native/docs | app follow-up на a21f74c396156d7ed58855639bf8c3cf887c3e2a; LayersFragment blob 4d354abe53c456e10edf2fe61d14ca875d2edd4e | #54 / my-maplibre | Предыдущие commits сохранены |
| Easypicker | d6327f3de7a6d488a18d1896f8c97c60cd28d2a8 | remote master | Pin не менялся |

Порядок: Merge Commit maplib #46, Merge Commit maplibui #34, fetch обоих remote
merge commits и repin app, затем Squash app #54 и повторный remote inclusion audit.
Текущие pins на Draft heads служат review/tests и не закрывают release-зависимость.
Publisher не менялся; никаких integration/release branches, merge или публикации.

Official app f11d38f77e4caf1b569526c5f620b2ec513c5f9d, maplibui
d9f5241c0e8a4904b6359bba9fae4a56bd62dd33 и maplib
b8f3e3e6bf4bad56f8ce910c885ea6af1b898998 повторно проверены 9 октября.
Official SettingsFragment всё ещё отключает флаг при отсутствии регистрации.
Tracker Hub API проверен по [официальному контракту](https://docs.nextgis.com/tracker_hub_dev/source/main.html).

## Уточнение индикации после регистрации — 9 октября 2026

Значение track_send само по себе больше не включает отметку трека.
TrackRegistrationState хранит подтверждение для точной пары сервер/UID;
unknown/registered=false скрывают отметку, транспортный сбой сохраняет известное
подтверждение, смена сервера требует своего подтверждения. Cache не заменяет
сетевую проверку перед packet и не влияет на автоматический retry или sent=0.
Preference listener и stale guard снимка учитывают регистрацию вместе с intent.

- Lisa Debug/test APK и обе flavor Release validation builds: успешны,
  JBR 21.0.9 / Gradle 9.3.1, 3 min 47 s; production версия не менялась.
- maplib 514/514, maplibui 92/92; docs validator и 7/7 tests, strict diff validation.
- На том же изолированном read-only API36 AVD итоговая native regression **8/8**,
  125.742 s: семь TrackUploadTest и failed Stop/tail recovery.
- Проверены hidden badge до регистрации при enabled intent, реальный WorkManager
  retry без settings, badge при confirmed registration/rejected packet, opt-out,
  revocation, HTTP503 с сохранением badge, Activity recreation, смена hub,
  отдельный background owner и packet-specific ACK для одинаковых timestamps.
- Первый instrumentation запуск не дошёл до тестов: ActivityManager завершил
  процесс за background ANR при холодной загрузке эмулятора. После завершения
  bootstrap повторный запуск всех восьми сценариев прошёл; обходов production
  lifecycle и увеличения test timeout не добавляли. Эмулятор после тестов закрыт.
- Дополнены module packs, config/invariant/change-impact/smoke registries,
  sync/settings/user-guide/official-differences и существующие карточки отчёта.
  Полевая/production WebGIS/Doze приёмка выше по-прежнему не выполнялась.
