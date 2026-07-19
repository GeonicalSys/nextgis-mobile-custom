[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ModuleId,
    [switch] $DryRun
)

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Set-Location $workspace

$candidates = @()
$py = Get-Command py -ErrorAction SilentlyContinue
if ($py) {
    $candidates += [pscustomobject]@{ Exe = $py.Source; Prefix = @('-3') }
}
$python = Get-Command python -ErrorAction SilentlyContinue
if ($python) {
    $candidates += [pscustomobject]@{ Exe = $python.Source; Prefix = @() }
}
$osgeoPython = 'C:\OSGeo4W\apps\Python312\python.exe'
if (Test-Path -LiteralPath $osgeoPython) {
    $candidates += [pscustomobject]@{ Exe = $osgeoPython; Prefix = @() }
}

foreach ($candidate in $candidates) {
    try {
        & $candidate.Exe @($candidate.Prefix) -c 'import yaml' 2>$null
    }
    catch {
        continue
    }
    if ($LASTEXITCODE -ne 0) {
        continue
    }
    $arguments = @(
        'docs/scripts/scaffold_module_docs.py',
        $ModuleId,
        '--workspace-root',
        '.'
    )
    if ($DryRun) {
        $arguments += '--dry-run'
    }
    & $candidate.Exe @($candidate.Prefix) @arguments
    exit $LASTEXITCODE
}

throw 'No usable Python with PyYAML found. Install docs/requirements.txt.'
