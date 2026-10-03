param(
    [Parameter(Mandatory = $true)][string]$CredentialsFile,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$Verify
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to Android Studio JBR or pass -JavaHome.' }
$credentialsPath = (Resolve-Path -LiteralPath $CredentialsFile).Path
$credentials = Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
$store = $credentials.storeFile
if (-not [IO.Path]::IsPathRooted($store)) { $store = Join-Path (Split-Path $credentialsPath) $store }
if (-not (Test-Path -LiteralPath $store)) { throw 'Upload keystore not found.' }
foreach ($field in 'storePassword','keyAlias','keyPassword') {
    if ([string]::IsNullOrWhiteSpace($credentials.$field)) { throw "Missing credential field: $field" }
}
$names = @('JAVA_HOME','PARCELRISE_UPLOAD_STORE','PARCELRISE_UPLOAD_STORE_PASSWORD','PARCELRISE_UPLOAD_ALIAS','PARCELRISE_UPLOAD_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:JAVA_HOME = $JavaHome
    $env:PARCELRISE_UPLOAD_STORE = $store
    $env:PARCELRISE_UPLOAD_STORE_PASSWORD = $credentials.storePassword
    $env:PARCELRISE_UPLOAD_ALIAS = $credentials.keyAlias
    $env:PARCELRISE_UPLOAD_KEY_PASSWORD = $credentials.keyPassword
    Push-Location (Join-Path $PSScriptRoot '..')
    try {
        $tasks = @(':app:bundleRelease', ':app:assembleRelease')
        if ($Verify) { $tasks += ':app:lintRelease' }
        & .\gradlew.bat @tasks --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    } finally { Pop-Location }
    Write-Output 'Signed AAB: app/build/outputs/bundle/release/app-release.aab'
    Write-Output 'Signed APK: app/build/outputs/apk/release/app-release.apk'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    $credentials = $null
}
