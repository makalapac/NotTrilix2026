<#
  NotTrilix 2026 - Faza 1 (AS IS): cijeli sustav jednim potezom.

  Sto radi: Docker (pokrene Docker Desktop ako ne radi) -> PostgreSQL (docker compose)
            -> JDK 25 -> Maven build (backend + React frontendi) -> pokrene aplikaciju u ovom prozoru.

  .\run-phase-1.ps1            build + start
  .\run-phase-1.ps1 -NoBuild   preskoci build (koristi postojeci target\nottrilix-2026.jar)
  .\run-phase-1.ps1 -Reset     obrisi bazu (svjezi seed) pa start
  .\run-phase-1.ps1 -Stop      ugasi PostgreSQL container (aplikacija se gasi s Ctrl+C)

  -WithAgent (koriste ga run-phase-3.ps1 i run-phase-4.ps1): uz sustav builda i pokrece aplikaciju agenta
  (agent\, port 8090). Oba procesa pisu u ovaj prozor; agentovi retci imaju oznaku [agent ]. Ctrl+C gasi oba.
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop,
    [switch]$WithAgent
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

function Test-Port([int]$port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $client.Connect("localhost", $port)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# Pokrene java proces u ovom prozoru (dijeli konzolu) i vrati ga; ceka da port proradi.
function Start-App([string]$name, [string]$jarPath, [int]$port, [int]$timeoutSeconds) {
    $process = Start-Process -FilePath "java" -NoNewWindow -PassThru `
        -ArgumentList @("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-jar", "`"$jarPath`"")
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while (-not (Test-Port $port)) {
        if ($process.HasExited) { throw "$name se ugasio pri pokretanju (vidi log iznad)." }
        if ((Get-Date) -gt $deadline) { throw "$name se nije pokrenuo u $timeoutSeconds s (port $port)." }
        Start-Sleep -Milliseconds 500
    }
    return $process
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

# 3b) OpenAI kljuc (Faza 2+: embeddinzi za bazu znanja, Faza 3+: model agenta). Datoteka openai.key je gitignored.
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (Test-Path $keyFile) {
    $env:OPENAI_API_KEY = (Get-Content $keyFile -Raw).Trim()
    Write-Host "OpenAI kljuc: ucitan iz openai.key"
} elseif ($env:OPENAI_API_KEY) {
    Write-Host "OpenAI kljuc: iz okoline (OPENAI_API_KEY)"
} else {
    Write-Host "OpenAI kljuc: nije pronadjen - baza znanja (RAG) i agent nece biti dostupni" -ForegroundColor Yellow
}

# 3c) ElevenLabs kljuc (Faza 5: sinteza govora). Bez njega agent govori kroz OpenAI TTS.
$elevenFile = Join-Path $PSScriptRoot "elevenlabs.key"
if (Test-Path $elevenFile) {
    $env:ELEVENLABS_API_KEY = (Get-Content $elevenFile -Raw).Trim()
    $env:NOTTRILIX_TTS = "elevenlabs"
    Write-Host "ElevenLabs kljuc: ucitan iz elevenlabs.key (govor: ElevenLabs)"
} elseif ($env:ELEVENLABS_API_KEY) {
    $env:NOTTRILIX_TTS = "elevenlabs"
    Write-Host "ElevenLabs kljuc: iz okoline (govor: ElevenLabs)"
} else {
    $env:NOTTRILIX_TTS = "openai"
    Write-Host "ElevenLabs kljuc: nije pronadjen - govor kroz OpenAI TTS"
}

# 4) Build
$mvn = if (Test-Path (Join-Path $PSScriptRoot "mvnw.cmd")) { ".\mvnw.cmd" } else { "mvn" }
$jar = Join-Path $PSScriptRoot "target\nottrilix-2026.jar"
if ($NoBuild -and (Test-Path $jar)) {
    Step "Build sustava preskocen (-NoBuild), koristim $jar"
} else {
    Step "Maven build sustava: backend + React frontendi (prvi put par minuta - skida Node i npm pakete)"
    & $mvn -ntp -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Build sustava nije uspio." }
}
$agentJar = Join-Path $PSScriptRoot "agent\target\nottrilix-agent.jar"
if ($WithAgent) {
    if ($NoBuild -and (Test-Path $agentJar)) {
        Step "Build agenta preskocen (-NoBuild), koristim $agentJar"
    } else {
        Step "Maven build agenta (agent\)"
        & $mvn -ntp -DskipTests -f agent\pom.xml package
        if ($LASTEXITCODE -ne 0) { throw "Build agenta nije uspio." }
    }
}

# 5) Run - u ovom prozoru; logovi idu u konzolu i u logs\
# UTF-8 u konzoli da hrvatski znakovi u logovima ostanu citljivi
chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Step "Pokrecem NotTrilix 2026 (Ctrl+C za prekid)"
Write-Host "  Shop (kupci):   http://localhost:8080/"               -ForegroundColor Green
Write-Host "  Admin:          http://localhost:8080/admin/"          -ForegroundColor Green
Write-Host "  API (Swagger):  http://localhost:8080/swagger-ui.html" -ForegroundColor Green
Write-Host "  PostgreSQL:     localhost:5433  (db/user/pass = nottrilix)"
if ($WithAgent) {
    Write-Host "  MCP server:     http://localhost:8080/mcp   (sustav; alati za agenta)" -ForegroundColor Green
    Write-Host "  Agent:          http://localhost:8090/chat  (zasebna aplikacija; retci s oznakom [agent ])" -ForegroundColor Green
}
Write-Host ""

if (-not $WithAgent) {
    & java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -jar $jar
    exit $LASTEXITCODE
}

# Dva procesa u istom prozoru: prvo sustav (MCP server), pa agent (MCP klijent se spaja pri startu).
$system = $null
$agent = $null
try {
    $system = Start-App "Sustav" $jar 8080 120
    $agent = Start-App "Agent" $agentJar 8090 60
    while (-not $system.HasExited -and -not $agent.HasExited) {
        Start-Sleep -Milliseconds 500
    }
    if ($system.HasExited) { Write-Host "Sustav se ugasio - gasim i agenta." -ForegroundColor Yellow }
    if ($agent.HasExited) { Write-Host "Agent se ugasio - gasim i sustav." -ForegroundColor Yellow }
} finally {
    foreach ($process in @($agent, $system)) {
        if ($process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
}
