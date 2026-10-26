# Compose implementation notes — Orbit Line

**Target:** Compose BOM `2025.09.00`, Material 3, minSdk 24, targetSdk 36, edge-to-edge (`MainActivity.enableEdgeToEdge()`).
**Scope:** this is a design-lab implementation guide. It describes changes to production files but does not modify them.
**Read with:** [proposal.md](proposal.md) · [tradeoffs.md](tradeoffs.md) · [mockup.html](mockup.html)

---

## 0. Contract

`MohakashBottomBar` keeps its existing entry points:

```kotlin
@Composable
fun MohakashBottomBar(
    currentRoute: String?,
    onSelect: (BottomDestination) -> Unit,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
    // optional phase 2; see §7
    isPickingRandom: Boolean = false,
)
```

- `BottomDestination` stays Feed / Archive. Random is **not** a destination and gets no enum entry.
- The bar is shown only on `Routes.TODAY` and `Routes.ARCHIVE`, and is hidden by the feed's fullscreen behaviour. That logic in `MohakashApp.kt` does not change.
- The bar remains solid `SurfaceRaised`. The orbit track is drawn on the bar, never over the feed.
- No new drawable is required. The three icons already exist: `ic_today_24`, `ic_archive_24`, `ic_rocket_24`.

---

## 1. Layout tree

```text
Column(background = SurfaceRaised)
├── HorizontalDivider(1.dp, Border)                     // top hairline
├── Box(                                                // content row, 80 dp min
│     fillMaxWidth()
│     .windowInsetsPadding(navigationBars.only(Horizontal))
│     .heightIn(min = NavDockHeight)                    // grows with font scale
│   )
│   └── Box(align = TopCenter, fillMaxWidth, widthIn(max = 480.dp))
│       ├── drawWithCache { orbit path }                // behind the Row
│       └── Row(fillMaxWidth, verticalAlignment = Top)
│           ├── NavStation(Feed,    weight = 1f)        // 128 dp at 360
│           ├── RandomStation(width = 104.dp)           // 48 dp ring
│           └── NavStation(Archive, weight = 1f)        // 128 dp at 360
└── Spacer(fillMaxWidth().windowInsetsBottomHeight(navigationBars))
```

Why the nested boxes:

- The **outer** box owns the minimum height and the horizontal safe-area padding. Its background comes from the Column, so the bar remains full-bleed under the system inset.
- The **inner** box is what gets `widthIn(max = 480.dp)`. On a tablet, the track and the three stations are 480 dp wide and centred; the bar surface still spans the screen.
- The track is drawn on the inner box with `drawWithCache`, so it is behind the Row and its width always matches the station row. No absolute left/right math, no RTL special case.
- The Row is top-aligned. All vertical rhythm comes from the two items; no fixed bar height.

### Exact item metrics (1×)

| | Destination item | Random item |
| --- | --- | --- |
| Top padding | 26 dp | 6 dp |
| Visual | 24 dp icon | 48 dp ring |
| Gap to label | 8 dp | 4 dp |
| Label | 12 / 16 sp | 12 / 16 sp |
| Selection gutter | 2 dp gap + 2 dp underline + 2 dp bottom | 6 dp transparent spacer |
| **Total height** | **80 dp** | **80 dp** |
| Label top | 58 dp | 58 dp |
| Click target | full weight × 80 dp (128 × 80 at 360) | 104 × 80 dp |

Both columns are exactly `64 + 16 × fontScale` dp tall, which is why labels share a baseline at every font scale. The random spacer is not decoration: it makes the random cell a full-height target and keeps both columns equal, so the Row height is predictable.

At 411 dp the side cells are 153.5 dp; the centre stays 104 dp. At 320 dp they are 108 dp.

---

## 2. Tokens and strings

Add to `MohakashDimens` (and retire the old `BottomNavHeight = 72.dp`):

```kotlin
val NavDockHeight = 80.dp
val NavStationCell = 104.dp
val NavStationSize = 48.dp
val NavRocketSize = 26.dp
val NavIconSize = 24.dp
val NavTrackEdgeY = 48.dp
val NavTrackApexY = 30.dp
val NavRingWidth = 1.5.dp
val NavUnderlineWidth = 20.dp
val NavUnderlineHeight = 2.dp
val NavContentMaxWidth = 480.dp
const val NavRingAlpha = 0.70f
```

Add one colour to `MohakashColors`:

```kotlin
/** The navigation orbit track — decorative, deliberately quieter than Border. */
val Orbit = Color(0xFF3A4055)
```

Add one string:

```xml
<string name="nav_random">Random</string>
<!-- Existing string, now used as the action label, not the accessible name. -->
<string name="nav_random_description">Send a rocket to a random picture</string>
```

Use `MohakashTextStyles.Caption` for all three labels. Selected destination: `Caption.copy(fontWeight = FontWeight.SemiBold)`; unselected: `Caption` with `TextMuted`; Random: `Caption` with `Accent` and `FontWeight.Medium`.

---

## 3. The station

```kotlin
@Composable
private fun NavStation(
    destination: BottomDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelColor by animateColorAsState(
        targetValue = if (selected) MohakashColors.Text else MohakashColors.TextMuted,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "navLabelColor",
    )
    val underlineWidth by animateDpAsState(
        targetValue = if (selected) MohakashDimens.NavUnderlineWidth else 0.dp,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "navUnderlineWidth",
    )

    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(top = 26.dp)
            .testTag("nav-${destination.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = null,                 // the visible label is the name
            tint = labelColor,
            modifier = Modifier.size(MohakashDimens.NavIconSize),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MohakashTextStyles.Caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .width(underlineWidth)
                .height(MohakashDimens.NavUnderlineHeight)
                .clip(RoundedCornerShape(1.dp))
                .background(MohakashColors.Accent),
        )
        Spacer(Modifier.height(2.dp))
    }
}
```

Notes:

- `selectable` goes **before** the internal padding, so the whole 80 dp column is the target.
- The underline keeps its 2 dp height when unselected (width animates to 0). This preserves the layout and prevents a 2 dp jump in the label baseline.
- The icon has `contentDescription = null`; the merged selectable node exposes “Feed, tab, selected”. Do not duplicate the name.
- `maxLines = 1` + `Ellipsis` is deliberate. The full string remains in the semantics tree even when the glyphs are ellipsised.
- Do **not** wrap the label in a `BasicText` with `softWrap = false` but no overflow; long localized labels must not bleed into the next cell.
- Animated colour + animated underline are the motion. The weight change is discrete at the state change; that is acceptable and avoids a second text layer.

---

## 4. The random station and the ring

```kotlin
@Composable
private fun RandomStation(
    isPicking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = modifier
            .width(MohakashDimens.NavStationCell)
            .clickable(
                interactionSource = interaction,
                indication = null,                      // the ring is the press affordance
                role = Role.Button,
                onClickLabel = stringResource(R.string.nav_random_description),
                onClick = onClick,
            )
            .padding(top = 6.dp)
            .testTag("nav-random"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RandomNode(isPicking = isPicking, pressed = pressed)
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.nav_random),
            style = MohakashTextStyles.Caption,
            fontWeight = FontWeight.Medium,
            color = MohakashColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(6.dp))                   // selection gutter, full-height target
    }
}
```

`RandomNode` draws the ring, the fill, the rocket and (while picking) the progress arc in one place:

```kotlin
@Composable
private fun RandomNode(isPicking: Boolean, pressed: Boolean) {
    val ringAlpha by animateFloatAsState(
        targetValue = when {
            pressed -> 1f
            isPicking -> 0.28f                          // let the progress arc read
            else -> MohakashDimens.NavRingAlpha
        },
        animationSpec = tween(160),
        label = "navRingAlpha",
    )

    val rotation: State<Float>? = if (isPicking) {
        val transition = rememberInfiniteTransition(label = "randomPick")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "randomRingRotation",
        )
    } else {
        null
    }

    Box(
        modifier = Modifier
            .size(MohakashDimens.NavStationSize)
            .clip(CircleShape)
            .background(MohakashColors.Surface)
            .drawBehind {
                val stroke = MohakashDimens.NavRingWidth.toPx()
                val radius = size.minDimension / 2f - stroke / 2f
                drawCircle(
                    color = MohakashColors.Accent.copy(alpha = ringAlpha),
                    radius = radius,
                    style = Stroke(width = stroke),
                )
                rotation?.let { angle ->
                    drawArc(
                        color = MohakashColors.Accent,
                        startAngle = angle.value - 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_rocket_24),
            contentDescription = null,
            tint = MohakashColors.Accent,
            modifier = Modifier.size(MohakashDimens.NavRocketSize),
        )
    }
}
```

Implementation details:

- The ring is **1.5 dp at 70% Accent** on `Surface`; no solid accent disc. The 48 dp fill is the only mass.
- The 48 dp node is the *visual*; the click target is the 104 × 80 dp column. Do not put `clickable` on the 48 dp box.
- `indication = null` removes a rectangular ripple around an instrument. The visible press feedback is the ring brightening to 100% in 160 ms. That is a real, non-colour-only pressed state.
- When `isPicking`, the base ring dims to 28% and a 90° accent arc sweeps clockwise. The angle is read inside `drawBehind`, so each frame invalidates only the draw phase, not recomposition.
- The infinite transition is created **only** while picking. Never run it at rest.
- Honour the system animation duration scale: if it is 0, skip the infinite transition and draw a static 90° arc at the top. The overlay's “Plotting a course…” copy is the primary status; the arc is supplementary.
- If `isPicking` cannot be hoisted cleanly (see §7), ship without the arc. The ring and label are complete without it.

### Why the ring is 70% and not 100%

The accent is a signal. A 62 dp solid core used ~3,019 dp² of it; the new ring uses ~220 dp². At 70% over `Surface` it still measures above 4.5:1 against the bar and reads at 1×; at 100% it would compete with the photo. Keep the alpha in a token so it can be tuned on device.

---

## 5. The orbit track

```kotlin
private fun Modifier.drawOrbitTrack(): Modifier = drawWithCache {
    val edgeY = MohakashDimens.NavTrackEdgeY.toPx()
    val apexY = MohakashDimens.NavTrackApexY.toPx()
    // Quadratic apex = (P0 + 2C + P2) / 4. With P0 = P2 = edgeY, C = 2*apexY - edgeY.
    val controlY = apexY * 2f - edgeY
    val path = Path().apply {
        moveTo(0f, edgeY)
        quadraticBezierTo(size.width / 2f, controlY, size.width, edgeY)
    }
    val brush = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.16f to MohakashColors.Orbit,
        0.84f to MohakashColors.Orbit,
        1f to Color.Transparent,
        startX = 0f,
        endX = size.width,
    )
    val stroke = Stroke(width = 1.dp.toPx())
    onDrawBehind { drawPath(path, brush = brush, style = stroke) }
}
```

- The apex is exactly the node centre: `y = 30 dp`. The edges leave at `y = 48 dp`.
- At the side cells' icon x, the curve is at ≈ 37–38 dp, which crosses the 24 dp icon band near its centre. That is what makes the icons feel like stations. It is not an accident of layout: the item paddings and the curve are tuned together.
- Do **not** fill the path. An open path with a default fill paints a black lens under the curve. Use `fill = null` / `drawPath(..., style = Stroke)`.
- The track is 1 dp and has no semantics. With `drawWithCache` it adds no semantics node automatically; if you replace it with a `Canvas` composable, add `Modifier.clearAndSetSemantics { }`.
- The fade is 16% of the track width. On a 480 dp tablet bar it is 77 dp; on 320 dp it is 51 dp. Do not fade to 0 at a fixed dp value.
- Make the colour a token. If device review finds it invisible, raise it to `#454C63`; if it reads as a stray line, lower it or remove it. The bar must not depend on it.

---

## 6. Insets, height and the scaffold

```kotlin
Column(Modifier.fillMaxWidth().background(MohakashColors.SurfaceRaised)) {
    HorizontalDivider(thickness = 1.dp, color = MohakashColors.Border)

    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal),
            )
            .heightIn(min = MohakashDimens.NavDockHeight),
    ) { /* track + row */ }

    Spacer(
        Modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars),
    )
}
```

- **Do not** put `navigationBarsPadding()` on the content row. It would add the inset inside the 80 dp measurement, shift the content up, and shrink the visual breathing room.
- The bottom spacer is the system inset; the Column's background covers it. Gesture nav is typically 24 dp; three-button is 48 dp. Nothing is clipped and the 80 dp content is never inside the inset.
- The horizontal insets matter in landscape when the navigation bar is on the side. They pad the content only; the bar surface stays full-bleed. The node centres in the safe width, not the physical screen, which is correct.
- If the app wants cutout safety at the bottom, use `WindowInsets.safeDrawing.only(Bottom + Horizontal)`; a bottom cutout is rare and this changes the 80 dp contract only by the inset.
- Keep `Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0))`. The bar is in `bottomBar`; the Scaffold measures the Column (rule + content + inset) and pads the NavHost. The feed's reading overlay therefore sits above the bar automatically.
- Keep the 4 dp opaque skirt at the bottom of `FeedPageContent`; it hides the fractional-density seam between the feed and the bar. With the new 80 dp bar the seam moves up; the skirt still does its job.
- The snackbar host is positioned by the Scaffold above the bottomBar; the taller bar moves it 8 dp up. No code change.
- Fullscreen hide/show is unchanged. The bar has no slide-in animation of its own; the feed's chrome visibility controls it.

### Font scale and dynamic height

The content height is `64 + 16 × fontScale` dp at both columns, plus the inset and the 1 dp rule:

| Font scale | Content | + 24 dp inset (gesture) |
| --- | --- | --- |
| 1.0 | 80 | 105 total with rule |
| 1.3 | ≈ 85 | ≈ 110 |
| 1.6 | ≈ 90 | ≈ 115 |
| 2.0 | 96 | ≈ 121 |
| 3.0 | 112 | ≈ 137 |

There is no fixed height, no `maxLines` truncation with a hard container, and no `fontSize` override. The node and the track stay anchored at the top (`y = 6 / 30 / 48`); only the label band grows downward. The Scaffold reserves the measured height, so content is never obscured.

---

## 7. Picking state (optional, phase 2)

The current feed owns `isPickingRandom` in `FeedViewModel`; the nav cannot see it. Two clean options:

1. **Hoist the state** to `MohakashApp` (e.g. a `RandomPickState` holder or a `SharedFlow` from the feed VM), pass `isPickingRandom` to the bar. Set it when `onRandom` fires and clear it when the pick succeeds, fails or is cancelled. This is the honest version.
2. **Do not ship the arc.** The existing `FeedRandomPickOverlay` already communicates “Plotting a course…” with a Cancel action. The nav's ring is static; the design is complete without it.

Do not fake the animation with a timer or `delay()`. If the state cannot be hoisted for this release, take option 2; the mockup's picking state is a target, not a requirement for v1.

If `isPickingRandom` is available:

- While picking, make the random action a no-op or leave it to the overlay; do not let a second tap stack pickers. The overlay's Cancel is the way out.
- Do not announce progress from the nav. The overlay is the live status; a second `liveRegion` would repeat it.
- Stop the loop when the screen is not resumed. A `rememberInfiniteTransition` inside the composed bar pauses when the bar leaves composition; if the bar stays composed during a dialog, use `Lifecycle`/`repeatOnLifecycle` to stop the animation.

### Motion summary

| Change | Spec | Notes |
| --- | --- | --- |
| Selection (destination) | label/icon colour 160 ms `FastOutSlowInEasing`; underline width 0 ↔ 20 dp 200 ms | No node movement, no track movement, no shared sliding pill |
| Press (random) | ring alpha 160 ms; no scale, no rectangular ripple | `indication = null`; the ring is the affordance |
| Press (destination) | the platform's bounded ripple on the full cell | Standard touch feedback; no custom scale |
| Picking (optional) | 90° arc, 1400 ms linear; base ring dims to 28% | Only while a pick is in flight; the overlay carries the status |
| Reduced motion | stop the loop; draw a static 90° arc at the top | Keep the 160/200 ms state transitions; they do not move |
| Bar visibility | no nav-specific animation | The Scaffold adds/removes the bar. If a crossfade is wanted, wrap it in `AnimatedVisibility(fadeIn/fadeOut(tween(180)))`; not required |

The track is never animated. There is no idle beacon, no orbiting dot, no shimmer. Motion is a state change or a real in-progress signal; if neither is true, nothing moves.

---

## 8. Semantics and accessibility

### Merge and names

| Element | Modifier | Result |
| --- | --- | --- |
| Feed / Archive | `selectable(selected, role = Role.Tab)` over the full 80 dp column | Merged node: “Feed, tab, selected”; label is the name; icon has null description |
| Random | `clickable(role = Role.Button, onClickLabel = nav_random_description)` over the 104 × 80 dp column | “Random, button”; action hint “Send a rocket to a random picture” |
| Orbit track | no semantics | Decorative; never the only cue |
| Node ring/arc | no semantics; the rocket icon has null description | Avoids a duplicate “rocket” node |
| Underline | no semantics | Decorative; selected is already announced |

### Rules

- **Do not** set `contentDescription` on the random column. It would override the visible “Random” label and make the node read as a picture description. Use `onClickLabel` for the action.
- **Do not** mark Random as a `Role.Tab` or give it a selected state. It is an action, not a destination.
- **Do not** add `selectableGroup()` around the Row: the random button is inside it but is not a tab, and the group would announce a three-item tab list that does not exist. The two tabs are separated by an action; traversal order is Feed → Random → Archive.
- The full cell is the target. Never move the click to the 48 dp ring to “match the visual”.
- Minimum target: 104 × 80 dp (random), 128 × 80 dp (destinations at 360), 108 × 80 dp at 320. The 48 dp node is a visual, not the target.
- Contrast: labels are `Text`/`TextMuted` on `SurfaceRaised` (> 7:1); the ring at 70% is ≈ 4.5:1 against the bar; the underline is ≈ 8:1. The track is decorative and exempt.
- The selected state is three cues: icon/label colour, label weight, underline. It survives greyscale and colour-vision deficiency.
- The label is 12 sp, not 10 sp caps. Letter spacing is 0.012 em; translations can remove it.
- The random label is a word, not an icon-only control. If the label must be shortened/truncated, the accessible name remains the full string.
- The bar is 80 dp of content, so the 48 dp minimum is met vertically by a wide margin; the node is only the visual — the 104 × 80 dp column is tappable.

### TalkBack traversal

Visual order is Feed → Random → Archive. This is also the semantics order. If user testing finds the action interrupting the two tabs confusing, use `Modifier.traversalIndex` to read the two tabs first and the action last; do **not** reorder the visual layout. Decide with a TalkBack test, not by assumption.

### Reduced motion

- The track is static.
- The underline/colour transitions are short (160–200 ms) and do not loop.
- The only loop is the picking arc (1.4 s, 90° sweep, no flash). Disable it when the animator duration scale is 0; leave a static arc and the overlay's text.
- No bounce, parallax, shimmer, or orbiting beacon at rest.

---

## 9. RTL

- Use a `Row` with children in logical order; Compose lays them out start → end. In RTL, Feed moves to the right, Random stays centred, Archive moves to the left. That is correct navigation behaviour.
- The track is symmetric: same path, same fade; no mirror code.
- The underline is centred under its label; no directional offset.
- Icons must **not** auto-mirror. Do not set `android:autoMirrored="true"` on the sun, archive or rocket. The rocket is symmetric enough; mirroring it would look wrong.
- The random action is at the centre in both directions; the 104 dp cell and the curve's apex are direction-independent.
- If the product ever pins Feed to the physical left in RTL (unusual), the track's apex and the cell order would need explicit layout-direction handling. Do not do this without a product decision.

---

## 10. Existing drawables and strings

| Need | Use | Change |
| --- | --- | --- |
| Feed icon | `R.drawable.ic_today_24` | none |
| Archive icon | `R.drawable.ic_archive_24` | none |
| Rocket | `R.drawable.ic_rocket_24` | none |
| Visible random label | **new** `R.string.nav_random` | add “Random” |
| Random action hint | `R.string.nav_random_description` | reuse; now on `onClickLabel` |
| Feed / Archive labels | `R.string.nav_feed`, `nav_archive` | none |
| Top rule | `HorizontalDivider` / `Box` | none |
| Orbit | code | no drawable |
| Progress | code (`drawArc`) | no drawable |

The orbit is code, not a vector: it must stretch to 360 / 411 / 480 and fade by percentage. A fixed vector would need multiple variants and would not scale its stroke correctly.

---

## 11. Migration steps

1. **Tokens and strings.** Change `BottomNavHeight` to 80 (or add `NavDockHeight`), add the node/track/underline tokens, `MohakashColors.Orbit`, `nav_random`.
2. **Rewrite `MohakashBottomBar.kt`.** Keep `BottomDestination`, `currentRoute`, `onSelect`, `onRandom`. Delete `RandomBarItem`, the 80 dp disc, the 62 dp core, the `offset(y = -16.dp)` and the 96 dp centre width. Add `NavStation`, `RandomStation`, `RandomNode`, `drawOrbitTrack`.
3. **`MohakashApp.kt`.** No call-site change for v1. For phase 2, hoist `isPickingRandom` and pass it through. Keep the existing `navigate(TODAY)` + `randomRequest += 1` behaviour.
4. **Feed.** No code change. Check the reading overlay at 360/411: the 8 dp taller bar lifts the overlay; the pills, title and action rail must not touch it. The 4 dp skirt stays.
5. **Insets.** Remove `navigationBarsPadding()` from the old Row; add the bottom spacer and the horizontal-only padding on the content. Test gesture, 3-button and landscape side-nav.
6. **Tests.** Add the UI tests in §12; screenshot the bar at 1× and 1.6× in both themes (dark only).
7. **Rollback.** The change is one file plus tokens/strings. Restore the old bar from git if needed; no data migration, no navigation-graph change.

---

## 12. Test plan

### Compose UI tests

```kotlin
@Test
fun targets_are_at_least_48dp() {
    composeRule.setContent { MohakashTheme { MohakashBottomBar(/* … */) } }
    val nav = composeRule.onNodeWithTag("nav-feed").getUnclippedBoundsInRoot()
    assertTrue(nav.width >= 48.dp && nav.height >= 48.dp)
    val random = composeRule.onNodeWithTag("nav-random").getUnclippedBoundsInRoot()
    assertTrue(random.width >= 48.dp && random.height >= 48.dp)
}

@Test
fun random_is_a_button_and_not_selected() {
    composeRule.onNodeWithTag("nav-random")
        .assertHasClickAction()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
}

@Test
fun feed_is_a_selected_tab() {
    composeRule.onNodeWithTag("nav-feed").assertIsSelected()
    composeRule.onNodeWithTag("nav-archive").assertIsNotSelected()
}

@Test
fun labels_are_present_and_merged() {
    composeRule.onNodeWithText("Feed").assertExists()
    composeRule.onNodeWithText("Random").assertExists()
    composeRule.onNodeWithText("Archive").assertExists()
}
```

Add tests for:

- RTL: provide `LocalLayoutDirection = Rtl`; assert `nav-feed`'s left bound is greater than `nav-archive`'s.
- 320 dp: render at `DpSize(320.dp, 800.dp)` via a constrained box; assert the three labels are displayed and the random target is 104 × 80.
- Large text: `CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f))`; assert the bar height is greater than 80 dp and all three nodes are displayed. A screenshot/golden is better than a hard-coded height.
- Click: `nav-random.performClick()` calls `onRandom`; `nav-archive.performClick()` calls `onSelect(ARCHIVE)`.
- No target overlap: the clickable bounds of Feed, Random and Archive do not intersect.

### Manual / device checks

- 360 and 411 widths, gesture nav and 3-button nav, portrait.
- 320 dp split-screen and 1.6× font scale; a long label stress string (not shipping copy).
- RTL with TalkBack.
- Landscape / tablet: bar height, horizontal insets, max-width centring.
- Contrast on device at 1× (not only the mockup): the 1 dp track should be *just* visible; the ring and labels clearly visible.
- Fullscreen hide/show in the feed: no clipped overlay, no ghost track over the photo.
- Random picking: if phase 2, verify the arc stops on success/cancel and does not loop when the bar is off-screen.

---

## 13. Anti-patterns (do not do these)

1. **Do not** restore the 80 dp disc or the 62 dp accent core. If prominence needs a boost, use ring width/alpha or 52 dp, not a filled slab.
2. **Do not** offset the node above the bar (`offset(y = -16.dp)`). The top edge is a boundary; breaking it is the original mistake.
3. **Do not** make Random a third tab or give it `selected`. It is an action that navigates into the feed.
4. **Do not** move the click target to the 48 dp node; the whole 104 × 80 dp cell is the target.
5. **Do not** set a single `contentDescription` on the random column that replaces the visible “Random” text.
6. **Do not** make the labels icon-only at large font scales. Let the bar grow and ellipsise the label; keep the full accessible name.
7. **Do not** fix the bar height at 80 dp or use `height(80.dp)`; use `heightIn(min = 80.dp)` and let the text grow.
8. **Do not** use 10 sp all-caps tracked labels to fit. 12 / 16 sp is the floor.
9. **Do not** use `navigationBarsPadding()` inside the row; it steals the 80 dp content and pushes the labels into the inset.
10. **Do not** animate the orbit, add a moving beacon at rest, or rotate the rocket. The only loop is real pick progress.
11. **Do not** use a translucent/glass bar over the photo. The bar is solid for contrast; the track gives it character.
12. **Do not** rely on the track for the selected state. It is decorative; colour + weight + underline carry the state.
13. **Do not** hard-code left/right positions or text alignment for RTL; use the Row and layout direction. Do not auto-mirror the icons.
14. **Do not** use a fixed 104 dp centre cell on a 280 dp window without checking the side cells; 320 dp is the tested practical minimum.
15. **Do not** draw the track at a fixed pixel width or with a default fill; use dp + a stroke and a percentage fade.
16. **Do not** let the track cross the labels or the system inset. It lives between y = 30 and y = 48, inside the 80 dp content.
17. **Do not** add a progress spinner to the whole bar or a permanently pulsing ring; the overlay is the status, the ring is a secondary cue.
18. **Do not** use `fillMaxWidth` on the labels without a horizontal padding; the ellipsis will touch the cell edge.
19. **Do not** use the old `BottomNavHeight = 72.dp` and assume the disc still fits; it no longer exists.
20. **Do not** add a second periwinkle pill/CTA in the nav. The Random label + ring is the only accent there.

---

## 14. Performance notes

- The track uses `drawWithCache`; the `Path` and `Brush` are built once per size, not per frame.
- The progress arc uses a `State<Float>` read inside `drawBehind`; only the draw phase invalidates. Do not read `angle.value` in composition.
- The infinite transition is conditional; when idle, no animation frame is requested.
- The bar has no blur, no shadow, no `graphicsLayer`; it is a solid surface with two draw calls (track, ring).
- Selection changes animate two colours and one width; no layout remeasure except the underline width (20 → 0), which is inside the fixed-height item and does not change the bar height.
- If the bar is composed during a picker dialog, stop the infinite transition when the lifecycle is not resumed (or when the overlay is visible). The picker already blocks interaction.

---

## 15. Open decisions

1. **Landscape.** The app does not lock orientation. An 80 + inset bar is ≈ 25–29% of a 360–411 dp landscape height. Choose portrait lock for the feed or design a 64 dp compact variant and test it separately. See [tradeoffs.md](tradeoffs.md).
2. **Picking arc.** Ship it only if `isPickingRandom` can be hoisted honestly; otherwise the static ring is the shipped design.
3. **Label copy.** “Random” fits every tested width. If it grows in translation, keep it as the visual label and move the full action into `onClickLabel`; do not shrink below 12 sp.
4. **Track visibility.** Confirm on a low-end OLED at 1×. If it disappears, raise the token; if it reads as a stray line, remove it. The nav must work both ways.
5. **Underline vs. a shared sliding indicator.** The per-item underline is simple, robust and RTL-safe. A single sliding indicator is prettier but needs parent coordinates and more code; not recommended for v1.
