$ErrorActionPreference = "Stop"
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Building Target Guard APK and Updating GitHub Release " -ForegroundColor Cyan
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

Write-Host "3. Uploading TargetGuard.apk to GitHub Release v1.0.0..." -ForegroundColor Yellow
gh release upload v1.0.0 "TargetGuard.apk" --clobber

Write-Host ""
Write-Host " SUCCESS! APK updated and uploaded to GitHub!" -ForegroundColor Green
Write-Host "Direct Download URL:" -ForegroundColor Cyan
Write-Host "https://github.com/imteyazzzzz/target-guard/releases/latest/download/TargetGuard.apk" -ForegroundColor Green
