# NotTrilix 2026 — napredak

_Zadnje ažurirano: **2026-09-22** · grana `phase1` · konferencija **JavaCro 2026, 11.–14. 10.**_

Predavanje: *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI*.
Ovaj repo je **novi početak (2026-09-22)**: sustav se gradi ispočetka, faza po faza, svaka na svojoj grani.
Prethodni pokušaj (`../mcp-demo`) ostaje kao referenca (deck, outline, research: glas, telefonija, Infobip, broj za demo).

## Status faza

| Faza | Grana | Što | Stanje | Detalji |
|---|---|---|---|---|
| 1 | `phase1` | Postojeći sustav (AS IS): Postgres + API + admin i customer React frontend, pravila u servisima, logovi, jedna skripta; prijava (kupac e-mail/lozinka, admin korisničko ime/lozinka, bearer token, uloge), kupcu skrivena zaliha; **wallet** (naplata kupnje s walleta uz potvrdu, nadoplata, povrat na wallet, knjiga promjena, admin uplata); **provjera bona kod pružatelja** (fiktivni `ProviderGateway`) | ✅ gotovo, e2e verificirano | [docs/PHASE1.md](docs/PHASE1.md) |
| 2 | `phase2` | Znanje: baza znanja za RAG (9 md dokumenata pisanih za chunking) + idempotentan ingest i semantička pretraga kroz **Spring AI** (OpenAI `text-embedding-3-small`, pgvector u istoj bazi, Flyway V3), admin tab „Znanje“, `run-phase-2.ps1` | ✅ gotovo, e2e verificirano | [docs/PHASE2.md](docs/PHASE2.md) |
| 3 | `phase3` | Agent (Spring AI `ChatClient`): memorija po razgovoru, RAG advisor, `@Tool` adapteri nad servisima + audit trag | ⏳ | — |
| 4 | `phase4` | Mrežni MCP (SSE server + agent klijent) | ⏳ | — |
| 5 | `phase5` | Glas (STT/TTS, ElevenLabs) | ⏳ | — |
| 6 | `phase6` | Telefonija (Infobip, poziv s mobitela) | ⏳ | — |

Za sve AI dijelove okosnica je **Spring AI** (ChatClient, EmbeddingModel, VectorStore, advisori, tool calling, MCP).

## Kako pokrenuti ono što postoji

```powershell
cd C:\Development\Projects\JCro2026\NotTrilix\NotTrilix2026
.\run-phase-2.ps1            # Faza 2 (traži openai.key); run-phase-1.ps1 = bez baze znanja. -NoBuild | -Reset (svježa baza) | -Stop
```

Shop http://localhost:8080/ · Admin http://localhost:8080/admin/ · Swagger http://localhost:8080/swagger-ui.html · Postgres `localhost:5433`.
Demo prijave: kupci `ana.anic@example.com` / `ana123` (wallet 115 €), marko.maric / marko123 (25 €), ivana.ivic / ivana123 (50 €) · admin `admin` / `admin123`.

## Gdje smo stali / za nastavak u novoj sesiji

- Faza 1 je zatvorena (uklj. prijavu, skrivanje zalihe i wallet); radni direktorij čist, grana `phase1`. **Nema remotea** (push na GitHub = TODO).
- Baza u Dockeru sadrži i podatke od testiranja → prije vježbe `.\run-phase-1.ps1 -Reset`.
- Stari stack (`mcp-demo`) može istovremeno raditi: on drži 5432 i 8081–8083, novi drži 5433 i 8080.
- **Faza 2 zatvorena** na grani `phase2` (baza znanja + ingest + pretraga, vidi [docs/PHASE2.md](docs/PHASE2.md)); `openai.key` je u korijenu projekta (gitignored). Sljedeće: grana `phase3` → agent (Spring AI `ChatClient` + RAG advisor nad istim vector storeom + `@Tool` adapteri nad servisima + audit).

## Stack i konvencije

Java 25 (`C:\Program Files\Java\jdk-25.0.3`) · Spring Boot 4.1.0 · springdoc 3.0.3 · PostgreSQL 17 (Docker, image `pgvector/pgvector:pg17`) · Flyway · React 18 + Vite 5 + TypeScript (build kroz Maven) · Logback.
Recordi za DTO/konfiguraciju, bez Lomboka, pravila u servisnom sloju, sve logirano u konzolu, jedna skripta po fazi, commit na kraju faze na grani `phaseN`, dokumentacija faze u `docs/PHASEN.md`.
