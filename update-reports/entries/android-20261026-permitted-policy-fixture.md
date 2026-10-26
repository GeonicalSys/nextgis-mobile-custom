---
id: android-20261026-permitted-policy-fixture
area: Проверки мобильного приложения
kind: internal
at: 2026-10-26T01:18:00Z
title: Явный перенос разрешённого снимка политик слоёв для проверки устройства
why: На планшете нет доступного Collector-проекта 1403; использование прежнего снимка согласуется отдельно
covers:
  - tools/run_mobile_tests.py
  - tools/mobile_test_project.py
  - tools/verify-mobile-tests.ps1
  - tools/mobile-test-plan.json
  - tools/tests/test_mobile_test_project.py
---

MOB-01/REL-03. Необязательный TestProjectFixture принимает только явно выбранный
metadata-only JSON с совпадающим TestProject ID. Допустимы только schema1,
ограниченные names/geometry/editability flags; лишние fields, credentials,
features, coordinates, неверные типы и inconsistent expected_editable запрещены.
Фиксируются source/output SHA256 и происхождение explicit-policy-fixture.
Без параметра прежний discovery/уточнение отсутствующего проекта сохраняются;
автоматического fallback на чужой проект или снимок нет.

Related project-policy позволяет проверить настоящий chooser в изолированном
APK по этому снимку. Это не утверждение, что проект загружен в production на
новом устройстве. Использование снимка смартфона 1403 на планшете ожидает
явного ответа пользователя; production project/data не меняются.
