<#
  NotTrilix 2026 - Faza 5 (glas, analiza razgovora, samoucenje, GDPR): isti sustav + agent kao u Fazi 4, uz:
    - glasovni kanal u agentu (STT OpenAI, TTS ElevenLabs ako postoji elevenlabs.key, inace OpenAI)
    - post-analizu razgovora u sustavu (pseudonimizirani transkript -> model -> nalaz + prijedlozi za bazu znanja)
    - privolu za glas, izvoz i brisanje podataka kupca, retenciju po rasporedu

  Trazi OpenAI kljuc (openai.key ili $env:OPENAI_API_KEY). ElevenLabs kljuc (elevenlabs.key) je opcionalan.
  Zatim poziva run-phase-1.ps1 -WithAgent.

  .\run-phase-5.ps1            build + start (sustav, pa agent; oba u ovom prozoru, Ctrl+C gasi oba)
  .\run-phase-5.ps1 -NoBuild   preskoci build
  .\run-phase-5.ps1 -Reset     obrisi bazu (svjezi seed + ponovni ingest baze znanja) pa start
  .\run-phase-5.ps1 -Stop      ugasi PostgreSQL container

  Shop:   http://localhost:8080/        (Chat s podrskom: tekst ili gumb s mikrofonom; Privatnost: izvoz, brisanje)
  Admin:  http://localhost:8080/admin/  (tab Razgovori: analiza; tab Znanje: prijedlozi iz razgovora; tab Audit)
  Proba:  python scripts\phase5_e2e.py  (glas, privatnost, analiza, ucenje)
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
