# -*- coding: utf-8 -*-
"""Console material for the Phase 4 video: the real two-process log of one confirmed purchase (docs/PHASE4.md, chapter 5)
and a live run of scripts/mcp_direct.py (MCP without the agent). Writes demo-video/faza4-konzola.json."""
import io
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..")

doc = io.open(os.path.join(ROOT, "docs", "PHASE4.md"), encoding="utf-8").read()
section = doc.split("## 5. Jedan turn u logu", 1)[1].split("## 6.", 1)[0]
block = re.search(r"```\n(.*?)```", section, re.S).group(1).strip("\n").split("\n")

lines = [{"html": "<span class='dim'>$</span> <span class='cmd'>.\\run-phase-4.ps1 -NoBuild</span>", "delay": 300},
         {"text": "==> Pokrecem NotTrilix 2026 (Ctrl+C za prekid)   sustav :8080 + agent :8090, oba u istom prozoru", "cls": "dim", "delay": 900},
         {"text": "", "delay": 600},
         {"text": "# kupac u shopu: 'Da, može.'  (potvrda kupnje predložene u prethodnom turnu)", "cls": "dim", "delay": 1400}]
for raw in block:
    cls = "sustav" if raw.startswith("[sustav]") else "agent" if raw.startswith("[agent ]") else "dim"
    html = raw.replace("&", "&amp;").replace("<", "&lt;")
    for key in ("CONFIRM PURCHASE", "confirmPendingAction", "PURCHASE OK", "WALLET DEBIT", "Authorization", "user=customer"):
        html = html.replace(key, "<span class='hl'>%s</span>" % key)
    lines.append({"html": html, "cls": cls, "delay": 1300 if "TURN" in raw else 750})
lines.append({"text": "", "delay": 1500})
lines.append({"text": "# isti alat izmjeren s obje strane mreže: 17 ms u sustavu, 26 ms gledano iz agenta", "cls": "dim", "delay": 2500})

# MCP without the agent: the real JSON-RPC exchange
lines.append({"text": "", "delay": 2500})
lines.append({"html": "<span class='dim'>$</span> <span class='cmd'>python scripts\\mcp_direct.py</span>", "delay": 600})
try:
    out = subprocess.run([sys.executable, os.path.join(ROOT, "scripts", "mcp_direct.py")], capture_output=True, text=True,
                         encoding="utf-8", timeout=180, env=dict(os.environ, PYTHONIOENCODING="utf-8")).stdout
except Exception as e:  # noqa: BLE001
    out = "(mcp_direct.py nije uspio: %s)" % e
for raw in out.splitlines()[:60]:
    cls = "err" if "401" in raw or "403" in raw or "REQUIRED" in raw or "NOTHING_TO_CONFIRM" in raw else "dim" if raw.startswith("=") else ""
    lines.append({"text": raw[:200], "cls": cls, "delay": 220 if len(raw) > 120 else 380})
lines.append({"text": "", "delay": 2500})

data = {"title": "NotTrilix 2026 · Faza 4 · sustav [sustav] je MCP server, agent [agent ] je zasebna aplikacija · jedna konzola", "lines": lines}
io.open(os.path.join(HERE, "faza4-konzola.json"), "w", encoding="utf-8").write(json.dumps(data, ensure_ascii=False, indent=1))
print("ok faza4-konzola.json: %d redaka" % len(lines))
