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
    $localValidatorTask = @($ValidatorArgs)
    $changedJsonTask = $null
    if ($RunTests) {
        & $candidate.Exe @($candidate.Prefix) -B 'tools/check_update_reports.py'
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $candidate.Exe @($candidate.Prefix) -B -m unittest discover -s tools -p 'test_check_update_reports.py' -v
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $policyTask = Get-Content -LiteralPath 'tools/update-report-policy.json' -Raw | ConvertFrom-Json
        $baseTask = 'origin/' + $policyTask.base_branch
        $changedJsonTask = & $candidate.Exe @($candidate.Prefix) -B 'docs/scripts/check_change_impact.py' --local-paths --base $baseTask
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read complete local task paths for documentation impact' }
        $impactPathsTask = ConvertFrom-Json -InputObject ($changedJsonTask -join [Environment]::NewLine)
        if ($impactPathsTask.Count -gt 0) {
            $localValidatorTask += @('--enforce-diff', '--changed-files-stdin')
        } else {
            $changedJsonTask = $null
            Write-Host 'Documentation impact: no task/worktree changes; complete workspace validation still runs.'
        }
    }
    if ($null -eq $changedJsonTask) {
        & $candidate.Exe @($candidate.Prefix) -B 'docs/scripts/validate_docs.py' @localValidatorTask
    } else {
        $changedJsonTask | & $candidate.Exe @($candidate.Prefix) -B 'docs/scripts/validate_docs.py' @localValidatorTask
    }
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    if ($RunTests) {
        & $candidate.Exe @($candidate.Prefix) -B -m unittest discover -s docs/tests -p 'test_*.py' -v
        exit $LASTEXITCODE
    }
    exit 0
}

throw 'No usable Python with PyYAML found. Install docs/requirements.txt.'
