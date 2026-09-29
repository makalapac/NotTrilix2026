# -*- coding: utf-8 -*-
"""
MCP bez agenta (Faza 4): izravni pozivi MCP servera sustava. MCP preko HTTP-a bez sesije je obican
POST /mcp s JSON-RPC porukom; tko zove govori zaglavlje Authorization, a razgovor i turn polje _meta.

  .\\run-phase-4.ps1 -Reset                  # svjeza baza (pozivi ocekuju seed podatke)
  $env:PYTHONIOENCODING = "utf-8"
  python scripts\\mcp_direct.py              # popis alata + pozivi
  python scripts\\mcp_direct.py list         # samo popis alata
  python scripts\\mcp_direct.py calls        # samo pozivi

Pokazuje granice sustava neovisno o agentu i modelu: bez tokena nema pristupa, s kljucem agenta vidi se samo
popis alata, kupac dolazi iz tokena, radnja se izvrsava tek u turnu nakon prijedloga, tudja radnja se ne moze
potvrditi. Treba samo Python 3. Pozivi mijenjaju podatke (Marko kupi HT bon 10 EUR).
"""
import json
import sys
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
AGENT_KEY = "nottrilix-demo-agent-key"  # demo vrijednost iz application.yml (nottrilix.mcp.agent-key)
_id = 0


def http(method, path, body=None, token=None):
    headers = {"Accept": "application/json, text/event-stream"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            text = res.read().decode("utf-8")
            return res.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        text = e.read().decode("utf-8")
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text


def rpc(method, params=None, token=None):
    global _id
    _id += 1
    body = {"jsonrpc": "2.0", "id": _id, "method": method}
    if params is not None:
        body["params"] = params
    return http("POST", "/mcp", body, token)


def login(email, password):
    status, body = http("POST", "/api/auth/customer/login", {"email": email, "password": password})
    assert status == 200, (status, body)
    return body["token"]


def call(tool, args=None, token=None, meta=None):
    params = {"name": tool, "arguments": args or {}}
    if meta is not None:
        params["_meta"] = meta
    status, body = rpc("tools/call", params, token)
    turn = "" if not meta else " turn=%s" % meta.get("turn")
    label = "%s %s%s" % (tool, json.dumps(args, ensure_ascii=False) if args else "", turn)
    if status != 200 or "result" not in body:
        print("  %-44s -> HTTP %s %s" % (label, status, json.dumps(body, ensure_ascii=False)[:200]))
        return None
    text = body["result"]["content"][0]["text"] if body["result"].get("content") else ""
    try:
        result = json.loads(text)
    except ValueError:
        print("  %-44s -> isError=%s %s" % (label, body["result"].get("isError"), text[:200]))
        return None
    outcome = "OK" if result.get("ok") else result.get("status")
    detail = result.get("message") or json.dumps(result.get("data"), ensure_ascii=False)
    print("  %-44s -> %-22s %s" % (label, outcome, (detail or "")[:150]))
    return result


def list_tools():
    status, body = rpc("tools/list")
    print("tools/list bez tokena:        HTTP %s %s" % (status, json.dumps(body, ensure_ascii=False)))
    status, body = rpc("tools/list", token=AGENT_KEY)
    tools = body["result"]["tools"]
    print("tools/list s kljucem agenta:  HTTP %s, %d alata" % (status, len(tools)))
    for t in tools:
        a = t.get("annotations", {})
        kind = "cita" if a.get("readOnlyHint") else ("IZVRSAVA" if a.get("destructiveHint") else "mijenja/predlaze")
        print("  %-24s %-17s parametri=%s" % (t["name"], kind, list(t["inputSchema"].get("properties", {}))))


def calls():
    conv = {"conversationId": "mcp-direct-1", "channel": "CHAT"}
    print("\nKljuc agenta bez kupca - alat ne radi:")
    call("getMyWallet", token=AGENT_KEY)

    marko = login("marko.maric@example.com", "marko123")
    ana = login("ana.anic@example.com", "ana123")
    print("\nMarko (token kupca) - kupac dolazi iz tokena:")
    call("whoAmI", token=marko)
    call("getMyWallet", token=marko)
    print("\nPravilo odbija, bez konteksta razgovora nema radnje, s kontekstom radnja se samo predlaze:")
    call("purchaseVoucher", {"productId": 9}, marko, dict(conv, turn=1))
    call("purchaseVoucher", {"productId": 5}, marko)
    call("purchaseVoucher", {"productId": 5}, marko, dict(conv, turn=1))
    call("getPendingAction", token=marko, meta=dict(conv, turn=1))
    print("\nPotvrda u istom turnu ne prolazi:")
    call("confirmPendingAction", token=marko, meta=dict(conv, turn=1))
    print("\nAna (drugi kupac, isti conversationId) ne moze potvrditi Markovu radnju ni vratiti njegovu kupnju:")
    call("confirmPendingAction", token=ana, meta=dict(conv, turn=2))
    call("refundPurchase", {"transactionId": 3}, ana, dict(conv, turn=2))
    print("\nMarko u iducem turnu - radnja se izvrsava, jednom:")
    call("confirmPendingAction", token=marko, meta=dict(conv, turn=2))
    call("confirmPendingAction", token=marko, meta=dict(conv, turn=3))
    call("getMyWallet", token=marko)
    print("\nBaza znanja kroz MCP:")
    call("searchKnowledgeBase", {"query": "koliki je rok za povrat novca"}, marko, dict(conv, turn=3))


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("list", "all"):
        list_tools()
    if what in ("calls", "all"):
        calls()
