# NotTrilix 2026 — Faza 1: postojeći sustav (AS IS)

Pojednostavljeni „produkcijski“ sustav za prodaju bonova: **jedna Spring Boot aplikacija** (API + dva React frontenda) i **PostgreSQL** baza.
Ovo je polazna točka za predavanje *From REST API to Voice Agent* (JavaCro 2026) — sve kasnije faze grade oko ovog sustava.

Status faza i „gdje smo stali“: [PROGRESS.md](PROGRESS.md) · puni opis faze 1 (odluke, domena, pravila, API, seed, verifikacija, zamke): [docs/PHASE1.md](docs/PHASE1.md).

## Što sustav radi

- **Proizvodi:** Telekom bonovi (A1, HT) i Gaming bonovi (Steam, PlayStation, Xbox), denominacije u EUR.
- **Zaliha:** unaprijed generirani bonovi (serijski broj + aktivacijski kod), 8 po proizvodu.
- **Wallet:** svaki kupac ima wallet; kupnja se **naplaćuje s walleta** (uz potvrdu u dijalogu), povrat ide natrag na wallet, kupac može nadoplatiti wallet (simulirano plaćanje karticom), sve promjene su u knjizi promjena.
- **Kupci (shop, `/`):** prijava e-mailom i lozinkom, katalog (dostupno / rasprodano — kupac ne vidi stanje zalihe), kupnja bona s walleta (odmah dobiju kod), „Moj wallet“ (stanje + povijest), pregled svojih kupnji.
- **Admin (`/admin/`):** prijava korisničkim imenom i lozinkom; pregled svih transakcija (filtri), kupci s walletima (uplata podrške, povijest), stanje zaliha, podrška — povrat novca na wallet, blokada bona, prijave kupaca.
- **Provjera bona kod pružatelja:** fiktivni upit prema A1/HT/Steam/PlayStation/Xbox („je li bon iskorišten?“) — support za bilo koji bon, kupac za vlastite; odgovor sadrži status kod pružatelja, datum iskorištenja, referencu i simuliranu latenciju.
- **Jedan API** (`/api/**`, Swagger na `/swagger-ui.html`) za oba kanala; pristup bearer tokenom, uloge `CUSTOMER` / `ADMIN`.

```
   Shop (React)  ──┐                       ┌── PostgreSQL 17 (Docker, :5433)
                   ├──> Spring Boot :8080 ─┤    Flyway: V1 shema + V2 seed
   Admin (React) ──┘    REST API + pravila └── logovi: konzola + logs/nottrilix-2026.log
```

## Pokretanje (jedan potez)

```powershell
.\run-phase-1.ps1
```

Skripta: provjeri/pokrene Docker Desktop → podigne PostgreSQL (`docker compose up --wait`) → nađe JDK 25 → `mvn package`
(uključivo build React frontenda; prvi put skida Node i npm pakete, par minuta) → pokrene aplikaciju u istom prozoru (logovi u konzoli).

| Opcija | Što radi |
|---|---|
| `-NoBuild` | preskoči Maven build, koristi postojeći `target\nottrilix-2026.jar` |
| `-Reset` | obriši bazu (docker volume) → svježi seed |
| `-Stop` | ugasi PostgreSQL container (aplikaciju gasiš s Ctrl+C) |

Adrese: **Shop** http://localhost:8080/ · **Admin** http://localhost:8080/admin/ · **API/Swagger** http://localhost:8080/swagger-ui.html

Preduvjeti: Docker Desktop, JDK 25 (`C:\Program Files\Java\jdk-25*`), Maven 3.9+. Node **nije** potreban (Maven ga skida u `./node`).

## Demo podaci (Flyway `V2__seed.sql`)

- **Kupci (e-mail / lozinka · wallet):** `ana.anic@example.com` / `ana123` · 115 € — `marko.maric@example.com` / `marko123` · 25 € — `ivana.ivic@example.com` / `ivana123` · 50 € (Marko ne može kupiti Steam 50 € bez nadoplate).
- **Admin (korisničko ime / lozinka):** `admin` / `admin123`.
- **Proizvodi:** A1 5/10/20 € · HT 5/10/25 € · Steam 10/25/50 € · PlayStation 20/50 € · Xbox 15/30 €.
- **Bonovi:** serijski `PREFIX-DENOMINACIJA-NNN` (npr. `STM-25-003`), 12-znamenkasti kod.
- **Povijest:** 6 transakcija (web + kiosk; aktivni, iskorišteni, jedan povrat s blokiranim bonom) i 2 prijave podrške (1 otvorena).

## Poslovna pravila (servisni sloj, `application.yml → nottrilix.rules`)

| Pravilo | Greška (HTTP 409) |
|---|---|
| Dnevni limit kupnje po kupcu: **150 €** | `DAILY_LIMIT_EXCEEDED` |
| Na walletu mora biti dovoljno sredstava za cijenu bona | `INSUFFICIENT_FUNDS` |
| Bon mora postojati na zalihi (zaključavanje reda u bazi) | `OUT_OF_STOCK` |
| Uplata na wallet: najviše **500 €** po uplati | `TOPUP_LIMIT_EXCEEDED` |
| Povrat samo unutar **14 dana** i samo ako bon nije iskorišten; iznos ide na wallet, bon se blokira | `REFUND_WINDOW_EXPIRED`, `VOUCHER_ALREADY_REDEEMED`, `ALREADY_REFUNDED` |
| Blokada bona: ne za iskorišten / već blokiran | `VOUCHER_ALREADY_REDEEMED`, `ALREADY_BLOCKED` |
| Prijava se može vezati samo na transakciju istog kupca | `TRANSACTION_CUSTOMER_MISMATCH` |

Greške su uvijek `{ "error": "KOD", "message": "poruka" }` (404 `NOT_FOUND`, 400 `VALIDATION_ERROR`, 401 `UNAUTHORIZED` / `INVALID_CREDENTIALS`, 403 `FORBIDDEN`).

## Prijava i pristup

Spring Security, lozinke bcrypt u bazi, **bearer token** (bez sesija i kolačića; token u `localStorage`, zaseban ključ za shop i admin pa oba mogu biti prijavljena u istom pregledniku).

| Tko | Prijava | Uloga | Smije |
|---|---|---|---|
| Kupac | `POST /api/auth/customer/login` `{email, password}` | `CUSTOMER` | `/api/shop/**` (katalog, kupnja, vlastite kupnje) |
| Admin/support | `POST /api/auth/admin/login` `{username, password}` | `ADMIN` | `/api/admin/**`, `/api/customers/**` |

Odgovor prijave je `{ "token": "...", "user": {realm, id, name, login} }`; dalje `Authorization: Bearer <token>`. `GET /api/auth/me` = tko sam, `POST /api/auth/logout` poništava token. Statika, Swagger i login endpointi su javni; sve ostalo pod `/api/**` traži prijavu. U Swaggeru gumb *Authorize* prima token.

## API (sažetak)

| Metoda i putanja | Uloga | Opis |
|---|---|---|
| `POST /api/auth/customer/login`, `POST /api/auth/admin/login` | — | prijava → token |
| `GET /api/auth/me`, `POST /api/auth/logout` | bilo koja | tko sam / odjava |
| `GET /api/shop/catalog` | CUSTOMER | katalog (dostupno / rasprodano) |
| `POST /api/shop/purchase` `{productId}` | CUSTOMER | kupnja za prijavljenog kupca, naplata s walleta → `{transaction (s bonom), walletBalanceEur}` |
| `GET /api/shop/transactions` | CUSTOMER | kupnje prijavljenog kupca |
| `GET /api/shop/wallet`, `POST /api/shop/wallet/topup` `{amountEur}` | CUSTOMER | wallet (stanje + povijest) / nadoplata (simulirano) |
| `GET /api/shop/vouchers/{serial}/check` | CUSTOMER | provjera vlastitog bona kod pružatelja (tuđi bon = 404) |
| `GET /api/customers`, `GET /api/customers/{id}` | ADMIN | kupci (sa stanjem walleta) |
| `GET /api/admin/customers/{id}/wallet`, `POST /api/admin/customers/{id}/wallet/topup` `{amountEur, note}` | ADMIN | wallet kupca / uplata podrške |
| `GET /api/admin/transactions?status=&brand=&customerId=` | ADMIN | sve transakcije |
| `GET /api/admin/transactions/{id}` | ADMIN | jedna transakcija |
| `POST /api/admin/transactions/{id}/refund` | ADMIN | povrat novca na wallet kupca |
| `POST /api/admin/vouchers/{serial}/block` | ADMIN | blokada bona |
| `GET /api/admin/vouchers/{serial}/check` | ADMIN | provjera iskorištenosti bona kod pružatelja (fiktivni upit) |
| `GET /api/admin/stock` | ADMIN | zaliha po proizvodu/statusu |
| `GET /api/admin/tickets`, `POST /api/admin/tickets`, `POST /api/admin/tickets/{id}/resolve` | ADMIN | podrška |

## Logovi

Logback (`logback-spring.xml`): sve na konzolu (u boji) i u `logs/nottrilix-2026.log`. Svaki HTTP zahtjev s korisnikom (`HTTP` logger: `POST /api/shop/purchase user=customer:ana.anic@example.com -> 201 (60 ms)`),
svaka prijava/odjava i odbijeni pristup (`AUTH` logger: `LOGIN OK`, `LOGIN FAILED`, `AUTH 401/403`, `TOKEN issued/revoked`),
svaka poslovna operacija (`PURCHASE OK …`, `PURCHASE DENIED reason=OUT_OF_STOCK …`, `WALLET DEBIT/CREDIT/TOPUP …`, `REFUND OK …`, `TICKET …`, `VOUCHER CHECK …`), svaki „poziv“ pružatelju (`PROVIDER` logger) i svaki odbijeni zahtjev.
SQL ispis: `logging.level.org.hibernate.SQL: DEBUG` u `application.yml`.

## Razvoj

- Backend: `mvn -DskipTests package` (ili samo `mvn test` za testove pravila u `ShopServiceTest`). Build treba JDK 25 (`JAVA_HOME`).
- Frontend s hot-reloadom: aplikacija upaljena na 8080, pa u `frontend/`: `npm install && npm run dev` → http://localhost:5173 (i `/admin/`), `/api` se proxyja na 8080.
- Preskoči frontend u Maven buildu: `-Dskip.installnodenpm -Dskip.npm`.

## Struktura

```
src/main/java/com/nottrilix
  domain/    entiteti + enumi (Customer (+wallet), WalletEntry, AdminUser, Product, Voucher, Transaction, SupportTicket)
  repo/      Spring Data repozitoriji (FOR UPDATE za kupca/bon/transakciju)
  service/   CatalogService, ShopService, WalletService, SupportService, VoucherCheckService  <- poslovna pravila
  provider/  ProviderGateway (sučelje prema pružatelju) + SimulatedProviderGateway (fiktivni A1/HT/Steam/...)
  auth/      Realm, AuthUser, LoginToken, NotTrilixAuthenticationProvider (bcrypt), TokenService, BearerTokenFilter
  api/       kontroleri (Auth, Shop, Customers, Admin), dto/ (recordi), ApiExceptionHandler
  config/    SecurityConfig, RulesProperties, AppConfig (Clock, OpenAPI), WebConfig, RequestLoggingFilter
src/main/resources/db/migration   V1__schema.sql, V2__seed.sql
frontend/   Vite + React + TypeScript: src/customer (shop), src/admin, src/shared (api + token, tipovi, LoginPage, stil)
```
