# NotTrilix 2026 — napredak

_Zadnje ažurirano: **2026-09-29** · grana `phase3` · konferencija **JavaCro 2026, 11.–14. 10.**_

Predavanje: *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI*.
Ovaj repo je **novi početak (2026-09-22)**: sustav se gradi ispočetka, faza po faza, svaka na svojoj grani.
Prethodni pokušaj (`../mcp-demo`) ostaje kao referenca (deck, outline, research: glas, telefonija, Infobip, broj za demo).

## Status faza

| Faza | Grana | Što | Stanje | Detalji |
|---|---|---|---|---|
| 1 | `phase1` | Postojeći sustav (AS IS): Postgres + API + admin i customer React frontend, pravila u servisima, logovi, jedna skripta; prijava (kupac e-mail/lozinka, admin korisničko ime/lozinka, bearer token, uloge), kupcu skrivena zaliha; **wallet** (naplata kupnje s walleta uz potvrdu, nadoplata, povrat na wallet, knjiga promjena, admin uplata); **provjera bona kod pružatelja** (fiktivni `ProviderGateway`) | ✅ gotovo, e2e verificirano | [docs/PHASE1.md](docs/PHASE1.md) |
| 2 | `phase2` | Znanje: baza znanja za RAG (9 md dokumenata pisanih za chunking) + idempotentan ingest i semantička pretraga kroz **Spring AI** (OpenAI `text-embedding-3-small`, pgvector u istoj bazi, Flyway V3), admin tab „Znanje“, `run-phase-2.ps1` | ✅ gotovo, e2e verificirano | [docs/PHASE2.md](docs/PHASE2.md) |
| 3 | `phase3` | Agent „Nola“ (Spring AI `ChatClient`, `gpt-4o`): system prompt po pozivu, memorija po razgovoru, RAG advisor nad bazom znanja, 11 `@Tool` adaptera nad postojećim servisima (kupac iz prijave kroz `ToolContext`), **potvrda radnji na serveru** (`ConfirmationGate` + `confirmPendingAction`), **audit trag** (`audit_events`, Flyway V4), chat u shopu, admin tab „Audit“, `run-phase-3.ps1`, `scripts/agent_e2e.py` | ✅ gotovo, e2e verificirano | [docs/PHASE3.md](docs/PHASE3.md) |
| 4 | `phase4` | Mrežni MCP (MCP server nad servisima + agent kao MCP klijent) | ⏳ | — |
| 5 | `phase5` | Glas (STT/TTS, ElevenLabs) | ⏳ | — |
| 6 | `phase6` | Telefonija (Infobip, poziv s mobitela) | ⏳ | — |

Za sve AI dijelove okosnica je **Spring AI** (ChatClient, EmbeddingModel, VectorStore, advisori, tool calling, MCP).

## Kako pokrenuti ono što postoji

```powershell
cd C:\Development\Projects\JCro2026\NotTrilix\NotTrilix2026
.\run-phase-3.ps1            # Faza 3 (traži openai.key). -NoBuild | -Reset (svježa baza) | -Stop
                             # run-phase-2.ps1 = isto (agent je dio aplikacije); run-phase-1.ps1 = radi i bez ključa

$env:PYTHONIOENCODING = "utf-8"
python scripts\agent_e2e.py  # proba razgovora s agentom (scenariji A–H + audit); prije toga -Reset
```

Shop http://localhost:8080/ (gumb „Chat s podrškom“) · Admin http://localhost:8080/admin/ (tab „Audit“) · Swagger http://localhost:8080/swagger-ui.html · Postgres `localhost:5433`.
Demo prijave: kupci `ana.anic@example.com` / `ana123` (wallet 115 €), marko.maric / marko123 (25 €), ivana.ivic / ivana123 (50 €) · admin `admin` / `admin123`.

## Gdje smo stali / za nastavak u novoj sesiji

- **Faza 3 zatvorena** na grani `phase3` (2026-09-29): agent odgovara iz baze znanja i djeluje kroz alate; kupnja, povrat i blokada idu u dva koraka uz potvrdu kupca; sve je u auditu. Pouke iz probe (što prompt sam ne može) su u [docs/PHASE3.md](docs/PHASE3.md), poglavlja 4 i 11 — materijal za predavanje.
- Sljedeće: grana `phase4` → mrežni MCP. Glavno pitanje: kako kontekst kupca, potvrda radnji i audit prelaze mrežnu granicu.
- Remote `origin` = `github.com/makalapac/NotTrilix2026`; grane `phase1` i `phase2` su na GitHubu, **`phase3` je zasad samo lokalno** (`git push -u origin phase3`).
- Baza u Dockeru sadrži i podatke od testiranja → prije vježbe `.\run-phase-3.ps1 -Reset` (svježi seed + ponovni ingest baze znanja, ~10 s).
- `openai.key` je u korijenu projekta (gitignored).
- Build dok aplikacija radi ne prolazi (Windows drži `target\nottrilix-2026.jar`): prvo ugasiti aplikaciju.
- Stari stack (`mcp-demo`) može istovremeno raditi: on drži 5432 i 8081–8083, novi drži 5433 i 8080.

## Stack i konvencije

Java 25 (`C:\Program Files\Java\jdk-25.0.3`) · Spring Boot 4.1.0 · Spring AI 2.0.0 (OpenAI `gpt-4o` + `text-embedding-3-small`, pgvector) · springdoc 3.0.3 · PostgreSQL 17 (Docker, image `pgvector/pgvector:pg17`) · Flyway · React 18 + Vite 5 + TypeScript (build kroz Maven) · Logback.
Recordi za DTO/konfiguraciju, bez Lomboka, pravila u servisnom sloju, sve logirano u konzolu, jedna skripta po fazi, commit na kraju faze na grani `phaseN`, dokumentacija faze u `docs/PHASEN.md`.
