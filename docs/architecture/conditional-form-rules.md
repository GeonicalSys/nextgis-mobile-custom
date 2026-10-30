---
title: Обязательность, видимость и оформление NGFP
type: architecture
last_verified: 2026-10-30
related_code:
  - maplibui/src/main/java/com/nextgis/maplibui/util/FeatureTypeDefaults.java
  - maplibui/src/main/java/com/nextgis/maplibui/activity/FormBuilderModifyAttributesActivity.java
  - maplibui/src/main/java/com/nextgis/maplibui/formcontrol/TextEdit.java
  - maplibui/src/main/java/com/nextgis/maplibui/formcontrol/DoubleCombobox.java
  - maplib/src/main/java/com/nextgis/maplib/forms/ConditionalRequiredRules.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/ConditionalRequiredController.java
  - maplibui/src/main/java/com/nextgis/maplibui/util/FormMetadataSnapshot.java
  - maplibui/src/main/java/com/nextgis/maplibui/control/FormScrollView.java
  - maplibui/src/main/java/com/nextgis/maplibui/formcontrol/Tabs.java
---

# Обязательность, видимость и оформление NGFP

Native verification учитывает реальный viewport: скрытый прокруткой control
сначала раскрывается обычным requestRectangleOnScreen, затем выполняется один
жест. Проверка имени различает полное вписывание на одной строке широкого окна
и overflow: все строки/высота/ellipsis проверяются всегда, строгий перенос —
дополнительно на 320/280dp компонентах в обеих темах. Production navigation,
условия и layout правила этим test-only изменением не меняются.

Tabs применяет отложенную смену страницы только на прикреплённом resumed экране
с несохранённым FragmentManager state. Пока экран остановлен, сохраняется последний
запрос; после уничтожения callback отменяется. Контролы всех страниц и текущие
required/visible значения сохраняются, commitAllowingStateLoss не используется.

Явный [выбор типа при создании](feature-type-creation.md) заполняет поля до
первого расчёта правил. Required/visible и scripts видят уже выбранную категорию
и родителей; ни один общий Save gate этим заполнением не обходится.

Общие правила формы исполняет встроенный parser maplib, а не внешний JS.
NGFP `meta.json.lisa_form_rules` независимо от каскадных справочников задаёт,
при каких значениях других полей целевое поле становится обязательным или
элемент отображается. Версия 1 с одним `required` сохраняет прежнее поведение.
Версия 2 добавляет необязательный массив `visible`; `required` в ней тоже
необязателен. Условия не меняют и не очищают значения.

```json
{
  "schema_version": 2,
  "required": [
    {"field": "f_1_com", "label": "Комментарий к СИЗ", "when": {"field": "f_1", "op": "eq", "value": false}},
    {"field": "reason", "when": {"all": [
      {"field": "status", "op": "in", "value": ["risk", "stopped"]},
      {"field": "approver", "op": "is_empty"}
    ]}}
  ],
  "visible": [
    {"field": "f_1_com", "when": {"field": "f_1", "op": "eq", "value": false}},
    {"element": "safety-help", "when": {"field": "f_1", "op": "eq", "value": false}}
  ]
}
```

`eq`, `ne` принимают строку, число или Boolean; `in`, `not_in` — непустой массив
таких значений. `is_empty`, `is_not_empty` не имеют value. `all`/`any` содержат
непустой массив условий, `not` — одно условие. Имена полей/строки сравниваются
точно, числа — численно; Boolean false/true соответствует integer 0/1 чекбокса.
Текст «0» не превращается в число. Пустота, Unicode-пробелы, «Нет значения»
считаются отсутствием, «не применимо», 0 и false — значениями. Все сравнения
значений, включая ne/not_in, возвращают false для отсутствующего родителя;
пустоту проверяют явно. `not` логически инвертирует результат своего условия.

Необязательный `label` (непустая строка до 512 символов) задаёт понятное имя
для поля без подписи в шаблоне и сообщений о пропуске. Существующая подпись
шаблона имеет приоритет. Alias в схеме слоя и записываемые значения не меняются.

`visible` содержит ровно одну цель `field` или `element` и `when`. Поле без
правила видимо. При ложном условии целиком исчезает его контейнер: подпись,
ввод, подсказка/ошибка и отступ. Для отдельного текста, фотографии, подписи,
пробела или группы Tabs добавьте `"lisa_id":"safety-help"` на верхнем уровне
соответствующего элемента `form.json` и используйте `element`. Идентификаторы
уникальны во всей форме, включая неоткрытые/вложенные вкладки. Переносимое имя:
латинская буква, затем латинские буквы, `_` или `-`, всего до 128 символов;
цифры исключены, чтобы старый numeric-ID remap NGFP clone не менял имя.
ID страницы Tabs не поддерживается: целиться можно в сам элемент Tabs либо
в элементы внутри страницы. Скрытие внешних Tabs убирает и закреплённую шапку.

Видимость поля и содержащего его элемента объединяется через И. Обычное
переключение вкладок не считается условным скрытием. Условия всегда читают
исходные текущие значения, в том числе скрытые; взаимные ссылки не вызывают
рекурсии и не сбрасывают ответы. Не используйте скрытие как разграничение прав.

Окончательное требование для отображаемого поля — штатный NGW `required` ИЛИ
истинный `required.when`. Условно скрытые поля не блокируют required/каскадную
UI-проверку, включая поля внутри скрытой группы, и не получают звёздочку.
Метаданные `Field.isRequired` слоя не меняются. Значение скрытого поля остаётся
в контроле, Bundle, durable draft и обычном сохранении; при повторном показе
восстанавливается прежний текст. При редактировании объекта скрытие не очищает
прежний атрибут. Обязательное поле, вообще отсутствующее в шаблоне, по-прежнему
проверяется — это не условное скрытие.

Общие ограничения v1/v2: 512 правил суммарно, 4096 узлов условий, глубина 16,
256 KiB UTF-8.
Повтор цели, неизвестные версия/оператор/поле, неверная структура отклоняются.
Ссылки идут только на поля этого слоя. Нет SQL, сети, произвольных выражений,
изменения схемы или условий по вычисленному состоянию обязательности другого
поля. Поэтому взаимные ссылки на значения не образуют рекурсивный расчёт.
Неизвестный/повторяющийся `lisa_id`, отсутствующая цель видимости или лишний
ключ определения также блокируют Save; ошибка не превращается в обход правил.

maplibui читает текущие scalar controls, включая неактивные вкладки и оба поля
double_combobox. Отсутствующее в форме поле существующего объекта читается один
раз из owning SQLite; для нового объекта оно отсутствует. Pre-draw observer
сравнивает только значения ссылочных полей и обновляет видимость/маркеры при изменении,
не заменяя listeners каскадов/скриптов и не читая SQLite на каждом кадре.
Save повторно вычисляет требования по снимку записываемых значений в общем пути
required-проверки, после durable checkpoint и до записи объекта/вложений.
Переход к пропущенному полю открывает нужную вкладку. Back Save проходит тот же gate.

`lisa_required_pin` в Bundle/draft — проверяемая SHA-256 ссылка на атомарный файл
`<layer>/form_rules/<hash>.json`. Пустая ссылка закрепляет отсутствие правил.
Обновление формы не меняет открытый сеанс/черновик; старые снимки не удаляются.
Повреждённый снимок блокирует Save и сохраняет pin и введённые значения.
Общий `FormMetadataSnapshot` используется и каскадами, сохраняя их прежние пути
и возможность чтения старых inline pins. Нельзя превращать ошибку правил в
молчаливое отключение проверки.

При штатном открытии `LayerUtil` передаёт согласованную пару
`<formId>_form.json` / `<formId>_ngfp_meta.json`, в том числе при автоматическом
поиске default form. Legacy пара без префикса тоже поддерживается. Регрессия
проверяет публичный showEditForm, а не только прямой Intent тестовой Activity.

Шапки внешних NGFP Tabs живут под toolbar вне вертикального FormScrollView.
Свайп требует горизонтального перемещения от 48dp за время до 1,5 с и перевеса
горизонтали 1,25. Направление определяется после значимого перемещения, небольшой
стартовый дрейф не отменяет взмах. Dispatch наблюдает жест поверх clickable
controls; requestDisallowIntercept сам по себе не отменяет навигацию. На первом
уверенном горизонтальном MOVE старый control получает Cancel до выбора вкладки,
остаток жеста потребляется, включая края. Короткая страница включает пустую
область ScrollView. Проверка попадания
использует экранные координаты с системными отступами. Поле подписи сохраняет
минимальную высоту 200dp и не принимает свайпы переключения вкладок.
Поля и координаты прокручиваются; выбор вкладки открывает её начало, а required
reveal затем прокручивает к конкретному полю. Взмах влево выбирает следующую
вкладку, вправо — предыдущую, без перехода через край. Вертикальные жесты,
multi-touch, выделение текста/long press, подпись, фото и элементы со своей
горизонтальной прокруткой не перелистывают форму. Обычный короткий комментарий
допускает взмах. При TalkBack touch exploration custom swipe отключён; доступны
кнопки вкладок. Вложенные группы вкладок остаются в содержимом.

Desktop clone защищает весь lisa_form_rules от замены числовых литералов и имён
как NGW resource ID. Access sync сохраняет посторонний блок при обновлении
справочников. Веб-редактор не показывает настройки расширения; старые клиенты
их игнорируют. Это локальная проверка приложения, не ограничение сервера.
Порядок интеграции: maplib Merge Commit → maplibui Merge Commit → pins/app Squash
→ desktop clone Squash. Версии остаются прежними; изменения PostGIS не нужны.

[Результаты и границы проверки](../reference/form-behavior-verification.md).

## Оформление «Чёткие поля»

`FormFieldLayout` создаёт общий контейнер для NGFP и стандартной формы: подпись
14sp medium сверху, значение 17sp, рамка со скруглением 10dp, минимум 56dp для
ввода/списка и 20dp между полями. Отдельные заголовки сохраняют 16sp; подпись
не исчезает после ввода. Checkbox содержит собственный многострочный вопрос
и большую область нажатия. Псевдоним берётся из подписи шаблона, затем rule label,
NGFP fields.display_name и alias слоя. Длинное выбранное название переносится;
обычные списки открывают нативный диалог. Autocomplete сохраняет поиск/добавление.
Светлая/тёмная палитра определяется темой приложения, а не только темой Android.
Ошибка обязательности видна под конкретным полем и на его рамке. Нижняя кнопка
«Сохранить» закреплена и использует общий gate toolbar/Back Save.

У legacy double_combobox с `lisa_id` сохраняется сам control и две раздельные
обёртки; это удерживает цель видимости всей пары. Без ID управляемая каскадом
пара разворачивается как прежде. Field names, выбранные ключи и каскадный pin
не зависят от оформления. Видимость применяется и в режиме просмотра.

## Каноническая пара classobj/typeobj: источник, defaults и Save

Канонический `field_polygons_form.ngfp` версии2.2 содержит legacy
`double_combobox` с `field_level1=classobj`, `field_level2=typeobj`, `last=false`.
Оба поля — STRING-категории без физических единиц. Raw names `Прочие` / `прочие`
помечены template defaults; подпись alias, индекс Spinner, NGW resource ID и FID
не заменяют эти значения. Новый пустой сеанс без explicit/draft/cursor values
по обычному callback выбирает эту пару. Здесь не установлена неисправность default.

`FormBuilderModifyAttributesActivity.getControl` создаёт DoubleCombobox из
`formtemplate_doublecombobox.xml`. Сам control — AppCompatSpinner; второй Spinner
создаётся в init программно. У каждого поля нет отдельного XML ID: parent хранит
обе field identity, `getFieldView`/RequiredFieldUi находят нужный view, getValue и
saveState сохраняют raw pair. Это отличается от двух независимых selectors,
которые CascadingFormElements создаёт только при управляющем графе.

Полная valid Bundle pair, включая explicit new-feature category, имеет приоритет
перед cursor. Без complete saved pair используются cursor names; template default
выбирает fallback position, а last=false исключает preferences fallback. Для
parent/child списков последняя отметка default=true задаёт default position.
Callback родительского Spinner на первом показе применяет найденный restored child, затем default
выбранного parent. NULL/unlisted old values и замена формы требуют отдельной
согласованной политики; это не доказанное нарушение сохранения.

SQL `dynamic.field_polygons.classobj/typeobj` допускает NULL и не имеет DB default.
Connected Collector импорт получает `Field.required` и тип из серверного
`feature_layer.fields`; отсутствующий required читается как false. SQL nullable и
отсутствие required в template сами не доказывают actual server flag. Collector
visible — флаг слоя, не полевая conditional visibility. Save собирает обе строки
из DoubleComboboxValue и проходит существующие required/scripts/owner gates до
provider insert/update. Непустые default names являются значениями.

Desktop разрешает optional private overlay перед общей NGFP, проверяет catalog
pairs и публикует resolved bytes в form child. Check forms не проверяет все
required/default/visible признаки и не исправляет ZIP. Collector указывает layer;
Android выбирает первый formbuilder_form child, не canonical filename. Поэтому
actual tenant form ID/hash/order/required, obsolete-values policy и native Spinner
lifecycle остаются четырьмя условными неизвестными общей NGW family, а не новым
app-only списком задач.

### Прослеженные связи источников

| Link | Producer → consumer | Source anchors |
| --- | --- | --- |
| `NGFP-FIELD-01` | PostgreSQL canonical columns → NGW PostGIS field namespace | `tables.py:6403-6432; postgis_layers.json:/postgis_layers/10; ngw_layers.py:736; ngw_client.py:1052-1087` |
| `NGFP-FIELD-02` | Canonical NGFP v2.2 and field-type catalog → Resolved form input | `field_polygons_form.ngfp:meta.json fields0/1, form.json/1; field_poly_types.json:/pairs/6; plugin_parameters.py:277-287; form_paths.py:40-94` |
| `NGFP-FIELD-03` | Check forms action → Publisher input assurance | `field_data_forms.py:36-117; field_data_catalog.py:354-392; stand_project.py:1889` |
| `NGFP-FIELD-04` | Desktop apply forms → NGW formbuilder_form child | `stand_project.py:1432-1446; ngw_forms.py:131-148,211-245; ngw_client.py:1751-1794` |
| `NGFP-FIELD-05` | Collector canonical item → Numeric NGW layer reference | `collector_project.json:/root_item/children/3; ngw_collector_projects.py:145-187,238-275` |
| `NGFP-FIELD-06` | NGW layer children → Android selected form ID | `CollectorResource.java:414-457; LayerWithStyles.java:146-177; CollectorProjectCompositionSync.java:406-433` |
| `NGFP-FIELD-07` | Collector form ID → Local sidecars and connected NGW fill | `CollectorProjectImportHelper.java:262-277; LayerFillService.java:1835-1870,1942-1989` |
| `NGFP-FIELD-08` | NGW feature_layer.fields → Stored Android Field type and required flag | `NGWVectorLayer.java:676-704; NGWUtil.java:504-522; maplib LayerUtil.java:97-98; GeoConstants.java:115; Field.java:53-86` |
| `NGFP-FIELD-09` | Matched form/meta and form launch → Double selector restored state | `maplibui LayerUtil.java:137-151,209-224; ModifyAttributesActivity.java:420-424; FeatureTypeDefaults.java:132-164; DoubleCombobox.java:98-145` |
| `NGFP-FIELD-10` | Double-combobox defaults → Current values and saved pair | `DoubleCombobox.java:146-226,275-306; ControlHelper.java:137-141` |
| `NGFP-FIELD-11` | Control values → Required gate and provider Save | `FormBuilderModifyAttributesActivity.java:775-793,839-841; ModifyAttributesActivity.java:984-1003,1011-1062,1293-1321; RequiredFieldUi.java:22-29; RequiredFieldValidation.java:13-23; ConditionalRequiredController.java:46-55` |

Desktop paths принадлежат LISA `plugins/stand_project`; Android paths — maplib/maplibui.
Маршрут к единому владельцу и root registry: [экосистема](lisa-ecosystem.md).
Эти anchors обозначают source review, а не actual tenant/native acceptance.
