// Faza 1: the existing system. Ivana buys a voucher from her wallet (confirmation dialog), sees the masked code,
// runs into a rule (not enough money for Steam 50 €); the admin sees transactions, stock and tickets.
'use strict';
const { start, loginShop, loginAdmin, adminTab } = require('./lib');

(async () => {
  const demo = await start('01-faza1-sustav');
  const { page } = demo;
  await loginShop(demo, 'ivana.ivic@example.com', 'ivana123');
  await demo.caption('Faza 1 · Postojeći sustav', 'Spring Boot + PostgreSQL: shop, wallet, bonovi, pravila u servisnom sloju');
  await demo.pause(3000);

  await demo.caption('Kupnja s walleta uz potvrdu', 'HT bon 10 € · stanje walleta 50 €');
  await demo.click('button[title^="Kupi HT bon 10"]', 1500);
  await page.waitForSelector('text=Naplata s walleta');
  await demo.pause(2200);
  await demo.click('button:has-text("Da, naplati s walleta")', 1500);
  await page.waitForSelector('text=HT-10-00');
  await demo.caption('Kod je skriven', 'kupac vidi zadnje 4 znamenke; puni kod samo na zahtjev vlasnika');
  await demo.pause(1500);
  await demo.click('button:has-text("prikaži")', 2500);

  await demo.caption('Pravilo u kodu, ne u dijalogu', 'Steam Wallet 50 € premašuje stanje walleta');
  await demo.click('button[title^="Kupi Steam Wallet 50"]', 1500);
  await page.waitForSelector('text=Nedovoljno sredstava');
  await demo.pause(2800);
  await demo.click('button:has-text("Odustani")', 1200);

  await demo.caption('Admin', 'ista baza, ista pravila: transakcije, zaliha, prijave podrške');
  await loginAdmin(demo);
  await demo.pause(2500);
  await adminTab(demo, 'Zalihe');
  await demo.pause(2500);
  await adminTab(demo, 'Podrška');
  await demo.pause(2500);
  await demo.caption('', '');
  console.log('video:', await demo.finish());
})().catch((e) => { console.error(e); process.exit(1); });
