---
id: android-20261126-desktop-20261126-klph-mobile-field-maps
area: WebGIS КЛПХ
kind: user
at: '2026-11-26T19:05:29.754886+00:00'
title: Два мобильных проекта КЛПХ с отдельными стилями
why: Согласованные завершённые изменения рабочей сессии
covers:
- agent-system/knowledge/documentation/mobile-roadmap-collector-global-discovery.json
- agent-system/knowledge/documentation/mobile-roadmap-layout.json
- agent-system/knowledge/documentation/mobile-roadmap-queue-order.json
- agent-system/knowledge/documentation/mobile-roadmap-vector-label-line-breaks.json
- docs/roadmap/mobile.md
- update-reports/entries/android-20261126-collector-global-discovery-roadmap.md
- update-reports/entries/android-20261126-desktop-20261126-klph-mobile-field-maps.md
- update-reports/entries/android-20261126-vector-label-line-breaks-roadmap.md
---

Два мобильных проекта КЛПХ с отдельными стилями

Решения и зависимости:

- Эти три файла изменены мной в этом же чате при добавлении MAP-01. В них нужно добавить вторую запись по просьбе пользователя.
- Я создал эти два файла MAP-01 в этом чате. Пользователь разрешил зафиксировать подготовленные правки в текущих ветках.
- Созданы папки №980 и №990 прямо под №219. Проекты №989 и №1001 содержат 4 и 5 собственных PostGIS-слоёв. Эталоны WebGIS v2 привязаны к точным ID. Чистая сверка: 0 операций, 0 конфликтов.
- Ресурсы читают dynamic.storage, dynamic.type_roads_exist, basic.kvartal, lh_mera.lh_mery_recent и, у заготовителей, dynamic.volok_recent через подключение №700. Настройки Android в описаниях слоёв. Серверные QML взяты у стилей №801, №756, №769, №745 и №754. Подпись лесосеки из cut, year_, externalid содержит перевод строки.
- Два проекта используют независимые Collector workspace. До MAP-01 код текущего APK преобразует явный перенос в пробел. После MAP-01 тот же шаблон задаёт вторую строку.
- MAP-02 описывает доступные аккаунту Collector-проекты во всех папках сервера. MAP-01 сохраняет контракт явного переноса в подписях и приёмку на обновлении APK.
- После запланированного переноса самих проектов пользователем в каталог ЛИСА потребуется актуализировать их размещение в эталонах WebGIS v2. Кабинет LISA Web использует отдельные настройки веб-карт.
- Проверены 180 тестов customize, 1 пропущен. Проверены проекция и валидатор документации Android, карточки обновлений. Доставка manifest/content сохранена на Q, применимые записи workspace, границы customize и мобильного импорта прочитаны.

Полный журнал, входные хэши и результаты проверок: сессия 2026-11-25, задача desktop-20261126-klph-mobile-field-maps. Неизвестные связи и отдельные native/GUI проверки не объявляются пройденными.
