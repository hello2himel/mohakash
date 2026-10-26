#!/usr/bin/env python3
"""Downsample the 2x renders to exact 1080x1920 RGB PNGs and validate them.

Also writes two review artefacts into src/out/:
  contact-sheet.png    six plates on one page
  thumbnail-check.png  the same six at store-thumbnail scale
"""

from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "out"
FINAL = ROOT.parent
W, H = 1080, 1920

FILES = [
    ("01", "01-new-wallpaper-every-day.png"),
    ("02", "02-set-it-once.png"),
    ("03", "03-random-wallpapers.png"),
    ("04", "04-save-favourites.png"),
    ("05", "05-no-ads-no-account.png"),
    ("06", "06-full-resolution-explanation.png"),
]

report = []
finals = []
for plate, name in FILES:
    src = OUT / f"{plate}@2x.png"
    if not src.exists():
        raise SystemExit(f"missing render: {src}")
    im = Image.open(src).convert("RGB")
    if im.size != (W * 2, H * 2):
        raise SystemExit(f"{src.name}: expected 2160x3840, got {im.size}")
    im = im.resize((W, H), Image.LANCZOS)
    dest = FINAL / name
    im.save(dest, "PNG", optimize=True)
    # re-open the written file and verify
    check = Image.open(dest)
    if check.size != (W, H) or check.mode != "RGB":
        raise SystemExit(f"{dest.name}: {check.size} {check.mode}")
    finals.append((name, check))
    report.append({"file": name, "size": check.size, "mode": check.mode, "bytes": dest.stat().st_size})
    print(f"{name:44s} {check.size[0]}x{check.size[1]}  {dest.stat().st_size/1024:.0f} KB")

# contact sheet — 3 x 2, 32px gutters on Deep Space
GUT = 32
LABEL = 34
cell_w, cell_h = 360, 640
sheet = Image.new("RGB", (3 * cell_w + 4 * GUT, 2 * (cell_h + LABEL) + 3 * GUT), "#0B0D12")
draw = ImageDraw.Draw(sheet)
for i, (name, im) in enumerate(finals):
    col, row = i % 3, i // 3
    x = GUT + col * (cell_w + GUT)
    y = GUT + row * (cell_h + LABEL + GUT)
    sheet.paste(im.resize((cell_w, cell_h), Image.LANCZOS), (x, y))
    draw.text((x, y + cell_h + 11), name, fill="#8A93B2")
sheet.save(OUT / "contact-sheet.png", "PNG")

# thumbnail check — what the listing looks like at ~272px wide
TH_W, TH_H, gap = 272, 484, 18
thumbs = Image.new("RGB", (3 * TH_W + 4 * gap, 2 * TH_H + 3 * gap), "#0B0D12")
for i, (name, im) in enumerate(finals):
    col, row = i % 3, i // 3
    thumbs.paste(
        im.resize((TH_W, TH_H), Image.LANCZOS),
        (gap + col * (TH_W + gap), gap + row * (TH_H + gap)),
    )
thumbs.save(OUT / "thumbnail-check.png", "PNG")

(OUT / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
print("\nall six are 1080x1920 RGB PNG")
