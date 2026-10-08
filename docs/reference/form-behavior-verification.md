---
title: Проверка зависимостей, вкладок и обязательности NGFP
type: reference
last_verified: 2026-10-08
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/reliability/CascadingFormsTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ConditionalRequiredFieldsTest.java
  - maplib/src/test/java/com/nextgis/maplib/forms/ConditionalRequiredRulesTest.java
---

# Проверка NGFP — 7 октября 2026

Дополнение от 8 октября: [оформление, видимость и восстановление](../history/form-visibility-verification-2026-10-08.md).

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
Android docs validator и 7 его тестов прошли. Матрица трёх APK и MapLibre OpenGL
прошла на итоговом коде; после слияний повторяется с удалёнными pins. Версии не менялись:
Lisa Debug 3.1.2.23 / 218, Lisa/Belka Release 3.1.2.27 / 221.

На физическом Samsung SM-A566B через ADB проверен новый аудит Вывозка
тестового Collector 953 в группе 803. До выбора подрядчика дочерние поля
отключены. Для подрядчика с двумя должностями отображаются обе его должности;
для оператора список совпал с единственным сотрудником из соответствующей
пары contractor/position. Шапка сохранила координаты при вертикальной прокрутке;
взмахи в обе стороны переключили вкладки. Снятый флажок блокирует Save с пустым
комментарием и показывает название вопроса вместо имени f_1_com.
Пробный аудит отменён, feature draft пуст. Контрольные суммы layers.db и пустого journal совпали
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
#50 с удалёнными pins/Squash → desktop #165 Squash. Перед слиянием у всех четырёх
PR проверено отсутствие открытых дочерних PR. Пользователь явно разрешил эти
четыре слияния без поднятия версий и публикации. После всех слияний обязателен
повторный fetch, сравнение удалённых деревьев и pins, затем финальная сборка.

| Требование | Репозиторий / PR / base | Проверяемый исходный commit | Удалённый merge / проверка включения |
|---|---|---|---|
| Parser условной обязательности | maplib [#42](https://github.com/GeonicalSys/android_maplib/pull/42), master | 4a6ae22afdb7307652c5589a3f9785d276df7bfe | b4334744bde553dcdb3391a0621de469e54a921d; ancestry и полное равенство дерева |
| Metadata launch, Tabs/swipes, required UI/draft | maplibui [#30](https://github.com/GeonicalSys/android_maplibui/pull/30), master | cc1b115cad05d2ddfaab27aaa26315b058925135 | 5a3db1a1926c4548b722401841a43c79fb350197; ancestry и полное равенство дерева |
| Native regressions, документация, merged library pins | application [#50](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/50), my-maplibre | be25885119f65894892305380230906d5fa7c08b | 4d2bbed00d8285e2154b5db8b221b457ed175b42; полное равенство дерева и оба gitlink на merged libraries |
| Clone guard и desktop/mobile contract | desktop [#165](https://github.com/GeonicalSys/lisa/pull/165), main | ae19f628524fbf321f6486e907833cea00331676 | 6e9a5a527c3ff0c7b74526d160dab59aef03ee66; полное равенство дерева |

Предшествующие maplib #41, maplibui #29, application #49 и desktop #163 уже
входят в базы этих PR; их требования сохраняются. Easypicker и upload_mobile
не меняются. Параллельный desktop #164 относится к другой задаче, не пересекает
пути текущих изменений и сохраняется. Отложенных требований этой задачи нет.

## Исправления удобства формы после проверки пользователем

В отдельных follow-up ветках исправлены ранний выход без padding в каскадных
списках и отмена свайпа дочерними контролами. Управляемый `double_combobox`
отображается двумя подписанными полноширинными полями с высотой от 56 dp и
нижним промежутком 12 dp. JSON формы и ключи выбора/черновика не меняются.

Два новых regression-теста сначала упали на прежней реализации: поля оставались
сдвоенными, а свайп поверх списка не переключал вкладку. После исправления
20/20 проверок CascadingFormsTest и ConditionalRequiredFieldsTest прошли на
изолированном emulator-5554. Они включают три новые проверки: размеры/промежутки
и recreate, многократные свайпы над включёнными списками/checkbox/комментарием
и пустой областью, вертикальную прокрутку и исключение выделения текста.

Unit: maplibui 89/89, app 41/41; обе release-сборки ЛИСА/БЕЛКА успешны.
Docs validator и 7 тестов прошли. Версии, серверные формы, Access и PostGIS
не менялись. Полная native suite: 70/70, включая required, подпись, сохранение,
трек и обход. Сборка тестового Debug APK выполнена из этого же кода.
Общая синхронизация проектов в эти изменения не входит.

На физическом телефоне обнаружен новый незавершённый feature draft. Установка
follow-up APK с перезапуском приложения не выполнялась; черновик сохранён.
Поэтому удобство новой реализации на физическом устройстве пока не проверено.

Follow-up library: [maplibui #31](https://github.com/GeonicalSys/android_maplibui/pull/31),
base master, commit `251d5e7b939e89171d803b79d8d962ada4dae1d5`. Application
пока закрепляет этот опубликованный commit для тестирования. Перед интеграцией
нужно Merge Commit библиотеки, fetch и замена pin на удалённый merge commit,
затем Squash приложения. Новые PR не входят в прежнее разрешение на четыре
слияния. Maplib остаётся на merged #42; desktop и publisher не меняются.

Локальный тестовый APK: `build/deliverables/2026-10-07-form-usability/`;
package `com.nextgis.mobile.debug`, 3.1.2.23 / 218, подпись v2 проверена.
SHA-256: `7FFE565B743FC33844AAAFFF6008F635EF802262D9FD3B8C4F537D68012FAB48`.
Это проверочная сборка до интеграции, публикация не выполнялась.
