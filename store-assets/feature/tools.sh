#!/usr/bin/env bash
# Regenerate the two deterministic support textures from the real screenshot.
# Only run this if you change the hero frame or want to retune the atmosphere.
set -euo pipefail
cd "$(dirname "$0")"

python3 - <<'PY'
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

# 1) soft ambient spill — the same sky, defocused, for the left of the panel
src = Image.open("assets/app-feed.png").convert("RGB")
crop = src.crop((0, 120, 1080, 1320)).resize((1080, 1200), Image.LANCZOS)
amb = crop.filter(ImageFilter.GaussianBlur(70))
amb = ImageEnhance.Brightness(amb).enhance(0.55)
amb = ImageEnhance.Color(amb).enhance(0.9)
amb.save("assets/ambient.png", optimize=True)

# 2) fixed-seed film grain (no alpha; 160 px repeat)
rng = np.random.default_rng(20261027)
n = rng.normal(128, 26, (160, 160)).clip(0, 255).astype(np.uint8)
Image.fromarray(n, mode="L").convert("RGB").save("assets/grain.png", optimize=True)
print("assets/ambient.png and assets/grain.png regenerated")
PY
