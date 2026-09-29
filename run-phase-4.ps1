<#
  NotTrilix 2026 - Faza 4 (mrezni MCP): sustav kao MCP server + agent kao zasebna aplikacija (MCP klijent).

    sustav  :8080  baza, API, shop, admin, baza znanja, MCP server na /mcp (alati + brana potvrde + audit)
    agent   :8090  Spring AI ChatClient (model, memorija, RAG) - sa sustavom radi iskljucivo kroz MCP

  Trazi OpenAI kljuc (openai.key u ovom folderu ili $env:OPENAI_API_KEY), zatim poziva run-phase-1.ps1 -WithAgent.

  .\run-phase-4.ps1            build + start (sustav, pa agent; oba u ovom prozoru, Ctrl+C gasi oba)
  .\run-phase-4.ps1 -NoBuild   preskoci build
  .\run-phase-4.ps1 -Reset     obrisi bazu (svjezi seed + ponovni ingest baze znanja) pa start
  .\run-phase-4.ps1 -Stop      ugasi PostgreSQL container

  Chat s agentom: http://localhost:8080/  (prijava kao kupac -> gumb "Chat s podrskom")
  Audit trag:     http://localhost:8080/admin/  (tab Audit)
  Proba:          python scripts\agent_e2e.py   (razgovori)   |   python scripts\mcp_direct.py   (MCP bez agenta)
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (-not $Stop -and -not (Test-Path $keyFile) -and -not $env:OPENAI_API_KEY) {
    throw "Faza 4 treba OpenAI kljuc: stavi ga u $keyFile (jedna linija, gitignored) ili postavi OPENAI_API_KEY."
}

if ($Stop) {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -Stop
} else {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -WithAgent -NoBuild:$NoBuild -Reset:$Reset
}
