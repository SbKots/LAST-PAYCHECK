param([switch]$AddRuntime)
$ErrorActionPreference='Stop'
$target=Join-Path (Split-Path -Parent $PSScriptRoot) '.env'
function New-LocalSecret {
    $bytes=New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes)
}
if (Test-Path -LiteralPath $target) {
    if (-not $AddRuntime) { throw '.env already exists; use -AddRuntime to add missing runtime passwords' }
    $existing = Get-Content -LiteralPath $target
    $additions = foreach ($name in @('DB_READER_PASSWORD', 'DB_WRITER_PASSWORD')) {
        if (-not ($existing -match "^\s*$name\s*=")) { "$name=$(New-LocalSecret)" }
    }
    if ($additions) { Add-Content -LiteralPath $target -Value ("`n" + ($additions -join "`n")) -Encoding utf8 }
    Write-Output 'Missing runtime passwords configured. Existing values were preserved.'
    return
}
# Пароль нужен только локально: не передавайте его и не коммитьте .env.
$securePassword=Read-Host 'Enter password for local PostgreSQL user postgres' -AsSecureString
$password=[System.Net.NetworkCredential]::new('postgres', $securePassword).Password
if ([string]::IsNullOrWhiteSpace($password)) { throw 'PostgreSQL password cannot be empty.' }
$readerPassword=New-LocalSecret
$writerPassword=New-LocalSecret
$token=New-LocalSecret
@"
DB_URL=jdbc:postgresql://127.0.0.1:5432/lastpay
DB_USER=postgres
DB_PASSWORD=$password
DB_READER_PASSWORD=$readerPassword
DB_WRITER_PASSWORD=$writerPassword
ADMIN_TOKEN=$token
ADMIN_SCOPE=catalog:write
HOST=127.0.0.1
PORT=8089
DEMO_MODE=true
"@ | Set-Content -LiteralPath $target -Encoding utf8
Write-Output 'Local .env created. Keep it private; do not commit it.'
