# NotTrilix 2026 — napredak

_Zadnje ažurirano: **2026-09-22** · grana `phase1` · konferencija **JavaCro 2026, 11.–14. 10.**_

Predavanje: *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI*.
Ovaj repo je **novi početak (2026-09-22)**: sustav se gradi ispočetka, faza po faza, svaka na svojoj grani.
Prethodni pokušaj (`../mcp-demo`) ostaje kao referenca (deck, outline, research: glas, telefonija, Infobip, broj za demo).

## Status faza

| Faza | Grana | Što | Stanje | Detalji |
|---|---|---|---|---|
| 1 | `phase1` | Postojeći sustav (AS IS): Postgres + API + admin i customer React frontend, pravila u servisima, logovi, jedna skripta; prijava (kupac e-mail/lozinka, admin korisničko ime/lozinka, bearer token, uloge), kupcu skrivena zaliha; **wallet** (naplata kupnje s walleta uz potvrdu, nadoplata, povrat na wallet, knjiga promjena, admin uplata) | ✅ gotovo, e2e verificirano | [docs/PHASE1.md](docs/PHASE1.md) |
| 2 | `phase2` | Tool boundary: `@Tool` adapteri nad servisima + audit trag | ⏳ sljedeće | — |
| 3 | `phase3` | Light RAG (politike → vector store u istoj bazi) | ⏳ | — |
| 4 | `phase4` | Agent (Spring AI `ChatClient`, memorija, RAG, tool calling) | ⏳ | — |
| 5 | `phase5` | Mrežni MCP (SSE server + agent klijent) | ⏳ | — |
| 6 | `phase6` | Glas (STT/TTS, ElevenLabs) | ⏳ | — |
| 7 | `phase7` | Telefonija (Infobip, poziv s mobitela) | ⏳ | — |

## Kako pokrenuti ono što postoji

```powershell
cd C:\Development\Projects\JCro2026\NotTrilix\NotTrilix2026
.\run-phase-1.ps1            # -NoBuild | -Reset (svježa baza) | -Stop (ugasi Postgres)
```

Shop http://localhost:8080/ · Admin http://localhost:8080/admin/ · Swagger http://localhost:8080/swagger-ui.html · Postgres `localhost:5433`.
Demo prijave: kupci `ana.anic@example.com` / `ana123` (wallet 115 €), marko.maric / marko123 (25 €), ivana.ivic / ivana123 (50 €) · admin `admin` / `admin123`.

## Gdje smo stali / za nastavak u novoj sesiji

- Faza 1 je zatvorena (uklj. prijavu, skrivanje zalihe i wallet); radni direktorij čist, grana `phase1`. **Nema remotea** (push na GitHub = TODO).
- Baza u Dockeru sadrži i podatke od testiranja → prije vježbe `.\run-phase-1.ps1 -Reset`.
- Stari stack (`mcp-demo`) može istovremeno raditi: on drži 5432 i 8081–8083, novi drži 5433 i 8080.
- Sljedeći korak: grana `phase2` iz `phase1` → tool boundary + audit (plan u [docs/PHASE1.md §14](docs/PHASE1.md)).

## Stack i konvencije

Java 25 (`C:\Program Files\Java\jdk-25.0.3`) · Spring Boot 4.1.0 · springdoc 3.0.3 · PostgreSQL 17 (Docker, image `pgvector/pgvector:pg17`) · Flyway · React 18 + Vite 5 + TypeScript (build kroz Maven) · Logback.
Recordi za DTO/konfiguraciju, bez Lomboka, pravila u servisnom sloju, sve logirano u konzolu, jedna skripta po fazi, commit na kraju faze na grani `phaseN`, dokumentacija faze u `docs/PHASEN.md`.
