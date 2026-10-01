# From REST API to Voice Agent — nacrt prezentacije (JavaCro 2026)

_Nacrt: **2026-09-30** · slot **40 min** (plan: 35 min izlaganje + 5 min pitanja) · stanje koda: Faze 1–5 gotove (grana `phase5`), Faza 6 (telefon, Infobip) rezervirana._

> **Narativ:** polazište je postojeći Java sustav s poslovnim pravilima. Na njega se, fazu po fazu, dodaje agent: prvo znanje, pa ruke (alati), pa mreža, pa glas, pa telefon. Svaka faza ima jedno pitanje i jednu pouku, a agent između faza ostaje isti: **tekst unutra, tekst van**. Publika bi trebala otići s uvjerenjem da se to može i s njihovim sustavom.

## Raspodjela vremena

| # | Blok | min | Demo | Ključna rečenica |
|---|---|---|---|---|
| 0 | Hook, teza, mapa | 2 | — | „Lani sam pisao MCP servere. Danas ćemo sustav nazvati.“ |
| 1 | Polazište: sustav koji već imamo (Faza 1) | 3 | 30 s | „Sustav ima stav: pravila su u kodu, ne u modelu.“ |
| 2 | Znanje: baza znanja za RAG (Faza 2) | 3 | 30 s | „Dokumente pišemo za čitača koji čita odlomak po odlomak.“ |
| 3 | Agent: mozak i ruke (Faza 3) | 7 | 2 min | „Prompt nije jamstvo. Kod jest.“ |
| 4 | Mreža: sustav kao MCP server (Faza 4) | 5 | 1 min | „Granica koja živi u klijentu nije granica.“ |
| 5 | Glas i što ostaje iza razgovora (Faza 5) | 7 | 2,5 min | „Glas nije novi agent. Razgovor je zapis.“ |
| 6 | Telefon: poziv s mobitela (Faza 6, Infobip) | 6 | 2 min | „Telefonija je cijev za zvuk. Problem je tko zove.“ |
| 7 | Zaključak | 2 | — | „Pet faza, nula promjena mozga.“ |
| — | Pitanja | 5 | — | |

Ako organizator računa 40 min bez pitanja: blok 3 i 5 dobivaju po 2 min (više uživo, manje slajdova), blok 6 još 1 min.

---

## 0. Hook, teza, mapa (2 min)

- **Hook:** „Lani sam vam pokazao kako napisati vlastiti MCP server. Danas ću vam pokazati što se dogodi kad ga stavite ispred stvarnog sustava, pa ga nazovete telefonom.“
- **Teza:** agent je koristan tek kad je spojen na stvarni sustav, ograničen pravilima i auditabilan. Ovo nije chatbot demo.
- **Mapa (slajd):** šest faza kao stepenice: sustav → znanje → agent → mreža → glas → telefon. Ista slika se vraća na kraju svakog bloka s jednom stepenicom više.
- **Tehnološki podnožak (jedna linija, bez priče):** Java 25 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL 17 + pgvector · OpenAI · ElevenLabs · Infobip.

## 1. Polazište: sustav koji već imamo (3 min)

- **NotTrilix**: mala firma koja prodaje bonove (A1, HT, Steam, PlayStation, Xbox). Kupac ima wallet, kupuje bon, dobiva kod. Podrška rješava „kod ne radi“, povrate, blokade.
- **Ovo je danost, ne tema:** jedan Spring Boot (REST API + shop + admin), Postgres, Flyway, prijava s ulogama. Kao svaki vaš sustav, samo manji.
- **Pravila su u servisnom sloju** i to je najvažniji slajd cijele prezentacije: dnevni limit 150 €, povrat unutar 14 dana i samo ako bon nije iskorišten, kupac ne vidi zalihu, puni kod vidi samo vlasnik. Sve što agent kasnije radi prolazi kroz ta ista pravila.
- **Demo (30 s):** shop: kupnja s walleta uz potvrdu; admin: transakcije. „Sustav radi i ima stav.“
- **Pouka:** agent oko postojećeg sustava, ne novi sustav oko agenta.

## 2. Znanje: baza znanja za RAG (3 min)

- Call-centar zna politike: 10 kratkih markdown dokumenata (kupnja i limiti, iskorištavanje po pružatelju, kad kod ne radi, povrat, sigurnost, radno vrijeme, česta pitanja, rječnik, privatnost).
- **Napisani za RAG:** `##` odjeljak = jedan odlomak, brojke u tekstu su pravila sustava, svaki odlomak razumljiv sam za sebe. 85 odlomaka.
- **Spring AI:** `EmbeddingModel` (OpenAI `text-embedding-3-small`) + `VectorStore` (pgvector u istoj bazi kao poslovni podaci; nula nove infrastrukture). Ingest idempotentan po hashu datoteke, top-k 3, prag sličnosti 0,35.
- **Demo (30 s):** admin tab Znanje: „mogu li vratiti bon nakon 20 dana“ → odlomak o roku od 14 dana. Isto pitanje drukčije sročeno → isti odlomak.
- **Pouka:** RAG za demo nije RAG platforma. Vrijednost je u tome što dokumente pišu ljudi iz podrške, a agent ih čita.

## 3. Agent: mozak i ruke (7 min) ⭐

- **Spring AI `ChatClient`, jedan bean, četiri sastojka (slajd-dijagram):** system prompt (persona Nola, pravila ponašanja) · memorija po razgovoru · RAG advisor nad bazom znanja · alati (`@Tool` metode koje delegiraju u postojeće servise; kupac dolazi iz prijave kroz `ToolContext`, ne iz razgovora).
- **Kod na slajdu:** jedna `@Tool` metoda, tri retka delegacije. „Toliko koda treba da agent dobije ruku.“
- **Demo (2 min, chat u shopu, hrvatski):**
  1. „Koliko imam na walletu?“ → alat, odgovor s brojkom.
  2. „Moj Steam kod ne radi, kaže da je nevažeći.“ → kupnje + provjera kod pružatelja → „iskorišten 21. 9., povrat nije moguć“. Pravilo iz sustava, ne iz modela.
  3. „Kupi mi HT bon od 10 eura.“ → **prijedlog**, gumbi za potvrdu → „Da, potvrđujem.“ → izvršeno. Na ekranu paralelno audit trag (admin tab Audit).
- **Dvije pouke iz izgradnje (war stories, kratko):**
  - Memorija razgovora pamti samo tekst. U turnu potvrde model je **izmišljao id-eve**. Rješenje: sustav drži prijedlog radnje (`ConfirmationGate`), alat za potvrdu nema parametara, a potvrda vrijedi samo u idućem turnu. **Prompt nije jamstvo. Kod jest.**
  - Pravila u promptu („nemoj izmišljati datum“, „bez završnih fraza“) model je kršio u 1 od 10 slučajeva. Ono što mora vrijediti uvijek ide u kod: podatak s uputom, filter odgovora.
- **Audit od prvog dana:** svaki poziv alata i svaki turn je redak u tablici (tko, što, kada, ishod, trajanje). „Bez toga nema produkcije.“
- **🔀 Alternative (jedna rečenica):** model je konfiguracija (OpenAI, Anthropic, Azure, lokalni Ollama); izbor pao na `gpt-4o` zbog pouzdanog pozivanja alata i hrvatskog.

## 4. Mreža: sustav kao MCP server, agent kao zasebna aplikacija (5 min)

- **Motivacija:** „Alat u istom procesu ne možeš nazvati telefonom.“ Isti alati izlaze iz procesa: sustav postaje **MCP server** (`POST /mcp`, HTTP bez sesije), agent je zasebna aplikacija i **MCP klijent**. Krug s lanjskim talkom zatvoren.
- **Slajd „što od granica preživi mrežu“ (tablica, tri retka):**
  - tko je kupac: token kupca u zaglavlju **svakog** MCP poziva, sustav ga sam provjerava; model token nikad ne vidi;
  - potvrda radnje: brana **u sustavu**, agent u MCP `_meta` šalje samo `conversationId` i `turn`;
  - audit: omotač oko svakog MCP alata **u sustavu**; vrijedi za svaki klijent, agent ga ne može preskočiti.
- **Demo (1 min):** dva procesa u istoj konzoli (`[sustav]` / `[agent ]`), jedan turn s potvrdom: isti alat izmjeren s obje strane mreže (17 ms u sustavu, 26 ms iz agenta). Po volji: `curl` na `/mcp` bez tokena → 401, s ključem agenta → `CUSTOMER_REQUIRED`.
- **Nalaz za publiku (Spring AI 2.0):** RAG advisor je po zadanom redoslijedu **unutar petlje pozivanja alata** pa se dohvat ponavljao pri svakom pozivu modela; vidjelo se tek u auditu na strani sustava. Jedan redak: `.order(ToolCallingAdvisor.DEFAULT_ORDER - 50)`.
- **Pouke:** granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica, ne kao podatak; audit na strani koja posjeduje podatke.

## 5. Glas i što ostaje iza razgovora (7 min) ⭐

- **Glas je kanal, ne novi agent (slajd-pipeline):** 🎤 → STT (Spring AI `TranscriptionModel`, OpenAI) → **isti agent** → TTS (Spring AI `TextToSpeechModel`, ElevenLabs ili OpenAI, pružatelj = konfiguracija) → 🔊. Snimka se ne pohranjuje; u audit ide transkript.
- **Demo (1,5 min, mikrofon u shopu, hrvatski):** prvi put privola; klik, „Kupi mi HT bon od deset eura.“, snimanje stane samo → transkript odmah, Nola (glas „Maja“, `eleven_v4_turbo`) govori prijedlog ~0,2 s nakon odgovora → „Da, potvrđujem.“ → izvršeno. Log na ekranu: `VOICE STT … 486 ms`, `VOICE TTS ElevenLabs stream … prvi zvuk nakon 225 ms`. Ako model potraje, Nola kaže „Samo trenutak.“.
- **Nalaz (kratko, smiješno):** `language: hr` nije bio dovoljan: isti hrvatski zvuk model je vraćao kao srpsku ćirilicu, bugarski, poljski. Kratki prompt s rječnikom domene (brendovi, „wallet“, „potvrđujem“) riješio je sve uzorke. „STT-u dajte ono što dobije novi agent prvi dan: rječnik.“
- **Što ostaje iza razgovora (drugi dio bloka):**
  - audit trag iz Faze 4 već je **transkript**: KUPAC / ALAT / ZNANJE / AGENT po turnovima; ništa se ne bilježi dvaput;
  - **post-analiza**: utihnuli razgovor → pseudonimizacija (ime → `[KUPAC]`) → model → **strukturirani nalaz** (Spring AI structured output): tema, ishod, raspoloženje, ocjena agenta, prekršena pravila, **rupe u znanju**;
  - **samoučenje s čovjekom u petlji**: rupa u znanju → prijedlog odlomka → admin ispravi i odobri → odlomak u bazi znanja, agent ga koristi od idućeg pitanja.
- **Demo (1 min, admin):** tab Razgovori: nalaz glasovnog razgovora (Kupnja / Riješeno / Pozitivno, 5/5); razgovor u kojem je kupac pitao za R1 račun → rupa u znanju → tab Znanje: prijedlog s `PROVJERITI:` → odobri s pravim odgovorom → novi chat: agent zna. Trik: analiza uhvatila i razgovor s pokvarenim STT-om (Neriješeno, ocjena 3) prije nas.
- **GDPR u kodu (jedan slajd, bez pravnih tonova):** privola za glas s verzijom teksta (403 bez nje) · izvoz svih podataka JSON · brisanje razgovora na zahtjev · retencija po rasporedu (30 / 365 dana) · logovi bez e-maila · agent odgovara na „što čuvate o meni“ iz baze znanja. „Rok čuvanja je posao po rasporedu, izvoz je upit, brisanje je transakcija. Nije teško; teško je ne preskočiti.“
- **🔀 Alternative (jedna rečenica):** STT/TTS pružatelji su konfiguracija u Spring AI-ju (OpenAI, ElevenLabs, Azure); hrvatski TTS presudio za ElevenLabs.

## 6. Telefon: poziv s mobitela (6 min) — REZERVIRANO, dopuniti nakon Faze 6

_Blok se piše kad Faza 6 bude gotova. Ono što već znamo iz istraživanja i prošlogodišnjeg pokušaja (`../mcp-demo/INFOBIP_STATUS.md`, `PHONE_NUMBER_RESEARCH.md`, `TELEPHONY_RESEARCH.md`):_

- **Arhitektura (slajd):** mobitel → **Infobip** broj (+385 1 …) → webhook (javi se) → **WebSocket s PCM16 zvukom** u oba smjera ⇄ naš adapter: VAD (kad kupac govori) → STT → **isti agent** → TTS → natrag u poziv, s prekidanjem (barge-in). Telefonija je cijev za zvuk; agent, MCP i sustav se ne mijenjaju.
- **Pravi problem ove faze: tko zove?** Pozivatelj nema token kupca. Opcije za slajd: prepoznavanje po broju + kontrolno pitanje (npr. zadnje 4 znamenke koda ili iznos zadnje kupnje) → sustav agentu izda vjerodajnicu u ime kupca, s rokom i opsegom. Bez provjere: samo pitanja iz baze znanja, bez radnji na računu.
- **Demo (2 min, finale):** publika čuje: zvonjava → pozdrav → pitanje → Nola odgovara → kupnja uz potvrdu glasom. Log `TELEPHONY` paralelno na ekranu. Rezerva: Infobip **Call Link** (WebRTC iz preglednika, bez broja) i snimka.
- **War story (ispričati):** poziv odbijan s „The received request was rejected“ jer subscription u portalu nije bio vezan na Calls Configuration; propagacija „par minuta“; efemerni tunel URL koji svaki restart mijenja. Integracije su i to.
- **🔀 Zašto Infobip:** hrvatska firma (JavaCro kut), PCM16 na žici (bez transkodiranja), službeni ElevenLabs primjer, Call Link za demo bez broja. Alternativa Twilio (µ-law 8 kHz, bez hrvatskog u `<Say>`).
- **Što treba prije konferencije (organizacijski):** nadoplata Infobip računa (trial istekao ~29. 9.), kupnja HR broja (€8,71 + €8,71, `FORWARD_TO_SUBSCRIPTION` na postojeću konfiguraciju), stalni tunel ili javni host za webhookove, proba s mobitela.

## 7. Zaključak (2 min)

- **Rekapitulacija (jedan slajd, tablica po fazama):** kanal / tko je kupac / gdje su pravila / gdje je potvrda / gdje je audit. U svim retcima: pravila, potvrda i audit su u sustavu. Agent se od Faze 3 nije mijenjao: „**Pet faza, nula promjena mozga.**“
- **Pet pouka:**
  1. Agent oko postojećeg sustava; pravila ostaju u kodu, model bira među dopuštenim radnjama.
  2. Prompt nije jamstvo. Ono što mora vrijediti uvijek ide u kod (potvrda na serveru, filteri, podaci s uputom).
  3. Granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica.
  4. Audit trag je više od nadzora: transkript, analiza, izvoz kupcu, dokaz brisanja.
  5. Samoučenje da, ali s čovjekom koji odobrava; GDPR je najjednostavniji kad je kod.
- **Do produkcije nedostaje (iskrenost, jedan slajd):** HTTPS i sef za tajne, potpisani ugovori o obradi s pružateljima, streaming glas umjesto push-to-talk, NER umjesto regex pseudonimizacije, brisanje računa, potpisani webhookovi.
- **CTA:** repo (QR), lanjski talk + ovaj = cijela MCP priča, pitanja.

---

## Kod uz svaki blok: što otvoriti i što reći

Načelo: po bloku najviše dva do tri mjesta u kodu, svako stane na jedan ekran (do 25 redaka); ostalo je dijagram na slajdu.
Brojevi redaka su s grane `phase5` (sve faze u konačnom obliku). Za „kako je izgledalo u toj fazi“ služe grane `phase1`–`phase4`;
najlakše je unaprijed napraviti worktree po grani i držati ih otvorene u IDE-u:

```powershell
git worktree add ..\NotTrilix2026-phase3 phase3     # isto za phase1, phase2, phase4
```

**Opseg po fazama** (slajd „koliko je koda trebalo“, iz `git diff --shortstat`, bez package-lock datoteka):

| Faza | Datoteka promijenjeno | Redaka dodano | Novih `.java` | Napomena |
|---|---|---|---|---|
| 1 sustav | 67 | 3.052 | sve | REST + shop + admin + baza |
| 2 znanje | 34 | 1.504 | 12 | 9 md dokumenata + ingest + pretraga |
| 3 agent | 54 | 2.981 | 30 | ChatClient, alati, potvrda, audit, chat |
| 4 mreža | 67 | 2.898 (906 obrisano) | 29 | selidba agenta u `agent\` |
| 5 glas, analiza, GDPR | 85 | 4.378 | 46 | najveća faza: tri teme |

### Spring AI po fazama: što je iz Spring AI-ja, što je naše

Za svaki blok jedna rečenica na slajdu: „Spring AI je dao X, mi smo dodali Y.“ Ovisnosti su u `pom.xml` (sustav) i `agent/pom.xml` (agent), verzija 2.0.0 kroz `spring-ai-bom`.

| Faza | Spring AI dio (klasa / anotacija) | Ovisnost | Gdje u kodu | Što je naše oko toga |
|---|---|---|---|---|
| 2 znanje | `EmbeddingModel` → `OpenAiEmbeddingModel` (`spring.ai.openai.embedding.options.model: text-embedding-3-small`); nikad ga ne zovemo izravno, zove ga vector store | `spring-ai-starter-model-openai` | `application.yml:9–11` | — |
| 2 znanje | `VectorStore` → `PgVectorStore` (`initialize-schema: false`, tablica iz Flyway V3), `Document` (sadržaj + metapodaci `source`, `title`, `file_hash`), `vectorStore.add(...)`, `similaritySearch(SearchRequest…topK…similarityThreshold)`, `FilterExpressionBuilder` za brisanje po izvoru | `spring-ai-starter-vector-store-pgvector` | `kb/KnowledgeBaseIngestor.java:125,176`, `kb/KnowledgeBaseService.java:37–42` | parser markdowna (`##` = odlomak), hash datoteke, admin pretraga |
| 3 agent | `ChatClient` + autokonfigurirani `ChatClient.Builder` (`gpt-4o`): `.prompt().system(…).user(…).toolContext(…).call().chatClientResponse()`; `PromptTemplate` za system prompt s parametrima | `spring-ai-starter-model-openai` | grana `phase3`: `agent/AgentConfig.java`, `AgentService.java`; danas `agent/…/AgentConfig.java:83–108` | persona i pravila u `system-prompt.txt` |
| 3 agent | `ChatMemory` = `MessageWindowChatMemory` + `InMemoryChatMemoryRepository`; `MessageChatMemoryAdvisor` (memorija po `conversationId`) | isto | `AgentConfig.java:93` | brojač turnova, `conversationId` = razgovor u shopu |
| 3 agent | `QuestionAnswerAdvisor` nad `VectorStore` (RAG u jednom advisoru; u Fazi 4 zamijenjen `RetrievalAugmentationAdvisor`-om) | `spring-ai-vector-store-advisor` (samo grana `phase3`) | grana `phase3`: `AgentConfig.java:60` | — |
| 3 agent | `@Tool` / `@ToolParam` + `ToolCallbacks.from(...)`; `ToolContext` (kupac iz prijave, ne iz modela); `ToolCallback` kao omotač za audit | isto | grana `phase3`: `AgentTools.java:128–147`, `AuditingToolCallback.java` | `ToolResult` (ok / status / message / data), `ConfirmationGate`, audit |
| 3 agent | `ChatClientResponse` → `ChatResponse.getMetadata().getUsage()` (tokeni po turnu u audit) | isto | `agent/…/AgentService.java` | audit trag |
| 4 mreža (sustav) | `@McpTool`, `@McpToolParam`, `McpMeta` (kontekst iz `_meta`), `McpTool.McpAnnotations` (`readOnlyHint`, `destructiveHint`), `SyncMcpAnnotationProviders.statelessToolSpecifications(...)`; `spring.ai.mcp.server.protocol: STATELESS`, `streamable-http.mcp-endpoint: /mcp` | `spring-ai-starter-mcp-server-webmvc` (+ MCP Java SDK: `McpStatelessServerFeatures.SyncToolSpecification`, `McpSchema.CallToolRequest/CallToolResult`, `McpTransportContext`) | `mcp/SupportMcpTools.java:156–306`, `mcp/McpServerConfig.java:25–28`, `application.yml:23–30` | `McpCall` (kupac iz Spring Security konteksta + `_meta`), `AuditedToolHandler`, `ConfirmationGate` u sustavu |
| 4 mreža (agent) | `SyncMcpToolCallbackProvider` (alati sustava kao `ToolCallback`-ovi), `McpClientCustomizer<McpClient.SyncSpec>` i `McpClientCustomizer<HttpClientStreamableHttpTransport.Builder>` (token u zaglavlje po pozivu), `ToolContextToMcpMetaConverter` (`_meta`), `McpToolFilter`, `McpToolNamePrefixGenerator.noPrefix()`; `spring.ai.mcp.client.streamable-http.connections.*` | `spring-ai-starter-mcp-client` (+ SDK `McpSyncClient`, `HttpClientStreamableHttpTransport`) | `agent/…/McpClientConfig.java:36–61`, `agent/…/SystemGateway.java` | `CustomerToken` (`ScopedValue`), `TracingToolCallback` (kvar mreže → jasan ishod za model), `TurnContext` |
| 4 mreža (agent) | `RetrievalAugmentationAdvisor` + vlastiti `DocumentRetriever` + `ContextualQueryAugmenter` (`allowEmptyContext`), `Query`; `ToolCallingAdvisor.DEFAULT_ORDER` za redoslijed | `spring-ai-rag` | `agent/…/AgentConfig.java:69, 94–98` | dohvat ide kroz MCP alat `searchKnowledgeBase`; nalaz o ponovljenom dohvatu |
| 5 glas | `TranscriptionModel` → `OpenAiAudioTranscriptionModel`; `AudioTranscriptionPrompt(Resource)`, `AudioTranscriptionResponse`; opcije `spring.ai.openai.audio.transcription.options.{model, language, prompt}` | `spring-ai-starter-model-openai` | `agent/…/VoiceService.java:83–93`, `agent/…/application.yml:14–21` | domenski prompt (rječnik), naziv datoteke = format snimke |
| 5 glas | `TextToSpeechModel` → `ElevenLabsTextToSpeechModel` ili `OpenAiAudioSpeechModel`, izbor kroz `spring.ai.model.audio.speech`; `TextToSpeechPrompt`, `TextToSpeechResponse`; **`stream()`** daje `Flux` chunkova mp3 koje agent odmah prosljeđuje (glas v2) | `spring-ai-starter-model-elevenlabs` (+ OpenAI starter) | `agent/…/VoiceService.java` (`turnStreaming`), `agent/…/application.yml` (elevenlabs: `eleven_v4_turbo`, Maja, `language-code: hr`) | `SpeechText` (iznosi riječima), streaming protokol, „Samo trenutak.“, privola prije poziva, snimka se ne čuva |
| 5 analiza | **Structured output**: `BeanOutputConverter<ConversationInsight>` (`getFormat()` u prompt, `convert()` iz odgovora); drugi `ChatClient` bean u sustavu (`analysisChatClient`); `Usage` za tokene analize | `spring-ai-starter-model-openai` (sustav opet ima chat model) | `analysis/ConversationAnalyzer.java:96–120`, `analysis/AnalysisConfig.java`, `analysis/ConversationInsight.java` | pseudonimizirani transkript, raspored, prijedlozi za bazu znanja |
| 5 učenje | isti `VectorStore.add(Document)` kao u Fazi 2, metapodatak `source = nauceno-N` | pgvector starter | `kb/KnowledgeBaseIngestor.java:151–176` | odobravanje s ispravkom, ponovni ingest pri resetu |
| 6 telefon (plan) | ništa novo iz Spring AI-ja: isti `ChatClient`, `TextToSpeechModel.stream()`; WebSocket, VAD i telefonija su obična Java (Spring WebSocket) | — | `agent/…/telephony/` | „Gdje apstrakcija ne seže, obična Java.“ |

**Za zaključni slajd.** Spring AI je dao: `ChatClient` s advisorima (memorija, RAG), alate (`@Tool`, `ToolCallback`, `ToolContext`), MCP server (`@McpTool`) i MCP klijent (`McpClientCustomizer`, `SyncMcpToolCallbackProvider`), `EmbeddingModel` + `VectorStore`, `TranscriptionModel` + `TextToSpeechModel`, structured output (`BeanOutputConverter`).
Naše je: poslovna pravila, brana potvrde, audit, identitet kroz token, transkript iz audita, pseudonimizacija, GDPR, samoučenje s odobrenjem. Dva stupca na jednom slajdu.

### Blok 1 (Faza 1): pravila su u kodu

_Spring AI: ništa. Namjerno: prvo sustav s pravilima, bez ijednog AI dijela._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „pravila u servisima“ | `src/main/java/com/nottrilix/service/ShopService.java:81` (`purchase`), `:150–153` | dnevni limit → `deny("PURCHASE", "DAILY_LIMIT_EXCEEDED", …)`, zaključana zaliha, naplata s walleta, sve u jednoj transakciji | „Ovo je vlasnik istine. Sve što agent kasnije radi prolazi kroz ovu metodu.“ |
| isti slajd | `service/WalletService.java:85–92` | `INSUFFICIENT_FUNDS`, `debitForPurchase` | „Novac se ne troši u modelu.“ |
| slajd „uloge“ | `config/SecurityConfig.java:58–63` | `/api/shop/**` CUSTOMER, `/api/admin/**` ADMIN (redak 62 s `/mcp` spomenuti tek u bloku 4) | „Tko što smije određuje sustav, ne razgovor.“ |

Ne otvarati: kontrolere, DTO-e, React.

### Blok 2 (Faza 2): znanje

_Spring AI: `EmbeddingModel` (OpenAI) + `VectorStore` (pgvector), `Document`, `SearchRequest`; starteri `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „dokumenti pisani za RAG“ | `src/main/resources/kb/02-kupnja-wallet-i-limiti.md` | `##` odjeljci, brojke iz sustava (150 €, 14 dana), svaki odlomak samostalan | „Pisano za čitača koji vidi jedan odlomak.“ |
| slajd „ingest i pretraga“ | `kb/KbMarkdownParser.java:37` | `split("(?m)^## ")`: odlomak = chunk, jedan redak | |
| isti slajd | `kb/KnowledgeBaseIngestor.java:93–125` | hash datoteke → preskoči / zamijeni → `vectorStore.add(documents)` | „Spring AI VectorStore u tri retka, idempotentno po hashu.“ |
| isti slajd | `kb/KnowledgeBaseService.java:37–42` | `SearchRequest` s top-k i pragom | |
| konfiguracija | `src/main/resources/application.yml:9–17, 77` | embedding model, pgvector, prag 0,35 | „Pružatelj je konfiguracija.“ |
| baza | `db/migration/V3__vector_store.sql:8` | `vector_store` u istoj bazi kao kupci i kupnje | „Nula nove infrastrukture.“ |

### Blok 3 (Faza 3): agent

_Spring AI: `ChatClient`, `ChatMemory` + `MessageChatMemoryAdvisor`, `QuestionAnswerAdvisor` (RAG), `@Tool` + `ToolContext` + `ToolCallback`, `Usage`._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „četiri sastojka“ | `agent/src/main/java/com/nottrilix/agent/AgentConfig.java:83–108` | jedan `ChatClient` bean: system prompt, `MessageChatMemoryAdvisor`, `RetrievalAugmentationAdvisor`, `defaultToolCallbacks` (u Fazi 3 `QuestionAnswerAdvisor` nad vector storeom: grana `phase3`, `AgentConfig.java:59–60`) | „Jedan bean, četiri sastojka.“ |
| isti slajd | `agent/src/main/resources/agent/system-prompt.txt` | persona Nola, pravila ponašanja; redak 6 kanal VOICE spomenuti tek u bloku 5 | „Prompt kaže kako. Smije li, kaže kod.“ |
| slajd „`@Tool` u tri retka“ | grana `phase3`: `src/main/java/com/nottrilix/agent/AgentTools.java:136–147` (`getMyWallet(ToolContext ctx)`); danas `src/main/java/com/nottrilix/mcp/SupportMcpTools.java:181–186` | kupac iz `ToolContext` odnosno `McpMeta`, delegacija u postojeći servis | „Toliko koda treba da agent dobije ruku.“ |
| slajd „war story: izmišljeni id-evi“ | `mcp/SupportMcpTools.java:237–248` (`purchaseVoucher` → `gate.confirmed(…)` → `CONFIRMATION_REQUIRED`), `:306` (`confirmPendingAction` bez parametara), `mcp/ConfirmationGate.java:29–60` (`PendingAction`, potvrda vrijedi samo u idućem turnu) | „Model je izmišljao id-eve. Sad ih ne treba: prijedlog drži server.“ |
| isti slajd | `agent/…/ReplyStyle.java:13–26`, `mcp/SupportMcpTools.java:67` (`REDEMPTION_DATE_HINT`) | završne fraze filtrirane kodom; datum iskorištenja stiže s uputom uz podatak | „Pravilo koje mora vrijediti uvijek postaje kod.“ |
| slajd „audit“ | `db/migration/V4__audit_events.sql:4`, `mcp/AuditedToolHandler.java:48–80` | omotač oko alata: ulaz, ishod, trajanje → redak u `audit_events` | „Bez ovoga nema produkcije.“ (demo prati admin tab Audit) |

### Blok 4 (Faza 4): mreža

_Spring AI: MCP server (`@McpTool`, `McpMeta`, `SyncMcpAnnotationProviders`, starter `mcp-server-webmvc`), MCP klijent (`SyncMcpToolCallbackProvider`, `McpClientCustomizer`, `ToolContextToMcpMetaConverter`, `McpToolFilter`, starter `mcp-client`), `spring-ai-rag` (`RetrievalAugmentationAdvisor`, `DocumentRetriever`), `ToolCallingAdvisor.DEFAULT_ORDER`._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „dvije aplikacije“ | `src/main/resources/application.yml:23–30` i `agent/src/main/resources/application.yml` (`spring.ai.mcp.client.streamable-http.connections`) | server `STATELESS` na `/mcp`; klijent = URL | „MCP je konfiguracija; alati su isti.“ |
| isti slajd | `mcp/McpServerConfig.java:25–28` | `statelessToolSpecifications` + svaki alat omotan `AuditedToolHandler`-om pri registraciji | „Audit se ne može zaobići: nema alata bez omotača.“ |
| slajd „tko je kupac“ | `agent/…/McpClientConfig.java:43–46`, `agent/…/CustomerToken.java:13–23`, `mcp/McpCall.java:27–36`, `auth/TokenService.java:79` | token kupca u `Authorization` po pozivu (`ScopedValue`); sustav ga čita iz `SecurityContextHolder`; bez kupca `CUSTOMER_REQUIRED`; ključ agenta samo za spajanje | „Identitet putuje kao vjerodajnica, ne kao podatak.“ |
| slajd „razgovor u `_meta`“ | `agent/…/McpClientConfig.java:51`, `mcp/McpCall.java:58–72` | `ToolContextToMcpMetaConverter` pušta samo `conversationId`, `turn`, `channel`; bez njih `CONVERSATION_REQUIRED` | „Model `_meta` ne vidi.“ |
| isti slajd | `config/SecurityConfig.java:62` | `/mcp` za CUSTOMER i AGENT | |
| slajd „nalaz o redoslijedu advisora“ | `agent/…/AgentConfig.java:69, 94–98` | `DocumentRetriever` preko MCP-a; `.order(ToolCallingAdvisor.DEFAULT_ORDER - 50)` | „Jedan redak, dva dohvata manje po turnu.“ |
| demo | `scripts/mcp_direct.py` | isti `POST /mcp` bez tokena (401), s ključem agenta (`CUSTOMER_REQUIRED`), s tokenom kupca | |

### Blok 5 (Faza 5): glas, razgovor kao zapis, GDPR

_Spring AI: `TranscriptionModel` (OpenAI STT), `TextToSpeechModel` (ElevenLabs / OpenAI, izbor `spring.ai.model.audio.speech`), structured output `BeanOutputConverter`, drugi `ChatClient` za analizu, isti `VectorStore` za naučene odlomke. GDPR dio je bez Spring AI-ja: JPA, raspored, regex._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „glas kao kanal“ | `agent/src/main/java/com/nottrilix/agent/VoiceService.java:58–74` | `turn`: `transcribe` → `agent.chat(…, "VOICE")` → `speak`; redci 90 i 99: `stt.call(new AudioTranscriptionPrompt)`, `tts.call(new TextToSpeechPrompt)` | „Glas je omotač; agent isti.“ |
| isti slajd | `agent/src/main/resources/application.yml:13–37` | STT model + **prompt** (redak 19), ElevenLabs, prekidač `spring.ai.model.audio.speech` | „Pružatelj je konfiguracija, prompt je rječnik.“ |
| slajd „privola“ | `src/main/java/com/nottrilix/agent/AgentChatService.java:59–61` | `privacy.requireConsent(…)` prije glasa → 403 | |
| slajd „razgovor kao zapis“ | `service/ConversationService.java:98–120` | transkript iz audit redaka: `TOOL_CALL`, `KB_RETRIEVAL`, `CHAT_TURN` | „Ništa novo ne bilježimo.“ |
| isti slajd | `privacy/Pseudonymizer.java:27–37`, `analysis/ConversationAnalyzer.java:44` (prompt), `:96–120` (`asText(true)` → `converter.getFormat()` → `converter.convert`), `analysis/ConversationInsight.java:13–25` | pseudonimizacija prije modela; structured output u record | „Nalaz je record, ne prepričavanje.“ |
| slajd „samoučenje“ | `analysis/ConversationAnalyzer.java:160` (`propose`), `kb/KnowledgeLearningService.java:54–62` (`approve` → `ingestLearned`), `kb/KnowledgeBaseIngestor.java:151` | rupa u znanju → prijedlog → admin → odlomak `nauceno-N` | „Model predlaže, čovjek odobrava.“ |
| isti slajd | `analysis/AnalysisScheduler.java:38–55` | raspored: utihnuli razgovori, svaki svoja transakcija | |
| slajd „GDPR u kodu“ | `privacy/PrivacyService.java:82` (privola s verzijom), `:135` (izvoz), `:164–167` (brisanje), `privacy/RetentionJob.java:60–70`, `db/migration/V5__conversations_privacy.sql:54` (`consents`) | privola = redak s verzijom; izvoz = upit; brisanje = transakcija; rok = posao po rasporedu | „Nije teško; teško je ne preskočiti.“ |
| isti slajd | `src/main/resources/kb/10-privatnost-i-osobni-podaci.md` | agent na „što čuvate o meni“ odgovara iz baze znanja | |
| preglednik (po volji) | `frontend/src/customer/ChatWidget.tsx:97–122` | `MediaRecorder` → multipart → reprodukcija mp3 | |

### Blok 6 (Faza 6): dopuniti kad bude gotova

_Spring AI: ništa novo; telefonija je obična Java oko istog agenta._

Planirano: paket `agent/…/telephony/` (webhook „javi se“, WebSocket handler: frame → VAD → STT → **isti `AgentService`** → TTS → natrag u poziv), servis u sustavu koji pozivatelju nakon provjere izdaje vjerodajnicu za agenta, `run-phase-6.ps1`. Pokazati tri mjesta: webhook, WS handler s pozivom istog agenta, izdavanje vjerodajnice.

### Tehnika

- IDE s fontom ≥ 18, tabovi unaprijed otvoreni po redu blokova (bookmarki), bez skrolanja tijekom pokazivanja.
- „Što je faza dodala“ = `git diff --stat phase3 phase4` uživo ili tablica gore; worktree po grani za usporedbu jedne datoteke (npr. `AgentTools.java` u `phase3` uz `SupportMcpTools.java` u `phase5`).
- Ne otvarati: kontrolere, DTO-e, testove, React osim `ChatWidget`; migracije samo kao „jedna tablica po fazi“ (V3 znanje, V4 audit, V5 razgovori i privole).

## Popis slajdova (radni, ~26)

1. Naslov · 2. Lani / danas (hook) · 3. Teza · 4. Mapa šest faza
5. NotTrilix: što prodaje, tko koristi · 6. Sustav: jedan Spring Boot, pravila u servisima (kod) · 7. Demo 1
8. Baza znanja: dokumenti pisani za RAG · 9. Spring AI ingest + pretraga (kod/konfiguracija) · 10. Demo 2
11. ChatClient: četiri sastojka (dijagram) · 12. `@Tool` u tri retka · 13. Demo 3 · 14. War story: izmišljeni id-evi → potvrda na serveru · 15. Audit
16. Mreža: dvije aplikacije (dijagram) · 17. Što od granica preživi mrežu (tablica) · 18. Demo 4 + nalaz o redoslijedu advisora
19. Glas kao kanal (pipeline) · 20. Demo 5 + STT nalaz · 21. Razgovor kao zapis: transkript → nalaz → prijedlog (dijagram) · 22. Demo 6 · 23. GDPR u kodu
24. Telefon: arhitektura (Faza 6) · 25. Tko zove? · 26. Demo 7 (finale) + war story
27. Rekapitulacija: pet faza, nula promjena mozga · 28. Pouke · 29. Do produkcije · 30. Hvala / QR

## Demo plan i rezerve

| Demo | Priprema | Rizik | Rezerva |
|---|---|---|---|
| Svi | `.\run-phase-5.ps1 -NoBuild -Reset` prije izlaska; ključevi (`openai.key`, `elevenlabs.key`); dva prozora: shop + admin; konzola s logom vidljiva | internet u dvorani, latencija modela (turn 1–5 s, glas 5–8 s) | snimka svakog demo beata (video), `scripts/agent_e2e.py` i `scripts/phase5_e2e.py` kao proba dan prije |
| 3 (chat) | Marko: wallet 25 € (HT bon 10 € prolazi, Steam 50 € pada na pravilu) | model drukčije sroči odgovor | sve što je bitno je u alatima i auditu, ne u formulaciji |
| 5 (glas) | mikrofon u pregledniku dopušten, privola dana unaprijed ili uživo (bolje uživo: 10 s) | buka u dvorani, STT | tekstom u isti chat; snimka |
| 6 (analiza) | jedan razgovor unaprijed analiziran (ne čekati raspored od 2 min), gumb „Analiziraj“ za uživo | analiza 2–8 s | prethodno analizirani razgovori u tabu |
| 7 (telefon) | Infobip broj, tunel, probni poziv isto jutro | sve (mreža, mobilna, propagacija) | Call Link iz preglednika; snimka poziva |

Prije izlaska: `-Reset` briše i odobrene naučene odlomke; ako demo 6 treba već naučen odlomak, pripremiti ga nakon reseta.

## Što još treba napraviti do konferencije

- [ ] Faza 6 (Infobip): adapter (webhook + WebSocket + VAD + streaming), identitet pozivatelja, `run-phase-6.ps1`, `docs/PHASE6.md`; dopuniti blok 6 i slajdove 24–26.
- [ ] Infobip: nadoplata, HR broj, usmjeravanje na Calls Configuration, stalni tunel; proba s mobitela.
- [ ] Deck iz ovog nacrta (lanjski `../mcp-demo/presentation/build_pptx.py` može poslužiti kao predložak) + dijagrami iz `docs/PHASE3–5.md`.
- [ ] Snimke demo beatova (video) kao rezerva; jedna „pretpripremljena“ baza s analiziranim razgovorima.
- [ ] Generalna proba s mjerenjem vremena: cilj 33 min bez pitanja, 3 min rezerve za demo koji zapne.
