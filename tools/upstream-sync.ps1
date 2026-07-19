<#
    upstream-sync.ps1 — helper for syncing GeonicalSystem fork with NextGIS upstream.
    See docs/runbooks/upstream-sync.md for the manual recipe.

    Modes (parameter -Mode):
      Inventory      Fetches upstream in all 4 repos and prints, for each:
                     - cached vs real upstream/master tip,
                     - commit log <last-merged>..upstream/master --oneline,
                     - changed files --name-only.
                     Writes a draft report to UPSTREAM_SYNC_REPORT_<YYYY-MM-DD>.md.
      BackupTags     Creates pre-upstream-sync-<date>-<repo> tags on my-maplibre in all 4 repos.
      MergeSubmodules
                     For each submodule (maplib, maplibui, easypicker): checkout my-maplibre,
                     git merge --no-ff upstream/master. Stops on conflict for manual resolution.
      MergeRoot      Same merge for the parent repo + git add maplib maplibui easypicker.

    Safety:
      - Never runs `git push`, `--force`, `reset --hard`, or `--amend`.
      - Aborts a half-finished merge only if the work tree is clean.
      - Reports each repo state before acting.

    Example:
      pwsh tools/upstream-sync.ps1 -Mode Inventory
      pwsh tools/upstream-sync.ps1 -Mode BackupTags -Date 2026-05-15
      pwsh tools/upstream-sync.ps1 -Mode MergeSubmodules
      pwsh tools/upstream-sync.ps1 -Mode MergeRoot
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [ValidateSet('Inventory', 'BackupTags', 'MergeSubmodules', 'MergeRoot')]
    [string] $Mode = 'Inventory',

    [Parameter(Mandatory = $false)]
    [string] $Date = (Get-Date -Format 'yyyy-MM-dd'),

    [Parameter(Mandatory = $false)]
    [string] $Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$repos = @(
    [PSCustomObject]@{ Name = 'root';       Path = $Root },
    [PSCustomObject]@{ Name = 'maplib';     Path = Join-Path $Root 'maplib' },
    [PSCustomObject]@{ Name = 'maplibui';   Path = Join-Path $Root 'maplibui' },
    [PSCustomObject]@{ Name = 'easypicker'; Path = Join-Path $Root 'easypicker' }
)

function Invoke-Git {
    param([string] $Path, [string[]] $GitArgs)
    & git -C $Path @GitArgs
}

function Get-UpstreamTip {
    param([string] $Path)
    $sha = (& git -C $Path rev-parse 'upstream/master' 2>$null)
    return $sha.Trim()
}

function Get-MergeBase {
    param([string] $Path)
    $sha = (& git -C $Path merge-base 'my-maplibre' 'upstream/master' 2>$null)
    return $sha.Trim()
}

function Inventory {
    Write-Host "=== upstream-sync inventory ($Date) ===" -ForegroundColor Cyan
    $report = Join-Path $Root "UPSTREAM_SYNC_REPORT_$Date.md"
    "# Upstream sync inventory $Date" | Set-Content -Path $report -Encoding UTF8

    foreach ($r in $repos) {
        Write-Host ""
        Write-Host "--- $($r.Name) ---" -ForegroundColor Yellow
        Invoke-Git -Path $r.Path -GitArgs @('fetch', 'upstream', '--prune') | Out-Null
        $tip = Get-UpstreamTip -Path $r.Path
        $base = Get-MergeBase -Path $r.Path
        Write-Host "upstream/master tip: $tip"
        Write-Host "merge-base my-maplibre..upstream/master: $base"

        "`n## $($r.Name)" | Add-Content -Path $report
        "- upstream/master tip: $tip" | Add-Content -Path $report
        "- merge-base: $base" | Add-Content -Path $report
        "`n### Commits ($base..upstream/master)" | Add-Content -Path $report
        '```' | Add-Content -Path $report
        $log = Invoke-Git -Path $r.Path -GitArgs @('log', "$base..upstream/master", '--oneline', '--no-merges')
        if (-not $log) { $log = '(no new commits)' }
        $log | Add-Content -Path $report
        '```' | Add-Content -Path $report
        "`n### Changed files" | Add-Content -Path $report
        '```' | Add-Content -Path $report
        $files = Invoke-Git -Path $r.Path -GitArgs @('diff', '--name-only', "$base..upstream/master")
        if (-not $files) { $files = '(none)' }
        $files | Add-Content -Path $report
        '```' | Add-Content -Path $report
    }

    Write-Host ""
    Write-Host "Draft report written to: $report" -ForegroundColor Green
    Write-Host "Continue with -Mode BackupTags, then resolve merges manually." -ForegroundColor Green
}

function BackupTags {
    Write-Host "=== creating pre-upstream-sync-$Date-<repo> tags ===" -ForegroundColor Cyan
    foreach ($r in $repos) {
        $tag = "pre-upstream-sync-$Date-$($r.Name)"
        Write-Host "--- $($r.Name): $tag ---" -ForegroundColor Yellow
        $exists = Invoke-Git -Path $r.Path -GitArgs @('tag', '--list', $tag)
        if ($exists) {
            Write-Host "  (tag already exists, skipping)" -ForegroundColor DarkYellow
            continue
        }
        Invoke-Git -Path $r.Path -GitArgs @('tag', $tag, 'my-maplibre')
        Write-Host "  created at my-maplibre"
    }
    Write-Host "Done. To undo a merge: git -C <repo> reset --hard $tag (NOT run automatically)." -ForegroundColor Green
}

function MergeSubmodules {
    Write-Host "=== merging upstream/master into submodules my-maplibre ===" -ForegroundColor Cyan
    foreach ($r in $repos | Where-Object { $_.Name -ne 'root' }) {
        Write-Host ""
        Write-Host "--- $($r.Name) ---" -ForegroundColor Yellow
        $branch = (Invoke-Git -Path $r.Path -GitArgs @('rev-parse', '--abbrev-ref', 'HEAD')).Trim()
        if ($branch -ne 'my-maplibre') {
            Write-Host "  HEAD is on '$branch', switching to my-maplibre..." -ForegroundColor DarkYellow
            Invoke-Git -Path $r.Path -GitArgs @('checkout', 'my-maplibre')
        }
        $merge = Invoke-Git -Path $r.Path -GitArgs @('merge', '--no-ff', '--no-commit', 'upstream/master') 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  MERGE CONFLICT in $($r.Name). Resolve manually, then:" -ForegroundColor Red
            Write-Host "    git -C $($r.Path) status -sb"
            Write-Host "    # edit conflicted files, git add, git commit"
            Write-Host "  Stopping. Run -Mode MergeRoot after submodules are committed."
            return
        }
        Write-Host "  no conflicts; review staged changes then commit manually." -ForegroundColor Green
    }
}

function MergeRoot {
    Write-Host "=== merging upstream/master into root my-maplibre ===" -ForegroundColor Cyan
    $branch = (Invoke-Git -Path $Root -GitArgs @('rev-parse', '--abbrev-ref', 'HEAD')).Trim()
    if ($branch -ne 'my-maplibre') {
        Write-Host "Root HEAD is on '$branch', switching to my-maplibre..." -ForegroundColor DarkYellow
        Invoke-Git -Path $Root -GitArgs @('checkout', 'my-maplibre')
    }
    Write-Host "Staging submodule pointer updates first (to avoid 'overwritten by merge')..."
    Invoke-Git -Path $Root -GitArgs @('add', 'maplib', 'maplibui', 'easypicker')
    Invoke-Git -Path $Root -GitArgs @('commit', '-m', 'chore: bump submodule pointers before upstream merge')
    $merge = Invoke-Git -Path $Root -GitArgs @('merge', '--no-ff', '--no-commit', 'upstream/master') 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Root MERGE CONFLICT. Resolve manually, then git add / git commit." -ForegroundColor Red
        return
    }
    Write-Host "Root merge ready. Review staged changes, then commit manually." -ForegroundColor Green
}

switch ($Mode) {
    'Inventory'       { Inventory }
    'BackupTags'      { BackupTags }
    'MergeSubmodules' { MergeSubmodules }
    'MergeRoot'       { MergeRoot }
}
