# -*- coding: utf-8 -*-
"""Test audio for the voice video: Chromium's fake microphone plays a WAV file, so the recorded browser really goes
through STT -> agent -> TTS. Reads openai.key only to synthesize the sample; nothing is printed."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
import phone_sim  # noqa: E402  (synthesize_pcm16k, wav)

OUT = os.path.join(HERE, "samples")
os.makedirs(OUT, exist_ok=True)
for name, text in (("kupi-ht-bon.wav", "Kupi mi HT bon od deset eura."), ("koliko-wallet.wav", "Koliko imam na walletu?")):
    pcm = phone_sim.synthesize_pcm16k(text)
    # a second of silence first (the recorder's VAD needs to see speech start), then the utterance, then silence to end it
    silence = bytes(16000 * 2)
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(phone_sim.wav(silence + pcm + silence * 2))
    print("ok", name, len(pcm) // 32, "ms govora")
