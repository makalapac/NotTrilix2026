# NotTrilix 2026

A deliberately simplified "production" system for selling prepaid vouchers: **one Spring Boot application** (API + two React frontends) with a **PostgreSQL** database,
and next to it a customer-support agent as a **second Spring Boot application**.
It is the demo for the talk *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI* (JavaCro 2026). Every later phase is built around this system:
Phase 1 = the existing system (as is) · Phase 2 = knowledge base (RAG) · Phase 3 = support agent · Phase 4 = MCP over the network (the system is an MCP server, the agent a separate application) ·
Phase 5 = voice, post-call analysis with self-learning, GDPR · Phase 6 = telephone (Infobip): a call from a mobile phone into the same agent, the caller recognised by number. All AI parts are built on **Spring AI**.

The application itself speaks Croatian (the agent "Nola", the UI, the knowledge base), as does the per-phase documentation.
Phase status and "where we are": [PROGRESS.md](PROGRESS.md) · per-phase write-ups (decisions, verification, pitfalls): [docs/PHASE1.md](docs/PHASE1.md) · [docs/PHASE2.md](docs/PHASE2.md) · [docs/PHASE3.md](docs/PHASE3.md) · [docs/PHASE4.md](docs/PHASE4.md) · [docs/PHASE5.md](docs/PHASE5.md) · [docs/PHASE6.md](docs/PHASE6.md) · personal data: [docs/GDPR.md](docs/GDPR.md) · model choice: [docs/MODEL.md](docs/MODEL.md).

## What the system does

- **Products:** telecom top-up vouchers (A1, HT) and gaming vouchers (Steam, PlayStation, Xbox), denominations in EUR.
- **Stock:** pre-generated vouchers (serial number + activation code), 8 per product.
- **Wallet:** every customer has a wallet; a purchase is **charged to the wallet** (with a confirmation dialog), a refund goes back to the wallet, the customer can top the wallet up (simulated card payment); every change is in the wallet ledger.
- **Customers (shop, `/`):** login with e-mail and password, catalogue (available / sold out; the customer never sees stock levels), purchase from the wallet (the code is shown right away), "Moj wallet" (balance + history), own purchases.
- **Admin (`/admin/`):** login with username and password; all transactions (filters), customers with wallets (support top-up, history), stock, support: refund to the wallet, block a voucher, customer tickets.
- **Voucher check at the provider:** a simulated query to A1/HT/Steam/PlayStation/Xbox ("has the voucher been redeemed?"); support for any voucher, a customer for their own; the answer carries the provider status, redemption date, a reference and simulated latency.
- **Knowledge base (Phase 2, Spring AI):** 10 markdown support documents (`src/main/resources/kb/`) → ingested into pgvector at startup (OpenAI embeddings) → semantic search for support (admin tab "Znanje", `GET /api/admin/kb/search?q=…`). Details: [docs/PHASE2.md](docs/PHASE2.md).
- **Support agent "Nola" (Phase 3, Spring AI `ChatClient`):** the "Chat s podrškom" button in the shop. The agent answers from the knowledge base and acts through tools over the same services (purchases, wallet, voucher check, purchase, refund, block, ticket), always on behalf of the logged-in customer and under the same business rules. Purchase, refund and block require the **customer's confirmation** (the server holds the pending action). Every tool call, knowledge-base retrieval and conversation turn is in the **audit trail** (admin tab "Audit"). Details: [docs/PHASE3.md](docs/PHASE3.md).
- **MCP server (Phase 4):** the same tools are available over the network at `POST /mcp` (stateless HTTP). From this phase on the agent is a **separate application** (`agent\`, port 8090) acting as an MCP client: no database, no rules, and it enters the system with the customer's token. The customer comes from the token, confirmation and audit live in the system, so they hold for every MCP client. Details: [docs/PHASE4.md](docs/PHASE4.md).
- **Voice (Phase 5):** a microphone button in the chat: click, speak, recording stops on silence. The recording goes to the system and the agent, which transcribes it (OpenAI, Spring AI), answers with the same agent and returns the reply as speech (ElevenLabs "Maja" + `eleven_v4_turbo`, or OpenAI; Spring AI). The reply streams and plays from the first chunk; the transcript shows immediately. Recordings are not stored; the customer's consent is required. Details: [docs/PHASE5.md](docs/PHASE5.md).
- **Post-call analysis and self-learning (Phase 5):** idle conversations are assembled from the audit trail into a transcript, **pseudonymised** and analysed by the model (summary, topic, outcome, sentiment, agent score, knowledge gaps). Knowledge gaps become **knowledge-base proposals** that an admin approves (with edits) or rejects; approved sections enter RAG immediately. Admin tabs "Razgovori" and "Znanje".
- **Telephone (Phase 6):** Infobip reports the call to the agent and bridges it to the agent's WebSocket (PCM16 frames both ways); the system recognises the customer by caller id and issues the agent a customer token for the duration of the call; VAD, STT, the same agent, TTS as PCM, barge-in. The call simulator `scripts/phone_sim.py` exercises all of it without a phone number. Details: [docs/PHASE6.md](docs/PHASE6.md).
- **GDPR (Phase 5):** voice consent with the consent-text version, export of all customer data (JSON), deletion of conversations on request, automatic retention (30 days for messages, 365 days for anonymous analyses), logs without e-mail addresses, and the agent answers privacy questions from the knowledge base. Details: [docs/GDPR.md](docs/GDPR.md).
- **One API** (`/api/**`, Swagger at `/swagger-ui.html`) for all channels; bearer-token access, roles `CUSTOMER` / `ADMIN` (and `AGENT` for MCP).

```
   Shop (React) + chat/voice ──┐                     ┌── PostgreSQL 17 (Docker, :5433): data, vector_store, audit_events, conversations
                               ├──> system :8080 ────┤    Flyway V1–V5
   Admin (React) ──────────────┘    REST API + rules ├── OpenAI through Spring AI: embeddings (knowledge base), gpt-5.6-terra (conversation analysis)
                                    MCP server /mcp  ├── schedules: analysis of idle conversations, data retention
                                       ▲      │ message or recording + token
                        MCP (tools,    │      ▼
                        knowledge)     └── agent :8090 ─── OpenAI gpt-5.6-terra (ChatClient), STT gpt-4o-mini-transcribe (phone: gpt-4o-transcribe), TTS ElevenLabs/OpenAI
```

## Running it (one command)

```powershell
.\run-phase-1.ps1     # Phase 1 (works without an OpenAI key)
.\run-phase-2.ps1     # Phase 2: the same + knowledge base (needs openai.key or OPENAI_API_KEY)
.\run-phase-4.ps1     # Phase 4: system (MCP server) + agent (separate application); needs openai.key or OPENAI_API_KEY
.\run-phase-5.ps1     # Phase 5: the same + voice (elevenlabs.key is optional), conversation analysis, GDPR
.\run-phase-6.ps1     # Phase 6: the same + telephone: tunnel (cloudflared) + Infobip (infobip.key); -NoTunnel for the simulator
```

`run-phase-1.ps1` and `run-phase-2.ps1` start only the system; `run-phase-3.ps1`, `run-phase-4.ps1` and `run-phase-5.ps1` start the same thing on this branch (system + agent).
ElevenLabs key: the file `elevenlabs.key` (gitignored); without it the agent speaks through OpenAI TTS.

The script: checks/starts Docker Desktop → brings up PostgreSQL (`docker compose up --wait`) → finds JDK 25 → loads `openai.key` if present → `mvn package`
(including the React frontend build; the first run downloads Node and npm packages, a few minutes) → starts the application in the same window (logs in the console).
Phase 4 also builds the agent (`agent\`), starts the system, waits for it, then starts the agent; both processes write to the same window (tags `[sustav]` = system and `[agent ]`), Ctrl+C stops both.
OpenAI key: the file `openai.key` in the project root (one line, gitignored); the knowledge base and the agent need it; without it everything from Phase 1 works, while the knowledge base and the agent report themselves unavailable (503).
Infobip: the file `infobip.key` (line 1 = API key, line 2 = base URL, optional line 3 = media-stream config id); `demo-caller.txt` holds your own mobile number, which stands in for customer 1 (Ana). Both are gitignored.

| Option | What it does |
|---|---|
| `-NoBuild` | skip the Maven build, use the existing `target\nottrilix-2026.jar` (and `agent\target\nottrilix-agent.jar`) |
| `-Reset` | drop the database (docker volume) → fresh seed (and a new knowledge-base ingest) |
| `-Stop` | stop the PostgreSQL container (the application is stopped with Ctrl+C) |
| `-PublicUrl https://…` (Phase 6) | use a tunnel started separately (its URL survives application restarts; the Infobip webhooks are set once) |

Addresses: **Shop** http://localhost:8080/ · **Admin** http://localhost:8080/admin/ · **API/Swagger** http://localhost:8080/swagger-ui.html · **MCP** http://localhost:8080/mcp · **Agent** http://localhost:8090/chat

Prerequisites: Docker Desktop, JDK 25 (`C:\Program Files\Java\jdk-25*`), Maven 3.9+. Node is **not** required (Maven downloads it into `./node`). The scripts are PowerShell (Windows); on other systems run the same steps by hand (`docker compose up -d`, `mvn package`, `java -jar`).

## Demo data (Flyway `V2__seed.sql`)

- **Customers (e-mail / password · wallet):** `ana.anic@example.com` / `ana123` · 115 € — `marko.maric@example.com` / `marko123` · 25 € — `ivana.ivic@example.com` / `ivana123` · 50 € (Marko cannot buy Steam 50 € without a top-up).
- **Admin (username / password):** `admin` / `admin123`.
- **Products:** A1 5/10/20 € · HT 5/10/25 € · Steam 10/25/50 € · PlayStation 20/50 € · Xbox 15/30 €.
- **Vouchers:** serial `PREFIX-DENOMINATION-NNN` (e.g. `STM-25-003`), 12-digit activation code.
- **History:** 6 transactions (web + kiosk; active, redeemed, one refund with a blocked voucher) and 2 support tickets (1 open).

All credentials are demo values for a local run; change them before exposing the system anywhere.

## Business rules (service layer, `application.yml → nottrilix.rules`)

| Rule | Error (HTTP 409) |
|---|---|
| Daily purchase limit per customer: **150 €** | `DAILY_LIMIT_EXCEEDED` |
| The wallet must cover the voucher price | `INSUFFICIENT_FUNDS` |
| The voucher must be in stock (row lock in the database) | `OUT_OF_STOCK` |
| Wallet top-up: at most **500 €** per top-up | `TOPUP_LIMIT_EXCEEDED` |
| Refund only within **14 days** and only if the voucher is not redeemed; the amount goes to the wallet, the voucher is blocked | `REFUND_WINDOW_EXPIRED`, `VOUCHER_ALREADY_REDEEMED`, `ALREADY_REFUNDED` |
| Blocking a voucher: not for redeemed / already blocked | `VOUCHER_ALREADY_REDEEMED`, `ALREADY_BLOCKED` |
| A ticket can only reference a transaction of the same customer | `TRANSACTION_CUSTOMER_MISMATCH` |

Errors are always `{ "error": "CODE", "message": "text" }` (404 `NOT_FOUND`, 400 `VALIDATION_ERROR`, 401 `UNAUTHORIZED` / `INVALID_CREDENTIALS`, 403 `FORBIDDEN`).

## Login and access

Spring Security, bcrypt passwords in the database, **bearer token** (no sessions or cookies; the token lives in `localStorage` under separate keys for the shop and the admin, so both can be logged in in the same browser).

| Who | Login | Role | May use |
|---|---|---|---|
| Customer | `POST /api/auth/customer/login` `{email, password}` | `CUSTOMER` | `/api/shop/**` (catalogue, purchase, own purchases), `/api/agent/**` (conversation with the agent) |
| Admin/support | `POST /api/auth/admin/login` `{username, password}` | `ADMIN` | `/api/admin/**`, `/api/customers/**` |
| Agent application | the agent key as bearer token (`nottrilix.mcp.agent-key`) | `AGENT` | `/mcp`: connecting and listing tools; tools work only with a customer token. `/api/phone/**` |

The login response is `{ "token": "...", "user": {realm, id, name, login} }`; afterwards `Authorization: Bearer <token>`. `GET /api/auth/me` = who am I, `POST /api/auth/logout` revokes the token. Static files, Swagger and the login endpoints are public; everything else under `/api/**` requires a login. In Swagger the *Authorize* button takes the token.

## API (summary)

| Method and path | Role | Description |
|---|---|---|
| `POST /api/auth/customer/login`, `POST /api/auth/admin/login` | — | login → token |
| `GET /api/auth/me`, `POST /api/auth/logout` | any | who am I / logout |
| `GET /api/shop/catalog` | CUSTOMER | catalogue (available / sold out) |
| `POST /api/shop/purchase` `{productId}` | CUSTOMER | purchase for the logged-in customer, charged to the wallet → `{transaction (with the voucher), walletBalanceEur}` |
| `GET /api/shop/transactions` | CUSTOMER | the logged-in customer's purchases |
| `GET /api/shop/wallet`, `POST /api/shop/wallet/topup` `{amountEur}` | CUSTOMER | wallet (balance + history) / top-up (simulated) |
| `GET /api/shop/vouchers/{serial}/check` | CUSTOMER | check an own voucher at the provider (someone else's voucher = 404) |
| `GET /api/customers`, `GET /api/customers/{id}` | ADMIN | customers (with wallet balances) |
| `GET /api/admin/customers/{id}/wallet`, `POST /api/admin/customers/{id}/wallet/topup` `{amountEur, note}` | ADMIN | a customer's wallet / support top-up |
| `GET /api/admin/transactions?status=&brand=&customerId=` | ADMIN | all transactions |
| `GET /api/admin/transactions/{id}` | ADMIN | one transaction |
| `POST /api/admin/transactions/{id}/refund` | ADMIN | refund to the customer's wallet |
| `POST /api/admin/vouchers/{serial}/block` | ADMIN | block a voucher |
| `GET /api/admin/vouchers/{serial}/check` | ADMIN | redemption check at the provider (simulated query) |
| `GET /api/admin/stock` | ADMIN | stock per product/status |
| `GET /api/admin/tickets`, `POST /api/admin/tickets`, `POST /api/admin/tickets/{id}/resolve` | ADMIN | support tickets |
| `GET /api/admin/kb/search?q=&topK=`, `GET /api/admin/kb/status`, `POST /api/admin/kb/ingest?force=` | ADMIN | knowledge base: semantic search, status, re-ingest (503 `KB_UNAVAILABLE` without a key) |
| `POST /api/agent/chat` `{conversationId?, message}` | CUSTOMER | message to the agent → `{conversationId, reply, toolCalls, sources, pendingAction, …}`; the system forwards it to the agent application (503 `AGENT_UNAVAILABLE` when the agent is down) |
| `POST /api/agent/voice` (multipart `audio`, `conversationId?`) | CUSTOMER | voice message → `{transcript, chat, audioBase64, audioMimeType, sttMs, ttsMs, ttsProvider}`; 403 `CONSENT_REQUIRED` without consent |
| `POST /api/agent/voice/stream` (multipart) · `GET /api/agent/voice/filler` | CUSTOMER | the same as a stream: JSON lines `transcript`, `chat`, `audio`, then mp3 as it is produced (the browser plays immediately) · the "Samo trenutak." phrase (mp3) |
| `GET /api/shop/privacy`, `POST /api/shop/privacy/consent` `{type}`, `DELETE /api/shop/privacy/consent/{type}` | CUSTOMER | data overview and retention periods, giving and withdrawing consent (VOICE) |
| `GET /api/shop/privacy/export`, `POST /api/shop/privacy/erase-conversations` | CUSTOMER | export of all customer data (JSON) / deletion of conversations on request |
| `GET /api/admin/conversations?limit=`, `GET …/stats`, `GET …/{id}`, `POST …/{id}/analyze`, `POST …/analyze-idle` | ADMIN | conversations with analyses, statistics, transcript, analyse now |
| `GET /api/admin/kb/suggestions?status=`, `POST …/{id}/approve` `{question, answer}`, `POST …/{id}/reject` | ADMIN | knowledge-base proposals from conversations (self-learning with approval) |
| `POST /api/phone/calls` `{callId, from}`, `POST …/{id}/turns`, `POST …/{id}/end` | AGENT | phone channel: customer by caller id → customer token for the call; turns and the end of the call go to the audit trail |
| `POST /mcp` (JSON-RPC: `tools/list`, `tools/call`, …) | CUSTOMER, AGENT | MCP server: 14 tools over the services; customer from the token, conversation and turn in `_meta` |
| `GET /api/admin/audit?conversationId=&customerId=&limit=` | ADMIN | the agent's audit trail: tool calls, knowledge-base retrievals, conversation turns |

## Logs

Logback (`logback-spring.xml`): everything to the console (coloured) and to `logs/nottrilix-2026.log`. Every HTTP request with its user (`HTTP` logger: `POST /api/shop/purchase user=customer#1 -> 201 (60 ms)`),
every login/logout and denied access (`AUTH` logger: `LOGIN OK`, `LOGIN FAILED`, `AUTH 401/403`, `TOKEN issued/revoked`),
every business operation (`PURCHASE OK …`, `PURCHASE DENIED reason=OUT_OF_STOCK …`, `WALLET DEBIT/CREDIT/TOPUP …`, `REFUND OK …`, `TICKET …`, `VOUCHER CHECK …`), every "call" to a provider (`PROVIDER` logger) and every rejected request.
Knowledge base: `KB` logger (`KB INGEST …`, `KB SEARCH …`). MCP server: `MCP` logger (`MCP tools/call purchaseVoucher user=customer#1 conv=… turn=2 -> CONFIRMATION_REQUIRED`)
and the `AUDIT` logger (a mirror of the `audit_events` table: `TOOL_CALL purchaseVoucher … -> DENIED | INSUFFICIENT_FUNDS: …`).
The agent writes to the same console with the `[agent ]` tag (`TURN n conv=… msg='…'`, `TOOL purchaseVoucher … (12 ms preko MCP-a)`, `TURN n … OK 2981 ms, alati=[…], tokeni=…`, `VOICE STT … / TTS …`, `TELEPHONY …`, `CALL …`) and to `logs/nottrilix-agent.log`; the system's lines carry the `[sustav]` tag.
Phase 5: `ANALYSIS` logger (`ANALYSIS conv=… OK 4 s -> KUPNJA / RIJESENO / …`), `LEARN` (proposals and approvals), `PRIVACY` (consent, export, deletion, retention), `CONVERSATION`. Identity in the logs is `customer#2`, never an e-mail address; phone numbers are masked.
Log messages are in Croatian, like the UI. SQL output: `logging.level.org.hibernate.SQL: DEBUG` in `application.yml`.

## Development

- System: `mvn -DskipTests package` (or just `mvn test`: the rules in `ShopServiceTest`, the MCP boundaries in `ConfirmationGateTest`, `SupportMcpToolsTest`, `AuditedToolHandlerTest`). Agent: `mvn -f agent\pom.xml package`. The build needs JDK 25 (`JAVA_HOME`). On Windows the build fails while the application is running (it holds the jar in `target\`).
- Agent rehearsal against the running applications: `python scripts\agent_e2e.py` (scenarios A–H + audit; `-Reset` first, and for Croatian characters in the console `$env:PYTHONIOENCODING = "utf-8"`).
- MCP without the agent: `python scripts\mcp_direct.py` (raw JSON-RPC calls: without a token, with the agent key, with a customer token, confirmation across two turns).
- Phase 5: `python scripts\phase5_e2e.py` (voice with a test recording, privacy, conversation analysis, learning); the test recording is synthesised with `openai.key` (TTS), the key is never printed.
- Phase 6: `python scripts\phone_sim.py` plays Infobip (webhooks + WebSocket + PCM) against the running agent; `--from "+385 99 000 0000"` for an unknown caller.
- Frontend with hot reload: application running on 8080, then in `frontend/`: `npm install && npm run dev` → http://localhost:5173 (and `/admin/`), `/api` is proxied to 8080.
- Skip the frontend in the Maven build: `-Dskip.installnodenpm -Dskip.npm`.
- Fallback demo videos of phases 1–5 (Playwright against the real system): `demo-video/README.md`.

## Structure

```
src/main/java/com/nottrilix
  domain/    entities + enums (Customer (+wallet), WalletEntry, AdminUser, Product, Voucher, Transaction, SupportTicket, AuditEvent)
  repo/      Spring Data repositories (FOR UPDATE on customer/voucher/transaction)
  service/   CatalogService, ShopService, WalletService, SupportService, VoucherCheckService  <- business rules; AuditService
  provider/  ProviderGateway (interface towards the provider) + SimulatedProviderGateway (simulated A1/HT/Steam/...)
  kb/        knowledge base: KbMarkdownParser (md -> sections), KnowledgeBaseIngestor (Spring AI VectorStore, idempotent; + learned sections from kb_suggestions),
             KnowledgeBaseService (search), KnowledgeLearningService (proposals: approve/reject -> ingest), KnowledgeBaseWarmup
  analysis/  post-call analysis: ConversationAnalyzer (pseudonymised transcript -> Spring AI structured output -> ConversationInsight),
             AnalysisScheduler (idle conversations), AnalysisConfig (ChatClient for the analysis)
  privacy/   GDPR: PrivacyService (consents, export, deletion), RetentionJob (retention), Pseudonymizer, PrivacyProperties
  phone/     phone channel: PhoneSessionService (caller id -> customer token for the call), PhoneNumbers (digits, masking), PhoneProperties
  mcp/       MCP server: SupportMcpTools (@McpTool adapters over the services), McpCall (customer from the token, conversation from _meta),
             ConfirmationGate (action confirmation), AuditedToolHandler (audit at the boundary), McpServerConfig, ToolResult
  agent/     link to the agent application: AgentClient (HTTP, text and recording), AgentChatService (forwarding + turn audit + conversation frame + voice consent)
  auth/      Realm (CUSTOMER, ADMIN, AGENT), AuthUser, LoginToken, NotTrilixAuthenticationProvider (bcrypt), TokenService, BearerTokenFilter
  api/       controllers (Auth, Shop, Customers, Admin, KnowledgeBase, Agent, Audit, Conversation, Privacy, Phone), dto/ (records), ApiExceptionHandler
  config/    SecurityConfig, RulesProperties, AppConfig (Clock, OpenAPI), WebConfig, RequestLoggingFilter
src/main/resources/db/migration   V1__schema.sql, V2__seed.sql, V3__vector_store.sql (pgvector), V4__audit_events.sql (the agent's audit trail),
                                  V5__conversations_privacy.sql (conversations, analyses, knowledge-base proposals, consents)
src/main/resources/kb/            10 markdown knowledge-base documents (written for RAG, see docs/PHASE2.md; 10 = privacy)
frontend/   Vite + React + TypeScript: src/customer (shop, ChatWidget with voice and consent, PrivacyModal), src/admin (tabs Razgovori, Znanje with proposals, Audit), src/shared
scripts/    agent_e2e.py (agent rehearsal), mcp_direct.py (MCP calls without the agent), phase5_e2e.py (voice, privacy, analysis, learning), phone_sim.py (phone call simulator)
demo-video/ Playwright recordings of phases 1–5 as a fallback for the talk

agent/      the agent application (own pom.xml, port 8090)
  src/main/java/com/nottrilix/agent
             AgentConfig (ChatClient, memory, RAG over MCP), AgentService (turn), AgentController (POST /chat, POST /voice, /voice/stream),
             VoiceService (STT -> agent -> TTS, Spring AI TranscriptionModel + TextToSpeechModel), AgentWarmup (model and STT warmed at startup),
             McpClientConfig (customer token per call, _meta, tool filter), SystemGateway (who the customer is, what awaits confirmation, knowledge base),
             TracingToolCallback, CustomerToken, TurnContext, ToolOutcome, ReplyStyle, SpeechText (amounts spoken as words)
             telephony/ (Phase 6): InfobipWebhookController (call received -> bridge to our WebSocket, events), InfobipMediaWebSocketHandler (PCM frames, VAD, turn, pacing, barge-in),
             CallSession (VAD, frame queue), TelephonySpeech (STT/TTS for the line), InfobipCallsClient (Calls API), PhoneGateway (the system's /api/phone), CallRegistry, PhoneCall, PcmUtil
  src/main/resources/agent/system-prompt.txt (the agent's persona and rules, in Croatian; parameters filled per call)
```

## License

[MIT](LICENSE).
