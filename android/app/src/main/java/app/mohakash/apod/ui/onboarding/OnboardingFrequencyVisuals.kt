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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.withFrameNanos

internal enum class WallpaperCadence { OnceDaily, MultipleDaily }

private const val CYCLE_MS = 20_000L
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
    imageModel: Any?,
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
        PhonePreview(
            target = target,
            imageModel = imageModel,
            modifier = Modifier
                .width(92.dp)
                .height(160.dp),
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
    imageModel: Any?,
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
            animationSpec = tween(700),
            label = "phone-wallpaper",
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(16.dp)),
        ) { beat ->
            WallpaperPlate(index = positiveMod(beat, PLATE_COUNT), model = imageModel)
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
private fun WallpaperPlate(index: Int, model: Any?) {
    if (index == 0 && model != null) {
        val context = LocalContext.current
        val request = remember(model) {
            ImageRequest.Builder(context)
                .data(model)
                .size(720, 480)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        GeneratedPlate(index)
    }
}

@Composable
private fun GeneratedPlate(index: Int) {
    val palette = remember(index) {
        arrayOf(
            listOf(Color(0xFF080B1C), Color(0xFF1B2B5B), Color(0xFF7FA8D8)),
            listOf(Color(0xFF130D2B), Color(0xFF4B2D67), Color(0xFFE49A8A)),
            listOf(Color(0xFF061B24), Color(0xFF174D63), Color(0xFFA8DDD7)),
            listOf(Color(0xFF1A0E0C), Color(0xFF633428), Color(0xFFF2B36E)),
            listOf(Color(0xFF090A18), Color(0xFF2B2350), Color(0xFF9388D6)),
        )[positiveMod(index, 5)]
    }
    val stars = remember(index) {
        val random = Random(0x5EED + index * 97)
        List(26) {
            Triple(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 1.1f)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(palette, startY = 0f, endY = size.height),
        )
        stars.forEach { (x, y, radius) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.35f + 0.5f * (1f - y)),
                radius = radius.dp.toPx() * 0.55f,
                center = Offset(x * size.width, y * size.height * 0.72f),
            )
        }
        // A quiet planet/horizon silhouette gives each generated plate a distinct composition.
        val horizon = size.height * (0.68f + (index % 3) * 0.04f)
        drawCircle(
            color = Color(0xFF05070F).copy(alpha = 0.7f),
            radius = size.width * (0.24f + (index % 2) * 0.05f),
            center = Offset(size.width * (0.2f + (index % 3) * 0.3f), horizon),
        )
        drawRect(
            color = Color(0xFF05070F).copy(alpha = 0.75f),
            topLeft = Offset(0f, horizon),
            size = Size(size.width, size.height - horizon),
        )
    }
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
        drawRect(Brush.verticalGradient(listOf(top, bottom, Color(0xFF05070F)), 0f, size.height))
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
        drawSunAndMoon(phase, horizon)
        drawClouds(phase, horizon)
        drawLine(
            color = Color(0xFF05070F).copy(alpha = 0.55f),
            start = Offset(0f, horizon),
            end = Offset(size.width, horizon),
            strokeWidth = 1.dp.toPx(),
        )
    }
}


private fun DrawScope.drawSunAndMoon(phase: Float, horizon: Float) {
    if (phase in 0.24f..0.74f) {
        val u = (phase - 0.24f) / 0.50f
        val center = Offset(
            x = size.width * (0.15f + 0.70f * u),
            y = horizon * 0.88f - sin(PI.toFloat() * u) * horizon * 0.54f,
        )
        drawCircle(Color(0xFFFFD9A0).copy(alpha = 0.16f), radius = 12.dp.toPx(), center = center)
        drawCircle(Color(0xFFFFD9A0), radius = 3.8f.dp.toPx(), center = center)
    }
    val moonPhase = when {
        phase >= 0.78f -> (phase - 0.78f) / 0.44f
        phase <= 0.22f -> (phase + 0.22f) / 0.44f
        else -> null
    }
    if (moonPhase != null) {
        val u = moonPhase.coerceIn(0f, 1f)
        val center = Offset(
            x = size.width * (0.12f + 0.76f * u),
            y = horizon * 0.90f - sin(PI.toFloat() * u) * horizon * 0.60f,
        )
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
