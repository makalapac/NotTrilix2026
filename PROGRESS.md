# NotTrilix 2026 — napredak

_Zadnje ažurirano: **2026-09-30** · grana `phase4` · konferencija **JavaCro 2026, 11.–14. 10.**_

Predavanje: *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI*.
Ovaj repo je **novi početak (2026-09-22)**: sustav se gradi ispočetka, faza po faza, svaka na svojoj grani.
Prethodni pokušaj (`../mcp-demo`) ostaje kao referenca (deck, outline, research: glas, telefonija, Infobip, broj za demo).

## Status faza

| Faza | Grana | Što | Stanje | Detalji |
|---|---|---|---|---|
| 1 | `phase1` | Postojeći sustav (AS IS): Postgres + API + admin i customer React frontend, pravila u servisima, logovi, jedna skripta; prijava (kupac e-mail/lozinka, admin korisničko ime/lozinka, bearer token, uloge), kupcu skrivena zaliha; **wallet** (naplata kupnje s walleta uz potvrdu, nadoplata, povrat na wallet, knjiga promjena, admin uplata); **provjera bona kod pružatelja** (fiktivni `ProviderGateway`) | ✅ gotovo, e2e verificirano | [docs/PHASE1.md](docs/PHASE1.md) |
| 2 | `phase2` | Znanje: baza znanja za RAG (9 md dokumenata pisanih za chunking) + idempotentan ingest i semantička pretraga kroz **Spring AI** (OpenAI `text-embedding-3-small`, pgvector u istoj bazi, Flyway V3), admin tab „Znanje“, `run-phase-2.ps1` | ✅ gotovo, e2e verificirano | [docs/PHASE2.md](docs/PHASE2.md) |
| 3 | `phase3` | Agent „Nola“ (Spring AI `ChatClient`, `gpt-4o`): system prompt po pozivu, memorija po razgovoru, RAG advisor nad bazom znanja, 11 `@Tool` adaptera nad postojećim servisima (kupac iz prijave kroz `ToolContext`), **potvrda radnji na serveru** (`ConfirmationGate` + `confirmPendingAction`), **audit trag** (`audit_events`, Flyway V4), chat u shopu, admin tab „Audit“, `run-phase-3.ps1`, `scripts/agent_e2e.py` | ✅ gotovo, e2e verificirano | [docs/PHASE3.md](docs/PHASE3.md) |
| 4 | `phase4` | Mrežni MCP: sustav je **MCP server** (`POST /mcp`, HTTP bez sesije, 14 `@McpTool` alata), agent je **zasebna aplikacija** (`agent\`, :8090) i MCP klijent. Kupac = **token u zaglavlju** svakog MCP poziva, razgovor i turn u MCP `_meta`, **brana potvrde i audit na strani sustava**, baza znanja kroz MCP (`RetrievalAugmentationAdvisor`), `run-phase-4.ps1`, `scripts/mcp_direct.py` | ✅ gotovo, e2e verificirano | [docs/PHASE4.md](docs/PHASE4.md) |
| 5 | `phase5` | Glas (STT/TTS, ElevenLabs) | ⏳ | — |
| 6 | `phase6` | Telefonija (Infobip, poziv s mobitela) | ⏳ | — |

Za sve AI dijelove okosnica je **Spring AI** (ChatClient, EmbeddingModel, VectorStore, advisori, tool calling, MCP).

## Kako pokrenuti ono što postoji

```powershell
cd C:\Development\Projects\JCro2026\NotTrilix\NotTrilix2026
.\run-phase-4.ps1            # sustav :8080 + agent :8090 (traži openai.key). -NoBuild | -Reset (svježa baza) | -Stop
                             # run-phase-3.ps1 = isto; run-phase-1/2.ps1 = samo sustav (chat tada vraća 503)

$env:PYTHONIOENCODING = "utf-8"
python scripts\agent_e2e.py  # proba razgovora s agentom (scenariji A–H + audit); prije toga -Reset
python scripts\mcp_direct.py # MCP bez agenta: granice sustava sirovim JSON-RPC pozivima; prije toga -Reset
```

Shop http://localhost:8080/ (gumb „Chat s podrškom“) · Admin http://localhost:8080/admin/ (tab „Audit“) · Swagger http://localhost:8080/swagger-ui.html · MCP `POST http://localhost:8080/mcp` · Postgres `localhost:5433`.
Demo prijave: kupci `ana.anic@example.com` / `ana123` (wallet 115 €), marko.maric / marko123 (25 €), ivana.ivic / ivana123 (50 €) · admin `admin` / `admin123`.

## Gdje smo stali / za nastavak u novoj sesiji

- **Faza 4 zatvorena** na grani `phase4` (2026-09-30): agent je izvan sustava, a identitet kupca, potvrda radnji i audit prešli su na stranu sustava. Pouke za predavanje su u [docs/PHASE4.md](docs/PHASE4.md) (tablica na vrhu + poglavlja 4 i 10): granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica, ne kao podatak; audit na strani sustava otkrio je ponovljeni RAG dohvat u petlji alata.
- Sljedeće: grana `phase5` → glas (STT → agent → TTS). Agentov ulaz ostaje „tekst unutra, tekst van“; istraživanje glasa je u `../mcp-demo/VOICE_RESEARCH.md`.
- Otvoreno pitanje za Fazu 6 (telefon): pozivatelj nema token kupca — treba ga prepoznati drukčije i agentu dati vjerodajnicu u njegovo ime.
- Remote `origin` = `github.com/makalapac/NotTrilix2026`; grane `phase1` i `phase2` su na GitHubu, **`phase3` i `phase4` su zasad samo lokalno** (`git push -u origin phase3 phase4`).
- Baza u Dockeru sadrži i podatke od testiranja → prije vježbe `.\run-phase-4.ps1 -Reset` (svježi seed + ponovni ingest baze znanja, ~10 s).
- `openai.key` je u korijenu projekta (gitignored).
- Build dok aplikacije rade ne prolazi (Windows drži jar u `target\`): prvo ugasiti (Ctrl+C u prozoru skripte gasi oba procesa).
- Stari stack (`mcp-demo`) može istovremeno raditi: on drži 5432 i 8081–8083, novi drži 5433, 8080 (sustav) i 8090 (agent).

## Stack i konvencije

Java 25 (`C:\Program Files\Java\jdk-25.0.3`) · Spring Boot 4.1.0 · Spring AI 2.0.0 (OpenAI `gpt-4o` + `text-embedding-3-small`, pgvector, MCP server/klijent, MCP Java SDK 2.0.0) · springdoc 3.0.3 · PostgreSQL 17 (Docker, image `pgvector/pgvector:pg17`) · Flyway · React 18 + Vite 5 + TypeScript (build kroz Maven) · Logback.
Recordi za DTO/konfiguraciju, bez Lomboka, pravila u servisnom sloju, sve logirano u konzolu, jedna skripta po fazi, commit na kraju faze na grani `phaseN`, dokumentacija faze u `docs/PHASEN.md`.
