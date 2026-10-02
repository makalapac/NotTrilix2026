// Faza 4: networked MCP. There is nothing new to click, the story is in the console: one confirmed purchase seen from
// both processes, then MCP called directly (without the agent). A replay of real log lines in a terminal page.
'use strict';
const path = require('path');
const fs = require('fs');
const { start } = require('./lib');

(async () => {
  const data = JSON.parse(fs.readFileSync(path.join(__dirname, 'faza4-konzola.json'), 'utf8'));
  const demo = await start('04-faza4-mcp');
  const { page } = demo;
  await page.addInitScript((d) => { window.DEMO_DATA = d; }, data);
  await page.goto('file:///' + path.join(__dirname, 'terminal.html').replace(/\\/g, '/'));
  await demo.caption('Faza 4 · Mrežni MCP', 'sustav = MCP server (POST /mcp, HTTP bez sesije) · agent = zasebna aplikacija i MCP klijent · token kupca u svakom pozivu');
  await page.waitForFunction(() => document.title === 'done', null, { timeout: 180000 });
  await demo.pause(3000);
  await demo.caption('', '');
  console.log('video:', await demo.finish());
})().catch((e) => { console.error(e); process.exit(1); });
