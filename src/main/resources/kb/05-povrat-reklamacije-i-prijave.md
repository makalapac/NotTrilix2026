---
title: Povrat novca, reklamacije i prijave
category: podrska
tags: [povrat, refund, reklamacija, prijava, ticket, 14 dana, blokada bona, goodwill, wallet, rok]
audience: agent
updated: 2026-09-22
---

# Povrat novca, reklamacije i prijave

## Pravila povrata novca

Povrat novca za kupljeni bon moguć je samo ako su ispunjena sva tri uvjeta:

1. Od kupnje nije prošlo više od 14 dana (rok za povrat).
2. Bon nije iskorišten kod pružatelja (status „aktivan“, ne „iskorišten“).
3. Za tu kupnju već nije napravljen povrat.

Povrat radi podrška (agent) u admin konzoli akcijom „Povrat“ na transakciji. Kupac sam ne može napraviti povrat u web shopu.
Iznos povrata jednak je cijeni bona i vraća se isključivo na wallet kupca, odmah (u knjizi promjena walleta pojavljuje se redak „Povrat“). Povrat na karticu ili bankovni račun nije moguć.
Pri povratu se bon automatski blokira jer je kupac već vidio aktivacijski kod; blokiran bon ne može se iskoristiti.

## Kad sustav odbija povrat

- „Rok za povrat (14 dana) je istekao za transakciju …“ – kupnja je starija od 14 dana; povrat nije moguć ni iznimno, bon ostaje aktivan i kupac ga može iskoristiti.
- „Bon … je već iskorišten - povrat nije moguć“ – pružatelj je potvrdio iskorištenje; kredit je već dodan na račun kod pružatelja.
- „Transakcija … je već vraćena“ – povrat za tu kupnju već postoji; iznos je na walletu.

Agent nikad ne obećava povrat prije nego što sustav potvrdi da je povrat prošao.

## Kada agent nudi povrat ili zamjenu

- Kod ne radi, a pružatelj javlja „aktivan“ i problem se ne rješava ispravkom unosa: ponuditi povrat na wallet (bon se blokira) ili zamjenu (povrat pa nova kupnja istog bona; kupac dobiva novi kod).
- Kupac je greškom kupio pogrešan brend ili denominaciju (npr. HT umjesto A1) i kod nije iskoristio: povrat je moguć unutar 14 dana.
- Kupac se predomislio, a kod nije iskoristio: povrat je moguć unutar 14 dana.
- Kod je iskorišten: povrat nije moguć; vidi goodwill pravila.

## Blokada bona

Agent može blokirati aktivan bon (status „prodan/aktivan“) ili bon na zalihi akcijom „Blokiraj bon“ u admin konzoli.
Blokada se koristi kod sumnje na zlouporabu (kod ukraden ili podijeljen, a još nije iskorišten) ili kad kupac prijavi da mu je kod kompromitiran. Blokiran bon ne može se iskoristiti kod pružatelja.
Iskorišten bon ne može se blokirati (sustav vraća „Bon … je već iskorišten“); već blokiran bon ne može se blokirati ponovno.
Blokada bez povrata ne vraća novac: ako kupac ima pravo na povrat (unutar 14 dana), agent radi i povrat.

## Goodwill uplata na wallet

Kad povrat nije moguć, a NotTrilix je pridonio problemu (npr. kašnjenje, kriva informacija podrške, greška sustava), agent može kupcu uplatiti goodwill iznos na wallet.

- Agent samostalno odobrava goodwill do 10 € po slučaju.
- Iznose iznad 10 € odobrava voditelj podrške.
- Svaka goodwill uplata mora imati napomenu s razlogom (npr. „Goodwill - kašnjenje koda“); uplata bez napomene nije moguća.
- Goodwill nije zamjena za povrat iskorištenog bona kad je kupac sam iskoristio kod.

## Prijave (ticketi)

Prijava (ticket) je zapis o problemu kupca koji otvara podrška; kupac sam ne otvara prijave u web shopu (javlja se telefonom ili e-mailom).
Prijava sadrži kupca, po želji transakciju (kupnju) na koju se odnosi, naslov i opis. Transakcija u prijavi mora pripadati istom kupcu.
Statusi prijave: otvorena i riješena. Prijava se zatvara s opisom rješenja (npr. „Bon blokiran, iznos vraćen na wallet.“).

## Rokovi rješavanja prijava

- Prvi odgovor kupcu: najkasnije sljedeći radni dan od otvaranja prijave.
- Rješenje: u pravilu unutar 3 radna dana; slučajevi koji traže provjeru kod pružatelja mogu trajati dulje, o čemu se kupca obavještava.
- Kupac o rješenju dobiva obavijest e-mailom ili telefonom; stanje walleta i status bona odmah vidi u web shopu.
