---
id: android-20261126-legacy-recovery-229
area: Восстановление отправки полевых данных
kind: user
at: '2026-11-26T07:05:30+00:00'
title: Явная сверка старой очереди и подготовка ЛИСЫ и БЕЛКИ 3.1.2.35
why: Накопленные записи версии 226 должны сохраняться при обновлении APK и получать проверяемое подтверждение отправки
covers:
  - app/build.gradle
  - app/src/androidTest/java/com/nextgis/mobile/reliability/ChangesCentreNativeTest.java
  - app/src/androidTest/java/com/nextgis/mobile/reliability/LocalChangesUploadTest.java
  - app/src/main/java/com/nextgis/mobile/activity/ChangesCentreActivity.kt
  - app/src/main/res/values-ru/local_changes.xml
  - app/src/main/res/values/local_changes.xml
  - tools/verify-apk-version-matrix.ps1
  - maplib
  - agent-system/knowledge/documentation/flavors-and-versioning.json
  - agent-system/knowledge/documentation/fork-customizations-ngw-account-import-and-layer-config-routes.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-local-crud-and-outbox-transaction.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage-sync-regression-scope.json
  - agent-system/knowledge/documentation/ngw-sync-and-storage.json
  - agent-system/knowledge/documentation/project-synchronization-user-guide.json
  - agent-system/knowledge/documentation/release-apk.json
  - agent-system/knowledge/documentation/release-notes.json
  - agent-system/knowledge/documentation/release-note-3.1.2.34.json
  - agent-system/knowledge/documentation/release-note-3.1.2.35.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-4a8815f9c3c363b96babbbb1.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-66447f73f07d22eb763cd5f7.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-fb87c1c299a5598abcd742f1.json
  - agent-system/knowledge/ui-contracts/ui-non-method-evidence-changes-copy-ru.json
  - agent-system/knowledge/ui-contracts/ui-sources-wf-changes-centre-view.json
  - agent-system/provenance/structured-views.json
  - docs/reference/flavors-and-versioning.md
  - docs/registry/ui-contracts.json
  - docs/runbooks/release-apk.md
  - agent-system/knowledge/documentation/build-matrix.json
  - agent-system/knowledge/documentation/official-differences.json
  - agent-system/knowledge/documentation/official-differences-lisa-history-and-startup-229-20261126.json
  - docs/reference/build-matrix.md
  - docs/reference/official-differences.md
  - WHATS_NEW.md
---

Центр изменений объясняет неизвестный исход отправки старой версии и помогает
проверить серверные объекты по отправленным полям и полной геометрии. Найденные
ID предлагаются для явного выбора. Совпадение данных, единственный кандидат
или пустая доступная выборка не подтверждают принадлежность либо отсутствие
прежней отправки. Учитываются ограничение выборки и недоступность чтения.

Пользователь может подтвердить известный серверный ID либо отдельно разрешить
новую попытку для точки, которую ещё не отправляли. Если выбранный объект имеет
другие значения, отдельное подтверждение принадлежности сохраняет текущие
локальные поля, геометрию и фотографии и создаёт самостоятельную правку для PUT.
Существующая локальная строка, очередь либо каталог назначения блокируют
неявное объединение. Уже подтверждённый сервером ID нельзя заменить другим
одинаковым объектом или разрешить для него ещё одно создание.

Координированная реализация Maplib поддерживает старые частичные технические
поля до перезагрузки слоя. Настоящая очередь 226 без UUID и квитанции остаётся
для явной сверки. Для поддерживаемого сервера новая разрешённая попытка получает
durable transaction до HTTP: после начала commit повторные проходы только читают
его результат. Неопределённый результат внешней записи PostGIS и NotCommitted
не разрешают повторить commit. Точный серверный результат завершает локальный
ACK с сохранением поздних правок. Эти свойства относятся к поддержанному
transaction-пути; старые запросы без серверного идентификатора автоматически
не становятся однократными.

Тесты создают историческую пятиколоночную очередь SQL без современного
insertAddChanges, восстанавливают старую конфигурацию и проверяют отсутствие
POST до решения пользователя. Покрыты явная сверка, подтверждённый FID,
несколько одинаковых кандидатов, поздняя правка, коллизия локального ID,
сохранность фото и переход к одному PUT. HTTP-сценарии transaction различают
allocation, staging, commit и число фактических созданий; проверяют потерю
ответа, повторное чтение после загрузки слоя и передачу полного WKB.
Старые фото сохраняются в очереди: старый клиент мог пытаться отправлять их
до завершения отправки точки, поэтому автоматическое подтверждение всех
фотографий по временным ID не заявляется.

Подготовлены оба Release-варианта 3.1.2.35 / versionCode 229; версия 3.1.2.34 / 228
сохранена в исторических заметках. Обновлены канонические записи версий,
производные документы, UI-контракт, проверка матрицы APK и связь с Maplib.
Debug сохраняет 3.1.2.23 / 218. Подписанные артефакты, текущий gitlink, метаданные
и публичная выдача требуют самостоятельной проверки.

Связь с дорожной картой: REL-01 и UX-02. Финальные JVM/Robolectric, native,
GUI-проверки и приёмка emulator-only 229 ещё ожидаются. Промежуточный зелёный
набор 98 JVM-проверок предшествовал последним изменениям и не подтверждает
финальный кандидат. По прямому поручению пользователя только для 229
физическое устройство, USB-офлайн и физическая нагрузка имеют статус WAIVED;
стандартный Full — NOT_RUN. Общая политика следующих выпусков сохраняется.
Публикация и проверка обоих готовых APK ещё не завершены.

Итоговый кандидат прошёл 108 адресных JVM-проверок и 164 сценария на эмуляторе, включая 16 проверок отправки, 6 проверок экрана изменений, диагностику и обе матрицы интерфейса. Один нестабильный случай обхода повторён на той же паре APK. Первоначальный журнал и результаты повтора сохранены в `native-release229-03-verified-cohorts`. Проверены оба подписанных Release APK 229. Публикация проводится после проверки сборок издателем.
