# Tradeoffs & failure cases — Launch Cradle

Everything here was checked in the prototype (`mockup.html`) at 1 CSS px = 1 dp unless marked
*untested*. Numbers are from the final geometry: plate 252 × 54, key 44, cradle 58 × 12, well 72 × 40,
float 8 dp, footprint 72 dp + the system inset.

---

## 1. What this direction gives up

| | Current bar | Launch Cradle |
|---|---|---|
| Width | full-bleed | 252 dp centred (70% of 360) |
| Contrast guarantee | opaque strip across the whole width | plate sits on the app background, not on the picture |
| Discoverability of random | very high — biggest, brightest thing | medium — a key with an accent glyph, no permanent animation |
| Implementation | stock Rows/Boxes | one custom `Shape` + a small `Layout` |
| State cue | icon + label colour and weight | a cut-in well + colour and weight |
| Mood | generic bottom bar with a big centre button | one object; quieter, more authored |

The width and discoverability losses are real. The first is deliberate (a dock, not a strip); the
second is the main risk to watch (see §10).

---

## 2. Narrow screens

| Width | Plate | Outside margins | Result |
|---|---|---|---|
| 411 dp | 252 | 79.5 dp | airy; the plate can feel isolated (accepted) |
| 360 dp | 252 | 54 dp | the intended proportion |
| 320 dp | 252 | 34 dp | still comfortable; verified in `screenshots/stress.png` |
| 300 dp | 252 | 24 dp | fits, but the plate now dominates the width |
| 280 dp | 252 | 14 dp | no longer a "floating" object; fall back |

- The plate does **not** shrink on narrow screens: 252 dp is the minimum that keeps 84 dp cells and
  the 58 dp cradle without crowding. Shrinking it would break the 48 dp targets.
- Below ~300 dp (rare: small watches are not supported, some split-screen panes are), fall back to
  **icon-only**: 56 dp cells, 188 dp plate, 44 dp well, icons keep their content descriptions and
  48 dp targets. The key stays 44/48.
- Split-screen is the realistic narrow case. Android can give the app a 320–360 dp pane on a phone;
  34 dp margins are still fine. At 240 dp (foldable cover pane) the icon-only layout is required.

---

## 3. Large font scale

The plate is content-sized, so it grows downward while the key stays pinned to its top edge.

| fontScale | Label | Plate | Container | Inset band | Notes |
|---|---|---|---|---|---|
| 1.0 | 12 / 16 sp | 252 × 54 | 72 | 96 (gesture) | reference |
| 1.3 | 15.6 / 20.8 | 252 × 58 | 76 | 100 | most common "Large" setting |
| 1.6 | 19.2 / 25.6 | 272 × ~62 | 80 | 104 | cells widen to 94 dp |
| 2.0 | 24 / 32 | 272 × ~68–70 | 86–88 | 110–112 | worst plausible case |

- **Labels are never dropped** at these scales. That is a deliberate accessibility choice: the two
  destinations stay named for low-vision users. The plate grows instead. On a 320 dp screen at 2.0×,
  272 leaves 24 dp outside margins — tight but valid.
- The band grows from 96 to ~112 dp on a 640–800 dp tall screen: 14–17% of the height. That is the
  cost of keeping the labels; it is the same trade the current 72 dp bar makes (it also grows when
  its labels wrap? it does not — today the bar clips or squeezes; this is better).
- The **key does not scale**. At 2.0× the 44 dp key in a 70 dp plate looks proportionally smaller and
  the cradle reads deeper. It still works (see the 2.0× card in `screenshots/states.png`), but this
  is the first thing to re-check on device. If it looks under-scaled, grow the key to 48 and the
  cradle to 62 at `fontScale ≥ 1.6` — one extra branch, no layout change.
- The well is `min(40, plateHeight - 14)` so it never touches the plate's edges as the plate grows.
- If `fontScale ≥ 2.0` **and** width < 300 dp, drop labels (as in §2); otherwise the plate exceeds the
  usable width. This combination is not expected on a supported phone.

---

## 4. Landscape

Landscape is where a bottom dock is genuinely expensive: a 360 dp-tall phone loses 27% of its height
to a 96 dp gesture-nav band. The prototype therefore uses a **compact build**:

| | Portrait | Landscape compact |
|---|---|---|
| Plate | 252 × 54 | 252 × 44 |
| Key | 44 (48 target) | 36 (48 target) |
| Labels | 12 sp | 11 sp |
| Cradle | 58 × 12 | none — the key is inset/flush |
| Container | 72 | 56 |
| Band (gesture) | 96 | 80 (22% of 360) |

- The compact plate drops the notch because the proportions no longer support it: a 12 dp cradle in a
  44 dp plate leaves too little material above the well. The key becomes an inset disc with the same
  ring and glyph. It is the same object family, simplified.
- Below ~320 dp of height, even 80 dp is too much. The next step would be a **side rail** (a 56 dp
  vertical capsule on the leading edge, key at the top or bottom). That is a different navigation
  pattern and out of scope for v1; the feed is a portrait-first experience and landscape is a
  secondary mode.
- Landscape insets: the navigation bar can be on the side, so only the bottom inset is added (which
  may be 0). Add the display-cutout horizontal padding; the plate is centred, so a camera hole at the
  edge is not normally an issue, but the cutout inset is cheap insurance.
- The reading overlay needs to move up by the dock band in landscape (the prototype does this); the
  feed's bottom padding is currently a fixed 14 dp inside its content box, so the box itself shrinks
  with the Scaffold inset — no feed code changes needed, but check the right action rail, which sits
  close to the dock at short heights.

---

## 5. Gesture navigation vs 3-button navigation

| Mode | Bottom inset | Plate bottom above screen | Band |
|---|---|---|---|
| Gesture | 24 dp | 8 + 24 = **32 dp** | 96 |
| 3-button | 48 dp | 8 + 48 = **56 dp** | 120 |

- The 8 dp float is measured from the *inset*, not the screen edge, so the plate never crowds the
  gesture pill or the buttons. On gesture nav the gap to the pill's top is ~20 dp.
- On 3-button nav the dock rises 24 dp higher; the feed loses another 24 dp of picture. This is the
  same behaviour as any bottom bar; the floating plate makes it more visible because there is no
  full-width strip, but it is not worse in area.
- The system bar uses a dark background (`#06070B` in the mock); the plate's hairline keeps it from
  merging with the bar.
- Edge-to-edge: the dock is **above** the inset, never behind it. Do not draw the plate under the
  gesture area to "float" more — the system bar's contrast is not ours to control.

---

## 6. IME / keyboard

The Archive screen has an inline search field (`BasicTextField`), not a sheet. With `adjustResize` and
edge-to-edge, opening the keyboard can leave the dock floating above the IME, eating 96 dp of the
already-short result list.

- **Recommendation:** hide the dock with a 160 ms fade when `WindowInsets.isImeVisible` and the search
  field has focus; the Scaffold's bottom padding collapses with it. The list resizes; no state is lost.
- Alternative: keep the dock above the keyboard (many apps do). It is functional — the destinations
  stay reachable — but it costs the list two rows. Test both; the hide is likely better.
- Do **not** put the dock inside the IME inset: it would jump up by ~300 dp and cover the list.
- Modal bottom sheets (search sheet, wallpaper sheet, filter sheet) draw their own scrim over the
  dock; no action needed. The random dialog covers it too.

---

## 7. Fullscreen feed

Tapping the picture hides the chrome and the dock (existing `feedFullscreen`). With the current bar,
the opaque strip's disappearance is a clean cut; with a floating dock, the content grows by 96 dp and
the reading overlay moves, which can read as a jump.

- The prototype's recommendation: accept it (the intent is "give the picture the screen"), or fade
  the dock out over 160 ms while keeping the inset for the duration, then collapse. The latter needs
  the bottom-bar slot to animate its height, which means measuring the plate and animating a spacer —
  do it only if the jump looks bad on device.
- No content is obscured in either mode: fullscreen removes the dock rather than overlaying it.

---

## 8. RTL

The app is English-only today, but the manifest has `supportsRtl="true"`.

- A `Row` lays the destinations out in the layout direction, so Feed/Archive swap automatically.
- `Modifier.offset` does **not** mirror. The well's target must be computed from
  `LocalLayoutDirection.current`: swap the FEED and ARCHIVE offsets, or use
  `Modifier.layout { placeable.placeRelative(x, 0) }`.
- The cradle is centred, so it is direction-agnostic; the rocket glyph is symmetric enough.
- *Untested*: no RTL screenshot in this lab. If RTL becomes a requirement, add a preview with
  `LocalLayoutDirection` and check the well.

---

## 9. Tablets and wide screens

At 600+ dp the 252 dp plate is the same size in a much wider window; it can look like a small floating
island in the middle of a large screen.

- **v1:** keep it centred. It reads as a deliberate, compact instrument; a wide dock would break the
  "one object" idea and the 252 dp proportion.
- **Alternative:** align the plate to the bottom-end on wide screens (thumb reach on a tablet) while
  keeping the key at the plate's centre. This is a one-line change but changes the composition's
  symmetry; test with the tablet layout before choosing.
- The feed is portrait-phone-first; there is no tablet-specific layout in the app today.

---

## 10. Discoverability of the random action

This is the main product risk. The old design made random unmissable (a 62 dp accent ball, larger
than anything else in the bar). The new key is 44 dp with a 21 dp accent glyph, and it is deliberately
quiet.

What still signals it:

1. **Position** — it owns the only interruption in the plate's silhouette, at the exact optical
   centre. The eye lands there even without colour.
2. **Accent** — it is the only accent object in the dock.
3. **Shape** — a raised circle among rounded rectangles; the cradle draws a line around it.
4. **Copy** — the content description is already "Send a rocket to a random picture"; the overlay's
   title is "Plotting a course…".

Mitigations if testing shows it is missed:

- A **one-time** nudge after onboarding: the key lifts 2 dp and settles twice over ~600 ms, then never
  again. Not an infinite pulse.
- Mention it once in onboarding (the current onboarding does not).
- Do not add a permanent glow or a bigger ball — that returns to the problem.

Success signal: a first-time user can find "random" without being told, and a returning user does not
notice the dock until they need it.

---

## 11. One-handed reach

- The plate is centred and 252 dp wide: on a 360 dp phone, the key's centre is 180 dp from the left,
  ~90 dp from the bottom (plus inset). That is comfortably inside a right thumb's arc and at the edge
  of a left thumb's arc; the destinations are ±74 dp from the centre.
- 3-button nav moves everything up 24 dp, which is fine.
- On a 411 dp phone the key is 205.5 dp from the left; still fine.
- The key is not a FAB in the corner, so it does not compete with the feed's right-edge action rail,
  which sits 114 dp above the screen bottom and extends upward. The two columns are separated by the
  full dock height and by 20+ dp of horizontal space.

---

## 12. Contrast & accessibility

Approximate ratios (computed from the palette, sRGB):

| Pair | Ratio | Use |
|---|---|---|
| `Text` #E8EAF0 on `SurfaceStrong` #1B2030 | ~13:1 | selected label / icon |
| `TextMuted` #A3ABBD on `Surface` #151922 | ~7.4:1 | idle label / icon |
| `Accent` #8FB0FF on `SurfaceStrong` | ~8:1 | rocket glyph |
| `Background` #0B0D12 on `Surface` (well) | ~1.15:1 | **not** a functional cue |
| `BorderStrong` on `Surface` | ~1.6:1 | decorative hairline |

- Selection is **not** conveyed by the well's contrast alone. It is conveyed by three cues: the
  well's depth (a shape cue), the label's colour (Text vs TextMuted) and the label's weight
  (SemiBold vs Regular). The well is a fourth, redundant cue. A user who cannot perceive the well
  still gets the colour and weight change; a user who cannot perceive colour still gets the well and
  the weight.
- The accent glyph is decorative in the sense that the key also has a content description; a
  colour-blind user still sees a raised disc with a rocket outline.
- Touch targets: 84 × 48 (tabs) and 48 × 48 (key). The visible key is 44 dp; the extra 2 dp on each
  side is part of the target and must not be clipped by a parent.
- The 1 dp hairline is decorative; it is not required to find the plate. The plate's fill is close to
  the background (1.15:1) so the hairline and shadow do most of the separation work. On a very bright
  image this would matter — but the plate sits on the app background, not on the image.
- Focus traversal: Feed → Archive → Rocket. The key is a separate action after the two tabs, which
  matches the visual order (tabs first, key raised at the top).
- No text is hidden at any scale (except the documented icon-only fallback under 300 dp).

---

## 13. Motion & haptics

- All animations are finite (max 300 ms). Nothing loops. Compose respects the system animator
  duration scale, so a reduced-motion device gets an instant well and an instant press.
- The halo ring is decorative; if animations are off it simply does not appear. The random overlay
  still shows loading.
- Haptics are optional: `TextHandleMove` on tab change and the key press. If the device has no
  haptics, nothing breaks. Do not use `LongPress`-strength feedback for a normal tap.
- No ripple: the well movement is the feedback. If user testing says the dock feels dead, add a
  bounded 12% accent ripple inside the cell — but try without first.

---

## 14. Implementation risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| The notch reads as a defect at 1× / mdpi | low–medium | render at 1× before shipping; the 1 dp border and the key's ring define it. The prototype's 360 dp PNG is the reference. |
| The key's 48 dp target is clipped by a parent | medium | keep the key a sibling in the two-child `Layout`; do not apply `clipToBounds` to the dock container |
| The plate's shadow is invisible on the dark background | low | the 1 dp `BorderStrong` hairline is the primary edge; the shadow is a soft halo, not a requirement |
| Text clipping at large font scales | medium | content-driven height + `heightIn(min = 54)`; test 2.0× before merge; the icon-only fallback exists |
| RTL well offset | low (English only) | compute from `LocalLayoutDirection`; add a preview |
| Overdraw / recomposition from the shadow and path | low | one `remember`ed shape; `animate*AsState` scoped to the well and the key; no infinite transition |
| The random overlay and the dock both run animations | low | the halo is 300 ms and one-shot; it ends before the overlay's dialog settles |
| The 3-button inset makes the band 120 dp | medium | accepted; the plate is unchanged, only the picture area shrinks |
| The IME pushes the dock above the keyboard | medium | hide on focus (§6) |
| Landscape compact build looks like a different dock | medium | it is the same family (plate, well, key) minus the cradle; document it as the landscape build, not a second design |

---

## 15. Open questions

1. **Key size at 2.0× text** — keep 44 or grow to 48 with a 62 dp cradle? Prototype on device.
2. **IME behaviour** — hide the dock on Archive search focus, or float above the keyboard? Test with
   a real keyboard and a long result list.
3. **Tablet alignment** — centred vs bottom-end.
4. **Discoverability** — is a one-time nudge needed? Test with 5 first-time users before adding it.
5. **The landscape build** — is the 44 dp plate enough, or should landscape simply keep the portrait
   dock and accept the 96 dp band? The compact build is more work; only keep it if landscape is used.
6. **Haptics** — do they add anything on a quiet dock, or are they noise? Try both.

---

## 16. When to abandon this direction

- If first-time users cannot find the random action after one pass **and** a one-time nudge does not
  fix it, the key is too quiet. The fallback is not the old ball; it is the same plate with the key
  promoted to a 56 dp disc (still not a filled accent surface).
- If the notch cannot be made to read at mdpi without a heavier border, remove the cut and use the
  flat plate with a proud key (the knob control from the exploration). That is still one object; it
  loses the signature detail, not the structure.
- If the custom shape causes real rendering problems on low-end devices (unlikely; it is one path),
  use a stock pill and a raised key with no notch. The rest of the system (well, key, dimensions,
  semantics) is unchanged.
