# Faza 5 — Glas, post-analiza razgovora, samoučenje i GDPR

_Status: **GOTOVO i e2e verificirano 2026-09-30** · grana `phase5` (iz `phase4`) · Spring AI 2.0.0 (OpenAI STT/TTS, ElevenLabs TTS, structured output)._

Faza 4 je agenta izvela iz sustava: sve što zna i može dolazi kroz MCP, u ime prijavljenog kupca. Faza 5 dodaje tri stvari koje se u
call centru podrazumijevaju, a u demou obično preskaču: **kupac može govoriti**, **razgovor nakon završetka nekome nešto znači** i
**osobni podaci imaju vlasnika**.

Pitanje faze: **što se mijenja kad kupac progovori, i što ostaje iza razgovora?** Odgovor u tri retka, ostatak dokumenta ih razrađuje:

| Tema | Što je napravljeno | Što se pritom nije mijenjalo |
|---|---|---|
| **Glas** | glasovni kanal *ispred* istog agenta: snimka → prepoznavanje govora (STT) → agent → sinteza govora (TTS) → mp3; sve kroz Spring AI | agent, MCP, alati, sustav: agentov ulaz je i dalje „tekst unutra, tekst van“ |
| **Razgovor kao zapis** | audit trag iz Faze 4 postaje **transkript**; utihnuli razgovori idu na **post-analizu** modelom (strukturirani nalaz), a rupe u znanju postaju **prijedlozi za bazu znanja** koje admin odobrava | audit trag: ništa se ne bilježi dvaput, transkript se sastavlja iz onoga što već postoji |
| **GDPR** | privola za glas (s verzijom teksta), izvoz svih podataka, brisanje razgovora, automatska retencija, pseudonimizacija prije modela, logovi bez e-maila | poslovni podaci (kupnje, wallet, bonovi) ostaju: ugovor i računovodstvo |

Cjelovit opis obrade osobnih podataka (evidencija aktivnosti, načela u kodu, što u demou nije napravljeno): [GDPR.md](GDPR.md).

---

## 1. Kako je složeno

```
 preglednik (shop): chat + gumb s mikrofonom + „Privatnost“                admin: tabovi Razgovori / Znanje / Audit
        │ POST /api/agent/chat  {message}                                        │
        │ POST /api/agent/voice  multipart audio        Authorization: Bearer <token kupca>
        ▼                                                                        ▼
┌──────────────────────────────────── SUSTAV :8080 ──────────────────────────────────────────────┐
│ AgentChatService: privola za glas? → agent → audit CHAT_TURN + okvir razgovora (conversations) │
│ PrivacyService: privola · izvoz · brisanje      RetentionJob: rok čuvanja (pri startu + noću)  │
│ ConversationService: audit trag → transkript (KUPAC / ALAT / ZNANJE / AGENT)                   │
│ ConversationAnalyzer: transkript → Pseudonymizer → gpt-4o (structured output) → nalaz          │
│      └─> KbSuggestion (PROPOSED) ──admin odobri──> KnowledgeBaseIngestor (odlomak „nauceno-N“) │
│ MCP server /mcp (Faza 4, nepromijenjen)                                                        │
└───────▲──────────────────────────────────────┬─────────────────────────────────────────────────┘
        │ MCP tools/call (token kupca, _meta)  │ poruka ili snimka + token
        │                                      ▼
        │                      ┌──────────── AGENT :8090 ─────────────────────────────────┐
        └──────────────────────┤ VoiceService: STT (OpenAI) → AgentService → TTS          │
                               │ AgentService: Spring AI ChatClient (gpt-4o), memorija,  │
                               │               RAG i alati kroz MCP (Faza 4)             │
                               └─────────────────────────────────────────────────────────┘
```

Glasovni turn: preglednik snimi govor (push-to-talk) → sustav provjeri privolu i snimku proslijedi agentu → agent je pretvori u tekst i dalje
radi točno ono što i s tekstualnom porukom → odgovor se sintetizira i vraća kao mp3 uz tekst → sustav u audit upiše **transkript** (ne snimku).

Post-analiza: `AnalysisScheduler` svake minute pokupi razgovore bez nove poruke dulje od `idle-minutes` → `ConversationAnalyzer` sastavi transkript iz
audit traga, pseudonimizira ga i pošalje modelu → nalaz (kategorija, ishod, raspoloženje, ocjena, rupe u znanju) ide u `conversation_analyses`, prijedlozi
u `kb_suggestions` → admin ih u tabu „Znanje“ pročita, ispravi i odobri → odlomak ulazi u vector store i agent ga od idućeg pitanja koristi.

## 2. Odluke

| Odluka | Razlog |
|---|---|
| **Glas je kanal, ne novi agent** (`VoiceService` ispred `AgentService`) | Isti system prompt, memorija, alati, brana potvrde i audit. Jedina razlika je uputa „kanal VOICE: kratko“ u promptu, jer se govor sluša, ne čita. |
| **STT i TTS kroz Spring AI** (`TranscriptionModel`, `TextToSpeechModel`) | Pružatelj je konfiguracija: `spring.ai.model.audio.speech: elevenlabs` ili `openai`; kod agenta se ne mijenja. ElevenLabs za kvalitetu hrvatskog, OpenAI kao rezerva bez ključa. |
| **Snimka se ne pohranjuje i ne logira** | U trag ide transkript kao poruka kupca (`CHAT_TURN`, kanal `VOICE`). Glas je biometrijski osjetljiv; ne čuvati ga je najjednostavnija zaštita. |
| **Glas traži privolu** (403 `CONSENT_REQUIRED`, tablica `consents` s **verzijom teksta**) | Govor obrađuju vanjski izvršitelji (OpenAI, ElevenLabs). Nova verzija teksta privole = kupac je daje ponovno; povlačenje jednim klikom. |
| **Transkript se sastavlja iz audit traga**, ne iz nove tablice poruka | Faza 4 je već bilježila svaki turn, poziv alata i dohvat iz baze znanja. Tablica `conversations` je samo okvir (tko, kanal, kada, koliko turnova, status). |
| **Analiza dobiva pseudonimizirani transkript** (`Pseudonymizer`: ime → `[KUPAC]`, e-mail, telefon, serijski broj, kod) | Model za analizu ne treba znati tko je kupac. Test `PseudonymizerTest` to čuva. |
| **Nalaz je strukturiran** (Spring AI `BeanOutputConverter<ConversationInsight>`) | Kategorija, ishod i raspoloženje su enumi, ocjena 1–5, rupe u znanju popis: može se brojati, filtrirati i prikazati; nema slobodnog prepričavanja razgovora. |
| **Samoučenje s čovjekom u petlji** (`kb_suggestions`: PROPOSED → APPROVED / REJECTED) | Model predlaže, admin odobrava i ispravlja; tek odobreni odlomak ide u RAG. Nacrt kojeg model ne zna potvrditi počinje s `PROVJERITI:`. |
| **Naučeni odlomci su ravnopravni dokumentima** (izvor `nauceno-<id>`, kategorija `nauceno`) | Isti ingest, isti vector store, isti `searchKnowledgeBase`. Pri `-Reset` se odobreni prijedlozi ponovno ingestiraju. |
| **Sustav opet ima chat model** (`gpt-4o` za analizu) | Analiza je back-office posao sustava nad vlastitim tragom, ne posao agenta; kupac u njoj ne sudjeluje. |
| **Prava kupca su kod, ne dokument** (`PrivacyService`: izvoz, brisanje; `RetentionJob`: rok čuvanja) | Izvoz je JSON sa svime (čl. 15, 20); brisanje uklanja sadržaj razgovora, a pozivi alata i analize gube vezu s kupcem (čl. 17); retencija radi pri startu i svake noći, s tragom u auditu. |
| **Logovi bez e-maila** (`customer#2`) | Log je najdulje živi i najmanje nadzirani zapis; identifikator je dovoljan za dijagnostiku. |
| **Agent odgovara na pitanja o privatnosti iz baze znanja** (`10-privatnost-i-osobni-podaci.md`) | Kupac koji agenta pita „što čuvate o meni“ dobiva isti odgovor kao i u odjeljku Privatnost, i upućen je gdje to može sam napraviti. |

## 3. Glas

Agent (`agent\pom.xml`): `spring-ai-starter-model-openai` već daje STT (`OpenAiAudioTranscriptionModel`) i TTS (`OpenAiAudioSpeechModel`);
`spring-ai-starter-model-elevenlabs` daje `ElevenLabsTextToSpeechModel`. Konfiguracija (`agent\src\main\resources\application.yml`):

```yaml
spring.ai.openai.audio:
  transcription.options:
    model: gpt-4o-mini-transcribe
    language: hr
    prompt: "Razgovor kupca s podrškom NotTrilix na hrvatskom. Pojmovi: HT bon, A1 bon, Steam Wallet, … potvrđujem, odustajem."
  speech.options: { model: gpt-4o-mini-tts, voice: nova, response-format: mp3 }
spring.ai.elevenlabs:
  api-key: ${ELEVENLABS_API_KEY:not-set}
  tts.options: { model-id: eleven_multilingual_v2, voice-id: ${NOTTRILIX_VOICE_ID:…}, output-format: mp3_44100_64 }
spring.ai.model.audio.speech: ${NOTTRILIX_TTS:openai}   # elevenlabs | openai  (run-phase-1.ps1 postavlja prema elevenlabs.key)
nottrilix.voice: { enabled: true, max-reply-chars: 600 }
```

`VoiceService.turn(...)`:

```java
String transcript = stt.call(new AudioTranscriptionPrompt(resource)).getResult().getOutput();   // snimka -> tekst
AgentChatResponse chat = agent.chat(customerToken, conversationId, transcript, "VOICE");        // isti agent
byte[] mp3 = tts.call(new TextToSpeechPrompt(spoken)).getResult().getOutput();                  // tekst -> govor
```

- Naziv datoteke snimke (`speech.webm`, `speech.mp4`, `speech.mp3`) govori STT modelu o formatu; preglednik šalje ono što `MediaRecorder` zna (`pickMimeType()`).
- **Nalaz: `language: hr` nije dovoljan.** Bez prompta je `gpt-4o-mini-transcribe` iste hrvatske snimke vraćao kao srpsku ćirilicu, bugarski, poljski ili slovački
  („Да, потврђујем.“, „Da, potwierdzuję.“, „Kúpi mi HT bón od desať eur.“). S kratkim promptom na hrvatskom koji nabraja pojmove domene (brendovi, „wallet“, „potvrđujem“)
  transkript je bio točan na svim uzorcima (tri glasa, tri rečenice); `whisper-1` je bio otporniji i bez prompta, ali je brendove pisao „HT-BON“. Prompt je rječnik call centra: ono što
  bi novi agent dobio prvi dan.

### Glas v2: prirodniji i fluidniji (2026-10-02)

Nakon prve probe Nola je govorila glasom „Roger“ (američki muški premade glas), a svaki glasovni turn čekao je cijelu sintezu prije prvog zvuka. Promjene:

| Što | Kako | Učinak |
|---|---|---|
| Glas i model | Voice Library glas **Maja** (hrvatski; library glasovi preko API-ja traže plaćeni ElevenLabs plan, Starter) + `eleven_v4_turbo` + `language_code: hr`; promjena kroz `NOTTRILIX_VOICE_ID` i `NOTTRILIX_TTS_MODEL` | prirodan hrvatski; prvi TTS chunk 0,2–0,4 s |
| Tekst za govor | `SpeechText`: iznosi riječima („10 €“ → „deset eura“, „12,50 €“ → „dvanaest eura i pedeset centi“), `->` → „na“, `%` → „posto“, crtice u serijskim brojevima → pauze. ElevenLabs nema normalizaciju teksta za hrvatski (`language text normalization is not supported for 'hr'`), pa je to naš kod | brojke i simboli se više ne čitaju po engleski |
| Streaming | `POST /api/agent/voice/stream`: JSON redci `transcript` → `chat` → `audio`, pa mp3 bajtovi kako ih TTS proizvodi (Spring AI `TextToSpeechModel.stream()`); sustav prosljeđuje i bilježi turn kad stigne odgovor; preglednik svira kroz MediaSource od prvog chunka (`voicePlayer.ts`) | transkript na ekranu nakon ~0,5 s; prvi zvuk ~0,2 s nakon odgovora umjesto nakon cijele sinteze |
| „Samo trenutak.“ | `GET /api/agent/voice/filler`: fraza sintetizirana jednom i keširana; preglednik je pusti ako odgovor ne stigne 0,9 s nakon transkripta | tišina dok model radi je kraća i prirodnija |
| Snimanje | klik umjesto držanja gumba; `voiceRecorder.ts` s detekcijom tišine (RMS; 0,9 s pauze nakon govora, 6 s bez govora, 20 s najviše) | kupac govori i stane, ništa ne drži |
| Prekid | klik na mikrofon dok Nola govori zaustavlja reprodukciju i počinje slušati | razgovor, ne izmjena poruka |
| Serijski broj bez crtica | kupac kaže „STM pedeset nula nula dva“, STT napiše `STM50002`; sustav uspoređuje serijske brojeve u sažetom obliku (`VoucherSerial.compact`: samo slova i znamenke, velika slova) u repozitoriju, pa `STM50002`, `stm 50 002` i `STM-50-002` nalaze isti bon; kupcu se vraća pohranjeni oblik | glasom izgovoren bon se pronađe; vrijedi i za chat i REST |

Protokol streama je isti iz agenta prema sustavu i iz sustava prema pregledniku:

```
{"type":"transcript","transcript":"Da, potvrđujem.","sttMs":486}
{"type":"chat","chat":{ …AgentChatResponse… },"ttsProvider":"ElevenLabs","spoken":"Kupnja HT bona od deset eura …"}
{"type":"audio","mimeType":"audio/mpeg"}
<mp3 bajtovi do kraja>
```

Greška nakon početka odgovora putuje kao `{"type":"error", …}` redak, jer je HTTP status već poslan. Stari `POST /api/agent/voice` (JSON s base64 mp3) ostaje za skripte.

Dvije zamke iz izgradnje:
- Spring MVC `StreamingResponseBody` pisao je u 8 KB međuspremnik odgovora, a `flush()` ga nije praznio: događaji su stizali tek s prvih 8 KB zvuka (vidjelo se tek mjerenjem chunkova na klijentu). Rješenje: pisanje izravno u `HttpServletResponse` na dretvi zahtjeva uz `flushBuffer()` nakon svakog događaja (`flushing(response)` u oba kontrolera).
- Isti asinkroni put rušio je stream u sustavu: ASYNC dispatch koji završava `StreamingResponseBody` nema bearer autentikaciju (`OncePerRequestFilter` preskače async dispatch), Spring Security ga odbije i veza pukne bez završnog chunka. Sinkrono pisanje uklanja i to.

Mjerenja (`scripts\phase5_e2e.py voice`, drugi turn kroz stream; preglednik Chromium kroz MediaSource):

| Točka | Vrijeme od slanja snimke |
|---|---|
| transkript na ekranu | 0,45–0,49 s |
| odgovor (tekst) | 1,5–2,5 s |
| prvi zvuk | 1,6–2,7 s (0,15–0,2 s nakon odgovora) |
| kraj govora | 1,9–3,8 s |

Prije toga prvi zvuk je dolazio nakon 4,7–8,3 s (odgovor + cijela sinteza + prijenos). Sljedeći korak, ako zatreba: rečenica po rečenica u TTS dok model još piše (prvi zvuk prije kraja odgovora).
- Odgovor dulji od `max-reply-chars` se za govor skrati na granici riječi (kupac cijeli tekst vidi u chatu); prompt za kanal `VOICE` traži jednu do dvije rečenice.
- Odgovor: `{transcript, chat, audioBase64, audioMimeType, sttMs, ttsMs, ttsProvider}`; preglednik prikaže transkript i tekst, mp3 odsvira odmah (gumb za ponovno slušanje).
- Preglednik: gumb s mikrofonom radi kao *push-to-talk* (drži i govori); prije prvog snimanja kupac vidi tekst privole i potvrđuje je (`POST /api/shop/privacy/consent`).

## 4. Razgovor kao zapis

**Transkript** (`ConversationService.transcript`): audit redci razgovora po redu nastanka →
`[turn] KUPAC: …`, `[turn] ALAT: purchaseVoucher -> CONFIRMATION_REQUIRED`, `[turn] ZNANJE: 03-povrat › Rok (0.71)`, `[turn] AGENT: …`.
Isti transkript vidi admin (tab Razgovori) i kupac u izvozu (samo KUPAC i AGENT redci); model za analizu ga dobiva pseudonimiziran.

**Analiza** (`ConversationAnalyzer`): system prompt opisuje pravila NotTrilixa i što znače redci ALAT i ZNANJE, a format izlaza daje Spring AI:

```java
chatClient.prompt().system(SYSTEM_PROMPT)
        .user(u -> u.text("TRANSKRIPT:\n{transcript}\n\n{format}")
                .param("transcript", pseudonymized).param("format", converter.getFormat()))
        .call().chatClientResponse();
ConversationInsight insight = converter.convert(content);   // record: summary, category, outcome, sentiment, qualityScore,
                                                            //   rulesFollowed, knowledgeGaps, knowledgeSuggestions, improvementNotes
```

Nalaz zamjenjuje prijašnji (analiza se može ponoviti), razgovor prelazi u `ANALYZED`, a u audit ide `ANALYSIS analyze` s brojem znakova, tokenima i sažetkom.
Ocjena i `rulesFollowed` su **nadzor agenta**: nalaz `PREKRŠENA pravila` u audit tragu je ono što bi voditelj podrške htio vidjeti prvo.

**Samoučenje** (`KnowledgeLearningService`): prijedlog (`question`, `answer`, iz kojeg razgovora) admin odobri s ispravkama → `KnowledgeBaseIngestor.ingestLearned`
napravi dokument `Naučeno iz razgovora › <pitanje>\n\n<odgovor>` (izvor `nauceno-<id>`) → od idućeg pitanja ga `searchKnowledgeBase` vraća kao i svaki drugi odlomak.
Odbijeni prijedlog ostaje zabilježen (ne predlaže se ponovno iz istog razgovora).

## 5. GDPR u kodu

| Pravo / načelo | Gdje |
|---|---|
| Privola (čl. 7) | `consents` (kupac, vrsta, verzija teksta, dana, povučena); `PrivacyService.requireConsent` prije glasa; `PrivacyModal` i `ChatWidget` u shopu |
| Pristup i prenosivost (čl. 15, 20) | `GET /api/shop/privacy/export` → `CustomerDataExport` (račun, wallet, kupnje, prijave, razgovori s porukama, analize, privole); audit `PRIVACY export` |
| Brisanje (čl. 17) | `POST /api/shop/privacy/erase-conversations` → poruke i dohvati iz baze znanja obrisani, ostali audit redci i analize anonimizirani, razgovori `ERASED`; audit `PRIVACY erase` (dokaz da je provedeno) |
| Ograničenje pohrane (čl. 5(1)(e)) | `RetentionJob` (pri startu + cron `0 15 3 * * *`): poruke starije od 30 dana brisati, ostalo anonimizirati, analize starije od 365 dana anonimizirati; audit `PRIVACY retention` |
| Smanjenje podataka (čl. 5(1)(c)) | agent dobiva ime, ne e-mail; analiza dobiva `[KUPAC]`; snimka se ne čuva; logovi `customer#2` |
| Transparentnost (čl. 13) | obavijest u chatu, tekst privole, odjeljak Privatnost (rokovi iz konfiguracije), baza znanja za agenta |

Sve što kupac ili sustav napravi s osobnim podacima je događaj u audit tragu (`PRIVACY`, `ANALYSIS`), vidljiv u admin tabu Audit.

## 6. Jedan glasovni turn u logu

Kupac (Marko) drži mikrofon i kaže „Kupi mi HT bon od deset eura.“ (u probi: sintetizirana snimka). Oba procesa pišu u istu konzolu; skraćeno:

```
[sustav] AUDIT   : PRIVACY consent conv=null customer=2 input=VOICE -> OK | privola dana, verzija teksta 2026-09
[sustav] AGENT   : VOICE -> agent customer=2 conv=(novi) audio=53760 B (audio/mpeg)
[agent ] VOICE   : VOICE STT 53760 B (speech.mp3) -> 1263 ms: 'Kupi mi HT bon od 10 eura.'
[agent ] AGENT   : TURN 1 conv=8c1b7c94 customer=2 channel=VOICE pending='-' msg='Kupi mi HT bon od 10 eura.'
[sustav] AUDIT   : KB_RETRIEVAL retrieve conv=8c1b7c94 customer=2 input=Kupi mi HT bon od 10 eura. -> OK (198 ms) | 03-iskoristavanje-bona-po-pruzatelju.md › Hrvatski Telekom (HT) … (0.55); …
[sustav] MCP     : MCP tools/call getCatalog user=customer#2 conv=8c1b7c94 turn=1 input={} -> OK (17 ms)
[sustav] MCP     : CONFIRM PURCHASE 5 conv=c2:8c1b7c94 turn=1 -> ceka potvrdu kupca
[sustav] MCP     : MCP tools/call purchaseVoucher user=customer#2 conv=8c1b7c94 turn=1 input={"productId":5} -> CONFIRMATION_REQUIRED (19 ms)
[agent ] AGENT   : TURN 1 conv=8c1b7c94 OK 5090 ms, alati=[getCatalog:OK, purchaseVoucher:CONFIRMATION_REQUIRED], izvori=3, tokeni=3415/44, ceka potvrdu=true
[agent ] VOICE   : VOICE TTS ElevenLabs -> 77157 B (audio/mpeg) u 1851 ms
[sustav] AUDIT   : CHAT_TURN turn conv=8c1b7c94 customer=2 input=Kupi mi HT bon od 10 eura. -> OK (8339 ms) | Kupnja HT bona od 10 € će se naplatiti s Vašeg walleta, a stanje će se smanjiti s 25 € na 15 €. Potvrđujete li kupnju?
[sustav] AGENT   : VOICE <- agent conv=8c1b7c94 OK 8339 ms, alati=[getCatalog:OK, purchaseVoucher:CONFIRMATION_REQUIRED], izvori=3, ceka potvrdu=true
```

Kupac: „Da, potvrđujem.“

```
[agent ] VOICE   : VOICE STT 50688 B (speech.mp3) -> 713 ms: 'Da, potvrđujem.'
[agent ] AGENT   : TURN 2 conv=8c1b7c94 customer=2 channel=VOICE pending='kupnja HT bon 10 €, naplata 10 € s walleta (stanje 25 € -> 15 €)' msg='Da, potvrđujem.'
[sustav] MCP     : CONFIRM PURCHASE 5 conv=c2:8c1b7c94 predlozeno u turnu 1, potvrdjeno u turnu 2
[sustav] MCP     : MCP tools/call confirmPendingAction user=customer#2 conv=8c1b7c94 turn=2 input={} -> OK (26 ms)
[agent ] AGENT   : TURN 2 conv=8c1b7c94 OK 2378 ms, alati=[confirmPendingAction:OK], izvori=0, tokeni=2616/52, ceka potvrdu=false
[agent ] VOICE   : VOICE TTS ElevenLabs -> 94502 B (audio/mpeg) u 1589 ms
[sustav] AUDIT   : CHAT_TURN turn conv=8c1b7c94 customer=2 input=Da, potvrđujem. -> OK (4705 ms) | Kupnja HT bona od 10 € je uspješno izvršena. Stanje Vašeg walleta sada je 15 €. …
```

U auditu je transkript, ne snimka; u logu je `customer#2`, ne e-mail. Sve između STT-a i TTS-a je Faza 4 bez izmjene: isti alati, ista brana potvrde kroz dva turna, isti token kupca.

Dvije i pol minute kasnije, bez ikoga za tipkovnicom:

```
[sustav] ANALYSIS: ANALYSIS start conv=8c1b7c94 turns=2 toolCalls=3 znakova=845
[sustav] AUDIT   : ANALYSIS analyze conv=8c1b7c94 customer=2 input=845 znakova transkripta (pseudonimizirano) -> OK (1692 ms) | KUPNJA / RIJESENO / POZITIVNO, ocjena 5, pravila poštovana, rupe u znanju 0, prijedloga 0: Kupac je želio kupiti HT bon od 10 eura. Kupnja je uspješno izvršena nakon potvrde kupca.
[sustav] ANALYSIS: ANALYSIS raspored: analizirano 2 razgovora [8c1b7c94, d9e89175]
```

A razgovor u kojem je Ana pitala za R1 račun (o tome baza znanja nema ništa) → prijedlog → admin ga odobri s ispravljenim odgovorom → agent to zna:

```
[sustav] ANALYSIS: ANALYSIS start conv=fcacbfb2 turns=2 toolCalls=2 znakova=912
[sustav] ANALYSIS: LEARN prijedlog #1 iz conv=fcacbfb2: 'Mogu li dobiti R1 račun za kupnju bonova putem NotTrilix web shopa?'
[sustav] ANALYSIS: ANALYSIS conv=fcacbfb2 OK 7971 ms -> KOD_NE_RADI / RIJESENO / NEUTRALNO, ocjena 4, pravila poštovana, rupe u znanju 1, prijedloga 1: Kupac je imao problem s nevažećim Steam kodom …
[sustav] KB      : KB INGEST nauceno-1 -> 1 odjeljak (naučeno: 'Mogu li dobiti R1 račun za kupnju bonova putem NotTrilix web shopa?')
[sustav] LEARN   : LEARN #1 odobren (admin): 'Mogu li dobiti R1 račun …' -> u bazi znanja kao nauceno-1
[sustav] KB      : KB SEARCH q='Trebam račun na firmu za bon, kako to ide?' topK=3 threshold=0.35 -> 3 hits (best: nauceno-1 › Mogu li dobiti R1 račun … score=0.597)
```

## 7. API

| Metoda i putanja | Tko | Opis |
|---|---|---|
| `POST /api/agent/voice` (multipart `audio`, `conversationId?`) | CUSTOMER | glasovna poruka; 403 `CONSENT_REQUIRED` bez privole, 503 `AGENT_UNAVAILABLE` / `VOICE_UNAVAILABLE` |
| `GET /api/shop/privacy` | CUSTOMER | rokovi čuvanja, verzija teksta privole, privole, broj spremljenih razgovora |
| `POST /api/shop/privacy/consent` `{type}` · `DELETE /api/shop/privacy/consent/{type}` | CUSTOMER | privola dana / povučena (`VOICE`) |
| `GET /api/shop/privacy/export` | CUSTOMER | JSON sa svim podacima kupca (preuzimanje) |
| `POST /api/shop/privacy/erase-conversations` | CUSTOMER | brisanje razgovora → `{deletedMessages, anonymizedEvents, erasedConversations, anonymizedAnalyses}` |
| `GET /api/admin/conversations?limit=` · `GET …/stats` · `GET …/{id}` | ADMIN | razgovori s nalazom, statistika (kategorije, ishodi, raspoloženje, prosječna ocjena), transkript |
| `POST /api/admin/conversations/{id}/analyze` · `POST …/analyze-idle` | ADMIN | analiza odmah (inače raspored) |
| `GET /api/admin/kb/suggestions?status=` · `POST …/{id}/approve` `{question, answer}` · `POST …/{id}/reject` | ADMIN | prijedlozi za bazu znanja |
| `POST :8090/voice` (multipart `audio`, `conversationId?`, `audioContentType`) + token kupca | sustav → agent | ulaz glasa u agenta |

Tekstualni chat (`POST /api/agent/chat`) i MCP su nepromijenjeni.

## 8. Pokretanje

```powershell
.\run-phase-5.ps1             # build oba projekta, pa sustav, pa agent (traži openai.key; elevenlabs.key opcionalan)
.\run-phase-5.ps1 -NoBuild -Reset
python scripts\phase5_e2e.py  # glas (probna snimka iz OpenAI TTS-a), privatnost, analiza, učenje; ili pojedinačno: voice | privacy | analysis | learning
python scripts\agent_e2e.py   # regresija Faze 4 (scenariji A–H)
```

`run-phase-1.ps1` uz `openai.key` čita i `elevenlabs.key` (`ELEVENLABS_API_KEY`, `NOTTRILIX_TTS=elevenlabs`); bez njega agent govori kroz OpenAI TTS.
Konfiguracija sustava: `nottrilix.analysis` (`enabled`, `idle-minutes: 2`, `poll-seconds: 60`, `max-suggestions-per-conversation: 3`),
`nottrilix.privacy` (`conversation-retention-days: 30`, `analysis-retention-days: 365`, `voice-consent-version: "2026-09"`, `retention-cron`).

## 9. Što je dodano i promijenjeno

**Sustav (korijen repoa)**
- Flyway `V5__conversations_privacy.sql`: `conversations`, `conversation_analyses`, `kb_suggestions`, `consents`.
- Paket `analysis` (novo): `ConversationAnalyzer`, `ConversationInsight`, `AnalysisScheduler`, `AnalysisConfig`, `AnalysisProperties`, `AnalysisFailedException`.
- Paket `privacy` (novo): `PrivacyService`, `RetentionJob`, `Pseudonymizer`, `PrivacyProperties`, `ConsentRequiredException`.
- `service/ConversationService` (okvir razgovora, transkript, popis, statistika), `kb/KnowledgeLearningService`, `KnowledgeBaseIngestor.ingestLearned`.
- Domena: `Conversation`, `ConversationAnalysis`, `KbSuggestion`, `Consent` + enumi (`ConversationStatus`, `AnalysisCategory`, `AnalysisOutcome`, `Sentiment`, `SuggestionStatus`, `ConsentType`); `AuditEventType` + `ANALYSIS`, `PRIVACY`.
- API: `PrivacyController`, `ConversationController`, `KnowledgeBaseController` (prijedlozi), `AgentController` (`POST /api/agent/voice`), `ApiExceptionHandler` (403 `CONSENT_REQUIRED`, 503 `ANALYSIS_FAILED`), DTO-i.
- `AgentChatService` (glas, privola, okvir razgovora), `AgentClient.voice` (multipart prema agentu), `NotTrilixApplication` `@EnableScheduling`.
- Logovi bez e-maila: `RequestLoggingFilter`, `SecurityConfig`, `AuditedToolHandler`.
- Baza znanja: `kb/10-privatnost-i-osobni-podaci.md`.
- `application.yml`: chat model `gpt-4o` (analiza), `nottrilix.analysis.*`, `nottrilix.privacy.*`, multipart 10 MB.

**Agent (`agent\`)**
- `VoiceService`, `VoiceProperties`, `AgentController` (`POST /voice`), `ApiErrors` (`VOICE_UNAVAILABLE`), `pom.xml` (+ ElevenLabs starter), `application.yml` (STT/TTS), system prompt (kanal VOICE, privatnost; bez e-maila kupca).

**Frontend**
- Shop: `ChatWidget` (mikrofon, privola, reprodukcija), `PrivacyModal`, `App` (gumb Privatnost). Admin: `ConversationsTab`, `SuggestionsPanel` (u tabu Znanje), `App` (tab Razgovori).

**Ostalo**: `run-phase-5.ps1`, `run-phase-1.ps1` (ElevenLabs ključ), `scripts/phase5_e2e.py`, `docs/GDPR.md`.

**Testovi**: sustav 47 (+ `PseudonymizerTest`), agent 19.

## 10. Verificirano (2026-09-30)

Svježa baza (`-Reset`), stvarni OpenAI (`gpt-4o`, `gpt-4o-mini-transcribe`) i ElevenLabs (`eleven_multilingual_v2`), sve pokrenuto kroz `run-phase-5.ps1`.

**Regresija Faze 4 (`scripts\agent_e2e.py`)**: scenariji A–H s istim ishodima kao u Fazi 4 (Ana 115 → 95 €, Marko 25 → 15 €, Ivana 50 → 100 €; potvrde, odustajanje, blokada; tuđi podaci, zaliha i tuđi `conversationId` odbijeni).

**Faza 5 (`scripts\phase5_e2e.py`)**

| Scenarij | Ishod |
|---|---|
| Glas (Marko) | bez privole 403 `CONSENT_REQUIRED`; privola (verzija `2026-09`); snimka „Kupi mi HT bon od deset eura.“ → STT 1,3 s `Kupi mi HT bon od 10 eura.` → prijedlog kupnje (`getCatalog`, `purchaseVoucher` → `CONFIRMATION_REQUIRED`) → TTS ElevenLabs 1,9 s, 77 KB mp3; „Da, potvrđujem.“ → STT 0,7 s → `confirmPendingAction` → wallet 25 → 15 €. Glasovni turn iz sustava: 8,3 s odnosno 4,7 s (STT + model + alati + TTS) |
| Privatnost (Ivana) | „Što sve čuvate o meni?“ → agent odgovara iz `10-privatnost-i-osobni-podaci.md` (3 odlomka); pregled; izvoz JSON (2 transakcije, 1 razgovor s 2 poruke); brisanje: 2 poruke obrisane, 1 događaj anonimiziran, 1 razgovor `ERASED`; nakon toga pregled 0 razgovora, izvoz bez razgovora; u auditu `PRIVACY consent / export / erase` (izvoz prije brisanja anonimiziran, sam zahtjev za brisanje ostaje uz kupca) |
| Analiza (Ana: Steam kod ne radi + R1 račun) | nalaz `KOD_NE_RADI / RIJESENO / NEUTRALNO`, ocjena 4, pravila poštovana, rupa u znanju „R1 račun za kupnju bonova“, prijedlog #1 s nacrtom `PROVJERITI: …`; 8 s, 1457 / 262 tokena; audit `ANALYSIS analyze`; statistika kroz `/stats` |
| Učenje | admin odobri prijedlog s ispravljenim odgovorom → `KB INGEST nauceno-1`, audit `ANALYSIS learn`; pretraga „račun na firmu R1“ vraća `nauceno-1` prvi (0,53); Marko u novom razgovoru pita „Trebam račun na firmu za bon“ → odgovor iz `nauceno-1` (0,60) s odobrenim postupkom |
| Raspored | 2,5 min nakon zadnje poruke `AnalysisScheduler` sam analizira utihnule razgovore: glasovni `KUPNJA / RIJESENO / POZITIVNO`, ocjena 5 (1,7 s); Markov o R1 `INFORMACIJA / RIJESENO`, 5; admin vidi `0 otvoreno · 3 analizirano · 1 obrisano`, prosjek 4,7 / 5, `0 čeka · 1 odobreno` |
| Retencija | pri startu `PRIVACY retention: rok razgovora 30 dana, analiza 365 dana -> 0 poruka obrisano, …` (svježa baza nema ništa za brisanje) |

**Preglednik**: admin tab Razgovori (statistika, popis s nalazima, obrisani razgovor kao „anonimno / Obrisan“, kanal `VOICE`, transkript), tab Znanje (naučeni odlomci, prijedlozi),
shop: odjeljak Privatnost (privola, izvoz, brisanje) i chat s gumbom mikrofona, tekstom privole i obavijesti o obradi. Snimanje mikrofonom nije vježbano u probnom pregledniku (nema mikrofona);
glasovni put je pokriven skriptom kroz isti endpoint i istu multipart poruku koju šalje `ChatWidget`.

**Popravljeno tijekom verifikacije**
- Sustav se pri prvom startu srušio: `RetentionJob.onStartup()` je zvao `run()` iznutra, pa `@Transactional` na `run()` nije vrijedio (samopozivanje ne prolazi kroz Spring proxy) →
  „No active transaction for update or delete query“. Isti obrazac je čekao u `analyzeIdle()`: analiza bi prošla, ali promjena statusa razgovora ne bi bila spremljena, pa bi raspored isti razgovor
  analizirao svake minute. Rješenje: `@Transactional` na razini klase `RetentionJob`; petlja po utihnulim razgovorima u `AnalysisScheduler`, koji `analyze()` zove kroz proxy (svaki razgovor svoja transakcija).
- STT bez domenskog prompta (poglavlje 3): prva glasovna proba je prošla kroz cijeli lanac, ali s transkriptom „Kopimi ha tebón od deset eura.“ i „Da, potwierdziłem.“ — agent je pitao koji bon,
  a analiza je taj razgovor točno ocijenila kao `KUPNJA / NERIJESENO`, ocjena 3. Post-analiza je uhvatila kvar kanala prije nego što bismo ga mi primijetili.
- Probno pitanje za učenje: „studentski popust“ baza znanja već pokriva (cijena = nominalna vrijednost), pa rupe nije bilo; R1 račun je stvarna rupa.

**Brojke**: STT 0,5–1,3 s, TTS ElevenLabs 1,1–3,2 s za jednu do dvije rečenice (42–95 KB mp3), analiza 1,7–8 s i oko 1.450 ulaznih tokena za razgovor od dva turna; glasovni turn ukupno 4,7–8,3 s (tekstualni 1–5 s).
Glas v2 (streaming, poglavlje 3): transkript 0,5 s, prvi zvuk 1,6–2,7 s od slanja snimke.

## 11. Pouke

| Što se pokazalo | Što to znači |
|---|---|
| Glas nije promijenio ni jedan redak agenta, MCP-a ni sustava | Kanal je omotač oko „tekst unutra, tekst van“. Sve granice iz Faze 4 vrijede i za govor, jer glas do sustava stiže kao ista poruka s istim tokenom. |
| Audit trag iz Faze 4 je bio dovoljan za transkript | Ono što je bilježeno radi nadzora poslužilo je i za analizu, izvoz kupcu i brisanje. Jedan zapis, tri svrhe. |
| Strukturirani nalaz se može brojati; slobodni tekst ne | Enumi i ocjena daju statistiku (koliko NERIJEŠENO, koliko prekršenih pravila) umjesto hrpe sažetaka. |
| Rupa u znanju je najkorisniji izlaz analize | Model sam kaže „ovo baza znanja nije znala“; to je popis posla za admina, ne za programera. |
| Samoučenje bez čovjeka je rizik | Model bi bez provjere „naučio“ vlastitu pogrešku ili izmišljeno pravilo; odobravanje s ispravkom čuva istinitost baze znanja. |
| GDPR je najjednostavniji kad je kod | Rok čuvanja je posao po rasporedu, izvoz je upit, brisanje je transakcija, privola je redak s verzijom. Ništa od toga nije teško; teško je to ne preskočiti. |
| Pseudonimizacija pravilima ima granice | Kosi padeži hrvatskih imena prolaze regexu; produkcija = NER ili manje podataka prema modelu. |
| Streaming nije stream dok ga netko ne flusha | 8 KB međuspremnik odgovora i `flush()` koji ga ne prazni: mjeriti na klijentu po chunkovima, ne vjerovati logu servera. Glas koji „radi“ i glas koji je fluidan razlikuju se u jednom `flushBuffer()`. |

## 12. Poznata ograničenja

- **Turn po turn, ne razgovor u stvarnom vremenu**: kupac govori, stane, sluša (STT + model + prvi zvuk 1,6–2,7 s); prekid Nole je klik, ne govor. Telefon traži streaming zvuka u oba smjera s prekidanjem govorom (Faza 6).
- **Privola je po vrsti obrade i verziji teksta**, ne po izvršitelju; promjena pružatelja TTS-a traži novu verziju teksta.
- **Pseudonimizacija je regex** (vidi pouke); model za analizu i dalje vidi sadržaj razgovora (kupnje, iznosi).
- **Retencija analiza čita sve nalaze** (`findAll`) — za demo dovoljno, za produkciju upit po datumu.
- **Brisanje računa** kupca nije napravljeno (samo brisanje razgovora); zahtjev ide voditelju (piše u bazi znanja).
- **Analiza jednog razgovora traje 5–15 s i košta tokene**: raspored svake minute analizira samo utihnule razgovore, ali u demou s mnogo razgovora to je vidljiv trošak.
- Ključevi izvršitelja su u datotekama (`openai.key`, `elevenlabs.key`, gitignored); produkcija = sef za tajne, HTTPS, potpisani ugovori o obradi.

## 13. Sljedeće (Faza 6 — telefonija)

Poziv s mobitela: telefonski kanal nema token kupca ni preglednik. Pozivatelja treba prepoznati (broj, provjera identiteta), agentu dati vjerodajnicu u njegovo ime,
a glas streamati u oba smjera s prekidanjem. Sustav, MCP i agent bi trebali ostati kakvi jesu; mijenja se samo ono što je ispred agenta.
