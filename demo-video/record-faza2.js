// Faza 2: the knowledge base. Admin tab "Znanje": status, semantic search with scores, differently worded questions
// finding the same section.
'use strict';
const { start, loginAdmin, adminTab } = require('./lib');

(async () => {
  const demo = await start('02-faza2-znanje');
  const { page } = demo;
  await loginAdmin(demo);
  await demo.caption('Faza 2 · Baza znanja za RAG', '10 markdown dokumenata pisanih po odlomcima → Spring AI EmbeddingModel → pgvector u istoj bazi');
  await adminTab(demo, 'Znanje');
  await demo.pause(3500);

  await demo.caption('Semantička pretraga', 'pitanje kupca → najsličniji odlomci s ocjenom (prag 0,35)');
  await demo.type('input[placeholder^="Pitanje kupca"]', 'mogu li vratiti bon nakon 20 dana');
  await demo.pause(300);
  await demo.click('button:has-text("Pretraži")', 1000);
  await page.waitForTimeout(4000);

  await demo.caption('Isto pitanje, druge riječi', 'značenje, ne ključne riječi');
  await page.locator('input[placeholder^="Pitanje kupca"]').fill('');
  await demo.type('input[placeholder^="Pitanje kupca"]', 'prošlo je tri tjedna od kupnje, hoću novac natrag');
  await demo.click('button:has-text("Pretraži")', 1000);
  await page.waitForTimeout(4500);

  await demo.caption('Primjeri iz podrške', '„Steam kaže da je kod već iskorišten“');
  await demo.click('button:has-text("Steam kaže da je kod već iskorišten")', 1000);
  await page.waitForTimeout(4500);
  await demo.caption('', '');
  console.log('video:', await demo.finish());
})().catch((e) => { console.error(e); process.exit(1); });
