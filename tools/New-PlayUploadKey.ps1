param(
    [Parameter(Mandatory = $true)][string]$PrivateDirectory,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Set JAVA_HOME to Android Studio JBR or pass -JavaHome.' }
$keytool = Join-Path $JavaHome 'bin/keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'keytool.exe was not found.' }
$privatePath = [IO.Path]::GetFullPath($PrivateDirectory)
$repoPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if ($privatePath.StartsWith($repoPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or $privatePath -eq $repoPath) {
    throw 'Choose a private directory outside the source repository.'
}
New-Item -ItemType Directory -Path $privatePath -Force | Out-Null
$store = Join-Path $privatePath 'parcelrise-upload.p12'
$credentialsPath = Join-Path $privatePath 'upload-credentials.json'
if ((Test-Path -LiteralPath $store) -or (Test-Path -LiteralPath $credentialsPath)) { throw 'Upload key already exists; it will not be replaced.' }
$passwordBytes = New-Object byte[] 32
$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($passwordBytes) } finally { $random.Dispose() }
$password = [Convert]::ToBase64String($passwordBytes)
$previousPassword = $env:PARCELRISE_KEYTOOL_PASSWORD
try {
    $env:PARCELRISE_KEYTOOL_PASSWORD = $password
    & $keytool -genkeypair -storetype PKCS12 -keystore $store -alias upload -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Parcelrise Tycoon Upload' -storepass:env PARCELRISE_KEYTOOL_PASSWORD -keypass:env PARCELRISE_KEYTOOL_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    & $keytool -exportcert -rfc -keystore $store -alias upload -storepass:env PARCELRISE_KEYTOOL_PASSWORD -file (Join-Path $privatePath 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Certificate export failed.' }
    [ordered]@{storeFile='parcelrise-upload.p12';storePassword=$password;keyAlias='upload';keyPassword=$password} |
        ConvertTo-Json | Set-Content -LiteralPath $credentialsPath -Encoding utf8
    Write-Output 'Dedicated upload key and private credentials created. Keep this directory private and backed up.'
} finally {
    $env:PARCELRISE_KEYTOOL_PASSWORD = $previousPassword
    $password = $null
}
