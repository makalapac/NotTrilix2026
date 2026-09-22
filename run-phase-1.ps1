<#
  NotTrilix 2026 - Faza 1 (AS IS): cijeli sustav jednim potezom.

  Sto radi: Docker (pokrene Docker Desktop ako ne radi) -> PostgreSQL (docker compose)
            -> JDK 25 -> Maven build (backend + React frontendi) -> pokrene aplikaciju u ovom prozoru.

  .\run-phase-1.ps1            build + start
  .\run-phase-1.ps1 -NoBuild   preskoci build (koristi postojeci target\nottrilix-2026.jar)
  .\run-phase-1.ps1 -Reset     obrisi bazu (svjezi seed) pa start
  .\run-phase-1.ps1 -Stop      ugasi PostgreSQL container (aplikacija se gasi s Ctrl+C)
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Test-Docker {
    cmd /c "docker info >nul 2>&1"
    return ($LASTEXITCODE -eq 0)
}

if ($Stop) {
    Step "Gasim PostgreSQL (docker compose down)"
    docker compose down
    exit $LASTEXITCODE
}

# 1) Docker
Step "Docker"
if (-not (Test-Docker)) {
    $desktop = Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $desktop)) {
        throw "Docker daemon ne radi, a Docker Desktop nije pronadjen ($desktop)."
    }
    Write-Host "Docker daemon ne radi - pokrecem Docker Desktop i cekam (do 2 min)..."
    Start-Process $desktop
    $deadline = (Get-Date).AddSeconds(120)
    while (-not (Test-Docker) -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        Write-Host -NoNewline "."
    }
    Write-Host ""
    if (-not (Test-Docker)) {
        throw "Docker daemon se nije podigao na vrijeme. Pokreni Docker Desktop rucno i ponovi."
    }
}
Write-Host "Docker OK"

if ($Reset) {
    Step "Reset baze (docker compose down -v)"
    docker compose down -v
}

# 2) PostgreSQL
Step "PostgreSQL (docker compose up -d --wait)"
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw "docker compose up nije uspio." }

# 3) JDK 25
Step "JDK 25"
$jdk = Get-ChildItem (Join-Path $env:ProgramFiles "Java") -Directory -Filter "jdk-25*" -ErrorAction SilentlyContinue |
       Sort-Object Name -Descending | Select-Object -First 1
if ($jdk) {
    $env:JAVA_HOME = $jdk.FullName
    $env:Path = "$($jdk.FullName)\bin;$env:Path"
}
$javaVersion = cmd /c "java -version 2>&1" | Select-Object -First 1
Write-Host $javaVersion
if ($javaVersion -notmatch 'version "25') {
    throw "Treba JDK 25 (JAVA_HOME=$env:JAVA_HOME). Instaliraj ga u $env:ProgramFiles\Java\jdk-25*."
}

# 4) Build
$jar = Join-Path $PSScriptRoot "target\nottrilix-2026.jar"
if ($NoBuild -and (Test-Path $jar)) {
    Step "Build preskocen (-NoBuild), koristim $jar"
} else {
    Step "Maven build: backend + React frontendi (prvi put par minuta - skida Node i npm pakete)"
    $mvn = if (Test-Path (Join-Path $PSScriptRoot "mvnw.cmd")) { ".\mvnw.cmd" } else { "mvn" }
    & $mvn -ntp -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Build nije uspio." }
}

# 5) Run - u ovom prozoru; logovi idu u konzolu i u logs\nottrilix-2026.log
# UTF-8 u konzoli da hrvatski znakovi u logovima ostanu citljivi
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Step "Pokrecem NotTrilix 2026 (Ctrl+C za prekid)"
Write-Host "  Shop (kupci):   http://localhost:8080/"               -ForegroundColor Green
Write-Host "  Admin:          http://localhost:8080/admin/"          -ForegroundColor Green
Write-Host "  API (Swagger):  http://localhost:8080/swagger-ui.html" -ForegroundColor Green
Write-Host "  PostgreSQL:     localhost:5433  (db/user/pass = nottrilix)"
Write-Host ""
& java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -jar $jar
