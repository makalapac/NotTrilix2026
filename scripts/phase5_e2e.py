# -*- coding: utf-8 -*-
"""
Phase 5 trial against the running applications (run-phase-5.ps1): voice, privacy (GDPR), conversation analysis, self-learning.

  .\\run-phase-5.ps1 -Reset
  $env:PYTHONIOENCODING = "utf-8"
  python scripts\\phase5_e2e.py               # everything
  python scripts\\phase5_e2e.py voice         # voice only (needs openai.key in the project root: the test recording is made with it)
  python scripts\\phase5_e2e.py privacy | analysis | learning

Needs only Python 3. The script reads openai.key solely to turn text into speech (the customer's test recording);
the key is never printed. Outcomes are read by eye; the script checks nothing automatically.
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
HERE = os.path.dirname(os.path.abspath(__file__))
KEY_FILE = os.path.join(HERE, "..", "openai.key")


def http(method, path, body=None, token=None, raw=None, content_type=None):
    headers = {"Accept": "application/json"}
    data = None
    if raw is not None:
        data = raw
        headers["Content-Type"] = content_type
    elif body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=180) as res:
            text = res.read().decode("utf-8")
            return res.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text


def login(email, password):
    status, body = http("POST", "/api/auth/customer/login", {"email": email, "password": password})
    assert status == 200, (status, body)
    return body["token"]


def admin_login():
    status, body = http("POST", "/api/auth/admin/login", {"username": "admin", "password": "admin123"})
    assert status == 200, (status, body)
    return body["token"]


def title(text):
    print("\n" + "=" * 100 + "\n" + text + "\n" + "=" * 100)


def show_chat(label, body):
    print("%s: %s" % (label, body["reply"].replace("\n", " ")))
    for t in body["toolCalls"]:
        print("   [alat] %-24s %-22s %s (%d ms)" % (t["tool"], t["outcome"], t["input"], t["durationMs"]))
    if body["sources"]:
        print("   [izvori] " + "; ".join("%s › %s (%.2f)" % (s["source"], s["section"], s["score"]) for s in body["sources"]))
    print("   [%d ms, razgovor %s]" % (body["durationMs"], body["conversationId"]))


# ---- customer test recording: text -> speech (OpenAI TTS), key from openai.key, never printed ----

def synthesize(text):
    with open(KEY_FILE, encoding="utf-8") as f:
        key = f.read().strip()
    payload = json.dumps({"model": "gpt-4o-mini-tts", "voice": "echo", "input": text, "response_format": "mp3",
                          "instructions": "Govori hrvatski jezik, prirodno i jasno, kao kupac koji zove korisničku podršku."}).encode("utf-8")
    req = urllib.request.Request("https://api.openai.com/v1/audio/speech", data=payload, method="POST",
                                 headers={"Authorization": "Bearer " + key, "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as res:
        return res.read()


def multipart(fields, file_field, filename, content, mime):
    boundary = "----NotTrilixBoundary7MA4YWxk"
    parts = []
    for name, value in fields.items():
        parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"\r\n\r\n%s\r\n" % (boundary, name, value)).encode("utf-8"))
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n"
                  % (boundary, file_field, filename, mime)).encode("utf-8"))
    parts.append(content)
    parts.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


def voice_turn(token, conversation_id, audio, filename="speech.mp3", mime="audio/mpeg"):
    fields = {}
    if conversation_id:
        fields["conversationId"] = conversation_id
    body, ctype = multipart(fields, "audio", filename, audio, mime)
    return http("POST", "/api/agent/voice", token=token, raw=body, content_type=ctype)


def voice():
    title("GLAS: privola, pa dvije glasovne poruke (kupnja uz potvrdu)")
    marko = login("marko.maric@example.com", "marko123")
    status, body = http("GET", "/api/shop/privacy", token=marko)
    print("privatnost prije:", json.dumps(body, ensure_ascii=False)[:200])
    print("pripremam probnu snimku (OpenAI TTS: 'Kupi mi HT bon od deset eura.')...")
    audio1 = synthesize("Kupi mi HT bon od deset eura.")
    print("   snimka: %d B" % len(audio1))

    status, body = voice_turn(marko, None, audio1)
    print("glas bez privole -> HTTP %s %s" % (status, json.dumps(body, ensure_ascii=False)[:160]))

    status, body = http("POST", "/api/shop/privacy/consent", {"type": "VOICE"}, marko)
    print("privola -> HTTP %s %s" % (status, json.dumps(body, ensure_ascii=False)))

    status, body = voice_turn(marko, None, audio1)
    if status != 200:
        print("glas -> HTTP %s %s" % (status, body))
        return
    print("KUPAC (glas -> tekst, STT %d ms): %s" % (body["sttMs"], body["transcript"]))
    show_chat("NOLA (tekst -> govor %s, TTS %d ms, %d B mp3)" % (body["ttsProvider"], body["ttsMs"],
                                                                 len(base64.b64decode(body["audioBase64"]))), body["chat"])
    cid = body["chat"]["conversationId"]
    out = os.path.join(HERE, "..", "logs", "nola-odgovor.mp3")
    with open(out, "wb") as f:
        f.write(base64.b64decode(body["audioBase64"]))
    print("   govor spremljen u logs/nola-odgovor.mp3 (za slusanje)")

    audio2 = synthesize("Da, potvrđujem.")
    status, body = voice_turn(marko, cid, audio2)
    if status != 200:
        print("glas 2 -> HTTP %s %s" % (status, body))
        return
    print("KUPAC (glas -> tekst, STT %d ms): %s" % (body["sttMs"], body["transcript"]))
    show_chat("NOLA", body["chat"])
    return cid


def privacy():
    title("PRIVATNOST: pregled, izvoz, brisanje razgovora (Ivana)")
    ivana = login("ivana.ivic@example.com", "ivana123")
    status, chat = http("POST", "/api/agent/chat", {"message": "Što sve čuvate o meni i koliko dugo?"}, ivana)
    if status == 200:
        show_chat("NOLA", chat)
    status, body = http("GET", "/api/shop/privacy", token=ivana)
    print("pregled -> HTTP %s %s" % (status, json.dumps(body, ensure_ascii=False)))
    status, body = http("GET", "/api/shop/privacy/export", token=ivana)
    print("izvoz -> HTTP %s: kupac=%s, transakcija=%d, razgovora=%d (poruka u prvom: %d), analiza=%d, privola=%d" % (
        status, body["customer"]["email"], len(body["transactions"]), len(body["conversations"]),
        len(body["conversations"][0]["messages"]) if body["conversations"] else 0, len(body["analyses"]), len(body["consents"])))
    status, body = http("POST", "/api/shop/privacy/erase-conversations", token=ivana)
    print("brisanje -> HTTP %s %s" % (status, json.dumps(body, ensure_ascii=False)))
    status, body = http("GET", "/api/shop/privacy", token=ivana)
    print("pregled nakon -> spremljenih razgovora: %s" % body["storedConversations"])
    status, body = http("GET", "/api/shop/privacy/export", token=ivana)
    print("izvoz nakon -> razgovora=%d, poruka u prvom=%d" % (
        len(body["conversations"]), len(body["conversations"][0]["messages"]) if body["conversations"] else 0))


def analysis(conversation_id=None):
    title("ANALIZA RAZGOVORA (admin)")
    admin = admin_login()
    if conversation_id is None:
        ana = login("ana.anic@example.com", "ana123")
        status, chat = http("POST", "/api/agent/chat", {"message": "Moj Steam kod ne radi, kaže da je nevažeći."}, ana)
        show_chat("NOLA", chat)
        conversation_id = chat["conversationId"]
        status, chat = http("POST", "/api/agent/chat", {"conversationId": conversation_id,
                                                        "message": "Mogu li za tu kupnju dobiti R1 račun na tvrtku?"}, ana)
        show_chat("NOLA", chat)
    status, body = http("POST", "/api/admin/conversations/%s/analyze" % conversation_id, token=admin)
    print("analiza -> HTTP %s" % status)
    if status == 200:
        for k in ("category", "outcome", "sentiment", "qualityScore", "rulesFollowed", "summary", "knowledgeGaps",
                  "improvementNotes", "model", "durationMs", "promptTokens", "completionTokens"):
            print("   %-18s %s" % (k, body.get(k)))
    else:
        print(body)
    status, stats = http("GET", "/api/admin/conversations/stats", token=admin)
    print("statistika:", json.dumps(stats, ensure_ascii=False))
    return conversation_id


def learning():
    title("SAMOUCENJE: prijedlog iz razgovora -> admin odobri -> agent zna")
    admin = admin_login()
    status, suggestions = http("GET", "/api/admin/kb/suggestions?status=PROPOSED", token=admin)
    print("prijedloga ceka: %d" % len(suggestions))
    for s in suggestions:
        print("   #%d %s\n      nacrt: %s" % (s["id"], s["question"], s["answer"][:200]))
    if not suggestions:
        print("nema prijedloga - prvo pokreni 'analysis'")
        return
    s = suggestions[0]
    answer = ("R1 račun na tvrtku izdaje se naknadno: kupac nakon kupnje pošalje broj kupnje, naziv tvrtke i OIB na "
              "racuni@nottrilix.hr, a račun stiže e-mailom u roku od 2 radna dana. Iznos je isti kao na bonu (nema PDV-a na bonove).")
    status, body = http("POST", "/api/admin/kb/suggestions/%d/approve" % s["id"], {"question": s["question"], "answer": answer}, admin)
    print("odobreno -> HTTP %s status=%s" % (status, body.get("status") if isinstance(body, dict) else body))
    status, hits = http("GET", "/api/admin/kb/search?q=" + urllib.request.quote("račun na firmu R1"), token=admin)
    print("pretraga baze znanja 'račun na firmu R1' -> %s" % "; ".join("%s › %s (%.2f)" % (h["source"], h["section"], h["score"]) for h in hits))
    marko = login("marko.maric@example.com", "marko123")
    status, chat = http("POST", "/api/agent/chat", {"message": "Trebam račun na firmu za bon, kako to ide?"}, marko)
    show_chat("NOLA (novi razgovor, nakon ucenja)", chat)


if __name__ == "__main__":
    what = sys.argv[1:] or ["voice", "privacy", "analysis", "learning"]
    cid = None
    for name in what:
        if name == "voice":
            cid = voice()
        elif name == "privacy":
            privacy()
        elif name == "analysis":
            analysis()
        elif name == "learning":
            learning()
        else:
            print("nepoznato:", name)
