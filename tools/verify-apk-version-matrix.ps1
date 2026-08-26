param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repositoryRoot = Split-Path -Parent $PSScriptRoot

if (-not $SkipBuild) {
    Push-Location $repositoryRoot
    try {
        & .\gradlew.bat `
            :app:assembleLisaDebug `
            :app:assembleLisaRelease `
            :app:assembleBelkaRelease
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle version-matrix build failed with exit code $LASTEXITCODE."
        }
    }
    finally {
        Pop-Location
    }
}

Push-Location $repositoryRoot
try {
    $releaseRuntimeDependencies = & .\gradlew.bat -q `
        :app:dependencies `
        --configuration lisaReleaseRuntimeClasspath
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle dependency report failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

$releaseRuntimeDependencyText = $releaseRuntimeDependencies -join "`n"
if ($releaseRuntimeDependencyText -notmatch 'org\.maplibre\.gl:android-sdk-opengl:13\.0\.2') {
    throw 'Lisa Release runtime does not contain MapLibre OpenGL 13.0.2.'
}
if ($releaseRuntimeDependencyText -match 'org\.maplibre\.gl:android-sdk(?:-vulkan)?:13\.0\.2') {
    throw 'Lisa Release runtime contains the generic or Vulkan MapLibre 13.0.2 artifact.'
}
Write-Output 'OK: Lisa Release runtime uses MapLibre android-sdk-opengl:13.0.2 without generic/Vulkan artifact'

$sdkCandidates = @(@(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
) | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Container) })

if ($sdkCandidates.Count -eq 0) {
    throw 'Android SDK not found in ANDROID_SDK_ROOT, ANDROID_HOME or LOCALAPPDATA.'
}

$aapt = Get-ChildItem -LiteralPath (Join-Path $sdkCandidates[0] 'build-tools') `
        -Directory |
    Sort-Object { [version](($_.Name -split '-')[0]) } -Descending |
    ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
    Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
    Select-Object -First 1

if (-not $aapt) {
    throw 'aapt.exe not found in Android SDK build-tools.'
}

function Get-VariantApk {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RelativeOutputDirectory
    )

    $outputDirectory = Join-Path $repositoryRoot $RelativeOutputDirectory
    $metadataPath = Join-Path $outputDirectory 'output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "Missing APK metadata: $metadataPath"
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw -Encoding utf8 |
        ConvertFrom-Json
    $elements = @($metadata.elements)
    if ($elements.Count -ne 1) {
        throw "Expected one APK output in $metadataPath, found $($elements.Count)."
    }

    $apkPath = Join-Path $outputDirectory $elements[0].outputFile
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw "APK listed by metadata does not exist: $apkPath"
    }
    return $apkPath
}

function Assert-ApkIdentity {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$ApkPath,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedApplicationId,
        [Parameter(Mandatory = $true)]
        [int]$ExpectedVersionCode,
        [Parameter(Mandatory = $true)]
        [string]$ExpectedVersionName
    )

    $badging = & $aapt dump badging $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "aapt failed for $ApkPath with exit code $LASTEXITCODE."
    }

    $packageLine = $badging | Select-Object -First 1
    if ($packageLine -notmatch "package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'") {
        throw "Cannot parse package identity from aapt output for $ApkPath."
    }

    $actualApplicationId = $Matches[1]
    $actualVersionCode = [int]$Matches[2]
    $actualVersionName = $Matches[3]

    if ($actualApplicationId -ne $ExpectedApplicationId) {
        throw "$Name applicationId mismatch: expected $ExpectedApplicationId, got $actualApplicationId."
    }
    if ($actualVersionCode -ne $ExpectedVersionCode) {
        throw "$Name versionCode mismatch: expected $ExpectedVersionCode, got $actualVersionCode."
    }
    if ($actualVersionName -ne $ExpectedVersionName) {
        throw "$Name versionName mismatch: expected $ExpectedVersionName, got $actualVersionName."
    }

    Write-Output "OK: $Name package=$actualApplicationId versionCode=$actualVersionCode versionName=$actualVersionName apk=$ApkPath"
}

# Keep these expected values independent from Gradle constants so an accidental production
# bump or a missing variant override is detected instead of being accepted automatically.
$matrix = @(
    [pscustomobject]@{
        Name = 'Lisa Debug'
        Directory = 'app\build\outputs\apk\lisa\debug'
        ApplicationId = 'com.nextgis.mobile.debug'
        VersionCode = 213
        VersionName = '3.1.2.18'
    },
    [pscustomobject]@{
        Name = 'Lisa Release'
        Directory = 'app\build\outputs\apk\lisa\release'
        ApplicationId = 'com.nextgis.mobile.geonical'
        VersionCode = 212
        VersionName = '3.1.2.18'
    },
    [pscustomobject]@{
        Name = 'Belka Release'
        Directory = 'app\build\outputs\apk\belka\release'
        ApplicationId = 'com.nextgis.mobile.geonical'
        VersionCode = 212
        VersionName = '3.1.2.18'
    }
)

foreach ($entry in $matrix) {
    $apkPath = Get-VariantApk -RelativeOutputDirectory $entry.Directory
    Assert-ApkIdentity `
        -Name $entry.Name `
        -ApkPath $apkPath `
        -ExpectedApplicationId $entry.ApplicationId `
        -ExpectedVersionCode $entry.VersionCode `
        -ExpectedVersionName $entry.VersionName
}

$maplibVersions = @{
    debug = '3.1.2.18'
    release = '3.1.2.18'
}

foreach ($buildType in $maplibVersions.Keys) {
    $buildConfigPath = Join-Path $repositoryRoot `
        "maplib\build\generated\source\buildConfig\$buildType\com\nextgis\maplib\BuildConfig.java"
    if (-not (Test-Path -LiteralPath $buildConfigPath -PathType Leaf)) {
        throw "Missing maplib BuildConfig for ${buildType}: $buildConfigPath"
    }

    $expected = $maplibVersions[$buildType]
    $buildConfig = Get-Content -LiteralPath $buildConfigPath -Raw -Encoding utf8
    if ($buildConfig -notmatch "VERSION_NAME = `"$([regex]::Escape($expected))`";") {
        throw "maplib $buildType VERSION_NAME mismatch: expected $expected."
    }
    Write-Output "OK: maplib $buildType VERSION_NAME=$expected"
}
