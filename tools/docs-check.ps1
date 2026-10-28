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
    if ($RunTests) {
        & $candidate.Exe @($candidate.Prefix) -B 'tools/check_update_reports.py'
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & $candidate.Exe @($candidate.Prefix) -B -m unittest discover -s tools -p 'test_check_update_reports.py' -v
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        $policyTask = Get-Content -LiteralPath 'tools/update-report-policy.json' -Raw | ConvertFrom-Json
        $baseTask = 'origin/' + $policyTask.base_branch
        $changedTask = @(& git diff --name-only ($baseTask + '...HEAD'))
        if ($LASTEXITCODE -ne 0) { throw 'Local impact comparison needs the fetched target branch' }
        $changedTask += @(& git diff --name-only HEAD)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read local changes for documentation impact' }
        $changedTask += @(& git ls-files --others --exclude-standard)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot read new files for documentation impact' }
        $impactPathsTask = @($changedTask | Where-Object { $_ } | Sort-Object -Unique)
        if ($impactPathsTask.Count -gt 0) {
            $localValidatorTask += '--enforce-diff'
            foreach ($pathTask in $impactPathsTask) {
                $localValidatorTask += @('--changed-file', $pathTask)
            }
        } else {
            Write-Host 'Documentation impact: no task/worktree changes; complete workspace validation still runs.'
        }
    }
    & $candidate.Exe @($candidate.Prefix) -B 'docs/scripts/validate_docs.py' @localValidatorTask
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
