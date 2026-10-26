package app.mohakash.apod.ui.feed

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The first-run cue for the vertical feed.
 *
 * Two chevrons drift upwards and fade in sequence, the same language short-video feeds use to
 * teach the gesture without a hand-drawn illustration. The cue is deliberately a small floating
 * pill: it sits above the reading overlay, never blocks the picture, and disappears on the first
 * swipe or after a few seconds.
 */
@Composable
internal fun FeedSwipeHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "swipe-hint")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "swipe-hint-phase",
    )

    Row(
        modifier = modifier
            .background(
                color = MohakashColors.Surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
            )
            .border(
                width = 1.dp,
                color = MohakashColors.BorderStrong,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 17.dp, height = 26.dp)) {
            val stroke = 1.8.dp.toPx()
            val w = size.width
            val h = size.height
            repeat(2) { index ->
                val progress = (phase + index * 0.45f) % 1f
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.9f
                val y = h * (0.78f - progress * 0.48f)
                drawLine(
                    color = MohakashColors.Accent.copy(alpha = alpha),
                    start = Offset(w * 0.16f, y),
                    end = Offset(w * 0.5f, y - h * 0.16f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = MohakashColors.Accent.copy(alpha = alpha),
                    start = Offset(w * 0.84f, y),
                    end = Offset(w * 0.5f, y - h * 0.16f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = stringResource(R.string.feed_swipe_hint_title),
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.Text,
            )
            Text(
                text = stringResource(R.string.feed_swipe_hint_body),
                style = MohakashTextStyles.Caption,
                color = MohakashColors.TextMuted,
            )
        }
    }
}
