<#
  NotTrilix 2026 - Faza 3 (agent): isti sustav + baza znanja + agent podrske (Spring AI ChatClient:
  system prompt, memorija razgovora, RAG, alati nad servisima, audit trag).

  Od Faze 4 agent je zasebna aplikacija, pa ova skripta na ovoj grani pokrece isto sto i run-phase-4.ps1
  (sustav + agent). Agent u istom procesu sa sustavom je na grani phase3.

  .\run-phase-3.ps1            build + start
  .\run-phase-3.ps1 -NoBuild   preskoci build
  .\run-phase-3.ps1 -Reset     obrisi bazu (svjezi seed + ponovni ingest baze znanja) pa start
  .\run-phase-3.ps1 -Stop      ugasi PostgreSQL container
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

& (Join-Path $PSScriptRoot "run-phase-4.ps1") @PSBoundParameters
