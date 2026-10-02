// Faza 3: the agent. Marko chats with Nola: wallet, a voucher check, a purchase that waits for confirmation,
// confirmation, the tool trace under the reply; then the audit trail in the admin.
'use strict';
const { start, loginShop, loginAdmin, adminTab, openChat, chat } = require('./lib');

(async () => {
  const demo = await start('03-faza3-agent');
  const { page } = demo;
  await loginShop(demo, 'marko.maric@example.com', 'marko123');
  await demo.caption('Faza 3 · Agent Nola', 'Spring AI ChatClient: prompt + memorija + RAG + alati nad postojećim servisima');
  await openChat(demo);
  await demo.pause(2000);

  await demo.caption('Alat čita podatke prijavljenog kupca', 'kupac dolazi iz prijave, ne iz razgovora');
  await chat(demo, 'Koliko imam na walletu?');

  await demo.caption('Provjera kod pružatelja', 'pravila i podaci iz sustava, ne iz modela');
  await chat(demo, 'Moj HT kod ne radi, kaže da je nevažeći.');

  await demo.caption('Radnja koja mijenja novac čeka potvrdu', 'prijedlog drži server (ConfirmationGate), model ne zna id-eve');
  await chat(demo, 'Kupi mi HT bon od 10 eura.');
  await demo.pause(1500);
  await demo.click('.quick-replies button:has-text("Da, potvrđujem")', 800);
  await demo.waitForReply();
  await demo.read();

  await demo.caption('Što je agent napravio', 'alati s ishodom i odlomci baze znanja, ispod svakog odgovora');
  await demo.click('.msg.agent details.activity summary >> nth=-1', 3500);

  await demo.caption('Audit trag u sustavu', 'svaki poziv alata i svaki turn: tko, što, ishod, trajanje');
  await loginAdmin(demo);
  await adminTab(demo, 'Audit');
  await demo.pause(5000);
  await demo.caption('', '');
  console.log('video:', await demo.finish());
})().catch((e) => { console.error(e); process.exit(1); });
