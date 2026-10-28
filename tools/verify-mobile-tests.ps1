[CmdletBinding()]
param(
    [ValidateSet('Related','Full','Check-Publication','List')][string]$Scope='Related',
    [string[]]$Area=@('layers'),
    [string]$PhoneSerial,
    [string]$EmulatorSerial,
    [string]$TestProject,
    [string]$TestProjectFixture,
    [string]$Avd,
    [string]$SdkPath="$env:LOCALAPPDATA\Android\Sdk",
    [string]$PythonPath,
    [string]$OutputPath,
    [switch]$SkipBuild,
    [switch]$PhoneOnly,
    [switch]$AllowEmulator
)
$ErrorActionPreference='Stop'
$repoTaskPath=Split-Path -Parent $PSScriptRoot
$previousJavaTask=$env:JAVA_HOME
try {
    if(!$env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Android\Android Studio\jbr')) {
        $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
    }
    $pythonPrefix=@()
    if(!$PythonPath) {
        $launcherTask=Get-Command py -ErrorAction SilentlyContinue
        if($launcherTask) {$PythonPath=$launcherTask.Source; $pythonPrefix=@('-3')}
        else {$PythonPath=(Get-Command python -ErrorAction Stop).Source}
    }
    $runArgs=@(Join-Path $PSScriptRoot 'run_mobile_tests.py')
    $runArgs+=@('--scope',$Scope.ToLowerInvariant(),'--sdk',$SdkPath)
    foreach($areaTask in $Area) {$runArgs+=@('--area',$areaTask)}
    if($PhoneSerial) {$runArgs+=@('--phone-serial',$PhoneSerial)}
    if($EmulatorSerial) {$runArgs+=@('--emulator-serial',$EmulatorSerial)}
    if($TestProject) {$runArgs+=@('--test-project',$TestProject)}
    if($TestProjectFixture) {$runArgs+=@('--test-project-fixture',$TestProjectFixture)}
    if($Avd) {$runArgs+=@('--avd',$Avd)}
    if($OutputPath) {$runArgs+=@('--output',$OutputPath)}
    if($SkipBuild) {$runArgs+='--skip-build'}
    if($PhoneOnly) {$runArgs+='--phone-only'}
    if($AllowEmulator) {$runArgs+='--allow-emulator'}
    Push-Location $repoTaskPath
    try { & $PythonPath @pythonPrefix @runArgs; $runExitTask=$LASTEXITCODE }
    finally {Pop-Location}
} finally {$env:JAVA_HOME=$previousJavaTask}
exit $runExitTask
