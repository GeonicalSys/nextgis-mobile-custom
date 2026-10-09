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
только при включённой отправке. Commit observer и preference listener обновляют
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
Предыдущие a73bdddddc8947884d0a7b3b7f1490c2a933c0a3 app и
ca1bac8127d976d3cfdea5dd415541164d0af82a maplibui остаются предками; все категории,
обход, GPS pan и объединённое меню измерений сохранены. Отложенных требований нет.

| Требование | Владелец / commit | PR / base | Включение |
|---|---|---|---|
| Default и сохранение намерения отправки | maplib c25ab629e5aba9a17dc7295490170c48c676aa47 | #46 / master | Exact gitlink consumer; commit указан в #54 |
| Все прежние формы/категории/обход + durable upload и ACK | maplibui 028e1c01fcc75afa95cb1d13b3478ac938331f48 | #34 / master | Exact gitlink consumer; предыдущие пять commits сохранены |
| Все прежние creation/walk/measurements + default UI, badge, native/docs | app follow-up на a73bdddddc8947884d0a7b3b7f1490c2a933c0a3; LayersFragment blob ed651553260a91497303120a75f9aee3cfb9f045 | #54 / my-maplibre | Предыдущие девять commits сохранены |
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
