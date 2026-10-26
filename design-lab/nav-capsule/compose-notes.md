# Compose implementation notes — Launch Cradle dock

Target: replace `ui/navigation/MohakashBottomBar.kt` with a new `MohakashDock` composable. No route,
nav-graph, deep-link or state-restoration changes. Compose BOM 2025.09.00, minSdk 24, edge-to-edge.

---

## 1. Files

| File | Change |
|---|---|
| `ui/navigation/MohakashDock.kt` | **new** — dock, cradle shape, tab, key |
| `ui/navigation/MohakashBottomBar.kt` | delete after the flag ships (keep `BottomDestination` until then) |
| `ui/navigation/MohakashApp.kt` | swap the `bottomBar` call; pass `onRandom`, `onSelect` through unchanged |
| `ui/theme/Dimens.kt` | add the dock tokens (§5) |
| `ui/theme/Color.kt` | add `DockWellRim` (and `DockKeyRing` if the extra 1 dp of ring light is wanted) |
| `ui/preview/DockPreviews.kt` | **new** — 360 / 411 / 1.3× / 2.0× previews |

`BottomDestination` (`FEED`, `ARCHIVE`) and its `labelRes` / `iconRes` / `activeIconRes` stay as they
are; the dock only reads them.

---

## 2. Layout tree

```
MohakashApp
└─ Scaffold(bottomBar = { if (showBottomBar && !feedFullscreen) MohakashDock(...) })
   └─ MohakashDock
      └─ Layout( fillMaxWidth, wrapContentHeight,
                 windowInsetsPadding(navigationBars.only(Bottom) + displayCutout.only(Horizontal)) )
         ├─ [0] DockPlate   (measured with minWidth = plateWidth, minHeight = 54)
         │      Box( width(plateWidth), heightIn(min = 54.dp), shadow(10, shape), clip(shape),
         │           background(Surface), border(1.dp, BorderStrong, shape) )
         │      ├─ SelectionWell  align(CenterStart), offset(x = wellX), 72 × 40, r 20
         │      ├─ Row(selectableGroup) { DockTab(FEED); Spacer(64.dp); DockTab(ARCHIVE) }
         │      └─ (no key here — the key is deliberately outside the plate)
         └─ [1] RocketKey  48 × 48, measured fixed, placed at y = 0, centred
   place: plate at y = 10.dp, key at y = 0
   container height = 10 + plate.height + 8 + navigationBars.bottom
```

Why a custom `Layout` for two children: the plate's height is content-driven (it grows with the font
scale), and the key must stay pinned to the plate's **top** edge while the plate grows downward. Two
`Box` alignments cannot express that without measuring the plate first. The whole thing is ~20 lines
of measure/place.

**The key must be a sibling of the plate, not a child.** Its 48 dp target extends above the plate; if
it is placed inside the plate's `clip(shape)` it loses the outer 6 dp of its target and the notch
ordering breaks.

---

## 3. The cradle shape

```kotlin
/** A pill with a shallow, centred bite in its top edge. */
class LaunchCradleShape(
    private val cradleWidth: Dp = MohakashDimens.DockCradleWidth,   // 58.dp
    private val cradleDepth: Dp = MohakashDimens.DockCradleDepth,   // 12.dp
    private val shoulder: Dp = MohakashDimens.DockCradleShoulder,   // 8.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = size.height / 2f
        val half = with(density) { cradleWidth.toPx() } / 2f
        val depth = with(density) { cradleDepth.toPx() }
        val sh = with(density) { shoulder.toPx() }
        val cx = size.width / 2f

        val path = Path().apply {
            moveTo(r, 0f)
            lineTo(cx - half, 0f)
            // left shoulder: lip → floor
            cubicTo(cx - half, depth * 0.55f, cx - half + sh, depth, cx - half + sh, depth)
            lineTo(cx + half - sh, depth)
            // right shoulder: floor → lip
            cubicTo(cx + half - sh, depth, cx + half, depth * 0.55f, cx + half, 0f)
            lineTo(size.width - r, 0f)
            // right cap
            arcTo(Rect(size.width - 2 * r, 0f, size.width, size.height), -90f, 180f, false)
            // bottom edge is implied by the arc's end point → left cap start
            arcTo(Rect(0f, 0f, 2 * r, size.height), 90f, 180f, false)
            close()
        }
        return Outline.Generic(path)
    }
}
```

- Use one `val shape = remember { LaunchCradleShape() }` in the dock so the path is not recreated per
  recomposition.
- `Modifier.shadow(10.dp, shape, clip = false)` then `.clip(shape)` then `.background` then
  `.border(1.dp, BorderStrong, shape)`. The border follows the notch automatically.
- A simpler notch is a 58 × 12 rounded-rect (5 dp corners) subtracted with `Path.op(..., Difference)`.
  It is less code but leaves a 58 dp floor: the 44 dp key then shows ~7 dp of background on each side
  at the top edge and ~12 dp at the floor, so it reads as a slot rather than a seat. The shape above
  keeps a 42 dp floor with 8 dp shoulders, matching the mockup. Choose one and keep it everywhere.
- The shape is height-agnostic: the caps use `size.height / 2`, the notch depth stays 12 dp. The key
  is pinned, so a taller plate simply has more material below the cradle.
- At mdpi the 1 dp border can alias against the path; keep the hairline at 1 dp (the app's language)
  and check a 320 dp device before shipping. Do **not** stroke the path inside a `Canvas` — use
  `Modifier.border` so the stroke stays crisp and the semantics stay on the Box.

---

## 4. Width and height behaviour

```kotlin
val fontScale = LocalDensity.current.fontScale
val cellWidth = if (fontScale >= 1.6f) 94.dp else 84.dp
val plateWidth = cellWidth * 2 + DockSlotWidth + DockSidePadding * 2
// 84 → 252 dp;  94 → 272 dp
```

- The plate's height is **not** set: `heightIn(min = 54.dp)` plus the tab's `padding(vertical = 6.dp)`
  and the label's line height drives it. At 1.0× it measures 54, at 1.3× ~58, at 2.0× ~68–70.
- `plateWidth` is a *minimum*; the measure pass also caps it at `constraints.maxWidth - 24.dp`. If a
  label still cannot fit (fontScale > 2.0 or a screen under ~300 dp), set `labels = false`: cells
  become 56 dp, the plate 188 dp, the icons keep 48 dp targets and the content descriptions remain.
  This is the only fallback and it is not expected on any current device.
- The key is always 44 dp / 48 dp target; it is not scaled by the font scale. The cradle stays
  58 × 12 so the key's fit is stable.

**Well offset** (LTR):

```kotlin
val wellBase = DockSidePadding + (cellWidth - DockWellWidth) / 2      // 16 dp at 84
val wellTarget = wellBase + if (selected == ARCHIVE) cellWidth + DockSlotWidth else 0.dp
val wellX by animateDpAsState(wellTarget, tween(220, easing = FastOutSlowInEasing), label = "dock-well")
```

For RTL, `Modifier.offset` does not mirror. Compute the offset from the plate's layout direction
(`LocalLayoutDirection.current`): swap the two values. The app ships LTR only (`supportsRtl` is true in
the manifest but there is no translated layout), so this is a note, not a requirement.

---

## 5. Tokens

Add to `MohakashDimens`:

```kotlin
val DockWidth = 252.dp          // 84 + 84 + 64 + 10 + 10
val DockHeight = 54.dp
val DockRadius = 27.dp
val DockFloat = 8.dp            // plate bottom → navigation inset
val DockLift = 10.dp            // container top → plate top (key sits in the first 48)
val DockCradleWidth = 58.dp
val DockCradleDepth = 12.dp
val DockCradleShoulder = 8.dp
val DockKeySize = 44.dp
val DockKeyTarget = 48.dp
val DockCellWidth = 84.dp
val DockCellMinHeight = 48.dp
val DockSlotWidth = 64.dp
val DockSidePadding = 10.dp
val DockWellWidth = 72.dp
val DockWellHeight = 40.dp
val DockWellRadius = 20.dp
val DockShadow = 10.dp
```

Add to `MohakashColors`:

```kotlin
/** The recessed rim of the selected seat; darker than the plate. */
val DockWellRim = Color(0xFF05060A)

/** Optional: the lift ring around the rocket key. BorderStrong also works. */
val DockKeyRing = Color(0xFF3A4257)
```

Mapping to what already exists: plate = `Surface` `#151922`; hairline = `BorderStrong` `#323849`;
key = `SurfaceStrong` `#1B2030`; well = `Background` `#0B0D12`; selected text = `Text`; idle text =
`TextMuted`; accent = `Accent`. No new *visual* colours are strictly required — the two additions are
only for the well's inner rim and a 1 dp lighter key ring.

Typography: `MohakashTextStyles.Caption` (12 / 16 Inter) for labels; selected copies
`fontWeight = FontWeight.SemiBold`. Icon size 22 dp; rocket glyph 21 dp.

---

## 6. Animation & state

Everything below is finite; no `rememberInfiniteTransition` in the dock.

| Interaction | Spec | Code |
|---|---|---|
| Well travel | 220 ms `FastOutSlowIn`, 148 dp | `animateDpAsState(..., tween(220, FastOutSlowInEasing))` |
| Label / icon tint | 120 ms | `animateColorAsState(..., tween(120))` |
| Key press | scale 1 → 0.96 in 90 ms; release spring 220 ms | `collectIsPressedAsState()` + `animateFloatAsState(if (pressed) 0.96f else 1f)` |
| Launch ring | 300 ms, radius 44 → 58, alpha 1 → 0 | `Animatable(0f)` + `LaunchedEffect(launchToken)` |
| Selection haptic | one tick | `HapticFeedbackType.TextHandleMove` |
| Launch haptic | one confirm | `HapticFeedbackType.TextHandleMove` (or `LongPress`) — `Confirm` is newer than the BOM's stable surface |

Key points:

- **No ripple.** `clickable(interactionSource, indication = null, role = Role.Button)`. The well
  movement and the key's scale are the feedback; a ripple inside a 44 dp disc added visual noise.
- **Press state** comes from the key's own `MutableInteractionSource`, not from `onClick`. The launch
  ring is triggered in the same lambda that calls `onRandom()`.
- **`launchToken`**: a local `mutableIntStateOf(0)` incremented on click, consumed by a
  `LaunchedEffect(launchToken)`. Keep the ring self-contained; there is no need to route the feed's
  `isPickingRandom` up in v1. If the ring should stay accent while the random index loads, add a
  `randomLaunching: Boolean` callback (the feed already has `onFullscreenChange`, so the pattern
  exists) — but the ring is feedback, not a progress indicator, and the overlay owns progress.
- **Selection is derived, never stored.** `selected = if (currentRoute == Routes.ARCHIVE) ARCHIVE else FEED`.
  The dock is stateless with respect to navigation; no `rememberSaveable`, no duplicated state.
- **Animator scale**: Compose respects the system animator duration scale for `animate*`; the ring's
  `Animatable` also follows it. Nothing loops, so a reduced-motion device simply gets an instant well.
  Do not add `graphicsLayer` animations beyond the 0.96 scale.

---

## 7. Icons and assets

No new drawables are needed:

| Slot | Drawable | Size | Tint |
|---|---|---|---|
| Feed | `R.drawable.ic_today_24` | 22 dp | `Text` / `TextMuted` |
| Archive | `R.drawable.ic_archive_24` | 22 dp | `Text` / `TextMuted` |
| Rocket | `R.drawable.ic_rocket_24` | 21 dp | `Accent` |
| Settings (top bars) | `R.drawable.ic_settings_24` | 24 dp | unchanged |

All are Remix Icon paths already in the project (`ic_today_24`, `ic_archive_24`, `ic_rocket_24`).
`BottomDestination.activeIconRes` currently points at the same file as `iconRes`; that stays true —
the active state is colour + weight, not a different glyph. If a filled sun is added later, put it in
`activeIconRes` and nothing else changes.

The cradle is a **shape**, not a drawable. Do not add a nine-patch or vector background: the notch
must scale with the measured plate height and the border must follow the cut.

---

## 8. Safe area

```kotlin
Modifier.windowInsetsPadding(
    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom) +
        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
)
```

- The Scaffold uses `contentWindowInsets = WindowInsets(0, 0, 0, 0)` and each screen owns its top
  inset, so the dock owns the bottom one. The dock's height (including this padding) becomes the
  Scaffold's bottom content padding — the NavHost needs no changes.
- Do **not** use `navigationBarsPadding()`: in landscape the navigation bar can be on the side, and
  all-sides padding would shrink the centred plate's available width for no reason. Bottom only.
- Gesture nav: the 8 dp float sits above the 24 dp inset; the capsule's bottom is 32 dp above the
  screen edge, well clear of the gesture pill.
- 3-button nav: the inset becomes 48 dp; the capsule floats 56 dp above the screen edge. The band is
  120 dp, but the plate itself is unchanged.
- Landscape: the navigation bar inset is often on the side; the bottom inset may be 0. The compact
  build uses a 56 dp container + the inset. Add the display-cutout horizontal padding so the plate
  never sits under a camera hole (it is centred, so this is belt-and-braces).
- IME: the navigation-bar inset does not include the keyboard. On the Archive screen the search field
  is inline, so opening the keyboard can leave the dock floating above it. Recommendation: hide the
  dock with a 160 ms fade when `WindowInsets.isImeVisible` and the search field has focus, and let
  the content's padding collapse with it. Test this; the alternative (dock above the keyboard) is
  acceptable but eats 96 dp of the visible list.

---

## 9. Semantics

```kotlin
Row(Modifier.selectableGroup()) {
    DockTab(destination = FEED, selected = selected == FEED, onClick = { onSelect(FEED) })
    Spacer(Modifier.width(DockSlotWidth))
    DockTab(destination = ARCHIVE, selected = selected == ARCHIVE, onClick = { onSelect(ARCHIVE) })
}

@Composable
private fun DockTab(destination: BottomDestination, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(cellWidth)
            .heightIn(min = DockCellMinHeight)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(destination.iconRes), contentDescription = null, ...)
        Text(stringResource(destination.labelRes), ...)
    }
}
```

- Each tab merges its icon + label into one node; TalkBack announces *“Feed, tab, 1 of 2, selected”*.
  Do not put a `contentDescription` on the icons — the label is the name.
- The key is outside the `selectableGroup` and is a `Role.Button`:

```kotlin
Box(
    Modifier
        .size(DockKeyTarget)
        .semantics {
            contentDescription = randomDescription      // existing nav_random_description
            if (picking) stateDescription = "Choosing a picture"
        }
        .clickable(interactionSource, indication = null, role = Role.Button, onClick = onRandom),
)
```

- The dock as a whole needs no `contentDescription`: it is a container; the two tabs and the key are
  already named. Do not mark it as an image or a single button.
- Keep the existing string resources (`nav_feed`, `nav_archive`, `nav_random_description`). No new
  copy is required.
- Minimum targets: the tab cell is 84 × 48, the key 48 × 48. The visible key is 44; the target is the
  box, not the disc.
- If a haptic is added, keep it on the key and on tab changes only — not on every recomposition and
  not on the well animation.

---

## 10. Scaffold wiring

```kotlin
bottomBar = {
    if (showBottomBar && !feedFullscreen) {
        MohakashDock(
            currentRoute = currentRoute,
            onSelect = { destination -> /* unchanged navigate block */ },
            onRandom = { /* unchanged: navigate TODAY + randomRequest += 1 */ },
        )
    }
}
```

- `MohakashDock` should not know about `NavController`, routes or the feed; it takes a route string
  (or a `DockSelection` enum) and two lambdas. That keeps the previews trivial.
- The random overlay stays in the feed. The dock only fires the callback.
- The fullscreen feed path already removes the bottom bar; note that the content padding jumps by the
  dock height. This is existing behaviour, not a regression, but it is more visible with a floating
  dock (there is no opaque strip to mask the reflow). If it looks bad, fade the dock out and keep the
  inset for the fade duration with an animated `Spacer` in the bottomBar slot — measure before
  building it.

---

## 11. Previews and checks

```kotlin
@Preview(name = "Dock · 360", widthDp = 360, heightDp = 100,
         backgroundColor = 0xFF0B0D12, showBackground = true)
@Preview(name = "Dock · 411", widthDp = 411, heightDp = 100, ...)
@Preview(name = "Dock · 1.3×", widthDp = 360, fontScale = 1.3f, ...)
@Preview(name = "Dock · 2.0×", widthDp = 320, fontScale = 2.0f, ...)
@Composable
private fun DockPreview() = MohakashTheme {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.BottomCenter) {
        MohakashDock(currentRoute = Routes.TODAY, onSelect = {}, onRandom = {})
    }
}
```

Manual checks before merge:

- 320 dp / 360 dp / 411 dp; 1.0× / 1.3× / 2.0× text.
- Gesture nav and 3-button nav; landscape on a phone.
- A white/bright APOD frame (the dock is over the app background, but check the feed's bottom edge).
- TalkBack traversal order: Feed → Archive → Rocket (the key is a separate action after the tabs).
- The plate's top hairline at mdpi; the notch at 2.0× (does the 12 dp depth still look intentional when
  the plate is 68 dp? It does in the prototype; verify on device).
- No clipping of the key's 48 dp target by a parent `clipToBounds`.

---

## 12. Effort

| Piece | Estimate |
|---|---|
| Shape + dock layout + tokens | 0.5 day |
| Tabs, key, well, animation | 0.5 day |
| Semantics, previews, insets | 0.25 day |
| Device/QA pass and flag wiring | 0.25–0.5 day |
| **Total** | **~1.5 days** |

No new dependencies. The only non-stock piece is the `Shape` (~40 lines) and the two-child `Layout`
(~30 lines).
