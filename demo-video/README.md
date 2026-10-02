# Demo videi (rezerva za predavanje)

Snimke stvarnog shopa, admina i konzole, snimljene automatski (Playwright + Chromium) protiv pokrenutog sustava i agenta,
s vidljivim kursorom i natpisima koji govore što se gleda. Ako na pozornici zakaže mreža, mikrofon ili API, ovo se pusti.

| Datoteka (`out/`) | Trajanje | Što se vidi |
|---|---|---|
| `01-faza1-sustav.mp4` | ~1 min | Ivana kupuje HT bon s walleta uz potvrdu, kod skriven do klika, Steam 50 € odbijen pravilom; admin: transakcije, zaliha, prijave |
| `02-faza2-znanje.mp4` | ~0,5 min | admin tab Znanje: 85 odlomaka, semantička pretraga s ocjenama, isto pitanje drugim riječima |
| `03-faza3-agent.mp4` | ~1,5 min | Marko i Nola: wallet, kod ne radi, kupnja uz potvrdu (gumbi), alati i izvori ispod odgovora, admin Audit |
| `04-faza4-mcp.mp4` | ~1,5 min | konzola: jedan potvrđeni turn s obje strane mreže (`[sustav]` / `[agent ]`), pa MCP pozivi bez agenta |
| `05a-faza5-glas.mp4` | ~1,5 min | Ana govori (snimka kroz lažni mikrofon), privola, transkript, odgovor sa streamingom; **Nolin govor je u zvuku videa** |
| `05b-faza5-analiza-gdpr.mp4` | ~3 min | pitanje bez odgovora u bazi znanja, analiza razgovora, odobrenje prijedloga, agent odgovara iz naučenog, izvoz i brisanje |
| `00-sve-faze.mp4` | ~9 min | sve gore, s naslovnom karticom prije svake faze |

Reprodukcija: bilo koji player (VLC, Windows Media Player), 1280×720, H.264. Na pozornici: VLC na cijelom ekranu, razmak za pauzu.

## Ponovno snimanje

```powershell
.\run-phase-6.ps1 -NoBuild -Reset -NoTunnel      # svjež seed, sustav + agent (traži openai.key i elevenlabs.key)
cd demo-video
npm install && npx playwright install chromium   # prvi put
python make-samples.py                           # probne snimke za lažni mikrofon (OpenAI TTS)
node record-faza1.js ; node record-faza2.js ; node record-faza3.js
python build-faza4-data.py ; node record-faza4.js
node record-faza5a.js ; node record-faza5b.js
python make-all.py                               # jedan video sa svim fazama
```

Snimke mijenjaju podatke (kupnje, privole), zato prije snimanja `-Reset`. Redoslijed je bitan: 5b računa na razgovor iz 5a i na svježu bazu znanja.
Mape `out/`, `samples/` i `node_modules/` nisu u gitu.

## Kako je snimljeno

- `lib.js`: Chromium u Playwrightu s `recordVideo` 1280×720; skripta u stranicu ubaci kursor, klik-efekt i traku s natpisom, tipka sporo i čeka stvarne odgovore agenta.
- Glas (5a): Chromium opcija `--use-file-for-fake-audio-capture` pušta WAV kao mikrofon, pa prolaze pravi STT, agent i TTS; zvuk odgovora se uzme iz streama (`/api/agent/voice/stream`) i naknadno umiksa u video na trenutak kad je krenuo.
- Faza 4 (`terminal.html`): nema što kliknuti, pa se stvarne linije loga (iz `docs/PHASE4.md` i žive `scripts/mcp_direct.py`) ispisuju kao u konzoli.
