# NextGIS Mobile

GeonicalSystem maintains the LISA and BELKA Android variants of
[NextGIS Mobile](https://github.com/nextgis/android_gisapp) for field work
with NextGIS Web. The [LISA ecosystem guide](docs/architecture/lisa-ecosystem.md)
describes how the app connects to LISA desktop and its QGIS plugins.

## Table of contents

- [Key capabilities](#key-capabilities)
  - [Field data collection](#field-data-collection)
  - [Offline-first workflows](#offline-first-workflows)
  - [Synchronization with NextGIS Web](#synchronization-with-nextgis-web)
  - [Map visualization and layer control](#map-visualization-and-layer-control)
  - [GPS tracking](#gps-tracking)
  - [Data management and editing](#data-management-and-editing)
- [Typical use cases](#typical-use-cases)
- [Installation](#installation)
- [Documentation](#documentation)
- [Community and ecosystem](#community-and-ecosystem)
- [Commercial support](#commercial-support)
- [License](#license)

## Key capabilities

### Field data collection

The [form guide](docs/guides/form-behavior-user-guide.md) explains how to
fill in fields, use lookup lists and save features.

### Offline-first workflows

See [shared offline basemaps](docs/architecture/shared-underlays.md) for
local map storage and use across projects.

### Synchronization with NextGIS Web

The [project synchronization guide](docs/guides/project-synchronization-user-guide.md)
explains when the app sends local edits and how it handles a server interruption.

### Map visualization and layer control

See [map rendering](docs/architecture/map-rendering.md) for rendering modes,
layer order, styles and labels.

### GPS Tracking

See [shared tracks](docs/architecture/shared-tracks.md) for track recording,
history and synchronization.

### Data management and editing

See [feature editing](docs/architecture/feature-copy-transform.md) for
copying, moving and rotating features in the app.

## Typical use cases

Use mobile projects to collect and update geospatial data in the field.
The [mobile project setup guide](docs/runbooks/collector-project-setup.md)
covers project configuration in NextGIS Web.

## Installation

Use the signed APK or update channel for your LISA or BELKA deployment.
See [package and channel identities](docs/reference/flavors-and-versioning.md)
and the [APK release guide](docs/runbooks/release-apk.md) for details.

The official NextGIS Mobile app is listed on
[Google Play](https://play.google.com/store/apps/details?id=com.nextgis.mobile).

## Documentation

- [App documentation by role](docs/README.md)
- [Start here for contributors and agents](docs/START-HERE.md)
- [GeonicalSystem fork customizations](docs/reference/fork-customizations.md)
- [Official NextGIS Mobile user guide](https://docs.nextgis.com/docs_ngmobile/source/index.html)

Validate the App documentation locally from this repository root.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File tools/docs-check.ps1 -RunTests
```

## Community and ecosystem

- [NextGIS community forum](https://community.nextgis.com)
- [Upstream GitHub issues](https://github.com/nextgis/android_gisapp/issues)
- [NextGIS Web](https://github.com/nextgis/nextgisweb)
- [NextGIS Connect for QGIS](https://github.com/nextgis/nextgis_connect)

## Commercial support

See [NextGIS contacts](https://nextgis.com/contact/) for support from the
upstream NextGIS team.

## License

NextGIS Mobile is released under the GNU General Public License v3.0 or later.
See the repository's [license text](LICENSE).
