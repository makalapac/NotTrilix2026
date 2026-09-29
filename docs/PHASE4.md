# Faza 4 — Mrežni MCP: sustav kao MCP server, agent kao zasebna aplikacija

_Status: **GOTOVO i e2e verificirano 2026-09-30** · grana `phase4` (iz `phase3`) · Spring AI 2.0.0, MCP Java SDK 2.0.0._

U Fazi 3 agent je živio u istom procesu kao sustav i alate zvao kao obične Java metode. U Fazi 4 **isti alati izlaze iz procesa**:
sustav ih nudi kao **MCP server** preko HTTP-a, a agent je **zasebna aplikacija** koja je MCP klijent. Poslovna pravila, baza, shop i admin se ne mijenjaju.

Pitanje faze: **što od granica iz Faze 3 preživi mrežu?** Odgovor je u tri retka, a ostatak dokumenta ih razrađuje:

| Granica | Faza 3 (isti proces) | Faza 4 (preko mreže) |
|---|---|---|
| **Tko je kupac** | `customerId` u Spring AI `ToolContext`-u | **token kupca u HTTP zaglavlju** svakog MCP zahtjeva; sustav ga sam provjerava (Spring Security) |
| **Potvrda radnje** | `ConfirmationGate` u agentu | `ConfirmationGate` **u sustavu**; agent u MCP `_meta` javlja samo `conversationId` i `turn` |
| **Audit** | omotač oko alata u agentu | omotač oko MCP alata **u sustavu** — vrijedi za svakog MCP klijenta, agent ga ne može preskočiti |

Agent time postaje ono što i treba biti: **nepovjerljiv klijent**. Nema bazu, nema pravila, nema audit; sve što zna i može dolazi kroz MCP, u ime prijavljenog kupca.

---

## 1. Kako je složeno

```
 preglednik (shop + chat)                                            admin (tab Audit)
        │ POST /api/agent/chat   Authorization: Bearer <token kupca>        │
        ▼                                                                   ▼
┌─────────────────────────────── SUSTAV :8080 ───────────────────────────────────────┐
│ AgentController ─ AgentChatService ──(1) poruka + token──┐        audit: CHAT_TURN │
│                                                          │                         │
│ POST /mcp  (MCP server, HTTP bez sesije)                 │                         │
│   Spring Security: token -> kupac                        │                         │
│   AuditedToolHandler ──> audit_events (TOOL_CALL)        │                         │
│   SupportMcpTools ──> ConfirmationGate                   │                         │
│        └─> CatalogService · ShopService · WalletService · SupportService ·         │
│            VoucherCheckService · KnowledgeBaseService (audit: KB_RETRIEVAL)        │
└───────▲──────────────────────────────────────────────────┼─────────────────────────┘
        │ (2) MCP tools/call                               ▼
        │     Authorization: Bearer <token kupca>   ┌──────────── AGENT :8090 ─────────────┐
        │     _meta: conversationId, turn, channel  │ AgentController ─ AgentService       │
        └───────────────────────────────────────────┤ Spring AI ChatClient (gpt-4o)        │
                                                    │   memorija razgovora                 │
                                                    │   RAG advisor ──> searchKnowledgeBase│
                                                    │   alati = MCP klijent (11 alata)     │
                                                    └──────────────────────────────────────┘
```

Jedan turn: shop pošalje poruku sustavu → sustav je s kupčevim tokenom proslijedi agentu (1) → agent pita model, a svaki alat i svaki dohvat iz
baze znanja je MCP poziv natrag u sustav (2), s istim tokenom → odgovor se vraća istim putem, a sustav turn upiše u audit.

## 2. Odluke

| Odluka | Razlog |
|---|---|
| **Dvije aplikacije, isti repo**: sustav u korijenu (kao dosad), agent u `agent\` (vlastiti `pom.xml`) | Mrežna granica je stvarna tek kad je i procesna. Bez premještanja postojećeg koda i bez višemodulnog builda. |
| **MCP preko HTTP-a bez sesije** (`spring.ai.mcp.server.protocol: STATELESS`, endpoint `/mcp`) | Svaki MCP poziv je jedan `POST /mcp` s tokenom — lako za objasniti i pokazati (`curl`). Restart sustava ne ruši vezu: agent nastavlja bez ponovnog spajanja. Poruke od servera prema klijentu nam ne trebaju. |
| **Alati su `@McpTool` metode** (`SupportMcpTools`), ne `@Tool` | MCP anotacije daju ono što preko mreže treba: `McpMeta` parametar (kontekst razgovora) i oznake alata (`readOnlyHint`, `destructiveHint`) koje vidi svaki MCP klijent. |
| **Kupac se prepoznaje po tokenu, ne po parametru ni po `_meta`** | Agent ne može reći „radim za kupca 2“; može samo proslijediti token koji je dobio. Sustav token provjerava istim filtrom kao i za shop. Model token nikad ne vidi. |
| **Agent se predstavlja vlastitim ključem** (`nottrilix.mcp.agent-key`, uloga `AGENT`) | Pri spajanju i dohvatu popisa alata još nema kupca. Ključ agenta sam po sebi ne daje pristup podacima: svaki alat bez kupčeva tokena vraća `CUSTOMER_REQUIRED`. |
| **Brana potvrde je u sustavu**, ključ = kupac (iz tokena) + razgovor (iz `_meta`) | Štiti sustav od svakog klijenta, ne samo od našeg agenta. Tuđi `conversationId` ne vrijedi: ključ uvijek sadrži kupca iz tokena. |
| **Bez konteksta razgovora nema radnje** (`CONVERSATION_REQUIRED`) | MCP klijent koji ne šalje `conversationId` i `turn` može čitati, ali ne može kupiti, vratiti ni blokirati — brana bez turna ne može znati je li kupac imao priliku odgovoriti. |
| **Audit je u sustavu** (`AuditedToolHandler` oko svakog MCP alata; turn bilježi `AgentChatService`) | Trag nastaje na granici sustava, neovisno o klijentu. Agent nema pristup tablici `audit_events`. |
| **Baza znanja kroz MCP** (`searchKnowledgeBase`) + Spring AI `RetrievalAugmentationAdvisor` s vlastitim `DocumentRetriever`-om | Agent nema bazu ni embedding model; dohvat ostaje determinističan (uvijek prije odgovora), a ne prepušten modelu. |
| **Alati za model i alati za aplikaciju agenta** | `whoAmI`, `getPendingAction` i `searchKnowledgeBase` zove kod agenta, ne model (`McpToolFilter` ih skriva od modela). Isti put, isti token, ista provjera. |
| **Shop razgovara sa sustavom, sustav s agentom** (`/api/agent/chat` prosljeđuje poruku) | Frontend se ne mijenja, agent nije izložen pregledniku, a sustav na svojoj granici vidi i bilježi svaki turn. |
| Sustav više nema model za razgovor (`spring.ai.model.chat: none`) | Sustavu od AI-ja ostaje samo embedding model za bazu znanja; razgovor je posao agenta. |

## 3. Sustav kao MCP server

Ovisnost `spring-ai-starter-mcp-server-webmvc`, konfiguracija u `application.yml`:

```yaml
spring.ai.mcp.server:
  name: nottrilix-system
  type: SYNC
  protocol: STATELESS               # HTTP bez sesije
  streamable-http.mcp-endpoint: /mcp
  annotation-scanner.enabled: false # alate registrira McpServerConfig, svaki s auditom
nottrilix.mcp:
  agent-key: ${NOTTRILIX_AGENT_KEY:nottrilix-demo-agent-key}
  confirmation-ttl-minutes: 10
```

Pristup (`SecurityConfig`): `/mcp` smiju uloge `CUSTOMER` i `AGENT`; bez tokena 401, admin 403.
Alat se izvršava na dretvi HTTP zahtjeva (Spring AI to uključuje za servlet okruženje), pa je prijava dostupna kroz `SecurityContextHolder` — `McpCall` iz nje čita kupca.

### Alati (14)

| Alat | Za koga | Oznaka | Potvrda kupca |
|---|---|---|---|
| `getCatalog`, `getMyPurchases`, `getMyWallet`, `getMyTickets` | model | čita | — |
| `checkVoucherAtProvider(voucherSerial)` | model | čita (vanjski pružatelj) | — |
| `purchaseVoucher(productId)`, `refundPurchase(transactionId)`, `blockVoucher(voucherSerial)` | model | **predlaže** | da |
| `confirmPendingAction()` | model | **izvršava** (`destructiveHint`) | — |
| `cancelPendingAction()` | model | odustajanje | — |
| `openSupportTicket(subject, description, transactionId?)` | model | mijenja | ne |
| `whoAmI()` | aplikacija agenta | čita | — |
| `getPendingAction()` | aplikacija agenta | čita | — |
| `searchKnowledgeBase(query)` | aplikacija agenta | čita | — |

### MCP je običan HTTP

```bash
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -H "Authorization: Bearer <token kupca>" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"purchaseVoucher",
       "arguments":{"productId":13},"_meta":{"conversationId":"demo-1","turn":1,"channel":"CHAT"}}}'
```

```json
{"jsonrpc":"2.0","id":1,"result":{"isError":false,"content":[{"type":"text","text":
  "{\"ok\":false,\"status\":\"CONFIRMATION_REQUIRED\",\"message\":\"Radnja NIJE izvršena, čeka potvrdu kupca: kupnja Xbox Gift Card 30 €, naplata 30 € s walleta (stanje 100 € -> 70 €). …\",\"data\":{\"productId\":13,…}}"}]}}
```

Rezultat alata je isti `ToolResult` (`ok`, `status`, `message`, `data`) kao u Fazi 3, samo zapakiran u MCP sadržaj.
`scripts\mcp_direct.py` prolazi sve granice ovako, bez agenta i bez modela.

## 4. Agent kao MCP klijent

Ovisnosti: `spring-ai-starter-model-openai`, `spring-ai-starter-mcp-client`, `spring-ai-rag`. Veza je konfiguracija:

```yaml
spring.ai.mcp.client:
  type: SYNC
  streamable-http.connections.nottrilix:
    url: http://localhost:8080
    endpoint: /mcp
```

Spring AI iz toga složi MCP klijenta i `SyncMcpToolCallbackProvider` (alati sustava kao Spring AI `ToolCallback`-ovi). `McpClientConfig` dodaje četiri stvari:

| Dodatak | Bean | Što radi |
|---|---|---|
| Token po pozivu | `McpClientCustomizer<McpClient.SyncSpec>` + `McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>` | MCP klijent je jedan, kupaca je mnogo: token za trenutni poziv (`CustomerToken`, `ScopedValue`) ide u `Authorization` zaglavlje; bez kupca ide ključ agenta |
| Kontekst razgovora | `ToolContextToMcpMetaConverter` | iz Spring AI `ToolContext`-a u MCP `_meta` prolaze samo `conversationId`, `turn`, `channel` |
| Imena alata | `McpToolNamePrefixGenerator.noPrefix()` | model vidi `purchaseVoucher`, ne ime s prefiksom veze |
| Alati za model | `McpToolFilter` | skriva `whoAmI`, `getPendingAction`, `searchKnowledgeBase` |

`ChatClient` je isti kao u Fazi 3 (system prompt po pozivu, memorija, RAG, alati), uz dvije razlike:

- alati su MCP `ToolCallback`-ovi omotani u `TracingToolCallback` (poziv u ime kupca, čist rezultat za model, trag turna, **kvar mreže ne ruši razgovor**);
- RAG je `RetrievalAugmentationAdvisor` čiji `DocumentRetriever` zove `searchKnowledgeBase` kroz MCP.

### Nalaz: RAG advisor i petlja poziva alata

U Spring AI 2.0 pozivanje alata je i samo advisor (`ToolCallingAdvisor`, redoslijed `HIGHEST_PRECEDENCE + 300`), a RAG advisori imaju zadani redoslijed `0` —
dakle su **unutar** petlje alata i izvršavaju se pri svakom pozivu modela u istom turnu. Turn s dva alata značio je tri pretrage baze znanja (i tri embedding poziva) za isto pitanje.
U Fazi 3 to se nije vidjelo; ovdje ga je pokazao audit na strani sustava (tri `KB_RETRIEVAL` retka za jedan turn).
Rješenje je jedan redak: `.order(ToolCallingAdvisor.DEFAULT_ORDER - 50)` — dohvat prije petlje, jednom po turnu (memorija razgovora je na `+200`, dakle još ranije).

## 5. Jedan turn u logu

Oba procesa pišu u istu konzolu; oznaka na početku retka kaže tko govori. Turn u kojem kupac potvrđuje kupnju (skraćeno):

```
[sustav] AGENT       : CHAT -> agent customer=2 (Marko Marić) conv=4b3467f9 channel=CHAT msg='Da, može.'
[sustav] MCP         : MCP tools/call whoAmI user=customer:marko.maric@example.com … -> OK (0 ms)
[sustav] MCP         : MCP tools/call getPendingAction user=customer:marko.maric@example.com conv=4b3467f9 turn=3 -> OK (0 ms)
[agent ] AGENT       : TURN 3 conv=4b3467f9 customer=2 (Marko Marić) channel=CHAT pending='kupnja HT bon 10 €, naplata 10 € s walleta (stanje 25 € -> 15 €)' msg='Da, može.'
[sustav] AUDIT       : KB_RETRIEVAL retrieve conv=4b3467f9 customer=2 input=Da, može. -> OK (139 ms) | 0 odlomaka iznad praga
[sustav] MCP         : MCP tools/call searchKnowledgeBase … conv=4b3467f9 turn=3 input={"query":"Da, može."} -> OK (144 ms)
[sustav] MCP         : CONFIRM PURCHASE 5 conv=c2:4b3467f9 predlozeno u turnu 2, potvrdjeno u turnu 3
[sustav] ShopService : PURCHASE request customer=2 product=5 channel=AGENT
[sustav] WalletService : WALLET DEBIT customer=2 -10 € (PURCHASE tx=7) -> 15 €
[sustav] ShopService : PURCHASE OK tx=7 customer=2 (Marko Marić) product=HT bon 10 € amount=10 € voucher=HT-10-001 channel=AGENT spentToday=10 € wallet=15 €
[sustav] MCP         : MCP tools/call confirmPendingAction user=customer:marko.maric@example.com conv=4b3467f9 turn=3 input={} -> OK (17 ms)
[sustav] AUDIT       : TOOL_CALL confirmPendingAction conv=4b3467f9 customer=2 input={} -> OK (17 ms) | … Potvrđena radnja: PURCHASE 5 (…)
[agent ] AGENT       : TOOL confirmPendingAction input={} -> OK (26 ms preko MCP-a)
[sustav] MCP         : MCP tools/call getPendingAction … conv=4b3467f9 turn=3 -> OK (0 ms)
[agent ] AGENT       : TURN 3 conv=4b3467f9 OK 1860 ms, alati=[confirmPendingAction:OK], izvori=0, tokeni=2527/67, ceka potvrdu=false
[sustav] AUDIT       : CHAT_TURN turn conv=4b3467f9 customer=2 input=Da, može. -> OK (1876 ms) | Kupnja HT bona od 10 € je uspješno izvršena. …
```

Isti alat je s dvije strane mreže izmjeren dvaput: 17 ms u sustavu, 26 ms gledano iz agenta.

## 6. API

| Metoda i putanja | Tko | Opis |
|---|---|---|
| `POST :8080/api/agent/chat` `{conversationId?, message}` | CUSTOMER | nepromijenjeno za shop; sustav prosljeđuje agentu i bilježi turn. 503 `AGENT_UNAVAILABLE` ako agent ne radi |
| `POST :8080/mcp` (JSON-RPC: `initialize`, `tools/list`, `tools/call`) | CUSTOMER, AGENT | MCP server sustava |
| `POST :8090/chat` `{conversationId?, message, channel?}` + `Authorization: Bearer <token kupca>` | sustav → agent | ulaz u agenta; 401 ako sustav ne prihvaća token, 503 `AGENT_UNAVAILABLE` / `SYSTEM_UNAVAILABLE` |

Odgovor razgovora je isti kao u Fazi 3 (`conversationId`, `reply`, `toolCalls`, `sources`, `pendingAction`, trajanje, tokeni), pa se frontend nije mijenjao.

## 7. Pokretanje

```powershell
.\run-phase-4.ps1            # build oba projekta, pa sustav, pa agent; oba u istom prozoru, Ctrl+C gasi oba
.\run-phase-4.ps1 -NoBuild -Reset
python scripts\agent_e2e.py  # razgovori A–H + audit (kroz shop API)
python scripts\mcp_direct.py # MCP bez agenta: granice sustava sirovim pozivima
```

`run-phase-1.ps1` je dobio prekidač `-WithAgent` (build i pokretanje agenta uz sustav); `run-phase-4.ps1` i `run-phase-3.ps1` ga zovu. `run-phase-1.ps1` i `run-phase-2.ps1` pokreću samo sustav (chat tada vraća 503).
Redoslijed je bitan samo pri startu: agent se pri pokretanju spaja na MCP server, pa sustav mora već raditi — skripta to čeka.

## 8. Što je dodano i promijenjeno

**Sustav (korijen repoa)**
- `pom.xml`: + `spring-ai-starter-mcp-server-webmvc`, − `spring-ai-vector-store-advisor`.
- Paket `com.nottrilix.mcp` (novo): `SupportMcpTools` (bivši `AgentTools`, sada `@McpTool`), `ConfirmationGate` i `ToolResult` (preseljeni iz agenta), `McpCall` (kupac iz prijave + razgovor iz `_meta`),
  `AuditedToolHandler`, `McpServerConfig`, `McpProperties`, `McpCallException`.
- Paket `com.nottrilix.agent` sada je samo veza prema agentu: `AgentClient` (HTTP), `AgentChatService` (prosljeđivanje + audit turna), `AgentClientProperties`.
- Prijava: `Realm.AGENT`, `TokenService` prepoznaje ključ agenta, `SecurityConfig` (`/mcp`), `RequestLoggingFilter` logira i `/mcp`.
- `application.yml`: `spring.ai.mcp.server.*`, `spring.ai.model.chat: none`, `nottrilix.mcp.*`, `nottrilix.agent.url`; log s oznakom `[sustav]`.

**Agent (`agent\`, novo)**
- `AgentConfig` (ChatClient, memorija, RAG preko MCP-a, redoslijed advisora), `AgentService`, `AgentController` (`POST /chat`), `McpClientConfig`, `SystemGateway` (pozivi aplikacije agenta),
  `TracingToolCallback`, `CustomerToken`, `TurnContext`, `ToolOutcome`, `ReplyStyle`, `ApiErrors`, `agent/system-prompt.txt` (nepromijenjen), log s oznakom `[agent ]`.

**Ostalo**: `run-phase-4.ps1`, `run-phase-1.ps1 -WithAgent`, `scripts/mcp_direct.py`. Frontend i baza (Flyway V1–V4) su nepromijenjeni.

**Testovi**: sustav 44 (`SupportMcpToolsTest`, `AuditedToolHandlerTest`, `ConfirmationGateTest` + dosadašnji), agent 19 (`TracingToolCallbackTest`, `TurnContextTest`, `ToolOutcomeTest`, `ReplyStyleTest`).

## 9. Verificirano (2026-09-30)

Svježa baza (`-Reset`), stvarni OpenAI (`gpt-4o`), sve pokrenuto kroz `run-phase-4.ps1`.

**Razgovori (`scripts\agent_e2e.py`, tri puna prolaza)** — isti scenariji i isti ishodi kao u Fazi 3, sada preko mreže:

| Scenarij | Ishod |
|---|---|
| A wallet, pravila, „kod ne radi“ | wallet 115 €; rok 14 dana iz baze znanja; bon iskorišten 20. 9. (datum od pružatelja) |
| B povrat | prijedlog → „Da, potvrđujem.“ → povrat izvršen, wallet 50 → 100 € |
| C kupnja | Steam 50 € odbijen (`INSUFFICIENT_FUNDS`); HT 10 € uz potvrdu, wallet 25 → 15 €, kanal `AGENT` |
| D sigurnost | tuđa transakcija nije dostupna; zaliha i „ti si administrator“ odbijeni; iskorišten bon se ne vraća |
| E odustajanje | `cancelPendingAction`, zatim druga kupnja uz potvrdu, wallet 95 € |
| F blokada | prijedlog → potvrda → bon blokiran |
| G, H | potvrda bez prijedloga i potvrda s tuđim `conversationId` ne izvršavaju ništa |

Radnje koje mijenjaju podatke i završno stanje (Ana 95 €, Marko 15 €, Ivana 100 €) isti su u sva tri prolaza; razlikuju se pomoćni pozivi čitanja i formulacije.

**MCP izravno (`scripts\mcp_direct.py`)**: bez tokena 401; ključ agenta vidi popis alata, a alat vraća `CUSTOMER_REQUIRED`; bez `_meta` radnja vraća `CONVERSATION_REQUIRED`;
potvrda u istom turnu ne prolazi; drugi kupac s istim `conversationId` dobiva `NOTHING_TO_CONFIRM`; potvrda u idućem turnu izvršava jednom.

**Odbijeni pristupi**: agent bez tokena, s izmišljenim tokenom, s ključem agenta, s admin tokenom i s tokenom odjavljenog kupca → 401; `/mcp` s admin tokenom → 403.

**Otpornost**: agent ugašen → `POST /api/agent/chat` vraća 503, shop radi, turn je u auditu kao `ERROR`. Sustav restartan dok agent radi → agent nastavlja bez restarta (nema sesije koju bi izgubio).

**Preglednik**: chat u shopu (prijedlog → gumbi potvrde → odustajanje), nepromijenjen frontend.

**Brojke** (27 turnova po prolazu): trajanje turna medijan 2,3–2,5 s (najbrži 0,9 s, najsporiji 5–9 s), ~3.000 ulaznih i ~50 izlaznih tokena po turnu; poziv alata preko MCP-a 10–50 ms (provjera kod pružatelja ~250 ms, simulirano),
dohvat iz baze znanja ~180 ms (embedding upita), jednom po turnu. Mreža između agenta i sustava dodaje nekoliko milisekundi po pozivu; vrijeme turna i dalje određuje model.

## 10. Pouke

| Što se pokazalo | Što to znači |
|---|---|
| Sve tri granice iz Faze 3 preselile su se **u sustav**, a agentu je ostao samo razgovor | Granica koja živi u klijentu nije granica. Čim postoji drugi klijent (ili drugi agent), mora biti na strani koja posjeduje podatke. |
| Identitet ne putuje kao podatak nego kao vjerodajnica | `customerId` u parametru ili `_meta` bio bi tvrdnja klijenta; token je nešto što sustav sam provjerava. |
| `_meta` je pravo mjesto za kontekst koji nije ni parametar alata ni identitet | Model ga ne vidi, shema alata ostaje čista, a server ga dobiva uz svaki poziv. |
| Audit na strani sustava pokazao je ono što u agentu nismo vidjeli | Ponovljeni dohvat iz baze znanja unutar petlje alata (poglavlje 4). |
| Alat s druge strane mreže može i ne odgovoriti | Omotač na strani agenta pretvara kvar u jasan „nije izvršeno“ za model, umjesto da sruši turn. |
| HTTP bez sesije pojednostavljuje rad | Nema stanja veze koje treba obnavljati; svaki poziv je samostalan i može se ponoviti `curl`-om. |

## 11. Poznata ograničenja

- **Ključ agenta je demo vrijednost u konfiguraciji** (kao i lozinka baze); za stvarnu upotrebu ide iz okoline (`NOTTRILIX_AGENT_KEY`) i HTTPS je obavezan — token i ključ putuju u zaglavlju.
- **Agentov `/chat` nije zaštićen sam po sebi**: tko ga može dosegnuti i ima valjan token kupca, može razgovarati u ime tog kupca. Zato je agent iza sustava, a ne pred preglednikom.
- **Sustav ne razlikuje našeg agenta od drugog MCP klijenta s tokenom kupca.** To je namjerno (sve granice vrijede jednako), ali audit ne bilježi koji je klijent zvao.
- **Turn broji agent.** Brana štiti od modela (koji `_meta` ne vidi), ne od zlonamjernog klijenta koji bi sam povećao turn — takav klijent ionako djeluje s tokenom kupca, dakle u njegovo ime.
- **Telefonski kanal nema token kupca**: pozivatelja će trebati prepoznati drukčije (broj, provjera identiteta) i agentu dati vjerodajnicu u njegovo ime. Pitanje za Fazu 6.
- Memorija razgovora i brojač turnova su u memoriji agenta, radnje koje čekaju potvrdu i tokeni u memoriji sustava: restart ih briše (audit je u bazi).
- MCP mogućnost *elicitation* (server traži potvrdu od korisnika kroz klijenta) bila bi protokolom propisan put za potvrdu, ali traži da turn čeka odgovor kupca; u razgovoru koji ide poruku po poruku to ne odgovara, pa je potvrda riješena stanjem na serveru.

## 12. Sljedeće (Faza 5 — glas)

Agentov ulaz je i dalje „tekst unutra, tekst van“. Glasovni kanal ispred njega stavlja prepoznavanje govora (STT) i sintezu (TTS); sustav, MCP i agent se ne mijenjaju.
