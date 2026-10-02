// Shared helpers for the fallback demo videos: a recorded Chromium (Playwright) drives the real shop and admin
// against the running system and agent, with a visible cursor, captions and unhurried typing, and the recording is
// converted to MP4 (H.264) with ffmpeg. Videos land in demo-video/out/.
'use strict';

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const OUT = path.join(__dirname, 'out');
const SIZE = { width: 1280, height: 720 };
const SHOP = 'http://localhost:8080/';
const ADMIN = 'http://localhost:8080/admin/';
const TYPE_DELAY = 55;

// A cursor the viewer can follow (Playwright videos have none), a click ripple, a caption bar and a slight zoom.
const OVERLAY_SCRIPT = `
(() => {
  const style = document.createElement('style');
  style.textContent = \`
    body { zoom: 1.12; }
    #demo-cursor { position: fixed; z-index: 2147483647; width: 22px; height: 22px; margin: -4px 0 0 -4px; pointer-events: none;
      border-radius: 50%; border: 3px solid #0f766e; background: rgba(15,118,110,.25); box-shadow: 0 0 0 2px #fff; transition: transform .08s; }
    #demo-cursor.down { transform: scale(.7); background: rgba(220,38,38,.5); border-color: #dc2626; }
    #demo-caption { position: fixed; left: 0; right: 0; bottom: 0; z-index: 2147483646; pointer-events: none;
      background: rgba(17,24,39,.88); color: #fff; font: 600 20px/1.3 system-ui, sans-serif; padding: 12px 22px; display: none; }
    #demo-caption small { display: block; font-weight: 400; font-size: 15px; opacity: .85; margin-top: 2px; }
  \`;
  const install = () => {
    if (document.getElementById('demo-cursor')) return;
    document.head.appendChild(style);
    const cursor = document.createElement('div'); cursor.id = 'demo-cursor'; cursor.style.left = '-50px'; cursor.style.top = '-50px';
    const caption = document.createElement('div'); caption.id = 'demo-caption';
    document.body.appendChild(cursor); document.body.appendChild(caption);
    try {
      // the caption survives navigation (shop -> admin) within the same origin
      const saved = JSON.parse(sessionStorage.getItem('demo-caption') || 'null');
      if (saved && saved[0]) { caption.innerHTML = saved[0] + (saved[1] ? '<small>' + saved[1] + '</small>' : ''); caption.style.display = 'block'; }
    } catch (e) { /* no storage */ }
    document.addEventListener('mousemove', (e) => { cursor.style.left = e.clientX + 'px'; cursor.style.top = e.clientY + 'px'; }, true);
    document.addEventListener('mousedown', () => cursor.classList.add('down'), true);
    document.addEventListener('mouseup', () => setTimeout(() => cursor.classList.remove('down'), 120), true);
  };
  if (document.body) install(); else document.addEventListener('DOMContentLoaded', install);
})();`;

async function start(name, options = {}) {
  fs.mkdirSync(path.join(OUT, 'raw'), { recursive: true });
  const args = ['--autoplay-policy=no-user-gesture-required'];
  if (options.fakeAudioWav) {
    args.push('--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream',
      `--use-file-for-fake-audio-capture=${options.fakeAudioWav}%noloop`);
  }
  const browser = await chromium.launch({ headless: true, args });
  const context = await browser.newContext({
    viewport: SIZE,
    recordVideo: { dir: path.join(OUT, 'raw'), size: SIZE },
    locale: 'hr-HR',
    permissions: options.fakeAudioWav ? ['microphone'] : [],
  });
  await context.addInitScript(OVERLAY_SCRIPT);
  const page = await context.newPage();
  const startedAt = Date.now();
  const marks = [];
  const demo = {
    browser, context, page, marks,
    /** Seconds since the recording started (for muxing audio later). */
    t: () => (Date.now() - startedAt) / 1000,
    mark: (label) => marks.push({ label, t: (Date.now() - startedAt) / 1000 }),
    pause: (ms) => page.waitForTimeout(ms),
    caption: async (title, subtitle = '') => {
      await page.evaluate(([t, s]) => {
        try { sessionStorage.setItem('demo-caption', JSON.stringify([t, s])); } catch (e) { /* no storage */ }
        const el = document.getElementById('demo-caption');
        if (!el) return;
        el.innerHTML = t ? `${t}${s ? `<small>${s}</small>` : ''}` : '';
        el.style.display = t ? 'block' : 'none';
      }, [title, subtitle]);
    },
    /** Moves the cursor, clicks, and lets the viewer see the result. */
    click: async (selector, after = 600) => {
      const locator = page.locator(selector).first();
      await locator.waitFor({ state: 'visible', timeout: 30000 });
      await locator.hover();
      await page.waitForTimeout(250);
      await locator.click();
      await page.waitForTimeout(after);
    },
    type: async (selector, text) => {
      const locator = page.locator(selector).first();
      await locator.click();
      await locator.pressSequentially(text, { delay: TYPE_DELAY });
    },
    /** Waits until the chat's "Nola piše…" bubble is gone (reply arrived). */
    waitForReply: async (timeout = 90000) => {
      await page.waitForTimeout(600);
      await page.waitForFunction(() => !document.querySelector('.chat-messages .bubble.muted'), null, { timeout });
      await page.waitForTimeout(400);
    },
    /** Reading time for a reply: roughly 55 ms per character, 2–8 s. */
    read: async (selector = '.msg.agent .bubble') => {
      const text = await page.locator(selector).last().innerText().catch(() => '');
      await page.waitForTimeout(Math.min(8000, Math.max(2000, text.length * 55)));
    },
    finish: async () => {
      await page.waitForTimeout(1200);
      const video = page.video();
      await context.close();
      const webm = await video.path();
      await browser.close();
      const mp4 = path.join(OUT, `${name}.mp4`);
      toMp4(webm, mp4);
      fs.writeFileSync(path.join(OUT, `${name}.marks.json`), JSON.stringify(marks, null, 2));
      return mp4;
    },
  };
  return demo;
}

function toMp4(webm, mp4) {
  execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', webm, '-c:v', 'libx264', '-preset', 'medium', '-crf', '20',
    '-pix_fmt', 'yuv420p', '-movflags', '+faststart', mp4], { stdio: 'inherit' });
}

async function loginShop(demo, email, password) {
  await demo.page.goto(SHOP);
  await demo.page.waitForSelector('input[type=email]');
  await demo.type('input[type=email]', email);
  await demo.type('input[type=password]', password);
  await demo.click('button:has-text("Prijavi se")', 1200);
  await demo.page.waitForSelector('text=Moj wallet');
}

async function loginAdmin(demo) {
  await demo.page.goto(ADMIN);
  await demo.page.waitForSelector('input[placeholder="admin"]');
  await demo.type('input[placeholder="admin"]', 'admin');
  await demo.type('input[type=password]', 'admin123');
  await demo.click('button:has-text("Prijavi se")', 1200);
  await demo.page.waitForSelector('nav.tabs');
}

async function adminTab(demo, label) {
  await demo.click(`nav.tabs button:has-text("${label}")`, 900);
}

async function openChat(demo) {
  await demo.click('button:has-text("Chat s podrškom")', 900);
  await demo.page.waitForSelector('.chat-panel');
}

/** Types a message into the chat, sends it, waits for Nola's reply and lets the viewer read it. */
async function chat(demo, text) {
  await demo.type('.chat-input input', text);
  await demo.pause(400);
  await demo.page.keyboard.press('Enter');
  await demo.waitForReply();
  await demo.read();
}

module.exports = { start, loginShop, loginAdmin, adminTab, openChat, chat, SHOP, ADMIN, OUT };
