package app.mohakash.apod.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

private enum class LauncherGlyph { Camera, Phone, Messages, Browser, Clock, Weather, Photos, Music }

private data class LauncherApp(
    val color: Color,
    val glyph: LauncherGlyph,
    val label: String,
)

private val HOME_APPS = listOf(
    LauncherApp(Color(0xFF72A7F2), LauncherGlyph.Camera, "Camera"),
    LauncherApp(Color(0xFF8668C9), LauncherGlyph.Phone, "Phone"),
    LauncherApp(Color(0xFFE49A69), LauncherGlyph.Messages, "Messages"),
    LauncherApp(Color(0xFF5EBDB4), LauncherGlyph.Browser, "Browser"),
    LauncherApp(Color(0xFF6F7DB4), LauncherGlyph.Clock, "Clock"),
    LauncherApp(Color(0xFFD98DA8), LauncherGlyph.Weather, "Weather"),
    LauncherApp(Color(0xFF8E9BE5), LauncherGlyph.Photos, "Photos"),
    LauncherApp(Color(0xFFB78A66), LauncherGlyph.Music, "Music"),
)

/**
 * Decorative Android launcher chrome drawn over the demo wallpaper.
 *
 * Phone mockups should read as phones without shipping a second set of screenshots. Every
 * dimension is scaled from the mockup width, so the chrome works at both onboarding preview sizes.
 */
@Composable
internal fun PhoneLauncherChrome(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { },
    ) {
        val unit = maxWidth / 20f
        val icon = unit * 2.25f
        val iconRadius = unit * 0.62f
        val statusTextSize = (unit.value * 1.08f).coerceAtLeast(5f).sp
        val chromeHeight = maxHeight
        val labelSize = (unit.value * 0.55f).coerceAtLeast(4.2f).sp

        Box(Modifier.fillMaxSize()) {
            // Glass reflection across the whole panel.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.075f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.025f),
                            ),
                        ),
                    ),
            )

            // Calm the lower half so app labels stay readable over the art.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(chromeHeight * 0.58f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xFF03050B).copy(alpha = 0.62f)),
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = unit * 1.25f, top = unit * 0.78f, end = unit * 1.20f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "9:41",
                    style = MohakashTextStyles.Caption.copy(
                        fontSize = statusTextSize,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 1.5f),
                    ),
                    color = Color.White.copy(alpha = 0.92f),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(unit * 0.38f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .width(unit * 0.22f)
                                .height(unit * (0.38f + index * 0.17f))
                                .background(
                                    Color.White.copy(alpha = 0.84f),
                                    RoundedCornerShape(unit * 0.07f),
                                ),
                        )
                    }
                    Canvas(Modifier.size(unit * 0.95f)) {
                        val stroke = unit.toPx() * 0.11f
                        drawArc(
                            color = Color.White.copy(alpha = 0.84f),
                            startAngle = -46f,
                            sweepAngle = 92f,
                            useCenter = false,
                            style = Stroke(stroke),
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.84f),
                            startAngle = -52f,
                            sweepAngle = 104f,
                            useCenter = false,
                            style = Stroke(stroke),
                            topLeft = Offset(size.width * 0.16f, size.height * 0.18f),
                            size = Size(size.width * 0.68f, size.height * 0.68f),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.9f),
                            radius = unit.toPx() * 0.10f,
                            center = Offset(size.width * 0.5f, size.height * 0.76f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(unit * 0.98f)
                            .height(unit * 0.48f)
                            .clip(RoundedCornerShape(unit * 0.11f))
                            .border(unit * 0.07f, Color.White.copy(alpha = 0.72f)),
                    )
                }
            }

            // Punch-hole camera with a lens ring and a tiny cool reflection.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = unit * 0.56f)
                    .size(unit * 1.48f)
                    .clip(CircleShape)
                    .background(Color(0xFF010207))
                    .border(unit * 0.075f, Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(unit * 0.92f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF31406E),
                                    Color(0xFF080A16),
                                    Color(0xFF010207),
                                ),
                            ),
                        )
                        .border(unit * 0.055f, Color(0xFF6074B8).copy(alpha = 0.4f), CircleShape),
                )
            }

            // Side buttons, inset just inside the frame edge.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = unit * 0.18f)
                    .width(unit * 0.24f)
                    .height(unit * 2.2f)
                    .background(Color(0xFF2A3346), RoundedCornerShape(unit * 0.12f)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = unit * 0.18f, y = -unit * 1.6f)
                    .width(unit * 0.24f)
                    .height(unit * 3.0f)
                    .background(Color(0xFF232B3D), RoundedCornerShape(unit * 0.12f)),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = unit * 1.05f, end = unit * 1.05f, bottom = unit * 0.80f),
                verticalArrangement = Arrangement.spacedBy(unit * 0.42f),
            ) {
                // Page indicator above the dock.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = unit * 0.16f)
                                .size(if (index == 0) unit * 0.46f else unit * 0.30f)
                                .clip(CircleShape)
                                .background(
                                    Color.White.copy(alpha = if (index == 0) 0.82f else 0.30f),
                                ),
                        )
                    }
                }

                HOME_APPS.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        row.forEach { app ->
                            LauncherAppIcon(
                                app = app,
                                size = icon,
                                radius = iconRadius,
                                labelSize = labelSize,
                            )
                        }
                    }
                }

                // Dock with a frosted surface and a hairline highlight.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(unit * 1.25f))
                        .background(Color.White.copy(alpha = 0.10f))
                        .border(
                            unit * 0.07f,
                            Color.White.copy(alpha = 0.16f),
                            RoundedCornerShape(unit * 1.25f),
                        )
                        .padding(horizontal = unit * 0.72f, vertical = unit * 0.48f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(
                        Color(0xFF91B6F5) to LauncherGlyph.Phone,
                        Color(0xFF75C1A9) to LauncherGlyph.Messages,
                        Color(0xFFE4A47C) to LauncherGlyph.Camera,
                        Color(0xFF9B8BCB) to LauncherGlyph.Music,
                    ).forEach { (color, glyph) ->
                        DockIcon(
                            color = color,
                            glyph = glyph,
                            size = icon * 0.88f,
                            radius = unit * 0.52f,
                        )
                    }
                }

                // Low-contrast gesture pill instead of three navigation buttons.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(unit * 1.9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(unit * 4.6f)
                            .height(unit * 0.34f)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.58f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherAppIcon(
    app: LauncherApp,
    size: Dp,
    radius: Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(radius * 0.48f),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(radius * 0.55f, RoundedCornerShape(radius), clip = false)
                .clip(RoundedCornerShape(radius))
                .background(
                    Brush.linearGradient(
                        listOf(app.color.copy(alpha = 0.96f), app.color.copy(alpha = 0.58f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyph(
                glyph = app.glyph,
                modifier = Modifier.size(size * 0.58f),
            )
        }
        Text(
            text = app.label,
            style = TextStyle(
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(Color.Black.copy(alpha = 0.75f), blurRadius = 1.4f),
            ),
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun DockIcon(
    color: Color,
    glyph: LauncherGlyph,
    size: Dp,
    radius: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(radius * 0.35f, RoundedCornerShape(radius), clip = false)
            .clip(RoundedCornerShape(radius))
            .background(color.copy(alpha = 0.90f)),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyph(glyph = glyph, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun AppGlyph(glyph: LauncherGlyph, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tint = Color.White.copy(alpha = 0.94f)
        val w = size.width
        val h = size.height
        when (glyph) {
            LauncherGlyph.Camera -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.30f),
                    size = Size(w * 0.76f, h * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f),
                    style = Stroke(w * 0.08f),
                )
                drawCircle(tint, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.56f), style = Stroke(w * 0.07f))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.32f, h * 0.18f),
                    size = Size(w * 0.24f, h * 0.15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
                )
            }
            LauncherGlyph.Phone -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.32f, h * 0.10f),
                    size = Size(w * 0.36f, h * 0.80f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = Stroke(w * 0.08f),
                )
                drawCircle(tint, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.80f))
            }
            LauncherGlyph.Messages -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.12f, h * 0.18f),
                    size = Size(w * 0.76f, h * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
                )
                val tail = Path().apply {
                    moveTo(w * 0.30f, h * 0.66f)
                    lineTo(w * 0.22f, h * 0.84f)
                    lineTo(w * 0.46f, h * 0.68f)
                    close()
                }
                drawPath(tail, tint)
            }
            LauncherGlyph.Browser -> {
                drawCircle(tint, radius = w * 0.34f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(w * 0.08f))
                drawArc(
                    color = tint,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(w * 0.24f, h * 0.16f),
                    size = Size(w * 0.52f, h * 0.68f),
                    style = Stroke(w * 0.06f),
                )
                drawLine(tint, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.82f, h * 0.50f), w * 0.06f)
            }
            LauncherGlyph.Clock -> {
                drawCircle(tint, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(w * 0.08f))
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.24f), w * 0.07f)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.68f, h * 0.60f), w * 0.07f)
            }
            LauncherGlyph.Weather -> {
                drawCircle(tint, radius = w * 0.18f, center = Offset(w * 0.40f, h * 0.38f))
                repeat(6) { index ->
                    val angle = Math.toRadians((index * 60).toDouble())
                    drawLine(
                        color = tint,
                        start = Offset(
                            w * 0.40f + kotlin.math.cos(angle).toFloat() * w * 0.26f,
                            h * 0.38f + kotlin.math.sin(angle).toFloat() * w * 0.26f,
                        ),
                        end = Offset(
                            w * 0.40f + kotlin.math.cos(angle).toFloat() * w * 0.34f,
                            h * 0.38f + kotlin.math.sin(angle).toFloat() * w * 0.34f,
                        ),
                        strokeWidth = w * 0.06f,
                    )
                }
                drawOval(
                    tint,
                    topLeft = Offset(w * 0.30f, h * 0.58f),
                    size = Size(w * 0.52f, h * 0.22f),
                )
            }
            LauncherGlyph.Photos -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.72f, h * 0.68f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                    style = Stroke(w * 0.07f),
                )
                val mountain = Path().apply {
                    moveTo(w * 0.20f, h * 0.72f)
                    lineTo(w * 0.42f, h * 0.42f)
                    lineTo(w * 0.56f, h * 0.60f)
                    lineTo(w * 0.66f, h * 0.48f)
                    lineTo(w * 0.82f, h * 0.72f)
                    close()
                }
                drawPath(mountain, tint)
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.68f, h * 0.30f))
            }
            LauncherGlyph.Music -> {
                drawLine(tint, Offset(w * 0.46f, h * 0.22f), Offset(w * 0.46f, h * 0.70f), w * 0.07f)
                drawLine(tint, Offset(w * 0.46f, h * 0.22f), Offset(w * 0.78f, h * 0.16f), w * 0.07f)
                drawLine(tint, Offset(w * 0.78f, h * 0.16f), Offset(w * 0.78f, h * 0.64f), w * 0.07f)
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.35f, h * 0.72f))
                drawCircle(tint, radius = w * 0.13f, center = Offset(w * 0.68f, h * 0.66f))
            }
        }
    }
}
