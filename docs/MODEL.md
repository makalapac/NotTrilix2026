# Izbor modela (2026-10-03): `gpt-4o` → `gpt-5.6-terra`

Pitanje je bilo: može li se `gpt-4o` zamijeniti nečim boljim, ne nužno najskupljim. Odgovor je izmjeren, ne pretpostavljen:
skripta je sa stvarnim system promptom agenta, stvarnim RAG predloškom i stvarnim MCP alatima (pozvanima kroz pokrenuti
sustav kao kupac Ana) odigrala iste turnove na sedam modela i mjerila trajanje poziva modela, tokene, izbor alata i odgovor.

## Kandidati (Chat Completions, cijene USD po 1M tokena, listopad 2026)

| Model | Ulaz / izlaz | Što je | Prolaz 1: model ms/turn (prvi poziv) |
|---|---|---|---|
| `gpt-4o` (dosadašnji) | 2,50 / 10 | svibanj 2024 | 1738 (843) |
| `gpt-4.1` | 2 / 8 | travanj 2025, bez razmišljanja | 1752 (812) |
| `gpt-5.1` | 1,25 / 10 | studeni 2025, `reasoning_effort: none` | 2330 (999) |
| `gpt-5.4-mini` | 0,75 / 4,50 | ožujak 2026, mini | 1916 (854) |
| `gpt-5.4` | 2,50 / 15 | ožujak 2026 | 2675 (1204) |
| `gpt-5.6-luna` | 0,20 / 1,20 | najnovija generacija, nano razred | 2642 (1203) |
| **`gpt-5.6-terra`** | **2 / 12** | **najnovija generacija, mini razred, znanje do 2/2026** | **2628 (1112)** |

Prolaz 1: četiri scenarija (stanje walleta, serijski broj bez crtica `STM25001`, kupnja HT bona uz potvrdu, pitanje o R1
računu koje baza znanja ne pokriva). Prolaz 2, samo za tri finalista, dodao je: povrat zadnje kupnje, „Kod mi ne radi“ bez
serijskog broja, telefonski kratko „stanje“ i „Možete li bon poslati poštom?“ (također izvan baze znanja).

| Prolaz 2 (8 scenarija) | model ms/turn | prvi poziv | pošten izvan baze znanja |
|---|---|---|---|
| `gpt-4o` | 1989 | 815 | da (R1, pošta) |
| `gpt-5.4-mini` | 1880 | 832 | **ne**: „ne izdajemo R1 račun“, „bonove ne šaljemo poštom, isključivo digitalno“ (izmišljeno pravilo) |
| `gpt-5.6-terra` | 2681 | 1216 | da (oba puta „nemam informaciju“ + nudi prijavu) |

Svi modeli prihvaćaju `temperature: 0.2` uz `reasoning_effort: none`; svi su birali ispravne alate (serijski broj bez crtica
prolazi jer ga sustav sam normalizira). Razlike koje su presudile:

- **`gpt-5.4-mini`** je brz i jeftin, ali dvaput izmislio politiku. Za agenta podrške to je diskvalifikacija, a razbilo bi
  i demo Faze 5 (rupa u znanju → prijedlog odlomka) koji ovisi o tome da agent pošteno kaže da nema informaciju.
- **`gpt-5.6-luna`** se zbunio oko kompaktnog serijskog broja („ne pronalazim STM25001… postoji STM-25-001“).
- **`gpt-4.1`** je jednako brz kao 4o, ali završava ponudom pomoći koju prompt zabranjuje; nije korak naprijed.
- **`gpt-5.4`** je kvalitetom uz terru, ali sporiji i skuplji (2,50 / 15).
- **`gpt-5.6-terra`**: sažeti odgovori (dobro za glas i telefon), na kratko „stanje“ zove samo wallet (4o zove tri alata i
  piše odlomak), kod bona bez serijskog broja uzima zadnju kupnju i daje konkretne upute. Cijena po turnu praktički ista
  kao 4o (~3000 ulaznih tokena: 0,6 ¢ prema 0,75 ¢). **Trošak: ~0,7 s sporiji turn, prvi poziv ~1,2 s umjesto ~0,8 s.**

## Konfiguracija

- Agent (`agent/src/main/resources/application.yml`): `model: ${NOTTRILIX_CHAT_MODEL:gpt-5.6-terra}`,
  `reasoning-effort: none` (bez razmišljanja prije odgovora: glas i telefon trebaju brz prvi token), `temperature: 0.2`.
- Sustav, analiza razgovora (`src/main/resources/application.yml`): isti model, `reasoning-effort: low` (posao u pozadini,
  kašnjenje nije bitno, malo razmišljanja pomaže strukturiranom nalazu). **Bez `temperature`**: s uključenim razmišljanjem
  API je odbija (`Only the default (1) value is supported`).
- `NOTTRILIX_CHAT_MODEL` mijenja oba bez builda, npr. `gpt-5.4-mini` kad je brzina važnija od svega ili `gpt-4o` za povratak.
- Spring AI 2.0 OpenAI modul ide preko službenog OpenAI Java SDK-a; `reasoning-effort` se šalje kao string
  (`ReasoningEffort.of("none")`), pa i nove vrijednosti prolaze bez nadogradnje.

## STT (nije mijenjan)

OpenAI je 2026-08-26 najavio gašenje `gpt-4o-mini-transcribe` (i `whisper-1`, `gpt-4o-transcribe`) za **2027-02-26**;
nasljednici su `gpt-transcribe` i `gpt-live-transcribe`. Test na 7 sintetiziranih hrvatskih rečenica iz demoa:
`gpt-transcribe` je 4 od 7 vratio na srpskoj ćirilici (s našim promptom i bez njega), `gpt-4o-mini-transcribe` s
promptom uvijek latinicu uz sitne greške. Za predavanje ostaje `gpt-4o-mini-transcribe`; prije veljače 2027 ponoviti test
sa stvarnim snimkama i novim parametrima `gpt-transcribe` (jezični hintovi, kontekst).

Mjerna skripta nije u repou (jednokratna, u scratchpadu sesije); rezultati su gore.
