<#
  NotTrilix 2026 - Phase 4 (networked MCP): the system as an MCP server + the agent as a separate application (MCP client).

    system  :8080  database, API, shop, admin, knowledge base, MCP server at /mcp (tools + confirmation gate + audit)
    agent   :8090  Spring AI ChatClient (model, memory, RAG) - talks to the system exclusively through MCP

  Requires an OpenAI key (openai.key in this folder or $env:OPENAI_API_KEY), then calls run-phase-1.ps1 -WithAgent.

  .\run-phase-4.ps1            build + start (system, then agent; both in this window, Ctrl+C stops both)
  .\run-phase-4.ps1 -NoBuild   skip the build
  .\run-phase-4.ps1 -Reset     drop the database (fresh seed + knowledge base re-ingest), then start
  .\run-phase-4.ps1 -Stop      stop the PostgreSQL container

  Chat with the agent: http://localhost:8080/  (log in as a customer -> "Chat s podrskom" button)
  Audit trail:         http://localhost:8080/admin/  (Audit tab)
  Try it:              python scripts\agent_e2e.py   (conversations)   |   python scripts\mcp_direct.py   (MCP without the agent)
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
