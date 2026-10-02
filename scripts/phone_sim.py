# -*- coding: utf-8 -*-
"""
Phone call simulator (Phase 6): plays the part of Infobip against the running agent, without a phone, a number or a tunnel.

  .\\run-phase-6.ps1 -NoTunnel   (or run-phase-5.ps1; the agent must be up on :8090, the system on :8080)
  $env:PYTHONIOENCODING = "utf-8"
  python scripts\\phone_sim.py                       # caller = Ana's number from the seed
  python scripts\\phone_sim.py --from "+385 99 000 0000"   # unknown caller: greeting + hangup

What it does: POST /infobip/call-received (the caller id), then opens ws://localhost:8090/infobip/media, sends the
metadata line and streams 20 ms PCM16 frames at 16 kHz in real time (speech synthesized with OpenAI TTS, silence
in between), receives Nola's frames and transcribes them (OpenAI STT) so you can read the call. Keys are read from
openai.key only to make and read the test audio; nothing is printed. Audio of the whole call: logs/poziv-nola.wav.
"""
import argparse
import base64
import json
import os
import socket
import struct
import sys
import threading
import time
import urllib.error
import urllib.request
from array import array

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")
AGENT = ("localhost", 8090)
SYSTEM = "http://localhost:8080"
RATE = 16000
FRAME_MS = 20
FRAME_BYTES = RATE * 2 * FRAME_MS // 1000  # 640


def openai_key():
    with open(os.path.join(ROOT, "openai.key"), encoding="utf-8") as f:
        return f.read().strip()


# ---------- test audio: OpenAI TTS (pcm 24 kHz) -> 16 kHz ----------

def synthesize_pcm16k(text):
    payload = json.dumps({"model": "gpt-4o-mini-tts", "voice": "echo", "input": text, "response_format": "pcm",
                          "instructions": "Govori hrvatski jezik, prirodno i jasno, kao kupac koji zove korisničku podršku."}).encode("utf-8")
    req = urllib.request.Request("https://api.openai.com/v1/audio/speech", data=payload, method="POST",
                                 headers={"Authorization": "Bearer " + openai_key(), "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as res:
        pcm24 = res.read()
    samples = array("h")
    samples.frombytes(pcm24[: len(pcm24) - len(pcm24) % 2])
    out = array("h")
    ratio = 24000 / RATE
    n = int(len(samples) / ratio)
    for i in range(n):
        pos = i * ratio
        j = int(pos)
        frac = pos - j
        a = samples[j]
        b = samples[j + 1] if j + 1 < len(samples) else a
        out.append(int(a + (b - a) * frac))
    return out.tobytes()


def wav(pcm, rate=RATE):
    header = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", 36 + len(pcm), b"WAVE", b"fmt ", 16, 1, 1, rate, rate * 2, 2, 16,
                         b"data", len(pcm))
    return header + pcm


def transcribe(pcm):
    if not pcm:
        return "(tišina)"
    boundary = "----NotTrilixPhoneSim"
    body = b"".join([
        ("--%s\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-4o-mini-transcribe\r\n" % boundary).encode(),
        ("--%s\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\nhr\r\n" % boundary).encode(),
        ("--%s\r\nContent-Disposition: form-data; name=\"prompt\"\r\n\r\nNotTrilix podrška, agentica Nola, HT bon, wallet, eura.\r\n" % boundary).encode(),
        ("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"nola.wav\"\r\nContent-Type: audio/wav\r\n\r\n" % boundary).encode(),
        wav(pcm), ("\r\n--%s--\r\n" % boundary).encode()])
    req = urllib.request.Request("https://api.openai.com/v1/audio/transcriptions", data=body, method="POST",
                                 headers={"Authorization": "Bearer " + openai_key(),
                                          "Content-Type": "multipart/form-data; boundary=" + boundary})
    with urllib.request.urlopen(req, timeout=120) as res:
        return json.loads(res.read().decode("utf-8")).get("text", "")


# ---------- a very small WebSocket client (RFC 6455, client side) ----------

class WebSocketClient:
    def __init__(self, host, port, path):
        self.sock = socket.create_connection((host, port), timeout=30)
        key = base64.b64encode(os.urandom(16)).decode()
        request = ("GET %s HTTP/1.1\r\nHost: %s:%d\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                   "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (path, host, port, key))
        self.sock.sendall(request.encode())
        response = b""
        while b"\r\n\r\n" not in response:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise RuntimeError("WebSocket handshake failed")
            response += chunk
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise RuntimeError("WebSocket handshake rejected: " + response.split(b"\r\n", 1)[0].decode(errors="replace"))
        self.lock = threading.Lock()
        self.closed = False

    def _send_frame(self, opcode, payload):
        mask = os.urandom(4)
        header = bytes([0x80 | opcode])
        n = len(payload)
        if n < 126:
            header += bytes([0x80 | n])
        elif n < 65536:
            header += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            header += bytes([0x80 | 127]) + struct.pack(">Q", n)
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
        with self.lock:
            self.sock.sendall(header + mask + masked)

    def send_text(self, text):
        self._send_frame(0x1, text.encode("utf-8"))

    def send_binary(self, data):
        self._send_frame(0x2, data)

    def _recv_exact(self, n):
        data = b""
        while len(data) < n:
            chunk = self.sock.recv(n - len(data))
            if not chunk:
                raise ConnectionError("closed")
            data += chunk
        return data

    def recv_frame(self):
        """Returns (opcode, payload); handles ping/pong; None on close."""
        while True:
            b1, b2 = self._recv_exact(2)
            opcode = b1 & 0x0F
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._recv_exact(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._recv_exact(8))[0]
            mask = self._recv_exact(4) if b2 & 0x80 else None
            payload = self._recv_exact(n)
            if mask:
                payload = bytes(b ^ mask[i % 4] for i, b in enumerate(payload))
            if opcode == 0x8:
                return None
            if opcode == 0x9:
                self._send_frame(0xA, payload)
                continue
            if opcode == 0xA:
                continue
            return opcode, payload

    def close(self):
        if not self.closed:
            self.closed = True
            try:
                self._send_frame(0x8, struct.pack(">H", 1000))
            except OSError:
                pass
            self.sock.close()


# ---------- the simulated line ----------

class Line:
    """Streams frames to the agent every 20 ms (speech when queued, silence otherwise) and collects Nola's frames."""

    def __init__(self, ws):
        self.ws = ws
        self.pending = []           # outbound speech frames
        self.incoming = []          # (timestamp, frame)
        self.running = True
        self.silence = bytes(FRAME_BYTES)
        self.sender = threading.Thread(target=self._send_loop, daemon=True)
        self.receiver = threading.Thread(target=self._recv_loop, daemon=True)
        self.sender.start()
        self.receiver.start()

    def _send_loop(self):
        next_tick = time.perf_counter()
        while self.running:
            frame = self.pending.pop(0) if self.pending else self.silence
            try:
                self.ws.send_binary(frame)
            except OSError:
                return
            next_tick += FRAME_MS / 1000
            delay = next_tick - time.perf_counter()
            if delay > 0:
                time.sleep(delay)

    def _recv_loop(self):
        try:
            while self.running:
                frame = self.ws.recv_frame()
                if frame is None:
                    break
                opcode, payload = frame
                if opcode == 0x2:
                    self.incoming.append((time.perf_counter(), payload))
        except (OSError, ConnectionError):
            pass
        self.running = False

    def say(self, pcm):
        frames = [pcm[i:i + FRAME_BYTES] for i in range(0, len(pcm), FRAME_BYTES)]
        if frames and len(frames[-1]) < FRAME_BYTES:
            frames[-1] = frames[-1] + bytes(FRAME_BYTES - len(frames[-1]))
        self.pending.extend(frames)
        while self.pending and self.running:
            time.sleep(0.02)
        return time.perf_counter()

    def wait_reply(self, since, first_timeout=20.0, gap=1.2, turn_done=None):
        """Waits for Nola's audio after `since`; `turn_done()` (optional) says when the agent's turn is complete, so a
        bridging phrase is not mistaken for the whole reply. Returns (pcm, first_audio_s, duration_s) or (b'', None, 0)."""
        start = time.perf_counter()
        first = None
        while self.running and time.perf_counter() - start < first_timeout:
            later = [f for f in self.incoming if f[0] >= since]
            if later:
                first = later[0][0]
                break
            time.sleep(0.05)
        if first is None:
            return b"", None, 0
        if turn_done is not None:
            while self.running and time.perf_counter() - start < 60 and not turn_done():
                time.sleep(0.3)
        while self.running:
            later = [f for f in self.incoming if f[0] >= since]
            if time.perf_counter() - later[-1][0] > gap:
                break
            time.sleep(0.05)
        later = [f for f in self.incoming if f[0] >= since]
        pcm = b"".join(f[1] for f in later)
        return pcm, first - since, later[-1][0] - first + FRAME_MS / 1000

    def stop(self):
        self.running = False
        self.ws.close()


def post_json(url, body, token=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            text = res.read().decode("utf-8")
            return res.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:200]


def admin_token():
    status, body = post_json(SYSTEM + "/api/auth/admin/login", {"username": "admin", "password": "admin123"})
    return body["token"] if status == 200 else None


def audit_events(token, call_id):
    req = urllib.request.Request(SYSTEM + "/api/admin/audit?conversationId=" + call_id,
                                 headers={"Authorization": "Bearer " + token, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as res:
        return json.loads(res.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser(description="NotTrilix phone call simulator")
    parser.add_argument("--from", dest="caller", default="+385 91 111 2222", help="caller id (default: Ana from the seed)")
    parser.add_argument("--say", nargs="*", default=["Koliko imam na walletu?", "Kupi mi HT bon od deset eura.", "Da, potvrđujem."],
                        help="what the caller says, utterance by utterance")
    args = parser.parse_args()

    call_id = "sim-%d" % int(time.time())
    print("=" * 100)
    print("POZIV %s s broja %s" % (call_id, args.caller))
    print("=" * 100)
    status, body = post_json("http://%s:%d/infobip/call-received" % AGENT,
                             {"callId": call_id, "type": "CALL_RECEIVED", "from": args.caller.replace(" ", "").lstrip("+"),
                              "to": "38515507725"})
    print("webhook call-received -> HTTP %s %s" % (status, body))
    status, body = post_json("http://%s:%d/infobip/event" % AGENT, {"callId": call_id, "type": "CALL_ESTABLISHED"})
    print("webhook event CALL_ESTABLISHED -> HTTP %s %s" % (status, body))

    print("pripremam govor pozivatelja (OpenAI TTS)...")
    utterances = [(text, synthesize_pcm16k(text)) for text in args.say]

    ws = WebSocketClient(AGENT[0], AGENT[1], "/infobip/media")
    line = Line(ws)
    t_meta = time.perf_counter()
    ws.send_text(json.dumps({"callId": call_id, "sampleRate": RATE, "packetizationTime": FRAME_MS}))
    all_audio = []
    admin = admin_token()
    turns_done = [0]

    def turn_recorded():
        if not admin:
            return True
        n = sum(1 for e in audit_events(admin, call_id) if e.get("eventType") == "CHAT_TURN")
        return n > turns_done[0]

    pcm, first, duration = line.wait_reply(t_meta, first_timeout=15.0)
    all_audio.append(pcm)
    print("NOLA pozdrav (prvi zvuk nakon %s, %.1f s govora): %s" % ("%.2f s" % first if first is not None else "-", duration, transcribe(pcm)))

    for text, speech in utterances:
        if not line.running:
            print("linija zatvorena (poziv prekinut)")
            break
        print("KUPAC: %s" % text)
        ended = line.say(speech)
        pcm, first, duration = line.wait_reply(ended, turn_done=turn_recorded)
        turns_done[0] += 1
        all_audio.append(pcm)
        if first is None:
            print("NOLA: (nema odgovora u 20 s)")
            continue
        print("NOLA (prvi zvuk %.2f s nakon kraja govora, %.1f s govora ukupno): %s" % (first, duration, transcribe(pcm)))

    time.sleep(0.5)
    line.stop()
    post_json("http://%s:%d/infobip/event" % AGENT, {"callId": call_id, "type": "CALL_FINISHED"})
    out = os.path.join(ROOT, "logs", "poziv-nola.wav")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "wb") as f:
        f.write(wav(b"".join(all_audio)))
    print("Nolin govor iz cijelog poziva: logs/poziv-nola.wav")

    # what the system recorded about the call
    if admin:
        time.sleep(1.0)
        events = audit_events(admin, call_id)
        print("audit trag poziva u sustavu (%d događaja): %s" % (len(events), ", ".join(
            "%s %s %s" % (e.get("eventType"), e.get("operation"), e.get("outcome")) for e in events)))


if __name__ == "__main__":
    sys.exit(main())
