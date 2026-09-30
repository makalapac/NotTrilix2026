---
title: Privatnost i osobni podaci kupca (GDPR)
category: privatnost
tags: [privatnost, gdpr, osobni podaci, privola, snimanje, glas, transkript, brisanje, izvoz podataka, rok čuvanja, obrada]
audience: agent
updated: 2026-09-30
---

# Privatnost i osobni podaci kupca (GDPR)

## Koje podatke NotTrilix obrađuje u podršci

NotTrilix u podršci obrađuje: ime i e-mail kupca (iz računa), kupnje i stanje walleta, serijske brojeve bonova, poruke kupca i odgovore agenta u chatu, te transkript glasovnih poruka.
Svrha obrade je pružanje podrške i izvršenje kupnje (ugovor s kupcem) te sigurnost sustava.
Voditelj obrade je NotTrilix. Kupac se za pitanja o podacima može obratiti na e-mail privatnost@nottrilix.hr.

## Tko obrađuje razgovor i gdje

Odgovore agentice Nole stvara jezični model tvrtke OpenAI: poruke kupca i podaci potrebni za odgovor šalju se OpenAI-ju kao izvršitelju obrade, s ugovorom o obradi podataka; OpenAI takve podatke ne koristi za učenje svojih modela.
Glasovna poruka se pretvara u tekst kod OpenAI-ja, a odgovor u govor kod ElevenLabsa.
Podaci se ne dijele s pružateljima bonova (A1, HT, Steam, PlayStation, Xbox) osim serijskog broja pri provjeri bona.

## Snimanje glasa: što se čuva, a što ne

Snimka glasa kupca se ne pohranjuje: obradi se u tekst i odmah odbaci. Čuva se samo transkript, jednako kao poruka u chatu.
Za glasovni razgovor potrebna je privola kupca; kupac je daje u web shopu pri prvom korištenju mikrofona i može je povući u odjeljku Privatnost.
Glas kupca se ne koristi za prepoznavanje identiteta (nema biometrijske obrade).

## Koliko dugo se čuvaju razgovori

Poruke razgovora (chat i transkripti glasa) čuvaju se 30 dana, a zatim se automatski brišu.
Zapisi o radnjama u sustavu (kupnja, povrat, blokada) ostaju uz kupnju dok traje ugovorni odnos, ali se nakon 30 dana odvajaju od razgovora.
Analize razgovora (sažetak, tema, ocjena) čuvaju se do godinu dana, a nakon 30 dana više nisu povezane s kupcem.
Tehnički logovi sustava čuvaju se 7 dana.

## Analiza razgovora i učenje iz razgovora

NotTrilix nakon razgovora automatski analizira transkript (sažetak, tema, ishod, ocjena rada agenta) radi kvalitete podrške.
Prije analize iz transkripta se uklanjaju ime, e-mail, telefon, serijski brojevi i kodovi; model vidi razgovor bez identiteta kupca.
Iz analize mogu nastati prijedlozi za bazu znanja podrške; oni su poopćeni (pitanje i odgovor bez osobnih podataka) i ulaze u bazu znanja tek kad ih odobri voditelj podrške.
Razgovori se ne koriste za učenje jezičnog modela.

## Prava kupca: pristup, izvoz, brisanje

Kupac može u web shopu, pod Privatnost, preuzeti sve svoje podatke u strojno čitljivom obliku (JSON): račun, kupnje, wallet, prijave, razgovore i analize.
Kupac može u web shopu, pod Privatnost, obrisati svoje razgovore: poruke se brišu odmah, a ostali zapisi gube vezu s kupcem.
Kupnje, bonovi i wallet se ne brišu na zahtjev jer su potrebni za ugovor i računovodstvo; brisanje cijelog računa traži zahtjev podršci (prijava za voditelja).
Agent podrške ne može sam obrisati ni izvesti podatke kupca: kupca upućuje na odjeljak Privatnost u web shopu ili otvara prijavu.

## Što agent smije i ne smije s osobnim podacima

Agent koristi samo podatke kupca s kojim razgovara i samo one koje mu treba za konkretnu radnju.
Agent ne traži podatke koje ne treba (adresa, datum rođenja, podaci kartice, lozinka) i ne ponavlja osobne podatke kupca u odgovoru bez potrebe.
Ako kupac u razgovoru spomene tuđe osobne podatke, agent ih ne obrađuje i ne zapisuje u prijavu.

## Najčešća pitanja o privatnosti

Snimate li moj glas? Snimka se ne čuva; čuva se samo transkript, 30 dana.
Koristite li moje razgovore za treniranje umjetne inteligencije? Ne. Razgovori se koriste samo za podršku i analizu kvalitete; ne koriste se za učenje jezičnog modela.
Mogu li vidjeti što o meni čuvate? Da, izvoz podataka je u web shopu pod Privatnost.
Kako obrisati razgovore? U web shopu pod Privatnost, gumb za brisanje razgovora; briše se odmah.
Tko još vidi moje razgovore? Voditelj i agenti podrške (radi kvalitete i reklamacija) te izvršitelji obrade OpenAI i ElevenLabs, samo za pružanje usluge.
