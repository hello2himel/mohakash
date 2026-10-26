# The Launch Cradle

**Bottom navigation proposal for Mohakash**
Design lab · 27 October 2026 · status: **recommended, not implemented**

> One floating capsule instead of three evenly spaced slots. The plate is cut once — a 58 × 12 dp
> cradle in its top edge — and the rocket sits in that cut like a key. Selection is a well pressed
> into the plate, not a bright fill. The dock keeps the 72 dp footprint of the bar it replaces.

Files: [`mockup.html`](mockup.html) · [`screenshots/`](screenshots) · [`compose-notes.md`](compose-notes.md) ·
[`tradeoffs.md`](tradeoffs.md) · [`ATTRIBUTION.md`](ATTRIBUTION.md)

---

## 1. The problem with the bar we have

The current bar is a full-width, opaque strip with two weighted slots (Feed, Archive) and a
random-photo rocket in the middle: an 80 dp ring floating 16 dp above the strip around a 62 dp
**accent-filled ball**. It is competent, and it has three problems:

1. **It is still three slots.** The oversized centre button changes the rhythm but not the idea:
   three things in a row, evenly weighted. The user's complaint — "three evenly spaced slots" — is
   structural, not cosmetic.
2. **The accent is spent on a surface.** The ball is the largest accent area on every screen, so the
   random action outranks the actual primary action (Set wallpaper) and the reading itself. Accent
   should be a signal, not a place.
3. **The strip cuts the picture.** A hard horizontal edge and a lighter SurfaceRaised panel sit under
   a full-bleed photograph. The feed's gradient already resolves to the app background; the bar adds
   a second, competing edge.

**Brief:** a compact floating dock/capsule, one deliberate object rather than three slots; random
special but quiet; Feed, Archive and Settings kept; 48 dp targets; nothing obscured.

---

## 2. Design principles

| # | Principle | Consequence in the design |
|---|---|---|
| 1 | **One object** | A single 252 × 54 dp plate. The silhouette — pill with one bite — is the identity. |
| 2 | **A key, not a tab** | The rocket never shows selection, never changes size with state, and is mounted on the object rather than placed in the row. |
| 3 | **Material carries state** | Selected = a 72 × 40 well cut into the plate; action = a 44 dp key raised out of it. No bright fills, no glow. |
| 4 | **Accent is a signal** | Exactly one accent glyph in the dock: the rocket. The selected label becomes Text + SemiBold instead of accent. |
| 5 | **Same footprint** | 72 dp total, including the key's lift. The picture loses nothing compared with today. |

---

## 3. Three directions

### A · Launch Cradle — *recommended*

```
                         ╭─────╮                 ◉  launch key, 44 dp
                  ╭──────╯     ╰──────╮             floats 8 dp above the plate,
            ╭─────╯                   ╰─────╮       accent glyph only
            │   ▣ Feed      ▣ Archive        │
            ╰─────────────────────────────────╯   plate 252 × 54, r 27
              ▲                  ▲                 Surface + 1 dp hairline + shadow
           84 × 48            84 × 48

  key:  44 dp disc, SurfaceStrong, 1 dp ring, glyph 21 dp, target 48 × 48
  cut:  58 dp opening · 12 dp deep · 42 dp floor · 8 dp shoulders
  well: 72 × 40, Background fill + inset rim, slides 148 dp between cells
```

One plate with one deliberate interruption. The cut is functional: it lets the key sit proud of the
plate without a floating ring, and it makes the plate's silhouette recognisable at a glance. The
key is pinned to the top edge, so the plate can grow with the font scale without moving it.

- **Reads as:** a machined instrument — a plate with an inset well and a raised key.
- **Random feels special because:** it owns the only break in the silhouette and the only accent.
- **Risk:** a custom `Shape` (small, testable) instead of stock Material components.

### B · Split Dock — *rejected*

```
       ╭──────────╮      ╭───╮      ╭──────────╮
       │ ▣ Feed   │      │ ◉ │      │ ▣ Archive│
       ╰──────────╯      ╰─┬─╯      ╰──────────╯
                           │  1 dp rail, 16 dp
       two 100 × 54 islands · 64 dp bay · band 76 dp
```

Two islands with the key floating in the bay between them, a hairline rail joining the key to the
baseline. It separates "browse" from "library" strongly and the launch bay is a genuinely nice place
for the rocket.

- **Why not:** it costs 4 dp of footprint and reads as **three objects again** — exactly the
  complaint that started this round. The rail is doing the joining work, which means the composition
  isn't doing it on its own. On a 360 dp screen the bay is wide enough that the key looks becalmed.

### C · Launch Key — *rejected*

```
       ╭──────────────────────┬─────╮
       │  ▣ Feed     ▣ Archive│  ◉  │
       ╰──────────────────────┴─────╯
        plate 260 × 54 · sealed bay 57 dp · 1 dp hairline · key 36 dp inside
```

An asymmetric plate: two quieter destinations on the left, a sealed launch bay on the right behind a
hairline. The best one-handed reach for the action, and the most "instrument panel" of the three.

- **Why not:** it demotes the rocket from the midpoint of the object to a corner control, and the
  bay sits exactly where the feed's right-edge action rail (favorite / download / share) already
  lives. It also weakens the "two equal destinations" story: the dock reads as one tab and one
  button, not as two destinations and an action.

### Comparison

| | **A · Cradle** | B · Split | C · Key |
|---|---|---|---|
| Objects read at a glance | **1 plate + 1 key** | 2 islands + 1 key | 1 plate with a bay |
| Footprint | **72 dp** (same) | 76 dp | 72 dp |
| Key position | **midpoint, raised** | midpoint, floating | right end, inset |
| Accent surfaces | **0** (glyph only) | 0 | 0 |
| Selection cue | **cut-in well + weight** | cut-in well | well / hairline |
| Main risk | custom Shape | three-object read | competes with feed rail |
| Verdict | **recommended** | no | no |

---

## 4. Recommendation

**Build A.** It is the only direction that changes the *structure* (one object) rather than the
*styling* of three slots, and it keeps the rocket at the optical centre where the brief wants it.

### 4.1 Why it works

**One object.** A plate with a single bite is a shape you can draw from memory. Every element is
either cut into it (the well, the cradle) or mounted on it (the key) — nothing floats beside it. At
arm's length, on a 360 dp screen, it reads as one thing.

**Quiet, but not plain.** The cut is a real, functional detail: it is how the key can be 8 dp proud
without a second floating ring. The key is not bigger than the plate; it is *of* the plate. That is
the whole trick — special through placement, not size.

**State without brightness.** The selected destination is a seat cut into the plate: Background fill,
a 1 px inset rim, and the label switching to Text + SemiBold. The plate itself never changes size,
so the dock is a stable landmark. Because the well is dark, the accent stays free for the key.

**The accent is a signal.** One glyph, 21 dp. The rocket is the only periwinkle object in the dock.
When a wallpaper CTA appears on the feed, that CTA is still the brightest accent on the screen — the
navigation has stopped competing with the content.

**Nothing moves closer to the picture.** Total footprint is 72 dp: 10 dp of lift space for the key +
54 dp plate + 8 dp float, plus the navigation inset. It is exactly today's `BottomNavHeight`. The
feed's own gradient already resolves to `Background`, so the capsule's surroundings are the same
colour as the end of the gradient — no new edge.

### 4.2 The object, in numbers

| Element | Value | Notes |
|---|---|---|
| Plate | **252 × 54 dp**, r 27 | Surface `#151922`, 1 dp BorderStrong, 10 dp shadow |
| Cradle cut | **58 w × 12 d** | 42 dp floor, 8 dp shoulders, centred |
| Launch key | **44 dp**, 8 dp proud | SurfaceStrong `#1B2030`, 1 dp ring `#3A4257`, glyph 21 |
| Key target | **48 × 48 dp** | centred on the disc; extends 2 dp outside the plate |
| Destination cell | **84 × 48 dp** | icon 22, 3 dp gap, label 12 sp / 16 sp |
| Selection well | **72 × 40 dp**, r 20 | Background + inset rim; travels 148 dp |
| Plate padding | 10 dp | left and right of both cells |
| Float | 8 dp | plate bottom to the navigation-bar inset |
| Bottom slot | **72 + inset** | inset 24 gesture / 48 three-button |
| Side margins | 54 / 79.5 / 34 dp | at 360 / 411 / 320 |
| Type | Inter 12/16 | SemiBold when selected; TextMuted otherwise |
| Accent | `#8FB0FF` | key glyph only |

**On the shoulders:** the cradle's opening is 58 dp but its floor is 42 dp. Those 8 dp shoulders
are what make the key look *seated* rather than dropped into a slot — a plain 58 dp rounded-rectangle
cut (5 dp corners) is easier to build but leaves the key ringed by background at the top edge. Pick
deliberately; the mockup uses the 42 dp floor.

Full-size growth: the plate is `heightIn(min = 54.dp)` and its contents decide the height, so at
1.3× text it is ~58 dp, at 2.0× ~68 dp; cells widen to 94 dp at 1.6× and above (plate 272 dp). The
key stays pinned to the top edge and the cradle stays 58 × 12. See `tradeoffs.md` for the exact
fallbacks.

### 4.3 Motion, in one paragraph

The well slides 148 dp in 220 ms (`FastOutSlowIn`); labels crossfade in 120 ms. The key scales to 0.96
in 90 ms with a light haptic on press, springs back in 220 ms. Tapping the key runs a single 300 ms
accent ring that opens from 44 to 58 dp and fades while the random overlay loads — then the overlay
carries the motion. No infinite animation, no ripple, no bounce, no size change on selection.

### 4.4 What it preserves

- **Feed · random · Archive** and their routes (`today`, `archive`); the Archive screen keeps its
  Archive/Saved segmented control.
- **Settings in the top-right** of the feed and the archive hub — untouched.
- **Back stack behaviour, deep links, state restoration** — untouched (only the bottom-bar composable
  and its tokens change).
- **Fullscreen feed** — the dock disappears with the rest of the chrome, exactly as today.
- **48 dp targets** — 84 × 48 per destination, 48 × 48 for the key.
- **Accessible labels** — the existing `nav_feed` / `nav_archive` / `nav_random_description` strings.

### 4.5 What deliberately did *not* happen

No glassmorphism, no periwinkle glow, no FAB, no five tabs, no labels hidden to look clean, no
infinitely pulsing rocket, no size-changing bar on selection. The point is a quieter object, not a
new gimmick.

---

## 5. Evidence & checks

- **In context:** [`screenshots/mockup-360.png`](screenshots/mockup-360.png) (Feed), 
  [`screenshots/mockup-411.png`](screenshots/mockup-411.png) (Archive), both at 2×.
- **Anatomy and tap targets:** [`screenshots/anatomy.png`](screenshots/anatomy.png).
- **States, type scaling, motion:** [`screenshots/states.png`](screenshots/states.png).
- **Stress tests (320 dp, 3-button nav, landscape):** [`screenshots/stress.png`](screenshots/stress.png).
- **The whole page:** [`screenshots/mockup-full.png`](screenshots/mockup-full.png) and the live
  [`mockup.html`](mockup.html).

Checks done in the prototype: 360 / 411 / 320 dp; 1.0× / 1.3× / 2.0× text; gesture and 3-button
insets; landscape 640 × 360; light and dark image crops; active state on both destinations; pressed
and launching states. Not yet: real device rendering, TalkBack pass, RTL, tablets.

---

## 6. Next steps

1. Build the composable behind a flag (`useLaunchCradle`), sharing `BottomDestination` and routes.
2. Test on a 320 dp device at 2.0× text and with 3-button navigation before deleting the old bar.
3. Decide the IME behaviour on the Archive search (hide vs. float above the keyboard — see
   `tradeoffs.md §6`).
4. If the rocket proves hard to discover, add a one-time, 8 dp, 600 ms nudge after onboarding —
   never a permanent animation.
5. Only then delete `MohakashBottomBar.kt`.
