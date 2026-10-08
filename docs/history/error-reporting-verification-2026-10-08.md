---
title: Проверка мобильных багрепортов 8 октября 2026
type: reference
last_verified: 2026-10-08
related_code:
  - app/src/main/java/com/nextgis/mobile/util/AppDiagnostics.java
  - app/src/main/java/com/nextgis/mobile/util/DiagnosticsTransport.java
  - app/src/androidTest/java/com/nextgis/mobile/util/DiagnosticsDeliveryTest.java
  - tools/verify-diagnostic-delivery.ps1
---

# Проверка мобильных багрепортов

Работа продолжена по явному указанию пользователя в app branch
`codex/mobile-form-sync`, Draft PR #51. Это проверка реализации в незамерженном
PR, без повышения версий или публикации. Desktop checkout не изменялся.

## Состав

| Владелец | База / включённый commit | PR / состояние | Роль |
|---|---|---|---|
| app | e18a8db586f92a310b3f3cf4286175739bc51dd6 + этот инкремент | #51, Draft, base my-maplibre | формы, общая sync, отказ азимута и новые багрепорты |
| maplib | 0cd7d39b8bdfe34d391bdf6c5f118b0709e549a8 | #43, Draft, base master | существующий pin проекта; код в этой доработке не менялся |
| maplibui | 76f417e08bc99f1e270f1a4c4bbc2376839580ad | #31, Draft, base master | существующий pin UI; код в этой доработке не менялся |
| easypicker | f91abdf28f3d49fdc5b72193fdf2c9a9b70e82ac | открытого PR нет | без изменений |

Будущий порядок merge остаётся maplib #43 Merge Commit → maplibui #31 Merge
Commit → fetched remote library pins в app → app #51 Squash. Проверочные APK
из этого PR не являются опубликованным выпуском. Новых библиотечных/desktop
контрактов багрепорты не вводят; их manifests не изменены.

## Native delivery

Отдельный Android emulator `Medium_Phone_API_36.0`, emulator-5554, ranchu.
Рабочий телефон не использовался. SDK 8.37.1, WorkManager 2.11.2,
GlitchTip 6.2.6. Test runner подставлял DSN только проекта Setup verification
(id 2). Обычный APK настроен private file на проект NextGIS Mobile (id 1).
Административный пароль или реальный DSN в Git/docs не сохранены.

Проверены:

- Handled IllegalStateException без Wi-Fi и mobile data сохраняется в private SDK
  cache с operation AZIMUTH, breadcrumbs и diagnostics_contract. Тестовые пароль
  и URL удалены из сериализованного сообщения.
- Настоящий IllegalStateException на main thread проходит через HyperLog/Sentry
  к Android и завершает только synthetic процесс. Ожидаемый результат этой фазы
  — instrumentation `Process crashed`, а не зелёное завершение JUnit.
- Следующий холодный запуск при отключённой сети видит оба отчёта на диске.
- Сеть включена при том же процессе. SDK connection observer доставляет очередь;
  тест завершился успешно, private envelopes подтверждены/очищены.
- Synthetic local HTTP receiver сначала отвечает 503. Envelope остаётся на диске.
  После переключения receiver на 200 без смены сети настоящий WorkManager
  исполняет DiagnosticsRetryWorker и завершает job как SUCCEEDED. Очередь пуста,
  объект default crash handler не изменился.
- Windows PowerShell 5 helper `verify-diagnostic-delivery.ps1 -SkipBuild` выполнил
  все пять фаз и восстановил настройки сети эмулятора. Отчёт первого полного
  helper run: `build/diagnostic-delivery-check/mobile-20261008-104943/`.

В API самого GlitchTip подтверждены два мобильных события в test project:

| Issue | Event UUID | Stack frames | Контекст |
|---|---|---:|---|
| 2 | 257c9b958e944af5b84ecde88fc4c0a5 | 34 | handled exception, AZIMUTH, app/device/os/trace |
| 3 | 1455e1d6b36848388a4f65009e50cdd2 | 10 | fatal exception, AZIMUTH, app/device/os/trace |

Проверены source frame с файлом/строкой, версия Debug, Android/device,
brand/environment, SDK и breadcrumbs. Новое поле source_revision позволяет
различать сборки с неизменной версией; итоговый APK собирается после commit.
Техническое подтверждение без credentials —
`build/diagnostic-delivery-check/server-event-verification.json` (локальный артефакт).

При проверке обнаружены и исправлены две причины отсутствия надёжного retry:
стандартный AsyncHttpTransport удаляет envelope после HTTP 503, а Android использует
отдельный package-private SendCachedEnvelopeIntegration. Транспорт расширен через
публичный ITransportFactory; retry использует общий public connection observer,
не core-only class, reflection или повторный Sentry.init.

## Остальные проверки и границы

Все 49 app unit-тестов прошли, включая redaction SQL/WKT/credentials, сохранение
полезного Android сообщения и stack frames, ограничение повторов и исключение
произвольных payload/breadcrumbs. Debug, Lisa Release, Belka Release и test APK
собраны без изменения versions/accounts/signing. Фактические APK metadata aapt:
Debug 3.1.2.23/218, Lisa и Belka Release 3.1.2.27/221. Host/project компилируемого
приёмника и наличие SOURCE_REVISION проверены для всех variants без вывода DSN.

ProjectScriptsTest и StakeoutForegroundServiceTest завершили 13 native-сценариев
без failures при выданном GPS; denied-location ветвь с assumption отдельно
пройдена после фактического отзыва fine/coarse (ещё один запуск). Bluetooth
оставался запрещён. SDK и retry не нарушили Binder sandbox или GPS lease cleanup.
Validator, семь docs tests и strict changed-file coverage проверены. Логи в
`build/diagnostics-*-check.log` и `build/diagnostic-delivery-check/`.

End-to-end проверен JVM fatal. NDK/minidump и Android ANR hooks SDK включены,
но отдельные реальные native crash/ANR в этой проверке не вызывались. Doze,
полностью выключенный телефон, переполненный диск и длительное исчерпание
256 envelopes не являются пройденными device smoke. Границы доставки описаны
в [архитектуре](../architecture/error-reporting.md).

Official app master проверен через GitHub API: f11d38f77e4caf1b569526c5f620b2ec513c5f9d,
commit 8 октября 2026 07:14 UTC; такого механизма delivery/cache/WorkManager нет.
