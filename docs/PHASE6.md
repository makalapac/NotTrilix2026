# Faza 6 — Telefon: poziv s mobitela u istog agenta (Infobip)

_Status: **kod gotov, simulator verificiran 2026-10-02, živi poziv s mobitela prošao 2026-10-09** (HR broj +385 1 5809 291) · grana `phase6` (iz `phase5`) · što je živi test tražio da se popravi: poglavlje 9._

Faza 5 je dala glas u pregledniku: kupac je prijavljen, klikne mikrofon, govori, sluša. Faza 6 miče preglednik: **kupac bira telefonski broj**.
Nema prijave, nema tokena, nema ekrana; postoji samo zvuk s jedne i broj pozivatelja s druge strane.

Pitanje faze: **tko zove, i tko mu smije dati ruke?** Odgovor u tri retka, ostatak dokumenta ih razrađuje:

| Tema | Što je napravljeno | Što se pritom nije mijenjalo |
|---|---|---|
| **Tko zove** | sustav prepoznaje kupca po **broju pozivatelja** i agentu izda **token kupca za trajanje poziva**; s nepoznatog broja nema radnji na računu | ista vjerodajnica kao u chatu: svaki MCP poziv nosi token, brana potvrde i audit u sustavu vrijede i za telefon |
| **Kako zvuk putuje** | Infobip javi poziv i premosti ga na **WebSocket agenta**: 20 ms okviri PCM16 u oba smjera; VAD reže iskaze, STT, isti agent, TTS izravno u PCM, upad pozivatelja prekida Nolu | agent je i dalje „tekst unutra, tekst van“; glas u pregledniku i telefon dijele iste Spring AI modele |
| **Što sustav zna o pozivu** | agent prijavljuje sustavu početak (identitet), svaki turn (transkript i odgovor) i kraj poziva; iz toga nastaju audit, transkript, analiza i prava kupca kao i za chat | tablice iz Faze 5: `conversations`, `audit_events` (kanal `PHONE`), bez nove migracije |

Telefonija je, kako je i planirano, cijev za zvuk. Ono što je novo nije govor nego **identitet bez prijave**.

---

## 1. Kako je složeno

```
 mobitel ── PSTN ──> Infobip (HR broj) ── webhook CALL_RECEIVED ──> agent :8090  /infobip/call-received
                                                                      │  POST /api/phone/calls {callId, from} ──> sustav: kupac po broju -> token kupca (traje do kraja poziva)
                                                                      │  Calls API: answer
                     ── webhook CALL_ESTABLISHED ──> /infobip/event ──┘  Calls API: dialog = most poziva na WEBSOCKET endpoint (media-stream config)

 Infobip <═══ wss://<tunel>/infobip/media ═══> agent: prva poruka JSON metadata, zatim 20 ms okviri PCM16 @ 16 kHz u oba smjera
     pozivatelj → VAD (iskaz) → STT (Spring AI TranscriptionModel, WAV) → AgentService.chat(token, callId, tekst, PHONE)
                                                                              └─> MCP alati u sustavu s tokenom kupca (kao chat)
     Nola ← okviri u stvarnom vremenu (20 ms takt) ← TTS (Spring AI, ElevenLabs pcm_16000) ← odgovor; upad pozivatelja prazni red

 kraj poziva ── webhook CALL_FINISHED ──> /infobip/event ──> POST /api/phone/calls/{id}/end ──> token povučen, audit PHONE_CALL
```

Dvije ravnine, kao i kod svakog telefonskog pružatelja: **kontrolna** (webhookovi i Calls API: javi se, premosti, prekini) i **medijska** (WebSocket s golim zvukom).
Agent drži obje; sustav o telefoniji ne zna ništa osim da je kanal `PHONE`.

## 2. Odluke

| Odluka | Razlog |
|---|---|
| **Pozivatelj = broj telefona → kupac → token kupca** (`PhoneSessionService`, `POST /api/phone/calls`) | Granica iz Faze 4 ostaje ista: identitet putuje kao vjerodajnica koju sustav sam izdaje i provjerava. Agent ne odlučuje tko zove; sustav mu kaže i da mu token. Token živi koliko i poziv (`end` ga povlači). |
| **Nepoznat broj: pozdrav, objašnjenje, kraj poziva** | Bez tokena nema alata, a razgovor „samo o pravilima“ bez identiteta u demou unosi zbrku. Pošteno i jednostavno: „broj nije u računu, prijavite se u web shop“. |
| **Broj pozivatelja se maskira** u logu i auditu (`385 91***222`) | Broj je osobni podatak nepoznate osobe kad poziv ne pripada kupcu; kupčev je ionako u računu. |
| **Demo pozivatelj iz okoline** (`NOTTRILIX_DEMO_CALLER`, datoteka `demo-caller.txt` u `.gitignore`) | Predavač zove sa svog mobitela, a njegov broj ne ide u seed ni u repo. Broj se mapira na kupca 1 (Ana). |
| **Turnovi poziva idu kroz `/api/phone/calls/{id}/turns`**, ne kroz `/api/agent/chat` | Chat i glas u pregledniku prolaze kroz sustav jer sustav drži prijavu; telefon ulazi u agenta izvana, pa agent sustavu prijavljuje što se dogodilo. Rezultat je isti: `CHAT_TURN` s kanalom `PHONE`, okvir razgovora, analiza, izvoz, brisanje. |
| **Isti Spring AI modeli kao u pregledniku**: `TranscriptionModel` (WAV iz PCM), `TextToSpeechModel` s `outputFormat pcm_16000` | Telefonska linija je PCM16; ElevenLabs ga daje izravno, pa ništa ne transkodiramo. OpenAI TTS bi dao 24 kHz PCM i tražio resampling: telefon zato traži ElevenLabs. |
| **VAD energijom** (RMS po 20 ms okviru: 240 ms govora započinje iskaz, 700 ms tišine ga završava, 300 ms pre-rolla) | Dovoljno za demo i razumljivo na slajdu; model VAD-a (Silero) bi bio bolji u buci, ali je još jedan model. |
| **Upad pozivatelja (barge-in)**: govor dok Nola govori prazni red za slanje | Telefon bez toga ne djeluje kao razgovor. Pozdrav nepoznatom pozivatelju se ne da prekinuti (treba ga čuti do kraja). |
| **„Samo trenutak.“** nakon 1,2 s čekanja | Isto što i u pregledniku: tišina na liniji je gora od fraze. |
| **Jedan poziv = jedan razgovor** (`conversationId` = Infobipov `callId`) | Memorija agenta, brana potvrde i transkript vežu se uz poziv; potvrda kupnje radi kroz dva iskaza kao kroz dvije poruke. |
| **Simulator poziva je dio projekta** (`scripts/phone_sim.py` glumi Infobip: webhookovi + WebSocket + PCM) | Cijeli medijski put se vježba bez broja, tunela i troška; živi poziv dodaje samo Infobip. |
| **Javni URL = cloudflared quick tunnel**, media-stream config se sam prepiše pri startu, webhookovi se postavljaju u portalu | Tunel mijenja adresu svakim startom. Media config ima API, pretplate na događaje na ovom računu nemaju (`/subscriptions` → 404), pa ostaju u portalu. |
| **Bez provjere potpisa webhooka i bez ograničenja pristupa na `/infobip/**`** | Demo. U produkciji: potpis ili tajna u URL-u, dopuštene IP adrese pružatelja, HTTPS bez tunela. |

## 3. Sustav: `/api/phone` (uloga AGENT)

| Metoda i putanja | Tijelo | Što radi |
|---|---|---|
| `POST /api/phone/calls` | `{callId, from, to}` | kupac po broju (`CustomerRepository.findByPhoneDigits`, usporedba znamenki; `PhoneNumbers`) → `TokenService.issue` → `{identified, customerId, customerName, customerToken}`; audit `PHONE_CALL identify` (OK ili DENIED) |
| `POST /api/phone/calls/{id}/turns` | `{customerToken, message, reply, durationMs, promptTokens, completionTokens}` | token → kupac; audit `CHAT_TURN` kanal `PHONE`; `conversations.recordTurn` |
| `POST /api/phone/calls/{id}/end` | `{customerToken, reason, durationMs, turns}` | token povučen; audit `PHONE_CALL end` |

Agent te pozive šalje svojim ključem (realm `AGENT`); `SecurityConfig`: `/api/phone/**` → `hasRole("AGENT")`. Sve ostalo u sustavu (MCP alati, brana, baza znanja, analiza, GDPR) vidi telefon kao još jedan kanal.

## 4. Agent: paket `telephony`

| Klasa | Uloga |
|---|---|
| `InfobipWebhookController` | `/infobip/call-received` (identitet + javljanje), `/infobip/event` (`CALL_ESTABLISHED` → most, `CALL_FINISHED` → kraj), `/infobip/setup`, `/infobip/status`; pri startu prepiše media-stream config na `wss://<javni URL>/infobip/media` i ispiše webhook adrese |
| `InfobipCallsClient` | Calls API: `answer`, `dialogs` (most na `WEBSOCKET` endpoint), `hangup`, media-stream config (PUT ili POST) |
| `PhoneGateway` | veza prema sustavu: identitet, turn, kraj |
| `CallRegistry`, `PhoneCall` | pozivi u tijeku: tko zove, token, je li premošten, broj turnova |
| `InfobipMediaWebSocketHandler` | medijska ravnina: metadata → `CallSession`; okviri → VAD → iskaz → `turn()`; pacer šalje jedan okvir svakih 20 ms; upad, filler, prekid, kraj |
| `CallSession`, `PcmUtil` | VAD i red za slanje; RMS, WAV omot, rezanje na okvire |
| `TelephonySpeech` | STT (`TranscriptionModel`, WAV) i TTS (`TextToSpeechModel` → `ElevenLabsTextToSpeechOptions.outputFormat("pcm_16000")`), pozdrav s imenom, pozdrav nepoznatom, filler |
| `TelephonyProperties` | `nottrilix.telephony.*`: Infobip, javni URL, sample rate, tekstovi, VAD pragovi |

Jedan iskaz pozivatelja prolazi isti put kao glasovna poruka iz preglednika, samo bez HTTP-a oko sebe: `transcribe` → `AgentService.chat(token, callId, tekst, "PHONE")` → `SpeechText` → `speak` → okviri u red.
Prompt za kanal `PHONE` traži isto što i za `VOICE` (kratko, bez znamenki) i kaže modelu da je pozivatelj prepoznat po broju.

## 5. Infobip: što treba na računu

| Što | Stanje / kako |
|---|---|
| Račun i ključ | `infobip.key` (redak 1 ključ, redak 2 bazni URL); stanje računa se vidi s `GET /account/1/balance` |
| Calls configuration | postoji od lanjske probe (`cabb314c-…`, ime „NotTrilix“) |
| Media-stream config (`WEBSOCKET_ENDPOINT`, 16000 Hz) | postoji (`6a6cec2b…`); agent je pri startu prepiše na aktualni tunel |
| Pretplate na događaje (notification profiles) | u portalu: `CALL_RECEIVED` → `https://<tunel>/infobip/call-received`, ostali događaji → `…/infobip/event`, s filtrom na Calls configuration (bez filtra Infobip odbija poziv: „The received request was rejected“) |
| Broj | HR fiksni broj (Zagreb 01): ~8,80 € mjesečno + ~8,80 € aktivacija, kupnja u portalu ili `POST /numbers/1/numbers`; broj se usmjeri na Calls configuration (`FORWARD_TO_SUBSCRIPTION`) |
| Bez broja | Call Link (WebRTC iz preglednika na istu Calls configuration) iz portala |
| Trošak poziva | ~0,012 €/min dolazni + 0,002 €/min streaming: demo poziv od 5 min ≈ 0,07 € |

## 6. Pokretanje

```powershell
.\run-phase-6.ps1                  # tunel + sustav + agent (traži openai.key, elevenlabs.key; infobip.key za živi poziv)
.\run-phase-6.ps1 -NoBuild -Reset
.\run-phase-6.ps1 -NoTunnel        # bez javnog URL-a: samo simulator
python scripts\phone_sim.py        # glumi Infobip: Ana zove, pita za wallet, kupi HT bon uz potvrdu
python scripts\phone_sim.py --from "+385 99 000 0000"   # nepoznat pozivatelj
```

Skripta učitava `infobip.key` (u `INFOBIP_API_KEY`, `INFOBIP_BASE_URL`), `demo-caller.txt` (u `NOTTRILIX_DEMO_CALLER`), diže `cloudflared tunnel --url http://localhost:8090`,
iz njegova loga pročita `https://…trycloudflare.com` i preda ga agentu kao `NOTTRILIX_PUBLIC_URL`. Agent pri startu prepiše media-stream config i u log ispiše dvije webhook adrese za portal.

**Tunel odvojeno od aplikacija (za demo):** quick tunnel mijenja URL pri svakom startu, a webhookovi u portalu se mijenjaju ručno.
Zato se tunel pokrene jednom, u vlastitom prozoru, a aplikacije se dižu (i restartaju) s tim URL-om:

```powershell
& "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8090 --no-autoupdate   # ispiše https://<x>.trycloudflare.com
.\run-phase-6.ps1 -NoBuild -PublicUrl https://<x>.trycloudflare.com                                       # bez vlastitog tunela
```

U portalu (Developer Tools → Subscriptions Management → Notification profiles) profil `NotTrilix` dobije `https://<x>…/infobip/call-received`,
profil `NotTrilix2` dobije `https://<x>…/infobip/event`; pretplata (kanal VOICE_VIDEO, filtar na Calls konfiguraciju) ostaje. Prvi quick-tunnel
zahtjev zna isteći (`context deadline exceeded`): ponoviti. Broj je u portalu usmjeren na Calls konfiguraciju („Forward to Subscription“).

## 7. Jedan poziv u logu

Simulirani poziv (`scripts\phone_sim.py`): Ana zove, pita za wallet, kupi HT bon uz potvrdu. Sustav i agent u istoj konzoli, skraćeno:

```
[sustav] AUDIT     : PHONE_CALL identify conv=sim-1790974413 customer=1 input=38591***222 -> OK | pozivatelj prepoznat po broju telefona, token za agenta izdan
[agent ] TELEPHONY : CALL sim-1790974413 dolazni poziv, pozivatelj customer#1 (Ana Anić)
[agent ] TELEPHONY : CALL sim-1790974413 media stream otvoren (16000 Hz, 640 B/okvir), pozivatelj customer#1
[agent ] TELEPHONY : CALL sim-1790974413 pozivatelj (3600 ms, STT 672 ms): 'Kupi mi HT bon od 10 eura.'
[sustav] MCP       : MCP tools/call purchaseVoucher user=customer#1 conv=sim-1790974413 turn=2 input={"productId":5} -> CONFIRMATION_REQUIRED
[agent ] TELEPHONY : CALL sim-1790974413 Nola (agent 3132 ms, TTS prvi zvuk 205 ms / ukupno 1690 ms, 14560 ms govora): 'Kupnja HT bona od 10 € čeka Vašu potvrdu. …'
[sustav] PHONE     : PHONE call=sim-1790974413 turn customer#1 OK 5494 ms
[agent ] TELEPHONY : CALL sim-1790974413 pozivatelj (1320 ms, STT 529 ms): 'Otvoriđujem.'
[sustav] MCP       : CONFIRM PURCHASE 5 conv=c1:sim-1790974413 predlozeno u turnu 2, potvrdjeno u turnu 3
[agent ] TELEPHONY : CALL sim-1790974413 Nola (agent 2504 ms, TTS prvi zvuk 267 ms / ukupno 2303 ms, 19440 ms govora): 'Kupnja HT bona od 10 € je uspješno izvršena. …'
[agent ] TELEPHONY : CALL sim-1790974413 media stream zatvoren (CloseStatus[code=1000]), 3 turnova
[sustav] AUDIT     : PHONE_CALL end conv=sim-1790974413 customer=1 input=media-closed -> OK (88129 ms) | 3 turnova, 88 s
[sustav] PHONE     : PHONE call=sim-1790974413 end reason=media-closed turns=3 88 s, token povučen=true
```

Isti alati, ista brana, isti audit kao u chatu; kanal je `PHONE`, razgovor je `callId`, a tokena nakon poziva više nema. Nepoznat broj:

```
[sustav] AUDIT     : PHONE_CALL identify conv=sim-1790974502 customer=null input=38599***000 -> DENIED | pozivatelj nije prepoznat po broju: razgovor bez radnji na računu
[agent ] TELEPHONY : CALL sim-1790974502 media stream otvoren (16000 Hz, 640 B/okvir), pozivatelj nepoznat
[agent ] TELEPHONY : CALL sim-1790974502 kraj (done)                      <- pozdrav s objašnjenjem odsviran do kraja, pa prekid
[sustav] AUDIT     : PHONE_CALL end conv=sim-1790974502 customer=null input=media-closed -> OK (17814 ms) | 0 turnova, 17 s
```

## 8. Verificirano

**Simulator (2026-10-02, `run-phase-6.ps1 -NoTunnel`, stvarni OpenAI STT i ElevenLabs TTS, Infobip bez poziva)**

| Scenarij | Ishod |
|---|---|
| Ana zove (broj iz seeda) | `identified: true`, pozdrav imenom (prvi zvuk 0,33 s nakon metadata, streaming TTS); „Koliko imam na walletu?“ → `getMyWallet`, odgovor sa stanjem; „Kupi mi HT bon od deset eura.“ → `purchaseVoucher` → `CONFIRMATION_REQUIRED`; „Da, potvrđujem.“ → `confirmPendingAction` → kupnja izvršena, wallet 55 → 45 €; 3 turna u auditu (`CHAT_TURN`, kanal `PHONE`), `PHONE_CALL identify` i `end`, token povučen |
| Nepoznat broj | `identified: false`, `PHONE_CALL identify DENIED`; Nola kaže da broj nije u računu i prekine poziv (17 s); bez turnova, bez tokena |
| Upad pozivatelja | govor dok Nola govori prazni red za slanje (`upad: … 2460 ms neizgovoreno`) |
| Kraj poziva | zatvaranje WebSocketa ili `CALL_FINISHED` → jedan `end` (zaštita od dvostrukog zatvaranja) |

**Brojke po turnu** (simulator, 16 kHz PCM): kraj govora → kraj iskaza 0,7 s (VAD) · STT 0,5–0,9 s · agent 2,5–3,1 s · TTS prvi zvuk 0,15–0,27 s nakon odgovora ·
„Samo trenutak.“ 1,2 s nakon iskaza ako odgovor još nije tu. Pozivatelj čuje prvi zvuk 2,3–2,9 s nakon što zašuti, a odgovor na pitanje 4–5 s.

**Živi poziv kroz Infobip (2026-10-09, mobitel → HR broj +385 1 5809 291, četiri poziva):** prepoznavanje po broju (`demo-caller.txt`), pozdrav imenom, wallet, kupnja HT bona uz potvrdu, „kod ne radi“ s provjerom kod pružatelja, prijedlog povrata, tri upada u riječ, odbijanje zahtjeva izvan opsega, odgovor o privatnosti iz baze znanja; sustav je razgovore poslije sam analizirao (KUPNJA / RIJEŠENO, ocjena 5, rupa u znanju „je li razgovor tajan“ → prijedlog odlomka). **Brojke po turnu:** kraj govora → kraj iskaza 0,7 s (VAD) · STT 0,5–1,1 s (`gpt-4o-transcribe`) · agent 2,2–6,1 s (`gpt-5.6-terra`; dva poziva modela kad ima alata) · TTS prvi zvuk 0,15–0,25 s · most na WebSocket 0,6 s nakon `CALL_RECEIVED`, `CALL_ESTABLISHED` odmah zatim. Pozivatelj čuje „Provjeravam.“ oko 3 s nakon što zašuti, odgovor 4–8 s. Tri od četiri poziva prvo su pukla; svaki uzrok i popravak su u poglavlju 9.

**Testovi**: sustav 50 (+ `PhoneNumbersTest`), agent 28 (+ `CallSessionTest`: VAD, PCM okviri, streamani chunkovi bez praznina).

## 9. Pouke

| Što se pokazalo | Što to znači |
|---|---|
| Telefon nije promijenio ni agenta ni sustav; promijenio je **tko izdaje identitet** | U chatu identitet dolazi iz prijave, na telefonu iz broja. Oboje završava kao isti token u istom zaglavlju. Kanal je omotač, vjerodajnica je granica. |
| Simulator je vrijedio više od dokumentacije | Cijeli medijski put (okviri, VAD, STT, agent, TTS, pacing, upad) vježba se lokalno, bez troška i bez čekanja pružatelja. Živi poziv onda dodaje samo dvije stvari: webhookove i broj. |
| Nepoznat pozivatelj je slučaj koji treba odlučiti, ne izbjeći | Najjednostavniji pošten odgovor: reci što se dogodilo i gdje se dalje može. |
| **Živi test, 2026-10-09:** `answer` preko API-ja bez medija ostavi PSTN poziv u `PRE_ESTABLISHED`; `CALL_ESTABLISHED` nikad ne stigne, pozivatelj čuje tišinu | Most odmah po `CALL_RECEIVED`: dialog s djetetom `WEBSOCKET`; platforma sama javi poziv kad se naša noga spoji. Lanjski WebRTC Call Link prikrivao je razliku. Stari put (`answer` + provjera stanja) ostaje kao rezerva. |
| Infobipov ID poziva ima 68 znakova; naši ID-evi razgovora 64 (`varchar(64)` u `audit_events`, validacije) | Dva poziva su prošla kao „nepoznat pozivatelj“ prije nego je itko pogledao duljinu. Agent za duge ID-eve izvede `tel-<digest>` i njega šalje sustavu; Infobipov ID ostaje prema Infobipu i u logu. Bez migracije. |
| **Noga bez okvira umire za ~0,3 s** | Pacer (okvir svakih 20 ms) ne smije blokirati: prva sinteza „Samo trenutak.“ (1–2,5 s) išla je s njegove niti, pa je svaki *prvi* poziv nakon starta pukao 0,3 s nakon što je filler trebao krenuti, a svaki drugi prošao. Sad: fraze sintetizirane pri startu (`TelephonySpeech.warmUp`), filler s druge niti, okvir tišine kad Nola šuti. U telefoniji je tišina greška, ne pauza. |
| STT na liniji griješi više nego u pregledniku | Linija je uskopojasna (8 kHz). Na 7 stvarnih iskaza: `gpt-4o-mini-transcribe` 3 greške (uklj. ćirilicu na „Da, potvrđujem“), `gpt-transcribe` 1 (ćirilica), `gpt-4o-transcribe` 0, ista brzina (0,8 s) → telefon ima svoj model (`nottrilix.telephony.stt-model`), preglednik ostaje na miniju. |
| Eho Nolina glasa izgleda kao govor pozivatelja | Prvi iskaz jednog poziva bio je doslovno kraj njezina pozdrava. Upad u riječ sad traži jasno glasniji zvuk (`vad.barge-in-rms: 1500`) od praga govora (500). |
| Prvi dohvat iz baze znanja nakon pauze traje ~1 s, ostali 0,15 s | Veza prema embedding servisu zahladi. Sustav je svakih 30 s grije (`KnowledgeBaseWarmup`), bez loga; prvi turn poziva više ne plaća sekundu. |
| Ista fraza „Samo trenutak.“ u svakom turnu iritira | Četiri kratke fraze u rotaciji, start nakon 1,5 s. Prava mjera je latencija modela: `NOTTRILIX_CHAT_MODEL=gpt-5.4-mini` skida oko sekundu po turnu, uz rizik izmišljanja pravila ([MODEL.md](MODEL.md)). |

## 10. Poznata ograničenja

- **Broj pozivatelja nije dokaz identiteta** (spoofing postoji). Za radnje s novcem produkcija bi tražila dodatnu provjeru (PIN, poziv natrag, jednokratni kod u aplikaciji); ovdje brana potvrde ostaje jedina zaštita.
- **VAD energijom** griješi u buci i pri tihom govoru; prag `vad.speech-rms` je ručno namješten.
- **Turn po turn**: Nola govori tek kad pozivatelj zašuti; nema simultanog slušanja (duplex), ali ima upad.
- **Jedan poziv istodobno** je pretpostavka demoa: media stream se po `callId` ne mora poklapati s pozivom koji smo javili, pa se uzima najnoviji poziv bez medija.
- **Webhookovi bez potpisa, tunel bez autentikacije**: samo za demo, ne ostavljati upaljeno.
- **Pretplate na događaje se mijenjaju ručno** u portalu pri svakoj promjeni tunela (Subscriptions API nije dostupan na računu); zato tunel u vlastitom prozoru (poglavlje 6).
- **Quick tunnel bez računa** nema jamstva; na dan demoa ga pokrenuti ranije i provjeriti `GET https://<x>/infobip/status` izvana.
- **Dump iskaza** (`NOTTRILIX_DUMP_UTTERANCES=<mapa>`) sprema zvuk pozivatelja kao WAV: samo za podešavanje STT-a i VAD-a, nikad u demou (zvuk kupca se inače ne čuva).

## 11. Što je dodano i promijenjeno

**Sustav**: `phone/PhoneSessionService`, `phone/PhoneNumbers`, `phone/PhoneProperties`, `api/PhoneController`, DTO-i `PhoneCallStart`, `PhoneIdentity`, `PhoneTurn`, `PhoneCallEnd`;
nakon živog testa: validacije ID-eva `[A-Za-z0-9_-]{1,128}` (`PhoneCallStart`, `PhoneController`, `mcp/McpCall`), `kb/KnowledgeBaseWarmup`;
`AuditEventType.PHONE_CALL`; `CustomerRepository.findByPhoneDigits`; `SecurityConfig` (`/api/phone/**` AGENT); `application.yml` (`nottrilix.phone.demo-caller`); admin tab Audit (oznaka „poziv“);
baza znanja `10-privatnost-i-osobni-podaci.md` (odjeljak „Poziv telefonom“). Testovi: `PhoneNumbersTest`.

**Agent**: paket `telephony` (poglavlje 4), `pom.xml` (+ `spring-boot-starter-websocket`), `application.yml` (`nottrilix.telephony.*`), system prompt (kanal `PHONE`). Testovi: `CallSessionTest`.
Nakon živog testa: `PhoneCall.conversationId` (izvedeni ID), `InfobipCallsClient.state`, dialog odmah po `CALL_RECEIVED` + provjera stanja kao rezerva (`InfobipWebhookController`),
`TelephonySpeech.warmUp` + fraze u rotaciji + `stt-model`, `CallSession.silenceFrame` + `bargeInRms`, `AgentService` validacija ID-a razgovora, dump iskaza za podešavanje; `run-phase-6.ps1 -PublicUrl`.

**Ostalo**: `run-phase-6.ps1`, `scripts/phone_sim.py`, `.gitignore` (`demo-caller.txt`), [GDPR.md](GDPR.md) (telefonski kanal).
