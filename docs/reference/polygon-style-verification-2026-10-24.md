---
title: Проверка штриховок, контуров и живого образца — 2026-10-24
type: reference
last_verified: 2026-10-24
related_code:
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleRenderingTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleEditorTest.java
  - app/src/androidTest/java/com/nextgis/mobile/rendering/PolygonStyleTileFallbackTest.java
  - tools/mobile-test-plan.json
  - tools/mobile_style_evidence.py
---

# Проверка штриховок, контуров и живого образца

## Проверенный состав

| Владелец | Коммит реализации | Draft PR | База |
|---|---|---|---|
| maplib | `118d1399578d62f35ddd6a21bf332b6165e74664` | [#60](https://github.com/GeonicalSys/android_maplib/pull/60) | master |
| maplibui | `b3fd7ba2acfb0306433a79eaa3a2ee306ef8f087` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) | master |
| Android | `e77a917788bc11e9b64bd744037bcb82ea3f5269` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) | my-maplibre |

Root закрепляет указанные коммиты библиотек. Их предыдущие реализации
«Светлячка» остаются предками, проверка документации всего PR сохраняется.
[Контракт пакета](../architecture/polygon-style-controls.md).
Fingerprint кода/тестов трёх владельцев: `ab6fb74c3d0f267fc9449d48a736e9314b89787aa25aa4453f480881293074f8`.
Финальный Related завершён 2026-10-24T12:51:19.726644+00:00.

## Результат

- maplib: 602 unit tests, 0 failures/errors/skips; maplibui: 93, также 0/0/0.
- maplibui assembleDebug — PASS. Lisa и Belka Release Kotlin/Java consumers
  скомпилированы; release APK не собирались. Версия не повышалась.
- Свежие Lisa verification Debug + AndroidTest APK собраны отдельно с
  fieldVerificationChecks/ciReliabilityChecks; затем Related styles PhoneOnly
  выполнен с SkipBuild на тех же APK: **24/24 distinct native cases PASS**.
- Два редактора («Светлячок» и полигон) прошли по 72 русских локальных профиля:
  обе темы и ориентации, font 1/1,3/2 и normal/320/280 dp; Save/Cancel дают
  суммарно 288 настоящих диалогов. Новая матрица проверяет contrast ≥4,5:1,
  полный текст и доступность последнего действия выбора цвета.
- Дополнительный native case проверил pixels живого образца точки, линии и
  полигона и изменение alpha настоящим касанием до Save. Source style остаётся
  неизменным после Cancel. Shared factories, а не отдельная схема рисунка.
- На карте проверены шесть procedural patterns, прозрачный фон, независимая
  alpha, пунктир/вторая обводка, отверстия, Polygon/MultiPolygon, дробный zoom,
  сохранённые категории, picking/visibility/restyle/image cleanup и live MVT
  fallback. Старые rendering/label/blur cases прошли вместе с новыми.
- Документационный validator + strict diff — PASS; docs tests 24/24;
  runner tests 26/26. Новые карточки всех владельцев и точные covers проверены.

Телефон: Samsung SM-A566B, Android 16/API 36, A566BXXSECZI2. Использован только
`com.nextgis.mobile.verification.debug`; рабочая ЛИСА не заменялась. Системные
font/display/network settings не менялись. Test fixtures удалены, verification
process остановлен runner. Эмулятор в этом прогоне не использовался.

Runtime: installed Android SDK, MapLibre Android 13.0.2 OpenGL, Gradle 9.3.1,
JBR 21.0.9+-14787801-b1163.94. SDK/JBR/Gradle и существующие Q build junctions
переиспользованы; новые копии runtimes и эмуляторов не создавались.

## Артефакты и пределы

- `ngmobile-3.1.2.30-lisa-debug.apk`: 84762977 bytes; SHA-256 `3a4bfbcf24e2d99ede9820b3c8a85149b2d63fb6ee10539a45758afdcf870a06`.
- `ngmobile-3.1.2.30-lisa-debug-androidTest.apk`: 968263 bytes; SHA-256 `457468a623546a967348fff154796e7ad768588bcdc2664318a20a66ca49f56f`.

Несмотря на production-номер в имени файла, aapt подтвердил test package
`com.nextgis.mobile.verification.debug`, versionName 3.1.2.23/code 218.
Это отдельная Debug проверка, не новый подписанный выпуск.
Полный каталог теперь содержит 48 классов / 233 метода; **Full не запускался**
и остаётся обязательным перед публикацией. Текущие Draft PR не сливались,
публикация и APK Белки не выполнялись.

Фактические логи, result.json с fingerprint, APK hashes, native PNG и SHA indexes:
`Q:\Codex\Scratch\polygon-style-controls-20261024\evidence`.
Первоначальные неудачные прогоны сохранены отдельно: проверка нашла некликабельный
checkbox в overlay-теме, слишком высокий preview на крупном landscape и слабый
контраст dark controls. Эти дефекты исправлены и покрыты окончательным прогоном.
Тест отверстия получил независимый oracle текущего фона вместо предположения
о белом фоне, а камера смешанных категорий охватывает все их реальные bounds.

Пустой собственный disposable-каталог удалён штатным PowerShell после проверки
manifest, физических путей и отсутствия reparse points; отсутствие подтверждено.
Удалено 0 bytes файлов, осталось около **16,4 MiB** компактных отчётов/снимков
до ревью PR; диагностические неудачи пересмотреть в течение 7 дней.
Нет собственных оставленных runtime/DB/emulator окружений или pending cleanup.
Shared Gradle build outputs/cache принадлежат существующим путям и не удалялись.
Пользовательский образец `polygon-preview.png` сохранён отдельно в Documents/Codex.

Preflight fetch подтвердил tracking 0/0 для всех Android владельцев. В desktop
монорепозитории параллельно находится другая задача `codex/road-edit-failure-tests`
с параллельными изменениями; они сохранены и не входят в этот Android commit.
Финальный preflight должен показывать их открыто, а не выдавать весь workspace
за clean.

Порядок будущего выпуска: maplib #60 через Merge Commit → pin remote merge в
maplibui/consumer → maplibui #44 через Merge Commit → pin remote merge в root →
приложение #78 через Squash. До публикации нужна повторная closure-проверка
всех обязательных изменений и Full на окончательных входах.

## Дополнительная проверка реального входа в категорию

Окончательный Related содержит 24 метода. Новый native case нажимает настоящую
строку RuleFeatureRendererUI, проверяет effective inherited параметры, точный
Cancel JSON, Save только выбранной категории, JSON reload и независимое удаление.
Редактор получает private effective clone, а Save закрепляет показанный snapshot.
Синхронизация касаний ждёт фокус, usable viewport и стабильные физические bounds;
штатная targeted UID защита Android сохранена. В окончательном прогоне отказы targeted input не повторились; реальные
нажатия не заменены прямым вызовом business handlers.
После изменения повторены обе матрицы и весь Related styles на смартфоне.
Финальные логи: evidence/phone-styles-confirmed; prior 23-case receipt относится
к предыдущей реализации и не используется как текущая квитанция.
