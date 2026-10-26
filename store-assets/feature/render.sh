#!/usr/bin/env bash
# Render the 1024×500 Play / F-Droid feature graphic deterministically.
# Requires Chromium (or Google Chrome) and ImageMagick for the final check.
set -euo pipefail
cd "$(dirname "$0")"

OUT="featureGraphic-1024x500.png"
CHROME="${CHROME:-}"
if [ -z "$CHROME" ]; then
  for c in \
    "$HOME/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome" \
    "$(command -v chromium || true)" \
    "$(command -v chromium-browser || true)" \
    "$(command -v google-chrome || true)" \
    "/usr/bin/google-chrome"; do
    if [ -n "$c" ] && [ -x "$c" ]; then CHROME="$c"; break; fi
  done
fi
[ -n "$CHROME" ] || { echo "No Chromium/Chrome found. Set CHROME=/path/to/chrome" >&2; exit 1; }

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

"$CHROME" \
  --headless --no-sandbox --disable-gpu \
  --hide-scrollbars --force-device-scale-factor=1 \
  --force-color-profile=srgb \
  --window-size=1024,500 \
  --virtual-time-budget=6000 \
  --run-all-compositor-stages-before-draw \
  --user-data-dir="$TMP/profile" \
  --screenshot="$TMP/feature.png" \
  "file://$PWD/feature.html" 2>&1 | tail -n 2

# Keep the exact 1024×500 frame, 8-bit sRGB, no alpha channel.
magick "$TMP/feature.png" -alpha off -colorspace sRGB -strip "$OUT"

echo
identify -format 'OUT  %m  %wx%h  %[channels]  %[colorspace]  depth=%z  %b\n' "$OUT"
python3 - "$OUT" <<'PY'
from PIL import Image
import sys
im = Image.open(sys.argv[1])
assert im.size == (1024, 500), f"bad size: {im.size}"
assert im.mode in ("RGB", "P"), f"bad mode: {im.mode}"
print(f"OK  {im.size[0]}x{im.size[1]}  mode={im.mode}  no alpha={im.mode != 'RGBA'}")
PY
