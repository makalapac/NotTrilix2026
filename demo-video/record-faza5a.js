// Faza 5a: voice in the browser. Chromium's fake microphone plays a real recording ("Kupi mi HT bon od deset eura."),
// so the whole path runs for real: consent, STT, the agent with its tools, streamed TTS. Nola's reply audio is
// captured from the stream response and muxed into the video afterwards (the recording itself has no sound).
'use strict';
const path = require('path');
const fs = require('fs');
const { execFileSync } = require('child_process');
const { start, loginShop, openChat, OUT } = require('./lib');

(async () => {
  const sample = path.join(__dirname, 'samples', 'kupi-ht-bon.wav');
  const demo = await start('05a-faza5-glas', { fakeAudioWav: sample });
  const { page } = demo;
  // The page keeps Nola's speech only as blobs; remember every audio blob the app turns into an object URL and every
  // play() with its moment, so the sound can be put back into the silent recording afterwards.
  await page.addInitScript(() => {
    window.__demoAudio = { blobs: [], plays: [] };
    const createObjectURL = URL.createObjectURL.bind(URL);
    URL.createObjectURL = (obj) => {
      const url = createObjectURL(obj);
      if (obj instanceof Blob && obj.type.startsWith('audio/')) window.__demoAudio.blobs.push({ url, blob: obj });
      return url;
    };
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      window.__demoAudio.plays.push({ t: performance.now(), src: this.currentSrc || this.src });
      return play.call(this);
    };
  });
  const pageOpenedAt = demo.t();

  await loginShop(demo, 'ana.anic@example.com', 'ana123');
  await demo.caption('Faza 5 · Glas', 'isti agent, novi kanal: snimka → STT (OpenAI) → Nola → TTS (ElevenLabs, glas Maja) → govor');
  await openChat(demo);
  await demo.pause(1500);

  await demo.caption('Privola za glas (GDPR)', 'govor obrađuju vanjski izvršitelji; verzija teksta privole se pamti');
  await demo.click('.chat-input .mic', 1200);
  await page.waitForSelector('.consent-box');
  await demo.pause(3500);
  await demo.click('.consent-box button:has-text("Slažem se")', 1200);

  await demo.caption('Kupac govori: „Kupi mi HT bon od deset eura.“', 'snimanje stane samo kad kupac zašuti; transkript se vidi odmah, odgovor stiže kao stream');
  await demo.click('.chat-input .mic', 500);
  await page.waitForSelector('.msg.user .voice-mark', { timeout: 30000 });
  await page.waitForFunction(() => {
    const users = document.querySelectorAll('.msg.user .bubble');
    return users.length > 0 && !users[users.length - 1].textContent.includes('🎤 …');
  }, null, { timeout: 60000 });
  demo.mark('transcript');
  await demo.waitForReply(90000);
  demo.mark('reply');
  await demo.read();
  await demo.pause(6000); // the spoken reply (muxed in afterwards) is longer than the reading time

  await demo.caption('Potvrda radi i ovdje', 'ista brana na serveru; potvrda može biti glasom ili klikom');
  await demo.click('.quick-replies button:has-text("Da, potvrđujem")', 800);
  await demo.waitForReply();
  await demo.read();
  await demo.pause(2000);

  await demo.caption('Što sustav čuva', 'transkript kao poruka u auditu; snimka se ne pohranjuje');
  await demo.click('.msg.agent details.activity summary >> nth=-1', 3500);
  await demo.caption('', '');

  // Collect the audio before the page goes away: blobs as base64, play moments converted to video time.
  const captured = await page.evaluate(async () => {
    const toBase64 = (blob) => new Promise((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result.split(',')[1]);
      reader.readAsDataURL(blob);
    });
    const blobs = [];
    for (const b of window.__demoAudio.blobs) blobs.push({ url: b.url, size: b.blob.size, data: await toBase64(b.blob) });
    return { blobs, plays: window.__demoAudio.plays, now: performance.now() };
  });
  const nowVideo = demo.t();
  const videoTime = (perfMs) => nowVideo - (captured.now - perfMs) / 1000;
  const blobUrls = new Set(captured.blobs.map((b) => b.url));
  const audioClips = [];
  // Nola's full replies are the audio blobs the app created after playback; the filler is the small one played directly.
  const replies = captured.blobs.filter((b) => b.size > 20000);
  const filler = captured.blobs.find((b) => b.size <= 20000);
  const streamPlays = captured.plays.filter((p) => !blobUrls.has(p.src));          // MediaSource-backed playback
  streamPlays.forEach((p, i) => {
    if (!replies[i]) return;
    const file = path.join(OUT, 'raw', `nola-${i + 1}.mp3`);
    fs.writeFileSync(file, Buffer.from(replies[i].data, 'base64'));
    audioClips.push({ t: videoTime(p.t), file });
  });
  if (filler) {
    const file = path.join(OUT, 'raw', 'nola-filler.mp3');
    fs.writeFileSync(file, Buffer.from(filler.data, 'base64'));
    captured.plays.filter((p) => p.src === filler.url).forEach((p) => audioClips.push({ t: videoTime(p.t), file }));
  }
  console.log('audio:', captured.blobs.length, 'blobs,', captured.plays.length, 'plays ->', audioClips.length, 'clips');
  const mp4 = await demo.finish();

  // Mux Nola's speech into the video at the moments it played.
  if (audioClips.length > 0) {
    const inputs = [];
    const filters = [];
    audioClips.forEach((clip, i) => {
      inputs.push('-i', clip.file);
      const delay = Math.max(0, Math.round(clip.t * 1000));
      filters.push(`[${i + 1}]adelay=${delay}|${delay}[a${i}]`);
    });
    const mix = audioClips.map((_, i) => `[a${i}]`).join('') + `amix=inputs=${audioClips.length}:normalize=0[a]`;
    const withAudio = mp4.replace(/\.mp4$/, '-zvuk.mp4');
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-i', mp4, ...inputs, '-filter_complex', filters.concat(mix).join(';'),
      '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k', withAudio], { stdio: 'inherit' });
    fs.renameSync(withAudio, mp4);
    console.log('audio muxed:', audioClips.map((c) => `${c.t.toFixed(1)}s`).join(', '));
  }
  console.log('video:', mp4);
})().catch((e) => { console.error(e); process.exit(1); });
