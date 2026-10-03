---
title: Настройка рабочего места и сборка
type: runbook
last_verified: 2026-10-03
related_code:
  - gradle/wrapper/gradle-wrapper.properties
  - build.gradle
  - app/build.gradle
---

# Настройка рабочего места и сборка

## Требования

- Android SDK с платформой для `compileSdk 36`;
- совместимый JDK для AGP `9.1.0` и Gradle `9.3.1`;
- checkout root с инициализированными `maplib`, `maplibui`, `easypicker`;
- локальный `local.properties` с Android SDK path;
- при необходимости локальные properties для Sentry/NGID без публикации
  секретов.

Не копировать `local.properties`, DSN или client secrets в документацию и git.
Dummy Sentry DSN допустим только для локальной сборки, если код/плагин требует
непустое значение.

## Быстрая проверка

```powershell
.\gradlew.bat --version
.\gradlew.bat projects
.\gradlew.bat :maplib:testDebugUnitTest
.\gradlew.bat :app:assembleLisaRelease :app:assembleBelkaRelease
```

Если изменён только один модуль, начать с его unit/assemble. Общий API,
resources, manifest, flavors или Gradle требуют проверки обеих release-вариантов.

## Типовые проблемы

| Симптом | Проверить |
|---|---|
| Сабмодуль пуст/не та ревизия | `.gitmodules`, root pointer, `git submodule status` |
| SDK not found | локальный `local.properties`, установленный SDK 36 |
| Ошибка Sentry config | локальный `sentry.properties`, отсутствие реального секрета в git |
| Flavor resource collision | `productFlavors` и `buildTypes` в `app/build.gradle` |
| Только один бренд собирается | выполнить обе Lisa/Belka release tasks |

## Эмулятор для regression suite

На Windows необходим работающий гипервизор: emulator -accel-check должен
подтвердить WHPX. После включения Windows Hypervisor Platform может потребоваться
перезагрузка ОС. Для аудита используется отдельный data directory, read-only AVD,
port5556, SwiftShader, no-snapshot и no-window; исходный AVD не изменяется.
Команды adb адресуются только emulator-5556; подключённый телефон не затрагивается.

После установки Lisa Debug/test APK запустить `AUTO-RELIABILITY-NATIVE`; сначала
разрешить storage/location/notifications и включить системную location. Suite
использует свои local layers, SQLite triggers и localhost sign-in, без настоящего
аккаунта/NGW. Runtime и ограничения фиксируются в
[отчёте аудита](../reference/mobile-reliability-audit.md).
