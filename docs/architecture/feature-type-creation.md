---
title: Выбор типа нового объекта по стилю
type: architecture
last_verified: 2026-10-09
related_code:
  - app/src/main/java/com/nextgis/mobile/fragment/MapFragment.kt
  - maplibui/src/main/java/com/nextgis/maplibui/dialog/ChooseFeatureTypeDialog.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FeatureTypeDefaults.java
  - maplib/src/main/java/com/nextgis/maplib/forms/CascadingLists.java
---

# Выбор типа нового объекта по стилю

После «Добавить объект» и выбора слоя MapFragment открывает выбор конкретной
категории FieldStyleRule. При единственном слое выбор категории также сохраняется.
Слой с простым стилем начинает обычное рисование. Catch-all без конкретного
значения не превращается в тип. Существующие объекты не получают начальные
значения. Каталог использует порядок и effective symbols локального стиля;
сеть не нужна. Длинные названия переносятся, форма и справочники читаются в фоне.
Loading/error header находится внутри ListView, без второго custom content
panel AlertDialog. Поэтому длинный список прокручивается в доступной высоте,
а «Отменить» остаётся видимым. Header не выбирается как категория; ошибка
подготовки показывается понятным сообщением, с доступной отменой.

FeatureTypeDefaults использует ту же пару default form/meta, что и LayerUtil.
Для legacy double_combobox выбираются точные name ребёнка и его родителя,
alias служит только подписью. При нескольких родителях показываются разные
строки с их подписями. Запомненный прошлый выбор не заменяет явный тип.
Обход смешанной формы сначала отбирает combobox/double_combobox и непустые
привязки полей, затем нормализует имена. Подписи, вкладки и отсутствие
field_level2 у обычного combobox не прерывают подготовку категорий.

CascadingLists.initialSelections идёт назад по графу lisa_form_dependencies
через стабильные parent keys и все AND-фильтры. Повторные строки одного tuple
объединяются, противоречивый общий предок отбрасывается. Результаты содержат
отдельные keys, values и labels; предел — 256 кандидатов на промежуточном уровне.
Текущий сеанс модели не меняется. Все допустимые цепочки остаются отдельными
вариантами, остальные ветви нового объекта пусты. Отсутствующая категория
справочника видима, но недоступна: ошибка не подменяется первым вариантом.

Выбор переносится в небольшом typed Bundle feature_type_initial_values.
Каскад закрепляет обычный SHA-256 snapshot form_dependencies до рисования;
таблицы не помещаются в Bundle. GeometryEditDraftStore сохраняет initial_values
в прежнем формате v1; старые записи без этого поля читаются. При переходе к форме
LayerUtil сначала сохраняет durable checkpoint с состоянием и owning map path,
затем запускает Activity. Состояние Activity и восстановленный durable draft
имеют приоритет над начальными значениями. Смена родителя использует штатную
очистку потомков; required, scripts и before-save gate остаются общими.

Новые форматы NGW/NGFP, desktop-публикация и схема SQLite не требуются.
Порядок интеграции: Merge Commit maplib → Merge Commit maplibui → remote pins
в app → Squash app → проверка включения → Lisa Release APK 3.1.2.27/221.
Версии app/maplib не повышаются. Публикация APK не входит в эту задачу.

Проверки: CascadingListsTest, GeometryEditDraftStoreTest и native
CascadingFormsTest для начального выбора, legacy пары, SQLite, recreate,
durable recovery и асинхронного диалога. MapEditingToolsTest проверяет полный
путь layer → category → sketch → form → SQLite и возврат на карту без черновиков.
StandardFeatureTypesTest использует form/schema/renderer из стандарта
field_points, field_lines и field_polygons: все 40 категорий имеют символ и
правильную пару classobj/typeobj; стандартная точечная форма получает выбор.
В fixtures отсутствуют NGW connection metadata и строки объектов.
Физический телефон требует отдельного smoke; synthetic suite запускается
только на изолированном эмуляторе.
