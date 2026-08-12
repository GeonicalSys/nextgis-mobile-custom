# app — инструкции для ИИ-агентов

Перед изменением `app` прочитай:

1. [`docs/README.md`](docs/README.md) и [`docs/manifest.yaml`](docs/manifest.yaml).
2. [`../docs/registry/change-impact.yaml`](../docs/registry/change-impact.yaml).
3. Связанные invariant IDs из [`../docs/registry/invariants.yaml`](../docs/registry/invariants.yaml).
4. Для release/update — [`../docs/runbooks/release-apk.md`](../docs/runbooks/release-apk.md).
5. Для NGW/Collector связи с desktop QGIS —
   [`../docs/architecture/lisa-ecosystem.md`](../docs/architecture/lisa-ecosystem.md)
   и [`../docs/registry/ecosystem.yaml`](../docs/registry/ecosystem.yaml).

`app` владеет Android lifecycle, Map host implementation, product flavors,
preferences, release и updater. Он не должен переносить GIS/storage реализацию
из `maplib`/`maplibui`.

При открытии любой карты `MainApplication` обязан нормализовать базовые слои:
дефолтный OSM/Mapnik — прямой дочерний слой с индексом `0`, «Мои треки» —
последний внутренний слой. Это правило действует и для нового Collector
workspace, а существующая видимость OSM не должна сбрасываться.

При изменении `MapFragment` проверить `MaplibreMapInteraction` и consumers. При
изменении Gradle/resources/manifest проверить Lisa и Belka. При изменении
updater проверить identity, version, size, APK hash и signing certificate
validation. Если updater отправляет пользователя за специальным разрешением
Android, он сохраняет только проверенный pending manifest, повторно валидирует
его после возврата и не требует повторного ручного запуска проверки обновлений.

## Версионирование variants

- Production Lisa/Belka получают `versionCode`/`versionName` из
  `defaultConfig`.
- Единственный debug variant — `lisaDebug`; его версия задаётся через
  `androidComponents.onVariants(selector().withBuildType("debug"))`.
- На AGP 9.x не добавляй `versionCode` или `versionName` в `buildTypes`.
- В `maplib` debug coupling задаётся override поля
  `BuildConfig.VERSION_NAME`; не используй `buildTypes.debug.versionName`.
- Debug-only bump не должен менять metadata Lisa/Belka Release. Обязательная
  проверка из корня репозитория:

  ```powershell
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools\verify-apk-version-matrix.ps1
  ```

  Она должна реально завершиться успешно до handoff. Не считай имя файла
  доказательством версии: проверяй APK metadata, а публикацию начинай только с
  `--dry-run`. При намеренном bump обновляй ожидаемую matrix в verification
  script вместе с Gradle constants.

Не добавляй чтение `Q:\standart_profiles`, QGIS plugin workspace,
`variables.py` или plugin mirrors в Android runtime. Данные между desktop и
mobile проходят через явный NGW/Collector contract или поддерживаемый
пользовательский import переносимого артефакта.

Обновляй README при смене пользовательского поведения или troubleshooting;
manifest — при смене entry point, key component, contract, setting или smoke.
Central cross-module docs обновляй в той же root-задаче.
