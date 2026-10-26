---
title: Общая история треков
type: architecture
last_verified: 2026-10-25
related_code:
  - maplib/src/main/java/com/nextgis/maplib/map/SharedTrackStore.java
  - maplib/src/main/java/com/nextgis/maplib/map/TrackLayer.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/SharedTrackProjects.java
  - maplibui/src/main/java/com/nextgis/maplibui/mapui/TrackWorker.java
  - maplibui/src/main/java/com/nextgis/maplibui/service/TrackerService.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/SharedTracksTest.java
---

# Общая история треков

Название, индивидуальный цвет, карта и безопасное удаление описаны в
[управлении треками](track-controls.md). Переименование не меняет ID/сегменты;
удаление истории не останавливает другой записываемый сеанс.

Тёмная сердцевина выводится из сохранённого цвета только при подготовке
отрисовки (73% затемнения sRGB); исходный насыщенный цвет остаётся общим для
диалога выбора и свечения. Эта настройка не меняет схему, значения цвета в БД
или поток координат для отправки.

Одна установка приложения хранит все треки независимо от Web GIS/локального
проекта. Карта содержит ровно одно служебное представление «Мои треки» сверху
пользовательского списка; база, цвет/видимость отдельных треков, видимость слоя,
список, GPX и остаток отправки общие. Векторы, формы, черновики и accounts сохраняют
проектную изоляцию. Переключение во время записи по-прежнему запрещено в обоих UI
и CollectorProjectRegistry; эта доработка его не разрешает.

## Хранение

SharedTrackStore schema 2 транзакционно заполняет прежние NULL цвета при
upgrade v1 и при позднем импорте legacy workspace. Все заданные цвета и остальные
столбцы остаются прежними; версия базы/цвета при ошибке откатываются вместе.
Повторный запуск не генерирует новые цвета.

SharedTrackStore открывает private files/shared-tracks/tracks.db через отдельный
SQLiteOpenHelper. TrackLayer и MapDrawable читают эту БД; прежний проектный файл
`layers.db` больше не является рабочей базой треков. `layers` — логическое имя
MapContentProviderHelper: DatabaseHelper через DatabaseContext добавляет `.db`.
Мигратор использует тот же чистый resolver имени без создания исходной БД;
extensionless offline export поддерживается, если обычного `layers.db` нет.
Точки имеют INTEGER PRIMARY KEY AUTOINCREMENT, поэтому удалённый ID не может быть
переиспользован перед запоздалым ACK. Новая очередь pending-track-points принадлежит
общему каталогу. UUID журнал записи и снятие checkpoint после commit сохраняются.
Stop закрывает только текущий ID после сохранения хвоста, сохраняя другие истории.

## Перенос

Исправление 2026-10-15 повторяет перенос для уже обновлённых установок: прежний
код искал `layers` и пропускал настоящий `layers.db`, не ставя completion marker.
Сбрасывать общую БД или маркеры не требуется. Уже записанные после обновления
треки остаются; коллизии старых IDs получают отдельные общие IDs. Regression
создаёт исходник через настоящий DatabaseHelper, а не прямой SQLite open с
логическим именем.

SharedTrackProjects читает сохранённого владельца незавершённого сеанса, текущий
workspace и все зарегистрированные проекты до открытия map helpers. Источник
открывается read-only. Одна target-транзакция переносит tracks, точки в исходном
rowid-порядке, sent/segment/accuracy и остальные показатели, UUID lost-reply journal,
затем replay durable tail и completion marker. Исходные таблицы и файлы очереди
сохраняются. Ошибка, orphan или повреждённый checkpoint откатывает весь источник;
готовые источники не импортируются повторно. Удалённая общая история не воскресает
из rollback-копии. Большая история читается курсорами, без массива всех точек.

legacy_tracks/legacy_points сопоставляют canonical workspace + старый ID с общим
ID. Треки с одинаковыми IDs и timestamps из разных проектов остаются отдельными.
Импорт сохраняет исходный ID только выше sqlite_sequence high-water mark: удалённый
общий session ID никогда не выдаётся вновь, даже если другой источник импортируется
позднее. Старый recording checkpoint не может попасть в чужой сеанс.
Схема до v3 конвертирует WGS84 в Web Mercator; отсутствующие sent/segment получают
исторические defaults 1/0. Bootstrap standalone -> initial local регистрирует
происхождение копии и не дублирует историю. tracks_temp.track_uri/track_map меняются
после commit миграции; неудачный preferences commit безопасно повторяется.
Миграционная ошибка сообщается пользователю без технических подробностей.
Карта остаётся доступной; запись/отправка повторяют prepare и не принимают
неполный перенос за успешный.

Для аварии во время обновления `PendingTrackPoints.replayReadOnly` читает
сохранённый `.bak` либо основной файл, а при первой незавершённой atomic записи —
полностью сохранённый `.new`. Исходные файлы не переименовываются и не удаляются.
UUID уже вставленной точки исключает дубликат при потерянном ACK. Ошибка target
посередине переноса откатывает историю, mappings и marker; повтор выполняет ту же
транзакцию. Recording checkpoint remap выполняется после commit, pending Stop
сохраняется и закрывает только восстановленный сеанс.

## Отправка и проектные операции

TrackWorker планирует одну unique immediate и одну periodic работу shared-v1.
Старые проектные задания удаляются по известным именам, а уже запущенная старая
работа перепланирует общую очередь без чтения старой БД и отменяет свой Work ID:
одного success недостаточно для прекращения старой periodic работы, в том числе
для удалённого workspace. TrackUploader сериализует
live/durable packets одним lock; ACK меняет только IDs принятого пакета общей БД.
track_send, точный server/UID registration gate, network retry и opt-out сохраняются.
Отправка трека не занимает project DATA_SYNC lease и не обновляет full-project clock.
Перед удалением каталога проекта его старая история импортируется; общая база
находится вне этого каталога и сохраняется.

## Проверки и ограничения

SharedTrackStoreTest: коллизии, одинаковые времена, sent, segments, replay после
потерянного ответа, rollback/retry, повреждение checkpoint, старая схема и bootstrap.
Fixtures создают настоящий `layers.db` через DatabaseHelper; отдельные случаи
проверяют новую историю рядом со старой, аварийный rollback target и неизменные
atomic backup/new файлы.
SharedTracksTest: provider/list, native GeoJSON на карте после switch, общие цвет и
видимость, удаление проекта, сохранение запрета switch во время записи. Существующие
TrackPersistence/TrackerStopRecovery/TrackUpload проверяют общую БД; delayed ACK
после удаления сеанса не отмечает новую точку отправленной. Synthetic fixtures
ждут завершения startup underlay migration перед регистрацией recording lease.
Три дополнительных native случая проходят production SharedTrackProjects.prepare:
полный перенос всех полей из finished history рядом с новым треком и восстановление
старого owner/URI/pending Stop с lost ACK без повторных точек; отдельный случай
собирает истории сразу двух неактивных проектов с одинаковыми legacy IDs рядом
с новой общей записью, не активируя их карты, и проверяет обе истории после switch.
Для локального
диагностического прогона completed case допускает явно переданный track-only
fixture в verification external files; реальные accounts/прочие таблицы не копируются.

Related tracks/map обязателен после правки. Full и Check-Publication нужны перед
публикацией после закрытия библиотечных зависимостей. Real RF/GPS в поле остаётся
отдельной проверкой. При полном диске RAM-точки не переживут смерть процесса до
успешного durable checkpoint; прежнее ограничение не скрывается.
