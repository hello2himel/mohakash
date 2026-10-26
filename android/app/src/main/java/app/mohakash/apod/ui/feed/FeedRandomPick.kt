package app.mohakash.apod.ui.feed

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.BrandMarkBadge
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The short, explicitly cancellable animation shown while the random picker loads the index and
 * validates a picture.
 *
 * The app mark drifts over a small dotted course while the index is checked; it is the same blue
 * key used in the navigation dock, so the action keeps one visual identity.
 */
@Composable
internal fun FeedRandomPickOverlay(onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MohakashColors.Surface)
                    .border(1.dp, MohakashColors.Border, RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RandomCourse()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.feed_random_title),
                    style = MohakashTextStyles.SectionHeading,
                    color = MohakashColors.Text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.feed_random_body),
                    style = MohakashTextStyles.BodySmall,
                    color = MohakashColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                TextAction(
                    text = stringResource(R.string.feed_random_cancel),
                    onClick = onCancel,
                )
            }
        }
    }
}

/** A dotted flight path, a quiet star field, and the floating brand mark. */
@Composable
private fun RandomCourse() {
    val transition = rememberInfiniteTransition(label = "random-course")
    val density = LocalDensity.current
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "course-bob",
    )

    Box(
        modifier = Modifier.size(width = 190.dp, height = 104.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stars = listOf(
                Offset(size.width * 0.10f, size.height * 0.18f),
                Offset(size.width * 0.30f, size.height * 0.82f),
                Offset(size.width * 0.54f, size.height * 0.12f),
                Offset(size.width * 0.73f, size.height * 0.78f),
                Offset(size.width * 0.90f, size.height * 0.36f),
                Offset(size.width * 0.82f, size.height * 0.12f),
            )
            stars.forEach { point ->
                drawCircle(
                    color = MohakashColors.TextMuted.copy(alpha = 0.42f),
                    radius = 1.1.dp.toPx(),
                    center = point,
                )
            }

            // A dotted arc behind the rocket.
            val path = listOf(
                Offset(size.width * 0.06f, size.height * 0.82f),
                Offset(size.width * 0.20f, size.height * 0.61f),
                Offset(size.width * 0.34f, size.height * 0.49f),
                Offset(size.width * 0.50f, size.height * 0.44f),
                Offset(size.width * 0.66f, size.height * 0.47f),
                Offset(size.width * 0.80f, size.height * 0.59f),
                Offset(size.width * 0.94f, size.height * 0.78f),
            )
            path.forEachIndexed { index, point ->
                drawCircle(
                    color = MohakashColors.Accent.copy(alpha = 0.16f + index * 0.025f),
                    radius = 1.4.dp.toPx(),
                    center = point,
                )
            }
        }

        BrandMarkBadge(
            badgeSize = 64.dp,
            iconSize = 36.dp,
            modifier = Modifier.graphicsLayer {
                translationY = with(density) { (-4f + bob * 5f).dp.toPx() }
            },
        )
    }
}
