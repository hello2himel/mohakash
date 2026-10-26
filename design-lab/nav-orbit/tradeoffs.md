# Tradeoffs and failure cases — Orbit Line

Companion to [proposal.md](proposal.md) and [compose-notes.md](compose-notes.md).
All numbers are dp at 1× unless a font scale is stated. The mockup was rendered at 360 × 800, 411 × 891, 320 dp, 1.6×, RTL and 48 dp inset.

---

## 0. What was actually tested

| Case | How it appears in the mockup | Result |
| --- | --- | --- |
| 360 × 800, feed selected | full phone, real APOD frame | 80 dp content, 24 dp gesture inset, no overlap |
| 411 × 891, feed selected | full phone | sides grow to 153.5 dp; node stays 104/48 |
| 320 dp, 1.6×, long label | state F | bar grows; **label ellipsises**; full name remains in semantics |
| 320 dp, 1× | same frame, readable | sides 108 dp; all labels fit |
| 1.6× at 360 | state C | content 89.6 dp; nothing clipped; node and track anchored |
| RTL | state D | Feed at the right, Archive at the left, random centred |
| Three-button inset, 48 dp | state E | bar surface behind the inset; content row untouched |
| Gesture inset, 24 dp | every phone | gesture pill sits in the inset, not under a label |
| Picking | state B | 90° arc on the ring; base ring dims; no other motion |
| Archive selected | state A | underline and label move; track unchanged |

Not rendered, only reasoned about: landscape, tablets/foldables, font scale 3.0, OEM gesture inset = 0, and right-to-left long-copy combinations. Those are called out below.

---

## 1. Narrow screens

| Width | Side cell | Centre cell | What happens |
| --- | --- | --- | --- |
| 411 | 153.5 | 104 | generous; labels centred under icons |
| 360 | 128 | 104 | the reference width |
| 320 | 108 | 104 | the practical minimum; labels still fit at 1× |
| 280 | 88 | 104 | not a target; labels ellipsise; centre is 37% of the width |
| 260 | 78 | 104 | centre dominates; do not support this as a phone layout |

At 320 the 104 dp centre is fixed, so the side cells lose 20 dp each relative to 360. The labels are still centred under the icons; the 20 dp underline is 6% of the cell. The 1 dp track still enters at the screen edges and crosses both icons.

**Failure mode.** A narrow split-screen window plus a `1fr` track without `minmax(0, 1fr)` lets a long label force the grid wider than the screen; the Archive label then collides with Random. This is exactly what the first HTML draft did. In Compose, weight cells are measured with the remaining width, so the failure is less likely, but the Text must still be `maxLines = 1` + `Ellipsis` + a 4 dp horizontal padding. Do not let a label's intrinsic width influence the cell width.

**Mitigation.** Keep the centre at 104 and the sides weighted. Do not shrink the node to buy label width. If a 280 dp window must be supported, reduce the side horizontal padding to 2 dp and accept ellipsis; do not reduce below 12 sp.

---

## 2. Large font scale

The content height is linear in the label line height:

`content = 64 + 16 × fontScale` &nbsp;&nbsp; (both columns; see compose-notes §1)

| Font scale | Label | Content | Total with 1 dp rule + 24 dp gesture inset |
| --- | --- | --- | --- |
| 1.0 | 12 / 16 | 80 | 105 |
| 1.3 | 15.6 / 20.8 | 84.8 | 109.8 |
| 1.6 | 19.2 / 25.6 | 89.6 | 114.6 |
| 2.0 | 24 / 32 | 96 | 121 |
| 3.0 | 36 / 48 | 112 | 137 |

The bar grows; the Scaffold measures it; the feed content is padded by the new height. The node, ring and track do **not** scale (they are `dp`), so at 2× the ring looks proportionally smaller next to the labels. That is acceptable; the 48 dp node is still larger than the 24 dp icons and the ring is still the anchor. If the node looks too small at 2×, do not scale it with `sp`; consider 52 dp at a large font scale, but that changes the label gap (52 + 2 = 54, label at 58) and must be tested.

**Failure mode.** A fixed `height(80.dp)` would clip 2× labels. A `maxLines = 1` with no ellipsis overflows into the neighbouring cell. Capping the font scale with a `Density` override is an accessibility regression and is not recommended.

**Mitigation.** `heightIn(min = 80.dp)`, no fixed height, `TextOverflow.Ellipsis`, and a full accessible name. At 3.0× the side labels may ellipsise on 320; that is a visual truncation, not a loss of the name. The selected underline moves with the label; the track stays at the top.

**Underline at large scale.** Because the underline is laid out after the label, its gap stays 2 dp and it moves down with the text. It never ends up in the system inset; the bottom spacer is separate. The bar is taller by exactly the label growth.

---

## 3. Long labels and localization

The tested stress string is **Bibliothèque** (12 characters). At 12 sp on 320 dp it fits a 108 dp side cell (roughly 70–75 dp of glyphs). At 19.2 sp (1.6×) it needs roughly 110–120 dp and ellipsises to “Bibliothè…”. The mockup's state F shows this exact case.

| String shape | 360 dp @1× | 320 dp @1× | 320 dp @1.6× |
| --- | --- | --- | --- |
| Feed / Random / Archive | fit | fit | fit (Random/Feed), Archive may ellipsise |
| Archiv / Zufall | fit | fit | likely fit |
| Bibliothèque | fit | fit | **ellipsis** |
| “Random picture” | fit at 104? no — 14 chars ~90 dp, fits 96 content | tight | ellipsis |
| Bengali “আর্কাইভ” | likely fit; wider glyphs | tight | ellipsis |

- Keep the **visible labels to one short word**: Feed, Archive, Random. Do not use “Random picture” in the dock; put the full phrase in `nav_random_description`.
- The accessible name is the **full** text even when the glyphs are ellipsised; Compose's semantics text is the original string, not the truncated layout.
- The centre cell is 104 dp with 4 dp horizontal padding: about 96 dp of text at 12 sp (~13 Latin characters). At 1.6× about 96 dp at 19.2 sp (~9 characters). At 320 it is the same 96 because the centre is fixed.
- RTL does not change the width; Arabic/Bengali glyph shapes can be wider. Test the actual translations before shipping.
- Do not use all-caps or letter-spacing on translated labels; it adds width and reduces legibility. The mockup uses 0.012 em; drop it for languages that need the room.
- If a translated label is too long, shorten the translation, not the type size. 12 sp is the floor.

---

## 4. RTL

**What happens.** The Row mirrors: Feed at the right (start), Archive at the left, Random at the centre. The orbit is symmetric and does not mirror. The underline is centred. The icons are not auto-mirrored.

**Why this is correct.** In RTL, the first destination should be at the start of the reading direction; the random action stays the centre of the bar. This matches platform navigation conventions. It also means a muscle-memory “Feed is on the left” does not survive a locale change — but that is the correct behaviour for a layout-direction-aware app.

**What to verify.**

- The track's gradient is symmetric (it is), so the fade looks the same.
- The selected underline is under the label, not offset by a start/end assumption.
- No hard-coded `padding(start = …)` on only one side that would be asymmetric under RTL; the layout uses centred columns.
- The rocket and archive icons do not have `android:autoMirrored="true"`.
- TalkBack order in RTL follows the visual order: Archive → Random → Feed. This is expected but should be read through once.
- If the product decides Feed must remain physically left in RTL (some apps do), the station order needs `LayoutDirection`-aware placement and the track apex stays centred; not recommended without a product decision.

**Failure mode.** Using `Modifier.offset` or absolute positions for the underline/node; the bar would not mirror. The proposed layout uses a Row and centred columns, so this is avoided.

---

## 5. System insets

| Mode | Bottom inset | Bar total | Content row |
| --- | --- | --- | --- |
| Gesture navigation | 24 dp (typical) | 105 dp (1 + 80 + 24) | 80 dp, untouched |
| Three-button | 48 dp | 129 dp | 80 dp, untouched |
| Hardware nav / inset 0 | 0 | 81 dp | 80 dp; see below |

- The bar's Column background covers the inset; the spacer is inside it. The content is never shifted into the system area.
- The 1 dp top rule is outside the 80 dp content; the spec's “80 dp dock” means the content row.
- Do **not** use `navigationBarsPadding()` on the Row. It would add the inset inside the 80 dp, moving the labels up and reducing their breathing room.
- **Inset = 0.** Some OEM gesture implementations report 0 while still drawing a pill over the app. On the proposed layout the pill would sit near the bottom 8–12 dp and can overlap the underline (76–78) or the bottom of the label at very large font scales. If this appears on a real device, add a minimum bottom breathing space (`max(inset, 16.dp)`) or use `WindowInsets.safeDrawing` where the platform reports it. Do not hard-code 24.
- **Landscape side nav.** If the navigation bar is on the side, the bottom inset is 0 but the horizontal inset matters. Apply `navigationBars.only(Horizontal)` to the content, not the background; the node centres in the safe width. The bar still spans the physical width.
- **Cutouts.** A bottom display cutout is rare; `safeDrawing` covers it. If used, the 80 dp content remains, plus the cutout inset. Test on a device with a bottom cutout if one is in the support matrix.
- **Inset changes at runtime** (gesture ↔ three-button): the bar height changes by 24 dp; the Scaffold remeasures; the feed's pager state is preserved. Verify there is no visible jump in the reading overlay (it moves with the content padding).

---

## 6. Landscape and tablets

### Landscape

At a 360 dp height, the 105 dp bar is **29%** of the screen; at 411 dp it is **25.5%**. The current bar is 97 dp (27% / 23.6%), plus a disc that protrudes 16 dp. The new bar is not materially worse and removes the protrusion, but the photo area is small.

The app does not lock orientation (`AndroidManifest.xml` has no `screenOrientation`). Options:

1. **Recommended for this release:** lock the feed to portrait if landscape is not a designed experience today. The app is a daily full-screen picture; the current layout is portrait-first (reading overlay, action rail, date rail).
2. If landscape must be supported: build a compact 64 dp dock variant — 20 dp top padding, 44 dp node, 11 sp labels, 44 + 2 + 14 + 4 = 64. That breaks the 48 dp node visual but keeps the 64 dp cell target; labels at 11 sp are below the 12 sp floor and need an accessibility review. This is a separate design, not a media query on this one.
3. Do not shrink the bar by hiding labels in landscape; that removes the accessible name for sighted users and changes the nav's identity.

The orbit in landscape is flatter (a 720–900 dp wide bar, 80 high); the apex is still at the node. The track's edge fade is 16% of a wider width; fine.

### Tablets / foldables

- The content is capped at 480 dp and centred. The bar surface is full-bleed. The side cells become 188 dp at 480; the node stays 104/48. This avoids three controls spread across an 800 dp slab.
- On a foldable in tabletop mode, the bar sits at the bottom of one pane; the cap keeps the three controls together. If the app ever uses a two-pane layout, the bar may need a different home; out of scope.
- The track is 480 dp, not full screen; the fade lives within the content. On a wide screen, a full-width 1 dp line would be a decorative underline to the whole tablet; the cap prevents that.

---

## 7. Failure modes and graceful degradation

| Failure | Likelihood | Effect | Mitigation |
| --- | --- | --- | --- |
| Track invisible on a low-end panel | Medium | No functional loss; the nav looks like a plain bar with a ring | Raise `Orbit` to `#454C63`; or remove the track. The design does not depend on it. |
| Track reads as a stray line / accidental rule | Medium | Aesthetic distraction; users may think it is a divider | 1 dp is already quiet; ensure it does not touch the labels; if review dislikes it, remove it. The station ring and underline still tell the story. |
| Random feels under-prominent | Medium | Users may not notice the action | Raise the ring to 2 dp / 80%, or the node to 52 dp (label gap becomes 2 dp); do **not** restore the 80 dp disc. A one-line user test will settle it. |
| Random mistaken for a third tab | Medium | Tapping it navigates rather than switches; the label says Random and the rocket suggests an action, but the ring could read as a state | The random is not selectable, has no underline, and has an action label; the ring's press state and the picker overlay clarify. Test with 5 users. |
| Long label ellipsises | High at 1.6× + narrow | Visual truncation | Full accessible name; keep labels short; test translations; do not shrink type. |
| Font scale 3.0 on 320 | Low | Side labels ellipsise; bar is 137 dp tall | Accept; the accessible name remains. If this is a hard requirement, a 2-line label or a vertical layout is a different design. |
| Label overlaps the system gesture pill when inset is 0 | Low/OEM | The underline/label bottom can collide | Add `max(inset, 16.dp)` bottom space on affected devices; use `safeDrawing`. |
| Node's progress arc keeps animating off-screen | Low | Battery/animation waste if the state is stale | Stop on lifecycle pause; clear the pick state on success/failure/cancel. |
| Two random taps stack pickers | Medium | Two overlays or a second pick | Disable the nav random while picking or let the overlay be the only interaction; the current `onRandom` navigates and increments. |
| The bar covers the feed's overlay at 411/360 | Low | Reading copy or pills touch the bar | The Scaffold pads the feed; the overlay uses `bottom = 14.dp`; verify both widths. The mockups show it clear. |
| `fillMaxWidth` label without padding | Low | Ellipsis touches the cell edge | 4 dp horizontal padding on the label. |
| Missing `minmax(0, 1fr)`-equivalent in a custom layout | Low | Long label widens a cell; centre shifts | Use weights and `maxLines=1`; test the long-label state. |
| Auto-mirrored icons in RTL | Low | Rocket/box flips oddly | Do not set `autoMirrored`; use the existing drawables. |
| Selected state only by colour | Low if implemented as specced | Colour-blind users lose the cue | Weight + underline + colour; the underline is the non-colour cue. |

---

## 8. What would change the recommendation

- **If the random action must be reachable with one thumb on a 6.7″ phone**, the centre is the least reachable spot. The current design already puts it there; the brief fixes the three-part layout. If reachability becomes the primary criterion, a **Launch Bay at the trailing edge** (or a right-side action dock) is a better spatial answer than the centre; that is concept 2, moved.
- **If the product wants a bolder identity**, the Horizon Crest is the most distinctive, but it needs a real silhouette treatment (not a hairline on a dark bar) and a custom shape. It is a better fit for an onboarding or a wallpaper preview than a persistent nav.
- **If the track fails in review**, the fallback is not the old disc: keep the 80 dp dock, the 48 dp ring, the labels and the underline, and delete the track. The nav still solves the initial problem (calm, labelled, no protrusion).
- **If the 80 dp bar is too tall on a specific device class**, reduce the top padding (26 → 22) and icon gap (8 → 6) before reducing the node or the labels; the content height becomes 76 dp + inset. Do not go below 72 dp with 12 sp labels.

---

## 9. Fidelity notes

- The mockup is HTML/CSS, not Compose. Font metrics, line heights and ellipsis points will differ by a fraction; use the Compose implementation and a device screenshot as the source of truth.
- The phone mockups include a device bezel and a simulated status bar, camera hole and gesture pill. Those are not app UI.
- The feed overlay in the mockup approximates the real `FeedPageContent` (date rail, credit, title, teaser, pills, action rail) to test the bar against real content. The real app's exact copy/height differs.
- The photo is the current APOD frame (M42, © Roberto Palaia) used for context only; it is not shipped with the app or the design system.
- The 1.6× state simulates Android font scaling in CSS. Compose may measure the label slightly differently; the bar-growth formula is the contract, not the exact CSS height.
- The 320 dp long-label state uses “Bibliothèque” as a stress string, not shipping copy. The app is currently English-only; the state proves the ellipsis and the need for short labels.
