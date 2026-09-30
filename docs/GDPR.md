# GDPR — obrada osobnih podataka u NotTrilix podršci (agent, glas, analiza)

_Faza 5 · 2026-09-30 · demo za predavanje, ne pravni savjet. Pokazuje kako se načela GDPR-a pretvaraju u kod: što se čuva, koliko dugo, tko obrađuje, što kupac može sam._

## 1. Evidencija aktivnosti obrade (čl. 30, sažeto)

| Aktivnost | Svrha | Pravna osnova | Podaci | Izvršitelji obrade | Rok čuvanja |
|---|---|---|---|---|---|
| Prodaja bonova i wallet | izvršenje ugovora | čl. 6(1)(b) ugovor | ime, e-mail, telefon, kupnje, wallet, serijski brojevi, kodovi | — | dok traje račun + zakonski rokovi računovodstva |
| Razgovor s agentom (chat) | podrška kupcu, izvršenje radnji na računu | čl. 6(1)(b) ugovor; sigurnost čl. 6(1)(f) | ime kupca, poruke, odgovori agenta, pozivi alata | **OpenAI** (jezični model) | poruke **30 dana**; pozivi alata uz kupnju dok traje račun, bez veze s razgovorom nakon 30 dana |
| Glasovni razgovor | isto kao chat, glasom | **privola** čl. 6(1)(a) (obrada glasa kod vanjskih izvršitelja) | snimka (samo u prolazu), transkript | **OpenAI** (govor → tekst), **ElevenLabs** (tekst → govor) | snimka **0 dana** (ne pohranjuje se); transkript 30 dana |
| Post-analiza razgovora | kvaliteta podrške, otkrivanje rupa u znanju | legitimni interes čl. 6(1)(f) | pseudonimizirani transkript; nalaz (sažetak, tema, ishod, ocjena) | **OpenAI** | nalaz **365 dana**, nakon 30 dana **bez veze s kupcem** |
| Samoučenje baze znanja | poboljšanje odgovora | legitimni interes | poopćeno pitanje i odgovor (bez osobnih podataka) | — | trajno (nije osobni podatak) |
| Audit trag | sigurnost, dokazivost, nadzor agenta | legitimni interes; čl. 5(2) odgovornost | tko, što, kada, ishod | — | 30 dana s kupcem, zatim anonimizirano |
| Tehnički logovi | rad sustava | legitimni interes | identifikator kupca (`customer#2`), sadržaj poruka | — | **7 dana** (Logback rotacija) |

Voditelj obrade: NotTrilix (demo). Kontakt za privatnost: privatnost@nottrilix.hr (demo). Prijenos u treće zemlje: OpenAI i ElevenLabs su izvršitelji u SAD-u → ugovor o obradi (DPA) i standardne ugovorne klauzule; oba u API uvjetima ne koriste podatke za učenje modela. **U demou to nije potpisano** — točka za stvarnu implementaciju.

## 2. Načela u kodu

| Načelo (čl. 5) | Kako je izvedeno |
|---|---|
| **Smanjenje količine podataka** | agent od sustava dobiva samo ime kupca (ne e-mail) i samo podatke za konkretnu radnju (MCP alati, Faza 4); analiza dobiva **pseudonimizirani** transkript (`Pseudonymizer`: ime, e-mail, telefon, serijski broj, kod → `[KUPAC]`, `[E-MAIL]`, `[TELEFON]`, `[BON]`, `[KOD]`); snimka glasa se ne pohranjuje |
| **Ograničenje pohrane** | `RetentionJob` (pri startu i svake noći): poruke starije od `conversation-retention-days` (30) se brišu, ostali audit zapisi i razgovori gube vezu s kupcem, analize starije od `analysis-retention-days` (365) se anonimiziraju; svaki prolaz ostavlja `PRIVACY retention` zapis |
| **Transparentnost** (čl. 13) | obavijest u chatu na početku razgovora; tekst privole za glas prije prvog snimanja; odjeljak **Privatnost** u shopu; baza znanja (`10-privatnost-i-osobni-podaci.md`) da i agent točno odgovara na pitanja o podacima |
| **Privola** (čl. 7) | tablica `consents` (kupac, vrsta, **verzija teksta**, kada dana, kada povučena); glasovni endpoint bez privole vraća 403 `CONSENT_REQUIRED`; nova verzija teksta traži novu privolu; povlačenje jednim klikom |
| **Pravo pristupa i prenosivosti** (čl. 15, 20) | `GET /api/shop/privacy/export` → JSON sa svime što sustav ima o kupcu (račun, kupnje, wallet, prijave, razgovori s porukama, analize, privole); gumb u shopu |
| **Pravo na brisanje** (čl. 17) | `POST /api/shop/privacy/erase-conversations`: poruke i dohvati iz baze znanja se brišu, pozivi alata i analize gube vezu s kupcem, razgovori postaju `ERASED`; kupnje, wallet i bonovi ostaju (ugovor, računovodstvo) i to kupcu jasno piše; brisanje cijelog računa = prijava voditelju |
| **Odgovornost** (čl. 5(2)) | privola, izvoz, brisanje i retencija su sami događaji u audit tragu (`PRIVACY`); analiza i učenje također (`ANALYSIS`) |
| **Tehničke mjere** (čl. 32) | prijava i uloge, kupac se prepoznaje po tokenu na svakoj granici (Faza 4), bez punog aktivacijskog koda prema modelu, logovi bez e-maila, demo bez HTTPS-a (**produkcija: HTTPS obavezan**) |

## 3. Tok podataka za jedan glasovni turn

```
preglednik ──snimka (webm)──> sustav :8080 ──(privola VOICE? da)──> agent :8090
                                                                     │ STT (OpenAI): snimka -> tekst   [snimka se odbacuje]
                                                                     │ model (OpenAI): tekst + alati kroz MCP (sustav, token kupca)
                                                                     │ TTS (ElevenLabs): odgovor -> mp3
sustav: audit CHAT_TURN (transkript, odgovor), conversations.turn <──┘
preglednik: prikaz transkripta i odgovora, reprodukcija mp3 (ništa se ne sprema)
```

Što OpenAI vidi: transkript poruke, ime kupca i podaci koje agent dohvati alatima (kupnje s serijskim brojem i zadnje 4 znamenke koda, stanje walleta). Što ElevenLabs vidi: samo tekst odgovora agenta. Što nitko vanjski ne vidi: puni aktivacijski kod, lozinku, e-mail.

## 4. Post-analiza i samoučenje: zaštitne mjere

1. **Ulaz je pseudonimiziran**: model dobiva `[KUPAC]`, ne ime. Test `PseudonymizerTest` to čuva.
2. **Izlaz je strukturiran** (Spring AI structured output): sažetak, kategorija, ishod, raspoloženje, ocjena, rupe u znanju, prijedlozi. Nema slobodnog prepisivanja razgovora.
3. **Prijedlozi za bazu znanja su poopćeni** (uputa modelu + čovjek koji odobrava): ni jedan odobreni odlomak ne smije sadržavati osobne podatke; admin ga pročita i ispravi prije odobrenja.
4. **Razgovori se ne koriste za učenje jezičnog modela**: API uvjeti izvršitelja; sustav uči samo u obliku odobrenih odlomaka baze znanja.
5. **Brisanje na zahtjev** ne briše poopćene odlomke (nisu osobni podatak), ali analizu odvaja od kupca.

## 5. Što u demou nije napravljeno (i trebalo bi u produkciji)

- Potpisani ugovori o obradi (DPA) s OpenAI-jem i ElevenLabsom; procjena prijenosa u SAD; po mogućnosti EU regija izvršitelja.
- HTTPS i upravljanje tajnama (ključevi u sefu, ne u datotekama).
- Brisanje cijelog računa kupca (s računovodstvenim rokovima) — ovdje samo prijava voditelju.
- Procjena učinka (DPIA) za glasovnu obradu; ovdje je zamijenjena odlukom da se snimka ne pohranjuje i da nema biometrije.
- Logovi sadrže sadržaj poruka radi demonstracije („sve pod logovima“); u produkciji ili bez sadržaja ili s istim rokom i pristupom kao audit.
- Pseudonimizacija je pravilima (regex): imena u kosim padežima (npr. „Ani“, „Markov“) mogu promaknuti; produkcija = NER ili uža lista podataka koja se šalje modelu.
