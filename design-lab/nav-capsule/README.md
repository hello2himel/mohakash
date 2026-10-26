# Navigation lab — the Launch Cradle

A design prototype for a new Mohakash bottom navigation: one floating capsule instead of
Feed · rocket · Archive as three evenly spaced slots. **Recommended, not implemented in this folder.** No production
files were modified by this work.

**The idea in one line:** a single 252 × 54 dp plate with a shallow cut in its top edge; the rocket sits
in that cut like a key, the selected destination is a well pressed into the plate, and the only accent
is the key's glyph.

## Read in this order

| File | What it is |
|---|---|
| [`proposal.md`](proposal.md) | The three directions, ASCII wireframes, rationale, the recommendation, numbers |
| [`mockup.html`](mockup.html) | The annotated visual mockup — 360 and 411 dp in context, anatomy, states, stress tests |
| [`compose-notes.md`](compose-notes.md) | Layout tree, Compose snippets, tokens, animation/state, icons, safe area, semantics |
| [`tradeoffs.md`](tradeoffs.md) | Narrow screens, large text, landscape, gesture/3-button insets, failure cases |
| [`screenshots/`](screenshots) | PNG exports at 2× (`mockup-360`, `mockup-411`, `anatomy`, `states`, `stress`, full page) |
| [`ATTRIBUTION.md`](ATTRIBUTION.md) | Image / font / icon licenses |

## The recommendation

- **Plate** 252 × 54 dp, r 27, `Surface` with a 1 dp `BorderStrong` hairline and a 10 dp shadow,
  floating 8 dp above the navigation inset.
- **Cradle cut** 58 w × 12 d, 42 dp floor, centred in the top edge.
- **Key** 44 dp disc, 8 dp proud, `SurfaceStrong` with a 1 dp ring and a 21 dp `Accent` rocket glyph;
  **48 × 48 dp target**. It never shows selection and never changes size with state.
- **Destinations** 84 × 48 dp cells, icon 22 / label 12 sp. Selection is a 72 × 40 dp well cut into the
  plate (`Background` + inset rim) that slides 148 dp in 220 ms; the selected label becomes Text +
  SemiBold. No accent fill, no ripple.
- **Footprint** 72 dp including the key's lift — the same as today's `BottomNavHeight`, so no picture is
  lost. Settings stays top-right; routes, back stack and Saved inside Archive are untouched.

## Viewing it

Open `mockup.html` in a browser. It is fully local (fonts and images are in `assets/`); no server is
needed. The page is 1440 px wide and is designed to be read at a desk or exported to PNG.

## Reproducing the screenshots

```bash
# Chromium is the Playwright build already on this machine.
node tools/shot.mjs mockup.html screenshots/mockup-full.png 1440 1200 1   # full page
node tools/shot.mjs mockup.html /tmp/full@2x.png 1440 1200 2              # then crop sections
node tools/rects.mjs mockup.html 1440 '#dev-feed' '#dev-archive' '#panel' # section coordinates
```

`tools/shot.mjs` uses the Chrome DevTools Protocol directly, with no npm dependencies, and captures
beyond the viewport. The hard-coded browser path is
`~/.cache/ms-playwright/chromium-1248/chrome-linux64/chrome`; edit it at the top of the file if the
Playwright build changes.

## Constraints honoured

- 48 dp minimum targets (84 × 48 tabs, 48 × 48 key).
- Nothing is drawn over the picture or the list; the dock lives in the Scaffold's bottom slot.
- Accessible names come from the existing `nav_feed` / `nav_archive` / `nav_random_description`
  strings; the icons are decorative.
- App palette only (`Surface`, `SurfaceStrong`, `BorderStrong`, `Background`, `Accent`, `Text`,
  `TextMuted`); no glass, neon or new surface effects.
- Restrained motion: finite, ≤ 300 ms, no infinite animation.

## Status

Design proposal. The production implementation is specified in `compose-notes.md`; it is not part of this
folder. See `proposal.md §6` for the next steps and `tradeoffs.md §16` for when to abandon the
direction.
