# NotTrilix 2026

Pojednostavljeni „produkcijski“ sustav za prodaju bonova: **jedna Spring Boot aplikacija** (API + dva React frontenda) i **PostgreSQL** baza,
a oko njega agent podrške kao **druga Spring Boot aplikacija**.
Ovo je polazna točka za predavanje *From REST API to Voice Agent* (JavaCro 2026) — sve kasnije faze grade oko ovog sustava:
Faza 1 = postojeći sustav (AS IS) · Faza 2 = baza znanja (RAG) · Faza 3 = agent podrške · Faza 4 = mrežni MCP (sustav je MCP server, agent zasebna aplikacija) ·
Faza 5 = glas, post-analiza razgovora sa samoučenjem i GDPR. AI dijelovi su građeni na **Spring AI**.

Status faza i „gdje smo stali“: [PROGRESS.md](PROGRESS.md) · opis po fazama (odluke, verifikacija, zamke): [docs/PHASE1.md](docs/PHASE1.md) · [docs/PHASE2.md](docs/PHASE2.md) · [docs/PHASE3.md](docs/PHASE3.md) · [docs/PHASE4.md](docs/PHASE4.md) · [docs/PHASE5.md](docs/PHASE5.md) · obrada osobnih podataka: [docs/GDPR.md](docs/GDPR.md).

## Što sustav radi

- **Proizvodi:** Telekom bonovi (A1, HT) i Gaming bonovi (Steam, PlayStation, Xbox), denominacije u EUR.
- **Zaliha:** unaprijed generirani bonovi (serijski broj + aktivacijski kod), 8 po proizvodu.
- **Wallet:** svaki kupac ima wallet; kupnja se **naplaćuje s walleta** (uz potvrdu u dijalogu), povrat ide natrag na wallet, kupac može nadoplatiti wallet (simulirano plaćanje karticom), sve promjene su u knjizi promjena.
- **Kupci (shop, `/`):** prijava e-mailom i lozinkom, katalog (dostupno / rasprodano — kupac ne vidi stanje zalihe), kupnja bona s walleta (odmah dobiju kod), „Moj wallet“ (stanje + povijest), pregled svojih kupnji.
- **Admin (`/admin/`):** prijava korisničkim imenom i lozinkom; pregled svih transakcija (filtri), kupci s walletima (uplata podrške, povijest), stanje zaliha, podrška — povrat novca na wallet, blokada bona, prijave kupaca.
- **Provjera bona kod pružatelja:** fiktivni upit prema A1/HT/Steam/PlayStation/Xbox („je li bon iskorišten?“) — support za bilo koji bon, kupac za vlastite; odgovor sadrži status kod pružatelja, datum iskorištenja, referencu i simuliranu latenciju.
- **Baza znanja (Faza 2, Spring AI):** 9 markdown dokumenata podrške (`src/main/resources/kb/`) → pri startu ingest u pgvector (OpenAI embeddinzi) → semantička pretraga za support (admin tab „Znanje“, `GET /api/admin/kb/search?q=…`). Detalji: [docs/PHASE2.md](docs/PHASE2.md).
- **Agent podrške „Nola“ (Faza 3, Spring AI `ChatClient`):** u shopu gumb „Chat s podrškom“. Agent odgovara iz baze znanja i djeluje kroz alate nad istim servisima (kupnje, wallet, provjera bona, kupnja, povrat, blokada, prijava) — uvijek u ime prijavljenog kupca i uz ista poslovna pravila. Kupnja, povrat i blokada traže **potvrdu kupca** (stanje drži server). Svaki poziv alata, dohvat iz baze znanja i turn razgovora je u **audit tragu** (admin tab „Audit“). Detalji: [docs/PHASE3.md](docs/PHASE3.md).
- **MCP server (Faza 4):** isti alati dostupni su preko mreže na `POST /mcp` (HTTP bez sesije). Agent je od ove faze **zasebna aplikacija** (`agent\`, port 8090) koja je MCP klijent: nema bazu ni pravila, a u sustav ulazi s tokenom kupca. Kupac se prepoznaje po tokenu, potvrda radnji i audit su na strani sustava, pa vrijede za svakog MCP klijenta. Detalji: [docs/PHASE4.md](docs/PHASE4.md).
- **Glas (Faza 5):** u chatu gumb s mikrofonom: klik, govor, snimanje stane samo. Snimka ide sustavu i agentu, koji je pretvori u tekst (OpenAI, Spring AI), odgovori istim agentom i odgovor vrati kao govor (ElevenLabs „Maja“ + `eleven_v4_turbo`, ili OpenAI; Spring AI). Odgovor stiže kao stream i svira od prvog chunka; transkript se vidi odmah. Snimka se ne pohranjuje; traži privolu kupca. Detalji: [docs/PHASE5.md](docs/PHASE5.md).
- **Post-analiza razgovora i samoučenje (Faza 5):** utihnuli razgovori se iz audit traga sastave u transkript, **pseudonimiziraju** i analiziraju modelom (sažetak, tema, ishod, raspoloženje, ocjena agenta, rupe u znanju). Rupe u znanju postaju **prijedlozi za bazu znanja** koje admin odobri (uz ispravke) ili odbije; odobreno odmah ulazi u RAG. Admin tab „Razgovori“ i tab „Znanje“.
- **GDPR (Faza 5):** privola za glas s verzijom teksta, izvoz svih podataka kupca (JSON), brisanje razgovora na zahtjev, automatska retencija (30 dana poruke, 365 dana anonimne analize), logovi bez e-maila, agent odgovara na pitanja o privatnosti iz baze znanja. Detalji: [docs/GDPR.md](docs/GDPR.md).
- **Jedan API** (`/api/**`, Swagger na `/swagger-ui.html`) za sve kanale; pristup bearer tokenom, uloge `CUSTOMER` / `ADMIN` (i `AGENT` za MCP).

```
   Shop (React) + chat/glas ──┐                     ┌── PostgreSQL 17 (Docker, :5433): podaci, vector_store, audit_events, conversations
                              ├──> sustav :8080 ────┤    Flyway V1–V5
   Admin (React) ─────────────┘    REST API + pravila├── OpenAI kroz Spring AI: embeddinzi (baza znanja), gpt-4o (analiza razgovora)
                                   MCP server /mcp   ├── raspored: analiza utihnulih razgovora, retencija podataka
                                      ▲      │ poruka ili snimka + token
                       MCP (alati,    │      ▼
                       baza znanja)   └── agent :8090 ─── OpenAI gpt-4o (ChatClient), STT gpt-4o-mini-transcribe, TTS ElevenLabs/OpenAI
```

## Pokretanje (jedan potez)

```powershell
.\run-phase-1.ps1     # Faza 1 (radi i bez OpenAI ključa)
.\run-phase-2.ps1     # Faza 2: isto + baza znanja (traži openai.key ili OPENAI_API_KEY)
.\run-phase-4.ps1     # Faza 4: sustav (MCP server) + agent (zasebna aplikacija); traži openai.key ili OPENAI_API_KEY
.\run-phase-5.ps1     # Faza 5: isto + glas (elevenlabs.key je opcionalan), analiza razgovora, GDPR
```

`run-phase-1.ps1` i `run-phase-2.ps1` pokreću samo sustav; `run-phase-3.ps1`, `run-phase-4.ps1` i `run-phase-5.ps1` na ovoj grani pokreću isto (sustav + agent).
ElevenLabs ključ: datoteka `elevenlabs.key` (gitignored); bez njega agent govori kroz OpenAI TTS.

Skripta: provjeri/pokrene Docker Desktop → podigne PostgreSQL (`docker compose up --wait`) → nađe JDK 25 → učita `openai.key` ako postoji → `mvn package`
(uključivo build React frontenda; prvi put skida Node i npm pakete, par minuta) → pokrene aplikaciju u istom prozoru (logovi u konzoli).
Faza 4 builda i agenta (`agent\`), pokrene sustav, pričeka ga, pa pokrene agenta; oba procesa pišu u isti prozor (oznake `[sustav]` i `[agent ]`), Ctrl+C gasi oba.
OpenAI ključ: datoteka `openai.key` u korijenu projekta (jedna linija, gitignored) — trebaju ga baza znanja i agent; bez njega sve iz Faze 1 radi, a baza znanja i agent javljaju da nisu dostupni (503).

| Opcija | Što radi |
|---|---|
| `-NoBuild` | preskoči Maven build, koristi postojeći `target\nottrilix-2026.jar` (i `agent\target\nottrilix-agent.jar`) |
| `-Reset` | obriši bazu (docker volume) → svježi seed (i ponovni ingest baze znanja) |
| `-Stop` | ugasi PostgreSQL container (aplikaciju gasiš s Ctrl+C) |

Adrese: **Shop** http://localhost:8080/ · **Admin** http://localhost:8080/admin/ · **API/Swagger** http://localhost:8080/swagger-ui.html · **MCP** http://localhost:8080/mcp · **Agent** http://localhost:8090/chat

Preduvjeti: Docker Desktop, JDK 25 (`C:\Program Files\Java\jdk-25*`), Maven 3.9+. Node **nije** potreban (Maven ga skida u `./node`).

## Demo podaci (Flyway `V2__seed.sql`)

- **Kupci (e-mail / lozinka · wallet):** `ana.anic@example.com` / `ana123` · 115 € — `marko.maric@example.com` / `marko123` · 25 € — `ivana.ivic@example.com` / `ivana123` · 50 € (Marko ne može kupiti Steam 50 € bez nadoplate).
- **Admin (korisničko ime / lozinka):** `admin` / `admin123`.
- **Proizvodi:** A1 5/10/20 € · HT 5/10/25 € · Steam 10/25/50 € · PlayStation 20/50 € · Xbox 15/30 €.
- **Bonovi:** serijski `PREFIX-DENOMINACIJA-NNN` (npr. `STM-25-003`), 12-znamenkasti kod.
- **Povijest:** 6 transakcija (web + kiosk; aktivni, iskorišteni, jedan povrat s blokiranim bonom) i 2 prijave podrške (1 otvorena).

## Poslovna pravila (servisni sloj, `application.yml → nottrilix.rules`)

| Pravilo | Greška (HTTP 409) |
|---|---|
| Dnevni limit kupnje po kupcu: **150 €** | `DAILY_LIMIT_EXCEEDED` |
| Na walletu mora biti dovoljno sredstava za cijenu bona | `INSUFFICIENT_FUNDS` |
| Bon mora postojati na zalihi (zaključavanje reda u bazi) | `OUT_OF_STOCK` |
| Uplata na wallet: najviše **500 €** po uplati | `TOPUP_LIMIT_EXCEEDED` |
| Povrat samo unutar **14 dana** i samo ako bon nije iskorišten; iznos ide na wallet, bon se blokira | `REFUND_WINDOW_EXPIRED`, `VOUCHER_ALREADY_REDEEMED`, `ALREADY_REFUNDED` |
| Blokada bona: ne za iskorišten / već blokiran | `VOUCHER_ALREADY_REDEEMED`, `ALREADY_BLOCKED` |
| Prijava se može vezati samo na transakciju istog kupca | `TRANSACTION_CUSTOMER_MISMATCH` |

Greške su uvijek `{ "error": "KOD", "message": "poruka" }` (404 `NOT_FOUND`, 400 `VALIDATION_ERROR`, 401 `UNAUTHORIZED` / `INVALID_CREDENTIALS`, 403 `FORBIDDEN`).

## Prijava i pristup

Spring Security, lozinke bcrypt u bazi, **bearer token** (bez sesija i kolačića; token u `localStorage`, zaseban ključ za shop i admin pa oba mogu biti prijavljena u istom pregledniku).

| Tko | Prijava | Uloga | Smije |
|---|---|---|---|
| Kupac | `POST /api/auth/customer/login` `{email, password}` | `CUSTOMER` | `/api/shop/**` (katalog, kupnja, vlastite kupnje), `/api/agent/**` (razgovor s agentom) |
| Admin/support | `POST /api/auth/admin/login` `{username, password}` | `ADMIN` | `/api/admin/**`, `/api/customers/**` |
| Aplikacija agenta | ključ agenta kao bearer token (`nottrilix.mcp.agent-key`) | `AGENT` | `/mcp`: spajanje i popis alata; alati rade tek uz token kupca |

Odgovor prijave je `{ "token": "...", "user": {realm, id, name, login} }`; dalje `Authorization: Bearer <token>`. `GET /api/auth/me` = tko sam, `POST /api/auth/logout` poništava token. Statika, Swagger i login endpointi su javni; sve ostalo pod `/api/**` traži prijavu. U Swaggeru gumb *Authorize* prima token.

## API (sažetak)

| Metoda i putanja | Uloga | Opis |
|---|---|---|
| `POST /api/auth/customer/login`, `POST /api/auth/admin/login` | — | prijava → token |
| `GET /api/auth/me`, `POST /api/auth/logout` | bilo koja | tko sam / odjava |
| `GET /api/shop/catalog` | CUSTOMER | katalog (dostupno / rasprodano) |
| `POST /api/shop/purchase` `{productId}` | CUSTOMER | kupnja za prijavljenog kupca, naplata s walleta → `{transaction (s bonom), walletBalanceEur}` |
| `GET /api/shop/transactions` | CUSTOMER | kupnje prijavljenog kupca |
| `GET /api/shop/wallet`, `POST /api/shop/wallet/topup` `{amountEur}` | CUSTOMER | wallet (stanje + povijest) / nadoplata (simulirano) |
| `GET /api/shop/vouchers/{serial}/check` | CUSTOMER | provjera vlastitog bona kod pružatelja (tuđi bon = 404) |
| `GET /api/customers`, `GET /api/customers/{id}` | ADMIN | kupci (sa stanjem walleta) |
| `GET /api/admin/customers/{id}/wallet`, `POST /api/admin/customers/{id}/wallet/topup` `{amountEur, note}` | ADMIN | wallet kupca / uplata podrške |
| `GET /api/admin/transactions?status=&brand=&customerId=` | ADMIN | sve transakcije |
| `GET /api/admin/transactions/{id}` | ADMIN | jedna transakcija |
| `POST /api/admin/transactions/{id}/refund` | ADMIN | povrat novca na wallet kupca |
| `POST /api/admin/vouchers/{serial}/block` | ADMIN | blokada bona |
| `GET /api/admin/vouchers/{serial}/check` | ADMIN | provjera iskorištenosti bona kod pružatelja (fiktivni upit) |
| `GET /api/admin/stock` | ADMIN | zaliha po proizvodu/statusu |
| `GET /api/admin/tickets`, `POST /api/admin/tickets`, `POST /api/admin/tickets/{id}/resolve` | ADMIN | podrška |
| `GET /api/admin/kb/search?q=&topK=`, `GET /api/admin/kb/status`, `POST /api/admin/kb/ingest?force=` | ADMIN | baza znanja: semantička pretraga, stanje, ponovni ingest (503 `KB_UNAVAILABLE` bez ključa) |
| `POST /api/agent/chat` `{conversationId?, message}` | CUSTOMER | poruka agentu → `{conversationId, reply, toolCalls, sources, pendingAction, …}`; sustav je prosljeđuje aplikaciji agenta (503 `AGENT_UNAVAILABLE` ako agent ne radi) |
| `POST /api/agent/voice` (multipart `audio`, `conversationId?`) | CUSTOMER | glasovna poruka → `{transcript, chat, audioBase64, audioMimeType, sttMs, ttsMs, ttsProvider}`; 403 `CONSENT_REQUIRED` bez privole |
| `POST /api/agent/voice/stream` (multipart) · `GET /api/agent/voice/filler` | CUSTOMER | isto kao stream: JSON redci `transcript`, `chat`, `audio` pa mp3 kako nastaje (preglednik svira odmah) · fraza „Samo trenutak.“ (mp3) |
| `GET /api/shop/privacy`, `POST /api/shop/privacy/consent` `{type}`, `DELETE /api/shop/privacy/consent/{type}` | CUSTOMER | pregled podataka i rokova, davanje i povlačenje privole (VOICE) |
| `GET /api/shop/privacy/export`, `POST /api/shop/privacy/erase-conversations` | CUSTOMER | izvoz svih podataka kupca (JSON) / brisanje razgovora na zahtjev |
| `GET /api/admin/conversations?limit=`, `GET …/stats`, `GET …/{id}`, `POST …/{id}/analyze`, `POST …/analyze-idle` | ADMIN | razgovori s analizom, statistika, transkript, analiza sada |
| `GET /api/admin/kb/suggestions?status=`, `POST …/{id}/approve` `{question, answer}`, `POST …/{id}/reject` | ADMIN | prijedlozi za bazu znanja iz razgovora (samoučenje s odobrenjem) |
| `POST /mcp` (JSON-RPC: `tools/list`, `tools/call`, …) | CUSTOMER, AGENT | MCP server: 14 alata nad servisima; kupac iz tokena, razgovor i turn u `_meta` |
| `GET /api/admin/audit?conversationId=&customerId=&limit=` | ADMIN | audit trag agenta: pozivi alata, dohvati iz baze znanja, turnovi razgovora |

## Logovi

Logback (`logback-spring.xml`): sve na konzolu (u boji) i u `logs/nottrilix-2026.log`. Svaki HTTP zahtjev s korisnikom (`HTTP` logger: `POST /api/shop/purchase user=customer:ana.anic@example.com -> 201 (60 ms)`),
svaka prijava/odjava i odbijeni pristup (`AUTH` logger: `LOGIN OK`, `LOGIN FAILED`, `AUTH 401/403`, `TOKEN issued/revoked`),
svaka poslovna operacija (`PURCHASE OK …`, `PURCHASE DENIED reason=OUT_OF_STOCK …`, `WALLET DEBIT/CREDIT/TOPUP …`, `REFUND OK …`, `TICKET …`, `VOUCHER CHECK …`), svaki „poziv“ pružatelju (`PROVIDER` logger) i svaki odbijeni zahtjev.
Baza znanja: `KB` logger (`KB INGEST …`, `KB SEARCH …`). MCP server: `MCP` logger (`MCP tools/call purchaseVoucher user=customer:… conv=… turn=2 -> CONFIRMATION_REQUIRED`, `CONFIRM PURCHASE 5 … -> ceka potvrdu kupca`)
i `AUDIT` logger (zrcalo tablice `audit_events`: `TOOL_CALL purchaseVoucher … -> DENIED | INSUFFICIENT_FUNDS: …`).
Agent piše u istu konzolu s oznakom `[agent ]` (`TURN n conv=… msg='…'`, `TOOL purchaseVoucher … (12 ms preko MCP-a)`, `TURN n … OK 2981 ms, alati=[…], tokeni=…`, `VOICE STT … / TTS …`) i u `logs/nottrilix-agent.log`; retci sustava imaju oznaku `[sustav]`.
Faza 5: `ANALYSIS` logger (`ANALYSIS conv=… OK 4 s -> KUPNJA / RIJESENO / …`), `LEARN` (prijedlozi i odobrenja), `PRIVACY` (privola, izvoz, brisanje, retencija), `CONVERSATION`. Identitet u logovima je `customer#2`, ne e-mail.
SQL ispis: `logging.level.org.hibernate.SQL: DEBUG` u `application.yml`.

## Razvoj

- Sustav: `mvn -DskipTests package` (ili samo `mvn test`: pravila u `ShopServiceTest`, granice MCP-a u `ConfirmationGateTest`, `SupportMcpToolsTest`, `AuditedToolHandlerTest`). Agent: `mvn -f agent\pom.xml package`. Build treba JDK 25 (`JAVA_HOME`). Na Windowsu build ne prolazi dok aplikacija radi (drži jar u `target\`).
- Proba agenta protiv pokrenutih aplikacija: `python scripts\agent_e2e.py` (scenariji A–H + audit; prije toga `-Reset`, a za hrvatske znakove u konzoli `$env:PYTHONIOENCODING = "utf-8"`).
- MCP bez agenta: `python scripts\mcp_direct.py` (sirovi JSON-RPC pozivi: bez tokena, s ključem agenta, s tokenom kupca, potvrda kroz dva turna).
- Faza 5: `python scripts\phase5_e2e.py` (glas s probnom snimkom, privatnost, analiza razgovora, učenje); probna snimka nastaje iz `openai.key` (TTS), ključ se ne ispisuje.
- Frontend s hot-reloadom: aplikacija upaljena na 8080, pa u `frontend/`: `npm install && npm run dev` → http://localhost:5173 (i `/admin/`), `/api` se proxyja na 8080.
- Preskoči frontend u Maven buildu: `-Dskip.installnodenpm -Dskip.npm`.

## Struktura

```
src/main/java/com/nottrilix
  domain/    entiteti + enumi (Customer (+wallet), WalletEntry, AdminUser, Product, Voucher, Transaction, SupportTicket, AuditEvent)
  repo/      Spring Data repozitoriji (FOR UPDATE za kupca/bon/transakciju)
  service/   CatalogService, ShopService, WalletService, SupportService, VoucherCheckService  <- poslovna pravila; AuditService
  provider/  ProviderGateway (sučelje prema pružatelju) + SimulatedProviderGateway (fiktivni A1/HT/Steam/...)
  kb/        baza znanja: KbMarkdownParser (md -> odlomci), KnowledgeBaseIngestor (Spring AI VectorStore, idempotentno; + naučeni odlomci iz kb_suggestions),
             KnowledgeBaseService (pretraga), KnowledgeLearningService (prijedlozi: odobri/odbij -> ingest)
  analysis/  post-analiza razgovora: ConversationAnalyzer (pseudonimizirani transkript -> Spring AI structured output -> ConversationInsight),
             AnalysisScheduler (utihnuli razgovori), AnalysisConfig (ChatClient za analizu)
  privacy/   GDPR: PrivacyService (privole, izvoz, brisanje), RetentionJob (rok čuvanja), Pseudonymizer, PrivacyProperties
  mcp/       MCP server: SupportMcpTools (@McpTool adapteri nad servisima), McpCall (kupac iz tokena, razgovor iz _meta),
             ConfirmationGate (potvrda radnji), AuditedToolHandler (audit na granici), McpServerConfig, ToolResult
  agent/     veza prema aplikaciji agenta: AgentClient (HTTP, tekst i snimka), AgentChatService (prosljeđivanje + audit turna + okvir razgovora + privola za glas)
  auth/      Realm (CUSTOMER, ADMIN, AGENT), AuthUser, LoginToken, NotTrilixAuthenticationProvider (bcrypt), TokenService, BearerTokenFilter
  api/       kontroleri (Auth, Shop, Customers, Admin, KnowledgeBase, Agent, Audit), dto/ (recordi), ApiExceptionHandler
  config/    SecurityConfig, RulesProperties, AppConfig (Clock, OpenAPI), WebConfig, RequestLoggingFilter
src/main/resources/db/migration   V1__schema.sql, V2__seed.sql, V3__vector_store.sql (pgvector), V4__audit_events.sql (audit trag agenta),
                                  V5__conversations_privacy.sql (razgovori, analize, prijedlozi za bazu znanja, privole)
src/main/resources/kb/            10 markdown dokumenata baze znanja (pisani za RAG, vidi docs/PHASE2.md; 10 = privatnost)
frontend/   Vite + React + TypeScript: src/customer (shop, ChatWidget s glasom i privolom, PrivacyModal), src/admin (tabovi Razgovori, Znanje s prijedlozima, Audit), src/shared
scripts/    agent_e2e.py (proba razgovora s agentom), mcp_direct.py (MCP pozivi bez agenta), phase5_e2e.py (glas, privatnost, analiza, učenje)

agent/      aplikacija agenta (vlastiti pom.xml, port 8090)
  src/main/java/com/nottrilix/agent
             AgentConfig (ChatClient, memorija, RAG preko MCP-a), AgentService (turn), AgentController (POST /chat, POST /voice),
             VoiceService (STT -> agent -> TTS, Spring AI TranscriptionModel + TextToSpeechModel),
             McpClientConfig (token kupca po pozivu, _meta, filtar alata), SystemGateway (tko je kupac, što čeka potvrdu, baza znanja),
             TracingToolCallback, CustomerToken, TurnContext, ToolOutcome, ReplyStyle
  src/main/resources/agent/system-prompt.txt (persona i pravila ponašanja agenta; parametri se pune po pozivu)
```
