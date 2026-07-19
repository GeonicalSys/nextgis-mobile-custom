[CmdletBinding()]
param(
    [string[]] $ValidatorArgs = @('--workspace-root', '.'),
    [switch] $RunTests
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
    & $candidate.Exe @($candidate.Prefix) 'docs/scripts/validate_docs.py' @ValidatorArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    if ($RunTests) {
        & $candidate.Exe @($candidate.Prefix) -m unittest discover -s docs/tests -p 'test_*.py' -v
        exit $LASTEXITCODE
    }
    exit 0
}

throw 'No usable Python with PyYAML found. Install docs/requirements.txt.'
