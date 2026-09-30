<#
  NotTrilix 2026 - Phase 2 (knowledge / RAG): the same system as Phase 1 + knowledge base (Spring AI, OpenAI embeddings, pgvector).
  Requires an OpenAI key (openai.key in this folder or $env:OPENAI_API_KEY), then calls run-phase-1.ps1 with the same parameters.

  .\run-phase-2.ps1            build + start (knowledge base ingest at startup)
  .\run-phase-2.ps1 -NoBuild   skip the build
  .\run-phase-2.ps1 -Reset     drop the database (fresh seed + re-ingest), then start
  .\run-phase-2.ps1 -Stop      stop the PostgreSQL container
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
