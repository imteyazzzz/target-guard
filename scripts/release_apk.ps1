$ErrorActionPreference = "Stop"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Building Target Guard APK (Local Server Mode) " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$rootDir = Split-Path -Parent $PSScriptRoot
Set-Location $rootDir

Write-Host "1. Running Gradle build..." -ForegroundColor Yellow
& .\gradlew.bat assembleDebug

$sourceApk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $sourceApk)) {
    Write-Error "APK file not found at $sourceApk"
    exit 1
}

Write-Host "2. Copying APK to root and web directory..." -ForegroundColor Yellow
Copy-Item $sourceApk "TargetGuard.apk" -Force
Copy-Item $sourceApk "web\TargetGuard.apk" -Force

Write-Host ""
Write-Host " SUCCESS! APK built and ready in Web Portal!" -ForegroundColor Green
Write-Host "Direct Download URL (Web Portal):" -ForegroundColor Cyan
Write-Host "http://localhost:3000/TargetGuard.apk" -ForegroundColor Green
