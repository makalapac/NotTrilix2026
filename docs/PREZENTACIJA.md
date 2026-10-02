# From REST API to Voice Agent — nacrt prezentacije (JavaCro 2026)

_Nacrt **v2: 2026-10-02** (v1: 2026-09-30) · slot **40 min** (plan: 35 min izlaganje + 5 min pitanja) · stanje koda: Faze 1–5 gotove (grana `phase5`), Faza 6 (telefon, Infobip) **u izradi na grani `phase6`**._

> **Jezik.** Predavanje je **na engleskom** (slajdovi i bilješke za govornika u decku su engleski). Demo aplikacija govori hrvatski (Nola, UI, baza znanja), pa se ono što se u demou tipka ili izgovara na slajdovima navodi na hrvatskom s prijevodom u zagradi. Ovaj nacrt ostaje na hrvatskom kao interni radni dokument.

> **Narativ.** Na početku jasno kažem dvije stvari: (1) sustav je **namjerno pojednostavljen** radi demo efekta, ali ono što agent dira (pravila, prijava, transakcije, audit) nije; (2) cilj predavanja je **proći kroz faze izgradnje agentske aplikacije** oko postojećeg Java sustava: znanje i kontekst (RAG) → chatbot → alati i agentske mogućnosti → MCP → glas → telefon. Svaka faza ima jedno pitanje, jedan demo i jednu pouku, a agent između faza ostaje isti: **tekst unutra, tekst van**. Publika bi trebala otići s uvjerenjem da isti put može proći i sa svojim sustavom.

## Raspodjela vremena

| # | Blok | Faza u repou | min | Demo | Ključna rečenica |
|---|---|---|---|---|---|
| 0 | Uvod: hook, teza, **okvir „pojednostavljeno namjerno“**, mapa faza | — | 3 | — | „Pojednostavio sam sve oko agenta. Ništa u agentu.“ |
| 1 | Polazište: sustav koji već imamo | 1 | 2,5 | 30 s | „Sustav ima stav: pravila su u kodu, ne u modelu.“ |
| 2 | Znanje i kontekst: baza znanja za RAG | 2 | 3 | 30 s | „Dokumente pišemo za čitača koji vidi jedan odlomak.“ |
| 3 | Chatbot: model + prompt + memorija + znanje | 3 (bez alata) | 3,5 | 1 min | „Chatbot zna politiku. Ne zna kupca.“ |
| 4 | Alati i agentske mogućnosti ⭐ | 3 | 6 | 2 min | „Prompt nije jamstvo. Kod jest.“ |
| 5 | MCP: sustav kao MCP server, agent kao zasebna aplikacija | 4 | 4,5 | 1 min | „Granica koja živi u klijentu nije granica.“ |
| 6 | Glas, i što ostaje iza razgovora | 5 | 6 | 2 min | „Glas nije novi agent. Razgovor je zapis.“ |
| 7 | Telefon: poziv s mobitela (Infobip) | 6 | 5 | 2 min | „Telefonija je cijev za zvuk. Pravi problem je tko zove.“ |
| 8 | Zaključak | — | 2 | — | „Od chatbota do telefona: isti mozak.“ |
| — | Pitanja | | 5 | | |

Zbroj izlaganja: 35,5 min. Ako organizator računa 40 min bez pitanja: blok 4 i 6 dobivaju po 1,5 min (više uživo), blok 7 još 1 min. Ako demo zapne, prvo se krati drugi dio bloka 6 (analiza, GDPR → jedan slajd), pa blok 2.

---

## 0. Uvod: hook, teza, okvir, mapa (3 min)

- **Hook:** „Lani sam vam pokazao kako napisati vlastiti MCP server. Danas ću vam pokazati što se dogodi kad ga stavite ispred stvarnog sustava, pa ga nazovete telefonom.“
- **Teza (slajd):** agent je koristan tek kad je spojen na stvarni sustav, ograničen pravilima i auditabilan. Ovo nije chatbot demo. Chatbot je tek jedna stepenica.
- **Okvir „pojednostavljeno namjerno“ (slajd, dva stupca):** ovo je **pojednostavljena verzija** sustava, da bi demo stao u 40 minuta i da bi se svaki korak vidio na ekranu.

  | Pojednostavljeno (oko agenta) | Nije pojednostavljeno (ono što agent dira) |
  |---|---|
  | jedna Spring Boot aplikacija nosi API, shop i admin | poslovna pravila u servisnom sloju, jedna istina za sve kanale |
  | wallet umjesto kartice i pravog PSP-a (nadoplata je simulirana) | prijava, uloge, bearer token; kupac radi samo sa svojim podacima |
  | pružatelj (A1, HT, Steam…) je simuliran iza sučelja | transakcije i zaključavanje u bazi (dva kupca ne dobiju isti bon) |
  | 8 bonova po proizvodu, 3 kupca u seedu | audit trag svakog poziva alata i svakog turna |
  | tokeni u memoriji, bez HTTPS-a na pozornici | privola, izvoz, brisanje, retencija (GDPR) |

  Rečenica: „Sve što je pojednostavljeno je *oko* agenta. Ništa što je pojednostavljeno nije *u* agentu. Vaš sustav je veći, ali su koraci isti.“
- **Mapa faza izgradnje (slajd, stepenice):** polazište je sustav koji već imamo; na njega dolazi **znanje i kontekst (RAG)** → **chatbot** → **alati i agentske mogućnosti** → **MCP** → **glas** → **telefon**. Ista slika se vraća na kraju svakog bloka s jednom stepenicom više. Svaka stepenica = jedno pitanje + jedna pouka.
- **Tehnološki podnožak (jedna linija, bez priče):** Java 25 · Spring Boot 4.1 · Spring AI 2.0 · PostgreSQL 17 + pgvector · OpenAI · ElevenLabs · Infobip.

## 1. Polazište: sustav koji već imamo (2,5 min)

- **NotTrilix**: mala firma koja prodaje bonove (A1, HT, Steam, PlayStation, Xbox). Kupac ima wallet, kupuje bon, dobiva kod. Podrška rješava „kod ne radi“, povrate, blokade.
- **Ovo je danost, ne tema:** REST API + shop + admin, Postgres, Flyway, prijava s ulogama. Kao svaki vaš sustav, samo manji (vidi okvir iz uvoda).
- **Pravila su u servisnom sloju** i to je najvažniji slajd cijele prezentacije: dnevni limit 150 €, povrat unutar 14 dana i samo ako bon nije iskorišten, kupac ne vidi zalihu, puni kod vidi samo vlasnik. Sve što agent kasnije radi prolazi kroz ta ista pravila.
- **Demo (30 s):** shop: kupnja s walleta uz potvrdu; admin: transakcije. „Sustav radi i ima stav.“
- **Pouka:** agent oko postojećeg sustava, ne novi sustav oko agenta.

## 2. Znanje i kontekst: baza znanja za RAG (3 min)

- **Pitanje faze:** odakle agent zna *naše* politike, a ne opće znanje modela?
- Call-centar zna politike: 10 kratkih markdown dokumenata koje bi pisali ljudi iz podrške (kupnja i limiti, iskorištavanje po pružatelju, kad kod ne radi, povrat, sigurnost, radno vrijeme, česta pitanja, rječnik, privatnost).
- **Napisani za RAG:** `##` odjeljak = jedan odlomak, brojke u tekstu su pravila sustava (150 €, 14 dana), svaki odlomak razumljiv sam za sebe, jezik kupaca i sinonimi. 85 odlomaka.
- **Spring AI:** `EmbeddingModel` (OpenAI `text-embedding-3-small`) + `VectorStore` (pgvector u istoj bazi kao poslovni podaci; nula nove infrastrukture). Ingest idempotentan po hashu datoteke, top-k 3, prag sličnosti 0,35.
- **Demo (30 s):** admin tab Znanje: „mogu li vratiti bon nakon 20 dana“ → odlomak o roku od 14 dana. Isto pitanje drukčije sročeno → isti odlomak.
- **🔀 Alternative (jedna rečenica):** vector store i embeddinzi su konfiguracija (in-memory za demo, Qdrant/Redis za skaliranje; Ollama za lokalne embeddinge); pgvector jer je baza već tu.
- **Pouka:** RAG za demo nije RAG platforma. Vrijednost je u tome što dokumente pišu ljudi iz podrške, a agent ih čita.

## 3. Chatbot: model + prompt + memorija + znanje (3,5 min)

- **Pitanje faze:** što dobijemo kad model dobije persona-prompt, memoriju razgovora i bazu znanja, i ništa više?
- **Spring AI `ChatClient`, jedan bean, tri sastojka (slajd-dijagram):** system prompt (persona Nola, pravila ponašanja, hrvatski) · memorija po razgovoru (`MessageChatMemoryAdvisor`, `conversationId` = razgovor u shopu) · RAG advisor nad bazom znanja. Ulaz i izlaz su tekst: `POST /api/agent/chat`. **Ovaj API se do kraja predavanja više ne mijenja.**
- **Demo (1 min, chat u shopu):**
  1. „Mogu li vratiti bon nakon 20 dana?“ → odgovor iz baze znanja, s izvorom. Dobar chatbot.
  2. „A koji sam bon ja kupio prošli tjedan?“ → Nola ne zna; može samo reći gdje to kupac sam vidi. **Tu stane chatbot.**
  3. „Zapamti da me zanima Steam.“ → sljedeći turn pokaže memoriju.
- **Slajd „što chatbot ne može“:** ne vidi kupca, ne vidi kupnje ni wallet, ne može ništa napraviti. Zna *politiku*, ne zna *kupca*. Zato iduća stepenica.
- **🔀 Alternative (jedna rečenica):** model je konfiguracija (OpenAI, Anthropic, Azure, lokalni Ollama); izbor pao na `gpt-4o` zbog pouzdanog pozivanja alata i hrvatskog.
- **Pouka:** chatbot je koristan za pitanja o politici, ali call centar živi od pitanja o *mom* računu.
- **Priprema (TODO):** u repou Faza 3 ima alate od početka; za ovaj demo treba prekidač u agentu (npr. `nottrilix.agent.tools-enabled=false` ili env `NOTTRILIX_TOOLS=off`) koji `ChatClient` digne bez alata, pa ga uključiti uživo za blok 4 (restart agenta traje ~5 s; alternativa: dva agenta na dva porta, ili samo slajd).

## 4. Alati i agentske mogućnosti (6 min) ⭐

- **Pitanje faze:** kako agentu dati ruke, a ne dati mu ključeve od dućana?
- **Četvrti sastojak:** alati. `@Tool` metode su **tanki adapteri** nad postojećim servisima (`ShopService`, `WalletService`, `SupportService`…), bez ijednog pravila u sebi. **Kupac dolazi iz prijave kroz `ToolContext`, ne iz razgovora**: model ne može reći „radim za Marka“.
- **Kod na slajdu:** jedna `@Tool` metoda, tri retka delegacije. „Toliko koda treba da agent dobije ruku.“
- **Slajd „alati“ (tablica):** čitanja (katalog, kupnje, wallet, provjera bona kod pružatelja, prijave) i **radnje s potvrdom** (kupnja, povrat, blokada) + `confirmPendingAction()` bez parametara. Čega nema među alatima (uplata, goodwill, tuđi podaci, admin radnje), agent **ne može**: prompt injection nema što pozvati.
- **Demo (2 min, isti chat, alati uključeni):**
  1. „Koliko imam na walletu?“ → alat, odgovor s brojkom (ono što chatbot nije mogao).
  2. „Moj Steam kod ne radi, kaže da je nevažeći.“ → kupnje + provjera kod pružatelja → „iskorišten 21. 9., povrat nije moguć“. Pravilo iz sustava, ne iz modela.
  3. „Kupi mi HT bon od 10 eura.“ → **prijedlog**, gumbi za potvrdu → „Da, potvrđujem.“ → izvršeno. Paralelno admin tab Audit.
  4. (po volji) „Kupi mi Steam od 50.“ s Markom (wallet 25 €) → `INSUFFICIENT_FUNDS` istom porukom kao u shopu.
- **Dvije pouke iz izgradnje (war stories, kratko):**
  - Memorija razgovora pamti samo tekst. U turnu potvrde model je **izmišljao id-eve**. Rješenje: sustav drži prijedlog radnje (`ConfirmationGate`), alat za potvrdu nema parametara, potvrda vrijedi samo u idućem turnu. **Prompt nije jamstvo. Kod jest.**
  - Pravila u promptu („nemoj izmišljati datum“, „bez završnih fraza“) model je kršio u 1 od 10 slučajeva. Ono što mora vrijediti uvijek ide u kod: podatak s uputom, filter odgovora.
- **Audit od prvog dana:** svaki poziv alata, dohvat iz baze znanja i turn je redak u tablici (tko, što, kada, ishod, trajanje). „Bez toga nema produkcije.“
- **Pouka:** model bira *koju dopuštenu* radnju; *smije li*, kaže kod. Pravila ostaju tamo gdje su bila.

## 5. MCP: sustav kao MCP server, agent kao zasebna aplikacija (4,5 min)

- **Pitanje faze:** što od granica iz bloka 4 preživi mrežu?
- **Motivacija:** „Alat u istom procesu ne možeš nazvati telefonom.“ Isti alati izlaze iz procesa: sustav postaje **MCP server** (`POST /mcp`, HTTP bez sesije, 14 `@McpTool` alata), agent je zasebna aplikacija i **MCP klijent**, bez baze i bez pravila. Krug s lanjskim talkom zatvoren: lani smo MCP servere pisali, danas ih agent konzumira.
- **Slajd „što od granica preživi mrežu“ (tablica, tri retka):**
  - tko je kupac: **token kupca u zaglavlju svakog MCP poziva**, sustav ga sam provjerava; model token nikad ne vidi;
  - potvrda radnje: brana **u sustavu**, agent u MCP `_meta` šalje samo `conversationId` i `turn`;
  - audit: omotač oko svakog MCP alata **u sustavu**; vrijedi za svaki klijent, agent ga ne može preskočiti.
- **Demo (1 min):** dva procesa u istoj konzoli (`[sustav]` / `[agent ]`), jedan turn s potvrdom: isti alat izmjeren s obje strane mreže. Po volji: `curl` na `/mcp` bez tokena → 401, s ključem agenta → `CUSTOMER_REQUIRED`.
- **Nalaz za publiku (Spring AI 2.0):** RAG advisor je po zadanom redoslijedu **unutar petlje pozivanja alata** pa se dohvat ponavljao pri svakom pozivu modela; vidjelo se tek u auditu na strani sustava. Jedan redak: `.order(ToolCallingAdvisor.DEFAULT_ORDER - 50)`.
- **Pouke:** granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica, ne kao podatak; audit na strani koja posjeduje podatke.

## 6. Glas, i što ostaje iza razgovora (6 min)

- **Pitanje faze:** što se mijenja kad kupac progovori, i što ostaje iza razgovora?
- **Glas je kanal, ne novi agent (slajd-pipeline):** 🎤 → STT (Spring AI `TranscriptionModel`, OpenAI) → **isti agent** (`/api/agent/chat` iznutra) → TTS (Spring AI `TextToSpeechModel`, ElevenLabs ili OpenAI, pružatelj = konfiguracija) → 🔊. Odgovor **streama**: prvi zvuk 1,6–2,7 s od kraja snimanja, „Samo trenutak.“ ako model potraje, snimanje stane samo na tišinu, klik prekida Nolu. Snimka se ne pohranjuje; u audit ide transkript.
- **Demo (1,5 min, mikrofon u shopu, hrvatski):** prvi put privola (10 s, uživo); klik, „Kupi mi HT bon od deset eura.“ → transkript odmah, Nola (glas „Maja“, `eleven_v4_turbo`) govori prijedlog → „Da, potvrđujem.“ → izvršeno. Log na ekranu: `VOICE STT … ms`, `VOICE TTS … prvi zvuk nakon … ms`.
- **Nalaz (kratko, smiješno):** `language: hr` nije bio dovoljan: isti hrvatski zvuk model je vraćao kao srpsku ćirilicu, bugarski, poljski. Kratki prompt s rječnikom domene (brendovi, „wallet“, „potvrđujem“) riješio je sve uzorke. „STT-u dajte ono što dobije novi agent prvi dan: rječnik.“ Drugi: serijske brojeve kupac izgovara bez crtica, pa ih sustav tako i traži.
- **Što ostaje iza razgovora (2,5 min; prvo za kraćenje ako vrijeme bježi):**
  - audit trag iz bloka 5 već je **transkript**: KUPAC / ALAT / ZNANJE / AGENT po turnovima; ništa se ne bilježi dvaput;
  - **post-analiza**: utihnuli razgovor → pseudonimizacija (ime → `[KUPAC]`) → model → **strukturirani nalaz** (Spring AI structured output): tema, ishod, raspoloženje, ocjena agenta, **rupe u znanju**;
  - **samoučenje s čovjekom u petlji**: rupa u znanju → prijedlog odlomka → admin ispravi i odobri → odlomak u bazi znanja, agent ga koristi od idućeg pitanja;
  - **GDPR u kodu (jedan slajd):** privola za glas s verzijom teksta · izvoz JSON · brisanje razgovora · retencija po rasporedu (30 / 365 dana) · logovi bez e-maila. „Rok čuvanja je posao po rasporedu, izvoz je upit, brisanje je transakcija. Nije teško; teško je ne preskočiti.“
- **Demo (30 s, admin):** tab Razgovori: nalaz upravo održanog glasovnog razgovora (gumb „Analiziraj“); tab Znanje: prijedlog iz ranijeg razgovora o R1 računu → odobri → agent zna.
- **🔀 Alternative (jedna rečenica):** STT/TTS pružatelji su konfiguracija u Spring AI-ju (OpenAI, ElevenLabs, Azure); hrvatski glas presudio za ElevenLabs.
- **Pouke:** glas nije novi agent; audit trag je više od nadzora; samoučenje da, ali s čovjekom koji odobrava.

## 7. Telefon: poziv s mobitela (5 min) — finale

_Stanje koda (grana `phase6`, 2026-10-02): **simulator poziva verificiran** (`scripts/phone_sim.py` glumi Infobip: webhookovi + WebSocket + PCM), živi poziv kroz Infobip čeka broj i webhookove u portalu. Opis: [PHASE6.md](PHASE6.md). Blok dopuniti mjerenjima (prvi zvuk, turn) kad živi poziv prođe._

- **Pitanje faze:** tko zove, i tko mu smije dati ruke? Kupac nema ni preglednik ni prijavu, samo zvuk s jedne i broj pozivatelja s druge strane.
- **Arhitektura (slajd, dvije ravnine):**
  - **kontrolna:** mobitel → Infobip HR broj → webhook `CALL_RECEIVED` u agenta → agent pita sustav tko zove, pa se javi (Calls API) → `CALL_ESTABLISHED` → most poziva na naš WebSocket (dialog s `WEBSOCKET` odredištem) → `CALL_FINISHED` → kraj poziva u sustav;
  - **medijska:** `wss://<tunel>/infobip/media`, **20 ms okviri PCM16 @ 16 kHz u oba smjera** ⇄ agent: energetski VAD (240 ms govora počinje iskaz, 700 ms tišine ga završava) → STT (isti Spring AI `TranscriptionModel`, WAV iz PCM-a) → **isti `AgentService`** → TTS (ElevenLabs **`pcm_16000`**, izravno na liniju, bez transkodiranja) → okviri natrag u taktu od 20 ms. **Barge-in:** kupac progovori, red neizgovorenih okvira se baca. „Samo trenutak.“ nakon 1,2 s čekanja. Jedan poziv = jedan razgovor (`conversationId` = Infobipov `callId`), pa potvrda kupnje radi kroz dva iskaza kao kroz dvije poruke.
- **Ništa novo iz Spring AI-ja:** isti `ChatClient`, isti `TranscriptionModel` i `TextToSpeechModel`. WebSocket, VAD, pacing i Infobip su obična Java. „Gdje apstrakcija ne seže, obična Java. I to je u redu.“ Jedan sitan ali poučan detalj: telefon traži ElevenLabs jer daje PCM 16 kHz; OpenAI TTS bi dao 24 kHz i tražio resampling.
- **Pravi problem ove faze: tko zove? (slajd):** rješenje je u sustavu, ne u agentu: `POST /api/phone/calls` (agent zove s vlastitim ključem, uloga AGENT) → broj pozivatelja (samo znamenke, u logu i auditu **maskiran**) → kupac po telefonu → **token kupca za trajanje poziva**, povučen na kraju poziva. Od tog trenutka svaki MCP poziv nosi isti token kao chat: **ista pravila, ista brana potvrde, isti audit**, bez ijedne nove grane u sustavu i bez nove migracije (kanal `PHONE` u postojećim tablicama). **Nepoznat broj:** pozdrav, objašnjenje („broj nije u računu, prijavite se u web shop“), kraj poziva. Iskreno reći: broj pozivatelja je identifikacija, ne autentikacija (spoofing postoji); za radnje s novcem produkcija bi tražila PIN, poziv natrag ili jednokratni kod u aplikaciji.
- **Demo (2 min, finale):** publika čuje: zvonjava → „Dobar dan, Ana…“ → „Koliko imam na walletu?“ → Nola odgovara → „Kupi mi HT bon od deset eura.“ → prijedlog → „Potvrđujem.“ → izvršeno → upad u riječ. Na ekranu paralelno log `TELEPHONY` / `PHONE` i admin tab Audit s oznakom „poziv“. **Rezerva 1:** `python scripts\phone_sim.py` uživo (isti medijski put, bez Infobipa; publika čuje Nolin odgovor iz zvučnika ako simulator reproducira PCM, inače se vidi transkript). **Rezerva 2:** snimka poziva.
- **War story (ispričati, iz prvog pokušaja):** poziv odbijan s „The received request was rejected“ jer subscription u portalu nije bio vezan na Calls Configuration; propagacija „par minuta“; tunel URL koji se mijenja sa svakim startom (agent pri startu sam prepiše media-stream konfiguraciju, ali pretplate na događaje na ovom računu nemaju API, pa se mijenjaju ručno u portalu). Integracije su i to.
- **🔀 Zašto Infobip:** hrvatska firma (JavaCro kut), PCM16 na žici (bez transkodiranja), službeni ElevenLabs primjer, Call Link za demo bez broja. Alternativa Twilio (µ-law 8 kHz, bez hrvatskog u `<Say>`). Telefonija je kod oba „glupa cijev za zvuk“.
- **Pouke (iz PHASE6.md §9):** telefon nije promijenio ni agenta ni sustav, promijenio je *tko izdaje identitet*: u chatu prijava, na telefonu broj, oboje završi kao isti token u istom zaglavlju; **kanal je omotač, vjerodajnica je granica**. Simulator je vrijedio više od dokumentacije: cijeli medijski put vježba se lokalno, živi poziv dodaje samo webhookove i broj. Nepoznat pozivatelj je slučaj koji treba odlučiti, ne izbjeći.

## 8. Zaključak (2 min)

- **Rekapitulacija (jedan slajd, tablica po fazama):** kanal / tko je kupac / gdje su pravila / gdje je potvrda / gdje je audit. U svim retcima: pravila, potvrda i audit su u sustavu. `ChatClient` se od bloka 3 nije mijenjao: „**Od chatbota do telefona: isti mozak.**“

  | Faza | Kanal | Tko je kupac | Pravila | Potvrda | Audit |
  |---|---|---|---|---|---|
  | chatbot | tekst | prijava | sustav | — | turn |
  | alati | tekst | `ToolContext` iz prijave | sustav | sustav | alat + turn |
  | MCP | tekst | token u zaglavlju | sustav | sustav | sustav, za svaki klijent |
  | glas | zvuk → tekst | isti token | sustav | sustav | transkript |
  | telefon | PSTN → zvuk → tekst | broj → token za poziv | sustav | sustav | kanal PHONE |

- **Pet pouka:**
  1. Agent oko postojećeg sustava; pravila ostaju u kodu, model bira među dopuštenim radnjama.
  2. Prompt nije jamstvo. Ono što mora vrijediti uvijek ide u kod (potvrda na serveru, filteri, podaci s uputom).
  3. Granica koja živi u klijentu nije granica; identitet putuje kao vjerodajnica.
  4. Audit trag je više od nadzora: transkript, analiza, izvoz kupcu, dokaz brisanja.
  5. Kanali su adapteri: tekst, glas, telefon. Agentov API se nije promijenio.
- **Do produkcije nedostaje (iskrenost, jedan slajd):** HTTPS i sef za tajne, autentikacija pozivatelja (ne samo broj), potpisani webhookovi, ugovori o obradi s pružateljima, NER umjesto regex pseudonimizacije, brisanje računa, mjerenje i limiti troška.
- **CTA:** repo (QR), lanjski talk + ovaj = cijela MCP priča, pitanja.

---

## Kod uz svaki blok: što otvoriti i što reći

Blokovi su iz raspodjele vremena gore; faze su grane u repou (Faza 3 pokriva dva bloka: chatbot bez alata, pa alati).

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
| 6 telefon | ništa novo iz Spring AI-ja: isti `ChatClient`, `TranscriptionModel` (WAV iz PCM-a), `TextToSpeechModel` s `ElevenLabsTextToSpeechOptions.outputFormat("pcm_16000")`; WebSocket, VAD, pacing i Infobip su obična Java (Spring WebSocket) | `spring-boot-starter-websocket` (agent) | `agent/…/telephony/`, `phone/` u sustavu | „Gdje apstrakcija ne seže, obična Java.“ Identitet pozivatelja izdaje sustav kao token. |

**Za zaključni slajd.** Spring AI je dao: `ChatClient` s advisorima (memorija, RAG), alate (`@Tool`, `ToolCallback`, `ToolContext`), MCP server (`@McpTool`) i MCP klijent (`McpClientCustomizer`, `SyncMcpToolCallbackProvider`), `EmbeddingModel` + `VectorStore`, `TranscriptionModel` + `TextToSpeechModel`, structured output (`BeanOutputConverter`).
Naše je: poslovna pravila, brana potvrde, audit, identitet kroz token, transkript iz audita, pseudonimizacija, GDPR, samoučenje s odobrenjem. Dva stupca na jednom slajdu.

### Faza 1 (blok 1): pravila su u kodu

_Spring AI: ništa. Namjerno: prvo sustav s pravilima, bez ijednog AI dijela._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „pravila u servisima“ | `src/main/java/com/nottrilix/service/ShopService.java:81` (`purchase`), `:150–153` | dnevni limit → `deny("PURCHASE", "DAILY_LIMIT_EXCEEDED", …)`, zaključana zaliha, naplata s walleta, sve u jednoj transakciji | „Ovo je vlasnik istine. Sve što agent kasnije radi prolazi kroz ovu metodu.“ |
| isti slajd | `service/WalletService.java:85–92` | `INSUFFICIENT_FUNDS`, `debitForPurchase` | „Novac se ne troši u modelu.“ |
| slajd „uloge“ | `config/SecurityConfig.java:58–63` | `/api/shop/**` CUSTOMER, `/api/admin/**` ADMIN (redak 62 s `/mcp` spomenuti tek u bloku 4) | „Tko što smije određuje sustav, ne razgovor.“ |

Ne otvarati: kontrolere, DTO-e, React.

### Faza 2 (blok 2): znanje i kontekst

_Spring AI: `EmbeddingModel` (OpenAI) + `VectorStore` (pgvector), `Document`, `SearchRequest`; starteri `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „dokumenti pisani za RAG“ | `src/main/resources/kb/02-kupnja-wallet-i-limiti.md` | `##` odjeljci, brojke iz sustava (150 €, 14 dana), svaki odlomak samostalan | „Pisano za čitača koji vidi jedan odlomak.“ |
| slajd „ingest i pretraga“ | `kb/KbMarkdownParser.java:37` | `split("(?m)^## ")`: odlomak = chunk, jedan redak | |
| isti slajd | `kb/KnowledgeBaseIngestor.java:93–125` | hash datoteke → preskoči / zamijeni → `vectorStore.add(documents)` | „Spring AI VectorStore u tri retka, idempotentno po hashu.“ |
| isti slajd | `kb/KnowledgeBaseService.java:37–42` | `SearchRequest` s top-k i pragom | |
| konfiguracija | `src/main/resources/application.yml:9–17, 77` | embedding model, pgvector, prag 0,35 | „Pružatelj je konfiguracija.“ |
| baza | `db/migration/V3__vector_store.sql:8` | `vector_store` u istoj bazi kao kupci i kupnje | „Nula nove infrastrukture.“ |

### Faza 3 (blokovi 3 i 4): chatbot, pa alati

_Spring AI: `ChatClient`, `ChatMemory` + `MessageChatMemoryAdvisor`, `QuestionAnswerAdvisor` (RAG), `@Tool` + `ToolContext` + `ToolCallback`, `Usage`._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „četiri sastojka“ | `agent/src/main/java/com/nottrilix/agent/AgentConfig.java:83–108` | jedan `ChatClient` bean: system prompt, `MessageChatMemoryAdvisor`, `RetrievalAugmentationAdvisor`, `defaultToolCallbacks` (u Fazi 3 `QuestionAnswerAdvisor` nad vector storeom: grana `phase3`, `AgentConfig.java:59–60`) | „Jedan bean, četiri sastojka.“ |
| isti slajd | `agent/src/main/resources/agent/system-prompt.txt` | persona Nola, pravila ponašanja; redak 6 kanal VOICE spomenuti tek u bloku 5 | „Prompt kaže kako. Smije li, kaže kod.“ |
| slajd „`@Tool` u tri retka“ | grana `phase3`: `src/main/java/com/nottrilix/agent/AgentTools.java:136–147` (`getMyWallet(ToolContext ctx)`); danas `src/main/java/com/nottrilix/mcp/SupportMcpTools.java:181–186` | kupac iz `ToolContext` odnosno `McpMeta`, delegacija u postojeći servis | „Toliko koda treba da agent dobije ruku.“ |
| slajd „war story: izmišljeni id-evi“ | `mcp/SupportMcpTools.java:237–248` (`purchaseVoucher` → `gate.confirmed(…)` → `CONFIRMATION_REQUIRED`), `:306` (`confirmPendingAction` bez parametara), `mcp/ConfirmationGate.java:29–60` (`PendingAction`, potvrda vrijedi samo u idućem turnu) | „Model je izmišljao id-eve. Sad ih ne treba: prijedlog drži server.“ |
| isti slajd | `agent/…/ReplyStyle.java:13–26`, `mcp/SupportMcpTools.java:67` (`REDEMPTION_DATE_HINT`) | završne fraze filtrirane kodom; datum iskorištenja stiže s uputom uz podatak | „Pravilo koje mora vrijediti uvijek postaje kod.“ |
| slajd „audit“ | `db/migration/V4__audit_events.sql:4`, `mcp/AuditedToolHandler.java:48–80` | omotač oko alata: ulaz, ishod, trajanje → redak u `audit_events` | „Bez ovoga nema produkcije.“ (demo prati admin tab Audit) |

### Faza 4 (blok 5): MCP

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

### Faza 5 (blok 6): glas, razgovor kao zapis, GDPR

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

### Faza 6 (blok 7): telefon

_Spring AI: ništa novo (isti `TranscriptionModel`, `TextToSpeechModel` s `ElevenLabsTextToSpeechOptions.outputFormat("pcm_16000")`); telefonija je obična Java (Spring WebSocket, Infobip Calls API) oko istog agenta. Kod na grani `phase6` (2026-10-02, još necommitano); brojeve redaka upisati kad se kod smiri. Pregled klasa: [PHASE6.md](PHASE6.md) §3–4._

| Trenutak | Datoteka | Što pokazati | Rečenica |
|---|---|---|---|
| slajd „arhitektura: kontrolna ravnina“ | `agent/src/main/java/com/nottrilix/agent/telephony/InfobipWebhookController.java` | `/infobip/call-received` (identitet + javljanje), `/infobip/event` (`CALL_ESTABLISHED` → most, `CALL_FINISHED` → kraj); pri startu prepiše media-stream config na javni `wss://` URL | „Tri webhooka, tri REST poziva. Telefonija je cijev.“ |
| isti slajd | `agent/…/telephony/InfobipCallsClient.java` | `answer`, `dialogs` (most na `WEBSOCKET` endpoint), `hangup`, media-stream config | |
| slajd „arhitektura: medijska ravnina“ | `agent/…/telephony/InfobipMediaWebSocketHandler.java` | metadata → `CallSession`; okvir → VAD → iskaz → `turn()` → **isti `AgentService.chat(token, callId, tekst, "PHONE")`** → TTS → red okvira; pacer 20 ms; upad prazni red; filler nakon 1,2 s | „Ista petlja kao glas u pregledniku, samo u stvarnom tempu.“ |
| isti slajd | `agent/…/telephony/CallSession.java` | energetski VAD (RMS, 240 ms govora, 700 ms tišine, 300 ms pre-rolla), red izlaznih okvira; zvuk kupca se ne čuva | „VAD u 30 redaka; ništa se ne snima.“ |
| isti slajd | `agent/…/telephony/TelephonySpeech.java` | STT iz WAV omota, TTS `pcm_16000`, pozdrav s imenom, pozdrav nepoznatom, filler | „ElevenLabs daje PCM 16 kHz; zato telefon traži ElevenLabs.“ |
| isti slajd | `agent/src/main/resources/application.yml` (`nottrilix.telephony.*`) | pozdravi, filler, najdulji poziv, pragovi VAD-a, javni URL | „Sve što se ugađa je konfiguracija.“ |
| slajd „tko zove“ | `src/main/java/com/nottrilix/phone/PhoneSessionService.java` (`identify`) | broj → znamenke → `findByPhoneDigits` (ili `NOTTRILIX_DEMO_CALLER` → kupac 1) → `TokenService.issue` token za trajanje poziva; nepoznat → bez tokena; audit `PHONE_CALL` s maskiranim brojem | „Sustav izdaje vjerodajnicu u ime kupca. Agent je i dalje nepovjerljiv klijent.“ |
| isti slajd | `src/main/java/com/nottrilix/api/PhoneController.java`, `config/SecurityConfig.java` (`/api/phone/**` AGENT) | `POST /api/phone/calls`, `/turns`, `/end`; kraj poziva povlači token | „Tri endpointa, nijedno novo pravilo, nijedna nova migracija.“ |
| isti slajd | `src/main/java/com/nottrilix/phone/PhoneNumbers.java` | `digits` i `mask` („385 91***222“) | „Broj je osobni podatak: u logu maskiran.“ |
| demo / rezerva | `scripts/phone_sim.py` | glumi Infobip: webhookovi + WebSocket + PCM; `--from` za nepoznat broj | „Simulator je vrijedio više od dokumentacije.“ |

Ne otvarati: `PcmUtil`, `CallRegistry`, `PhoneGateway`, DTO-e, Infobip JSON.

### Tehnika

- IDE s fontom ≥ 18, tabovi unaprijed otvoreni po redu blokova (bookmarki), bez skrolanja tijekom pokazivanja.
- „Što je faza dodala“ = `git diff --stat phase3 phase4` uživo ili tablica gore; worktree po grani za usporedbu jedne datoteke (npr. `AgentTools.java` u `phase3` uz `SupportMcpTools.java` u `phase5`).
- Ne otvarati: kontrolere, DTO-e, testove, React osim `ChatWidget`; migracije samo kao „jedna tablica po fazi“ (V3 znanje, V4 audit, V5 razgovori i privole).

## Popis slajdova (radni, ~32)

1. Naslov · 2. Lani / danas (hook) · 3. Teza · 4. **Pojednostavljeno namjerno** (dva stupca) · 5. Mapa faza izgradnje (stepenice)
6. NotTrilix: što prodaje, tko koristi · 7. Sustav: pravila u servisima (kod) · 8. Demo 1
9. Baza znanja: dokumenti pisani za RAG · 10. Spring AI ingest + pretraga (kod/konfiguracija) · 11. Demo 2
12. ChatClient: tri sastojka (dijagram) · 13. Demo 3 (chatbot) · 14. Što chatbot ne može
15. Četvrti sastojak: `@Tool` u tri retka · 16. Alati: čitanja i radnje s potvrdom (tablica) · 17. Demo 4 · 18. War story: izmišljeni id-evi → potvrda na serveru · 19. Audit
20. MCP: dvije aplikacije (dijagram) · 21. Što od granica preživi mrežu (tablica) · 22. Demo 5 + nalaz o redoslijedu advisora
23. Glas kao kanal (pipeline, streaming) · 24. Demo 6 + STT nalaz · 25. Razgovor kao zapis: transkript → nalaz → prijedlog (dijagram) · 26. GDPR u kodu
27. Telefon: arhitektura (Infobip, WebSocket, VAD, barge-in) · 28. Tko zove? (broj → token za poziv) · 29. Demo 7 (finale) + war story
30. Rekapitulacija: od chatbota do telefona, isti mozak (tablica) · 31. Pouke + do produkcije · 32. Hvala / QR

## Demo plan i rezerve

| Demo | Priprema | Rizik | Rezerva |
|---|---|---|---|
| Svi | `.\run-phase-6.ps1 -NoBuild -Reset` prije izlaska (`-NoTunnel` ako se telefon ne demonstrira uživo); ključevi (`openai.key`, `elevenlabs.key`, `infobip.key`); dva prozora: shop + admin; konzola s logom vidljiva, font ≥ 18 | internet u dvorani, latencija modela (turn 1–5 s) | snimka svakog demo beata (video); `scripts/agent_e2e.py`, `scripts/phase5_e2e.py` kao proba dan prije |
| 3 (chatbot) | agent pokrenut **bez alata** (prekidač, vidi blok 3); Ana prijavljena | model ipak „zna“ nešto o kupcu iz prompta | u promptu nema podataka o kupnjama; pitanje o kupnji uvijek završi s „ne vidim“ |
| 4 (alati) | agent restartan s alatima (~5 s, dok govorim slajd 15); Marko: wallet 25 € (HT 10 € prolazi, Steam 50 € pada na pravilu) | model drukčije sroči odgovor | sve što je bitno je u alatima i auditu, ne u formulaciji |
| 5 (MCP) | obje konzole vidljive; `scripts/mcp_direct.py` spreman | ništa posebno | tablica na slajdu |
| 6 (glas) | mikrofon u pregledniku dopušten, privola uživo (10 s) | buka u dvorani, STT | tekstom u isti chat; snimka |
| 6b (analiza) | jedan razgovor unaprijed analiziran, gumb „Analiziraj“ za uživo; prijedlog o R1 računu pripremljen **nakon** reseta | analiza 2–8 s | prethodno analizirani razgovori u tabu |
| 7 (telefon) | Infobip broj + webhookovi u portalu, tunel (`run-phase-6.ps1`), `demo-caller.txt` = moj broj, probni poziv isto jutro; mobitel na zvučnik ili u mikser dvorane | sve (mreža, mobilna, propagacija, Infobip račun) | `scripts\phone_sim.py` uživo (isti medijski put bez Infobipa); Call Link iz preglednika; snimka poziva |

Prije izlaska: `-Reset` briše i odobrene naučene odlomke; ako demo 6b treba već naučen odlomak, pripremiti ga nakon reseta.

**Rezervni videi** (ako zakaže mreža, mikrofon ili API): `demo-video/out/01-faza1-sustav.mp4` … `05b-faza5-analiza-gdpr.mp4` i `00-sve-faze.mp4` (sve faze s naslovnim karticama, 6,5 min). Snimljeno automatski protiv stvarnog sustava (Playwright), s kursorom i natpisima; glas ima Nolin zvuk. VLC na cijelom ekranu, razmak za pauzu.

## Što još treba napraviti do konferencije (11.–14. 10.)

**Kod**
- [ ] Faza 6 dovršiti: živi poziv kroz Infobip (broj, webhookovi u portalu, tunel), mjerenja (prvi zvuk, turn) za blok 7 i `PHASE6.md` §7–8; commit na `phase6`; brojevi redaka u dodatku „Faza 6“.
- [ ] Prekidač „agent bez alata“ za demo chatbota (blok 3) + način brzog uključivanja uživo.
- [ ] Pripremljena baza s jednim analiziranim razgovorom i prijedlogom za bazu znanja (ili skripta koja ih napravi nakon `-Reset`).

**Infobip / organizacija**
- [ ] Nadoplata računa, HR broj (usmjeravanje na Calls Configuration), pretplate na događaje u portalu (bez API-ja na ovom računu, ručno pri svakoj promjeni tunela); stalni tunel ili javni host; Call Link kao rezerva (vrijedi 24 h, svježi na dan talka).
- [ ] Mikrofon i zvuk dvorane: kako publika čuje mobitel (zvučnik uz mikrofon ili ulaz u mikser).

**Deck i proba**
- [x] Deck v1 (2026-10-02, engleski od 2026-10-03): `presentation/build_pptx.py` → `presentation/NotTrilix-JavaCro2026.pptx` (31 slajd, bilješke za govornika iz ovog nacrta; `python presentation/build_pptx.py` za regeneraciju). Otvoreno: QR kod na zadnjem slajdu, mjerenja za telefon, stvarni redci audita umjesto ilustrativnih na slajdu 18.
- [x] Snimke demo beatova za faze 1–5 (2026-10-03): `demo-video/out/` (po fazama + `00-sve-faze.mp4`, 1280×720, glas sa zvukom); ponovno snimanje po `demo-video/README.md`. Ostaje snimka poziva (Faza 6) kad bude živ.
- [ ] Generalna proba s mjerenjem vremena: cilj 33 min bez pitanja, 3 min rezerve za demo koji zapne; označiti što se krati prvo (blok 6 drugi dio, pa blok 2).
