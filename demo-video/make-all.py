# -*- coding: utf-8 -*-
"""One video for the whole talk: a title card before every phase clip, clips concatenated (ffmpeg). Output: out/00-sve-faze.mp4."""
import os
import subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
FONT = "C\\:/Windows/Fonts/segoeuib.ttf"
CLIPS = [
    ("01-faza1-sustav.mp4", "Faza 1", "Postojeći sustav: shop, wallet, pravila u kodu"),
    ("02-faza2-znanje.mp4", "Faza 2", "Baza znanja za RAG (Spring AI + pgvector)"),
    ("03-faza3-agent.mp4", "Faza 3", "Agent Nola: alati, potvrda na serveru, audit"),
    ("04-faza4-mcp.mp4", "Faza 4", "Mrežni MCP: sustav kao MCP server, agent kao klijent"),
    ("05a-faza5-glas.mp4", "Faza 5", "Glas: isti agent, novi kanal"),
    ("05b-faza5-analiza-gdpr.mp4", "Faza 5", "Razgovor kao zapis: analiza, samoučenje, GDPR"),
]


def run(args):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error"] + args, check=True)


def title_card(path, title, subtitle):
    text = "drawtext=fontfile=%s:text='%s':fontcolor=white:fontsize=72:x=(w-text_w)/2:y=(h-text_h)/2-50," \
           "drawtext=fontfile=%s:text='%s':fontcolor=0xB9C6FF:fontsize=34:x=(w-text_w)/2:y=(h-text_h)/2+50" % (
               FONT, title.replace("'", "\\'"), FONT, subtitle.replace("'", "\\'").replace(":", "\\:"))
    run(["-f", "lavfi", "-i", "color=c=0x0f172a:s=1280x720:d=3:r=25", "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo",
         "-vf", text, "-t", "3", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", path])


def with_audio(src, dst):
    """Every clip gets an audio track (silent if it has none) so the concat filter accepts them all."""
    probe = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries", "stream=codec_name", "-of", "csv=p=0", src],
                           capture_output=True, text=True).stdout.strip()
    if probe:
        run(["-i", src, "-c:v", "copy", "-c:a", "aac", "-ar", "48000", "-ac", "2", dst])
    else:
        run(["-i", src, "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo", "-c:v", "copy", "-c:a", "aac", "-shortest", dst])


parts = []
for i, (clip, title, subtitle) in enumerate(CLIPS):
    src = os.path.join(OUT, clip)
    if not os.path.exists(src):
        print("preskacem (nema):", clip)
        continue
    card = os.path.join(OUT, "raw", "card-%d.mp4" % i)
    norm = os.path.join(OUT, "raw", "norm-%d.mp4" % i)
    title_card(card, title, subtitle)
    with_audio(src, norm)
    parts += [card, norm]

inputs = []
for p in parts:
    inputs += ["-i", p]
filt = "".join("[%d:v][%d:a]" % (i, i) for i in range(len(parts))) + "concat=n=%d:v=1:a=1[v][a]" % len(parts)
final = os.path.join(OUT, "00-sve-faze.mp4")
run(inputs + ["-filter_complex", filt, "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "medium", "-crf", "20",
              "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", final])
dur = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", final], capture_output=True, text=True).stdout.strip()
print("ok", final, "%.0f s" % float(dur), "%d KB" % (os.path.getsize(final) // 1024))
