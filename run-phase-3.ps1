<#
  NotTrilix 2026 - Phase 3 (agent): the same system + knowledge base + support agent (Spring AI ChatClient:
  system prompt, conversation memory, RAG, tools over the services, audit trail).

  From Phase 4 the agent is a separate application, so on this branch this script starts the same as run-phase-4.ps1
  (system + agent). The agent in the same process as the system is on the phase3 branch.

  .\run-phase-3.ps1            build + start
  .\run-phase-3.ps1 -NoBuild   skip the build
  .\run-phase-3.ps1 -Reset     drop the database (fresh seed + knowledge base re-ingest), then start
  .\run-phase-3.ps1 -Stop      stop the PostgreSQL container
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

& (Join-Path $PSScriptRoot "run-phase-4.ps1") @PSBoundParameters
