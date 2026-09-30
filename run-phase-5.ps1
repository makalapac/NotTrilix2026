<#
  NotTrilix 2026 - Phase 5 (voice, conversation analysis, self-learning, GDPR): the same system + agent as in Phase 4, plus:
    - a voice channel in the agent (STT OpenAI, TTS ElevenLabs if elevenlabs.key exists, otherwise OpenAI)
    - conversation post-analysis in the system (pseudonymized transcript -> model -> findings + knowledge base suggestions)
    - voice consent, export and erasure of customer data, scheduled retention

  Requires an OpenAI key (openai.key or $env:OPENAI_API_KEY). The ElevenLabs key (elevenlabs.key) is optional.
  Then calls run-phase-1.ps1 -WithAgent.

  .\run-phase-5.ps1            build + start (system, then agent; both in this window, Ctrl+C stops both)
  .\run-phase-5.ps1 -NoBuild   skip the build
  .\run-phase-5.ps1 -Reset     drop the database (fresh seed + knowledge base re-ingest), then start
  .\run-phase-5.ps1 -Stop      stop the PostgreSQL container

  Shop:   http://localhost:8080/        (Chat s podrskom: text or the microphone button; Privatnost: export, erasure)
  Admin:  http://localhost:8080/admin/  (Razgovori tab: analysis; Znanje tab: suggestions from conversations; Audit tab)
  Try it: python scripts\phase5_e2e.py  (voice, privacy, analysis, learning)
#>
param(
    [switch]$NoBuild,
    [switch]$Reset,
    [switch]$Stop
)

$ErrorActionPreference = 'Stop'
$keyFile = Join-Path $PSScriptRoot "openai.key"
if (-not $Stop -and -not (Test-Path $keyFile) -and -not $env:OPENAI_API_KEY) {
    throw "Faza 5 treba OpenAI kljuc: stavi ga u $keyFile (jedna linija, gitignored) ili postavi OPENAI_API_KEY."
}

if ($Stop) {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -Stop
} else {
    & (Join-Path $PSScriptRoot "run-phase-1.ps1") -WithAgent -NoBuild:$NoBuild -Reset:$Reset
}
