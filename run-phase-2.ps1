<#
  NotTrilix 2026 - Faza 2 (znanje / RAG): isti sustav kao Faza 1 + baza znanja (Spring AI, OpenAI embeddinzi, pgvector).
  Trazi OpenAI kljuc (openai.key u ovom folderu ili $env:OPENAI_API_KEY), zatim poziva run-phase-1.ps1 s istim parametrima.

  .\run-phase-2.ps1            build + start (ingest baze znanja pri startu)
  .\run-phase-2.ps1 -NoBuild   preskoci build
  .\run-phase-2.ps1 -Reset     obrisi bazu (svjezi seed + ponovni ingest) pa start
  .\run-phase-2.ps1 -Stop      ugasi PostgreSQL container
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (-not $Stop -and -not (Test-Path $keyFile) -and -not $env:OPENAI_API_KEY) {
    throw "Faza 2 treba OpenAI kljuc: stavi ga u $keyFile (jedna linija, gitignored) ili postavi OPENAI_API_KEY."
}

& (Join-Path $PSScriptRoot "run-phase-1.ps1") @PSBoundParameters
