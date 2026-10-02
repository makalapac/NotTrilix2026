<#
  NotTrilix 2026 - Phase 6 (telephony, Infobip): the same system + agent as Phase 5, plus a phone line into the agent.
    - Infobip answers the call and bridges it to the agent's media WebSocket (PCM16 frames both ways)
    - the system recognises the caller by phone number and issues the agent a customer token for the call
    - the call is public: a cloudflared quick tunnel exposes the agent (webhooks + media WebSocket)

  Needs: openai.key, elevenlabs.key (the phone speaks PCM from ElevenLabs), infobip.key (line 1 = API key,
  line 2 = base URL). Without infobip.key only the simulator works (python scripts\phone_sim.py).
  Optional demo-caller.txt: your own mobile number -> stands in for customer 1 (Ana); never committed (.gitignore).

  .\run-phase-6.ps1            build + start (tunnel, system, agent; Ctrl+C stops all)
  .\run-phase-6.ps1 -NoBuild   skip the build
  .\run-phase-6.ps1 -Reset     drop the database (fresh seed), then start
  .\run-phase-6.ps1 -NoTunnel  no public URL (simulator only)
  .\run-phase-6.ps1 -Stop      stop the PostgreSQL container

  After start the agent logs the two webhook URLs (…/infobip/call-received and …/infobip/event) to set in the
  Infobip portal (notification profiles of the Calls configuration); the media-stream config is updated automatically.
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop,
    [switch]$NoTunnel
)

$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot "openai.key"
if ($Stop) {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -Stop
    exit $LASTEXITCODE
}
if (-not (Test-Path $keyFile) -and -not $env:OPENAI_API_KEY) {
    throw "Faza 6 treba OpenAI kljuc: stavi ga u $keyFile (jedna linija, gitignored) ili postavi OPENAI_API_KEY."
}

# Infobip (line 1 = API key, line 2 = base URL)
$infobipFile = Join-Path $PSScriptRoot "infobip.key"
if (Test-Path $infobipFile) {
    $lines = Get-Content $infobipFile | Where-Object { $_.Trim() -ne "" }
    $env:INFOBIP_API_KEY = $lines[0].Trim()
    $env:INFOBIP_BASE_URL = $lines[1].Trim()
    Write-Host "Infobip kljuc: ucitan iz infobip.key"
} else {
    Write-Host "Infobip kljuc: nije pronadjen - telefon radi samo kroz simulator (scripts\phone_sim.py)" -ForegroundColor Yellow
}

# Demo caller: the presenter's own number maps to customer 1 (never in the repo)
$callerFile = Join-Path $PSScriptRoot "demo-caller.txt"
if (Test-Path $callerFile) {
    $env:NOTTRILIX_DEMO_CALLER = (Get-Content $callerFile -Raw).Trim()
    Write-Host "Demo pozivatelj: ucitan iz demo-caller.txt (broj se ne ispisuje)"
}

# Public URL for Infobip: cloudflared quick tunnel to the agent (:8090)
$tunnel = $null
if (-not $NoTunnel -and $env:INFOBIP_API_KEY) {
    $cloudflared = Join-Path ${env:ProgramFiles(x86)} "cloudflared\cloudflared.exe"
    if (-not (Test-Path $cloudflared)) { $cloudflared = "cloudflared" }
    $tunnelLog = Join-Path $PSScriptRoot "logs\cloudflared.log"
    New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot "logs") | Out-Null
    if (Test-Path $tunnelLog) { Remove-Item $tunnelLog -Force }
    Write-Host ""
    Write-Host "==> Tunel (cloudflared quick tunnel -> http://localhost:8090)" -ForegroundColor Cyan
    $tunnel = Start-Process -FilePath $cloudflared -ArgumentList @("tunnel", "--url", "http://localhost:8090", "--no-autoupdate") `
        -NoNewWindow -PassThru -RedirectStandardError $tunnelLog -RedirectStandardOutput (Join-Path $PSScriptRoot "logs\cloudflared.out")
    $deadline = (Get-Date).AddSeconds(60)
    $publicUrl = $null
    while (-not $publicUrl -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 2
        if (Test-Path $tunnelLog) {
            $match = Select-String -Path $tunnelLog -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' | Select-Object -First 1
            if ($match) { $publicUrl = $match.Matches[0].Value }
        }
    }
    if (-not $publicUrl) {
        if ($tunnel -and -not $tunnel.HasExited) { Stop-Process -Id $tunnel.Id -Force }
        throw "Tunel se nije podigao u 60 s (vidi logs\cloudflared.log)."
    }
    $env:NOTTRILIX_PUBLIC_URL = $publicUrl
    Write-Host "Javni URL agenta: $publicUrl" -ForegroundColor Green
    Write-Host "Webhookovi za Infobip portal: $publicUrl/infobip/call-received  i  $publicUrl/infobip/event" -ForegroundColor Green
    Write-Host "Media WebSocket (postavlja se sam pri startu agenta): $($publicUrl -replace '^https://','wss://')/infobip/media"
}

try {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -WithAgent -NoBuild:$NoBuild -Reset:$Reset
} finally {
    if ($tunnel -and -not $tunnel.HasExited) {
        Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue
        Write-Host "Tunel ugasen."
    }
}
