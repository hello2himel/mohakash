package app.mohakash.apod.ui.feed

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.TextAction
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The short, explicitly cancellable animation shown while the random picker loads the index and
 * validates a picture. The dice rotates and softly pulses; nothing else moves.
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
                val transition = rememberInfiniteTransition(label = "random-dice")
                val rotation by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "dice-rotation",
                )
                val scale by transition.animateFloat(
                    initialValue = 0.93f,
                    targetValue = 1.06f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dice-pulse",
                )
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MohakashColors.SurfaceStrong)
                        .border(1.dp, MohakashColors.BorderStrong, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dice_24),
                        contentDescription = null,
                        tint = MohakashColors.Accent,
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                rotationZ = rotation
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                }
                Spacer(Modifier.height(16.dp))
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
