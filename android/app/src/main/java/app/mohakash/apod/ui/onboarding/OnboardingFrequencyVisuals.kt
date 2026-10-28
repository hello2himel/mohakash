package app.mohakash.apod.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.withFrameNanos

internal enum class WallpaperCadence { OnceDaily, MultipleDaily }

private const val CYCLE_MS = 2_000L
private const val START_PHASE_MS = 2_000L
private const val REDUCED_PHASE_MS = 3_100L // ~03:43, a complete night frame
private const val ONCE_CHANGE_PHASE = 0.19f
private val MULTI_CHANGE_PHASES = floatArrayOf(0.08f, 0.32f, 0.56f, 0.80f)
private const val PLATE_COUNT = 5

/** One monotonic day-night clock shared by both cards. */
@Stable
internal class DayCycleClock(initialMs: Long = START_PHASE_MS) {
    var elapsedMs by mutableStateOf(initialMs)
        private set

    val phase: Float get() = (elapsedMs % CYCLE_MS).toFloat() / CYCLE_MS.toFloat()
    val cycle: Long get() = elapsedMs / CYCLE_MS

    fun advance(deltaMs: Long) {
        elapsedMs += deltaMs.coerceAtLeast(0L)
    }

    fun reset(ms: Long) {
        elapsedMs = ms
    }
}

@Composable
internal fun rememberDayCycleClock(animate: Boolean): DayCycleClock {
    val clock = remember { DayCycleClock(if (animate) START_PHASE_MS else REDUCED_PHASE_MS) }
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(animate, lifecycleOwner) {
        if (!animate) {
            clock.reset(REDUCED_PHASE_MS)
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) {
                withFrameNanos { frame ->
                    if (previous != 0L) {
                        clock.advance(((frame - previous) / 1_000_000L).coerceIn(0L, 64L))
                    }
                    previous = frame
                }
            }
        }
    }
    return clock
}

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    return remember(context, inspection) {
        if (inspection) return@remember true
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * A self-contained, clipped day-night preview. The sky is drawn in a Canvas; the
 * background plate changes on the cadence represented by [cadence].
 */
@Composable
internal fun DayNightPreview(
    clock: DayCycleClock,
    cadence: WallpaperCadence,
    modifier: Modifier = Modifier,
) {
    val target by remember(clock, cadence) {
        derivedStateOf { imageTarget(clock.cycle, clock.phase, cadence) }
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MohakashColors.Background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // The day-night sky is the card's own background; the phone sits on top of it.
        AnimatedSkyBackground(clock = clock, modifier = Modifier.matchParentSize())
        // A soft halo keeps the eye on the phone rather than the animated sky.
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(198.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MohakashColors.Accent.copy(alpha = 0.22f),
                            MohakashColors.Accent.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        PhonePreview(
            target = target,
            modifier = Modifier
                .width(96.dp)
                .height(166.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(9.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.52f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = stringResource(
                    when (cadence) {
                        WallpaperCadence.OnceDaily -> R.string.onboarding_changes_once
                        WallpaperCadence.MultipleDaily -> R.string.onboarding_changes_multiple
                    },
                ),
                style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

/** The phone mockup is the only place an astronomical image is shown. */
@Composable
private fun PhonePreview(
    target: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF05070F))
            .border(2.dp, MohakashColors.Accent.copy(alpha = 0.72f), shape)
            .padding(4.dp),
    ) {
        Crossfade(
            targetState = target,
            animationSpec = tween(320),
            label = "phone-wallpaper",
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp)),
        ) { beat ->
            WallpaperPlate(index = positiveMod(beat, PLATE_COUNT))
        }
        // Speaker slot, subtle enough not to read as content.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 3.dp)
                .width(24.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.72f)),
        )
    }
}


private fun imageTarget(cycle: Long, phase: Float, cadence: WallpaperCadence): Int =
    when (cadence) {
        WallpaperCadence.OnceDaily ->
            (cycle + if (phase >= ONCE_CHANGE_PHASE) 1L else 0L).toInt()

        WallpaperCadence.MultipleDaily -> {
            val changes = MULTI_CHANGE_PHASES.count { phase >= it }
            (cycle * MULTI_CHANGE_PHASES.size + changes + 2L).toInt()
        }
    }

private fun positiveMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus

@Composable
private fun WallpaperPlate(index: Int) {
    val scene = remember(index) { positiveMod(index, DEMO_SCENES.size) }
    Canvas(Modifier.fillMaxSize()) {
        when (DEMO_SCENES[scene]) {
            DemoAstro.Moon -> drawMoon()
            DemoAstro.Jupiter -> drawJupiter()
            DemoAstro.Andromeda -> drawAndromeda()
            DemoAstro.Nebula -> drawNebula()
            DemoAstro.BlackHole -> drawBlackHole()
        }
    }
}

private enum class DemoAstro { Moon, Jupiter, Andromeda, Nebula, BlackHole }

private val DEMO_SCENES = listOf(
    DemoAstro.Moon,
    DemoAstro.Jupiter,
    DemoAstro.Andromeda,
    DemoAstro.Nebula,
    DemoAstro.BlackHole,
)

private fun DrawScope.drawStarfield(seed: Int, count: Int, maxY: Float = 0.9f) {
    val random = Random(seed)
    repeat(count) {
        val x = random.nextFloat()
        val y = random.nextFloat() * maxY
        val radius = (0.35f + random.nextFloat() * 1.1f).dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.25f + random.nextFloat() * 0.65f),
            radius = radius,
            center = Offset(x * size.width, y * size.height),
        )
    }
}

private fun DrawScope.drawMoon() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF060A1A), Color(0xFF151A35), Color(0xFF03040A))))
    drawStarfield(seed = 17, count = 42)
    val center = Offset(size.width * 0.54f, size.height * 0.48f)
    val radius = size.width * 0.28f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF8E7), Color(0xFFBEB7A8)),
            center = Offset(center.x - radius * 0.25f, center.y - radius * 0.3f),
            radius = radius * 1.8f,
        ),
        radius = radius,
        center = center,
    )
    // Craters and a soft shadow edge.
    drawCircle(Color(0xFF8D877D).copy(alpha = 0.45f), radius * 0.10f, Offset(center.x - radius * 0.35f, center.y - radius * 0.25f))
    drawCircle(Color(0xFF8D877D).copy(alpha = 0.35f), radius * 0.07f, Offset(center.x + radius * 0.28f, center.y - radius * 0.04f))
    drawCircle(Color(0xFF6E685F).copy(alpha = 0.30f), radius * 0.12f, Offset(center.x + radius * 0.12f, center.y + radius * 0.38f))
    drawCircle(Color(0xFF05070F).copy(alpha = 0.42f), radius = radius * 0.98f, center = Offset(center.x + radius * 0.72f, center.y - radius * 0.12f))
}

private fun DrawScope.drawJupiter() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A16), Color(0xFF11162B), Color(0xFF03040A))))
    drawStarfield(seed = 31, count = 34)
    val center = Offset(size.width * 0.52f, size.height * 0.48f)
    val radius = size.width * 0.30f
    drawCircle(Color(0xFFD5B486), radius = radius, center = center)
    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, radius)) }
    clipPath(clip) {
        drawRect(Color(0xFFE8D7B9).copy(alpha = 0.7f), topLeft = Offset(center.x - radius, center.y - radius * 0.62f), size = Size(radius * 2f, radius * 0.30f))
        drawRect(Color(0xFF9E6F4E).copy(alpha = 0.65f), topLeft = Offset(center.x - radius, center.y + radius * 0.16f), size = Size(radius * 2f, radius * 0.22f))
        drawRect(Color(0xFFC79062).copy(alpha = 0.5f), topLeft = Offset(center.x - radius, center.y + radius * 0.46f), size = Size(radius * 2f, radius * 0.18f))
        drawOval(Color(0xFFB65B40).copy(alpha = 0.85f), topLeft = Offset(center.x + radius * 0.08f, center.y + radius * 0.10f), size = Size(radius * 0.42f, radius * 0.22f))
    }
    drawCircle(Color(0xFF05070F).copy(alpha = 0.35f), radius = radius * 0.98f, center = Offset(center.x + radius * 0.66f, center.y - radius * 0.06f))
}

private fun DrawScope.drawAndromeda() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF050716), Color(0xFF11162E), Color(0xFF03040A))))
    drawStarfield(seed = 47, count = 55)
    val center = Offset(size.width * 0.55f, size.height * 0.46f)
    rotate(degrees = -28f, pivot = center) {
        drawOval(
            brush = Brush.radialGradient(
                listOf(Color(0xFFD6E5FF).copy(alpha = 0.8f), Color(0xFF809FE0).copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = size.width * 0.48f,
            ),
            topLeft = Offset(center.x - size.width * 0.42f, center.y - size.width * 0.16f),
            size = Size(size.width * 0.84f, size.width * 0.32f),
        )
        drawOval(
            brush = Brush.radialGradient(listOf(Color(0xFFFFF4D0).copy(alpha = 0.55f), Color.Transparent), center = center, radius = size.width * 0.22f),
            topLeft = Offset(center.x - size.width * 0.22f, center.y - size.width * 0.10f),
            size = Size(size.width * 0.44f, size.width * 0.20f),
        )
    }
    drawCircle(Color(0xFFFFF7DC).copy(alpha = 0.95f), radius = size.width * 0.045f, center = center)
}

private fun DrawScope.drawNebula() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF080516), Color(0xFF1A0D2E), Color(0xFF03040A))))
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFFE48BB5).copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 0.44f, size.height * 0.45f), radius = size.width * 0.55f),
        radius = size.width * 0.55f,
        center = Offset(size.width * 0.44f, size.height * 0.45f),
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF8A9BFF).copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width * 0.66f, size.height * 0.38f), radius = size.width * 0.42f),
        radius = size.width * 0.42f,
        center = Offset(size.width * 0.66f, size.height * 0.38f),
    )
    drawCircle(
        brush = Brush.radialGradient(listOf(Color(0xFF7DE3D1).copy(alpha = 0.26f), Color.Transparent), center = Offset(size.width * 0.38f, size.height * 0.62f), radius = size.width * 0.38f),
        radius = size.width * 0.38f,
        center = Offset(size.width * 0.38f, size.height * 0.62f),
    )
    drawStarfield(seed = 61, count = 32)
}

private fun DrawScope.drawBlackHole() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF03040A), Color(0xFF0A0D20), Color(0xFF020309))))
    drawStarfield(seed = 83, count = 42)
    val center = Offset(size.width * 0.51f, size.height * 0.48f)
    val radius = size.width * 0.24f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFB8C8FF).copy(alpha = 0.28f), Color.Transparent),
            center = center,
            radius = size.width * 0.62f,
        ),
        radius = size.width * 0.62f,
        center = center,
    )
    // Accretion disk: a tilted, glowing ellipse around a pitch-black event horizon.
    rotate(degrees = -22f, pivot = center) {
        drawOval(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFFD59A).copy(alpha = 0.85f), Color(0xFFF08B54).copy(alpha = 0.55f), Color(0xFF8A6BFF).copy(alpha = 0.35f)),
                start = Offset(center.x - size.width * 0.55f, center.y),
                end = Offset(center.x + size.width * 0.55f, center.y),
            ),
            topLeft = Offset(center.x - size.width * 0.48f, center.y - size.width * 0.12f),
            size = Size(size.width * 0.96f, size.width * 0.24f),
        )
        drawOval(
            color = Color(0xFFFFE7B8).copy(alpha = 0.7f),
            topLeft = Offset(center.x - size.width * 0.38f, center.y - size.width * 0.075f),
            size = Size(size.width * 0.76f, size.width * 0.15f),
            style = Stroke(width = 0.8f.dp.toPx()),
        )
    }
    drawCircle(Color(0xFF010207), radius = radius, center = center)
    drawCircle(
        color = Color(0xFFFFD7A0).copy(alpha = 0.55f),
        radius = radius * 1.02f,
        center = center,
        style = Stroke(width = 0.7f.dp.toPx()),
    )
}

@Composable
private fun AnimatedSkyBackground(clock: DayCycleClock, modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(0x5EED)
        List(22) {
            Triple(random.nextFloat(), random.nextFloat(), 0.55f + random.nextFloat() * 0.9f)
        }
    }
    Canvas(modifier) {
        val phase = clock.phase
        val (top, bottom) = skyColors(phase)
        drawRect(Brush.verticalGradient(listOf(top, bottom), 0f, size.height))
        val starAlpha = starVisibility(phase)
        if (starAlpha > 0f) {
            stars.forEach { (x, y, radius) ->
                drawCircle(
                    color = Color.White.copy(alpha = starAlpha * (0.4f + y * 0.55f)),
                    radius = radius.dp.toPx() * 0.45f,
                    center = Offset(x * size.width, y * size.height * 0.82f),
                )
            }
        }
        val horizon = size.height * 0.55f
        drawCelestialBody(phase, horizon)
        drawClouds(phase, horizon)
        // Mute the whole backdrop and add a vignette so the phone remains the focal point.
        drawRect(Color(0xFF05070F).copy(alpha = 0.20f))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF04060D).copy(alpha = 0.34f),
                ),
                center = Offset(size.width / 2f, size.height * 0.56f),
                radius = size.maxDimension * 0.72f,
            ),
        )
    }
}


private fun DrawScope.drawCelestialBody(phase: Float, horizon: Float) {
    // Sun travels the first three quarters of the arc, from the left horizon to the zenith.
    // The moon then continues from the zenith down to the right horizon.
    val angle = if (phase < 0.75f) {
        PI.toFloat() - (phase / 0.75f) * (PI.toFloat() / 2f)
    } else {
        (PI.toFloat() / 2f) - ((phase - 0.75f) / 0.25f) * (PI.toFloat() / 2f)
    }
    val center = Offset(
        x = size.width / 2f + kotlin.math.cos(angle) * size.width * 0.38f,
        y = horizon - kotlin.math.sin(angle) * horizon * 0.55f,
    )
    if (phase < 0.75f) {
        drawCircle(Color(0xFFFFD9A0).copy(alpha = 0.16f), radius = 12.dp.toPx(), center = center)
        drawCircle(Color(0xFFFFD9A0), radius = 3.8f.dp.toPx(), center = center)
    } else {
        drawCircle(Color(0xFFE8EAF0).copy(alpha = 0.10f), radius = 8.dp.toPx(), center = center)
        drawCircle(Color(0xFFE8EAF0).copy(alpha = 0.9f), radius = 3.4f.dp.toPx(), center = center)
    }
}

private fun DrawScope.drawClouds(phase: Float, horizon: Float) {
    val alpha = when {
        phase < 0.27f || phase > 0.72f -> 0f
        phase < 0.40f -> (phase - 0.27f) / 0.13f * 0.23f
        phase > 0.62f -> (0.72f - phase) / 0.10f * 0.23f
        else -> 0.23f
    }
    if (alpha <= 0f) return
    val drift = (phase * 2f) % 1f
    drawOval(
        color = Color(0xFFF0F5FF).copy(alpha = alpha),
        topLeft = Offset(size.width * ((0.12f + drift * 0.06f) % 0.7f), horizon * 0.18f),
        size = Size(size.width * 0.42f, 4.dp.toPx()),
    )
    drawOval(
        color = Color(0xFFF0F5FF).copy(alpha = alpha * 0.75f),
        topLeft = Offset(size.width * ((0.55f + drift * 0.04f) % 0.75f), horizon * 0.34f),
        size = Size(size.width * 0.28f, 3.dp.toPx()),
    )
}

private fun starVisibility(phase: Float): Float = when {
    phase < 0.20f -> 0.95f
    phase < 0.28f -> (0.28f - phase) / 0.08f * 0.95f
    phase > 0.76f -> ((phase - 0.76f) / 0.12f * 0.95f).coerceAtMost(0.95f)
    else -> 0f
}

private data class SkyStop(val at: Float, val top: Color, val bottom: Color)

private val SKY_STOPS = listOf(
    SkyStop(0.00f, Color(0xFF05070F), Color(0xFF0B1020)),
    SkyStop(0.12f, Color(0xFF060912), Color(0xFF0D1430)),
    SkyStop(0.17f, Color(0xFF0B0F22), Color(0xFF241A44)),
    SkyStop(0.21f, Color(0xFF2A1B52), Color(0xFF8E4468)),
    SkyStop(0.235f, Color(0xFF3E2568), Color(0xFFD06A7A)),
    SkyStop(0.25f, Color(0xFF4E3070), Color(0xFFF2A05E)),
    SkyStop(0.29f, Color(0xFF3C5F97), Color(0xFFF7C48C)),
    SkyStop(0.34f, Color(0xFF2E6DAE), Color(0xFFA9CFF0)),
    SkyStop(0.46f, Color(0xFF2F79BC), Color(0xFFBFDCF4)),
    SkyStop(0.58f, Color(0xFF356FA8), Color(0xFFD7E3EE)),
    SkyStop(0.66f, Color(0xFF4A4A82), Color(0xFFF2A85C)),
    SkyStop(0.72f, Color(0xFF4A2A60), Color(0xFFE2643F)),
    SkyStop(0.78f, Color(0xFF251743), Color(0xFF7A3350)),
    SkyStop(0.84f, Color(0xFF0F1226), Color(0xFF232A48)),
    SkyStop(0.92f, Color(0xFF070A18), Color(0xFF0E1430)),
    SkyStop(1.00f, Color(0xFF05070F), Color(0xFF0B1020)),
)

private fun skyColors(phase: Float): Pair<Color, Color> {
    val value = phase.coerceIn(0f, 1f)
    for (index in 0 until SKY_STOPS.lastIndex) {
        val first = SKY_STOPS[index]
        val second = SKY_STOPS[index + 1]
        if (value in first.at..second.at) {
            val raw = if (second.at == first.at) 0f else (value - first.at) / (second.at - first.at)
            val t = raw * raw * (3f - 2f * raw)
            return lerp(first.top, second.top, t) to lerp(first.bottom, second.bottom, t)
        }
    }
    return SKY_STOPS.last().top to SKY_STOPS.last().bottom
}
