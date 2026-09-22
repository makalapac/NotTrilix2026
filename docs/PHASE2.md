# Faza 2 — Znanje: baza znanja i RAG (Spring AI)

_Status: **U TIJEKU** (početak 2026-09-22) · grana `phase2` (iz `phase1`) · okosnica za sve AI dijelove = **Spring AI**._

Cilj faze: agent podrške (dolazi u sljedećoj fazi) mora odgovarati iz **stvarnih politika NotTrilixa**, ne iz opće memorije modela.
Faza 2 gradi tu podlogu: bazu znanja u markdownu, njezin ingest u vector store kroz Spring AI i semantičku pretragu koja vraća relevantne odlomke.
Postojeći sustav (Faza 1) se ne mijenja.

---

## 1. Korak 1 — baza znanja (GOTOVO 2026-09-22)

Devet dokumenata u `src/main/resources/kb/` (hrvatski, ~3.000 riječi), pisanih **za RAG**, ne za ljude koji čitaju od početka do kraja:

| Datoteka | Sadržaj |
|---|---|
| `01-o-nottrilixu-i-bonovima.md` | što NotTrilix prodaje, brendovi i denominacije, serijski broj vs. aktivacijski kod, statusi bona, kanali |
| `02-kupnja-wallet-i-limiti.md` | tok kupnje, wallet kao jedini način plaćanja, uplata (1–500 €), poruke „nedovoljno sredstava“ / „dnevni limit 150 €“ / „rasprodano“, kiosk |
| `03-iskoristavanje-bona-po-pruzatelju.md` | pravila unosa koda, upute po brendu (A1, HT, Steam, PlayStation, Xbox), regija, rok valjanosti |
| `04-kad-kod-ne-radi-postupak.md` | postupak za agenta: identifikacija → provjera unosa → provjera kod pružatelja → radnja po statusu (aktivan / iskorišten / blokiran / nije aktiviran) → dokumentiranje |
| `05-povrat-reklamacije-i-prijave.md` | pravila povrata (14 dana, ne iskorišten, na wallet, blokada), kad sustav odbija, blokada, goodwill (10 € agent / više voditelj), prijave i rokovi |
| `06-sigurnost-identitet-i-zastita-koda.md` | provjera identiteta, tajnost koda (zadnje 4 znamenke), zlouporaba, lozinka, što NotTrilix nikad ne traži |
| `07-radno-vrijeme-kontakt-i-eskalacija.md` | radno vrijeme, kontakt (0800 13 37), što agent rješava odmah, što ide voditelju, rokovi |
| `08-cesta-pitanja.md` | FAQ u obliku pitanje (`##`) → kratak odgovor, jezikom kupaca |
| `09-rjecnik-pojmova.md` | sinonimi kojima kupci govore (voucher/kupon/PIN/novčanik/saldo/redeem…) → naš pojam |

### Konvencije pisanja (zašto dokumenti izgledaju ovako)

1. **Jedna tema po datoteci, YAML front matter** (`title`, `category`, `tags`, `audience`, `updated`) → metapodaci uz svaki chunk (filtriranje, prikaz izvora u odgovoru).
2. **`##` odjeljak = jedinica za chunking.** Svaki odjeljak je samostalan (ponavlja imenicu o kojoj govori, nema „gore navedeno“/„kao što je rečeno“), duljine 50–200 riječi → chunk nosi cijeli smisao bez susjeda.
3. **Brojke i poruke su identične sustavu:** dnevni limit 150 €, povrat 14 dana, uplata 1–500 €, goodwill 10 €, poruke grešaka doslovno kako ih ispisuje aplikacija (`Nedovoljno sredstava na walletu: …`, `Dnevni limit od 150 € je premašen: …`, `Rok za povrat (14 dana) je istekao …`). Kupci citiraju poruke → izravno se pogađa pravi odlomak, a agent ne može „izmisliti“ drugačije pravilo od koda.
4. **Jezik kupaca + sinonimi:** dokumenti sadrže i način kako kupci govore („kod ne radi“, „nisam dobio kod e-mailom“, voucher/kupon/PIN/saldo) — semantička pretraga radi bolje kad upit i tekst dijele rječnik; rječnik pojmova to eksplicitno mapira.
5. **Postupci za agenta izdvojeni i numerirani** („Postupak za agenta: 1. … 2. …“) → agent u sljedećoj fazi dobiva korake koje može pretvoriti u tool pozive (provjera bona, blokada, povrat, prijava).
6. **FAQ = pitanje kao naslov** → chunk počinje točno pitanjem koje će kupac postaviti.
7. **Konzistentna terminologija:** bon, serijski broj, aktivacijski kod, wallet, uplata, povrat, blokada, prijava, pružatelj usluge, voditelj podrške.
8. **Bez osjetljivih podataka i bez tajni** (nema lozinki, punih kodova, osobnih podataka kupaca); `audience: agent` = interni dokument.
9. **Hrvatski s dijakritikom, UTF-8**, isti jezik kao agent i kupci.

Sve što je u dokumentima pravilo sustava (limiti, rokovi, statusi, poruke) mora ostati usklađeno s `application.yml` (`nottrilix.rules.*`) i porukama u servisima — kad se pravilo promijeni u kodu, mijenja se i dokument.

---

## 2. Korak 2 — ingest i pretraga (SLJEDEĆE)

Plan (Spring AI, isti Postgres):

- **Ovisnosti:** `spring-ai-bom` (2.x), `spring-ai-starter-model-openai` (embedding model `text-embedding-3-small`), `spring-ai-starter-vector-store-pgvector` (ekstenzija `vector` u postojećoj bazi — image `pgvector/pgvector:pg17` već je odabran u Fazi 1).
- **Ključ:** `OPENAI_API_KEY` iz lokalne datoteke `openai.key` (gitignored, kao u starom projektu); skripta ga učitava u okolinu.
- **Ingest pri startu, idempotentan:** čitanje `classpath:kb/*.md`, parsiranje front mattera u metapodatke, dijeljenje po `##` odjeljcima (svaki chunk = naslov dokumenta + naslov odjeljka + tekst), `VectorStore.add(...)`; ponovni start ne duplicira (provjera po `source`/hash sadržaja).
- **Pretraga:** servis `KnowledgeBaseService.search(query, topK)` nad `VectorStore.similaritySearch` + endpoint za admin (`GET /api/admin/kb/search?q=…`) koji vraća odlomke s izvorom, naslovom i ocjenom sličnosti — demo „pitanje kupca → točan odlomak“.
- **Log:** `KB INGEST … chunks`, `KB SEARCH q='…' -> n hits (top: …)`.
- **Otvorene odluke:** granica sličnosti (`similarityThreshold`), topK (3), treba li `TokenTextSplitter` ispod razine odjeljka (odjeljci su kratki → vjerojatno ne).

---

## 3. Što ostaje netaknuto

Domena, pravila, API i frontendi Faze 1. RAG je dodatak: nova tablica `vector_store` u istoj bazi i nova pretraga; ništa postojeće se ne mijenja.
