# Faza 1 — postojeći sustav (AS IS)

_Status: **GOTOVO i e2e verificirano 2026-09-22** (uklj. dopunu: prijava kupaca/admina, kupcu skrivena zaliha) · repo `NotTrilix2026` · grana `phase1`._

Ovo je polazna točka predavanja *From REST API to Voice Agent: Building a Java Call Center Agent with Spring AI* (JavaCro 2026, 11.–14. 10., Rovinj).
Faza 1 predstavlja **pojednostavljeni produkcijski sustav** kakav ima svaka firma: baza, REST API s prijavom, poslovna pravila u kodu i dva frontenda.
Sve sljedeće faze (tool boundary, RAG, agent, mrežni MCP, glas, telefonija) grade se **oko** ovog sustava, bez mijenjanja njegove srži.

> Ovo je **novi početak** (2026-09-22). Prethodni pokušaj (`../mcp-demo`, evolucija lanjskog MCP demoa, faze 0–5 + Infobip telefonija) ostaje kao referenca:
> deck `mcp-demo/presentation/`, `PRESENTATION_OUTLINE.md`, `SECURITY.md`, `VOICE_RESEARCH.md`, `TELEPHONY_RESEARCH.md`, `INFOBIP_STATUS.md`, `PHONE_NUMBER_RESEARCH.md`.

---

## 1. Što sustav radi

**NotTrilix** prodaje bonove (vouchere):

| Kategorija | Brend | Denominacije (EUR) | Serijski prefiks |
|---|---|---|---|
| TELEKOM | A1 Hrvatska | 5, 10, 20 | `A1` |
| TELEKOM | Hrvatski Telekom | 5, 10, 25 | `HT` |
| GAMING | Steam (Steam Wallet) | 10, 25, 50 | `STM` |
| GAMING | PlayStation Store | 20, 50 | `PSN` |
| GAMING | Xbox (Gift Card) | 15, 30 | `XBX` |

- **Prodajni kanal (shop, `/`)** — kupac se prijavi (e-mail + lozinka), lista katalog (vidi samo *dostupno / rasprodano*, ne i koliko je na zalihi), kupi bon (odmah dobije serijski broj i aktivacijski kod), vidi svoje kupnje.
- **Admin (`/admin/`)** — prijava korisničkim imenom i lozinkom; pregled svih transakcija (filtri), stanje zaliha, podrška: povrat novca, blokada bona, prijave kupaca (otvaranje i rješavanje).
- **Jedan API (`/api/**`)** služi oba kanala; pristup bearer tokenom, uloge `CUSTOMER` / `ADMIN`; Swagger na `/swagger-ui.html`.

Cijena bona = nominalna vrijednost (bez naknada).

---

## 2. Odluke i razlozi

| Odluka | Razlog |
|---|---|
| **Jedna Spring Boot aplikacija** (API + oba frontenda kao statika), port 8080 | Zahtjev: jedan API za demo; najmanje pomičnih dijelova na pozornici. |
| **Spring Boot 4.1.0 · Java 25 · recordi · bez Lomboka** | Zahtjev. Recordi za DTO-ove i konfiguraciju; JPA entiteti su obične klase (JPA ne podržava recorde). |
| **PostgreSQL 17 u Dockeru, host port 5433** | „Produkcijska“ baza; 5433 da se ne sudara sa starim stackom (`mcp-demo` drži 5432 i 8081–8083). Image `pgvector/pgvector:pg17` = obični Postgres 17 + ekstenzija koja se u Fazi 1 **ne koristi** (kasnije faze ne moraju mijenjati bazu). |
| **Flyway** (V1 shema, V2 seed) + `ddl-auto: validate` | Shema je verzionirana i deterministična; JPA je samo provjerava. |
| **React + Vite + TypeScript**, dvije stranice (MPA) | Dva odvojena frontenda (kupci / admin), jedan build. Build ide **kroz Maven** (`frontend-maven-plugin` skida vlastiti Node u `./node`) — Node nije preduvjet, `mvn package` je dovoljan. |
| Vite output u `src/main/resources/static` (gitignored) | Spring Boot servira `/` (customer) i `/admin/` (admin) bez ikakve konfiguracije osim `forward` za `/admin`. |
| **springdoc-openapi 3.0.3** | Swagger UI = „API“ koji se pokazuje na pozornici; gumb *Authorize* prima bearer token. |
| **Prijava: Spring Security, dvije domene (`Realm`)** — kupac (e-mail + lozinka, `ROLE_CUSTOMER`), admin (korisničko ime + lozinka, `ROLE_ADMIN`) | Kupac i support su različiti svjetovi; autorizacija po putanjama (`/api/shop/**` vs `/api/admin/**`). |
| **Bearer tokeni u memoriji** (neprozirni slučajni string, TTL 8 h) umjesto sesija/kolačića | Stateless API; **bez CSRF-a** (nema kolačića); token u `localStorage` pod zasebnim ključem za shop i admin → kupac i admin mogu biti **istovremeno prijavljeni u istom pregledniku** (bitno za demo). Restart = ponovna prijava. |
| **bcrypt** lozinke; seed ih generira u SQL-u (`pgcrypto` `crypt`/`gen_salt('bf')`) | Nema hashova zalijepljenih u repo; demo lozinke ostaju čitljive u seedu. Spring `BCryptPasswordEncoder` čita `$2a$` hashove iz Postgresa. |
| Kupac ne vidi količinu zalihe (`CatalogItem.available: boolean`) | Interna informacija; kupcu samo *dostupno / rasprodano*. Admin vidi brojke u „Zalihe“. |
| Kupnja se veže na **prijavljenog** kupca (`@AuthenticationPrincipal`), ne na `customerId` iz zahtjeva | Nitko ne može kupovati/gledati u tuđe ime. |
| **Poslovna pravila u servisnom sloju** (`ShopService`, `SupportService`), parametri u `application.yml` (`nottrilix.rules.*` → record `RulesProperties`) | Poanta predavanja: pravila žive u Javi, kasnije ih ni agent ne može zaobići. |
| **Logback** (`logback-spring.xml`): konzola u boji + `logs/nottrilix-2026.log` | Zahtjev: sve što se događa vidljivo u konzoli. |
| Prijave podrške otvara **support** (admin), ne kupac | Realno za call centar (kupac zove, agent otvara prijavu) — točno scenarij kasnijih faza. |
| Kanal transakcije `WEB` / `POS` | Da povijest izgleda stvarno (web + kiosk); kasnije faze dodaju nove kanale. |
| Nema „redeem“ endpointa; status `REDEEMED` postoji samo u seedu | Iskorištavanje bona događa se kod providera (A1, Steam…), ne u ovom sustavu. Za support scenarije dovoljna je povijest u seedu. |
| Zaliha = unaprijed generirani bonovi, prodaja zaključava red (`FOR UPDATE`) | Dva istovremena kupca ne mogu dobiti isti kod. |

---

## 3. Arhitektura

```
   Shop  (React, /)       ──┐                                  ┌── PostgreSQL 17 (Docker :5433)
                            ├──> Spring Boot :8080 ────────────┤    Flyway V1 shema + V2 seed
   Admin (React, /admin/) ──┘    Spring Security (bearer)      └── logovi: konzola + logs/nottrilix-2026.log
                                 REST /api/**  · Swagger
                                 servisi = poslovna pravila
```

```
src/main/java/com/nottrilix
  NotTrilixApplication      @SpringBootApplication + @ConfigurationPropertiesScan
  domain/                   Customer (+password_hash), AdminUser, Product, Voucher, Transaction, SupportTicket + enumi
  repo/                     Spring Data JPA (fetch-join upiti, FOR UPDATE za zalihu, agregati, findByEmail/Username)
  service/                  CatalogService, ShopService, SupportService, BusinessRuleException, NotFoundException
  auth/                     Realm, AuthUser (principal), LoginToken, NotTrilixAuthenticationProvider (bcrypt),
                            TokenService (tokeni u memoriji), BearerTokenFilter
  api/                      AuthController, ShopController, CustomerController, AdminController, ApiExceptionHandler
  api/dto/                  recordi (LoginResponse, AuthUserDto, CatalogItem, TransactionDto, VoucherDto, TicketDto, StockRow, ...) + DtoMapper
  config/                   SecurityConfig, RulesProperties (record), AppConfig (Clock, OpenAPI+bearer), WebConfig (/admin forward), RequestLoggingFilter
src/main/resources
  application.yml · logback-spring.xml · banner.txt · db/migration/V1__schema.sql, V2__seed.sql
src/test/java/com/nottrilix   service/ShopServiceTest (pravila), auth/NotTrilixAuthenticationProviderTest (prijava)
frontend/                   Vite + React + TS: index.html (shop), admin/index.html, src/customer, src/admin,
                            src/shared (api + token, types, format, LoginPage, styles)
docker-compose.yml · run-phase-1.ps1 · mvnw(.cmd) · README.md · PROGRESS.md · docs/
```

Tok jednog zahtjeva: Spring Security (`BearerTokenFilter` → korisnik iz tokena; autorizacija po putanji) → `RequestLoggingFilter` (log `HTTP` s korisnikom)
→ kontroler (validacija `@Valid`, `@AuthenticationPrincipal`) → servis (`@Transactional`, pravila, log odluke) → repozitorij → `DtoMapper` → JSON.
Greške: `ApiExceptionHandler` (i Security entry point / access denied handler) → uvijek `{ "error": "KOD", "message": "..." }`.

---

## 4. Domenski model

| Tablica | Ključna polja | Napomena |
|---|---|---|
| `customers` | `name`, `email` (unique = login), `phone`, `password_hash` (bcrypt) | 3 kupca u seedu |
| `admin_users` | `username` (unique), `password_hash` (bcrypt), `name` | 1 admin u seedu |
| `products` | `category`, `brand`, `name`, `serial_prefix`, `denomination_eur`, `active` | unique `(brand, denomination_eur)`; 13 proizvoda |
| `vouchers` | `product_id`, `serial_number` (unique), `code`, `status`, `sold_at` | zaliha; 8 po proizvodu = 104 bona |
| `transactions` | `created_at`, `channel`, `status`, `customer_id`, `product_id`, `voucher_id` (unique), `amount_eur`, `refunded_at` | jedna kupnja = jedan bon |
| `support_tickets` | `created_at`, `status`, `customer_id`, `transaction_id` (opcionalno), `subject`, `description`, `resolution`, `resolved_at` | prijave podrške |

Enumi (u bazi kao `VARCHAR`, `@Enumerated(STRING)`):

- `Category`: `TELEKOM`, `GAMING`
- `Brand`: `A1`, `HT`, `STEAM`, `SONY`, `XBOX` (+ `displayName()`)
- `VoucherStatus`: `AVAILABLE → SOLD → REDEEMED`; `SOLD`/`AVAILABLE → BLOCKED`
- `TransactionStatus`: `COMPLETED`, `REFUNDED`
- `TicketStatus`: `OPEN`, `RESOLVED`
- `Channel`: `WEB`, `POS`
- `Realm` (auth): `CUSTOMER`, `ADMIN`

Životni ciklus bona:

```
AVAILABLE ──kupnja──> SOLD ──(provider)──> REDEEMED
    │                  │
    └──blokada──> BLOCKED <──povrat / blokada──┘
```

Novac: `BigDecimal`, `NUMERIC(10,2)`. Vrijeme: `Instant` / `TIMESTAMP(6) WITH TIME ZONE`; jedan `Clock` bean (`Europe/Zagreb`) u servisima i `TokenService` — testovi ubace fiksni.

---

## 5. Prijava i pristup

| Tko | Endpoint | Tijelo | Uloga | Smije |
|---|---|---|---|---|
| Kupac | `POST /api/auth/customer/login` | `{email, password}` | `ROLE_CUSTOMER` | `/api/shop/**` |
| Admin/support | `POST /api/auth/admin/login` | `{username, password}` | `ROLE_ADMIN` | `/api/admin/**`, `/api/customers/**` |

- Odgovor: `{ "token": "<43 znaka>", "user": { "realm", "id", "name", "login" } }` → dalje `Authorization: Bearer <token>`.
- `GET /api/auth/me` (tko sam), `POST /api/auth/logout` (204, token poništen). Javno: statika, Swagger, login endpointi; sve ostalo pod `/api/**` traži prijavu.
- Provjera lozinke: `NotTrilixAuthenticationProvider` (`AuthenticationProvider` registriran u `ProviderManager`) — `LoginToken(realm, login, password)` → repo po domeni → `BCryptPasswordEncoder.matches` → `UsernamePasswordAuthenticationToken.authenticated(AuthUser, null, ROLE_…)`.
- Kriva lozinka i nepostojeći korisnik daju **istu** poruku (401 `INVALID_CREDENTIALS`) — ne otkriva se postoji li račun.
- `TokenService`: `issue` (SecureRandom 32 B, base64url), `resolve` (TTL 8 h), `revoke`; `BearerTokenFilter` iz headera puni `SecurityContext` samo za taj zahtjev (`STATELESS`, bez sesije).
- Frontend: token u `localStorage` (`nottrilix.shop.token` / `nottrilix.admin.token`), na startu `GET /api/auth/me`; svaki 401 vraća na ekran prijave (npr. nakon restarta servera).
- Greške pristupa (JSON + `AUTH` log): 401 `UNAUTHORIZED` „Prijava je potrebna“, 403 `FORBIDDEN` „Nemate pravo pristupa“.

---

## 6. Poslovna pravila

Parametri: `application.yml` → `nottrilix.rules.daily-limit-eur: 150`, `refund-window-days: 14` (record `RulesProperties`).

| Operacija | Pravilo | Kod greške (HTTP 409) |
|---|---|---|
| Kupnja | proizvod mora biti aktivan | `PRODUCT_INACTIVE` |
| Kupnja | zbroj današnjih `COMPLETED` kupnji kupca + cijena ≤ **150 €** | `DAILY_LIMIT_EXCEEDED` |
| Kupnja | mora postojati `AVAILABLE` bon proizvoda (red zaključan `PESSIMISTIC_WRITE`) | `OUT_OF_STOCK` |
| Povrat | transakcija nije već vraćena | `ALREADY_REFUNDED` |
| Povrat | unutar **14 dana** od kupnje | `REFUND_WINDOW_EXPIRED` |
| Povrat | bon nije `REDEEMED`; `SOLD` bon se **blokira** (kod je već kod kupca) | `VOUCHER_ALREADY_REDEEMED` |
| Blokada bona | ne za `REDEEMED` / već `BLOCKED`; `AVAILABLE` i `SOLD` se blokiraju | `VOUCHER_ALREADY_REDEEMED`, `ALREADY_BLOCKED` |
| Prijava podrške | vezana transakcija mora pripadati istom kupcu | `TRANSACTION_CUSTOMER_MISMATCH` |
| Rješavanje prijave | samo `OPEN` prijava | `ALREADY_RESOLVED` |

Ostale greške: `NOT_FOUND` (404), `VALIDATION_ERROR` (400, npr. `subject je obavezan`), `BAD_REQUEST` (400, nečitljiv JSON / krivi tip),
`INVALID_CREDENTIALS` (401), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `HTTP_<status>` (Springove vlastite, npr. 404 za nepostojeću rutu), `INTERNAL_ERROR` (500).

Svako odbijanje logira servis na razini `WARN`: `PURCHASE DENIED reason=DAILY_LIMIT_EXCEEDED - Dnevni limit od 150 € je premašen: ...`.

---

## 7. API

| Metoda i putanja | Uloga | Opis | Odgovor |
|---|---|---|---|
| `POST /api/auth/customer/login` | — | prijava kupca `{email, password}` | `LoginResponse` |
| `POST /api/auth/admin/login` | — | prijava admina `{username, password}` | `LoginResponse` |
| `GET /api/auth/me` | bilo koja | prijavljeni korisnik | `AuthUserDto` |
| `POST /api/auth/logout` | bilo koja | poništi token | `204` |
| `GET /api/shop/catalog` | CUSTOMER | aktivni proizvodi, `available` (redoslijed seeda) | `CatalogItem[]` |
| `POST /api/shop/purchase` | CUSTOMER | kupnja `{productId}` za prijavljenog kupca (kanal `WEB`) | `201 TransactionDto` (s bonom) |
| `GET /api/shop/transactions` | CUSTOMER | kupnje prijavljenog kupca, najnovije prve | `TransactionDto[]` |
| `GET /api/customers`, `GET /api/customers/{id}` | ADMIN | kupci (bez lozinki) | `CustomerDto[]` / `CustomerDto` |
| `GET /api/admin/transactions?status=&brand=&customerId=` | ADMIN | sve transakcije, opcionalni filtri | `TransactionDto[]` |
| `GET /api/admin/transactions/{id}` | ADMIN | jedna transakcija | `TransactionDto` |
| `POST /api/admin/transactions/{id}/refund` | ADMIN | povrat novca | `TransactionDto` |
| `POST /api/admin/vouchers/{serial}/block` | ADMIN | blokada bona | `VoucherDto` |
| `GET /api/admin/stock` | ADMIN | zaliha po proizvodu i statusu (brojke) | `StockRow[]` |
| `GET /api/admin/tickets` | ADMIN | prijave, najnovije prve | `TicketDto[]` |
| `POST /api/admin/tickets` | ADMIN | nova prijava `{customerId, transactionId?, subject, description?}` | `201 TicketDto` |
| `POST /api/admin/tickets/{id}/resolve` | ADMIN | zatvaranje `{resolution}` | `TicketDto` |

Primjer (prijava pa kupnja):

```bash
curl -X POST localhost:8080/api/auth/customer/login -H "Content-Type: application/json" -d "{\"email\":\"ana.anic@example.com\",\"password\":\"ana123\"}"
# {"token":"k3Jv…","user":{"realm":"CUSTOMER","id":1,"name":"Ana Anić","login":"ana.anic@example.com"}}
curl -X POST localhost:8080/api/shop/purchase -H "Authorization: Bearer k3Jv…" -H "Content-Type: application/json" -d "{\"productId\":8}"
```
```json
{"id":7,"createdAt":"2026-09-22T18:04:18.268Z","channel":"WEB","status":"COMPLETED",
 "customerId":1,"customerName":"Ana Anić","productId":8,"brand":"STEAM","productName":"Steam Wallet 25 €",
 "amountEur":25.00,"voucher":{"serial":"STM-25-002","code":"080842214180","status":"SOLD","soldAt":"2026-09-22T18:04:18.268Z"},
 "refundedAt":null}
```

Primjer odbijanja:

```json
{"error":"DAILY_LIMIT_EXCEEDED","message":"Dnevni limit od 150 € je premašen: danas već kupljeno 125 €, pokušaj kupnje 50 €"}
```

ID-jevi u API-ju su `Long` (mogu biti `null` u unit testovima bez baze). Datumi ISO-8601 (UTC), iznosi brojevi.

---

## 8. Demo podaci (`V2__seed.sql`)

**Kupci (id · e-mail / lozinka):** 1 Ana Anić · `ana.anic@example.com` / `ana123` · 2 Marko Marić · `marko.maric@example.com` / `marko123` · 3 Ivana Ivić · `ivana.ivic@example.com` / `ivana123`.
**Admin:** `admin` / `admin123` („NotTrilix podrška“). Lozinke su bcrypt (`crypt(..., gen_salt('bf', 10))`, pgcrypto).

**Proizvodi (id):** A1 5 (1), 10 (2), 20 (3) · HT 5 (4), 10 (5), 25 (6) · Steam 10 (7), 25 (8), 50 (9) · PlayStation 20 (10), 50 (11) · Xbox 15 (12), 30 (13).

**Bonovi:** 8 po proizvodu. Serijski `PREFIX-DENOMINACIJA-NNN` (npr. `STM-25-003`), aktivacijski kod = 12 znamenki, deterministički iz serijskog:
`lpad((abs(hashtextextended(serial_number, 0)) % 1000000000000)::text, 12, '0')` — isti seed = isti kodovi.

**Povijest (transakcije 1–6, datumi relativni na trenutak seeda):**

| # | Kupac | Proizvod | Kanal | Kada | Bon | Status |
|---|---|---|---|---|---|---|
| 1 | Ana | A1 10 € | WEB | −3 d | `A1-10-001` SOLD | COMPLETED |
| 2 | Ana | Steam 25 € | WEB | −10 d | `STM-25-001` REDEEMED | COMPLETED |
| 3 | Marko | HT 5 € | POS | −2 d | `HT-5-001` SOLD | COMPLETED |
| 4 | Marko | PlayStation 20 € | WEB | −20 d | `PSN-20-001` REDEEMED | COMPLETED (izvan roka za povrat) |
| 5 | Ivana | Xbox 15 € | WEB | −5 d | `XBX-15-001` BLOCKED | REFUNDED (−4 d) |
| 6 | Ivana | Steam 50 € | WEB | −1 d | `STM-50-001` SOLD | COMPLETED |

**Prijave:** 1 Ivana / tx 5 „Xbox kod ne radi“ (RESOLVED: „Bon blokiran, iznos vracen na karticu.“) · 2 Marko / tx 3 „Nisam dobio kod e-mailom“ (OPEN).

Svježe stanje: `.\run-phase-1.ps1 -Reset` (briše docker volume → Flyway ponovno migrira i seeda).

---

## 9. Frontend

Zajednički kod `frontend/src/shared`: `api.ts` (fetch wrapper, bearer token u `localStorage` s ključem po aplikaciji, `ApiError` s `status`/`code`/`message`, `isUnauthorized`), `types.ts` (zrcalo DTO recorda), `format.ts` (EUR/datum `hr-HR`, kod `1234-5678-9012`, maskiranje `••••-••••-9012`, HR nazivi statusa), `LoginPage.tsx` (zajednički ekran prijave), `styles.css`.

**Shop (`src/customer/App.tsx`)**
- Ekran prijave (e-mail + lozinka, s popisom demo računa); token se pamti, na startu `GET /api/auth/me`; zaglavlje pokazuje ime i gumb **Odjava**.
- Katalog po kategorijama (Telekom, Gaming) → kartica po brendu → gumb po denominaciji; **bez stanja zalihe** (rasprodano = onemogućen gumb s natpisom).
- Klik = kupnja; uspjeh = zeleni banner sa serijskim brojem i kodom; greška = crveni banner s porukom iz API-ja; 401 = natrag na prijavu.
- „Moje kupnje“: datum, proizvod, iznos, kanal, serijski, kod (maskiran, gumb „prikaži/sakrij“), status (bona ili „Vraćen novac“).

**Admin (`src/admin/App.tsx`)**
- Ekran prijave (korisničko ime + lozinka); zaglavlje s imenom i **Odjava**.
- Tab **Transakcije**: filtri (status, brend, kupac), tablica sa serijskim + kodom, statusima, akcije **Povrat** (samo `COMPLETED`) i **Blokiraj bon** (samo `SOLD`/`AVAILABLE`).
- Tab **Zalihe**: po proizvodu na zalihi / prodano / iskorišteno / blokirano + ukupno.
- Tab **Podrška**: forma za novu prijavu (kupac, transakcija #, naslov, opis) + tablica prijava s inline rješavanjem.
- Rezultat svake akcije ide u banner (uspjeh/greška); 401 = natrag na prijavu.

**Build:** Vite MPA (`vite.config.ts`: `input: { customer: index.html, admin: admin/index.html }`, `outDir: ../src/main/resources/static`, `emptyOutDir: true`).
`npm run build` = `vite build` (bez `tsc`), typecheck zasebno `npm run typecheck`. Dev s hot-reloadom: app na 8080 + `npm run dev` (5173, proxy `/api`).

---

## 10. Logovi

`logback-spring.xml` (uključuje Bootov `defaults.xml` zbog `%clr`):

- **CONSOLE**: `HH:mm:ss.SSS LEVEL thread logger : poruka` u boji (`spring.output.ansi.enabled: always`); skripta postavlja `chcp 65001` + `-Dstdout.encoding=UTF-8` da hrvatski znakovi ostanu čitljivi.
- **FILE**: `logs/nottrilix-2026.log`, dnevna rotacija, 7 dana.
- Razine: `com.nottrilix` DEBUG, root INFO, `org.hibernate.SQL` INFO (DEBUG = ispis svakog SQL-a).

Što se logira:

| Logger | Primjer |
|---|---|
| `HTTP` (filter, samo `/api/**`, s korisnikom) | `POST /api/shop/purchase user=customer:ana.anic@example.com -> 201 (60 ms)`, `GET /api/admin/stock user=admin:admin -> 200 (8 ms)` |
| `AUTH` | `LOGIN OK realm=CUSTOMER id=1 login=ana.anic@example.com`, `LOGIN FAILED realm=ADMIN login=…` (WARN), `TOKEN issued/revoked/expired …`, `LOGOUT …`, `AUTH 401 GET /api/auth/me - prijava je potrebna`, `AUTH 403 GET /api/admin/stock user=CUSTOMER:ana… - nema pravo pristupa` |
| `ShopService` | `PURCHASE request customer=1 product=8 channel=WEB` → `PURCHASE OK tx=7 customer=1 (Ana Anić) product=Steam Wallet 25 € amount=25 € voucher=STM-25-002 channel=WEB spentToday=25 €` |
| `ShopService` (WARN) | `PURCHASE DENIED reason=OUT_OF_STOCK - Proizvod A1 bon 5 € je trenutno rasprodan` |
| `SupportService` | `REFUND OK tx=7 customer=Ana Anić amount=25 € voucher=STM-25-002 SOLD -> BLOCKED`, `BLOCK OK voucher=HT-5-001 SOLD -> BLOCKED`, `TICKET OK id=3 ...`, `TICKET RESOLVED id=3 resolution='...'` |
| `CatalogService` | `CATALOG 13 proizvoda, 13 dostupno`, `STOCK 13 proizvoda` |
| `ApiExceptionHandler` | `NOT FOUND - Transakcija 999 ne postoji`, `VALIDATION - subject je obavezan`, `HTTP 404 - No static resource api/nothing.` |
| Flyway / Tomcat / Boot | migracije, port, `Started NotTrilixApplication in 3.7 seconds` |

---

## 11. Pokretanje i build

`run-phase-1.ps1` (PowerShell 5.1+):

1. **Docker** — `docker info`; ako daemon ne radi, pokrene `Docker Desktop.exe` i čeka do 2 min.
2. `-Reset` → `docker compose down -v`.
3. **PostgreSQL** — `docker compose up -d --wait` (healthcheck `pg_isready`).
4. **JDK 25** — traži `C:\Program Files\Java\jdk-25*`, postavi `JAVA_HOME`/`Path`, provjeri `java -version`.
5. **Build** — `.\mvnw.cmd -ntp -DskipTests package` (preskoči s `-NoBuild` ako jar postoji).
6. **Run** — `java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar target\nottrilix-2026.jar` u istom prozoru (Ctrl+C gasi).
7. `-Stop` → `docker compose down` (samo baza).

Ručno: `JAVA_HOME` na JDK 25 pa `mvn package` (s testovima) ili `mvn -DskipTests package`; frontend preskoči s `-Dskip.installnodenpm -Dskip.npm`.
Maven wrapper (`mvnw`, Maven 3.9.11) je u repou. Node `v22.14.0` skida se u `./node` (gitignored), `npm install` u `frontend/` (lock file je u repou).

Portovi: app **8080**, Postgres **5433** (`nottrilix`/`nottrilix`/`nottrilix`). Container `nottrilix2026-postgres`, volume `nottrilix2026-pgdata`.

---

## 12. Što je verificirano (2026-09-22)

- `mvn package` na JDK 25: 55 klasa, frontend build (Vite), **6/6 testova** (`ShopServiceTest`: prodaja prvog slobodnog bona, `OUT_OF_STOCK`, `DAILY_LIMIT_EXCEEDED`; `NotTrilixAuthenticationProviderTest`: kupac/admin OK, kriva lozinka i nepoznat korisnik ista greška). TypeScript `tsc --noEmit` čist.
- Skripta prošla sve korake (Docker OK → Postgres healthy → JDK 25 → jar → start); app se digne za **3–4 s**; Flyway primijenio V1+V2; restart na postojećoj bazi → „Schema up to date“.
- Prijava i pristup (curl): bez tokena 401 na shop/admin/me; kriva lozinka, nepoznat korisnik i kupčevi podaci na admin loginu → 401 `INVALID_CREDENTIALS`; validacija 400; login kupca i admina → token (43 znaka) + korisnik; kupac → 403 na `/api/admin/**` i `/api/customers`; admin → 403 na `/api/shop/**`; odjava → token više ne vrijedi; izmišljen token 401; statika i Swagger javni; `/v3/api-docs` sadrži bearer shemu; nema Bootove „generated security password“ poruke.
- API (curl): katalog (13, samo `available`, bez količina), kupnja za prijavljenog kupca (201 + kod), kupnje kupca, **dnevni limit** (25+50+50 OK, +50 → 409), **rasprodano** (8× A1 5 € OK, 9. → 409), povrat: rok istekao / iskorišten / već vraćen / OK (bon → BLOCKED) / 404, blokada: OK / već blokiran / iskorišten / 404, zaliha, prijave: mismatch 409 / kreiranje 201 / rješavanje 200 / ponovno 409 / validacija 400, nečitljiv JSON 400, nepoznata ruta 404 (JSON), `/`, `/admin`, `/admin/` 200, Swagger 302.
- UI u pregledniku: ekran prijave shopa → prijava kao Ana → katalog bez količina, klik kupnje → banner s kodom; ekran prijave admina → prijava kao admin → konzola (transakcije, zalihe, podrška s prijavama).
- Logovi: svaki zahtjev (s korisnikom), svaka prijava/odbijanje i svaka poslovna odluka vidljivi u konzoli i u `logs/nottrilix-2026.log`.

---

## 13. Naučeno / zamke

- **Boot 4 (modularni autoconfig):** Flyway treba `spring-boot-starter-flyway` **i** `flyway-database-postgresql`; `spring-boot-starter-web` i dalje radi.
- `@ConfigurationProperties` na recordu + `@ConfigurationPropertiesScan` → bez settera i bez `@EnableConfigurationProperties`.
- **Spring Security 7:** samo lambda DSL; vlastiti `AuthenticationManager` bean (`ProviderManager`) ujedno isključuje Bootov default korisnik + „generated security password“. Bearer filter se **ne** registrira kao `@Component` (inače bi radio i izvan Security lanca), nego `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.
- `@Component` filteri (npr. `RequestLoggingFilter`) rade **iza** Security lanca → vide korisnika, ali ne vide zahtjeve koje je Security odbio (401/403) — te logira entry point / access-denied handler.
- Springove vlastite HTTP greške (`NoResourceFoundException`, 405…) implementiraju `org.springframework.web.ErrorResponse` — catch-all handler ih mora propustiti s izvornim statusom, inače bi 404 statike postao 500.
- **Mockito na JDK 21+/25** samostalno učitava agenta → upozorenje; surefire `argLine` `-XX:+EnableDynamicAgentLoading -Xshare:off` ga uklanja.
- **Windows:** dok app radi, `mvn package` pada na repackageu (jar zaključan) — ugasi app prije builda (ne samo po portu; provjeri `java.exe` procese s `nottrilix-2026`). Stari stack (`mcp-demo`) drži 5432/8081–8083, zato 5433/8080.
- **Vite MPA:** `outDir` izvan roota traži eksplicitni `emptyOutDir: true`; `/admin` bez `index.html` treba `forward` (Boot welcome page radi samo za `/`).
- **PowerShell 5.1:** `2>&1` na native exe-ima uz `$ErrorActionPreference = 'Stop'` puca — skripta koristi `cmd /c "... 2>&1"` za `docker info` i `java -version`.
- Hrvatski znakovi u konzoli: `chcp 65001` + `-Dstdout.encoding=UTF-8`; Logback encoder `charset UTF-8`.
- `hashtextextended()` daje deterministične 12-znamenkaste kodove, `pgcrypto` bcrypt hashove — sve u SQL seedu bez vanjskih alata.
- Promjena V1/V2 (npr. dodavanje lozinki) mijenja Flyway checksume → postojeću bazu treba resetirati (`-Reset`).

---

## 14. Poznata ograničenja (namjerno izvan Faze 1)

- Tokeni žive u memoriji (restart = ponovna prijava); nema osvježavanja tokena, registracije, promjene/reseta lozinke ni ograničenja broja pokušaja prijave.
- Admin vidi pune aktivacijske kodove; nema audita tko je što pogledao (dolazi u kasnijim fazama).
- Nema plaćanja (kupnja uvijek prolazi ako pravila dopuštaju) ni e-mail slanja koda.
- Nema endpointa za iskorištavanje bona (`REDEEMED` samo iz seeda).
- Admin filtri se rade u memoriji nad svim transakcijama; nema paginacije (demo-scale).
- Nema integracijskih testova s bazom (samo unit testovi pravila i prijave).
- Repo još nema remote (GitHub push = TODO).

---

## 15. Sljedeći koraci (prijedlog, po priči predavanja)

| Faza | Što | Što ostaje netaknuto |
|---|---|---|
| 2 | **Tool boundary**: `@Tool` adapteri nad `ShopService`/`SupportService` (tanki, delegiraju) + **audit trag** svakog tool poziva (tablica `audit_event` + `AUDIT` log); sigurnosna granica = samo dopuštene operacije | servisi, pravila, REST, prijava, frontendi |
| 3 | **Light RAG**: nekoliko markdown politika (povrat, rok, „kod ne radi“ procedura) → embeddinzi → vector store u istoj bazi (pgvector image već tu) → `searchKnowledgeBase` tool | sve iz faze 1–2 |
| 4 | **Agent**: Spring AI `ChatClient` (system prompt, memorija po `conversationId`, RAG advisor, tool calling) → `POST /api/agent/chat`; agent radi u ime identificiranog kupca; demo „kod ne radi“ → blokada + prijava | mozak se od tu više ne mijenja |
| 5 | **Mrežni MCP**: tool-ovi kao MCP server (SSE), agent kao MCP klijent | `@Tool` kod |
| 6 | **Glas**: STT → isti `/api/agent/chat` → TTS (ElevenLabs, hrvatski), push-to-talk u browseru | agent API |
| 7 | **Telefonija**: Infobip Calls API (webhook + PCM16 WebSocket), poziv s mobitela — vidi `../mcp-demo/PHONE_NUMBER_RESEARCH.md` i `INFOBIP_STATUS.md` | agent API |

Svaka faza: vlastita grana `phaseN`, commit na kraju, `docs/PHASEN.md` + red u `PROGRESS.md`.
