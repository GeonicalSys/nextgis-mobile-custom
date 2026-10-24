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
| maplibui | `6704566860699a000501b5cfb131cfe5ac406f81` | [#44](https://github.com/GeonicalSys/android_maplibui/pull/44) | master |
| Android | `604bb24d7e823d6d8683c837d4b67e2b2f1bfd98` | [#78](https://github.com/GeonicalSys/nextgis-mobile-custom/pull/78) | my-maplibre |

Root закрепляет указанные коммиты библиотек. Их предыдущие реализации
«Светлячка» остаются предками, проверка документации всего PR сохраняется.
[Контракт пакета](../architecture/polygon-style-controls.md).
Fingerprint кода/тестов трёх владельцев: `8cb8b83b8a86b8eda477a6d7bd5e064ca6864c86b6059fe3648f129da3b1449b`.
Финальный Related завершён 2026-10-24T12:07:53.191074+00:00.

## Результат

- maplib: 602 unit tests, 0 failures/errors/skips; maplibui: 93, также 0/0/0.
- maplibui assembleDebug — PASS. Lisa и Belka Release Kotlin/Java consumers
  скомпилированы; release APK не собирались. Версия не повышалась.
- Свежие Lisa verification Debug + AndroidTest APK собраны отдельно с
  fieldVerificationChecks/ciReliabilityChecks; затем Related styles PhoneOnly
  выполнен с SkipBuild на тех же APK: **23/23 distinct native cases PASS**.
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

- `ngmobile-3.1.2.30-lisa-debug.apk`: 84762977 bytes; SHA-256 `582b63ae02109cb1e0d6bacebe7300333d3f5bdcaf62bc48eb37f09a52a884b4`.
- `ngmobile-3.1.2.30-lisa-debug-androidTest.apk`: 968263 bytes; SHA-256 `b1c87d0fed3e910f4642b258c51e6665751d181a5c09c1da8441129237b9cb52`.

Несмотря на production-номер в имени файла, aapt подтвердил test package
`com.nextgis.mobile.verification.debug`, versionName 3.1.2.23/code 218.
Это отдельная Debug проверка, не новый подписанный выпуск.
Полный каталог теперь содержит 48 классов / 232 метода; **Full не запускался**
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
Удалено 0 bytes файлов, осталось около **7,5 MiB** компактных отчётов/снимков
до ревью PR; диагностические неудачи пересмотреть в течение 7 дней.
Нет собственных оставленных runtime/DB/emulator окружений или pending cleanup.
Shared Gradle build outputs/cache принадлежат существующим путям и не удалялись.
Пользовательский образец `polygon-preview.png` сохранён отдельно в Documents/Codex.

Preflight fetch подтвердил tracking 0/0 для всех Android владельцев. В desktop
монорепозитории параллельно находится другая задача `codex/road-edit-failure-tests`
с 18 изменёнными путями; они сохранены и не входят в этот Android commit.
Финальный preflight должен показывать их открыто, а не выдавать весь workspace
за clean.

Порядок будущего выпуска: maplib #60 через Merge Commit → pin remote merge в
maplibui/consumer → maplibui #44 через Merge Commit → pin remote merge в root →
приложение #78 через Squash. До публикации нужна повторная closure-проверка
всех обязательных изменений и Full на окончательных входах.
