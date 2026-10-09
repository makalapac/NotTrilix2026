<#
  NotTrilix 2026 - Phase 1 (AS IS): the whole system in one go.

  What it does: Docker (starts Docker Desktop if it is not running) -> PostgreSQL (docker compose)
                -> JDK 25 -> Maven build (backend + React frontends) -> starts the application in this window.

  .\run-phase-1.ps1            build + start
  .\run-phase-1.ps1 -NoBuild   skip the build (uses the existing target\nottrilix-2026.jar)
  .\run-phase-1.ps1 -Reset     drop the database (fresh seed), then start
  .\run-phase-1.ps1 -Stop      stop the PostgreSQL container (the application stops with Ctrl+C)

  -WithAgent (used by run-phase-3.ps1 and run-phase-4.ps1): also builds and starts the agent application
  (agent\, port 8090) next to the system. Both processes write to this window; agent lines carry the [agent ] tag. Ctrl+C stops both.
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

# Starts a java process in this window (shares the console) and returns it; waits for the port to come up.
function Start-App([string]$name, [string]$jarPath, [int]$port, [int]$timeoutSeconds) {
    # ZGC: sub-millisecond pauses. The phone media leg dies when no frame leaves for ~0.3 s, so a GC pause of that
    # size in the agent costs a call; the GC log (logs\gc-<app>.log) shows whether a pause ever came close.
    $gcLog = Join-Path $PSScriptRoot ("logs\gc-" + [IO.Path]::GetFileNameWithoutExtension($jarPath) + ".log")
    New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot "logs") | Out-Null
    $process = Start-Process -FilePath "java" -NoNewWindow -PassThru `
        -ArgumentList @("-XX:+UseZGC", "-Xlog:gc*:file=`"$gcLog`":time,uptime:filecount=2,filesize=5m",
                        "--enable-native-access=ALL-UNNAMED",
                        "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-jar", "`"$jarPath`"")
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

# 3b) OpenAI key (Phase 2+: embeddings for the knowledge base, Phase 3+: the agent's model). The openai.key file is gitignored.
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (Test-Path $keyFile) {
    $env:OPENAI_API_KEY = (Get-Content $keyFile -Raw).Trim()
    Write-Host "OpenAI kljuc: ucitan iz openai.key"
} elseif ($env:OPENAI_API_KEY) {
    Write-Host "OpenAI kljuc: iz okoline (OPENAI_API_KEY)"
} else {
    Write-Host "OpenAI kljuc: nije pronadjen - baza znanja (RAG) i agent nece biti dostupni" -ForegroundColor Yellow
}

# 3c) ElevenLabs key (Phase 5: speech synthesis). Without it the agent speaks through OpenAI TTS.
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

# 5) Run - in this window; logs go to the console and to logs\
# UTF-8 in the console so Croatian characters in the logs stay readable
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

# Two processes in the same window: first the system (MCP server), then the agent (the MCP client connects at startup).
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
