<#
  NotTrilix 2026 - Faza 3 (agent): isti sustav + baza znanja + agent podrske (Spring AI ChatClient:
  system prompt, memorija razgovora, RAG, alati nad servisima, audit trag).
  Trazi OpenAI kljuc (openai.key u ovom folderu ili $env:OPENAI_API_KEY), zatim poziva run-phase-1.ps1 s istim parametrima.

  .\run-phase-3.ps1            build + start
  .\run-phase-3.ps1 -NoBuild   preskoci build
  .\run-phase-3.ps1 -Reset     obrisi bazu (svjezi seed + ponovni ingest baze znanja) pa start
  .\run-phase-3.ps1 -Stop      ugasi PostgreSQL container

  Chat s agentom: http://localhost:8080/  (prijava kao kupac -> gumb "Chat s podrskom")
  Audit trag:     http://localhost:8080/admin/  (tab Audit)
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (-not $Stop -and -not (Test-Path $keyFile) -and -not $env:OPENAI_API_KEY) {
    throw "Faza 3 treba OpenAI kljuc: stavi ga u $keyFile (jedna linija, gitignored) ili postavi OPENAI_API_KEY."
}

& (Join-Path $PSScriptRoot "run-phase-1.ps1") @PSBoundParameters
