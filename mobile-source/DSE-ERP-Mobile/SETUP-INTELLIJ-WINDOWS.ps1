$ErrorActionPreference = 'Stop'

Write-Host 'DSE ERP Android - IntelliJ 2026.2.1 setup check'
Write-Host ''

# IMPORTANT: force the pipeline result to remain an array even when it contains
# only one Android SDK path. Without @(...), PowerShell string indexing can turn
# a path such as C:\Users\...\Android\Sdk into just "C".
$sdkCandidates = @(
    @(
        $env:ANDROID_HOME
        $env:ANDROID_SDK_ROOT
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
)

if ($sdkCandidates.Count -eq 0) {
    Write-Host 'ANDROID_SDK_NOT_FOUND'
    Write-Host ''
    Write-Host 'Android Studio is NOT required.'
    Write-Host 'Install the official Android CLI in PowerShell with:'
    Write-Host '  curl.exe -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "$env:TEMP\android-cli-install.cmd"'
    Write-Host '  cmd.exe /c "$env:TEMP\android-cli-install.cmd"'
    Write-Host ''
    Write-Host 'Then CLOSE and REOPEN IntelliJ/PowerShell and run:'
    Write-Host '  android sdk install platforms/android-36 build-tools/36.0.0 platform-tools'
    exit 2
}

$sdk = $sdkCandidates | Select-Object -First 1
Write-Host "Android SDK: $sdk"

# local.properties accepts forward slashes on Windows and avoids escaping issues.
$sdkForGradle = $sdk.Replace('\','/')
Set-Content -Path (Join-Path $PSScriptRoot 'local.properties') -Value "sdk.dir=$sdkForGradle" -Encoding ASCII
Write-Host 'local.properties configured.'

$required = @(
    (Join-Path $sdk 'platforms\android-36'),
    (Join-Path $sdk 'build-tools\36.0.0'),
    (Join-Path $sdk 'platform-tools')
)

$missing = @($required | Where-Object { -not (Test-Path $_) })
if ($missing.Count -gt 0) {
    Write-Host 'ANDROID_SDK_COMPONENTS_MISSING'
    $missing | ForEach-Object { Write-Host "Missing: $_" }
    Write-Host ''
    if (Get-Command android -ErrorAction SilentlyContinue) {
        Write-Host 'Install them with:'
        Write-Host '  android sdk install platforms/android-36 build-tools/36.0.0 platform-tools'
    } else {
        Write-Host 'The Android CLI is not on PATH.'
        Write-Host 'Install it first with:'
        Write-Host '  curl.exe -fsSL https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd -o "$env:TEMP\android-cli-install.cmd"'
        Write-Host '  cmd.exe /c "$env:TEMP\android-cli-install.cmd"'
        Write-Host 'Then close/reopen IntelliJ/PowerShell and rerun this setup script.'
    }
    exit 3
}

Push-Location $PSScriptRoot
try {
    & .\gradlew.bat :androidApp:tasks --all
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host ''
    Write-Host 'ANDROID_GRADLE_MODEL_READY'
    Write-Host 'Return to IntelliJ and click Reload All Gradle Projects.'
    Write-Host 'The supplied run configuration is named: DSE ERP Android'
} finally {
    Pop-Location
}
