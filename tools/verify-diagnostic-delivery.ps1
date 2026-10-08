param(
    [Parameter(Mandatory = $true)][string]$TestDsn,
    [string]$Serial = 'emulator-5554',
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
Set-Location $repository
$endpoint = [Uri]$TestDsn
if ($endpoint.Scheme -ne 'https' -or $endpoint.AbsolutePath -ne '/errors/2' -or
        $endpoint.UserInfo -notmatch '^[a-zA-Z0-9]+$' -or
        $endpoint.Query -or $endpoint.Fragment) {
    throw 'Use the public DSN of the disposable GlitchTip verification project, not the production project.'
}
$hardware = (& $AdbPath -s $Serial shell getprop ro.hardware | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $hardware -notin @('ranchu', 'goldfish') -or $Serial -notlike 'emulator-*') {
    throw 'Diagnostic probes may run only on an isolated Android emulator, never on a working phone.'
}
$run = 'mobile-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$reports = Join-Path $repository "build\diagnostic-delivery-check\$run"
New-Item -ItemType Directory -Path $reports -Force | Out-Null
$runner = 'com.nextgis.mobile.debug.test/com.nextgis.mobile.util.DiagnosticsTestRunner'
$testClass = 'com.nextgis.mobile.util.DiagnosticsDeliveryTest'
$wifiBefore = (& $AdbPath -s $Serial shell settings get global wifi_on | Out-String).Trim()
$dataBefore = (& $AdbPath -s $Serial shell settings get global mobile_data | Out-String).Trim()
$javaBefore = $env:JAVA_HOME

function Assert-Probe([string]$Name, [string]$Path, [bool]$Fatal = $false) {
    $result = Get-Content -LiteralPath $Path -Raw
    if ($Fatal) {
        if ($result -notmatch 'shortMsg=Process crashed' -or $result -notmatch 'Synthetic offline fatal mobile diagnostic') {
            throw "Fatal probe did not delegate to Android: $Name. See $Path"
        }
    } elseif ($result -notmatch 'OK \(1 test\)') {
        throw "Diagnostic probe failed: $Name. See $Path"
    }
    Write-Output "$Name : PASSED"
}

function Invoke-Probe([string]$Name, [string]$Dsn = $TestDsn, [bool]$Fatal = $false) {
    & $AdbPath -s $Serial shell am force-stop com.nextgis.mobile.debug
    $path = Join-Path $reports "$Name.txt"
    & $AdbPath -s $Serial shell am instrument -w -e diagnosticsDsn $Dsn -e diagnosticsRun $run `
        -e class "$testClass#$Name" $runner > $path 2>&1
    Assert-Probe $Name $path $Fatal
}

try {
    if (!$SkipBuild) {
        if (!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
            throw 'Provide -JavaHome with the JDK required by this workspace.'
        }
        $env:JAVA_HOME = $JavaHome
        & .\gradlew.bat :app:assembleLisaDebug :app:assembleLisaDebugAndroidTest `
            -PdiagnosticDeliveryChecks=true --console=plain > (Join-Path $reports 'build.txt') 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Diagnostic probe build failed. See build.txt.' }
    }
    $appApk = Get-ChildItem app\build\outputs\apk\lisa\debug -Filter '*.apk' | Select-Object -First 1
    $testApk = Get-ChildItem app\build\outputs\apk\androidTest\lisa\debug -Filter '*.apk' | Select-Object -First 1
    if (!$appApk -or !$testApk) { throw 'Debug and instrumentation APKs are required.' }
    & $AdbPath -s $Serial install -r $appApk.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Debug APK installation failed.' }
    & $AdbPath -s $Serial install -r $testApk.FullName
    if ($LASTEXITCODE -ne 0) { throw 'Instrumentation APK installation failed.' }

    & $AdbPath -s $Serial shell svc wifi enable
    & $AdbPath -s $Serial shell svc data enable
    Invoke-Probe 'workerRetriesTemporaryServerFailureWithoutNetworkChange' 'http://local-test@127.0.0.1:18539/2'

    & $AdbPath -s $Serial shell svc wifi disable
    & $AdbPath -s $Serial shell svc data disable
    Invoke-Probe 'queueHandledWhileOffline'
    Invoke-Probe 'crashWhileOffline' $TestDsn $true
    Invoke-Probe 'reportsSurviveOfflineRestart'

    & $AdbPath -s $Serial shell am force-stop com.nextgis.mobile.debug
    $path = Join-Path $reports 'deliverWhenNetworkReturns.txt'
    $arguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-e', 'diagnosticsDsn', $TestDsn,
        '-e', 'diagnosticsRun', $run, '-e', 'class', "$testClass#deliverWhenNetworkReturns", $runner)
    $process = Start-Process -FilePath $AdbPath -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $path -RedirectStandardError (Join-Path $reports 'network-return-stderr.txt')
    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    while ([DateTime]::UtcNow -lt $deadline) {
        $pidText = (& $AdbPath -s $Serial shell pidof com.nextgis.mobile.debug | Out-String).Trim()
        if ($pidText) { break }
        Start-Sleep -Milliseconds 100
    }
    & $AdbPath -s $Serial shell svc wifi enable
    & $AdbPath -s $Serial shell svc data enable
    $deadline = [DateTime]::UtcNow.AddSeconds(110)
    while (!$process.WaitForExit(1000)) {
        if ([DateTime]::UtcNow -gt $deadline) {
            Stop-Process -Id $process.Id -Force
            throw 'Network-return probe timed out.'
        }
    }
    Assert-Probe 'deliverWhenNetworkReturns' $path
    @{run=$run; native_delivery='passed'; backend_content='verify in the disposable GlitchTip project';
        reports=$reports} | ConvertTo-Json | Set-Content (Join-Path $reports 'summary.json') -Encoding utf8
    Write-Output "Native delivery verified; confirm stack/device/version/operation in the test GlitchTip project. Reports: $reports"
} finally {
    & $AdbPath -s $Serial shell svc wifi $(if ($wifiBefore -eq '0') { 'disable' } else { 'enable' })
    & $AdbPath -s $Serial shell svc data $(if ($dataBefore -eq '0') { 'disable' } else { 'enable' })
    $env:JAVA_HOME = $javaBefore
}
