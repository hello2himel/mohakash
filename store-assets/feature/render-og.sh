#!/usr/bin/env bash
# Optional 1200×630 social / Open Graph cut. Same source system as render.sh.
set -euo pipefail
cd "$(dirname "$0")"
OUT="og-1200x630.png"
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
  --window-size=1200,630 \
  --virtual-time-budget=6000 \
  --run-all-compositor-stages-before-draw \
  --user-data-dir="$TMP/profile" \
  --screenshot="$TMP/og.png" \
  "file://$PWD/og-1200x630.html" 2>&1 | tail -n 2
magick "$TMP/og.png" -alpha off -colorspace sRGB -strip "$OUT"
echo
identify -format 'OUT  %m  %wx%h  %[channels]  %[colorspace]  depth=%z  %b\n' "$OUT"
python3 - "$OUT" <<'PY'
from PIL import Image
import sys
im = Image.open(sys.argv[1])
assert im.size == (1200, 630), f"bad size: {im.size}"
assert im.mode in ("RGB", "P"), f"bad mode: {im.mode}"
print(f"OK  {im.size[0]}x{im.size[1]}  mode={im.mode}  no alpha={im.mode != 'RGBA'}")
PY
