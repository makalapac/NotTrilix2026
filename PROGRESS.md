# NotTrilix 2026 — napredak

_Zadnje ažurirano: **2026-10-02** · grana `phase6` · konferencija **JavaCro 2026, 11.–14. 10.**_

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
| 5 | `phase5` | **Glas** (push-to-talk u chatu; STT OpenAI `gpt-4o-mini-transcribe`, TTS ElevenLabs ili OpenAI, sve kroz Spring AI `TranscriptionModel`/`TextToSpeechModel`; snimka se ne pohranjuje), **post-analiza razgovora** (audit trag → transkript → `Pseudonymizer` → `gpt-4o` structured output → nalaz: kategorija, ishod, raspoloženje, ocjena, rupe u znanju; raspored za utihnule razgovore), **samoučenje s odobrenjem** (prijedlozi za bazu znanja → admin odobri/ispravi → ingest `nauceno-N` → RAG), **GDPR** (privola za glas s verzijom teksta, izvoz JSON, brisanje razgovora, retencija 30/365 dana, logovi bez e-maila, KB dokument o privatnosti), Flyway V5, admin tab „Razgovori“ + prijedlozi u „Znanje“, shop „Privatnost“, `run-phase-5.ps1`, `scripts/phase5_e2e.py` | ✅ gotovo, e2e verificirano | [docs/PHASE5.md](docs/PHASE5.md) · [docs/GDPR.md](docs/GDPR.md) |
| 6 | `phase6` | **Telefon (Infobip)**: webhookovi `CALL_RECEIVED` / `CALL_ESTABLISHED` → javljanje i most na WebSocket agenta (20 ms PCM16 @ 16 kHz), VAD, STT (Spring AI), isti agent, TTS ElevenLabs `pcm_16000`, upad pozivatelja, „Samo trenutak.“; **identitet pozivatelja**: sustav po broju izda token kupca za poziv (`/api/phone/calls`, audit `PHONE_CALL`), turnovi i kraj poziva u audit; simulator `scripts/phone_sim.py`; `run-phase-6.ps1` s cloudflared tunelom | 🔧 simulator verificiran, živi poziv čeka broj | [docs/PHASE6.md](docs/PHASE6.md) **Živi poziv s mobitela prošao 2026-10-09**; popravci iz živog testa u `docs/PHASE6.md` §9. |

Za sve AI dijelove okosnica je **Spring AI** (ChatClient, EmbeddingModel, VectorStore, advisori, tool calling, MCP, TranscriptionModel, TextToSpeechModel, structured output).

## Kako pokrenuti ono što postoji

```powershell
cd C:\Development\Projects\JCro2026\NotTrilix\NotTrilix2026
.\run-phase-6.ps1            # Faza 6: tunel + sustav :8080 + agent :8090 (openai.key, elevenlabs.key, infobip.key); -NoTunnel = samo simulator
.\run-phase-5.ps1            # sustav :8080 + agent :8090 (traži openai.key; elevenlabs.key opcionalan). -NoBuild | -Reset (svježa baza) | -Stop
                             # run-phase-3/4.ps1 = isto na ovoj grani; run-phase-1/2.ps1 = samo sustav (chat tada vraća 503)

$env:PYTHONIOENCODING = "utf-8"
python scripts\phase5_e2e.py # Faza 5: glas (probna snimka), privatnost, analiza, učenje (ili pojedinačno: voice | privacy | analysis | learning)
python scripts\phone_sim.py  # Faza 6: simulirani telefonski poziv (glumi Infobip: webhookovi + WebSocket + PCM)
python scripts\agent_e2e.py  # proba razgovora s agentom (scenariji A–H + audit); prije toga -Reset
python scripts\mcp_direct.py # MCP bez agenta: granice sustava sirovim JSON-RPC pozivima; prije toga -Reset
```

Shop http://localhost:8080/ (gumb „Chat s podrškom“ s mikrofonom, gumb „Privatnost“) · Admin http://localhost:8080/admin/ (tabovi „Razgovori“, „Znanje“, „Audit“) · Swagger http://localhost:8080/swagger-ui.html · MCP `POST http://localhost:8080/mcp` · Postgres `localhost:5433`.
Demo prijave: kupci `ana.anic@example.com` / `ana123` (wallet 115 €), marko.maric / marko123 (25 €), ivana.ivic / ivana123 (50 €) · admin `admin` / `admin123`.

## Gdje smo stali / za nastavak u novoj sesiji

- **Faza 5 zatvorena** na grani `phase5` (2026-09-30): glas je kanal ispred istog agenta, audit trag je postao transkript za analizu i izvoz, samoučenje ide preko odobrenja admina, prava kupca su kod. Pouke za predavanje: [docs/PHASE5.md](docs/PHASE5.md) (tablica na vrhu + poglavlje 11), obrada osobnih podataka: [docs/GDPR.md](docs/GDPR.md).
- Faza 4 (2026-09-30): pouke u [docs/PHASE4.md](docs/PHASE4.md) (granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica; audit na strani sustava otkrio ponovljeni RAG dohvat).
- **Nacrt prezentacije (40 min, blok za Fazu 6 rezerviran):** [docs/PREZENTACIJA.md](docs/PREZENTACIJA.md) — raspodjela vremena, demo beatovi, popis slajdova, rezerve, što još treba do konferencije.
- Faza 6 uživo (2026-10-09): HR broj (Zagreb), četiri poziva, kupnja uz potvrdu telefonom. Popravci iz živog testa (dialog umjesto `answer`, izvedeni ID razgovora, pacer bez blokiranja + grijanje fraza, STT `gpt-4o-transcribe` za telefon, prag upada, grijanje embeddinga i modela, fraze u rotaciji (prag 2,5 s), ZGC + stražar pacera, `run-phase-6.ps1 -PublicUrl`): `docs/PHASE6.md` §9. Ostaje: snimka poziva kao rezervni video, nadoplata računa.
- Remote `origin` = `github.com/makalapac/NotTrilix2026`; sve grane `phase1`–`phase5` su na GitHubu (`phase5` = zadnje stanje: glas v2, serijski brojevi bez crtica).
- Baza u Dockeru sadrži i podatke od testiranja → prije vježbe `.\run-phase-5.ps1 -Reset` (svježi seed + ponovni ingest baze znanja, ~10 s; odobreni prijedlozi iz `kb_suggestions` nestaju s bazom).
- `openai.key` i `elevenlabs.key` su u korijenu projekta (gitignored); bez `elevenlabs.key` agent govori kroz OpenAI TTS.
- **Glas v2 (2026-10-02):** ElevenLabs Starter plan je aktivan (library glasovi preko API-ja traže plaćeni plan); Nola = „Maja“ + `eleven_v4_turbo` + `language_code: hr` (`NOTTRILIX_VOICE_ID`, `NOTTRILIX_TTS_MODEL`), iznosi riječima (`SpeechText`), streaming odgovora (`/api/agent/voice/stream`, MediaSource u pregledniku), „Samo trenutak.“, snimanje s detekcijom tišine, prekid klikom. Prvi zvuk 1,6–2,7 s od slanja snimke (prije 4,7–8,3 s). Serijski brojevi se traže u sažetom obliku (bez crtica/razmaka), jer ih kupac tako izgovori. Opcionalno dalje: rečenica po rečenica u TTS dok model streama. Zamke: `StreamingResponseBody` ne flusha (8 KB), ASYNC dispatch bez bearer autentikacije → sinkrono pisanje s `flushBuffer()`.
- **Opseg agenta (2026-10-03):** system prompt dobio odjeljak OPSEG: Nola nije opći asistent (ne piše kod, ne prevodi, ne piše tekstove, ne rješava opće zadatke); takav zahtjev odbije jednom rečenicom i vrati razgovor na bonove/wallet, i kad kupac inzistira ili traži „neslužbeno“. Provjereno slijedom iz stvarnog razgovora (wallet u kune → python kod) i kontrolnim razgovorom.
- **Model (2026-10-03):** `gpt-4o` → `gpt-5.6-terra` u agentu (`reasoning-effort: none`, `temperature: 0.2`) i u analizi sustava (`reasoning-effort: low`); `NOTTRILIX_CHAT_MODEL` mijenja oba bez builda. Izmjereno na stvarnom promptu i alatima: vidi `docs/MODEL.md`.
- **Rezervni demo videi (2026-10-03):** `demo-video/out/` (faze 1–5 + `00-sve-faze.mp4`), skripte u `demo-video/` (Playwright + ffmpeg; `README.md` za ponovno snimanje nakon `-Reset`). Snimke nisu u gitu.
- Analiza utihnulih razgovora kreće 2 min nakon zadnje poruke (raspored svake minute); u demou je brže kliknuti „Analiziraj“ u tabu Razgovori. Retencija radi pri startu (u logu `PRIVACY retention`).
- Docker Desktop 4.36 zna ostati na „engine stopped“ zbog zaostale datoteke `%LOCALAPPDATA%\Docker\run\userAnalyticsOtlpHttp.sock` (ne da se obrisati, „cannot be accessed by the system“): ugasiti Docker Desktop, preimenovati mapu `run` (npr. u `run.stale`) i pokrenuti ga ponovno.
- Build dok aplikacije rade ne prolazi (Windows drži jar u `target\`): prvo ugasiti (Ctrl+C u prozoru skripte gasi oba procesa).
- Stari stack (`mcp-demo`) može istovremeno raditi: on drži 5432 i 8081–8083, novi drži 5433, 8080 (sustav) i 8090 (agent).

## Stack i konvencije

Java 25 (`C:\Program Files\Java\jdk-25.0.3`) · Spring Boot 4.1.0 · Spring AI 2.0.0 (OpenAI `gpt-5.6-terra` + `text-embedding-3-small` + `gpt-4o-mini-transcribe` + `gpt-4o-mini-tts`, ElevenLabs TTS, pgvector, MCP server/klijent, MCP Java SDK 2.0.0) · springdoc 3.0.3 · PostgreSQL 17 (Docker, image `pgvector/pgvector:pg17`) · Flyway · React 18 + Vite 5 + TypeScript (build kroz Maven) · Logback.
Recordi za DTO/konfiguraciju, bez Lomboka, pravila u servisnom sloju, sve logirano u konzolu, jedna skripta po fazi, commit na kraju faze na grani `phaseN`, dokumentacija faze u `docs/PHASEN.md`.
