# generate_license.ps1
# Run from the project root:  .\tools\generate_license.ps1 -Name "PlayerName" -Days 90
# Outputs a signed token. Send it to the player — they type  #activate <token>  in Minecraft.
# No recompile needed on your end.

param(
    [Parameter(Mandatory)][string]$Name,
    [int]$Days = 90
)

$toolsDir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir  = Split-Path -Parent $toolsDir
$privKeyFile = Join-Path $projectDir "private_key.b64"
$signerSrc   = Join-Path $toolsDir "LicenseSigner.java"
$signerClass = Join-Path $toolsDir "LicenseSigner.class"

if (-not (Test-Path $privKeyFile)) {
    Write-Error "private_key.b64 not found at: $privKeyFile"
    Write-Error "This file is gitignored. Keep a backup somewhere safe."
    exit 1
}

if (-not (Test-Path $signerClass)) {
    Write-Host "Compiling LicenseSigner..." -ForegroundColor Gray
    javac $signerSrc -d $toolsDir
    if ($LASTEXITCODE -ne 0) { Write-Error "Compile failed."; exit 1 }
}

$privKey = (Get-Content $privKeyFile -Raw).Trim()
$token   = java -cp $toolsDir LicenseSigner $privKey $Name $Days
if ($LASTEXITCODE -ne 0) { Write-Error "Signing failed."; exit 1 }

$today  = (Get-Date).ToString("yyyy-MM-dd")
$expiry = (Get-Date).AddDays($Days).ToString("yyyy-MM-dd")

Write-Host ""
Write-Host "=== License for: $Name  (expires $expiry) ===" -ForegroundColor Green
Write-Host ""
Write-Host $token
Write-Host ""
Write-Host "Please type this into the minecraft chat." -ForegroundColor Cyan
Write-Host "  #activate $token"
Write-Host "Do not share this with anyone." -ForegroundColor Yellow
Write-Host "Valid for: $Days days."
Write-Host "Username: $Name"
Write-Host "Licensed exp: $expiry"
Write-Host "Awarded licence on: $today"
Write-Host ""
