// Faza 5b: what remains after the conversation. Ana asks something the knowledge base does not cover (R1 invoice),
// the admin analyses the conversation (structured findings, knowledge gap), approves the suggested section, and a
// new conversation answers from it. Then the customer's privacy screen: export and erasure.
'use strict';
const { start, loginShop, loginAdmin, adminTab, openChat, chat } = require('./lib');

(async () => {
  const demo = await start('05b-faza5-analiza-gdpr');
  const { page } = demo;
  await loginShop(demo, 'ana.anic@example.com', 'ana123');
  await demo.caption('Faza 5 · Razgovor kao zapis', 'audit trag iz Faze 4 je transkript: ništa se ne bilježi dvaput');
  await openChat(demo);
  await demo.click('.chat-header button:has-text("Novi")', 600);
  await demo.pause(800);
  await chat(demo, 'Moj Steam kod ne radi, kaže da je nevažeći.');
  await demo.caption('Pitanje koje baza znanja ne pokriva', 'agent pošteno kaže da nema informaciju');
  await chat(demo, 'Mogu li za tu kupnju dobiti R1 račun na tvrtku?');
  await chat(demo, 'A možete li mi bon poslati poštom na kućnu adresu?');
  await demo.pause(1500);

  await demo.caption('Post-analiza razgovora', 'pseudonimizirani transkript → gpt-4o → strukturirani nalaz: tema, ishod, raspoloženje, ocjena, rupe u znanju');
  await loginAdmin(demo);
  await adminTab(demo, 'Razgovori');
  await demo.pause(2000);
  await demo.click('tbody tr >> nth=0 >> button:has-text("Analiziraj")', 500);
  await page.waitForFunction(() => {
    const row = document.querySelector('tbody tr');
    return row && /Riješeno|Neriješeno|Eskalirano|Odustao/.test(row.textContent);
  }, null, { timeout: 90000 });
  await demo.pause(4500);
  await demo.click('tbody tr >> nth=0 >> button.link', 1500);
  await demo.caption('Transkript iz audit traga', 'KUPAC / ALAT / ZNANJE / AGENT po turnovima, nalaz iznad');
  await demo.pause(5000);
  await demo.click('button:has-text("Zatvori")', 800);

  await demo.caption('Samoučenje s čovjekom u petlji', 'rupa u znanju → prijedlog odlomka → admin ispravi i odobri → RAG');
  await adminTab(demo, 'Znanje');
  let learned = false;
  try {
    await page.waitForSelector('.suggestions textarea', { timeout: 20000 });
    await demo.pause(3000);
    // the first proposal is about the R1 invoice (the first gap); the admin writes the real policy
    const question = (await page.locator('.suggestions input').first().inputValue()).toLowerCase();
    const answer = question.includes('r1') || question.includes('račun')
      ? 'R1 račun na tvrtku izdaje se naknadno: pošaljite broj kupnje, naziv tvrtke i OIB na racuni@nottrilix.hr; račun stiže e-mailom u roku od 2 radna dana.'
      : 'Bonovi se ne šalju poštom: aktivacijski kod je odmah vidljiv u web shopu pod Moje kupnje i vrijedi kao i fizički bon.';
    await page.locator('.suggestions textarea').first().fill('');
    await demo.type('.suggestions textarea', answer);
    await demo.pause(800);
    await demo.click('button:has-text("Odobri i uči")', 2500);
    learned = question.includes('r1') || question.includes('račun');
  } catch (e) {
    console.warn('no suggestion appeared:', e.message);
    await demo.caption('Samoučenje', 'ovaj put analiza nije našla rupu u znanju');
    await demo.pause(2500);
  }

  await demo.caption('Agent odmah zna', 'novi razgovor, isto pitanje, odgovor iz naučenog odlomka');
  await page.goto('http://localhost:8080/');
  await page.waitForSelector('text=Moj wallet');
  await openChat(demo);
  await chat(demo, learned ? 'Trebam račun na firmu za bon, kako to ide?' : 'Možete li mi bon poslati poštom?');
  await demo.click('.msg.agent details.activity summary >> nth=-1', 3500);

  await demo.caption('Prava kupca (GDPR)', 'izvoz svih podataka, privola, brisanje razgovora; retencija 30 / 365 dana po rasporedu');
  await demo.click('.chat-header button:has-text("Zatvori")', 800); // the chat panel covers the header button
  await demo.click('button[title="Moji podaci, privole, izvoz i brisanje"]', 1500);
  await page.waitForSelector('.modal.privacy');
  await demo.pause(4000);
  await demo.click('.modal.privacy button:has-text("Obriši moje razgovore")', 1200);
  await demo.pause(1500);
  await demo.click('.confirm-erase button.danger', 2500);
  await demo.pause(3000);
  await demo.click('.modal.privacy button:has-text("Zatvori")', 800);
  await demo.caption('', '');
  console.log('video:', await demo.finish());
})().catch((e) => { console.error(e); process.exit(1); });
