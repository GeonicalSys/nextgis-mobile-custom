# AI context — GeonicalSystem / NextGIS Mobile fork

**Purpose:** paste or `@`-reference this file at the start of a **new chat** so the assistant does not need the full prior conversation. It complements (does not replace) the detailed catalogs below.

**Human note:** основной язык общения с пользователем — **русский**; технические имена классов/файлов — как в коде (англ.).

---

## 1. What this repo is

- **Upstream:** official [NextGIS Mobile for Android](https://github.com/nextgis/nextgis_mobile_android) (`upstream/master` on remote `upstream`).
- **Fork:** GeonicalSystem customization on branch **`my-maplibre`** (same branch name in **all** repos below).
- **Working tree:** root repo **`android_gisapp`** with **git submodules:**
  - `maplib` → fork of `nextgis/android_maplib`
  - `maplibui` → fork of `nextgis/android_maplibui`
  - `easypicker` → fork of `nextgis/easypicker`
- Each submodule has **`origin`** (write, GeonicalSystem) and **`upstream`** (read-only, nextgis). Remotes mapping: see `CUSTOMIZATIONS.md` §15.

---

## 2. Read first (source of truth order)

| Priority | File | Why |
|----------|------|-----|
| 1 | [`CUSTOMIZATIONS.md`](CUSTOMIZATIONS.md) | Full list of fork-only behavior, files, and **non-negotiable** choices (e.g. §14 tracks UI, collector types, flavors `lisa`/`belka`). |
| 2 | [`UPSTREAM_SYNC_REPORT.md`](UPSTREAM_SYNC_REPORT.md) | Per-cycle technical report: what merged, what rejected, conflict table, SHAs. |
| 3 | [`WHATS_NEW.md`](WHATS_NEW.md) | User-facing changelog (Russian). |
| 4 | [`tools/upstream-sync.ps1`](tools/upstream-sync.ps1) | Safe automation: `Inventory`, `BackupTags`, `MergeSubmodules`, `MergeRoot` (no push/force/amend). |

---

## 3. Workflow expectations (user + project)

- **Upstream sync:** no “blind” merges. Understand diffs; on overlap with fork, compare with `CUSTOMIZATIONS.md` and choose **upstream / ours / hybrid** with a short rationale; document in `UPSTREAM_SYNC_REPORT.md` for the cycle.
- **Merge order:** typically **`easypicker` → `maplib` → `maplibui`** (submodules), then **root** `android_gisapp` with updated submodule pointers. Use backup tags `pre-upstream-sync-<date>-<repo>` before merges (see §15 + script).
- **Git commits:** user rule — **only commit when explicitly asked**; do not change `git config`; no destructive git unless requested.
- **Build:** root `sentry.properties` may be a **dummy DSN** for local builds; both flavors matter: `:app:assembleLisaRelease`, `:app:assembleBelkaRelease` (see `CUSTOMIZATIONS.md` §16 / flavors).

---

## 4. Versioning (fork)

- **`versionName`:** `<upstream-base>.<fork-patch>` (e.g. `3.0.3.1` on top of upstream `3.0.3`).
- **`versionCode`:** bump in **`app/build.gradle`**; align **`maplib/build.gradle`** `versionName` for `BuildConfig` where applicable.
- Details: `CUSTOMIZATIONS.md` §16.

---

## 5. High-risk technical areas (triage map)

Use this when merging or debugging map/layers/walk:

| Topic | Main locations | Notes |
|-------|----------------|-------|
| MapLibre style, layer order, hot-add | `maplib/.../MapDrawable.java` | Upstream renamed `mapFragment` → **`mapContext`**; `setMapContext` / `mapContext.get()`. |
| Raster anchor / sibling insert | `maplib/.../MPLFeaturesUtils.java` | `resolveRasterSiblingAnchorOrNull`, `RasterSiblingAnchor`; interacts with `signaturesRootLayer`. |
| NGRc / local TMS fill + list order | `maplibui/.../LayerFillService.java` | **LayerGroup index 0 = bottom**; NGRc: above OSM when `osm` layer exists, else insert at `0`. |
| “Tap layer list fixes Z-order” | `maplibui/.../ReorderedLayerView.java` | `ACTION_UP` → `notifyDataChanged` → **`loadLayersLite()`**. After hot-add raster, fork calls `loadLayersLite()` from `MapDrawable.addLayerByID` for `GT_RASTER_WA` so map matches list without restart. |
| Walk-by-geometry | `app/.../MapFragment.kt`, `maplib/.../MapDrawable.java`, `maplibui/.../EditLayerOverlay.java` | **Per-aspect decisions:** `CUSTOMIZATIONS.md` **§17** (ours vs upstream vs hybrid). |
| NGW / collector | `maplib/.../datasource/ngw/Connection.java`, `maplibui/.../SelectNGWResourceActivity.java` | Keep **`NGWResourceTypeCollector`** where upstream commented it out — fork requirement. |
| App ↔ map host interface | `MaplibreMapInteraction` in maplib | New methods may appear; implement in `MapFragment.kt` (possibly no-op if fork uses its own deferred reload — see §17 table). |

---

## 6. Invariants (do not regress without explicit user decision)

- **§14 / tracks:** fork intentionally **does not** enable upstream-style **start/end track flag** layers on the map style; do not reintroduce those sources/layers unless the user changes §14.
- **Flavors:** preserve **`lisa` / `belka`** and `APP_NAME` via flavors — do not overwrite with upstream single-`APP_NAME` in `release` if that conflicts.
- **NGRc stack:** rasters must not sit above the whole user stack after import; list order must match map after import **without** requiring restart (current fix: `loadLayersLite()` after hot raster add + `LayerFillService` insert rules).

---

## 7. Typical assistant bootstrap (new chat)

1. Confirm branch **`my-maplibre`** and clean `git status` in root + each submodule (if task is git-related).
2. Open **`CUSTOMIZATIONS.md`** for the relevant § (walk §2/§17, git §15, release §16, etc.).
3. For upstream work: last cycle in **`UPSTREAM_SYNC_REPORT.md`**, then `tools/upstream-sync.ps1 -Mode Inventory`.
4. After code changes: compile affected modules + both app flavors when touching app/maplib/maplibui.

---

## 8. Related transcript (optional)

Long-form chat history (if something is missing here): see Cursor agent transcripts for workspace `q-android-projects`; search keywords: `NGRc`, `loadLayersLite`, `upstream`, `walk`, `MapDrawable`.

---

*Last consolidated for handoff: 2026-05-15 (upstream sync cycle + NGRc Z-order / walk reconciliation). Update this file when a major new invariant or workflow change lands.*
