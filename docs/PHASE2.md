# Faza 2 — Znanje: baza znanja i RAG (Spring AI)

_Status: **GOTOVO i e2e verificirano 2026-09-22** · grana `phase2` (iz `phase1`) · okosnica za sve AI dijelove = **Spring AI**._

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

## 2. Korak 2 — ingest i pretraga (GOTOVO 2026-09-22)

```
classpath:kb/*.md ──KbMarkdownParser──> KbChunk (## odjeljak + metapodaci)
        │                                        │
        │  KnowledgeBaseIngestor (ApplicationReadyEvent, idempotentno)
        ▼                                        ▼
  Spring AI VectorStore.add(Document) ──OpenAI text-embedding-3-small──> Postgres vector_store (pgvector, HNSW, cosine)
                                                                                   ▲
  GET /api/admin/kb/search?q=…  ──KnowledgeBaseService──> VectorStore.similaritySearch(topK=3, threshold=0.35) ──┘
```

### Odluke

| Odluka | Razlog |
|---|---|
| **Spring AI 2.0.0** (`spring-ai-bom`), `spring-ai-starter-model-openai` + `spring-ai-starter-vector-store-pgvector` | Okosnica po zahtjevu; embedding model i vector store su konfiguracija, ne kod (zamjena providera = yml). |
| **Vector store u istoj Postgres bazi** (`vector_store`, pgvector, HNSW, cosine) | Nula nove infrastrukture; image `pgvector/pgvector:pg17` odabran još u Fazi 1. |
| **Tablicu `vector_store` kreira Flyway (`V3__vector_store.sql`)**, `spring.ai.vectorstore.pgvector.initialize-schema=false` | Shemu i dalje drži Flyway (jedna istina, `-Reset` reproducibilan); oblik tablice = onaj koji Spring AI PgVectorStore očekuje (`id uuid, content text, metadata json, embedding vector(1536)`). |
| **Vlastiti parser markdowna** (`KbMarkdownParser`, ~100 linija) umjesto Spring AI `MarkdownDocumentReader` + `TokenTextSplitter` | Chunk = `##` odjeljak (konvencija dokumenata), front matter → metapodaci, `title › section` kao prefiks sadržaja; odjeljci su 50–150 riječi pa dodatni token-splitter nije potreban. |
| **Idempotentan ingest pri startu**: hash datoteke (`file_hash`) u metapodacima svakog odlomka; nepromijenjena datoteka se preskače, promijenjena se briše po `source` (`VectorStore.delete(Filter)`) i ponovno ugrađuje | Restart ne duplicira i ne troši embeddinge; promjena jednog dokumenta = ponovni ingest samo tog dokumenta. `POST /api/admin/kb/ingest?force=true` za prisilni ingest. |
| **Bez ključa app radi** (`api-key: ${OPENAI_API_KEY:not-set}`), baza znanja javlja 503 `KB_UNAVAILABLE` | Faza 1 i dalje radi bez ikakvog AI ključa; `run-phase-2.ps1` traži ključ, `run-phase-1.ps1` ga učita ako postoji. |
| **top-k 3, prag sličnosti 0,35** (`nottrilix.kb.*`) | Mjereno na `text-embedding-3-small`: relevantni odlomci 0,40–0,68, nevezani upit („recept za pizzu“) 0,32–0,34 → prag 0,35 reže šum, a kratke ključne riječi („voucher PIN nadoplata“ 0,39) ostaju. |
| Pretraga samo za `ADMIN` (`/api/admin/kb/**`) | Dokumenti su interni (`audience: agent`); kupcu ih u sljedećoj fazi posreduje agent, ne sirova pretraga. |

### Što je dodano

- `kb/`: `KbProperties` (record), `KbChunk`, `KbMarkdownParser`, `KnowledgeBaseIngestor`, `KnowledgeBaseService`, `KnowledgeBaseUnavailableException`.
- API `/api/admin/kb`: `GET /search?q=&topK=` → `KbHit[]` (source, title, section, category, tags, score, content), `GET /status` → `KbStatus` (dostupnost, odlomci po dokumentu, zadnji ingest, top-k, prag), `POST /ingest?force=` → `KbIngestResult`.
- Admin tab **Znanje**: status, pretraga s primjerima upita, pogoci s postotkom sličnosti i tekstom odlomka, „Ponovni ingest“, popis dokumenata.
- Logovi (`KB` logger): `KB INGEST <datoteka> -> n odjeljaka (novo|zamijenjeno)` / `nepromijenjen - preskačem`, `KB INGEST gotov: …`, `KB SEARCH q='…' topK=3 threshold=0.35 -> n hits (best: <izvor> › <odjeljak> score=0.678)`.
- Skripte: `run-phase-1.ps1` učitava `openai.key` ako postoji; `run-phase-2.ps1` = zahtijeva ključ + poziva `run-phase-1.ps1`.
- Test `KbMarkdownParserTest` (front matter, odjeljci, preambula se ne embedda, stabilan hash).

### Verificirano (2026-09-22)

- Start s ključem: Flyway V3 → `KB INGEST start: 9 datoteka` → 77 odlomaka u ~7 s (9 poziva embedding API-ja, po datoteci) → `KB INGEST gotov`. Ponovni ingest (`force=false`) → `9 nepromijenjenih, 0 novih`. `select count(*), count(distinct metadata->>'source') from vector_store` = 77 / 9.
- Pretraga (top-3, ocjena): „koliki je dnevni limit kupnje“ → *Dnevni limit kupnje od 150 €* 0,68; „mogu li vratiti bon nakon 20 dana“ → FAQ *Mogu li vratiti bon?* 0,59 + *Pravila povrata novca* 0,56; „kod ne radi“ → tri odjeljka postupka *Kad kod ne radi* 0,52–0,54; „nisam dobio kod e-mailom“ → FAQ 0,50; „mogu li platiti karticom“ → FAQ 0,58; „koje denominacije ima Xbox“ → *Xbox Gift Card* 0,59; „voucher PIN nadoplata“ → rječnik 0,39; upiti s hrvatskim znakovima (ž, š, ć) rade uz ispravno UTF-8 kodiranje.
- Sigurnost: bez tokena 401, kupac 403; 19/19 testova, TypeScript čist.

---

## 3. Što ostaje netaknuto

Domena, pravila, API i frontendi Faze 1. RAG je dodatak: nova tablica `vector_store` u istoj bazi, nova pretraga i novi admin tab; ništa postojeće se ne mijenja.

## 4. Sljedeće (Faza 3 — agent)

Spring AI `ChatClient` s `QuestionAnswerAdvisor` nad istim `VectorStore` (RAG), memorijom po razgovoru i `@Tool` adapterima nad postojećim servisima (provjera bona, povrat, blokada, prijava, wallet) + audit trag svakog tool poziva.
