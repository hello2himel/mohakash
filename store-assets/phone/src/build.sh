#!/usr/bin/env bash
# Rebuild the six phone screenshots.
#
#   bash src/build.sh          all six
#   bash src/build.sh 01 05    selected plates (regenerates those @2x only)
#
# Requires Node 18+, Playwright with Chromium, and Python 3 + Pillow.
# render.mjs finds Playwright in PLAYWRIGHT_DIR, the local project, or the
# npx cache; set PLAYWRIGHT_DIR if your install lives somewhere else.

set -euo pipefail
cd "$(dirname "$0")"

node render.mjs "$@"
python3 postprocess.py
