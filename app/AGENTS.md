# app — инструкции для ИИ-агентов

Перед изменением `app` прочитай:

1. [`docs/README.md`](docs/README.md) и [`docs/manifest.yaml`](docs/manifest.yaml).
2. [`../docs/registry/change-impact.yaml`](../docs/registry/change-impact.yaml).
3. Связанные invariant IDs из [`../docs/registry/invariants.yaml`](../docs/registry/invariants.yaml).
4. Для release/update — [`../docs/runbooks/release-apk.md`](../docs/runbooks/release-apk.md).

`app` владеет Android lifecycle, Map host implementation, product flavors,
preferences, release и updater. Он не должен переносить GIS/storage реализацию
из `maplib`/`maplibui`.

При изменении `MapFragment` проверить `MaplibreMapInteraction` и consumers. При
изменении Gradle/resources/manifest проверить Lisa и Belka. При изменении
updater проверить identity, version, size, APK hash и signing certificate
validation.

Обновляй README при смене пользовательского поведения или troubleshooting;
manifest — при смене entry point, key component, contract, setting или smoke.
Central cross-module docs обновляй в той же root-задаче.
