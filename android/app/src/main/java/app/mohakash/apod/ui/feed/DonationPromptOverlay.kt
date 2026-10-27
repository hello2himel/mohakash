package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import app.mohakash.apod.ui.components.PrimaryButton
import app.mohakash.apod.ui.components.QuietButton
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** A rare, dismissible support card over the feed. It never opens a page automatically. */
@Composable
internal fun DonationPromptOverlay(
    onSupport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(enabled = true, onClick = onDismiss)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MohakashColors.Surface)
                .border(1.dp, MohakashColors.Accent.copy(alpha = 0.28f), shape)
                .clickable(onClick = {})
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart_24),
                contentDescription = null,
                tint = MohakashColors.Accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.donation_feed_title),
                style = MohakashTextStyles.OnboardingTitle,
                color = MohakashColors.Text,
            )
            Text(
                text = stringResource(R.string.donation_feed_body),
                style = MohakashTextStyles.BodyBase,
                color = MohakashColors.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PrimaryButton(
                    text = stringResource(R.string.settings_support_title),
                    onClick = onSupport,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                )
                QuietButton(
                    text = stringResource(R.string.donation_feed_not_now),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    minHeight = 48.dp,
                    color = MohakashColors.TextMuted,
                    style = MohakashTextStyles.UiSmallSemiBold,
                )
            }
        }
    }
}
}
