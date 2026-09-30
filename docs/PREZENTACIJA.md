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
- **Demo (1,5 min, push-to-talk u shopu, hrvatski):** prvi put privola; „Kupi mi HT bon od deset eura.“ → Nola govori prijedlog → „Da, potvrđujem.“ → izvršeno. Log na ekranu: `VOICE STT … 1263 ms`, `TTS ElevenLabs … 1851 ms`.
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
