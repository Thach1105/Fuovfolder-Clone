# FuOverflow backend: clean, build, and run (same steps as manual workflow).
# From backend folder:
#   mvn clean
#   mvn install package
#   cd app && mvn spring-boot:run
#
# Usage:
#   .\run.ps1
#   .\run.ps1 -SkipDocker
#   .\run.ps1 -SkipClean
#   .\run.ps1 -SkipTests

param(
    [switch] $SkipDocker,
    [switch] $SkipClean,
    [switch] $SkipTests
)

$ErrorActionPreference = "Stop"
$BackendRoot = $PSScriptRoot
$AppRoot = Join-Path $BackendRoot "app"

function Require-Command {
    param([string] $Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found on PATH: $Name"
    }
}

function Invoke-Maven {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Goals,
        [string] $WorkingDirectory = $BackendRoot
    )
    Push-Location $WorkingDirectory
    try {
        Write-Host "    (cwd: $WorkingDirectory) mvn $($Goals -join ' ')" -ForegroundColor DarkGray
        & mvn @Goals
        if ($LASTEXITCODE -ne 0) {
            throw "mvn failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

Require-Command "mvn"

Write-Host "==> FuOverflow backend ($BackendRoot)" -ForegroundColor Cyan

if (-not $SkipDocker) {
    Require-Command "docker"
    Write-Host "==> docker compose up -d postgres redis minio minio-init" -ForegroundColor Cyan
    Push-Location $BackendRoot
    try {
        & docker compose up -d postgres redis minio minio-init
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "==> Skipping docker compose (-SkipDocker)" -ForegroundColor Yellow
}

if (-not $SkipClean) {
    Write-Host "==> mvn clean" -ForegroundColor Cyan
    Invoke-Maven -Goals @("clean")
} else {
    Write-Host "==> Skipping mvn clean (-SkipClean)" -ForegroundColor Yellow
}

$installGoals = @("install", "package")
if ($SkipTests) {
    $installGoals += "-DskipTests"
}
Write-Host "==> mvn install package$(if ($SkipTests) { ' -DskipTests' })" -ForegroundColor Cyan
Invoke-Maven -Goals $installGoals

Write-Host "==> cd app && mvn spring-boot:run" -ForegroundColor Cyan
Write-Host "    Health: http://localhost:8080/actuator/health" -ForegroundColor DarkGray
Write-Host "    Press Ctrl+C to stop." -ForegroundColor DarkGray
Invoke-Maven -Goals @("spring-boot:run") -WorkingDirectory $AppRoot
