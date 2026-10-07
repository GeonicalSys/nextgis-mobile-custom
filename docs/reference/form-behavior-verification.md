---
title: Проверка зависимостей, вкладок и обязательности NGFP
type: reference
last_verified: 2026-10-07
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/CascadingFormsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ConditionalRequiredFieldsTest.java
  - maplib/src/test/java/com/nextgis/maplib/forms/ConditionalRequiredRulesTest.java
---

# Проверка NGFP — 7 октября 2026

Проверен публичный путь создания объекта через LayerUtil.showEditForm с default
form: regression сначала воспроизвёл отсутствие KEY_META_PATH для 933_form.json,
после исправления numbered и legacy-пары передают правильный metadata-файл.

Полная нативная suite на изолированном emulator-5554: 67/67. В неё входят
17 проверок каскадов/условной обязательности: настоящие controls и SQLite,
две ветви сотрудников, очистка потомков, неактивные вкладки, закреплённая шапка,
свайпы/подпись, поворот, toolbar и Back Save, сохранение текста после снятия
условия, pin черновика, обновление metadata и повреждённые снимки. Runtime
свайпов использует явную дистанцию/направление/время и экранные координаты;
результат не зависит от классификации long/double tap и последней скорости.

Unit: maplib 491/491 (включая 8 parser-тестов), maplibui 89/89, app 41/41.
Android docs validator и 7 его тестов прошли. Версии не менялись:
Lisa Debug 3.1.2.23 / 218, Lisa/Belka Release 3.1.2.27 / 221.

На физическом Samsung SM-A566B через ADB проверен новый аудит Вывозка
тестового Collector 953 в группе 803. До выбора подрядчика дочерние поля
отключены. Для подрядчика с двумя должностями отображаются обе его должности;
для оператора список совпал с единственным сотрудником из соответствующей
пары contractor/position. Снятый флажок блокирует Save с пустым комментарием.
Пробный аудит отменён. Контрольные суммы layers.db и пустого journal совпали
до/после; база на компьютер не копировалась. Полная native suite на телефоне
не запускалась. Полная sync с возможной отправкой outbox не выполнялась;
NGFP metadata установлен из проверенного серверного архива точечно.

В NGW изменён только meta.json формы 933 под слоем 807 в разрешённой группе
803: 33 checkbox/comment пары, читаемые label. Form.json и data.geojson
сохранены побайтово, cascade namespace и схема — структурно. Итоговый NGFP
SHA-256: 1714210671e7cfe807a11bfe5338ecd462aa487106802d8828faf87d76a0dbec.
Серверный readback совпал. Записей feature/PostGIS не было. Локальная симуляция
обновления справочника прошла существующий Access guard и сохранила правила.
Access frontend/backend и синхронизатор не изменялись.

Desktop: 15 clone-тестов и два offscreen открытия/закрытия диалога в каждой
среде QGIS 3.44.14 / Qt 5.15.13 / PyQt 5.15.11 и QGIS 4.2.2 / Qt 6.11.0 /
PyQt 6.11.0, Python 3.12.14. Qt checker не предложил изменений изменённого
clone-модуля; сообщил о наличии PyQt5 в своей среде. Docs validator плагинов
и 5 profile docs-тестов прошли. Живое клонирование через меню QGIS не проверено;
совместимость всего stand_project с QGIS 4 этим не объявляется.

Интеграция: maplib #42 Merge Commit → maplibui #30 Merge Commit → application
pins/Squash → desktop #165 Squash. Easypicker и upload_mobile не меняются.
Параллельный desktop #164 относится к другой задаче и сохраняется.
